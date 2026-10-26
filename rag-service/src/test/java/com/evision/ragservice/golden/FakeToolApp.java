package com.evision.ragservice.golden;

import com.evision.ragservice.domain.ToolResult;
import com.evision.ragservice.domain.ToolSource;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Plays the calling app for the golden tool tier: the service never executes tools itself (RAG-08). */
class FakeToolApp {

    private static final String NO_SUCH_RECORD = "{\"error\":\"no such record\"}";

    private final ObjectMapper json;
    private final JsonNode app;

    FakeToolApp(ObjectMapper json, JsonNode app) {
        this.json = json;
        this.app = app;
    }

    ToolResult execute(String callId, String name, Map<String, Object> arguments) {
        for (JsonNode entry : app.path(name)) {
            if (matches(entry.path("match"), arguments)) {
                ToolSource source = entry.has("source")
                        ? new ToolSource(entry.get("source").path("id").asString(null),
                                entry.get("source").path("title").asString(null),
                                entry.get("source").path("url").asString(null))
                        : null;
                if (entry.has("error")) {
                    return new ToolResult(callId, json.writeValueAsString(Map.of("error", entry.get("error").asString())),
                            true, source);
                }
                return new ToolResult(callId, json.writeValueAsString(entry.get("content")), false, source);
            }
        }
        return new ToolResult(callId, NO_SUCH_RECORD, true, null);
    }

    private static boolean matches(JsonNode match, Map<String, Object> arguments) {
        for (Map.Entry<String, JsonNode> expected : match.properties()) {
            Object actual = arguments == null ? null : arguments.get(expected.getKey());
            if (actual == null || !String.valueOf(actual).equals(expected.getValue().asString())) {
                return false;
            }
        }
        return true;
    }
}
