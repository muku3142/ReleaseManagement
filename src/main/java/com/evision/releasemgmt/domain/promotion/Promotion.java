package com.evision.releasemgmt.domain.promotion;

import java.time.Instant;
import java.util.UUID;

public record Promotion(UUID id, UUID releaseId, UUID stageId, PromotionOutcome outcome, Instant deployedAt,
                        Instant recordedAt, String recordedBy, String note, UUID supersedesId, String correctionReason) {
}
