package com.evision.ragservice.application;

import com.evision.ragservice.config.ChatProperties;
import com.evision.ragservice.config.ToolProperties;
import com.evision.ragservice.domain.CancelReason;
import com.evision.ragservice.domain.Cancellation;
import com.evision.ragservice.domain.ChatMessage;
import com.evision.ragservice.domain.ChatMessage.Role;
import com.evision.ragservice.domain.CitationBasis;
import com.evision.ragservice.domain.Completion;
import com.evision.ragservice.domain.DocumentCollection;
import com.evision.ragservice.domain.EmbeddingPurpose;
import com.evision.ragservice.domain.EmbeddingUnavailableException;
import com.evision.ragservice.domain.ErrorReason;
import com.evision.ragservice.domain.GenerationFailedException;
import com.evision.ragservice.domain.GenerationOptions;
import com.evision.ragservice.domain.InvalidContentException;
import com.evision.ragservice.domain.MetadataFilter;
import com.evision.ragservice.domain.MetadataRules;
import com.evision.ragservice.domain.MixedEmbeddingModelsException;
import com.evision.ragservice.domain.ModelNotAvailableException;
import com.evision.ragservice.domain.OllamaUnavailableException;
import com.evision.ragservice.domain.SearchHit;
import com.evision.ragservice.domain.StreamEvents;
import com.evision.ragservice.domain.StreamEvents.Citations;
import com.evision.ragservice.domain.StreamEvents.Done;
import com.evision.ragservice.domain.StreamEvents.Status;
import com.evision.ragservice.domain.StreamEvents.Token;
import com.evision.ragservice.domain.StreamEvents.ToolCallEvent;
import com.evision.ragservice.domain.TooManyRequestsException;
import com.evision.ragservice.domain.ToolCall;
import com.evision.ragservice.domain.ToolDefinition;
import com.evision.ragservice.domain.ToolResult;
import com.evision.ragservice.domain.ToolSchema;
import com.evision.ragservice.domain.ToolTurn;
import com.evision.ragservice.domain.ToolTurnRejectedException;
import com.evision.ragservice.ports.ChatPort;
import com.evision.ragservice.ports.ChunkSearch;
import com.evision.ragservice.ports.EmbeddingPort;
import com.evision.ragservice.ports.EventSink;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

// Deliberately not @Transactional: retrieval is one short query and no DB connection may be held across the stream
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final double TRUNCATION_RATIO = 0.6;
    private static final double NEAR_FULL_CONTEXT = 0.8;

    public record Command(List<String> collections, String question, List<ChatMessage> history,
                          Map<String, Object> filter, Integer k, List<ToolDefinition> tools,
                          List<ToolTurn> toolTurns) {

        public Command {
            tools = tools == null ? List.of() : tools;
            toolTurns = toolTurns == null ? List.of() : toolTurns;
        }

        public Command(List<String> collections, String question, List<ChatMessage> history,
                       Map<String, Object> filter, Integer k) {
            this(collections, question, history, filter, k, List.of(), List.of());
        }
    }

    public record Plan(String requestId, UUID clientId, List<DocumentCollection> collections, String question,
                       List<ChatMessage> history, MetadataFilter filter, int k, Cancellation cancellation,
                       List<ToolDefinition> tools, List<ToolTurn> toolTurns, String questionId) {}

    private final CollectionService collections;
    private final EmbeddingPort embeddings;
    private final ChunkSearch chunks;
    private final ChatPort chat;
    private final GenerationGate gate;
    private final GenerationTimers timers;
    private final ChatProperties props;
    private final PromptBuilder promptBuilder;
    private final String promptHash;
    private final CitationBuilder citationBuilder;
    private final ToolProperties toolProps;
    private final ToolTurnSigner signer;
    private final ToolPolicy toolPolicy;

    public ChatService(CollectionService collections, EmbeddingPort embeddings, ChunkSearch chunks, ChatPort chat,
                       GenerationGate gate, GenerationTimers timers, ChatProperties props, ToolProperties toolProps,
                       ObjectMapper objectMapper, PromptRules rules) {
        this.collections = collections;
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.chat = chat;
        this.gate = gate;
        this.timers = timers;
        this.props = props;
        this.promptBuilder = new PromptBuilder(new TokenEstimator(props.charsPerToken()), props.numCtx(),
                props.historyTokenBudget(), rules);
        this.promptHash = rules.hash();
        this.citationBuilder = new CitationBuilder(props.minScore());
        this.toolProps = toolProps;
        this.signer = new ToolTurnSigner(toolProps.signingSecret(), objectMapper);
        this.toolPolicy = new ToolPolicy(toolProps.maxRounds(), toolProps.maxCalls(), toolProps.resultMaxChars());
    }

    public Plan prepare(UUID clientId, Command command) {
        List<DocumentCollection> owned = collections.requireAll(clientId, command.collections());

        String question = command.question();
        MetadataRules.rejectNul("question", question);
        if (question.length() > props.questionMaxChars()) {
            throw new InvalidContentException("question exceeds " + props.questionMaxChars() + " characters");
        }

        List<ChatMessage> history = command.history() == null ? List.of() : command.history();
        if (history.size() > props.historyMaxTurns()) {
            throw new InvalidContentException("history exceeds " + props.historyMaxTurns() + " turns");
        }
        for (ChatMessage turn : history) {
            if (turn.role() == Role.SYSTEM) {
                throw new InvalidContentException("history roles must be user or assistant");
            }
            if (turn.content().length() > props.historyTurnMaxChars()) {
                throw new InvalidContentException("history turn exceeds " + props.historyTurnMaxChars() + " characters");
            }
            MetadataRules.rejectNul("history", turn.content());
        }

        int k = command.k() == null ? props.kDefault() : command.k();
        if (k < 1 || k > props.kMax()) {
            throw new InvalidContentException("k must be between 1 and " + props.kMax());
        }

        MetadataFilter filter = MetadataRules.parseFilter(command.filter());

        if (owned.stream().map(DocumentCollection::embeddingModel).distinct().count() > 1) {
            throw new MixedEmbeddingModelsException();
        }

        List<ToolDefinition> tools = command.tools();
        List<ToolTurn> turns = command.toolTurns();
        if (!tools.isEmpty()) {
            ToolSchema.requireValidDefinitions(tools, toolProps.maxTools(), toolProps.definitionsMaxChars());
        }
        if (!turns.isEmpty()) {
            if (tools.isEmpty()) {
                throw new ToolTurnRejectedException("toolTurns require tools");
            }
            signer.verifyChain(clientId, question, turns);
            toolPolicy.requireValidResume(turns, tools);
        }
        if (!tools.isEmpty()) {
            promptBuilder.requireToolBudget(question, tools, turns, k, toolPolicy.isFinalRound(turns),
                    toolProps.numPredict());
        }

        chat.assertReady();
        if (!tools.isEmpty()) {
            chat.assertToolsReady();
        }

        if (!gate.tryAcquire(clientId)) {
            log.info("generation endpoint=chat outcome=TOO_MANY_REQUESTS clientId={}", clientId);
            throw new TooManyRequestsException();
        }
        String requestId = UUID.randomUUID().toString();
        String questionId = turns.isEmpty() ? requestId : turns.getFirst().questionId();
        return new Plan(requestId, clientId, owned, question, history, filter, k, new Cancellation(), tools, turns,
                questionId);
    }

    private static final class RunStats {
        String outcome = "OK";
        int passages;
        Double topScore;
        Integer estimatedTokens;
        Completion completion;
        Long ttftMs;
        Boolean refusal;
        CitationBasis basis;
        Integer inventedIds;
        boolean truncated;
        int callsEmitted;
        List<String> callNames = List.of();
        int unknownCallNames;
        boolean correctionRetry;
    }

    public void run(Plan plan, EventSink sink) {
        long start = System.nanoTime();
        Cancellation cancel = plan.cancellation();
        RunStats stats = new RunStats();
        ScheduledFuture<?> watchdog = timers.after(props.deadline(), () -> cancel.cancel(CancelReason.DEADLINE));
        ScheduledFuture<?> heartbeat = timers.every(props.heartbeatInterval(), sink::heartbeat);
        boolean withTools = !plan.tools().isEmpty();
        boolean finalRound = withTools && toolPolicy.isFinalRound(plan.toolTurns());
        AtomicBoolean released = new AtomicBoolean();
        Runnable releaseOnce = () -> {
            if (released.compareAndSet(false, true)) {
                gate.release(plan.clientId());
            }
        };
        try {
            sink.send(StreamEvents.STATUS, new Status(plan.requestId(), "SEARCHING"));
            String model = plan.collections().getFirst().embeddingModel();
            float[] vector = embeddings.embed(model, EmbeddingPurpose.QUERY, List.of(plan.question())).getFirst();
            List<SearchHit> hits = chunks.search(plan.collections().stream().map(DocumentCollection::id).toList(),
                    model, vector, plan.filter(), plan.k());
            stats.passages = hits.size();
            stats.topScore = hits.isEmpty() ? null : hits.getFirst().score();
            if (!withTools && (hits.isEmpty() || hits.getFirst().score() < props.minScore())) {
                refuse(sink, false, "GATE", stats, null);
                return;
            }

            List<SourceDocument> sources = group(hits, plan.collections());
            PromptBuilder.BuiltPrompt answerPrompt = withTools
                    ? promptBuilder.chatWithTools(sources, plan.history(), plan.question(), plan.tools(),
                            plan.toolTurns(), finalRound, toolProps.numPredict())
                    : promptBuilder.chat(sources, plan.history(), plan.question(), props.numPredict());
            // The first tool round is a routing request without sources; its text is never streamed, except as a
            // clarification when the answer round refuses (it saw only the question and history, no retrieved content)
            boolean routing = withTools && !finalRound && plan.toolTurns().isEmpty();
            PromptBuilder.BuiltPrompt prompt = routing
                    ? promptBuilder.toolRouting(plan.history(), plan.question(), plan.tools(), toolProps.numPredict())
                    : answerPrompt;
            GenerationOptions options = withTools
                    ? new GenerationOptions(props.numCtx(), toolProps.numPredict(), props.temperature(),
                            finalRound ? null : plan.tools())
                    : new GenerationOptions(props.numCtx(), props.numPredict(), props.temperature());
            stats.estimatedTokens = prompt.estimatedTokens();
            sink.send(StreamEvents.STATUS, new Status(plan.requestId(), "GENERATING"));

            int attempt = 0;
            String clarification = null;
            while (true) {
                boolean routed = routing;
                StringBuilder answer = new StringBuilder();
                RefusalGuard guard = new RefusalGuard(text -> {
                    if (routed) {
                        return;
                    }
                    if (stats.ttftMs == null) {
                        stats.ttftMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
                    }
                    sink.send(StreamEvents.TOKEN, new Token(text));
                });
                Completion completion = chat.stream(prompt.messages(), options,
                        token -> {
                            answer.append(token);
                            guard.accept(token);
                        }, cancel);
                stats.completion = completion;
                if (completion.isCancelled()) {
                    if (completion.cancelledBy() == CancelReason.DEADLINE) {
                        sink.send(StreamEvents.ERROR, new StreamEvents.Error(ErrorReason.DEADLINE));
                    }
                    stats.outcome = completion.cancelledBy().name();
                    return;
                }
                if (routing && !completion.hasToolCalls()) {
                    clarification = usableClarification(answer.toString());
                    routing = false;
                    prompt = answerPrompt;
                    stats.estimatedTokens = prompt.estimatedTokens();
                    continue;
                }
                if (!withTools || !completion.hasToolCalls()) {
                    guard.finish();
                    stats.truncated = calibrate(plan.requestId(), prompt.estimatedTokens(),
                            completion.promptEvalCount());
                    if (guard.refused() || !guard.emittedAny()) {
                        if (clarification != null) {
                            clarify(sink, clarification, historyTrimmed(prompt, plan), stats, completion, start);
                        } else {
                            refuse(sink, historyTrimmed(prompt, plan), "MODEL", stats, completion);
                        }
                        return;
                    }
                    CitationBuilder.Result result = citationBuilder.build(answer.toString(), prompt.sourcesUsed(),
                            prompt.toolSources());
                    stats.basis = result.basis();
                    stats.inventedIds = result.inventedIds();
                    stats.refusal = false;
                    sink.send(StreamEvents.CITATIONS, new Citations(result.basis(), result.items()));
                    sink.send(StreamEvents.DONE, new Done(false, null, completion.finishReason(), stats.truncated,
                            completion.promptEvalCount(), completion.evalCount(), historyTrimmed(prompt, plan),
                            props.model(), promptHash));
                    return;
                }

                // Tokens streamed before a tool call are provisional, so the guard is deliberately not finished
                List<ToolCall> calls = completion.toolCalls();
                recordCalls(stats, calls, plan.tools());
                List<String> problems = finalRound
                        ? List.of("no more tool calls are allowed; answer now")
                        : toolPolicy.check(calls, plan.tools(), plan.toolTurns());
                if (problems.isEmpty()) {
                    int round = plan.toolTurns().size() + 1;
                    String previous = plan.toolTurns().isEmpty() ? "" : plan.toolTurns().getLast().signature();
                    String signature = signer.sign(plan.clientId(), plan.question(), plan.questionId(), round,
                            previous, calls);
                    // The caller resumes as soon as it sees done, so its slot must already be free
                    releaseOnce.run();
                    sink.send(StreamEvents.TOOL_CALL, new ToolCallEvent(round, plan.questionId(), calls, signature));
                    sink.send(StreamEvents.DONE,
                            Done.forToolCalls(completion.promptEvalCount(), completion.evalCount(),
                                    historyTrimmed(prompt, plan), props.model(), promptHash));
                    stats.outcome = "TOOL_CALL";
                    return;
                }
                if (attempt == 1 || prompt.estimatedTokens() + toolProps.numPredict() > props.numCtx()) {
                    sink.send(StreamEvents.ERROR, new StreamEvents.Error(ErrorReason.TOOL_CALL_INVALID));
                    stats.outcome = "TOOL_CALL_INVALID";
                    return;
                }
                prompt = promptBuilder.withCorrection(prompt, calls, problems);
                stats.estimatedTokens = prompt.estimatedTokens();
                stats.correctionRetry = true;
                attempt++;
            }
        } catch (RuntimeException e) {
            ErrorReason reason = cancel.reason() == CancelReason.DEADLINE ? ErrorReason.DEADLINE : errorReason(e);
            if (cancel.reason() == CancelReason.CLIENT_GONE) {
                stats.outcome = CancelReason.CLIENT_GONE.name();
            } else {
                sink.send(StreamEvents.ERROR, new StreamEvents.Error(reason));
                stats.outcome = reason.name();
            }
            if (reason == ErrorReason.INTERNAL) {
                log.error("generation requestId={} failed with {}", plan.requestId(), e.getClass().getSimpleName());
            }
        } finally {
            watchdog.cancel(false);
            heartbeat.cancel(false);
            releaseOnce.run();
            logRun(plan, stats, start);
            sink.complete();
        }
    }

    private static void recordCalls(RunStats stats, List<ToolCall> calls, List<ToolDefinition> definitions) {
        List<String> offered = definitions.stream().map(ToolDefinition::name).toList();
        stats.callsEmitted = calls.size();
        stats.callNames = calls.stream().map(ToolCall::name).filter(offered::contains).toList();
        stats.unknownCallNames = calls.size() - stats.callNames.size();
    }

    private static int resultChars(List<ToolTurn> turns) {
        return turns.stream().flatMap(turn -> turn.results().stream()).map(ToolResult::content)
                .mapToInt(content -> content == null ? 0 : content.length()).sum();
    }

    private static boolean historyTrimmed(PromptBuilder.BuiltPrompt prompt, Plan plan) {
        return prompt.historyTurnsUsed() < plan.history().size();
    }

    // Models without native tool calling (e.g. qwen2.5-coder) print the call as JSON or a code fence instead
    private static String usableClarification(String text) {
        String stripped = text.strip();
        if (stripped.isEmpty() || stripped.startsWith(RefusalGuard.MARKER)
                || stripped.startsWith("{") || stripped.startsWith("[") || stripped.startsWith("`")
                || stripped.replaceAll("\\p{Punct}+$", "").strip().equalsIgnoreCase("NONE")) {
            return null;
        }
        return stripped;
    }

    private void clarify(EventSink sink, String text, boolean historyTrimmed, RunStats stats, Completion completion,
                         long start) {
        if (stats.ttftMs == null) {
            stats.ttftMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        }
        sink.send(StreamEvents.TOKEN, new Token(text));
        sink.send(StreamEvents.CITATIONS, new Citations(CitationBasis.RETRIEVED, List.of()));
        sink.send(StreamEvents.DONE, new Done(false, null, completion.finishReason(), stats.truncated,
                completion.promptEvalCount(), completion.evalCount(), historyTrimmed, props.model(), promptHash));
        stats.outcome = "CLARIFIED";
        stats.refusal = false;
        stats.basis = CitationBasis.RETRIEVED;
    }

    private void refuse(EventSink sink, boolean historyTrimmed, String layer, RunStats stats,
                        Completion completion) {
        sink.send(StreamEvents.TOKEN, new Token(RefusalGuard.REFUSAL_TEXT));
        sink.send(StreamEvents.CITATIONS, new Citations(CitationBasis.RETRIEVED, List.of()));
        sink.send(StreamEvents.DONE, new Done(true, layer, completion == null ? "stop" : completion.finishReason(),
                false, completion == null ? null : completion.promptEvalCount(),
                completion == null ? null : completion.evalCount(), historyTrimmed, props.model(), promptHash));
        stats.outcome = "REFUSED";
        stats.refusal = true;
        stats.basis = CitationBasis.RETRIEVED;
    }

    private static List<SourceDocument> group(List<SearchHit> hits, List<DocumentCollection> owned) {
        Map<String, String> names = new LinkedHashMap<>();
        owned.forEach(c -> names.put(c.id().toString(), c.name()));
        Map<String, List<SearchHit>> byDocument = new LinkedHashMap<>();
        for (SearchHit hit : hits) {
            byDocument.computeIfAbsent(hit.collectionId() + "/" + hit.documentId(), key -> new ArrayList<>()).add(hit);
        }
        List<SourceDocument> sources = new ArrayList<>();
        for (List<SearchHit> group : byDocument.values()) {
            SearchHit first = group.getFirst();
            sources.add(new SourceDocument(sources.size() + 1, first.documentId(), first.title(), first.url(),
                    names.get(first.collectionId().toString()), first.metadata(),
                    group.stream().map(SearchHit::text).toList(), first.score()));
        }
        return sources;
    }

    private boolean calibrate(String requestId, int estimated, Integer actual) {
        if (actual == null) {
            return false;
        }
        boolean underCounted = estimated < actual;
        if (underCounted) {
            log.warn("generation requestId={} estimator under-counted: estimatedPromptTokens={} promptEvalCount={}",
                    requestId, estimated, actual);
        }
        boolean truncated = probablyTruncated(estimated, actual, props.numCtx());
        if (truncated) {
            log.warn("generation requestId={} probable truncation: estimatedPromptTokens={} promptEvalCount={}",
                    requestId, estimated, actual);
        }
        return underCounted || truncated;
    }

    // Ollama silently drops prompt overflow: the model saw far fewer tokens than we sent into a nearly full context
    static boolean probablyTruncated(int estimated, int actual, int numCtx) {
        return actual <= TRUNCATION_RATIO * estimated && estimated > NEAR_FULL_CONTEXT * numCtx;
    }

    static ErrorReason errorReason(RuntimeException e) {
        return switch (e) {
            case OllamaUnavailableException ignored -> ErrorReason.OLLAMA_UNAVAILABLE;
            case ModelNotAvailableException ignored -> ErrorReason.MODEL_NOT_AVAILABLE;
            case GenerationFailedException ignored -> ErrorReason.GENERATION_FAILED;
            case EmbeddingUnavailableException ignored -> ErrorReason.EMBEDDING_UNAVAILABLE;
            default -> ErrorReason.INTERNAL;
        };
    }

    private void logRun(Plan plan, RunStats s, long start) {
        Completion c = s.completion;
        String promptVersion = plan.tools().isEmpty() ? Prompts.PROMPT_VERSION : Prompts.TOOLS_PROMPT_VERSION;
        log.info("generation endpoint=chat outcome={} requestId={} clientId={} collectionIds={} k={} passages={} "
                        + "topScore={} promptVersion={} model={} estimatedPromptTokens={} promptEvalCount={} "
                        + "evalCount={} doneReason={} loadMs={} totalMs={} ttftMs={} refusal={} citationBasis={} "
                        + "inventedIds={} truncated={} tools={} round={} questionId={} callsEmitted={} "
                        + "callNames={} unknownCallNames={} correctionRetry={} toolResultChars={} evalMs={} promptHash={}",
                s.outcome, plan.requestId(), plan.clientId(),
                plan.collections().stream().map(DocumentCollection::id).toList(), plan.k(), s.passages,
                s.topScore == null ? null : String.format("%.3f", s.topScore), promptVersion, props.model(),
                s.estimatedTokens, c == null ? null : c.promptEvalCount(), c == null ? null : c.evalCount(),
                c == null ? null : c.finishReason(), c == null ? null : c.loadDurationMs(),
                Duration.ofNanos(System.nanoTime() - start).toMillis(), s.ttftMs, s.refusal, s.basis, s.inventedIds,
                s.truncated, plan.tools().size(), plan.toolTurns().size(), plan.questionId(), s.callsEmitted,
                s.callNames, s.unknownCallNames, s.correctionRetry, resultChars(plan.toolTurns()),
                c == null ? null : c.evalDurationMs(), promptHash);
    }
}
