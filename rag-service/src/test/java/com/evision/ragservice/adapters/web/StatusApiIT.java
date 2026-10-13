package com.evision.ragservice.adapters.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.application.ClientService;
import com.evision.ragservice.application.CollectionService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StatusApiIT {

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

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM chunk").update();
        jdbc.sql("DELETE FROM document").update();
        jdbc.sql("DELETE FROM collection").update();
        jdbc.sql("DELETE FROM client_app").update();
        ClientService.IssuedKey a = clients.register("client-a");
        ClientService.IssuedKey b = clients.register("client-b");
        keyA = a.apiKey();
        insertDocuments(collections.put(a.client().id(), "docs", null).collection().id(), "PENDING", "PENDING", "FAILED");
        insertDocuments(collections.put(b.client().id(), "docs", null).collection().id(), "PENDING");
    }

    private void insertDocuments(UUID collectionId, String... statuses) {
        for (int i = 0; i < statuses.length; i++) {
            jdbc.sql("""
                    INSERT INTO document (collection_id, external_id, title, body, content_hash, status)
                    VALUES (:collection, :ext, 't', 'b', '\\x01', :status)
                    """)
                    .param("collection", collectionId).param("ext", "d" + i).param("status", statuses[i]).update();
        }
    }

    @Test
    void statusShowsOllamaDownAndOnlyTheCallersQueueDepth() throws Exception {
        mvc.perform(get("/v1/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + keyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ollama").value("DOWN"))
                .andExpect(jsonPath("$.pending").value(2))
                .andExpect(jsonPath("$.failed").value(1));
    }

    @Test
    void readinessStaysUpWhileOllamaIsDown() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessIsUp() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void statusRequiresAKey() throws Exception {
        mvc.perform(get("/v1/status")).andExpect(status().isUnauthorized());
    }
}
