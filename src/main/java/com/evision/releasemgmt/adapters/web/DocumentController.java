package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.ChromeFieldsResponse;
import com.evision.releasemgmt.adapters.web.dto.DocumentResponse;
import com.evision.releasemgmt.adapters.web.dto.DocumentRevisionListResponse;
import com.evision.releasemgmt.adapters.web.dto.DocumentRevisionResponse;
import com.evision.releasemgmt.application.DocumentGenerationService;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

// revision is a query parameter, never a path segment - a {revision} path variable would be
// ambiguous with the literal /revisions segment and bind-fail with a 400.
@RestController
@RequestMapping("/api/releases/{releaseId}/documents/{documentType}")
public class DocumentController {

    private final DocumentGenerationService service;

    public DocumentController(DocumentGenerationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse generate(@PathVariable UUID releaseId,
                                     @PathVariable TemplateDocumentType documentType,
                                     Principal principal) {
        // Every /api/** path is authenticated() in SecurityConfig, so Spring Security guarantees a
        // principal here. Generation takes no request body (D-05-01: nothing is prompted for at
        // generation time).
        return toResponse(service.generate(releaseId, documentType, principal.getName()));
    }

    @GetMapping
    public DocumentResponse get(@PathVariable UUID releaseId, @PathVariable TemplateDocumentType documentType,
                                @RequestParam(required = false) Integer revision) {
        DocumentSnapshot snapshot = revision == null
                ? service.latest(releaseId, documentType)
                : service.revision(releaseId, documentType, revision);
        return toResponse(snapshot);
    }

    @GetMapping("/revisions")
    public DocumentRevisionListResponse revisions(@PathVariable UUID releaseId,
                                                   @PathVariable TemplateDocumentType documentType) {
        List<DocumentSnapshot> all = service.revisions(releaseId, documentType);
        int latestRevision = all.isEmpty() ? 0 : all.get(0).revision(); // newest-first, per the port's contract
        List<DocumentRevisionResponse> revisions = all.stream()
                .map(s -> new DocumentRevisionResponse(s.revision(), s.revision() == latestRevision,
                        s.generatedAt(), s.generatedBy()))
                .toList();
        return new DocumentRevisionListResponse(revisions);
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo(@PathVariable UUID releaseId, @PathVariable TemplateDocumentType documentType,
                                       @RequestParam(required = false) Integer revision) {
        return service.logo(releaseId, documentType, revision)
                .map(DocumentController::toImageResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // The filename here comes from the copied upload metadata, not from free text, so it needs no
    // sanitisation - contrast plan 05-08's export endpoints, which sit next to this one and build
    // their filename from the release name/version and must NOT copy this line verbatim.
    private static ResponseEntity<byte[]> toImageResponse(LogoImage image) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, image.metadata().contentType())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + image.metadata().filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(image.bytes());
    }

    private DocumentResponse toResponse(DocumentSnapshot snapshot) {
        ChromeFields chrome = snapshot.chrome();
        ChromeFieldsResponse chromeResponse = new ChromeFieldsResponse(chrome.coverEnabled(), chrome.coverTitle(),
                chrome.coverSubtitle(), chrome.headerText(), chrome.footerText(), chrome.pageNumbersEnabled());
        int latestRevision = service.nextRevision(snapshot.releaseId(), snapshot.documentType()) - 1;
        boolean templateDeleted = !service.templateStillExists(snapshot.templateId());
        boolean hasLogo = service.hasLogo(snapshot.id());
        return new DocumentResponse(snapshot.id(), snapshot.releaseId(), snapshot.documentType().name(),
                snapshot.revision(), latestRevision, snapshot.markdown(), snapshot.templateName(),
                templateDeleted, hasLogo, chromeResponse, snapshot.generatedAt(), snapshot.generatedBy());
    }
}
