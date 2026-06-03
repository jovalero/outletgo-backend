package com.outletgo.backend.repository;

import com.outletgo.backend.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreRepository extends JpaRepository<Store, UUID> {
    Optional<Store> findByUserId(UUID userId);

    @Query("SELECT s FROM Store s WHERE " +
           "(:search IS NULL OR LOWER(s.businessName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.user.email) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:isactive IS NULL OR s.user.isactive = :isactive)")
    Page<Store> searchSellersAdmin(
            @Param("search") String search,
            @Param("isactive") Boolean isactive,
            Pageable pageable);
}
