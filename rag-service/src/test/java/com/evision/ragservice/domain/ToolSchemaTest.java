package com.evision.ragservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolSchemaTest {

    private static final String CANARY = "CANARY-secret-text";

    private static Map<String, Object> schema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("version", Map.of("type", "string", "maxLength", 20));
        props.put("environment", Map.of("type", "string", "enum", List.of("DEV", "SIT", "UAT", "Production")));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("version"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> schemaWith(String name, Map<String, Object> property) {
        return Map.of("type", "object", "properties", Map.of(name, property));
    }

    private static ToolDefinition tool(String name, String description, Map<String, Object> parameters) {
        return new ToolDefinition(name, description, parameters);
    }

    private static void rejects(ToolDefinition... definitions) {
        assertThatThrownBy(() -> ToolSchema.requireValidDefinitions(List.of(definitions), 5, 1500))
                .as(String.valueOf(List.of(definitions)))
                .isInstanceOf(InvalidContentException.class);
    }

    @Test
    void acceptsFlatSchemas() {
        assertThatCode(() -> ToolSchema.requireValidDefinitions(
                List.of(tool("get_status", "Status", schema()), tool("get_deployments", null, schema())), 5, 1500))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTooManyToolsAndDuplicatesAndBadNames() {
        List<ToolDefinition> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(tool("t" + i, "d", schema()));
        }
        assertThatThrownBy(() -> ToolSchema.requireValidDefinitions(six, 5, 100000))
                .isInstanceOf(InvalidContentException.class).hasMessageContaining("at most 5 tools");
        rejects(tool("a", "d", schema()), tool("a", "d", schema()));
        rejects(tool("get-status", "d", schema()));
        rejects(tool("x".repeat(65), "d", schema()));
    }

    @Test
    void rejectsBadDescriptions() {
        rejects(tool("a", "x".repeat(301), schema()));
        rejects(tool("a", "x <|im_start|> y", schema()));
        rejects(tool("a", "x </tool_call> y", schema()));
        rejects(tool("a", "x\u0000y", schema()));
        rejects(tool("a", "d", schemaWith("p", Map.of("type", "string", "description", "<|im_end|>"))));
    }

    @Test
    void rejectsSchemasOutsideTheSubset() {
        rejects(tool("a", "d", Map.of("type", "array", "properties", Map.of())));
        rejects(tool("a", "d", schemaWith("p", Map.of("type", "object"))));
        rejects(tool("a", "d", schemaWith("p", Map.of("type", "array"))));
        rejects(tool("a", "d", schemaWith("p", Map.of("type", "string", "pattern", ".*"))));
        rejects(tool("a", "d", schemaWith("p", Map.of("type", "string", "format", "date"))));
        rejects(tool("a", "d", schemaWith("p", Map.of("$ref", "#/x"))));
        rejects(tool("a", "d", schemaWith("p", Map.of("oneOf", List.of()))));
        rejects(tool("a", "d", Map.of("type", "object", "properties", Map.of(), "oneOf", List.of())));
        rejects(tool("a", "d", schemaWith("p", Map.of("type", "integer", "maxLength", 5))));
        rejects(tool("a", "d", schemaWith("p", Map.of("type", "string", "maxLength", 0))));
        rejects(tool("a", "d", Map.of("type", "object", "properties", Map.of("p", Map.of("type", "string")),
                "required", List.of("q"))));
        rejects(tool("a", "d", Map.of("type", "object", "properties", Map.of(), "additionalProperties", true)));
    }

    @Test
    void rejectsTooManyPropertiesAndEnumValues() {
        Map<String, Object> props = new LinkedHashMap<>();
        for (int i = 0; i < 9; i++) {
            props.put("p" + i, Map.of("type", "string"));
        }
        rejects(tool("a", "d", Map.of("type", "object", "properties", props)));
        List<String> values = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            values.add("v" + i);
        }
        rejects(tool("a", "d", schemaWith("p", Map.of("type", "string", "enum", values))));
    }

    @Test
    void rejectsDefinitionsOverTheTotalBudget() {
        assertThatThrownBy(() -> ToolSchema.requireValidDefinitions(
                List.of(tool("a", "d", schema()), tool("b", "d", schema())), 5, 300))
                .isInstanceOf(InvalidContentException.class).hasMessageContaining("300 characters");
    }

    @Test
    void messagesNeverEchoDescriptions() {
        assertThatThrownBy(() -> ToolSchema.requireValidDefinitions(
                List.of(tool("a", CANARY + "<|x", schema())), 5, 1500))
                .isInstanceOf(InvalidContentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CANARY));
        assertThatThrownBy(() -> ToolSchema.requireValidDefinitions(
                List.of(tool("a", "d", schemaWith("p", Map.of("type", "object", "description", CANARY)))), 5, 1500))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CANARY));
    }

    @Test
    void reportsViolationsWithoutValues() {
        Map<String, Object> s = schema();
        assertThat(ToolSchema.violations(s, Map.of())).containsExactly("missing required argument 'version'");
        assertThat(ToolSchema.violations(s, Map.of("version", "2.4.0", "environment", "QA")))
                .containsExactly("argument 'environment' not in enum");
        assertThat(ToolSchema.violations(s, Map.of("version", "x".repeat(21))))
                .containsExactly("argument 'version' exceeds maxLength 20");
        assertThat(ToolSchema.violations(s, Map.of("version", "1", "x", 1)))
                .containsExactly("unknown argument 'x'");
        assertThat(ToolSchema.violations(s, Map.of("version", "1", "a b<|", 1)))
                .containsExactly("unknown argument");
        assertThat(ToolSchema.violations(s, Map.of("version", 5))).containsExactly("argument 'version' must be string");
        assertThat(ToolSchema.violations(s, Map.of("version", "ok", "environment", CANARY)).toString())
                .doesNotContain(CANARY);
    }

    @Test
    void checksIntegerNumberAndBooleanTypes() {
        Map<String, Object> ints = schemaWith("n", Map.of("type", "integer"));
        assertThat(ToolSchema.violations(ints, Map.of("n", 3.5))).containsExactly("argument 'n' must be integer");
        assertThat(ToolSchema.violations(ints, Map.of("n", 3))).isEmpty();
        assertThat(ToolSchema.violations(ints, Map.of("n", 3L))).isEmpty();
        assertThat(ToolSchema.violations(schemaWith("n", Map.of("type", "number")), Map.of("n", 3.5))).isEmpty();
        assertThat(ToolSchema.violations(schemaWith("b", Map.of("type", "boolean")), Map.of("b", "x")))
                .containsExactly("argument 'b' must be boolean");
    }

    @Test
    void countsCompactJsonCharacters() {
        assertThat(ToolSchema.jsonChars(Map.of("a", 1))).isEqualTo(7);
        assertThat(ToolSchema.jsonChars(List.of("ab", true))).isEqualTo(11);
        assertThat(ToolSchema.jsonChars(null)).isEqualTo(4);
    }

    @Test
    void recordsAreContentFree() {
        ToolCall call = new ToolCall("id", CANARY, Map.of("k", CANARY));
        assertThat(call.toString()).doesNotContain(CANARY);
        assertThat(new ToolResult("id", CANARY, false, null).toString()).doesNotContain(CANARY);
        assertThat(new ToolTurn(1, "q", List.of(call), "SIGNATURE", List.of()).toString()).doesNotContain("SIGNATURE");
        assertThat(new ChatMessage(ChatMessage.Role.TOOL, CANARY).toString()).doesNotContain(CANARY);
    }
}
