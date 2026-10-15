package com.evision.releasemgmt.domain.assistant;

import java.util.Map;

public record ToolDefinition(String name, String description, Map<String, Object> parameters) {
}
