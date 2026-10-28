package com.evision.releasemgmt.adapters.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_bundle")
public class ProductBundleEntity {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected ProductBundleEntity() {
    }

    public static ProductBundleEntity create(UUID productId, String name) {
        ProductBundleEntity entity = new ProductBundleEntity();
        entity.id = UUID.randomUUID();
        entity.productId = productId;
        entity.name = name;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }
}
