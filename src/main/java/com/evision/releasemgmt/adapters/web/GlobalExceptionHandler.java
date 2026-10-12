package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.SessionRefreshException;
import com.evision.releasemgmt.application.JiraSiteConfigService.JiraSiteNotConfiguredException;
import com.evision.releasemgmt.domain.ado.AdoAccessException;
import com.evision.releasemgmt.domain.ado.AdoCredentialTokenRequiredException;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.document.DocumentNotFoundException;
import com.evision.releasemgmt.domain.document.ExportFailedException;
import com.evision.releasemgmt.domain.document.ChosenTemplateNotFoundException;
import com.evision.releasemgmt.domain.document.NoTemplateException;
import com.evision.releasemgmt.domain.document.TemplateTypeMismatchException;
import com.evision.releasemgmt.domain.promotion.LadderEditRejectedException;
import com.evision.releasemgmt.domain.promotion.PromotionNotFoundException;
import com.evision.releasemgmt.domain.promotion.PromotionRejectedException;
import com.evision.releasemgmt.domain.promotion.StageNotFoundException;
import com.evision.releasemgmt.domain.release.ManualItemNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseDuplicateException;
import com.evision.releasemgmt.domain.release.ReleaseFrozenException;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseRefreshIncompleteException;
import com.evision.releasemgmt.domain.template.LogoRejectedException;
import com.evision.releasemgmt.domain.template.RenderBoundExceededException;
import com.evision.releasemgmt.domain.template.TemplateDuplicateException;
import com.evision.releasemgmt.domain.template.TemplateMalformedException;
import com.evision.releasemgmt.domain.template.TemplateNotFoundException;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

    @ExceptionHandler(AdoAccessException.class)
    public ProblemDetail handleAdoAccess(AdoAccessException ex) {
        // Never 401: the frontend treats 401 as the user's own Jira session failing.
        return switch (ex.reason()) {
            case UNAUTHORIZED -> problem(HttpStatus.UNPROCESSABLE_ENTITY, "Azure DevOps token rejected",
                    "Azure DevOps rejected this token. Use a token scoped to one organization with Code (Read) access.",
                    "ADO_UNAUTHORIZED");
            case NOT_FOUND -> problem(HttpStatus.UNPROCESSABLE_ENTITY, "Azure DevOps organization not found",
                    "No Azure DevOps organization with that name is reachable with this token.",
                    "ADO_ORGANIZATION_NOT_FOUND");
            case RATE_LIMITED -> problem(HttpStatus.SERVICE_UNAVAILABLE, "Azure DevOps rate limit reached",
                    "Azure DevOps is rate-limiting requests. Wait a moment and try again.", "ADO_RATE_LIMITED");
            case UNREACHABLE -> problem(HttpStatus.SERVICE_UNAVAILABLE, "Azure DevOps unreachable",
                    "Azure DevOps could not be reached. Try again later.", "ADO_UNREACHABLE");
            case ERROR, TOO_LARGE -> problem(HttpStatus.BAD_GATEWAY, "Azure DevOps returned an error",
                    "Azure DevOps returned an unexpected error.", "ADO_ERROR");
        };
    }

    @ExceptionHandler(AdoCredentialTokenRequiredException.class)
    public ProblemDetail handleAdoTokenRequired(AdoCredentialTokenRequiredException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Token required",
                "Enter the token: no Azure DevOps token is stored for this organization yet.", "ADO_TOKEN_REQUIRED");
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

    @ExceptionHandler(PromotionRejectedException.class)
    public ProblemDetail handlePromotionRejected(PromotionRejectedException ex) {
        return switch (ex.reason()) {
            case RELEASE_NOT_FROZEN -> problem(HttpStatus.CONFLICT, "Release not frozen",
                    "Only frozen releases can be promoted. Freeze the release first.", "RELEASE_NOT_FROZEN");
            case PROMOTION_STAGE_SKIPPED -> problem(HttpStatus.CONFLICT, "Stage cannot be targeted",
                    "A promotion can only target the next stage of the ladder, or the current stage for a redeploy or a rollback.",
                    "PROMOTION_STAGE_SKIPPED");
            case PROMOTION_TARGET_RETIRED -> problem(HttpStatus.CONFLICT, "Stage retired",
                    "That stage has been retired from the environment ladder and can no longer be targeted.",
                    "PROMOTION_TARGET_RETIRED");
            case PROMOTION_ROLLBACK_WITHOUT_SUCCESS -> problem(HttpStatus.CONFLICT, "Nothing to roll back",
                    "A rollback needs a successful deployment to that stage dated at or before the rollback.",
                    "PROMOTION_ROLLBACK_WITHOUT_SUCCESS");
            case PROMOTION_ALREADY_SUPERSEDED -> problem(HttpStatus.CONFLICT, "Entry already corrected",
                    "This entry has already been corrected. Correct the latest correction instead.",
                    "PROMOTION_ALREADY_SUPERSEDED");
            case PROMOTION_DEPLOYED_AT_INVALID -> problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid deployment time",
                    "The deployment time cannot be in the future or before the release was frozen.",
                    "PROMOTION_DEPLOYED_AT_INVALID");
            case PROMOTION_OUTCOME_NOT_RECORDABLE -> problem(HttpStatus.UNPROCESSABLE_ENTITY, "Outcome not allowed",
                    "An entry can only be marked VOIDED by correcting it.", "PROMOTION_OUTCOME_NOT_RECORDABLE");
        };
    }

    @ExceptionHandler(LadderEditRejectedException.class)
    public ProblemDetail handleLadderEditRejected(LadderEditRejectedException ex) {
        return switch (ex.reason()) {
            case STAGE_IN_USE -> problem(HttpStatus.CONFLICT, "Stage in use",
                    "Promotions reference this stage, so it can be retired but not deleted.", "STAGE_IN_USE");
            case STAGE_NAME_DUPLICATE -> problem(HttpStatus.CONFLICT, "Duplicate stage name",
                    "A stage with this name already exists, including retired stages.", "STAGE_NAME_DUPLICATE");
            case PRODUCTION_STAGE_PROTECTED -> problem(HttpStatus.CONFLICT, "Production stage protected",
                    "The Production stage cannot be retired or deleted. Move the Production flag to another stage first.",
                    "PRODUCTION_STAGE_PROTECTED");
            case LAST_ACTIVE_STAGE -> problem(HttpStatus.CONFLICT, "Last active stage",
                    "The ladder must keep at least one active stage.", "LAST_ACTIVE_STAGE");
            case STAGE_RETIRED -> problem(HttpStatus.CONFLICT, "Stage retired",
                    "A retired stage cannot carry the Production flag. Restore it first.", "STAGE_RETIRED");
            case STAGE_ORDER_MISMATCH -> problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid stage order",
                    "The new order must list every stage exactly once.", "STAGE_ORDER_MISMATCH");
        };
    }

    @ExceptionHandler(StageNotFoundException.class)
    public ProblemDetail handleStageNotFound(StageNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Stage not found",
                "This environment stage doesn't exist.", "STAGE_NOT_FOUND");
    }

    @ExceptionHandler(PromotionNotFoundException.class)
    public ProblemDetail handlePromotionNotFound(PromotionNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Promotion not found",
                "This promotion entry doesn't exist on this release.", "PROMOTION_NOT_FOUND");
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    public ProblemDetail handleTemplateNotFound(TemplateNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Template not found",
                "This template doesn't exist or has been deleted.", "TEMPLATE_NOT_FOUND");
    }

    @ExceptionHandler(TemplateDuplicateException.class)
    public ProblemDetail handleTemplateDuplicate(TemplateDuplicateException ex) {
        return problem(HttpStatus.CONFLICT, "Duplicate template name",
                "A template with this name already exists for this document type. Choose a different name.",
                "TEMPLATE_DUPLICATE");
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ProblemDetail handleDocumentNotFound(DocumentNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Document not found",
                "This document doesn't exist yet.", "DOCUMENT_NOT_FOUND");
    }

    @ExceptionHandler(NoTemplateException.class)
    public ProblemDetail handleNoTemplate(NoTemplateException ex) {
        return problem(HttpStatus.CONFLICT, "No template",
                "No template is available for this document type. Create one from Templates, or choose "
                        + "one, before generating.", "NO_TEMPLATE");
    }

    @ExceptionHandler(TemplateTypeMismatchException.class)
    public ProblemDetail handleTemplateTypeMismatch(TemplateTypeMismatchException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Template type mismatch",
                "The chosen template is for a different document type. Choose a template of this "
                        + "document type.", "TEMPLATE_TYPE_MISMATCH");
    }

    @ExceptionHandler(ChosenTemplateNotFoundException.class)
    public ProblemDetail handleChosenTemplateNotFound(ChosenTemplateNotFoundException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Chosen template not found",
                "The chosen template no longer exists. Choose another template and try again.",
                "CHOSEN_TEMPLATE_NOT_FOUND");
    }

    @ExceptionHandler(ExportFailedException.class)
    public ProblemDetail handleExportFailed(ExportFailedException ex) {
        log.error("Document export failed", ex);
        return problem(HttpStatus.BAD_GATEWAY, "Export failed",
                "The document could not be exported. Try again, and check the server logs if it "
                        + "persists.", "EXPORT_FAILED");
    }

    // The one handler in this file whose detail is dynamic: the SPA renders it verbatim after
    // "Template not saved — ", so it must name the specific defect and, for a chrome field, the field.
    @ExceptionHandler(TemplateMalformedException.class)
    public ProblemDetail handleTemplateMalformed(TemplateMalformedException ex) {
        String detail = "Body".equals(ex.field())
                ? ex.getMessage()
                : ex.field() + ": " + ex.getMessage();
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Template not saved", detail, "TEMPLATE_MALFORMED");
    }

    @ExceptionHandler(LogoRejectedException.class)
    public ProblemDetail handleLogoRejected(LogoRejectedException ex) {
        return switch (ex.reason()) {
            case INVALID_TYPE -> problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Logo rejected",
                    "This file isn't a supported image. Upload a PNG or JPEG — SVG and other "
                            + "formats aren't accepted.", "LOGO_INVALID_TYPE");
            case TOO_LARGE -> problem(HttpStatus.PAYLOAD_TOO_LARGE, "Logo rejected",
                    "This image is too large for the configured byte limit.", "LOGO_TOO_LARGE");
            case DIMENSIONS_EXCEEDED -> problem(HttpStatus.UNPROCESSABLE_ENTITY, "Logo rejected",
                    "This image exceeds the configured maximum pixel dimensions.",
                    "LOGO_DIMENSIONS_EXCEEDED");
        };
    }

    @ExceptionHandler(RenderBoundExceededException.class)
    public ProblemDetail handleRenderBoundExceeded(RenderBoundExceededException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Template not saved",
                "Validation took too long or produced too much content to render safely. "
                        + "Simplify the template (fewer nested sections or shorter loops) and try again.",
                "RENDER_BOUND_EXCEEDED");
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

    // Spring rejects anything above spring.servlet.multipart.max-file-size before our validator
    // sees a byte. ResponseEntityExceptionHandler already resolves this exception through its own
    // handleException dispatch, so a plain @ExceptionHandler here would be ambiguous with the
    // superclass method - overriding the superclass's own hook is the correct way to customise it,
    // exactly like handleMethodArgumentNotValid below.
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
                                                                           HttpHeaders headers,
                                                                           HttpStatusCode status,
                                                                           WebRequest request) {
        ProblemDetail pd = problem(HttpStatus.PAYLOAD_TOO_LARGE, "Logo rejected",
                "This image is too large for the configured byte limit.", "LOGO_TOO_LARGE");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(pd);
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
