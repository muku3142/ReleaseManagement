package com.evision.releasemgmt.domain.promotion;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record PromotionHistory(UUID releaseId, boolean frozen, Instant frozenAt, List<Stage> stages, List<Promotion> entries) {

    // D-07-10: a browser clock slightly ahead of the server must not make "now" look like the future.
    public static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(2);

    // D-07-13: "later" is the later deployed_at, tie-broken by recorded_at.
    public static final Comparator<Promotion> LEDGER_ORDER =
            Comparator.comparing(Promotion::deployedAt).thenComparing(Promotion::recordedAt);

    public PromotionHistory {
        stages = stages.stream().sorted(Comparator.comparingInt(Stage::position)).toList();
        entries = List.copyOf(entries);
    }

    public Optional<UUID> supersededBy(UUID promotionId) {
        return entries.stream()
                .filter(e -> promotionId.equals(e.supersedesId()))
                .map(Promotion::id)
                .findFirst();
    }

    public boolean isEffective(Promotion promotion) {
        return supersededBy(promotion.id()).isEmpty();
    }

    public Optional<Stage> currentStage() {
        return stages.reversed().stream().filter(s -> hasStandingSuccess(s.id())).findFirst();
    }

    public Optional<Stage> nextStage() {
        int currentPosition = currentStage().map(Stage::position).orElse(Integer.MIN_VALUE);
        return stages.stream().filter(s -> !s.retired() && s.position() > currentPosition).findFirst();
    }

    public Promotion record(PromotionDraft draft, String actor, Instant now) {
        if (!frozen) {
            throw reject(PromotionRejection.RELEASE_NOT_FROZEN);
        }
        PromotionOutcome outcome = draft.outcome();
        if (outcome == PromotionOutcome.VOIDED) {
            throw reject(PromotionRejection.PROMOTION_OUTCOME_NOT_RECORDABLE);
        }
        // Postgres stores microseconds; truncating keeps in-memory comparisons identical to what is read back.
        Instant nowMicros = now.truncatedTo(ChronoUnit.MICROS);
        Instant deployedAt = draft.deployedAt() == null ? nowMicros : draft.deployedAt().truncatedTo(ChronoUnit.MICROS);
        if (deployedAt.isAfter(nowMicros.plus(FUTURE_TOLERANCE)) || deployedAt.isBefore(frozenAt)) {
            throw reject(PromotionRejection.PROMOTION_DEPLOYED_AT_INVALID);
        }
        Stage target = stages.stream()
                .filter(s -> s.id().equals(draft.stageId()))
                .findFirst()
                .orElseThrow(StageNotFoundException::new);
        if (target.retired()) {
            throw reject(PromotionRejection.PROMOTION_TARGET_RETIRED);
        }
        if (outcome == PromotionOutcome.ROLLED_BACK) {
            checkRollback(target, deployedAt);
        } else if (!isNextOrCurrent(target)) {
            throw reject(PromotionRejection.PROMOTION_STAGE_SKIPPED);
        }
        return new Promotion(UUID.randomUUID(), releaseId, target.id(), outcome, deployedAt, nowMicros, actor,
                trimToNull(draft.note()), null, null);
    }

    private void checkRollback(Stage target, Instant deployedAt) {
        boolean successToRollBack = standingSuccess(target.id())
                .filter(success -> !success.deployedAt().isAfter(deployedAt))
                .isPresent();
        if (!successToRollBack) {
            throw reject(PromotionRejection.PROMOTION_ROLLBACK_WITHOUT_SUCCESS);
        }
        if (!currentStage().filter(target::equals).isPresent()) {
            throw reject(PromotionRejection.PROMOTION_STAGE_SKIPPED);
        }
    }

    private boolean isNextOrCurrent(Stage target) {
        return nextStage().filter(target::equals).isPresent() || currentStage().filter(target::equals).isPresent();
    }

    private boolean hasStandingSuccess(UUID stageId) {
        return standingSuccess(stageId).isPresent();
    }

    // D-07-11/D-07-15: superseded and VOIDED entries count toward nothing; D-07-08: FAILED is ignored.
    private Optional<Promotion> standingSuccess(UUID stageId) {
        return entries.stream()
                .filter(this::isEffective)
                .filter(e -> e.stageId().equals(stageId))
                .filter(e -> e.outcome() == PromotionOutcome.SUCCESS || e.outcome() == PromotionOutcome.ROLLED_BACK)
                .max(LEDGER_ORDER)
                .filter(e -> e.outcome() == PromotionOutcome.SUCCESS);
    }

    private static PromotionRejectedException reject(PromotionRejection reason) {
        return new PromotionRejectedException(reason);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
