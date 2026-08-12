package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.application.ReleaseViewAssembler;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.application.TemplateService;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.GroupingDimension;
import com.evision.releasemgmt.domain.template.RenderBoundExceededException;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.domain.template.TemplateMalformedException;
import com.evision.releasemgmt.domain.template.TemplateNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TemplateController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class TemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TemplateService templateService;

    @MockitoBean
    private ReleaseService releaseService;

    @MockitoBean
    private ReleaseViewAssembler assembler;

    @MockitoBean
    private ActorResolutionService actorResolution;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"));
    }

    private static Template template(UUID id, boolean active) {
        Instant now = Instant.parse("2026-08-09T09:00:00Z");
        return new Template(id, "Standard notes", TemplateDocumentType.RELEASE_NOTES, "Body", active,
                ChromeFields.empty(), GroupingDimension.ISSUE_TYPE, null, List.of(), now, "557058:abc-123",
                now, "557058:abc-123");
    }

    private void stubActors() {
        when(actorResolution.resolve(any(String[].class)))
                .thenReturn(new ResolvedActors(Map.of("557058:abc-123", "rm@acme.com")));
    }

    @Test
    void listReturnsTemplateSummaries() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.list()).thenReturn(List.of(template(id, true)));

        mockMvc.perform(get("/api/templates").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templates[0].id").value(id.toString()))
                .andExpect(jsonPath("$.templates[0].name").value("Standard notes"))
                .andExpect(jsonPath("$.templates[0].documentType").value("RELEASE_NOTES"))
                .andExpect(jsonPath("$.templates[0].active").value(true))
                .andExpect(jsonPath("$.templates[0].hasLogo").value(false))
                .andExpect(jsonPath("$.templates[0].warningCount").value(0));
    }

    @Test
    void templateListStillCarriesNoActorField() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.list()).thenReturn(List.of(template(id, true)));

        mockMvc.perform(get("/api/templates").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templates[0].createdBy").doesNotExist());

        verifyNoInteractions(actorResolution);
    }

    @Test
    void createReturns201AndRecordsTheAuthenticatedPrincipalAsActor() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.create(any(), any(), any())).thenReturn(template(id, false));
        stubActors();

        mockMvc.perform(post("/api/templates").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Standard\",\"documentType\":\"RELEASE_NOTES\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> actor = ArgumentCaptor.forClass(String.class);
        verify(templateService).create(eq("Standard"), eq(TemplateDocumentType.RELEASE_NOTES),
                actor.capture());
        assertThat(actor.getValue()).isEqualTo("557058:abc-123");
    }

    @Test
    void createWithABlankNameIsRejectedBeforeAnyServiceCall() throws Exception {
        mockMvc.perform(post("/api/templates").with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"documentType\":\"RELEASE_NOTES\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void getAnUnknownTemplateMapsTo404TemplateNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.get(id)).thenThrow(new TemplateNotFoundException());

        mockMvc.perform(get("/api/templates/{id}", id).with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("TEMPLATE_NOT_FOUND"));
    }

    @Test
    void createdByAndUpdatedByAreResolvedEmails() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.get(id)).thenReturn(template(id, true));
        stubActors();

        mockMvc.perform(get("/api/templates/{id}", id).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").value("rm@acme.com"))
                .andExpect(jsonPath("$.updatedBy").value("rm@acme.com"));

        verify(actorResolution, times(1)).resolve(any(String[].class));
    }

    @Test
    void anUnresolvedTemplateAuthorFallsBackToTheAccountId() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.get(id)).thenReturn(template(id, true));
        when(actorResolution.resolve(any(String[].class))).thenReturn(new ResolvedActors(Map.of()));

        mockMvc.perform(get("/api/templates/{id}", id).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").value("557058:abc-123"));
    }

    @Test
    void saveContentWithAMalformedChromeFieldReturns422WithTheFieldNamedInTheDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.saveContent(eq(id), any(), any(), any(), any()))
                .thenThrow(new TemplateMalformedException("Cover title",
                        "Partials are not supported: {{>header}}."));

        mockMvc.perform(put("/api/templates/{id}/content", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validContentBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("TEMPLATE_MALFORMED"))
                .andExpect(jsonPath("$.detail")
                        .value("Cover title: Partials are not supported: {{>header}}."));
    }

    @Test
    void saveContentWithAMalformedBodyReturnsTheDetailWithNoFieldPrefix() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.saveContent(eq(id), any(), any(), any(), any()))
                .thenThrow(new TemplateMalformedException("Body", "Partials are not supported: {{>x}}."));

        mockMvc.perform(put("/api/templates/{id}/content", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validContentBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Partials are not supported: {{>x}}."));
    }

    @Test
    void saveContentWithARenderBoundBreachReturns422RenderBoundExceeded() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.saveContent(eq(id), any(), any(), any(), any()))
                .thenThrow(new RenderBoundExceededException());

        mockMvc.perform(put("/api/templates/{id}/content", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validContentBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("RENDER_BOUND_EXCEEDED"));
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/templates/{id}", id).with(csrf()).with(authenticated()))
                .andExpect(status().isNoContent());

        verify(templateService).delete(id);
    }

    @Test
    void catalogReturnsPlaceholderEntriesAndBackendDeclaredLogoLimits() throws Exception {
        mockMvc.perform(get("/api/templates/catalog").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isNotEmpty())
                .andExpect(jsonPath("$.logoLimits.maxBytes").value(2097152))
                .andExpect(jsonPath("$.logoLimits.maxWidth").value(2000))
                .andExpect(jsonPath("$.logoLimits.maxHeight").value(2000))
                .andExpect(jsonPath("$.logoLimits.contentTypes")
                        .value(org.hamcrest.Matchers.hasItems("image/png", "image/jpeg")));
    }

    private static String validContentBody() {
        return contentBodyWithLogoHeight("45");
    }

    private static String contentBodyWithLogoHeight(String mm) {
        return "{\"body\":\"Release notes\",\"chrome\":{\"coverEnabled\":false,\"coverTitle\":\"\","
                + "\"coverSubtitle\":\"\",\"headerText\":\"\",\"footerText\":\"\","
                + "\"pageNumbersEnabled\":false,\"logoRenderHeightMm\":" + mm + "}}";
    }

    @Test
    void saveContentRejectsALogoHeightAboveThePrintableBound() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/api/templates/{id}/content", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contentBodyWithLogoHeight("121")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));

        verify(templateService, never()).saveContent(any(), any(), any(), any(), any());
    }

    @Test
    void saveContentDefaultsAnAbsentLogoHeightInsteadOfRejectingIt() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.saveContent(any(), any(), any(), any(), any())).thenReturn(template(id, false));
        stubActors();

        // A payload from a client build that predates this field - Integer + the controller's default,
        // not a primitive int that would deserialise to 0 and fail @Min(5).
        mockMvc.perform(put("/api/templates/{id}/content", id).with(csrf()).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Release notes\",\"chrome\":{\"coverEnabled\":false,"
                                + "\"coverTitle\":\"\",\"coverSubtitle\":\"\",\"headerText\":\"\","
                                + "\"footerText\":\"\",\"pageNumbersEnabled\":false}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ChromeFields> chrome = ArgumentCaptor.forClass(ChromeFields.class);
        verify(templateService).saveContent(any(), any(), chrome.capture(), any(), any());
        assertThat(chrome.getValue().logoRenderHeightMm()).isEqualTo(30);
    }
}
