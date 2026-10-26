package com.evision.releasemgmt.domain.assistant;

import java.util.List;

public sealed interface ChatOutcome {

    record Answered(String text, List<Source> citations, boolean refused, String finishReason,
                    boolean historyTrimmed, String model, String promptHash) implements ChatOutcome {
    }

    record ToolCallsRequested(SignedTurn turn) implements ChatOutcome {
    }

    record Failed(FailureReason reason, Integer retryAfterSeconds) implements ChatOutcome {
    }
}
