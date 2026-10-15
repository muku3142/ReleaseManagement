package com.evision.releasemgmt.domain.assistant;

import java.util.Map;

public record ToolCall(String id, String name, Map<String, Object> arguments) {

    @Override
    public String toString() {
        return "ToolCall[" + id + ", " + name + "]";
    }
}
