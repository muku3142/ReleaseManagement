package com.evision.releasemgmt.domain.enrichment;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record CodeLinks(EnrichmentStatus status, EnrichmentReason reason, boolean incomplete, Instant attemptedAt,
                        CommitsSource commitsSource, List<LinkedPullRequest> pullRequests, List<LinkedCommit> commits,
                        List<String> branches, StatusHistory history) {

    public CodeLinks {
        pullRequests = pullRequests == null ? List.of() : List.copyOf(pullRequests);
        commits = commits == null ? List.of() : List.copyOf(commits);
        branches = branches == null ? List.of() : List.copyOf(branches);
        history = history == null ? StatusHistory.notRead() : history;
    }

    public static CodeLinks notAttempted() {
        return new CodeLinks(EnrichmentStatus.NOT_ATTEMPTED, null, false, null, null, List.of(), List.of(),
                List.of(), StatusHistory.notRead());
    }

    // Keep the last good rows; documents still render empty because only LINKED reaches them.
    public CodeLinks failed(EnrichmentReason failureReason, Instant at, StatusHistory newHistory) {
        return new CodeLinks(EnrichmentStatus.FAILED, failureReason, false, at, commitsSource, pullRequests, commits,
                branches, newHistory);
    }

    public List<LinkedCommit> documentCommits() {
        if (status != EnrichmentStatus.LINKED) {
            return List.of();
        }
        return commits.stream()
                .sorted(Comparator.comparing(LinkedCommit::authoredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LinkedCommit::sha))
                .toList();
    }

    // Environment-merge PRs carry other tickets' work, so documents list only the ticket's own PRs; the release page shows all.
    public List<LinkedPullRequest> documentPullRequests() {
        if (status != EnrichmentStatus.LINKED) {
            return List.of();
        }
        return pullRequests.stream().filter(LinkedPullRequest::countsTowardCommits)
                .sorted(Comparator.comparingLong(LinkedPullRequest::numericId)).toList();
    }

    // The only comparison that may stamp items_changed_at: it covers exactly what documents render.
    public boolean sameInDocumentsAs(CodeLinks other) {
        return documentView(documentCommits(), documentPullRequests())
                .equals(documentView(other.documentCommits(), other.documentPullRequests()));
    }

    private static List<List<List<String>>> documentView(List<LinkedCommit> docCommits, List<LinkedPullRequest> docPrs) {
        return List.of(
                docCommits.stream().map(c -> List.of(c.sha(), c.message(), nullToEmpty(c.author()),
                        nullToEmpty(c.url()))).toList(),
                docPrs.stream().map(p -> List.of(p.id(), nullToEmpty(p.title()), nullToEmpty(p.status()),
                        nullToEmpty(p.url()))).toList());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
