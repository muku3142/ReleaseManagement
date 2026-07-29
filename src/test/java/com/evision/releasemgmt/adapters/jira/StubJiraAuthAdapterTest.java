package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.auth.JiraIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StubJiraAuthAdapterTest {

    private final StubJiraAuthAdapter adapter = new StubJiraAuthAdapter();

    @Test
    void returnsDevIdentityForTheCannedCredentialPair() {
        JiraIdentity identity = adapter.validate("https://ignored.atlassian.net", "dev@example.com", "valid-token");

        assertThat(identity).isEqualTo(new JiraIdentity("dev-account-id", "dev@example.com", "Dev User"));
    }

    @Test
    void rejectsAnyOtherEmailOrToken() {
        assertThatThrownBy(() -> adapter.validate("https://ignored.atlassian.net", "someone@else.com", "wrong-token"))
                .isInstanceOf(JiraAuthenticationException.class)
                .satisfies(ex -> assertThat(((JiraAuthenticationException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.INVALID_CREDENTIALS));
    }
}
