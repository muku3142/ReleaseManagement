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
        return toPdf(resolve(releaseId, type, revisionOrNull));
    }

    public byte[] toDocx(UUID releaseId, TemplateDocumentType type, Integer revisionOrNull) {
        return toDocx(resolve(releaseId, type, revisionOrNull));
    }

    // WR-02: callers that have already resolved "latest" once (e.g. to name the download) must
    // export from that SAME snapshot rather than letting this service resolve "latest" again -
    // two independent resolutions can observe different revisions if a generate() commits a new
    // one in between, leaving the downloaded filename naming a different revision than its bytes.
    public byte[] toPdf(DocumentSnapshot snapshot) {
        LogoImage logo = snapshotRepository.findLogo(snapshot.id()).orElse(null);
        try {
            return pdfExportPort.toPdf(snapshot.markdown(), snapshot.chrome(), logo);
        } catch (RuntimeException ex) {
            throw new ExportFailedException(ex);
        }
    }

    public byte[] toDocx(DocumentSnapshot snapshot) {
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
