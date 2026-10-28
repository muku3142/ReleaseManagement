package com.evision.releasemgmt.adapters.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.adapters.security.AuthCookieWriter;
import com.evision.releasemgmt.adapters.security.JwtTokenService;
import com.evision.releasemgmt.domain.assistant.ChatOutcome;
import com.evision.releasemgmt.domain.assistant.ClientGoneException;
import com.evision.releasemgmt.domain.assistant.FailureReason;
import com.evision.releasemgmt.domain.assistant.SignedTurn;
import com.evision.releasemgmt.domain.assistant.Source;
import com.evision.releasemgmt.domain.assistant.ToolCall;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.ports.RagChatPort;
import com.evision.releasemgmt.ports.RagChatPort.ChatListener;
import com.evision.releasemgmt.ports.RagIndexPort;
import com.evision.releasemgmt.ports.ReleaseRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
        "app.assistant.stream-deadline=PT2S"})
@Testcontainers
class AssistantChatStreamIT {

    private static final String QUESTION = "SENTINEL-QUESTION-7731";
    private static final String TOKEN = "SENTINEL-TOKEN-4410";
    private static final String ARGUMENT = "SENTINEL-ARG-5521";
    private static final String API_KEY = "it-key-SENTINEL-KEY-9921";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @MockitoBean
    private RagIndexPort index;

    @MockitoBean
    private RagChatPort chat;

    @Autowired
    private JwtTokenService tokens;

    @Autowired
    private ReleaseRepository releases;

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
        List<String> lines = logs.list.stream()
                .map(e -> e.getFormattedMessage() + (e.getThrowableProxy() == null ? ""
                        : ThrowableProxyUtil.asString(e.getThrowableProxy())))
                .toList();
        assertThat(lines).noneMatch(l -> l.contains("AccessDeniedException"));
        assertThat(lines).noneMatch(l -> l.contains("already committed"));
        assertThat(lines).noneMatch(l -> l.contains(QUESTION) || l.contains(TOKEN) || l.contains(ARGUMENT)
                || l.contains(API_KEY));
        jdbcTemplate.execute("TRUNCATE release CASCADE");
    }

    @Test
    void streamsTheBrowserProtocolInOrder() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        when(chat.chat(any(), any())).thenAnswer(inv -> {
            ChatListener listener = inv.getArgument(1);
            if (calls.incrementAndGet() == 1) {
                return new ChatOutcome.ToolCallsRequested(new SignedTurn("{}",
                        List.of(new ToolCall("c1", "releases", Map.of("release", ARGUMENT)))));
            }
            listener.token("Hello ");
            listener.token(TOKEN + " [1]");
            return new ChatOutcome.Answered("Hello " + TOKEN + " [1]",
                    List.of(new Source(1, "rm-tickets", "PROJ-1", "Ticket", null, "PASSAGE",
                            Map.of("ticket_key", "PROJ-1"))),
                    false, "stop", false, "qwen-test", "hash-1");
        });

        HttpResponse<InputStream> response = post(withCookie(), body(QUESTION, "[]", null));
        List<Event> events = readEvents(response);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-cache");
        assertThat(response.headers().firstValue("X-Accel-Buffering")).contains("no");
        assertThat(events).extracting(Event::name).containsExactly("discard", "status", "token", "token", "done");
        assertThat(events.get(1).data().get("tool").asString()).isEqualTo("releases");
        JsonNode done = events.get(4).data();
        assertThat(done.get("sources").get(0).get("kind").asString()).isEqualTo("TICKET");
        assertThat(done.get("sources").get(0).get("ticketKey").asString()).isEqualTo("PROJ-1");
        assertThat(done.get("tools").get(0).get("name").asString()).isEqualTo("releases");
        assertThat(done.get("historyTrimmed").asBoolean()).isFalse();
        assertThat(done.get("meta").get("model").asString()).isEqualTo("qwen-test");
        assertThat(done.get("meta").get("promptHash").asString()).isEqualTo("hash-1");
        assertThat(events.toString()).doesNotContain(API_KEY);
    }

    @Test
    void busyBecomesASingleErrorEventWithRetryAfter() throws Exception {
        when(chat.chat(any(), any())).thenReturn(new ChatOutcome.Failed(FailureReason.BUSY, 5));

        List<Event> events = readEvents(post(withCookie(), body("q", "[]", null)));

        assertThat(events).extracting(Event::name).containsExactly("error");
        assertThat(events.get(0).data().get("outcome").asString()).isEqualTo("BUSY");
        assertThat(events.get(0).data().get("retryAfterSeconds").asInt()).isEqualTo(5);
    }

    @Test
    void rejectedIsReportedAsFailed() throws Exception {
        when(chat.chat(any(), any())).thenReturn(new ChatOutcome.Failed(FailureReason.REJECTED, null));

        List<Event> events = readEvents(post(withCookie(), body("q", "[]", null)));

        assertThat(events.get(0).data().get("outcome").asString()).isEqualTo("FAILED");
    }

    @Test
    void scopedChatForAKnownReleaseStreams() throws Exception {
        Release release = releases.create(Release.create("Scoped", "1.0", "CNV", "rm@acme.com", Instant.now()),
                List.of());
        when(chat.chat(any(), any())).thenReturn(
                new ChatOutcome.Answered("ok", List.of(), false, "stop", false, "m", "h"));

        List<Event> events = readEvents(post(withCookie(), body("q", "[]", release.id().toString())));

        assertThat(events).extracting(Event::name).containsExactly("done");
    }

    @Test
    void invalidCookieIsAProblemJson401AndNoChatCall() throws Exception {
        HttpResponse<InputStream> response = post(AuthCookieWriter.ACCESS_COOKIE + "=not-a-jwt", body("q", "[]", null));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("problem+json");
        verifyNoInteractions(chat);
        response.body().close();
    }

    // Without any bearer cookie the CSRF filter answers first, as for every other POST under /api.
    @Test
    void missingCookieIsRefusedBeforeAnyStreamOpens() throws Exception {
        HttpResponse<InputStream> response = post(null, body("q", "[]", null));

        assertThat(response.statusCode()).isEqualTo(403);
        verifyNoInteractions(chat);
        response.body().close();
    }

    @Test
    void overlongQuestionIs400() throws Exception {
        HttpResponse<InputStream> response = post(withCookie(), body("x".repeat(1501), "[]", null));

        assertThat(response.statusCode()).isEqualTo(400);
        verifyNoInteractions(chat);
        response.body().close();
    }

    @Test
    void historyRoleOtherThanUserOrAssistantIs400() throws Exception {
        HttpResponse<InputStream> response = post(withCookie(),
                body("q", "[{\"role\":\"system\",\"content\":\"hi\"}]", null));

        assertThat(response.statusCode()).isEqualTo(400);
        verifyNoInteractions(chat);
        response.body().close();
    }

    @Test
    void unknownReleaseIsAProblemJson404() throws Exception {
        HttpResponse<InputStream> response = post(withCookie(), body("q", "[]", UUID.randomUUID().toString()));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(new String(response.body().readAllBytes(), StandardCharsets.UTF_8)).contains("RELEASE_NOT_FOUND");
        verifyNoInteractions(chat);
    }

    @Test
    void browserDisconnectReachesTheChatPortAsClientGone() throws Exception {
        CountDownLatch observed = new CountDownLatch(1);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        when(chat.chat(any(), any())).thenAnswer(inv -> {
            ChatListener listener = inv.getArgument(1);
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
            return new ChatOutcome.Failed(FailureReason.FAILED, null);
        });

        HttpResponse<InputStream> response = post(withCookie(), body("q", "[]", null));
        assertThat(response.body().read()).isNotEqualTo(-1);
        response.body().close();

        assertThat(observed.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(thrown.get()).isInstanceOf(ClientGoneException.class);
    }

    @Test
    void streamPastTheDeadlineEndsWithADeadlineError() throws Exception {
        when(chat.chat(any(), any())).thenAnswer(inv -> {
            ChatListener listener = inv.getArgument(1);
            for (int i = 0; i < 60; i++) {
                listener.heartbeat();
                Thread.sleep(100);
            }
            return new ChatOutcome.Failed(FailureReason.FAILED, null);
        });

        List<Event> events = readEvents(post(withCookie(), body("q", "[]", null)));

        assertThat(events).extracting(Event::name).containsExactly("error");
        assertThat(events.get(0).data().get("outcome").asString()).isEqualTo("DEADLINE");
    }

    private String withCookie() {
        return AuthCookieWriter.ACCESS_COOKIE + "=" + tokens.issue("557058:abc-123", "rm@acme.com").accessToken();
    }

    private String body(String question, String history, String releaseId) {
        return "{\"question\":\"" + question + "\",\"history\":" + history + ",\"releaseId\":"
                + (releaseId == null ? "null" : "\"" + releaseId + "\"") + "}";
    }

    private HttpResponse<InputStream> post(String cookie, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/assistant/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    private List<Event> readEvents(HttpResponse<InputStream> response) throws IOException {
        List<Event> events = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String name = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event:")) {
                    name = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:") && name != null) {
                    events.add(new Event(name, json.readTree(line.substring("data:".length()).trim())));
                    name = null;
                }
            }
        }
        return events;
    }

    private record Event(String name, JsonNode data) {
    }
}
