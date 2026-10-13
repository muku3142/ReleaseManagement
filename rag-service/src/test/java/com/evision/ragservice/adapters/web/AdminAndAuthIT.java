package com.evision.ragservice.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.domain.ApiKeys;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
class AdminAndAuthIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Value("${rag.admin-key}")
    private String adminKey;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM chunk").update();
        jdbc.sql("DELETE FROM document").update();
        jdbc.sql("DELETE FROM collection").update();
        jdbc.sql("DELETE FROM client_app").update();
    }

    private String bearer(String key) {
        return "Bearer " + key;
    }

    private String createClient(String name) throws Exception {
        String body = mvc.perform(post("/v1/admin/clients").header(HttpHeaders.AUTHORIZATION, bearer(adminKey))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return body;
    }

    @Test
    void createReturnsKeyOnceAndStoresOnlyItsHash() throws Exception {
        String body = createClient("alpha");
        String apiKey = JsonPath.read(body, "$.apiKey");

        assertThat(apiKey).hasSize(43);
        byte[] stored = jdbc.sql("SELECT key_hash FROM client_app WHERE name = 'alpha'")
                .query(byte[].class).single();
        assertThat(stored).isEqualTo(ApiKeys.hash(apiKey)).isNotEqualTo(apiKey.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void adminRoutesAcceptOnlyTheAdminKey() throws Exception {
        String clientKey = JsonPath.read(createClient("alpha"), "$.apiKey");
        String json = "{\"name\":\"beta\"}";

        mvc.perform(post("/v1/admin/clients").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("INVALID_API_KEY"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        mvc.perform(post("/v1/admin/clients").header(HttpHeaders.AUTHORIZATION, bearer("wrong-admin-key"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/admin/clients").header(HttpHeaders.AUTHORIZATION, bearer(clientKey))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateNameIsConflict() throws Exception {
        createClient("alpha");
        mvc.perform(post("/v1/admin/clients").header(HttpHeaders.AUTHORIZATION, bearer(adminKey))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"alpha\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("CLIENT_NAME_TAKEN"));
    }

    @Test
    void clientKeyAuthenticatesClientRoutesButAdminKeyDoesNot() throws Exception {
        String clientKey = JsonPath.read(createClient("alpha"), "$.apiKey");

        mvc.perform(get("/v1/collections").header(HttpHeaders.AUTHORIZATION, bearer(clientKey)))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/collections").header(HttpHeaders.AUTHORIZATION, bearer(adminKey)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotateKillsTheOldKeyAndRevokeKillsTheNewOne() throws Exception {
        String created = createClient("alpha");
        String oldKey = JsonPath.read(created, "$.apiKey");
        String id = JsonPath.read(created, "$.id");

        String rotated = mvc.perform(post("/v1/admin/clients/" + id + "/rotate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminKey)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newKey = JsonPath.read(rotated, "$.apiKey");

        mvc.perform(get("/v1/collections").header(HttpHeaders.AUTHORIZATION, bearer(oldKey)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/collections").header(HttpHeaders.AUTHORIZATION, bearer(newKey)))
                .andExpect(status().isOk());

        mvc.perform(post("/v1/admin/clients/" + id + "/revoke").header(HttpHeaders.AUTHORIZATION, bearer(adminKey)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/v1/collections").header(HttpHeaders.AUTHORIZATION, bearer(newKey)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/admin/clients/" + id + "/rotate").header(HttpHeaders.AUTHORIZATION, bearer(adminKey)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("CLIENT_NOT_FOUND"));
    }

    @Test
    void listShowsLastUsedAtAndNeverTheKey() throws Exception {
        String clientKey = JsonPath.read(createClient("alpha"), "$.apiKey");
        mvc.perform(get("/v1/collections").header(HttpHeaders.AUTHORIZATION, bearer(clientKey)))
                .andExpect(status().isOk());

        String body = mvc.perform(get("/v1/admin/clients").header(HttpHeaders.AUTHORIZATION, bearer(adminKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastUsedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("apiKey").doesNotContain("keyHash").doesNotContain(clientKey);
    }

    @Test
    void operationalAndDocsEndpointsNeedNoKey() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void shortAdminKeyFailsFast() {
        assertThatThrownBy(() -> new ApiKeyFilter(null, null, "short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RAG_ADMIN_KEY");
    }
}
