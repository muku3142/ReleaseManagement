package com.evision.releasemgmt.adapters.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "briefing_settings")
public class BriefingSettingsEntity {

    @Id
    private Short id;

    @Column(name = "scheduler_account_id")
    private String schedulerAccountId;

    @Column(name = "grace_minutes", nullable = false)
    private int graceMinutes;

    @Column(name = "work_days", nullable = false)
    private String workDays;

    @Column(name = "keep_runs")
    private Integer keepRuns;

    @Column(name = "fallback_enabled", nullable = false)
    private boolean fallbackEnabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    public BriefingSettingsEntity() {
    }

    public String getSchedulerAccountId() {
        return schedulerAccountId;
    }

    public void setSchedulerAccountId(String schedulerAccountId) {
        this.schedulerAccountId = schedulerAccountId;
    }

    public int getGraceMinutes() {
        return graceMinutes;
    }

    public void setGraceMinutes(int graceMinutes) {
        this.graceMinutes = graceMinutes;
    }

    public String getWorkDays() {
        return workDays;
    }

    public void setWorkDays(String workDays) {
        this.workDays = workDays;
    }

    public Integer getKeepRuns() {
        return keepRuns;
    }

    public void setKeepRuns(Integer keepRuns) {
        this.keepRuns = keepRuns;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
