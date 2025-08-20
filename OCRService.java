package org.example.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.example.util.TesseractConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OCRService {

    @Value("${geoapify.api.key}")
    private String geoapifyApiKey;

    public String extractText(MultipartFile file) throws IOException, TesseractException {
        File preprocessed = preprocessAndSaveTemp(file);
        ITesseract tesseract = TesseractConfig.getTesseractInstance();
        try {
            String text = tesseract.doOCR(preprocessed);
            return (text == null) ? "" : text;
        } finally {
            preprocessed.delete();
        }
    }

    private File preprocessAndSaveTemp(MultipartFile file) throws IOException {
        BufferedImage in = ImageIO.read(file.getInputStream());
        if (in == null) {
            throw new IOException("Could not read image from uploaded file");
        }

        BufferedImage gray = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(in, 0, 0, null);
        g.dispose();

        int minDim = Math.min(gray.getWidth(), gray.getHeight());
        if (minDim < 600) {
            int newW = gray.getWidth() * 2;
            int newH = gray.getHeight() * 2;
            Image tmp = gray.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
            BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g2 = scaled.createGraphics();
            g2.drawImage(tmp, 0, 0, null);
            g2.dispose();
            gray = scaled;
        }

        BufferedImage bin = new BufferedImage(gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_BYTE_BINARY);

        long sum = 0;
        int w = gray.getWidth(), h = gray.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = gray.getRGB(x, y) & 0xFF;
                sum += rgb;
            }
        }
        int avg = (int) (sum / (w * h));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = gray.getRGB(x, y) & 0xFF;
                int v = (rgb > avg) ? 0xFFFFFF : 0x000000;
                bin.setRGB(x, y, v);
            }
        }

        File temp = File.createTempFile("preproc-", ".png");
        ImageIO.write(bin, "png", temp);
        return temp;
    }

    public List<String> findAddressCandidates(String ocrText) {
        if (ocrText == null) ocrText = "";
        List<String> lines = Arrays.stream(ocrText.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        List<String> candidates = new ArrayList<>();

        for (String line : lines) {
            if (line.toLowerCase().contains("address") || line.toLowerCase().contains("delivery address:")|| line.toLowerCase().contains("addr")) {
                StringBuilder sb = new StringBuilder(line.replaceAll("(?i)address[:\\s]*", "").trim());
                int idx = lines.indexOf(line);
                if (idx + 1 < lines.size()) {
                    String next = lines.get(idx + 1);
                    if (!next.matches(".\\d{1,4}.") || next.length() < 20) {
                        sb.append(", ").append(next);
                    }
                }
                if (sb.length() > 3) candidates.add(sb.toString());
            }
        }

        Pattern streetPattern = Pattern.compile(
                "(\\d+\\s+([A-Za-z0-9\\-.,'\\/ ]+?)\\b(?:Street|St\\.|St|Road|Rd\\.|Rd|Avenue|Ave\\.|Ave|Boulevard|Blvd|Lane|Ln|Drive|Dr\\.|Suite|Ste|No\\.|No))",
                Pattern.CASE_INSENSITIVE);
        for (String line : lines) {
            Matcher m = streetPattern.matcher(line);
            if (m.find()) {
                candidates.add(m.group(1).trim());
            }
        }

        Pattern postalPattern = Pattern.compile("(\\d{3,6})");
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = postalPattern.matcher(line);
            if (m.find()) {
                StringBuilder ctx = new StringBuilder(line);
                if (i - 1 >= 0) ctx.insert(0, lines.get(i - 1) + ", ");
                if (i + 1 < lines.size()) ctx.append(", ").append(lines.get(i + 1));
                candidates.add(ctx.toString());
            }
        }

        String[] keywords = new String[]{"city", "town", "village", "country", "postal", "zip", "pincode", "district"};
        for (String line : lines) {
            for (String kw : keywords) {
                if (line.toLowerCase().contains(kw)) {
                    candidates.add(line);
                    break;
                }
            }
        }

        int n = Math.min(4, lines.size());
        if (n > 0) {
            String lastBlock = String.join(", ", lines.subList(Math.max(0, lines.size() - n), lines.size()));
            if (!lastBlock.isBlank()) candidates.add(lastBlock);
        }

        if (!ocrText.isBlank()) candidates.add(ocrText.replaceAll("\\s+", " ").trim());

        List<String> dedup = new ArrayList<>();
        for (String c : candidates) {
            String s = c.trim();
            if (s.length() > 3 && !dedup.contains(s)) dedup.add(s);
            if (dedup.size() >= 10) break;
        }
        return dedup;
    }

    public String validateAddressWithGeoapify(String address) {
        try {
            String encodedAddress = URLEncoder.encode(address, "UTF-8");
            String apiUrl = "https://api.geoapify.com/v1/geocode/search?text=" + encodedAddress +
                    "&format=json&apiKey=" + geoapifyApiKey;

            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("GET");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString(); // Returns full JSON, can be parsed further
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error validating address with Geoapify: " + e.getMessage();
        }
    }
}
