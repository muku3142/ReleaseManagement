package com.evision.releasemgmt.adapters.persistence.entity;

import com.evision.releasemgmt.domain.jira.StatusCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "release_item_status_change")
public class ReleaseItemStatusChangeEntity {

    @Id
    private UUID id;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "from_status_id")
    private String fromStatusId;

    @Column(name = "to_status_id", nullable = false)
    private String toStatusId;

    @Column(name = "from_category")
    @Enumerated(EnumType.STRING)
    private StatusCategory fromCategory;

    @Column(name = "to_category")
    @Enumerated(EnumType.STRING)
    private StatusCategory toCategory;

    public ReleaseItemStatusChangeEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public void setItemId(UUID itemId) {
        this.itemId = itemId;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getFromStatusId() {
        return fromStatusId;
    }

    public void setFromStatusId(String fromStatusId) {
        this.fromStatusId = fromStatusId;
    }

    public String getToStatusId() {
        return toStatusId;
    }

    public void setToStatusId(String toStatusId) {
        this.toStatusId = toStatusId;
    }

    public StatusCategory getFromCategory() {
        return fromCategory;
    }

    public void setFromCategory(StatusCategory fromCategory) {
        this.fromCategory = fromCategory;
    }

    public StatusCategory getToCategory() {
        return toCategory;
    }

    public void setToCategory(StatusCategory toCategory) {
        this.toCategory = toCategory;
    }
}
