package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.JiraSiteConfigService;
import com.evision.releasemgmt.application.JiraSiteConfigService.JiraSiteNotConfiguredException;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.jira.JiraSiteConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JiraSiteConfigController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class, ProblemDetailAuthenticationEntryPoint.class})
class JiraSiteConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JiraSiteConfigService service;

    @Test
    void getReturnsStoredBaseUrl() throws Exception {
        when(service.get()).thenReturn(new JiraSiteConfig("https://acme.atlassian.net", Instant.now(), "seed"));

        mockMvc.perform(get("/api/settings/jira-site"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseUrl").value("https://acme.atlassian.net"));
    }

    @Test
    void getReturnsConflictWhenNotConfigured() throws Exception {
        when(service.get()).thenThrow(new JiraSiteNotConfiguredException());

        mockMvc.perform(get("/api/settings/jira-site"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.reason").value("SITE_NOT_CONFIGURED"));
    }

    @Test
    void putUpdatesBaseUrl() throws Exception {
        when(service.updateBaseUrl(eq("https://acme.atlassian.net"), any()))
                .thenReturn(new JiraSiteConfig("https://acme.atlassian.net", Instant.now(), "557058:abc-123"));

        mockMvc.perform(put("/api/settings/jira-site")
                        .with(csrf())
                        .with(jwt().jwt(builder -> builder.subject("557058:abc-123").claim("token_type", "access")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://acme.atlassian.net\"}"))
                .andExpect(status().isOk());

        verify(service).updateBaseUrl(eq("https://acme.atlassian.net"), any());
    }

    @Test
    void putRejectsNonJiraCloudUrl() throws Exception {
        mockMvc.perform(put("/api/settings/jira-site")
                        .with(csrf())
                        .with(jwt().jwt(builder -> builder.subject("557058:abc-123").claim("token_type", "access")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"http://evil.example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));

        verify(service, never()).updateBaseUrl(any(), any());
    }

    @Test
    void putRejectsTrailingSlash() throws Exception {
        mockMvc.perform(put("/api/settings/jira-site")
                        .with(csrf())
                        .with(jwt().jwt(builder -> builder.subject("557058:abc-123").claim("token_type", "access")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://acme.atlassian.net/\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putWithoutAuthenticationIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/settings/jira-site")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://acme.atlassian.net\"}"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).updateBaseUrl(any(), any());
    }
}
