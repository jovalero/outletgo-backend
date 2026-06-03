package com.outletgo.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@CrossOrigin
@RequestMapping("/api/uploads")
public class UploadController {

    private final Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();

    public UploadController() {
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
            Path targetLocation = this.uploadDir.resolve(uniqueName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String contextPath = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
            String proto = request.getHeader("X-Forwarded-Proto");
            if ("https".equalsIgnoreCase(proto) && contextPath.startsWith("http://")) {
                contextPath = contextPath.replace("http://", "https://");
            }
            
            String fileDownloadUri = contextPath + "/uploads/" + uniqueName;

            Map<String, String> response = new HashMap<>();
            response.put("url", fileDownloadUri);
            return ResponseEntity.ok(response);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el archivo", ex);
        }
    }
}
