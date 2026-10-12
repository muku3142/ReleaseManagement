package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.dashboard.ChurnItem;
import com.evision.releasemgmt.domain.dashboard.DashboardLedger;
import com.evision.releasemgmt.domain.dashboard.DashboardRelease;
import com.evision.releasemgmt.domain.dashboard.DeploymentRow;
import com.evision.releasemgmt.domain.dashboard.ReleaseChurn;
import com.evision.releasemgmt.domain.dashboard.Stats;
import com.evision.releasemgmt.domain.dashboard.TicketChurn;
import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.ports.DashboardReadRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Collectors;

// D-08-08 read model. Every figure comes from this system's tables (criterion 6: no Jira or ADO call on this path).
// Stage, shipped and failed come only from PromotionHistory so the effective/VOIDED/rollback rules exist once.
@Service
public class DashboardService {

    static final Duration SHIPPED_WINDOW = Duration.ofDays(90);   // D-08-11

    public record Summary(int visibleReleases, int totalReleases, Double medianCreatedToFrozenSeconds, int createdToFrozenCount,
                          Double medianFrozenToProductionSeconds, int frozenToProductionCount,
                          Map<UUID, Long> releasesPerStage, long notPromoted) {
    }

    public record ReleaseMetrics(DashboardRelease release, UUID currentStageId, Instant shippedAt, UUID lastFailedStageId,
                                 Long createdToFrozenSeconds, Long frozenToProductionSeconds, ReleaseChurn churn) {
    }

    public record DashboardView(List<Stage> stages, Summary summary, List<ReleaseMetrics> releases) {
    }

    public record DeploymentPage(List<DeploymentRow> entries, boolean hasMore) {
    }

    private record Candidate(DashboardRelease release, Optional<Stage> current, Optional<Instant> shipped,
                             Optional<UUID> lastFailed) {
    }

    private final DashboardReadRepository repository;
    private final Clock clock;

    public DashboardService(DashboardReadRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public DashboardView releases(boolean all) {
        DashboardLedger ledger = repository.loadLedger();
        Map<UUID, List<Promotion>> promotionsByRelease = ledger.promotions().stream()
                .collect(Collectors.groupingBy(Promotion::releaseId));
        Optional<Stage> production = ledger.stages().stream().filter(Stage::production).findFirst();
        Instant windowStart = clock.instant().minus(SHIPPED_WINDOW);

        List<Candidate> visible = new ArrayList<>();
        for (DashboardRelease release : ledger.releases()) {
            PromotionHistory history = new PromotionHistory(release.id(), release.status() == ReleaseStatus.FROZEN,
                    release.frozenAt(), ledger.stages(), promotionsByRelease.getOrDefault(release.id(), List.of()));
            Candidate candidate = new Candidate(release, history.currentStage(), history.firstProductionSuccess(),
                    history.latestEffective().filter(p -> p.outcome() == PromotionOutcome.FAILED).map(Promotion::stageId));
            if (all || isVisibleByDefault(candidate, production, windowStart)) {
                visible.add(candidate);
            }
        }

        List<UUID> visibleIds = visible.stream().map(c -> c.release().id()).toList();
        Map<UUID, List<ChurnItem>> itemsByRelease = repository.findChurnItems(visibleIds).stream()
                .collect(Collectors.groupingBy(ChurnItem::releaseId));

        List<ReleaseMetrics> metrics = visible.stream()
                .map(c -> toMetrics(c, ReleaseChurn.of(itemsByRelease.getOrDefault(c.release().id(), List.of()))))
                .toList();
        return new DashboardView(ledger.stages(), summarize(metrics, ledger.stages(), ledger.releases().size()), metrics);
    }

    // D-08-11/D-08-15: in flight, or first shipped within the window; a rolled-back release is in flight again.
    private static boolean isVisibleByDefault(Candidate c, Optional<Stage> production, Instant windowStart) {
        boolean inProduction = c.current().isPresent() && production.isPresent()
                && c.current().get().position() >= production.get().position();
        return c.shipped().isEmpty() || !inProduction || !c.shipped().get().isBefore(windowStart);
    }

    private static ReleaseMetrics toMetrics(Candidate c, ReleaseChurn churn) {
        DashboardRelease r = c.release();
        Long createdToFrozen = r.frozenAt() == null ? null : Duration.between(r.createdAt(), r.frozenAt()).toSeconds();
        Long frozenToProduction = r.frozenAt() != null && c.shipped().isPresent()
                ? Duration.between(r.frozenAt(), c.shipped().get()).toSeconds() : null;
        return new ReleaseMetrics(r, c.current().map(Stage::id).orElse(null), c.shipped().orElse(null),
                c.lastFailed().orElse(null), createdToFrozen, frozenToProduction, churn);
    }

    private static Summary summarize(List<ReleaseMetrics> metrics, List<Stage> stages, int totalReleases) {
        List<Long> createdToFrozen = metrics.stream().map(ReleaseMetrics::createdToFrozenSeconds)
                .filter(Objects::nonNull).toList();
        List<Long> frozenToProduction = metrics.stream().map(ReleaseMetrics::frozenToProductionSeconds)
                .filter(Objects::nonNull).toList();
        Map<UUID, Long> perStage = metrics.stream().filter(m -> m.currentStageId() != null)
                .collect(Collectors.groupingBy(ReleaseMetrics::currentStageId, Collectors.counting()));
        Map<UUID, Long> perStageInLadderOrder = new LinkedHashMap<>();
        stages.stream().sorted(Comparator.comparingInt(Stage::position))
                .filter(s -> perStage.containsKey(s.id()))
                .forEach(s -> perStageInLadderOrder.put(s.id(), perStage.get(s.id())));
        return new Summary(metrics.size(), totalReleases,
                medianOrNull(createdToFrozen), createdToFrozen.size(),
                medianOrNull(frozenToProduction), frozenToProduction.size(),
                perStageInLadderOrder, metrics.stream().filter(m -> m.currentStageId() == null).count());
    }

    private static Double medianOrNull(List<Long> values) {
        OptionalDouble median = Stats.median(values);
        return median.isPresent() ? median.getAsDouble() : null;
    }

    // limit + 1 avoids a count query. OFFSET pages can shift if promotions are recorded between loads;
    // acceptable for an audit list.
    public DeploymentPage deployments(UUID stageId, Instant from, Instant to, int limit, int offset) {
        List<DeploymentRow> rows = repository.findDeployments(stageId, from, to, limit + 1, offset);
        return new DeploymentPage(rows.stream().limit(limit).toList(), rows.size() > limit);
    }

    public List<TicketChurn> ticketChurn(UUID releaseId) {
        return ReleaseChurn.of(repository.findChurnItems(List.of(releaseId))).perTicket();
    }
}
