package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.application.PromotionService;
import com.evision.releasemgmt.domain.dashboard.ChurnItem;
import com.evision.releasemgmt.domain.dashboard.DashboardLedger;
import com.evision.releasemgmt.domain.dashboard.DeploymentRow;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.enrichment.HistoryState;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.PromotionDraft;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.ports.DashboardReadRepository;
import com.evision.releasemgmt.ports.PromotionRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DashboardPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    private static final Instant FROM = Instant.now().minus(2, ChronoUnit.DAYS);
    private static final Instant TO = Instant.now().plus(1, ChronoUnit.DAYS);

    @Autowired
    private DashboardReadRepository dashboard;

    @Autowired
    private PromotionService service;

    @Autowired
    private PromotionRepository promotions;

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        jdbc.execute("TRUNCATE release CASCADE");
    }

    private UUID release(String name, boolean freeze) {
        return release(name, freeze, List.of());
    }

    private UUID release(String name, boolean freeze, List<ReleaseItem> items) {
        Release release = Release.create(name, "1.0", "CNV", "rm@acme.com", Instant.now().minus(1, ChronoUnit.HOURS));
        releaseRepository.create(release, items);
        if (freeze) {
            releaseRepository.freeze(release.id(), "rm@acme.com", Instant.now().minus(30, ChronoUnit.MINUTES));
        }
        return release.id();
    }

    private static ReleaseItem item(String key) {
        return new ReleaseItem(new TicketDetail(key, "Summary " + key, "Bug", "Done", "Fixed",
                "Dev Person", "Reporter Person", List.of("2026.08"), null,
                "Ship it", List.of(), 0, null, null, null, null, false), Instant.now(), CodeLinks.notAttempted());
    }

    private UUID stageId(String name) {
        return jdbc.queryForObject("SELECT id FROM environment_stage WHERE name = ?", UUID.class, name);
    }

    private static Instant minutesAgo(int minutes) {
        return Instant.now().minus(minutes, ChronoUnit.MINUTES);
    }

    private PromotionHistory record(UUID releaseId, String stage, PromotionOutcome outcome, Instant deployedAt) {
        return service.record(releaseId, new PromotionDraft(stageId(stage), outcome, deployedAt, null), "acc-1");
    }

    private UUID lastEntryId(PromotionHistory history) {
        return history.entries().getLast().id();
    }

    private PromotionHistory correct(UUID releaseId, UUID promotionId, String stage, PromotionOutcome outcome,
                                     Instant deployedAt) {
        return service.correct(releaseId, promotionId, new PromotionDraft(stageId(stage), outcome, deployedAt, null),
                "test correction", "acc-1");
    }

    private Set<UUID> ids(List<DeploymentRow> rows) {
        return rows.stream().map(DeploymentRow::promotionId).collect(Collectors.toSet());
    }

    @Test
    void deploymentsEqualTheEffectiveNonVoidedEntriesOfEveryReleasesHistory() {
        UUID r1 = release("R1", true);
        record(r1, "DEV", PromotionOutcome.SUCCESS, minutesAgo(25));
        record(r1, "TEST", PromotionOutcome.SUCCESS, minutesAgo(20));
        record(r1, "TEST", PromotionOutcome.ROLLED_BACK, minutesAgo(15));
        UUID r2 = release("R2", true);
        record(r2, "DEV", PromotionOutcome.SUCCESS, minutesAgo(25));
        PromotionHistory r2History = record(r2, "TEST", PromotionOutcome.FAILED, minutesAgo(20));
        correct(r2, lastEntryId(r2History), "TEST", PromotionOutcome.VOIDED, minutesAgo(20));
        UUID r3 = release("R3", true);
        PromotionHistory r3History = record(r3, "DEV", PromotionOutcome.SUCCESS, minutesAgo(25));
        PromotionHistory r3Corrected = correct(r3, lastEntryId(r3History), "DEV", PromotionOutcome.SUCCESS, minutesAgo(24));

        List<DeploymentRow> rows = dashboard.findDeployments(null, FROM, TO, 100, 0);

        Set<UUID> expected = new java.util.HashSet<>();
        for (UUID releaseId : List.of(r1, r2, r3)) {
            PromotionHistory history = promotions.load(releaseId);
            history.entries().stream()
                    .filter(e -> history.isEffective(e) && e.outcome() != PromotionOutcome.VOIDED)
                    .map(Promotion::id)
                    .forEach(expected::add);
        }
        assertThat(rows).hasSize(expected.size());
        assertThat(ids(rows)).isEqualTo(expected);
        assertThat(rows).noneMatch(r -> r.outcome() == PromotionOutcome.VOIDED);
        UUID correcting = lastEntryId(r3Corrected);
        assertThat(rows).filteredOn(r -> r.promotionId().equals(correcting)).singleElement()
                .matches(DeploymentRow::corrected);
        assertThat(rows).filteredOn(r -> !r.promotionId().equals(correcting)).noneMatch(DeploymentRow::corrected);
    }

    @Test
    void stageFilterNarrowsAndNullMeansAllStages() {
        UUID r1 = release("R1", true);
        record(r1, "DEV", PromotionOutcome.SUCCESS, minutesAgo(25));
        record(r1, "TEST", PromotionOutcome.SUCCESS, minutesAgo(20));

        List<DeploymentRow> test = dashboard.findDeployments(stageId("TEST"), FROM, TO, 100, 0);
        List<DeploymentRow> all = dashboard.findDeployments(null, FROM, TO, 100, 0);

        assertThat(test).hasSize(1).allMatch(r -> r.stageName().equals("TEST"));
        assertThat(all).hasSize(2);
    }

    @Test
    void rangeIsHalfOpen() {
        UUID r1 = release("R1", true);
        Instant at = minutesAgo(20).truncatedTo(ChronoUnit.MILLIS);
        record(r1, "DEV", PromotionOutcome.SUCCESS, at);

        assertThat(dashboard.findDeployments(null, at, at.plusSeconds(60), 100, 0)).hasSize(1);
        assertThat(dashboard.findDeployments(null, at.minusSeconds(60), at, 100, 0)).isEmpty();
    }

    @Test
    void pagesNeverDuplicateOrSkipRowsWithEqualDeployedAt() {
        Instant at = minutesAgo(20).truncatedTo(ChronoUnit.MILLIS);
        List<UUID> recordedOrder = new ArrayList<>();
        for (String name : List.of("R1", "R2", "R3")) {
            UUID releaseId = release(name, true);
            recordedOrder.add(lastEntryId(record(releaseId, "DEV", PromotionOutcome.SUCCESS, at)));
        }

        List<DeploymentRow> pageOne = dashboard.findDeployments(null, FROM, TO, 2, 0);
        List<DeploymentRow> pageTwo = dashboard.findDeployments(null, FROM, TO, 2, 2);

        List<UUID> paged = new ArrayList<>();
        pageOne.forEach(r -> paged.add(r.promotionId()));
        pageTwo.forEach(r -> paged.add(r.promotionId()));
        assertThat(paged).containsExactlyInAnyOrderElementsOf(recordedOrder);
        List<UUID> newestRecordedFirst = new ArrayList<>(recordedOrder);
        java.util.Collections.reverse(newestRecordedFirst);
        assertThat(paged).containsExactlyElementsOf(newestRecordedFirst);
    }

    @Test
    void ledgerHoldsReleasesStagesAndTheRawPromotionsIncludingSupersededAndVoided() {
        UUID older = release("Older", true);
        PromotionHistory history = record(older, "DEV", PromotionOutcome.SUCCESS, minutesAgo(25));
        correct(older, lastEntryId(history), "DEV", PromotionOutcome.VOIDED, minutesAgo(24));
        UUID newer = release("Newer", false);
        releaseRepository.addManualItem(newer, ManualItem.create("Script", "d", ManualItemCategory.OTHER, Instant.now()));
        releaseRepository.addManualItem(newer, ManualItem.create("Other", "d", ManualItemCategory.OTHER, Instant.now()));
        jdbc.update("INSERT INTO environment_stage (id, name, position, is_production, retired_at) VALUES (?, 'LEGACY', 99, false, now())",
                UUID.randomUUID());
        try {
            DashboardLedger ledger = dashboard.loadLedger();

            assertThat(ledger.releases()).extracting(r -> r.name()).containsExactly("Newer", "Older");
            assertThat(ledger.releases().getFirst().manualCount()).isEqualTo(2);
            assertThat(ledger.releases().getLast().manualCount()).isZero();
            assertThat(ledger.stages()).anyMatch(s -> s.name().equals("LEGACY") && s.retiredAt() != null);
            assertThat(ledger.promotions()).hasSize(2);
            assertThat(ledger.promotions()).anyMatch(p -> p.outcome() == PromotionOutcome.VOIDED);
        } finally {
            jdbc.update("DELETE FROM environment_stage WHERE name = 'LEGACY'");
        }
    }

    @Test
    void commitsAreSplitAtFirstDoneAndOtherStatesRoundTrip() {
        UUID releaseId = release("R1", false, List.of(item("CNV-1"), item("CNV-2"), item("CNV-3")));
        Instant done = Instant.now().minus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        enrich(releaseId, "CNV-1", EnrichmentStatus.LINKED, false, HistoryState.AVAILABLE, done, 1);
        enrich(releaseId, "CNV-2", EnrichmentStatus.LINKED, true, HistoryState.UNAVAILABLE, null, null);
        commit(releaseId, "CNV-1", "a1", done.minus(1, ChronoUnit.HOURS));
        commit(releaseId, "CNV-1", "a2", done);
        commit(releaseId, "CNV-1", "a3", done.plus(1, ChronoUnit.HOURS));
        commit(releaseId, "CNV-2", "b1", done);
        commit(releaseId, "CNV-2", "b2", done.plus(1, ChronoUnit.HOURS));

        List<ChurnItem> items = dashboard.findChurnItems(List.of(releaseId));

        assertThat(items).extracting(ChurnItem::ticketKey).containsExactly("CNV-1", "CNV-2", "CNV-3");
        ChurnItem split = items.get(0);
        assertThat(split.commitsToDone()).isEqualTo(2);
        assertThat(split.postDoneCommits()).isEqualTo(1);
        assertThat(split.commitTotal()).isEqualTo(3);
        assertThat(split.enrichmentStatus()).isEqualTo(EnrichmentStatus.LINKED);
        assertThat(split.historyState()).isEqualTo(HistoryState.AVAILABLE);
        assertThat(split.reopenCount()).isEqualTo(1);
        assertThat(split.firstDoneAt()).isEqualTo(done);
        ChurnItem noDone = items.get(1);
        assertThat(noDone.commitsToDone()).isZero();
        assertThat(noDone.postDoneCommits()).isZero();
        assertThat(noDone.commitTotal()).isEqualTo(2);
        assertThat(noDone.enrichmentIncomplete()).isTrue();
        assertThat(noDone.historyState()).isEqualTo(HistoryState.UNAVAILABLE);
        assertThat(noDone.reopenCount()).isNull();
        ChurnItem none = items.get(2);
        assertThat(none.commitTotal()).isZero();
        assertThat(none.commitsToDone()).isZero();
        assertThat(none.postDoneCommits()).isZero();
        assertThat(none.commitsSource()).isNull();
    }

    @Test
    void storyPointsAndReadFlagComeBackOnTheChurnItems() {
        UUID releaseId = release("R-SP", false, List.of(item("CNV-1"), item("CNV-2")));
        jdbc.update("UPDATE release_ticket_item SET story_points = 5, story_points_read = TRUE "
                + "WHERE release_id = ? AND ticket_key = 'CNV-1'", releaseId);

        List<ChurnItem> items = dashboard.findChurnItems(List.of(releaseId));

        assertThat(items.get(0).storyPoints()).isEqualByComparingTo("5");
        assertThat(items.get(0).storyPointsRead()).isTrue();
        assertThat(items.get(1).storyPoints()).isNull();
        assertThat(items.get(1).storyPointsRead()).isFalse();
    }

    @Test
    void anEmptyReleaseIdListReturnsNothing() {
        assertThat(dashboard.findChurnItems(List.of())).isEmpty();
    }

    private void enrich(UUID releaseId, String key, EnrichmentStatus status, boolean incomplete, HistoryState state,
                        Instant firstDoneAt, Integer reopenCount) {
        jdbc.update("UPDATE release_ticket_item SET enrichment_status = ?, enrichment_incomplete = ?, history_state = ?, "
                        + "first_done_at = ?, reopen_count = ? WHERE release_id = ? AND ticket_key = ?",
                status.name(), incomplete, state.name(), firstDoneAt == null ? null : java.sql.Timestamp.from(firstDoneAt),
                reopenCount, releaseId, key);
    }

    private void commit(UUID releaseId, String key, String sha, Instant authoredAt) {
        UUID itemId = jdbc.queryForObject("SELECT id FROM release_ticket_item WHERE release_id = ? AND ticket_key = ?",
                UUID.class, releaseId, key);
        jdbc.update("INSERT INTO release_item_commit (id, item_id, sha, message, author_name, authored_at, url) "
                + "VALUES (?, ?, ?, 'm', 'a', ?, 'https://dev.azure.com/x')",
                UUID.randomUUID(), itemId, sha, java.sql.Timestamp.from(authoredAt));
    }
}
