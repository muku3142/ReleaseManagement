package com.evision.ragservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ToolPropertiesTest {

    private static final String SECRET = "s".repeat(32);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GenerationConfig.class)
            .withPropertyValues(
                    "rag.chat.model=m", "rag.chat.num-ctx=1", "rag.chat.num-predict=1", "rag.chat.temperature=0",
                    "rag.chat.transform-temperature=0", "rag.chat.keep-alive=1m", "rag.chat.deadline=PT1S",
                    "rag.chat.heartbeat-interval=PT1S", "rag.chat.max-in-flight-per-client=1",
                    "rag.chat.max-in-flight-global=1", "rag.chat.retry-after-seconds=1", "rag.chat.k-default=1",
                    "rag.chat.k-max=1", "rag.chat.min-score=0", "rag.chat.question-max-chars=1",
                    "rag.chat.history-max-turns=1", "rag.chat.history-turn-max-chars=1",
                    "rag.chat.history-token-budget=1", "rag.chat.instruction-max-chars=1",
                    "rag.chat.chars-per-token=3");

    @Test
    void shortSecretIsRejectedWithoutEchoingIt() {
        String shortSecret = "x".repeat(31);
        assertThatThrownBy(() -> new ToolProperties(shortSecret, 4, 4, 5, 1500, 8000, 768))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RAG_TOOL_SIGNING_SECRET")
                .hasMessageNotContaining(shortSecret);
    }

    @Test
    void nullSecretIsRejected() {
        assertThatThrownBy(() -> new ToolProperties(null, 4, 4, 5, 1500, 8000, 768))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validSecretConstructsAndExposesValues() {
        ToolProperties props = new ToolProperties(SECRET, 4, 3, 5, 1500, 8000, 768);
        assertThat(props.signingSecret()).isEqualTo(SECRET);
        assertThat(props.maxCalls()).isEqualTo(3);
        assertThat(props.numPredict()).isEqualTo(768);
    }

    @Test
    void toStringOmitsTheSecret() {
        String text = new ToolProperties(SECRET, 4, 4, 5, 1500, 8000, 768).toString();
        assertThat(text).doesNotContain(SECRET).contains("maxRounds=4");
    }

    @Test
    void contextWithShortSecretFailsToStart() {
        runner.withPropertyValues("rag.tools.signing-secret=short", "rag.tools.max-rounds=4").run(context ->
                assertThat(context.getStartupFailure()).isNotNull());
    }

    @Test
    void contextWithValidSecretBindsCaps() {
        runner.withPropertyValues("rag.tools.signing-secret=" + SECRET, "rag.tools.max-rounds=4",
                "rag.tools.max-calls=4", "rag.tools.max-tools=5", "rag.tools.definitions-max-chars=1500",
                "rag.tools.result-max-chars=8000", "rag.tools.num-predict=768").run(context -> {
                    assertThat(context).hasNotFailed();
                    ToolProperties props = context.getBean(ToolProperties.class);
                    assertThat(props.maxRounds()).isEqualTo(4);
                    assertThat(props.resultMaxChars()).isEqualTo(8000);
                });
    }
}
