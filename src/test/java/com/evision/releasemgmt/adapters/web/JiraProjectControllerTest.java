package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.JiraProjectService;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraProjectSummary;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.JiraSprint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JiraProjectController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class, ProblemDetailAuthenticationEntryPoint.class})
class JiraProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JiraProjectService service;

    @Test
    void authenticatedListReturnsProjects() throws Exception {
        when(service.listProjects()).thenReturn(List.of(new JiraProjectSummary("10023", "CNV", "Conversion")));

        mockMvc.perform(get("/api/jira/projects")
                        .with(jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("CNV"))
                .andExpect(jsonPath("$[0].id").value("10023"));
    }

    @Test
    void authenticatedSprintsReturnsSprints() throws Exception {
        when(service.listSprints("10023"))
                .thenReturn(List.of(new JiraSprint("32", "Sprint 2", "active")));

        mockMvc.perform(get("/api/jira/projects/10023/sprints")
                        .with(jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("32"))
                .andExpect(jsonPath("$[0].name").value("Sprint 2"))
                .andExpect(jsonPath("$[0].state").value("active"));
    }

    @Test
    void unauthenticatedListIsRejected() throws Exception {
        mockMvc.perform(get("/api/jira/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.reason").value("UNAUTHENTICATED"));
    }

    @Test
    void jiraRateLimitedSurfacesAs429() throws Exception {
        when(service.listProjects()).thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_RATE_LIMITED));

        mockMvc.perform(get("/api/jira/projects")
                        .with(jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reason").value("JIRA_RATE_LIMITED"));
    }

    @Test
    void jiraUnreachableSurfacesAs503() throws Exception {
        when(service.listProjects()).thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_UNREACHABLE));

        mockMvc.perform(get("/api/jira/projects")
                        .with(jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("JIRA_UNREACHABLE"));
    }
}
