package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.promotion.PromotionOutcome;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeploymentPageResponse(List<Entry> entries, boolean hasMore) {

    public record Entry(UUID id, UUID releaseId, String releaseName, String releaseVersion, UUID stageId, String stageName,
                        boolean stageRetired, PromotionOutcome outcome, Instant deployedAt, String recordedBy, String note,
                        boolean corrected) {
    }
}
