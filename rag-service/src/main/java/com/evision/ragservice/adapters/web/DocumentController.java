package com.evision.ragservice.adapters.web;

import com.evision.ragservice.adapters.web.dto.DocumentResponse;
import com.evision.ragservice.adapters.web.dto.PutDocumentRequest;
import com.evision.ragservice.application.DocumentService;
import com.evision.ragservice.domain.DocumentContent;
import com.evision.ragservice.domain.DocumentStatus;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/collections/{collection}/documents")
public class DocumentController {

    public record PushResponse(String id, DocumentStatus status) {
    }

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PutMapping("/{documentId}")
    public ResponseEntity<PushResponse> put(
            @Parameter(hidden = true) @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) UUID clientId,
            @PathVariable @Pattern(regexp = CollectionController.NAME_PATTERN) String collection,
            @PathVariable @Pattern(regexp = CollectionController.NAME_PATTERN) String documentId,
            @Valid @RequestBody PutDocumentRequest request) {
        DocumentStatus status = documents.push(clientId, collection,
                new DocumentContent(documentId, request.title(), request.text(), request.url(), request.metadata()));
        return ResponseEntity.accepted().body(new PushResponse(documentId, status));
    }

    @GetMapping("/{documentId}")
    public DocumentResponse get(
            @Parameter(hidden = true) @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) UUID clientId,
            @PathVariable @Pattern(regexp = CollectionController.NAME_PATTERN) String collection,
            @PathVariable @Pattern(regexp = CollectionController.NAME_PATTERN) String documentId) {
        return DocumentResponse.from(documents.get(clientId, collection, documentId));
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(hidden = true) @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) UUID clientId,
            @PathVariable @Pattern(regexp = CollectionController.NAME_PATTERN) String collection,
            @PathVariable @Pattern(regexp = CollectionController.NAME_PATTERN) String documentId) {
        documents.delete(clientId, collection, documentId);
    }
}
