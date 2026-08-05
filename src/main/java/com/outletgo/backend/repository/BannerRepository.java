package com.outletgo.backend.repository;

import com.outletgo.backend.entity.Banner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import java.util.List;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface BannerRepository extends JpaRepository<Banner, UUID> {
    Page<Banner> findAll(Pageable pageable);

    @Query("SELECT DISTINCT b FROM Banner b LEFT JOIN FETCH b.stores LEFT JOIN FETCH b.products WHERE b.status = 'ACTIVE' AND (b.startDate IS NULL OR b.startDate <= :now) AND (b.endDate IS NULL OR b.endDate >= :now) ORDER BY b.createdAt DESC")
    List<Banner> findActiveBanners(@Param("now") LocalDateTime now);

    @Query("SELECT DISTINCT b FROM Banner b LEFT JOIN FETCH b.stores LEFT JOIN FETCH b.products WHERE b.status = :status ORDER BY b.createdAt DESC")
    List<Banner> findByStatusOrderByCreatedAtDesc(@Param("status") String status);
}
