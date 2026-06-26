package com.example.urbaneye.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class TranslationService {

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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Cacheable(value = "translations", key = "#title.concat('-').concat(#description).concat('-').concat(#targetLanguage)")
    public Map<String, String> translate(String title, String description, String targetLanguage) {
        if (targetLanguage == null || targetLanguage.equalsIgnoreCase("English") || targetLanguage.trim().isEmpty()) {
            return Map.of("title", title, "description", description);
        }

        String prompt = "You are a professional translator. Translate the following JSON object containing issue fields ('title' and 'description') into " + targetLanguage + ". "
                + "Ensure the translation is natural and culturally accurate in " + targetLanguage + ". "
                + "Return ONLY the translated JSON object containing keys 'title' and 'description'. Do not wrap the JSON object in markdown code blocks or add any other text.";

        String inputJson = "";
        try {
            inputJson = objectMapper.writeValueAsString(Map.of("title", title, "description", description));
        } catch (Exception e) {
            return Map.of("title", title, "description", description);
        }

        List<String> models = List.of(primaryModel, secondaryModel, tertiaryModel);
        for (String model : models) {
            try {
                Map<String, Object> message = Map.of(
                        "role", "user",
                        "content", prompt + "\n\nJSON to translate:\n" + inputJson
                );
                Map<String, Object> requestBody = Map.of(
                        "model", model,
                        "messages", List.of(message),
                        "temperature", 0.3
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(apiKey);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<String> response = restTemplate.exchange(
                        apiUrl + "/chat/completions",
                        HttpMethod.POST,
                        entity,
                        String.class
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    String rawText = root.path("choices").path(0).path("message").path("content").asText("").trim();
                    
                    if (rawText.startsWith("```")) {
                        rawText = rawText.replaceAll("(?s)```[a-z]*\\n?", "").replace("```", "").trim();
                    }

                    JsonNode translatedNode = objectMapper.readTree(rawText);
                    String translatedTitle = translatedNode.path("title").asText(title);
                    String translatedDesc = translatedNode.path("description").asText(description);
                    
                    return Map.of("title", translatedTitle, "description", translatedDesc);
                }
            } catch (Exception e) {
                System.err.println("[TranslationService] Error using model " + model + ": " + e.getMessage());
            }
        }

        return Map.of("title", title, "description", description);
    }
}
