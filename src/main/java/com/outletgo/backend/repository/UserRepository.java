package com.outletgo.backend.repository;

import com.outletgo.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.role = 'CLIENT' AND " +
           "(:search IS NULL OR LOWER(u.email) LIKE :search) AND " +
           "(:isactive IS NULL OR u.isactive = :isactive)")
    Page<User> searchBuyersAdmin(
            @Param("search") String search,
            @Param("isactive") Boolean isactive,
            Pageable pageable);
}
