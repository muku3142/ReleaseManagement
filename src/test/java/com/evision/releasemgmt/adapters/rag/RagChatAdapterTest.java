package com.evision.releasemgmt.adapters.rag;

import com.evision.releasemgmt.config.RagRestClientConfig;
import com.evision.releasemgmt.domain.assistant.AnsweredTurn;
import com.evision.releasemgmt.domain.assistant.ChatMessage;
import com.evision.releasemgmt.domain.assistant.ChatOutcome;
import com.evision.releasemgmt.domain.assistant.ChatRequest;
import com.evision.releasemgmt.domain.assistant.ClientGoneException;
import com.evision.releasemgmt.domain.assistant.FailureReason;
import com.evision.releasemgmt.domain.assistant.IndexCollection;
import com.evision.releasemgmt.domain.assistant.SignedTurn;
import com.evision.releasemgmt.domain.assistant.ToolCall;
import com.evision.releasemgmt.domain.assistant.ToolDefinition;
import com.evision.releasemgmt.domain.assistant.ToolResult;
import com.evision.releasemgmt.ports.RagChatPort;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class RagChatAdapterTest {

    private static final String KEY = "test-key";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOOL_CALL_DATA = "{\"round\":1,\"questionId\":\"q-1\",\"calls\":[{\"id\":\"c1\","
            + "\"name\":\"get_release\",\"arguments\":{\"releaseId\":\"R-1\"}}],\"signature\":\"sig-abc\"}";

    private static RagChatAdapter adapter(String baseUrl, Duration readTimeout) {
        return new RagChatAdapter(RagRestClientConfig.ragClient(baseUrl, KEY, Duration.ofSeconds(1), readTimeout),
                MAPPER);
    }

    private static RagChatAdapter adapter(WireMockRuntimeInfo wm) {
        return adapter(wm.getHttpBaseUrl(), Duration.ofSeconds(5));
    }

    private static ChatRequest request(List<AnsweredTurn> turns) {
        return new ChatRequest("secret-question", List.of(new ChatMessage("user", "earlier")),
                List.of(IndexCollection.values()), Map.of("release_id", "R-1"),
                List.of(new ToolDefinition("get_release", "Gets a release", Map.of("type", "object"))), turns);
    }

    private static ResponseDefinitionBuilder sse(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "text/event-stream").withBody(body);
    }

    private static ResponseDefinitionBuilder problem(int status, String reason) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/problem+json")
                .withBody("{\"reason\":\"" + reason + "\"}");
    }

    private static class RecordingListener implements RagChatPort.ChatListener {
        final List<String> tokens = new ArrayList<>();

        @Override
        public void token(String text) {
            tokens.add(text);
        }

        @Override
        public void roundDiscarded() {
        }
    }

    @Test
    void plainAnswerStreamIsConcatenatedWithCitations(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse(
                ": hb\n\n"
                        + "event: status\ndata: {\"requestId\":\"r\",\"phase\":\"SEARCHING\"}\n\n"
                        + "event: token\ndata: {\"text\":\"Hel\"}\n\n"
                        + ": hb\n\n"
                        + "event: token\ndata: {\"text\":\"lo \"}\n\n"
                        + "event: token\ndata: {\"text\":\"[1]\"}\n\n"
                        + "event: mystery\ndata: {\"x\":1}\n\n"
                        + "event: citations\ndata: {\"basis\":\"MARKED\",\"items\":[{\"n\":1,\"kind\":\"TOOL\","
                        + "\"documentId\":\"d1\",\"title\":\"T\",\"collection\":\"rm-tickets\",\"metadata\":{\"a\":\"b\"}}]}\n\n"
                        + "event: done\ndata: {\"refusal\":false,\"finishReason\":\"stop\"}\n\n")));
        RecordingListener listener = new RecordingListener();

        ChatOutcome outcome = adapter(wm).chat(request(List.of()), listener);

        assertThat(listener.tokens).containsExactly("Hel", "lo ", "[1]");
        ChatOutcome.Answered answered = (ChatOutcome.Answered) outcome;
        assertThat(answered.text()).isEqualTo("Hello [1]");
        assertThat(answered.refused()).isFalse();
        assertThat(answered.finishReason()).isEqualTo("stop");
        assertThat(answered.citations()).singleElement().satisfies(source -> {
            assertThat(source.n()).isEqualTo(1);
            assertThat(source.kind()).isEqualTo("TOOL");
            assertThat(source.documentId()).isEqualTo("d1");
            assertThat(source.collection()).isEqualTo("rm-tickets");
            assertThat(source.url()).isNull();
            assertThat(source.metadata()).containsEntry("a", "b");
        });
    }

    @Test
    void toolStreamYieldsSignedTurnWithRawJson(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse(
                "event: status\ndata: {\"phase\":\"GENERATING\"}\n\n"
                        + "event: token\ndata: {\"text\":\"Let me\"}\n\n"
                        + "event: tool_call\ndata: " + TOOL_CALL_DATA + "\n\n"
                        + "event: done\ndata: {\"refusal\":false,\"finishReason\":\"tool_calls\"}\n\n")));

        ChatOutcome outcome = adapter(wm).chat(request(List.of()), RagChatPort.ChatListener.NONE);

        SignedTurn turn = ((ChatOutcome.ToolCallsRequested) outcome).turn();
        assertThat(turn.rawJson()).isEqualTo(TOOL_CALL_DATA);
        assertThat(turn.calls()).singleElement().satisfies(call -> {
            assertThat(call.id()).isEqualTo("c1");
            assertThat(call.name()).isEqualTo("get_release");
            assertThat(call.arguments()).containsEntry("releaseId", "R-1");
        });
    }

    @Test
    void resendWritesSignedFieldsBackUnchangedWithResults(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse(
                "event: done\ndata: {\"refusal\":false,\"finishReason\":\"stop\"}\n\n")));
        SignedTurn turn = new SignedTurn(TOOL_CALL_DATA,
                List.of(new ToolCall("c1", "get_release", Map.of("releaseId", "R-1"))));
        AnsweredTurn answered = new AnsweredTurn(turn, List.of(
                new ToolResult("c1", "{\"ok\":true}", false, "release:R-1", "Release R-1"),
                new ToolResult("c2", "boom", true, null, null)));

        adapter(wm).chat(request(List.of(answered)), RagChatPort.ChatListener.NONE);

        JsonNode body = MAPPER.readTree(findAll(postRequestedFor(urlPathEqualTo("/v1/chat"))).getFirst().getBodyAsString());
        JsonNode sent = body.get("toolTurns").get(0);
        JsonNode received = MAPPER.readTree(TOOL_CALL_DATA);
        for (String field : List.of("round", "questionId", "calls", "signature")) {
            assertThat(sent.get(field)).isEqualTo(received.get(field));
        }
        assertThat(sent.get("results").get(0).get("callId").asString()).isEqualTo("c1");
        assertThat(sent.get("results").get(0).get("isError").asBoolean()).isFalse();
        assertThat(sent.get("results").get(0).get("source").get("id").asString()).isEqualTo("release:R-1");
        assertThat(sent.get("results").get(0).get("source").get("title").asString()).isEqualTo("Release R-1");
        assertThat(sent.get("results").get(0).get("source").has("url")).isFalse();
        assertThat(sent.get("results").get(1).get("isError").asBoolean()).isTrue();
        assertThat(sent.get("results").get(1).has("source")).isFalse();
    }

    @Test
    void requestCarriesContractFieldsAndBearerKey(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse(
                "event: done\ndata: {\"refusal\":false,\"finishReason\":\"stop\"}\n\n")));

        adapter(wm).chat(request(List.of()), RagChatPort.ChatListener.NONE);

        var recorded = findAll(postRequestedFor(urlPathEqualTo("/v1/chat"))
                .withHeader("Authorization", equalTo("Bearer " + KEY))
                .withHeader("Accept", equalTo("text/event-stream"))).getFirst();
        JsonNode body = MAPPER.readTree(recorded.getBodyAsString());
        assertThat(body.get("collections").valueStream().map(JsonNode::asString).toList())
                .containsExactly("rm-tickets", "rm-documents", "rm-manual-items", "rm-code-links");
        assertThat(body.get("question").asString()).isEqualTo("secret-question");
        assertThat(body.get("history").get(0).get("role").asString()).isEqualTo("user");
        assertThat(body.get("filter").get("release_id").asString()).isEqualTo("R-1");
        assertThat(body.get("tools").get(0).get("name").asString()).isEqualTo("get_release");
        assertThat(body.get("tools").get(0).get("description").asString()).isEqualTo("Gets a release");
        assertThat(body.get("tools").get(0).get("parameters").get("type").asString()).isEqualTo("object");
        assertThat(body.get("toolTurns")).isEmpty();
    }

    @Test
    void httpErrorsMapToFailureReasons(WireMockRuntimeInfo wm) {
        assertFailure(wm, aResponse().withStatus(429).withHeader("Retry-After", "5"), FailureReason.BUSY, 5);
        assertFailure(wm, aResponse().withStatus(429), FailureReason.BUSY, null);
        assertFailure(wm, aResponse().withStatus(429).withHeader("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT"),
                FailureReason.BUSY, null);
        assertFailure(wm, problem(503, "MODEL_NOT_AVAILABLE"), FailureReason.MODEL_MISSING, null);
        assertFailure(wm, problem(503, "OLLAMA_UNAVAILABLE"), FailureReason.OFFLINE, null);
        assertFailure(wm, problem(400, "TOOL_TURN_INVALID"), FailureReason.REJECTED, null);
        assertFailure(wm, problem(413, "TOOL_RESULT_TOO_LARGE"), FailureReason.REJECTED, null);
    }

    private void assertFailure(WireMockRuntimeInfo wm, ResponseDefinitionBuilder response, FailureReason expected,
                               Integer expectedRetry) {
        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(response));

        ChatOutcome outcome = adapter(wm).chat(request(List.of()), RagChatPort.ChatListener.NONE);

        assertThat(outcome).isEqualTo(new ChatOutcome.Failed(expected, expectedRetry));
    }

    @Test
    void inStreamErrorEventIsMappedByReason(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse(
                "event: token\ndata: {\"text\":\"x\"}\n\n"
                        + "event: error\ndata: {\"reason\":\"DEADLINE\"}\n\n")));

        assertThat(adapter(wm).chat(request(List.of()), RagChatPort.ChatListener.NONE))
                .isEqualTo(new ChatOutcome.Failed(FailureReason.DEADLINE, null));

        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse(
                "event: error\ndata: {\"reason\":\"TOOL_CALL_INVALID\"}\n\n")));

        assertThat(adapter(wm).chat(request(List.of()), RagChatPort.ChatListener.NONE))
                .isEqualTo(new ChatOutcome.Failed(FailureReason.FAILED, null));
    }

    @Test
    void malformedDataAndTruncatedStreamFailWithoutThrowing(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse("event: token\ndata: {not json\n\n")));

        assertThat(adapter(wm).chat(request(List.of()), RagChatPort.ChatListener.NONE))
                .isEqualTo(new ChatOutcome.Failed(FailureReason.FAILED, null));

        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse("event: token\ndata: {\"text\":\"a\"}\n\n")));

        assertThat(adapter(wm).chat(request(List.of()), RagChatPort.ChatListener.NONE))
                .isEqualTo(new ChatOutcome.Failed(FailureReason.FAILED, null));
    }

    @Test
    void multiLineDataIsJoined(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse(
                "event: token\ndata: {\"text\":\ndata: \"split\"}\n\n"
                        + "event: done\ndata: {\"refusal\":true,\"finishReason\":\"stop\"}\n\n")));

        ChatOutcome.Answered answered = (ChatOutcome.Answered) adapter(wm).chat(request(List.of()),
                RagChatPort.ChatListener.NONE);

        assertThat(answered.text()).isEqualTo("split");
        assertThat(answered.refused()).isTrue();
    }

    private static final class HeartbeatCounter extends RecordingListener {
        int heartbeats;

        @Override
        public void heartbeat() {
            heartbeats++;
        }
    }

    @Test
    void everyCommentLineIsForwardedAsHeartbeat(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse(
                ": hb\n\n: hb\n\n"
                        + "event: token\ndata: {\"text\":\"a\"}\n\n"
                        + "event: done\ndata: {\"refusal\":false,\"finishReason\":\"stop\"}\n\n")));
        HeartbeatCounter listener = new HeartbeatCounter();

        ChatOutcome.Answered answered = (ChatOutcome.Answered) adapter(wm).chat(request(List.of()), listener);

        assertThat(listener.heartbeats).isEqualTo(2);
        assertThat(answered.text()).isEqualTo("a");
    }

    @Test
    void doneMetaReachesTheAnswerAndDefaultsWhenAbsent(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse(
                "event: done\ndata: {\"refusal\":false,\"finishReason\":\"stop\",\"historyTrimmed\":true,"
                        + "\"model\":\"qwen2.5-coder:7b\",\"promptHash\":\"abc123def456\"}\n\n")));

        ChatOutcome.Answered with = (ChatOutcome.Answered) adapter(wm).chat(request(List.of()),
                RagChatPort.ChatListener.NONE);

        assertThat(with.historyTrimmed()).isTrue();
        assertThat(with.model()).isEqualTo("qwen2.5-coder:7b");
        assertThat(with.promptHash()).isEqualTo("abc123def456");

        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse(
                "event: done\ndata: {\"refusal\":false,\"finishReason\":\"stop\"}\n\n")));

        ChatOutcome.Answered without = (ChatOutcome.Answered) adapter(wm).chat(request(List.of()),
                RagChatPort.ChatListener.NONE);

        assertThat(without.historyTrimmed()).isFalse();
        assertThat(without.model()).isNull();
        assertThat(without.promptHash()).isNull();
    }

    @Test
    void listenerExceptionPropagatesUnchangedAndStopsReading(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse(
                "event: token\ndata: {\"text\":\"one\"}\n\n"
                        + "event: token\ndata: {\"text\":\"two\"}\n\n"
                        + "event: token\ndata: {\"text\":\"three\"}\n\n"
                        + "event: done\ndata: {\"refusal\":false,\"finishReason\":\"stop\"}\n\n")));
        ClientGoneException gone = new ClientGoneException();
        List<String> seen = new ArrayList<>();
        RagChatPort.ChatListener listener = new RagChatPort.ChatListener() {
            @Override
            public void token(String text) {
                seen.add(text);
                if (seen.size() == 2) {
                    throw gone;
                }
            }

            @Override
            public void roundDiscarded() {
            }
        };

        assertThatThrownBy(() -> adapter(wm).chat(request(List.of()), listener)).isSameAs(gone);
        assertThat(seen).containsExactly("one", "two");
    }

    @Test
    void anyListenerRuntimeExceptionPropagatesAsIs(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse(": hb\n\n")));
        IllegalStateException boom = new IllegalStateException();
        RagChatPort.ChatListener listener = new RagChatPort.ChatListener() {
            @Override
            public void token(String text) {
            }

            @Override
            public void roundDiscarded() {
            }

            @Override
            public void heartbeat() {
                throw boom;
            }
        };

        assertThatThrownBy(() -> adapter(wm).chat(request(List.of()), listener)).isSameAs(boom);
    }

    @Test
    void refusedConnectionIsOffline() {
        assertThat(adapter("http://localhost:1", Duration.ofSeconds(1)).chat(request(List.of()),
                RagChatPort.ChatListener.NONE)).isEqualTo(new ChatOutcome.Failed(FailureReason.OFFLINE, null));
    }

    @Test
    void readTimeoutIsDeadline(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/v1/chat")).willReturn(sse("event: done\ndata: {}\n\n").withFixedDelay(2500)));

        assertThat(adapter(wm.getHttpBaseUrl(), Duration.ofSeconds(1)).chat(request(List.of()),
                RagChatPort.ChatListener.NONE)).isEqualTo(new ChatOutcome.Failed(FailureReason.DEADLINE, null));
    }
}
