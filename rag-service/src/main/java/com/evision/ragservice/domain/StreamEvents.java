package com.evision.ragservice.domain;

import java.util.List;

public final class StreamEvents {

    public static final String STATUS = "status";
    public static final String TOKEN = "token";
    public static final String CITATIONS = "citations";
    public static final String DONE = "done";
    public static final String ERROR = "error";
    public static final String TOOL_CALL = "tool_call";

    private StreamEvents() {
    }

    /** phase: SEARCHING | GENERATING */
    public record Status(String requestId, String phase) {}

    public record Token(String text) {
        @Override
        public String toString() {
            return "Token[chars=" + (text == null ? 0 : text.length()) + "]";
        }
    }

    public record Citations(CitationBasis basis, List<Citation> items) {}

    /** refusalLayer: GATE | MODEL | null */
    public record Done(boolean refusal, String refusalLayer, String finishReason, boolean truncated,
                       Integer promptTokens, Integer completionTokens) {

        public static Done forToolCalls(Integer promptTokens, Integer completionTokens) {
            return new Done(false, null, "tool_calls", false, promptTokens, completionTokens);
        }
    }

    public record ToolCallEvent(int round, String questionId, List<ToolCall> calls, String signature) {
        @Override
        public String toString() {
            return "ToolCallEvent[round=" + round + ", calls=" + (calls == null ? 0 : calls.size()) + "]";
        }
    }

    public record Error(ErrorReason reason) {}
}
