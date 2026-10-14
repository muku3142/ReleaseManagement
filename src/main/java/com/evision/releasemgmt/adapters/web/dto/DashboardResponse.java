package com.evision.releasemgmt.adapters.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(Summary summary, List<StageResponse> stages, List<ReleaseRow> releases) {

    public record Summary(int visibleReleases, int totalReleases, Double medianCreatedToFrozenSeconds, int createdToFrozenCount,
                          Double medianFrozenToProductionSeconds, int frozenToProductionCount,
                          List<StageCount> releasesPerStage, long notPromoted) {
    }

    public record StageCount(UUID stageId, long count) {
    }

    public record ReleaseRow(UUID id, String name, String version, String status, Instant createdAt, Instant frozenAt,
                             UUID currentStageId, Instant shippedAt, UUID lastFailedStageId,
                             Long createdToFrozenSeconds, Long frozenToProductionSeconds,
                             Composition composition, Churn churn, StoryPoints storyPoints) {
    }

    public record Composition(int tickets, long manual, int linked, int noLink, int failed, int noData) {
    }

    public record StoryPoints(BigDecimal total, int estimated, int unestimated, int unread) {
    }

    public record Churn(Double medianCommitsToDone, int commitNoData, long reopens, int reopenNoData,
                        Double postDoneShare, int outliers, int keyTagged) {
    }
}
