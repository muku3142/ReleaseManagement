package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.adapters.security.AuthCookieWriter;
import com.evision.releasemgmt.application.briefing.BriefingScheduleService;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.briefing.BriefingIssue;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.ports.JiraBriefingPort;
import com.evision.releasemgmt.ports.JiraFieldMetadataPort;
import com.evision.releasemgmt.ports.JiraIssueHistoryPort;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Phase 17.1 end to end: product keys, settings, run now, the Cairo tick and the merged GET /api/briefings.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"stub-jira", "local"})
@Testcontainers
class BriefingFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    private static final Path DATA_DIR;
    private static final Path CONFIG;

    static {
        try {
            DATA_DIR = Files.createTempDirectory("briefing-flow-data");
            CONFIG = Files.createTempFile("briefing-flow-config", ".json");
            Files.writeString(CONFIG, "{\"version\":1,\"jira\":{\"projects\":[\"CNV\"]},\"tasks\":{}}");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Mon 2026-10-26 09:20 Cairo (+03): the 08:44 digest slot is 05:44Z and its 30-minute grace has passed.
    private static final Instant TICK = Instant.parse("2026-10-26T06:20:00Z");
    private static final Instant SLOT = Instant.parse("2026-10-26T05:44:00Z");
    private static final String CLAUDE_ROW_AT = "2026-10-26T05:50:00Z";

    @DynamicPropertySource
    static void briefingFiles(DynamicPropertyRegistry registry) {
        registry.add("app.briefings.dir", DATA_DIR::toString);
        registry.add("app.briefings.config", CONFIG::toString);
    }

    @MockitoBean
    private JiraBriefingPort jira;

    @MockitoBean
    private JiraFieldMetadataPort fieldMetadata;

    @MockitoBean
    private JiraIssueHistoryPort issueHistory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BriefingScheduleService scheduler;

    private MockCookie accessCookie;

    @BeforeEach
    void signIn() throws Exception {
        cleanUp();
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
    void cleanUp() throws IOException {
        jdbcTemplate.execute("TRUNCATE briefing_run, product_jira_project");
        jdbcTemplate.execute("TRUNCATE product, client CASCADE");
        jdbcTemplate.update("UPDATE briefing_settings SET scheduler_account_id = NULL, fallback_enabled = FALSE, "
                + "grace_minutes = 30, keep_runs = NULL, updated_by = NULL WHERE id = 1");
        Files.deleteIfExists(DATA_DIR.resolve("jira_digest.json"));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.with(csrf()).cookie(accessCookie);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private String idOf(String name) throws Exception {
        String json = call(post("/api/products"), "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(json, "$.products[?(@.name=='" + name + "')].id");
        return ids.get(0);
    }

    private static BriefingIssue flaggedInProgress(String key) {
        Instant updated = Instant.parse("2026-10-25T07:00:00Z");
        return new BriefingIssue(key, "Bank config", "Story", "In Progress", StatusCategory.IN_PROGRESS, "High",
                true, "Dev", updated, updated, null, null, null, null, 0, true, null, List.of());
    }

    private void writeClaudeDigest(String status) throws IOException {
        Files.writeString(DATA_DIR.resolve("jira_digest.json"), "[{\"run_at\":\"" + CLAUDE_ROW_AT
                + "\",\"status\":\"" + status + "\",\"headline\":\"Claude digest\",\"attention\":0}]");
    }

    private int slotRuns(String productId, String status, String reason) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM briefing_run WHERE task = 'jira_digest' "
                        + "AND product_id = ?::uuid AND slot_at = ? AND status = ? AND reason IS NOT DISTINCT FROM ?",
                Integer.class, productId, Timestamp.from(SLOT), status, reason);
    }

    private int allRuns() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM briefing_run", Integer.class);
    }

    @Test
    void briefingsFallbackFlowAcrossSettingsRunNowTheTickAndTheFeed() throws Exception {
        // 1. Product Jira keys: normalised, one owner per key, format checked.
        String core = idOf("Core");
        String cards = idOf("Cards");
        call(put("/api/products/" + core + "/jira-projects"), "{\"keys\":[\"cnv\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[?(@.name=='Core')].jiraProjectKeys[0]").value("CNV"));
        call(put("/api/products/" + cards + "/jira-projects"), "{\"keys\":[\"CNV\"]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("PRODUCT_JIRA_KEY_TAKEN"))
                .andExpect(jsonPath("$.productName").value("Core"));
        call(put("/api/products/" + cards + "/jira-projects"), "{\"keys\":[\"c d\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("PRODUCT_JIRA_KEY_INVALID"));
        call(put("/api/products/" + cards + "/jira-projects"), "{\"keys\":[\"CRD\"]}")
                .andExpect(status().isOk());

        // 2. No scheduler user yet; the caller designates themselves.
        call(get("/api/settings/briefings"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedulerUser.email").doesNotExist())
                .andExpect(jsonPath("$.credentialProblem").value(true))
                .andExpect(jsonPath("$.fallbackEnabled").value(false))
                .andExpect(jsonPath("$.tasks[0].time").value("08:44"));
        call(put("/api/settings/briefings/scheduler-user"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedulerUser.isYou").value(true))
                .andExpect(jsonPath("$.credentialProblem").value(false));

        // 3. Run now uses the designated credential and ignores the fallback switch.
        given(jira.search(any(), anyString(), any(), anyInt())).willAnswer(inv -> {
            String jql = inv.getArgument(1);
            return List.of(flaggedInProgress(jql.contains("CNV") ? "CNV-1" : "CRD-1"));
        });
        call(post("/api/settings/briefings/tasks/jira_digest/run"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").value(2))
                .andExpect(jsonPath("$.attention", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.needsCredential").value(0));
        int afterRunNow = allRuns();
        assertThat(afterRunNow).isEqualTo(2);

        // 4. D-17.1-24: with the switch off the tick records nothing.
        writeClaudeDigest("ok");
        scheduler.tick(TICK);
        assertThat(allRuns()).isEqualTo(afterRunNow);
        call(put("/api/settings/briefings/fallback"), "{\"enabled\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallbackEnabled").value(true));

        // Claude's ok row covers CNV only, so Core is skipped and Cards gets an app run.
        scheduler.tick(TICK);
        assertThat(slotRuns(core, "skipped", "CLAUDE_COVERED")).isEqualTo(1);
        assertThat(slotRuns(cards, "attention", null)).isEqualTo(1);
        int afterFirstTick = allRuns();
        scheduler.tick(TICK);
        assertThat(allRuns()).isEqualTo(afterFirstTick);

        // 5. A failed Claude row covers nothing: both products get app runs for the slot.
        writeClaudeDigest("failed");
        jdbcTemplate.update("DELETE FROM briefing_run WHERE slot_at = ?", Timestamp.from(SLOT));
        scheduler.tick(TICK);
        assertThat(slotRuns(core, "attention", null)).isEqualTo(1);
        assertThat(slotRuns(cards, "attention", null)).isEqualTo(1);

        // 6. A rejected credential is a recorded outcome, not a 401 for the caller.
        given(jira.search(any(), anyString(), any(), anyInt()))
                .willThrow(new JiraTicketReadException(JiraAuthFailureReason.INVALID_CREDENTIALS));
        call(post("/api/settings/briefings/tasks/jira_digest/run"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsCredential").value(2))
                .andExpect(jsonPath("$.products").value(2));
        call(get("/api/settings/briefings"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialProblem").value(true));

        // 7. The merged feed: Claude's failed row plus the app's newest run per product.
        String feed = call(get("/api/briefings"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(5))
                .andExpect(jsonPath("$.tasks[?(@.id=='jira_digest')].latest.status").value("failed"))
                .andExpect(jsonPath("$.tasks[?(@.id=='reg_watch')].appFallback").value(false))
                .andReturn().getResponse().getContentAsString();
        List<String> latestProducts = JsonPath.read(feed, "$.tasks[?(@.id=='jira_digest')].appLatest[*].productName");
        List<String> latestStatuses = JsonPath.read(feed, "$.tasks[?(@.id=='jira_digest')].appLatest[*].status");
        assertThat(latestProducts).containsExactly("Cards", "Core");
        assertThat(latestStatuses).containsOnly("needs_credential");
        List<String> runAts = JsonPath.read(feed, "$.tasks[?(@.id=='jira_digest')].appRuns[*].runAt");
        List<Instant> instants = runAts.stream().map(Instant::parse).toList();
        assertThat(instants).hasSize(allRuns()).isSortedAccordingTo((a, b) -> b.compareTo(a));

        // 8. Both endpoints need a signed-in user.
        mockMvc.perform(get("/api/briefings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/settings/briefings")).andExpect(status().isUnauthorized());
    }
}
