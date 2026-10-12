package com.evision.releasemgmt.domain.enrichment;

import java.time.Instant;

public record LinkedPullRequest(String repositoryId, String id, String title, String status, String url,
                                String sourceBranch, String targetBranch, boolean countsTowardCommits,
                                Instant commitsFetchedAt) {

    // Used for ordering only.
    public long numericId() {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }
}
