package com.outletgo.backend.repository;

import com.outletgo.backend.entity.ProductFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductFavoriteRepository extends JpaRepository<ProductFavorite, UUID> {
    List<ProductFavorite> findByUserId(UUID userId);
    Optional<ProductFavorite> findByUserIdAndProductId(UUID userId, UUID productId);
    boolean existsByUserIdAndProductId(UUID userId, UUID productId);
}
