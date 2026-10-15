package com.evision.releasemgmt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.assistant")
public record AssistantProperties(boolean enabled, String baseUrl, String apiKey, String embeddingModel,
                                  Sweep sweep, Timeouts index, Timeouts chat, int maxRounds, int toolResultMaxChars) {

    public record Sweep(Duration initialDelay, Duration interval) {
    }

    public record Timeouts(Duration connectTimeout, Duration readTimeout) {
    }

    public AssistantProperties {
        if (enabled && (isBlank(baseUrl) || isBlank(apiKey))) {
            throw new IllegalStateException(
                    "app.assistant.enabled=true requires app.assistant.base-url and app.assistant.api-key");
        }
    }

    // The key is a bearer credential for rag-service; it must never reach logs or actuator output.
    @Override
    public String toString() {
        return "AssistantProperties[enabled=" + enabled + ", baseUrl=" + baseUrl
                + ", apiKey=" + (isBlank(apiKey) ? "<unset>" : "***")
                + ", embeddingModel=" + embeddingModel + ", sweep=" + sweep + ", index=" + index
                + ", chat=" + chat + ", maxRounds=" + maxRounds + ", toolResultMaxChars=" + toolResultMaxChars + "]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
