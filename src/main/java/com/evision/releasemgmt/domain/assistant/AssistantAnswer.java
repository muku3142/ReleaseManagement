package com.evision.releasemgmt.domain.assistant;

import java.util.List;

public record AssistantAnswer(String text, List<Source> sources, List<ToolRun> toolsRun, boolean refused,
                              FailureReason failure) {
}
