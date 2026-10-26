package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.briefing.AppRunView;
import com.evision.releasemgmt.application.briefing.BriefingOverview;
import com.evision.releasemgmt.application.briefing.BriefingService;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.briefing.AppBriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingContent;
import com.evision.releasemgmt.domain.briefing.BriefingFeed;
import com.evision.releasemgmt.domain.briefing.BriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import com.evision.releasemgmt.domain.briefing.BriefingTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BriefingController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class BriefingControllerTest {

    private static final String BASE = "/api/briefings";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BriefingService service;

    @BeforeEach
    void feeds() {
        BriefingRun run = new BriefingRun(Instant.parse("2026-10-26T05:44:12Z"), BriefingStatus.ATTENTION,
                "3 blockers", 5, "CNV-3120 blocked", null, null);
        AppBriefingRun appRun = new AppBriefingRun(UUID.randomUUID(), BriefingTask.JIRA_DIGEST, UUID.randomUUID(),
                Instant.parse("2026-10-26T06:44:00Z"), BriefingTrigger.SLOT, BriefingStatus.NEEDS_CREDENTIAL, null,
                null, "Jira credential needed", null,
                List.of(new BriefingContent.Section("Blockers", List.of(new BriefingContent.Item("CNV-1", "Blocked")))),
                Instant.parse("2026-10-26T06:44:01Z"), Instant.parse("2026-10-26T06:44:03Z"));
        AppRunView view = new AppRunView(appRun, "Convergence");
        when(service.overview()).thenReturn(Arrays.stream(BriefingTask.values())
                .map(t -> switch (t) {
                    case JIRA_DIGEST -> new BriefingOverview(new BriefingFeed(t, List.of(run), false), List.of(view),
                            List.of(view));
                    case SPRINT_CHECK -> new BriefingOverview(new BriefingFeed(t, List.of(), true), List.of(), List.of());
                    default -> new BriefingOverview(new BriefingFeed(t, List.of(), false), List.of(), List.of());
                }).toList());
    }

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"));
    }

    @Test
    void b7_returnsAllFiveTasksInOrderWithLatestAndRuns() throws Exception {
        mockMvc.perform(get(BASE).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(5))
                .andExpect(jsonPath("$.tasks[0].id").value("jira_digest"))
                .andExpect(jsonPath("$.tasks[1].id").value("sprint_check"))
                .andExpect(jsonPath("$.tasks[2].id").value("weekly_status"))
                .andExpect(jsonPath("$.tasks[3].id").value("reg_watch"))
                .andExpect(jsonPath("$.tasks[4].id").value("gulf_invest"))
                .andExpect(jsonPath("$.tasks[0].name").value("Daily Jira digest"))
                .andExpect(jsonPath("$.tasks[0].readError").value(false))
                .andExpect(jsonPath("$.tasks[0].latest.runAt").value("2026-10-26T05:44:12Z"))
                .andExpect(jsonPath("$.tasks[0].latest.status").value("attention"))
                .andExpect(jsonPath("$.tasks[0].latest.headline").value("3 blockers"))
                .andExpect(jsonPath("$.tasks[0].latest.attention").value(5))
                .andExpect(jsonPath("$.tasks[0].latest.h1").value("CNV-3120 blocked"))
                .andExpect(jsonPath("$.tasks[0].runs.length()").value(1))
                .andExpect(jsonPath("$.tasks[2].latest").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.tasks[2].runs.length()").value(0))
                .andExpect(jsonPath("$.tasks[1].readError").value(true));
    }

    @Test
    void returnsAppFallbackRunsPerTask() throws Exception {
        mockMvc.perform(get(BASE).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].appFallback").value(true))
                .andExpect(jsonPath("$.tasks[3].appFallback").value(false))
                .andExpect(jsonPath("$.tasks[3].appLatest.length()").value(0))
                .andExpect(jsonPath("$.tasks[0].appLatest[0].productName").value("Convergence"))
                .andExpect(jsonPath("$.tasks[0].appLatest[0].runAt").value("2026-10-26T06:44:03Z"))
                .andExpect(jsonPath("$.tasks[0].appLatest[0].status").value("needs_credential"))
                .andExpect(jsonPath("$.tasks[0].appLatest[0].trigger").value("slot"))
                .andExpect(jsonPath("$.tasks[0].appLatest[0].reason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.tasks[0].appLatest[0].sections[0].title").value("Blockers"))
                .andExpect(jsonPath("$.tasks[0].appLatest[0].sections[0].items[0].key").value("CNV-1"))
                .andExpect(jsonPath("$.tasks[0].appLatest[0].sections[0].items[0].text").value("Blocked"))
                .andExpect(jsonPath("$.tasks[0].appRuns.length()").value(1));
    }

    @Test
    void b8_unauthenticatedGetIsProblemDetail401() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void b8_writeMethodsAreNotMapped() throws Exception {
        mockMvc.perform(post(BASE).with(csrf()).with(authenticated())).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put(BASE).with(csrf()).with(authenticated())).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(BASE).with(csrf()).with(authenticated())).andExpect(status().isMethodNotAllowed());
    }
}
