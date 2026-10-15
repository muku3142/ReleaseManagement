package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.DashboardService;
import com.evision.releasemgmt.application.DashboardService.DashboardView;
import com.evision.releasemgmt.application.DashboardService.ReleaseMetrics;
import com.evision.releasemgmt.application.DashboardService.Summary;
import com.evision.releasemgmt.application.EnvironmentLadderService;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class DashboardTool implements AssistantTool {

    static final int MAX_ROWS = 25;

    public record StageRow(String name, int position, boolean production, boolean retired, long releaseCount) {
    }

    public record ReleaseRow(String name, String version, ReleaseStatus status, String currentStage, Instant shippedAt) {
    }

    public record SummaryFigures(int visibleReleases, int totalReleases, Double medianCreatedToFrozenSeconds,
                                 int createdToFrozenCount, Double medianFrozenToProductionSeconds,
                                 int frozenToProductionCount, long notPromoted) {
    }

    public record DashboardResult(String scope, SummaryFigures summary, List<StageRow> stages,
                                  List<ReleaseRow> releases, boolean truncated) {
    }

    private final DashboardService dashboardService;
    private final EnvironmentLadderService ladderService;

    public DashboardTool(DashboardService dashboardService, EnvironmentLadderService ladderService) {
        this.dashboardService = dashboardService;
        this.ladderService = ladderService;
    }

    @Override
    public String name() {
        return "dashboard";
    }

    @Override
    public String description() {
        return "Dashboard figures (counts, median lead times) and ladder stages with release counts. scope: active or all.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object",
                "properties", Map.of("scope", Map.of("type", "string", "enum", List.of("active", "all"))),
                "additionalProperties", false);
    }

    @Override
    public ToolOutput call(Map<String, Object> arguments) {
        Object scope = arguments.getOrDefault("scope", "active");
        if (!"active".equals(scope) && !"all".equals(scope)) {
            throw new ToolArgumentException("scope must be active or all");
        }
        DashboardView view = dashboardService.releases("all".equals(scope));
        Map<UUID, String> stageNames = new java.util.HashMap<>();
        ladderService.ladder().forEach(stage -> stageNames.put(stage.id(), stage.name()));
        view.stages().forEach(stage -> stageNames.putIfAbsent(stage.id(), stage.name()));

        Summary summary = view.summary();
        List<StageRow> stages = view.stages().stream()
                .map(stage -> stageRow(stage, summary.releasesPerStage())).toList();
        List<ReleaseRow> rows = view.releases().stream().limit(MAX_ROWS)
                .map(metrics -> releaseRow(metrics, stageNames)).toList();
        DashboardResult result = new DashboardResult(scope.toString(),
                new SummaryFigures(summary.visibleReleases(), summary.totalReleases(),
                        summary.medianCreatedToFrozenSeconds(), summary.createdToFrozenCount(),
                        summary.medianFrozenToProductionSeconds(), summary.frozenToProductionCount(),
                        summary.notPromoted()),
                stages, rows, view.releases().size() > MAX_ROWS);
        return new ToolOutput(result, "dashboard:" + scope, "ReleaseManagement: dashboard");
    }

    private static StageRow stageRow(Stage stage, Map<UUID, Long> perStage) {
        return new StageRow(stage.name(), stage.position(), stage.production(), stage.retired(),
                perStage.getOrDefault(stage.id(), 0L));
    }

    private static ReleaseRow releaseRow(ReleaseMetrics metrics, Map<UUID, String> stageNames) {
        return new ReleaseRow(metrics.release().name(), metrics.release().version(), metrics.release().status(),
                metrics.currentStageId() == null ? null : stageNames.get(metrics.currentStageId()), metrics.shippedAt());
    }
}
