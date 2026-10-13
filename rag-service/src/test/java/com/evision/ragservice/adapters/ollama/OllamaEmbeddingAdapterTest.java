package com.evision.ragservice.adapters.ollama;

import com.evision.ragservice.domain.EmbeddingPurpose;
import com.evision.ragservice.domain.EmbeddingRejectedException;
import com.evision.ragservice.domain.EmbeddingUnavailableException;
import com.evision.ragservice.ports.EmbeddingPort;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class OllamaEmbeddingAdapterTest {

    private static final String MODEL = "nomic-embed-text";
    private static final String SECRET_TEXT = "confidential-input-text";

    private OllamaEmbeddingAdapter adapter(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        return new OllamaEmbeddingAdapter(RestClient.builder().requestFactory(requestFactory).build(), baseUrl, 32,
                Duration.ofSeconds(1), "search_document: ", "search_query: ");
    }

    private OllamaEmbeddingAdapter adapter(WireMockRuntimeInfo wm) {
        return adapter(wm.getHttpBaseUrl());
    }

    private static String vector(int dimensions, float seed) {
        return IntStream.range(0, dimensions).mapToObj(i -> Float.toString(seed + i))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String embeddings(int count, int dimensions) {
        String vectors = IntStream.range(0, count).mapToObj(i -> vector(dimensions, i * 1000f))
                .collect(Collectors.joining(","));
        return "{\"model\":\"nomic-embed-text\",\"embeddings\":[" + vectors + "],\"total_duration\":1}";
    }

    private static void stubEmbed(int status, String body) {
        stubFor(post(urlEqualTo("/api/embed")).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    void embedsInInputOrderWithDocumentPrefixAndTruncateFalse(WireMockRuntimeInfo wm) {
        stubEmbed(200, embeddings(2, 768));

        List<float[]> result = adapter(wm).embed(MODEL, EmbeddingPurpose.DOCUMENT, List.of("a", "b"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).hasSize(768);
        assertThat(result.get(0)[1]).isEqualTo(1f);
        assertThat(result.get(1)[0]).isEqualTo(1000f);
        verify(postRequestedFor(urlEqualTo("/api/embed"))
                .withRequestBody(matchingJsonPath("$.truncate", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.model", equalTo(MODEL)))
                .withRequestBody(matchingJsonPath("$.input[0]", equalTo("search_document: a")))
                .withRequestBody(matchingJsonPath("$.input[1]", equalTo("search_document: b"))));
    }

    @Test
    void requestCarriesNoOptionsOrKeepAlive(WireMockRuntimeInfo wm) {
        stubEmbed(200, embeddings(1, 768));

        adapter(wm).embed(MODEL, EmbeddingPurpose.DOCUMENT, List.of("a"));

        verify(postRequestedFor(urlEqualTo("/api/embed"))
                .withRequestBody(equalToJson("{\"model\":\"nomic-embed-text\",\"input\":[\"search_document: a\"],"
                        + "\"truncate\":false}", true, false)));
        verify(0, postRequestedFor(urlEqualTo("/api/embed")).withRequestBody(matchingJsonPath("$.options")));
        verify(0, postRequestedFor(urlEqualTo("/api/embed")).withRequestBody(matchingJsonPath("$.keep_alive")));
    }

    @Test
    void queryPurposeUsesQueryPrefix(WireMockRuntimeInfo wm) {
        stubEmbed(200, embeddings(1, 768));

        adapter(wm).embed(MODEL, EmbeddingPurpose.QUERY, List.of("q"));

        verify(postRequestedFor(urlEqualTo("/api/embed"))
                .withRequestBody(matchingJsonPath("$.input[0]", equalTo("search_query: q"))));
    }

    @Test
    void splitsIntoBatchesAndKeepsOrder(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/api/embed")).inScenario("batches").whenScenarioStateIs("Started")
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(embeddings(32, 768)))
                .willSetStateTo("second"));
        stubFor(post(urlEqualTo("/api/embed")).inScenario("batches").whenScenarioStateIs("second")
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(embeddings(32, 768)))
                .willSetStateTo("third"));
        stubFor(post(urlEqualTo("/api/embed")).inScenario("batches").whenScenarioStateIs("third")
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(embeddings(6, 768))));
        List<String> inputs = IntStream.range(0, 70).mapToObj(i -> "chunk-" + i).toList();

        List<float[]> result = adapter(wm).embed(MODEL, EmbeddingPurpose.DOCUMENT, inputs);

        assertThat(result).hasSize(70);
        assertThat(result.get(32)[0]).isEqualTo(0f);
        assertThat(result.get(64)[0]).isEqualTo(0f);
        assertThat(result.get(69)[0]).isEqualTo(5000f);
        verify(3, postRequestedFor(urlEqualTo("/api/embed")));
        verify(1, postRequestedFor(urlEqualTo("/api/embed"))
                .withRequestBody(matchingJsonPath("$.input[31]", equalTo("search_document: chunk-31")))
                .withRequestBody(matchingJsonPath("$[?(@.input.size() == 32)]")));
        verify(1, postRequestedFor(urlEqualTo("/api/embed"))
                .withRequestBody(matchingJsonPath("$[?(@.input.size() == 6)]")));
    }

    @Test
    void status400IsAContentError(WireMockRuntimeInfo wm) {
        stubEmbed(400, "{\"error\":\"the input length exceeds the context length\"}");

        assertThatThrownBy(() -> adapter(wm).embed(MODEL, EmbeddingPurpose.DOCUMENT, List.of(SECRET_TEXT)))
                .isInstanceOf(EmbeddingRejectedException.class)
                .hasMessageNotContaining(SECRET_TEXT);
    }

    @Test
    void status404ModelNotFoundIsAnOutage(WireMockRuntimeInfo wm) {
        stubEmbed(404, "{\"error\":\"model \\\"x\\\" not found, try pulling it first\"}");

        assertThatThrownBy(() -> adapter(wm).embed(MODEL, EmbeddingPurpose.DOCUMENT, List.of(SECRET_TEXT)))
                .isInstanceOf(EmbeddingUnavailableException.class)
                .hasMessageNotContaining(SECRET_TEXT);
    }

    @Test
    void status500IsAnOutage(WireMockRuntimeInfo wm) {
        stubEmbed(500, "{\"error\":\"boom\"}");

        assertThatThrownBy(() -> adapter(wm).embed(MODEL, EmbeddingPurpose.DOCUMENT, List.of(SECRET_TEXT)))
                .isInstanceOf(EmbeddingUnavailableException.class)
                .hasMessageNotContaining(SECRET_TEXT);
    }

    @Test
    void readTimeoutIsAnOutage(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/api/embed")).willReturn(aResponse().withFixedDelay(3000)
                .withHeader("Content-Type", "application/json").withBody(embeddings(1, 768))));

        assertThatThrownBy(() -> adapter(wm).embed(MODEL, EmbeddingPurpose.DOCUMENT, List.of(SECRET_TEXT)))
                .isInstanceOf(EmbeddingUnavailableException.class)
                .hasMessageNotContaining(SECRET_TEXT);
    }

    @Test
    void connectionRefusedIsAnOutage() {
        assertThatThrownBy(() -> adapter("http://localhost:1")
                .embed(MODEL, EmbeddingPurpose.DOCUMENT, List.of(SECRET_TEXT)))
                .isInstanceOf(EmbeddingUnavailableException.class)
                .hasMessageNotContaining(SECRET_TEXT);
    }

    @Test
    void wrongDimensionIsAnOutage(WireMockRuntimeInfo wm) {
        stubEmbed(200, embeddings(1, 767));

        assertThatThrownBy(() -> adapter(wm).embed(MODEL, EmbeddingPurpose.DOCUMENT, List.of(SECRET_TEXT)))
                .isInstanceOf(EmbeddingUnavailableException.class)
                .hasMessageNotContaining(SECRET_TEXT);
    }

    @Test
    void wrongCountIsAnOutage(WireMockRuntimeInfo wm) {
        stubEmbed(200, embeddings(1, 768));

        assertThatThrownBy(() -> adapter(wm).embed(MODEL, EmbeddingPurpose.DOCUMENT, List.of("a", "b")))
                .isInstanceOf(EmbeddingUnavailableException.class);
    }

    @Test
    void availableWhenVersionEndpointAnswers(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/api/version")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"version\":\"0.34.0\"}")));

        assertThat(adapter(wm).isAvailable()).isTrue();
    }

    @Test
    void unavailableOnServerError(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/api/version")).willReturn(aResponse().withStatus(500)));

        assertThat(adapter(wm).isAvailable()).isFalse();
    }

    @Test
    void unavailableWhenConnectionRefused() {
        assertThat(adapter("http://localhost:1").isAvailable()).isFalse();
    }

    @Test
    void dimensionsConstantMatchesSchema() {
        assertThat(EmbeddingPort.DIMENSIONS).isEqualTo(768);
    }
}
