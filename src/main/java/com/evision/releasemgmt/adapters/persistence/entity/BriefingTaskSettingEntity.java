package com.evision.releasemgmt.adapters.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "briefing_task_setting")
public class BriefingTaskSettingEntity {

    @Id
    private String task;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private String cron;

    @Column(name = "stale_after_working_days")
    private Integer staleAfterWorkingDays;

    @Column(name = "due_soon_days")
    private Integer dueSoonDays;

    @Column(name = "backlog_items")
    private Integer backlogItems;

    public BriefingTaskSettingEntity() {
    }

    public String getTask() {
        return task;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public Integer getStaleAfterWorkingDays() {
        return staleAfterWorkingDays;
    }

    public void setStaleAfterWorkingDays(Integer staleAfterWorkingDays) {
        this.staleAfterWorkingDays = staleAfterWorkingDays;
    }

    public Integer getDueSoonDays() {
        return dueSoonDays;
    }

    public void setDueSoonDays(Integer dueSoonDays) {
        this.dueSoonDays = dueSoonDays;
    }

    public Integer getBacklogItems() {
        return backlogItems;
    }

    public void setBacklogItems(Integer backlogItems) {
        this.backlogItems = backlogItems;
    }
}
