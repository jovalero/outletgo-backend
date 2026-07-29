package com.outletgo.backend.repository;

import com.outletgo.backend.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findByUserId(UUID userId);
    Optional<UserAddress> findByIdAndUserId(Long id, UUID userId);
}
