package com.evision.releasemgmt.domain.auth;

public class JiraAuthenticationException extends RuntimeException {

    private final JiraAuthFailureReason reason;

    public JiraAuthenticationException(JiraAuthFailureReason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public JiraAuthFailureReason reason() {
        return reason;
    }
}
