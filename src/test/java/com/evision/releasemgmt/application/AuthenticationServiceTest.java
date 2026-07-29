package com.evision.releasemgmt.application;

import com.evision.releasemgmt.adapters.security.JwtTokenService;
import com.evision.releasemgmt.adapters.security.SessionRefreshException;
import com.evision.releasemgmt.adapters.security.TokenPair;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.auth.JiraCredential;
import com.evision.releasemgmt.domain.auth.JiraIdentity;
import com.evision.releasemgmt.domain.jira.JiraSiteConfig;
import com.evision.releasemgmt.ports.JiraAuthPort;
import com.evision.releasemgmt.ports.JiraCredentialRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AuthenticationServiceTest {

    private static final String SITE_URL = "https://acme.atlassian.net";

    private final JiraSiteConfigService jiraSiteConfigService = mock(JiraSiteConfigService.class);
    private final JiraAuthPort jiraAuthPort = mock(JiraAuthPort.class);
    private final JiraCredentialRepository credentialRepository = mock(JiraCredentialRepository.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final JwtDecoder accessTokenDecoder = mock(JwtDecoder.class);

    private final AuthenticationService service = new AuthenticationService(
            jiraSiteConfigService, jiraAuthPort, credentialRepository, jwtTokenService, accessTokenDecoder);

    @Test
    void loginValidatesAgainstJiraBeforePersistingAndIssuingTokens() {
        when(jiraSiteConfigService.get()).thenReturn(new JiraSiteConfig(SITE_URL, Instant.now(), "actor"));
        when(jiraAuthPort.validate(SITE_URL, "rm@acme.com", "tok-123"))
                .thenReturn(new JiraIdentity("557058:abc-123", "rm@acme.com", "Release Manager"));
        Instant absoluteExpiry = Instant.now().plusSeconds(3600);
        TokenPair pair = new TokenPair("access-jwt", "refresh-jwt", absoluteExpiry);
        when(jwtTokenService.issue("557058:abc-123", "rm@acme.com")).thenReturn(pair);

        AuthenticationService.LoginResult result = service.login("rm@acme.com", "tok-123");

        InOrder inOrder = inOrder(jiraAuthPort, credentialRepository, jwtTokenService);
        inOrder.verify(jiraAuthPort).validate(SITE_URL, "rm@acme.com", "tok-123");
        inOrder.verify(credentialRepository).upsert("557058:abc-123", "rm@acme.com", "tok-123");
        inOrder.verify(jwtTokenService).issue("557058:abc-123", "rm@acme.com");

        assertThat(result.user().accountId()).isEqualTo("557058:abc-123");
        assertThat(result.user().email()).isEqualTo("rm@acme.com");
        assertThat(result.user().absoluteExpiry()).isEqualTo(absoluteExpiry);
        assertThat(result.pair()).isEqualTo(pair);
    }

    @Test
    void loginDoesNotPersistOrIssueTokensWhenCredentialsAreInvalid() {
        when(jiraSiteConfigService.get()).thenReturn(new JiraSiteConfig(SITE_URL, Instant.now(), "actor"));
        when(jiraAuthPort.validate(SITE_URL, "rm@acme.com", "bad-token"))
                .thenThrow(new JiraAuthenticationException(JiraAuthFailureReason.INVALID_CREDENTIALS));

        assertThatThrownBy(() -> service.login("rm@acme.com", "bad-token"))
                .isInstanceOf(JiraAuthenticationException.class)
                .extracting(ex -> ((JiraAuthenticationException) ex).reason())
                .isEqualTo(JiraAuthFailureReason.INVALID_CREDENTIALS);

        verifyNoInteractions(credentialRepository);
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void loginPreservesJiraUnreachableReasonWithoutPersistingAnything() {
        when(jiraSiteConfigService.get()).thenReturn(new JiraSiteConfig(SITE_URL, Instant.now(), "actor"));
        when(jiraAuthPort.validate(SITE_URL, "rm@acme.com", "tok-123"))
                .thenThrow(new JiraAuthenticationException(JiraAuthFailureReason.JIRA_UNREACHABLE));

        assertThatThrownBy(() -> service.login("rm@acme.com", "tok-123"))
                .isInstanceOf(JiraAuthenticationException.class)
                .extracting(ex -> ((JiraAuthenticationException) ex).reason())
                .isEqualTo(JiraAuthFailureReason.JIRA_UNREACHABLE);

        verifyNoInteractions(credentialRepository);
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void loginPropagatesSiteNotConfiguredWithoutCallingJira() {
        when(jiraSiteConfigService.get()).thenThrow(new JiraSiteConfigService.JiraSiteNotConfiguredException());

        assertThatThrownBy(() -> service.login("rm@acme.com", "tok-123"))
                .isInstanceOf(JiraSiteConfigService.JiraSiteNotConfiguredException.class);

        verifyNoInteractions(jiraAuthPort);
        verifyNoInteractions(credentialRepository);
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void loginStoresTheSubmittedEmailEvenWhenIdentityEmailIsNull() {
        when(jiraSiteConfigService.get()).thenReturn(new JiraSiteConfig(SITE_URL, Instant.now(), "actor"));
        when(jiraAuthPort.validate(SITE_URL, "rm@acme.com", "tok-123"))
                .thenReturn(new JiraIdentity("acc", null, "Name"));
        TokenPair pair = new TokenPair("access-jwt", "refresh-jwt", Instant.now().plusSeconds(3600));
        when(jwtTokenService.issue(eq("acc"), any())).thenReturn(pair);

        service.login("rm@acme.com", "tok-123");

        verify(credentialRepository).upsert("acc", "rm@acme.com", "tok-123");
    }

    @Test
    void refreshDelegatesToJwtTokenServiceAndPreservesAbsoluteExpiry() {
        Instant absoluteExpiry = Instant.now().plusSeconds(1800);
        TokenPair pair = new TokenPair("new-access-jwt", "new-refresh-jwt", absoluteExpiry);
        when(jwtTokenService.refresh("old-refresh-jwt")).thenReturn(pair);
        Jwt decoded = Jwt.withTokenValue("new-access-jwt")
                .header("alg", "HS256")
                .subject("557058:abc-123")
                .claim(JwtTokenService.CLAIM_EMAIL, "rm@acme.com")
                .build();
        when(accessTokenDecoder.decode("new-access-jwt")).thenReturn(decoded);

        AuthenticationService.LoginResult result = service.refresh("old-refresh-jwt");

        assertThat(result.user().accountId()).isEqualTo("557058:abc-123");
        assertThat(result.user().email()).isEqualTo("rm@acme.com");
        assertThat(result.user().absoluteExpiry()).isEqualTo(absoluteExpiry);
        assertThat(result.pair()).isEqualTo(pair);
    }

    @Test
    void refreshPropagatesSessionRefreshExceptionReasonsUnchanged() {
        when(jwtTokenService.refresh("invalid"))
                .thenThrow(new SessionRefreshException(SessionRefreshException.Reason.INVALID_SESSION));
        when(jwtTokenService.refresh("expired"))
                .thenThrow(new SessionRefreshException(SessionRefreshException.Reason.SESSION_EXPIRED));

        assertThatThrownBy(() -> service.refresh("invalid"))
                .isInstanceOf(SessionRefreshException.class)
                .extracting(ex -> ((SessionRefreshException) ex).reason())
                .isEqualTo(SessionRefreshException.Reason.INVALID_SESSION);

        assertThatThrownBy(() -> service.refresh("expired"))
                .isInstanceOf(SessionRefreshException.class)
                .extracting(ex -> ((SessionRefreshException) ex).reason())
                .isEqualTo(SessionRefreshException.Reason.SESSION_EXPIRED);
    }

    @Test
    void replaceTokenRevalidatesAgainstJiraThenUpsertsInPlace() {
        when(credentialRepository.findByAccountId("557058:abc-123"))
                .thenReturn(Optional.of(new JiraCredential("557058:abc-123", "rm@acme.com", "old-tok", 1, Instant.now())));
        when(jiraSiteConfigService.get()).thenReturn(new JiraSiteConfig(SITE_URL, Instant.now(), "actor"));
        when(jiraAuthPort.validate(SITE_URL, "rm@acme.com", "new-tok"))
                .thenReturn(new JiraIdentity("557058:abc-123", "rm@acme.com", "Release Manager"));

        service.replaceToken("557058:abc-123", "new-tok");

        InOrder inOrder = inOrder(jiraAuthPort, credentialRepository);
        inOrder.verify(jiraAuthPort).validate(SITE_URL, "rm@acme.com", "new-tok");
        inOrder.verify(credentialRepository).upsert("557058:abc-123", "rm@acme.com", "new-tok");
    }

    @Test
    void replaceTokenPersistsNothingWhenValidationFails() {
        when(credentialRepository.findByAccountId("557058:abc-123"))
                .thenReturn(Optional.of(new JiraCredential("557058:abc-123", "rm@acme.com", "old-tok", 1, Instant.now())));
        when(jiraSiteConfigService.get()).thenReturn(new JiraSiteConfig(SITE_URL, Instant.now(), "actor"));
        when(jiraAuthPort.validate(SITE_URL, "rm@acme.com", "wrong-tok"))
                .thenThrow(new JiraAuthenticationException(JiraAuthFailureReason.INVALID_CREDENTIALS));

        assertThatThrownBy(() -> service.replaceToken("557058:abc-123", "wrong-tok"))
                .isInstanceOf(JiraAuthenticationException.class)
                .extracting(ex -> ((JiraAuthenticationException) ex).reason())
                .isEqualTo(JiraAuthFailureReason.INVALID_CREDENTIALS);

        verify(credentialRepository).findByAccountId("557058:abc-123");
        verifyNoMoreInteractions(credentialRepository);
    }

    @Test
    void replaceTokenRejectsAccountMismatchWithoutPersisting() {
        when(credentialRepository.findByAccountId("557058:abc-123"))
                .thenReturn(Optional.of(new JiraCredential("557058:abc-123", "rm@acme.com", "old-tok", 1, Instant.now())));
        when(jiraSiteConfigService.get()).thenReturn(new JiraSiteConfig(SITE_URL, Instant.now(), "actor"));
        when(jiraAuthPort.validate(SITE_URL, "rm@acme.com", "other-tok"))
                .thenReturn(new JiraIdentity("557058:zzz", "other@acme.com", "Other User"));

        assertThatThrownBy(() -> service.replaceToken("557058:abc-123", "other-tok"))
                .isInstanceOf(JiraAuthenticationException.class)
                .extracting(ex -> ((JiraAuthenticationException) ex).reason())
                .isEqualTo(JiraAuthFailureReason.ACCOUNT_MISMATCH);

        verify(credentialRepository, never()).upsert(any(), any(), any());
    }

    @Test
    void replaceTokenWithNoStoredCredentialFailsBeforeValidating() {
        when(credentialRepository.findByAccountId("no-such-account")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceToken("no-such-account", "tok"))
                .isInstanceOf(JiraAuthenticationException.class)
                .extracting(ex -> ((JiraAuthenticationException) ex).reason())
                .isEqualTo(JiraAuthFailureReason.INVALID_CREDENTIALS);

        verifyNoInteractions(jiraAuthPort);
    }

    @Test
    void replaceTokenPropagatesSiteNotConfiguredWithoutValidating() {
        when(credentialRepository.findByAccountId("557058:abc-123"))
                .thenReturn(Optional.of(new JiraCredential("557058:abc-123", "rm@acme.com", "old-tok", 1, Instant.now())));
        when(jiraSiteConfigService.get()).thenThrow(new JiraSiteConfigService.JiraSiteNotConfiguredException());

        assertThatThrownBy(() -> service.replaceToken("557058:abc-123", "tok"))
                .isInstanceOf(JiraSiteConfigService.JiraSiteNotConfiguredException.class);

        verifyNoInteractions(jiraAuthPort);
    }
}
