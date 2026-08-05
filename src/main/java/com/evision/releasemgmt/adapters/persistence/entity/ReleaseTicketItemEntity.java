package com.evision.releasemgmt.adapters.persistence.entity;

import com.evision.releasemgmt.adapters.persistence.converter.JsonStringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "comments", nullable = false)
    @Convert(converter = JsonStringListConverter.class)
    private List<String> comments;

    @Column(name = "comment_total", nullable = false)
    private int commentTotal;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    public ReleaseTicketItemEntity() {
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
