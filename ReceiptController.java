package org.example.controller;

import org.example.dto.ReceiptDTO;
import org.example.service.GeoLocationService;
import org.example.service.OCRService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.UUID;

@RestController
@RequestMapping("/api/receipt")
public class ReceiptController {

    @Autowired
    private OCRService ocrService;

    @Autowired
    private GeoLocationService geoLocationService;

    // Thread-safe in-memory store for receipts (clears when app restarts)
    private static final List<ReceiptDTO> STORE = new CopyOnWriteArrayList<>();

    @PostMapping("/upload")
    public ResponseEntity<?> uploadReceipt(@RequestParam("file") MultipartFile file) {
        try {
            // 1) OCR
            String extractedText = ocrService.extractText(file);

            // 2) address candidates
            List<String> candidates = ocrService.findAddressCandidates(extractedText);

            // 3) geocode first valid candidate(s)
            Map<String, Object> geoResult = geoLocationService.findCoordinatesFromCandidates(candidates, extractedText);

            // 4) Assemble DTO
            String id = UUID.randomUUID().toString();
            Double lat = null, lng = null;
            String formatted = null;
            String detected = candidates.isEmpty() ? "" : candidates.get(0);

            if (geoResult != null && geoResult.containsKey("lat")) {
                lat = ((Number) geoResult.get("lat")).doubleValue();
                lng = ((Number) geoResult.get("lng")).doubleValue();
                formatted = (String) geoResult.getOrDefault("formatted", null);
                if (formatted != null && !formatted.isBlank()) {
                    detected = formatted;
                }
            }

            ReceiptDTO dto = new ReceiptDTO(id, extractedText, detected, lat, lng, formatted);
            STORE.add(dto);

            // Return the DTO directly (simplifies the front-end redirect)
            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Fetch all stored receipts (still available if you need it)
    @GetMapping("/all")
    public ResponseEntity<List<ReceiptDTO>> getAllReceipts() {
        return ResponseEntity.ok(new ArrayList<>(STORE)); // return copy
    }

    // NEW: Fetch a single receipt by id (map.html uses this)
    @GetMapping("/{id}")
    public ResponseEntity<?> getReceiptById(@PathVariable String id) {
        return STORE.stream()
                .filter(r -> Objects.equals(r.getId(), id))
                .findFirst()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "Not found")));
    }

    // Clear (for testing)
    @DeleteMapping("/clear")
    public ResponseEntity<?> clearStore() {
        STORE.clear();
        return ResponseEntity.ok(Map.of("cleared", true));
    }
}