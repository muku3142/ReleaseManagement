package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;

import java.util.UUID;
import java.util.function.Function;

// D-07-11: append-only by construction. There is deliberately no update, delete, remove or save here -
// a mutation method on this port is a defect, because AUDIT-03's guarantee rests on there being no way
// to alter a stored entry. Corrections are new entries that supersede old ones.
public interface PromotionRepository {

    /**
     * The release's ladder and every entry, oldest recorded first.
     *
     * @throws com.evision.releasemgmt.domain.release.ReleaseNotFoundException when the release does not exist
     */
    PromotionHistory load(UUID releaseId);

    /** Locks the release row, builds its history, applies decide, inserts the result; one transaction. Insert only. */
    Promotion appendUnderLock(UUID releaseId, Function<PromotionHistory, Promotion> decide);
}
