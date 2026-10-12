package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.AdoCredentialService;
import com.evision.releasemgmt.application.AdoCredentialService.View;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.ado.AdoAccessException;
import com.evision.releasemgmt.domain.ado.AdoCredentialState;
import com.evision.releasemgmt.domain.ado.AdoFailureReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

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
import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(AdoCredentialController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class, ProblemDetailAuthenticationEntryPoint.class})
class AdoCredentialControllerTest {

    private static final String URL = "/api/settings/azure-devops";
    private static final String SECRET = "super-secret-pat-value";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdoCredentialService service;

    @MockitoBean
    private ActorResolutionService actorResolution;

    private static String future() {
        return LocalDate.now().plusDays(60).toString();
    }

    private MockHttpServletRequestBuilder authedPut(String body) {
        return put(URL)
                .with(csrf())
                .with(jwt().jwt(builder -> builder.subject("557058:abc-123").claim("token_type", "access")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    void getUnconfigured() throws Exception {
        when(service.status()).thenReturn(new View(false, null, null, null, null, null));

        mockMvc.perform(get(URL)
                        .with(jwt().jwt(builder -> builder.subject("557058:abc-123").claim("token_type", "access"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void putNeverEchoesTheToken() throws Exception {
        when(service.save(eq("acme"), eq(SECRET), any(), any()))
                .thenReturn(new View(true, "acme", LocalDate.now().plusDays(60), AdoCredentialState.OK,
                        Instant.now(), "557058:abc-123"));
        when(actorResolution.resolve(any(String[].class)))
                .thenReturn(new ResolvedActors(Map.of("557058:abc-123", "rm@acme.com")));

        String json = mockMvc.perform(authedPut(
                        "{\"organization\":\"acme\",\"token\":\"" + SECRET + "\",\"expiresOn\":\"" + future() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.state").value("OK"))
                .andExpect(jsonPath("$.updatedBy").value("rm@acme.com"))
                .andReturn().getResponse().getContentAsString();

        assertThat(json).doesNotContain(SECRET);
    }

    @Test
    void rejectedTokenIs422() throws Exception {
        when(service.save(any(), any(), any(), any())).thenThrow(new AdoAccessException(AdoFailureReason.UNAUTHORIZED));

        mockMvc.perform(authedPut(
                        "{\"organization\":\"acme\",\"token\":\"" + SECRET + "\",\"expiresOn\":\"" + future() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.reason").value("ADO_UNAUTHORIZED"));
    }

    @Test
    void badOrganizationIsRejected() throws Exception {
        mockMvc.perform(authedPut("{\"organization\":\"a/b\",\"token\":\"x\",\"expiresOn\":\"" + future() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));

        verify(service, never()).save(any(), any(), any(), any());
    }

    @Test
    void pastExpiryIsRejected() throws Exception {
        mockMvc.perform(authedPut("{\"organization\":\"acme\",\"token\":\"x\",\"expiresOn\":\"2000-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }
}
