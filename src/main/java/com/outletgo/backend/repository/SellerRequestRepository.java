package com.outletgo.backend.repository;

import com.outletgo.backend.entity.SellerRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SellerRequestRepository extends JpaRepository<SellerRequest, UUID> {
    List<SellerRequest> findAllByOrderByCreatedAtDesc();
}
