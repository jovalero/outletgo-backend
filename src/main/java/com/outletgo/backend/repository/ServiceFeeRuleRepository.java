package com.outletgo.backend.repository;

import com.outletgo.backend.entity.ServiceFeeRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ServiceFeeRuleRepository extends JpaRepository<ServiceFeeRule, UUID> {

    @Query("SELECT r FROM ServiceFeeRule r WHERE " +
           "(:isActive IS NULL OR r.isActive = :isActive) AND " +
           "(:search IS NULL OR LOWER(r.name) LIKE :search)")
    Page<ServiceFeeRule> searchServiceFeeRulesAdmin(
            @Param("isActive") Boolean isActive,
            @Param("search") String search,
            Pageable pageable);
}
