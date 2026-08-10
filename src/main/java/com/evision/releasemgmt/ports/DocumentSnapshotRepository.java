package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// D-05-06: append-only by construction. There is deliberately no update, no delete and no setActive
// here - contrast TemplateRepository, which has all three. Do not add one "just for tests": a
// mutation method on this port is a defect, because DOC-07's immutability guarantee and Phase 7's
// AUDIT-02 both rest on there being no way to alter a stored revision.
public interface DocumentSnapshotRepository {

    /**
     * Appends a new revision, together with a copy of the template's logo bytes if it had one.
     *
     * @param logo the logo copied from the source template at generation time, or {@code null}
     * @throws IllegalStateException when (releaseId, documentType, revision) already exists -
     *         a lost race on {@link #nextRevision}, not a user error
     */
    DocumentSnapshot create(DocumentSnapshot snapshot, LogoImage logo);

    Optional<DocumentSnapshot> findLatest(UUID releaseId, TemplateDocumentType documentType);

    Optional<DocumentSnapshot> findByRevision(UUID releaseId, TemplateDocumentType documentType,
                                              int revision);

    /** Newest revision first - the order the revision-history disclosure and picker render. */
    List<DocumentSnapshot> findAllRevisions(UUID releaseId, TemplateDocumentType documentType);

    /**
     * The latest revision of every (release, document type) that has one. Backs DOC-09's
     * cross-release readiness grid in a single query rather than one call per release per type.
     */
    List<DocumentSnapshot> findLatestForAllReleases();

    /** The logo copied onto this snapshot, if the source template had one. */
    Optional<LogoImage> findLogo(UUID snapshotId);

    /** 1 when this (release, document type) has never been generated, else max(revision) + 1. */
    int nextRevision(UUID releaseId, TemplateDocumentType documentType);
}
