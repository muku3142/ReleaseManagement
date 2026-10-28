package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.application.JiraSiteConfigService;
import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraCredential;
import com.evision.releasemgmt.ports.BriefingSettingsRepository;
import com.evision.releasemgmt.ports.JiraCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** The Jira credential of the user who designated themselves as the briefing scheduler (D-17.1-19). */
@Service
public class BriefingJiraAccess {

    private static final Logger log = LoggerFactory.getLogger(BriefingJiraAccess.class);

    private final BriefingSettingsRepository settings;
    private final JiraCredentialRepository credentials;
    private final JiraSiteConfigService site;

    public BriefingJiraAccess(BriefingSettingsRepository settings, JiraCredentialRepository credentials,
            JiraSiteConfigService site) {
        this.settings = settings;
        this.credentials = credentials;
        this.site = site;
    }

    // Fresh on every call, never cached: a rotated or revoked token must take effect on the next run.
    public Optional<JiraAccessContext> designatedContext() {
        String accountId = settings.get().schedulerAccountId();
        if (accountId == null) {
            return Optional.empty();
        }
        try {
            Optional<JiraCredential> credential = credentials.findByAccountId(accountId);
            if (credential.isEmpty()) {
                return Optional.empty();
            }
            String siteBaseUrl = site.get().baseUrl();
            return Optional.of(new JiraAccessContext(siteBaseUrl, accountId, credential.get().email(),
                    credential.get().apiToken()));
        } catch (RuntimeException e) {
            // Decryption under a superseded key throws; the message may echo ciphertext, so only the type is logged.
            log.warn("briefing scheduler credential unavailable exceptionType={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
