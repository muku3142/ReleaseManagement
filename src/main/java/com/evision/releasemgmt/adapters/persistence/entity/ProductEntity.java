package com.evision.releasemgmt.adapters.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product")
public class ProductEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected ProductEntity() {
    }

    public static ProductEntity create(String name) {
        ProductEntity entity = new ProductEntity();
        entity.id = UUID.randomUUID();
        entity.name = name;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }
}
