package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.DashboardService;
import com.evision.releasemgmt.application.DashboardService.DeploymentPage;
import com.evision.releasemgmt.application.EnvironmentLadderService;
import com.evision.releasemgmt.application.PromotionService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.domain.dashboard.DeploymentRow;
import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.promotion.Stage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class DeploymentsTool implements AssistantTool {

    static final int LIMIT = 25;
    static final int MIN_DAYS = 1;
    static final int MAX_DAYS = 366;
    static final int DEFAULT_DAYS = 30;
    static final int STAGE_MAX_LENGTH = 60;

    public record HistoryEntry(String stage, PromotionOutcome outcome, Instant deployedAt, Instant recordedAt,
                               String recordedBy, boolean corrected, boolean superseded) {
    }

    public record ReleaseHistory(String currentStage, List<HistoryEntry> entries) {
    }

    public record RecentRow(String releaseName, String releaseVersion, String stage, PromotionOutcome outcome,
                            Instant deployedAt, String recordedBy, boolean corrected) {
    }

    public record RecentDeployments(int days, List<RecentRow> entries, boolean hasMore) {
    }

    private final PromotionService promotionService;
    private final DashboardService dashboardService;
    private final EnvironmentLadderService ladderService;
    private final ActorResolutionService actors;
    private final ReleaseRef releaseRef;
    private final Clock clock;

    public DeploymentsTool(PromotionService promotionService, DashboardService dashboardService,
                           EnvironmentLadderService ladderService, ActorResolutionService actors,
                           ReleaseRef releaseRef, Clock clock) {
        this.promotionService = promotionService;
        this.dashboardService = dashboardService;
        this.ladderService = ladderService;
        this.actors = actors;
        this.releaseRef = releaseRef;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "deployments";
    }

    @Override
    public String description() {
        return "Deployments: promotion history of `release`, else recent ones by `stage` over `days` (default 30).";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "release", Map.of("type", "string", "maxLength", ReleaseRef.MAX_LENGTH),
                        "stage", Map.of("type", "string", "maxLength", STAGE_MAX_LENGTH),
                        "days", Map.of("type", "integer")),
                "additionalProperties", false);
    }

    @Override
    public ToolOutput call(Map<String, Object> arguments) {
        Object release = arguments.get("release");
        if (release != null) {
            UUID id = releaseRef.resolve(release);
            return new ToolOutput(history(promotionService.history(id)), "deployments:" + id,
                    "ReleaseManagement: deployment history");
        }
        return new ToolOutput(recent(arguments.get("stage"), days(arguments.get("days"))), "deployments:recent",
                "ReleaseManagement: recent deployments");
    }

    private ReleaseHistory history(PromotionHistory history) {
        ResolvedActors resolved = actors.resolve(history.entries().stream().map(Promotion::recordedBy)
                .toArray(String[]::new));
        Map<UUID, String> stageNames = history.stages().stream().collect(Collectors.toMap(Stage::id, Stage::name));
        List<HistoryEntry> entries = history.entries().stream()
                .sorted(PromotionHistory.LEDGER_ORDER)
                .map(p -> new HistoryEntry(stageNames.get(p.stageId()), p.outcome(), p.deployedAt(), p.recordedAt(),
                        resolved.display(p.recordedBy()), p.supersedesId() != null, !history.isEffective(p)))
                .toList();
        return new ReleaseHistory(history.currentStage().map(Stage::name).orElse(null), entries);
    }

    private RecentDeployments recent(Object stageArgument, int days) {
        UUID stageId = stageArgument == null ? null : stageId(stageArgument.toString().trim());
        Instant now = clock.instant();
        DeploymentPage page = dashboardService.deployments(stageId, now.minus(Duration.ofDays(days)), now, LIMIT, 0);
        ResolvedActors resolved = actors.resolve(page.entries().stream().map(DeploymentRow::recordedBy)
                .toArray(String[]::new));
        List<RecentRow> rows = page.entries().stream()
                .map(r -> new RecentRow(r.releaseName(), r.releaseVersion(), r.stageName(), r.outcome(),
                        r.deployedAt(), resolved.display(r.recordedBy()), r.corrected()))
                .toList();
        return new RecentDeployments(days, rows, page.hasMore());
    }

    private UUID stageId(String name) {
        List<Stage> ladder = ladderService.ladder();
        return ladder.stream().filter(s -> s.name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))
                .findFirst().map(Stage::id)
                .orElseThrow(() -> new ToolArgumentException("unknown stage '" + name + "', stages: "
                        + ladder.stream().map(Stage::name).collect(Collectors.joining(", "))));
    }

    private static int days(Object value) {
        if (value == null) {
            return DEFAULT_DAYS;
        }
        if (!(value instanceof Number number) || number.intValue() < MIN_DAYS || number.intValue() > MAX_DAYS) {
            throw new ToolArgumentException("days must be " + MIN_DAYS + " to " + MAX_DAYS);
        }
        return number.intValue();
    }
}
