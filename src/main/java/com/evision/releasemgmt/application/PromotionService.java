package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.promotion.PromotionDraft;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.ports.PromotionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class PromotionService {

    private final PromotionRepository promotions;
    private final Clock clock;

    public PromotionService(PromotionRepository promotions, Clock clock) {
        this.promotions = promotions;
        this.clock = clock;
    }

    public PromotionHistory history(UUID releaseId) {
        return promotions.load(releaseId);
    }

    public PromotionHistory correct(UUID releaseId, UUID promotionId, PromotionDraft draft, String reason, String actor) {
        Instant now = clock.instant();
        promotions.appendUnderLock(releaseId, history -> history.correct(promotionId, draft, reason, actor, now));
        return promotions.load(releaseId);
    }

    public PromotionHistory record(UUID releaseId, PromotionDraft draft, String actor) {
        Instant now = clock.instant();
        promotions.appendUnderLock(releaseId, history -> history.record(draft, actor, now));
        return promotions.load(releaseId);
    }
}
