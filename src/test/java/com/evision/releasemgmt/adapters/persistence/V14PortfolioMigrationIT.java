package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class V14PortfolioMigrationIT {

    private static final String RELEASE_PREFIX = "00000000-0000-0000-0000-0000000000a";
    private static final List<String> COUNTED_TABLES = List.of(
            "release", "release_ticket_item", "release_manual_item", "promotion", "document_snapshot", "template");
    private static final String RELEASE_AGGREGATE = """
            SELECT string_agg(id::text || ':' || coalesce(items_changed_at::text, 'null') || ':' || status, ',' ORDER BY id)
            FROM release""";
    private static final String PROMOTION_AGGREGATE = """
            SELECT string_agg(id::text || outcome || deployed_at::text, ',' ORDER BY id) FROM promotion""";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    private Flyway flyway(DataSource ds, MigrationVersion target) {
        var config = Flyway.configure().dataSource(ds).locations("classpath:db/migration");
        return (target == null ? config : config.target(target)).load();
    }

    private UUID releaseId(int n) {
        return UUID.fromString(RELEASE_PREFIX + n);
    }

    private UUID productOf(JdbcTemplate jdbc, int releaseNumber) {
        return jdbc.queryForObject("SELECT product_id FROM release_product WHERE release_id = ?",
                UUID.class, releaseId(releaseNumber));
    }

    @Test
    void v14MigratesOverV11DataWithoutTouchingIt() throws SQLException {
        DataSource ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        flyway(ds, MigrationVersion.fromVersion("13")).migrate();
        try (Connection connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v1_1-snapshot.sql"));
        }
        Map<String, Integer> countsBefore = counts(jdbc);
        String releasesBefore = jdbc.queryForObject(RELEASE_AGGREGATE, String.class);
        String promotionsBefore = jdbc.queryForObject(PROMOTION_AGGREGATE, String.class);
        assertThat(countsBefore.get("release")).isEqualTo(5);

        flyway(ds, MigrationVersion.fromVersion("14")).migrate();

        assertThat(flyway(ds, null).info().current().getVersion().getVersion()).isEqualTo("14");
        assertThat(counts(jdbc)).isEqualTo(countsBefore);
        assertThat(jdbc.queryForObject(RELEASE_AGGREGATE, String.class)).isEqualTo(releasesBefore);
        assertThat(jdbc.queryForObject(PROMOTION_AGGREGATE, String.class)).isEqualTo(promotionsBefore);
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN
                      ('product', 'client', 'product_client', 'release_product', 'client_deployment')""", String.class))
                .hasSize(5);
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.views
                WHERE table_schema = 'public' AND table_name IN ('effective_client_deployment', 'current_client_release')""",
                String.class)).hasSize(2);
        assertThatThrownBy(() -> jdbc.update("UPDATE promotion SET note = 'x'"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertBackfill(jdbc);
        assertCurrentReleaseWorksOnBackfilledLinks(jdbc);
    }

    private void assertBackfill(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForList("SELECT name FROM product ORDER BY name COLLATE \"C\"", String.class))
                .containsExactly("ALPHA", "BETA", "Default");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM release_product", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM release r
                WHERE (SELECT count(*) FROM release_product rp WHERE rp.release_id = r.id) <> 1""", Integer.class)).isZero();

        UUID alpha = jdbc.queryForObject("SELECT id FROM product WHERE name = 'ALPHA'", UUID.class);
        assertThat(productOf(jdbc, 1)).isEqualTo(alpha);
        assertThat(productOf(jdbc, 2)).isEqualTo(alpha);
        assertThat(productOf(jdbc, 4)).isEqualTo(alpha);
        assertThat(productOf(jdbc, 3))
                .isEqualTo(jdbc.queryForObject("SELECT id FROM product WHERE name = 'BETA'", UUID.class));
        assertThat(productOf(jdbc, 5))
                .isEqualTo(jdbc.queryForObject("SELECT id FROM product WHERE name = 'Default'", UUID.class));

        for (String table : List.of("client", "product_client", "client_deployment")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isZero();
        }
    }

    private void assertCurrentReleaseWorksOnBackfilledLinks(JdbcTemplate jdbc) {
        UUID alpha = productOf(jdbc, 2);
        UUID client = UUID.randomUUID();
        jdbc.update("INSERT INTO client (id, name) VALUES (?, 'Demo Client')", client);
        jdbc.update("INSERT INTO product_client (product_id, client_id) VALUES (?, ?)", alpha, client);
        jdbc.update("""
                INSERT INTO client_deployment (id, release_id, product_id, client_id, outcome, installed_at, recorded_by)
                VALUES (?, ?, ?, ?, 'SUCCESS', now(), 'acc-demo-1')""", UUID.randomUUID(), releaseId(2), alpha, client);

        assertThat(jdbc.queryForObject("SELECT release_id FROM current_client_release WHERE client_id = ?",
                UUID.class, client)).isEqualTo(releaseId(2));
    }

    private Map<String, Integer> counts(JdbcTemplate jdbc) {
        return COUNTED_TABLES.stream().collect(java.util.stream.Collectors.toMap(
                table -> table, table -> jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)));
    }
}
