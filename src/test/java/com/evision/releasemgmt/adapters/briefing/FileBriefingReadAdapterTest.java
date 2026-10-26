package com.evision.releasemgmt.adapters.briefing;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.evision.releasemgmt.config.BriefingsProperties;
import com.evision.releasemgmt.domain.briefing.BriefingFeed;
import com.evision.releasemgmt.domain.briefing.BriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileBriefingReadAdapterTest {

    @TempDir
    Path dir;

    private final List<String> opened = new ArrayList<>();
    private ListAppender<ILoggingEvent> logs;
    private Logger adapterLogger;

    @BeforeEach
    void captureLogs() {
        adapterLogger = (Logger) LoggerFactory.getLogger(FileBriefingReadAdapter.class);
        logs = new ListAppender<>();
        logs.start();
        adapterLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        adapterLogger.detachAppender(logs);
    }

    private FileBriefingReadAdapter adapter(Path base) {
        BriefingsProperties props = new BriefingsProperties(base, null, null, null, null, null);
        return new FileBriefingReadAdapter(props, JsonMapper.builder().build(), (b, name) -> {
            opened.add(name);
            return BriefingFiles.readBounded(b, name);
        });
    }

    private void write(String name, String json) throws IOException {
        Files.writeString(dir.resolve(name), json, StandardCharsets.UTF_8);
    }

    private static String row(String runAt, String status, String headline, String extra) {
        return "{\"run_at\":\"" + runAt + "\",\"status\":\"" + status + "\",\"headline\":\"" + headline + "\"" + extra + "}";
    }

    @Test
    void b1_validFileReturnsSixtyRunsNewestFirst() throws Exception {
        StringBuilder sb = new StringBuilder("[");
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 70; i++) {
            sb.append(i == 0 ? "" : ",").append(row(start.plusSeconds(i * 60L).toString(), "ok", "run " + i, ""));
        }
        write("jira_digest.json", sb.append("]").toString());

        BriefingFeed feed = adapter(dir).feed(BriefingTask.JIRA_DIGEST);

        assertThat(feed.readError()).isFalse();
        assertThat(feed.runs()).hasSize(60);
        assertThat(feed.runs().get(0).headline()).isEqualTo("run 69");
        assertThat(feed.runs().get(59).headline()).isEqualTo("run 10");
        assertThat(feed.latest()).map(BriefingRun::headline).contains("run 69");
    }

    @Test
    void b2_missingDirectoryAndMissingFileAreEmptyWithoutError() {
        BriefingFeed noDir = adapter(dir.resolve("absent")).feed(BriefingTask.JIRA_DIGEST);
        BriefingFeed noFile = adapter(dir).feed(BriefingTask.JIRA_DIGEST);

        assertThat(noDir.runs()).isEmpty();
        assertThat(noDir.readError()).isFalse();
        assertThat(noFile.runs()).isEmpty();
        assertThat(noFile.readError()).isFalse();
    }

    @Test
    void b3_malformedJsonAndNonArrayAreReadErrorsWithoutLeakingPathOrContent() throws Exception {
        write("jira_digest.json", "[{");
        write("sprint_check.json", "{\"run_at\":\"secret-content\"}");

        BriefingFeed malformed = adapter(dir).feed(BriefingTask.JIRA_DIGEST);
        BriefingFeed notArray = adapter(dir).feed(BriefingTask.SPRINT_CHECK);

        assertThat(malformed.runs()).isEmpty();
        assertThat(malformed.readError()).isTrue();
        assertThat(notArray.runs()).isEmpty();
        assertThat(notArray.readError()).isTrue();
        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(e -> {
            String text = e.getFormattedMessage();
            assertThat(text).doesNotContain(dir.toString()).doesNotContain("secret-content").doesNotContain("[{");
        });
    }

    @Test
    void b4_fileOverOneMegabyteIsReadError() throws Exception {
        String padding = " ".repeat((int) BriefingFiles.MAX_BYTES - 1);
        write("jira_digest.json", "[]" + padding);
        assertThat(Files.size(dir.resolve("jira_digest.json"))).isEqualTo(BriefingFiles.MAX_BYTES + 1);

        BriefingFeed feed = adapter(dir).feed(BriefingTask.JIRA_DIGEST);

        assertThat(feed.runs()).isEmpty();
        assertThat(feed.readError()).isTrue();
    }

    @Test
    void b5_truncatesCleansAndDropsBadRows() throws Exception {
        String longHeadline = "a".repeat(200);
        String h1 = "x".repeat(119) + "😀" + "tail";
        String dirty = "ab\\u0000c\\u202Ed\\u2066e\\u007Ff";
        String rows = "["
                + row("2026-10-26T05:00:00Z", "attention", longHeadline, ",\"attention\":null,\"h1\":\"" + h1 + "\"")
                + "," + row("2026-10-26T06:00:00Z", "ok", dirty, ",\"attention\":3")
                + "," + row("2026-10-26T07:00:00Z", "setup", "placeholder", "")
                + "," + row("2026-10-26T08:00:00Z", "unknown", "odd", "")
                + "," + row("not-a-date", "ok", "bad date", "")
                + "," + row("2026-10-26T09:00:00Z", "ok", "negative", ",\"attention\":-1")
                + "," + row("2026-10-26T10:00:00Z", "ok", "fractional", ",\"attention\":1.5")
                + ",\"not an object\""
                + "," + row("2026-10-26T11:00:00+02:00", "quiet", "offset date", "")
                + "]";
        write("jira_digest.json", rows);

        BriefingFeed feed = adapter(dir).feed(BriefingTask.JIRA_DIGEST);

        assertThat(feed.readError()).isFalse();
        assertThat(feed.runs()).extracting(BriefingRun::headline)
                .containsExactly("offset date", "abcdef", "a".repeat(140)).as("newest first, bad rows dropped");
        BriefingRun truncated = feed.runs().get(2);
        assertThat(truncated.attention()).isNull();
        assertThat(truncated.status()).isEqualTo(BriefingStatus.ATTENTION);
        assertThat(truncated.h1().codePointCount(0, truncated.h1().length())).isEqualTo(120);
        assertThat(truncated.h1()).endsWith("😀");
        assertThat(feed.runs().get(1).attention()).isEqualTo(3);
    }

    @Test
    void b6_onlyTheFiveEnumFileNamesAreOpenedAndSymlinksAreRefused() throws Exception {
        write("jira_digest.json", "[]");
        write("other.json", "[]");
        Files.writeString(dir.getParent().resolve("outside.json"), "[]");
        boolean symlinked = true;
        try {
            Files.createSymbolicLink(dir.resolve("sprint_check.json"), dir.resolve("other.json"));
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            symlinked = false;
        }

        FileBriefingReadAdapter adapter = adapter(dir);
        List<BriefingFeed> feeds = new ArrayList<>();
        for (BriefingTask task : BriefingTask.values()) {
            feeds.add(adapter.feed(task));
        }

        Set<String> allowed = Set.of("jira_digest.json", "sprint_check.json", "weekly_status.json",
                "reg_watch.json", "gulf_invest.json");
        assertThat(opened).hasSize(5).allSatisfy(name -> assertThat(allowed).contains(name));
        if (symlinked) {
            assertThat(feeds.get(1).readError()).isTrue();
        }
        Assumptions.assumeTrue(symlinked, "OS refused to create a symlink");
    }

    @Test
    void readBoundedRefusesNamesOutsideTheBaseDirectory() throws Exception {
        Files.writeString(dir.getParent().resolve("outside.json"), "[]");

        assertThatThrownBy(() -> BriefingFiles.readBounded(dir, "../outside.json")).isInstanceOf(IOException.class);
    }

    @Test
    void cleanTextKeepsNullAndTurnsEmptyIntoNull() {
        assertThat(BriefingFiles.cleanText(null, 10)).isNull();
        assertThat(BriefingFiles.cleanText(" \u0000 ", 10)).isNull();
    }

    @Test
    void directoryReadableReflectsTheConfiguredDirectory() {
        assertThat(adapter(dir).directoryReadable()).isTrue();
        assertThat(adapter(dir.resolve("absent")).directoryReadable()).isFalse();
    }
}
