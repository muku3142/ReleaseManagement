package com.evision.releasemgmt.domain.promotion;

import java.time.Instant;
import java.util.UUID;

/** deployedAt null means "now" (D-07-10). */
public record PromotionDraft(UUID stageId, PromotionOutcome outcome, Instant deployedAt, String note) {
}
