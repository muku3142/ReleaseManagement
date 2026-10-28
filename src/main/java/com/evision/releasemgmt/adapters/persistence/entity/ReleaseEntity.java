package com.evision.releasemgmt.adapters.persistence.entity;

import com.evision.releasemgmt.domain.release.ReleaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "release")
public class ReleaseEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ReleaseStatus status;

    @Column(name = "jira_project_key")
    private String jiraProjectKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Column(name = "frozen_by")
    private String frozenBy;

    @Column(name = "last_refreshed_at")
    private Instant lastRefreshedAt;

    @Column(name = "items_changed_at")
    private Instant itemsChangedAt;

    @Column(name = "packages_url")
    private String packagesUrl;

    public ReleaseEntity() {
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public ReleaseStatus getStatus() {
        return status;
    }

    public void setStatus(ReleaseStatus status) {
        this.status = status;
    }

    public String getJiraProjectKey() {
        return jiraProjectKey;
    }

    public void setJiraProjectKey(String jiraProjectKey) {
        this.jiraProjectKey = jiraProjectKey;
    }

    public String getPackagesUrl() {
        return packagesUrl;
    }

    public void setPackagesUrl(String packagesUrl) {
        this.packagesUrl = packagesUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getFrozenAt() {
        return frozenAt;
    }

    public void setFrozenAt(Instant frozenAt) {
        this.frozenAt = frozenAt;
    }

    public String getFrozenBy() {
        return frozenBy;
    }

    public void setFrozenBy(String frozenBy) {
        this.frozenBy = frozenBy;
    }

    public Instant getLastRefreshedAt() {
        return lastRefreshedAt;
    }

    public void setLastRefreshedAt(Instant lastRefreshedAt) {
        this.lastRefreshedAt = lastRefreshedAt;
    }

    public Instant getItemsChangedAt() {
        return itemsChangedAt;
    }

    public void setItemsChangedAt(Instant itemsChangedAt) {
        this.itemsChangedAt = itemsChangedAt;
    }
}
