package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.dashboard.ChurnItem;
import com.evision.releasemgmt.domain.dashboard.DashboardLedger;
import com.evision.releasemgmt.domain.dashboard.DashboardRelease;
import com.evision.releasemgmt.domain.dashboard.DeploymentRow;
import com.evision.releasemgmt.domain.dashboard.ReleaseChurn;
import com.evision.releasemgmt.domain.dashboard.TicketChurn;
import com.evision.releasemgmt.domain.enrichment.CommitsSource;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.enrichment.HistoryState;
import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.ports.DashboardReadRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    private final DashboardReadRepository repository = mock(DashboardReadRepository.class);
    private final DashboardService service = new DashboardService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    private final Stage dev = new Stage(UUID.randomUUID(), "DEV", 1, false, null);
    private final Stage test = new Stage(UUID.randomUUID(), "TEST", 2, false, null);
    private final Stage uat = new Stage(UUID.randomUUID(), "UAT", 3, false, null);
    private final Stage prod = new Stage(UUID.randomUUID(), "PROD", 4, true, null);
    private final List<Stage> ladder = List.of(dev, test, uat, prod);

    private final List<DashboardRelease> releases = new ArrayList<>();
    private final List<Promotion> promotions = new ArrayList<>();

    private DashboardRelease release(Instant createdAt, Instant frozenAt) {
        DashboardRelease r = new DashboardRelease(UUID.randomUUID(), "R" + releases.size(), "1." + releases.size(),
                frozenAt == null ? ReleaseStatus.OPEN : ReleaseStatus.FROZEN, createdAt, frozenAt, 0);
        releases.add(r);
        return r;
    }

    private void promote(DashboardRelease r, Stage stage, PromotionOutcome outcome, Instant deployedAt) {
        promotions.add(new Promotion(UUID.randomUUID(), r.id(), stage.id(), outcome, deployedAt, deployedAt, "acc", null,
                null, null));
    }

    private DashboardService.DashboardView view(boolean all) {
        when(repository.loadLedger()).thenReturn(new DashboardLedger(releases, ladder, promotions));
        when(repository.findChurnItems(anyCollection())).thenReturn(List.of());
        return service.releases(all);
    }

    private DashboardRelease shippedDaysAgo(long days) {
        Instant frozen = NOW.minus(Duration.ofDays(days + 5));
        DashboardRelease r = release(frozen.minus(Duration.ofDays(1)), frozen);
        promote(r, prod, PromotionOutcome.SUCCESS, NOW.minus(Duration.ofDays(days)));
        return r;
    }

    private static boolean contains(DashboardService.DashboardView view, DashboardRelease r) {
        return view.releases().stream().anyMatch(m -> m.release().id().equals(r.id()));
    }

    @Test
    void openNeverPromotedReleaseIsVisible() {
        DashboardRelease open = release(NOW.minus(Duration.ofDays(1)), null);

        DashboardService.DashboardView view = view(false);

        assertThat(contains(view, open)).isTrue();
        assertThat(view.releases().getFirst().currentStageId()).isNull();
        assertThat(view.summary().notPromoted()).isEqualTo(1);
    }

    @Test
    void recentlyShippedIsVisibleOldShippedIsHiddenUnlessAll() {
        DashboardRelease recent = shippedDaysAgo(10);
        DashboardRelease old = shippedDaysAgo(100);

        DashboardService.DashboardView byDefault = view(false);
        assertThat(contains(byDefault, recent)).isTrue();
        assertThat(contains(byDefault, old)).isFalse();
        assertThat(byDefault.summary().totalReleases()).isEqualTo(2);
        assertThat(byDefault.summary().visibleReleases()).isEqualTo(1);

        assertThat(contains(service.releases(true), old)).isTrue();
    }

    @Test
    void rolledBackReleaseIsInFlightAgainButKeepsItsShippedDate() {
        Instant frozen = NOW.minus(Duration.ofDays(210));
        DashboardRelease r = release(frozen.minus(Duration.ofDays(1)), frozen);
        Instant shipped = NOW.minus(Duration.ofDays(200));
        promote(r, dev, PromotionOutcome.SUCCESS, shipped.minus(Duration.ofDays(3)));
        promote(r, test, PromotionOutcome.SUCCESS, shipped.minus(Duration.ofDays(2)));
        promote(r, uat, PromotionOutcome.SUCCESS, shipped.minus(Duration.ofDays(1)));
        promote(r, prod, PromotionOutcome.SUCCESS, shipped);
        promote(r, prod, PromotionOutcome.ROLLED_BACK, shipped.plus(Duration.ofHours(1)));

        DashboardService.DashboardView view = view(false);

        assertThat(contains(view, r)).isTrue();
        DashboardService.ReleaseMetrics m = view.releases().getFirst();
        assertThat(m.currentStageId()).isEqualTo(uat.id());
        assertThat(m.shippedAt()).isEqualTo(shipped);
    }

    @Test
    void lastFailedStageClearsOnALaterSuccess() {
        DashboardRelease r = release(NOW.minus(Duration.ofDays(5)), NOW.minus(Duration.ofDays(4)));
        promote(r, dev, PromotionOutcome.SUCCESS, NOW.minus(Duration.ofDays(3)));
        promote(r, test, PromotionOutcome.FAILED, NOW.minus(Duration.ofDays(2)));

        assertThat(view(false).releases().getFirst().lastFailedStageId()).isEqualTo(test.id());

        promote(r, test, PromotionOutcome.SUCCESS, NOW.minus(Duration.ofDays(1)));

        assertThat(service.releases(false).releases().getFirst().lastFailedStageId()).isNull();
    }

    @Test
    void durationsAreNullWhenNotApplicable() {
        release(NOW.minus(Duration.ofDays(2)), null);
        DashboardRelease frozenOnly = release(NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(1)));

        List<DashboardService.ReleaseMetrics> rows = view(false).releases();

        assertThat(rows.get(0).createdToFrozenSeconds()).isNull();
        assertThat(rows.get(0).frozenToProductionSeconds()).isNull();
        assertThat(rows.get(1).release()).isEqualTo(frozenOnly);
        assertThat(rows.get(1).createdToFrozenSeconds()).isEqualTo(Duration.ofDays(1).toSeconds());
        assertThat(rows.get(1).frozenToProductionSeconds()).isNull();
    }

    @Test
    void summaryMediansCoverVisibleReleasesOnly() {
        DashboardRelease recent = shippedDaysAgo(10);
        shippedDaysAgo(100);
        DashboardRelease open = release(NOW.minus(Duration.ofDays(3)), null);

        DashboardService.Summary summary = view(false).summary();

        assertThat(summary.createdToFrozenCount()).isEqualTo(1);
        assertThat(summary.medianCreatedToFrozenSeconds()).isEqualTo((double) Duration.ofDays(1).toSeconds());
        assertThat(summary.frozenToProductionCount()).isEqualTo(1);
        assertThat(summary.medianFrozenToProductionSeconds()).isEqualTo((double) Duration.ofDays(5).toSeconds());
        assertThat(summary.releasesPerStage()).containsOnlyKeys(prod.id()).containsEntry(prod.id(), 1L);
        assertThat(summary.notPromoted()).isEqualTo(1);
        assertThat(recent).isNotNull();
        assertThat(open).isNotNull();
    }

    @Test
    void mediansAreNullWhenNothingHasTheMeasure() {
        release(NOW.minus(Duration.ofDays(1)), null);

        DashboardService.Summary summary = view(false).summary();

        assertThat(summary.medianCreatedToFrozenSeconds()).isNull();
        assertThat(summary.medianFrozenToProductionSeconds()).isNull();
    }

    @Test
    void releasesKeepTheLedgerOrder() {
        DashboardRelease newest = release(NOW.minus(Duration.ofDays(1)), null);
        DashboardRelease older = release(NOW.minus(Duration.ofDays(2)), null);

        assertThat(view(false).releases()).extracting(m -> m.release().id()).containsExactly(newest.id(), older.id());
    }

    @Test
    void ledgerAndChurnItemsAreQueriedOnceWhateverTheReleaseCount() {
        IntStream.range(0, 5).forEach(i -> release(NOW.minus(Duration.ofDays(i + 1)), null));

        view(false);

        verify(repository, times(1)).loadLedger();
        verify(repository, times(1)).findChurnItems(releases.stream().map(DashboardRelease::id).toList());
    }

    @Test
    void churnIsBuiltFromEachReleasesOwnItems() {
        DashboardRelease withItems = release(NOW.minus(Duration.ofDays(1)), null);
        DashboardRelease without = release(NOW.minus(Duration.ofDays(2)), null);
        when(repository.loadLedger()).thenReturn(new DashboardLedger(releases, ladder, promotions));
        when(repository.findChurnItems(anyCollection())).thenReturn(List.of(item(withItems.id(), "A-1"), item(withItems.id(), "A-2")));

        List<DashboardService.ReleaseMetrics> rows = service.releases(false).releases();

        assertThat(rows.get(0).churn().tickets()).isEqualTo(2);
        assertThat(rows.get(1).release()).isEqualTo(without);
        assertThat(rows.get(1).churn()).isEqualTo(ReleaseChurn.of(List.of()));
    }

    @Test
    void deploymentsFetchesOneExtraRowToDetectMore() {
        Instant from = NOW.minus(Duration.ofDays(30));
        UUID stage = UUID.randomUUID();
        when(repository.findDeployments(stage, from, NOW, 51, 0)).thenReturn(rows(51));

        DashboardService.DeploymentPage page = service.deployments(stage, from, NOW, 50, 0);

        assertThat(page.entries()).hasSize(50);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void deploymentsWithoutSurplusHasNoMore() {
        when(repository.findDeployments(eq(null), any(), any(), anyInt(), anyInt())).thenReturn(rows(30));

        DashboardService.DeploymentPage page = service.deployments(null, NOW.minusSeconds(1), NOW, 50, 0);

        assertThat(page.entries()).hasSize(30);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void ticketChurnReturnsThePerTicketRows() {
        UUID releaseId = UUID.randomUUID();
        when(repository.findChurnItems(List.of(releaseId))).thenReturn(List.of(item(releaseId, "A-1")));

        List<TicketChurn> perTicket = service.ticketChurn(releaseId);

        assertThat(perTicket).extracting(TicketChurn::ticketKey).containsExactly("A-1");
    }

    private static ChurnItem item(UUID releaseId, String key) {
        return new ChurnItem(releaseId, key, EnrichmentStatus.LINKED, false, CommitsSource.ADO_COMPLETE,
                HistoryState.AVAILABLE, NOW, 0, 3, 1, 4, null, false);
    }

    private static List<DeploymentRow> rows(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new DeploymentRow(UUID.randomUUID(), UUID.randomUUID(), "R", "1", UUID.randomUUID(), "DEV",
                        false, PromotionOutcome.SUCCESS, NOW, "acc", null, false))
                .toList();
    }
}
