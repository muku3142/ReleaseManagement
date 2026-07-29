package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.auth.JiraCredential;
import com.evision.releasemgmt.ports.CurrentUserPort;
import com.evision.releasemgmt.ports.JiraCredentialRepository;
import org.springframework.stereotype.Service;

/**
 * The single supported way any code in this project obtains Jira credentials for an
 * outbound call. The lookup key is always the currently authenticated principal - there
 * is no overload taking an accountId and no path to any credential outside the caller's
 * own signed-in identity (AUTH-02).
 */
@Service
public class JiraAccessContextService {

    private final CurrentUserPort currentUserPort;
    private final JiraCredentialRepository credentialRepository;
    private final JiraSiteConfigService jiraSiteConfigService;

    public JiraAccessContextService(CurrentUserPort currentUserPort,
                                     JiraCredentialRepository credentialRepository,
                                     JiraSiteConfigService jiraSiteConfigService) {
        this.currentUserPort = currentUserPort;
        this.credentialRepository = credentialRepository;
        this.jiraSiteConfigService = jiraSiteConfigService;
    }

    public JiraAccessContext currentContext() {
        String accountId = currentUserPort.currentAccountId()
                .orElseThrow(() -> new JiraAuthenticationException(JiraAuthFailureReason.INVALID_CREDENTIALS));
        JiraCredential credential = credentialRepository.findByAccountId(accountId)
                .orElseThrow(() -> new JiraAuthenticationException(JiraAuthFailureReason.INVALID_CREDENTIALS));
        String siteBaseUrl = jiraSiteConfigService.get().baseUrl();
        return new JiraAccessContext(siteBaseUrl, accountId, credential.email(), credential.apiToken());
    }
}
