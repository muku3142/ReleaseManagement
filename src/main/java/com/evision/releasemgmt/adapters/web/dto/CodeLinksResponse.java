package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.enrichment.LinkedPullRequest;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record CodeLinksResponse(String status, String reason, boolean incomplete, Instant attemptedAt,
                                String commitsSource, List<PullRequestLinkResponse> pullRequests,
                                List<CommitLinkResponse> commits, List<String> branches) {

    public static CodeLinksResponse from(CodeLinks links) {
        return new CodeLinksResponse(links.status().name(),
                links.reason() == null ? null : links.reason().name(), links.incomplete(), links.attemptedAt(),
                links.commitsSource() == null ? null : links.commitsSource().name(),
                links.pullRequests().stream()
                        .sorted(Comparator.comparingLong(LinkedPullRequest::numericId))
                        .map(pr -> new PullRequestLinkResponse(pr.id(), pr.title(), pr.status(), pr.url(),
                                pr.sourceBranch(), pr.targetBranch(), pr.countsTowardCommits()))
                        .toList(),
                links.commits().stream()
                        .map(c -> new CommitLinkResponse(c.sha(), c.shortSha(), c.message(), c.author(),
                                c.authoredAt(), c.url()))
                        .toList(),
                links.branches());
    }
}
