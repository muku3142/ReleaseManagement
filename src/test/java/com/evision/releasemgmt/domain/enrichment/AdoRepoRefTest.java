package com.evision.releasemgmt.domain.enrichment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdoRepoRefTest {

    private static final String PR_URL =
            "https://dev.azure.com/example-org/example-project/_git/example-repo/pullrequest/1002";

    @Test
    void trustsOnlyHttpsDevAzureCom() {
        assertThat(AdoRepoRef.isTrustedUrl(PR_URL)).isTrue();
        assertThat(AdoRepoRef.isTrustedUrl("http://dev.azure.com/x")).isFalse();
        assertThat(AdoRepoRef.isTrustedUrl("https://dev.azure.com.evil.com/x")).isFalse();
        assertThat(AdoRepoRef.isTrustedUrl("https://evil.com/https://dev.azure.com/")).isFalse();
        assertThat(AdoRepoRef.isTrustedUrl("https://user@dev.azure.com/x")).isFalse();
        assertThat(AdoRepoRef.isTrustedUrl("https://dev.azure.com:8443/x")).isFalse();
        assertThat(AdoRepoRef.isTrustedUrl("javascript:alert(1)")).isFalse();
        assertThat(AdoRepoRef.isTrustedUrl(null)).isFalse();
    }

    @Test
    void derivesOrganizationProjectAndRepositoryWebUrl() {
        AdoRepoRef ref = AdoRepoRef.fromPullRequestUrl(PR_URL).orElseThrow();

        assertThat(ref.organization()).isEqualTo("example-org");
        assertThat(ref.project()).isEqualTo("example-project");
        assertThat(ref.repositoryWebUrl())
                .isEqualTo("https://dev.azure.com/example-org/example-project/_git/example-repo");
    }

    @Test
    void decodesProjectButKeepsRawSegmentsInTheWebUrl() {
        AdoRepoRef ref = AdoRepoRef.fromPullRequestUrl(
                "https://dev.azure.com/example-org/My%20Project/_git/repo/pullrequest/1").orElseThrow();

        assertThat(ref.project()).isEqualTo("My Project");
        assertThat(ref.repositoryWebUrl()).isEqualTo("https://dev.azure.com/example-org/My%20Project/_git/repo");
    }

    @Test
    void rejectsBadOrganizationAndMissingGitSegment() {
        assertThat(AdoRepoRef.fromPullRequestUrl("https://dev.azure.com/-bad/p/_git/r/pullrequest/1")).isEmpty();
        assertThat(AdoRepoRef.fromPullRequestUrl("https://dev.azure.com/org/p/r/pullrequest/1")).isEmpty();
        assertThat(AdoRepoRef.fromPullRequestUrl("https://evil.com/org/p/_git/r")).isEmpty();
    }
}
