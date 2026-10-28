package com.evision.releasemgmt.adapters.rag;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.evision.releasemgmt.config.RagRestClientConfig;
import com.evision.releasemgmt.domain.assistant.ClientGoneException;
import com.evision.releasemgmt.domain.assistant.FailureReason;
import com.evision.releasemgmt.domain.assistant.TransformOutcome;
import com.evision.releasemgmt.ports.RagChatPort;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
class RagTransformAdapterTest {

    private static final String KEY = "test-key";
    private static final String URL = "/v1/transform";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DOC = "SENTINEL-DOC-5521";
    private static final String INSTRUCTION = "SENTINEL-INSTR-8830";
    private static final String DONE = "event: done\ndata: {\"refusal\":false,\"finishReason\":\"stop\","
            + "\"truncated\":false,\"promptTokens\":512,\"completionTokens\":300}\n\n";

    private static RagTransformAdapter adapter(String baseUrl, Duration readTimeout) {
        return new RagTransformAdapter(
                RagRestClientConfig.ragClient(baseUrl, KEY, Duration.ofSeconds(1), readTimeout), MAPPER);
    }

    private static RagTransformAdapter adapter(WireMockRuntimeInfo wm) {
        return adapter(wm.getHttpBaseUrl(), Duration.ofSeconds(5));
    }

    private static ResponseDefinitionBuilder sse(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "text/event-stream").withBody(body);
    }

    private static ResponseDefinitionBuilder problem(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/problem+json").withBody(body);
    }

    private static TransformOutcome run(RagTransformAdapter adapter, RagChatPort.ChatListener listener) {
        return adapter.transform(DOC, INSTRUCTION, listener);
    }

    private static class Recorder implements RagChatPort.ChatListener {
        final List<String> tokens = new ArrayList<>();
        int heartbeats;

        @Override
        public void token(String text) {
            tokens.add(text);
        }

        @Override
        public void roundDiscarded() {
        }

        @Override
        public void heartbeat() {
            heartbeats++;
        }
    }

    @Test
    void streamsTokensAndHeartbeatsThenReturnsDone(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(URL)).willReturn(sse(
                "event: status\ndata: {\"requestId\":\"r\",\"phase\":\"GENERATING\"}\n\n"
                        + "event: token\ndata: {\"text\":\"Hello \"}\n\n"
                        + ": heartbeat\n\n"
                        + "event: token\ndata: {\"text\":\"world\"}\n\n" + DONE)));
        Recorder listener = new Recorder();

        TransformOutcome outcome = run(adapter(wm), listener);

        assertThat(listener.tokens).containsExactly("Hello ", "world");
        assertThat(listener.heartbeats).isEqualTo(1);
        assertThat(outcome).isEqualTo(new TransformOutcome.Done("stop", false, 512, 300));
    }

    @Test
    void requestBodyIsTextAndInstructionWithBearerKey(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlPathEqualTo(URL)).willReturn(sse(DONE)));

        run(adapter(wm), RagChatPort.ChatListener.NONE);

        var recorded = findAll(postRequestedFor(urlPathEqualTo(URL))
                .withHeader("Authorization", equalTo("Bearer " + KEY))
                .withHeader("Accept", equalTo("text/event-stream"))).getFirst();
        JsonNode body = MAPPER.readTree(recorded.getBodyAsString());
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("text", "instruction");
        assertThat(body.get("text").asString()).isEqualTo(DOC);
        assertThat(body.get("instruction").asString()).isEqualTo(INSTRUCTION);
    }

    @Test
    void truncatedDoneIsReported(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(URL)).willReturn(sse(
                "event: done\ndata: {\"finishReason\":\"length\",\"truncated\":true}\n\n")));

        assertThat(run(adapter(wm), RagChatPort.ChatListener.NONE))
                .isEqualTo(new TransformOutcome.Done("length", true, null, null));
    }

    @Test
    void errorEventsAreMappedByReason(WireMockRuntimeInfo wm) {
        assertStreamError(wm, "DEADLINE", FailureReason.DEADLINE);
        assertStreamError(wm, "OLLAMA_UNAVAILABLE", FailureReason.OFFLINE);
        assertStreamError(wm, "MODEL_NOT_AVAILABLE", FailureReason.MODEL_MISSING);
        assertStreamError(wm, "GENERATION_FAILED", FailureReason.FAILED);
        assertStreamError(wm, "INTERNAL", FailureReason.FAILED);
    }

    private void assertStreamError(WireMockRuntimeInfo wm, String reason, FailureReason expected) {
        stubFor(post(urlPathEqualTo(URL)).willReturn(sse(
                "event: token\ndata: {\"text\":\"x\"}\n\nevent: error\ndata: {\"reason\":\"" + reason + "\"}\n\n")));

        assertThat(run(adapter(wm), RagChatPort.ChatListener.NONE))
                .isEqualTo(new TransformOutcome.Failed(expected, null));
    }

    @Test
    void streamWithoutTerminalEventOrWithMalformedDoneFails(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(URL)).willReturn(sse("event: token\ndata: {\"text\":\"x\"}\n\n")));
        assertThat(run(adapter(wm), RagChatPort.ChatListener.NONE))
                .isEqualTo(new TransformOutcome.Failed(FailureReason.FAILED, null));

        stubFor(post(urlPathEqualTo(URL)).willReturn(sse("event: done\ndata: {not json\n\n")));
        assertThat(run(adapter(wm), RagChatPort.ChatListener.NONE))
                .isEqualTo(new TransformOutcome.Failed(FailureReason.FAILED, null));
    }

    @Test
    void httpErrorsMapToOutcomes(WireMockRuntimeInfo wm) {
        assertHttp(wm, aResponse().withStatus(429).withHeader("Retry-After", "7"),
                new TransformOutcome.Failed(FailureReason.BUSY, 7));
        assertHttp(wm, aResponse().withStatus(429).withHeader("Retry-After", "tomorrow"),
                new TransformOutcome.Failed(FailureReason.BUSY, null));
        assertHttp(wm, problem(503, "{\"reason\":\"MODEL_NOT_AVAILABLE\"}"),
                new TransformOutcome.Failed(FailureReason.MODEL_MISSING, null));
        assertHttp(wm, problem(503, "{\"reason\":\"OLLAMA_UNAVAILABLE\"}"),
                new TransformOutcome.Failed(FailureReason.OFFLINE, null));
        assertHttp(wm, problem(400, "{\"reason\":\"BAD\"}"), new TransformOutcome.Failed(FailureReason.REJECTED, null));
        assertHttp(wm, problem(413, "{\"title\":\"Text too large\",\"status\":413,\"detail\":\"Text exceeds the "
                + "maximum of 5523 characters for this model's context window\",\"reason\":\"TEXT_TOO_LARGE\"}"),
                new TransformOutcome.TooLarge(5523));
        assertHttp(wm, problem(413, "{\"reason\":\"TEXT_TOO_LARGE\"}"), new TransformOutcome.TooLarge(null));
    }

    private void assertHttp(WireMockRuntimeInfo wm, ResponseDefinitionBuilder response, TransformOutcome expected) {
        stubFor(post(urlPathEqualTo(URL)).willReturn(response));

        assertThat(run(adapter(wm), RagChatPort.ChatListener.NONE)).isEqualTo(expected);
    }

    @Test
    void refusedConnectionIsOffline() {
        assertThat(run(adapter("http://localhost:1", Duration.ofSeconds(1)), RagChatPort.ChatListener.NONE))
                .isEqualTo(new TransformOutcome.Failed(FailureReason.OFFLINE, null));
    }

    @Test
    void readTimeoutIsDeadline(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(URL)).willReturn(sse(DONE).withFixedDelay(2500)));

        assertThat(run(adapter(wm.getHttpBaseUrl(), Duration.ofSeconds(1)), RagChatPort.ChatListener.NONE))
                .isEqualTo(new TransformOutcome.Failed(FailureReason.DEADLINE, null));
    }

    @Test
    void listenerExceptionsPropagateUnchanged(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(URL)).willReturn(sse(
                "event: token\ndata: {\"text\":\"one\"}\n\nevent: token\ndata: {\"text\":\"two\"}\n\n" + DONE)));
        ClientGoneException gone = new ClientGoneException();
        List<String> seen = new ArrayList<>();
        RagChatPort.ChatListener listener = new Recorder() {
            @Override
            public void token(String text) {
                seen.add(text);
                throw gone;
            }
        };

        assertThatThrownBy(() -> run(adapter(wm), listener)).isSameAs(gone);
        assertThat(seen).containsExactly("one");

        stubFor(post(urlPathEqualTo(URL)).willReturn(sse(": hb\n\n")));
        IllegalStateException boom = new IllegalStateException();
        RagChatPort.ChatListener heartbeatFails = new Recorder() {
            @Override
            public void heartbeat() {
                throw boom;
            }
        };

        assertThatThrownBy(() -> run(adapter(wm), heartbeatFails)).isSameAs(boom);
    }

    @Test
    void noDocumentOrInstructionTextIsLogged(WireMockRuntimeInfo wm) {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            stubFor(post(urlPathEqualTo(URL)).willReturn(sse(DONE)));
            run(adapter(wm), RagChatPort.ChatListener.NONE);
            stubFor(post(urlPathEqualTo(URL)).willReturn(problem(413, "{\"detail\":\"maximum of 9 characters\"}")));
            run(adapter(wm), RagChatPort.ChatListener.NONE);
            run(adapter("http://localhost:1", Duration.ofSeconds(1)), RagChatPort.ChatListener.NONE);
        } finally {
            root.detachAppender(appender);
        }

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(m -> m.contains(DOC) || m.contains(INSTRUCTION));
    }
}
