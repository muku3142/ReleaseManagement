package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class JiraAuthProblemDetailTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @ParameterizedTest
    @EnumSource(JiraAuthFailureReason.class)
    void mapsEachFailureReasonToADistinguishableProblemDetail(JiraAuthFailureReason reason) {
        ProblemDetail pd = handler.handleJiraAuthFailure(new JiraAuthenticationException(reason));

        assertThat(pd.getStatus()).isEqualTo(expectedStatus(reason).value());
        assertThat(pd.getProperties()).containsEntry("reason", reason.name());
        assertThat(pd.getDetail()).isNotBlank();
        assertThat(pd.getDetail()).doesNotContain("Basic ", "token=", "Authorization");
    }

    @Test
    void coversEveryDeclaredFailureReason() {
        for (JiraAuthFailureReason reason : JiraAuthFailureReason.values()) {
            ProblemDetail pd = handler.handleJiraAuthFailure(new JiraAuthenticationException(reason));
            assertThat(pd).isNotNull();
        }
        assertThat(JiraAuthFailureReason.values().length).isEqualTo(6);
    }

    private HttpStatus expectedStatus(JiraAuthFailureReason reason) {
        return switch (reason) {
            case INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case JIRA_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case JIRA_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case JIRA_UNREACHABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case JIRA_ERROR -> HttpStatus.BAD_GATEWAY;
            case SITE_NOT_CONFIGURED -> HttpStatus.CONFLICT;
        };
    }
}
