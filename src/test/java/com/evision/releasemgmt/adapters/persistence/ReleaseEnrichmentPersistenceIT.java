package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.enrichment.CommitsSource;
import com.evision.releasemgmt.domain.enrichment.EnrichmentReason;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.enrichment.HistoryState;
import com.evision.releasemgmt.domain.enrichment.LinkedCommit;
import com.evision.releasemgmt.domain.enrichment.LinkedPullRequest;
import com.evision.releasemgmt.domain.enrichment.StatusChange;
import com.evision.releasemgmt.domain.enrichment.StatusHistory;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseFrozenException;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class ReleaseEnrichmentPersistenceIT {

    private static final String KEY = "CNV-1";
    private static final Instant T0 = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-02T10:00:00Z");
    private static final Instant ATTEMPTED = Instant.parse("2026-09-01T00:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private ReleaseRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID releaseId;

    @BeforeEach
    void createRelease() {
        Release release = Release.create("Enrichment Test", "1.0", "CNV", "rm@acme.com", Instant.now());
        TicketDetail ticket = new TicketDetail(KEY, "Summary", "Bug", "Done", null, null, null, List.of(), null,
                "", List.of(), 0, "10001", T0, StatusCategory.DONE, null, false);
        repository.create(release, List.of(new ReleaseItem(ticket, Instant.now(), CodeLinks.notAttempted())));
        releaseId = release.id();
    }

    @AfterEach
    void clearReleases() {
        jdbcTemplate.execute("TRUNCATE release CASCADE");
    }

    private static StatusHistory history() {
        return StatusHistory.derive(List.of(
                new StatusChange(T0, "1", "2", StatusCategory.TO_DO, StatusCategory.IN_PROGRESS),
                new StatusChange(T1, "2", "3", StatusCategory.IN_PROGRESS, StatusCategory.DONE)), T1);
    }

    private static CodeLinks linked(String commitMessage) {
        List<LinkedPullRequest> prs = List.of(
                new LinkedPullRequest("repo-1", "11", "PR eleven", "completed", "https://ado/pr/11",
                        "feature/CNV-1-a", "main", true, ATTEMPTED),
                new LinkedPullRequest("repo-1", "12", "Merge to test", "completed", "https://ado/pr/12",
                        "main", "test", false, null));
        List<LinkedCommit> commits = List.of(
                new LinkedCommit("aaa1111", commitMessage, "Dev One", T0, "https://ado/c/aaa1111"),
                new LinkedCommit("bbb2222", "second", "Dev Two", T1, "https://ado/c/bbb2222"),
                new LinkedCommit("ccc3333", "third", null, T1.plusSeconds(60), "https://ado/c/ccc3333"));
        return new CodeLinks(EnrichmentStatus.LINKED, null, false, ATTEMPTED, CommitsSource.KEY_TAGGED, prs, commits,
                List.of("feature/CNV-1-a"), history());
    }

    private Instant itemsChangedAt() {
        return repository.findById(releaseId).orElseThrow().itemsChangedAt();
    }

    private CodeLinks stored() {
        return repository.findById(releaseId).orElseThrow().items().get(0).codeLinks();
    }

    @Test
    void savedLinkedCodeLinksReadBackEqual() {
        CodeLinks links = linked("first");

        repository.saveEnrichment(releaseId, Map.of(KEY, links));

        CodeLinks reloaded = stored();
        assertThat(reloaded.pullRequests()).containsExactlyInAnyOrderElementsOf(links.pullRequests());
        assertThat(reloaded.commits()).containsExactlyElementsOf(links.commits());
        assertThat(reloaded.branches()).containsExactly("feature/CNV-1-a");
        assertThat(reloaded.history()).isEqualTo(links.history());
        assertThat(reloaded.history().state()).isEqualTo(HistoryState.AVAILABLE);
        assertThat(reloaded.status()).isEqualTo(EnrichmentStatus.LINKED);
        assertThat(reloaded.commitsSource()).isEqualTo(CommitsSource.KEY_TAGGED);
        assertThat(reloaded.attemptedAt()).isEqualTo(ATTEMPTED);
    }

    @Test
    void savingTheSameResultTwiceNoOpDoesNotStampAgain() {
        repository.saveEnrichment(releaseId, Map.of(KEY, linked("first")));
        Instant afterFirst = itemsChangedAt();

        repository.saveEnrichment(releaseId, Map.of(KEY, linked("first")));

        assertThat(itemsChangedAt()).isEqualTo(afterFirst);
    }

    @Test
    void notAttemptedToNoLinkDoesNotStamp() {
        Instant before = itemsChangedAt();
        CodeLinks noLink = new CodeLinks(EnrichmentStatus.NO_LINK, null, false, ATTEMPTED, null, List.of(),
                List.of(), List.of(), StatusHistory.unavailable());

        repository.saveEnrichment(releaseId, Map.of(KEY, noLink));

        assertThat(stored().status()).isEqualTo(EnrichmentStatus.NO_LINK);
        assertThat(itemsChangedAt()).isEqualTo(before);
    }

    @Test
    void failedWithoutRowsToNoLinkDoesNotStamp() {
        repository.saveEnrichment(releaseId,
                Map.of(KEY, CodeLinks.notAttempted().failed(EnrichmentReason.TIMEOUT, ATTEMPTED, StatusHistory.notRead())));
        Instant before = itemsChangedAt();
        CodeLinks noLink = new CodeLinks(EnrichmentStatus.NO_LINK, null, false, ATTEMPTED, null, List.of(),
                List.of(), List.of(), StatusHistory.notRead());

        repository.saveEnrichment(releaseId, Map.of(KEY, noLink));

        assertThat(itemsChangedAt()).isEqualTo(before);
    }

    @Test
    void linkedToFailedKeepsRowsAndStamps() {
        CodeLinks links = linked("first");
        repository.saveEnrichment(releaseId, Map.of(KEY, links));
        Instant before = itemsChangedAt();

        repository.saveEnrichment(releaseId,
                Map.of(KEY, links.failed(EnrichmentReason.TIMEOUT, ATTEMPTED.plusSeconds(5), links.history())));

        CodeLinks reloaded = stored();
        assertThat(reloaded.status()).isEqualTo(EnrichmentStatus.FAILED);
        assertThat(reloaded.reason()).isEqualTo(EnrichmentReason.TIMEOUT);
        assertThat(reloaded.pullRequests()).hasSize(2);
        assertThat(reloaded.commits()).hasSize(3);
        assertThat(itemsChangedAt()).isAfter(before);
    }

    @Test
    void changingOnlyAttemptedAtReasonOrHistoryDoesNotStamp() {
        CodeLinks links = linked("first");
        repository.saveEnrichment(releaseId, Map.of(KEY, links));
        Instant before = itemsChangedAt();
        CodeLinks sameDocuments = new CodeLinks(EnrichmentStatus.LINKED, null, true, ATTEMPTED.plusSeconds(99),
                links.commitsSource(), links.pullRequests(), links.commits(), links.branches(),
                StatusHistory.unavailable());

        repository.saveEnrichment(releaseId, Map.of(KEY, sameDocuments));

        assertThat(stored().history().state()).isEqualTo(HistoryState.UNAVAILABLE);
        assertThat(itemsChangedAt()).isEqualTo(before);
    }

    @Test
    void changingACommitMessageOfALinkedItemStamps() {
        repository.saveEnrichment(releaseId, Map.of(KEY, linked("first")));
        Instant before = itemsChangedAt();

        repository.saveEnrichment(releaseId, Map.of(KEY, linked("reworded")));

        assertThat(stored().commits().get(0).message()).isEqualTo("reworded");
        assertThat(itemsChangedAt()).isAfter(before);
    }

    @Test
    void saveOnAFrozenReleaseIsRefusedAndWritesNothing() {
        repository.freeze(releaseId, "rm@acme.com", Instant.now());

        assertThatThrownBy(() -> repository.saveEnrichment(releaseId, Map.of(KEY, linked("first"))))
                .isInstanceOf(ReleaseFrozenException.class);

        assertThat(stored()).isEqualTo(CodeLinks.notAttempted());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_item_commit", Integer.class)).isZero();
    }

    @Test
    void aTicketKeyNoLongerOnTheReleaseIsIgnored() {
        repository.saveEnrichment(releaseId, Map.of("CNV-999", linked("first"), KEY, linked("first")));

        assertThat(stored().commits()).hasSize(3);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_item_commit", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void removingTheItemCascadesItsChildRows() {
        repository.saveEnrichment(releaseId, Map.of(KEY, linked("first")));

        repository.removeItem(releaseId, KEY);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_item_commit", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_item_pull_request", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_item_status_change", Integer.class))
                .isZero();
    }
}
