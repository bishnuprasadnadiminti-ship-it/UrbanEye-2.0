package com.example.urbaneye.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class GeocodingService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/reverse?format=json&lat={lat}&lon={lon}";

    @Cacheable(value = "geocoding", key = "#latitude + '-' + #longitude")
    public String resolveAddress(double latitude, double longitude) {
        try {
            // Required by OpenStreetMap Nominatim guidelines
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "UrbanEye-Civic-App");
            HttpEntity<String> entity = new HttpEntity<>("parameters", headers);

            var response = restTemplate.exchange(
                    NOMINATIM_URL, HttpMethod.GET, entity, Map.class, latitude, longitude);

            if (response.getBody() != null && response.getBody().containsKey("display_name")) {
                return (String) response.getBody().get("display_name");
            }
        } catch (Exception e) {
            System.err.println("Geocoding API failed: " + e.getMessage()); // Will be caught and cached as unknown
        }
        return "Unknown Location";
    }
}
