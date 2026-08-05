package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseDuplicateException;
import com.evision.releasemgmt.domain.release.ReleaseFrozenException;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"));
    }

    private static Release release(UUID id, ReleaseStatus status) {
        return new Release(id, "Sprint 42", "2026.08", status, "CNV",
                Instant.parse("2026-08-04T09:00:00Z"), "557058:abc-123",
                status == ReleaseStatus.FROZEN ? Instant.parse("2026-08-04T10:00:00Z") : null,
                status == ReleaseStatus.FROZEN ? "557058:abc-123" : null,
                null, List.of(), List.of());
    }

    @Test
    void createReturns201AndRecordsTheAuthenticatedPrincipalAsActor() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(), any(), any(), any())).thenReturn(release(id, ReleaseStatus.OPEN));

        mockMvc.perform(post("/api/releases").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint 42\",\"version\":\"2026.08\",\"jiraProjectKey\":\"CNV\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sprint 42"))
                .andExpect(jsonPath("$.version").value("2026.08"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        ArgumentCaptor<String> actor = ArgumentCaptor.forClass(String.class);
        verify(service).create(eq("Sprint 42"), eq("2026.08"), eq("CNV"), actor.capture());
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

        verify(service, never()).create(any(), any(), any(), any());
    }

    @Test
    void createWithBlankVersionIsRejected() throws Exception {
        mockMvc.perform(post("/api/releases").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint 42\",\"version\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.version").exists());

        verify(service, never()).create(any(), any(), any(), any());
    }

    @Test
    void createWithAnOverlongNameIsRejected() throws Exception {
        String overlongName = "a".repeat(201);

        mockMvc.perform(post("/api/releases").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + overlongName + "\",\"version\":\"1.0\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).create(any(), any(), any(), any());
    }

    @Test
    void duplicateNameAndVersionMapsTo409ReleaseDuplicate() throws Exception {
        when(service.create(any(), any(), any(), any())).thenThrow(new ReleaseDuplicateException());

        mockMvc.perform(post("/api/releases").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint 42\",\"version\":\"2026.08\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("RELEASE_DUPLICATE"));
    }

    @Test
    void nonSemverVersionIsAccepted() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(), any(), any(), any())).thenReturn(release(id, ReleaseStatus.OPEN));

        mockMvc.perform(post("/api/releases").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hotfix\",\"version\":\"Sprint 42\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void getReturnsTheFullReleaseIncludingItemDetail() throws Exception {
        UUID id = UUID.randomUUID();
        TicketDetail ticket = new TicketDetail("CNV-1", "Summary", "Bug", "Done", "Fixed",
                "Dev Person", "Reporter Person", List.of("2026.1"), "Full description",
                List.of("First comment", "Second comment"), 2);
        ReleaseItem item = new ReleaseItem(ticket, Instant.parse("2026-08-04T09:30:00Z"));
        ManualItem manual = new ManualItem(UUID.randomUUID(), "Run migration", "Apply schema change",
                ManualItemCategory.DATABASE_SCRIPT, Instant.parse("2026-08-04T09:31:00Z"));
        Release release = new Release(id, "Sprint 42", "2026.08", ReleaseStatus.OPEN, "CNV",
                Instant.parse("2026-08-04T09:00:00Z"), "557058:abc-123", null, null, null,
                List.of(item), List.of(manual));
        when(service.get(id)).thenReturn(release);

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
                null, null, null, 3);
        ReleaseSummary second = new ReleaseSummary(UUID.randomUUID(), "Sprint 41", "2026.07",
                ReleaseStatus.FROZEN, "CNV", Instant.parse("2026-07-04T09:00:00Z"), "557058:abc-123",
                Instant.parse("2026-07-05T09:00:00Z"), "557058:abc-123", null, 5);
        when(service.search(any())).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/releases").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releases.length()").value(2))
                .andExpect(jsonPath("$.releases[0].itemCount").value(3));
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
    void freezeReturnsTheFrozenReleaseAndRecordsTheActor() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.freeze(eq(id), any())).thenReturn(release(id, ReleaseStatus.FROZEN));

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

        verify(service, never()).create(any(), any(), any(), any());
    }
}
