package com.evision.ragservice.domain;

public record Completion(String finishReason, Integer promptEvalCount, Integer evalCount, Long loadDurationMs,
                         Long totalDurationMs, CancelReason cancelledBy) {

    public static Completion cancelled(CancelReason reason) {
        return new Completion(null, null, null, null, null, reason);
    }

    public boolean isCancelled() {
        return cancelledBy != null;
    }
}
