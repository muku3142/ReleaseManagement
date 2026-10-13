package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.application.EnvironmentLadderService;
import com.evision.releasemgmt.application.PromotionService;
import com.evision.releasemgmt.domain.promotion.LadderEditRejectedException;
import com.evision.releasemgmt.domain.promotion.LadderEditRejection;
import com.evision.releasemgmt.domain.promotion.PromotionDraft;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.promotion.PromotionRejectedException;
import com.evision.releasemgmt.domain.promotion.PromotionRejection;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.release.Release;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class EnvironmentLadderPersistenceIT {

    private static final String SEED_LADDER = """
            INSERT INTO environment_stage (id, name, position, is_production) VALUES
                (gen_random_uuid(), 'DEV',  1, FALSE),
                (gen_random_uuid(), 'TEST', 2, FALSE),
                (gen_random_uuid(), 'UAT',  3, FALSE),
                (gen_random_uuid(), 'PROD', 4, TRUE)
            """;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private EnvironmentLadderService ladder;

    @Autowired
    private PromotionService promotions;

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void restore() {
        jdbc.execute("TRUNCATE release CASCADE");
        jdbc.execute("DELETE FROM environment_stage");
        jdbc.execute(SEED_LADDER);
    }

    private UUID stageId(String name) {
        return jdbc.queryForObject("SELECT id FROM environment_stage WHERE name = ?", UUID.class, name);
    }

    private List<String> names() {
        return ladder.ladder().stream().map(Stage::name).toList();
    }

    private UUID frozenRelease() {
        Release release = Release.create("R1", "1.0", "CNV", "rm@acme.com", Instant.now().minus(1, ChronoUnit.HOURS));
        releaseRepository.create(release, List.of());
        releaseRepository.freeze(release.id(), "rm@acme.com", Instant.now().minus(30, ChronoUnit.MINUTES));
        return release.id();
    }

    private PromotionHistory record(UUID releaseId, String stage) {
        return promotions.record(releaseId, new PromotionDraft(stageId(stage), PromotionOutcome.SUCCESS, null, null),
                "acc-1");
    }

    private static void assertRejected(Runnable action, LadderEditRejection reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(LadderEditRejectedException.class,
                e -> assertThat(e.reason()).isEqualTo(reason));
    }

    @Test
    void addAppendsAtTheEnd() {
        ladder.add("QA");

        Stage last = ladder.ladder().getLast();
        assertThat(last.name()).isEqualTo("QA");
        assertThat(last.position()).isEqualTo(5);
        assertThat(last.production()).isFalse();
        assertThat(last.retired()).isFalse();
    }

    @Test
    void duplicateNameIgnoringCase() {
        assertRejected(() -> ladder.add("dev"), LadderEditRejection.STAGE_NAME_DUPLICATE);
        assertRejected(() -> ladder.rename(stageId("TEST"), "Dev"), LadderEditRejection.STAGE_NAME_DUPLICATE);
        assertThat(names()).containsExactly("DEV", "TEST", "UAT", "PROD");
    }

    @Test
    void reorderSwaps() {
        ladder.reorder(List.of(stageId("TEST"), stageId("DEV"), stageId("UAT"), stageId("PROD")));

        assertThat(names()).containsExactly("TEST", "DEV", "UAT", "PROD");
    }

    @Test
    void renameRelabelsHistory() {
        UUID releaseId = frozenRelease();
        record(releaseId, "DEV");

        ladder.rename(stageId("DEV"), "Development");

        PromotionHistory history = promotions.history(releaseId);
        UUID entryStage = history.entries().getFirst().stageId();
        assertThat(history.stages()).filteredOn(s -> s.id().equals(entryStage))
                .extracting(Stage::name).containsExactly("Development");
    }

    @Test
    void usedStageCannotBeDeleted() {
        UUID releaseId = frozenRelease();
        record(releaseId, "DEV");

        assertRejected(() -> ladder.delete(stageId("DEV")), LadderEditRejection.STAGE_IN_USE);
        assertThat(names()).contains("DEV");

        ladder.retire(stageId("DEV"));
        assertThat(ladder.ladder()).filteredOn(Stage::retired).extracting(Stage::name).containsExactly("DEV");
        assertThat(promotions.history(releaseId).entries().getFirst().stageId()).isEqualTo(stageId("DEV"));
    }

    @Test
    void anUnusedStageCanBeDeleted() {
        ladder.delete(stageId("UAT"));

        assertThat(names()).containsExactly("DEV", "TEST", "PROD");
    }

    @Test
    void productionFlagNeverAbsent() {
        ladder.moveProduction(stageId("UAT"));

        assertThat(jdbc.queryForList("SELECT name FROM environment_stage WHERE is_production", String.class))
                .containsExactly("UAT");
        assertRejected(() -> ladder.retire(stageId("UAT")), LadderEditRejection.PRODUCTION_STAGE_PROTECTED);
    }

    @Test
    void retiredTargetRejected() {
        UUID releaseId = frozenRelease();
        record(releaseId, "DEV");
        ladder.retire(stageId("TEST"));

        assertThatThrownBy(() -> record(releaseId, "TEST")).isInstanceOfSatisfying(PromotionRejectedException.class,
                e -> assertThat(e.reason()).isEqualTo(PromotionRejection.PROMOTION_TARGET_RETIRED));

        ladder.restore(stageId("TEST"));
        assertThat(record(releaseId, "TEST").currentStage()).map(Stage::name).contains("TEST");
    }
}
