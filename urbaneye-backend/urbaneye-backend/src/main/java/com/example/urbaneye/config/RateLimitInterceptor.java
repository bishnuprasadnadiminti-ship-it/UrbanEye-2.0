package com.example.urbaneye.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDate;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.SetOptions;
import com.google.firebase.cloud.FirestoreClient;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    private Bucket resolveBucket(String ip) {
        return cache.computeIfAbsent(ip, this::newBucket);
    }

    private Bucket newBucket(String ip) {
        // Allows 50 requests per minute per IP address
        Bandwidth limit = Bandwidth.builder()
                .capacity(50)
                .refillGreedy(50, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // IP extraction with proxy support
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Target Issue Post and Admin Updates endpoints
        boolean isPostCreation = (uri.equals("/api/issues") && method.equalsIgnoreCase("POST")) ||
                                 (uri.matches("^/api/admin/community/.+/updates$") && method.equalsIgnoreCase("POST"));

        if (isPostCreation) {
            String today = LocalDate.now().toString();
            String dailyKey = (ip != null ? ip.replace(":", "_").replace(".", "_") : "unknown") + "_" + today;
            
            try {
                Firestore db = FirestoreClient.getFirestore();
                DocumentReference docRef = db.collection("rate_limits").document(dailyKey);
                
                DocumentSnapshot snap = docRef.get().get();
                long currentCount = 0;
                if (snap.exists()) {
                    Long count = snap.getLong("count");
                    if (count != null) {
                        currentCount = count;
                    }
                }
                
                if (currentCount >= 5) {
                    response.setStatus(429); // 429 Too Many Requests
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Daily post limit reached for this IP\", \"limit\": 5}");
                    return false;
                }
                
                docRef.set(Map.of("count", FieldValue.increment(1)), SetOptions.merge());
            } catch (Exception e) {
                // Silently fallback on DB error to avoid freezing essential user posts
                e.printStackTrace();
            }
        }
        
        Bucket bucket = resolveBucket(ip);
        if (bucket.tryConsume(1)) {
            return true;
        } else {
            response.setStatus(429); // 429 Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Too many requests. Please try again later.\"}");
            return false;
        }
    }
}
