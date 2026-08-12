package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.document.DocumentNotFoundException;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.document.NoActiveTemplateException;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.domain.template.view.ReleaseView;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import com.evision.releasemgmt.ports.TemplateRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// D-05-10: generation is explicit and per-document-type, never automatic - the release manager
// triggers it, never a freeze or refresh side effect.
@Service
public class DocumentGenerationService {

    private final ReleaseRepository releaseRepository;
    private final TemplateRepository templateRepository;
    private final DocumentSnapshotRepository snapshotRepository;
    private final ReleaseViewAssembler assembler;
    private final RenderService renderService;

    public DocumentGenerationService(ReleaseRepository releaseRepository,
                                     TemplateRepository templateRepository,
                                     DocumentSnapshotRepository snapshotRepository,
                                     ReleaseViewAssembler assembler, RenderService renderService) {
        this.releaseRepository = releaseRepository;
        this.templateRepository = templateRepository;
        this.snapshotRepository = snapshotRepository;
        this.assembler = assembler;
        this.renderService = renderService;
    }

    public DocumentSnapshot generate(UUID releaseId, TemplateDocumentType documentType, String actor) {
        // Deliberately no lifecycle guard here: generating final paperwork right after freezing is a
        // normal sequence (05-UI-SPEC screen 1), and D-05-09 makes "frozen + generated" the terminal
        // satisfied state. No Jira call happens here either - D-03-06 already materialised every
        // ticket, so this reads only the locally stored release.
        Release release = releaseRepository.findById(releaseId).orElseThrow(ReleaseNotFoundException::new);
        Template template = templateRepository.findActive(documentType).orElseThrow(NoActiveTemplateException::new);
        // D-05-04/D-05-53: grouped by the dimension of the template that produced this document, so
        // the snapshot's grouping always stays self-consistent with its copied templateBody.
        ReleaseView view = assembler.assemble(release, template.groupingDimension());

        // The single bounded render entry point (D-04-11) - do not inline a call to the low-level
        // render engine port or build a second bounded path.
        //
        // D-05-05: the snapshot body is post-Mustache, PRE-flexmark MARKDOWN. resolveText, not
        // render: render() would additionally run flexmark and hand back HTML, which both export
        // adapters would then re-parse as Markdown - and MustacheFlexmarkRenderAdapter's
        // neutralizeRawHtml (a required control, not an option) would escape every tag into visible
        // text. Both bounds (TMPL-05 timeout, collection cap) are enforced by resolveText exactly as
        // they are by render - this is the same single bounded entry point (D-04-11), minus the
        // step that changes the content type.
        String markdown = renderService.resolveText(template.body(), view, "Body").output();

        ChromeFields templateChrome = template.chrome();
        String coverTitle = renderService.resolveText(templateChrome.coverTitle(), view, "Cover title").output();
        String coverSubtitle = renderService.resolveText(templateChrome.coverSubtitle(), view, "Cover subtitle").output();
        String headerText = renderService.resolveText(templateChrome.headerText(), view, "Header text").output();
        String footerText = renderService.resolveText(templateChrome.footerText(), view, "Footer text").output();
        // D-05.1-03: logoRenderHeightMm is copied through UNRESOLVED, exactly like coverEnabled and
        // pageNumbersEnabled - it carries no {{ }} placeholders, so it never goes near resolveText.
        // Copying it (rather than leaving the exporters to read the template) is what makes a
        // re-export of this revision reproduce the size it was generated with.
        ChromeFields resolvedChrome = new ChromeFields(templateChrome.coverEnabled(), coverTitle, coverSubtitle,
                headerText, footerText, templateChrome.pageNumbersEnabled(),
                templateChrome.logoRenderHeightMm());

        int revision = snapshotRepository.nextRevision(releaseId, documentType);
        LogoImage logo = templateRepository.findLogo(template.id()).orElse(null);

        DocumentSnapshot snapshot = DocumentSnapshot.create(releaseId, documentType, revision, markdown,
                template.id(), template.name(), template.body(), resolvedChrome, actor, Instant.now());
        return snapshotRepository.create(snapshot, logo);
    }

    public DocumentSnapshot latest(UUID releaseId, TemplateDocumentType documentType) {
        return snapshotRepository.findLatest(releaseId, documentType).orElseThrow(DocumentNotFoundException::new);
    }

    public DocumentSnapshot revision(UUID releaseId, TemplateDocumentType documentType, int revision) {
        return snapshotRepository.findByRevision(releaseId, documentType, revision)
                .orElseThrow(DocumentNotFoundException::new);
    }

    public List<DocumentSnapshot> revisions(UUID releaseId, TemplateDocumentType documentType) {
        return snapshotRepository.findAllRevisions(releaseId, documentType);
    }

    public Optional<LogoImage> logo(UUID releaseId, TemplateDocumentType documentType, Integer revisionOrNull) {
        DocumentSnapshot snapshot = revisionOrNull == null
                ? latest(releaseId, documentType)
                : revision(releaseId, documentType, revisionOrNull);
        return snapshotRepository.findLogo(snapshot.id());
    }

    // 05-UI-SPEC Planning Input #2's templateDeleted question, resolved as yes, expose it: it is one
    // indexed primary-key lookup on a screen that already loads a whole document.
    public boolean templateStillExists(UUID templateIdOrNull) {
        return templateIdOrNull != null && templateRepository.findById(templateIdOrNull).isPresent();
    }

    /** 1 when this (release, document type) has never been generated, else max(revision) + 1. */
    public int nextRevision(UUID releaseId, TemplateDocumentType documentType) {
        return snapshotRepository.nextRevision(releaseId, documentType);
    }

    public boolean hasLogo(UUID snapshotId) {
        return snapshotRepository.findLogo(snapshotId).isPresent();
    }
}
