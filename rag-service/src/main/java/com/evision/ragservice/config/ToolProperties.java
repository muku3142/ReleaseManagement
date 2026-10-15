package com.evision.ragservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rag.tools")
public record ToolProperties(String signingSecret, int maxRounds, int maxCalls, int maxTools,
                             int definitionsMaxChars, int resultMaxChars, int numPredict) {

    public static final int MIN_SECRET_CHARS = 32;

    public ToolProperties {
        if (signingSecret == null || signingSecret.length() < MIN_SECRET_CHARS) {
            throw new IllegalStateException("rag.tools.signing-secret (RAG_TOOL_SIGNING_SECRET) must be at least "
                    + MIN_SECRET_CHARS + " characters");
        }
    }

    @Override
    public String toString() {
        return "ToolProperties[maxRounds=" + maxRounds + ", maxCalls=" + maxCalls + ", maxTools=" + maxTools
                + ", definitionsMaxChars=" + definitionsMaxChars + ", resultMaxChars=" + resultMaxChars
                + ", numPredict=" + numPredict + "]";
    }
}
