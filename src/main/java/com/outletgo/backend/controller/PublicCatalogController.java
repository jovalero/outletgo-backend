package com.outletgo.backend.controller;

import com.outletgo.backend.entity.Category;
import com.outletgo.backend.entity.Product;
import com.outletgo.backend.entity.ProductImage;
import com.outletgo.backend.entity.Store;
import com.outletgo.backend.repository.CategoryRepository;
import com.outletgo.backend.repository.ProductImageRepository;
import com.outletgo.backend.repository.ProductRepository;
import com.outletgo.backend.repository.StoreRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@RestController
@CrossOrigin
@Transactional(readOnly = true)
public class PublicCatalogController {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private com.outletgo.backend.repository.ReviewRepository reviewRepository;

    @GetMapping({"/api/products/categories", "/api/catalog/categories", "/products/categories"})
    public ResponseEntity<List<Category>> getCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    @GetMapping({"/api/products/new-arrivals", "/api/catalog/products/new-arrivals", "/products/new-arrivals"})
    public ResponseEntity<List<CatalogProductDto>> getNewArrivals(
            @RequestParam(required = false, defaultValue = "10") int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        Page<Product> products = productRepository.findAll(pageable);
        List<CatalogProductDto> list = products.getContent().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsactive()))
                .map(p -> {
                    String thumb = null;
                    List<ProductImage> imgs = productImageRepository.findByProductId(p.getId());
                    if (!imgs.isEmpty()) {
                        thumb = imgs.get(0).getImageUrl();
                    }
                    Double avgRating = reviewRepository.getAverageRatingForProduct(p.getId());
                    Long countRating = reviewRepository.countReviewsForProduct(p.getId());

                    return CatalogProductDto.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .thumbnailUrl(thumb)
                            .price(p.getBasePrice())
                            .storeId(p.getStore() != null ? p.getStore().getId() : null)
                            .storeName(p.getStore() != null ? p.getStore().getBusinessName() : "Tienda")
                            .ratingAvg(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : null)
                            .ratingCount(countRating != null ? countRating.intValue() : 0)
                            .distanceKm(null)
                            .build();
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping({"/api/stores/top-rated", "/stores/top-rated"})
    public ResponseEntity<List<TopRatedStoreDto>> getTopRatedStores(
            @RequestParam(required = false, defaultValue = "10") int limit) {
        List<Store> stores = storeRepository.findAll();
        List<TopRatedStoreDto> list = stores.stream()
                .filter(s -> s.getUser() != null && Boolean.TRUE.equals(s.getUser().getIsactive()))
                .map(s -> {
                    Double avgRating = reviewRepository.getAverageRatingForStore(s.getId());
                    Long countRating = reviewRepository.countReviewsForStore(s.getId());
                    return TopRatedStoreDto.builder()
                            .id(s.getId())
                            .name(s.getBusinessName())
                            .ratingAvg(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : null)
                            .ratingCount(countRating != null ? countRating.intValue() : 0)
                            .imageUrl(s.getHeaderImage())
                            .address(s.getAddress())
                            .build();
                })
                .sorted((a, b) -> {
                    Double ratingA = a.getRatingAvg() != null ? a.getRatingAvg() : 0.0;
                    Double ratingB = b.getRatingAvg() != null ? b.getRatingAvg() : 0.0;
                    return Double.compare(ratingB, ratingA);
                })
                .limit(limit)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatalogProductDto {
        private UUID id;
        private String name;
        private String thumbnailUrl;
        private Double price;
        private UUID storeId;
        private String storeName;
        private Double ratingAvg;
        private Integer ratingCount;
        private Double distanceKm;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopRatedStoreDto {
        private UUID id;
        private String name;
        private Double ratingAvg;
        private Integer ratingCount;
        private String imageUrl;
        private String address;
    }
}
