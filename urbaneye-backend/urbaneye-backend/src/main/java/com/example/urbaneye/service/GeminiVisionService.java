package com.example.urbaneye.service;

import com.example.urbaneye.exception.FreeTierExhaustedException;
import com.example.urbaneye.model.AiUsage;
import com.example.urbaneye.repository.AiUsageRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service that delegates image analysis to Featherless AI using an
 * OpenAI-compatible endpoint.
 *
 * Model cascade on 503 (capacity_exhausted):
 * 1. featherless.model (primary – Qwen3-VL-32B-Instruct)
 * 2. featherless.secondaryModel (secondary – Qwen2.5-VL-72B-Instruct)
 * 3. featherless.tertiaryModel (tertiary – Qwen3-VL-8B-Instruct)
 * 4. FreeTierExhaustedException → manual-entry fallback in the UI
 */
@Service
public class GeminiVisionService {

    private static final String PROMPT = "You are an AI assistant analyzing civil/civic issues from photographs. " +
            "Analyze the image and return ONLY a raw JSON object (no markdown, no backticks) with: " +
            "\"title\" (short 3-5 word descriptive title summarizing the issue), " +
            "\"category\" (must be exactly one of: \"Garbage\", \"Water\", \"Traffic\", \"Road\", \"Air Quality\", or \"General\"), " +
            "\"description\" (concise description of the problem/scene, identifying what is shown in the image), " +
            "\"severity\" (one of: HIGH, MEDIUM, LOW), " +
            "\"tags\" (array of short strings, e.g. [\"pothole\",\"road\",\"damage\"]).";

    @Value("${featherless.apiKey}")
    private String apiKey;

    @Value("${featherless.baseUrl:https://api.featherless.ai/v1}")
    private String apiUrl;

    @Value("${featherless.model:Qwen/Qwen3-VL-32B-Instruct}")
    private String primaryModel;

    @Value("${featherless.secondaryModel:Qwen/Qwen2.5-VL-72B-Instruct}")
    private String secondaryModel;

    @Value("${featherless.tertiaryModel:Qwen/Qwen3-VL-8B-Instruct}")
    private String tertiaryModel;

    @Autowired
    private AiUsageRepository aiUsageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    // ──────────────────────────────────────────────────────────────
    // Public DTO returned to callers
    // ──────────────────────────────────────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiResult {
        private String title;
        private String category;
        private String description;
        private String severity;
        private List<String> tags;

        public String getTitle() {
            return title;
        }

        public void setTitle(String t) {
            this.title = t;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String c) {
            this.category = c;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String d) {
            this.description = d;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String s) {
            this.severity = s;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> t) {
            this.tags = t;
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Main analysis method – cascades through model tiers on 503
    // ──────────────────────────────────────────────────────────────
    public AiResult analyzeImage(byte[] imageBytes, String mimeType) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:" + mimeType + ";base64," + base64Image;

        // Try each model in order; stop at the first success
        List<String> models = List.of(primaryModel, secondaryModel, tertiaryModel);

        for (int i = 0; i < models.size(); i++) {
            String model = models.get(i);
            boolean isLast = (i == models.size() - 1);

            try {
                return callModel(model, dataUrl);
            } catch (CapacityExhaustedException e) {
                if (isLast) {
                    // All models are at capacity – escalate to manual UI fallback
                    System.err.println("[FeatherlessVision] All models exhausted. Triggering manual fallback.");
                    throw new FreeTierExhaustedException(
                            "All Featherless AI models are temporarily unavailable. Please describe the issue manually.");
                }
                String next = models.get(i + 1);
                System.err.println("[FeatherlessVision] " + model + " is at capacity (503). " +
                        "Falling back to: " + next);
            } catch (FreeTierExhaustedException e) {
                throw e; // re-throw – already the right type
            } catch (Exception e) {
                // Non-capacity error (auth, bad request, network, etc.) – fail fast
                System.err.println("[FeatherlessVision] Unrecoverable error on model " + model + ": " + e.getMessage());
                throw new RuntimeException("Featherless Vision error: " + e.getMessage(), e);
            }
        }

        // Should never reach here, but satisfy the compiler
        throw new FreeTierExhaustedException("All Featherless AI models are temporarily unavailable.");
    }

    // ──────────────────────────────────────────────────────────────
    // Single model call – throws CapacityExhaustedException on 429/503
    // ──────────────────────────────────────────────────────────────
    private AiResult callModel(String model, String dataUrl) {
        Map<String, Object> textPart = Map.of("type", "text", "text", PROMPT);
        Map<String, Object> imagePart = Map.of("type", "image_url",
                "image_url", Map.of("url", dataUrl));

        Map<String, Object> message = Map.of("role", "user",
                "content", List.of(textPart, imagePart));
        Map<String, Object> requestBody = Map.of("model", model,
                "messages", List.of(message));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String fullUrl = apiUrl + "/chat/completions";
        System.out.println("[FeatherlessVision] Trying model: " + model + " → " + fullUrl);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl, HttpMethod.POST, entity, String.class);
            AiResult result = parseResponse(response.getBody());
            System.out.println("[FeatherlessVision] ✓ Success with model: " + model);
            return result;

        } catch (HttpStatusCodeException e) {
            int code = e.getStatusCode().value();
            String body = e.getResponseBodyAsString();
            System.err.println("[FeatherlessVision] HTTP " + code + " from model " + model + ": " + body);

            if (code == 429 || code == 503) {
                // Signal the cascade loop to try the next model
                throw new CapacityExhaustedException("Model " + model + " is at capacity (" + code + ")");
            }
            // 4xx / other 5xx errors are not retried with a different model
            throw new RuntimeException("Featherless REST call failed (" + e.getStatusCode() + "): " + body, e);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Parse OpenAI chat response JSON → AiResult
    // ──────────────────────────────────────────────────────────────
    private AiResult parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Track token usage if present
            JsonNode usageNode = root.path("usage");
            if (!usageNode.isMissingNode()) {
                long tokens = usageNode.path("total_tokens").asLong(0);
                if (tokens > 0)
                    recordTokenUsage(tokens);
            }

            // Extract generated text
            String rawText = root
                    .path("choices").path(0)
                    .path("message").path("content")
                    .asText("");

            if (rawText.isEmpty()) {
                System.err.println("[FeatherlessVision] Empty content text in response: " + responseBody);
                return buildFallback("AI analysis produced no text output.");
            }

            // Strip markdown fences if the model adds them
            rawText = rawText.trim();
            if (rawText.startsWith("```")) {
                rawText = rawText.replaceAll("(?s)```[a-z]*\\n?", "").replace("```", "").trim();
            }

            try {
                return objectMapper.readValue(rawText, AiResult.class);
            } catch (Exception jsonEx) {
                System.err.println("[FeatherlessVision] JSON parse failed, using raw text. Raw: " + rawText);
                return buildFallback(rawText.length() > 300 ? rawText.substring(0, 300) : rawText);
            }

        } catch (Exception e) {
            System.err.println("[FeatherlessVision] Failed to parse response: " + e.getMessage());
            return buildFallback("AI analysis completed but response could not be parsed.");
        }
    }

    private AiResult buildFallback(String description) {
        AiResult result = new AiResult();
        result.setTitle("Auto-generated Title");
        result.setCategory("General");
        result.setDescription(description);
        result.setSeverity("MEDIUM");
        result.setTags(List.of("civic-issue"));
        return result;
    }

    // ──────────────────────────────────────────────────────────────
    // Persist daily token usage
    // ──────────────────────────────────────────────────────────────
    private void recordTokenUsage(long tokens) {
        LocalDate today = LocalDate.now();
        Optional<AiUsage> usageOpt = aiUsageRepository.findByDate(today);
        AiUsage usage;
        if (usageOpt.isPresent()) {
            usage = usageOpt.get();
            usage.setTokensUsed(usage.getTokensUsed() + tokens);
        } else {
            usage = new AiUsage();
            usage.setDate(today);
            usage.setTokensUsed(tokens);
        }
        aiUsageRepository.save(usage);
    }

    // ──────────────────────────────────────────────────────────────
    // Internal exception used only for the cascade loop
    // ──────────────────────────────────────────────────────────────
    private static class CapacityExhaustedException extends RuntimeException {
        CapacityExhaustedException(String message) {
            super(message);
        }
    }
}
