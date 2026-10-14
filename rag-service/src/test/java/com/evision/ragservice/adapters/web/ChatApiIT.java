package com.evision.ragservice.adapters.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evision.ragservice.FakeOllamaServer;
import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.SseEvents;
import com.evision.ragservice.application.ClientService;
import com.evision.ragservice.application.CollectionService;
import com.evision.ragservice.application.GenerationGate;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ChatApiIT {

    private static final String MODEL = "nomic-embed-text";
    private static final int DIMENSIONS = 768;
    private static final JsonMapper JSON = JsonMapper.builder().build();

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
    @Autowired
    private GenerationGate gate;

    private UUID clientA;
    private String keyA;

    @BeforeEach
    void setUp() {
        ollama.resetAll();
        jdbc.sql("DELETE FROM chunk").update();
        jdbc.sql("DELETE FROM document").update();
        jdbc.sql("DELETE FROM collection").update();
        jdbc.sql("DELETE FROM client_app").update();
        ClientService.IssuedKey issuedA = clients.register("client-a");
        clientA = issuedA.client().id();
        keyA = issuedA.apiKey();
        UUID clientB = clients.register("client-b").client().id();
        seed(collections.put(clientA, "docs", null).collection().id(), "doc-e0", unit(1f, 0f));
        seed(collections.put(clientA, "docs2", null).collection().id(), "doc-mix", unit(0.7071068f, 0.7071068f));
        seed(collections.put(clientB, "b-docs", null).collection().id(), "doc-b", unit(1f, 0f));
    }

    private static float[] unit(float x, float y) {
        float[] vector = new float[DIMENSIONS];
        vector[0] = x;
        vector[1] = y;
        return vector;
    }

    private void seed(UUID collectionId, String externalId, float[] vector) {
        String text = "text of " + externalId;
        documents.upsert(collectionId,
                new DocumentContent(externalId, externalId, text, "https://example.test/" + externalId, Map.of()),
                ContentHash.of(externalId, text));
        for (ClaimedDocument claimed : queue.claim(10, Duration.ofMinutes(10))) {
            queue.complete(claimed.id(), claimed.contentHash(), MODEL,
                    List.of(new ChunkEmbedding(0, claimed.text(), vector)));
        }
    }

    private static void stubEmbed(int hotIndex) {
        String vector = IntStream.range(0, DIMENSIONS).mapToObj(i -> i == hotIndex ? "1.0" : "0.0")
                .collect(Collectors.joining(",", "[", "]"));
        ollama.stubFor(WireMock.post(urlEqualTo("/api/embed")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"model\":\"" + MODEL + "\",\"embeddings\":[" + vector + "]}")));
    }

    private static void stubShow(int status, String body) {
        ollama.stubFor(WireMock.post(urlEqualTo("/api/show")).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static void stubReady() {
        stubShow(200, "{\"capabilities\":[\"completion\"]}");
    }

    private static void stubChat(String... lines) {
        ollama.stubFor(WireMock.post(urlEqualTo("/api/chat")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/x-ndjson").withBody(String.join("\n", lines) + "\n")));
    }

    private static void stubHappyAnswer() {
        stubChat(FakeOllamaServer.tokenLine("Release 4.2.0 "), FakeOllamaServer.tokenLine("[1]."),
                FakeOllamaServer.doneLine("stop", 120, 8));
    }

    private ResultActions chat(String key, String body) throws Exception {
        return mvc.perform(post("/v1/chat").header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<SseEvents.Event> stream(String body) throws Exception {
        MvcResult result = chat(keyA, body).andExpect(request().asyncStarted()).andReturn();
        result.getAsyncResult(10_000);
        return SseEvents.parse(result.getResponse().getContentAsString());
    }

    private static List<String> names(List<SseEvents.Event> events) {
        return events.stream().map(SseEvents.Event::name).toList();
    }

    private static JsonNode data(List<SseEvents.Event> events, String name) {
        return JSON.readTree(events.stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow().data());
    }

    @Test
    void streamsStatusTokensCitationsAndDoneInOrder() throws Exception {
        stubReady();
        stubEmbed(0);
        stubHappyAnswer();

        MvcResult result = chat(keyA, "{\"collections\":[\"docs\",\"docs2\"],\"question\":\"release?\"}")
                .andExpect(request().asyncStarted()).andReturn();
        result.getAsyncResult(10_000);

        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(result.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache");
        List<SseEvents.Event> events = SseEvents.parse(result.getResponse().getContentAsString());
        assertThat(names(events)).startsWith("status", "status", "token").endsWith("token", "citations", "done");
        assertThat(data(events, "status").get("phase").asString()).isEqualTo("SEARCHING");
        JsonNode first = data(events, "citations").get("items").get(0);
        assertThat(first.get("documentId").asString()).isEqualTo("doc-e0");
        assertThat(first.get("collection").asString()).isEqualTo("docs");
        assertThat(data(events, "done").get("refusal").asBoolean()).isFalse();
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void callerCannotChooseTheChatModel() throws Exception {
        stubReady();
        stubEmbed(0);
        stubHappyAnswer();

        List<SseEvents.Event> events = stream(
                "{\"collections\":[\"docs\"],\"question\":\"release?\",\"model\":\"evil\"}");

        assertThat(names(events)).contains("done");
        ollama.verify(postRequestedFor(urlEqualTo("/api/chat"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("qwen3:4b"))));
    }

    @Test
    void anotherClientsAndUnknownCollectionsAreIndistinguishableNotFound() throws Exception {
        stubReady();
        String foreign = chat(keyA, "{\"collections\":[\"b-docs\"],\"question\":\"q\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.reason").value("COLLECTION_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String unknown = chat(keyA, "{\"collections\":[\"nope\"],\"question\":\"q\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.reason").value("COLLECTION_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();

        assertThat(JSON.readTree(foreign).get("title")).isEqualTo(JSON.readTree(unknown).get("title"));
    }

    @Test
    void malformedRequestsAreValidationFailures() throws Exception {
        stubReady();
        chat(keyA, "{\"collections\":[],\"question\":\"q\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
        chat(keyA, "{\"collections\":[\"a b\"],\"question\":\"q\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
        chat(keyA, "{\"collections\":[\"docs\"],\"question\":\"q\",\"history\":[{\"role\":\"system\",\"content\":\"x\"}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void configuredCapsAreInvalidContent() throws Exception {
        stubReady();
        chat(keyA, "{\"collections\":[\"docs\"],\"question\":\"q\",\"k\":9}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("INVALID_CONTENT"));
        String turns = IntStream.range(0, 21).mapToObj(i -> "{\"role\":\"user\",\"content\":\"x\"}")
                .collect(Collectors.joining(",", "[", "]"));
        chat(keyA, "{\"collections\":[\"docs\"],\"question\":\"q\",\"history\":" + turns + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("INVALID_CONTENT"));
    }

    @Test
    void mixedEmbeddingModelsAreRejected() throws Exception {
        stubReady();
        jdbc.sql("UPDATE collection SET embedding_model = 'other-model' WHERE name = 'docs2'").update();

        chat(keyA, "{\"collections\":[\"docs\",\"docs2\"],\"question\":\"q\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("MIXED_EMBEDDING_MODELS"));
    }

    @Test
    void ollamaOutageAndMissingModelAreServiceUnavailableProblems() throws Exception {
        stubShow(500, "{}");
        chat(keyA, "{\"collections\":[\"docs\"],\"question\":\"q\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.reason").value("OLLAMA_UNAVAILABLE"));
        stubShow(404, "{}");
        chat(keyA, "{\"collections\":[\"docs\"],\"question\":\"q\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.reason").value("MODEL_NOT_AVAILABLE"));

        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void aClientAtItsLimitGetsTooManyRequestsEvenWhenAcceptingEventStream() throws Exception {
        stubReady();
        assertThat(gate.tryAcquire(clientA)).isTrue();
        try {
            chat(keyA, "{\"collections\":[\"docs\"],\"question\":\"q\"}")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.reason").value("TOO_MANY_REQUESTS"));
            mvc.perform(post("/v1/chat").header(HttpHeaders.AUTHORIZATION, "Bearer " + keyA)
                            .accept(MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"collections\":[\"docs\"],\"question\":\"q\"}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        } finally {
            gate.release(clientA);
        }
    }

    @Test
    void weakRetrievalRefusesWithoutCallingTheModel() throws Exception {
        stubReady();
        stubEmbed(5);

        List<SseEvents.Event> events = stream("{\"collections\":[\"docs\"],\"question\":\"unrelated\"}");

        assertThat(names(events)).contains("token", "citations", "done");
        assertThat(data(events, "citations").get("items")).isEmpty();
        assertThat(data(events, "done").get("refusal").asBoolean()).isTrue();
        assertThat(data(events, "done").get("refusalLayer").asString()).isEqualTo("GATE");
        ollama.verify(0, postRequestedFor(urlEqualTo("/api/chat")));
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void generationFailureAfterOpenIsAnErrorEventWithoutDone() throws Exception {
        stubReady();
        stubEmbed(0);
        stubChat(FakeOllamaServer.tokenLine("Partial "), "{\"error\":\"boom\"}");

        List<SseEvents.Event> events = stream("{\"collections\":[\"docs\"],\"question\":\"release?\"}");

        assertThat(names(events)).contains("error").doesNotContain("done");
        assertThat(data(events, "error").get("reason").asString()).isEqualTo("GENERATION_FAILED");
        assertThat(gate.anyInFlight()).isFalse();
    }
}
