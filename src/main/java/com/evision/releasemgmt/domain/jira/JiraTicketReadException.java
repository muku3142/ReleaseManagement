package com.evision.releasemgmt.domain.jira;

import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;

// Extends rather than sitting alongside JiraAuthenticationException so Spring routes it to
// the one existing @ExceptionHandler and the eight-arm reason switch is never duplicated.
public class JiraTicketReadException extends JiraAuthenticationException {

    public JiraTicketReadException(JiraAuthFailureReason reason) {
        super(reason);
    }
}
