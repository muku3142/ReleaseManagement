package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.DocumentSnapshotEntity;
import com.evision.releasemgmt.adapters.persistence.entity.DocumentSnapshotLogoEntity;
import com.evision.releasemgmt.adapters.persistence.repository.DocumentSnapshotJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.DocumentSnapshotLogoJpaRepository;
import com.evision.releasemgmt.domain.document.DocumentNotFoundException;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.LogoMetadata;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DocumentSnapshotRepositoryAdapter implements DocumentSnapshotRepository {

    private final DocumentSnapshotJpaRepository jpa;
    private final DocumentSnapshotLogoJpaRepository logoJpa;

    public DocumentSnapshotRepositoryAdapter(DocumentSnapshotJpaRepository jpa,
                                             DocumentSnapshotLogoJpaRepository logoJpa) {
        this.jpa = jpa;
        this.logoJpa = logoJpa;
    }

    @Override
    @Transactional
    public DocumentSnapshot create(DocumentSnapshot snapshot, LogoImage logo) {
        try {
            jpa.saveAndFlush(toEntity(snapshot));
        } catch (DataIntegrityViolationException e) {
            // UNIQUE (release_id, document_type, revision) - D-05-06. Flushing immediately forces the
            // constraint to fire here rather than at commit, where it would escape this catch.
            throw new IllegalStateException("Concurrent generation produced a duplicate revision", e);
        }
        if (logo != null) {
            DocumentSnapshotLogoEntity logoEntity = new DocumentSnapshotLogoEntity();
            logoEntity.setSnapshotId(snapshot.id());
            logoEntity.setBytes(logo.bytes());
            LogoMetadata metadata = logo.metadata();
            logoEntity.setContentType(metadata.contentType());
            logoEntity.setFilename(metadata.filename());
            logoEntity.setByteSize(metadata.byteSize());
            logoEntity.setWidth(metadata.width());
            logoEntity.setHeight(metadata.height());
            logoEntity.setUploadedAt(metadata.uploadedAt());
            logoJpa.save(logoEntity);
        }
        return load(snapshot.id());
    }

    @Override
    public Optional<DocumentSnapshot> findLatest(UUID releaseId, TemplateDocumentType documentType) {
        return jpa.findFirstByReleaseIdAndDocumentTypeOrderByRevisionDesc(releaseId, documentType)
                .map(DocumentSnapshotRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<DocumentSnapshot> findByRevision(UUID releaseId, TemplateDocumentType documentType,
                                                      int revision) {
        return jpa.findByReleaseIdAndDocumentTypeAndRevision(releaseId, documentType, revision)
                .map(DocumentSnapshotRepositoryAdapter::toDomain);
    }

    @Override
    public List<DocumentSnapshot> findAllRevisions(UUID releaseId, TemplateDocumentType documentType) {
        return jpa.findByReleaseIdAndDocumentTypeOrderByRevisionDesc(releaseId, documentType).stream()
                .map(DocumentSnapshotRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<DocumentSnapshot> findLatestForAllReleases() {
        return jpa.findLatestPerReleaseAndType().stream()
                .map(DocumentSnapshotRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<LogoImage> findLogo(UUID snapshotId) {
        return logoJpa.findById(snapshotId).map(DocumentSnapshotRepositoryAdapter::toDomain);
    }

    @Override
    public int nextRevision(UUID releaseId, TemplateDocumentType documentType) {
        return jpa.findFirstByReleaseIdAndDocumentTypeOrderByRevisionDesc(releaseId, documentType)
                .map(e -> e.getRevision() + 1)
                .orElse(1);
    }

    private DocumentSnapshot load(UUID id) {
        return jpa.findById(id).map(DocumentSnapshotRepositoryAdapter::toDomain)
                .orElseThrow(DocumentNotFoundException::new);
    }

    private static DocumentSnapshotEntity toEntity(DocumentSnapshot snapshot) {
        DocumentSnapshotEntity entity = new DocumentSnapshotEntity();
        entity.setId(snapshot.id());
        entity.setReleaseId(snapshot.releaseId());
        entity.setDocumentType(snapshot.documentType());
        entity.setRevision(snapshot.revision());
        entity.setMarkdown(snapshot.markdown());
        entity.setTemplateId(snapshot.templateId());
        entity.setTemplateName(snapshot.templateName());
        entity.setTemplateBody(snapshot.templateBody());
        ChromeFields chrome = snapshot.chrome();
        entity.setCoverEnabled(chrome.coverEnabled());
        entity.setCoverTitle(chrome.coverTitle());
        entity.setCoverSubtitle(chrome.coverSubtitle());
        entity.setHeaderText(chrome.headerText());
        entity.setFooterText(chrome.footerText());
        entity.setPageNumbersEnabled(chrome.pageNumbersEnabled());
        entity.setGeneratedAt(snapshot.generatedAt());
        entity.setGeneratedBy(snapshot.generatedBy());
        return entity;
    }

    private static DocumentSnapshot toDomain(DocumentSnapshotEntity e) {
        ChromeFields chrome = new ChromeFields(e.isCoverEnabled(), e.getCoverTitle(), e.getCoverSubtitle(),
                e.getHeaderText(), e.getFooterText(), e.isPageNumbersEnabled());
        return new DocumentSnapshot(e.getId(), e.getReleaseId(), e.getDocumentType(), e.getRevision(),
                e.getMarkdown(), e.getTemplateId(), e.getTemplateName(), e.getTemplateBody(), chrome,
                e.getGeneratedAt(), e.getGeneratedBy());
    }

    private static LogoImage toDomain(DocumentSnapshotLogoEntity e) {
        LogoMetadata metadata = new LogoMetadata(e.getContentType(), e.getFilename(), e.getByteSize(),
                e.getWidth(), e.getHeight(), e.getUploadedAt());
        return new LogoImage(e.getBytes(), metadata);
    }
}
