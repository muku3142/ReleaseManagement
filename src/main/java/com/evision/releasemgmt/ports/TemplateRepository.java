package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TemplateRepository {

    /**
     * @throws com.evision.releasemgmt.domain.template.TemplateDuplicateException
     *         when (documentType, name) already exists
     */
    Template create(Template template);

    Optional<Template> findById(UUID templateId);

    /** Ordered by document type, then name ascending - the order the /templates screen renders. */
    List<Template> findAll();

    List<Template> findByDocumentType(TemplateDocumentType documentType);

    /** D-04-12: at most one per type. D-04-13 permits zero, which is a supported state. */
    Optional<Template> findActive(TemplateDocumentType documentType);

    /**
     * Replaces name, body, chrome and warnings and stamps updated_at/updated_by. Never touches
     * active or the logo.
     *
     * @throws com.evision.releasemgmt.domain.template.TemplateNotFoundException when the id is unknown
     * @throws com.evision.releasemgmt.domain.template.TemplateDuplicateException
     *         when the new name collides within the document type
     */
    Template update(Template template);

    /**
     * D-04-12: clears whichever template of this type is currently active, then activates this one,
     * in one transaction. The partial unique index is the actual guarantee; this is the mutation.
     *
     * @throws com.evision.releasemgmt.domain.template.TemplateNotFoundException when the id is unknown
     */
    Template setActive(UUID templateId);

    /** D-04-13: no guard. Deleting the active template is permitted and leaves the type with none. */
    void delete(UUID templateId);

    Template saveLogo(UUID templateId, LogoImage image);

    /** Idempotent: removing a logo from a template that has none is a no-op. */
    Template deleteLogo(UUID templateId);

    /** The only path that reads image bytes out of the database. */
    Optional<LogoImage> findLogo(UUID templateId);

    /** D-05.4-03: the account's own default for this type, or empty. */
    Optional<Template> findUserDefault(String accountId, TemplateDocumentType documentType);

    /** Every template id this account has marked as its default, across all types, in one query. */
    Set<UUID> findUserDefaultTemplateIds(String accountId);

    /**
     * D-05.4-04: upsert on (accountId, the template's own document type). The type is read from the
     * template, never supplied by the caller. Never touches template.active.
     *
     * @throws com.evision.releasemgmt.domain.template.TemplateNotFoundException when the id is unknown
     */
    void setUserDefault(String accountId, UUID templateId);

    /** Idempotent: deletes only when this template is currently the account's default for its type. */
    void clearUserDefault(String accountId, UUID templateId);

    /** D-05.4-06: readiness's blocking check - does any template of this type exist at all. */
    boolean existsByDocumentType(TemplateDocumentType documentType);
}
