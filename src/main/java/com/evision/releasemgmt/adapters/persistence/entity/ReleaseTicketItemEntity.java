package com.evision.releasemgmt.adapters.persistence.entity;

import com.evision.releasemgmt.adapters.persistence.converter.JsonStringListConverter;
import com.evision.releasemgmt.domain.enrichment.CommitsSource;
import com.evision.releasemgmt.domain.enrichment.EnrichmentReason;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.enrichment.HistoryState;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "release_ticket_item")
public class ReleaseTicketItemEntity {

    @Id
    private UUID id;

    @Column(name = "release_id", nullable = false)
    private UUID releaseId;

    @Column(name = "ticket_key", nullable = false)
    private String ticketKey;

    @Column(name = "summary", nullable = false)
    private String summary;

    @Column(name = "issue_type", nullable = false)
    private String issueType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "resolution")
    private String resolution;

    @Column(name = "assignee")
    private String assignee;

    @Column(name = "reporter")
    private String reporter;

    @Column(name = "fix_versions", nullable = false)
    @Convert(converter = JsonStringListConverter.class)
    private List<String> fixVersions;

    // D-05-03: nullable with no backfill. Existing rows keep NULL; an OPEN release picks the value
    // up on its next refresh, and a FROZEN release is never touched.
    @Column(name = "component")
    private String component;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "comments", nullable = false)
    @Convert(converter = JsonStringListConverter.class)
    private List<String> comments;

    @Column(name = "comment_total", nullable = false)
    private int commentTotal;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "jira_issue_id")
    private String jiraIssueId;

    @Column(name = "jira_updated_at")
    private Instant jiraUpdatedAt;

    @Column(name = "jira_status_category")
    @Enumerated(EnumType.STRING)
    private StatusCategory jiraStatusCategory;

    @Column(name = "enrichment_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private EnrichmentStatus enrichmentStatus = EnrichmentStatus.NOT_ATTEMPTED;

    @Column(name = "enrichment_reason")
    @Enumerated(EnumType.STRING)
    private EnrichmentReason enrichmentReason;

    @Column(name = "enrichment_incomplete", nullable = false)
    private boolean enrichmentIncomplete;

    @Column(name = "enrichment_attempted_at")
    private Instant enrichmentAttemptedAt;

    @Column(name = "commits_source")
    @Enumerated(EnumType.STRING)
    private CommitsSource commitsSource;

    @Column(name = "branches", nullable = false)
    @Convert(converter = JsonStringListConverter.class)
    private List<String> branches = List.of();

    @Column(name = "history_state", nullable = false)
    @Enumerated(EnumType.STRING)
    private HistoryState historyState = HistoryState.NOT_READ;

    @Column(name = "history_synced_updated_at")
    private Instant historySyncedUpdatedAt;

    @Column(name = "first_done_at")
    private Instant firstDoneAt;

    @Column(name = "reopen_count")
    private Integer reopenCount;

    public ReleaseTicketItemEntity() {
    }

    public String getJiraIssueId() {
        return jiraIssueId;
    }

    public void setJiraIssueId(String jiraIssueId) {
        this.jiraIssueId = jiraIssueId;
    }

    public Instant getJiraUpdatedAt() {
        return jiraUpdatedAt;
    }

    public void setJiraUpdatedAt(Instant jiraUpdatedAt) {
        this.jiraUpdatedAt = jiraUpdatedAt;
    }

    public StatusCategory getJiraStatusCategory() {
        return jiraStatusCategory;
    }

    public void setJiraStatusCategory(StatusCategory jiraStatusCategory) {
        this.jiraStatusCategory = jiraStatusCategory;
    }

    public EnrichmentStatus getEnrichmentStatus() {
        return enrichmentStatus;
    }

    public void setEnrichmentStatus(EnrichmentStatus enrichmentStatus) {
        this.enrichmentStatus = enrichmentStatus;
    }

    public EnrichmentReason getEnrichmentReason() {
        return enrichmentReason;
    }

    public void setEnrichmentReason(EnrichmentReason enrichmentReason) {
        this.enrichmentReason = enrichmentReason;
    }

    public boolean isEnrichmentIncomplete() {
        return enrichmentIncomplete;
    }

    public void setEnrichmentIncomplete(boolean enrichmentIncomplete) {
        this.enrichmentIncomplete = enrichmentIncomplete;
    }

    public Instant getEnrichmentAttemptedAt() {
        return enrichmentAttemptedAt;
    }

    public void setEnrichmentAttemptedAt(Instant enrichmentAttemptedAt) {
        this.enrichmentAttemptedAt = enrichmentAttemptedAt;
    }

    public CommitsSource getCommitsSource() {
        return commitsSource;
    }

    public void setCommitsSource(CommitsSource commitsSource) {
        this.commitsSource = commitsSource;
    }

    public List<String> getBranches() {
        return branches;
    }

    public void setBranches(List<String> branches) {
        this.branches = branches;
    }

    public HistoryState getHistoryState() {
        return historyState;
    }

    public void setHistoryState(HistoryState historyState) {
        this.historyState = historyState;
    }

    public Instant getHistorySyncedUpdatedAt() {
        return historySyncedUpdatedAt;
    }

    public void setHistorySyncedUpdatedAt(Instant historySyncedUpdatedAt) {
        this.historySyncedUpdatedAt = historySyncedUpdatedAt;
    }

    public Instant getFirstDoneAt() {
        return firstDoneAt;
    }

    public void setFirstDoneAt(Instant firstDoneAt) {
        this.firstDoneAt = firstDoneAt;
    }

    public Integer getReopenCount() {
        return reopenCount;
    }

    public void setReopenCount(Integer reopenCount) {
        this.reopenCount = reopenCount;
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

    public String getTicketKey() {
        return ticketKey;
    }

    public void setTicketKey(String ticketKey) {
        this.ticketKey = ticketKey;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getReporter() {
        return reporter;
    }

    public void setReporter(String reporter) {
        this.reporter = reporter;
    }

    public List<String> getFixVersions() {
        return fixVersions;
    }

    public void setFixVersions(List<String> fixVersions) {
        this.fixVersions = fixVersions;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getComments() {
        return comments;
    }

    public void setComments(List<String> comments) {
        this.comments = comments;
    }

    public int getCommentTotal() {
        return commentTotal;
    }

    public void setCommentTotal(int commentTotal) {
        this.commentTotal = commentTotal;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }
}
