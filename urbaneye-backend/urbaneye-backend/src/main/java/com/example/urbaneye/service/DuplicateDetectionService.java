package com.example.urbaneye.service;

import com.example.urbaneye.model.Issue;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DuplicateDetectionService {

    @Value("${featherless.apiKey}")
    private String apiKey;

    @Value("${featherless.baseUrl:https://api.featherless.ai/v1}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Models requested by user
    private final List<String> models = List.of(
            "deepseek-ai/DeepSeek-V4-Flash",
            "deepseek-ai/DeepSeek-R1-Distill-Qwen-32B",
            "deepseek-ai/DeepSeek-V3.2"
    );

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DuplicateCheckResult {
        private boolean duplicate;
        private String matchedIssueId;
        private double confidence;
        private String reason;

        public boolean isDuplicate() {
            return duplicate;
        }

        public void setDuplicate(boolean duplicate) {
            this.duplicate = duplicate;
        }

        public String getMatchedIssueId() {
            return matchedIssueId;
        }

        public void setMatchedIssueId(String matchedIssueId) {
            this.matchedIssueId = matchedIssueId;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    /**
     * Checks if the new complaint matches any of the nearby complaints using the LLM.
     */
    public DuplicateCheckResult checkForDuplicate(String description, double latitude, double longitude, List<Issue> nearbyIssues) {
        if (nearbyIssues == null || nearbyIssues.isEmpty()) {
            DuplicateCheckResult result = new DuplicateCheckResult();
            result.setDuplicate(false);
            result.setReason("No nearby complaints found.");
            return result;
        }

        // Construct prompt
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are a civic operations duplicate detector. Determine if a new complaint describes the EXACT same physical issue/problem as one of the nearby complaints.\n\n");
        promptBuilder.append("New Complaint:\n");
        promptBuilder.append("Description: ").append(description).append("\n");
        promptBuilder.append("Location: Latitude ").append(latitude).append(", Longitude ").append(longitude).append("\n\n");
        promptBuilder.append("Nearby Complaints List:\n");

        for (Issue issue : nearbyIssues) {
            promptBuilder.append("- ID: ").append(issue.getId()).append("\n");
            promptBuilder.append("  Title: ").append(issue.getTitle()).append("\n");
            promptBuilder.append("  Description: ").append(issue.getDescription()).append("\n");
            promptBuilder.append("  Location: Latitude ").append(issue.getLatitude()).append(", Longitude ").append(issue.getLongitude()).append("\n");
            promptBuilder.append("  Status: ").append(issue.getStatus()).append("\n\n");
        }

        promptBuilder.append("Respond ONLY with a raw JSON object (no markdown, no backticks, no chat prefix/suffix) in the following format:\n");
        promptBuilder.append("{\n");
        promptBuilder.append("  \"duplicate\": true or false,\n");
        promptBuilder.append("  \"matchedIssueId\": \"the exact ID of the matching nearby complaint (or null if duplicate is false)\",\n");
        promptBuilder.append("  \"confidence\": a number between 0 and 100. If duplicate is true, this must be 85 or higher,\n");
        promptBuilder.append("  \"reason\": \"brief explanation of why this is or is not a duplicate\"\n");
        promptBuilder.append("}\n");

        String prompt = promptBuilder.toString();

        // Run through model cascade
        for (int i = 0; i < models.size(); i++) {
            String modelName = models.get(i);
            boolean isLast = (i == models.size() - 1);

            try {
                return callModel(modelName, prompt);
            } catch (CapacityExhaustedException e) {
                if (isLast) {
                    System.err.println("[DuplicateCheck] All models exhausted. Skipping duplicate check.");
                    DuplicateCheckResult fallback = new DuplicateCheckResult();
                    fallback.setDuplicate(false);
                    fallback.setReason("AI Duplicate Check unavailable (capacity exhausted).");
                    return fallback;
                }
                System.err.println("[DuplicateCheck] Model " + modelName + " is at capacity. Trying next model.");
            } catch (Exception e) {
                System.err.println("[DuplicateCheck] Unrecoverable error on model " + modelName + ": " + e.getMessage());
                e.printStackTrace();
                DuplicateCheckResult errorResult = new DuplicateCheckResult();
                errorResult.setDuplicate(false);
                errorResult.setReason("AI Duplicate Check failed: " + e.getMessage());
                return errorResult;
            }
        }

        DuplicateCheckResult finalFallback = new DuplicateCheckResult();
        finalFallback.setDuplicate(false);
        finalFallback.setReason("AI Duplicate Check unavailable.");
        return finalFallback;
    }

    private DuplicateCheckResult callModel(String model, String prompt) {
        Map<String, Object> message = Map.of(
                "role", "user",
                "content", prompt
        );
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(message),
                "temperature", 0.1
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String fullUrl = apiUrl + "/chat/completions";
        System.out.println("[DuplicateCheck] Querying DeepSeek model: " + model);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl, HttpMethod.POST, entity, String.class);

            return parseResponse(response.getBody());

        } catch (HttpStatusCodeException e) {
            int code = e.getStatusCode().value();
            String body = e.getResponseBodyAsString();
            System.err.println("[DuplicateCheck] HTTP " + code + " from model " + model + ": " + body);

            if (code == 429 || code == 503) {
                throw new CapacityExhaustedException("Model " + model + " is at capacity (" + code + ")");
            }
            throw new RuntimeException("Featherless REST call failed (" + e.getStatusCode() + "): " + body, e);
        }
    }

    private DuplicateCheckResult parseResponse(String responseBody) {
        String rawText = "";
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            rawText = root
                    .path("choices").path(0)
                    .path("message").path("content")
                    .asText("")
                    .trim();

            System.out.println("[DuplicateCheck] Raw LLM content: " + rawText);

            if (rawText.isEmpty()) {
                throw new RuntimeException("Empty response content from LLM");
            }

            // Strip <think>...</think> blocks
            rawText = rawText.replaceAll("(?s)<think>.*?</think>", "").trim();

            // Strip markdown fences
            if (rawText.startsWith("```")) {
                rawText = rawText.replaceAll("(?s)```[a-z]*\\n?", "").replace("```", "").trim();
            }

            // If it starts with `"duplicate"` or similar due to omitted opening brace, prepend it
            if (!rawText.startsWith("{") && (rawText.contains("\"duplicate\"") || rawText.contains("duplicate"))) {
                rawText = "{" + rawText;
            }

            // Try to extract first JSON object
            int start = rawText.indexOf('{');
            int end = rawText.indexOf('}', start);
            if (start >= 0 && end > start) {
                rawText = rawText.substring(start, end + 1);
            }

            System.out.println("[DuplicateCheck] Cleaned LLM content: " + rawText);

            return objectMapper.readValue(rawText, DuplicateCheckResult.class);
        } catch (Exception e) {
            System.err.println("[DuplicateCheck] Jackson parsing failed: " + e.getMessage() + "\nRaw text: " + rawText);
            
            // Regex Fallback
            try {
                System.out.println("[DuplicateCheck] Attempting regex fallback parsing...");
                DuplicateCheckResult result = new DuplicateCheckResult();
                
                // duplicate
                java.util.regex.Matcher dupMatcher = java.util.regex.Pattern.compile("\"duplicate\"\\s*:\\s*(true|false)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(rawText);
                if (dupMatcher.find()) {
                    result.setDuplicate(Boolean.parseBoolean(dupMatcher.group(1)));
                } else {
                    result.setDuplicate(false);
                }
                
                // matchedIssueId
                java.util.regex.Matcher idMatcher = java.util.regex.Pattern.compile("\"matchedIssueId\"\\s*:\\s*(?:\"([^\"]*)\"|null)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(rawText);
                if (idMatcher.find() && idMatcher.group(1) != null) {
                    result.setMatchedIssueId(idMatcher.group(1));
                } else {
                    result.setMatchedIssueId(null);
                }
                
                // confidence
                java.util.regex.Matcher confMatcher = java.util.regex.Pattern.compile("\"confidence\"\\s*:\\s*([0-9.-]+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(rawText);
                if (confMatcher.find()) {
                    result.setConfidence(Double.parseDouble(confMatcher.group(1)));
                } else {
                    result.setConfidence(result.isDuplicate() ? 85.0 : 0.0);
                }
                
                // reason
                java.util.regex.Matcher reasonMatcher = java.util.regex.Pattern.compile("\"reason\"\\s*:\\s*\"([^\"]*)\"", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(rawText);
                if (reasonMatcher.find()) {
                    result.setReason(reasonMatcher.group(1));
                } else {
                    result.setReason("Extracted via regex fallback.");
                }
                
                System.out.println("[DuplicateCheck] Regex parsed successfully: duplicate=" + result.isDuplicate() + ", matchedIssueId=" + result.getMatchedIssueId() + ", confidence=" + result.getConfidence());
                return result;
            } catch (Exception ex) {
                System.err.println("[DuplicateCheck] Regex parsing failed as well: " + ex.getMessage());
                throw new RuntimeException("Failed to parse DeepSeek response: " + e.getMessage(), e);
            }
        }
    }

    private static class CapacityExhaustedException extends RuntimeException {
        CapacityExhaustedException(String message) {
            super(message);
        }
    }
}
