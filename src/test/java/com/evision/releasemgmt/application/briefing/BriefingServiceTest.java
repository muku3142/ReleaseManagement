package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.domain.briefing.AppBriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingFeed;
import com.evision.releasemgmt.domain.briefing.BriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import com.evision.releasemgmt.domain.briefing.BriefingTrigger;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.ports.BriefingReadPort;
import com.evision.releasemgmt.ports.BriefingRunRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BriefingServiceTest {

    private static final BriefingTask TASK = BriefingTask.JIRA_DIGEST;
    private static final Instant AT_0830 = Instant.parse("2026-10-26T06:30:00Z");
    private static final Instant AT_0850 = Instant.parse("2026-10-26T06:50:00Z");
    private static final Instant AT_0920 = Instant.parse("2026-10-26T07:20:00Z");

    private final Product alpha = new Product(UUID.randomUUID(), "alpha", null);
    private final Product beta = new Product(UUID.randomUUID(), "Beta", null);
    private final Product archived = new Product(UUID.randomUUID(), "Old", Instant.parse("2026-01-01T00:00:00Z"));

    private final BriefingReadPort readPort = mock(BriefingReadPort.class);
    private final BriefingRunRepository runs = mock(BriefingRunRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final BriefingService service = new BriefingService(readPort, runs, products);

    @BeforeEach
    void defaults() {
        when(products.findAll()).thenReturn(List.of(alpha, beta, archived));
        when(readPort.feed(any())).thenAnswer(inv -> new BriefingFeed(inv.getArgument(0), List.of(), false));
        when(runs.findByTask(any())).thenReturn(List.of());
    }

    private void claude(BriefingStatus status, Instant at) {
        when(readPort.feed(TASK)).thenReturn(new BriefingFeed(TASK,
                List.of(new BriefingRun(at, status, "h", 0, null, null, null)), false));
    }

    private static AppBriefingRun run(Product p, BriefingStatus status, String reason, Instant finishedAt) {
        return new AppBriefingRun(UUID.randomUUID(), TASK, p.id(), finishedAt, BriefingTrigger.SLOT, status, reason,
                1, "headline", null, List.of(), finishedAt.minusSeconds(5), finishedAt);
    }

    private BriefingOverview digest() {
        return service.overview().get(0);
    }

    private static List<UUID> latestIds(BriefingOverview o) {
        return o.appLatest().stream().map(v -> v.run().id()).toList();
    }

    @Test
    void appRunNewerThanClaudeOkIsShownOlderIsNot() {
        claude(BriefingStatus.OK, AT_0850);
        AppBriefingRun newer = run(alpha, BriefingStatus.ATTENTION, null, AT_0920);
        AppBriefingRun older = run(beta, BriefingStatus.OK, null, AT_0830);
        when(runs.findByTask(TASK)).thenReturn(List.of(newer, older));

        assertThat(latestIds(digest())).containsExactly(newer.id());
    }

    @Test
    void claudeFailedShowsOlderAppRun() {
        claude(BriefingStatus.FAILED, AT_0850);
        AppBriefingRun older = run(alpha, BriefingStatus.OK, null, AT_0830);
        when(runs.findByTask(TASK)).thenReturn(List.of(older));

        assertThat(latestIds(digest())).containsExactly(older.id());
    }

    @Test
    void emptyOrUnreadableClaudeFeedShowsNewestAppRun() {
        AppBriefingRun newest = run(alpha, BriefingStatus.OK, null, AT_0830);
        AppBriefingRun previous = run(alpha, BriefingStatus.OK, null, AT_0830.minusSeconds(3600));
        when(runs.findByTask(TASK)).thenReturn(List.of(newest, previous));

        assertThat(latestIds(digest())).containsExactly(newest.id());

        when(readPort.feed(TASK)).thenReturn(new BriefingFeed(TASK, List.of(), true));
        assertThat(latestIds(digest())).containsExactly(newest.id());
    }

    @Test
    void claudeCoveredMarkerIsSkippedAndOlderRealRunLosesToClaude() {
        claude(BriefingStatus.OK, AT_0850);
        AppBriefingRun marker = run(alpha, BriefingStatus.SKIPPED, AppBriefingRun.REASON_CLAUDE_COVERED, AT_0920);
        AppBriefingRun real = run(alpha, BriefingStatus.OK, null, AT_0830);
        when(runs.findByTask(TASK)).thenReturn(List.of(marker, real));

        BriefingOverview o = digest();
        assertThat(o.appLatest()).isEmpty();
        assertThat(o.appRuns()).hasSize(2);
    }

    @Test
    void noJiraProjectSkippedRunNewerThanClaudeIsShown() {
        claude(BriefingStatus.OK, AT_0850);
        AppBriefingRun keyless = run(alpha, BriefingStatus.SKIPPED, AppBriefingRun.REASON_NO_JIRA_PROJECT, AT_0920);
        when(runs.findByTask(TASK)).thenReturn(List.of(keyless));

        assertThat(latestIds(digest())).containsExactly(keyless.id());
    }

    @Test
    void archivedProductRunsOnlyInHistoryWithTheirName() {
        AppBriefingRun old = run(archived, BriefingStatus.OK, null, AT_0920);
        when(runs.findByTask(TASK)).thenReturn(List.of(old));

        BriefingOverview o = digest();
        assertThat(o.appLatest()).isEmpty();
        assertThat(o.appRuns()).singleElement().satisfies(v -> {
            assertThat(v.run().id()).isEqualTo(old.id());
            assertThat(v.productName()).isEqualTo("Old");
        });
    }

    @Test
    void latestOrderedByProductNameRunsNewestFirst() {
        AppBriefingRun betaRun = run(beta, BriefingStatus.OK, null, AT_0920);
        AppBriefingRun alphaRun = run(alpha, BriefingStatus.OK, null, AT_0830);
        when(runs.findByTask(TASK)).thenReturn(List.of(betaRun, alphaRun));

        BriefingOverview o = digest();
        assertThat(o.appLatest()).extracting(AppRunView::productName).containsExactly("alpha", "Beta");
        assertThat(o.appRuns()).extracting(v -> v.run().id()).containsExactly(betaRun.id(), alphaRun.id());
    }

    @Test
    void fiveTasksInOrderAndDisplayOnlyTasksHaveNoAppRuns() {
        List<BriefingOverview> all = service.overview();

        assertThat(all).extracting(o -> o.claude().task()).containsExactly(BriefingTask.values());
        assertThat(all.get(3).appLatest()).isEmpty();
        assertThat(all.get(3).appRuns()).isEmpty();
        assertThat(all.get(4).appRuns()).isEmpty();
        verify(runs, never()).findByTask(BriefingTask.REG_WATCH);
        verify(runs, never()).findByTask(BriefingTask.GULF_INVEST);
    }
}
