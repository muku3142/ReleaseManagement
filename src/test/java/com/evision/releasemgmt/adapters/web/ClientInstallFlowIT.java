package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.adapters.security.AuthCookieWriter;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.ports.ReleaseRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Phase 15 end to end through HTTP only: products, clients, release products and the install ledger.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"stub-jira", "local"})
@Testcontainers
class ClientInstallFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReleaseRepository releaseRepository;

    private MockCookie accessCookie;

    @BeforeEach
    void signIn() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dev@example.com\",\"apiToken\":\"valid-token\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String setCookie = login.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith(AuthCookieWriter.ACCESS_COOKIE + "="))
                .findFirst().orElseThrow();
        String value = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
        accessCookie = new MockCookie(AuthCookieWriter.ACCESS_COOKIE, value);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE release CASCADE");
        jdbcTemplate.execute("TRUNCATE product, client CASCADE");
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.with(csrf()).cookie(accessCookie);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private String idOf(String collection, String name) throws Exception {
        String json = call(post("/api/" + collection), "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(json, "$." + collection + "[?(@.name=='" + name + "')].id");
        return ids.get(0);
    }

    @Test
    void portfolioFlowAcrossProductsClientsReleasesAndInstalls() throws Exception {
        String core = idOf("products", "Core");
        String mobile = idOf("products", "Mobile");
        String acme = idOf("clients", "Acme");
        String globex = idOf("clients", "Globex");
        String outsider = idOf("clients", "Initech");
        call(put("/api/clients/" + acme + "/products/" + core), null).andExpect(status().isOk());
        call(put("/api/clients/" + globex + "/products/" + core), null).andExpect(status().isOk());

        // D-15-18: rejected before any Jira call, creates nothing.
        call(post("/api/releases"), "{\"name\":\"No product\",\"version\":\"1.0\",\"ticketKeys\":[\"CNV-1\"]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("RELEASE_PRODUCT_REQUIRED"));
        call(post("/api/releases"),
                "{\"name\":\"No product\",\"version\":\"1.0\",\"ticketKeys\":[\"CNV-1\"],\"productId\":\"" + core + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("PRODUCT_NO_JIRA_PROJECT"));
        call(get("/api/releases"), null).andExpect(jsonPath("$.releases[?(@.name=='No product')]").isEmpty());

        Release created = releaseRepository.create(
                Release.create("Flow Release", "1.0", "CNV", "dev-account-id", Instant.now()), List.of());
        UUID releaseId = created.id();
        releaseRepository.freeze(releaseId, "dev-account-id", Instant.now());
        String r = releaseId.toString();

        call(put("/api/releases/" + r + "/product"), "{\"productId\":\"" + mobile + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].name").value("Mobile"));
        call(put("/api/releases/" + r + "/product"), "{\"productId\":\"" + core + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(1))
                .andExpect(jsonPath("$.products[0].name").value("Core"));
        String promotionsBefore = call(get("/api/releases/" + r + "/promotions"), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        call(post("/api/releases/" + r + "/installs"), "{\"productId\":\"" + core + "\",\"clientIds\":[\"" + acme
                + "\",\"" + globex + "\"],\"outcome\":\"SUCCESS\",\"installedAt\":null,\"note\":\"night window\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.inProduction").value(false));

        // D-15-10: one unlinked client rejects the whole batch.
        call(post("/api/releases/" + r + "/installs"), "{\"productId\":\"" + core + "\",\"clientIds\":[\"" + outsider
                + "\"],\"outcome\":\"SUCCESS\",\"installedAt\":null,\"note\":null}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("INSTALL_CLIENTS_REJECTED"))
                .andExpect(jsonPath("$.clients[0].reason").value("NOT_LINKED"));
        call(get("/api/releases/" + r + "/installs"), null).andExpect(jsonPath("$.entries.length()").value(2));

        String history = call(get("/api/releases/" + r + "/installs"), null).andReturn().getResponse()
                .getContentAsString();
        List<String> acmeEntry = JsonPath.read(history, "$.entries[?(@.clientName=='Acme')].id");
        String original = acmeEntry.get(0);

        String correction = call(post("/api/releases/" + r + "/installs/" + original + "/corrections"),
                "{\"outcome\":\"FAILED\",\"installedAt\":\"2026-01-01T00:00:00Z\",\"note\":null,"
                        + "\"reason\":\"wrong outcome\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        List<String> correctionId = JsonPath.read(correction,
                "$.entries[?(@.clientName=='Acme' && @.outcome=='FAILED')].id");
        String voided = call(post("/api/releases/" + r + "/installs/" + correctionId.get(0) + "/corrections"),
                "{\"outcome\":\"VOIDED\",\"installedAt\":\"2026-01-01T00:00:00Z\",\"note\":null,"
                        + "\"reason\":\"duplicate\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entries.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        List<String> effectiveSuccesses = JsonPath.read(voided,
                "$.entries[?(@.effective == true && @.outcome == 'SUCCESS')].clientName");
        assertThat(effectiveSuccesses).containsExactly("Globex");

        call(get("/api/clients/" + acme), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs[0].current").value((Object) null))
                .andExpect(jsonPath("$.runs[0].hasInstalls").value(true))
                .andExpect(jsonPath("$.history.length()").value(3))
                .andExpect(jsonPath("$.history[?(@.effective==false)]").isNotEmpty());
        call(get("/api/clients/" + globex), null)
                .andExpect(jsonPath("$.runs[0].current.releaseId").value(r))
                .andExpect(jsonPath("$.runs[0].current.inProduction").value(false));

        // D-15-15
        call(put("/api/releases/" + r + "/product"), "{\"productId\":\"" + mobile + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("PRODUCT_HAS_INSTALLS_ON_RELEASE"));
        call(get("/api/releases/" + r), null).andExpect(jsonPath("$.products.length()").value(1))
                .andExpect(jsonPath("$.products[0].name").value("Core"));
        call(delete("/api/clients/" + acme + "/products/" + core), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("CLIENT_LINK_HAS_INSTALLS"));

        // PORT-05: installs never touch the promotion history.
        String promotionsAfter = call(get("/api/releases/" + r + "/promotions"), null)
                .andReturn().getResponse().getContentAsString();
        assertThat(promotionsAfter).isEqualTo(promotionsBefore);

        // Defensive display path: a release with no product rows lists as products: [].
        releaseRepository.create(Release.create("Bare", "1.0", "CNV", "dev-account-id", Instant.now()), List.of());
        String list = call(get("/api/releases"), null).andReturn().getResponse().getContentAsString();
        List<List<Object>> flowProducts = JsonPath.read(list, "$.releases[?(@.name == 'Flow Release')].products");
        List<List<Object>> bareProducts = JsonPath.read(list, "$.releases[?(@.name == 'Bare')].products");
        assertThat(flowProducts.get(0)).hasSize(1);
        assertThat(bareProducts.get(0)).isEmpty();
    }
}
