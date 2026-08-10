package com.evision.releasemgmt.application;

import com.evision.releasemgmt.adapters.render.MustacheFlexmarkRenderAdapter;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.document.NoActiveTemplateException;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.GroupingDimension;
import com.evision.releasemgmt.domain.template.RenderBoundExceededException;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.domain.template.view.SampleReleaseView;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import com.evision.releasemgmt.ports.TemplateRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Uses a real RenderService wired to a real MustacheFlexmarkRenderAdapter and a real
// ReleaseViewAssembler, mirroring TemplateServiceTest's convention - mocking the render pipeline
// would test nothing about the resolution behaviour these tests pin down.
class DocumentGenerationServiceTest {

    private final ReleaseRepository releaseRepository = mock(ReleaseRepository.class);
    private final TemplateRepository templateRepository = mock(TemplateRepository.class);
    private final DocumentSnapshotRepository snapshotRepository = mock(DocumentSnapshotRepository.class);
    private final ReleaseViewAssembler assembler = mock(ReleaseViewAssembler.class);
    private final RenderService renderService =
            new RenderService(new MustacheFlexmarkRenderAdapter(), Duration.ofSeconds(2), 500, 64);
    private final DocumentGenerationService service = new DocumentGenerationService(releaseRepository,
            templateRepository, snapshotRepository, assembler, renderService);

    @Test
    void generatingStoresRevisionOneWithBodyRenderedThroughRenderService() {
        Release release = openRelease();
        Template template = activeTemplate(TemplateDocumentType.RELEASE_NOTES, "Release {{release.name}}",
                ChromeFields.empty());
        stubHappyPath(release, template, 1);

        service.generate(release.id(), TemplateDocumentType.RELEASE_NOTES, "actor-1");

        String expectedMarkdown = renderService.render(template.body(), SampleReleaseView.instance(), "Body").html();
        assertThat(snapshotCaptor().markdown()).isEqualTo(expectedMarkdown);
        assertThat(snapshotCaptor().revision()).isEqualTo(1);
    }

    @Test
    void regeneratingStoresRevisionTwoWhileRevisionOneStaysReadable() {
        Release release = openRelease();
        Template template = activeTemplate(TemplateDocumentType.RELEASE_NOTES, "Release notes body",
                ChromeFields.empty());
        stubHappyPath(release, template, 2);

        DocumentSnapshot revisionOne = DocumentSnapshot.create(release.id(), TemplateDocumentType.RELEASE_NOTES,
                1, "Release notes body", template.id(), template.name(), template.body(), ChromeFields.empty(),
                "actor-1", Instant.now());
        when(snapshotRepository.findByRevision(release.id(), TemplateDocumentType.RELEASE_NOTES, 1))
                .thenReturn(Optional.of(revisionOne));

        service.generate(release.id(), TemplateDocumentType.RELEASE_NOTES, "actor-2");

        assertThat(snapshotCaptor().revision()).isEqualTo(2);
        assertThat(service.revision(release.id(), TemplateDocumentType.RELEASE_NOTES, 1).markdown())
                .isEqualTo("Release notes body");
    }

    @Test
    void generatingForATypeWithNoActiveTemplateThrows() {
        Release release = openRelease();
        when(releaseRepository.findById(release.id())).thenReturn(Optional.of(release));
        when(templateRepository.findActive(TemplateDocumentType.RUNBOOK)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(release.id(), TemplateDocumentType.RUNBOOK, "actor-1"))
                .isInstanceOf(NoActiveTemplateException.class);
    }

    @Test
    void generatingForAnUnknownReleaseThrows() {
        UUID unknownReleaseId = UUID.randomUUID();
        when(releaseRepository.findById(unknownReleaseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(unknownReleaseId, TemplateDocumentType.RELEASE_NOTES, "actor-1"))
                .isInstanceOf(ReleaseNotFoundException.class);
    }

    @Test
    void storedChromeIsResolvedWhileBothBooleansAreCopiedUnchanged() {
        Release release = openRelease();
        ChromeFields templateChrome = new ChromeFields(true, "Version {{release.version}}",
                "For {{release.name}}", "Header {{release.version}}", "Footer {{release.version}}", true);
        Template template = activeTemplate(TemplateDocumentType.RELEASE_NOTES, "Body", templateChrome);
        stubHappyPath(release, template, 1);

        service.generate(release.id(), TemplateDocumentType.RELEASE_NOTES, "actor-1");

        ChromeFields storedChrome = snapshotCaptor().chrome();
        assertThat(storedChrome.coverTitle()).isEqualTo("Version 2026.08");
        assertThat(storedChrome.coverSubtitle()).isEqualTo("For Sample Release");
        assertThat(storedChrome.headerText()).isEqualTo("Header 2026.08");
        assertThat(storedChrome.footerText()).isEqualTo("Footer 2026.08");
        assertThat(storedChrome.coverEnabled()).isTrue();
        assertThat(storedChrome.pageNumbersEnabled()).isTrue();
    }

    @Test
    void generationWorksIdenticallyOnAFrozenRelease() {
        Instant now = Instant.now();
        Release frozenRelease = new Release(UUID.randomUUID(), "Sample Release", "2026.08",
                ReleaseStatus.FROZEN, "CNV", now, "actor-0", now, "actor-1", now, null, List.of(), List.of());
        Template template = activeTemplate(TemplateDocumentType.RELEASE_NOTES, "Body", ChromeFields.empty());
        stubHappyPath(frozenRelease, template, 1);

        service.generate(frozenRelease.id(), TemplateDocumentType.RELEASE_NOTES, "actor-1");

        assertThat(snapshotCaptor().revision()).isEqualTo(1);
    }

    @Test
    void generationDoesNotCallJira() {
        // Compile-time documentation: DocumentGenerationService's only constructor takes no Jira
        // port at all, so no code path here can reach Jira even by accident - D-03-06 already
        // materialised every ticket locally, which is what makes this possible.
        Class<?>[] paramTypes = DocumentGenerationService.class.getConstructors()[0].getParameterTypes();
        assertThat(paramTypes).noneMatch(type -> type.getSimpleName().contains("Jira"));
    }

    @Test
    void chromeIsStoredResolvedNotRaw() {
        Release release = openRelease();
        ChromeFields templateChrome = new ChromeFields(false, "Version {{release.version}}", "", "", "", false);
        Template template = activeTemplate(TemplateDocumentType.RELEASE_NOTES, "Body", templateChrome);
        stubHappyPath(release, template, 1);

        service.generate(release.id(), TemplateDocumentType.RELEASE_NOTES, "actor-1");

        assertThat(snapshotCaptor().chrome().coverTitle()).isEqualTo("Version 2026.08");
    }

    // D-05-04/D-05-53: generation must read the dimension off the same Template instance whose name
    // and body it copies onto the snapshot - never a hardcoded default.
    @Test
    void generationGroupsByTheActiveTemplatesDimension() {
        Release release = openRelease();
        Template componentGroupedTemplate = activeTemplate(TemplateDocumentType.RELEASE_NOTES, "Body",
                ChromeFields.empty(), GroupingDimension.COMPONENT);
        stubHappyPath(release, componentGroupedTemplate, 1);

        service.generate(release.id(), TemplateDocumentType.RELEASE_NOTES, "actor-1");

        verify(assembler).assemble(release, GroupingDimension.COMPONENT);
    }

    @Test
    void boundBreachPropagates() {
        Release release = openRelease();
        Template template = activeTemplate(TemplateDocumentType.RELEASE_NOTES, "Body", ChromeFields.empty());
        when(releaseRepository.findById(release.id())).thenReturn(Optional.of(release));
        when(templateRepository.findActive(template.documentType())).thenReturn(Optional.of(template));
        when(assembler.assemble(release, GroupingDimension.ISSUE_TYPE)).thenReturn(SampleReleaseView.instance());
        RenderService boundBreachingRenderService = mock(RenderService.class);
        when(boundBreachingRenderService.render(any(), any(), any())).thenThrow(new RenderBoundExceededException());
        DocumentGenerationService serviceWithBoundBreach = new DocumentGenerationService(releaseRepository,
                templateRepository, snapshotRepository, assembler, boundBreachingRenderService);

        assertThatThrownBy(() -> serviceWithBoundBreach.generate(release.id(), TemplateDocumentType.RELEASE_NOTES,
                "actor-1")).isInstanceOf(RenderBoundExceededException.class);

        verify(snapshotRepository, never()).create(any(), any());
    }

    private DocumentSnapshot snapshotCaptor() {
        var captor = org.mockito.ArgumentCaptor.forClass(DocumentSnapshot.class);
        org.mockito.Mockito.verify(snapshotRepository).create(captor.capture(), any());
        return captor.getValue();
    }

    private void stubHappyPath(Release release, Template template, int nextRevision) {
        when(releaseRepository.findById(release.id())).thenReturn(Optional.of(release));
        when(templateRepository.findActive(template.documentType())).thenReturn(Optional.of(template));
        when(assembler.assemble(release, template.groupingDimension())).thenReturn(SampleReleaseView.instance());
        when(templateRepository.findLogo(template.id())).thenReturn(Optional.empty());
        when(snapshotRepository.nextRevision(release.id(), template.documentType())).thenReturn(nextRevision);
        when(snapshotRepository.create(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Release openRelease() {
        Instant now = Instant.now();
        return new Release(UUID.randomUUID(), "Sample Release", "2026.08", ReleaseStatus.OPEN, "CNV",
                now, "actor-0", null, null, now, null, List.of(), List.of());
    }

    private static Template activeTemplate(TemplateDocumentType documentType, String body, ChromeFields chrome) {
        return activeTemplate(documentType, body, chrome, GroupingDimension.ISSUE_TYPE);
    }

    private static Template activeTemplate(TemplateDocumentType documentType, String body, ChromeFields chrome,
                                            GroupingDimension groupingDimension) {
        Instant now = Instant.now();
        return new Template(UUID.randomUUID(), "Standard notes", documentType, body, true, chrome,
                groupingDimension, null, List.of(), now, "actor-0", now, "actor-0");
    }
}
