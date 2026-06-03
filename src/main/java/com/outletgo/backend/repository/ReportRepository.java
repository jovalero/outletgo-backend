package com.outletgo.backend.repository;

import com.outletgo.backend.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    @Query("SELECT r FROM Report r WHERE r.product IS NOT NULL AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:storeId IS NULL OR r.product.store.id = :storeId) AND " +
           "(:productId IS NULL OR r.product.id = :productId) AND " +
           "(:search IS NULL OR LOWER(r.reason) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(r.product.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Report> searchProductReportsAdmin(
            @Param("status") Report.ReportStatus status,
            @Param("storeId") UUID storeId,
            @Param("productId") UUID productId,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT r FROM Report r WHERE r.store IS NOT NULL AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:storeId IS NULL OR r.store.id = :storeId) AND " +
           "(:search IS NULL OR LOWER(r.reason) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(r.store.businessName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Report> searchStoreReportsAdmin(
            @Param("status") Report.ReportStatus status,
            @Param("storeId") UUID storeId,
            @Param("search") String search,
            Pageable pageable);
}
