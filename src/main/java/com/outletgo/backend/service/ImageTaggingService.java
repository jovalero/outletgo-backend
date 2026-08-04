package com.outletgo.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class ImageTaggingService {

    @Value("${GOOGLE_VISION_API_KEY:${google.vision.api-key:}}")
    private String visionApiKey;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

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
                .connectTimeout(Duration.ofSeconds(6))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    private String getResolvedApiKey() {
        if (visionApiKey != null && !visionApiKey.trim().isEmpty()) {
            return visionApiKey.trim();
        }
        String envKey = System.getenv("GOOGLE_VISION_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        return null;
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

        String apiKey = getResolvedApiKey();
        if (apiKey != null) {
            try {
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                detected.addAll(callGoogleVisionApiBase64(base64Image, apiKey));
            } catch (Exception e) {
                log.warn("Fallo la llamada a Google Vision API por bytes: {}", e.getMessage());
            }
        } else {
            log.warn("No se encontro GOOGLE_VISION_API_KEY en variables de entorno ni application.properties.");
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

        String apiKey = getResolvedApiKey();
        if (apiKey != null) {
            try {
                tags.addAll(callGoogleVisionApiUrl(url, apiKey));
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

    private Set<String> callGoogleVisionApiUrl(String imageUrl, String apiKey) throws Exception {
        String jsonPayload = String.format(
                "{\"requests\":[{\"image\":{\"source\":{\"imageUri\":\"%s\"}},\"features\":[{\"type\":\"LABEL_DETECTION\",\"maxResults\":8},{\"type\":\"OBJECT_LOCALIZATION\",\"maxResults\":5}]}]}",
                imageUrl
        );
        return executeVisionRequest(jsonPayload, apiKey);
    }

    private Set<String> callGoogleVisionApiBase64(String base64Content, String apiKey) throws Exception {
        String jsonPayload = String.format(
                "{\"requests\":[{\"image\":{\"content\":\"%s\"}},\"features\":[{\"type\":\"LABEL_DETECTION\",\"maxResults\":8},{\"type\":\"OBJECT_LOCALIZATION\",\"maxResults\":5}]}]}",
                base64Content
        );
        return executeVisionRequest(jsonPayload, apiKey);
    }

    private Set<String> executeVisionRequest(String jsonPayload, String apiKey) throws Exception {
        String endpoint = "https://vision.googleapis.com/v1/images:annotate?key=" + apiKey;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(6))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Set<String> tags = new LinkedHashSet<>();
        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode responses = root.path("responses");
            if (responses.isArray() && responses.size() > 0) {
                JsonNode firstResp = responses.get(0);

                // 1. Label Annotations (description)
                JsonNode labels = firstResp.path("labelAnnotations");
                if (labels.isArray()) {
                    for (JsonNode l : labels) {
                        String desc = l.path("description").asText();
                        if (desc != null && !desc.isBlank()) {
                            addTag(tags, desc);
                        }
                    }
                }

                // 2. Object Localizations (name)
                JsonNode objects = firstResp.path("localizedObjectAnnotations");
                if (objects.isArray()) {
                    for (JsonNode o : objects) {
                        String name = o.path("name").asText();
                        if (name != null && !name.isBlank()) {
                            addTag(tags, name);
                        }
                    }
                }
            }
        } else {
            log.warn("Google Vision API devolvio status HTTP {}: {}", response.statusCode(), response.body());
        }
        return tags;
    }

    private void addTag(Set<String> tags, String rawWord) {
        String lower = rawWord.toLowerCase().trim();
        String translated = TRANSLATION_MAP.getOrDefault(lower, lower);
        if (translated.length() >= 3 && !translated.equalsIgnoreCase("font") && !translated.equalsIgnoreCase("logo")) {
            tags.add(translated);
        }
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

