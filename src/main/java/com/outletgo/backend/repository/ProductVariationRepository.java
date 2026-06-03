package com.outletgo.backend.repository;

import com.outletgo.backend.entity.ProductVariation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductVariationRepository extends JpaRepository<ProductVariation, UUID> {
    List<ProductVariation> findByProductId(UUID productId);
}
