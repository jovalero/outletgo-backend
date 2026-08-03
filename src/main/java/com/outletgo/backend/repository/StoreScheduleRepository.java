package com.outletgo.backend.repository;

import com.outletgo.backend.entity.StoreSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StoreScheduleRepository extends JpaRepository<StoreSchedule, UUID> {
    List<StoreSchedule> findByStoreIdOrderByDayOfWeekAsc(UUID storeId);
    void deleteByStoreId(UUID storeId);
}
