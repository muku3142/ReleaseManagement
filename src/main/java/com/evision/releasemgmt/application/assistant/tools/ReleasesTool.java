package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.PromotionService;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class ReleasesTool implements AssistantTool {

    static final int MAX_ROWS = 25;

    public record ReleaseRow(UUID id, String name, String version, ReleaseStatus status, String projectKey,
                             Instant createdAt, Instant frozenAt, Instant lastRefreshedAt, int itemCount) {
    }

    public record ReleaseList(List<ReleaseRow> releases, boolean truncated) {
    }

    public record ReleaseDetail(UUID id, String name, String version, ReleaseStatus status, Instant createdAt,
                                Instant frozenAt, Instant lastRefreshedAt, Instant itemsChangedAt, int ticketCount,
                                int manualItemCount, Map<String, Long> ticketsByStatusCategory,
                                String currentStage, String nextStage, Instant shippedAt) {
    }

    private final ReleaseService releaseService;
    private final PromotionService promotionService;
    private final ReleaseRef releaseRef;

    public ReleasesTool(ReleaseService releaseService, PromotionService promotionService, ReleaseRef releaseRef) {
        this.releaseService = releaseService;
        this.promotionService = promotionService;
        this.releaseRef = releaseRef;
    }

    @Override
    public String name() {
        return "releases";
    }

    @Override
    public String description() {
        return "Releases: list, or with `release` its status, dates, ticket counts and current stage.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object",
                "properties", Map.of("release", Map.of("type", "string", "maxLength", ReleaseRef.MAX_LENGTH)),
                "additionalProperties", false);
    }

    @Override
    public ToolOutput call(Map<String, Object> arguments) {
        Object release = arguments.get("release");
        if (release == null) {
            return new ToolOutput(list(), "releases:all", "ReleaseManagement: releases");
        }
        UUID id = releaseRef.resolve(release);
        Release found = releaseService.get(id);
        return new ToolOutput(detail(found), "releases:" + id, "ReleaseManagement: release " + found.name() + " " + found.version());
    }

    private ReleaseList list() {
        List<ReleaseSummary> all = releaseService.search("");
        List<ReleaseRow> rows = all.stream().limit(MAX_ROWS)
                .map(s -> new ReleaseRow(s.id(), s.name(), s.version(), s.status(), s.jiraProjectKey(),
                        s.createdAt(), s.frozenAt(), s.lastRefreshedAt(), s.itemCount()))
                .toList();
        return new ReleaseList(rows, all.size() > MAX_ROWS);
    }

    private ReleaseDetail detail(Release release) {
        PromotionHistory history = promotionService.history(release.id());
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (ReleaseItem item : release.items()) {
            StatusCategory category = item.ticket().statusCategory();
            byCategory.merge(category == null ? "UNKNOWN" : category.name(), 1L, Long::sum);
        }
        return new ReleaseDetail(release.id(), release.name(), release.version(), release.status(), release.createdAt(),
                release.frozenAt(), release.lastRefreshedAt(), release.itemsChangedAt(), release.items().size(),
                release.manualItems().size(), byCategory,
                history.currentStage().map(Stage::name).orElse(null),
                history.nextStage().map(Stage::name).orElse(null),
                history.firstProductionSuccess().orElse(null));
    }
}
