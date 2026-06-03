package com.outletgo.backend.repository;

import com.outletgo.backend.entity.ModerationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ModerationHistoryRepository extends JpaRepository<ModerationHistory, UUID> {
    List<ModerationHistory> findByProductId(UUID productId);
    List<ModerationHistory> findByAdminId(UUID adminId);
}
