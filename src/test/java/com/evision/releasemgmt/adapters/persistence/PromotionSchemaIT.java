package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class PromotionSchemaIT {

    private static final String SEED_LADDER = """
            INSERT INTO environment_stage (id, name, position, is_production) VALUES
                (gen_random_uuid(), 'DEV',  1, FALSE),
                (gen_random_uuid(), 'TEST', 2, FALSE),
                (gen_random_uuid(), 'UAT',  3, FALSE),
                (gen_random_uuid(), 'PROD', 4, TRUE)
            """;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    @AfterEach
    void restore() {
        jdbc.execute("TRUNCATE release CASCADE");
        jdbc.execute("DELETE FROM environment_stage");
        jdbc.execute(SEED_LADDER);
    }

    private UUID frozenRelease(String name) {
        Release release = Release.create(name, "1.0", "CNV", "rm@acme.com", Instant.now().minus(1, ChronoUnit.HOURS));
        releaseRepository.create(release, List.of());
        releaseRepository.freeze(release.id(), "rm@acme.com", Instant.now().minus(30, ChronoUnit.MINUTES));
        return release.id();
    }

    private UUID stageId(String name) {
        return jdbc.queryForObject("SELECT id FROM environment_stage WHERE name = ?", UUID.class, name);
    }

    private UUID insertPromotion(UUID releaseId, UUID stageId, String outcome, UUID supersedesId, String reason) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO promotion (id, release_id, stage_id, outcome, deployed_at, recorded_at, recorded_by,
                                       note, supersedes_id, correction_reason)
                VALUES (?, ?, ?, ?, now(), now(), 'acc-1', ?, ?, ?)
                """, id, releaseId, stageId, outcome, "n", supersedesId, reason);
        return id;
    }

    private UUID basePromotion() {
        return insertPromotion(frozenRelease("R"), stageId("DEV"), "SUCCESS", null, null);
    }

    @Test
    void seedLadder() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT name, position, is_production, retired_at FROM environment_stage ORDER BY position");

        assertThat(rows).extracting(r -> r.get("name")).containsExactly("DEV", "TEST", "UAT", "PROD");
        assertThat(rows).extracting(r -> r.get("position")).containsExactly(1, 2, 3, 4);
        assertThat(rows).extracting(r -> r.get("is_production")).containsExactly(false, false, false, true);
        assertThat(rows).extracting(r -> r.get("retired_at")).containsOnlyNulls();
    }

    @Test
    void updateIsRejected() {
        UUID id = basePromotion();

        assertThatThrownBy(() -> jdbc.update("UPDATE promotion SET note = 'x' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT note FROM promotion WHERE id = ?", String.class, id)).isEqualTo("n");
    }

    @Test
    void deleteIsRejected() {
        UUID id = basePromotion();

        assertThatThrownBy(() -> jdbc.update("DELETE FROM promotion WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM promotion WHERE id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void truncateCascadeStillCleansUp() {
        basePromotion();

        jdbc.execute("TRUNCATE release CASCADE");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM promotion", Integer.class)).isZero();
    }

    @Test
    void supersededAtMostOnce() {
        UUID release = frozenRelease("R");
        UUID original = insertPromotion(release, stageId("DEV"), "SUCCESS", null, null);
        insertPromotion(release, stageId("DEV"), "FAILED", original, "wrong outcome");

        assertThatThrownBy(() -> insertPromotion(release, stageId("DEV"), "ROLLED_BACK", original, "again"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void correctionMustBeOnSameRelease() {
        UUID original = basePromotion();
        UUID otherRelease = frozenRelease("B");

        assertThatThrownBy(() -> insertPromotion(otherRelease, stageId("DEV"), "FAILED", original, "cross"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void voidedRequiresSupersedes() {
        UUID release = frozenRelease("R");
        UUID original = insertPromotion(release, stageId("DEV"), "SUCCESS", null, null);

        assertThatThrownBy(() -> insertPromotion(release, stageId("DEV"), "VOIDED", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID voided = insertPromotion(release, stageId("DEV"), "VOIDED", original, "entered by mistake");
        assertThat(voided).isNotNull();
    }

    @Test
    void correctionNeedsReason() {
        UUID release = frozenRelease("R");
        UUID original = insertPromotion(release, stageId("DEV"), "SUCCESS", null, null);

        assertThatThrownBy(() -> insertPromotion(release, stageId("DEV"), "FAILED", original, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPromotion(release, stageId("DEV"), "FAILED", original, "   "))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPromotion(release, stageId("DEV"), "FAILED", null, "reason"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownOutcomeRejected() {
        UUID release = frozenRelease("R");

        assertThatThrownBy(() -> insertPromotion(release, stageId("DEV"), "STARTED", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void secondProductionRejected() {
        assertThatThrownBy(() -> jdbc.update("UPDATE environment_stage SET is_production = TRUE WHERE name = 'DEV'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productionCannotBeRetired() {
        assertThatThrownBy(() -> jdbc.update("UPDATE environment_stage SET retired_at = now() WHERE name = 'PROD'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stageNamesUniqueIgnoringCase() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO environment_stage (id, name, position) VALUES (?, 'dev', 9)", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usedStageCannotBeDeleted() {
        UUID dev = stageId("DEV");
        insertPromotion(frozenRelease("R"), dev, "SUCCESS", null, null);
        UUID spare = UUID.randomUUID();
        jdbc.update("INSERT INTO environment_stage (id, name, position) VALUES (?, 'SPARE', 9)", spare);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM environment_stage WHERE id = ?", dev))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("DELETE FROM environment_stage WHERE id = ?", spare)).isEqualTo(1);
    }

    @Test
    void promotedReleaseCannotBeDeleted() {
        UUID release = frozenRelease("R");
        insertPromotion(release, stageId("DEV"), "SUCCESS", null, null);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM release WHERE id = ?", release))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void positionSwapInOneTransaction() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            jdbc.update("UPDATE environment_stage SET position = 2 WHERE name = 'DEV'");
            jdbc.update("UPDATE environment_stage SET position = 1 WHERE name = 'TEST'");
        });

        assertThat(jdbc.queryForList("SELECT name FROM environment_stage ORDER BY position", String.class))
                .containsExactly("TEST", "DEV", "UAT", "PROD");
    }

    @Test
    void noStoredCurrentStage() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE column_name ILIKE '%current_stage%'",
                Integer.class)).isZero();
    }
}
