package com.outletgo.backend.repository;

import com.outletgo.backend.entity.StoreFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreFavoriteRepository extends JpaRepository<StoreFavorite, UUID> {
    List<StoreFavorite> findByUserId(UUID userId);
    Optional<StoreFavorite> findByUserIdAndStoreId(UUID userId, UUID storeId);
    boolean existsByUserIdAndStoreId(UUID userId, UUID storeId);
}
