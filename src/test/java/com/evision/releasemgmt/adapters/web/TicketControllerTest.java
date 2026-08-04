package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.TicketSearchService;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class, ProblemDetailAuthenticationEntryPoint.class})
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketSearchService service;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"));
    }

    private static TicketDetail ticket(String key) {
        return new TicketDetail(key, "Summary " + key, "Bug", "Done", "Fixed", "Dev Person", "Reporter Person",
                List.of("2026.1"), "Ship it", List.of("Looks good"), 1);
    }

    @Test
    void pickerSearchReturnsTicketsAndDefaultsToDoneOnly() throws Exception {
        when(service.search(any())).thenReturn(List.of(ticket("CNV-1")));

        mockMvc.perform(post("/api/tickets/search").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"10023\",\"fixVersionId\":\"10891\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets[0].key").value("CNV-1"));

        ArgumentCaptor<TicketSearchCriteria> criteria = ArgumentCaptor.forClass(TicketSearchCriteria.class);
        verify(service).search(criteria.capture());
        assertThat(criteria.getValue().toJql())
                .isEqualTo("project = 10023 AND fixVersion = 10891 AND statusCategory IN (\"Done\") ORDER BY key ASC");
    }

    @Test
    void jqlSearchForwardsQueryVerbatim() throws Exception {
        when(service.search(any())).thenReturn(List.of(ticket("CNV-1")));

        mockMvc.perform(post("/api/tickets/search").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jql\":\"project = CNV AND status = Done\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<TicketSearchCriteria> criteria = ArgumentCaptor.forClass(TicketSearchCriteria.class);
        verify(service).search(criteria.capture());
        assertThat(criteria.getValue().toJql()).isEqualTo("project = CNV AND status = Done");
    }

    @Test
    void bothFilterModesIsRejectedBeforeAnyJiraCall() throws Exception {
        mockMvc.perform(post("/api/tickets/search").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"10023\",\"fixVersionId\":\"10891\",\"jql\":\"project = CNV\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));

        verify(service, never()).search(any());
    }

    @Test
    void neitherFilterModeIsRejected() throws Exception {
        mockMvc.perform(post("/api/tickets/search").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void nonNumericProjectIdIsRejectedWithFieldError() throws Exception {
        mockMvc.perform(post("/api/tickets/search").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"CNV\",\"fixVersionId\":\"10891\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.projectId").exists());

        verify(service, never()).search(any());
    }

    @Test
    void halfFilledPickerIsRejected() throws Exception {
        mockMvc.perform(post("/api/tickets/search").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"10023\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyResultIsASuccessNotAnError() throws Exception {
        when(service.search(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/tickets/search").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"10023\",\"fixVersionId\":\"10891\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets").isArray())
                .andExpect(jsonPath("$.tickets").isEmpty());
    }

    @Test
    void resultSetTooLargeMapsToUnprocessableEntity() throws Exception {
        when(service.search(any()))
                .thenThrow(new JiraTicketReadException(JiraAuthFailureReason.RESULT_SET_TOO_LARGE));

        mockMvc.perform(post("/api/tickets/search").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"10023\",\"fixVersionId\":\"10891\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("RESULT_SET_TOO_LARGE"));
    }

    @Test
    void rateLimitedMapsToTooManyRequests() throws Exception {
        when(service.search(any()))
                .thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_RATE_LIMITED));

        mockMvc.perform(post("/api/tickets/search").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"10023\",\"fixVersionId\":\"10891\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reason").value("JIRA_RATE_LIMITED"));
    }

    @Test
    void unreachableMapsToServiceUnavailable() throws Exception {
        when(service.search(any()))
                .thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_UNREACHABLE));

        mockMvc.perform(post("/api/tickets/search").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"10023\",\"fixVersionId\":\"10891\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unauthenticatedSearchIsRejected() throws Exception {
        mockMvc.perform(post("/api/tickets/search").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"10023\",\"fixVersionId\":\"10891\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.reason").value("UNAUTHENTICATED"));
    }

    @Test
    void nullResolutionAndAssigneeSerialiseAsJsonNulls() throws Exception {
        when(service.search(any())).thenReturn(List.of(
                new TicketDetail("CNV-9", "Unresolved", "Task", "In Progress", null, null, "Reporter Person",
                        List.of(), "", List.of(), 0)));

        mockMvc.perform(post("/api/tickets/search").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"10023\",\"fixVersionId\":\"10891\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets[0].resolution").isEmpty())
                .andExpect(jsonPath("$.tickets[0].assignee").isEmpty());
    }

    @Test
    void explicitStatusCategoriesAreComposedIntoTheQuery() throws Exception {
        when(service.search(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/tickets/search").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"10023\",\"fixVersionId\":\"10891\","
                                + "\"statusCategories\":[\"TO_DO\",\"IN_PROGRESS\"]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<TicketSearchCriteria> criteria = ArgumentCaptor.forClass(TicketSearchCriteria.class);
        verify(service).search(criteria.capture());
        assertThat(criteria.getValue().toJql()).contains("statusCategory IN (\"To Do\", \"In Progress\")");
        assertThat(criteria.getValue().toJql()).doesNotContain("\"Done\"");
    }

    @Test
    void unknownStatusCategoryIsRejectedBeforeAnyJiraCall() throws Exception {
        mockMvc.perform(post("/api/tickets/search").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"10023\",\"fixVersionId\":\"10891\","
                                + "\"statusCategories\":[\"NOPE\"]}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).search(any());
    }
}
