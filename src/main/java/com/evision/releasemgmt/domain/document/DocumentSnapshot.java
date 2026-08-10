package com.evision.releasemgmt.domain.document;

import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;

import java.time.Instant;
import java.util.UUID;

// D-05-06: append-only. Unlike Template there is no update path and unlike Release there is no
// lifecycle guard, because there is no lifecycle - a snapshot is written once and never changes.
// Regeneration appends revision N+1; nothing is overwritten or pruned, which is what makes Phase 7's
// AUDIT-02 ("every document generated for a release, including superseded revisions") possible.
//
// D-05-05: templateName, templateBody, chrome and the logo are COPIES taken at generation time, not
// references. D-04-13 lets a template be deleted while its generated documents must stay readable
// and exportable. templateId is carried only so the API can report whether the source still exists.
// chrome holds RESOLVED text (post-Mustache) - re-resolving it at read time would read today's
// release data into yesterday's document.
public record DocumentSnapshot(UUID id, UUID releaseId, TemplateDocumentType documentType,
                               int revision, String markdown, UUID templateId, String templateName,
                               String templateBody, ChromeFields chrome,
                               Instant generatedAt, String generatedBy) {

    public static DocumentSnapshot create(UUID releaseId, TemplateDocumentType documentType,
                                          int revision, String markdown, UUID templateId,
                                          String templateName, String templateBody,
                                          ChromeFields resolvedChrome, String actor, Instant now) {
        return new DocumentSnapshot(UUID.randomUUID(), releaseId, documentType, revision, markdown,
                templateId, templateName, templateBody, resolvedChrome, now, actor);
    }
}
