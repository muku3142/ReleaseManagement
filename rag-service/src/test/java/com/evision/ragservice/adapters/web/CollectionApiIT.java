package com.evision.ragservice.adapters.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.application.ClientService;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CollectionApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ClientService clients;

    private String keyA;
    private String keyB;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM chunk").update();
        jdbc.sql("DELETE FROM document").update();
        jdbc.sql("DELETE FROM collection").update();
        jdbc.sql("DELETE FROM client_app").update();
        keyA = clients.register("client-a").apiKey();
        keyB = clients.register("client-b").apiKey();
    }

    private static String bearer(String key) {
        return "Bearer " + key;
    }

    private void putDocs(String key) throws Exception {
        mvc.perform(put("/v1/collections/docs").header(HttpHeaders.AUTHORIZATION, bearer(key)))
                .andExpect(status().isCreated());
    }

    @Test
    void putCreatesThenIsIdempotentAndRejectsUnknownModels() throws Exception {
        mvc.perform(put("/v1/collections/docs").header(HttpHeaders.AUTHORIZATION, bearer(keyA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.embeddingModel").value("nomic-embed-text"));
        mvc.perform(put("/v1/collections/docs").header(HttpHeaders.AUTHORIZATION, bearer(keyA)))
                .andExpect(status().isOk());
        mvc.perform(put("/v1/collections/other").header(HttpHeaders.AUTHORIZATION, bearer(keyA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"embeddingModel\":\"other-model\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("MODEL_NOT_ALLOWED"));
    }

    @Test
    void anotherClientsCollectionIsNotFoundNeverForbidden() throws Exception {
        putDocs(keyA);

        mvc.perform(get("/v1/collections/docs").header(HttpHeaders.AUTHORIZATION, bearer(keyB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("COLLECTION_NOT_FOUND"));
        mvc.perform(post("/v1/collections/docs/reindex").header(HttpHeaders.AUTHORIZATION, bearer(keyB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("COLLECTION_NOT_FOUND"));
        mvc.perform(get("/v1/collections").header(HttpHeaders.AUTHORIZATION, bearer(keyB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void sameCollectionNameIsIndependentPerClient() throws Exception {
        putDocs(keyA);
        putDocs(keyB);

        mvc.perform(get("/v1/collections").header(HttpHeaders.AUTHORIZATION, bearer(keyA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("docs"));
    }

    @Test
    void statusShowsCountsAndReindexRequeuesEverything() throws Exception {
        putDocs(keyA);
        jdbc.sql("""
                INSERT INTO document (collection_id, external_id, title, body, content_hash, status, indexed_at)
                SELECT id, d.ext, 't', 'b', '\\x01', d.st, CASE WHEN d.st = 'INDEXED' THEN now() END
                FROM collection, (VALUES ('1', 'PENDING'), ('2', 'INDEXED'), ('3', 'FAILED')) AS d(ext, st)
                WHERE collection.name = 'docs'
                """).update();

        mvc.perform(get("/v1/collections/docs").header(HttpHeaders.AUTHORIZATION, bearer(keyA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.indexed").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.lastIndexedAt").isNotEmpty());

        mvc.perform(post("/v1/collections/docs/reindex").header(HttpHeaders.AUTHORIZATION, bearer(keyA)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requeued").value(3));
        mvc.perform(get("/v1/collections/docs").header(HttpHeaders.AUTHORIZATION, bearer(keyA)))
                .andExpect(jsonPath("$.pending").value(3));
    }

    @Test
    void invalidNameIsRejected() throws Exception {
        mvc.perform(put("/v1/collections/{name}", "bad name!").header(HttpHeaders.AUTHORIZATION, bearer(keyA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void missingKeyIsUnauthorized() throws Exception {
        mvc.perform(get("/v1/collections")).andExpect(status().isUnauthorized());
    }
}
