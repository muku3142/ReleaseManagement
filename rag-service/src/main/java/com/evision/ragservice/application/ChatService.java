package com.evision.ragservice.application;

import com.evision.ragservice.config.ChatProperties;
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
import com.evision.ragservice.domain.TooManyRequestsException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Deliberately not @Transactional: retrieval is one short query and no DB connection may be held across the stream
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final double TRUNCATION_RATIO = 0.6;
    private static final double NEAR_FULL_CONTEXT = 0.8;

    public record Command(List<String> collections, String question, List<ChatMessage> history,
                          Map<String, Object> filter, Integer k) {}

    public record Plan(String requestId, UUID clientId, List<DocumentCollection> collections, String question,
                       List<ChatMessage> history, MetadataFilter filter, int k, Cancellation cancellation) {}

    private final CollectionService collections;
    private final EmbeddingPort embeddings;
    private final ChunkSearch chunks;
    private final ChatPort chat;
    private final GenerationGate gate;
    private final GenerationTimers timers;
    private final ChatProperties props;
    private final PromptBuilder promptBuilder;
    private final CitationBuilder citationBuilder;

    public ChatService(CollectionService collections, EmbeddingPort embeddings, ChunkSearch chunks, ChatPort chat,
                       GenerationGate gate, GenerationTimers timers, ChatProperties props) {
        this.collections = collections;
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.chat = chat;
        this.gate = gate;
        this.timers = timers;
        this.props = props;
        this.promptBuilder = new PromptBuilder(new TokenEstimator(props.charsPerToken()), props.numCtx(),
                props.historyTokenBudget());
        this.citationBuilder = new CitationBuilder(props.minScore());
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

        chat.assertReady();

        if (!gate.tryAcquire(clientId)) {
            log.info("generation endpoint=chat outcome=TOO_MANY_REQUESTS clientId={}", clientId);
            throw new TooManyRequestsException();
        }
        return new Plan(UUID.randomUUID().toString(), clientId, owned, question, history, filter, k,
                new Cancellation());
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
    }

    public void run(Plan plan, EventSink sink) {
        long start = System.nanoTime();
        Cancellation cancel = plan.cancellation();
        RunStats stats = new RunStats();
        ScheduledFuture<?> watchdog = timers.after(props.deadline(), () -> cancel.cancel(CancelReason.DEADLINE));
        ScheduledFuture<?> heartbeat = timers.every(props.heartbeatInterval(), sink::heartbeat);
        try {
            sink.send(StreamEvents.STATUS, new Status(plan.requestId(), "SEARCHING"));
            String model = plan.collections().getFirst().embeddingModel();
            float[] vector = embeddings.embed(model, EmbeddingPurpose.QUERY, List.of(plan.question())).getFirst();
            List<SearchHit> hits = chunks.search(plan.collections().stream().map(DocumentCollection::id).toList(),
                    model, vector, plan.filter(), plan.k());
            stats.passages = hits.size();
            stats.topScore = hits.isEmpty() ? null : hits.getFirst().score();
            if (hits.isEmpty() || hits.getFirst().score() < props.minScore()) {
                refuse(sink, stats, "GATE", null);
                return;
            }

            List<SourceDocument> sources = group(hits, plan.collections());
            PromptBuilder.BuiltPrompt prompt = promptBuilder.chat(sources, plan.history(), plan.question(),
                    props.numPredict());
            stats.estimatedTokens = prompt.estimatedTokens();
            sink.send(StreamEvents.STATUS, new Status(plan.requestId(), "GENERATING"));

            StringBuilder answer = new StringBuilder();
            RefusalGuard guard = new RefusalGuard(text -> {
                if (stats.ttftMs == null) {
                    stats.ttftMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
                }
                sink.send(StreamEvents.TOKEN, new Token(text));
            });
            Completion completion = chat.stream(prompt.messages(),
                    new GenerationOptions(props.numCtx(), props.numPredict(), props.temperature()),
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
            guard.finish();
            stats.truncated = calibrate(plan.requestId(), prompt.estimatedTokens(), completion.promptEvalCount());
            if (guard.refused() || !guard.emittedAny()) {
                refuse(sink, stats, "MODEL", completion);
                return;
            }
            CitationBuilder.Result result = citationBuilder.build(answer.toString(), prompt.sourcesUsed());
            stats.basis = result.basis();
            stats.inventedIds = result.inventedIds();
            stats.refusal = false;
            sink.send(StreamEvents.CITATIONS, new Citations(result.basis(), result.items()));
            sink.send(StreamEvents.DONE, new Done(false, null, completion.finishReason(), stats.truncated,
                    completion.promptEvalCount(), completion.evalCount()));
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
            gate.release(plan.clientId());
            logRun(plan, stats, start);
            sink.complete();
        }
    }

    private void refuse(EventSink sink, RunStats stats, String layer, Completion completion) {
        sink.send(StreamEvents.TOKEN, new Token(RefusalGuard.REFUSAL_TEXT));
        sink.send(StreamEvents.CITATIONS, new Citations(CitationBasis.RETRIEVED, List.of()));
        sink.send(StreamEvents.DONE, new Done(true, layer, completion == null ? "stop" : completion.finishReason(),
                false, completion == null ? null : completion.promptEvalCount(),
                completion == null ? null : completion.evalCount()));
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
        boolean truncated = actual <= TRUNCATION_RATIO * estimated && estimated > NEAR_FULL_CONTEXT * props.numCtx();
        if (truncated) {
            log.warn("generation requestId={} probable truncation: estimatedPromptTokens={} promptEvalCount={}",
                    requestId, estimated, actual);
        }
        return underCounted || truncated;
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
        log.info("generation endpoint=chat outcome={} requestId={} clientId={} collectionIds={} k={} passages={} "
                        + "topScore={} promptVersion={} model={} estimatedPromptTokens={} promptEvalCount={} "
                        + "evalCount={} doneReason={} loadMs={} totalMs={} ttftMs={} refusal={} citationBasis={} "
                        + "inventedIds={} truncated={}",
                s.outcome, plan.requestId(), plan.clientId(),
                plan.collections().stream().map(DocumentCollection::id).toList(), plan.k(), s.passages,
                s.topScore == null ? null : String.format("%.3f", s.topScore), Prompts.PROMPT_VERSION, props.model(),
                s.estimatedTokens, c == null ? null : c.promptEvalCount(), c == null ? null : c.evalCount(),
                c == null ? null : c.finishReason(), c == null ? null : c.loadDurationMs(),
                Duration.ofNanos(System.nanoTime() - start).toMillis(), s.ttftMs, s.refusal, s.basis, s.inventedIds,
                s.truncated);
    }
}
