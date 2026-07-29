package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.AuthCookieWriter;
import com.evision.releasemgmt.adapters.security.JwtTokenService;
import com.evision.releasemgmt.application.JiraAccessContextService;
import com.evision.releasemgmt.application.JiraSiteConfigService;
import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraCredential;
import com.evision.releasemgmt.domain.jira.JiraSiteConfig;
import com.evision.releasemgmt.ports.JiraCredentialRepository;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"stub-jira", "local"})
@Testcontainers
class AuthControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JiraSiteConfigService jiraSiteConfigService;

    @Autowired
    private JiraCredentialRepository credentialRepository;

    @Autowired
    private JiraAccessContextService jiraAccessContextService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginWithValidCredentialsIssuesCookiesAndReturnsIdentity() throws Exception {
        MvcResult result = performValidLogin();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);
        assertThat(setCookies).anyMatch(c -> c.startsWith("access_token=") && c.contains("HttpOnly") && c.contains("SameSite=Strict"));
        assertThat(setCookies).anyMatch(c -> c.startsWith("refresh_token=") && c.contains("Path=/api/auth"));
    }

    @Test
    void loginResponseBodyCarriesNoTokenMaterial() throws Exception {
        MvcResult result = performValidLogin();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("valid-token");
        Map<String, Object> json = JsonPath.parse(body).read("$");
        assertThat(json.keySet()).noneMatch(key -> key.toLowerCase().contains("token"));
    }

    @Test
    void loginStoresTheCredentialEncrypted() throws Exception {
        performValidLogin();

        String rawColumn = jdbcTemplate.queryForObject(
                "SELECT encrypted_token FROM jira_credential WHERE jira_account_id = 'dev-account-id'", String.class);
        assertThat(rawColumn).doesNotContain("valid-token");
    }

    @Test
    void loginWithInvalidTokenReturnsActionableProblemDetailAndPersistsNothing() throws Exception {
        Integer rowsBefore = credentialRowCount("dev-account-id");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dev@example.com\",\"apiToken\":\"wrong-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.reason").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail", Matchers.containsString("no longer valid")))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(credentialRowCount("dev-account-id")).isEqualTo(rowsBefore);
    }

    @Test
    void loginWithoutConfiguredSiteReturnsSiteNotConfigured() throws Exception {
        JiraSiteConfig previousConfig = jiraSiteConfigService.get();
        jdbcTemplate.update("DELETE FROM jira_site_config");
        try {
            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"dev@example.com\",\"apiToken\":\"valid-token\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.reason").value("SITE_NOT_CONFIGURED"));
        } finally {
            jiraSiteConfigService.updateBaseUrl(previousConfig.baseUrl(), previousConfig.updatedBy());
        }
    }

    @Test
    void loginWithInvalidPayloadReturnsValidationFailed() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"apiToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dev@example.com\",\"apiToken\":\"valid-token\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sessionEndpointReflectsSignedInPrincipalAndRejectsAnonymous() throws Exception {
        MvcResult loginResult = performValidLogin();
        String accessTokenValue = extractCookieValue(
                loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE), AuthCookieWriter.ACCESS_COOKIE);

        mockMvc.perform(get("/api/auth/session")
                        .cookie(new MockCookie(AuthCookieWriter.ACCESS_COOKIE, accessTokenValue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("dev-account-id"))
                .andExpect(jsonPath("$.email").value("dev@example.com"));

        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("UNAUTHENTICATED"));
    }

    @Test
    void refreshRotatesIdleWindowWhilePreservingAbsoluteCap() throws Exception {
        MvcResult loginResult = performValidLogin();
        List<String> loginCookies = loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String refreshTokenValue = extractCookieValue(loginCookies, AuthCookieWriter.REFRESH_COOKIE);
        String originalAbsoluteExpiry = JsonPath.parse(loginResult.getResponse().getContentAsString())
                .read("$.absoluteExpiry", String.class);

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(new MockCookie(AuthCookieWriter.REFRESH_COOKIE, refreshTokenValue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.absoluteExpiry").value(originalAbsoluteExpiry))
                .andReturn();

        assertThat(refreshResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(2);
    }

    @Test
    void refreshRejectsAccessTokenPresentedAsRefreshToken() throws Exception {
        MvcResult loginResult = performValidLogin();
        String accessTokenValue = extractCookieValue(
                loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE), AuthCookieWriter.ACCESS_COOKIE);

        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(new MockCookie(AuthCookieWriter.REFRESH_COOKIE, accessTokenValue)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("INVALID_SESSION"));
    }

    @Test
    void refreshWithoutCookieReturnsInvalidSession() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("INVALID_SESSION"));
    }

    @Test
    void logoutClearsBothCookiesAndSubsequentSessionCallWithNoCookieIsUnauthorized() throws Exception {
        MvcResult loginResult = performValidLogin();
        String accessTokenValue = extractCookieValue(
                loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE), AuthCookieWriter.ACCESS_COOKIE);

        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .cookie(new MockCookie(AuthCookieWriter.ACCESS_COOKIE, accessTokenValue)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn();

        List<String> logoutCookies = logoutResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(logoutCookies).hasSize(2);
        assertThat(logoutCookies).allMatch(c -> c.contains("Max-Age=0"));

        // simulate a real browser's cleared cookie jar: no access_token cookie is sent at all
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jiraAccessContextServiceResolvesPerSignedInPrincipal() throws Exception {
        performValidLogin();
        credentialRepository.upsert("other-account-id", "other@acme.com", "other-token");

        setAuthenticatedPrincipal("dev-account-id", "dev@example.com");
        JiraAccessContext devContext = jiraAccessContextService.currentContext();
        assertThat(devContext.accountId()).isEqualTo("dev-account-id");
        assertThat(devContext.apiToken()).isEqualTo("valid-token");

        setAuthenticatedPrincipal("other-account-id", "other@acme.com");
        JiraAccessContext otherContext = jiraAccessContextService.currentContext();
        assertThat(otherContext.accountId()).isEqualTo("other-account-id");
        assertThat(otherContext.apiToken()).isEqualTo("other-token");
    }

    @Test
    void replaceCredentialWithValidTokenReturnsNoContent() throws Exception {
        MvcResult loginResult = performValidLogin();
        String accessTokenValue = extractCookieValue(
                loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE), AuthCookieWriter.ACCESS_COOKIE);

        mockMvc.perform(put("/api/auth/credential")
                        .with(csrf())
                        .cookie(new MockCookie(AuthCookieWriter.ACCESS_COOKIE, accessTokenValue))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiToken\":\"valid-token\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void replaceCredentialIsNonDestructiveToTheStoredRowAndTheSiteConfig() throws Exception {
        MvcResult loginResult = performValidLogin();
        String accessTokenValue = extractCookieValue(
                loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE), AuthCookieWriter.ACCESS_COOKIE);

        Timestamp createdAtBefore = jdbcTemplate.queryForObject(
                "SELECT created_at FROM jira_credential WHERE jira_account_id = 'dev-account-id'", Timestamp.class);
        Timestamp updatedAtBefore = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM jira_credential WHERE jira_account_id = 'dev-account-id'", Timestamp.class);
        Map<String, Object> siteConfigBefore = jdbcTemplate.queryForMap("SELECT * FROM jira_site_config");

        mockMvc.perform(put("/api/auth/credential")
                        .with(csrf())
                        .cookie(new MockCookie(AuthCookieWriter.ACCESS_COOKIE, accessTokenValue))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiToken\":\"valid-token\"}"))
                .andExpect(status().isNoContent());

        assertThat(credentialRowCount("dev-account-id")).isEqualTo(1);
        String accountIdAfter = jdbcTemplate.queryForObject(
                "SELECT jira_account_id FROM jira_credential WHERE jira_account_id = 'dev-account-id'", String.class);
        assertThat(accountIdAfter).isEqualTo("dev-account-id");
        Timestamp createdAtAfter = jdbcTemplate.queryForObject(
                "SELECT created_at FROM jira_credential WHERE jira_account_id = 'dev-account-id'", Timestamp.class);
        assertThat(createdAtAfter).isEqualTo(createdAtBefore);
        Timestamp updatedAtAfter = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM jira_credential WHERE jira_account_id = 'dev-account-id'", Timestamp.class);
        assertThat(updatedAtAfter.after(updatedAtBefore)).isTrue();
        Map<String, Object> siteConfigAfter = jdbcTemplate.queryForMap("SELECT * FROM jira_site_config");
        assertThat(siteConfigAfter).isEqualTo(siteConfigBefore);
    }

    @Test
    void replaceCredentialStoresTheNewTokenEncryptedAtRest() throws Exception {
        MvcResult loginResult = performValidLogin();
        String accessTokenValue = extractCookieValue(
                loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE), AuthCookieWriter.ACCESS_COOKIE);

        mockMvc.perform(put("/api/auth/credential")
                        .with(csrf())
                        .cookie(new MockCookie(AuthCookieWriter.ACCESS_COOKIE, accessTokenValue))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiToken\":\"valid-token\"}"))
                .andExpect(status().isNoContent());

        String rawColumn = jdbcTemplate.queryForObject(
                "SELECT encrypted_token FROM jira_credential WHERE jira_account_id = 'dev-account-id'", String.class);
        assertThat(rawColumn).doesNotContain("valid-token");
    }

    @Test
    void replaceCredentialWithWrongTokenLeavesWorkingCredentialIntact() throws Exception {
        MvcResult loginResult = performValidLogin();
        String accessTokenValue = extractCookieValue(
                loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE), AuthCookieWriter.ACCESS_COOKIE);

        mockMvc.perform(put("/api/auth/credential")
                        .with(csrf())
                        .cookie(new MockCookie(AuthCookieWriter.ACCESS_COOKIE, accessTokenValue))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiToken\":\"wrong-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.reason").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail", Matchers.containsString("no longer valid")));

        JiraCredential stillWorking = credentialRepository.findByAccountId("dev-account-id").orElseThrow();
        assertThat(stillWorking.apiToken()).isEqualTo("valid-token");
    }

    @Test
    void replaceCredentialWithoutAuthenticationIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/auth/credential")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiToken\":\"valid-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("UNAUTHENTICATED"));
    }

    @Test
    void replaceCredentialWithNoAccessCookieAndNoCsrfTokenIsRejectedByCsrfFilterItself() throws Exception {
        // 01-05-SUMMARY.md's documented, unavoidable residual risk (threat_flag:
        // csrf-bypass-residual-risk): oauth2ResourceServer() unconditionally exempts any
        // request a bearer token is resolvable from - i.e. any request carrying the
        // access_token cookie, valid or not - from CSRF protection, with no public API to
        // remove that exemption (SecurityFilterChainIT.unsafeWriteWithoutAnyAccessCookieOrCsrfIsRejectedByCsrfFilterItself
        // proves the same thing for PUT /api/settings/jira-site). SecurityConfig is out of
        // scope for this plan, so this proves csrf.spa() is still enforced for this new
        // endpoint the one way that is actually testable: no access_token cookie at all.
        mockMvc.perform(put("/api/auth/credential")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiToken\":\"valid-token\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void replaceCredentialWithBlankTokenReturnsValidationFailed() throws Exception {
        MvcResult loginResult = performValidLogin();
        String accessTokenValue = extractCookieValue(
                loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE), AuthCookieWriter.ACCESS_COOKIE);

        MvcResult result = mockMvc.perform(put("/api/auth/credential")
                        .with(csrf())
                        .cookie(new MockCookie(AuthCookieWriter.ACCESS_COOKIE, accessTokenValue))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("\"apiToken\":\"\"");
    }

    private MvcResult performValidLogin() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dev@example.com\",\"apiToken\":\"valid-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("dev-account-id"))
                .andExpect(jsonPath("$.email").value("dev@example.com"))
                .andExpect(jsonPath("$.absoluteExpiry").exists())
                .andReturn();
    }

    private Integer credentialRowCount(String accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jira_credential WHERE jira_account_id = ?", Integer.class, accountId);
    }

    private String extractCookieValue(List<String> setCookieHeaders, String cookieName) {
        return setCookieHeaders.stream()
                .filter(header -> header.startsWith(cookieName + "="))
                .map(header -> header.substring((cookieName + "=").length()).split(";")[0])
                .findFirst()
                .orElseThrow();
    }

    private void setAuthenticatedPrincipal(String accountId, String email) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(accountId)
                .claim(JwtTokenService.CLAIM_EMAIL, email)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, Collections.emptyList()));
    }
}
