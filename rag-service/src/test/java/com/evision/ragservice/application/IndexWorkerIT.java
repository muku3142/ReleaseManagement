package com.evision.ragservice.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.domain.DocumentContent;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class IndexWorkerIT {

    private static final String EMBED = "/api/embed";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("rag.ollama.base-url", wm::baseUrl);
    }

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ClientService clients;

    @Autowired
    private CollectionService collections;

    @Autowired
    private DocumentService documents;

    @Autowired
    private IndexWorker worker;

    private UUID clientId;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM chunk").update();
        jdbc.sql("DELETE FROM document").update();
        jdbc.sql("DELETE FROM collection").update();
        jdbc.sql("DELETE FROM client_app").update();
        clientId = clients.register("client-a").client().id();
        collections.put(clientId, "docs", null);
        wm.resetAll();
        worker.runOnce();
    }

    private static MappingBuilder embedStub() {
        return post(urlEqualTo(EMBED));
    }

    private static String oneVector() {
        String vector = String.join(",", Collections.nCopies(768, "0.5"));
        return "{\"embeddings\":[[" + vector + "]]}";
    }

    private void stubOk() {
        wm.stubFor(embedStub().willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(oneVector())));
    }

    private void stubStatus(int status, String body) {
        wm.stubFor(embedStub().willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private void push(String id, String title, String text) {
        documents.push(clientId, "docs", new DocumentContent(id, title, text, null, null));
    }

    private String status(String id) {
        return jdbc.sql("SELECT status FROM document WHERE external_id = :id").param("id", id)
                .query(String.class).single();
    }

    private long embedCalls() {
        return wm.findAll(postRequestedFor(urlEqualTo(EMBED))).size();
    }

    private long chunkCount() {
        return jdbc.sql("SELECT count(*) FROM chunk").query(Long.class).single();
    }

    @Test
    void outageLeavesDocumentsPendingAndBacksOffToTheCap() {
        push("a", "A", "alpha");
        push("b", "B", "beta");
        push("c", "C", "gamma");
        stubStatus(500, "{}");

        assertThat(worker.runOnce()).isZero();
        assertThat(embedCalls()).isEqualTo(1);
        assertThat(worker.currentBackoff()).isEqualTo(Duration.ofSeconds(1));
        assertThat(jdbc.sql("SELECT count(*) FROM document WHERE status = 'PENDING' AND lease_until IS NULL")
                .query(Long.class).single()).isEqualTo(3);
        assertThat(chunkCount()).isZero();

        worker.runOnce();
        assertThat(worker.currentBackoff()).isEqualTo(Duration.ofSeconds(2));
        for (int i = 0; i < 12; i++) {
            worker.runOnce();
        }
        assertThat(worker.currentBackoff()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void unmatchedEndpointIsAnOutageNotAFailure() {
        push("a", "A", "alpha");

        assertThat(worker.runOnce()).isZero();

        assertThat(status("a")).isEqualTo("PENDING");
    }

    @Test
    void documentSpecificErrorWhileOllamaIsUpDoesNotStallTheQueue() {
        push("a", "A", "alpha");
        push("b", "B", "beta");
        wm.stubFor(get(urlEqualTo("/api/version")).willReturn(aResponse().withStatus(200)));
        stubOk();
        wm.stubFor(embedStub().withRequestBody(containing("alpha")).atPriority(1)
                .willReturn(aResponse().withStatus(500)));

        assertThat(worker.runOnce()).isEqualTo(1);

        assertThat(status("a")).isEqualTo("PENDING");
        assertThat(status("b")).isEqualTo("INDEXED");
        assertThat(worker.currentBackoff()).isNull();
        assertThat(jdbc.sql("SELECT lease_until > now() FROM document WHERE external_id = 'a'")
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void unchunkableDocumentFailsAndTheBatchContinues() {
        push("a", "A", "alpha");
        push("b", "B", "beta");
        jdbc.sql("UPDATE document SET title = repeat('t', 1400) WHERE external_id = 'a'").update();
        stubOk();

        assertThat(worker.runOnce()).isEqualTo(1);

        assertThat(status("a")).isEqualTo("FAILED");
        assertThat(jdbc.sql("SELECT status_reason FROM document WHERE external_id = 'a'").query(String.class).single())
                .contains("title is too long");
        assertThat(status("b")).isEqualTo("INDEXED");
    }

    @Test
    void unexpectedErrorKeepsTheLeaseAndTheBatchContinues() {
        push("a", "A", "alpha");
        push("b", "B", "beta");
        stubOk();
        // pgvector rejects infinite components, so complete() throws a DataAccessException
        String infinite = "{\"embeddings\":[[1e39" + ",0.5".repeat(767) + "]]}";
        wm.stubFor(embedStub().withRequestBody(containing("alpha")).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(infinite)));

        assertThat(worker.runOnce()).isEqualTo(1);

        assertThat(status("a")).isEqualTo("PENDING");
        assertThat(status("b")).isEqualTo("INDEXED");
        assertThat(jdbc.sql("SELECT lease_until > now() FROM document WHERE external_id = 'a'")
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void tickMakesNoCallWhileBackingOff() {
        push("a", "A", "alpha");
        stubStatus(500, "{}");
        worker.runOnce();
        wm.resetRequests();

        worker.tick();

        assertThat(embedCalls()).isZero();
    }

    @Test
    void recoveryIndexesPendingDocumentsAndResetsBackoff() throws Exception {
        push("a", "Title A", "body text");
        stubStatus(500, "{}");
        worker.runOnce();
        wm.resetAll();
        stubOk();

        assertThat(worker.runOnce()).isEqualTo(1);

        assertThat(status("a")).isEqualTo("INDEXED");
        assertThat(jdbc.sql("SELECT indexed_at IS NOT NULL FROM document").query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT embedding_model FROM chunk").query(String.class).list())
                .containsExactly("nomic-embed-text");
        assertThat(worker.currentBackoff()).isNull();
        wm.verify(postRequestedFor(urlEqualTo(EMBED))
                .withRequestBody(containing("\"search_document: Title A\\n\\nbody text\""))
                .withRequestBody(containing("\"truncate\":false")));
    }

    @Test
    void rejectedContentFailsUntilItChanges() {
        push("a", "A", "alpha");
        stubStatus(400, "{\"error\":\"the input length exceeds the context length\"}");

        worker.runOnce();

        assertThat(status("a")).isEqualTo("FAILED");
        assertThat(jdbc.sql("SELECT status_reason FROM document").query(String.class).single())
                .isEqualTo("Embedding model rejected the content");
        push("a", "A", "alpha");
        assertThat(status("a")).isEqualTo("PENDING");
    }

    @Test
    void unchangedRePushIsNotEmbeddedAgain() {
        push("a", "A", "alpha");
        stubOk();
        worker.runOnce();
        wm.resetRequests();

        push("a", "A", "alpha");
        worker.runOnce();

        assertThat(embedCalls()).isZero();
        assertThat(status("a")).isEqualTo("INDEXED");
    }

    @Test
    void changedRePushKeepsOldChunksUntilReplaced() {
        push("a", "A", "alpha");
        stubOk();
        worker.runOnce();

        push("a", "A", "new words");
        assertThat(status("a")).isEqualTo("PENDING");
        assertThat(jdbc.sql("SELECT content FROM chunk").query(String.class).single()).contains("alpha");

        worker.runOnce();

        assertThat(status("a")).isEqualTo("INDEXED");
        assertThat(jdbc.sql("SELECT content FROM chunk").query(String.class).single()).contains("new words");
    }

    @Test
    void deletedPendingDocumentIsNeverEmbedded() {
        push("a", "A", "alpha");
        documents.delete(clientId, "docs", "a");
        stubOk();

        worker.runOnce();

        assertThat(embedCalls()).isZero();
        assertThat(chunkCount()).isZero();
    }
}
