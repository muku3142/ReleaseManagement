package com.evision.ragservice.domain;

import java.util.List;

public record Completion(String finishReason, Integer promptEvalCount, Integer evalCount, Long loadDurationMs,
                         Long totalDurationMs, CancelReason cancelledBy, Long evalDurationMs,
                         List<ToolCall> toolCalls) {

    public Completion {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public Completion(String finishReason, Integer promptEvalCount, Integer evalCount, Long loadDurationMs,
                      Long totalDurationMs, CancelReason cancelledBy) {
        this(finishReason, promptEvalCount, evalCount, loadDurationMs, totalDurationMs, cancelledBy, null, List.of());
    }

    public static Completion cancelled(CancelReason reason) {
        return new Completion(null, null, null, null, null, reason);
    }

    public boolean isCancelled() {
        return cancelledBy != null;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
