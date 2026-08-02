package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.SessionRefreshException;
import com.evision.releasemgmt.application.JiraSiteConfigService.JiraSiteNotConfiguredException;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(JiraSiteNotConfiguredException.class)
    public ProblemDetail handleJiraSiteNotConfigured(JiraSiteNotConfiguredException ex) {
        return problem(HttpStatus.CONFLICT, "Jira site not configured",
                "No Jira Cloud site is configured yet.", "SITE_NOT_CONFIGURED");
    }

    @ExceptionHandler(JiraAuthenticationException.class)
    public ProblemDetail handleJiraAuthFailure(JiraAuthenticationException ex) {
        return switch (ex.reason()) {
            case INVALID_CREDENTIALS -> problem(HttpStatus.UNAUTHORIZED,
                    "Jira credential rejected",
                    "Your Jira credential is no longer valid. Enter your Jira email and a current API token.",
                    "INVALID_CREDENTIALS");
            case JIRA_FORBIDDEN -> problem(HttpStatus.FORBIDDEN,
                    "Jira access denied",
                    "The Jira account authenticated successfully but is not permitted to access the configured site.",
                    "JIRA_FORBIDDEN");
            case JIRA_RATE_LIMITED -> problem(HttpStatus.TOO_MANY_REQUESTS,
                    "Jira rate limit reached",
                    "Jira is rate-limiting requests. Wait a moment and try again.",
                    "JIRA_RATE_LIMITED");
            case JIRA_UNREACHABLE -> problem(HttpStatus.SERVICE_UNAVAILABLE,
                    "Jira unreachable",
                    "The configured Jira site could not be reached. Check the site URL in Settings and your network.",
                    "JIRA_UNREACHABLE");
            case JIRA_ERROR -> problem(HttpStatus.BAD_GATEWAY,
                    "Jira returned an error",
                    "The configured Jira site returned an unexpected error.",
                    "JIRA_ERROR");
            case SITE_NOT_CONFIGURED -> problem(HttpStatus.CONFLICT,
                    "Jira site not configured",
                    "No Jira Cloud site is configured. Set one in Settings before signing in.",
                    "SITE_NOT_CONFIGURED");
            case ACCOUNT_MISMATCH -> problem(HttpStatus.CONFLICT,
                    "Token belongs to a different Jira account",
                    "That API token authenticates as a different Jira account than the one you are signed in as. Sign out and sign in again with that account.",
                    "ACCOUNT_MISMATCH");
            case RESULT_SET_TOO_LARGE -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Too many matching tickets",
                    "This query matched too many tickets to load. Narrow your fix version or JQL filter.",
                    "RESULT_SET_TOO_LARGE");
        };
    }

    @ExceptionHandler(SessionRefreshException.class)
    public ProblemDetail handleSessionRefreshFailure(SessionRefreshException ex) {
        return switch (ex.reason()) {
            case SESSION_EXPIRED -> problem(HttpStatus.UNAUTHORIZED,
                    "Session expired",
                    "Your session has expired. Sign in again with your Jira email and API token.",
                    ex.reason().name());
            case INVALID_SESSION -> problem(HttpStatus.UNAUTHORIZED,
                    "Session invalid",
                    "Your session is no longer valid. Sign in again.",
                    ex.reason().name());
        };
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                   HttpHeaders headers,
                                                                   HttpStatusCode status,
                                                                   WebRequest request) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Request validation failed",
                "One or more fields failed validation.", "VALIDATION_FAILED");
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        pd.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error handling request", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error",
                "An unexpected error occurred. Please try again later.", "INTERNAL_ERROR");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String reason) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle(title);
        pd.setDetail(detail);
        pd.setProperty("reason", reason);
        return pd;
    }
}
