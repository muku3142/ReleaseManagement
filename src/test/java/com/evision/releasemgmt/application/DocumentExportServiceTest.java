package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.document.DocumentNotFoundException;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.document.ExportFailedException;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.LogoMetadata;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.DocxExportPort;
import com.evision.releasemgmt.ports.PdfExportPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentExportServiceTest {

    private final DocumentSnapshotRepository snapshotRepository = mock(DocumentSnapshotRepository.class);
    private final PdfExportPort pdfExportPort = mock(PdfExportPort.class);
    private final DocxExportPort docxExportPort = mock(DocxExportPort.class);
    private final DocumentExportService service =
            new DocumentExportService(snapshotRepository, pdfExportPort, docxExportPort);

    private final UUID releaseId = UUID.randomUUID();

    @Test
    void pdfUsesTheStoredMarkdownAndStoredChrome() {
        DocumentSnapshot snapshot = snapshot(3, "# Stored Markdown", ChromeFields.empty());
        when(snapshotRepository.findLatest(releaseId, TemplateDocumentType.RELEASE_NOTES))
                .thenReturn(Optional.of(snapshot));
        when(snapshotRepository.findLogo(snapshot.id())).thenReturn(Optional.empty());
        when(pdfExportPort.toPdf(any(), any(), any())).thenReturn(new byte[]{1});

        service.toPdf(releaseId, TemplateDocumentType.RELEASE_NOTES, null);

        verify(pdfExportPort).toPdf(snapshot.markdown(), snapshot.chrome(), null);
    }

    @Test
    void docxUsesTheStoredMarkdownAndStoredChrome() {
        DocumentSnapshot snapshot = snapshot(3, "# Stored Markdown", ChromeFields.empty());
        when(snapshotRepository.findLatest(releaseId, TemplateDocumentType.RELEASE_NOTES))
                .thenReturn(Optional.of(snapshot));
        when(snapshotRepository.findLogo(snapshot.id())).thenReturn(Optional.empty());
        when(docxExportPort.toDocx(any(), any(), any())).thenReturn(new byte[]{1});

        service.toDocx(releaseId, TemplateDocumentType.RELEASE_NOTES, null);

        verify(docxExportPort).toDocx(snapshot.markdown(), snapshot.chrome(), null);
    }

    @Test
    void supersededRevisionIsExportable() {
        DocumentSnapshot revisionOne = snapshot(1, "# Revision one", ChromeFields.empty());
        when(snapshotRepository.findByRevision(releaseId, TemplateDocumentType.RELEASE_NOTES, 1))
                .thenReturn(Optional.of(revisionOne));
        when(snapshotRepository.findLogo(revisionOne.id())).thenReturn(Optional.empty());
        when(pdfExportPort.toPdf(any(), any(), any())).thenReturn(new byte[]{1});

        service.toPdf(releaseId, TemplateDocumentType.RELEASE_NOTES, 1);

        verify(pdfExportPort).toPdf("# Revision one", revisionOne.chrome(), null);
        verify(snapshotRepository, never()).findLatest(any(), any());
    }

    @Test
    void omittedRevisionExportsTheLatest() {
        DocumentSnapshot latest = snapshot(5, "# Latest", ChromeFields.empty());
        when(snapshotRepository.findLatest(releaseId, TemplateDocumentType.RELEASE_NOTES))
                .thenReturn(Optional.of(latest));
        when(snapshotRepository.findLogo(latest.id())).thenReturn(Optional.empty());
        when(pdfExportPort.toPdf(any(), any(), any())).thenReturn(new byte[]{1});

        service.toPdf(releaseId, TemplateDocumentType.RELEASE_NOTES, null);

        verify(pdfExportPort).toPdf("# Latest", latest.chrome(), null);
        verify(snapshotRepository, never()).findByRevision(any(), any(), anyInt());
    }

    @Test
    void unknownRevisionThrowsDocumentNotFound() {
        when(snapshotRepository.findByRevision(releaseId, TemplateDocumentType.RELEASE_NOTES, 9))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toPdf(releaseId, TemplateDocumentType.RELEASE_NOTES, 9))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void copiedLogoIsPassedToTheRenderer() {
        DocumentSnapshot snapshot = snapshot(1, "# Body", ChromeFields.empty());
        LogoMetadata metadata = new LogoMetadata("image/png", "logo.png", 100, 10, 10, Instant.now());
        LogoImage logo = new LogoImage(new byte[]{1, 2, 3}, metadata);
        when(snapshotRepository.findLatest(releaseId, TemplateDocumentType.RELEASE_NOTES))
                .thenReturn(Optional.of(snapshot));
        when(snapshotRepository.findLogo(snapshot.id())).thenReturn(Optional.of(logo));
        when(pdfExportPort.toPdf(any(), any(), any())).thenReturn(new byte[]{1});

        service.toPdf(releaseId, TemplateDocumentType.RELEASE_NOTES, null);

        verify(pdfExportPort).toPdf(snapshot.markdown(), snapshot.chrome(), logo);
    }

    @Test
    void nullLogoIsPassedThrough() {
        DocumentSnapshot snapshot = snapshot(1, "# Body", ChromeFields.empty());
        when(snapshotRepository.findLatest(releaseId, TemplateDocumentType.RELEASE_NOTES))
                .thenReturn(Optional.of(snapshot));
        when(snapshotRepository.findLogo(snapshot.id())).thenReturn(Optional.empty());
        when(pdfExportPort.toPdf(any(), any(), any())).thenReturn(new byte[]{1});

        service.toPdf(releaseId, TemplateDocumentType.RELEASE_NOTES, null);

        verify(pdfExportPort).toPdf(snapshot.markdown(), snapshot.chrome(), null);
    }

    @Test
    void rendererFailureBecomesExportFailed() {
        DocumentSnapshot snapshot = snapshot(1, "# Body", ChromeFields.empty());
        when(snapshotRepository.findLatest(releaseId, TemplateDocumentType.RELEASE_NOTES))
                .thenReturn(Optional.of(snapshot));
        when(snapshotRepository.findLogo(snapshot.id())).thenReturn(Optional.empty());
        when(pdfExportPort.toPdf(any(), any(), any())).thenThrow(new RuntimeException("renderer blew up"));

        assertThatThrownBy(() -> service.toPdf(releaseId, TemplateDocumentType.RELEASE_NOTES, null))
                .isInstanceOf(ExportFailedException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    private DocumentSnapshot snapshot(int revision, String markdown, ChromeFields chrome) {
        return new DocumentSnapshot(UUID.randomUUID(), releaseId, TemplateDocumentType.RELEASE_NOTES, revision,
                markdown, UUID.randomUUID(), "Standard notes", "Body", chrome, Instant.now(), "actor-1");
    }
}
