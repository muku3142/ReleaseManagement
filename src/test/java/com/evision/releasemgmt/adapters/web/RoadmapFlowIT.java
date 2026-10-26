package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.adapters.security.AuthCookieWriter;
import com.evision.releasemgmt.application.TicketSearchService;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Phase 17 end to end through HTTP only: roadmap items, scoring, refresh, filters, history, CSV and the packages link.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"stub-jira", "local"})
@Testcontainers
class RoadmapFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReleaseRepository releaseRepository;

    @MockitoBean
    private TicketSearchService tickets;

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
        jdbcTemplate.execute("TRUNCATE roadmap_item CASCADE");
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

    private static TicketDetail ticket(String key, String issueId, String summary, String statusName) {
        return new TicketDetail(key, summary, "Story", statusName, null, null, null, List.of(), null, null,
                List.of(), 0, issueId, Instant.now(), StatusCategory.TO_DO, null, false);
    }

    private String itemId(String title) throws Exception {
        String json = call(get("/api/roadmap/items?done=true"), null).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(json, "$.items[?(@.title=='" + title + "')].id");
        return ids.get(0);
    }

    private void setInputs(String id, String reach, String impact, String confidence, String effort)
            throws Exception {
        call(put("/api/roadmap/items/" + id + "/inputs"), "{\"reach\":" + reach + ",\"impact\":" + impact
                + ",\"confidence\":" + confidence + ",\"effort\":" + effort + "}").andExpect(status().isOk());
    }

    @Test
    void roadmapFlowOverHttp() throws Exception {
        // 1
        String productJson = call(post("/api/products"), "{\"name\":\"Core\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        List<String> productIds = JsonPath.read(productJson, "$.products[?(@.name=='Core')].id");
        String core = productIds.get(0);
        String manualA = JsonPath.read(call(post("/api/roadmap/items"),
                        "{\"title\":\"Manual A\",\"productId\":\"" + core + "\"}")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.source").value("MANUAL"))
                        .andExpect(jsonPath("$.scoreDisplay").value((Object) null))
                        .andReturn().getResponse().getContentAsString(), "$.id");

        // 2
        when(tickets.search(any())).thenReturn(List.of(
                ticket("CNV-1", "1001", "First", "To Do"), ticket("CNV-2", "1002", "Second", "To Do")));
        call(post("/api/roadmap/items/jira"), "{\"keys\":[\"CNV-1\",\"CNV-2\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added.length()").value(2));
        when(tickets.search(any())).thenReturn(List.of(ticket("CNV-1", "1001", "First", "To Do")));
        call(post("/api/roadmap/items/jira"), "{\"keys\":[\"CNV-1\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyPresent[0].jiraKey").value("CNV-1"))
                .andExpect(jsonPath("$.added").isEmpty());
        String cnv1 = itemId("First");
        String cnv2 = itemId("Second");

        // 3
        setInputs(manualA, "10", "2", "80", "4");
        setInputs(cnv1, "10", "3", "100", "2");
        call(get("/api/roadmap/items"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].jiraKey").value("CNV-1"))
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].scoreDisplay").value("15.0"))
                .andExpect(jsonPath("$.items[1].title").value("Manual A"))
                .andExpect(jsonPath("$.items[1].rank").value(2))
                .andExpect(jsonPath("$.items[1].scoreDisplay").value("4.0"))
                .andExpect(jsonPath("$.items[2].jiraKey").value("CNV-2"))
                .andExpect(jsonPath("$.items[2].rank").value((Object) null))
                .andExpect(jsonPath("$.items[2].missing.length()").value(4));

        // 4
        call(put("/api/roadmap/items/" + manualA + "/inputs"),
                "{\"reach\":null,\"impact\":0.75,\"confidence\":null,\"effort\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("ROADMAP_IMPACT_INVALID"));

        // 5
        when(tickets.search(any())).thenReturn(List.of(ticket("CNV-1", "1001", "First renamed", "In Progress")));
        when(tickets.findByKey("CNV-2")).thenReturn(Optional.empty());
        call(post("/api/roadmap/refresh"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshed").value(1))
                .andExpect(jsonPath("$.unavailable[0].jiraKey").value("CNV-2"));
        call(get("/api/roadmap/items"), null)
                .andExpect(jsonPath("$.items[0].title").value("First renamed"))
                .andExpect(jsonPath("$.items[0].jiraStatus").value("In Progress"))
                .andExpect(jsonPath("$.items[0].scoreDisplay").value("15.0"))
                .andExpect(jsonPath("$.items[0].reach").value(10))
                .andExpect(jsonPath("$.items[2].jiraUnavailable").value(true));

        // 6
        when(tickets.search(any())).thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_UNREACHABLE));
        call(post("/api/roadmap/refresh"), null)
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.reason").value("JIRA_UNREACHABLE"));
        call(get("/api/roadmap/items"), null).andExpect(jsonPath("$.items[0].title").value("First renamed"));

        // 7
        call(get("/api/roadmap/items?productIds=" + core), null)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Manual A"));
        call(put("/api/roadmap/items/" + cnv1 + "/details"), "{\"title\":null,\"status\":\"DONE\",\"productId\":null,"
                + "\"targetQuarter\":null,\"owner\":null,\"notes\":null}").andExpect(status().isOk());
        call(get("/api/roadmap/items"), null).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[?(@.id=='" + cnv1 + "')]").isEmpty());
        call(get("/api/roadmap/items?done=true"), null).andExpect(jsonPath("$.items.length()").value(3));
        String defaultList = call(get("/api/roadmap/items"), null).andReturn().getResponse().getContentAsString();
        List<String> defaultTitles = JsonPath.read(defaultList, "$.items[*].title");

        // 8
        call(get("/api/roadmap/items/" + manualA + "/history"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(4))
                .andExpect(jsonPath("$.entries[0].changedBy").isNotEmpty());

        // 9
        MvcResult csv = call(get("/api/roadmap/export.csv"), null)
                .andExpect(status().isOk()).andReturn();
        assertThat(csv.getResponse().getContentType()).startsWith("text/csv");
        byte[] bytes = csv.getResponse().getContentAsByteArray();
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String[] lines = new String(bytes, StandardCharsets.UTF_8).substring(1).split("\r\n");
        List<String> csvTitles = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            csvTitles.add(lines[i].split(",", -1)[2]);
        }
        assertThat(csvTitles).containsExactlyElementsOf(defaultTitles);

        // 10
        Release created = releaseRepository.create(
                Release.create("Pkg Release", "1.0", "CNV", "dev-account-id", Instant.now()), List.of());
        UUID releaseId = created.id();
        releaseRepository.freeze(releaseId, "dev-account-id", Instant.now());
        String packages = "/api/releases/" + releaseId + "/packages-url";
        call(put(packages), "{\"url\":\"https://contoso.sharepoint.com/sites/r/1.0\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packagesUrl").value("https://contoso.sharepoint.com/sites/r/1.0"))
                .andExpect(jsonPath("$.status").value("FROZEN"));
        call(put(packages), "{\"url\":\"https://evil.com/x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("RELEASE_PACKAGES_URL_INVALID"));
        call(put(packages), "{\"url\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packagesUrl").value((Object) null));

        // 11
        mockMvc.perform(get("/api/roadmap/items")).andExpect(status().isUnauthorized());
    }
}
