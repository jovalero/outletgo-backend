package com.outletgo.backend.controller;

import com.outletgo.backend.config.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@CrossOrigin
@RequestMapping("/api/uploads")
public class UploadController {

    private final Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
    private final JwtUtil jwtUtil;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    public UploadController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize upload directory", e);
        }
    }

    @PostMapping("/product-image")
    public ResponseEntity<Map<String, String>> uploadProductImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "stagingSessionId", required = false) String stagingSessionId,
            HttpServletRequest request) {

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo está vacío");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            
            String uniqueName = UUID.randomUUID().toString() + extension;
            byte[] fileBytes = file.getBytes();
            String contentType = file.getContentType();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "image/jpeg"; // fallback
            }

            String fileUrl = null;

            // 1. Try to upload to Supabase Storage persistently
            try {
                String token = jwtUtil.generateServiceRoleToken();
                fileUrl = uploadToSupabase(uniqueName, fileBytes, contentType, token);
                System.out.println("Persistent upload succeeded: " + fileUrl);
            } catch (Exception e) {
                System.err.println("WARNING: Persistent Supabase upload failed, falling back to local storage. Error: " + e.getMessage());
            }

            // 2. Fallback to local ephemeral storage if Supabase failed or was skipped
            if (fileUrl == null) {
                Path targetLocation = this.uploadDir.resolve(uniqueName);
                Files.write(targetLocation, fileBytes);

                String contextPath = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
                String proto = request.getHeader("X-Forwarded-Proto");
                if ("https".equalsIgnoreCase(proto) && contextPath.startsWith("http://")) {
                    contextPath = contextPath.replace("http://", "https://");
                }
                fileUrl = contextPath + "/uploads/" + uniqueName;
            }

            Map<String, String> response = new HashMap<>();
            response.put("url", fileUrl);
            return ResponseEntity.ok(response);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el archivo", ex);
        }
    }

    private String uploadToSupabase(String fileName, byte[] bytes, String contentType, String token) throws Exception {
        String url = "https://mlsqofdvdjcpjdasrgdk.supabase.co/storage/v1/object/product-images/" + fileName;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("apikey", token)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
                
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            return "https://mlsqofdvdjcpjdasrgdk.supabase.co/storage/v1/object/public/product-images/" + fileName;
        } else {
            throw new RuntimeException("Supabase upload failed with status " + response.statusCode() + ": " + response.body());
        }
    }
}
