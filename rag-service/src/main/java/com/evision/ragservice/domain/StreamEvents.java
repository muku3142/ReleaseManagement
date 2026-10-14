package com.evision.ragservice.domain;

import java.util.List;

public final class StreamEvents {

    public static final String STATUS = "status";
    public static final String TOKEN = "token";
    public static final String CITATIONS = "citations";
    public static final String DONE = "done";
    public static final String ERROR = "error";

    private StreamEvents() {
    }

    /** phase: SEARCHING | GENERATING */
    public record Status(String requestId, String phase) {}

    public record Token(String text) {}

    public record Citations(CitationBasis basis, List<Citation> items) {}

    /** refusalLayer: GATE | MODEL | null */
    public record Done(boolean refusal, String refusalLayer, String finishReason, boolean truncated,
                       Integer promptTokens, Integer completionTokens) {}

    public record Error(ErrorReason reason) {}
}
