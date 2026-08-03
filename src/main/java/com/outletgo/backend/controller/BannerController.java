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

@RestController
@RequestMapping({"/api/banners", "/banners"})
@CrossOrigin(origins = "*")
public class BannerController {

    @Autowired
    private BannerService bannerService;

    @Autowired
    private ProductImageRepository productImageRepository;

    @GetMapping({"", "/", "/active"})
    public ResponseEntity<List<Banner>> getActiveBanners() {
        return ResponseEntity.ok(bannerService.getActiveBanners());
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<BannerDetailsDto> getBannerDetails(@PathVariable UUID id) {
        Banner banner = bannerService.getBannerById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaña no encontrada"));

        List<BannerStoreDto> storesDto = banner.getStores().stream()
                .map(s -> BannerStoreDto.builder()
                        .id(s.getId())
                        .businessName(s.getBusinessName())
                        .description(s.getDescription())
                        .address(s.getAddress())
                        .headerImage(s.getHeaderImage())
                        .ratingAvg(s.getRatingAvg())
                        .ratingCount(s.getRatingCount())
                        .build())
                .collect(Collectors.toList());

        List<BannerProductDto> productsDto = banner.getProducts().stream()
                .map(p -> {
                    String thumb = null;
                    List<ProductImage> imgs = productImageRepository.findByProductId(p.getId());
                    if (!imgs.isEmpty()) {
                        thumb = imgs.get(0).getImageUrl();
                    }
                    return BannerProductDto.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .description(p.getDescription())
                            .basePrice(p.getBasePrice())
                            .thumbnailUrl(thumb)
                            .ratingAvg(p.getRatingAvg())
                            .ratingCount(p.getRatingCount())
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
        private String description;
        private String address;
        private String headerImage;
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
        private String thumbnailUrl;
        private Double ratingAvg;
        private Integer ratingCount;
    }
}
