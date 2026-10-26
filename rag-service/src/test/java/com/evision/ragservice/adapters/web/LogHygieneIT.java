package com.evision.ragservice.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.evision.ragservice.FakeOllamaServer;
import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.SseEvents;
import com.evision.ragservice.application.ClientService;
import com.evision.ragservice.application.CollectionService;
import com.evision.ragservice.domain.ChunkEmbedding;
import com.evision.ragservice.domain.ClaimedDocument;
import com.evision.ragservice.domain.ContentHash;
import com.evision.ragservice.domain.DocumentContent;
import com.evision.ragservice.ports.DocumentStore;
import com.evision.ragservice.ports.IndexQueue;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

// SAFE-03: neither request content nor answer content may reach a log event, even when frameworks log at DEBUG
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LogHygieneIT {

    private static final String CANARY = "CANARY-7f3a9c-SECRET";
    private static final String MODEL = "nomic-embed-text";
    private static final int DIMENSIONS = 768;

    static FakeOllamaServer ollama = FakeOllamaServer.start();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @DynamicPropertySource
    static void ollamaUrl(DynamicPropertyRegistry registry) {
        registry.add("rag.ollama.base-url", ollama::baseUrl);
    }

    @AfterAll
    static void stopFake() {
        ollama.close();
    }

    @Value("${local.server.port}")
    private int port;
    @Value("${rag.admin-key}")
    private String adminKey;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private ClientService clients;
    @Autowired
    private CollectionService collections;
    @Autowired
    private DocumentStore documents;
    @Autowired
    private IndexQueue queue;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${rag.tools.signing-secret}")
    private String signingSecret;

    private final HttpClient http = HttpClient.newHttpClient();
    private String key;
    private String signature;

    @BeforeEach
    void setUp() {
        ollama.reset();
        ollama.chat(FakeOllamaServer.answer("Answer " + CANARY + " ", "[1]."), Duration.ofMillis(10));
        jdbc.sql("DELETE FROM chunk").update();
        jdbc.sql("DELETE FROM document").update();
        jdbc.sql("DELETE FROM collection").update();
        jdbc.sql("DELETE FROM client_app").update();
        ClientService.IssuedKey issued = clients.register("client-a");
        key = issued.apiKey();
        UUID collectionId = collections.put(issued.client().id(), "docs", null).collection().id();
        String text = "document text " + CANARY;
        documents.upsert(collectionId, new DocumentContent("doc-1", "title " + CANARY, text,
                "https://example.test/doc-1", Map.of()), ContentHash.of("doc-1", text));
        float[] vector = new float[DIMENSIONS];
        vector[0] = 1f;
        for (ClaimedDocument claimed : queue.claim(10, Duration.ofMinutes(10))) {
            queue.complete(claimed.id(), claimed.contentHash(), MODEL,
                    List.of(new ChunkEmbedding(0, claimed.text(), vector)));
        }
    }

    private HttpResponse<String> post(String path, String bearer, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + bearer)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private List<ILoggingEvent> driveEveryRequestKind() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            String chat = "{\"collections\":[\"docs\"],\"question\":\"" + CANARY + " question\","
                    + "\"history\":[{\"role\":\"user\",\"content\":\"" + CANARY + " earlier\"}]}";
            assertThat(post("/v1/chat", key, chat).body()).contains("event:done");
            String transform = "{\"text\":\"" + CANARY + " text\",\"instruction\":\"" + CANARY + " rewrite\"}";
            assertThat(post("/v1/transform", key, transform).body()).contains("event:done");

            assertThat(post("/v1/chat", key, "{\"question\":\"" + CANARY + " {").statusCode()).isEqualTo(400);
            String oversize = "{\"text\":\"" + (CANARY + " ").repeat(10_000) + "\",\"instruction\":\"x\"}";
            assertThat(post("/v1/transform", key, oversize).statusCode()).isEqualTo(413);
            HttpResponse<String> badName = http.send(HttpRequest
                    .newBuilder(URI.create("http://localhost:" + port + "/v1/collections/bad%20name"))
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + key)
                    .PUT(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(badName.statusCode()).isEqualTo(400);
            // the admin key is never a legitimate bearer on a content endpoint; the point is the failed path logs nothing
            post("/v1/chat", adminKey, chat);
            driveToolRoundTrip();
            Thread.sleep(300);
            return new ArrayList<>(appender.list);
        } finally {
            root.detachAppender(appender);
        }
    }

    private void driveToolRoundTrip() throws Exception {
        String tools = "[{\"name\":\"get_release_status\",\"description\":\"" + CANARY + " description\","
                + "\"parameters\":{\"type\":\"object\",\"properties\":{\"version\":{\"type\":\"string\"}},"
                + "\"required\":[\"version\"]}}]";
        ollama.reset();
        ollama.chatScript(List.of(
                FakeOllamaServer.toolCalls(FakeOllamaServer.toolCallLine("call_1", "get_release_status",
                        "{\"version\":\"" + CANARY + "\"}")),
                FakeOllamaServer.answer("Answer ", "[1].")), Duration.ofMillis(10));
        try {
            String first = "{\"collections\":[\"docs\"],\"question\":\"release?\",\"tools\":" + tools + "}";
            SseEvents.Event call = SseEvents.parse(post("/v1/chat", key, first).body()).stream()
                    .filter(e -> "tool_call".equals(e.name())).findFirst().orElseThrow();
            ObjectNode turn = (ObjectNode) JSON.readTree(call.data());
            signature = turn.get("signature").asString();

            String content = "{\"callId\":\"call_1\",\"content\":\"" + CANARY + " result\",\"isError\":false,"
                    + "\"source\":{\"id\":\"rel-1\",\"title\":\"" + CANARY + " title\",\"url\":\"https://x.invalid/r\"}}";
            ObjectNode resumeTurn = turn.deepCopy();
            resumeTurn.set("results", JSON.readTree("[" + content + "]"));
            assertThat(post("/v1/chat", key, toolBody(tools, resumeTurn)).body()).contains("event:done");

            ObjectNode forged = resumeTurn.deepCopy();
            forged.put("signature", "A".repeat(signature.length()));
            assertThat(post("/v1/chat", key, toolBody(tools, forged)).statusCode()).isEqualTo(400);

            ObjectNode oversized = turn.deepCopy();
            oversized.set("results", JSON.readTree("[{\"callId\":\"call_1\",\"content\":\"" + CANARY
                    + "x".repeat(8001) + "\",\"isError\":false}]"));
            assertThat(post("/v1/chat", key, toolBody(tools, oversized)).statusCode()).isEqualTo(413);
        } finally {
            ollama.reset();
            ollama.chat(FakeOllamaServer.answer("Answer " + CANARY + " ", "[1]."), Duration.ofMillis(10));
        }
    }

    private static String toolBody(String tools, ObjectNode turn) {
        return "{\"collections\":[\"docs\"],\"question\":\"release?\",\"tools\":" + tools + ",\"toolTurns\":["
                + turn + "]}";
    }

    private void assertNoCanary(List<ILoggingEvent> events) {
        assertThat(signature).isNotBlank();
        List<String> forbidden = List.of(CANARY, signature, signingSecret);
        for (ILoggingEvent event : events) {
            for (String secret : forbidden) {
                assertThat(event.getFormattedMessage()).as(event.getLoggerName()).doesNotContain(secret);
                if (event.getArgumentArray() != null) {
                    for (Object argument : event.getArgumentArray()) {
                        assertThat(String.valueOf(argument)).as(event.getLoggerName()).doesNotContain(secret);
                    }
                }
                for (IThrowableProxy proxy = event.getThrowableProxy(); proxy != null; proxy = proxy.getCause()) {
                    assertThat(proxy.getMessage()).as(event.getLoggerName()).doesNotContain(secret);
                }
            }
        }
    }

    private static void assertMetadataLines(List<ILoggingEvent> events) {
        assertThat(events).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.startsWith("generation endpoint=chat outcome="))
                .anyMatch(m -> m.startsWith("generation endpoint=transform outcome="))
                .anyMatch(m -> m.startsWith("generation endpoint=chat") && m.contains("promptVersion=p11-v2")
                        && m.contains("round=1 "));
    }

    @Test
    void noContentReachesLogsAtDefaultLevels() throws Exception {
        List<ILoggingEvent> events = driveEveryRequestKind();

        assertNoCanary(events);
        assertMetadataLines(events);
    }

    @Test
    void noContentReachesLogsWithFrameworkAndApplicationAtDebug() throws Exception {
        Logger web = (Logger) LoggerFactory.getLogger("org.springframework.web");
        Logger app = (Logger) LoggerFactory.getLogger("com.evision");
        Level webBefore = web.getLevel();
        Level appBefore = app.getLevel();
        web.setLevel(Level.DEBUG);
        app.setLevel(Level.DEBUG);
        try {
            List<ILoggingEvent> events = driveEveryRequestKind();

            assertNoCanary(events);
            assertMetadataLines(events);
        } finally {
            web.setLevel(webBefore);
            app.setLevel(appBefore);
        }
    }
}
