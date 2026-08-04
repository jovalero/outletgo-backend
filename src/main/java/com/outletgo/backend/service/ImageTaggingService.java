package com.outletgo.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ImageTaggingService {

    @Value("${google.vision.api-key:}")
    private String visionApiKey;

    private final HttpClient httpClient;

    private static final Map<String, String> TRANSLATION_MAP = Map.ofEntries(
            Map.entry("shoe", "calzado"),
            Map.entry("footwear", "calzado"),
            Map.entry("sneakers", "zapatillas"),
            Map.entry("athletic shoe", "zapatillas"),
            Map.entry("running shoe", "zapatillas"),
            Map.entry("boot", "botas"),
            Map.entry("shirt", "remera"),
            Map.entry("t-shirt", "remera"),
            Map.entry("active shirt", "remera deportiva"),
            Map.entry("sleeve", "manga"),
            Map.entry("jacket", "campera"),
            Map.entry("coat", "campera"),
            Map.entry("outerwear", "abrigos"),
            Map.entry("hoodie", "buzo"),
            Map.entry("sweater", "buzo"),
            Map.entry("trousers", "pantalón"),
            Map.entry("pants", "pantalón"),
            Map.entry("jeans", "jean"),
            Map.entry("denim", "jean"),
            Map.entry("shorts", "shorts"),
            Map.entry("dress", "vestido"),
            Map.entry("skirt", "pollera"),
            Map.entry("sportswear", "deportivo"),
            Map.entry("bag", "bolso"),
            Map.entry("handbag", "cartera"),
            Map.entry("backpack", "mochila"),
            Map.entry("leather", "cuero"),
            Map.entry("cotton", "algodón"),
            Map.entry("black", "negro"),
            Map.entry("white", "blanco"),
            Map.entry("red", "rojo"),
            Map.entry("blue", "azul")
    );

    private static final List<String> FASHION_KEYWORDS = List.of(
            "zapatillas", "calzado", "remera", "campera", "buzo", "pantalón", "jean",
            "shorts", "vestido", "pollera", "botas", "deportivo", "abrigos", "mochila",
            "cartera", "bolso", "cuero", "algodón", "outlet", "urbano", "casual"
    );

    public ImageTaggingService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Extrae etiquetas de IA a partir de una lista de URLs de imágenes.
     */
    public Set<String> extractTagsFromUrls(List<String> imageUrls) {
        Set<String> detected = new LinkedHashSet<>();
        if (imageUrls == null || imageUrls.isEmpty()) {
            return detected;
        }

        for (String url : imageUrls) {
            if (url == null || url.trim().isEmpty()) continue;
            Set<String> tagsForUrl = processImageUrl(url.trim());
            detected.addAll(tagsForUrl);
        }

        return detected;
    }

    /**
     * Extrae etiquetas de IA a partir de los bytes de una imagen subida por multipart (ej. desde la app mobile).
     */
    public Set<String> extractTagsFromImageBytes(byte[] imageBytes, String filename) {
        Set<String> detected = new LinkedHashSet<>();
        if (imageBytes == null || imageBytes.length == 0) {
            return detected;
        }

        if (visionApiKey != null && !visionApiKey.trim().isEmpty()) {
            try {
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                detected.addAll(callGoogleVisionApiBase64(base64Image));
            } catch (Exception e) {
                log.warn("Fallo la llamada a Google Vision API por bytes: {}", e.getMessage());
            }
        }

        // Fallback autonomo por nombre de archivo
        if (detected.isEmpty() && filename != null) {
            detected.addAll(extractKeywordsFromText(filename));
        }

        if (detected.isEmpty()) {
            detected.add("outlet");
            detected.add("moda");
        }

        return detected;
    }

    private Set<String> processImageUrl(String url) {
        Set<String> tags = new LinkedHashSet<>();

        if (visionApiKey != null && !visionApiKey.trim().isEmpty()) {
            try {
                tags.addAll(callGoogleVisionApiUrl(url));
            } catch (Exception e) {
                log.warn("Error consultando Google Vision API para URL {}: {}", url, e.getMessage());
            }
        }

        // Fallback o extraccion complementaria basada en el nombre de la imagen / URL
        if (tags.isEmpty()) {
            tags.addAll(extractKeywordsFromText(url));
        }

        return tags;
    }

    private Set<String> callGoogleVisionApiUrl(String imageUrl) throws Exception {
        String jsonPayload = String.format(
                "{\"requests\":[{\"image\":{\"source\":{\"imageUri\":\"%s\"}},\"features\":[{\"type\":\"LABEL_DETECTION\",\"maxResults\":8},{\"type\":\"OBJECT_LOCALIZATION\",\"maxResults\":5}]}]}",
                imageUrl
        );
        return executeVisionRequest(jsonPayload);
    }

    private Set<String> callGoogleVisionApiBase64(String base64Content) throws Exception {
        String jsonPayload = String.format(
                "{\"requests\":[{\"image\":{\"content\":\"%s\"}},\"features\":[{\"type\":\"LABEL_DETECTION\",\"maxResults\":8},{\"type\":\"OBJECT_LOCALIZATION\",\"maxResults\":5}]}]}",
                base64Content
        );
        return executeVisionRequest(jsonPayload);
    }

    private Set<String> executeVisionRequest(String jsonPayload) throws Exception {
        String endpoint = "https://vision.googleapis.com/v1/images:annotate?key=" + visionApiKey.trim();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(6))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Set<String> tags = new LinkedHashSet<>();
        if (response.statusCode() == 200) {
            String body = response.body();
            Pattern descriptionPattern = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
            Matcher matcher = descriptionPattern.matcher(body);
            while (matcher.find()) {
                String rawWord = matcher.group(1).toLowerCase().trim();
                String translated = TRANSLATION_MAP.getOrDefault(rawWord, rawWord);
                if (translated.length() >= 3 && !translated.equalsIgnoreCase("font") && !translated.equalsIgnoreCase("logo")) {
                    tags.add(translated);
                }
            }
        }
        return tags;
    }

    private Set<String> extractKeywordsFromText(String text) {
        Set<String> found = new LinkedHashSet<>();
        String lower = text.toLowerCase();
        for (String kw : FASHION_KEYWORDS) {
            if (lower.contains(kw)) {
                found.add(kw);
            }
        }
        return found;
    }
}
