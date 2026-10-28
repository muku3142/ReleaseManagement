package com.evision.releasemgmt.application.assistant;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.application.assistant.tools.AssistantTool;
import com.evision.releasemgmt.config.AssistantProperties;
import com.evision.releasemgmt.domain.assistant.ClientGoneException;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import java.time.Duration;
import java.time.Instant;
import com.evision.releasemgmt.application.assistant.tools.ToolArgumentException;
import com.evision.releasemgmt.application.assistant.tools.ToolOutput;
import com.evision.releasemgmt.application.assistant.tools.ToolRegistry;
import com.evision.releasemgmt.domain.assistant.AssistantAnswer;
import com.evision.releasemgmt.domain.assistant.ChatMessage;
import com.evision.releasemgmt.domain.assistant.ChatOutcome;
import com.evision.releasemgmt.domain.assistant.ChatRequest;
import com.evision.releasemgmt.domain.assistant.FailureReason;
import com.evision.releasemgmt.domain.assistant.IndexCollection;
import com.evision.releasemgmt.domain.assistant.SignedTurn;
import com.evision.releasemgmt.domain.assistant.Source;
import com.evision.releasemgmt.domain.assistant.ToolCall;
import com.evision.releasemgmt.ports.RagChatPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantServiceTest {

    private static final int MAX_ROUNDS = 4;
    private static final int MAX_CHARS = 6000;
    private static final String TOOL_ERROR_ANSWER = "I couldn't look that up: {error}.";
    private static final String QUESTION = "what ships in 2.4 SECRET-QUESTION";
    private static final List<ChatMessage> HISTORY = List.of(new ChatMessage("user", "hi"));

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final List<ChatRequest> requests = new ArrayList<>();
    private final Queue<ChatOutcome> script = new ArrayDeque<>();
    private final List<String> executed = new ArrayList<>();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger serviceLogger;

    @BeforeEach
    void captureLogs() {
        serviceLogger = (Logger) LoggerFactory.getLogger(AssistantService.class);
        logs.start();
        serviceLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        serviceLogger.detachAppender(logs);
    }

    private ChatOutcome repeat;
    private final RagChatPort chatPort = (request, listener) -> {
        requests.add(request);
        ChatOutcome next = script.poll();
        if (next == null) {
            return repeat != null ? repeat : new ChatOutcome.Failed(FailureReason.FAILED, null);
        }
        return next;
    };

    private AssistantTool tool(String name, Function<Map<String, Object>, ToolOutput> body) {
        return new AssistantTool() {
            public String name() {
                return name;
            }

            public String description() {
                return "d";
            }

            public Map<String, Object> parameters() {
                return Map.of("type", "object");
            }

            public ToolOutput call(Map<String, Object> arguments) {
                executed.add(name);
                return body.apply(arguments);
            }
        };
    }

    private AssistantTool scopedTool(String name, Function<Map<String, Object>, ToolOutput> body) {
        AssistantTool base = tool(name, body);
        return new AssistantTool() {
            public String name() {
                return base.name();
            }

            public String description() {
                return base.description();
            }

            public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of("release", Map.of("type", "string")));
            }

            public ToolOutput call(Map<String, Object> arguments) {
                return base.call(arguments);
            }
        };
    }

    private final ReleaseService releaseService = mock(ReleaseService.class);
    private final Release scoped = Release.create("Atlas", "4.2.0", null, "u", Instant.now());
    private Map<String, String> descriptionOverrides = Map.of();

    private AssistantService service(AssistantTool... tools) {
        when(releaseService.get(scoped.id())).thenReturn(scoped);
        AssistantProperties properties = new AssistantProperties(true, "http://rag", "key", "m", null, null, null,
                MAX_ROUNDS, MAX_CHARS,
                new AssistantProperties.Prompts("The question is about release \"{release}\". {question}",
                        descriptionOverrides, TOOL_ERROR_ANSWER),
                null, 20, Duration.ofMinutes(5),
                new AssistantProperties.DocumentAssist(4096, 3.0, 152, 2000, new AssistantProperties.Instructions(
                        new AssistantProperties.ByType("a", "a", "a"), new AssistantProperties.ByType("a", "a", "a")), java.time.Duration.ofMinutes(20)));
        return new AssistantService(chatPort, new ToolRegistry(List.of(tools)), mapper, MAX_ROUNDS, MAX_CHARS,
                releaseService, properties);
    }

    private AssistantService standardService() {
        return service(
                tool("releases", a -> new ToolOutput(Map.of("name", "2.4"), "rel-1", "Release 2.4")),
                tool("dashboard", a -> new ToolOutput(Map.of("open", 3), null, null)));
    }

    private static SignedTurn turn(String raw, ToolCall... calls) {
        return new SignedTurn(raw, List.of(calls));
    }

    private static ChatOutcome.Answered answered(String text) {
        return new ChatOutcome.Answered(text, List.of(
                new Source(1, "tickets", "d1", "T", "u", "TICKET", Map.of())), false, "stop", false, null, null);
    }

    private static ChatOutcome.ToolCallsRequested calls(String raw, ToolCall... calls) {
        return new ChatOutcome.ToolCallsRequested(turn(raw, calls));
    }

    private AssistantAnswer ask(AssistantService service, UUID releaseId) {
        return service.ask(QUESTION, HISTORY, releaseId, RagChatPort.ChatListener.NONE);
    }

    @Test
    void directAnswerSendsDefinitionsAndCollections() {
        script.add(answered("done"));
        AssistantAnswer answer = ask(standardService(), null);

        assertThat(answer.text()).isEqualTo("done");
        assertThat(answer.sources()).hasSize(1);
        assertThat(answer.toolsRun()).isEmpty();
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().tools()).hasSize(2);
        assertThat(requests.getFirst().collections()).containsExactly(IndexCollection.values());
        assertThat(requests.getFirst().filter()).isNull();
        assertThat(requests.getFirst().turns()).isEmpty();
    }

    @Test
    void sendsReleaseFilterAndScopedQuestionWhenReleaseGiven() {
        script.add(answered("done"));
        ask(standardService(), scoped.id());
        assertThat(requests.getFirst().filter()).isEqualTo(Map.of("release_id", scoped.id().toString()));
        assertThat(requests.getFirst().question())
                .isEqualTo("The question is about release \"Atlas 4.2.0\". " + QUESTION);
    }

    @Test
    void releaseNameCannotInjectTheQuestionTwice() {
        Release tricky = Release.create("x{question}y", "1", null, "u", Instant.now());
        when(releaseService.get(tricky.id())).thenReturn(tricky);
        script.add(answered("done"));
        ask(standardService(), tricky.id());
        assertThat(requests.getFirst().question())
                .isEqualTo("The question is about release \"x{question}y 1\". " + QUESTION);
    }

    @Test
    void unknownReleaseFailsBeforeAnyChatCall() {
        UUID unknown = UUID.randomUUID();
        when(releaseService.get(unknown)).thenThrow(new ReleaseNotFoundException());
        assertThatThrownBy(() -> ask(standardService(), unknown)).isInstanceOf(ReleaseNotFoundException.class);
        assertThat(requests).isEmpty();
    }

    @Test
    void unscopedSendsQuestionUnchangedAndPassesArgumentsThrough() {
        List<Map<String, Object>> seen = new ArrayList<>();
        script.add(calls("raw", new ToolCall("c1", "releases", Map.of("release", "2.4"))));
        script.add(answered("done"));
        ask(service(scopedTool("releases", a -> {
            seen.add(a);
            return new ToolOutput("r", null, null);
        }), tool("dashboard", a -> new ToolOutput("d", null, null))), null);
        assertThat(requests.getFirst().question()).isEqualTo(QUESTION);
        assertThat(requests.getFirst().tools()).hasSize(2);
        assertThat(seen).containsExactly(Map.of("release", "2.4"));
    }

    @Test
    void scopedToolsReceiveExactlyTheScopedReleaseAndDashboardIsNotOffered() {
        List<Map<String, Object>> seen = new ArrayList<>();
        AssistantService service = service(
                scopedTool("deployments", a -> {
                    seen.add(a);
                    return new ToolOutput("r", null, null);
                }),
                scopedTool("tickets", a -> {
                    seen.add(a);
                    return new ToolOutput("r", null, null);
                }),
                tool("dashboard", a -> new ToolOutput("d", null, null)));
        script.add(calls("raw",
                new ToolCall("c1", "deployments", Map.of("release", "Borealis 1.0.0", "stage", "Production")),
                new ToolCall("c2", "tickets", Map.of("key", "BOR-1")),
                new ToolCall("c3", "dashboard", Map.of())));
        script.add(answered("done"));

        AssistantAnswer answer = ask(service, scoped.id());

        Map<String, Object> pinned = Map.of("release", scoped.id().toString());
        assertThat(requests.getFirst().tools()).extracting("name").containsExactlyInAnyOrder("deployments", "tickets");
        assertThat(seen).containsExactly(pinned, pinned);
        assertThat(executed).doesNotContain("dashboard");
        assertThat(answer.toolsRun().get(0).arguments()).isEqualTo(pinned);
        assertThat(requests.get(1).turns().getFirst().results().get(2).content())
                .isEqualTo("{\"error\":\"unknown tool\"}");
    }

    @Test
    void toolDescriptionOverridesReplaceOnlyTheNamedTools() {
        descriptionOverrides = Map.of("releases", "custom");
        script.add(answered("done"));
        ask(standardService(), null);
        assertThat(requests.getFirst().tools()).extracting("description").containsExactlyInAnyOrder("custom", "d");
    }

    @Test
    void historyIsCappedToTheNewestMessagesAndFlagged() {
        List<ChatMessage> long25 = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            long25.add(new ChatMessage("user", "m" + i));
        }
        script.add(answered("done"));
        AssistantAnswer trimmed = standardService().ask(QUESTION, long25, null, RagChatPort.ChatListener.NONE);
        assertThat(trimmed.historyTrimmed()).isTrue();
        assertThat(requests.getFirst().history()).hasSize(20).isEqualTo(long25.subList(5, 25));

        script.add(answered("done"));
        AssistantAnswer exact = standardService().ask(QUESTION, long25.subList(0, 20), null,
                RagChatPort.ChatListener.NONE);
        assertThat(exact.historyTrimmed()).isFalse();
        assertThat(requests.get(1).history()).hasSize(20);
    }

    @Test
    void toolStartedIsCalledForOfferedToolsOnlyAndListenerExceptionStopsTheLoop() {
        List<String> started = new ArrayList<>();
        RagChatPort.ChatListener listener = new RagChatPort.ChatListener() {
            public void token(String text) {
            }

            public void roundDiscarded() {
            }

            public void toolStarted(String name) {
                started.add(name);
            }
        };
        script.add(calls("raw", new ToolCall("c1", "releases", Map.of()), new ToolCall("c2", "nope", Map.of())));
        script.add(answered("done"));
        standardService().ask(QUESTION, HISTORY, null, listener);
        assertThat(started).containsExactly("releases");

        requests.clear();
        script.add(calls("raw", new ToolCall("c1", "releases", Map.of())));
        script.add(answered("done"));
        RagChatPort.ChatListener gone = new RagChatPort.ChatListener() {
            public void token(String text) {
            }

            public void roundDiscarded() {
            }

            public void toolStarted(String name) {
                throw new ClientGoneException();
            }
        };
        assertThatThrownBy(() -> standardService().ask(QUESTION, HISTORY, null, gone))
                .isInstanceOf(ClientGoneException.class);
        assertThat(requests).hasSize(1);
    }

    @Test
    void runsToolsInOrderAndResendsSignedTurnUnchanged() {
        SignedTurn signed = turn("{\"raw\":1}", new ToolCall("c1", "releases", Map.of("release", "2.4")),
                new ToolCall("c2", "dashboard", Map.of()));
        script.add(new ChatOutcome.ToolCallsRequested(signed));
        script.add(answered("done"));

        AssistantAnswer answer = ask(standardService(), null);

        assertThat(executed).containsExactly("releases", "dashboard");
        assertThat(requests).hasSize(2);
        ChatRequest second = requests.get(1);
        assertThat(second.question()).isEqualTo(QUESTION);
        assertThat(second.history()).isEqualTo(HISTORY);
        assertThat(second.turns()).hasSize(1);
        assertThat(second.turns().getFirst().turn()).isSameAs(signed);
        assertThat(second.turns().getFirst().turn().rawJson()).isEqualTo("{\"raw\":1}");
        assertThat(second.turns().getFirst().results()).extracting("callId").containsExactly("c1", "c2");
        assertThat(second.turns().getFirst().results().getFirst().sourceId()).isEqualTo("rel-1");
        assertThat(second.turns().getFirst().results().getFirst().sourceTitle()).isEqualTo("Release 2.4");
        assertThat(answer.toolsRun()).hasSize(2).allMatch(run -> run.round() == 1);
    }

    @Test
    void threeRoundsKeepEarlierTurnsInOrder() {
        List<SignedTurn> emitted = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            SignedTurn t = turn("raw" + i, new ToolCall("c" + i, "dashboard", Map.of()));
            emitted.add(t);
            script.add(new ChatOutcome.ToolCallsRequested(t));
        }
        script.add(answered("done"));

        AssistantAnswer answer = ask(standardService(), null);

        assertThat(requests).hasSize(4);
        assertThat(requests.get(3).turns()).extracting(t -> t.turn().rawJson()).containsExactly("raw1", "raw2", "raw3");
        assertThat(requests.get(3).turns()).extracting(t -> t.turn()).containsExactlyElementsOf(emitted);
        assertThat(answer.toolsRun()).extracting("round").containsExactly(1, 2, 3);
    }

    @Test
    void endlessToolRequestsStopAfterMaxRoundsPlusOnePosts() {
        repeat = calls("raw", new ToolCall("c", "dashboard", Map.of()));

        AssistantAnswer answer = ask(standardService(), null);

        assertThat(requests).hasSize(MAX_ROUNDS + 1);
        assertThat(answer.failure()).isEqualTo(FailureReason.FAILED);
        assertThat(answer.text()).isNull();
    }

    @Test
    void toolFailuresBecomeErrorResultsWithoutLeakingDetail() {
        AssistantService service = service(
                tool("bad_args", a -> {
                    throw new ToolArgumentException("x");
                }),
                tool("boom", a -> {
                    throw new RuntimeException("db secret");
                }));
        script.add(calls("raw", new ToolCall("c1", "nope", Map.of()), new ToolCall("c2", "bad_args", Map.of()),
                new ToolCall("c3", "boom", Map.of())));
        script.add(answered("done"));

        AssistantAnswer answer = ask(service, null);

        var results = requests.get(1).turns().getFirst().results();
        assertThat(results).allMatch(r -> r.isError());
        assertThat(results).extracting("content").containsExactly(
                "{\"error\":\"unknown tool\"}", "{\"error\":\"x\"}", "{\"error\":\"tool failed\"}");
        assertThat(requests.get(1).turns().toString()).doesNotContain("db secret");
        assertThat(answer.toolsRun()).allMatch(run -> run.error());
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("db secret"));
    }

    private static ChatOutcome.Answered refused() {
        return new ChatOutcome.Answered("I don't know.", List.of(), true, "stop", false, null, null);
    }

    private AssistantTool failing(String name, RuntimeException failure) {
        return tool(name, a -> {
            throw failure;
        });
    }

    private RagChatPort.ChatListener recording(List<String> events) {
        return new RagChatPort.ChatListener() {
            public void token(String text) {
                events.add("token:" + text);
            }

            public void roundDiscarded() {
                events.add("discard");
            }
        };
    }

    @Test
    void refusedAnswerAfterAnArgumentErrorShowsTheToolMessage() {
        AssistantService service = service(failing("bad", new ToolArgumentException("no release matches 'Relase'")));
        script.add(calls("raw", new ToolCall("c1", "bad", Map.of())));
        script.add(refused());
        List<String> events = new ArrayList<>();

        AssistantAnswer answer = service.ask(QUESTION, HISTORY, null, recording(events));

        assertThat(answer.text()).isEqualTo("I couldn't look that up: no release matches 'Relase'.");
        assertThat(answer.refused()).isFalse();
        assertThat(answer.sources()).isEmpty();
        assertThat(answer.failure()).isNull();
        assertThat(answer.toolsRun()).hasSize(1);
        assertThat(events).containsExactly("discard", "discard",
                "token:I couldn't look that up: no release matches 'Relase'.");
    }

    @Test
    void distinctArgumentErrorsAreJoinedAndDuplicatesAppearOnce() {
        AssistantService service = service(
                failing("a", new ToolArgumentException("first")),
                failing("b", new ToolArgumentException("second")));
        script.add(calls("raw", new ToolCall("c1", "a", Map.of()), new ToolCall("c2", "a", Map.of())));
        script.add(calls("raw", new ToolCall("c3", "b", Map.of()), new ToolCall("c4", "a", Map.of())));
        script.add(refused());

        AssistantAnswer answer = ask(service, null);

        assertThat(answer.text()).isEqualTo("I couldn't look that up: first; second.");
    }

    @Test
    void refusedAnswerAfterInternalToolErrorsStaysARefusal() {
        AssistantService service = service(failing("boom", new RuntimeException("db secret")));
        script.add(calls("raw", new ToolCall("c1", "nope", Map.of()), new ToolCall("c2", "boom", Map.of())));
        script.add(refused());
        List<String> events = new ArrayList<>();

        AssistantAnswer answer = service.ask(QUESTION, HISTORY, null, recording(events));

        assertThat(answer.refused()).isTrue();
        assertThat(answer.text()).isEqualTo("I don't know.");
        assertThat(events).containsExactly("discard");
    }

    @Test
    void argumentErrorWithANormalAnswerKeepsTheModelAnswer() {
        AssistantService service = service(failing("bad", new ToolArgumentException("x")));
        script.add(calls("raw", new ToolCall("c1", "bad", Map.of())));
        script.add(answered("fine"));

        AssistantAnswer answer = ask(service, null);

        assertThat(answer.text()).isEqualTo("fine");
        assertThat(answer.sources()).hasSize(1);
    }

    @Test
    void oversizedResultIsReplacedByError() {
        AssistantService service = service(
                tool("big", a -> new ToolOutput("x".repeat(7000), "s", "t")));
        script.add(calls("raw", new ToolCall("c1", "big", Map.of())));
        script.add(answered("done"));

        ask(service, null);

        var result = requests.get(1).turns().getFirst().results().getFirst();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).isEqualTo("{\"error\":\"result too large, narrow the arguments\"}");
        assertThat(result.sourceId()).isNull();
    }

    @Test
    void ragMetaAndTrimFlagReachTheAnswer() {
        script.add(new ChatOutcome.Answered("t", List.of(), false, "stop", true, "m", "h"));

        AssistantAnswer answer = ask(standardService(), null);

        assertThat(answer.historyTrimmed()).isTrue();
        assertThat(answer.model()).isEqualTo("m");
        assertThat(answer.promptHash()).isEqualTo("h");
        assertThat(answer.retryAfterSeconds()).isNull();
    }

    @Test
    void roundCapFallthroughFailureHasNoRetryAfter() {
        repeat = calls("{}", new ToolCall("c", "dashboard", Map.of()));

        AssistantAnswer answer = ask(standardService(), null);

        assertThat(answer.failure()).isEqualTo(FailureReason.FAILED);
        assertThat(answer.retryAfterSeconds()).isNull();
    }

    @Test
    void portFailureIsReturnedWithoutRunningTools() {
        script.add(new ChatOutcome.Failed(FailureReason.BUSY, 5));

        AssistantAnswer answer = ask(standardService(), null);

        assertThat(answer.failure()).isEqualTo(FailureReason.BUSY);
        assertThat(answer.retryAfterSeconds()).isEqualTo(5);
        assertThat(answer.model()).isNull();
        assertThat(answer.promptHash()).isNull();
        assertThat(executed).isEmpty();
        assertThat(answer.toolsRun()).isEmpty();
    }

    @Test
    void listenerIsToldAboutEachDiscardedRoundAndLogsCarryMetadataOnly() {
        List<String> events = new ArrayList<>();
        RagChatPort.ChatListener listener = new RagChatPort.ChatListener() {
            public void token(String text) {
                events.add("token");
            }

            public void roundDiscarded() {
                events.add("discard");
            }
        };
        script.add(calls("raw", new ToolCall("c1", "releases", Map.of("release", "SECRET-ARG"))));
        script.add(calls("raw2", new ToolCall("c2", "dashboard", Map.of())));
        script.add(answered("done"));

        standardService().ask(QUESTION, HISTORY, null, listener);

        assertThat(events).containsExactly("discard", "discard");
        String logged = String.join("\n", logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
        assertThat(logged).contains("name=releases").contains("name=dashboard").contains("durationMs=");
        assertThat(logged).doesNotContain("SECRET-QUESTION").doesNotContain("SECRET-ARG").doesNotContain("2.4");
    }
}
