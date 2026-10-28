package com.evision.releasemgmt.quality;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.adapters.security.AuthCookieWriter;
import com.evision.releasemgmt.adapters.security.JwtTokenService;
import com.evision.releasemgmt.domain.assistant.IndexCollection;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.PromotionRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import com.evision.releasemgmt.ports.TemplateRepository;
import com.evision.releasemgmt.quality.GateCases.Case;
import com.evision.releasemgmt.quality.GateCases.CaseSet;
import com.evision.releasemgmt.quality.GateEvaluator.CaseResult;
import com.evision.releasemgmt.quality.GateEvaluator.RunResult;
import com.evision.releasemgmt.quality.GateEvaluator.StreamResult;
import com.evision.releasemgmt.quality.GateEvaluator.Verdict;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SAFE-04 answer-quality gate (D-13-16). Opt-in: run with {@code -Pquality-gate}. Drives the shipped
 * POST /api/assistant/chat over a seeded scratch database against a reachable rag-service, under a scratch rag client
 * of its own (the app's configured client key is never read). See docs/quality-gate.md.
 */
@Tag("quality-gate")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(QualityGateIT.GateClockConfig.class)
class QualityGateIT {

    private static final String CASES_RESOURCE = "quality-gate/cases-v1.json";
    private static final Path REPORT = Path.of("target", "quality-gate-report.json");
    private static final Duration RUN_TIMEOUT = Duration.ofMinutes(6);
    private static final Duration TEARDOWN_WAIT = Duration.ofSeconds(60);
    private static final Duration POLL = Duration.ofSeconds(2);
    private static final int ANSWER_EXCERPT_CHARS = 500;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private static String ragUrl;
    private static String adminKey;
    private static String hostLabel;
    private static boolean devWaiver;
    private static int runsPerCase;
    private static Duration indexTimeout;
    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;
    private static PostgreSQLContainer<?> container;
    private static String clientId;
    private static String clientKey;
    private static boolean clientRevoked;

    @TestConfiguration
    static class GateClockConfig {

        @Bean
        @Primary
        Clock gateClock() {
            return Clock.fixed(GateSeed.NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private ReleaseRepository releases;
    @Autowired
    private PromotionRepository promotions;
    @Autowired
    private DocumentSnapshotRepository snapshots;
    @Autowired
    private TemplateRepository templates;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtTokenService tokens;
    @LocalServerPort
    private int port;

    @BeforeAll
    static void configure() throws Exception {
        ragUrl = System.getProperty("gate.rag-url");
        adminKey = System.getenv("GATE_RAG_ADMIN_KEY");
        Assumptions.assumeFalse(ragUrl == null || ragUrl.isBlank() || adminKey == null || adminKey.isBlank(),
                "quality gate not configured: set -Dgate.rag-url and GATE_RAG_ADMIN_KEY");
        hostLabel = System.getProperty("gate.host-label");
        if (hostLabel == null || hostLabel.isBlank()) {
            throw new IllegalStateException("set -Dgate.host-label to the host the rag-service runs on");
        }
        devWaiver = Boolean.getBoolean("gate.dev-waiver");
        runsPerCase = Integer.getInteger("gate.runs", 3);
        indexTimeout = Duration.parse(System.getProperty("gate.index-timeout", "PT10M"));
        dbUrl = System.getProperty("gate.db-url");
        dbUser = System.getProperty("gate.db-user");
        dbPassword = System.getProperty("gate.db-password");
        if (dbUrl == null || dbUrl.isBlank()) {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                    "no database: Docker unavailable and -Dgate.db-url not set");
            container = new PostgreSQLContainer<>(PgVectorImage.IMAGE);
            container.start();
            dbUrl = container.getJdbcUrl();
            dbUser = container.getUsername();
            dbPassword = container.getPassword();
        }
        registerClient();
    }

    @AfterAll
    static void release() {
        revokeClient();
        if (container != null) {
            container.stop();
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("spring.datasource.username", () -> dbUser);
        registry.add("spring.datasource.password", () -> dbPassword);
        registry.add("app.assistant.enabled", () -> "true");
        registry.add("app.assistant.base-url", () -> ragUrl);
        registry.add("app.assistant.api-key", () -> clientKey);
        registry.add("app.assistant.sweep.initial-delay", () -> "PT1S");
        registry.add("app.assistant.sweep.interval", () -> "PT15S");
    }

    @Test
    void runGate() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from release", Long.class))
                .as("gate database must be empty").isZero();
        CaseSet set = GateCases.load(CASES_RESOURCE);
        List<CaseResult> results = new ArrayList<>();
        Set<String> models = new LinkedHashSet<>();
        Set<String> promptHashes = new LinkedHashSet<>();
        List<List<Map<String, Object>>> runDetails = new ArrayList<>();
        String teardown;
        try {
            GateSeed.Seeded seeded = GateSeed.seed(releases, promotions, snapshots, templates);
            awaitIndexed();
            String cookie = AuthCookieWriter.ACCESS_COOKIE + "="
                    + tokens.issue("557058:quality-gate", "gate@example.com").accessToken();
            for (Case c : set.cases()) {
                UUID scope = c.scope() == null ? null : seeded.releaseId(c.scope());
                List<RunResult> runs = new ArrayList<>();
                List<Map<String, Object>> details = new ArrayList<>();
                for (int i = 0; i < runsPerCase; i++) {
                    StreamResult stream = ask(cookie, c, scope);
                    RunResult run = GateEvaluator.evaluate(c, stream, scope);
                    runs.add(run);
                    details.add(runDetail(run, stream));
                    collectMeta(stream, models, promptHashes);
                }
                results.add(new CaseResult(c, runs));
                runDetails.add(details);
                System.out.println("gate " + c.id() + " " + runs.stream().filter(RunResult::pass).count() + "/"
                        + runs.size());
            }
        } finally {
            teardown = teardown();
        }
        Verdict verdict = GateEvaluator.verdict(results, devWaiver);
        writeReport(set, results, runDetails, verdict, models, promptHashes, teardown);

        assertThat(verdict.verdict()).isNotEqualTo(GateEvaluator.FAIL);
        assertThat(verdict.barMet()).as("safety 3/3 and quality >= 80%% (see %s)", REPORT).isTrue();
    }

    private StreamResult ask(String cookie, Case c, UUID scope) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", c.question());
        body.put("history", c.history());
        body.put("releaseId", scope == null ? null : scope.toString());
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/assistant/chat"))
                    .timeout(RUN_TIMEOUT).header("Content-Type", "application/json").header("Cookie", cookie)
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
            HttpResponse<java.io.InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                return failure("HTTP_" + response.statusCode(), start);
            }
            return readStream(response, start);
        } catch (IOException e) {
            return failure("CLIENT_" + e.getClass().getSimpleName(), start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure("CLIENT_INTERRUPTED", start);
        }
    }

    @SuppressWarnings("unchecked")
    private static StreamResult readStream(HttpResponse<java.io.InputStream> response, long start) throws IOException {
        StringBuilder text = new StringBuilder();
        List<String> statusTools = new ArrayList<>();
        Map<String, Object> done = null;
        Map<String, Object> error = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String name = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event:")) {
                    name = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:") && name != null) {
                    String data = line.substring("data:".length()).trim();
                    switch (name) {
                        case "token" -> text.append(JSON.readTree(data).path("text").asString());
                        case "discard" -> text.setLength(0);
                        case "status" -> statusTools.add(JSON.readTree(data).path("tool").asString());
                        case "done" -> done = JSON.readValue(data, Map.class);
                        case "error" -> error = JSON.readValue(data, Map.class);
                        default -> { }
                    }
                    name = null;
                }
            }
        }
        return new StreamResult(text.toString(), statusTools, done, error, elapsedMillis(start));
    }

    private static StreamResult failure(String outcome, long start) {
        return new StreamResult("", List.of(), null, Map.of("outcome", outcome), elapsedMillis(start));
    }

    private static long elapsedMillis(long start) {
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }

    private static Map<String, Object> runDetail(RunResult run, StreamResult stream) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("pass", run.pass());
        detail.put("failedChecks", run.failedChecks());
        detail.put("outcome", stream.error() != null ? stream.error().get("outcome")
                : stream.done() != null ? "DONE" : "NO_DONE");
        detail.put("toolsRun", toolNames(stream));
        detail.put("sourceCount", stream.done() == null ? 0 : listSize(stream.done().get("sources")));
        detail.put("millis", stream.millis());
        detail.put("answer", stream.text().substring(0, Math.min(stream.text().length(), ANSWER_EXCERPT_CHARS)));
        return detail;
    }

    private static List<String> toolNames(StreamResult stream) {
        if (stream.done() == null || !(stream.done().get("tools") instanceof List<?> tools)) {
            return List.of();
        }
        return tools.stream().filter(Map.class::isInstance).map(t -> String.valueOf(((Map<?, ?>) t).get("name"))).toList();
    }

    private static int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private static void collectMeta(StreamResult stream, Set<String> models, Set<String> promptHashes) {
        if (stream.done() != null && stream.done().get("meta") instanceof Map<?, ?> meta) {
            if (meta.get("model") != null) {
                models.add(meta.get("model").toString());
            }
            if (meta.get("promptHash") != null) {
                promptHashes.add(meta.get("promptHash").toString());
            }
        }
    }

    private void writeReport(CaseSet set, List<CaseResult> results, List<List<Map<String, Object>>> runDetails,
                             Verdict verdict, Set<String> models, Set<String> promptHashes, String teardown)
            throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("verdict", verdict.verdict());
        report.put("barMet", verdict.barMet());
        report.put("models", models);
        report.put("promptHashes", promptHashes);
        report.put("hostLabel", hostLabel);
        report.put("ragHost", URI.create(ragUrl).getHost());
        report.put("jvmHost", InetAddress.getLocalHost().getHostName());
        report.put("date", Instant.now().toString());
        report.put("casesFile", CASES_RESOURCE);
        report.put("casesVersion", set.version());
        report.put("labelsApprovedBy", set.labelsApprovedBy());
        report.put("runsPerCase", runsPerCase);
        report.put("devWaiver", devWaiver);
        report.put("safety", verdict.safety());
        report.put("quality", verdict.quality());
        report.put("perCategory", verdict.perCategory());
        report.put("toolDependentCases", verdict.toolDependentCases());
        report.put("teardown", teardown);
        List<Map<String, Object>> cases = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            Case c = results.get(i).testCase();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", c.id());
            entry.put("category", c.category());
            entry.put("safety", c.safety());
            entry.put("requiresTools", c.requiresTools());
            entry.put("excludedByWaiver", devWaiver && c.requiresTools());
            entry.put("runs", runDetails.get(i));
            cases.add(entry);
        }
        report.put("cases", cases);
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }

    // ---- scratch rag client and index state ----

    private static void registerClient() throws Exception {
        HttpResponse<String> issued = rag("POST", "/v1/admin/clients", adminKey,
                JSON.writeValueAsString(Map.of("name", "rm-quality-gate-" + Instant.now().getEpochSecond())));
        if (issued.statusCode() != 201) {
            throw new IllegalStateException("rag-service refused the scratch client: HTTP " + issued.statusCode());
        }
        JsonNode client = JSON.readTree(issued.body());
        clientId = client.get("id").asString();
        clientKey = client.get("apiKey").asString();
    }

    private static boolean revokeClient() {
        if (clientId == null || clientRevoked) {
            return clientRevoked;
        }
        try {
            clientRevoked = rag("POST", "/v1/admin/clients/" + clientId + "/revoke", adminKey, "").statusCode() / 100 == 2;
        } catch (IOException e) {
            clientRevoked = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            clientRevoked = false;
        }
        return clientRevoked;
    }

    private static HttpResponse<String> rag(String method, String path, String key, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(ragUrl + path)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + key).header("Content-Type", "application/json");
        return HTTP.send(request.method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Index counts of one rm-* collection; null while the collection does not exist yet. */
    private static JsonNode collectionStatus(IndexCollection collection) throws IOException, InterruptedException {
        HttpResponse<String> response = rag("GET", "/v1/collections/" + collection.wireName(), clientKey, "");
        return response.statusCode() == 200 ? JSON.readTree(response.body()) : null;
    }

    private void awaitIndexed() throws Exception {
        long deadline = System.nanoTime() + indexTimeout.toNanos();
        while (true) {
            boolean settled = true;
            for (IndexCollection collection : IndexCollection.values()) {
                JsonNode status = collectionStatus(collection);
                if (status != null && status.path("failed").asLong() > 0) {
                    throw new IllegalStateException(collection.wireName() + " has failed documents");
                }
                settled &= status != null && status.path("pending").asLong() == 0 && status.path("indexed").asLong() > 0;
            }
            if (settled) {
                return;
            }
            assertThat(System.nanoTime()).as("index settled within " + indexTimeout).isLessThan(deadline);
            Thread.sleep(POLL);
        }
    }

    /** Deletes the scratch data and revokes the client; never changes the verdict. */
    private String teardown() {
        boolean complete = true;
        try {
            List<ReleaseSummary> seeded = releases.search("");
            seeded.forEach(release -> releases.delete(release.id()));
            complete = awaitDrained();
        } catch (RuntimeException | IOException e) {
            complete = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            complete = false;
        }
        return revokeClient() && complete ? "COMPLETE" : "INCOMPLETE";
    }

    private boolean awaitDrained() throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TEARDOWN_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            boolean drained = true;
            for (IndexCollection collection : IndexCollection.values()) {
                JsonNode status = collectionStatus(collection);
                drained &= status == null || status.path("indexed").asLong() == 0;
            }
            if (drained) {
                return true;
            }
            Thread.sleep(POLL);
        }
        return false;
    }
}
