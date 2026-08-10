package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.DocumentExportService;
import com.evision.releasemgmt.application.DocumentGenerationService;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.document.DocumentNotFoundException;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.document.ExportFailedException;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.template.ChromeFields;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class DocumentExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentGenerationService service;

    @MockitoBean
    private DocumentExportService exportService;

    @MockitoBean
    private ReleaseRepository releaseRepository;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"));
    }

    private static DocumentSnapshot snapshot(UUID releaseId, int revision) {
        return new DocumentSnapshot(UUID.randomUUID(), releaseId, TemplateDocumentType.RELEASE_NOTES, revision,
                "# Notes", UUID.randomUUID(), "Standard notes", "Body", ChromeFields.empty(),
                Instant.parse("2026-08-10T09:00:00Z"), "557058:abc-123");
    }

    private static Release release(UUID releaseId, String name, String version) {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        return new Release(releaseId, name, version, ReleaseStatus.OPEN, "CNV", now, "actor-0", null, null,
                now, null, List.of(), List.of());
    }

    @Test
    void pdfExportSetsAttachmentDispositionAndPdfContentType() throws Exception {
        UUID releaseId = UUID.randomUUID();
        DocumentSnapshot snapshot = snapshot(releaseId, 2);
        when(service.latest(releaseId, TemplateDocumentType.RELEASE_NOTES)).thenReturn(snapshot);
        when(releaseRepository.findById(releaseId))
                .thenReturn(Optional.of(release(releaseId, "Orchstro", "4.2.0")));
        when(exportService.toPdf(releaseId, TemplateDocumentType.RELEASE_NOTES, null))
                .thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}/export.pdf", releaseId,
                        "RELEASE_NOTES").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Orchstro 4.2.0 Release Notes r2.pdf\""));
    }

    @Test
    void docxExportSetsTheOoxmlContentType() throws Exception {
        UUID releaseId = UUID.randomUUID();
        DocumentSnapshot snapshot = snapshot(releaseId, 1);
        when(service.latest(releaseId, TemplateDocumentType.RELEASE_NOTES)).thenReturn(snapshot);
        when(releaseRepository.findById(releaseId))
                .thenReturn(Optional.of(release(releaseId, "Orchstro", "4.2.0")));
        when(exportService.toDocx(releaseId, TemplateDocumentType.RELEASE_NOTES, null))
                .thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}/export.docx", releaseId,
                        "RELEASE_NOTES").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void bothExportsSetNoSniffAndNoStore() throws Exception {
        UUID releaseId = UUID.randomUUID();
        DocumentSnapshot snapshot = snapshot(releaseId, 1);
        when(service.latest(releaseId, TemplateDocumentType.RELEASE_NOTES)).thenReturn(snapshot);
        when(releaseRepository.findById(releaseId))
                .thenReturn(Optional.of(release(releaseId, "Orchstro", "4.2.0")));
        when(exportService.toPdf(releaseId, TemplateDocumentType.RELEASE_NOTES, null))
                .thenReturn(new byte[]{1});
        when(exportService.toDocx(releaseId, TemplateDocumentType.RELEASE_NOTES, null))
                .thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}/export.pdf", releaseId,
                        "RELEASE_NOTES").with(authenticated()))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}/export.docx", releaseId,
                        "RELEASE_NOTES").with(authenticated()))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void hostileReleaseNameCannotEscapeTheContentDispositionHeader() throws Exception {
        UUID releaseId = UUID.randomUUID();
        DocumentSnapshot snapshot = snapshot(releaseId, 1);
        when(service.latest(releaseId, TemplateDocumentType.RELEASE_NOTES)).thenReturn(snapshot);
        when(releaseRepository.findById(releaseId))
                .thenReturn(Optional.of(release(releaseId, "A\"B\r\nX-Injected: 1/../..", "1.0")));
        when(exportService.toPdf(releaseId, TemplateDocumentType.RELEASE_NOTES, null))
                .thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}/export.pdf", releaseId,
                        "RELEASE_NOTES").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.matchesPattern("^attachment; filename=\"[A-Za-z0-9 ._-]+\\.pdf\"$")))
                .andExpect(header().doesNotExist("X-Injected"));
    }

    @Test
    void filenameCarriesTheEffectiveRevisionWhenRevisionIsOmitted() throws Exception {
        UUID releaseId = UUID.randomUUID();
        DocumentSnapshot latest = snapshot(releaseId, 4);
        when(service.latest(releaseId, TemplateDocumentType.RELEASE_NOTES)).thenReturn(latest);
        when(releaseRepository.findById(releaseId))
                .thenReturn(Optional.of(release(releaseId, "Orchstro", "4.2.0")));
        when(exportService.toPdf(releaseId, TemplateDocumentType.RELEASE_NOTES, null))
                .thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}/export.pdf", releaseId,
                        "RELEASE_NOTES").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Orchstro 4.2.0 Release Notes r4.pdf\""));
    }

    @Test
    void exportOfAnUnknownDocumentReturns404WithReasonDocumentNotFound() throws Exception {
        UUID releaseId = UUID.randomUUID();
        when(service.latest(releaseId, TemplateDocumentType.RELEASE_NOTES))
                .thenThrow(new DocumentNotFoundException());

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}/export.pdf", releaseId,
                        "RELEASE_NOTES").with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void rendererFailureReturns502WithReasonExportFailed() throws Exception {
        UUID releaseId = UUID.randomUUID();
        DocumentSnapshot snapshot = snapshot(releaseId, 1);
        when(service.latest(releaseId, TemplateDocumentType.RELEASE_NOTES)).thenReturn(snapshot);
        when(releaseRepository.findById(releaseId))
                .thenReturn(Optional.of(release(releaseId, "Orchstro", "4.2.0")));
        when(exportService.toPdf(releaseId, TemplateDocumentType.RELEASE_NOTES, null))
                .thenThrow(new ExportFailedException(new RuntimeException("renderer blew up")));

        mockMvc.perform(get("/api/releases/{releaseId}/documents/{documentType}/export.pdf", releaseId,
                        "RELEASE_NOTES").with(authenticated()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.reason").value("EXPORT_FAILED"));
    }
}
