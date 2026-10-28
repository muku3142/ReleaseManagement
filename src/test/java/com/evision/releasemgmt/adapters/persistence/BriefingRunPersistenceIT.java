package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.domain.briefing.AppBriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingContent;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import com.evision.releasemgmt.domain.briefing.BriefingTrigger;
import com.evision.releasemgmt.ports.BriefingRunRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class BriefingRunPersistenceIT {

    private static final Instant SLOT = Instant.parse("2026-10-26T05:44:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private BriefingRunRepository runs;

    @Autowired
    private ProductRepository products;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        jdbc.execute("TRUNCATE briefing_run");
        jdbc.execute("TRUNCATE product, client CASCADE");
    }

    private AppBriefingRun run(BriefingTask task, UUID product, Instant slot, BriefingStatus status, Instant finished) {
        return new AppBriefingRun(UUID.randomUUID(), task, product, slot,
                slot == null ? BriefingTrigger.MANUAL : BriefingTrigger.SLOT, status, null, 2, "headline", "summary",
                List.of(new BriefingContent.Section("Blockers", List.of(new BriefingContent.Item("CNV-1", "stuck")))),
                finished.minusSeconds(5), finished);
    }

    private long count(UUID product) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM briefing_run WHERE product_id = ?", Long.class, product);
        return n == null ? 0 : n;
    }

    @Test
    void secondInsertForTheSameSlotStoresNothing() {
        UUID p = products.add("P").id();
        Instant now = Instant.now();

        assertThat(runs.insert(run(BriefingTask.JIRA_DIGEST, p, SLOT, BriefingStatus.OK, now))).isTrue();
        assertThat(runs.insert(run(BriefingTask.JIRA_DIGEST, p, SLOT, BriefingStatus.OK, now))).isFalse();

        assertThat(count(p)).isEqualTo(1);
        assertThat(runs.existsForSlot(BriefingTask.JIRA_DIGEST, p, SLOT)).isTrue();
        assertThat(runs.existsForSlot(BriefingTask.SPRINT_CHECK, p, SLOT)).isFalse();
    }

    @Test
    void manualRunsAreAlwaysStored() {
        UUID p = products.add("P").id();
        Instant now = Instant.now();

        assertThat(runs.insert(run(BriefingTask.JIRA_DIGEST, p, null, BriefingStatus.OK, now))).isTrue();
        assertThat(runs.insert(run(BriefingTask.JIRA_DIGEST, p, null, BriefingStatus.OK, now))).isTrue();

        assertThat(count(p)).isEqualTo(2);
    }

    @Test
    void findByTaskReturnsNewestFirstWithSectionsRoundTripped() {
        UUID p = products.add("P").id();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        AppBriefingRun older = run(BriefingTask.JIRA_DIGEST, p, SLOT, BriefingStatus.ATTENTION, now.minusSeconds(60));
        AppBriefingRun newer = new AppBriefingRun(UUID.randomUUID(), BriefingTask.JIRA_DIGEST, p, null,
                BriefingTrigger.MANUAL, BriefingStatus.SKIPPED, AppBriefingRun.REASON_CLAUDE_COVERED, null, "h", null,
                List.of(), now.minusSeconds(1), now);
        runs.insert(older);
        runs.insert(newer);
        runs.insert(run(BriefingTask.SPRINT_CHECK, p, null, BriefingStatus.OK, now));

        List<AppBriefingRun> found = runs.findByTask(BriefingTask.JIRA_DIGEST);

        assertThat(found).extracting(AppBriefingRun::id).containsExactly(newer.id(), older.id());
        assertThat(found.get(0).status()).isEqualTo(BriefingStatus.SKIPPED);
        assertThat(found.get(0).reason()).isEqualTo(AppBriefingRun.REASON_CLAUDE_COVERED);
        assertThat(found.get(0).trigger()).isEqualTo(BriefingTrigger.MANUAL);
        assertThat(found.get(1).sections()).containsExactly(
                new BriefingContent.Section("Blockers", List.of(new BriefingContent.Item("CNV-1", "stuck"))));
        assertThat(found.get(1).slotAt()).isEqualTo(SLOT);
    }

    @Test
    void pruneKeepsTheNewestPerTaskAndProduct() {
        UUID p = products.add("P").id();
        UUID q = products.add("Q").id();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        for (int i = 0; i < 65; i++) {
            runs.insert(run(BriefingTask.JIRA_DIGEST, p, null, BriefingStatus.OK, now.minusSeconds(i)));
        }
        for (int i = 0; i < 3; i++) {
            runs.insert(run(BriefingTask.JIRA_DIGEST, q, null, BriefingStatus.OK, now.minusSeconds(i)));
        }

        runs.prune(BriefingTask.JIRA_DIGEST, p, 60);

        assertThat(count(p)).isEqualTo(60);
        assertThat(count(q)).isEqualTo(3);
        Instant oldestKept = jdbc.queryForObject(
                "SELECT min(finished_at) FROM briefing_run WHERE product_id = ?", java.sql.Timestamp.class, p).toInstant();
        assertThat(oldestKept).isEqualTo(now.minusSeconds(59));
    }

    @Test
    void lastSuccessfulFinishedAtIgnoresNonSuccessfulRuns() {
        UUID p = products.add("P").id();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        assertThat(runs.lastSuccessfulFinishedAt(BriefingTask.JIRA_DIGEST, p)).isEmpty();
        runs.insert(run(BriefingTask.JIRA_DIGEST, p, null, BriefingStatus.QUIET, now.minusSeconds(100)));
        runs.insert(run(BriefingTask.JIRA_DIGEST, p, null, BriefingStatus.FAILED, now.minusSeconds(10)));
        runs.insert(run(BriefingTask.JIRA_DIGEST, p, null, BriefingStatus.NEEDS_CREDENTIAL, now.minusSeconds(5)));
        runs.insert(run(BriefingTask.JIRA_DIGEST, p, null, BriefingStatus.SKIPPED, now));

        assertThat(runs.lastSuccessfulFinishedAt(BriefingTask.JIRA_DIGEST, p)).contains(now.minusSeconds(100));
    }

    @Test
    void latestStatusIgnoresSkippedRuns() {
        UUID p = products.add("P").id();
        Instant now = Instant.now();
        assertThat(runs.latestStatus()).isEmpty();
        runs.insert(run(BriefingTask.JIRA_DIGEST, p, null, BriefingStatus.FAILED, now.minusSeconds(10)));
        runs.insert(run(BriefingTask.SPRINT_CHECK, p, null, BriefingStatus.SKIPPED, now));

        assertThat(runs.latestStatus()).contains(BriefingStatus.FAILED);
    }
}
