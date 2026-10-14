package com.evision.ragservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evision.ragservice.config.ChatProperties;
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

class ChatServiceTest {

    private static final UUID CLIENT = UUID.randomUUID();
    private static final String MODEL = "nomic-embed-text";

    private final CollectionService collections = mock(CollectionService.class);
    private final EmbeddingPort embeddings = mock(EmbeddingPort.class);
    private final ChunkSearch chunks = mock(ChunkSearch.class);
    private final FakeChatPort chat = new FakeChatPort();
    private final RecordingEventSink sink = new RecordingEventSink();
    private final ChatProperties props = new ChatProperties("qwen3:4b", 8192, 768, 0.2, 0.3, "30m",
            Duration.ofMillis(300), Duration.ofMillis(50), 1, 4, 5, 5, 8, 0.55, 2000, 20, 4000, 1500, 2000, 3.0);
    private final GenerationGate gate = new GenerationGate(props);
    private final GenerationTimers timers = new GenerationTimers();
    private final ChatService service = new ChatService(collections, embeddings, chunks, chat, gate, timers, props);

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
}
