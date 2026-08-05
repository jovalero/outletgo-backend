package com.outletgo.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    public enum Role {
        CLIENT,
        OUTLET_OWNER,
        ADMIN
    }

    public enum AuthProvider {
        LOCAL,
        GOOGLE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "isactive", nullable = false)
    private Boolean isactive = true;

    @Column(length = 100)
    private String name;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "selected_logistics_type", length = 20)
    private String selectedLogisticsType; // PICKUP | DELIVERY

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_address_id")
    private UserAddress selectedAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_pickup_point_id")
    private PickupPoint selectedPickupPoint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isactive == null) {
            isactive = true;
        }
        if (authProvider == null) {
            authProvider = AuthProvider.LOCAL;
        }
    }
}
