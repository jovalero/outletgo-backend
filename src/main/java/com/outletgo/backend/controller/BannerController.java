package com.outletgo.backend.controller;

import com.outletgo.backend.entity.Banner;
import com.outletgo.backend.entity.Product;
import com.outletgo.backend.entity.ProductImage;
import com.outletgo.backend.entity.Store;
import com.outletgo.backend.repository.ProductImageRepository;
import com.outletgo.backend.service.BannerService;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping({"/api/banners", "/banners"})
@CrossOrigin(origins = "*")
@Transactional(readOnly = true)
public class BannerController {

    @Autowired
    private BannerService bannerService;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private com.outletgo.backend.repository.ReviewRepository reviewRepository;

    @GetMapping({"", "/", "/active"})
    public ResponseEntity<?> getActiveBanners() {
        List<Banner> banners = bannerService.getActiveBanners();
        List<BannerResponseDto> dtos = banners.stream()
                .map(this::mapToBannerResponseDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    private BannerResponseDto mapToBannerResponseDto(Banner b) {
        List<String> storeIds = new ArrayList<>();
        List<String> productIds = new ArrayList<>();
        if (b.getStores() != null) {
            for (Store s : b.getStores()) {
                if (s != null && s.getId() != null) storeIds.add(s.getId().toString());
            }
        }
        if (b.getProducts() != null) {
            for (Product p : b.getProducts()) {
                if (p != null && p.getId() != null) productIds.add(p.getId().toString());
            }
        }

        return BannerResponseDto.builder()
                .id(b.getId())
                .title(b.getTitle())
                .description(b.getDescription())
                .imageUrl(b.getImageUrl())
                .type(b.getType())
                .status(b.getStatus())
                .badgeText(b.getBadgeText())
                .startDate(b.getStartDate())
                .endDate(b.getEndDate())
                .createdAt(b.getCreatedAt())
                .storeIds(storeIds)
                .productIds(productIds)
                .build();
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<BannerDetailsDto> getBannerDetails(@PathVariable UUID id) {
        Banner banner = bannerService.getBannerById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaña no encontrada"));

        List<BannerStoreDto> storesDto = banner.getStores().stream()
                .map(s -> {
                    String img = s.getHeaderImage() != null && !s.getHeaderImage().isBlank() ? s.getHeaderImage() : "https://picsum.photos/seed/store" + s.getId() + "/200/200";
                    Double avgFromDb = reviewRepository.getAverageRatingForStore(s.getId());
                    Long countFromDb = reviewRepository.countReviewsForStore(s.getId());

                    Double rating = (avgFromDb != null) ? avgFromDb : (s.getRatingAvg() != null ? s.getRatingAvg() : 0.0);
                    Integer count = (countFromDb != null && countFromDb > 0) ? countFromDb.intValue() : (s.getRatingCount() != null ? s.getRatingCount() : 0);

                    return BannerStoreDto.builder()
                            .id(s.getId())
                            .businessName(s.getBusinessName())
                            .name(s.getBusinessName())
                            .description(s.getDescription())
                            .address(s.getAddress())
                            .headerImage(img)
                            .imageUrl(img)
                            .logoUrl(img)
                            .ratingAvg(rating)
                            .ratingCount(count)
                            .build();
                })
                .collect(Collectors.toList());

        List<BannerProductDto> productsDto = banner.getProducts().stream()
                .map(p -> {
                    String thumb = null;
                    List<ProductImage> imgs = productImageRepository.findByProductId(p.getId());
                    if (imgs != null && !imgs.isEmpty()) {
                        thumb = imgs.get(0).getImageUrl();
                    }
                    if (thumb == null || thumb.isBlank()) {
                        thumb = "https://picsum.photos/seed/prod" + p.getId() + "/400/300";
                    }
                    Double priceVal = p.getBasePrice() != null ? p.getBasePrice() : 0.0;
                    Double avgFromDb = reviewRepository.getAverageRatingForProduct(p.getId());
                    Long countFromDb = reviewRepository.countReviewsForProduct(p.getId());

                    Double rating = (avgFromDb != null) ? avgFromDb : (p.getRatingAvg() != null ? p.getRatingAvg() : 0.0);
                    Integer count = (countFromDb != null && countFromDb > 0) ? countFromDb.intValue() : (p.getRatingCount() != null ? p.getRatingCount() : 0);

                    return BannerProductDto.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .description(p.getDescription())
                            .basePrice(priceVal)
                            .price(priceVal)
                            .thumbnailUrl(thumb)
                            .imageUrl(thumb)
                            .ratingAvg(rating)
                            .ratingCount(count)
                            .build();
                })
                .collect(Collectors.toList());

        BannerDetailsDto details = BannerDetailsDto.builder()
                .id(banner.getId())
                .title(banner.getTitle())
                .description(banner.getDescription())
                .imageUrl(banner.getImageUrl())
                .stores(storesDto)
                .products(productsDto)
                .build();

        return ResponseEntity.ok(details);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BannerResponseDto {
        private UUID id;
        private String title;
        private String description;
        private String imageUrl;
        private String type;
        private String status;
        private String badgeText;
        private java.time.LocalDateTime startDate;
        private java.time.LocalDateTime endDate;
        private java.time.LocalDateTime createdAt;
        @Builder.Default
        private List<String> storeIds = new ArrayList<>();
        @Builder.Default
        private List<String> productIds = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BannerDetailsDto {
        private UUID id;
        private String title;
        private String description;
        private String imageUrl;
        private List<BannerStoreDto> stores;
        private List<BannerProductDto> products;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BannerStoreDto {
        private UUID id;
        private String businessName;
        private String name;
        private String description;
        private String address;
        private String headerImage;
        private String imageUrl;
        private String logoUrl;
        private Double ratingAvg;
        private Integer ratingCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BannerProductDto {
        private UUID id;
        private String name;
        private String description;
        private Double basePrice;
        private Double price;
        private String thumbnailUrl;
        private String imageUrl;
        private Double ratingAvg;
        private Integer ratingCount;
    }
}
