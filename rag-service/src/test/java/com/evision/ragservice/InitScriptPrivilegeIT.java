package com.evision.ragservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class InitScriptPrivilegeIT {

    private static final String RAG_PASSWORD = "rag-it-pw";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE)
            .withDatabaseName("releasemgmt")
            .withUsername("releasemgmt")
            .withPassword("releasemgmt")
            .withEnv("RAG_DB_PASSWORD", RAG_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath("../docker/initdb/10-ragdb.sh", 0755),
                    "/docker-entrypoint-initdb.d/10-ragdb.sh");

    private static String url(String database) {
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + database;
    }

    private static List<String> queryColumn(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(url("ragdb"), "rag", RAG_PASSWORD);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (rs.next()) {
                values.add(rs.getString(1));
            }
            return values;
        }
    }

    @Test
    void flywayRunsAsRagRole() throws Exception {
        Flyway.configure().dataSource(url("ragdb"), "rag", RAG_PASSWORD)
                .locations("classpath:db/migration").load().migrate();

        assertThat(queryColumn("select tableowner from pg_tables where tablename='chunk'")).containsExactly("rag");
        assertThat(queryColumn("select extname from pg_extension")).contains("vector");
    }

    @Test
    void ragHasNoElevatedAttributes() throws Exception {
        assertThat(queryColumn("select rolsuper::text || ',' || rolcreatedb::text || ',' || rolcreaterole::text "
                + "from pg_roles where rolname = 'rag'")).containsExactly("false,false,false");
    }

    @Test
    void ragCannotConnectToReleasemgmt() {
        assertThatThrownBy(() -> DriverManager.getConnection(url("releasemgmt"), "rag", RAG_PASSWORD))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    private static void runScript(String ragPassword) throws Exception {
        var result = postgres.execInContainer("bash", "-c",
                "RAG_DB_PASSWORD=" + ragPassword
                        + " POSTGRES_USER=releasemgmt POSTGRES_DB=releasemgmt"
                        + " bash /docker-entrypoint-initdb.d/10-ragdb.sh");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    @Test
    void scriptIsIdempotent() throws Exception {
        runScript(RAG_PASSWORD);
    }

    @Test
    void rerunAppliesAChangedPassword() throws Exception {
        try {
            runScript("rag-it-rotated");

            DriverManager.getConnection(url("ragdb"), "rag", "rag-it-rotated").close();
            assertThatThrownBy(() -> DriverManager.getConnection(url("ragdb"), "rag", RAG_PASSWORD))
                    .isInstanceOf(SQLException.class);
        } finally {
            runScript(RAG_PASSWORD);
        }
    }
}
