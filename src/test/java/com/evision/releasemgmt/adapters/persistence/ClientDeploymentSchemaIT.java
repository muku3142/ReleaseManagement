package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class ClientDeploymentSchemaIT {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant T3 = Instant.parse("2026-04-01T00:00:00Z");
    private static final Instant T4 = Instant.parse("2026-05-01T00:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void restore() {
        jdbc.execute("TRUNCATE release CASCADE");
        jdbc.execute("TRUNCATE product, client CASCADE");
    }

    private UUID frozenRelease(String name) {
        Release release = Release.create(name, "1.0", "CNV", "rm@acme.com", Instant.now().minus(1, ChronoUnit.HOURS));
        releaseRepository.create(release, List.of());
        releaseRepository.freeze(release.id(), "rm@acme.com", Instant.now().minus(30, ChronoUnit.MINUTES));
        return release.id();
    }

    private UUID product(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO product (id, name) VALUES (?, ?)", id, name);
        return id;
    }

    private UUID client(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO client (id, name) VALUES (?, ?)", id, name);
        return id;
    }

    private void link(UUID productId, UUID clientId) {
        jdbc.update("INSERT INTO product_client (product_id, client_id) VALUES (?, ?)", productId, clientId);
    }

    private void cover(UUID releaseId, UUID productId) {
        jdbc.update("INSERT INTO release_product (release_id, product_id) VALUES (?, ?)", releaseId, productId);
    }

    private UUID install(UUID releaseId, UUID productId, UUID clientId, String outcome, Instant installedAt,
                         Instant recordedAt, UUID supersedesId, String reason) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO client_deployment (id, release_id, product_id, client_id, outcome, installed_at, recorded_at,
                                               recorded_by, supersedes_id, correction_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'acc-1', ?, ?)
                """, id, releaseId, productId, clientId, outcome, Timestamp.from(installedAt), Timestamp.from(recordedAt),
                supersedesId, reason);
        return id;
    }

    private UUID install(UUID releaseId, UUID productId, UUID clientId, String outcome, Instant installedAt) {
        return install(releaseId, productId, clientId, outcome, installedAt, installedAt, null, null);
    }

    private UUID currentRelease(UUID clientId, UUID productId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT release_id FROM current_client_release WHERE client_id = ? AND product_id = ?",
                UUID.class, clientId, productId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Test
    void ledgerIsAppendOnly() {
        UUID release = frozenRelease("R");
        UUID p = product("P");
        UUID c = client("C");
        link(p, c);
        cover(release, p);
        UUID id = install(release, p, c, "SUCCESS", T1);

        assertThatThrownBy(() -> jdbc.update("UPDATE client_deployment SET note = 'x' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM client_deployment WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM client_deployment", Integer.class)).isEqualTo(1);
    }

    @Test
    void installRequiresProductOnRelease() {
        UUID release = frozenRelease("R");
        UUID p = product("P");
        UUID c = client("C");
        link(p, c);

        assertThatThrownBy(() -> install(release, p, c, "SUCCESS", T1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("client_deployment_release_covers_product");
    }

    @Test
    void installRequiresClientLinkedToProduct() {
        UUID release = frozenRelease("R");
        UUID p = product("P");
        UUID c = client("C");
        cover(release, p);

        assertThatThrownBy(() -> install(release, p, c, "SUCCESS", T1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("client_deployment_client_has_product");
    }

    @Test
    void correctionMustTargetSameClient() {
        UUID release = frozenRelease("R");
        UUID p = product("P");
        UUID a = client("A");
        UUID b = client("B");
        link(p, a);
        link(p, b);
        cover(release, p);
        UUID original = install(release, p, a, "SUCCESS", T1);

        assertThatThrownBy(() -> install(release, p, b, "FAILED", T2, T2, original, "wrong client"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("client_deployment_supersedes_same_target");
    }

    @Test
    void supersededAtMostOnce() {
        UUID release = frozenRelease("R");
        UUID p = product("P");
        UUID c = client("C");
        link(p, c);
        cover(release, p);
        UUID original = install(release, p, c, "SUCCESS", T1);
        install(release, p, c, "FAILED", T2, T2, original, "wrong outcome");

        assertThatThrownBy(() -> install(release, p, c, "ROLLED_BACK", T3, T3, original, "again"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("client_deployment_superseded_once");
    }

    @Test
    void correctionShapeIsChecked() {
        UUID release = frozenRelease("R");
        UUID p = product("P");
        UUID c = client("C");
        link(p, c);
        cover(release, p);
        UUID original = install(release, p, c, "SUCCESS", T1);

        assertThatThrownBy(() -> install(release, p, c, "VOIDED", T2, T2, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> install(release, p, c, "FAILED", T2, T2, original, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> install(release, p, c, "FAILED", T2, T2, original, "   "))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> install(release, p, c, "FAILED", T2, T2, null, "reason"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(install(release, p, c, "VOIDED", T2, T2, original, "entered by mistake")).isNotNull();
    }

    @Test
    void referencedPairsCannotBeRemoved() {
        UUID release = frozenRelease("R");
        UUID p = product("P");
        UUID c = client("C");
        link(p, c);
        cover(release, p);
        UUID original = install(release, p, c, "SUCCESS", T1);
        install(release, p, c, "VOIDED", T2, T2, original, "mistake");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM release_product WHERE release_id = ? AND product_id = ?",
                release, p)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM product_client WHERE product_id = ? AND client_id = ?",
                p, c)).isInstanceOf(DataIntegrityViolationException.class);

        UUID p2 = product("P2");
        UUID c2 = client("C2");
        link(p2, c2);
        cover(release, p2);
        assertThat(jdbc.update("DELETE FROM release_product WHERE release_id = ? AND product_id = ?", release, p2))
                .isEqualTo(1);
        assertThat(jdbc.update("DELETE FROM product_client WHERE product_id = ? AND client_id = ?", p2, c2))
                .isEqualTo(1);
    }

    @Test
    void namesAreUniqueIgnoringCase() {
        product("Alpha");
        client("Acme");

        assertThatThrownBy(() -> product("ALPHA")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> client("acme")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> product("  ")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void currentReleaseFollowsNewestEffectiveSuccess() {
        UUID r1 = frozenRelease("R1");
        UUID r2 = frozenRelease("R2");
        UUID r3 = frozenRelease("R3");
        UUID r4 = frozenRelease("R4");
        UUID p = product("P");
        UUID c = client("C");
        link(p, c);
        for (UUID r : List.of(r1, r2, r3, r4)) {
            cover(r, p);
        }

        install(r1, p, c, "SUCCESS", T1);
        UUID r2Success = install(r2, p, c, "SUCCESS", T2);
        install(r3, p, c, "SUCCESS", T0, T4, null, null);
        install(r4, p, c, "FAILED", T3);
        install(r2, p, c, "ROLLED_BACK", T4);
        assertThat(currentRelease(c, p)).isEqualTo(r2);

        install(r2, p, c, "VOIDED", T4, T4, r2Success, "mistake");
        assertThat(currentRelease(c, p)).isEqualTo(r1);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM effective_client_deployment WHERE id = ?",
                Integer.class, r2Success)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM effective_client_deployment WHERE outcome = 'VOIDED'",
                Integer.class)).isZero();
    }

    @Test
    void equalInstalledAtIsBrokenByRecordedAt() {
        UUID r1 = frozenRelease("R1");
        UUID r2 = frozenRelease("R2");
        UUID p = product("P");
        UUID c = client("C");
        link(p, c);
        cover(r1, p);
        cover(r2, p);

        install(r1, p, c, "SUCCESS", T1, T1, null, null);
        install(r2, p, c, "SUCCESS", T1, T2, null, null);

        assertThat(currentRelease(c, p)).isEqualTo(r2);
    }

    @Test
    void truncateCascadeStillCleansUp() {
        UUID release = frozenRelease("R");
        UUID p = product("P");
        UUID c = client("C");
        link(p, c);
        cover(release, p);
        install(release, p, c, "SUCCESS", T1);

        jdbc.execute("TRUNCATE release CASCADE");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM client_deployment", Integer.class)).isZero();
    }
}
