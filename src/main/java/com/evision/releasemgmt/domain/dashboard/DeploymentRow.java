package com.evision.releasemgmt.domain.dashboard;

import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import java.time.Instant;
import java.util.UUID;

public record DeploymentRow(UUID promotionId, UUID releaseId, String releaseName, String releaseVersion, UUID stageId,
                            String stageName, boolean stageRetired, PromotionOutcome outcome, Instant deployedAt,
                            String recordedBy, String note, boolean corrected) {
}
