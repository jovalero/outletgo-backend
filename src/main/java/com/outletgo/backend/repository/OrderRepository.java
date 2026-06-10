package com.outletgo.backend.repository;

import com.outletgo.backend.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByClientIdOrderByOrderDateDesc(UUID clientId);
    Page<Order> findByClientIdOrderByOrderDateDesc(UUID clientId, Pageable pageable);
    List<Order> findByStoreIdOrderByOrderDateDesc(UUID storeId);

    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN OrderStore os ON os.order = o " +
           "WHERE " +
           "(:searchId IS NULL OR o.id = :searchId) AND " +
           "(:search IS NULL OR LOWER(o.client.email) LIKE :search OR LOWER(o.shippingAddress) LIKE :search OR LOWER(os.store.businessName) LIKE :search) AND " +
           "(:status IS NULL OR os.status = :status) AND " +
           "(:storeId IS NULL OR os.store.id = :storeId) AND " +
           "(CAST(:startDate AS timestamp) IS NULL OR o.orderDate >= :startDate) AND " +
           "(CAST(:endDate AS timestamp) IS NULL OR o.orderDate <= :endDate)")
    Page<Order> searchOrdersAdmin(
            @Param("searchId") UUID searchId,
            @Param("search") String search,
            @Param("status") Order.OrderStatus status,
            @Param("storeId") UUID storeId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            Pageable pageable);
}
