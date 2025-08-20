package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeoLocationService {

    @Value("${opencage.api.key}")
    private String apiKey;

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> findCoordinatesFromCandidates(List<String> candidates, String rawOcrText) {
        for (String candidate : candidates) {
            try {
                String cleaned = sanitizeForGeocode(candidate);
                if (cleaned.isBlank()) continue;
                Map<String, Object> coords = geocodeOnce(cleaned);
                if (coords != null && coords.containsKey("lat")) {
                    coords.put("usedQuery", cleaned);
                    return coords;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return Map.of("error", "No coordinates found");
    }

    private String sanitizeForGeocode(String s) {
        String cleaned = s.replaceAll("[^\\p{Print}]", " ");
        cleaned = cleaned.replaceAll("[^A-Za-z0-9,\\.\\-\\s]", " ");
        cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();
        if (cleaned.length() < 5) return "";
        return cleaned;
    }

    private Map<String, Object> geocodeOnce(String address) throws Exception {
        String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
        String url = "https://api.opencagedata.com/geocode/v1/json?q=" + encoded + "&key=" + apiKey + "&limit=1";
        String json = rest.getForObject(url, String.class);
        if (json == null) return null;
        JsonNode root = mapper.readTree(json);
        JsonNode results = root.path("results");
        if (results.isArray() && results.size() > 0) {
            JsonNode geometry = results.get(0).path("geometry");
            if (!geometry.isMissingNode()) {
                Map<String, Object> coords = new HashMap<>();
                coords.put("lat", geometry.path("lat").asDouble());
                coords.put("lng", geometry.path("lng").asDouble());
                coords.put("formatted", results.get(0).path("formatted").asText(""));
                return coords;
            }
        }
        return null;
    }
}