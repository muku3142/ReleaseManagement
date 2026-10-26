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

// Phase 15.1 end to end through HTTP: unfreeze, refreeze staleness, post-freeze product edits and stage skips.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"stub-jira", "local"})
@Testcontainers
class UnfreezeFlowIT {

    private static final String ACTOR = "dev-account-id";

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

    private String newRelease(String name) {
        Release created = releaseRepository.create(Release.create(name, "1.0", "CNV", ACTOR, Instant.now()), List.of());
        return created.id().toString();
    }

    private String readiness(String releaseId) throws Exception {
        String json = call(get("/api/documents/readiness"), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> states = JsonPath.read(json,
                "$.entries[?(@.releaseId=='" + releaseId + "' && @.documentType=='RELEASE_NOTES')].state");
        return states.get(0);
    }

    private String frozenAt(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.frozenAt");
    }

    @Test
    void unfreezeRefreezeStalenessAndStageSkipsAcrossHttp() throws Exception {
        String core = idOf("products", "Core");
        String r = newRelease("Flow Release");
        call(put("/api/releases/" + r + "/products"), "{\"productIds\":[\"" + core + "\"]}")
                .andExpect(status().isOk());

        // SC1: freeze, generate, unfreeze - unfreezing alone never stales the document (D-15.1-05).
        String firstFreeze = frozenAt(call(post("/api/releases/" + r + "/freeze"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FROZEN")));
        call(post("/api/releases/" + r + "/documents/RELEASE_NOTES"), null).andExpect(status().isCreated());
        assertThat(readiness(r)).isEqualTo("UP_TO_DATE");

        call(post("/api/releases/" + r + "/unfreeze"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.frozenAt").value((Object) null))
                .andExpect(jsonPath("$.unfreezeBlockedReason").value((Object) null));
        assertThat(readiness(r)).isEqualTo("UP_TO_DATE");

        // SC3: an edit while open stales the document.
        call(post("/api/releases/" + r + "/manual-items"),
                "{\"title\":\"Hotfix note\",\"description\":\"\",\"category\":\"OTHER\"}")
                .andExpect(status().isCreated());
        assertThat(readiness(r)).isEqualTo("STALE");

        // SC1/SC4: refreezing stamps a later frozenAt and the document stays stale until regenerated.
        String secondFreeze = frozenAt(call(post("/api/releases/" + r + "/freeze"), null).andExpect(status().isOk()));
        assertThat(Instant.parse(secondFreeze)).isAfter(Instant.parse(firstFreeze));
        assertThat(readiness(r)).isEqualTo("STALE");
        call(post("/api/releases/" + r + "/documents/RELEASE_NOTES"), null).andExpect(status().isCreated());
        assertThat(readiness(r)).isEqualTo("UP_TO_DATE");

        // D-15-02 kept: a post-freeze product edit does not stale the document.
        String mobile = idOf("products", "Mobile");
        call(put("/api/releases/" + r + "/products"),
                "{\"productIds\":[\"" + core + "\",\"" + mobile + "\"]}").andExpect(status().isOk());
        assertThat(readiness(r)).isEqualTo("UP_TO_DATE");

        // PROMO-SKIP: the first promotion may jump straight to the last stage, but never backwards.
        String history = call(get("/api/releases/" + r + "/promotions"), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> stageIds = JsonPath.read(history, "$.stages[?(@.retired == false)].id");
        String first = stageIds.get(0);
        String last = stageIds.get(stageIds.size() - 1);
        assertThat(first).isNotEqualTo(last);
        call(post("/api/releases/" + r + "/promotions"), "{\"stageId\":\"" + last + "\",\"outcome\":\"SUCCESS\"}")
                .andExpect(status().isCreated());
        call(post("/api/releases/" + r + "/promotions"), "{\"stageId\":\"" + first + "\",\"outcome\":\"SUCCESS\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("PROMOTION_STAGE_EARLIER"));
        String dashboard = call(get("/api/dashboard/releases?scope=all"), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> currentStage = JsonPath.read(dashboard, "$.releases[?(@.id=='" + r + "')].currentStageId");
        assertThat(currentStage).containsExactly(last);

        // SC2: promotion history makes the release un-unfreezable, and the payload says why up front.
        call(get("/api/releases/" + r), null)
                .andExpect(jsonPath("$.unfreezeBlockedReason").value("UNFREEZE_HAS_PROMOTIONS"));
        call(post("/api/releases/" + r + "/unfreeze"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("UNFREEZE_HAS_PROMOTIONS"));
        call(get("/api/releases/" + r), null).andExpect(jsonPath("$.status").value("FROZEN"));
    }

    @Test
    void installsAndOpenReleasesRefuseUnfreeze() throws Exception {
        String core = idOf("products", "Core");
        String acme = idOf("clients", "Acme");
        call(put("/api/clients/" + acme + "/products/" + core), null).andExpect(status().isOk());

        String r2 = newRelease("Installed");
        call(put("/api/releases/" + r2 + "/products"), "{\"productIds\":[\"" + core + "\"]}")
                .andExpect(status().isOk());
        call(post("/api/releases/" + r2 + "/freeze"), null).andExpect(status().isOk());
        call(post("/api/releases/" + r2 + "/installs"), "{\"productId\":\"" + core + "\",\"clientIds\":[\"" + acme
                + "\"],\"outcome\":\"SUCCESS\",\"installedAt\":null,\"note\":null}")
                .andExpect(status().isCreated());
        call(post("/api/releases/" + r2 + "/unfreeze"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("UNFREEZE_HAS_INSTALLS"));

        UUID unused = UUID.fromString(newRelease("Still open"));
        call(post("/api/releases/" + unused + "/unfreeze"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("UNFREEZE_RELEASE_NOT_FROZEN"));
    }
}
