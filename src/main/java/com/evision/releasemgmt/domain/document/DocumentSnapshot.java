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
//
// CONTENT TYPE, stated here because generation and both exporters must agree and once did not:
// `markdown` holds Mustache-resolved Markdown - post-Mustache, pre-flexmark (D-05-05). It is never
// HTML. PdfExportAdapter and DocxExportAdapter both parse it with parseMarkdown, and the document
// viewer renders it with markdown-it. Anything that puts HTML here makes every export and the
// viewer display literal escaped tags.
//
// D-14-01: an AI revision copies the source's template fields and chrome and carries the source's
// staleness basis, so readiness keeps comparing against the original generation time.
public record DocumentSnapshot(UUID id, UUID releaseId, TemplateDocumentType documentType,
                               int revision, String markdown, UUID templateId, String templateName,
                               String templateBody, ChromeFields chrome,
                               Instant generatedAt, String generatedBy, AiAssist aiAssist) {

    public static DocumentSnapshot create(UUID releaseId, TemplateDocumentType documentType,
                                          int revision, String markdown, UUID templateId,
                                          String templateName, String templateBody,
                                          ChromeFields resolvedChrome, String actor, Instant now) {
        return new DocumentSnapshot(UUID.randomUUID(), releaseId, documentType, revision, markdown,
                templateId, templateName, templateBody, resolvedChrome, now, actor, null);
    }

    public static DocumentSnapshot createAiRevision(DocumentSnapshot source, int revision, String markdown,
                                                    AiMode mode, String instruction, String actor, Instant now) {
        AiAssist assist = new AiAssist(mode, instruction, source.revision(), source.stalenessBasis());
        return new DocumentSnapshot(UUID.randomUUID(), source.releaseId(), source.documentType(), revision,
                markdown, source.templateId(), source.templateName(), source.templateBody(), source.chrome(),
                now, actor, assist);
    }

    public boolean aiAssisted() {
        return aiAssist != null;
    }

    public Instant stalenessBasis() {
        return aiAssist == null ? generatedAt : aiAssist.basisGeneratedAt();
    }

    // SAFE-03: markdown and template text are release content, so print sizes only.
    @Override
    public String toString() {
        return "DocumentSnapshot[id=" + id + ", releaseId=" + releaseId + ", documentType=" + documentType
                + ", revision=" + revision + ", markdownChars=" + (markdown == null ? 0 : markdown.length())
                + ", templateId=" + templateId + ", generatedAt=" + generatedAt + ", aiAssist=" + aiAssist + "]";
    }
}
