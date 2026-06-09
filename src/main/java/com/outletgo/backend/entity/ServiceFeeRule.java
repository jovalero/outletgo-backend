package com.outletgo.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "service_fee_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceFeeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "fee_type", nullable = false, length = 50)
    private String feeType;

    @Column(name = "fee_value", nullable = false)
    private Double feeValue;

    @Column(name = "fee_target", nullable = false, length = 50)
    private String feeTarget;

    @Column(name = "shipping_method", length = 50)
    private String shippingMethod;

    @Column(name = "min_order_amount", nullable = false)
    @Builder.Default
    private Double minOrderAmount = 0.0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @PrePersist
    protected void onCreate() {
        if (minOrderAmount == null) {
            minOrderAmount = 0.0;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (priority == null) {
            priority = 0;
        }
    }
}
