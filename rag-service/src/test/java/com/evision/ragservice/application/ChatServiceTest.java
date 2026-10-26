package com.evision.ragservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.evision.ragservice.config.ChatProperties;
import com.evision.ragservice.config.ToolProperties;
import com.evision.ragservice.domain.CitationKind;
import com.evision.ragservice.domain.ToolCall;
import com.evision.ragservice.domain.ToolDefinition;
import com.evision.ragservice.domain.ToolResult;
import com.evision.ragservice.domain.ToolResultTooLargeException;
import com.evision.ragservice.domain.ToolSource;
import com.evision.ragservice.domain.ToolTurn;
import com.evision.ragservice.domain.ToolTurnRejectedException;
import com.evision.ragservice.domain.ChatMessage;
import com.evision.ragservice.domain.ChatMessage.Role;
import com.evision.ragservice.domain.CitationBasis;
import com.evision.ragservice.domain.CollectionNotFoundException;
import com.evision.ragservice.domain.Completion;
import com.evision.ragservice.domain.DocumentCollection;
import com.evision.ragservice.domain.EmbeddingPurpose;
import com.evision.ragservice.domain.EmbeddingUnavailableException;
import com.evision.ragservice.domain.ErrorReason;
import com.evision.ragservice.domain.GenerationFailedException;
import com.evision.ragservice.domain.InvalidContentException;
import com.evision.ragservice.domain.MetadataRules;
import com.evision.ragservice.domain.MixedEmbeddingModelsException;
import com.evision.ragservice.domain.ModelNotAvailableException;
import com.evision.ragservice.domain.OllamaUnavailableException;
import com.evision.ragservice.domain.SearchHit;
import com.evision.ragservice.domain.StreamEvents;
import com.evision.ragservice.domain.TooManyRequestsException;
import com.evision.ragservice.ports.ChunkSearch;
import com.evision.ragservice.ports.EmbeddingPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ChatServiceTest {

    private static final UUID CLIENT = UUID.randomUUID();
    private static final String MODEL = "nomic-embed-text";
    private static final String SECRET = "test-tool-signing-secret-0123456789-abcdef";
    private static final String QUESTION = "What shipped?";

    private final CollectionService collections = mock(CollectionService.class);
    private final EmbeddingPort embeddings = mock(EmbeddingPort.class);
    private final ChunkSearch chunks = mock(ChunkSearch.class);
    private final FakeChatPort chat = new FakeChatPort();
    private final RecordingEventSink sink = new RecordingEventSink();
    private final ChatProperties props = new ChatProperties("qwen3:4b", 8192, 768, 0.2, 0.3, "30m",
            Duration.ofMillis(300), Duration.ofMillis(50), 1, 4, 5, 5, 8, 0.55, 2000, 20, 4000, 1500, 2000, 3.0);
    private final GenerationGate gate = new GenerationGate(props);
    private final GenerationTimers timers = new GenerationTimers();
    private final ToolProperties toolProps = new ToolProperties(SECRET, 4, 4, 5, 1500, 8000, 768);
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final ToolTurnSigner signer = new ToolTurnSigner(SECRET, mapper);
    private final ChatService service = new ChatService(collections, embeddings, chunks, chat, gate, timers, props,
            toolProps, mapper, PromptRules.defaults());

    private final DocumentCollection a1 = collection("a1", MODEL);
    private final DocumentCollection a2 = collection("a2", MODEL);

    private static DocumentCollection collection(String name, String model) {
        return new DocumentCollection(UUID.randomUUID(), CLIENT, name, model, Instant.now());
    }

    @AfterEach
    void tearDown() {
        timers.destroy();
    }

    private ChatService.Command command() {
        return new ChatService.Command(List.of("a1", "a2"), "What shipped?", null, null, null);
    }

    private ChatService.Command command(String question, List<ChatMessage> history, Integer k) {
        return new ChatService.Command(List.of("a1", "a2"), question, history, null, k);
    }

    private void owned() {
        when(collections.requireAll(eq(CLIENT), anyList())).thenReturn(List.of(a1, a2));
    }

    private SearchHit hit(DocumentCollection c, String documentId, double score) {
        return new SearchHit(c.id(), documentId, "Title " + documentId, "https://x/" + documentId,
                Map.of("team", "core"), 0, "passage of " + documentId, score);
    }

    private void retrieval(SearchHit... hits) {
        owned();
        when(embeddings.embed(eq(MODEL), eq(EmbeddingPurpose.QUERY), anyList())).thenReturn(List.of(new float[] {1f}));
        when(chunks.search(anyList(), eq(MODEL), any(), any(), anyInt())).thenReturn(List.of(hits));
    }

    private ChatService.Plan prepared() {
        return service.prepare(CLIENT, command());
    }

    @Test
    void unknownCollectionIs404BeforeAnythingElse() {
        when(collections.requireAll(eq(CLIENT), anyList())).thenThrow(new CollectionNotFoundException("a1"));

        assertThatThrownBy(() -> service.prepare(CLIENT, command())).isInstanceOf(CollectionNotFoundException.class);
        assertThat(chat.readyCalls).isZero();
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void mixedModelsAreRejectedOnlyAfterOwnership() {
        when(collections.requireAll(eq(CLIENT), anyList())).thenReturn(List.of(a1, collection("a2", "alt")));

        assertThatThrownBy(this::prepared).isInstanceOf(MixedEmbeddingModelsException.class);
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void invalidShapesAreRejected() {
        owned();
        List<ChatMessage> tooMany = java.util.Collections.nCopies(21, new ChatMessage(Role.USER, "x"));

        assertThatThrownBy(() -> service.prepare(CLIENT, command("q", null, 9))).isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> service.prepare(CLIENT, command("q", null, 0))).isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> service.prepare(CLIENT, command("q", tooMany, null)))
                .isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> service.prepare(CLIENT,
                command("q", List.of(new ChatMessage(Role.USER, "x".repeat(4001))), null)))
                .isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> service.prepare(CLIENT,
                command("q", List.of(new ChatMessage(Role.SYSTEM, "x")), null)))
                .isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> service.prepare(CLIENT, command("x".repeat(2001), null, null)))
                .isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> service.prepare(CLIENT, command("a\u0000b", null, null)))
                .isInstanceOf(InvalidContentException.class);
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void readinessFailurePropagatesAndLeavesGateEmpty() {
        owned();
        chat.readyFailure = new OllamaUnavailableException("down", null);
        assertThatThrownBy(this::prepared).isInstanceOf(OllamaUnavailableException.class);
        chat.readyFailure = new ModelNotAvailableException("qwen3:4b");
        assertThatThrownBy(this::prepared).isInstanceOf(ModelNotAvailableException.class);
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void secondPlanForTheSameClientIsRefused() {
        owned();
        prepared();

        assertThatThrownBy(this::prepared).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void happyPathStreamsStatusTokensCitationsDone() {
        retrieval(hit(a1, "PROJ-1", 0.9), hit(a2, "PROJ-2", 0.8));
        chat.tokens = List.of("Release 4.2.0 ", "ships PROJ-1 ", "[1].");

        service.run(prepared(), sink);

        assertThat(sink.events()).containsExactly("status", "status", "token", "token", "token", "citations", "done");
        var citations = sink.payload(StreamEvents.CITATIONS, StreamEvents.Citations.class);
        assertThat(citations.basis()).isEqualTo(CitationBasis.MARKED);
        assertThat(citations.items()).singleElement().satisfies(c -> {
            assertThat(c.n()).isEqualTo(1);
            assertThat(c.documentId()).isEqualTo("PROJ-1");
            assertThat(c.title()).isEqualTo("Title PROJ-1");
            assertThat(c.url()).isEqualTo("https://x/PROJ-1");
            assertThat(c.metadata()).containsEntry("team", "core");
            assertThat(c.collection()).isEqualTo("a1");
        });
        var done = sink.payload(StreamEvents.DONE, StreamEvents.Done.class);
        assertThat(done.refusal()).isFalse();
        assertThat(done.refusalLayer()).isNull();
        assertThat(done.finishReason()).isEqualTo("stop");
        assertThat(sink.completed).isTrue();
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void doneCarriesModelPromptHashAndKeptHistoryIsNotTrimmed() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokens = List.of("ok [1]");
        var history = List.of(new ChatMessage(Role.USER, "h1"), new ChatMessage(Role.ASSISTANT, "h2"),
                new ChatMessage(Role.USER, "h3"));

        service.run(service.prepare(CLIENT, command(QUESTION, history, null)), sink);

        var done = sink.payload(StreamEvents.DONE, StreamEvents.Done.class);
        assertThat(done.historyTrimmed()).isFalse();
        assertThat(done.model()).isEqualTo(props.model());
        assertThat(done.promptHash()).isEqualTo(PromptRules.defaults().hash());
    }

    @Test
    void doneReportsTrimmedHistoryWhenTheBudgetKeepsFewerTurns() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokens = List.of("ok [1]");
        var history = List.of(new ChatMessage(Role.USER, "a".repeat(4000)),
                new ChatMessage(Role.ASSISTANT, "b".repeat(4000)), new ChatMessage(Role.USER, "c".repeat(4000)));

        service.run(service.prepare(CLIENT, command(QUESTION, history, null)), sink);

        assertThat(sink.payload(StreamEvents.DONE, StreamEvents.Done.class).historyTrimmed()).isTrue();
    }

    @Test
    void gateAndModelRefusalsCarryModelAndPromptHash() {
        retrieval(hit(a1, "PROJ-1", 0.1));
        service.run(prepared(), sink);
        var gateDone = sink.payload(StreamEvents.DONE, StreamEvents.Done.class);
        assertThat(gateDone.historyTrimmed()).isFalse();
        assertThat(gateDone.model()).isEqualTo(props.model());
        assertThat(gateDone.promptHash()).isEqualTo(PromptRules.defaults().hash());

        var modelSink = new RecordingEventSink();
        gate.release(CLIENT);
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokens = List.of("NO_", "ANSWER");
        service.run(prepared(), modelSink);
        var modelDone = modelSink.payload(StreamEvents.DONE, StreamEvents.Done.class);
        assertThat(modelDone.refusalLayer()).isEqualTo("MODEL");
        assertThat(modelDone.model()).isEqualTo(props.model());
        assertThat(modelDone.promptHash()).isEqualTo(PromptRules.defaults().hash());
    }

    @Test
    void searchGetsBothCollectionsTheParsedFilterAndDefaultK() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokens = List.of("ok");
        var cmd = new ChatService.Command(List.of("a1", "a2"), "q", null, Map.of("team", "core"), null);

        service.run(service.prepare(CLIENT, cmd), sink);

        verify(chunks).search(eq(List.of(a1.id(), a2.id())), eq(MODEL), any(),
                eq(MetadataRules.parseFilter(Map.of("team", "core"))), eq(5));
    }


    @Test
    void lowScoreRefusesWithoutCallingTheModel() {
        retrieval(hit(a1, "PROJ-1", 0.4));

        service.run(prepared(), sink);

        assertThat(chat.streamCalls).isEmpty();
        assertThat(sink.events()).containsExactly("status", "token", "citations", "done");
        assertThat(sink.payload(StreamEvents.TOKEN, StreamEvents.Token.class).text())
                .isEqualTo(RefusalGuard.REFUSAL_TEXT);
        var citations = sink.payload(StreamEvents.CITATIONS, StreamEvents.Citations.class);
        assertThat(citations.basis()).isEqualTo(CitationBasis.RETRIEVED);
        assertThat(citations.items()).isEmpty();
        var done = sink.payload(StreamEvents.DONE, StreamEvents.Done.class);
        assertThat(done.refusal()).isTrue();
        assertThat(done.refusalLayer()).isEqualTo("GATE");
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void noHitsRefusesAtTheGate() {
        retrieval();

        service.run(prepared(), sink);

        assertThat(chat.streamCalls).isEmpty();
        assertThat(sink.payload(StreamEvents.DONE, StreamEvents.Done.class).refusalLayer()).isEqualTo("GATE");
    }

    @Test
    void modelNoAnswerRefusesWithoutLeakingModelText() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokens = List.of("NO_", "ANSWER");

        service.run(prepared(), sink);

        assertThat(sink.events()).containsExactly("status", "status", "token", "citations", "done");
        assertThat(sink.payload(StreamEvents.TOKEN, StreamEvents.Token.class).text())
                .isEqualTo(RefusalGuard.REFUSAL_TEXT);
        assertThat(sink.payload(StreamEvents.CITATIONS, StreamEvents.Citations.class).items()).isEmpty();
        var done = sink.payload(StreamEvents.DONE, StreamEvents.Done.class);
        assertThat(done.refusal()).isTrue();
        assertThat(done.refusalLayer()).isEqualTo("MODEL");
    }

    @Test
    void answerWithoutMarkersFallsBackToRetrievedCitations() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokens = List.of("It shipped.");

        service.run(prepared(), sink);

        var citations = sink.payload(StreamEvents.CITATIONS, StreamEvents.Citations.class);
        assertThat(citations.basis()).isEqualTo(CitationBasis.RETRIEVED);
        assertThat(citations.items()).hasSize(1);
    }

    @Test
    void deadlineCancelsTheStreamAndEndsWithDeadlineError() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.blockUntilCancelled = true;

        service.run(prepared(), sink);

        assertThat(sink.events()).containsExactly("status", "status", "error");
        assertThat(sink.payload(StreamEvents.ERROR, StreamEvents.Error.class).reason()).isEqualTo(ErrorReason.DEADLINE);
        assertThat(sink.completed).isTrue();
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void slowGenerationEmitsHeartbeats() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.delay = Duration.ofMillis(200);
        chat.tokens = List.of("ok");

        service.run(prepared(), sink);

        assertThat(sink.heartbeats.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void generationFailureAfterATokenKeepsTheTokenThenErrors() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokens = List.of("partial ", "more");
        chat.failAfterFirstToken = new GenerationFailedException("boom");

        service.run(prepared(), sink);

        assertThat(sink.events()).containsExactly("status", "status", "token", "error");
        assertThat(sink.payload(StreamEvents.ERROR, StreamEvents.Error.class).reason())
                .isEqualTo(ErrorReason.GENERATION_FAILED);
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void embeddingOutageMapsToEmbeddingUnavailable() {
        owned();
        when(embeddings.embed(any(), any(), anyList())).thenThrow(new EmbeddingUnavailableException("down", null));

        service.run(prepared(), sink);

        assertThat(sink.payload(StreamEvents.ERROR, StreamEvents.Error.class).reason())
                .isEqualTo(ErrorReason.EMBEDDING_UNAVAILABLE);
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void unexpectedExceptionMapsToInternal() {
        owned();
        when(embeddings.embed(any(), any(), anyList())).thenThrow(new IllegalStateException("secret detail"));

        service.run(prepared(), sink);

        assertThat(sink.payload(StreamEvents.ERROR, StreamEvents.Error.class).reason()).isEqualTo(ErrorReason.INTERNAL);
        assertThat(sink.completed).isTrue();
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void clientGoneEndsWithoutAnErrorEvent() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokens = List.of("ok");
        ChatService.Plan plan = prepared();
        sink.goneAfter(1, plan.cancellation());

        service.run(plan, sink);

        assertThat(sink.events()).doesNotContain("error");
        assertThat(sink.completed).isTrue();
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void cancelledCompletionFromClientGoneSendsNothingFurther() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.completion = Completion.cancelled(com.evision.ragservice.domain.CancelReason.CLIENT_GONE);

        service.run(prepared(), sink);

        assertThat(sink.events()).containsExactly("status", "status");
        assertThat(gate.anyInFlight()).isFalse();
    }

    // ---- tool protocol ----

    private static final String QID = "q-fixed-id";

    private static ToolDefinition statusTool() {
        return new ToolDefinition("get_release_status", "Status of a release",
                Map.of("type", "object", "properties", Map.of("version", Map.of("type", "string")),
                        "required", List.of("version")));
    }

    private static ToolDefinition deploymentsTool() {
        return new ToolDefinition("get_deployments", "Deployments of a release",
                Map.of("type", "object", "properties", Map.of("version", Map.of("type", "string"),
                        "environment", Map.of("type", "string", "enum", List.of("DEV", "UAT", "PROD"))),
                        "required", List.of("version")));
    }

    private static List<ToolDefinition> tools() {
        return List.of(statusTool(), deploymentsTool());
    }

    private ChatService.Command toolCommand(List<ToolDefinition> tools, List<ToolTurn> turns) {
        return new ChatService.Command(List.of("a1", "a2"), QUESTION, null, null, null, tools, turns);
    }

    private ChatService.Plan toolPrepared(List<ToolTurn> turns) {
        return service.prepare(CLIENT, toolCommand(tools(), turns));
    }

    private static ToolCall statusCall(String id, String version) {
        return new ToolCall(id, "get_release_status", Map.of("version", version));
    }

    private static Completion toolCompletion(ToolCall... calls) {
        return new Completion("tool_calls", 100, 10, 5L, 50L, null, 20L, List.of(calls));
    }

    private List<ToolTurn> chain(int rounds, int resultChars) {
        List<ToolTurn> turns = new java.util.ArrayList<>();
        String previous = "";
        for (int i = 1; i <= rounds; i++) {
            List<ToolCall> calls = List.of(statusCall("call_" + i, "1." + i));
            String signature = signer.sign(CLIENT, QUESTION, QID, i, previous, calls);
            turns.add(new ToolTurn(i, QID, calls, signature,
                    List.of(new ToolResult("call_" + i, "x".repeat(resultChars), false, null))));
            previous = signature;
        }
        return turns;
    }

    private List<ToolTurn> chain(int rounds) {
        return chain(rounds, 16);
    }

    private List<ToolTurn> resumeFrom(StreamEvents.ToolCallEvent event, ToolResult... results) {
        return List.of(new ToolTurn(event.round(), event.questionId(), event.calls(), event.signature(),
                List.of(results)));
    }

    @Test
    void noToolsKeepsPhase10Behaviour() {
        owned();

        ChatService.Plan plan = prepared();

        assertThat(chat.toolsReadyCalls).isZero();
        assertThat(plan.questionId()).isEqualTo(plan.requestId());
        assertThat(plan.tools()).isEmpty();
        assertThat(plan.toolTurns()).isEmpty();
    }

    @Test
    void nestedObjectPropertyIsRejectedBeforeOllama() {
        owned();
        ToolDefinition nested = new ToolDefinition("t", "d", Map.of("type", "object",
                "properties", Map.of("p", Map.of("type", "object"))));

        assertThatThrownBy(() -> service.prepare(CLIENT, toolCommand(List.of(nested), List.of())))
                .isInstanceOf(InvalidContentException.class);
        assertThat(chat.readyCalls).isZero();
    }

    @Test
    void turnsWithoutToolsAreRejected() {
        owned();

        assertThatThrownBy(() -> service.prepare(CLIENT, toolCommand(List.of(), chain(1))))
                .isInstanceOf(ToolTurnRejectedException.class);
        assertThat(chat.readyCalls).isZero();
    }

    @Test
    void aTamperedTurnCostsNoOllamaRequestAndNoSlot() {
        owned();
        ToolTurn good = chain(1).getFirst();
        ToolTurn forged = new ToolTurn(1, QID, List.of(statusCall("call_1", "9.9.9")), good.signature(), good.results());

        assertThatThrownBy(() -> toolPrepared(List.of(forged))).isInstanceOf(ToolTurnRejectedException.class);
        assertThat(chat.readyCalls).isZero();
        assertThat(chat.toolsReadyCalls).isZero();
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void moreRoundsThanTheCapAreRejected() {
        owned();

        assertThatThrownBy(() -> toolPrepared(chain(5))).isInstanceOf(ToolTurnRejectedException.class);
        assertThat(chat.readyCalls).isZero();
    }

    @Test
    void oversizedAndNonFittingResultsAre413BeforeOllama() {
        owned();

        assertThatThrownBy(() -> toolPrepared(chain(1, 8001))).isInstanceOf(ToolResultTooLargeException.class);
        assertThatThrownBy(() -> toolPrepared(chain(4, 8000))).isInstanceOf(ToolResultTooLargeException.class);
        assertThat(chat.readyCalls).isZero();
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void toolsNotReadyPropagatesAndLeavesGateEmpty() {
        owned();
        chat.toolsReadyFailure = new ModelNotAvailableException("m");

        assertThatThrownBy(() -> toolPrepared(List.of())).isInstanceOf(ModelNotAvailableException.class);
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void validResumeCarriesToolsAndTurnsAndHoldsTheGate() {
        owned();

        ChatService.Plan plan = toolPrepared(chain(2));

        assertThat(plan.tools()).hasSize(2);
        assertThat(plan.toolTurns()).hasSize(2);
        assertThat(plan.questionId()).isEqualTo(QID);
        assertThat(gate.anyInFlight()).isTrue();
    }

    @Test
    void firstRoundEmitsASignedToolCallAndNothingElse() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.completions = List.of(toolCompletion(statusCall("call_1", "2.4.0"),
                new ToolCall("call_2", "get_deployments", Map.of("version", "2.4.0", "environment", "UAT"))));
        ChatService.Plan plan = toolPrepared(List.of());

        service.run(plan, sink);

        assertThat(sink.events()).containsExactly("status", "status", "tool_call", "done");
        var event = sink.payload(StreamEvents.TOOL_CALL, StreamEvents.ToolCallEvent.class);
        assertThat(event.round()).isEqualTo(1);
        assertThat(event.questionId()).isEqualTo(plan.requestId());
        assertThat(event.calls()).hasSize(2);
        signer.verifyChain(CLIENT, QUESTION, resumeFrom(event, new ToolResult("call_1", "{}", false, null)));
        var toolDone = sink.payload(StreamEvents.DONE, StreamEvents.Done.class);
        assertThat(toolDone.finishReason()).isEqualTo("tool_calls");
        assertThat(toolDone.model()).isEqualTo(props.model());
        assertThat(toolDone.promptHash()).isEqualTo(PromptRules.defaults().hash());
        assertThat(toolDone.historyTrimmed()).isFalse();
        assertThat(chat.lastOptions.tools()).isNotNull();
        assertThat(chat.streamCalls).hasSize(1);
        assertThat(chat.lastMessages.getFirst().content()).isEqualTo(Prompts.TOOL_ROUTING);
        assertThat(chat.lastMessages.getLast().content()).doesNotContain("SOURCES", "<<<SRC");
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void routingTextIsDiscardedAndTheAnswerRequestKeepsSourcesToolsAndRules() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokenScript = List.of(List.of("I guess UAT"), List.of("Release 4.2.0 [1]."));

        service.run(toolPrepared(List.of()), sink);

        assertThat(chat.streamCalls).hasSize(2);
        assertThat(chat.streamCalls.get(0).getFirst().content()).isEqualTo(Prompts.TOOL_ROUTING);
        List<ChatMessage> answer = chat.streamCalls.get(1);
        assertThat(answer.getFirst().content()).contains("NO_ANSWER", "TOOLS:");
        assertThat(answer.getLast().content()).contains("<<<SRC");
        assertThat(chat.lastOptions.tools()).isNotNull();
        assertThat(sink.sent.stream().filter(s -> s.event().equals(StreamEvents.TOKEN))
                .map(s -> ((StreamEvents.Token) s.payload()).text())).containsExactly("Release 4.2.0 [1].");
        assertThat(sink.events()).containsExactly("status", "status", "token", "citations", "done");
    }

    @Test
    void routingClarificationIsShownWhenTheAnswerRoundRefuses() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokenScript = List.of(List.of("  Which release do you mean?  "), List.of("NO_ANSWER"));

        service.run(toolPrepared(List.of()), sink);

        assertThat(sink.events()).containsExactly("status", "status", "token", "citations", "done");
        assertThat(tokens()).containsExactly("Which release do you mean?");
        assertThat(sink.payload(StreamEvents.CITATIONS, StreamEvents.Citations.class).items()).isEmpty();
        StreamEvents.Done done = sink.payload(StreamEvents.DONE, StreamEvents.Done.class);
        assertThat(done.refusal()).isFalse();
        assertThat(done.refusalLayer()).isNull();
    }

    @Test
    void routingClarificationIsShownWhenTheAnswerRoundIsEmpty() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokenScript = List.of(List.of("Which release do you mean?"), List.of());

        service.run(toolPrepared(List.of()), sink);

        assertThat(tokens()).containsExactly("Which release do you mean?");
        assertThat(sink.payload(StreamEvents.DONE, StreamEvents.Done.class).refusal()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "none.", " None! ", "   ", "NO_ANSWER",
            "{\"name\": \"tickets\", \"arguments\": {\"release\": \"Release\"}}",
            "[{\"name\": \"tickets\"}]", "```json\n{\"name\": \"tickets\"}\n```"})
    void unusableRoutingTextFallsBackToTheNormalRefusal(String routingText) {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokenScript = List.of(List.of(routingText), List.of("NO_ANSWER"));

        service.run(toolPrepared(List.of()), sink);

        assertThat(tokens()).containsExactly(RefusalGuard.REFUSAL_TEXT);
        StreamEvents.Done done = sink.payload(StreamEvents.DONE, StreamEvents.Done.class);
        assertThat(done.refusal()).isTrue();
        assertThat(done.refusalLayer()).isEqualTo("MODEL");
    }

    private List<String> tokens() {
        return sink.sent.stream().filter(s -> s.event().equals(StreamEvents.TOKEN))
                .map(s -> ((StreamEvents.Token) s.payload()).text()).toList();
    }

    @Test
    void routingCallsAreEmittedWithoutRunningTheAnswerRequest() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokenScript = List.of(List.of("thinking aloud"));
        chat.completions = List.of(toolCompletion(statusCall("call_1", "2.4.0")));

        service.run(toolPrepared(List.of()), sink);

        assertThat(chat.streamCalls).hasSize(1);
        assertThat(sink.events()).containsExactly("status", "status", "tool_call", "done");
        assertThat(sink.payload(StreamEvents.TOOL_CALL, StreamEvents.ToolCallEvent.class).round()).isEqualTo(1);
    }

    @Test
    void roundsAfterToolResultsSkipRoutingAndUseTheAnswerFrame() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokens = List.of("Release 1.1 [1].");

        service.run(toolPrepared(chain(1)), sink);

        assertThat(chat.streamCalls).hasSize(1);
        assertThat(chat.lastMessages.getFirst().content()).contains("NO_ANSWER").isNotEqualTo(Prompts.TOOL_ROUTING);
    }

    @Test
    void theSlotIsFreeWhenTheToolCallIsSentSoAnImmediateResumeIsNeverRefused() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.completions = List.of(toolCompletion(statusCall("call_1", "2.4.0")));
        boolean[] inFlightAtToolCall = new boolean[1];
        boolean[] resumeAcquired = new boolean[1];
        RecordingEventSink resuming = new RecordingEventSink() {
            @Override
            public boolean send(String event, Object payload) {
                if (event.equals(StreamEvents.TOOL_CALL)) {
                    inFlightAtToolCall[0] = gate.anyInFlight();
                    resumeAcquired[0] = gate.tryAcquire(CLIENT);
                }
                return super.send(event, payload);
            }
        };

        service.run(toolPrepared(List.of()), resuming);

        assertThat(inFlightAtToolCall[0]).isFalse();
        assertThat(resumeAcquired[0]).isTrue();
        assertThat(gate.anyInFlight()).isTrue();
        gate.release(CLIENT);
    }

    @Test
    void roundTripCitesTheToolSourceAndRetrievesAgain() {
        retrieval(hit(a1, "PROJ-1", 0.9), hit(a2, "PROJ-2", 0.8));
        chat.completions = List.of(toolCompletion(statusCall("call_1", "2.4.0"),
                new ToolCall("call_2", "get_deployments", Map.of("version", "2.4.0"))));
        service.run(toolPrepared(List.of()), sink);
        var event = sink.payload(StreamEvents.TOOL_CALL, StreamEvents.ToolCallEvent.class);

        RecordingEventSink second = new RecordingEventSink();
        chat.completions = List.of();
        chat.tokens = List.of("Release 2.4.0 is in UAT [3].");
        service.run(toolPrepared(resumeFrom(event,
                new ToolResult("call_1", "{\"stage\":\"UAT\"}", false,
                        new ToolSource("rel-2.4.0", "Release 2.4.0", "https://jira/rel-2.4.0")),
                new ToolResult("call_2", "{\"error\":\"unavailable\"}", true, null))), second);

        var citations = second.payload(StreamEvents.CITATIONS, StreamEvents.Citations.class);
        assertThat(citations.basis()).isEqualTo(CitationBasis.MARKED);
        assertThat(citations.items()).singleElement().satisfies(c -> {
            assertThat(c.kind()).isEqualTo(CitationKind.TOOL);
            assertThat(c.documentId()).isEqualTo("rel-2.4.0");
        });
        assertThat(second.payload(StreamEvents.DONE, StreamEvents.Done.class).finishReason()).isEqualTo("stop");
        assertThat(chat.lastMessages.stream().filter(m -> m.role() == Role.TOOL)).hasSize(2);
        verify(embeddings, times(2)).embed(eq(MODEL), eq(EmbeddingPurpose.QUERY), anyList());
    }

    @Test
    void toolRequestsSkipTheScoreGateButWithoutToolsItStillRefuses() {
        retrieval();
        chat.tokens = List.of("ok");

        service.run(toolPrepared(List.of()), sink);

        assertThat(chat.streamCalls).hasSize(2);
        assertThat(sink.payload(StreamEvents.DONE, StreamEvents.Done.class).refusalLayer()).isNull();

        RecordingEventSink plain = new RecordingEventSink();
        service.run(prepared(), plain);
        assertThat(plain.payload(StreamEvents.DONE, StreamEvents.Done.class).refusalLayer()).isEqualTo("GATE");
        assertThat(chat.streamCalls).hasSize(2);
    }

    @Test
    void modelNoAnswerAfterAToolRoundIsAModelRefusal() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.tokens = List.of("NO_", "ANSWER");

        service.run(toolPrepared(chain(1)), sink);

        assertThat(sink.payload(StreamEvents.DONE, StreamEvents.Done.class).refusalLayer()).isEqualTo("MODEL");
    }

    @Test
    void atTheCapNoToolsAreOfferedAndAnotherCallIsAnError() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.completions = List.of(toolCompletion(statusCall("call_9", "7.7.7")));

        service.run(toolPrepared(chain(4)), sink);

        assertThat(chat.lastOptions.tools()).isNull();
        assertThat(chat.lastMessages.getFirst().content()).endsWith(Prompts.FINAL_ROUND_RULE);
        assertThat(sink.events()).containsExactly("status", "status", "error");
        assertThat(sink.payload(StreamEvents.ERROR, StreamEvents.Error.class).reason())
                .isEqualTo(ErrorReason.TOOL_CALL_INVALID);
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void anUnknownToolGetsOneCorrectionThenAnswers() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.completions = List.of(toolCompletion(new ToolCall("c1", "drop_tables", Map.of())),
                new Completion("stop", 100, 10, 5L, 50L, null));
        chat.tokenScript = List.of(List.of(), List.of("Release 4.2.0 [1]."));

        service.run(toolPrepared(List.of()), sink);

        assertThat(chat.streamCalls).hasSize(3);
        assertThat(chat.streamCalls.get(1).stream().filter(m -> m.role() == Role.TOOL))
                .singleElement().satisfies(m -> assertThat(m.content()).contains("invalid tool call"));
        assertThat(sink.events()).doesNotContain("tool_call");
        assertThat(sink.payload(StreamEvents.DONE, StreamEvents.Done.class).finishReason()).isEqualTo("stop");
    }

    @Test
    void twoInvalidAttemptsEndWithToolCallInvalid() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.completions = List.of(toolCompletion(new ToolCall("c1", "drop_tables", Map.of())));

        service.run(toolPrepared(List.of()), sink);

        assertThat(chat.streamCalls).hasSize(2);
        assertThat(sink.events()).doesNotContain("done", "tool_call");
        assertThat(sink.payload(StreamEvents.ERROR, StreamEvents.Error.class).reason())
                .isEqualTo(ErrorReason.TOOL_CALL_INVALID);
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void theCorrectionRetryIsNotARound() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.completions = List.of(toolCompletion(new ToolCall("c1", "drop_tables", Map.of())),
                toolCompletion(statusCall("call_1", "2.4.0")));

        service.run(toolPrepared(List.of()), sink);

        assertThat(sink.payload(StreamEvents.TOOL_CALL, StreamEvents.ToolCallEvent.class).round()).isEqualTo(1);
    }

    @Test
    void repeatingAnAnsweredCallIsCorrected() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.completions = List.of(toolCompletion(statusCall("again", "1.1")),
                new Completion("stop", 100, 10, 5L, 50L, null));
        chat.tokenScript = List.of(List.of(), List.of("Release 1.1 [1]."));

        service.run(toolPrepared(chain(1)), sink);

        assertThat(chat.streamCalls).hasSize(2);
        assertThat(sink.events()).doesNotContain("tool_call");
    }

    @Test
    void aBlockedToolRequestHitsTheDeadline() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        chat.blockUntilCancelled = true;

        service.run(toolPrepared(List.of()), sink);

        assertThat(sink.payload(StreamEvents.ERROR, StreamEvents.Error.class).reason()).isEqualTo(ErrorReason.DEADLINE);
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void theLogLineCarriesToolMetadataAndNoContent() {
        retrieval(hit(a1, "PROJ-1", 0.9));
        String canary = "CANARY-9f3a";
        List<ToolCall> answered = List.of(statusCall("call_1", canary));
        String signature = signer.sign(CLIENT, QUESTION, QID, 1, "", answered);
        List<ToolTurn> turns = List.of(new ToolTurn(1, QID, answered, signature,
                List.of(new ToolResult("call_1", canary + " result", false,
                        new ToolSource("src", canary + " title", "https://x")))));
        chat.completions = List.of(toolCompletion(statusCall("call_2", "3.0.0"),
                new ToolCall("call_3", "get_deployments", Map.of("version", "3.0.0"))));
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ChatService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.run(toolPrepared(turns), sink);
        } finally {
            logger.detachAppender(appender);
        }

        String emittedSignature = sink.payload(StreamEvents.TOOL_CALL, StreamEvents.ToolCallEvent.class).signature();
        String line = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.startsWith("generation endpoint=chat")).findFirst().orElseThrow();
        assertThat(line).contains("promptVersion=p11-v2", "round=1", "questionId=" + QID, "callsEmitted=2",
                "callNames=[get_release_status, get_deployments]", "unknownCallNames=0", "evalMs=20");
        assertThat(line).endsWith("promptHash=" + PromptRules.defaults().hash());
        assertThat(appender.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                .doesNotContain(canary).doesNotContain(emittedSignature).doesNotContain(signature));
    }
}
