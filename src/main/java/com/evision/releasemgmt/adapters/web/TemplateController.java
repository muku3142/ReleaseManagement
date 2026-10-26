package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.ChromeFieldsResponse;
import com.evision.releasemgmt.adapters.web.dto.CreateTemplateRequest;
import com.evision.releasemgmt.adapters.web.dto.LogoLimitsResponse;
import com.evision.releasemgmt.adapters.web.dto.LogoMetadataResponse;
import com.evision.releasemgmt.adapters.web.dto.PlaceholderCatalogResponse;
import com.evision.releasemgmt.adapters.web.dto.PlaceholderEntryResponse;
import com.evision.releasemgmt.adapters.web.dto.RenameTemplateRequest;
import com.evision.releasemgmt.adapters.web.dto.SaveTemplateContentRequest;
import com.evision.releasemgmt.adapters.web.dto.TemplateListResponse;
import com.evision.releasemgmt.adapters.web.dto.TemplateResponse;
import com.evision.releasemgmt.adapters.web.dto.TemplateSummaryResponse;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.application.ReleaseViewAssembler;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.application.TemplateService;
import com.evision.releasemgmt.domain.portfolio.ReleaseProduct;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.GroupingDimension;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.PlaceholderCatalog;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.view.ReleaseView;
import com.evision.releasemgmt.ports.ReleaseProductRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService templateService;
    private final ReleaseService releaseService;
    private final ReleaseViewAssembler assembler;
    private final ActorResolutionService actorResolution;
    private final ReleaseProductRepository releaseProducts;
    private final long logoMaxBytes;
    private final int logoMaxWidth;
    private final int logoMaxHeight;

    public TemplateController(TemplateService templateService, ReleaseService releaseService,
                              ReleaseViewAssembler assembler, ActorResolutionService actorResolution,
                              ReleaseProductRepository releaseProducts,
                              @Value("${app.template.logo.max-bytes}") long logoMaxBytes,
                              @Value("${app.template.logo.max-width}") int logoMaxWidth,
                              @Value("${app.template.logo.max-height}") int logoMaxHeight) {
        this.templateService = templateService;
        this.releaseService = releaseService;
        this.assembler = assembler;
        this.actorResolution = actorResolution;
        this.releaseProducts = releaseProducts;
        this.logoMaxBytes = logoMaxBytes;
        this.logoMaxWidth = logoMaxWidth;
        this.logoMaxHeight = logoMaxHeight;
    }

    @GetMapping
    public TemplateListResponse list(Principal principal) {
        // D-05.4-05: one lookup for the whole list, never one per row.
        Set<UUID> myDefaults = templateService.myDefaultIds(principal.getName());
        return new TemplateListResponse(templateService.list().stream()
                .map(t -> toSummaryResponse(t, myDefaults.contains(t.id()))).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse create(@Valid @RequestBody CreateTemplateRequest request, Principal principal) {
        return toResponse(templateService.create(request.name(), request.documentType(),
                principal.getName()), principal);
    }

    @GetMapping("/catalog")
    public PlaceholderCatalogResponse catalog() {
        List<PlaceholderEntryResponse> entries = PlaceholderCatalog.ENTRIES.stream()
                .map(entry -> new PlaceholderEntryResponse(entry.scope().name(), entry.token(),
                        entry.kind().name(), entry.description(), entry.tags()))
                .toList();
        LogoLimitsResponse logoLimits = new LogoLimitsResponse(logoMaxBytes, logoMaxWidth, logoMaxHeight,
                List.of("image/png", "image/jpeg"));
        return new PlaceholderCatalogResponse(entries, logoLimits);
    }

    // TMPL-06 previews against a real release, and D-04-09 renders that preview in the browser. This
    // serves the *data* the browser renders - the same view-model the server render uses - so the two
    // renderers cannot disagree about the data even though D-04-10 accepts that they may disagree about
    // rendering. It is deliberately not a server-side render endpoint; that was deferred.
    //
    // D-05-04: this preview is not tied to any one template, so it always previews against the
    // ISSUE_TYPE default rather than reading a groupingDimension the caller hasn't chosen yet.
    @GetMapping("/render-context/{releaseId}")
    public ReleaseView renderContext(@PathVariable UUID releaseId) {
        Release release = releaseService.get(releaseId);
        List<String> productNames = releaseProducts.productsOf(releaseId).stream().map(ReleaseProduct::name).toList();
        return assembler.assemble(release, productNames, GroupingDimension.ISSUE_TYPE);
    }

    @GetMapping("/{templateId}")
    public TemplateResponse get(@PathVariable UUID templateId, Principal principal) {
        return toResponse(templateService.get(templateId), principal);
    }

    @PutMapping("/{templateId}/name")
    public TemplateResponse rename(@PathVariable UUID templateId,
                                   @Valid @RequestBody RenameTemplateRequest request, Principal principal) {
        return toResponse(templateService.rename(templateId, request.name(), principal.getName()), principal);
    }

    @PutMapping("/{templateId}/content")
    public TemplateResponse saveContent(@PathVariable UUID templateId,
                                        @Valid @RequestBody SaveTemplateContentRequest request,
                                        Principal principal) {
        // D-05.1-01: an absent logoRenderHeightMm is an older client's payload - default it rather
        // than reject, the same disposition groupingDimension takes just below. @Min/@Max already
        // rejected any value that was present but out of range, so there is nothing to clamp here.
        int logoRenderHeightMm = request.chrome().logoRenderHeightMm() == null
                ? ChromeFields.DEFAULT_LOGO_RENDER_HEIGHT_MM
                : request.chrome().logoRenderHeightMm();
        ChromeFields chrome = new ChromeFields(request.chrome().coverEnabled(), request.chrome().coverTitle(),
                request.chrome().coverSubtitle(), request.chrome().headerText(),
                request.chrome().footerText(), request.chrome().pageNumbersEnabled(), logoRenderHeightMm);
        // D-05-04: null/blank is an older client's payload - default to ISSUE_TYPE rather than
        // rejecting it. The @Pattern on the request already guarantees this valueOf cannot throw.
        GroupingDimension groupingDimension = request.groupingDimension() == null
                || request.groupingDimension().isBlank()
                ? GroupingDimension.ISSUE_TYPE
                : GroupingDimension.valueOf(request.groupingDimension());
        return toResponse(templateService.saveContent(templateId, request.body(), chrome,
                groupingDimension, principal.getName()), principal);
    }

    @PostMapping("/{templateId}/activate")
    public TemplateResponse activate(@PathVariable UUID templateId, Principal principal) {
        return toResponse(templateService.activate(templateId, principal.getName()), principal);
    }

    // D-05.4-05: the requester's own default for this template's document type. The account is always the
    // authenticated principal and the type always comes from the template - neither is read from the request.
    @PutMapping("/{templateId}/my-default")
    public TemplateResponse setMyDefault(@PathVariable UUID templateId, Principal principal) {
        return toResponse(templateService.setMyDefault(templateId, principal.getName()), principal);
    }

    @DeleteMapping("/{templateId}/my-default")
    public TemplateResponse clearMyDefault(@PathVariable UUID templateId, Principal principal) {
        return toResponse(templateService.clearMyDefault(templateId, principal.getName()), principal);
    }

    @PostMapping("/{templateId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse duplicate(@PathVariable UUID templateId, Principal principal) {
        return toResponse(templateService.duplicate(templateId, principal.getName()), principal);
    }

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID templateId) {
        templateService.delete(templateId);
    }

    // Also serves as "replace" - saveLogo overwrites whatever logo the template already has, so
    // there is no separate replace endpoint (TMPL-09).
    @PostMapping(path = "/{templateId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateResponse uploadLogo(@PathVariable UUID templateId,
                                       @RequestParam("file") MultipartFile file,
                                       Principal principal) throws IOException {
        return toResponse(templateService.uploadLogo(templateId, file.getBytes(),
                file.getOriginalFilename()), principal);
    }

    @DeleteMapping("/{templateId}/logo")
    public TemplateResponse removeLogo(@PathVariable UUID templateId, Principal principal) {
        return toResponse(templateService.removeLogo(templateId), principal);
    }

    // WR-04: templateService.getLogo() 404s TEMPLATE_NOT_FOUND when the template itself doesn't
    // exist; an empty Optional here means the template is real but simply has no logo, which gets
    // a plain 404 rather than the misleading "this template doesn't exist" reason.
    @GetMapping("/{templateId}/logo")
    public ResponseEntity<byte[]> downloadLogo(@PathVariable UUID templateId) {
        return templateService.getLogo(templateId)
                .map(TemplateController::toLogoResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<byte[]> toLogoResponse(LogoImage image) {
        return ResponseEntity.ok()
                // The stored type is one of two values enforced by both the sniffing validator and
                // a CHECK constraint, and the header below stops a browser from re-interpreting the
                // bytes as anything else. User-uploaded content served from our own origin gets both belts.
                .header(HttpHeaders.CONTENT_TYPE, image.metadata().contentType())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + image.metadata().filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(image.bytes());
    }

    private TemplateResponse toResponse(Template template, Principal principal) {
        boolean myDefault = templateService.myDefaultIds(principal.getName()).contains(template.id());
        ChromeFields chrome = template.chrome();
        ChromeFieldsResponse chromeResponse = new ChromeFieldsResponse(chrome.coverEnabled(),
                chrome.coverTitle(), chrome.coverSubtitle(), chrome.headerText(), chrome.footerText(),
                chrome.pageNumbersEnabled(), chrome.logoRenderHeightMm());
        LogoMetadataResponse logoResponse = template.logo() == null ? null
                : new LogoMetadataResponse(template.logo().contentType(), template.logo().filename(),
                        template.logo().byteSize(), template.logo().width(), template.logo().height(),
                        template.logo().uploadedAt());
        ResolvedActors actors = actorResolution.resolve(template.createdBy(), template.updatedBy());
        return new TemplateResponse(template.id(), template.name(), template.documentType().name(),
                template.body(), template.active(), myDefault, chromeResponse, template.groupingDimension().name(),
                logoResponse, template.warnings(), template.createdAt(), actors.display(template.createdBy()),
                template.updatedAt(), actors.display(template.updatedBy()));
    }

    private static TemplateSummaryResponse toSummaryResponse(Template template, boolean myDefault) {
        return new TemplateSummaryResponse(template.id(), template.name(), template.documentType().name(),
                template.active(), myDefault, template.logo() != null, template.warnings().size(),
                template.updatedAt());
    }
}
