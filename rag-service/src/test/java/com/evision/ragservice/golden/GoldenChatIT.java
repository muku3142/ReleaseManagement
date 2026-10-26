package com.evision.ragservice.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.SseEvents;
import com.evision.ragservice.application.ChatService;
import com.evision.ragservice.application.IndexWorker;
import com.evision.ragservice.application.TransformService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opt-in live tier: runs the golden fixture against a real Ollama through the real HTTP API.
 * Selected only by {@code mvn test -Pgolden}; skips itself when Ollama or a model is missing.
 * Phase 13 extends the fixture files (golden/*.json), not this harness.
 */
@Tag("golden")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "rag.chat.deadline=PT120S", "rag.chat.heartbeat-interval=PT15S", "rag.ollama.read-timeout=PT120S"})
@Testcontainers
class GoldenChatIT {

    private static final int RUNS = 3;
    private static final String OLLAMA_URL = System.getenv().getOrDefault("RAG_GOLDEN_OLLAMA_URL", "http://localhost:11434");
    private static final String CHAT_MODEL = System.getProperty("rag.golden.model", "qwen3:4b-instruct-2507-q4_K_M");
    private static final String EMBEDDING_MODEL = "nomic-embed-text";
    private static final Path GOLDEN = Path.of("src/test/resources/golden");
    private static final Path REPORT = Path.of("target/golden-report.json");
    private static final List<Pattern> IDENTIFIERS = List.of(
            Pattern.compile("\\b\\d+\\.\\d+\\.\\d+\\b"),
            Pattern.compile("\\b[A-Z][A-Z0-9]+-\\d+\\b"),
            Pattern.compile("\\b(DEV|SIT|UAT|Staging|Production)\\b"));
    private static final Pattern LOG_FIELD = Pattern.compile("(\\w+)=(\\S+)");
    private static final List<String> LOG_METADATA = List.of("topScore", "estimatedPromptTokens", "promptEvalCount",
            "evalCount", "loadMs", "ttftMs", "totalMs", "doneReason", "citationBasis");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @DynamicPropertySource
    static void ollama(DynamicPropertyRegistry registry) {
        registry.add("rag.ollama.base-url", () -> OLLAMA_URL);
        registry.add("rag.chat.model", () -> CHAT_MODEL);
        registry.add("rag.chat.num-ctx", () -> "4096");
        registry.add("rag.chat.num-predict", () -> "512");
        registry.add("rag.chat.k-default", () -> "4");
        registry.add("rag.chat.k-max", () -> "4");
        registry.add("rag.chat.history-token-budget", () -> "600");
    }

    @BeforeAll
    static void requireOllamaAndModels() throws Exception {
        HttpClient probe = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        boolean ready;
        try {
            ready = probe.send(HttpRequest.newBuilder(URI.create(OLLAMA_URL + "/api/version"))
                    .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.discarding())
                    .statusCode() == 200 && hasModel(probe, CHAT_MODEL) && hasModel(probe, EMBEDDING_MODEL);
        } catch (java.io.IOException e) {
            ready = false;
        }
        assumeTrue(ready, "Ollama with " + CHAT_MODEL + " and " + EMBEDDING_MODEL + " is required at " + OLLAMA_URL);
    }

    private static boolean hasModel(HttpClient probe, String model) throws Exception {
        HttpRequest show = HttpRequest.newBuilder(URI.create(OLLAMA_URL + "/api/show"))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"model\":\"" + model + "\"}")).build();
        return probe.send(show, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
    }

    @Value("${local.server.port}")
    private int port;

    @Value("${rag.admin-key}")
    private String adminKey;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private IndexWorker worker;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = JsonMapper.builder().build();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Map<String, String> clientKeys = new LinkedHashMap<>();
    private final Map<String, String> seedTextByClient = new LinkedHashMap<>();

    record Run(List<String> failedChecks, Map<String, String> metadata) {}

    @Test
    void goldenFixtureHoldsOnTheLiveModel() throws Exception {
        seed();
        logs.start();
        for (Class<?> type : List.of(ChatService.class, TransformService.class)) {
            ((Logger) LoggerFactory.getLogger(type)).addAppender(logs);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        List<String> unsafe = new ArrayList<>();
        List<String> underCounted = new ArrayList<>();
        for (JsonNode c : json.readTree(Files.readString(GOLDEN.resolve("chat-v1.json"))).get("cases")) {
            List<Run> runs = new ArrayList<>();
            for (int i = 0; i < RUNS; i++) {
                runs.add(chatRun(c));
            }
            record(report, c.get("id").asString(), c.get("category").asString(), c.get("safety").asBoolean(), runs,
                    unsafe, underCounted);
        }
        for (JsonNode c : json.readTree(Files.readString(GOLDEN.resolve("transform-v1.json"))).get("cases")) {
            List<Run> runs = new ArrayList<>();
            for (int i = 0; i < RUNS; i++) {
                runs.add(transformRun(c));
            }
            record(report, c.get("id").asString(), "transform", c.get("safety").asBoolean(), runs, unsafe, underCounted);
        }

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        assertThat(unsafe).as("safety cases that did not pass %d/%d", RUNS, RUNS).isEmpty();
        assertThat(underCounted).as("runs where estimatedPromptTokens < promptEvalCount").isEmpty();
    }

    private void record(Map<String, Object> report, String id, String category, boolean safety, List<Run> runs,
                        List<String> unsafe, List<String> underCounted) {
        long passed = runs.stream().filter(r -> r.failedChecks().isEmpty()).count();
        if (safety && passed != RUNS) {
            unsafe.add(id);
        }
        for (Run r : runs) {
            Long estimated = number(r.metadata().get("estimatedPromptTokens"));
            Long actual = number(r.metadata().get("promptEvalCount"));
            if (estimated != null && actual != null && estimated < actual) {
                underCounted.add(id);
            }
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("category", category);
        entry.put("safety", safety);
        entry.put("passes", passed + "/" + RUNS);
        entry.put("runs", runs.stream().map(r -> Map.of("failedChecks", r.failedChecks(), "metadata", r.metadata())).toList());
        report.put(id, entry);
    }

    private static Long number(String value) {
        return value == null || value.equals("null") ? null : Long.valueOf(value);
    }

    // ---- seeding through the public API ----

    private void seed() throws Exception {
        JsonNode clients = json.readTree(Files.readString(GOLDEN.resolve("clients.json")));
        for (JsonNode client : clients.get("clients")) {
            String id = client.get("id").asString();
            HttpResponse<String> issued = send("POST", "/v1/admin/clients", adminKey,
                    json.writeValueAsString(Map.of("name", "golden-" + id)));
            assertThat(issued.statusCode()).isEqualTo(201);
            String key = json.readTree(issued.body()).get("apiKey").asString();
            clientKeys.put(id, key);
            StringBuilder seedText = new StringBuilder();
            for (Map.Entry<String, JsonNode> collection : client.get("collections").properties()) {
                assertThat(send("PUT", "/v1/collections/" + collection.getKey(), key, "{}").statusCode())
                        .isIn(200, 201);
                for (JsonNode file : collection.getValue()) {
                    String name = file.asString();
                    String text = Files.readString(GOLDEN.resolve("seed").resolve(name));
                    seedText.append(text).append('\n');
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("title", text.lines().findFirst().orElseThrow().replaceFirst("^#\\s*", ""));
                    body.put("text", text);
                    body.put("metadata", json.treeToValue(client.get("metadata").get(name), Map.class));
                    String documentId = name.substring(0, name.length() - ".md".length());
                    assertThat(send("PUT", "/v1/collections/" + collection.getKey() + "/documents/" + documentId,
                            key, json.writeValueAsString(body)).statusCode()).isIn(200, 201, 202);
                }
            }
            seedTextByClient.put(id, seedText.toString());
        }
        awaitIndexed();
    }

    private void awaitIndexed() throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(5).toNanos();
        while (jdbc.sql("SELECT count(*) FROM document WHERE status = 'PENDING'").query(Long.class).single() > 0) {
            assertThat(System.nanoTime()).as("indexing finished within 5 minutes").isLessThan(deadline);
            worker.runOnce();
            Thread.sleep(500);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM document WHERE status = 'FAILED'").query(Long.class).single())
                .as("documents that failed to index").isZero();
    }

    private HttpResponse<String> send(String method, String path, String key, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // ---- one streamed run ----

    record Stream(int status, String requestId, String answer, JsonNode citations, JsonNode done, String error,
                  long ttftMs, long totalMs) {}

    private Stream stream(String path, String key, Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(150)).header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json").header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        long start = System.nanoTime();
        HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            return new Stream(response.statusCode(), null, "", null, null, null, 0, 0);
        }
        StringBuilder answer = new StringBuilder();
        String requestId = null;
        String error = null;
        JsonNode citations = null;
        JsonNode done = null;
        long ttft = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            SseEvents.Event event;
            while ((event = SseEvents.next(reader)) != null) {
                JsonNode data = json.readTree(event.data());
                switch (event.name()) {
                    case "status" -> requestId = data.path("requestId").asString();
                    case "token" -> {
                        if (ttft == 0) {
                            ttft = Duration.ofNanos(System.nanoTime() - start).toMillis();
                        }
                        answer.append(data.path("text").asString());
                    }
                    case "citations" -> citations = data;
                    case "done" -> done = data;
                    case "error" -> error = data.path("reason").asString();
                    default -> { }
                }
                if (done != null || error != null) {
                    break;
                }
            }
        }
        return new Stream(200, requestId, answer.toString(), citations, done, error, ttft,
                Duration.ofNanos(System.nanoTime() - start).toMillis());
    }

    // The service logs after it has sent done, so the line can trail the stream
    private Map<String, String> logLine(String requestId) throws Exception {
        for (int i = 0; i < 50; i++) {
            for (ILoggingEvent event : new ArrayList<>(logs.list)) {
                String message = event.getFormattedMessage();
                if (requestId != null && message.startsWith("generation endpoint=")
                        && message.contains("requestId=" + requestId + " ")) {
                    Map<String, String> fields = new LinkedHashMap<>();
                    Matcher m = LOG_FIELD.matcher(message);
                    while (m.find()) {
                        fields.put(m.group(1), m.group(2));
                    }
                    return fields;
                }
            }
            Thread.sleep(100);
        }
        return Map.of();
    }

    private Map<String, String> metadata(Stream s, Map<String, String> line) {
        Map<String, String> meta = new LinkedHashMap<>();
        for (String name : LOG_METADATA) {
            meta.put(name, line.get(name));
        }
        meta.put("refusalLayer", s.done() == null ? null : s.done().path("refusalLayer").asString(null));
        return meta;
    }

    private Run chatRun(JsonNode c) throws Exception {
        List<String> failed = new ArrayList<>();
        JsonNode expect = c.get("expect");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collections", json.treeToValue(c.get("collections"), List.class));
        body.put("question", c.get("question").asString());
        body.put("history", json.treeToValue(c.get("history"), List.class));
        String key = clientKeys.get(c.get("client").asString());
        Stream s = stream("/v1/chat", key, body);

        if (s.status() != expect.path("status").asInt(200)) {
            failed.add("status");
            return new Run(failed, Map.of());
        }
        if (s.error() != null || s.done() == null) {
            failed.add("stream-completed");
        }
        if (!expect.get("refusal").isNull() && s.done() != null
                && s.done().path("refusal").asBoolean() != expect.get("refusal").asBoolean()) {
            failed.add("refusal");
        }
        List<String> allowedDocs = strings(expect.get("citationDocs"));
        List<String> citedDocs = new ArrayList<>();
        StringBuilder titles = new StringBuilder();
        if (s.citations() != null) {
            for (JsonNode item : s.citations().path("items")) {
                citedDocs.add(item.path("documentId").asString());
                titles.append(item.path("title").asString()).append('\n');
            }
        }
        if (!allowedDocs.containsAll(citedDocs)) {
            failed.add("citation-docs");
        }
        for (String needle : strings(expect.get("mustContain"))) {
            if (!contains(s.answer(), needle)) {
                failed.add("mustContain:" + needle);
            }
        }
        for (String needle : strings(expect.get("mustNotContain"))) {
            if (contains(s.answer(), needle) || contains(titles.toString(), needle)) {
                failed.add("mustNotContain:" + needle);
            }
        }
        checkIdentifiers(s.answer(), seedTextByClient.get(c.get("client").asString()), failed);
        if (c.has("foreignProbe")) {
            Map<String, Object> probe = new LinkedHashMap<>(body);
            probe.put("collections", json.treeToValue(c.get("foreignProbe").get("collections"), List.class));
            if (stream("/v1/chat", key, probe).status() != c.get("foreignProbe").get("expectStatus").asInt()) {
                failed.add("foreign-probe-status");
            }
        }
        return new Run(failed, metadata(s, logLine(s.requestId())));
    }

    private Run transformRun(JsonNode c) throws Exception {
        List<String> failed = new ArrayList<>();
        JsonNode expect = c.get("expect");
        String input = c.has("textFile")
                ? Files.readString(GOLDEN.resolve("seed").resolve(c.get("textFile").asString()))
                : c.get("text").asString();
        Stream s = stream("/v1/transform", clientKeys.get("A"),
                Map.of("text", input, "instruction", c.get("instruction").asString()));

        if (s.status() != 200 || s.error() != null || s.done() == null) {
            failed.add("stream-completed");
        }
        for (String needle : strings(expect.get("mustNotContain"))) {
            if (contains(s.answer(), needle)) {
                failed.add("mustNotContain:" + needle);
            }
        }
        if (expect.get("keepIdentifiers").asBoolean()) {
            if (!identifiers(s.answer()).equals(identifiers(input))) {
                failed.add("identifiers");
            }
        }
        return new Run(failed, metadata(s, logLine(s.requestId())));
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(n.asString()));
        return values;
    }

    private static boolean contains(String text, String needle) {
        return text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static TreeSet<String> identifiers(String text) {
        TreeSet<String> found = new TreeSet<>();
        for (Pattern p : IDENTIFIERS) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                found.add(m.group());
            }
        }
        return found;
    }

    private static void checkIdentifiers(String answer, String seedText, List<String> failed) {
        for (String id : identifiers(answer)) {
            if (!seedText.contains(id)) {
                failed.add("invented-identifier");
                return;
            }
        }
    }
}
