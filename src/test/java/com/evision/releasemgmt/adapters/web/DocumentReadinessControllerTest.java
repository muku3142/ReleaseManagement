package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.DocumentReadinessService;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.document.DocumentReadinessState;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentReadinessController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class DocumentReadinessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentReadinessService service;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"));
    }

    @Test
    void readinessReturnsTheReleaseIdDocumentTypeAndStateForEveryEntry() throws Exception {
        UUID releaseId = UUID.randomUUID();
        when(service.all()).thenReturn(List.of(
                new DocumentReadinessService.ReadinessEntry(releaseId, TemplateDocumentType.RELEASE_NOTES,
                        DocumentReadinessState.UP_TO_DATE)));

        mockMvc.perform(get("/api/documents/readiness").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].releaseId").value(releaseId.toString()))
                .andExpect(jsonPath("$.entries[0].documentType").value("RELEASE_NOTES"))
                .andExpect(jsonPath("$.entries[0].state").value("UP_TO_DATE"));
    }

    @Test
    void emptyDatabaseReturnsAnEmptyEntriesArrayNotA404() throws Exception {
        when(service.all()).thenReturn(List.of());

        mockMvc.perform(get("/api/documents/readiness").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries.length()").value(0));
    }
}
