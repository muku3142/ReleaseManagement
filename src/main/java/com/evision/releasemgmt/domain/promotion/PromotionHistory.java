package com.evision.releasemgmt.domain.promotion;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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

    // D-08-01/D-08-16: shipped = the earliest effective SUCCESS on the stage flagged Production now; a later rollback does not move it.
    public Optional<Instant> firstProductionSuccess() {
        return stages.stream().filter(Stage::production).findFirst()
                .flatMap(prod -> entries.stream()
                        .filter(this::isEffective)
                        .filter(e -> e.stageId().equals(prod.id()) && e.outcome() == PromotionOutcome.SUCCESS)
                        .map(Promotion::deployedAt)
                        .min(Comparator.naturalOrder()));
    }

    // D-08-02: "last promotion failed" = this is FAILED. VOIDED entries count toward nothing (D-07-15).
    public Optional<Promotion> latestEffective() {
        return entries.stream()
                .filter(this::isEffective)
                .filter(e -> e.outcome() != PromotionOutcome.VOIDED)
                .max(LEDGER_ORDER);
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
        requireDeployedAtInBounds(deployedAt, nowMicros);
        Stage target = findStage(draft.stageId());
        if (target.retired()) {
            throw reject(PromotionRejection.PROMOTION_TARGET_RETIRED);
        }
        if (outcome == PromotionOutcome.ROLLED_BACK) {
            checkRollback(target, deployedAt);
        } else if (isBeforeCurrent(target)) {
            throw reject(PromotionRejection.PROMOTION_STAGE_EARLIER);
        }
        return new Promotion(UUID.randomUUID(), releaseId, target.id(), outcome, deployedAt, nowMicros, actor,
                trimToNull(draft.note()), null, null);
    }

    // D-07-14: a correction passes field checks only, never the ladder-sequence rules - replaying the ladder rule (D-15.1-03) over corrected
    // history would make early entries impossible to fix.
    public Promotion correct(UUID originalId, PromotionDraft draft, String reason, String actor, Instant now) {
        Promotion original = entries.stream()
                .filter(e -> e.id().equals(originalId))
                .findFirst()
                .orElseThrow(PromotionNotFoundException::new);
        if (!isEffective(original)) {
            throw reject(PromotionRejection.PROMOTION_ALREADY_SUPERSEDED);
        }
        Instant nowMicros = now.truncatedTo(ChronoUnit.MICROS);
        Instant deployedAt = Objects.requireNonNull(draft.deployedAt()).truncatedTo(ChronoUnit.MICROS);
        requireDeployedAtInBounds(deployedAt, nowMicros);
        Stage target = findStage(draft.stageId());
        // The entry may keep its own stage even once retired, otherwise it could never be voided or re-timed.
        if (target.retired() && !target.id().equals(original.stageId())) {
            throw reject(PromotionRejection.PROMOTION_TARGET_RETIRED);
        }
        return new Promotion(UUID.randomUUID(), releaseId, target.id(), draft.outcome(), deployedAt, nowMicros, actor,
                trimToNull(draft.note()), originalId, reason.strip());
    }

    private void requireDeployedAtInBounds(Instant deployedAt, Instant nowMicros) {
        if (deployedAt.isAfter(nowMicros.plus(FUTURE_TOLERANCE)) || deployedAt.isBefore(frozenAt)) {
            throw reject(PromotionRejection.PROMOTION_DEPLOYED_AT_INVALID);
        }
    }

    private Stage findStage(UUID stageId) {
        return stages.stream()
                .filter(s -> s.id().equals(stageId))
                .findFirst()
                .orElseThrow(StageNotFoundException::new);
    }

    private void checkRollback(Stage target, Instant deployedAt) {
        boolean successToRollBack = standingSuccess(target.id())
                .filter(success -> !success.deployedAt().isAfter(deployedAt))
                .isPresent();
        if (!successToRollBack) {
            throw reject(PromotionRejection.PROMOTION_ROLLBACK_WITHOUT_SUCCESS);
        }
        if (!currentStage().filter(target::equals).isPresent()) {
            throw reject(PromotionRejection.PROMOTION_STAGE_EARLIER);
        }
    }

    // D-15.1-03: any later active stage or the current one; only going back is refused (ROLLED_BACK is the way back).
    private boolean isBeforeCurrent(Stage target) {
        return currentStage().filter(current -> target.position() < current.position()).isPresent();
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
