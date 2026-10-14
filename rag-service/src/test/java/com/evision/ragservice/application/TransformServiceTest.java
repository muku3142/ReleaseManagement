package com.evision.ragservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evision.ragservice.config.ChatProperties;
import com.evision.ragservice.domain.ErrorReason;
import com.evision.ragservice.domain.GenerationFailedException;
import com.evision.ragservice.domain.InvalidContentException;
import com.evision.ragservice.domain.ModelNotAvailableException;
import com.evision.ragservice.domain.StreamEvents;
import com.evision.ragservice.domain.TextTooLargeException;
import com.evision.ragservice.domain.TooManyRequestsException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TransformServiceTest {

    private static final UUID CLIENT = UUID.randomUUID();

    private final ChatProperties props = new ChatProperties("qwen3:4b", 8192, 768, 0.2, 0.3, "30m",
            Duration.ofMillis(300), Duration.ofMillis(50), 1, 4, 5, 5, 8, 0.55, 2000, 20, 4000, 1500, 2000, 3.0);
    private final FakeChatPort chat = new FakeChatPort();
    private final RecordingEventSink sink = new RecordingEventSink();
    private final GenerationGate gate = new GenerationGate(props);
    private final GenerationTimers timers = new GenerationTimers();
    private final TransformService service = new TransformService(chat, gate, timers, props);

    @AfterEach
    void tearDown() {
        timers.destroy();
    }

    private TransformService.Command command(String text) {
        return new TransformService.Command(text, "Make it formal");
    }

    @Test
    void oversizedInstructionAndNulAreRejected() {
        assertThatThrownBy(() -> service.prepare(CLIENT, new TransformService.Command("t", "x".repeat(2001))))
                .isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> service.prepare(CLIENT, new TransformService.Command("a\u0000", "i")))
                .isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> service.prepare(CLIENT, new TransformService.Command("t", "a\u0000")))
                .isInstanceOf(InvalidContentException.class);
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void oversizedTextIsRejectedBeforeReadinessAndGate() {
        var estimator = new TokenEstimator(props.charsPerToken());
        var builder = new PromptBuilder(estimator, props.numCtx(), props.historyTokenBudget());
        int maxChars = estimator.maxChars(builder.maxTransformTextTokens("Make it formal"));

        assertThatThrownBy(() -> service.prepare(CLIENT, command("x".repeat(maxChars + 10))))
                .isInstanceOfSatisfying(TextTooLargeException.class,
                        e -> assertThat(e.getMessage()).contains(String.valueOf(maxChars)));
        assertThat(chat.readyCalls).isZero();
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void readinessFailurePropagatesWithEmptyGate() {
        chat.readyFailure = new ModelNotAvailableException("qwen3:4b");

        assertThatThrownBy(() -> service.prepare(CLIENT, command("hello"))).isInstanceOf(ModelNotAvailableException.class);
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void gateIsSharedWithAnyHeldPlan() {
        gate.tryAcquire(CLIENT);

        assertThatThrownBy(() -> service.prepare(CLIENT, command("hello"))).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void happyPathStreamsWithoutCitations() {
        chat.tokens = List.of("Formal ", "text.");

        TransformService.Plan plan = service.prepare(CLIENT, command("hello"));
        service.run(plan, sink);

        assertThat(sink.events()).containsExactly("status", "token", "token", "done");
        assertThat(sink.payload(StreamEvents.STATUS, StreamEvents.Status.class).phase()).isEqualTo("GENERATING");
        assertThat(sink.payload(StreamEvents.DONE, StreamEvents.Done.class).refusal()).isFalse();
        assertThat(chat.lastMessages.getFirst().content()).isEqualTo(Prompts.TRANSFORM_SYSTEM);
        assertThat(chat.lastOptions.temperature()).isEqualTo(props.transformTemperature());
        assertThat(chat.lastOptions.numPredict())
                .isEqualTo(Math.min(4096, props.numCtx() - plan.estimatedTokens()));
        assertThat(sink.completed).isTrue();
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void noAnswerTextIsStreamedUnchanged() {
        chat.tokens = List.of("NO_", "ANSWER");

        service.run(service.prepare(CLIENT, command("hello")), sink);

        assertThat(sink.events()).containsExactly("status", "token", "token", "done");
    }

    @Test
    void deadlineEndsWithDeadlineError() {
        chat.blockUntilCancelled = true;

        service.run(service.prepare(CLIENT, command("hello")), sink);

        assertThat(sink.events()).containsExactly("status", "error");
        assertThat(sink.payload(StreamEvents.ERROR, StreamEvents.Error.class).reason()).isEqualTo(ErrorReason.DEADLINE);
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void generationFailureMapsToGenerationFailed() {
        chat.tokens = List.of("a", "b");
        chat.failAfterFirstToken = new GenerationFailedException("boom");

        service.run(service.prepare(CLIENT, command("hello")), sink);

        assertThat(sink.events()).containsExactly("status", "token", "error");
        assertThat(sink.payload(StreamEvents.ERROR, StreamEvents.Error.class).reason())
                .isEqualTo(ErrorReason.GENERATION_FAILED);
        assertThat(gate.anyInFlight()).isFalse();
    }
}
