package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.DocumentExportService;
import com.evision.releasemgmt.application.DocumentGenerationService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.document.DocumentNotFoundException;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.document.NoActiveTemplateException;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.LogoMetadata;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentGenerationService service;

    @MockitoBean
    private DocumentExportService exportService;

    @MockitoBean
    private ReleaseRepository releaseRepository;

    @MockitoBean
    private ActorResolutionService actorResolution;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"));
    }

    private static DocumentSnapshot snapshot(UUID releaseId, TemplateDocumentType type, int revision,
                                             UUID templateId) {
        Instant now = Instant.parse("2026-08-10T09:00:00Z");
        return new DocumentSnapshot(UUID.randomUUID(), releaseId, type, revision, "# Notes", templateId,
                "Standard notes", "Body", ChromeFields.empty(), now, "557058:abc-123");
    }

    private void stubActors() {
        when(actorResolution.resolve(any(String[].class)))
                .thenReturn(new ResolvedActors(Map.of("557058:abc-123", "rm@acme.com")));
    }

    @Test
    void postGeneratesAndReturns201WithRevisionOne() throws Exception {
        UUID releaseId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        DocumentSnapshot snapshot = snapshot(releaseId, TemplateDocumentType.RELEASE_NOTES, 1, templateId);
        when(service.generate(eq(releaseId), eq(TemplateDocumentType.RELEASE_NOTES), any())).thenReturn(snapshot);
        when(service.nextRevision(releaseId, TemplateDocumentType.RELEASE_NOTES)).thenReturn(2);
        when(service.templateStillExists(templateId)).thenReturn(true);
        when(service.hasLogo(snapshot.id())).thenReturn(false);
        stubActors();

        mockMvc.perform(post("/api/releases/{releaseId}/documents/{documentType}", releaseId, "RELEASE_NOTES")
                        .with(csrf()).with(authenticated()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.latestRevision").value(1))
                .andExpect(jsonPath("$.templateDeleted").value(false))
                .andExpect(jsonPath("$.hasLogo").value(false));

        verify(service).generate(eq(releaseId), eq(TemplateDocumentType.RELEASE_NOTES), eq("557058:abc-123"));
    }

    @Test
    void postWithNoActiveTemplateReturns409WithReasonNoActiveTemplate() throws Exception {
        UUID releaseId = UUID.randomUUID();
        when(service.generate(eq(releaseId), eq(TemplateDocumentType.RELEASE_NOTES), any()))
                .thenThrow(new NoActiveTemplateException());

        mockMvc.perform(post("/api/releases/{releaseId}/documents/{documentType}", releaseId, "RELEASE_NOTES")
                        .with(csrf()).with(authenticated()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("NO_ACTIVE_TEMPLATE"));
    }

    @Test
    void getUnknownDocumentReturns404WithReasonDocumentNotFound() throws Exception {
        UUID releaseId = UUID.randomUUID();
        when(service.latest(releaseId, TemplateDocumentType.RELEASE_NOTES))
                .thenThrow(new DocumentNotFoundException());

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}", releaseId, "RELEASE_NOTES")
                        .with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void getWithRevisionQueryParamReturnsThatRevision() throws Exception {
        UUID releaseId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        DocumentSnapshot snapshot = snapshot(releaseId, TemplateDocumentType.RELEASE_NOTES, 3, templateId);
        when(service.revision(releaseId, TemplateDocumentType.RELEASE_NOTES, 3)).thenReturn(snapshot);
        when(service.nextRevision(releaseId, TemplateDocumentType.RELEASE_NOTES)).thenReturn(4);
        when(service.templateStillExists(templateId)).thenReturn(true);
        stubActors();

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}", releaseId, "RELEASE_NOTES")
                        .param("revision", "3")
                        .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(3))
                .andExpect(jsonPath("$.latestRevision").value(3));

        verify(service, never()).latest(any(), any());
    }

    @Test
    void getRevisionsReturnsNewestFirstWithLatestFlagOnTheHighest() throws Exception {
        UUID releaseId = UUID.randomUUID();
        DocumentSnapshot revisionTwo = snapshot(releaseId, TemplateDocumentType.RELEASE_NOTES, 2, UUID.randomUUID());
        DocumentSnapshot revisionOne = snapshot(releaseId, TemplateDocumentType.RELEASE_NOTES, 1, UUID.randomUUID());
        when(service.revisions(releaseId, TemplateDocumentType.RELEASE_NOTES))
                .thenReturn(List.of(revisionTwo, revisionOne));
        stubActors();

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}/revisions", releaseId,
                        "RELEASE_NOTES").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisions[0].revision").value(2))
                .andExpect(jsonPath("$.revisions[0].latest").value(true))
                .andExpect(jsonPath("$.revisions[1].revision").value(1))
                .andExpect(jsonPath("$.revisions[1].latest").value(false));
    }

    @Test
    void generatedByIsTheResolvedEmail() throws Exception {
        UUID releaseId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        DocumentSnapshot snapshot = snapshot(releaseId, TemplateDocumentType.RELEASE_NOTES, 1, templateId);
        when(service.latest(releaseId, TemplateDocumentType.RELEASE_NOTES)).thenReturn(snapshot);
        when(service.nextRevision(releaseId, TemplateDocumentType.RELEASE_NOTES)).thenReturn(2);
        when(service.templateStillExists(templateId)).thenReturn(true);
        stubActors();

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}", releaseId, "RELEASE_NOTES")
                        .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedBy").value("rm@acme.com"));
    }

    @Test
    void revisionHistoryResolvesEveryRowFromASingleLookup() throws Exception {
        UUID releaseId = UUID.randomUUID();
        DocumentSnapshot revisionTwo = snapshot(releaseId, TemplateDocumentType.RELEASE_NOTES, 2, UUID.randomUUID());
        DocumentSnapshot revisionOne = snapshot(releaseId, TemplateDocumentType.RELEASE_NOTES, 1, UUID.randomUUID());
        when(service.revisions(releaseId, TemplateDocumentType.RELEASE_NOTES))
                .thenReturn(List.of(revisionTwo, revisionOne));
        stubActors();

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}/revisions", releaseId,
                        "RELEASE_NOTES").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisions[0].generatedBy").value("rm@acme.com"))
                .andExpect(jsonPath("$.revisions[1].generatedBy").value("rm@acme.com"));

        verify(actorResolution, times(1)).resolve(any(String[].class));
    }

    @Test
    void anUnresolvedActorFallsBackToTheAccountId() throws Exception {
        UUID releaseId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        DocumentSnapshot snapshot = snapshot(releaseId, TemplateDocumentType.RELEASE_NOTES, 1, templateId);
        when(service.latest(releaseId, TemplateDocumentType.RELEASE_NOTES)).thenReturn(snapshot);
        when(service.nextRevision(releaseId, TemplateDocumentType.RELEASE_NOTES)).thenReturn(2);
        when(service.templateStillExists(templateId)).thenReturn(true);
        when(actorResolution.resolve(any(String[].class))).thenReturn(new ResolvedActors(Map.of()));

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}", releaseId, "RELEASE_NOTES")
                        .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedBy").value("557058:abc-123"));
    }

    @Test
    void getWithUnknownDocumentTypeReturns400WithReasonValidationFailed() throws Exception {
        UUID releaseId = UUID.randomUUID();

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}", releaseId, "NOT_A_TYPE")
                        .with(authenticated()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void logoReturnsNoSniffAndInlineDisposition() throws Exception {
        UUID releaseId = UUID.randomUUID();
        LogoMetadata metadata = new LogoMetadata("image/png", "logo.png", 1024, 100, 100,
                Instant.parse("2026-08-01T00:00:00Z"));
        LogoImage image = new LogoImage(new byte[]{1, 2, 3}, metadata);
        when(service.logo(releaseId, TemplateDocumentType.RELEASE_NOTES, null)).thenReturn(Optional.of(image));

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}/logo", releaseId, "RELEASE_NOTES")
                        .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"logo.png\""))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"));
    }

    @Test
    void templateDeletedIsTrueWhenTheSourceTemplateIsGone() throws Exception {
        UUID releaseId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        DocumentSnapshot snapshot = snapshot(releaseId, TemplateDocumentType.RELEASE_NOTES, 1, templateId);
        when(service.latest(releaseId, TemplateDocumentType.RELEASE_NOTES)).thenReturn(snapshot);
        when(service.nextRevision(releaseId, TemplateDocumentType.RELEASE_NOTES)).thenReturn(2);
        when(service.templateStillExists(templateId)).thenReturn(false);
        stubActors();

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}", releaseId, "RELEASE_NOTES")
                        .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateDeleted").value(true));
    }
}
