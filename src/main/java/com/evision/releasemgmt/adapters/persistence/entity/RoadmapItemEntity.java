package com.evision.releasemgmt.adapters.persistence.entity;

import com.evision.releasemgmt.domain.roadmap.RiceInputs;
import com.evision.releasemgmt.domain.roadmap.RiceOverrides;
import com.evision.releasemgmt.domain.roadmap.RoadmapItem;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemSource;
import com.evision.releasemgmt.domain.roadmap.RoadmapStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "roadmap_item")
public class RoadmapItemEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private RoadmapItemSource source;

    @Column(name = "jira_key")
    private String jiraKey;

    @Column(name = "jira_issue_id")
    private String jiraIssueId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "jira_status")
    private String jiraStatus;

    @Column(name = "jira_updated_at")
    private Instant jiraUpdatedAt;

    @Column(name = "jira_synced_at")
    private Instant jiraSyncedAt;

    @Column(name = "jira_unavailable_since")
    private Instant jiraUnavailableSince;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RoadmapStatus status;

    @Column(name = "notes")
    private String notes;

    @Column(name = "target_quarter")
    private String targetQuarter;

    @Column(name = "owner")
    private String owner;

    @Column(name = "reach")
    private Integer reach;

    @Column(name = "impact")
    private BigDecimal impact;

    @Column(name = "confidence")
    private Short confidence;

    @Column(name = "effort")
    private BigDecimal effort;

    @Column(name = "reach_override_reason")
    private String reachOverrideReason;

    @Column(name = "impact_override_reason")
    private String impactOverrideReason;

    @Column(name = "confidence_override_reason")
    private String confidenceOverrideReason;

    @Column(name = "jira_priority")
    private String jiraPriority;

    @Column(name = "jira_readiness")
    private Short jiraReadiness;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected RoadmapItemEntity() {
    }

    public static RoadmapItemEntity from(RoadmapItem item) {
        RoadmapItemEntity e = new RoadmapItemEntity();
        e.id = item.id();
        e.source = item.source();
        e.jiraKey = item.jiraKey();
        e.jiraIssueId = item.jiraIssueId();
        e.title = item.title();
        e.jiraStatus = item.jiraStatus();
        e.jiraUpdatedAt = item.jiraUpdatedAt();
        e.jiraSyncedAt = item.jiraSyncedAt();
        e.jiraUnavailableSince = item.jiraUnavailableSince();
        e.productId = item.productId();
        e.status = item.status();
        e.notes = item.notes();
        e.targetQuarter = item.targetQuarter();
        e.owner = item.owner();
        e.applyInputs(item.inputs());
        e.applyOverrides(item.overrides());
        e.jiraPriority = item.jiraPriority();
        e.jiraReadiness = item.jiraReadiness() == null ? null : item.jiraReadiness().shortValue();
        e.createdAt = item.createdAt();
        e.createdBy = item.createdBy();
        e.archivedAt = item.archivedAt();
        return e;
    }

    public void applyInputs(RiceInputs inputs) {
        this.reach = inputs.reach();
        this.impact = inputs.impact();
        this.confidence = inputs.confidence() == null ? null : inputs.confidence().shortValue();
        this.effort = inputs.effort();
    }

    public void applyOverrides(RiceOverrides overrides) {
        this.reachOverrideReason = overrides.reach();
        this.impactOverrideReason = overrides.impact();
        this.confidenceOverrideReason = overrides.confidence();
    }

    public RoadmapItem toDomain() {
        RiceInputs inputs = new RiceInputs(reach, impact, confidence == null ? null : confidence.intValue(), effort);
        return new RoadmapItem(id, source, jiraKey, jiraIssueId, title, jiraStatus, jiraUpdatedAt, jiraSyncedAt,
                jiraUnavailableSince, productId, status, notes, targetQuarter, owner, inputs,
                new RiceOverrides(reachOverrideReason, impactOverrideReason, confidenceOverrideReason), jiraPriority,
                jiraReadiness == null ? null : jiraReadiness.intValue(), createdAt, createdBy, archivedAt);
    }
}
