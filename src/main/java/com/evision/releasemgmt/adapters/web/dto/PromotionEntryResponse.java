package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.promotion.PromotionOutcome;

import java.time.Instant;
import java.util.UUID;

public record PromotionEntryResponse(UUID id, UUID stageId, String stageName, PromotionOutcome outcome,
                                     Instant deployedAt, Instant recordedAt, String recordedBy, String note,
                                     UUID supersedesId, UUID supersededById, String correctionReason,
                                     boolean effective) {
}
