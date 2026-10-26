package com.evision.releasemgmt.adapters.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "briefing_run")
public class BriefingRunEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String task;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "slot_at")
    private Instant slotAt;

    @Column(name = "run_trigger", nullable = false)
    private String runTrigger;

    @Column(nullable = false)
    private String status;

    private String reason;

    private Integer attention;

    @Column(nullable = false)
    private String headline;

    private String summary;

    @Column(nullable = false)
    private String body;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    public BriefingRunEntity() {
    }

    public UUID getId() { return id; }
    public String getTask() { return task; }
    public UUID getProductId() { return productId; }
    public Instant getSlotAt() { return slotAt; }
    public String getRunTrigger() { return runTrigger; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public Integer getAttention() { return attention; }
    public String getHeadline() { return headline; }
    public String getSummary() { return summary; }
    public String getBody() { return body; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
