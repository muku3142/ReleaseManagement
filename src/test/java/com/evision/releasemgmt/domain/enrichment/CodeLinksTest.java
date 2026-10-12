package com.evision.releasemgmt.domain.enrichment;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeLinksTest {

    private static final Instant T1 = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-02T00:00:00Z");

    private static LinkedPullRequest pr(String id, boolean counts) {
        return new LinkedPullRequest("repo", id, "title " + id, "OPEN", "https://dev.azure.com/o/p/_git/r/pullrequest/" + id,
                "feature/EXAMPLE-1", "develop", counts, null);
    }

    private static LinkedCommit commit(String sha, Instant at) {
        return new LinkedCommit(sha, "EXAMPLE-1: msg", "Author", at, "https://dev.azure.com/o/p/_git/r/commit/" + sha);
    }

    private static CodeLinks linked(List<LinkedPullRequest> prs, List<LinkedCommit> commits) {
        return new CodeLinks(EnrichmentStatus.LINKED, null, false, T1, CommitsSource.KEY_TAGGED, prs, commits,
                List.of("feature/EXAMPLE-1"), null);
    }

    @Test
    void commitKeepsFirstLineTruncatedAndShortSha() {
        LinkedCommit multi = new LinkedCommit("abcdef1234567", "first\nsecond", "a", T1, "u");
        LinkedCommit longMsg = new LinkedCommit("abc", "x".repeat(600), "a", T1, "u");

        assertThat(multi.message()).isEqualTo("first");
        assertThat(multi.shortSha()).isEqualTo("abcdef1");
        assertThat(longMsg.message()).hasSize(500);
        assertThat(longMsg.shortSha()).isEqualTo("abc");
    }

    @Test
    void documentViewsAreEmptyUnlessLinked() {
        CodeLinks failed = linked(List.of(pr("1", true)), List.of(commit("a", T1)))
                .failed(EnrichmentReason.TIMEOUT, T2, null);

        assertThat(failed.documentCommits()).isEmpty();
        assertThat(failed.documentPullRequests()).isEmpty();
        assertThat(CodeLinks.notAttempted().documentCommits()).isEmpty();
    }

    @Test
    void documentViewsAreFilteredAndOrdered() {
        CodeLinks links = linked(List.of(pr("1003", true), pr("1001", false), pr("1002", true)),
                List.of(commit("b", T2), commit("a", T2), commit("c", T1)));

        assertThat(links.documentPullRequests()).extracting(LinkedPullRequest::id).containsExactly("1002", "1003");
        assertThat(links.documentCommits()).extracting(LinkedCommit::sha).containsExactly("c", "a", "b");
    }

    @Test
    void failedKeepsLastGoodRowsButDiffersInDocuments() {
        CodeLinks linked = linked(List.of(pr("1", true)), List.of(commit("a", T1)));

        CodeLinks failed = linked.failed(EnrichmentReason.TIMEOUT, T2, StatusHistory.unavailable());

        assertThat(failed.status()).isEqualTo(EnrichmentStatus.FAILED);
        assertThat(failed.pullRequests()).isEqualTo(linked.pullRequests());
        assertThat(failed.commits()).isEqualTo(linked.commits());
        assertThat(failed.branches()).isEqualTo(linked.branches());
        assertThat(failed.commitsSource()).isEqualTo(CommitsSource.KEY_TAGGED);
        assertThat(linked.sameInDocumentsAs(failed)).isFalse();
    }

    @Test
    void emptyStatesRenderTheSame() {
        CodeLinks noLink = new CodeLinks(EnrichmentStatus.NO_LINK, null, false, T1, null, null, null, null, null);
        CodeLinks failedWithoutRows = CodeLinks.notAttempted().failed(EnrichmentReason.TIMEOUT, T1, null);

        assertThat(noLink.sameInDocumentsAs(failedWithoutRows)).isTrue();
    }

    @Test
    void onlyDocumentRelevantChangesDiffer() {
        CodeLinks base = linked(List.of(pr("1", true)), List.of(commit("a", T1)));
        CodeLinks metaChanged = new CodeLinks(EnrichmentStatus.LINKED, EnrichmentReason.ADO_ERROR, true, T2,
                CommitsSource.ADO_COMPLETE, base.pullRequests(), base.commits(), List.of(), StatusHistory.unavailable());
        CodeLinks commitChanged = linked(base.pullRequests(), List.of(commit("z", T1)));

        assertThat(base.sameInDocumentsAs(metaChanged)).isTrue();
        assertThat(base.sameInDocumentsAs(commitChanged)).isFalse();
    }
}
