package com.evision.ragservice.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.application.ClientService;
import com.evision.ragservice.application.CollectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DocumentApiIT {

    private static final String DOC = "/v1/collections/docs/documents/doc-1";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ClientService clients;

    @Autowired
    private CollectionService collections;

    private String keyA;
    private String keyB;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM chunk").update();
        jdbc.sql("DELETE FROM document").update();
        jdbc.sql("DELETE FROM collection").update();
        jdbc.sql("DELETE FROM client_app").update();
        var a = clients.register("client-a");
        keyA = a.apiKey();
        keyB = clients.register("client-b").apiKey();
        collections.put(a.client().id(), "docs", null);
    }

    private ResultActions push(String key, String uri, String body) throws Exception {
        return mvc.perform(put(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(String text) {
        return "{\"title\":\"T\",\"text\":\"" + text + "\"}";
    }

    private ResultActions pushA(String text) throws Exception {
        return push(keyA, DOC, body(text));
    }

    @Test
    void pushIsAcceptedPendingWithoutOllamaAndRoundTripsWithoutText() throws Exception {
        push(keyA, DOC, "{\"title\":\"T\",\"text\":\"hello\",\"url\":\"https://x/1\","
                + "\"metadata\":{\"release_id\":\"R1\",\"n\":2}}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("doc-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(get(DOC).header(HttpHeaders.AUTHORIZATION, "Bearer " + keyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.metadata.release_id").value("R1"))
                .andExpect(jsonPath("$.metadata.n").value(2))
                .andExpect(jsonPath("$.text").doesNotExist());
    }

    @Test
    void unknownCollectionIsNotFoundAndNeverCreated() throws Exception {
        long before = jdbc.sql("SELECT count(*) FROM collection").query(Long.class).single();
        push(keyA, "/v1/collections/nope/documents/x", body("hello"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("COLLECTION_NOT_FOUND"));
        assertThat(jdbc.sql("SELECT count(*) FROM collection").query(Long.class).single()).isEqualTo(before);
    }

    @Test
    void anotherClientsCollectionIsNotFound() throws Exception {
        pushA("hello").andExpect(status().isAccepted());

        push(keyB, DOC, body("hello")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("COLLECTION_NOT_FOUND"));
        mvc.perform(get(DOC).header(HttpHeaders.AUTHORIZATION, "Bearer " + keyB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("COLLECTION_NOT_FOUND"));
        mvc.perform(delete(DOC).header(HttpHeaders.AUTHORIZATION, "Bearer " + keyB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("COLLECTION_NOT_FOUND"));
    }

    @Test
    void oversizedTextIsRejectedByCharsAndByUtf8Bytes() throws Exception {
        pushA("a".repeat(1_048_577)).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.reason").value("PAYLOAD_TOO_LARGE"));
        pushA("é".repeat(600_000)).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.reason").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void oversizedRequestIsRejectedByTheFilterBeforeParsing() throws Exception {
        push(keyA, DOC, body("a".repeat(2_200_000))).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.reason").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void invalidContentIsRejectedAtTheBoundary() throws Exception {
        pushA("bad\\u0000text").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("INVALID_CONTENT"));
        pushA("   ").andExpect(status().isBadRequest());
        push(keyA, DOC, "{\"title\":\"T\",\"text\":\"x\",\"metadata\":{\"a\":{\"b\":1}}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("INVALID_CONTENT"));
        push(keyA, DOC, "{\"title\":\"T\",\"text\":\"x\",\"url\":\"javascript:alert(1)\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
        push(keyA, DOC, "{\"text\":\"x\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void deleteRemovesTheDocument() throws Exception {
        pushA("hello").andExpect(status().isAccepted());

        mvc.perform(delete(DOC).header(HttpHeaders.AUTHORIZATION, "Bearer " + keyA))
                .andExpect(status().isNoContent());
        mvc.perform(get(DOC).header(HttpHeaders.AUTHORIZATION, "Bearer " + keyA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("DOCUMENT_NOT_FOUND"));
        mvc.perform(delete(DOC).header(HttpHeaders.AUTHORIZATION, "Bearer " + keyA))
                .andExpect(status().isNotFound());
    }

    @Test
    void identicalRePushOfAnIndexedDocumentStaysIndexed() throws Exception {
        pushA("hello").andExpect(status().isAccepted());
        jdbc.sql("UPDATE document SET status = 'INDEXED', indexed_at = now()").update();

        pushA("hello").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("INDEXED"));
    }
}
