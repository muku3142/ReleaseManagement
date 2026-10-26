package com.evision.ragservice.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.evision.ragservice.domain.ToolDefinition;
import com.evision.ragservice.domain.ToolResult;
import com.evision.ragservice.domain.ToolSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Keeps the golden tool fixture self-consistent without a model; GoldenToolsIT runs it live. */
class ToolsFixtureTest {

    private static final Path FIXTURE = Path.of("src/test/resources/golden/tools-v1.json");
    private static final Set<String> LADDER = Set.of("DEV", "SIT", "UAT", "Staging", "Production");
    private static final List<String> CASE_FIELDS =
            List.of("id", "category", "safety", "client", "collections", "question", "tools", "expect", "rubric");

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static String raw;
    private static JsonNode fixture;
    private static Map<String, JsonNode> toolsByName;

    @BeforeAll
    static void load() throws Exception {
        raw = Files.readString(FIXTURE);
        fixture = JSON.readTree(raw);
        toolsByName = new LinkedHashMap<>();
        fixture.get("tools").forEach(t -> toolsByName.put(t.get("name").asString(), t));
    }

    @Test
    void casesAreUniqueAndComplete() {
        Set<String> ids = new HashSet<>();
        for (JsonNode c : fixture.get("cases")) {
            assertThat(ids.add(c.get("id").asString())).as("unique id %s", c.get("id")).isTrue();
            for (String field : CASE_FIELDS) {
                assertThat(c.has(field)).as("%s has %s", c.get("id"), field).isTrue();
            }
        }
        assertThat(ids).containsExactlyInAnyOrder("TC01", "TC02", "TC03", "TC04", "TC04b", "TC05", "TC06", "TC07",
                "TC08", "TC09", "TC10", "TC11");
    }

    @Test
    void toolDefinitionsPassTheServiceSchemaRules() throws Exception {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (JsonNode tool : fixture.get("tools")) {
            definitions.add(JSON.treeToValue(tool, ToolDefinition.class));
        }
        ToolSchema.requireValidDefinitions(definitions, 5, 1500);
        assertThat(definitions).hasSize(4);
    }

    @Test
    void everyOfferedToolExistsAndEveryExpectedCallValidates() throws Exception {
        for (JsonNode c : fixture.get("cases")) {
            for (JsonNode offered : c.get("tools")) {
                assertThat(toolsByName).as("%s offers %s", c.get("id"), offered).containsKey(offered.asString());
            }
            JsonNode expect = c.get("expect");
            for (JsonNode round : expect.path("calls")) {
                for (JsonNode call : round) {
                    assertNoViolations(c, call.get("name").asString(), call.get("arguments"));
                }
            }
            for (JsonNode entries : c.path("app")) {
                for (JsonNode entry : entries) {
                    assertThat(entry.has("content") ^ entry.has("error")).as("%s entry", c.get("id")).isTrue();
                }
            }
            c.path("app").properties().forEach(tool -> {
                assertThat(toolsByName).containsKey(tool.getKey());
                tool.getValue().forEach(entry -> assertNoViolationsUnchecked(c, tool.getKey(), entry.get("match")));
            });
        }
    }

    @Test
    void theFakeAppAnswersEveryExpectedCall() throws Exception {
        for (JsonNode c : fixture.get("cases")) {
            if (!c.has("app")) {
                continue;
            }
            FakeToolApp app = new FakeToolApp(JSON, c.get("app"));
            for (JsonNode round : c.get("expect").path("calls")) {
                for (JsonNode call : round) {
                    Map<String, Object> arguments = arguments(call.get("arguments"));
                    ToolResult result = app.execute("call-1", call.get("name").asString(), arguments);
                    assertThat(result.content()).as("%s %s", c.get("id"), call.get("name")).isNotNull();
                    assertThat(result.callId()).isEqualTo("call-1");
                    if (c.get("id").asString().equals("TC04")) {
                        assertThat(result.isError()).isTrue();
                    }
                }
            }
        }
    }

    @Test
    void theFakeAppReportsUnknownRecordsAsErrors() throws Exception {
        FakeToolApp app = new FakeToolApp(JSON, JSON.readTree("{\"t\":[{\"match\":{\"a\":\"1\"},\"content\":{\"x\":1}}]}"));

        assertThat(app.execute("c", "t", Map.of("a", "1")).isError()).isFalse();
        ToolResult missing = app.execute("c", "t", Map.of("a", "2"));
        assertThat(missing.isError()).isTrue();
        assertThat(missing.content()).isEqualTo("{\"error\":\"no such record\"}");
    }

    @Test
    void fixtureTextHasNoEmailsAndOnlyLadderEnvironments() {
        assertThat(raw).doesNotContain("@");
        Set<String> environments = new HashSet<>();
        collectEnvironments(fixture, environments);
        assertThat(environments).isNotEmpty().isSubsetOf(LADDER);
    }

    private static void collectEnvironments(JsonNode node, Set<String> into) {
        if (node.isObject()) {
            node.properties().forEach(e -> {
                if (e.getKey().equals("environment") && e.getValue().isString()) {
                    into.add(e.getValue().asString());
                }
                if (e.getKey().equals("environment") && e.getValue().has("enum")) {
                    e.getValue().get("enum").forEach(v -> into.add(v.asString()));
                }
                collectEnvironments(e.getValue(), into);
            });
        } else if (node.isArray()) {
            node.forEach(n -> collectEnvironments(n, into));
        }
    }

    private static void assertNoViolations(JsonNode c, String tool, JsonNode arguments) throws Exception {
        JsonNode schema = toolsByName.get(tool).get("parameters");
        @SuppressWarnings("unchecked")
        Map<String, Object> schemaMap = JSON.treeToValue(schema, Map.class);
        assertThat(ToolSchema.violations(schemaMap, arguments(arguments)))
                .as("%s call %s", c.get("id"), tool).isEmpty();
    }

    private static void assertNoViolationsUnchecked(JsonNode c, String tool, JsonNode match) {
        try {
            JsonNode schema = toolsByName.get(tool).get("parameters");
            @SuppressWarnings("unchecked")
            Map<String, Object> schemaMap = JSON.treeToValue(schema, Map.class);
            Map<String, Object> args = arguments(match);
            Map<String, Object> unknownOnly = new LinkedHashMap<>(args);
            assertThat(ToolSchema.violations(schemaMap, unknownOnly).stream()
                    .filter(v -> !v.startsWith("missing required"))).as("%s match for %s", c.get("id"), tool).isEmpty();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> arguments(JsonNode node) throws Exception {
        return JSON.treeToValue(node, Map.class);
    }
}
