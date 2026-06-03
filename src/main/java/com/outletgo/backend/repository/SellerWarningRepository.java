package com.outletgo.backend.repository;

import com.outletgo.backend.entity.SellerWarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SellerWarningRepository extends JpaRepository<SellerWarning, UUID> {
    List<SellerWarning> findByStoreId(UUID storeId);
}
