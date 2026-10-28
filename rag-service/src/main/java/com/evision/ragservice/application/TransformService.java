package com.evision.ragservice.application;

import com.evision.ragservice.config.ChatProperties;
import com.evision.ragservice.domain.CancelReason;
import com.evision.ragservice.domain.Cancellation;
import com.evision.ragservice.domain.ChatMessage;
import com.evision.ragservice.domain.Completion;
import com.evision.ragservice.domain.ErrorReason;
import com.evision.ragservice.domain.GenerationOptions;
import com.evision.ragservice.domain.InvalidContentException;
import com.evision.ragservice.domain.MetadataRules;
import com.evision.ragservice.domain.StreamEvents;
import com.evision.ragservice.domain.StreamEvents.Done;
import com.evision.ragservice.domain.StreamEvents.Status;
import com.evision.ragservice.domain.StreamEvents.Token;
import com.evision.ragservice.domain.TextTooLargeException;
import com.evision.ragservice.domain.TooManyRequestsException;
import com.evision.ragservice.ports.ChatPort;
import com.evision.ragservice.ports.EventSink;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Shares gate, deadline, heartbeat and error mapping with ChatService; no retrieval, no citations
@Service
public class TransformService {

    private static final Logger log = LoggerFactory.getLogger(TransformService.class);
    private static final int MAX_OUTPUT_TOKENS = 4096;

    public record Command(String text, String instruction) {}

    public record Plan(String requestId, UUID clientId, List<ChatMessage> messages, int estimatedTokens,
                       int numPredict, Cancellation cancellation) {}

    private final ChatPort chat;
    private final GenerationGate gate;
    private final GenerationTimers timers;
    private final ChatProperties props;
    private final TokenEstimator estimator;
    private final PromptBuilder promptBuilder;
    private final String promptHash;

    public TransformService(ChatPort chat, GenerationGate gate, GenerationTimers timers, ChatProperties props,
                            PromptRules rules) {
        this.chat = chat;
        this.gate = gate;
        this.timers = timers;
        this.props = props;
        this.estimator = new TokenEstimator(props.charsPerToken());
        this.promptBuilder = new PromptBuilder(estimator, props.numCtx(), props.historyTokenBudget(), rules);
        this.promptHash = rules.hash();
    }

    public Plan prepare(UUID clientId, Command command) {
        MetadataRules.rejectNul("text", command.text());
        MetadataRules.rejectNul("instruction", command.instruction());
        if (command.instruction().length() > props.instructionMaxChars()) {
            throw new InvalidContentException("instruction exceeds " + props.instructionMaxChars() + " characters");
        }
        int maxTokens = promptBuilder.maxTransformTextTokens(command.instruction());
        if (estimator.estimate(command.text()) > maxTokens) {
            throw new TextTooLargeException(estimator.maxChars(maxTokens));
        }
        PromptBuilder.BuiltPrompt prompt = promptBuilder.transform(command.text(), command.instruction());
        int numPredict = Math.min(MAX_OUTPUT_TOKENS, props.numCtx() - prompt.estimatedTokens());

        chat.assertReady();

        if (!gate.tryAcquire(clientId)) {
            log.info("generation endpoint=transform outcome=TOO_MANY_REQUESTS clientId={}", clientId);
            throw new TooManyRequestsException();
        }
        return new Plan(UUID.randomUUID().toString(), clientId, prompt.messages(), prompt.estimatedTokens(),
                numPredict, new Cancellation());
    }

    public void run(Plan plan, EventSink sink) {
        long start = System.nanoTime();
        Cancellation cancel = plan.cancellation();
        String outcome = "OK";
        Completion completion = null;
        boolean truncated = false;
        long[] firstTokenNanos = {0};
        ScheduledFuture<?> watchdog = timers.after(props.deadline(), () -> cancel.cancel(CancelReason.DEADLINE));
        ScheduledFuture<?> heartbeat = timers.every(props.heartbeatInterval(), sink::heartbeat);
        try {
            sink.send(StreamEvents.STATUS, new Status(plan.requestId(), "GENERATING"));
            completion = chat.stream(plan.messages(),
                    new GenerationOptions(props.numCtx(), plan.numPredict(), props.transformTemperature()),
                    token -> {
                        if (firstTokenNanos[0] == 0) {
                            firstTokenNanos[0] = System.nanoTime();
                        }
                        sink.send(StreamEvents.TOKEN, new Token(token));
                    }, cancel);
            if (completion.isCancelled()) {
                if (completion.cancelledBy() == CancelReason.DEADLINE) {
                    sink.send(StreamEvents.ERROR, new StreamEvents.Error(ErrorReason.DEADLINE));
                }
                outcome = completion.cancelledBy().name();
            } else {
                truncated = completion.promptEvalCount() != null && ChatService.probablyTruncated(
                        plan.estimatedTokens(), completion.promptEvalCount(), props.numCtx());
                sink.send(StreamEvents.DONE, new Done(false, null, completion.finishReason(), truncated,
                        completion.promptEvalCount(), completion.evalCount(), false, props.model(), promptHash));
            }
        } catch (RuntimeException e) {
            ErrorReason reason = cancel.reason() == CancelReason.DEADLINE ? ErrorReason.DEADLINE
                    : ChatService.errorReason(e);
            if (cancel.reason() == CancelReason.CLIENT_GONE) {
                outcome = CancelReason.CLIENT_GONE.name();
            } else {
                sink.send(StreamEvents.ERROR, new StreamEvents.Error(reason));
                outcome = reason.name();
            }
            if (reason == ErrorReason.INTERNAL) {
                log.error("generation requestId={} failed with {}", plan.requestId(), e.getClass().getSimpleName());
            }
        } finally {
            watchdog.cancel(false);
            heartbeat.cancel(false);
            gate.release(plan.clientId());
            long end = System.nanoTime();
            log.info("generation endpoint=transform outcome={} requestId={} clientId={} promptVersion={} model={} "
                            + "estimatedPromptTokens={} promptEvalCount={} evalCount={} doneReason={} loadMs={} "
                            + "totalMs={} ttftMs={} truncated={} promptHash={}",
                    outcome, plan.requestId(), plan.clientId(), Prompts.PROMPT_VERSION, props.model(),
                    plan.estimatedTokens(), completion == null ? null : completion.promptEvalCount(),
                    completion == null ? null : completion.evalCount(),
                    completion == null ? null : completion.finishReason(),
                    completion == null ? null : completion.loadDurationMs(),
                    Duration.ofNanos(end - start).toMillis(),
                    firstTokenNanos[0] == 0 ? null : Duration.ofNanos(firstTokenNanos[0] - start).toMillis(),
                    truncated, promptHash);
            sink.complete();
        }
    }
}
