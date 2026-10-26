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
class V16RoadmapMigrationIT {

    private static final List<String> COUNTED_TABLES = List.of(
            "release", "release_ticket_item", "release_manual_item", "promotion", "document_snapshot", "template");
    private static final String RELEASE_AGGREGATE = """
            SELECT string_agg(id::text || ':' || coalesce(items_changed_at::text, 'null') || ':' || status, ',' ORDER BY id)
            FROM release""";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    private Flyway flyway(DataSource ds, MigrationVersion target) {
        var config = Flyway.configure().dataSource(ds).locations("classpath:db/migration");
        return (target == null ? config : config.target(target)).load();
    }

    @Test
    void v16MigratesOverV14DataWithoutTouchingItAndEnforcesRoadmapConstraints() throws SQLException {
        DataSource ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        flyway(ds, MigrationVersion.fromVersion("13")).migrate();
        try (Connection connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v1_1-snapshot.sql"));
        }
        flyway(ds, MigrationVersion.fromVersion("14")).migrate();
        Map<String, Integer> countsBefore = counts(jdbc);
        String releasesBefore = jdbc.queryForObject(RELEASE_AGGREGATE, String.class);
        assertThat(countsBefore.get("release")).isEqualTo(5);

        flyway(ds, MigrationVersion.fromVersion("16")).migrate();

        assertThat(flyway(ds, null).info().current().getVersion().getVersion()).isEqualTo("16");
        assertThat(counts(jdbc)).isEqualTo(countsBefore);
        assertThat(jdbc.queryForObject(RELEASE_AGGREGATE, String.class)).isEqualTo(releasesBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM release WHERE packages_url IS NOT NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM roadmap_item", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM roadmap_item_input_history", Integer.class)).isZero();

        assertConstraints(jdbc);
        assertHistoryIsAppendOnly(jdbc);
    }

    private void assertConstraints(JdbcTemplate jdbc) {
        assertRejected(jdbc, "MANUAL", null, "impact", "0.75");
        assertRejected(jdbc, "MANUAL", null, "confidence", "70");
        assertRejected(jdbc, "MANUAL", null, "effort", "0");
        assertRejected(jdbc, "MANUAL", null, "reach", "-1");
        assertRejected(jdbc, "JIRA", null, "reach", "1");

        insertItem(jdbc, "JIRA", "'CNV-1'");
        assertThatThrownBy(() -> insertItem(jdbc, "JIRA", "'CNV-1'")).isInstanceOf(DataAccessException.class);
    }

    private void assertRejected(JdbcTemplate jdbc, String source, String jiraKey, String column, String value) {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO roadmap_item (id, source, jira_key, title, created_at, created_by, "
                + column + ") VALUES (?, ?, ?, 't', now(), 'u', " + value + ")", UUID.randomUUID(), source, jiraKey))
                .as(column + "=" + value).isInstanceOf(DataAccessException.class);
    }

    private UUID insertItem(JdbcTemplate jdbc, String source, String jiraKeyLiteral) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO roadmap_item (id, source, jira_key, title, created_at, created_by) VALUES (?, ?, "
                + jiraKeyLiteral + ", 't', now(), 'u')", id, source);
        return id;
    }

    private void assertHistoryIsAppendOnly(JdbcTemplate jdbc) {
        UUID item = insertItem(jdbc, "MANUAL", "NULL");
        jdbc.update("""
                INSERT INTO roadmap_item_input_history (id, item_id, field, old_value, new_value, changed_at, changed_by)
                VALUES (gen_random_uuid(), ?, 'REACH', NULL, 100, now(), 'u')""", item);

        assertThatThrownBy(() -> jdbc.update("UPDATE roadmap_item_input_history SET new_value = 5"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM roadmap_item_input_history"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    private Map<String, Integer> counts(JdbcTemplate jdbc) {
        return COUNTED_TABLES.stream().collect(java.util.stream.Collectors.toMap(
                table -> table, table -> jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)));
    }
}
