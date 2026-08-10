package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.DocumentSnapshotEntity;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentSnapshotJpaRepository extends JpaRepository<DocumentSnapshotEntity, UUID> {

    Optional<DocumentSnapshotEntity> findFirstByReleaseIdAndDocumentTypeOrderByRevisionDesc(
            UUID releaseId, TemplateDocumentType documentType);

    Optional<DocumentSnapshotEntity> findByReleaseIdAndDocumentTypeAndRevision(
            UUID releaseId, TemplateDocumentType documentType, int revision);

    List<DocumentSnapshotEntity> findByReleaseIdAndDocumentTypeOrderByRevisionDesc(
            UUID releaseId, TemplateDocumentType documentType);

    // One row per (release_id, document_type): the highest revision of each. A derived query cannot
    // express "greatest-n-per-group", so this is one of the targeted native/JPQL escape hatches
    // CLAUDE.md's stack notes allow rather than a second persistence style.
    @Query("""
            select d from DocumentSnapshotEntity d
            where d.revision = (select max(d2.revision) from DocumentSnapshotEntity d2
                                where d2.releaseId = d.releaseId
                                  and d2.documentType = d.documentType)
            """)
    List<DocumentSnapshotEntity> findLatestPerReleaseAndType();
}
