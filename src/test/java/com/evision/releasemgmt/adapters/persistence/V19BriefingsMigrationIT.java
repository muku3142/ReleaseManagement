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
class V19BriefingsMigrationIT {

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
        var config = Flyway.configure().dataSource(ds).locations("classpath:db/migration").outOfOrder(true);
        return (target == null ? config : config.target(target)).load();
    }

    private UUID productNamed(JdbcTemplate jdbc, String name) {
        return jdbc.queryForObject("SELECT id FROM product WHERE name = ?", UUID.class, name);
    }

    private void addRelease(JdbcTemplate jdbc, String name, String key, String createdAt, UUID product) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO release (id, name, version, status, jira_project_key, created_at, created_by)
                VALUES (?, ?, '1.0', 'OPEN', ?, ?::timestamptz, 'acc-demo-1')""", id, name, key, createdAt);
        jdbc.update("INSERT INTO release_product (release_id, product_id) VALUES (?, ?)", id, product);
    }

    @Test
    void v19MigratesOverV11DataBackfillsKeysAndEnforcesRules() throws SQLException {
        DataSource ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        flyway(ds, MigrationVersion.fromVersion("13")).migrate();
        try (Connection connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v1_1-snapshot.sql"));
        }
        flyway(ds, MigrationVersion.fromVersion("16")).migrate();

        UUID alpha = productNamed(jdbc, "ALPHA");
        UUID beta = productNamed(jdbc, "BETA");
        UUID defaultProduct = productNamed(jdbc, "Default");
        addRelease(jdbc, "Shared A", " cnv ", "2026-01-01T00:00:00Z", alpha);
        addRelease(jdbc, "Shared B", "CNV", "2026-02-01T00:00:00Z", beta);
        addRelease(jdbc, "Junk", "bad key!", "2026-03-01T00:00:00Z", defaultProduct);

        Map<String, Integer> countsBefore = counts(jdbc);
        String releasesBefore = jdbc.queryForObject(RELEASE_AGGREGATE, String.class);
        String promotionsBefore = jdbc.queryForObject(PROMOTION_AGGREGATE, String.class);

        flyway(ds, MigrationVersion.fromVersion("19")).migrate();

        assertThat(flyway(ds, MigrationVersion.fromVersion("19")).info().current().getVersion().getVersion()).isEqualTo("19");
        assertThat(counts(jdbc)).isEqualTo(countsBefore);
        assertThat(jdbc.queryForObject(RELEASE_AGGREGATE, String.class)).isEqualTo(releasesBefore);
        assertThat(jdbc.queryForObject(PROMOTION_AGGREGATE, String.class)).isEqualTo(promotionsBefore);

        assertBackfill(jdbc, alpha, beta, defaultProduct);
        assertSettingsSeed(jdbc);
        assertConstraints(jdbc, alpha, beta);
    }

    private void assertBackfill(JdbcTemplate jdbc, UUID alpha, UUID beta, UUID defaultProduct) {
        assertThat(keysOf(jdbc, alpha)).containsExactly("ALPHA", "CNV");
        assertThat(keysOf(jdbc, beta)).containsExactly("BETA");
        assertThat(keysOf(jdbc, defaultProduct)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_jira_project", Integer.class)).isEqualTo(3);
    }

    private List<String> keysOf(JdbcTemplate jdbc, UUID product) {
        return jdbc.queryForList(
                "SELECT project_key FROM product_jira_project WHERE product_id = ? ORDER BY project_key", String.class, product);
    }

    private void assertSettingsSeed(JdbcTemplate jdbc) {
        Map<String, Object> settings = jdbc.queryForMap("SELECT * FROM briefing_settings");
        assertThat(((Number) settings.get("id")).intValue()).isEqualTo(1);
        assertThat(settings.get("grace_minutes")).isEqualTo(30);
        assertThat(settings.get("work_days")).isEqualTo("SUN,MON,TUE,WED,THU");
        assertThat(settings.get("scheduler_account_id")).isNull();
        assertThat(settings.get("fallback_enabled")).isEqualTo(false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM briefing_settings", Integer.class)).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT cron FROM briefing_task_setting WHERE task = 'jira_digest'", String.class))
                .isEqualTo("0 44 8 * * SUN,MON,TUE,WED,THU");
        assertThat(jdbc.queryForObject("SELECT cron FROM briefing_task_setting WHERE task = 'sprint_check'", String.class))
                .isEqualTo("0 41 9 * * WED");
        assertThat(jdbc.queryForObject("SELECT cron FROM briefing_task_setting WHERE task = 'weekly_status'", String.class))
                .isEqualTo("0 41 13 * * THU");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM briefing_task_setting
                WHERE enabled AND stale_after_working_days IS NULL AND due_soon_days IS NULL AND backlog_items IS NULL""",
                Integer.class)).isEqualTo(3);
    }

    private void assertConstraints(JdbcTemplate jdbc, UUID alpha, UUID beta) {
        String insertKey = "INSERT INTO product_jira_project (product_id, project_key) VALUES (?, ?)";
        assertThatThrownBy(() -> jdbc.update(insertKey, beta, "cnv")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(insertKey, beta, "CNV")).isInstanceOf(DataAccessException.class);

        String insertRun = """
                INSERT INTO briefing_run (id, task, product_id, slot_at, run_trigger, status, headline, started_at, finished_at)
                VALUES (?, 'jira_digest', ?, ?::timestamptz, ?, 'ok', 'h', now(), now())""";
        String slot = "2026-04-01T05:44:00Z";
        jdbc.update(insertRun, UUID.randomUUID(), alpha, slot, "SLOT");
        assertThatThrownBy(() -> jdbc.update(insertRun, UUID.randomUUID(), alpha, slot, "SLOT"))
                .isInstanceOf(DataAccessException.class);
        jdbc.update(insertRun, UUID.randomUUID(), alpha, null, "MANUAL");
        jdbc.update(insertRun, UUID.randomUUID(), alpha, null, "MANUAL");
        assertThatThrownBy(() -> jdbc.update(insertRun, UUID.randomUUID(), alpha, null, "SLOT"))
                .isInstanceOf(DataAccessException.class);
    }

    private Map<String, Integer> counts(JdbcTemplate jdbc) {
        return COUNTED_TABLES.stream().collect(java.util.stream.Collectors.toMap(
                table -> table, table -> jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)));
    }
}
