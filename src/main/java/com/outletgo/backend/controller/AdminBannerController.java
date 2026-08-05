package com.outletgo.backend.controller;

import com.outletgo.backend.entity.Banner;
import com.outletgo.backend.entity.Product;
import com.outletgo.backend.entity.Store;
import com.outletgo.backend.repository.ProductRepository;
import com.outletgo.backend.repository.StoreRepository;
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
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/admin/banners", "/admin/banners"})
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminBannerController {

    private final BannerService bannerService;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;

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
        log.info("type: {}", req.getType());
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

            String bannerType = req.getType() != null ? req.getType().trim().toUpperCase() : "CAMPAIGN";

            Set<Store> stores = resolveStores(bannerType, req.getStoreIds());
            Set<Product> products = resolveProducts(bannerType, req.getProductIds());

            Banner banner = Banner.builder()
                    .title(req.getTitle())
                    .description(req.getDescription())
                    .imageUrl(req.getImageUrl())
                    .type(bannerType)
                    .status("ACTIVE")
                    .startDate(req.parseStartDate())
                    .endDate(req.parseEndDate())
                    .badgeText(req.getBadgeText())
                    .stores(stores)
                    .products(products)
                    .build();

            Banner created = bannerService.createBanner(banner);
            return ResponseEntity.status(HttpStatus.CREATED).body(mapToAdminBannerResponse(created));
        } catch (Exception e) {
            log.error("Error creating banner: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error al crear banner: " + e.getMessage());
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
            String bannerType = req.getType() != null ? req.getType().trim().toUpperCase() : "CAMPAIGN";

            Set<Store> stores = resolveStores(bannerType, req.getStoreIds());
            Set<Product> products = resolveProducts(bannerType, req.getProductIds());

            Banner updateData = Banner.builder()
                    .title(req.getTitle())
                    .description(req.getDescription())
                    .imageUrl(req.getImageUrl())
                    .type(bannerType)
                    .startDate(req.parseStartDate())
                    .endDate(req.parseEndDate())
                    .badgeText(req.getBadgeText())
                    .stores(stores)
                    .products(products)
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

    private Set<Store> resolveStores(String type, List<String> storeIds) {
        Set<Store> result = new LinkedHashSet<>();
        if ("PRODUCT".equalsIgnoreCase(type) || storeIds == null || storeIds.isEmpty()) {
            return result;
        }

        List<String> idsToFetch = "STORE".equalsIgnoreCase(type) ? List.of(storeIds.get(0)) : storeIds;
        for (String idStr : idsToFetch) {
            if (idStr == null || idStr.isBlank()) continue;
            try {
                UUID uuid = UUID.fromString(idStr.trim());
                storeRepository.findById(uuid).ifPresent(result::add);
            } catch (Exception ignored) {}
        }
        return result;
    }

    private Set<Product> resolveProducts(String type, List<String> productIds) {
        Set<Product> result = new LinkedHashSet<>();
        if ("STORE".equalsIgnoreCase(type) || productIds == null || productIds.isEmpty()) {
            return result;
        }

        List<String> idsToFetch = "PRODUCT".equalsIgnoreCase(type) ? List.of(productIds.get(0)) : productIds;
        for (String idStr : idsToFetch) {
            if (idStr == null || idStr.isBlank()) continue;
            try {
                UUID uuid = UUID.fromString(idStr.trim());
                productRepository.findById(uuid).ifPresent(result::add);
            } catch (Exception ignored) {}
        }
        return result;
    }

    private AdminBannerResponse mapToAdminBannerResponse(Banner b) {
        List<Map<String, Object>> storeList = b.getStores().stream()
                .map(s -> Map.of(
                        "id", (Object) s.getId().toString(),
                        "businessName", (Object) (s.getBusinessName() != null ? s.getBusinessName() : "")
                ))
                .collect(Collectors.toList());

        List<Map<String, Object>> productList = b.getProducts().stream()
                .map(p -> Map.of(
                        "id", (Object) p.getId().toString(),
                        "name", (Object) (p.getName() != null ? p.getName() : "")
                ))
                .collect(Collectors.toList());

        List<String> storeIds = b.getStores().stream()
                .map(s -> s.getId().toString())
                .collect(Collectors.toList());

        List<String> productIds = b.getProducts().stream()
                .map(p -> p.getId().toString())
                .collect(Collectors.toList());

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
                .targetStoreId(b.getTargetStoreId())
                .targetProductId(b.getTargetProductId())
                .storeIds(storeIds)
                .productIds(productIds)
                .stores(storeList)
                .products(productList)
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateBannerRequest {
        private String title;
        private String description;
        private String imageUrl;
        private String type;
        private String badgeText;
        private String startDate;
        private String endDate;
        private List<String> storeIds;
        private List<String> productIds;

        public LocalDateTime parseStartDate() {
            return parseDateTime(startDate);
        }

        public LocalDateTime parseEndDate() {
            return parseDateTime(endDate);
        }

        private static LocalDateTime parseDateTime(String text) {
            if (text == null || text.isBlank()) return null;
            String clean = text.trim().replace("Z", "");
            if (clean.contains(".")) {
                clean = clean.split("\\.")[0];
            }
            if (clean.length() == 16) {
                clean = clean + ":00";
            }
            try {
                return LocalDateTime.parse(clean);
            } catch (Exception e) {
                try {
                    return LocalDateTime.parse(clean, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (Exception ex) {
                    return null;
                }
            }
        }
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
        private String targetStoreId;
        private String targetProductId;
        @Builder.Default
        private List<String> storeIds = new ArrayList<>();
        @Builder.Default
        private List<String> productIds = new ArrayList<>();
        @Builder.Default
        private List<Map<String, Object>> stores = new ArrayList<>();
        @Builder.Default
        private List<Map<String, Object>> products = new ArrayList<>();
    }
}
