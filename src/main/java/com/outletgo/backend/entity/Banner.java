package com.outletgo.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

import java.util.Set;
import java.util.HashSet;

@Entity
@Table(name = "banners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "stores", "products"})
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "image_url", nullable = false, columnDefinition = "text")
    private String imageUrl;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String type = "CAMPAIGN"; // CAMPAIGN, STORE, PRODUCT

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, PAUSED, EXPIRED

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "banner_stores",
        joinColumns = @JoinColumn(name = "banner_id"),
        inverseJoinColumns = @JoinColumn(name = "store_id")
    )
    @Builder.Default
    @JsonIgnore
    private Set<Store> stores = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "banner_products",
        joinColumns = @JoinColumn(name = "banner_id"),
        inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    @Builder.Default
    @JsonIgnore
    private Set<Product> products = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    public Set<Store> getStores() {
        return stores;
    }

    @JsonIgnore
    public Set<Product> getProducts() {
        return products;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (type == null) {
            type = "CAMPAIGN";
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }
}
