package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.document.DocumentNotFoundException;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.document.ExportFailedException;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.DocxExportPort;
import com.evision.releasemgmt.ports.PdfExportPort;
import org.springframework.stereotype.Service;

import java.util.UUID;

// D-05-05: export reads the STORED Markdown and STORED (already-resolved) chrome. It deliberately
// does NOT go through the bounded render/resolution pipeline and does NOT re-run Mustache.
// Re-resolving here would render yesterday's document against today's release data, so a
// re-download would silently differ from what was approved - which is the exact guarantee DOC-07
// and Phase 7's AUDIT-02 rest on. The absence of that dependency here is the design, not an
// oversight.
@Service
public class DocumentExportService {

    private final DocumentSnapshotRepository snapshotRepository;
    private final PdfExportPort pdfExportPort;
    private final DocxExportPort docxExportPort;

    public DocumentExportService(DocumentSnapshotRepository snapshotRepository, PdfExportPort pdfExportPort,
                                 DocxExportPort docxExportPort) {
        this.snapshotRepository = snapshotRepository;
        this.pdfExportPort = pdfExportPort;
        this.docxExportPort = docxExportPort;
    }

    public byte[] toPdf(UUID releaseId, TemplateDocumentType type, Integer revisionOrNull) {
        DocumentSnapshot snapshot = resolve(releaseId, type, revisionOrNull);
        LogoImage logo = snapshotRepository.findLogo(snapshot.id()).orElse(null);
        try {
            return pdfExportPort.toPdf(snapshot.markdown(), snapshot.chrome(), logo);
        } catch (RuntimeException ex) {
            throw new ExportFailedException(ex);
        }
    }

    public byte[] toDocx(UUID releaseId, TemplateDocumentType type, Integer revisionOrNull) {
        DocumentSnapshot snapshot = resolve(releaseId, type, revisionOrNull);
        LogoImage logo = snapshotRepository.findLogo(snapshot.id()).orElse(null);
        try {
            return docxExportPort.toDocx(snapshot.markdown(), snapshot.chrome(), logo);
        } catch (RuntimeException ex) {
            throw new ExportFailedException(ex);
        }
    }

    private DocumentSnapshot resolve(UUID releaseId, TemplateDocumentType type, Integer revisionOrNull) {
        return revisionOrNull == null
                ? snapshotRepository.findLatest(releaseId, type).orElseThrow(DocumentNotFoundException::new)
                : snapshotRepository.findByRevision(releaseId, type, revisionOrNull)
                        .orElseThrow(DocumentNotFoundException::new);
    }
}
