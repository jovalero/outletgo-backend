package com.outletgo.backend.repository;

import com.outletgo.backend.entity.BlogCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlogCategoryRepository extends JpaRepository<BlogCategory, UUID> {
    Optional<BlogCategory> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
