package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.auth.JiraCredential;
import com.evision.releasemgmt.domain.jira.JiraSiteConfig;
import com.evision.releasemgmt.ports.CurrentUserPort;
import com.evision.releasemgmt.ports.JiraCredentialRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JiraAccessContextServiceTest {

    private static final String SITE_URL = "https://acme.atlassian.net";

    private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
    private final JiraCredentialRepository credentialRepository = mock(JiraCredentialRepository.class);
    private final JiraSiteConfigService jiraSiteConfigService = mock(JiraSiteConfigService.class);

    private final JiraAccessContextService service =
            new JiraAccessContextService(currentUserPort, credentialRepository, jiraSiteConfigService);

    @Test
    void resolvesContextForTheCurrentPrincipal() {
        when(currentUserPort.currentAccountId()).thenReturn(Optional.of("557058:aaa"));
        when(credentialRepository.findByAccountId("557058:aaa"))
                .thenReturn(Optional.of(new JiraCredential("557058:aaa", "aaa@acme.com", "tok-aaa", 1, Instant.now())));
        when(jiraSiteConfigService.get()).thenReturn(new JiraSiteConfig(SITE_URL, Instant.now(), "actor"));

        JiraAccessContext context = service.currentContext();

        assertThat(context.accountId()).isEqualTo("557058:aaa");
        assertThat(context.email()).isEqualTo("aaa@acme.com");
        assertThat(context.apiToken()).isEqualTo("tok-aaa");
        assertThat(context.siteBaseUrl()).isEqualTo(SITE_URL);
    }

    @Test
    void perUserIsolationLookupKeyIsAlwaysTheCurrentPrincipal() {
        when(jiraSiteConfigService.get()).thenReturn(new JiraSiteConfig(SITE_URL, Instant.now(), "actor"));
        when(credentialRepository.findByAccountId("557058:aaa"))
                .thenReturn(Optional.of(new JiraCredential("557058:aaa", "aaa@acme.com", "tok-aaa", 1, Instant.now())));
        when(credentialRepository.findByAccountId("557058:bbb"))
                .thenReturn(Optional.of(new JiraCredential("557058:bbb", "bbb@acme.com", "tok-bbb", 1, Instant.now())));

        when(currentUserPort.currentAccountId()).thenReturn(Optional.of("557058:aaa"));
        assertThat(service.currentContext().apiToken()).isEqualTo("tok-aaa");

        when(currentUserPort.currentAccountId()).thenReturn(Optional.of("557058:bbb"));
        assertThat(service.currentContext().apiToken()).isEqualTo("tok-bbb");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(credentialRepository, times(2)).findByAccountId(captor.capture());
        assertThat(captor.getAllValues()).containsExactly("557058:aaa", "557058:bbb");
    }

    @Test
    void noAuthenticatedPrincipalThrowsInvalidCredentials() {
        when(currentUserPort.currentAccountId()).thenReturn(Optional.empty());

        assertThatThrownBy(service::currentContext)
                .isInstanceOf(JiraAuthenticationException.class)
                .extracting(ex -> ((JiraAuthenticationException) ex).reason())
                .isEqualTo(JiraAuthFailureReason.INVALID_CREDENTIALS);
    }

    @Test
    void authenticatedPrincipalWithNoCredentialRowThrowsInvalidCredentials() {
        when(currentUserPort.currentAccountId()).thenReturn(Optional.of("557058:revoked"));
        when(credentialRepository.findByAccountId("557058:revoked")).thenReturn(Optional.empty());

        assertThatThrownBy(service::currentContext)
                .isInstanceOf(JiraAuthenticationException.class)
                .extracting(ex -> ((JiraAuthenticationException) ex).reason())
                .isEqualTo(JiraAuthFailureReason.INVALID_CREDENTIALS);
    }

    @Test
    void noConfiguredSitePropagatesJiraSiteNotConfiguredException() {
        when(currentUserPort.currentAccountId()).thenReturn(Optional.of("557058:aaa"));
        when(credentialRepository.findByAccountId("557058:aaa"))
                .thenReturn(Optional.of(new JiraCredential("557058:aaa", "aaa@acme.com", "tok-aaa", 1, Instant.now())));
        when(jiraSiteConfigService.get()).thenThrow(new JiraSiteConfigService.JiraSiteNotConfiguredException());

        assertThatThrownBy(service::currentContext)
                .isInstanceOf(JiraSiteConfigService.JiraSiteNotConfiguredException.class);
    }
}
