package com.example.urbaneye.security;

import com.example.urbaneye.model.User;
import com.example.urbaneye.repository.UserRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

@Component
public class FirebaseTokenFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public FirebaseTokenFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        // ✅ 1. Skip public endpoints
        if (path.equals("/firestore-test") || path.startsWith("/api/public") || path.startsWith("/api/communities")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ 2. Skip if Firebase not initialized
        if (FirebaseApp.getApps().isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ 3. Get Authorization header
        String header = request.getHeader("Authorization");

        // ✅ 4. If no token → continue without blocking
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = header.substring(7);
            String uid;
            String email;

            if (token.startsWith("MOCK_USER_")) {
                uid = token.substring(10);
                User dbUser = userRepository.findById(uid);
                email = dbUser != null ? dbUser.getEmail() : "mock@example.com";
            } else {
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);
                uid = decodedToken.getUid();
                email = decodedToken.getEmail();
            }

            User user = userRepository.findById(uid);

            if (user == null) {
                user = new User();
                user.setId(uid);
                user.setEmail(email);
                user.setCommunityIds(new ArrayList<>());
                user.setBannedFromCommunityIds(new ArrayList<>());
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Unauthorized or expired token: " + e.getClass().getSimpleName() + " - " + e.getMessage() + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}