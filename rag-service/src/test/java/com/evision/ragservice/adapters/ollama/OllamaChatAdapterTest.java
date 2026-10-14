package com.evision.ragservice.adapters.ollama;

import com.evision.ragservice.FakeOllamaServer;
import com.evision.ragservice.config.ChatProperties;
import com.evision.ragservice.domain.CancelReason;
import com.evision.ragservice.domain.Cancellation;
import com.evision.ragservice.domain.ChatMessage;
import com.evision.ragservice.domain.Completion;
import com.evision.ragservice.domain.GenerationFailedException;
import com.evision.ragservice.domain.GenerationOptions;
import com.evision.ragservice.domain.ModelNotAvailableException;
import com.evision.ragservice.domain.OllamaUnavailableException;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class OllamaChatAdapterTest {

    private static final String MODEL = "qwen3:4b";
    private static final String SECRET_TEXT = "confidential-ollama-text";
    private static final GenerationOptions OPTIONS = new GenerationOptions(8192, 512, 0.2);
    private static final List<ChatMessage> MESSAGES = List.of(
            new ChatMessage(ChatMessage.Role.SYSTEM, "sys"), new ChatMessage(ChatMessage.Role.USER, "q"));

    private static OllamaChatAdapter adapter(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        ChatProperties chat = new ChatProperties(MODEL, 8192, 512, 0.2, 0.0, "30m", Duration.ofSeconds(120),
                Duration.ofSeconds(15), 1, 2, 5, 5, 10, 0.0, 1000, 6, 500, 1000, 300, 4.0);
        return new OllamaChatAdapter(RestClient.builder().requestFactory(factory).build(), JsonMapper.builder().build(),
                chat, baseUrl, Duration.ofSeconds(1));
    }

    private static void stubChat(int status, String body) {
        stubFor(post(urlEqualTo("/api/chat")).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/x-ndjson").withBody(body)));
    }

    private static void stubShow(int status, String body) {
        stubFor(post(urlEqualTo("/api/show")).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static String lines(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    private static Completion run(OllamaChatAdapter adapter, List<String> tokens) {
        return adapter.stream(MESSAGES, OPTIONS, tokens::add, new Cancellation());
    }

    @Test
    void streamsTokensInOrderAndMapsCompletion(WireMockRuntimeInfo wm) {
        stubChat(200, lines(FakeOllamaServer.tokenLine("Hel"), FakeOllamaServer.tokenLine("lo"),
                FakeOllamaServer.doneLine("stop", 100, 2)));
        List<String> tokens = new ArrayList<>();

        Completion completion = run(adapter(wm.getHttpBaseUrl()), tokens);

        assertThat(tokens).containsExactly("Hel", "lo");
        assertThat(completion.finishReason()).isEqualTo("stop");
        assertThat(completion.promptEvalCount()).isEqualTo(100);
        assertThat(completion.evalCount()).isEqualTo(2);
        assertThat(completion.loadDurationMs()).isEqualTo(1000L);
        assertThat(completion.totalDurationMs()).isEqualTo(2000L);
        assertThat(completion.isCancelled()).isFalse();
    }

    @Test
    void sendsModelAndGenerationParametersExplicitly(WireMockRuntimeInfo wm) {
        stubChat(200, lines(FakeOllamaServer.doneLine("stop", 1, 0)));

        run(adapter(wm.getHttpBaseUrl()), new ArrayList<>());

        verify(postRequestedFor(urlEqualTo("/api/chat"))
                .withRequestBody(matchingJsonPath("$[?(@.model == 'qwen3:4b')]"))
                .withRequestBody(matchingJsonPath("$[?(@.stream == true)]"))
                .withRequestBody(matchingJsonPath("$[?(@.think == false)]"))
                .withRequestBody(matchingJsonPath("$[?(@.keep_alive == '30m')]"))
                .withRequestBody(matchingJsonPath("$.options[?(@.num_ctx == 8192)]"))
                .withRequestBody(matchingJsonPath("$.options[?(@.num_predict == 512)]"))
                .withRequestBody(matchingJsonPath("$.options[?(@.temperature == 0.2)]"))
                .withRequestBody(matchingJsonPath("$.options[?(@.top_p == 0.8)]"))
                .withRequestBody(matchingJsonPath("$.messages[0][?(@.role == 'system')]")));
    }

    @Test
    void mapsLengthFinishReason(WireMockRuntimeInfo wm) {
        stubChat(200, lines(FakeOllamaServer.tokenLine("a"), FakeOllamaServer.doneLine("length", 5, 1)));

        assertThat(run(adapter(wm.getHttpBaseUrl()), new ArrayList<>()).finishReason()).isEqualTo("length");
    }

    @Test
    void neverForwardsThinkingAndIgnoresUnknownFields(WireMockRuntimeInfo wm) {
        stubChat(200, lines(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"thinking\":\"" + SECRET_TEXT + "\"},\"done\":false,\"extra\":1}",
                FakeOllamaServer.tokenLine("ok"), FakeOllamaServer.doneLine("stop", 1, 1)));
        List<String> tokens = new ArrayList<>();

        run(adapter(wm.getHttpBaseUrl()), tokens);

        assertThat(tokens).containsExactly("ok");
    }

    @Test
    void midStreamErrorLineFailsWithoutLeakingText(WireMockRuntimeInfo wm) {
        stubChat(200, lines(FakeOllamaServer.tokenLine("a"), "{\"error\":\"" + SECRET_TEXT + " boom\"}"));

        assertThatThrownBy(() -> run(adapter(wm.getHttpBaseUrl()), new ArrayList<>()))
                .isInstanceOf(GenerationFailedException.class).hasMessageNotContaining(SECRET_TEXT);
    }

    @Test
    void streamEndingWithoutDoneFails(WireMockRuntimeInfo wm) {
        stubChat(200, lines(FakeOllamaServer.tokenLine("a")));

        assertThatThrownBy(() -> run(adapter(wm.getHttpBaseUrl()), new ArrayList<>()))
                .isInstanceOf(GenerationFailedException.class).hasMessageNotContaining(SECRET_TEXT);
    }

    @Test
    void malformedLineNamesOnlyTheLineNumber(WireMockRuntimeInfo wm) {
        stubChat(200, lines(FakeOllamaServer.tokenLine("a"), "{not json " + SECRET_TEXT));

        assertThatThrownBy(() -> run(adapter(wm.getHttpBaseUrl()), new ArrayList<>()))
                .isInstanceOf(GenerationFailedException.class)
                .hasMessage("Malformed stream line 2")
                .hasNoCause();
    }

    @Test
    void chatStatusesMapToDomainExceptions(WireMockRuntimeInfo wm) {
        OllamaChatAdapter adapter = adapter(wm.getHttpBaseUrl());

        stubChat(404, "{\"error\":\"" + SECRET_TEXT + "\"}");
        assertThatThrownBy(() -> run(adapter, new ArrayList<>())).isInstanceOf(ModelNotAvailableException.class)
                .hasMessageNotContaining(SECRET_TEXT);

        stubChat(400, "{\"error\":\"\\\"nomic-embed-text\\\" does not support chat " + SECRET_TEXT + "\"}");
        assertThatThrownBy(() -> run(adapter, new ArrayList<>())).isInstanceOf(ModelNotAvailableException.class)
                .hasMessageNotContaining(SECRET_TEXT);

        stubChat(400, "{\"error\":\"" + SECRET_TEXT + "\"}");
        assertThatThrownBy(() -> run(adapter, new ArrayList<>())).isInstanceOf(GenerationFailedException.class)
                .hasMessageNotContaining(SECRET_TEXT);

        stubChat(500, "{\"error\":\"" + SECRET_TEXT + "\"}");
        assertThatThrownBy(() -> run(adapter, new ArrayList<>())).isInstanceOf(OllamaUnavailableException.class)
                .hasMessageNotContaining(SECRET_TEXT);
    }

    @Test
    void unreachableBackendIsUnavailable() {
        OllamaChatAdapter adapter = adapter("http://localhost:1");

        assertThatThrownBy(() -> run(adapter, new ArrayList<>())).isInstanceOf(OllamaUnavailableException.class);
        assertThatThrownBy(adapter::assertReady).isInstanceOf(OllamaUnavailableException.class);
    }

    @Test
    void assertReadyChecksCompletionCapability(WireMockRuntimeInfo wm) {
        OllamaChatAdapter adapter = adapter(wm.getHttpBaseUrl());

        stubShow(200, "{\"license\":\"" + SECRET_TEXT + "\",\"capabilities\":[\"completion\",\"tools\"]}");
        adapter.assertReady();

        stubShow(200, "{\"capabilities\":[\"embedding\"]}");
        assertThatThrownBy(adapter::assertReady).isInstanceOf(ModelNotAvailableException.class);

        stubShow(404, "{\"error\":\"" + SECRET_TEXT + "\"}");
        assertThatThrownBy(adapter::assertReady).isInstanceOf(ModelNotAvailableException.class)
                .hasMessageNotContaining(SECRET_TEXT);

        stubShow(500, "{\"error\":\"" + SECRET_TEXT + "\"}");
        assertThatThrownBy(adapter::assertReady).isInstanceOf(OllamaUnavailableException.class)
                .hasMessageNotContaining(SECRET_TEXT);
    }

    @Test
    void cancellingClosesTheStreamAndFakeObservesTheAbort() throws Exception {
        try (FakeOllamaServer fake = FakeOllamaServer.start()) {
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                lines.add(FakeOllamaServer.tokenLine("t" + i));
            }
            fake.chat(lines, Duration.ofMillis(100));
            Cancellation cancel = new Cancellation();
            CountDownLatch firstToken = new CountDownLatch(1);
            AtomicReference<Completion> result = new AtomicReference<>();
            Thread reader = Thread.ofVirtual().start(() -> result.set(adapter(fake.baseUrl()).stream(MESSAGES, OPTIONS,
                    token -> firstToken.countDown(), cancel)));

            assertThat(firstToken.await(3, TimeUnit.SECONDS)).isTrue();
            long cancelledAt = System.nanoTime();
            cancel.cancel(CancelReason.DEADLINE);
            reader.join(2000);

            assertThat(reader.isAlive()).isFalse();
            assertThat(Duration.ofNanos(System.nanoTime() - cancelledAt)).isLessThan(Duration.ofSeconds(2));
            assertThat(result.get()).isEqualTo(Completion.cancelled(CancelReason.DEADLINE));
            assertThat(fake.awaitChatAborted(Duration.ofSeconds(3))).isTrue();
        }
    }

    @Test
    void cancellationBeforeHeadersReturnsCancelledOnceHeadersArrive() throws Exception {
        try (FakeOllamaServer fake = FakeOllamaServer.start()) {
            fake.chatHeaderDelay(Duration.ofSeconds(1));
            fake.chat(FakeOllamaServer.answer("a", "b"), Duration.ofMillis(200));
            Cancellation cancel = new Cancellation();
            AtomicReference<Completion> result = new AtomicReference<>();
            Thread reader = Thread.ofVirtual().start(() -> result.set(adapter(fake.baseUrl()).stream(MESSAGES, OPTIONS,
                    token -> { }, cancel)));

            Thread.sleep(200);
            cancel.cancel(CancelReason.CLIENT_GONE);
            reader.join(5000);

            assertThat(reader.isAlive()).isFalse();
            assertThat(result.get()).isEqualTo(Completion.cancelled(CancelReason.CLIENT_GONE));
        }
    }
}
