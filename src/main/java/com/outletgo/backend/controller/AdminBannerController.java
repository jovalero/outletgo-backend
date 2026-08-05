package com.outletgo.backend.controller;

import com.outletgo.backend.entity.Banner;
import com.outletgo.backend.service.BannerService;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/admin/banners", "/admin/banners"})
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminBannerController {

    private final BannerService bannerService;

    @GetMapping({"", "/"})
    public ResponseEntity<?> getBanners(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        try {
            Page<Banner> bannerPage = bannerService.getBanners(page, size);
            Pageable pageable = bannerPage.getPageable();
            List<AdminBannerResponse> mapped = bannerPage.getContent().stream()
                    .map(this::mapToAdminBannerResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(new PageImpl<>(mapped, pageable, bannerPage.getTotalElements()));
        } catch (Exception e) {
            log.error("Error fetching banners: ", e);
            return ResponseEntity.ok(new PageImpl<>(new ArrayList<>()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createBanner(@RequestBody CreateBannerRequest req) {
        log.info("=== CREATE BANNER REQUEST ===");
        log.info("title: {}", req.getTitle());
        log.info("description: {}", req.getDescription());
        log.info("imageUrl length: {}", req.getImageUrl() != null ? req.getImageUrl().length() : "NULL");
        log.info("imageUrl starts with: {}", req.getImageUrl() != null && req.getImageUrl().length() > 50 ? req.getImageUrl().substring(0, 50) : req.getImageUrl());
        log.info("type: {}", req.getType());
        log.info("startDate: {}", req.getStartDate());
        log.info("endDate: {}", req.getEndDate());
        log.info("storeIds: {}", req.getStoreIds());
        log.info("productIds: {}", req.getProductIds());
        log.info("============================");

        try {
            if (req.getTitle() == null || req.getTitle().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error: el campo 'title' es requerido.");
            }
            if (req.getImageUrl() == null || req.getImageUrl().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error: el campo 'imageUrl' es requerido.");
            }

            Banner banner = Banner.builder()
                    .title(req.getTitle())
                    .description(req.getDescription())
                    .imageUrl(req.getImageUrl())
                    .type(req.getType() != null ? req.getType() : "CAMPAIGN")
                    .status("ACTIVE")
                    .startDate(req.getStartDate())
                    .endDate(req.getEndDate())
                    .badgeText(req.getBadgeText())
                    .build();

            Banner created = bannerService.createBanner(banner);
            return ResponseEntity.status(HttpStatus.CREATED).body(mapToAdminBannerResponse(created));
        } catch (Exception e) {
            log.error("Error creating banner - Exception type: {}", e.getClass().getName());
            log.error("Error creating banner - Message: {}", e.getMessage());
            if (e.getCause() != null) {
                log.error("Error creating banner - Cause: {}", e.getCause().getMessage());
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error al crear banner: [" + e.getClass().getSimpleName() + "] " + e.getMessage()
                            + (e.getCause() != null ? " | Causa: " + e.getCause().getMessage() : ""));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBannerById(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            return bannerService.getBannerById(uuid)
                    .map(b -> ResponseEntity.ok(mapToAdminBannerResponse(b)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching banner by id {}: ", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBanner(@PathVariable String id, @RequestBody CreateBannerRequest req) {
        try {
            UUID uuid = UUID.fromString(id);
            Banner updateData = Banner.builder()
                    .title(req.getTitle())
                    .description(req.getDescription())
                    .imageUrl(req.getImageUrl())
                    .type(req.getType())
                    .startDate(req.getStartDate())
                    .endDate(req.getEndDate())
                    .badgeText(req.getBadgeText())
                    .build();

            Banner updated = bannerService.updateBanner(uuid, updateData);
            if (updated != null) {
                return ResponseEntity.ok(mapToAdminBannerResponse(updated));
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating banner id {}: ", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error al actualizar el banner: " + e.getMessage());
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            UUID uuid = UUID.fromString(id);
            String status = body.getOrDefault("status", "ACTIVE");
            Banner updated = bannerService.updateBannerStatus(uuid, status);
            return ResponseEntity.ok(mapToAdminBannerResponse(updated));
        } catch (Exception e) {
            log.error("Error updating banner status for id {}: ", id, e);
            return ResponseEntity.ok().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBanner(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            bannerService.deleteBanner(uuid);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting banner for id {}: ", id, e);
            return ResponseEntity.noContent().build();
        }
    }

    private AdminBannerResponse mapToAdminBannerResponse(Banner b) {
        return AdminBannerResponse.builder()
                .id(b.getId())
                .title(b.getTitle())
                .description(b.getDescription())
                .imageUrl(b.getImageUrl())
                .type(b.getType())
                .status(b.getStatus())
                .startDate(b.getStartDate())
                .endDate(b.getEndDate())
                .badgeText(b.getBadgeText())
                .createdAt(b.getCreatedAt())
                .stores(new ArrayList<>())
                .products(new ArrayList<>())
                .build();
    }

    /** DTO de entrada para crear banners — desacoplado de la entidad JPA */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateBannerRequest {
        private String title;
        private String description;
        private String imageUrl;
        private String type;
        private String badgeText;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        private LocalDateTime startDate;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        private LocalDateTime endDate;
        // storeIds y productIds se ignoran por ahora (relaciones a implementar en siguiente iteración)
        private List<String> storeIds;
        private List<String> productIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminBannerResponse {
        private UUID id;
        private String title;
        private String description;
        private String imageUrl;
        private String type;
        private String status;
        private String badgeText;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private LocalDateTime createdAt;
        @Builder.Default
        private List<Object> stores = new ArrayList<>();
        @Builder.Default
        private List<Object> products = new ArrayList<>();
    }
}
