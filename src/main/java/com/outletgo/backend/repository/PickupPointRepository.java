package com.outletgo.backend.repository;

import com.outletgo.backend.entity.PickupPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PickupPointRepository extends JpaRepository<PickupPoint, UUID> {

    @Query("SELECT p FROM PickupPoint p WHERE " +
           "(:isActive IS NULL OR p.isActive = :isActive) AND " +
           "(:search IS NULL OR LOWER(p.name) LIKE :search OR LOWER(p.city) LIKE :search OR LOWER(p.address) LIKE :search)")
    Page<PickupPoint> searchPickupPointsAdmin(
            @Param("isActive") Boolean isActive,
            @Param("search") String search,
            Pageable pageable);
}
