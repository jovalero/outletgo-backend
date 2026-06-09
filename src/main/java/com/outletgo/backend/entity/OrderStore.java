package com.outletgo.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "order_stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStore {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Order.OrderStatus status = Order.OrderStatus.PENDING;

    @Column(name = "subtotal_amount", nullable = false)
    private Double subtotalAmount;

    @Column(name = "refund_amount")
    private Double refundAmount = 0.0;

    @Column(name = "mp_refund_id", length = 255)
    private String mpRefundId;

    @Column(name = "commission_rate")
    private Double commissionRate;

    @Column(name = "commission_amount")
    private Double commissionAmount;

    @Column(name = "net_amount")
    private Double netAmount;

    @Column(name = "payout_status", length = 30)
    @Builder.Default
    private String payoutStatus = "PENDING";

    @Column(name = "paid_at")
    private java.time.LocalDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = Order.OrderStatus.PENDING;
        }
        if (refundAmount == null) {
            refundAmount = 0.0;
        }
        if (payoutStatus == null) {
            payoutStatus = "PENDING";
        }
    }
}
