package com.evision.releasemgmt.adapters.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "environment_stage")
public class EnvironmentStageEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "is_production", nullable = false)
    private boolean production;

    @Column(name = "retired_at")
    private Instant retiredAt;

    protected EnvironmentStageEntity() {
    }

    public static EnvironmentStageEntity create(String name, int position) {
        EnvironmentStageEntity entity = new EnvironmentStageEntity();
        entity.id = UUID.randomUUID();
        entity.name = name;
        entity.position = position;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public boolean isProduction() {
        return production;
    }

    public void setProduction(boolean production) {
        this.production = production;
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }

    public void setRetiredAt(Instant retiredAt) {
        this.retiredAt = retiredAt;
    }
}
