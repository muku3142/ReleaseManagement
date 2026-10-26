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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Phase 16 end to end through HTTP: product-scoped dashboard, portfolio summary, matrix and clients-behind.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"stub-jira", "local"})
@Testcontainers
class PortfolioDashboardFlowIT {

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

    private String release(String name, Instant frozenAt, String... productIds) throws Exception {
        UUID id = releaseRepository.create(
                Release.create(name, "1.0", "CNV", "dev-account-id", Instant.now()), List.of()).id();
        String ids = "\"" + String.join("\",\"", productIds) + "\"";
        call(put("/api/releases/" + id + "/products"), "{\"productIds\":[" + ids + "]}").andExpect(status().isOk());
        if (frozenAt != null) {
            releaseRepository.freeze(id, "dev-account-id", frozenAt);
        }
        return id.toString();
    }

    private void install(String releaseId, String productId, String clientId) throws Exception {
        call(post("/api/releases/" + releaseId + "/installs"), "{\"productId\":\"" + productId + "\",\"clientIds\":[\""
                + clientId + "\"],\"outcome\":\"SUCCESS\",\"installedAt\":null,\"note\":null}")
                .andExpect(status().isCreated());
    }

    private static String cell(String clientId, String productId) {
        return "$.cells[?(@.clientId=='" + clientId + "' && @.productId=='" + productId + "')]";
    }

    @Test
    void portfolioDashboardAcrossProductsReleasesAndInstalls() throws Exception {
        String core = idOf("products", "Core");
        String mobile = idOf("products", "Mobile");
        String acme = idOf("clients", "Acme");
        String globex = idOf("clients", "Globex");
        call(put("/api/clients/" + acme + "/products/" + core), null).andExpect(status().isOk());
        call(put("/api/clients/" + acme + "/products/" + mobile), null).andExpect(status().isOk());
        call(put("/api/clients/" + globex + "/products/" + core), null).andExpect(status().isOk());

        // R covers both products and goes straight to Production; S stays open; T is frozen after R.
        Instant now = Instant.now();
        String r = release("R", now.minusSeconds(600), core, mobile);
        release("S", null, core);
        String t = release("T", now.minusSeconds(300), mobile);
        String history = call(get("/api/releases/" + r + "/promotions"), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> stageIds = JsonPath.read(history, "$.stages[?(@.retired == false)].id");
        call(post("/api/releases/" + r + "/promotions"),
                "{\"stageId\":\"" + stageIds.get(stageIds.size() - 1) + "\",\"outcome\":\"SUCCESS\"}")
                .andExpect(status().isCreated());

        // a release in two products lists once under a filter naming both
        String releases = call(get("/api/dashboard/releases?scope=all&productIds=" + core + "," + mobile), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.visibleReleases").value(3))
                .andReturn().getResponse().getContentAsString();
        List<Object> matching = JsonPath.read(releases, "$.releases[?(@.id=='" + r + "')]");
        assertThat(matching).hasSize(1);

        call(get("/api/dashboard/portfolio/summary?productIds=" + core + "," + mobile), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.open").value(1))
                .andExpect(jsonPath("$.totals.frozen").value(1))
                .andExpect(jsonPath("$.totals.inProduction").value(1));

        install(r, core, acme);
        install(r, mobile, acme);
        call(get("/api/dashboard/portfolio/matrix"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath(cell(acme, core) + ".run.releaseId").value(r))
                .andExpect(jsonPath(cell(acme, core) + ".run.inProduction").value(true))
                .andExpect(jsonPath(cell(globex, core) + ".run").value((Object) null))
                .andExpect(jsonPath(cell(globex, mobile)).isEmpty());

        call(get("/api/dashboard/portfolio/behind"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behind.length()").value(1))
                .andExpect(jsonPath("$.behind[0].clientName").value("Acme"))
                .andExpect(jsonPath("$.behind[0].productName").value("Mobile"))
                .andExpect(jsonPath("$.behind[0].releasesBehind").value(1))
                .andExpect(jsonPath("$.behind[0].newest.releaseId").value(t))
                .andExpect(jsonPath("$.notInstalled.length()").value(1))
                .andExpect(jsonPath("$.notInstalled[0].clientName").value("Globex"))
                .andExpect(jsonPath("$.notInstalled[0].productName").value("Core"));
    }

    @Test
    void portfolioEndpointsRejectBadInputAndAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/dashboard/portfolio/matrix")).andExpect(status().isUnauthorized());
        call(get("/api/dashboard/portfolio/summary?productIds=not-a-uuid"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }
}
