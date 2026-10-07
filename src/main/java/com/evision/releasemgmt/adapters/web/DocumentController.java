package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.ChromeFieldsResponse;
import com.evision.releasemgmt.adapters.web.dto.GenerateDocumentRequest;
import com.evision.releasemgmt.adapters.web.dto.DocumentResponse;
import com.evision.releasemgmt.adapters.web.dto.DocumentRevisionListResponse;
import com.evision.releasemgmt.adapters.web.dto.DocumentRevisionResponse;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.DocumentExportService;
import com.evision.releasemgmt.application.DocumentGenerationService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final DocumentExportService exportService;
    private final ReleaseRepository releaseRepository;
    private final ActorResolutionService actorResolution;

    public DocumentController(DocumentGenerationService service, DocumentExportService exportService,
                              ReleaseRepository releaseRepository, ActorResolutionService actorResolution) {
        this.service = service;
        this.exportService = exportService;
        this.releaseRepository = releaseRepository;
        this.actorResolution = actorResolution;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse generate(@PathVariable UUID releaseId,
                                     @PathVariable TemplateDocumentType documentType,
                                     @RequestBody(required = false) GenerateDocumentRequest request,
                                     Principal principal) {
        // Every /api/** path is authenticated() in SecurityConfig, so Spring Security guarantees a
        // principal here. D-05.4-01 supersedes D-05-01 for the template choice only: the body is
        // optional and, when absent, the server resolves the requester's default then the active
        // template. The actor always comes from the principal, never from the body.
        UUID templateId = request == null ? null : request.templateId();
        return toResponse(service.generate(releaseId, documentType, templateId, principal.getName()));
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
        // D-05.3-05: resolved once for the whole history, not inside the map - a per-row lookup here is
        // the N+1 this phase exists to avoid.
        ResolvedActors actors = actorResolution.resolve(
                all.stream().map(DocumentSnapshot::generatedBy).toArray(String[]::new));
        List<DocumentRevisionResponse> revisions = all.stream()
                .map(s -> new DocumentRevisionResponse(s.revision(), s.revision() == latestRevision,
                        s.generatedAt(), actors.display(s.generatedBy()), s.templateName()))
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

    @GetMapping("/export.pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID releaseId,
                                            @PathVariable TemplateDocumentType documentType,
                                            @RequestParam(required = false) Integer revision) {
        DocumentSnapshot snapshot = resolveSnapshot(releaseId, documentType, revision);
        byte[] bytes = exportService.toPdf(snapshot);
        String filename = exportFilename(releaseId, documentType, snapshot.revision(), "pdf");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ExportFilename.attachment(filename))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }

    @GetMapping("/export.docx")
    public ResponseEntity<byte[]> exportDocx(@PathVariable UUID releaseId,
                                             @PathVariable TemplateDocumentType documentType,
                                             @RequestParam(required = false) Integer revision) {
        DocumentSnapshot snapshot = resolveSnapshot(releaseId, documentType, revision);
        byte[] bytes = exportService.toDocx(snapshot);
        String filename = exportFilename(releaseId, documentType, snapshot.revision(), "docx");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ExportFilename.attachment(filename))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }

    // WR-02: resolved exactly once and reused for both the filename and the exported bytes, so a
    // concurrent generate() cannot make the downloaded filename disagree with its content - a
    // caller that omits ?revision= still gets a filename naming the concrete revision they
    // actually downloaded.
    private DocumentSnapshot resolveSnapshot(UUID releaseId, TemplateDocumentType documentType, Integer revision) {
        return revision == null
                ? service.latest(releaseId, documentType)
                : service.revision(releaseId, documentType, revision);
    }

    private String exportFilename(UUID releaseId, TemplateDocumentType documentType, int revision,
                                  String extension) {
        Release release = releaseRepository.findById(releaseId).orElseThrow(ReleaseNotFoundException::new);
        return ExportFilename.sanitize(release.name(), release.version(), documentType, revision, extension);
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
                chrome.coverSubtitle(), chrome.headerText(), chrome.footerText(), chrome.pageNumbersEnabled(),
                chrome.logoRenderHeightMm());
        int latestRevision = service.nextRevision(snapshot.releaseId(), snapshot.documentType()) - 1;
        boolean templateDeleted = !service.templateStillExists(snapshot.templateId());
        boolean hasLogo = service.hasLogo(snapshot.id());
        ResolvedActors actors = actorResolution.resolve(snapshot.generatedBy());
        return new DocumentResponse(snapshot.id(), snapshot.releaseId(), snapshot.documentType().name(),
                snapshot.revision(), latestRevision, snapshot.markdown(), snapshot.templateName(),
                templateDeleted, hasLogo, chromeResponse, snapshot.generatedAt(), actors.display(snapshot.generatedBy()));
    }
}
