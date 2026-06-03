package com.outletgo.backend.repository;

import com.outletgo.backend.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {
    List<Review> findByProductId(UUID productId);
    List<Review> findByStoreId(UUID storeId);
    List<Review> findByUserId(UUID userId);
    List<Review> findByIsVisibleTrue();

    @Query("SELECT r FROM Review r WHERE " +
           "(:scope IS NULL OR (:scope = 'product' AND r.product IS NOT NULL) OR (:scope = 'store' AND r.product IS NULL)) AND " +
           "(:storeId IS NULL OR r.store.id = :storeId) AND " +
           "(:productId IS NULL OR r.product.id = :productId) AND " +
           "(:isVisible IS NULL OR r.isVisible = :isVisible) AND " +
           "(:rating IS NULL OR r.rating = :rating) AND " +
           "(:search IS NULL OR LOWER(r.user.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Review> searchReviewsAdmin(
            @Param("scope") String scope,
            @Param("storeId") UUID storeId,
            @Param("productId") UUID productId,
            @Param("isVisible") Boolean isVisible,
            @Param("rating") Integer rating,
            @Param("search") String search,
            Pageable pageable);
}
