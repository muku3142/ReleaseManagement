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
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.application.ReleaseViewAssembler;
import com.evision.releasemgmt.application.TemplateService;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.PlaceholderCatalog;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.view.ReleaseView;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService templateService;
    private final ReleaseService releaseService;
    private final ReleaseViewAssembler assembler;
    private final long logoMaxBytes;
    private final int logoMaxWidth;
    private final int logoMaxHeight;

    public TemplateController(TemplateService templateService, ReleaseService releaseService,
                              ReleaseViewAssembler assembler,
                              @Value("${app.template.logo.max-bytes}") long logoMaxBytes,
                              @Value("${app.template.logo.max-width}") int logoMaxWidth,
                              @Value("${app.template.logo.max-height}") int logoMaxHeight) {
        this.templateService = templateService;
        this.releaseService = releaseService;
        this.assembler = assembler;
        this.logoMaxBytes = logoMaxBytes;
        this.logoMaxWidth = logoMaxWidth;
        this.logoMaxHeight = logoMaxHeight;
    }

    @GetMapping
    public TemplateListResponse list() {
        return new TemplateListResponse(templateService.list().stream()
                .map(TemplateController::toSummaryResponse).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse create(@Valid @RequestBody CreateTemplateRequest request, Principal principal) {
        return toResponse(templateService.create(request.name(), request.documentType(),
                principal.getName()));
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
    @GetMapping("/render-context/{releaseId}")
    public ReleaseView renderContext(@PathVariable UUID releaseId) {
        return assembler.assemble(releaseService.get(releaseId));
    }

    @GetMapping("/{templateId}")
    public TemplateResponse get(@PathVariable UUID templateId) {
        return toResponse(templateService.get(templateId));
    }

    @PutMapping("/{templateId}/name")
    public TemplateResponse rename(@PathVariable UUID templateId,
                                   @Valid @RequestBody RenameTemplateRequest request, Principal principal) {
        return toResponse(templateService.rename(templateId, request.name(), principal.getName()));
    }

    @PutMapping("/{templateId}/content")
    public TemplateResponse saveContent(@PathVariable UUID templateId,
                                        @Valid @RequestBody SaveTemplateContentRequest request,
                                        Principal principal) {
        ChromeFields chrome = new ChromeFields(request.chrome().coverEnabled(), request.chrome().coverTitle(),
                request.chrome().coverSubtitle(), request.chrome().headerText(),
                request.chrome().footerText(), request.chrome().pageNumbersEnabled());
        return toResponse(templateService.saveContent(templateId, request.body(), chrome,
                principal.getName()));
    }

    @PostMapping("/{templateId}/activate")
    public TemplateResponse activate(@PathVariable UUID templateId, Principal principal) {
        return toResponse(templateService.activate(templateId, principal.getName()));
    }

    @PostMapping("/{templateId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse duplicate(@PathVariable UUID templateId, Principal principal) {
        return toResponse(templateService.duplicate(templateId, principal.getName()));
    }

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID templateId) {
        templateService.delete(templateId);
    }

    private static TemplateResponse toResponse(Template template) {
        ChromeFields chrome = template.chrome();
        ChromeFieldsResponse chromeResponse = new ChromeFieldsResponse(chrome.coverEnabled(),
                chrome.coverTitle(), chrome.coverSubtitle(), chrome.headerText(), chrome.footerText(),
                chrome.pageNumbersEnabled());
        LogoMetadataResponse logoResponse = template.logo() == null ? null
                : new LogoMetadataResponse(template.logo().contentType(), template.logo().filename(),
                        template.logo().byteSize(), template.logo().width(), template.logo().height(),
                        template.logo().uploadedAt());
        return new TemplateResponse(template.id(), template.name(), template.documentType().name(),
                template.body(), template.active(), chromeResponse, logoResponse, template.warnings(),
                template.createdAt(), template.createdBy(), template.updatedAt(), template.updatedBy());
    }

    private static TemplateSummaryResponse toSummaryResponse(Template template) {
        return new TemplateSummaryResponse(template.id(), template.name(), template.documentType().name(),
                template.active(), template.logo() != null, template.warnings().size(),
                template.updatedAt());
    }
}
