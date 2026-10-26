package com.evision.releasemgmt.adapters.briefing;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.evision.releasemgmt.config.BriefingsProperties;
import com.evision.releasemgmt.domain.briefing.BriefingClaudeConfig;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BriefingConfigFileAdapterTest {

    @TempDir
    Path dir;

    private ListAppender<ILoggingEvent> logs;
    private Logger adapterLogger;

    @BeforeEach
    void captureLogs() {
        adapterLogger = (Logger) LoggerFactory.getLogger(BriefingConfigFileAdapter.class);
        logs = new ListAppender<>();
        logs.start();
        adapterLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        adapterLogger.detachAppender(logs);
    }

    private BriefingClaudeConfig read(Path config) {
        BriefingsProperties props = new BriefingsProperties(dir, config, null, null, null, null);
        return new BriefingConfigFileAdapter(props, JsonMapper.builder().build()).read();
    }

    private Path write(String json) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void repoConfigGivesCnvCoverageAndDefaults() throws Exception {
        Path config = write(Files.readString(Path.of("briefings/config.json")));

        BriefingClaudeConfig c = read(config);

        assertThat(c.readable()).isTrue();
        assertThat(c.coveredProjects()).isEqualTo(Map.of(
                BriefingTask.JIRA_DIGEST, Set.of("CNV"),
                BriefingTask.SPRINT_CHECK, Set.of("CNV"),
                BriefingTask.WEEKLY_STATUS, Set.of("CNV")));
        assertThat(c.staleAfterWorkingDays()).isEqualTo(3);
        assertThat(c.dueSoonDays()).isEqualTo(5);
        assertThat(c.backlogItems()).isEqualTo(15);
        assertThat(c.keepRuns()).isEqualTo(60);
    }

    @Test
    void taskProjectsOverrideTheGlobalListAndInvalidKeysAreDropped() throws Exception {
        Path config = write("""
                {"jira": {"projects": ["CNV"]},
                 "tasks": {"sprint_check": {"projects": ["abc", "bad key", "CNV"]}}}
                """);

        BriefingClaudeConfig c = read(config);

        assertThat(c.coveredProjects().get(BriefingTask.SPRINT_CHECK)).containsExactlyInAnyOrder("ABC", "CNV");
        assertThat(c.coveredProjects().get(BriefingTask.JIRA_DIGEST)).containsExactly("CNV");
        assertThat(c.coveredProjects().get(BriefingTask.WEEKLY_STATUS)).containsExactly("CNV");
        assertThat(c.staleAfterWorkingDays()).isNull();
        assertThat(c.keepRuns()).isNull();
    }

    @Test
    void outOfRangeValuesBecomeNull() throws Exception {
        Path config = write("""
                {"jira": {"projects": ["CNV"]}, "output": {"keepRuns": 5},
                 "tasks": {"jira_digest": {"staleAfterWorkingDays": 0, "dueSoonDays": 999},
                           "sprint_check": {"backlogItemsWhenNoFutureSprint": 101}}}
                """);

        BriefingClaudeConfig c = read(config);

        assertThat(c.readable()).isTrue();
        assertThat(c.staleAfterWorkingDays()).isNull();
        assertThat(c.dueSoonDays()).isNull();
        assertThat(c.backlogItems()).isNull();
        assertThat(c.keepRuns()).isNull();
    }

    @Test
    void missingFileIsUnreadable() {
        assertThat(read(dir.resolve("absent.json"))).isEqualTo(BriefingClaudeConfig.UNREADABLE);
    }

    @Test
    void malformedJsonIsUnreadableAndLogsOnlyTheExceptionClass() throws Exception {
        Path config = write("{\"jira\": secret-content");

        assertThat(read(config)).isEqualTo(BriefingClaudeConfig.UNREADABLE);
        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(e -> {
            assertThat(e.getFormattedMessage()).doesNotContain("secret-content").doesNotContain(dir.toString());
            assertThat(e.getThrowableProxy()).isNull();
        });
    }

    @Test
    void nonObjectRootIsUnreadable() throws Exception {
        assertThat(read(write("[]"))).isEqualTo(BriefingClaudeConfig.UNREADABLE);
    }

    @Test
    void oversizedFileIsUnreadable() throws Exception {
        Path config = write("{\"pad\":\"" + "x".repeat((int) BriefingFiles.MAX_BYTES) + "\"}");

        assertThat(read(config)).isEqualTo(BriefingClaudeConfig.UNREADABLE);
    }

    @Test
    void symlinkIsUnreadable() throws Exception {
        Path target = dir.resolve("real.json");
        Files.writeString(target, "{\"jira\": {\"projects\": [\"CNV\"]}}");
        Path link = dir.resolve("config.json");
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assumptions.abort("OS refused to create a symlink");
        }

        assertThat(read(link)).isEqualTo(BriefingClaudeConfig.UNREADABLE);
    }

    @Test
    void adapterNeverReadsTheSiteOrCloudId() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/evision/releasemgmt/adapters/briefing/BriefingConfigFileAdapter.java"));

        assertThat(source).doesNotContain("cloudId").doesNotContain("\"site\"");
    }
}
