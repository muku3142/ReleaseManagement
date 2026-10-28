package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.document.AiMode;
import com.evision.releasemgmt.domain.document.DocumentNotFoundException;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.document.RevisionSupersededException;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

// ASST-10/D-14-01: the only write path for AI text. It appends revision N+1 copied from the latest
// revision; the original is never touched. Instruction resolution and mode validation belong to the
// caller, so this service has no assistant dependency and logs nothing.
@Service
public class AiRevisionService {

    private final DocumentSnapshotRepository snapshotRepository;

    public AiRevisionService(DocumentSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    // No outer @Transactional: the repository's create is the transaction, so the after-commit index
    // listener fires when it commits, and a wider read-then-write transaction would only enlarge the
    // unique-constraint race on (release, type, revision).
    public DocumentSnapshot save(UUID releaseId, TemplateDocumentType type, int sourceRevision, AiMode mode,
                                 String instruction, String markdown, String actor) {
        DocumentSnapshot source = snapshotRepository.findLatest(releaseId, type)
                .orElseThrow(DocumentNotFoundException::new);
        if (source.revision() != sourceRevision) {
            throw new RevisionSupersededException();
        }
        int revision = snapshotRepository.nextRevision(releaseId, type);
        LogoImage logo = snapshotRepository.findLogo(source.id()).orElse(null);
        return snapshotRepository.create(
                DocumentSnapshot.createAiRevision(source, revision, markdown, mode, instruction, actor, Instant.now()),
                logo);
    }
}
