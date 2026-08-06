package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.SessionRefreshException;
import com.evision.releasemgmt.application.JiraSiteConfigService.JiraSiteNotConfiguredException;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.release.ManualItemNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseDuplicateException;
import com.evision.releasemgmt.domain.release.ReleaseFrozenException;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseRefreshIncompleteException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    @ExceptionHandler(ReleaseNotFoundException.class)
    public ProblemDetail handleReleaseNotFound(ReleaseNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Release not found",
                "This release doesn't exist or has been deleted.", "RELEASE_NOT_FOUND");
    }

    @ExceptionHandler(ReleaseFrozenException.class)
    public ProblemDetail handleReleaseFrozen(ReleaseFrozenException ex) {
        return problem(HttpStatus.CONFLICT, "Release frozen",
                "This release is frozen and can no longer be changed.", "RELEASE_FROZEN");
    }

    @ExceptionHandler(ManualItemNotFoundException.class)
    public ProblemDetail handleManualItemNotFound(ManualItemNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Manual item not found",
                "This manual item doesn't exist or has already been removed.",
                "MANUAL_ITEM_NOT_FOUND");
    }

    @ExceptionHandler(ReleaseRefreshIncompleteException.class)
    public ProblemDetail handleReleaseRefreshIncomplete(ReleaseRefreshIncompleteException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Release not fully refreshed",
                "Jira did not return every ticket on this release, so nothing was refreshed. "
                        + "Remove the tickets that no longer exist in Jira, or check your Jira "
                        + "permissions, then try again.",
                "REFRESH_INCOMPLETE");
    }

    @ExceptionHandler(ReleaseDuplicateException.class)
    public ProblemDetail handleReleaseDuplicate(ReleaseDuplicateException ex) {
        return problem(HttpStatus.CONFLICT, "Duplicate release",
                "A release with this name and version already exists. Choose a different name or version.",
                "RELEASE_DUPLICATE");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed path parameter",
                "A value in the request path was not of the expected type.", "VALIDATION_FAILED");
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

    // D-04: the SPA keys all of its error copy on `reason`, but every ProblemDetail the framework
    // builds for itself - malformed JSON, an unknown enum value, a wrong method or media type -
    // arrives without one, and rawFetch degrades that to 'UNKNOWN'. The status name is exactly
    // the machine-readable token those paths are missing.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail pd
                && (pd.getProperties() == null || !pd.getProperties().containsKey("reason"))) {
            pd.setProperty("reason", HttpStatus.valueOf(statusCode.value()).name());
        }
        return response;
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
