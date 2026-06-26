package com.example.urbaneye.controller;

import com.example.urbaneye.service.TranslationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public/translate")
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> translate(@RequestBody Map<String, String> request) {
        String title = request.getOrDefault("title", "");
        String description = request.getOrDefault("description", "");
        String targetLanguage = request.getOrDefault("targetLanguage", "English");
        
        Map<String, String> result = translationService.translate(title, description, targetLanguage);
        return ResponseEntity.ok(result);
    }
}
