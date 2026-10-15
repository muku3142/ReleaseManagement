package com.evision.ragservice.domain;

import java.util.Map;

public record ToolCall(String id, String name, Map<String, Object> arguments) {

    @Override
    public String toString() {
        return "ToolCall[nameChars=" + (name == null ? 0 : name.length())
                + ", arguments=" + (arguments == null ? 0 : arguments.size()) + "]";
    }
}
