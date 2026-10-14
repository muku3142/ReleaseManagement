package com.evision.ragservice.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.evision.ragservice.FakeOllamaServer;
import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.SseEvents;
import com.evision.ragservice.application.ClientService;
import com.evision.ragservice.application.CollectionService;
import com.evision.ragservice.application.GenerationGate;
import com.evision.ragservice.application.IndexWorker;
import com.evision.ragservice.domain.ChunkEmbedding;
import com.evision.ragservice.domain.ClaimedDocument;
import com.evision.ragservice.domain.ContentHash;
import com.evision.ragservice.domain.DocumentContent;
import com.evision.ragservice.ports.DocumentStore;
import com.evision.ragservice.ports.IndexQueue;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

// Real Tomcat: client disconnects and async dispatch cannot be shown with MockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SseStreamingIT {

    private static final String MODEL = "nomic-embed-text";
    private static final int DIMENSIONS = 768;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CHAT_BODY = "{\"collections\":[\"docs\"],\"question\":\"release?\"}";

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
    @Autowired
    private GenerationGate gate;
    @Autowired
    private IndexWorker indexWorker;

    private final HttpClient http = HttpClient.newHttpClient();
    private UUID clientA;
    private String keyA;
    private String keyB;
    private UUID collectionA;

    @BeforeEach
    void setUp() throws Exception {
        awaitGateFree();
        ollama.reset();
        jdbc.sql("DELETE FROM chunk").update();
        jdbc.sql("DELETE FROM document").update();
        jdbc.sql("DELETE FROM collection").update();
        jdbc.sql("DELETE FROM client_app").update();
        ClientService.IssuedKey issuedA = clients.register("client-a");
        clientA = issuedA.client().id();
        keyA = issuedA.apiKey();
        ClientService.IssuedKey issuedB = clients.register("client-b");
        keyB = issuedB.apiKey();
        collectionA = collections.put(clientA, "docs", null).collection().id();
        seed(collectionA, "doc-e0");
        UUID collectionB = collections.put(issuedB.client().id(), "docs", null).collection().id();
        seed(collectionB, "doc-b");
    }

    private void seed(UUID collectionId, String externalId) {
        String text = "text of " + externalId;
        documents.upsert(collectionId,
                new DocumentContent(externalId, externalId, text, "https://example.test/" + externalId, Map.of()),
                ContentHash.of(externalId, text));
        float[] vector = new float[DIMENSIONS];
        vector[0] = 1f;
        for (ClaimedDocument claimed : queue.claim(10, Duration.ofMinutes(10))) {
            queue.complete(claimed.id(), claimed.contentHash(), MODEL,
                    List.of(new ChunkEmbedding(0, claimed.text(), vector)));
        }
    }

    private void awaitGateFree() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (gate.anyInFlight() && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertThat(gate.anyInFlight()).as("gate free").isFalse();
    }

    private HttpRequest chatRequest(String key, String accept) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/chat"))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(CHAT_BODY));
        if (accept != null) {
            builder.header("Accept", accept);
        }
        return builder.build();
    }

    private HttpResponse<InputStream> open(String key) throws Exception {
        return http.send(chatRequest(key, null), HttpResponse.BodyHandlers.ofInputStream());
    }

    private static BufferedReader reader(HttpResponse<InputStream> response) {
        return new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
    }

    private static SseEvents.Event nextOfKind(BufferedReader reader, String name) {
        SseEvents.Event event;
        while ((event = SseEvents.next(reader)) != null) {
            if (name.equals(event.name())) {
                return event;
            }
        }
        return null;
    }

    private static List<String> slowAnswer(int tokens) {
        List<String> lines = new ArrayList<>(IntStream.range(0, tokens).mapToObj(i -> FakeOllamaServer.tokenLine("t" + i + " ")).toList());
        lines.add(FakeOllamaServer.doneLine("stop", 100, tokens));
        return lines;
    }

    @Test
    void firstTokenArrivesWhileOllamaIsStillStreaming() throws Exception {
        ollama.chat(slowAnswer(20), Duration.ofMillis(100));
        long start = System.nanoTime();
        HttpResponse<InputStream> response = open(keyA);
        try (BufferedReader reader = reader(response)) {
            SseEvents.Event first = nextOfKind(reader, "token");
            long firstTokenMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
            assertThat(first).isNotNull();
            assertThat(firstTokenMillis).isLessThan(1000);

            int tokens = 1;
            List<String> rest = new ArrayList<>();
            SseEvents.Event event;
            while ((event = SseEvents.next(reader)) != null) {
                rest.add(event.name());
                if ("token".equals(event.name())) {
                    tokens++;
                }
            }
            assertThat(tokens).isEqualTo(20);
            assertThat(rest).endsWith("citations", "done");
        }
        awaitGateFree();
    }

    @Test
    void clientAbortStopsOllamaFreesTheGateAndIndexingResumes() throws Exception {
        ollama.chat(slowAnswer(100), Duration.ofMillis(100));
        HttpResponse<InputStream> response = open(keyA);
        BufferedReader reader = reader(response);
        try {
            assertThat(nextOfKind(reader, "token")).isNotNull();
            assertThat(gate.anyInFlight()).isTrue();

            documents.upsert(collectionA, new DocumentContent("late", "late", "late text", "https://example.test/late",
                    Map.of()), ContentHash.of("late", "late text"));
            indexWorker.tick();
            assertThat(statusOf("late")).isEqualTo("PENDING");
        } finally {
            reader.close();
        }

        assertThat(ollama.awaitChatAborted(Duration.ofSeconds(5))).isTrue();
        awaitGateFree();
        indexWorker.tick();
        assertThat(statusOf("late")).isEqualTo("INDEXED");
    }

    private String statusOf(String externalId) {
        return jdbc.sql("SELECT status FROM document WHERE external_id = ?").param(externalId)
                .query(String.class).single();
    }

    @Test
    void deadlineEndsTheStreamWithAnErrorEventAfterTheTokensAlreadySent() throws Exception {
        ollama.chat(slowAnswer(100), Duration.ofMillis(100));
        long start = System.nanoTime();
        HttpResponse<InputStream> response = open(keyA);
        List<String> names = new ArrayList<>();
        SseEvents.Event error = null;
        try (BufferedReader reader = reader(response)) {
            SseEvents.Event event;
            while ((event = SseEvents.next(reader)) != null) {
                names.add(event.name());
                if ("error".equals(event.name())) {
                    error = event;
                }
            }
        }
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(names).contains("token").doesNotContain("done");
        assertThat(error).isNotNull();
        assertThat(JSON.readTree(error.data()).get("reason").asString()).isEqualTo("DEADLINE");
        assertThat(elapsedMillis).isBetween(2500L, 6000L);
        assertThat(ollama.awaitChatAborted(Duration.ofSeconds(5))).isTrue();
        awaitGateFree();
    }

    @Test
    void secondRequestFromTheSameClientIsRefusedWhileADifferentClientIsServed() throws Exception {
        ollama.chat(slowAnswer(100), Duration.ofMillis(100));
        HttpResponse<InputStream> first = open(keyA);
        BufferedReader reader = reader(first);
        try {
            assertThat(nextOfKind(reader, "token")).isNotNull();

            HttpResponse<String> plain = http.send(chatRequest(keyA, null), HttpResponse.BodyHandlers.ofString());
            assertThat(plain.statusCode()).isEqualTo(429);
            assertThat(plain.headers().firstValue("Retry-After")).isPresent();
            assertThat(plain.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");

            HttpResponse<String> sse = http.send(chatRequest(keyA, "text/event-stream"),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(sse.statusCode()).isEqualTo(429);
            assertThat(sse.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");

            HttpResponse<InputStream> other = open(keyB);
            try {
                assertThat(other.statusCode()).isEqualTo(200);
            } finally {
                other.body().close();
            }
        } finally {
            reader.close();
        }
        assertThat(ollama.awaitChatAborted(Duration.ofSeconds(5))).isTrue();
        awaitGateFree();
    }

    @Test
    void midStreamErrorLineIsAnErrorEventOverRealHttp() throws Exception {
        ollama.chat(List.of(FakeOllamaServer.tokenLine("Partial "), "{\"error\":\"boom\"}"), Duration.ofMillis(10));
        HttpResponse<InputStream> response = open(keyA);
        SseEvents.Event error;
        try (BufferedReader reader = reader(response)) {
            error = nextOfKind(reader, "error");
        }

        assertThat(error).isNotNull();
        assertThat(JSON.readTree(error.data()).get("reason").asString()).isEqualTo("GENERATION_FAILED");
        awaitGateFree();
    }

    @Test
    void searchingStatusArrivesBeforeTheColdModelAnswersWithHeaders() throws Exception {
        ollama.chatHeaderDelay(Duration.ofMillis(1500));
        long start = System.nanoTime();
        HttpResponse<InputStream> response = open(keyA);
        try (BufferedReader reader = reader(response)) {
            SseEvents.Event status = nextOfKind(reader, "status");
            long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();

            assertThat(status).isNotNull();
            assertThat(JSON.readTree(status.data()).get("phase").asString()).isEqualTo("SEARCHING");
            assertThat(millis).isLessThan(1000);
        }
        ollama.awaitChatAborted(Duration.ofSeconds(1));
        awaitGateFree();
    }
}
