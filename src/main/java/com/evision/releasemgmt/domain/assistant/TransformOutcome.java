package com.evision.releasemgmt.domain.assistant;

public sealed interface TransformOutcome {

    record Done(String finishReason, boolean truncated, Integer promptTokens, Integer completionTokens)
            implements TransformOutcome {
    }

    record TooLarge(Integer maxChars) implements TransformOutcome {
    }

    record Failed(FailureReason reason, Integer retryAfterSeconds) implements TransformOutcome {
    }
}
