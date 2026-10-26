package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.application.PromotionService;
import com.evision.releasemgmt.domain.promotion.PromotionDraft;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.promotion.PromotionNotFoundException;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.promotion.PromotionRejectedException;
import com.evision.releasemgmt.domain.promotion.PromotionRejection;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.ports.PromotionRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class PromotionPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private PromotionService service;

    @Autowired
    private PromotionRepository promotions;

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        jdbc.execute("TRUNCATE release CASCADE");
    }

    private UUID release(String name, boolean freeze) {
        Release release = Release.create(name, "1.0", "CNV", "rm@acme.com", Instant.now().minus(1, ChronoUnit.HOURS));
        releaseRepository.create(release, List.of());
        if (freeze) {
            releaseRepository.freeze(release.id(), "rm@acme.com", Instant.now().minus(30, ChronoUnit.MINUTES));
        }
        return release.id();
    }

    private UUID stageId(String name) {
        return jdbc.queryForObject("SELECT id FROM environment_stage WHERE name = ?", UUID.class, name);
    }

    private PromotionDraft draft(String stage, PromotionOutcome outcome) {
        return new PromotionDraft(stageId(stage), outcome, null, null);
    }

    private int promotionCount() {
        return jdbc.queryForObject("SELECT count(*) FROM promotion", Integer.class);
    }

    @Test
    void recordsAndLoadsAnEntryOnAFrozenRelease() {
        UUID releaseId = release("R1", true);

        PromotionHistory history = service.record(releaseId, draft("DEV", PromotionOutcome.SUCCESS), "acc-1");

        assertThat(history.entries()).hasSize(1);
        assertThat(history.entries().getFirst().recordedBy()).isEqualTo("acc-1");
        assertThat(history.currentStage()).map(Stage::name).contains("DEV");
    }

    @Test
    void voidingAnEntrySupersedesItAndItCannotBeCorrectedAgain() {
        UUID releaseId = release("R5", true);
        UUID original = service.record(releaseId, draft("DEV", PromotionOutcome.SUCCESS), "acc-1")
                .entries().getFirst().id();
        Instant deployedAt = Instant.now().minus(10, ChronoUnit.MINUTES);
        PromotionDraft voided = new PromotionDraft(stageId("DEV"), PromotionOutcome.VOIDED, deployedAt, null);

        PromotionHistory history = service.correct(releaseId, original, voided, "wrong environment", "acc-2");

        assertThat(history.entries()).hasSize(2);
        assertThat(history.entries().stream().filter(e -> e.id().equals(original)).findFirst().orElseThrow())
                .matches(e -> !history.isEffective(e));
        assertThat(history.currentStage()).isEmpty();
        assertThatThrownBy(() -> service.correct(releaseId, original, voided, "again", "acc-2"))
                .isInstanceOfSatisfying(PromotionRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(PromotionRejection.PROMOTION_ALREADY_SUPERSEDED));
    }

    @Test
    void anEntryOfAnotherReleaseCannotBeCorrectedThroughThisOne() {
        UUID mine = release("R6", true);
        UUID other = release("R7", true);
        UUID foreign = service.record(other, draft("DEV", PromotionOutcome.SUCCESS), "acc-1").entries().getFirst().id();
        PromotionDraft voided = new PromotionDraft(stageId("DEV"), PromotionOutcome.VOIDED,
                Instant.now().minus(10, ChronoUnit.MINUTES), null);

        assertThatThrownBy(() -> service.correct(mine, foreign, voided, "idor", "acc-2"))
                .isInstanceOf(PromotionNotFoundException.class);
        assertThat(promotionCount()).isEqualTo(1);
    }

    @Test
    void anOpenReleaseCannotBePromotedAndNothingIsInserted() {
        UUID releaseId = release("R2", false);

        assertThatThrownBy(() -> service.record(releaseId, draft("DEV", PromotionOutcome.SUCCESS), "acc-1"))
                .isInstanceOfSatisfying(PromotionRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(PromotionRejection.RELEASE_NOT_FROZEN));
        assertThat(promotionCount()).isZero();
    }

    @Test
    void aFailedDeploymentToTheNextStageDoesNotAdvanceTheCurrentStage() {
        UUID releaseId = release("R3", true);
        service.record(releaseId, draft("DEV", PromotionOutcome.SUCCESS), "acc-1");

        PromotionHistory history = service.record(releaseId, draft("TEST", PromotionOutcome.FAILED), "acc-1");

        assertThat(history.entries()).hasSize(2);
        assertThat(history.currentStage()).map(Stage::name).contains("DEV");
    }

    @Test
    void anUnknownReleaseIsNotFoundOnLoadAndOnAppend() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> promotions.load(unknown)).isInstanceOf(ReleaseNotFoundException.class);
        assertThatThrownBy(() -> promotions.appendUnderLock(unknown, h -> null))
                .isInstanceOf(ReleaseNotFoundException.class);
    }

    @Test
    void concurrentRecordingsForOneReleaseAreSerialised() throws Exception {
        UUID releaseId = release("R4", true);
        service.record(releaseId, draft("DEV", PromotionOutcome.SUCCESS), "acc-1");
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> firstRollBack = () -> attempt(start, releaseId, draft("DEV", PromotionOutcome.ROLLED_BACK));
        Callable<Boolean> secondRollBack = () -> attempt(start, releaseId, draft("DEV", PromotionOutcome.ROLLED_BACK));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(firstRollBack);
            Future<Boolean> second = pool.submit(secondRollBack);
            start.countDown();

            assertThat(List.of(outcome(first), outcome(second))).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
        assertThat(promotionCount()).isEqualTo(2);
    }

    private boolean attempt(CountDownLatch start, UUID releaseId, PromotionDraft draft) throws InterruptedException {
        start.await();
        try {
            service.record(releaseId, draft, "acc-1");
            return true;
        } catch (PromotionRejectedException e) {
            return false;
        }
    }

    private static boolean outcome(Future<Boolean> future) throws InterruptedException, ExecutionException {
        return future.get();
    }
}
