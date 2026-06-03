package com.outletgo.backend.repository;

import com.outletgo.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByStoreId(UUID storeId);
    List<Product> findByCategoryId(UUID categoryId);
    List<Product> findByIsactiveTrue();

    @Query("SELECT p FROM Product p WHERE " +
           "(:search IS NULL OR LOWER(p.name) LIKE :search) AND " +
           "(:isactive IS NULL OR p.isactive = :isactive) AND " +
           "(:storeId IS NULL OR p.store.id = :storeId)")
    Page<Product> searchProductsAdmin(
            @Param("search") String search,
            @Param("isactive") Boolean isactive,
            @Param("storeId") UUID storeId,
            Pageable pageable);
}
