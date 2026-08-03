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
    public ResponseEntity<?> createBanner(@RequestBody Banner banner) {
        try {
            Banner created = bannerService.createBanner(banner);
            return ResponseEntity.status(HttpStatus.CREATED).body(mapToAdminBannerResponse(created));
        } catch (Exception e) {
            log.error("Error creating banner: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error al crear el banner: " + e.getMessage());
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
                .createdAt(b.getCreatedAt())
                .stores(new ArrayList<>())
                .products(new ArrayList<>())
                .build();
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
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private LocalDateTime createdAt;
        @Builder.Default
        private List<Object> stores = new ArrayList<>();
        @Builder.Default
        private List<Object> products = new ArrayList<>();
    }
}
