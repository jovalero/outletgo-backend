package com.outletgo.backend.repository;

import com.outletgo.backend.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {
    List<Review> findByProductId(UUID productId);
    List<Review> findByStoreId(UUID storeId);
    List<Review> findByUserId(UUID userId);
    List<Review> findByIsVisibleTrue();
}
