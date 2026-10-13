package com.evision.ragservice.adapters.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.application.ClientService;
import com.evision.ragservice.application.CollectionService;
import com.evision.ragservice.domain.ChunkEmbedding;
import com.evision.ragservice.domain.ClaimedDocument;
import com.evision.ragservice.domain.ContentHash;
import com.evision.ragservice.domain.DocumentContent;
import com.evision.ragservice.ports.DocumentStore;
import com.evision.ragservice.ports.IndexQueue;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SearchApiIT {

    private static final String MODEL = "nomic-embed-text";
    private static final int DIMENSIONS = 768;

    @RegisterExtension
    static WireMockExtension ollama = WireMockExtension.newInstance().options(
            com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort()).build();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @DynamicPropertySource
    static void ollamaUrl(DynamicPropertyRegistry registry) {
        registry.add("rag.ollama.base-url", ollama::baseUrl);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private ClientService clients;
    @Autowired
    private CollectionService collections;
    @Autowired
    private DocumentStore documents;
    @Autowired
    private IndexQueue queue;

    private String keyA;
    private String keyB;

    @BeforeEach
    void setUp() {
        ollama.resetAll();
        jdbc.sql("DELETE FROM chunk").update();
        jdbc.sql("DELETE FROM document").update();
        jdbc.sql("DELETE FROM collection").update();
        jdbc.sql("DELETE FROM client_app").update();
        ClientService.IssuedKey issuedA = clients.register("client-a");
        UUID clientA = issuedA.client().id();
        keyA = issuedA.apiKey();
        keyB = clients.register("client-b").apiKey();
        UUID collectionId = collections.put(clientA, "docs", null).collection().id();
        seed(collectionId, "doc-e0", "R1", "A", unit(1f, 0f));
        seed(collectionId, "doc-mix", "R1", "B", unit(0.7071068f, 0.7071068f));
        seed(collectionId, "doc-e1", "R2", "C", unit(0f, 1f));
    }

    private static float[] unit(float x, float y) {
        float[] vector = new float[DIMENSIONS];
        vector[0] = x;
        vector[1] = y;
        return vector;
    }

    private void seed(UUID collectionId, String externalId, String release, String project, float[] vector) {
        String text = "text of " + externalId;
        documents.upsert(collectionId,
                new DocumentContent(externalId, externalId, text, "https://example.test/" + externalId,
                        Map.of("release_id", release, "project_key", project)),
                ContentHash.of(externalId, text));
        for (ClaimedDocument claimed : queue.claim(10, Duration.ofMinutes(10))) {
            queue.complete(claimed.id(), claimed.contentHash(), MODEL,
                    List.of(new ChunkEmbedding(0, claimed.text(), vector)));
        }
    }

    private static void stubEmbedReturningE0() {
        String vector = IntStream.range(0, DIMENSIONS).mapToObj(i -> i == 0 ? "1.0" : "0.0")
                .collect(Collectors.joining(",", "[", "]"));
        ollama.stubFor(WireMock.post(urlEqualTo("/api/embed")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"model\":\"" + MODEL + "\",\"embeddings\":[" + vector + "]}")));
    }

    private ResultActions search(String key, String body) throws Exception {
        return mvc.perform(post("/v1/collections/docs/search").header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void returnsRankedChunkHitsWithSourceReferencesAndQueryPrefix() throws Exception {
        stubEmbedReturningE0();

        search(keyA, "{\"query\":\"deploy steps\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].documentId").value("doc-e0"))
                .andExpect(jsonPath("$.results[1].documentId").value("doc-mix"))
                .andExpect(jsonPath("$.results[2].documentId").value("doc-e1"))
                .andExpect(jsonPath("$.results[0].score").value(org.hamcrest.Matchers.closeTo(1.0, 0.001)))
                .andExpect(jsonPath("$.results[0].title").value("doc-e0"))
                .andExpect(jsonPath("$.results[0].url").value("https://example.test/doc-e0"))
                .andExpect(jsonPath("$.results[0].metadata.release_id").value("R1"))
                .andExpect(jsonPath("$.results[0].chunkIndex").value(0))
                .andExpect(jsonPath("$.results[0].text").value("text of doc-e0"));

        ollama.verify(postRequestedFor(urlEqualTo("/api/embed"))
                .withRequestBody(matchingJsonPath("$.input[0]", equalTo("search_query: deploy steps"))));
    }

    @Test
    void equalityFilterNarrowsResults() throws Exception {
        stubEmbedReturningE0();

        search(keyA, "{\"query\":\"x\",\"filter\":{\"release_id\":\"R1\"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[*].metadata.release_id",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("R1"))));
    }

    @Test
    void equalityAndInListFiltersAreAnded() throws Exception {
        stubEmbedReturningE0();

        search(keyA, "{\"query\":\"x\",\"filter\":{\"release_id\":\"R1\",\"project_key\":[\"B\",\"C\"]}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].documentId").value("doc-mix"));
    }

    @Test
    void kIsHonouredDefaultedAndCapped() throws Exception {
        stubEmbedReturningE0();

        search(keyA, "{\"query\":\"x\",\"k\":1}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(1));
        search(keyA, "{\"query\":\"x\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(3));
        search(keyA, "{\"query\":\"x\",\"k\":51}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("INVALID_CONTENT"));
    }

    @Test
    void anotherClientsCollectionIsNotFoundWithoutCallingOllama() throws Exception {
        stubEmbedReturningE0();

        search(keyB, "{\"query\":\"x\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("COLLECTION_NOT_FOUND"));

        ollama.verify(0, postRequestedFor(urlEqualTo("/api/embed")));
    }

    @Test
    void ollamaOutageIsServiceUnavailableAndRejectionIsBadRequest() throws Exception {
        ollama.stubFor(WireMock.post(urlEqualTo("/api/embed")).willReturn(aResponse().withStatus(500)));
        search(keyA, "{\"query\":\"x\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("EMBEDDING_UNAVAILABLE"));

        ollama.stubFor(WireMock.post(urlEqualTo("/api/embed")).willReturn(aResponse().withStatus(400)));
        search(keyA, "{\"query\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("QUERY_REJECTED"));
    }

    @Test
    void invalidFilterAndBlankQueryAreRejected() throws Exception {
        search(keyA, "{\"query\":\"x\",\"filter\":{\"a\":{\"b\":1}}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("INVALID_CONTENT"));
        search(keyA, "{\"query\":\"  \"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void deletedDocumentNeverAppears() throws Exception {
        stubEmbedReturningE0();
        UUID collectionId = jdbc.sql("SELECT id FROM collection WHERE name = 'docs'").query(UUID.class).single();

        documents.delete(collectionId, "doc-e0");

        search(keyA, "{\"query\":\"x\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[?(@.documentId == 'doc-e0')]").isEmpty());
    }

    @Test
    void statusReportsOllamaUpWhenReachable() throws Exception {
        ollama.stubFor(WireMock.get(urlEqualTo("/api/version")).willReturn(aResponse().withStatus(200).withBody("{}")));

        mvc.perform(get("/v1/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + keyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ollama").value("UP"))
                .andExpect(jsonPath("$.pending").value(0))
                .andExpect(jsonPath("$.failed").value(0));
    }
}
