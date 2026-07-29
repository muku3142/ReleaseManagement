package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.auth.JiraIdentity;
import com.evision.releasemgmt.ports.JiraAuthPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("stub-jira")
public class StubJiraAuthAdapter implements JiraAuthPort {

    @Override
    public JiraIdentity validate(String siteBaseUrl, String email, String apiToken) {
        if ("dev@example.com".equals(email) && "valid-token".equals(apiToken)) {
            return new JiraIdentity("dev-account-id", email, "Dev User");
        }
        throw new JiraAuthenticationException(JiraAuthFailureReason.INVALID_CREDENTIALS);
    }
}
