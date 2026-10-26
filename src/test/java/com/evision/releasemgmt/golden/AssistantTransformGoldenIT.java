package com.evision.releasemgmt.golden;

import com.evision.releasemgmt.adapters.rag.RagTransformAdapter;
import com.evision.releasemgmt.application.assistant.TransformSizeEstimator;
import com.evision.releasemgmt.config.AssistantProperties;
import com.evision.releasemgmt.config.RagRestClientConfig;
import com.evision.releasemgmt.domain.assistant.FailureReason;
import com.evision.releasemgmt.domain.assistant.TransformMode;
import com.evision.releasemgmt.domain.assistant.TransformOutcome;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.golden.TransformGoldenCases.Case;
import com.evision.releasemgmt.golden.TransformGoldenCases.Mode;
import com.evision.releasemgmt.golden.TransformGoldenEvaluator.CaseResult;
import com.evision.releasemgmt.ports.RagChatPort.ChatListener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Opt-in live tier for document assist (AI-SPEC section 5). Runs the fixture through the real RagTransformAdapter
 * against a reachable rag-service, using the shipped instruction texts. Never runs in a plain build: the tag is
 * excluded in pom.xml, and without the two environment variables the class is skipped.
 */
@Tag("assistant-golden")
class AssistantTransformGoldenIT {

    private static final String BASE_URL_ENV = "RAG_GOLDEN_BASE_URL";
    private static final String API_KEY_ENV = "RAG_GOLDEN_API_KEY";
    private static final Path REPORT = Path.of("target", "assistant-golden-report.json");
    private static final Path OUTPUTS = Path.of("target", "assistant-golden-outputs");
    private static final String NEAR_LIMIT_CASE = "N01";
    private static final double DEADLINE_SECONDS = 120.0;
    private static final double RAISED_DEADLINE_CEILING_SECONDS = 300.0;
    private static final double CHARS_PER_TOKEN = 3.0;
    private static final double SAFETY_MARGIN = 0.9;
    private static final String TOO_LARGE_PREFLIGHT = "TOO_LARGE_PREFLIGHT";

    private static String baseUrl;
    private static String apiKey;

    @BeforeAll
    static void requireLiveEnvironment() {
        baseUrl = System.getenv(BASE_URL_ENV);
        apiKey = System.getenv(API_KEY_ENV);
        assumeThat(baseUrl).as(BASE_URL_ENV).isNotBlank();
        assumeThat(apiKey).as(API_KEY_ENV).isNotBlank();
    }

    private record RunRecord(Case testCase, int run, String outcome, Map<String, Boolean> checks, boolean diffBlind,
                             int documentChars, int resultChars, Integer promptTokens, Integer completionTokens,
                             String finishReason, boolean truncated, Double ttftSeconds, Double tokensPerSecond,
                             double totalSeconds) {
        boolean completed() {
            return "OK".equals(outcome) || "CHECKS_FAILED".equals(outcome);
        }

        boolean allPassed() {
            return checks.values().stream().allMatch(Boolean::booleanValue);
        }
    }

    private static final class TimingListener implements ChatListener {
        private final long start = System.nanoTime();
        private final StringBuilder text = new StringBuilder();
        private long firstTokenNanos = -1;

        @Override
        public void token(String token) {
            if (firstTokenNanos < 0) {
                firstTokenNanos = System.nanoTime();
            }
            text.append(token);
        }

        @Override
        public void roundDiscarded() {
            text.setLength(0);
        }

        Double ttftSeconds() {
            return firstTokenNanos < 0 ? null : (firstTokenNanos - start) / 1e9;
        }

        double totalSeconds() {
            return (System.nanoTime() - start) / 1e9;
        }
    }

    @Test
    void runsTheFixtureThroughTheRealAdapter() throws Exception {
        AssistantProperties.DocumentAssist assist = bindDocumentAssist();
        TransformSizeEstimator estimator = new TransformSizeEstimator(assist);
        RagTransformAdapter adapter = new RagTransformAdapter(
                RagRestClientConfig.ragClient(baseUrl, apiKey, Duration.ofSeconds(2), Duration.ofSeconds(130)),
                JsonMapper.builder().build());

        int runs = Integer.parseInt(System.getProperty("golden.runs", "3"));
        boolean keepOutputs = Boolean.getBoolean("golden.keepOutputs");
        Set<String> selected = System.getProperty("golden.cases") == null ? Set.of()
                : Arrays.stream(System.getProperty("golden.cases").split(",")).map(String::trim).collect(Collectors.toSet());

        List<Case> cases = TransformGoldenCases.load().cases().stream()
                .filter(c -> selected.isEmpty() || selected.contains(c.id())).toList();
        List<RunRecord> records = new ArrayList<>();
        for (Case testCase : cases) {
            String document = TransformGoldenCases.documentText(testCase);
            String instruction = instruction(assist, testCase);
            for (int run = 1; run <= runs; run++) {
                RunRecord record = execute(adapter, estimator, testCase, document, instruction, run, keepOutputs);
                records.add(record);
                System.out.printf("case=%s run=%d/%d outcome=%s%n", testCase.id(), run, runs, record.outcome());
            }
        }
        writeReport(records, assist, estimator, runs);
    }

    private static RunRecord execute(RagTransformAdapter adapter, TransformSizeEstimator estimator, Case testCase,
                                     String document, String instruction, int run, boolean keepOutputs) throws IOException {
        if (!estimator.fits(document.length(), instruction.length())) {
            return new RunRecord(testCase, run, TOO_LARGE_PREFLIGHT, Map.of(), false, document.length(), 0,
                    null, null, null, false, null, null, 0);
        }
        TimingListener listener = new TimingListener();
        TransformOutcome outcome = adapter.transform(document, instruction, listener);
        double total = listener.totalSeconds();
        String result = listener.text.toString();
        return switch (outcome) {
            case TransformOutcome.Done done -> {
                CaseResult scored = TransformGoldenEvaluator.evaluate(
                        testCase, document, result, done.finishReason(), done.truncated());
                if (keepOutputs) {
                    keep(testCase, run, result);
                }
                yield new RunRecord(testCase, run, scored.allPassed() ? "OK" : "CHECKS_FAILED", scored.checks(),
                        scored.diffBlind(), document.length(), result.length(), done.promptTokens(),
                        done.completionTokens(), done.finishReason(), done.truncated(), listener.ttftSeconds(),
                        tokensPerSecond(done.completionTokens(), listener.ttftSeconds(), total), total);
            }
            case TransformOutcome.TooLarge tooLarge -> failed(testCase, run, "TOO_LARGE", document, total);
            case TransformOutcome.Failed failure -> failed(testCase, run, outcomeName(failure.reason()), document, total);
        };
    }

    private static RunRecord failed(Case testCase, int run, String outcome, String document, double total) {
        return new RunRecord(testCase, run, outcome, Map.of(), false, document.length(), 0, null, null, null, false,
                null, null, total);
    }

    private static String outcomeName(FailureReason reason) {
        return reason.name();
    }

    private static Double tokensPerSecond(Integer completionTokens, Double ttft, double total) {
        if (completionTokens == null || ttft == null || total - ttft <= 0) {
            return null;
        }
        return completionTokens / (total - ttft);
    }

    private static String instruction(AssistantProperties.DocumentAssist assist, Case testCase) {
        if (testCase.mode() == Mode.CUSTOM) {
            return testCase.instruction();
        }
        return assist.instruction(TransformMode.valueOf(testCase.mode().name()),
                TemplateDocumentType.valueOf(testCase.documentType().name()));
    }

    private static void keep(Case testCase, int run, String result) throws IOException {
        Files.createDirectories(OUTPUTS);
        Files.writeString(OUTPUTS.resolve(testCase.id() + "-run" + run + ".md"), result, StandardCharsets.UTF_8);
    }

    private static void writeReport(List<RunRecord> records, AssistantProperties.DocumentAssist assist,
                                    TransformSizeEstimator estimator, int runs) throws IOException {
        List<RunRecord> completed = records.stream().filter(RunRecord::completed).toList();
        List<RunRecord> cleanUp = completed.stream().filter(r -> r.testCase().mode() == Mode.CLEAN_UP).toList();
        long incomplete = records.stream()
                .filter(r -> !r.completed() && !TOO_LARGE_PREFLIGHT.equals(r.outcome()) && !"TOO_LARGE".equals(r.outcome()))
                .count();
        long diffBlind = cleanUp.stream().filter(RunRecord::diffBlind).count();
        long cleanUpPassed = cleanUp.stream().filter(RunRecord::allPassed).count();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("fixtureVersion", TransformGoldenCases.load().version());
        report.put("runsPerCase", runs);
        report.put("modelTag", null);
        report.put("instructionHash", instructionHash(assist));
        report.put("totalRuns", records.size());
        report.put("completedRuns", completed.size());
        report.put("incompleteRuns", incomplete);
        report.put("incompleteRate", rate(incomplete, records.size()));
        report.put("cleanUpCompletedRuns", cleanUp.size());
        report.put("cleanUpPassRate", rate(cleanUpPassed, cleanUp.size()));
        report.put("diffBlindFailureRate", rate(diffBlind, cleanUp.size()));
        report.put("latencyDecision", latencyDecision(records, estimator, assist));
        report.put("runs", records.stream().map(AssistantTransformGoldenIT::runEntry).toList());

        Files.createDirectories(REPORT.getParent());
        ObjectMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        Files.writeString(REPORT, mapper.writeValueAsString(report), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> runEntry(RunRecord r) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("case", r.testCase().id());
        entry.put("run", r.run());
        entry.put("mode", r.testCase().mode().name());
        entry.put("type", r.testCase().documentType().name());
        entry.put("outcome", r.outcome());
        entry.put("checks", r.checks());
        entry.put("diffBlind", r.diffBlind());
        entry.put("documentChars", r.documentChars());
        entry.put("resultChars", r.resultChars());
        entry.put("promptTokens", r.promptTokens());
        entry.put("completionTokens", r.completionTokens());
        entry.put("finishReason", r.finishReason());
        entry.put("truncated", r.truncated());
        entry.put("ttftSeconds", r.ttftSeconds());
        entry.put("tokensPerSecond", r.tokensPerSecond());
        entry.put("totalSeconds", r.totalSeconds());
        return entry;
    }

    // Inputs per AI-SPEC "Latency decision rule"; N01 is the near-limit point and the three sizes are B01/B04/N01.
    private static Map<String, Object> latencyDecision(List<RunRecord> records, TransformSizeEstimator estimator,
                                                       AssistantProperties.DocumentAssist assist) {
        Map<String, Object> decision = new LinkedHashMap<>();
        List<RunRecord> timed = records.stream()
                .filter(r -> r.tokensPerSecond() != null && r.ttftSeconds() != null).toList();
        Set<String> sizes = timed.stream().map(r -> r.testCase().id()).collect(Collectors.toSet());
        boolean hasNearLimit = sizes.contains(NEAR_LIMIT_CASE);
        decision.put("nearLimitCaseCompleted", hasNearLimit);
        decision.put("sizesCompleted", sizes.size());
        if (timed.isEmpty() || !hasNearLimit) {
            decision.put("branch", "NOT_EVALUATED");
            return decision;
        }
        double r10 = percentile(timed.stream().map(RunRecord::tokensPerSecond).toList(), 10);
        double ttft95 = percentile(timed.stream().map(RunRecord::ttftSeconds).toList(), 95);
        int sizeLimit = estimator.maxChars(
                assist.instruction(TransformMode.CLEAN_UP, TemplateDocumentType.RUNBOOK).length());
        long latencyLimitChars = (long) Math.floor(r10 * (DEADLINE_SECONDS - ttft95) * CHARS_PER_TOKEN * SAFETY_MARGIN);
        double neededDeadline = ttft95 + (sizeLimit / CHARS_PER_TOKEN) / (r10 * SAFETY_MARGIN);
        decision.put("r10", r10);
        decision.put("ttft95", ttft95);
        decision.put("sizeLimit", sizeLimit);
        decision.put("deadline", DEADLINE_SECONDS);
        decision.put("latencyLimitChars", latencyLimitChars);
        decision.put("neededDeadline", neededDeadline);
        decision.put("branch", latencyLimitChars >= sizeLimit ? "1_CHANGE_NOTHING"
                : neededDeadline <= RAISED_DEADLINE_CEILING_SECONDS ? "2_RAISE_DEADLINE" : "3_CONFIG_CAP");
        return decision;
    }

    private static double percentile(List<Double> values, int percent) {
        List<Double> sorted = values.stream().sorted().toList();
        int rank = (int) Math.ceil(percent / 100.0 * sorted.size());
        return sorted.get(Math.max(0, rank - 1));
    }

    private static double rate(long part, long whole) {
        return whole == 0 ? 0.0 : (double) part / whole;
    }

    private static String instructionHash(AssistantProperties.DocumentAssist assist) {
        StringBuilder all = new StringBuilder();
        for (TransformMode mode : List.of(TransformMode.CLEAN_UP, TransformMode.SUMMARIZE)) {
            for (TemplateDocumentType type : TemplateDocumentType.values()) {
                all.append(assist.instruction(mode, type)).append('\n');
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(all.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AssistantProperties.DocumentAssist bindDocumentAssist() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("yml", new FileSystemResource("src/main/resources/application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        environment.getPropertySources().addLast(new MapPropertySource("empty", Map.of()));
        return Binder.get(environment).bind("app.assistant", AssistantProperties.class).get().documentAssist();
    }
}
