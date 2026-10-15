package com.evision.releasemgmt.application.assistant;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.evision.releasemgmt.application.assistant.tools.AssistantTool;
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

class AssistantServiceTest {

    private static final int MAX_ROUNDS = 4;
    private static final int MAX_CHARS = 6000;
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
            return repeat != null ? repeat : new ChatOutcome.Failed(FailureReason.FAILED);
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

    private AssistantService service(AssistantTool... tools) {
        return new AssistantService(chatPort, new ToolRegistry(List.of(tools)), mapper, MAX_ROUNDS, MAX_CHARS);
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
                new Source(1, "tickets", "d1", "T", "u", "TICKET", Map.of())), false, "stop");
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
    void sendsReleaseFilterWhenReleaseGiven() {
        UUID id = UUID.randomUUID();
        script.add(answered("done"));
        ask(standardService(), id);
        assertThat(requests.getFirst().filter()).isEqualTo(Map.of("release_id", id.toString()));
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
    void portFailureIsReturnedWithoutRunningTools() {
        script.add(new ChatOutcome.Failed(FailureReason.BUSY));

        AssistantAnswer answer = ask(standardService(), null);

        assertThat(answer.failure()).isEqualTo(FailureReason.BUSY);
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
