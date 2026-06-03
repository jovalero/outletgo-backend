package com.outletgo.backend.repository;

import com.outletgo.backend.entity.StoreSocial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StoreSocialRepository extends JpaRepository<StoreSocial, UUID> {
    List<StoreSocial> findByStoreId(UUID storeId);
}
