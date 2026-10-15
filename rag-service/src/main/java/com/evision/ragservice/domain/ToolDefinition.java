package com.evision.ragservice.domain;

import java.util.Map;

public record ToolDefinition(String name, String description, Map<String, Object> parameters) {

    @Override
    public String toString() {
        return "ToolDefinition[name=" + name + ", descriptionChars=" + (description == null ? 0 : description.length()) + "]";
    }
}
