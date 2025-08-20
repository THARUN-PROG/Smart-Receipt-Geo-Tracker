package org.example.dto;

public class ReceiptDTO {
    private String id;
    private String extractedText;
    private String detectedAddress;
    private Double lat;
    private Double lng;
    private String formatted; // from geocoder (optional)

    public ReceiptDTO() {}

    public ReceiptDTO(String id, String extractedText, String detectedAddress, Double lat, Double lng, String formatted) {
        this.id = id;
        this.extractedText = extractedText;
        this.detectedAddress = detectedAddress;
        this.lat = lat;
        this.lng = lng;
        this.formatted = formatted;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }

    public String getDetectedAddress() { return detectedAddress; }
    public void setDetectedAddress(String detectedAddress) { this.detectedAddress = detectedAddress; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String getFormatted() { return formatted; }
    public void setFormatted(String formatted) { this.formatted = formatted; }
}