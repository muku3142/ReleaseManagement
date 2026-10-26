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

// DOC-09/D-05-09 (revised by D-15.1-05): the single staleness comparison for OPEN and FROZEN releases.
// Both the /releases readiness columns and release detail's Documents section render whatever state
// comes out of here - neither screen is allowed to re-derive staleness from raw timestamps client-side (T-05-55).
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
    public DocumentReadinessState state(ReleaseStatus status, Instant frozenAt, Instant lastRefreshedAt,
                                        Instant itemsChangedAt, boolean hasTemplate, Instant generatedAtOrNull) {
        if (!hasTemplate) {
            return DocumentReadinessState.NO_TEMPLATE;
        }
        if (generatedAtOrNull == null) {
            return DocumentReadinessState.NOT_GENERATED;
        }
        if (lastRefreshedAt != null && generatedAtOrNull.isBefore(lastRefreshedAt)) {
            return DocumentReadinessState.STALE;
        }
        if (itemsChangedAt == null || !generatedAtOrNull.isBefore(itemsChangedAt)) {
            return DocumentReadinessState.UP_TO_DATE;
        }
        // D-15.1-05: on a frozen release only a change made before the (latest) freeze counts; a later itemsChangedAt
        // can only be a product edit (D-15-01), which never makes a document stale (D-15-02).
        boolean productEditAfterFreeze = status == ReleaseStatus.FROZEN && frozenAt != null
                && itemsChangedAt.isAfter(frozenAt);
        return productEditAfterFreeze ? DocumentReadinessState.UP_TO_DATE : DocumentReadinessState.STALE;
    }

    /** DOC-09: every (release, document type) pair, in one pass. */
    public List<ReadinessEntry> all() {
        List<ReleaseSummary> releases = releaseRepository.search("");
        // D-05.4-06: readiness must read the same for every viewer, so it cannot depend on the active
        // template or per-user defaults.
        Map<TemplateDocumentType, Boolean> hasTemplate = new HashMap<>();
        for (TemplateDocumentType type : TemplateDocumentType.values()) {
            hasTemplate.put(type, templateRepository.existsByDocumentType(type));
        }
        // An AI revision carries its source's basis, so saving one cannot make a stale document look current (D-14-01).
        Map<SnapshotKey, Instant> latestBasis = new HashMap<>();
        for (var snapshot : snapshotRepository.findLatestForAllReleases()) {
            latestBasis.put(new SnapshotKey(snapshot.releaseId(), snapshot.documentType()), snapshot.stalenessBasis());
        }

        List<ReadinessEntry> entries = new ArrayList<>();
        for (ReleaseSummary release : releases) {
            for (TemplateDocumentType type : TemplateDocumentType.values()) {
                Instant basis = latestBasis.get(new SnapshotKey(release.id(), type));
                DocumentReadinessState state = state(release.status(), release.frozenAt(), release.lastRefreshedAt(),
                        release.itemsChangedAt(), hasTemplate.get(type), basis);
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
