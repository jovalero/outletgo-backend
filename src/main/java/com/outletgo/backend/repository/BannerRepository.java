package com.outletgo.backend.repository;

import com.outletgo.backend.entity.Banner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BannerRepository extends JpaRepository<Banner, UUID> {
    Page<Banner> findAll(Pageable pageable);

    @Query(value = "SELECT b FROM Banner b ORDER BY b.createdAt DESC",
           countQuery = "SELECT COUNT(b) FROM Banner b")
    Page<Banner> findAllWithRelations(Pageable pageable);

    @Query("SELECT DISTINCT b FROM Banner b LEFT JOIN FETCH b.stores LEFT JOIN FETCH b.products WHERE b.status = 'ACTIVE' AND (b.startDate IS NULL OR b.startDate <= :now) AND (b.endDate IS NULL OR b.endDate >= :now) ORDER BY b.createdAt DESC")
    List<Banner> findActiveBanners(@Param("now") LocalDateTime now);

    @Query("SELECT DISTINCT b FROM Banner b LEFT JOIN FETCH b.stores LEFT JOIN FETCH b.products WHERE b.status = :status ORDER BY b.createdAt DESC")
    List<Banner> findByStatusOrderByCreatedAtDesc(@Param("status") String status);
}
