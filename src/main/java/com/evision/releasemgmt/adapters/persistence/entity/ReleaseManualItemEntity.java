package com.evision.releasemgmt.adapters.persistence.entity;

import com.evision.releasemgmt.domain.release.ManualItemCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "release_manual_item")
public class ReleaseManualItemEntity {

    @Id
    private UUID id;

    @Column(name = "release_id", nullable = false)
    private UUID releaseId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "category", nullable = false)
    @Enumerated(EnumType.STRING)
    private ManualItemCategory category;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    public ReleaseManualItemEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(UUID releaseId) {
        this.releaseId = releaseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ManualItemCategory getCategory() {
        return category;
    }

    public void setCategory(ManualItemCategory category) {
        this.category = category;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }
}
