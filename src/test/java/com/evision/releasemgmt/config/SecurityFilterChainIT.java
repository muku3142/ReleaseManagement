package com.evision.releasemgmt.config;

import com.evision.releasemgmt.adapters.security.AuthCookieWriter;
import com.evision.releasemgmt.adapters.security.JwtTokenService;
import com.evision.releasemgmt.adapters.security.TokenPair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"stub-jira", "local"})
@Testcontainers
class SecurityFilterChainIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void jiraSiteLookupIsPublicToAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/settings/jira-site"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedWriteWithoutAuthenticationIsUnauthorizedWithProblemDetail() throws Exception {
        mockMvc.perform(put("/api/settings/jira-site")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://acme-it-unauth.atlassian.net\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.reason").value("UNAUTHENTICATED"));
    }

    @Test
    void protectedWriteWithValidAccessCookieAndCsrfSucceedsAndRecordsActor() throws Exception {
        TokenPair pair = jwtTokenService.issue("557058:abc-123", "rm@acme.com");

        mockMvc.perform(put("/api/settings/jira-site")
                        .with(csrf())
                        .cookie(new MockCookie(AuthCookieWriter.ACCESS_COOKIE, pair.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://acme-it-test.atlassian.net\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedBy").value("557058:abc-123"));
    }

    @Test
    void unsafeWriteWithoutAnyAccessCookieOrCsrfIsRejectedByCsrfFilterItself() throws Exception {
        // See "Deviations" / Threat Flags in 01-05-SUMMARY.md: Spring's oauth2ResourceServer()
        // unconditionally exempts any request from which a bearer token is resolvable (here,
        // the access_token cookie) from CSRF checking (CsrfFilter's requireCsrfProtectionMatcher
        // is AND-ed with a negated BearerTokenRequestMatcher, registered internally regardless
        // of DSL ordering - see protectedWriteWithValidAccessCookieAndCsrfSucceedsAndRecordsActor,
        // which succeeds without csrf.spa() ever being exercised because the cookie exempts it).
        // A request with no access_token cookie at all is NOT exempt, so CsrfFilter itself
        // rejects it (403, running before the authentication filter) - proving csrf.spa() is
        // genuinely wired into the chain and not accidentally disabled.
        mockMvc.perform(put("/api/settings/jira-site")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://acme-it-test-2.atlassian.net\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshTokenPresentedInAccessCookieIsRejected() throws Exception {
        TokenPair pair = jwtTokenService.issue("557058:abc-123", "rm@acme.com");

        mockMvc.perform(put("/api/settings/jira-site")
                        .with(csrf())
                        .cookie(new MockCookie(AuthCookieWriter.ACCESS_COOKIE, pair.refreshToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://acme-it-test-3.atlassian.net\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientSideRouteServesIndexHtmlWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
