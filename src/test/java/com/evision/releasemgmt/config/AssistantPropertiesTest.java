package com.evision.releasemgmt.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantPropertiesTest {

    private static final String SECRET = "canary-secret-123";

    private static AssistantProperties props(boolean enabled, String baseUrl, String apiKey) {
        AssistantProperties.Timeouts timeouts = new AssistantProperties.Timeouts(Duration.ofSeconds(2), Duration.ofSeconds(5));
        return new AssistantProperties(enabled, baseUrl, apiKey, "nomic-embed-text",
                new AssistantProperties.Sweep(Duration.ofSeconds(10), Duration.ofMinutes(30)),
                timeouts, timeouts, 4, 6000);
    }

    @Test
    void enabledWithBlankBaseUrlFailsNamingBothKeys() {
        assertThatThrownBy(() -> props(true, " ", SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.assistant.base-url")
                .hasMessageContaining("app.assistant.api-key")
                .hasMessageNotContaining(SECRET);
    }

    @Test
    void enabledWithBlankApiKeyFailsNamingBothKeys() {
        assertThatThrownBy(() -> props(true, "http://rag-service:8081", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.assistant.base-url")
                .hasMessageContaining("app.assistant.api-key");
    }

    @Test
    void disabledWithBlankApiKeyConstructs() {
        assertThatCode(() -> props(false, "", "")).doesNotThrowAnyException();
    }

    @Test
    void toStringNeverContainsTheApiKey() {
        assertThat(props(true, "http://rag-service:8081", SECRET).toString())
                .doesNotContain(SECRET)
                .contains("***");
    }
}
