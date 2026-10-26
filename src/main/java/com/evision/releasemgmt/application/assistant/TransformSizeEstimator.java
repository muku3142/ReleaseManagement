package com.evision.releasemgmt.application.assistant;

import com.evision.releasemgmt.config.AssistantProperties.DocumentAssist;

/**
 * Mirrors rag-service's transform size limit so "too long" is decided before anything is sent.
 * Lengths are UTF-16 code units, the same unit as rag-service and JavaScript's string.length.
 */
public final class TransformSizeEstimator {

    static final int TEMPLATE_SLACK = 256;   // = rag-service PromptBuilder.TEMPLATE_SLACK (package-private there)

    private final int numCtx;
    private final double charsPerToken;
    private final int systemTokens;

    public TransformSizeEstimator(DocumentAssist documentAssist) {
        this(documentAssist.numCtx(), documentAssist.charsPerToken(), documentAssist.systemTokens());
    }

    TransformSizeEstimator(int numCtx, double charsPerToken, int systemTokens) {
        this.numCtx = numCtx;
        this.charsPerToken = charsPerToken;
        this.systemTokens = systemTokens;
    }

    public int maxChars(int instructionChars) {
        int instructionTokens = (int) Math.ceil(instructionChars / charsPerToken);
        // Integer division, as rag-service: the document is echoed back, so it may use half the remaining window.
        int tokens = (numCtx - systemTokens - instructionTokens - TEMPLATE_SLACK) / 2;
        return tokens <= 0 ? 0 : (int) Math.floor(tokens * charsPerToken);
    }

    public boolean fits(int documentChars, int instructionChars) {
        return documentChars <= maxChars(instructionChars);
    }
}
