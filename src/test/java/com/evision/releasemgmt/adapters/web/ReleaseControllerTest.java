package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.DashboardService;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.dashboard.TicketChurn;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.enrichment.CommitsSource;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.enrichment.LinkedCommit;
import com.evision.releasemgmt.domain.enrichment.LinkedPullRequest;
import com.evision.releasemgmt.domain.enrichment.StatusHistory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.AddTicketsResult;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.ManualItemNotFoundException;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseDuplicateException;
import com.evision.releasemgmt.domain.release.ReleaseFrozenException;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseRefreshIncompleteException;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

@WebMvcTest(ReleaseController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class ReleaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReleaseService service;

    @MockitoBean
    private ActorResolutionService actorResolution;

    @MockitoBean
    private DashboardService dashboard;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"));
    }

    private void stubActors() {
        when(actorResolution.resolve(any(String[].class)))
                .thenReturn(new ResolvedActors(Map.of("557058:abc-123", "rm@acme.com")));
    }

    private static Release release(UUID id, ReleaseStatus status) {
        return new Release(id, "Sprint 42", "2026.08", status, "CNV",
                Instant.parse("2026-08-04T09:00:00Z"), "557058:abc-123",
                status == ReleaseStatus.FROZEN ? Instant.parse("2026-08-04T10:00:00Z") : null,
                status == ReleaseStatus.FROZEN ? "557058:abc-123" : null,
                null, null, List.of(), List.of());
    }

    @Test
    void createReturns201AndRecordsTheAuthenticatedPrincipalAsActor() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.createWithTickets(any(), any(), any(), any(), any()))
                .thenReturn(new AddTicketsResult(release(id, ReleaseStatus.OPEN), 0, 0, 0));
        stubActors();

        mockMvc.perform(post("/api/releases").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint 42\",\"version\":\"2026.08\",\"jiraProjectKey\":\"CNV\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.release.name").value("Sprint 42"))
                .andExpect(jsonPath("$.release.version").value("2026.08"))
                .andExpect(jsonPath("$.release.status").value("OPEN"));

        ArgumentCaptor<String> actor = ArgumentCaptor.forClass(String.class);
        verify(service).createWithTickets(eq("Sprint 42"), eq("2026.08"), eq("CNV"), any(),
                actor.capture());
        assertThat(actor.getValue()).isEqualTo("557058:abc-123");
    }

    @Test
    void createWithBlankNameIsRejectedBeforeAnyServiceCall() throws Exception {
        mockMvc.perform(post("/api/releases").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"version\":\"1.0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        verify(service, never()).createWithTickets(any(), any(), any(), any(), any());
    }

    @Test
    void createWithBlankVersionIsRejected() throws Exception {
        mockMvc.perform(post("/api/releases").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint 42\",\"version\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.version").exists());

        verify(service, never()).createWithTickets(any(), any(), any(), any(), any());
    }

    @Test
    void createWithAnOverlongNameIsRejected() throws Exception {
        String overlongName = "a".repeat(201);

        mockMvc.perform(post("/api/releases").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + overlongName + "\",\"version\":\"1.0\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).createWithTickets(any(), any(), any(), any(), any());
    }

    @Test
    void duplicateNameAndVersionMapsTo409ReleaseDuplicate() throws Exception {
        when(service.createWithTickets(any(), any(), any(), any(), any()))
                .thenThrow(new ReleaseDuplicateException());

        mockMvc.perform(post("/api/releases").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint 42\",\"version\":\"2026.08\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("RELEASE_DUPLICATE"));
    }

    @Test
    void nonSemverVersionIsAccepted() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.createWithTickets(any(), any(), any(), any(), any()))
                .thenReturn(new AddTicketsResult(release(id, ReleaseStatus.OPEN), 0, 0, 0));
        stubActors();

        mockMvc.perform(post("/api/releases").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hotfix\",\"version\":\"Sprint 42\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createWithTicketKeysReturnsAddedCount() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.createWithTickets(any(), any(), any(), any(), any()))
                .thenReturn(new AddTicketsResult(release(id, ReleaseStatus.OPEN), 2, 0, 0));
        stubActors();

        mockMvc.perform(post("/api/releases").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint 42\",\"version\":\"2026.08\",\"jiraProjectKey\":\"CNV\","
                                + "\"ticketKeys\":[\"CNV-1\",\"CNV-2\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.alreadyPresent").value(0))
                .andExpect(jsonPath("$.release.id").exists());
    }

    @Test
    void createWithAMalformedTicketKeyIsRejectedBeforeAnyServiceCall() throws Exception {
        mockMvc.perform(post("/api/releases").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint 42\",\"version\":\"2026.08\","
                                + "\"ticketKeys\":[\"not a key\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));

        verify(service, never()).createWithTickets(any(), any(), any(), any(), any());
    }

    @Test
    void getReturnsTheFullReleaseIncludingItemDetail() throws Exception {
        UUID id = UUID.randomUUID();
        TicketDetail ticket = new TicketDetail("CNV-1", "Summary", "Bug", "Done", "Fixed",
                "Dev Person", "Reporter Person", List.of("2026.1"), null, "Full description",
                List.of("First comment", "Second comment"), 2, null, null, null);
        ReleaseItem item = new ReleaseItem(ticket, Instant.parse("2026-08-04T09:30:00Z"), CodeLinks.notAttempted());
        ManualItem manual = new ManualItem(UUID.randomUUID(), "Run migration", "Apply schema change",
                ManualItemCategory.DATABASE_SCRIPT, Instant.parse("2026-08-04T09:31:00Z"));
        Release release = new Release(id, "Sprint 42", "2026.08", ReleaseStatus.OPEN, "CNV",
                Instant.parse("2026-08-04T09:00:00Z"), "557058:abc-123", null, null, null, null,
                List.of(item), List.of(manual));
        when(service.get(id)).thenReturn(release);
        stubActors();

        mockMvc.perform(get("/api/releases/{id}", id).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].key").value("CNV-1"))
                .andExpect(jsonPath("$.items[0].description").value("Full description"))
                .andExpect(jsonPath("$.items[0].comments.length()").value(2))
                .andExpect(jsonPath("$.manualItems[0].category").value("DATABASE_SCRIPT"));
    }

    @Test
    void getUnknownReleaseMapsTo404ReleaseNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new ReleaseNotFoundException());

        mockMvc.perform(get("/api/releases/{id}", id).with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("RELEASE_NOT_FOUND"));
    }

    @Test
    void getWithAMalformedUuidIsA400NotA500() throws Exception {
        mockMvc.perform(get("/api/releases/not-a-uuid").with(authenticated()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listReturnsSummariesWithItemCounts() throws Exception {
        ReleaseSummary first = new ReleaseSummary(UUID.randomUUID(), "Sprint 42", "2026.08",
                ReleaseStatus.OPEN, "CNV", Instant.parse("2026-08-04T09:00:00Z"), "557058:abc-123",
                null, null, null, null, 3);
        ReleaseSummary second = new ReleaseSummary(UUID.randomUUID(), "Sprint 41", "2026.07",
                ReleaseStatus.FROZEN, "CNV", Instant.parse("2026-07-04T09:00:00Z"), "557058:abc-123",
                Instant.parse("2026-07-05T09:00:00Z"), "557058:abc-123", null, null, 5);
        when(service.search(any())).thenReturn(List.of(first, second));
        stubActors();

        mockMvc.perform(get("/api/releases").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releases.length()").value(2))
                .andExpect(jsonPath("$.releases[0].itemCount").value(3));
    }

    @Test
    void listResolvesEveryRowFromASingleLookup() throws Exception {
        ReleaseSummary first = new ReleaseSummary(UUID.randomUUID(), "Sprint 42", "2026.08",
                ReleaseStatus.OPEN, "CNV", Instant.parse("2026-08-04T09:00:00Z"), "557058:abc-123",
                null, null, null, null, 3);
        ReleaseSummary second = new ReleaseSummary(UUID.randomUUID(), "Sprint 41", "2026.07",
                ReleaseStatus.OPEN, "CNV", Instant.parse("2026-07-04T09:00:00Z"), "557058:def-456",
                null, null, null, null, 5);
        when(service.search(any())).thenReturn(List.of(first, second));
        when(actorResolution.resolve(any(String[].class))).thenReturn(new ResolvedActors(
                Map.of("557058:abc-123", "rm@acme.com", "557058:def-456", "other@acme.com")));

        mockMvc.perform(get("/api/releases").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releases[0].createdBy").value("rm@acme.com"))
                .andExpect(jsonPath("$.releases[1].createdBy").value("other@acme.com"));

        verify(actorResolution, times(1)).resolve(any(String[].class));
    }

    @Test
    void anUnresolvedCreatorFallsBackToTheAccountId() throws Exception {
        ReleaseSummary first = new ReleaseSummary(UUID.randomUUID(), "Sprint 42", "2026.08",
                ReleaseStatus.OPEN, "CNV", Instant.parse("2026-08-04T09:00:00Z"), "557058:abc-123",
                null, null, null, null, 3);
        when(service.search(any())).thenReturn(List.of(first));
        when(actorResolution.resolve(any(String[].class))).thenReturn(new ResolvedActors(Map.of()));

        mockMvc.perform(get("/api/releases").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releases[0].createdBy").value("557058:abc-123"));
    }

    @Test
    void createdByAndFrozenByAreResolvedEmails() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenReturn(release(id, ReleaseStatus.FROZEN));
        stubActors();

        mockMvc.perform(get("/api/releases/{id}", id).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").value("rm@acme.com"))
                .andExpect(jsonPath("$.frozenBy").value("rm@acme.com"));
    }

    @Test
    void frozenByStaysNullOnAnUnfrozenRelease() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenReturn(release(id, ReleaseStatus.OPEN));
        stubActors();

        mockMvc.perform(get("/api/releases/{id}", id).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frozenBy").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void listPassesTheSearchQueryThrough() throws Exception {
        when(service.search(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/releases").param("query", "sprint").with(authenticated()))
                .andExpect(status().isOk());

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(service).search(query.capture());
        assertThat(query.getValue()).isEqualTo("sprint");
    }

    @Test
    void listWithNoQueryParameterSearchesEverything() throws Exception {
        when(service.search(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/releases").with(authenticated()))
                .andExpect(status().isOk());

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(service).search(query.capture());
        assertThat(query.getValue()).isEqualTo("");
    }

    @Test
    void addTicketsReturnsEveryCountIncludingTheKeysJiraDidNotReturn() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.addTickets(eq(id), any()))
                .thenReturn(new AddTicketsResult(release(id, ReleaseStatus.OPEN), 1, 1, 2));
        stubActors();

        mockMvc.perform(post("/api/releases/{id}/items", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketKeys\":[\"CNV-1\",\"CNV-2\",\"CNV-3\",\"CNV-4\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1))
                .andExpect(jsonPath("$.alreadyPresent").value(1))
                .andExpect(jsonPath("$.notFound").value(2));
    }

    @Test
    void addTicketsWithAnEmptyListIsRejected() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/releases/{id}/items", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketKeys\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addTicketsWithMoreThanFiveHundredKeysIsRejected() throws Exception {
        UUID id = UUID.randomUUID();
        String keys = IntStream.rangeClosed(1, 501).mapToObj(i -> "\"CNV-" + i + "\"")
                .collect(Collectors.joining(","));

        mockMvc.perform(post("/api/releases/{id}/items", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketKeys\":[" + keys + "]}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).addTickets(any(), any());
    }

    @Test
    void addTicketsToAFrozenReleaseMapsTo409ReleaseFrozen() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.addTickets(eq(id), any())).thenThrow(new ReleaseFrozenException());

        mockMvc.perform(post("/api/releases/{id}/items", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketKeys\":[\"CNV-1\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("RELEASE_FROZEN"));
    }

    @Test
    void aJiraFailureDuringAddSurfacesAsItsExistingProblemDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.addTickets(eq(id), any()))
                .thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_UNREACHABLE));

        mockMvc.perform(post("/api/releases/{id}/items", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketKeys\":[\"CNV-1\"]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("JIRA_UNREACHABLE"));
    }

    @Test
    void unauthenticatedAddIsRejected() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/releases/{id}/items", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketKeys\":[\"CNV-1\"]}"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).addTickets(any(), any());
    }

    @Test
    void removeTicketReturnsTheUpdatedRelease() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.removeTicket(id, "CNV-1")).thenReturn(release(id, ReleaseStatus.OPEN));
        stubActors();

        mockMvc.perform(delete("/api/releases/{id}/items/CNV-1", id).with(csrf()).with(authenticated()))
                .andExpect(status().isOk());
    }

    @Test
    void refreshReturnsTheReleaseWithLastRefreshedAt() throws Exception {
        UUID id = UUID.randomUUID();
        Release refreshed = new Release(id, "Sprint 42", "2026.08", ReleaseStatus.OPEN, "CNV",
                Instant.parse("2026-08-04T09:00:00Z"), "557058:abc-123", null, null,
                Instant.parse("2026-08-05T09:00:00Z"), null, List.of(), List.of());
        when(service.refresh(id)).thenReturn(refreshed);
        stubActors();

        mockMvc.perform(post("/api/releases/{id}/refresh", id).with(csrf()).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastRefreshedAt").exists());
    }

    private static Release releaseWithLinkedItem(UUID id) {
        TicketDetail ticket = new TicketDetail("EXAMPLE-1", "Summary", "Bug", "Done", "Fixed", null, null,
                List.of(), null, "", List.of(), 0, "10001", null, null);
        CodeLinks links = new CodeLinks(EnrichmentStatus.LINKED, null, false, Instant.parse("2026-08-05T09:00:00Z"),
                CommitsSource.KEY_TAGGED,
                List.of(new LinkedPullRequest("repo", "7", "Fix", "MERGED", "https://pr", "feature/x", "main", true, null)),
                List.of(new LinkedCommit("0123456789abcdef", "msg", "Dev", Instant.parse("2026-08-04T10:00:00Z"), "https://c")),
                List.of("feature/x"), StatusHistory.notRead());
        return new Release(id, "Sprint 42", "2026.08", ReleaseStatus.OPEN, "EXAMPLE",
                Instant.parse("2026-08-04T09:00:00Z"), "557058:abc-123", null, null, null, null,
                List.of(new ReleaseItem(ticket, Instant.parse("2026-08-04T09:30:00Z"), links)), List.of());
    }

    @Test
    void getExposesCodeLinksOnEveryItem() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenReturn(releaseWithLinkedItem(id));
        stubActors();

        mockMvc.perform(get("/api/releases/{id}", id).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].codeLinks.status").value("LINKED"))
                .andExpect(jsonPath("$.items[0].codeLinks.commitsSource").value("KEY_TAGGED"))
                .andExpect(jsonPath("$.items[0].codeLinks.commits[0].shortSha").value("0123456"))
                .andExpect(jsonPath("$.items[0].codeLinks.commits[0].authoredAt").exists())
                .andExpect(jsonPath("$.items[0].codeLinks.pullRequests[0].id").value("7"))
                .andExpect(jsonPath("$.items[0].codeLinks.branches[0]").value("feature/x"));
    }

    @Test
    void getExposesPerTicketChurnFromTheSharedDashboardRules() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenReturn(releaseWithLinkedItem(id));
        when(dashboard.ticketChurn(id)).thenReturn(List.of(new TicketChurn("EXAMPLE-1", 3L, 1L, 2, true, true)));
        stubActors();

        mockMvc.perform(get("/api/releases/{id}", id).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].churn.commitsToDone").value(3))
                .andExpect(jsonPath("$.items[0].churn.postDoneCommits").value(1))
                .andExpect(jsonPath("$.items[0].churn.reopens").value(2))
                .andExpect(jsonPath("$.items[0].churn.keyTaggedOnly").value(true))
                .andExpect(jsonPath("$.items[0].churn.outlier").value(true));
        verify(dashboard, times(1)).ticketChurn(id);
    }

    @Test
    void aTicketWithNoChurnEntryHasNullChurn() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenReturn(releaseWithLinkedItem(id));
        stubActors();

        mockMvc.perform(get("/api/releases/{id}", id).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].churn").value(nullValue()));
    }

    @Test
    void unknownChurnFiguresStayNullRatherThanZero() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenReturn(releaseWithLinkedItem(id));
        when(dashboard.ticketChurn(id)).thenReturn(List.of(new TicketChurn("EXAMPLE-1", null, null, null, false, false)));
        stubActors();

        mockMvc.perform(get("/api/releases/{id}", id).with(authenticated()))
                .andExpect(jsonPath("$.items[0].churn.commitsToDone").value(nullValue()))
                .andExpect(jsonPath("$.items[0].churn.postDoneCommits").value(nullValue()))
                .andExpect(jsonPath("$.items[0].churn.reopens").value(nullValue()));
    }

    @Test
    void codeLinksRefreshForOneTicketReturnsTheRelease() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.refreshCodeLinks(id, "EXAMPLE-1")).thenReturn(releaseWithLinkedItem(id));
        stubActors();

        mockMvc.perform(post("/api/releases/{id}/code-links/refresh", id).param("ticketKey", "EXAMPLE-1")
                        .with(csrf()).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].codeLinks.status").value("LINKED"));
    }

    @Test
    void codeLinksRefreshOnAFrozenReleaseMapsTo409ReleaseFrozen() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.refreshCodeLinks(id, null)).thenThrow(new ReleaseFrozenException());

        mockMvc.perform(post("/api/releases/{id}/code-links/refresh", id).with(csrf()).with(authenticated()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("RELEASE_FROZEN"));
    }

    @Test
    void refreshOnAFrozenReleaseMapsTo409ReleaseFrozen() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.refresh(id)).thenThrow(new ReleaseFrozenException());

        mockMvc.perform(post("/api/releases/{id}/refresh", id).with(csrf()).with(authenticated()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("RELEASE_FROZEN"));
    }

    @Test
    void aPartialRefreshMapsTo422RefreshIncompleteRatherThanASilentSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.refresh(id)).thenThrow(new ReleaseRefreshIncompleteException());

        mockMvc.perform(post("/api/releases/{id}/refresh", id).with(csrf()).with(authenticated()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("REFRESH_INCOMPLETE"));
    }

    @Test
    void freezeReturnsTheFrozenReleaseAndRecordsTheActor() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.freeze(eq(id), any())).thenReturn(release(id, ReleaseStatus.FROZEN));
        stubActors();

        mockMvc.perform(post("/api/releases/{id}/freeze", id).with(csrf()).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.frozenBy").exists());

        ArgumentCaptor<String> actor = ArgumentCaptor.forClass(String.class);
        verify(service).freeze(eq(id), actor.capture());
        assertThat(actor.getValue()).isEqualTo("557058:abc-123");
    }

    @Test
    void freezingAnAlreadyFrozenReleaseMapsTo409ReleaseFrozen() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.freeze(eq(id), any())).thenThrow(new ReleaseFrozenException());

        mockMvc.perform(post("/api/releases/{id}/freeze", id).with(csrf()).with(authenticated()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("RELEASE_FROZEN"));
    }

    @Test
    void deleteAnOpenReleaseReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/releases/{id}", id).with(csrf()).with(authenticated()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).delete(id);
    }

    @Test
    void deletingAFrozenReleaseMapsTo409ReleaseFrozen() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ReleaseFrozenException()).when(service).delete(id);

        mockMvc.perform(delete("/api/releases/{id}", id).with(csrf()).with(authenticated()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("RELEASE_FROZEN"));
    }

    @Test
    void thereIsNoUnfreezeEndpoint() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/releases/{id}/unfreeze", id).with(csrf()).with(authenticated()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void unauthenticatedRequestsAreRejectedWithProblemDetail() throws Exception {
        mockMvc.perform(get("/api/releases"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.reason").value("UNAUTHENTICATED"));
    }

    @Test
    void unauthenticatedCreateIsRejected() throws Exception {
        mockMvc.perform(post("/api/releases").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint 42\",\"version\":\"2026.08\"}"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).createWithTickets(any(), any(), any(), any(), any());
    }

    private static Release releaseWithManualItem(UUID id, ManualItemCategory category) {
        ManualItem manual = new ManualItem(UUID.randomUUID(), "Rebuild search index",
                "Run scripts/reindex.sh on each node", category, Instant.parse("2026-08-04T09:31:00Z"));
        return new Release(id, "Sprint 42", "2026.08", ReleaseStatus.OPEN, "CNV",
                Instant.parse("2026-08-04T09:00:00Z"), "557058:abc-123", null, null, null, null,
                List.of(), List.of(manual));
    }

    @Test
    void addManualItemReturns201AndTheUpdatedRelease() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.addManualItem(eq(id), any(), any(), any()))
                .thenReturn(releaseWithManualItem(id, ManualItemCategory.DATABASE_SCRIPT));
        stubActors();

        mockMvc.perform(post("/api/releases/{id}/manual-items", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Rebuild search index\","
                                + "\"description\":\"Run scripts/reindex.sh on each node\","
                                + "\"category\":\"DATABASE_SCRIPT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.manualItems[0].category").value("DATABASE_SCRIPT"));
    }

    @Test
    void addManualItemWithABlankTitleIsRejected() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/releases/{id}/manual-items", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \",\"description\":\"desc\",\"category\":\"OTHER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.title").exists());

        verify(service, never()).addManualItem(any(), any(), any(), any());
    }

    @Test
    void addManualItemWithAnUnknownCategoryIsRejected() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/releases/{id}/manual-items", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Title\",\"description\":\"desc\","
                                + "\"category\":\"SOMETHING_ELSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("BAD_REQUEST"));

        verify(service, never()).addManualItem(any(), any(), any(), any());
    }

    @Test
    void anUnknownManualItemIdMapsTo404ManualItemNotFoundNotReleaseNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        UUID manualItemId = UUID.randomUUID();
        when(service.updateManualItem(eq(id), eq(manualItemId), any(), any(), any()))
                .thenThrow(new ManualItemNotFoundException());

        mockMvc.perform(put("/api/releases/{id}/manual-items/{itemId}", id, manualItemId)
                        .with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Title\",\"description\":\"d\",\"category\":\"OTHER\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("MANUAL_ITEM_NOT_FOUND"));
    }

    // D-04 applies to every error body, including the ones the framework builds for itself.
    @Test
    void aMethodTheApiDoesNotSupportStillCarriesAMachineReadableReason() throws Exception {
        mockMvc.perform(put("/api/releases").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.reason").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void aMalformedJsonBodyStillCarriesAMachineReadableReason() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/releases/{id}/items", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketKeys\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("BAD_REQUEST"));

        verify(service, never()).addTickets(any(), any());
    }

    @Test
    void addManualItemWithNoCategoryIsRejected() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/releases/{id}/manual-items", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Title\",\"description\":\"desc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addManualItemWithAnOverlongDescriptionIsRejected() throws Exception {
        UUID id = UUID.randomUUID();
        String overlongDescription = "a".repeat(5001);

        mockMvc.perform(post("/api/releases/{id}/manual-items", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Title\",\"description\":\"" + overlongDescription + "\","
                                + "\"category\":\"OTHER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addManualItemWithNoDescriptionIsAccepted() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.addManualItem(eq(id), any(), any(), any()))
                .thenReturn(releaseWithManualItem(id, ManualItemCategory.OTHER));
        stubActors();

        mockMvc.perform(post("/api/releases/{id}/manual-items", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Title\",\"category\":\"OTHER\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void updateManualItemReturnsTheUpdatedRelease() throws Exception {
        UUID id = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(service.updateManualItem(eq(id), eq(itemId), any(), any(), any()))
                .thenReturn(releaseWithManualItem(id, ManualItemCategory.INFRASTRUCTURE));
        stubActors();

        mockMvc.perform(put("/api/releases/{id}/manual-items/{itemId}", id, itemId)
                        .with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\",\"description\":\"Updated description\","
                                + "\"category\":\"INFRASTRUCTURE\"}"))
                .andExpect(status().isOk());

        verify(service).updateManualItem(eq(id), eq(itemId), any(), any(), any());
    }

    @Test
    void removeManualItemReturnsTheUpdatedRelease() throws Exception {
        UUID id = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(service.removeManualItem(id, itemId)).thenReturn(release(id, ReleaseStatus.OPEN));
        stubActors();

        mockMvc.perform(delete("/api/releases/{id}/manual-items/{itemId}", id, itemId)
                        .with(csrf()).with(authenticated()))
                .andExpect(status().isOk());
    }

    @Test
    void manualItemWritesOnAFrozenReleaseMapTo409ReleaseFrozen() throws Exception {
        UUID id = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(service.addManualItem(any(), any(), any(), any())).thenThrow(new ReleaseFrozenException());
        when(service.updateManualItem(any(), any(), any(), any(), any())).thenThrow(new ReleaseFrozenException());
        org.mockito.Mockito.doThrow(new ReleaseFrozenException()).when(service).removeManualItem(any(), any());

        mockMvc.perform(post("/api/releases/{id}/manual-items", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Title\",\"category\":\"OTHER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("RELEASE_FROZEN"));

        mockMvc.perform(put("/api/releases/{id}/manual-items/{itemId}", id, itemId)
                        .with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Title\",\"category\":\"OTHER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("RELEASE_FROZEN"));

        mockMvc.perform(delete("/api/releases/{id}/manual-items/{itemId}", id, itemId)
                        .with(csrf()).with(authenticated()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("RELEASE_FROZEN"));
    }

    @Test
    void manualItemWriteOnAnUnknownReleaseMapsTo404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.addManualItem(any(), any(), any(), any())).thenThrow(new ReleaseNotFoundException());

        mockMvc.perform(post("/api/releases/{id}/manual-items", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Title\",\"category\":\"OTHER\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("RELEASE_NOT_FOUND"));
    }

    @Test
    void unauthenticatedManualItemWriteIsRejected() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/releases/{id}/manual-items", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Title\",\"category\":\"OTHER\"}"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).addManualItem(any(), any(), any(), any());
    }
}
