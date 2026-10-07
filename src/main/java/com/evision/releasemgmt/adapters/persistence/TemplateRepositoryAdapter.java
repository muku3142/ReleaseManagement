package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.TemplateEntity;
import com.evision.releasemgmt.adapters.persistence.entity.TemplateLogoEntity;
import com.evision.releasemgmt.adapters.persistence.entity.TemplateUserDefaultEntity;
import com.evision.releasemgmt.adapters.persistence.repository.TemplateJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.TemplateLogoJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.TemplateUserDefaultJpaRepository;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.LogoMetadata;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.domain.template.TemplateDuplicateException;
import com.evision.releasemgmt.domain.template.TemplateNotFoundException;
import com.evision.releasemgmt.ports.TemplateRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class TemplateRepositoryAdapter implements TemplateRepository {

    private final TemplateJpaRepository jpa;
    private final TemplateLogoJpaRepository logoJpa;

    private final TemplateUserDefaultJpaRepository defaultJpa;

    public TemplateRepositoryAdapter(TemplateJpaRepository jpa, TemplateLogoJpaRepository logoJpa,
                                     TemplateUserDefaultJpaRepository defaultJpa) {
        this.jpa = jpa;
        this.logoJpa = logoJpa;
        this.defaultJpa = defaultJpa;
    }

    @Override
    @Transactional
    public Template create(Template template) {
        try {
            jpa.saveAndFlush(toEntity(template));
        } catch (DataIntegrityViolationException e) {
            // UNIQUE (document_type, name) - D-04-12. saveAndFlush forces the constraint to fire
            // here rather than at commit, where it would escape this catch.
            throw new TemplateDuplicateException();
        }
        return load(template.id());
    }

    @Override
    public Optional<Template> findById(UUID templateId) {
        return jpa.findById(templateId).map(e -> toDomain(e, logoJpa.findMetadata(templateId).orElse(null)));
    }

    @Override
    public List<Template> findAll() {
        List<TemplateEntity> entities = jpa.findAllByOrderByDocumentTypeAscNameAsc();
        Set<UUID> withLogo = Set.copyOf(logoJpa.findAllTemplateIdsWithLogo());
        return entities.stream().map(e -> toDomain(e, logoFor(e.getId(), withLogo))).toList();
    }

    @Override
    public List<Template> findByDocumentType(TemplateDocumentType documentType) {
        List<TemplateEntity> entities = jpa.findByDocumentTypeOrderByNameAsc(documentType);
        Set<UUID> withLogo = Set.copyOf(logoJpa.findAllTemplateIdsWithLogo());
        return entities.stream().map(e -> toDomain(e, logoFor(e.getId(), withLogo))).toList();
    }

    @Override
    public Optional<Template> findActive(TemplateDocumentType documentType) {
        return jpa.findByDocumentTypeAndActiveTrue(documentType)
                .map(e -> toDomain(e, logoJpa.findMetadata(e.getId()).orElse(null)));
    }

    @Override
    @Transactional
    public Template update(Template template) {
        TemplateEntity entity = jpa.findById(template.id()).orElseThrow(TemplateNotFoundException::new);
        entity.setName(template.name());
        entity.setBody(template.body());
        ChromeFields chrome = template.chrome();
        entity.setCoverEnabled(chrome.coverEnabled());
        entity.setCoverTitle(chrome.coverTitle());
        entity.setCoverSubtitle(chrome.coverSubtitle());
        entity.setHeaderText(chrome.headerText());
        entity.setFooterText(chrome.footerText());
        entity.setPageNumbersEnabled(chrome.pageNumbersEnabled());
        entity.setLogoRenderHeightMm(chrome.logoRenderHeightMm());
        entity.setGroupingDimension(template.groupingDimension());
        entity.setWarnings(template.warnings());
        entity.setUpdatedAt(template.updatedAt());
        entity.setUpdatedBy(template.updatedBy());
        try {
            jpa.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw new TemplateDuplicateException();
        }
        return load(template.id());
    }

    @Override
    @Transactional
    public Template setActive(UUID templateId) {
        TemplateEntity target = jpa.findById(templateId).orElseThrow(TemplateNotFoundException::new);
        // D-04-12: deactivate-then-activate, flushed between the two steps. Without the flush,
        // Hibernate may order the INSERT-side update first and the partial unique index rejects
        // the transaction.
        jpa.findByDocumentTypeAndActiveTrue(target.getDocumentType()).ifPresent(e -> e.setActive(false));
        jpa.flush();
        target.setActive(true);
        jpa.flush();
        return load(templateId);
    }

    @Override
    @Transactional
    public void delete(UUID templateId) {
        // D-04-13: no guard, no existence check. The template_logo and template_user_default rows
        // go with it via ON DELETE CASCADE.
        jpa.deleteById(templateId);
    }

    @Override
    @Transactional
    public Template saveLogo(UUID templateId, LogoImage image) {
        TemplateLogoEntity entity = logoJpa.findById(templateId).orElseGet(TemplateLogoEntity::new);
        entity.setTemplateId(templateId);
        entity.setBytes(image.bytes());
        LogoMetadata metadata = image.metadata();
        entity.setContentType(metadata.contentType());
        entity.setFilename(metadata.filename());
        entity.setByteSize(metadata.byteSize());
        entity.setWidth(metadata.width());
        entity.setHeight(metadata.height());
        entity.setUploadedAt(metadata.uploadedAt());
        logoJpa.save(entity);
        return load(templateId);
    }

    @Override
    @Transactional
    public Template deleteLogo(UUID templateId) {
        if (logoJpa.existsById(templateId)) {
            logoJpa.deleteById(templateId);
        }
        return load(templateId);
    }

    @Override
    public Optional<LogoImage> findLogo(UUID templateId) {
        return logoJpa.findById(templateId).map(TemplateRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Template> findUserDefault(String accountId, TemplateDocumentType documentType) {
        return defaultJpa.findByAccountIdAndDocumentType(accountId, documentType)
                .flatMap(d -> findById(d.getTemplateId()));
    }

    @Override
    public Set<UUID> findUserDefaultTemplateIds(String accountId) {
        return defaultJpa.findByAccountId(accountId).stream()
                .map(TemplateUserDefaultEntity::getTemplateId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional
    public void setUserDefault(String accountId, UUID templateId) {
        if (defaultJpa.upsertFromTemplate(accountId, templateId) == 0) {
            throw new TemplateNotFoundException();
        }
    }

    @Override
    @Transactional
    public void clearUserDefault(String accountId, UUID templateId) {
        defaultJpa.deleteByAccountIdAndTemplateId(accountId, templateId);
    }

    @Override
    public boolean existsByDocumentType(TemplateDocumentType documentType) {
        return jpa.existsByDocumentType(documentType);
    }

    private Template load(UUID id) {
        return findById(id).orElseThrow(TemplateNotFoundException::new);
    }

    private LogoMetadata logoFor(UUID templateId, Set<UUID> withLogo) {
        return withLogo.contains(templateId) ? logoJpa.findMetadata(templateId).orElse(null) : null;
    }

    private static TemplateEntity toEntity(Template template) {
        TemplateEntity entity = new TemplateEntity();
        entity.setId(template.id());
        entity.setName(template.name());
        entity.setDocumentType(template.documentType());
        entity.setBody(template.body());
        entity.setActive(template.active());
        ChromeFields chrome = template.chrome();
        entity.setCoverEnabled(chrome.coverEnabled());
        entity.setCoverTitle(chrome.coverTitle());
        entity.setCoverSubtitle(chrome.coverSubtitle());
        entity.setHeaderText(chrome.headerText());
        entity.setFooterText(chrome.footerText());
        entity.setPageNumbersEnabled(chrome.pageNumbersEnabled());
        entity.setLogoRenderHeightMm(chrome.logoRenderHeightMm());
        entity.setGroupingDimension(template.groupingDimension());
        entity.setWarnings(template.warnings());
        entity.setCreatedAt(template.createdAt());
        entity.setCreatedBy(template.createdBy());
        entity.setUpdatedAt(template.updatedAt());
        entity.setUpdatedBy(template.updatedBy());
        return entity;
    }

    private static Template toDomain(TemplateEntity e, LogoMetadata logo) {
        ChromeFields chrome = new ChromeFields(e.isCoverEnabled(), e.getCoverTitle(), e.getCoverSubtitle(),
                e.getHeaderText(), e.getFooterText(), e.isPageNumbersEnabled(), e.getLogoRenderHeightMm());
        return new Template(e.getId(), e.getName(), e.getDocumentType(), e.getBody(), e.isActive(), chrome,
                e.getGroupingDimension(), logo, e.getWarnings(), e.getCreatedAt(), e.getCreatedBy(),
                e.getUpdatedAt(), e.getUpdatedBy());
    }

    private static LogoImage toDomain(TemplateLogoEntity e) {
        LogoMetadata metadata = new LogoMetadata(e.getContentType(), e.getFilename(), e.getByteSize(),
                e.getWidth(), e.getHeight(), e.getUploadedAt());
        return new LogoImage(e.getBytes(), metadata);
    }
}
