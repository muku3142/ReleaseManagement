package com.evision.releasemgmt.adapters.rag;

import com.evision.releasemgmt.config.RagRestClientConfig;
import com.evision.releasemgmt.domain.assistant.IndexCollection;
import com.evision.releasemgmt.domain.assistant.IndexDocument;
import com.evision.releasemgmt.domain.assistant.RagServiceException;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class RagIndexAdapterTest {

    private static final String KEY = "test-key";
    private static final String DOC_PATH = "/v1/collections/rm-tickets/documents/ticket%3AR%3APAY-1";
    private static final IndexDocument DOC = new IndexDocument(IndexCollection.TICKETS, "ticket:R:PAY-1",
            "PAY-1 Fix", "secret-document-text", Map.of("releaseId", "R"));

    private static RagIndexAdapter adapter(String baseUrl) {
        return new RagIndexAdapter(
                RagRestClientConfig.ragClient(baseUrl, KEY, Duration.ofSeconds(1), Duration.ofSeconds(1)),
                "nomic-embed-text");
    }

    private static RagIndexAdapter adapter(WireMockRuntimeInfo wm) {
        return adapter(wm.getHttpBaseUrl());
    }

    @Test
    void ensureCollectionPutsEmbeddingModelWithBearerKey(WireMockRuntimeInfo wm) {
        stubFor(put(urlPathEqualTo("/v1/collections/rm-tickets")).willReturn(aResponse().withStatus(200)));

        adapter(wm).ensureCollection(IndexCollection.TICKETS);

        verify(putRequestedFor(urlPathEqualTo("/v1/collections/rm-tickets"))
                .withHeader("Authorization", equalTo("Bearer " + KEY))
                .withRequestBody(matchingJsonPath("$.embeddingModel", equalTo("nomic-embed-text"))));
    }

    @Test
    void ensureCollectionConflictIsReported(WireMockRuntimeInfo wm) {
        stubFor(put(urlPathEqualTo("/v1/collections/rm-tickets")).willReturn(aResponse().withStatus(409)));

        assertThatThrownBy(() -> adapter(wm).ensureCollection(IndexCollection.TICKETS))
                .isInstanceOfSatisfying(RagServiceException.class, e -> assertThat(e.status()).isEqualTo(409));
    }

    @Test
    void putSendsTitleTextMetadataAndAcceptsEncodedId(WireMockRuntimeInfo wm) {
        stubFor(put(urlEqualTo(DOC_PATH)).willReturn(aResponse().withStatus(202)));

        adapter(wm).put(DOC);

        verify(putRequestedFor(urlEqualTo(DOC_PATH))
                .withRequestBody(matchingJsonPath("$.title", equalTo("PAY-1 Fix")))
                .withRequestBody(matchingJsonPath("$.text", equalTo("secret-document-text")))
                .withRequestBody(matchingJsonPath("$.metadata.releaseId", equalTo("R"))));
    }

    @Test
    void putFailuresCarryStatusAndNoSecrets(WireMockRuntimeInfo wm) {
        for (int status : new int[]{413, 503}) {
            stubFor(put(urlEqualTo(DOC_PATH)).willReturn(aResponse().withStatus(status)));

            assertThatThrownBy(() -> adapter(wm).put(DOC))
                    .isInstanceOfSatisfying(RagServiceException.class, e -> {
                        assertThat(e.status()).isEqualTo(status);
                        assertThat(e.getMessage()).doesNotContain(KEY).doesNotContain("secret-document-text");
                    });
        }
    }

    @Test
    void deleteTreatsNotFoundAsSuccess(WireMockRuntimeInfo wm) {
        stubFor(delete(urlEqualTo(DOC_PATH)).willReturn(aResponse().withStatus(204)));
        assertThatCode(() -> adapter(wm).delete(DOC.ref())).doesNotThrowAnyException();

        stubFor(delete(urlEqualTo(DOC_PATH)).willReturn(aResponse().withStatus(404)));
        assertThatCode(() -> adapter(wm).delete(DOC.ref())).doesNotThrowAnyException();
        verify(deleteRequestedFor(urlEqualTo(DOC_PATH)));
    }

    @Test
    void deleteServerErrorIsReported(WireMockRuntimeInfo wm) {
        stubFor(delete(urlEqualTo(DOC_PATH)).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter(wm).delete(DOC.ref()))
                .isInstanceOfSatisfying(RagServiceException.class, e -> assertThat(e.status()).isEqualTo(500));
    }

    @Test
    void readTimeoutBecomesStatusZero(WireMockRuntimeInfo wm) {
        stubFor(put(urlEqualTo(DOC_PATH)).willReturn(aResponse().withStatus(202).withFixedDelay(3000)));

        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter(wm).put(DOC))
                .isInstanceOfSatisfying(RagServiceException.class, e -> assertThat(e.status()).isZero());
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2800));
    }

    @Test
    void connectionRefusedBecomesStatusZero() {
        assertThatThrownBy(() -> adapter("http://localhost:1").put(DOC))
                .isInstanceOfSatisfying(RagServiceException.class, e -> {
                    assertThat(e.status()).isZero();
                    assertThat(e.getMessage()).doesNotContain(KEY);
                });
    }
}
