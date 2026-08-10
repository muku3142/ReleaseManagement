package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.document.DocumentReadinessState;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import com.evision.releasemgmt.ports.TemplateRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// DOC-09/D-05-09: the single staleness comparison. Both the /releases readiness columns and
// release detail's Documents section render whatever state comes out of here - neither screen is
// allowed to re-derive staleness from raw timestamps client-side (T-05-55).
@Service
public class DocumentReadinessService {

    private final ReleaseRepository releaseRepository;
    private final TemplateRepository templateRepository;
    private final DocumentSnapshotRepository snapshotRepository;

    public DocumentReadinessService(ReleaseRepository releaseRepository, TemplateRepository templateRepository,
                                    DocumentSnapshotRepository snapshotRepository) {
        this.releaseRepository = releaseRepository;
        this.templateRepository = templateRepository;
        this.snapshotRepository = snapshotRepository;
    }

    /** Pure comparison - no port calls. The caller supplies what it has already loaded. */
    public DocumentReadinessState state(ReleaseStatus status, Instant lastRefreshedAt, Instant itemsChangedAt,
                                        boolean hasActiveTemplate, Instant generatedAtOrNull) {
        if (!hasActiveTemplate) {
            return DocumentReadinessState.NO_ACTIVE_TEMPLATE;
        }
        if (generatedAtOrNull == null) {
            return DocumentReadinessState.NOT_GENERATED;
        }
        if (status == ReleaseStatus.FROZEN) {
            // D-05-09: a frozen release's ticket set cannot change (D-03-10), so "frozen + generated"
            // is the terminal, fully-satisfied state and no later timestamp can make it stale.
            return DocumentReadinessState.UP_TO_DATE;
        }
        if (lastRefreshedAt != null && generatedAtOrNull.isBefore(lastRefreshedAt)) {
            return DocumentReadinessState.STALE;
        }
        if (itemsChangedAt != null && generatedAtOrNull.isBefore(itemsChangedAt)) {
            return DocumentReadinessState.STALE;
        }
        return DocumentReadinessState.UP_TO_DATE;
    }

    /** DOC-09: every (release, document type) pair, in one pass. */
    public List<ReadinessEntry> all() {
        List<ReleaseSummary> releases = releaseRepository.search("");
        Map<TemplateDocumentType, Boolean> hasActiveTemplate = new HashMap<>();
        for (TemplateDocumentType type : TemplateDocumentType.values()) {
            hasActiveTemplate.put(type, templateRepository.findActive(type).isPresent());
        }
        Map<SnapshotKey, Instant> latestGeneratedAt = new HashMap<>();
        for (var snapshot : snapshotRepository.findLatestForAllReleases()) {
            latestGeneratedAt.put(new SnapshotKey(snapshot.releaseId(), snapshot.documentType()), snapshot.generatedAt());
        }

        List<ReadinessEntry> entries = new ArrayList<>();
        for (ReleaseSummary release : releases) {
            for (TemplateDocumentType type : TemplateDocumentType.values()) {
                Instant generatedAt = latestGeneratedAt.get(new SnapshotKey(release.id(), type));
                DocumentReadinessState state = state(release.status(), release.lastRefreshedAt(),
                        release.itemsChangedAt(), hasActiveTemplate.get(type), generatedAt);
                entries.add(new ReadinessEntry(release.id(), type, state));
            }
        }
        return entries;
    }

    public record ReadinessEntry(UUID releaseId, TemplateDocumentType documentType, DocumentReadinessState state) {
    }

    private record SnapshotKey(UUID releaseId, TemplateDocumentType documentType) {
    }
}
