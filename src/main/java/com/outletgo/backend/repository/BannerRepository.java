package com.outletgo.backend.repository;

import com.outletgo.backend.entity.Banner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface BannerRepository extends JpaRepository<Banner, UUID> {
    Page<Banner> findAll(Pageable pageable);
    List<Banner> findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByCreatedAtDesc(
            String status, LocalDateTime nowStart, LocalDateTime nowEnd);
}
