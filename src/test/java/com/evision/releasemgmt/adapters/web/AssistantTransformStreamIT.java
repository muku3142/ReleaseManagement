package com.evision.releasemgmt.adapters.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.adapters.security.AuthCookieWriter;
import com.evision.releasemgmt.adapters.security.JwtTokenService;
import com.evision.releasemgmt.application.assistant.TransformSizeEstimator;
import com.evision.releasemgmt.config.AssistantProperties;
import com.evision.releasemgmt.domain.assistant.ClientGoneException;
import com.evision.releasemgmt.domain.assistant.FailureReason;
import com.evision.releasemgmt.domain.assistant.TransformMode;
import com.evision.releasemgmt.domain.assistant.TransformOutcome;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.RagChatPort;
import com.evision.releasemgmt.ports.RagChatPort.ChatListener;
import com.evision.releasemgmt.ports.RagIndexPort;
import com.evision.releasemgmt.ports.RagTransformPort;
import com.evision.releasemgmt.ports.ReleaseRepository;
import com.evision.releasemgmt.ports.TemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.assistant.enabled=true",
        "app.assistant.base-url=http://localhost:1",
        "app.assistant.api-key=it-key-SENTINEL-KEY-9921",
        "app.assistant.sweep.initial-delay=PT1H",
        "app.assistant.stream-deadline=PT2S",
        "app.assistant.document-assist.stream-deadline=PT2S"})
@Testcontainers
class AssistantTransformStreamIT {

    private static final String DOC_SENTINEL = "SENTINEL-DOC-3318";
    private static final String CUSTOM_SENTINEL = "SENTINEL-CUSTOM-7714";
    private static final String TOKEN_SENTINEL = "SENTINEL-TOKEN-4410";
    private static final String SAVED_SENTINEL = "SENTINEL-SAVED-6602";
    private static final String API_KEY = "it-key-SENTINEL-KEY-9921";
    private static final String ACTOR = "557058:abc-123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @MockitoBean
    private RagIndexPort index;

    @MockitoBean
    private RagChatPort chat;

    @MockitoBean
    private RagTransformPort transform;

    @Autowired
    private JwtTokenService tokens;

    @Autowired
    private ReleaseRepository releases;

    @Autowired
    private DocumentSnapshotRepository snapshots;

    @Autowired
    private TemplateRepository templates;

    @Autowired
    private AssistantProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private ListAppender<ILoggingEvent> logs;
    private Level rootLevel;

    @BeforeEach
    void captureLogs() {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLevel = root.getLevel();
        root.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void assertLogsAreCleanAndReset() {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.detachAppender(logs);
        root.setLevel(rootLevel);
        // Hibernate's own DEBUG output dumps whole entities (documents included) once the root level is DEBUG;
        // that is the test's logging level, not application logging, so it is outside this SAFE-03 check.
        List<String> lines = logs.list.stream()
                .filter(e -> !e.getLoggerName().startsWith("org.hibernate"))
                .map(e -> e.getFormattedMessage() + (e.getThrowableProxy() == null ? ""
                        : ThrowableProxyUtil.asString(e.getThrowableProxy())))
                .toList();
        assertThat(lines).noneMatch(l -> l.contains("AccessDeniedException"));
        assertThat(lines).noneMatch(l -> l.contains("already committed"));
        assertThat(lines).noneMatch(l -> l.contains(DOC_SENTINEL) || l.contains(CUSTOM_SENTINEL)
                || l.contains(TOKEN_SENTINEL) || l.contains(SAVED_SENTINEL) || l.contains(API_KEY));
        jdbcTemplate.execute("TRUNCATE release CASCADE");
        jdbcTemplate.execute("TRUNCATE template CASCADE");
    }

    @Test
    void streamsTokensAndDoneUsingTheStoredDocumentAndConfiguredInstruction() throws Exception {
        UUID releaseId = seedRelease(TemplateDocumentType.RUNBOOK, "Runbook " + DOC_SENTINEL);
        AtomicReference<String> sentText = new AtomicReference<>();
        AtomicReference<String> sentInstruction = new AtomicReference<>();
        when(transform.transform(any(), any(), any())).thenAnswer(inv -> {
            sentText.set(inv.getArgument(0));
            sentInstruction.set(inv.getArgument(1));
            ChatListener listener = inv.getArgument(2);
            listener.heartbeat();
            listener.token("A");
            listener.token(TOKEN_SENTINEL);
            return new TransformOutcome.Done("stop", false, 10, 20);
        });

        HttpResponse<InputStream> response = post("/api/assistant/transform", withCookie(),
                transformBody(releaseId, "RUNBOOK", 1, "CLEAN_UP", "IGNORED-CLIENT-TEXT"));
        Stream stream = readStream(response);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-cache");
        assertThat(response.headers().firstValue("X-Accel-Buffering")).contains("no");
        assertThat(stream.lines()).contains(":hb");
        assertThat(stream.events()).extracting(Event::name).containsExactly("token", "token", "done");
        JsonNode done = stream.events().get(2).data();
        assertThat(done.get("finishReason").asString()).isEqualTo("stop");
        assertThat(done.get("truncated").asBoolean()).isFalse();
        assertThat(done.get("complete").asBoolean()).isTrue();
        assertThat(sentText.get()).isEqualTo("Runbook " + DOC_SENTINEL);
        assertThat(sentInstruction.get())
                .isEqualTo(properties.documentAssist().instruction(TransformMode.CLEAN_UP, TemplateDocumentType.RUNBOOK));
    }

    @Test
    void cleanUpSendsEachSectionAndStreamsHeadingsVerbatimWithProgress() throws Exception {
        UUID releaseId = seedRelease(TemplateDocumentType.RUNBOOK, "# Runbook\n\nFirst step\n\n## Rollback\nSecond step\n");
        List<String> sentTexts = new java.util.concurrent.CopyOnWriteArrayList<>();
        when(transform.transform(any(), any(), any())).thenAnswer(inv -> {
            String text = inv.getArgument(0);
            sentTexts.add(text);
            ((ChatListener) inv.getArgument(2)).token(text.toUpperCase() + "\n");
            return new TransformOutcome.Done("stop", false, 10, 20);
        });

        Stream stream = readStream(post("/api/assistant/transform", withCookie(),
                transformBody(releaseId, "RUNBOOK", 1, "CLEAN_UP", null)));

        assertThat(sentTexts).containsExactly("First step", "Second step");
        assertThat(stream.events()).extracting(Event::name)
                .containsExactly("token", "token", "progress", "token", "token", "token", "progress", "token",
                        "token", "done");
        assertThat(stream.events()).filteredOn(event -> event.name().equals("progress"))
                .extracting(event -> event.data().get("section").asInt() + "/" + event.data().get("sections").asInt())
                .containsExactly("1/2", "2/2");
        assertThat(stream.events()).filteredOn(event -> event.name().equals("token"))
                .extracting(event -> event.data().get("text").asString())
                .containsExactly("# Runbook\n", "\n", "FIRST STEP", "\n\n", "## Rollback\n", "SECOND STEP", "\n");
        assertThat(stream.events().get(stream.events().size() - 1).data().get("complete").asBoolean()).isTrue();
    }

    @Test
    void lengthFinishIsNotComplete() throws Exception {
        UUID releaseId = seedRelease(TemplateDocumentType.RUNBOOK, "Runbook");
        when(transform.transform(any(), any(), any())).thenReturn(new TransformOutcome.Done("length", true, 1, 1));

        Stream stream = readStream(post("/api/assistant/transform", withCookie(),
                transformBody(releaseId, "RUNBOOK", 1, "SUMMARIZE", null)));

        assertThat(stream.events()).extracting(Event::name).containsExactly("done");
        assertThat(stream.events().get(0).data().get("complete").asBoolean()).isFalse();
        assertThat(stream.events().get(0).data().get("truncated").asBoolean()).isTrue();
    }

    @Test
    void busyBecomesASingleErrorEventWithRetryAfter() throws Exception {
        UUID releaseId = seedRelease(TemplateDocumentType.RUNBOOK, "Runbook");
        when(transform.transform(any(), any(), any()))
                .thenReturn(new TransformOutcome.Failed(FailureReason.BUSY, 5));

        Stream stream = readStream(post("/api/assistant/transform", withCookie(),
                transformBody(releaseId, "RUNBOOK", 1, "CLEAN_UP", null)));

        assertThat(stream.events()).extracting(Event::name).containsExactly("error");
        assertThat(stream.events().get(0).data().get("outcome").asString()).isEqualTo("BUSY");
        assertThat(stream.events().get(0).data().get("retryAfterSeconds").asInt()).isEqualTo(5);
    }

    @Test
    void serverTooLargeCarriesBothSizes() throws Exception {
        UUID releaseId = seedRelease(TemplateDocumentType.RUNBOOK, "Runbook");
        when(transform.transform(any(), any(), any())).thenReturn(new TransformOutcome.TooLarge(null));

        Stream stream = readStream(post("/api/assistant/transform", withCookie(),
                transformBody(releaseId, "RUNBOOK", 1, "CLEAN_UP", null)));

        assertThat(stream.events()).extracting(Event::name).containsExactly("error");
        JsonNode error = stream.events().get(0).data();
        assertThat(error.get("outcome").asString()).isEqualTo("TOO_LARGE");
        assertThat(error.get("characters").asInt()).isEqualTo("Runbook".length());
        assertThat(error.get("maxCharacters").asInt()).isEqualTo(presetLimit(TransformMode.CLEAN_UP,
                TemplateDocumentType.RUNBOOK));
    }

    @Test
    void preStreamProblemsAreProblemJsonAndMakeNoModelCall() throws Exception {
        UUID releaseId = seedRelease(TemplateDocumentType.RUNBOOK, "Runbook");
        UUID bigRelease = seedRelease(TemplateDocumentType.APPROVAL, "x".repeat(6000));

        HttpResponse<InputStream> customWithoutText = post("/api/assistant/transform", withCookie(),
                transformBody(releaseId, "RUNBOOK", 1, "CUSTOM", null));
        assertThat(customWithoutText.statusCode()).isEqualTo(400);
        customWithoutText.body().close();

        HttpResponse<InputStream> unknown = post("/api/assistant/transform", withCookie(),
                transformBody(UUID.randomUUID(), "RUNBOOK", 1, "CLEAN_UP", null));
        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(body(unknown)).contains("DOCUMENT_NOT_FOUND");

        HttpResponse<InputStream> superseded = post("/api/assistant/transform", withCookie(),
                transformBody(releaseId, "RUNBOOK", 2, "CLEAN_UP", null));
        assertThat(superseded.statusCode()).isEqualTo(409);
        assertThat(body(superseded)).contains("REVISION_SUPERSEDED");

        HttpResponse<InputStream> tooLarge = post("/api/assistant/transform", withCookie(),
                transformBody(bigRelease, "APPROVAL", 1, "CLEAN_UP", null));
        assertThat(tooLarge.statusCode()).isEqualTo(413);
        assertThat(tooLarge.headers().firstValue("Content-Type").orElse("")).contains("problem+json");
        JsonNode problem = json.readTree(body(tooLarge));
        assertThat(problem.get("reason").asString()).isEqualTo("TEXT_TOO_LARGE");
        assertThat(problem.get("characters").asInt()).isEqualTo(6000);
        assertThat(problem.get("maxCharacters").asInt())
                .isEqualTo(presetLimit(TransformMode.CLEAN_UP, TemplateDocumentType.APPROVAL));

        verifyNoInteractions(transform);
    }

    @Test
    void invalidCookieIs401AndMissingCookieIs403() throws Exception {
        UUID releaseId = seedRelease(TemplateDocumentType.RUNBOOK, "Runbook");
        String request = transformBody(releaseId, "RUNBOOK", 1, "CLEAN_UP", null);

        HttpResponse<InputStream> invalid = post("/api/assistant/transform",
                AuthCookieWriter.ACCESS_COOKIE + "=not-a-jwt", request);
        assertThat(invalid.statusCode()).isEqualTo(401);
        assertThat(invalid.headers().firstValue("Content-Type").orElse("")).contains("problem+json");
        invalid.body().close();

        // Without any bearer cookie the CSRF filter answers first, as for every other POST under /api.
        HttpResponse<InputStream> missing = post("/api/assistant/transform", null, request);
        assertThat(missing.statusCode()).isEqualTo(403);
        missing.body().close();

        verifyNoInteractions(transform);
    }

    @Test
    void limitsComeFromConfiguration() throws Exception {
        HttpResponse<InputStream> response = get("/api/assistant/transform/limits");
        JsonNode limits = json.readTree(body(response));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(limits.get("numCtx").asInt()).isEqualTo(4096);
        for (TransformMode mode : List.of(TransformMode.CLEAN_UP, TransformMode.SUMMARIZE)) {
            for (TemplateDocumentType type : TemplateDocumentType.values()) {
                assertThat(limits.get("presets").get(mode.name()).get(type.name()).asInt())
                        .as(mode + "/" + type).isEqualTo(presetLimit(mode, type));
            }
        }
    }

    @Test
    void saveCleanUpAppendsRevisionTwoAndLeavesRevisionOneUntouched() throws Exception {
        UUID releaseId = seedRelease(TemplateDocumentType.RUNBOOK, "Runbook " + DOC_SENTINEL);

        HttpResponse<InputStream> saved = post("/api/assistant/documents/ai-revisions", withCookie(),
                saveBody(releaseId, 1, "CLEAN_UP", "# Cleaned " + SAVED_SENTINEL));
        assertThat(saved.statusCode()).isEqualTo(201);
        assertThat(json.readTree(body(saved)).get("revision").asInt()).isEqualTo(2);

        JsonNode latest = json.readTree(body(get("/api/releases/" + releaseId + "/documents/RUNBOOK")));
        assertThat(latest.get("revision").asInt()).isEqualTo(2);
        assertThat(latest.get("aiAssisted").asBoolean()).isTrue();
        assertThat(latest.get("aiMode").asString()).isEqualTo("CLEAN_UP");
        assertThat(latest.get("aiInstruction").asString())
                .isEqualTo(properties.documentAssist().instruction(TransformMode.CLEAN_UP, TemplateDocumentType.RUNBOOK));
        assertThat(latest.get("markdown").asString()).isEqualTo("# Cleaned " + SAVED_SENTINEL);
        JsonNode first = json.readTree(body(get("/api/releases/" + releaseId + "/documents/RUNBOOK?revision=1")));
        assertThat(first.get("markdown").asString()).isEqualTo("Runbook " + DOC_SENTINEL);
        assertThat(first.get("aiAssisted").asBoolean()).isFalse();

        HttpResponse<InputStream> summarize = post("/api/assistant/documents/ai-revisions", withCookie(),
                saveBody(releaseId, 2, "SUMMARIZE", "summary"));
        assertThat(summarize.statusCode()).isEqualTo(201);
        assertThat(json.readTree(body(summarize)).get("revision").asInt()).isEqualTo(3);
        JsonNode summarized = json.readTree(body(get("/api/releases/" + releaseId + "/documents/RUNBOOK")));
        assertThat(summarized.get("aiMode").asString()).isEqualTo("SUMMARIZE");
        assertThat(summarized.get("aiInstruction").asString())
                .isEqualTo(properties.documentAssist().instruction(TransformMode.SUMMARIZE, TemplateDocumentType.RUNBOOK));
        assertThat(summarized.get("markdown").asString()).isEqualTo("summary");
        List<DocumentSnapshot> revisions = snapshots.findAllRevisions(releaseId, TemplateDocumentType.RUNBOOK);
        assertThat(revisions).hasSize(3);
        assertThat(revisions).extracting(DocumentSnapshot::stalenessBasis)
                .containsOnly(revisions.get(0).stalenessBasis());

        HttpResponse<InputStream> stale = post("/api/assistant/documents/ai-revisions", withCookie(),
                saveBody(releaseId, 1, "CLEAN_UP", "stale"));
        assertThat(stale.statusCode()).isEqualTo(409);
        stale.body().close();

        HttpResponse<InputStream> staleSummary = post("/api/assistant/documents/ai-revisions", withCookie(),
                saveBody(releaseId, 2, "SUMMARIZE", "stale summary"));
        assertThat(staleSummary.statusCode()).isEqualTo(409);
        staleSummary.body().close();
        assertThat(snapshots.findAllRevisions(releaseId, TemplateDocumentType.RUNBOOK)).hasSize(3);
    }

    @Test
    void browserDisconnectReachesTheTransformPortAsClientGone() throws Exception {
        UUID releaseId = seedRelease(TemplateDocumentType.RUNBOOK, "Runbook");
        CountDownLatch observed = new CountDownLatch(1);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        when(transform.transform(any(), any(), any())).thenAnswer(inv -> {
            ChatListener listener = inv.getArgument(2);
            try {
                for (int i = 0; i < 100; i++) {
                    listener.heartbeat();
                    Thread.sleep(100);
                }
            } catch (ClientGoneException e) {
                thrown.set(e);
                throw e;
            } finally {
                observed.countDown();
            }
            return new TransformOutcome.Failed(FailureReason.FAILED, null);
        });

        HttpResponse<InputStream> response = post("/api/assistant/transform", withCookie(),
                transformBody(releaseId, "RUNBOOK", 1, "CLEAN_UP", null));
        assertThat(response.body().read()).isNotEqualTo(-1);
        response.body().close();

        assertThat(observed.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(thrown.get()).isInstanceOf(ClientGoneException.class);
    }

    @Test
    void streamPastTheDeadlineEndsWithADeadlineError() throws Exception {
        UUID releaseId = seedRelease(TemplateDocumentType.RUNBOOK, "Runbook");
        when(transform.transform(any(), any(), any())).thenAnswer(inv -> {
            ChatListener listener = inv.getArgument(2);
            for (int i = 0; i < 60; i++) {
                listener.heartbeat();
                Thread.sleep(100);
            }
            return new TransformOutcome.Failed(FailureReason.FAILED, null);
        });

        Stream stream = readStream(post("/api/assistant/transform", withCookie(),
                transformBody(releaseId, "RUNBOOK", 1, "CLEAN_UP", null)));

        assertThat(stream.events()).extracting(Event::name).containsExactly("error");
        assertThat(stream.events().get(0).data().get("outcome").asString()).isEqualTo("DEADLINE");
    }

    @Test
    void aDeadlineAfterAFinishedSectionSendsTheOriginalRestBeforeTheError() throws Exception {
        String markdown = "## A\nOne\n## B\nTwo\n## C\nThree\n";
        UUID releaseId = seedRelease(TemplateDocumentType.RUNBOOK, markdown);
        when(transform.transform(any(), any(), any())).thenAnswer(inv -> {
            ChatListener listener = inv.getArgument(2);
            if ("One".equals(inv.getArgument(0))) {
                listener.token("ONE");
                return new TransformOutcome.Done("stop", false, 10, 20);
            }
            listener.token("TW");
            for (int i = 0; i < 60; i++) {
                listener.heartbeat();
                Thread.sleep(100);
            }
            return new TransformOutcome.Failed(FailureReason.FAILED, null);
        });

        Stream stream = readStream(post("/api/assistant/transform", withCookie(),
                transformBody(releaseId, "RUNBOOK", 1, "CLEAN_UP", null)));

        List<String> names = stream.events().stream().map(Event::name).toList();
        assertThat(names.subList(names.size() - 2, names.size())).containsExactly("remainder", "error");
        JsonNode remainder = stream.events().get(names.size() - 2).data();
        String sent = stream.events().stream().filter(event -> event.name().equals("token"))
                .map(event -> event.data().get("text").asString()).collect(java.util.stream.Collectors.joining());
        assertThat(sent.substring(0, remainder.get("keep").asInt()) + remainder.get("text").asString())
                .isEqualTo("## A\nONE\n## B\nTwo\n## C\nThree\n");
        assertThat(stream.events().get(names.size() - 1).data().get("outcome").asString()).isEqualTo("DEADLINE");
    }

    @Test
    void offlineAssistantDoesNotBlockDocumentGeneration() throws Exception {
        Release release = releases.create(Release.create("Offline", "1.0", "CNV", "rm@acme.com", Instant.now()),
                List.of());
        Template template = templates.create(Template.create("Runbook", TemplateDocumentType.RUNBOOK, ACTOR,
                Instant.now()));
        templates.setActive(template.id());
        when(transform.transform(any(), any(), any()))
                .thenReturn(new TransformOutcome.Failed(FailureReason.OFFLINE, null));

        HttpResponse<InputStream> generated = post("/api/releases/" + release.id() + "/documents/RUNBOOK",
                withCookie(), "{}");

        assertThat(generated.statusCode()).isEqualTo(201);
        generated.body().close();
    }

    private int presetLimit(TransformMode mode, TemplateDocumentType type) {
        return new TransformSizeEstimator(properties.documentAssist())
                .maxChars(properties.documentAssist().instruction(mode, type).length());
    }

    private UUID seedRelease(TemplateDocumentType type, String markdown) {
        Release release = releases.create(Release.create("Assist " + UUID.randomUUID(), "1.0", "CNV", "rm@acme.com", Instant.now()),
                List.of());
        snapshots.create(DocumentSnapshot.create(release.id(), type, 1, markdown, null, "Template", "Body",
                ChromeFields.empty(), ACTOR, Instant.now()), null);
        return release.id();
    }

    private String withCookie() {
        return AuthCookieWriter.ACCESS_COOKIE + "=" + tokens.issue(ACTOR, "rm@acme.com").accessToken();
    }

    private static String transformBody(UUID releaseId, String type, int revision, String mode, String instruction) {
        return "{\"releaseId\":\"" + releaseId + "\",\"documentType\":\"" + type + "\",\"sourceRevision\":"
                + revision + ",\"mode\":\"" + mode + "\",\"instruction\":"
                + (instruction == null ? "null" : "\"" + instruction + "\"") + "}";
    }

    private static String saveBody(UUID releaseId, int revision, String mode, String markdown) {
        return "{\"releaseId\":\"" + releaseId + "\",\"documentType\":\"RUNBOOK\",\"sourceRevision\":" + revision
                + ",\"mode\":\"" + mode + "\",\"instruction\":null,\"markdown\":\"" + markdown + "\"}";
    }

    private HttpResponse<InputStream> post(String path, String cookie, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    private HttpResponse<InputStream> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Cookie", withCookie()).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    private static String body(HttpResponse<InputStream> response) throws IOException {
        try (InputStream in = response.body()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Stream readStream(HttpResponse<InputStream> response) throws IOException {
        List<Event> events = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String name = null;
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line.replace(": ", ":"));
                if (line.startsWith("event:")) {
                    name = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:") && name != null) {
                    events.add(new Event(name, json.readTree(line.substring("data:".length()).trim())));
                    name = null;
                }
            }
        }
        return new Stream(events, lines);
    }

    private record Stream(List<Event> events, List<String> lines) {
    }

    private record Event(String name, JsonNode data) {
    }
}
