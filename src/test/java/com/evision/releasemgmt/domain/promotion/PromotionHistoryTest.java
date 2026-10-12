package com.evision.releasemgmt.domain.promotion;

import static com.evision.releasemgmt.domain.promotion.PromotionOutcome.FAILED;
import static com.evision.releasemgmt.domain.promotion.PromotionOutcome.ROLLED_BACK;
import static com.evision.releasemgmt.domain.promotion.PromotionOutcome.SUCCESS;
import static com.evision.releasemgmt.domain.promotion.PromotionOutcome.VOIDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromotionHistoryTest {

    private static final Instant FROZEN_AT = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private static final UUID RELEASE = UUID.randomUUID();

    private final Stage dev = stage("DEV", 1, false, null);
    private final Stage test = stage("TEST", 2, false, null);
    private final Stage uat = stage("UAT", 3, false, null);
    private final Stage prod = stage("PROD", 4, true, null);

    private static Stage stage(String name, int position, boolean production, Instant retiredAt) {
        return new Stage(UUID.randomUUID(), name, position, production, retiredAt);
    }

    private static Stage retired(Stage s) {
        return new Stage(s.id(), s.name(), s.position(), s.production(), NOW.minus(Duration.ofDays(1)));
    }

    private static Stage at(Stage s, int position) {
        return new Stage(s.id(), s.name(), position, s.production(), s.retiredAt());
    }

    private static Instant day(int d) {
        return Instant.parse("2026-10-01T09:00:00Z").plus(Duration.ofDays(d));
    }

    private static Promotion entry(Stage s, PromotionOutcome outcome, Instant deployedAt, Instant recordedAt) {
        return new Promotion(UUID.randomUUID(), RELEASE, s.id(), outcome, deployedAt, recordedAt, "acc-1", null, null, null);
    }

    private static Promotion entry(Stage s, PromotionOutcome outcome, int deployedDay) {
        return entry(s, outcome, day(deployedDay), day(deployedDay));
    }

    private static Promotion correction(Promotion original, PromotionOutcome outcome) {
        return new Promotion(UUID.randomUUID(), RELEASE, original.stageId(), outcome, original.deployedAt(),
                original.recordedAt().plusSeconds(60), "acc-1", null, original.id(), "typo");
    }

    private PromotionHistory history(List<Stage> stages, Promotion... entries) {
        return new PromotionHistory(RELEASE, true, FROZEN_AT, stages, List.of(entries));
    }

    private PromotionHistory history(Promotion... entries) {
        return history(List.of(dev, test, uat, prod), entries);
    }

    private static PromotionDraft draft(Stage s, PromotionOutcome outcome) {
        return new PromotionDraft(s.id(), outcome, null, null);
    }

    private static void assertRejected(Runnable action, PromotionRejection reason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(PromotionRejectedException.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }

    private List<Promotion> fullLadder() {
        return List.of(entry(dev, SUCCESS, 1), entry(test, SUCCESS, 2), entry(uat, SUCCESS, 3), entry(prod, SUCCESS, 4));
    }

    private PromotionHistory historyOf(List<Promotion> entries) {
        return new PromotionHistory(RELEASE, true, FROZEN_AT, List.of(dev, test, uat, prod), entries);
    }

    @Test
    void noEntriesHasNoCurrentStageAndDevAsNext() {
        PromotionHistory h = history();
        assertThat(h.currentStage()).isEmpty();
        assertThat(h.nextStage()).contains(dev);
    }

    @Test
    void successMovesCurrentStage() {
        PromotionHistory h = history(entry(dev, SUCCESS, 1));
        assertThat(h.currentStage()).contains(dev);
        assertThat(h.nextStage()).contains(test);
    }

    @Test
    void failedNeverAdvances() {
        PromotionHistory h = history(entry(dev, SUCCESS, 1), entry(test, FAILED, 2));
        assertThat(h.currentStage()).contains(dev);
    }

    @Test
    void rollbackLowersTheStage() {
        List<Promotion> entries = new ArrayList<>(fullLadder());
        entries.add(entry(prod, ROLLED_BACK, 5));
        PromotionHistory h = historyOf(entries);
        assertThat(h.currentStage()).contains(uat);
        assertThat(h.nextStage()).contains(prod);
    }

    @Test
    void successAfterRollbackRestoresTheStage() {
        PromotionHistory h = history(entry(dev, SUCCESS, 1), entry(test, SUCCESS, 2), entry(uat, SUCCESS, 3),
                entry(prod, SUCCESS, 4), entry(prod, ROLLED_BACK, 5), entry(prod, SUCCESS, 6));
        assertThat(h.currentStage()).contains(prod);
    }

    @Test
    void equalDeployedAtIsTieBrokenByRecordedAt() {
        Instant same = day(4);
        Promotion success = entry(prod, SUCCESS, same, same.plusSeconds(1));
        Promotion rollback = entry(prod, ROLLED_BACK, same, same.plusSeconds(2));
        assertThat(history(success, rollback).currentStage()).isEmpty();
        Promotion laterSuccess = entry(prod, SUCCESS, same, same.plusSeconds(3));
        assertThat(history(rollback, laterSuccess).currentStage()).contains(prod);
    }

    @Test
    void supersededEntryCountsTowardNothing() {
        Promotion original = entry(dev, SUCCESS, 1);
        Promotion fix = correction(original, FAILED);
        PromotionHistory h = history(original, fix);
        assertThat(h.currentStage()).isEmpty();
        assertThat(h.isEffective(original)).isFalse();
        assertThat(h.isEffective(fix)).isTrue();
        assertThat(h.supersededBy(original.id())).contains(fix.id());
        assertThat(h.supersededBy(fix.id())).isEmpty();
    }

    @Test
    void voidedCorrectionCountsTowardNothing() {
        Promotion original = entry(dev, SUCCESS, 1);
        assertThat(history(original, correction(original, VOIDED)).currentStage()).isEmpty();
    }

    @Test
    void ladderOrderIsTheCurrentOrderNotTheOrderAtRecordTime() {
        List<Stage> reordered = List.of(at(test, 1), at(dev, 2), uat, prod);
        PromotionHistory h = history(reordered, entry(dev, SUCCESS, 1), entry(test, SUCCESS, 2));
        assertThat(h.currentStage()).map(Stage::name).contains("DEV");
    }

    @Test
    void retiredStageIsSkippedAsNextTarget() {
        PromotionHistory h = history(List.of(dev, retired(test), uat, prod), entry(dev, SUCCESS, 1));
        assertThat(h.nextStage()).contains(uat);
    }

    @Test
    void retiredStageStillCountsInHistory() {
        PromotionHistory h = history(List.of(dev, retired(test), uat, prod), entry(dev, SUCCESS, 1), entry(test, SUCCESS, 2));
        assertThat(h.currentStage()).map(Stage::name).contains("TEST");
        assertThat(h.nextStage()).contains(uat);
    }

    @Test
    void recordOnOpenReleaseIsRejected() {
        PromotionHistory open = new PromotionHistory(RELEASE, false, null, List.of(dev, test, uat, prod), List.of());
        assertRejected(() -> open.record(draft(dev, SUCCESS), "acc-1", NOW), PromotionRejection.RELEASE_NOT_FROZEN);
    }

    @Test
    void voidedOutcomeIsNotRecordable() {
        assertRejected(() -> history().record(draft(dev, VOIDED), "acc-1", NOW),
                PromotionRejection.PROMOTION_OUTCOME_NOT_RECORDABLE);
    }

    @Test
    void firstEntryMustTargetTheFirstStage() {
        assertRejected(() -> history().record(draft(test, SUCCESS), "acc-1", NOW), PromotionRejection.PROMOTION_STAGE_SKIPPED);
        assertThat(history().record(draft(dev, SUCCESS), "acc-1", NOW).stageId()).isEqualTo(dev.id());
    }

    @Test
    void skippingAStageIsRejectedNextAndRedeployAreAccepted() {
        PromotionHistory h = history(entry(dev, SUCCESS, 1));
        assertRejected(() -> h.record(draft(uat, SUCCESS), "acc-1", NOW), PromotionRejection.PROMOTION_STAGE_SKIPPED);
        assertThat(h.record(draft(test, SUCCESS), "acc-1", NOW).stageId()).isEqualTo(test.id());
        assertThat(h.record(draft(dev, SUCCESS), "acc-1", NOW).stageId()).isEqualTo(dev.id());
    }

    @Test
    void failedFollowsTheSameSequenceRules() {
        PromotionHistory h = history(entry(dev, SUCCESS, 1));
        assertRejected(() -> h.record(draft(uat, FAILED), "acc-1", NOW), PromotionRejection.PROMOTION_STAGE_SKIPPED);
        assertThat(h.record(draft(test, FAILED), "acc-1", NOW).outcome()).isEqualTo(FAILED);
    }

    @Test
    void retiredTargetIsRejectedEvenWhenItWouldBeNext() {
        PromotionHistory h = history(List.of(dev, retired(test), uat, prod), entry(dev, SUCCESS, 1));
        assertRejected(() -> h.record(draft(test, SUCCESS), "acc-1", NOW), PromotionRejection.PROMOTION_TARGET_RETIRED);
    }

    @Test
    void unknownStageIsNotFound() {
        assertThatThrownBy(() -> history().record(new PromotionDraft(UUID.randomUUID(), SUCCESS, null, null), "acc-1", NOW))
                .isInstanceOf(StageNotFoundException.class);
    }

    @Test
    void rollbackDatedAtOrAfterTheStandingSuccessIsAccepted() {
        PromotionHistory h = historyOf(fullLadder());
        Instant successAt = day(4);
        assertThat(h.record(new PromotionDraft(prod.id(), ROLLED_BACK, successAt, null), "acc-1", NOW).outcome())
                .isEqualTo(ROLLED_BACK);
        assertThat(h.record(new PromotionDraft(prod.id(), ROLLED_BACK, successAt.plusSeconds(1), null), "acc-1", NOW).outcome())
                .isEqualTo(ROLLED_BACK);
    }

    @Test
    void rollbackDatedBeforeTheStandingSuccessIsRejected() {
        PromotionHistory h = historyOf(fullLadder());
        assertRejected(() -> h.record(new PromotionDraft(prod.id(), ROLLED_BACK, day(4).minusSeconds(1), null), "acc-1", NOW),
                PromotionRejection.PROMOTION_ROLLBACK_WITHOUT_SUCCESS);
    }

    @Test
    void rollbackWithNoSuccessOnThatStageIsRejected() {
        PromotionHistory h = history(entry(dev, SUCCESS, 1));
        assertRejected(() -> h.record(draft(test, ROLLED_BACK), "acc-1", NOW),
                PromotionRejection.PROMOTION_ROLLBACK_WITHOUT_SUCCESS);
    }

    @Test
    void rollbackOfAnAlreadyRolledBackSuccessIsRejected() {
        PromotionHistory h = history(entry(dev, SUCCESS, 1), entry(dev, ROLLED_BACK, 2));
        assertRejected(() -> h.record(draft(dev, ROLLED_BACK), "acc-1", NOW),
                PromotionRejection.PROMOTION_ROLLBACK_WITHOUT_SUCCESS);
    }

    @Test
    void rollbackOfANonCurrentStageIsRejectedAsSkipped() {
        PromotionHistory h = historyOf(fullLadder());
        assertRejected(() -> h.record(draft(uat, ROLLED_BACK), "acc-1", NOW), PromotionRejection.PROMOTION_STAGE_SKIPPED);
    }

    @Test
    void deployedAtDefaultsToNow() {
        assertThat(history().record(draft(dev, SUCCESS), "acc-1", NOW).deployedAt()).isEqualTo(NOW);
    }

    @Test
    void deployedAtBoundedByClockToleranceAndFreeze() {
        PromotionHistory h = history();
        assertThat(h.record(new PromotionDraft(dev.id(), SUCCESS, NOW.plus(Duration.ofMinutes(1)), null), "acc-1", NOW)
                .deployedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertRejected(() -> h.record(new PromotionDraft(dev.id(), SUCCESS, NOW.plus(Duration.ofMinutes(3)), null), "acc-1", NOW),
                PromotionRejection.PROMOTION_DEPLOYED_AT_INVALID);
        assertRejected(() -> h.record(new PromotionDraft(dev.id(), SUCCESS, FROZEN_AT.minusSeconds(1), null), "acc-1", NOW),
                PromotionRejection.PROMOTION_DEPLOYED_AT_INVALID);
        assertThat(h.record(new PromotionDraft(dev.id(), SUCCESS, FROZEN_AT, null), "acc-1", NOW).deployedAt())
                .isEqualTo(FROZEN_AT);
    }

    @Test
    void acceptedEntryCarriesServerSideProvenance() {
        Promotion p = history().record(new PromotionDraft(dev.id(), SUCCESS, null, "  hi  "), "acc-7", NOW);
        assertThat(p.id()).isNotNull();
        assertThat(p.releaseId()).isEqualTo(RELEASE);
        assertThat(p.recordedAt()).isEqualTo(NOW);
        assertThat(p.recordedBy()).isEqualTo("acc-7");
        assertThat(p.supersedesId()).isNull();
        assertThat(p.correctionReason()).isNull();
        assertThat(p.note()).isEqualTo("hi");
    }

    @Test
    void correctingAnUnknownEntryIsNotFound() {
        assertThatThrownBy(() -> history().correct(UUID.randomUUID(), new PromotionDraft(dev.id(), SUCCESS, day(1), null), "typo", "acc-1", NOW))
                .isInstanceOf(PromotionNotFoundException.class);
    }

    @Test
    void anEntryCannotBeCorrectedTwiceButItsCorrectionCan() {
        Promotion original = entry(dev, SUCCESS, 1);
        Promotion fix = correction(original, FAILED);
        PromotionHistory h = history(original, fix);
        PromotionDraft again = new PromotionDraft(dev.id(), SUCCESS, day(1), null);

        assertRejected(() -> h.correct(original.id(), again, "typo", "acc-1", NOW), PromotionRejection.PROMOTION_ALREADY_SUPERSEDED);
        assertThat(h.correct(fix.id(), again, "typo", "acc-1", NOW).supersedesId()).isEqualTo(fix.id());
    }

    @Test
    void correctionSkipsTheLadderSequenceRules() {
        Promotion devOk = entry(dev, SUCCESS, 1);
        Promotion testOk = entry(test, SUCCESS, 2);
        PromotionHistory h = history(devOk, testOk);

        Promotion fix = h.correct(devOk.id(), new PromotionDraft(dev.id(), FAILED, day(1), null), "wrong result", "acc-1", NOW);

        PromotionHistory after = history(devOk, testOk, fix);
        assertThat(after.currentStage()).contains(test);
    }

    @Test
    void correctingToVoidedRemovesBothFromEffect() {
        Promotion devOk = entry(dev, SUCCESS, 1);
        PromotionHistory h = history(devOk);

        Promotion voided = h.correct(devOk.id(), new PromotionDraft(dev.id(), VOIDED, day(1), null), "never happened", "acc-1", NOW);

        PromotionHistory after = history(devOk, voided);
        assertThat(voided.outcome()).isEqualTo(VOIDED);
        assertThat(after.isEffective(devOk)).isFalse();
        assertThat(after.currentStage()).isEmpty();
    }

    @Test
    void correctionMayKeepItsOwnRetiredStageButNotMoveToAnotherRetiredOne() {
        Promotion devOk = entry(dev, SUCCESS, 1);
        PromotionHistory h = history(List.of(retired(dev), retired(test), uat, prod), devOk);

        assertThat(h.correct(devOk.id(), new PromotionDraft(dev.id(), VOIDED, day(1), null), "typo", "acc-1", NOW).stageId())
                .isEqualTo(dev.id());
        assertRejected(() -> h.correct(devOk.id(), new PromotionDraft(test.id(), SUCCESS, day(1), null), "typo", "acc-1", NOW),
                PromotionRejection.PROMOTION_TARGET_RETIRED);
    }

    @Test
    void correctionDeployedAtIsBounded() {
        Promotion devOk = entry(dev, SUCCESS, 1);
        PromotionHistory h = history(devOk);

        assertRejected(() -> h.correct(devOk.id(), new PromotionDraft(dev.id(), SUCCESS, NOW.plus(Duration.ofMinutes(3)), null), "typo", "acc-1", NOW),
                PromotionRejection.PROMOTION_DEPLOYED_AT_INVALID);
        assertRejected(() -> h.correct(devOk.id(), new PromotionDraft(dev.id(), SUCCESS, FROZEN_AT.minusSeconds(1), null), "typo", "acc-1", NOW),
                PromotionRejection.PROMOTION_DEPLOYED_AT_INVALID);
    }

    @Test
    void acceptedCorrectionCarriesProvenance() {
        Promotion devOk = entry(dev, SUCCESS, 1);

        Promotion fix = history(devOk).correct(devOk.id(), new PromotionDraft(dev.id(), FAILED, day(2), "  "), "  wrong  ", "acc-9", NOW);

        assertThat(fix.id()).isNotEqualTo(devOk.id());
        assertThat(fix.supersedesId()).isEqualTo(devOk.id());
        assertThat(fix.correctionReason()).isEqualTo("wrong");
        assertThat(fix.recordedAt()).isEqualTo(NOW);
        assertThat(fix.recordedBy()).isEqualTo("acc-9");
        assertThat(fix.note()).isNull();
    }

    @Test
    void blankNoteBecomesNull() {
        assertThat(history().record(new PromotionDraft(dev.id(), SUCCESS, null, "   "), "acc-1", NOW).note()).isNull();
    }

    @Test
    void timestampsAreTruncatedToMicroseconds() {
        Instant nanos = NOW.plusNanos(123_456_789);
        Promotion p = history().record(draft(dev, SUCCESS), "acc-1", nanos);
        assertThat(p.recordedAt()).isEqualTo(NOW.plusNanos(123_456_000));
        assertThat(p.deployedAt()).isEqualTo(p.recordedAt());
    }

    @Test
    void firstProductionSuccessIgnoresRepromotion() {
        assertThat(history(entry(prod, SUCCESS, 3), entry(prod, SUCCESS, 5)).firstProductionSuccess()).contains(day(3));
    }

    @Test
    void firstProductionSuccessIgnoresFailedAttempts() {
        assertThat(history(entry(prod, FAILED, 2), entry(prod, SUCCESS, 4)).firstProductionSuccess()).contains(day(4));
    }

    @Test
    void firstProductionSuccessSurvivesRollback() {
        PromotionHistory h = history(entry(prod, SUCCESS, 4), entry(prod, ROLLED_BACK, 6));
        assertThat(h.firstProductionSuccess()).contains(day(4));
        assertThat(h.currentStage()).isNotEqualTo(java.util.Optional.of(prod));
    }

    @Test
    void firstProductionSuccessIsEmptyWhenOnlySuccessIsVoided() {
        Promotion success = entry(prod, SUCCESS, 4);
        assertThat(history(success, correction(success, VOIDED)).firstProductionSuccess()).isEmpty();
    }

    @Test
    void firstProductionSuccessFollowsTheCurrentProductionFlag() {
        Stage uatProd = new Stage(uat.id(), "UAT", 3, true, null);
        Stage prodNotProd = new Stage(prod.id(), "PROD", 4, false, null);
        List<Stage> ladder = List.of(dev, test, uatProd, prodNotProd);
        assertThat(history(ladder, entry(uat, SUCCESS, 3), entry(prod, SUCCESS, 4)).firstProductionSuccess())
                .contains(day(3));
        assertThat(history(ladder, entry(prod, SUCCESS, 4)).firstProductionSuccess()).isEmpty();
    }

    @Test
    void firstProductionSuccessIsEmptyWithoutProductionStage() {
        assertThat(history(List.of(dev, test), entry(dev, SUCCESS, 1)).firstProductionSuccess()).isEmpty();
    }

    @Test
    void latestEffectiveIsTheFailedEntryUntilALaterSuccess() {
        Promotion failed = entry(test, FAILED, 2);
        assertThat(history(entry(dev, SUCCESS, 1), failed).latestEffective()).contains(failed);
        Promotion success = entry(test, SUCCESS, 3);
        assertThat(history(entry(dev, SUCCESS, 1), failed, success).latestEffective()).contains(success);
    }

    @Test
    void latestEffectiveIgnoresVoidedCorrectionAndItsOriginal() {
        Promotion devSuccess = entry(dev, SUCCESS, 1);
        Promotion failed = entry(test, FAILED, 2);
        assertThat(history(devSuccess, failed, correction(failed, VOIDED)).latestEffective()).contains(devSuccess);
    }

    @Test
    void latestEffectiveBreaksDeployedAtTiesByRecordedAt() {
        Promotion earlier = entry(dev, SUCCESS, day(1), day(1));
        Promotion later = entry(test, FAILED, day(1), day(1).plusSeconds(5));
        assertThat(history(earlier, later).latestEffective()).contains(later);
    }

    @Test
    void latestEffectiveIsEmptyWithoutEntries() {
        assertThat(history().latestEffective()).isEmpty();
    }
}
