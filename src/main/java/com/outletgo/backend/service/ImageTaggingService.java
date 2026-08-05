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
     * Extrae etiquetas de IA a partir de una lista de URLs de imágenes y contexto del producto.
     */
    public Set<String> extractTagsFromUrls(List<String> imageUrls, String productName, String description) {
        Set<String> detected = new LinkedHashSet<>();
        if (imageUrls != null) {
            for (String url : imageUrls) {
                if (url == null || url.trim().isEmpty()) continue;
                Set<String> tagsForUrl = processImageUrl(url.trim());
                detected.addAll(tagsForUrl);
            }
        }

        // Fallback: Si no se obtuvieron etiquetas de la imagen (ej. sin API key o URL sin keywords), extraer del nombre y descripcion
        if (detected.isEmpty()) {
            if (productName != null && !productName.trim().isEmpty()) {
                detected.addAll(extractKeywordsFromText(productName));
            }
            if (description != null && !description.trim().isEmpty()) {
                detected.addAll(extractKeywordsFromText(description));
            }
        }

        // Fallback garantizado por defecto
        if (detected.isEmpty()) {
            detected.add("outlet");
            detected.add("moda");
        }

        return detected;
    }

    public Set<String> extractTagsFromUrls(List<String> imageUrls) {
        return extractTagsFromUrls(imageUrls, null, null);
    }

    /**
     * Extrae etiquetas de IA a partir de los bytes de una imagen subida por multipart (ej. desde la app mobile).
     */
    public Set<String> extractTagsFromImageBytes(byte[] imageBytes, String filename) {
        Set<String> detected = new LinkedHashSet<>();
        if (imageBytes == null || imageBytes.length == 0) {
            log.warn("[IMAGE TAGGING] imageBytes está vacío o nulo.");
            return detected;
        }

        String apiKey = getResolvedApiKey();
        if (apiKey != null) {
            log.info("[IMAGE TAGGING] API Key resuelta (longitud: {} chars, inicio: '{}...'). Enviando {} bytes a Google Vision.",
                    apiKey.length(), apiKey.length() > 6 ? apiKey.substring(0, 6) : apiKey, imageBytes.length);
            try {
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                detected.addAll(callGoogleVisionApiBase64(base64Image, apiKey));
                log.info("[IMAGE TAGGING] Etiquetas obtenidas de Google Vision API: {}", detected);
            } catch (Exception e) {
                log.error("[IMAGE TAGGING] Excepción al invocar Google Vision API: {}", e.getMessage(), e);
            }
        } else {
            log.warn("[IMAGE TAGGING] ALERTA CRÍTICA: No se encontró GOOGLE_VISION_API_KEY en variables de entorno ni application.properties.");
        }

        // Fallback autónomo por nombre de archivo
        if (detected.isEmpty() && filename != null) {
            Set<String> filenameTags = extractKeywordsFromText(filename);
            if (!filenameTags.isEmpty()) {
                log.info("[IMAGE TAGGING] Se aplicó fallback por nombre de archivo ('{}'): {}", filename, filenameTags);
                detected.addAll(filenameTags);
            }
        }

        if (detected.isEmpty()) {
            log.warn("[IMAGE TAGGING] CAÍDA EN FALLBACK DEFAULT: No se obtuvieron etiquetas de Google Vision ni del nombre del archivo. Asignando ['outlet', 'moda'].");
            detected.add("outlet");
            detected.add("moda");
        }

        return detected;
    }

    private Set<String> processImageUrl(String url) {
        Set<String> tags = new LinkedHashSet<>();

        String apiKey = getResolvedApiKey();
        if (apiKey != null) {
            log.info("[IMAGE TAGGING] Procesando imagen por URL para Google Vision: {}", url);
            try {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .GET()
                                .timeout(Duration.ofSeconds(5))
                                .build();
                        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                        if (response.statusCode() == 200 && response.body() != null && response.body().length > 0) {
                            String base64Image = Base64.getEncoder().encodeToString(response.body());
                            Set<String> visionTags = callGoogleVisionApiBase64(base64Image, apiKey);
                            log.info("[IMAGE TAGGING] Google Vision detectó etiquetas desde los bytes de la URL: {}", visionTags);
                            tags.addAll(visionTags);
                        } else {
                            tags.addAll(callGoogleVisionApiUrl(url, apiKey));
                        }
                    } catch (Exception ex) {
                        log.warn("[IMAGE TAGGING] Error descargando bytes de la URL, intentando imageUri directo: {}", ex.getMessage());
                        tags.addAll(callGoogleVisionApiUrl(url, apiKey));
                    }
                } else if (url.startsWith("data:image")) {
                    String base64Data = url.substring(url.indexOf(",") + 1);
                    tags.addAll(callGoogleVisionApiBase64(base64Data, apiKey));
                } else {
                    tags.addAll(callGoogleVisionApiUrl(url, apiKey));
                }
            } catch (Exception e) {
                log.warn("Error consultando Google Vision API para URL {}: {}", url, e.getMessage());
            }
        } else {
            log.warn("[IMAGE TAGGING] No se encontró GOOGLE_VISION_API_KEY en variables de entorno de Render/servidor.");
        }

        // Fallback o extraccion complementaria basada en el nombre de la imagen / URL
        if (tags.isEmpty()) {
            tags.addAll(extractKeywordsFromText(url));
        }

        return tags;
    }

    private Set<String> callGoogleVisionApiUrl(String imageUrl, String apiKey) throws Exception {
        Map<String, Object> imageMap = Map.of("source", Map.of("imageUri", imageUrl));
        Map<String, Object> featureLabel = Map.of("type", "LABEL_DETECTION", "maxResults", 8);
        Map<String, Object> featureObject = Map.of("type", "OBJECT_LOCALIZATION", "maxResults", 5);
        Map<String, Object> requestMap = Map.of("image", imageMap, "features", List.of(featureLabel, featureObject));
        Map<String, Object> payloadMap = Map.of("requests", List.of(requestMap));

        String jsonPayload = objectMapper.writeValueAsString(payloadMap);
        return executeVisionRequest(jsonPayload, apiKey);
    }

    private Set<String> callGoogleVisionApiBase64(String base64Content, String apiKey) throws Exception {
        String cleanBase64 = base64Content != null ? base64Content.replaceAll("\\s+", "") : "";
        Map<String, Object> imageMap = Map.of("content", cleanBase64);
        Map<String, Object> featureLabel = Map.of("type", "LABEL_DETECTION", "maxResults", 8);
        Map<String, Object> featureObject = Map.of("type", "OBJECT_LOCALIZATION", "maxResults", 5);
        Map<String, Object> requestMap = Map.of("image", imageMap, "features", List.of(featureLabel, featureObject));
        Map<String, Object> payloadMap = Map.of("requests", List.of(requestMap));

        String jsonPayload = objectMapper.writeValueAsString(payloadMap);
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

        log.info("[GOOGLE VISION API HTTP] Status Code: {}", response.statusCode());

        Set<String> tags = new LinkedHashSet<>();
        if (response.statusCode() == 200) {
            String body = response.body();
            log.info("[GOOGLE VISION API HTTP] Body devuelto (primeros 400 chars): {}",
                    body != null && body.length() > 400 ? body.substring(0, 400) + "..." : body);

            JsonNode root = objectMapper.readTree(body);
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
            log.error("[GOOGLE VISION API HTTP ERROR] Google Vision devolvió status {}: {}", response.statusCode(), response.body());
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

