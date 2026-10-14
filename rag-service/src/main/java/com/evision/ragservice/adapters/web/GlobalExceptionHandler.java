package com.evision.ragservice.adapters.web;

import com.evision.ragservice.config.ChatProperties;
import com.evision.ragservice.domain.ClientNameTakenException;
import com.evision.ragservice.domain.MixedEmbeddingModelsException;
import com.evision.ragservice.domain.ModelNotAvailableException;
import com.evision.ragservice.domain.OllamaUnavailableException;
import com.evision.ragservice.domain.TextTooLargeException;
import com.evision.ragservice.domain.TooManyRequestsException;
import com.evision.ragservice.domain.ClientNotFoundException;
import com.evision.ragservice.domain.CollectionModelConflictException;
import com.evision.ragservice.domain.CollectionNotFoundException;
import com.evision.ragservice.domain.DocumentNotFoundException;
import com.evision.ragservice.domain.EmbeddingModelNotAllowedException;
import com.evision.ragservice.domain.EmbeddingRejectedException;
import com.evision.ragservice.domain.EmbeddingUnavailableException;
import com.evision.ragservice.domain.InvalidContentException;
import com.evision.ragservice.domain.PayloadTooLargeException;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ChatProperties chatProperties;

    public GlobalExceptionHandler(ChatProperties chatProperties) {
        this.chatProperties = chatProperties;
    }

    @ExceptionHandler(CollectionNotFoundException.class)
    public ProblemDetail handleCollectionNotFound(CollectionNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Collection not found", ex.getMessage(), "COLLECTION_NOT_FOUND");
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ProblemDetail handleDocumentNotFound(DocumentNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Document not found", ex.getMessage(), "DOCUMENT_NOT_FOUND");
    }

    @ExceptionHandler(ClientNotFoundException.class)
    public ProblemDetail handleClientNotFound(ClientNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Client not found", ex.getMessage(), "CLIENT_NOT_FOUND");
    }

    @ExceptionHandler(ClientNameTakenException.class)
    public ProblemDetail handleClientNameTaken(ClientNameTakenException ex) {
        return problem(HttpStatus.CONFLICT, "Client name taken", ex.getMessage(), "CLIENT_NAME_TAKEN");
    }

    @ExceptionHandler(EmbeddingModelNotAllowedException.class)
    public ProblemDetail handleModelNotAllowed(EmbeddingModelNotAllowedException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Embedding model not allowed", ex.getMessage(), "MODEL_NOT_ALLOWED");
    }

    @ExceptionHandler(CollectionModelConflictException.class)
    public ProblemDetail handleCollectionModelConflict(CollectionModelConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Collection model conflict", ex.getMessage(), "COLLECTION_MODEL_CONFLICT");
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ProblemDetail handlePayloadTooLarge(PayloadTooLargeException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large", ex.getMessage(), "PAYLOAD_TOO_LARGE");
    }

    @ExceptionHandler(InvalidContentException.class)
    public ProblemDetail handleInvalidContent(InvalidContentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid content", ex.getMessage(), "INVALID_CONTENT");
    }

    @ExceptionHandler(EmbeddingUnavailableException.class)
    public ProblemDetail handleEmbeddingUnavailable(EmbeddingUnavailableException ex) {
        log.warn("Embedding backend unavailable: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Embedding unavailable", ex.getMessage(), "EMBEDDING_UNAVAILABLE");
    }

    @ExceptionHandler(EmbeddingRejectedException.class)
    public ProblemDetail handleEmbeddingRejected(EmbeddingRejectedException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Query rejected", ex.getMessage(), "QUERY_REJECTED");
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyRequestsException ex) {
        ProblemDetail pd = problem(HttpStatus.TOO_MANY_REQUESTS, "Too many requests", ex.getMessage(),
                "TOO_MANY_REQUESTS");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(chatProperties.retryAfterSeconds()))
                .body(pd);
    }

    @ExceptionHandler(OllamaUnavailableException.class)
    public ProblemDetail handleOllamaUnavailable(OllamaUnavailableException ex) {
        log.warn("Generation backend unavailable: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Generation unavailable", ex.getMessage(),
                "OLLAMA_UNAVAILABLE");
    }

    @ExceptionHandler(ModelNotAvailableException.class)
    public ProblemDetail handleModelNotAvailable(ModelNotAvailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Chat model not available", ex.getMessage(),
                "MODEL_NOT_AVAILABLE");
    }

    @ExceptionHandler(TextTooLargeException.class)
    public ProblemDetail handleTextTooLarge(TextTooLargeException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Text too large", ex.getMessage(), "TEXT_TOO_LARGE");
    }

    @ExceptionHandler(MixedEmbeddingModelsException.class)
    public ProblemDetail handleMixedEmbeddingModels(MixedEmbeddingModelsException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Mixed embedding models", ex.getMessage(),
                "MIXED_EMBEDDING_MODELS");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                   HttpHeaders headers,
                                                                   HttpStatusCode status,
                                                                   WebRequest request) {
        String fields = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldError)
                .collect(Collectors.joining(", "));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Request validation failed", fields, "VALIDATION_FAILED");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                             HttpHeaders headers,
                                                                             HttpStatusCode status,
                                                                             WebRequest request) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Request validation failed",
                "One or more request parameters failed validation.", "VALIDATION_FAILED");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                   HttpHeaders headers,
                                                                   HttpStatusCode status,
                                                                   WebRequest request) {
        // RequestSizeLimitFilter's bounded stream fails mid-parse, so Jackson wraps it
        if (ex.getMostSpecificCause() instanceof PayloadTooLargeException tooLarge) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(handlePayloadTooLarge(tooLarge));
        }
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "Request body is not valid JSON for this endpoint", "MALFORMED_REQUEST");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    // Field names only: rejected values can be document text or keys.
    private static String fieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String reason) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle(title);
        pd.setDetail(detail);
        pd.setProperty("reason", reason);
        return pd;
    }
}
