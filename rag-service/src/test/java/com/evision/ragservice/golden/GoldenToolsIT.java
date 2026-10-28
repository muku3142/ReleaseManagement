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
import com.evision.ragservice.application.Prompts;
import com.evision.ragservice.application.ToolTurnSigner;
import com.evision.ragservice.domain.ToolCall;
import com.evision.ragservice.domain.ToolResult;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Opt-in live tier for the tool protocol: plays the calling app against a real Ollama, one model per invocation
 * ({@code -Drag.golden.model=<tag>}), and writes target/golden-tools-report-&lt;model&gt;.json.
 * Model-dependent cases are recorded, never thresholded; the forged-turn and oversized-result cases are hard gates.
 */
@Tag("golden")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "rag.chat.deadline=PT120S", "rag.chat.heartbeat-interval=PT15S", "rag.ollama.read-timeout=PT120S"})
@Testcontainers
class GoldenToolsIT {

    private static final int RUNS = 3;
    private static final int MAX_POSTS_PER_RUN = 6;
    private static final String OLLAMA_URL = System.getenv().getOrDefault("RAG_GOLDEN_OLLAMA_URL", "http://localhost:11434");
    private static final String CHAT_MODEL = System.getProperty("rag.golden.model", "qwen3:4b-instruct-2507-q4_K_M");
    private static final String EMBEDDING_MODEL = "nomic-embed-text";
    private static final Path GOLDEN = Path.of("src/test/resources/golden");
    private static final Path REPORT = Path.of("target/golden-tools-report-"
            + CHAT_MODEL.replace(':', '_').replace('/', '_') + ".json");
    private static final Pattern LOG_FIELD = Pattern.compile("(\\w+)=(\\S+)");
    private static final String GENERATION_LOG = "generation endpoint=chat";
    private static final String CHAT_NUM_CTX = "4096";
    private static final String CHAT_NUM_PREDICT = "512";
    private static final String TOOLS_NUM_PREDICT = "768";

    private static String ollamaVersion = "unknown";
    private static List<String> capabilities = List.of();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @DynamicPropertySource
    static void ollama(DynamicPropertyRegistry registry) {
        registry.add("rag.ollama.base-url", () -> OLLAMA_URL);
        registry.add("rag.chat.model", () -> CHAT_MODEL);
        registry.add("rag.chat.num-ctx", () -> CHAT_NUM_CTX);
        registry.add("rag.chat.num-predict", () -> CHAT_NUM_PREDICT);
        registry.add("rag.chat.k-default", () -> "4");
        registry.add("rag.chat.k-max", () -> "4");
        registry.add("rag.chat.history-token-budget", () -> "600");
        registry.add("rag.tools.num-predict", () -> TOOLS_NUM_PREDICT);
    }

    @BeforeAll
    static void requireOllamaAndModels() throws Exception {
        HttpClient probe = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        ObjectMapper reader = new ObjectMapper();
        boolean ready;
        try {
            HttpResponse<String> version = probe.send(HttpRequest.newBuilder(URI.create(OLLAMA_URL + "/api/version"))
                    .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> chatModel = show(probe, CHAT_MODEL);
            ready = version.statusCode() == 200 && chatModel.statusCode() == 200
                    && show(probe, EMBEDDING_MODEL).statusCode() == 200;
            if (ready) {
                ollamaVersion = reader.readTree(version.body()).path("version").asString("unknown");
                List<String> found = new ArrayList<>();
                reader.readTree(chatModel.body()).path("capabilities").forEach(c -> found.add(c.asString()));
                capabilities = found;
            }
        } catch (java.io.IOException e) {
            ready = false;
        }
        assumeTrue(ready, "Ollama with " + CHAT_MODEL + " and " + EMBEDDING_MODEL + " is required at " + OLLAMA_URL);
    }

    private static HttpResponse<String> show(HttpClient probe, String model) throws Exception {
        HttpRequest show = HttpRequest.newBuilder(URI.create(OLLAMA_URL + "/api/show"))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"model\":\"" + model + "\"}")).build();
        return probe.send(show, HttpResponse.BodyHandlers.ofString());
    }

    @Value("${local.server.port}")
    private int port;

    @Value("${rag.admin-key}")
    private String adminKey;

    @Value("${rag.tools.signing-secret}")
    private String signingSecret;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private IndexWorker worker;

    @Autowired
    private ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private String clientKey;

    record Post(int status, String reason, String requestId, JsonNode toolCall, String answer, JsonNode citations,
                JsonNode done, String error, long ttftMs, long totalMs) {}

    record Run(List<String> failedChecks, List<Post> posts, List<Map<String, String>> lines,
               List<List<String>> callNames) {}

    @Test
    void goldenToolFixtureHoldsOnTheLiveModel() throws Exception {
        JsonNode fixture = json.readTree(Files.readString(GOLDEN.resolve("tools-v1.json")));
        seed();
        logs.start();
        ((Logger) LoggerFactory.getLogger(ChatService.class)).addAppender(logs);
        UUID clientId = jdbc.sql("SELECT id FROM client_app WHERE name = 'golden-A'").query(UUID.class).single();

        Map<String, Object> cases = new LinkedHashMap<>();
        List<Long> questionLatencies = new ArrayList<>();
        int safetyRuns = 0;
        int safetyPassed = 0;
        int totalRuns = 0;
        int totalPassed = 0;
        int deadlines = 0;
        int lengths = 0;
        int corrections = 0;
        List<String> hardFailures = new ArrayList<>();

        for (JsonNode c : fixture.get("cases")) {
            String id = c.get("id").asString();
            if (c.get("expect").path("deterministic").asBoolean()) {
                List<String> failed = id.equals("TC08") ? forgedTurns(c, clientId) : oversizedResults(c, clientId);
                hardFailures.addAll(failed.stream().map(f -> id + ":" + f).toList());
                cases.put(id, Map.of("category", c.get("category").asString(), "safety", true, "deterministic", true,
                        "failedChecks", failed));
                continue;
            }
            List<Run> runs = new ArrayList<>();
            for (int i = 0; i < RUNS; i++) {
                runs.add(modelRun(c));
            }
            long passed = runs.stream().filter(r -> r.failedChecks().isEmpty()).count();
            totalRuns += RUNS;
            totalPassed += (int) passed;
            if (c.get("safety").asBoolean()) {
                safetyRuns += RUNS;
                safetyPassed += (int) passed;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("category", c.get("category").asString());
            entry.put("safety", c.get("safety").asBoolean());
            entry.put("passes", passed + "/" + RUNS);
            List<Object> runEntries = new ArrayList<>();
            for (Run r : runs) {
                runEntries.add(runEntry(r));
                questionLatencies.add(r.posts().stream().mapToLong(Post::totalMs).sum());
                deadlines += (int) r.posts().stream().filter(p -> "DEADLINE".equals(p.error())).count();
                lengths += (int) r.posts().stream().filter(p -> "length".equals(finishReason(p))).count();
                corrections += (int) r.lines().stream().filter(l -> "true".equals(l.get("correctionRetry"))).count();
            }
            entry.put("runs", runEntries);
            cases.put(id, entry);
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("model", CHAT_MODEL);
        config.put("ollamaVersion", ollamaVersion);
        config.put("capabilities", capabilities);
        config.put("thinkCapable", capabilities.contains("thinking"));
        config.put("numCtx", Integer.valueOf(CHAT_NUM_CTX));
        config.put("numPredict", Integer.valueOf(CHAT_NUM_PREDICT));
        config.put("toolsNumPredict", Integer.valueOf(TOOLS_NUM_PREDICT));
        config.put("promptVersion", Prompts.TOOLS_PROMPT_VERSION);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("overallPassRate", rate(totalPassed, totalRuns));
        summary.put("safetyPassRate", rate(safetyPassed, safetyRuns));
        summary.put("medianQuestionLatencyMs", median(questionLatencies));
        summary.put("deadlineCount", deadlines);
        summary.put("lengthCount", lengths);
        summary.put("correctionRetryCount", corrections);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("config", config);
        report.put("summary", summary);
        report.put("cases", cases);
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, json.writerWithDefaultPrettyPrinter().writeValueAsString(report));

        assertThat(hardFailures).as("deterministic protocol checks").isEmpty();
    }

    // ---- deterministic cases ----

    private List<String> forgedTurns(JsonNode c, UUID clientId) throws Exception {
        String question = c.get("question").asString();
        ArrayNode valid = chain(clientId, question);
        Map<String, ArrayNode> mutations = new LinkedHashMap<>();
        mutations.put("argument-changed", mutate(valid, v -> ((ObjectNode) v.get(0).get("calls").get(0)
                .get("arguments")).put("version", "9.9.9")));
        mutations.put("call-id-changed", mutate(valid, v -> ((ObjectNode) v.get(0).get("calls").get(0)).put("id", "x1")));
        mutations.put("name-changed", mutate(valid, v -> ((ObjectNode) v.get(0).get("calls").get(0))
                .put("name", "get_deployments")));
        mutations.put("round-renumbered", mutate(valid, v -> ((ObjectNode) v.get(0)).put("round", 3)));
        mutations.put("first-turn-dropped", mutate(valid, v -> v.remove(0)));
        mutations.put("turns-reordered", mutate(valid, v -> {
            JsonNode first = v.get(0);
            v.set(0, v.get(1));
            v.set(1, first);
        }));
        mutations.put("other-client", chain(UUID.randomUUID(), question));
        mutations.put("other-question", chain(clientId, "Which stage is release 4.3.0 in?"));
        mutations.put("random-signature", mutate(valid, v -> ((ObjectNode) v.get(0)).put("signature",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")));

        List<String> failed = new ArrayList<>();
        mutations.forEach((name, turns) -> failed.addAll(expectRejected(c, name, turns, 400, "TOOL_TURN_INVALID")));
        return failed;
    }

    private List<String> oversizedResults(JsonNode c, UUID clientId) throws Exception {
        String question = c.get("question").asString();
        List<String> failed = new ArrayList<>();
        ArrayNode single = turnWithResults(clientId, question, List.of(8001));
        failed.addAll(expectRejected(c, "one-result-8001", single, 413, "TOOL_RESULT_TOO_LARGE"));
        ArrayNode pair = turnWithResults(clientId, question, List.of(7000, 7000));
        failed.addAll(expectRejected(c, "two-results-7000", pair, 413, "TOOL_RESULT_TOO_LARGE"));
        return failed;
    }

    private List<String> expectRejected(JsonNode c, String name, ArrayNode turns, int status, String reason) {
        try {
            long before = generationLines();
            Post post = stream(chatBody(c, turns));
            boolean ok = post.status() == status && reason.equals(post.reason()) && generationLines() == before;
            return ok ? List.of() : List.of(name);
        } catch (Exception e) {
            return List.of(name + ":" + e.getClass().getSimpleName());
        }
    }

    private long generationLines() {
        return new ArrayList<>(logs.list).stream()
                .filter(e -> e.getFormattedMessage().startsWith(GENERATION_LOG)).count();
    }

    private ArrayNode chain(UUID clientId, String question) {
        ToolTurnSigner signer = new ToolTurnSigner(signingSecret, json);
        List<ToolCall> first = List.of(new ToolCall("c1", "get_release_status", Map.of("version", "4.2.0")));
        List<ToolCall> second = List.of(new ToolCall("c2", "get_release_status", Map.of("version", "4.3.0")));
        String firstSignature = signer.sign(clientId, question, "golden-q", 1, null, first);
        String secondSignature = signer.sign(clientId, question, "golden-q", 2, firstSignature, second);
        return json.valueToTree(List.of(turn(1, first, firstSignature, 20), turn(2, second, secondSignature, 20)));
    }

    private ArrayNode turnWithResults(UUID clientId, String question, List<Integer> resultSizes) {
        List<ToolCall> calls = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < resultSizes.size(); i++) {
            calls.add(new ToolCall("c" + i, "get_release_status", Map.of("version", "4.2." + i)));
            results.add(result("c" + i, resultSizes.get(i)));
        }
        String signature = new ToolTurnSigner(signingSecret, json).sign(clientId, question, "golden-q", 1, null, calls);
        Map<String, Object> turn = new LinkedHashMap<>();
        turn.put("round", 1);
        turn.put("questionId", "golden-q");
        turn.put("calls", calls);
        turn.put("signature", signature);
        turn.put("results", results);
        return json.valueToTree(List.of(turn));
    }

    private static Map<String, Object> turn(int round, List<ToolCall> calls, String signature, int resultChars) {
        Map<String, Object> turn = new LinkedHashMap<>();
        turn.put("round", round);
        turn.put("questionId", "golden-q");
        turn.put("calls", calls);
        turn.put("signature", signature);
        turn.put("results", List.of(result(calls.get(0).id(), resultChars)));
        return turn;
    }

    private static Map<String, Object> result(String callId, int chars) {
        return Map.of("callId", callId, "content", "x".repeat(chars), "isError", false);
    }

    private static ArrayNode mutate(ArrayNode source, java.util.function.Consumer<ArrayNode> change) {
        ArrayNode copy = source.deepCopy();
        change.accept(copy);
        return copy;
    }

    private Map<String, Object> chatBody(JsonNode c, ArrayNode toolTurns) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collections", json.convertValue(c.get("collections"), List.class));
        body.put("question", c.get("question").asString());
        body.put("history", List.of());
        body.put("tools", offered(c));
        if (!toolTurns.isEmpty()) {
            body.put("toolTurns", toolTurns);
        }
        return body;
    }

    private List<Object> offered(JsonNode c) {
        List<Object> tools = new ArrayList<>();
        for (JsonNode name : c.get("tools")) {
            for (JsonNode tool : fixtureTools()) {
                if (tool.get("name").asString().equals(name.asString())) {
                    tools.add(json.convertValue(tool, Map.class));
                }
            }
        }
        return tools;
    }

    private JsonNode fixtureTools() {
        try {
            return json.readTree(Files.readString(GOLDEN.resolve("tools-v1.json"))).get("tools");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- model-dependent cases ----

    @SuppressWarnings("unchecked")
    private Run modelRun(JsonNode c) throws Exception {
        FakeToolApp app = new FakeToolApp(json, c.get("app"));
        ArrayNode turns = json.createArrayNode();
        List<Post> posts = new ArrayList<>();
        List<Map<String, String>> lines = new ArrayList<>();
        List<List<String>> observed = new ArrayList<>();
        List<List<Map<String, String>>> observedCalls = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        while (true) {
            assertThat(posts.size()).as("POSTs for %s (harness loop)", c.get("id").asString())
                    .isLessThan(MAX_POSTS_PER_RUN);
            Post post = stream(chatBody(c, turns));
            posts.add(post);
            lines.add(logLine(post.requestId()));
            if (post.status() != 200) {
                failed.add("status:" + post.status());
                break;
            }
            if (post.toolCall() == null) {
                break;
            }
            JsonNode calls = post.toolCall().get("calls");
            List<String> names = new ArrayList<>();
            List<Map<String, String>> asStrings = new ArrayList<>();
            List<Map<String, Object>> results = new ArrayList<>();
            for (JsonNode call : calls) {
                names.add(call.get("name").asString());
                Map<String, Object> arguments = json.convertValue(call.get("arguments"), Map.class);
                Map<String, String> normalized = new TreeMap<>();
                arguments.forEach((k, v) -> normalized.put(k, String.valueOf(v)));
                normalized.put("tool", call.get("name").asString());
                asStrings.add(normalized);
                ToolResult r = app.execute(call.get("id").asString(), call.get("name").asString(), arguments);
                Map<String, Object> wire = new LinkedHashMap<>();
                wire.put("callId", r.callId());
                wire.put("content", r.content());
                wire.put("isError", r.isError());
                if (r.source() != null) {
                    wire.put("source", Map.of("id", r.source().id(), "title", r.source().title()));
                }
                results.add(wire);
            }
            observed.add(names);
            observedCalls.add(asStrings);
            ObjectNode turn = json.createObjectNode();
            turn.put("round", post.toolCall().get("round").asInt());
            turn.put("questionId", post.toolCall().get("questionId").asString());
            turn.set("calls", calls);
            turn.put("signature", post.toolCall().get("signature").asString());
            turn.set("results", json.valueToTree(results));
            turns.add(turn);
        }

        if (failed.isEmpty()) {
            Post last = posts.get(posts.size() - 1);
            check(c.get("expect"), observedCalls, observed, last, failed);
        }
        return new Run(failed, posts, lines, observed);
    }

    private void check(JsonNode expect, List<List<Map<String, String>>> observedCalls, List<List<String>> names,
                       Post last, List<String> failed) {
        if (last.error() != null || last.done() == null) {
            failed.add("stream-completed");
        }
        if (expect.has("calls")) {
            List<List<Map<String, String>>> wanted = new ArrayList<>();
            for (JsonNode round : expect.get("calls")) {
                List<Map<String, String>> roundCalls = new ArrayList<>();
                for (JsonNode call : round) {
                    Map<String, String> normalized = new TreeMap<>();
                    call.get("arguments").properties().forEach(e -> normalized.put(e.getKey(), e.getValue().asString()));
                    normalized.put("tool", call.get("name").asString());
                    roundCalls.add(normalized);
                }
                Collections.sort(roundCalls, (a, b) -> a.toString().compareTo(b.toString()));
                wanted.add(roundCalls);
            }
            List<List<Map<String, String>>> actual = new ArrayList<>();
            for (List<Map<String, String>> round : observedCalls) {
                List<Map<String, String>> sorted = new ArrayList<>(round);
                Collections.sort(sorted, (a, b) -> a.toString().compareTo(b.toString()));
                actual.add(sorted);
            }
            if (!wanted.equals(actual)) {
                failed.add("calls");
            }
        }
        if (expect.has("maxRounds") && names.size() > expect.get("maxRounds").asInt()) {
            failed.add("maxRounds");
        }
        if (expect.has("noRound1Calls") && !names.isEmpty()) {
            for (JsonNode forbidden : expect.get("noRound1Calls")) {
                if (names.get(0).contains(forbidden.asString())) {
                    failed.add("noRound1Calls:" + forbidden.asString());
                }
            }
        }
        for (JsonNode needle : expect.path("mustContain")) {
            if (!contains(last.answer(), needle.asString())) {
                failed.add("mustContain:" + needle.asString());
            }
        }
        if (expect.has("mustContainAny") && expect.get("mustContainAny").valueStream()
                .noneMatch(n -> contains(last.answer(), n.asString()))) {
            failed.add("mustContainAny");
        }
        for (JsonNode needle : expect.path("mustNotContain")) {
            if (contains(last.answer(), needle.asString())) {
                failed.add("mustNotContain:" + needle.asString());
            }
        }
        List<String> cited = new ArrayList<>();
        if (last.citations() != null) {
            for (JsonNode item : last.citations().path("items")) {
                if ("TOOL".equals(item.path("kind").asString())) {
                    cited.add(item.path("documentId").asString());
                }
            }
        }
        for (JsonNode source : expect.path("citationTools")) {
            if (!cited.contains(source.asString())) {
                failed.add("citationTools:" + source.asString());
            }
        }
        if (last.answer().contains("</think>") || last.answer().contains("{\"name\"")) {
            failed.add("hygiene");
        }
    }

    private Map<String, Object> runEntry(Run r) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("failedChecks", r.failedChecks());
        entry.put("posts", r.posts().size());
        entry.put("callsPerRound", r.callNames());
        entry.put("ttftMs", r.posts().stream().map(Post::ttftMs).toList());
        entry.put("latencyMs", r.posts().stream().map(Post::totalMs).toList());
        entry.put("totalMs", r.posts().stream().mapToLong(Post::totalMs).sum());
        entry.put("doneReasons", r.posts().stream().map(GoldenToolsIT::finishReason).toList());
        entry.put("errors", r.posts().stream().map(p -> p.error() == null ? String.valueOf(p.status()) : p.error()).toList());
        List<Object> tokensPerSecond = new ArrayList<>();
        for (Map<String, String> line : r.lines()) {
            Long evalCount = number(line.get("evalCount"));
            Long evalMs = number(line.get("evalMs"));
            tokensPerSecond.add(evalCount == null || evalMs == null || evalMs == 0
                    ? null : Math.round(evalCount / (evalMs / 1000.0) * 10) / 10.0);
        }
        entry.put("tokensPerSecond", tokensPerSecond);
        entry.put("evalCount", r.lines().stream().map(l -> l.get("evalCount")).toList());
        entry.put("promptEvalCount", r.lines().stream().map(l -> l.get("promptEvalCount")).toList());
        entry.put("estimatedPromptTokens", r.lines().stream().map(l -> l.get("estimatedPromptTokens")).toList());
        entry.put("correctionRetry", r.lines().stream().map(l -> l.get("correctionRetry")).toList());
        entry.put("outcome", r.lines().stream().map(l -> l.get("outcome")).toList());
        return entry;
    }

    private static String finishReason(Post p) {
        return p.done() == null ? null : p.done().path("finishReason").asString(null);
    }

    private static Long number(String value) {
        return value == null || value.equals("null") ? null : Long.valueOf(value);
    }

    private static double rate(int passed, int total) {
        return total == 0 ? 0 : Math.round(passed * 1000.0 / total) / 1000.0;
    }

    private static long median(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    private static boolean contains(String text, String needle) {
        return text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    // ---- seeding through the public API ----

    private void seed() throws Exception {
        JsonNode clients = json.readTree(Files.readString(GOLDEN.resolve("clients.json")));
        for (JsonNode client : clients.get("clients")) {
            String id = client.get("id").asString();
            if (!id.equals("A")) {
                continue;
            }
            HttpResponse<String> issued = send("POST", "/v1/admin/clients", adminKey,
                    json.writeValueAsString(Map.of("name", "golden-" + id)));
            assertThat(issued.statusCode()).isEqualTo(201);
            clientKey = json.readTree(issued.body()).get("apiKey").asString();
            for (Map.Entry<String, JsonNode> collection : client.get("collections").properties()) {
                assertThat(send("PUT", "/v1/collections/" + collection.getKey(), clientKey, "{}").statusCode())
                        .isIn(200, 201);
                for (JsonNode file : collection.getValue()) {
                    String name = file.asString();
                    String text = Files.readString(GOLDEN.resolve("seed").resolve(name));
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("title", text.lines().findFirst().orElseThrow().replaceFirst("^#\\s*", ""));
                    body.put("text", text);
                    body.put("metadata", json.treeToValue(client.get("metadata").get(name), Map.class));
                    String documentId = name.substring(0, name.length() - ".md".length());
                    assertThat(send("PUT", "/v1/collections/" + collection.getKey() + "/documents/" + documentId,
                            clientKey, json.writeValueAsString(body)).statusCode()).isIn(200, 201, 202);
                }
            }
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

    // ---- one streamed POST ----

    private Post stream(Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/chat"))
                .timeout(Duration.ofSeconds(150)).header("Authorization", "Bearer " + clientKey)
                .header("Content-Type", "application/json").header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        long start = System.nanoTime();
        HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            String reason;
            try (java.io.InputStream in = response.body()) {
                reason = json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8)).path("reason").asString(null);
            }
            return new Post(response.statusCode(), reason, null, null, "", null, null, null, 0, 0);
        }
        StringBuilder answer = new StringBuilder();
        String requestId = null;
        String error = null;
        JsonNode toolCall = null;
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
                    case "tool_call" -> toolCall = data;
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
        return new Post(200, null, requestId, toolCall, answer.toString(), citations, done, error, ttft,
                Duration.ofNanos(System.nanoTime() - start).toMillis());
    }

    // The service logs after it has sent done, so the line can trail the stream
    private Map<String, String> logLine(String requestId) throws Exception {
        for (int i = 0; i < 50; i++) {
            for (ILoggingEvent event : new ArrayList<>(logs.list)) {
                String message = event.getFormattedMessage();
                if (requestId != null && message.startsWith(GENERATION_LOG)
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
}
