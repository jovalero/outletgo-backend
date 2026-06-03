package com.outletgo.backend.repository;

import com.outletgo.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByStoreId(UUID storeId);
    List<Product> findByCategoryId(UUID categoryId);
    List<Product> findByIsactiveTrue();
}
