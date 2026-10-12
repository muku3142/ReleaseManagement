package com.evision.releasemgmt.adapters.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "release_item_pull_request")
public class ReleaseItemPullRequestEntity {

    @Id
    private UUID id;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "repository_id", nullable = false)
    private String repositoryId;

    @Column(name = "pr_id", nullable = false)
    private String prId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "source_branch", nullable = false)
    private String sourceBranch;

    @Column(name = "target_branch")
    private String targetBranch;

    @Column(name = "counts_toward_commits", nullable = false)
    private boolean countsTowardCommits;

    @Column(name = "commits_fetched_at")
    private Instant commitsFetchedAt;

    public ReleaseItemPullRequestEntity() {
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

    public String getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(String repositoryId) {
        this.repositoryId = repositoryId;
    }

    public String getPrId() {
        return prId;
    }

    public void setPrId(String prId) {
        this.prId = prId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public void setSourceBranch(String sourceBranch) {
        this.sourceBranch = sourceBranch;
    }

    public String getTargetBranch() {
        return targetBranch;
    }

    public void setTargetBranch(String targetBranch) {
        this.targetBranch = targetBranch;
    }

    public boolean isCountsTowardCommits() {
        return countsTowardCommits;
    }

    public void setCountsTowardCommits(boolean countsTowardCommits) {
        this.countsTowardCommits = countsTowardCommits;
    }

    public Instant getCommitsFetchedAt() {
        return commitsFetchedAt;
    }

    public void setCommitsFetchedAt(Instant commitsFetchedAt) {
        this.commitsFetchedAt = commitsFetchedAt;
    }
}
