package com.evision.releasemgmt.domain.assistant;

import java.util.Map;

public record ToolRun(int round, String name, Map<String, Object> arguments, boolean error, long durationMillis) {
}
