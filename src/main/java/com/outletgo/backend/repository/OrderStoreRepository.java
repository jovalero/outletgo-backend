package com.outletgo.backend.repository;

import com.outletgo.backend.entity.OrderStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderStoreRepository extends JpaRepository<OrderStore, UUID> {
    List<OrderStore> findByOrderId(UUID orderId);
    List<OrderStore> findByStoreId(UUID storeId);
}
