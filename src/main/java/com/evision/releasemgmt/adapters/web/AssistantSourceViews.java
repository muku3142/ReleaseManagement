package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.AssistantChatEvents.SourceView;
import com.evision.releasemgmt.domain.assistant.FailureReason;
import com.evision.releasemgmt.domain.assistant.Source;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

// Citations are built from the retrieval record only, never from model-written text (D-13-22). Every id that
// reaches the browser is validated, because the UI turns them into links.
final class AssistantSourceViews {

    private static final Pattern TICKET_KEY = Pattern.compile("^[A-Z][A-Z0-9_]+-\\d+$");
    private static final int RELEASE_VERSION_MAX = 100;
    private static final int TITLE_MAX = 300;
    private static final String TOOL_KIND = "TOOL";

    private AssistantSourceViews() {
    }

    static SourceView of(Source source) {
        String title = truncate(source.title(), TITLE_MAX);
        if (TOOL_KIND.equals(source.kind())) {
            return toolView(source, title);
        }
        Map<String, Object> meta = source.metadata() == null ? Map.of() : source.metadata();
        String releaseId = uuid(meta.get("release_id"));
        String ticketKey = ticketKey(meta.get("ticket_key"));
        String collection = source.collection() == null ? "" : source.collection();
        return switch (collection) {
            case "rm-tickets" -> new SourceView(source.n(), "TICKET", title, releaseId, null, ticketKey, null, null);
            case "rm-documents" -> new SourceView(source.n(), "DOCUMENT", title, releaseId,
                    truncate(string(meta.get("release_version")), RELEASE_VERSION_MAX), null,
                    documentType(meta.get("doc_type")), revision(meta.get("revision")));
            case "rm-manual-items" ->
                    new SourceView(source.n(), "MANUAL_ITEM", title, releaseId, null, null, null, null);
            case "rm-code-links" ->
                    new SourceView(source.n(), "CODE_LINKS", title, releaseId, null, ticketKey, null, null);
            default -> new SourceView(source.n(), "DOCUMENT", title, null, null, null, null, null);
        };
    }

    static String ofError(FailureReason reason) {
        return reason == FailureReason.REJECTED || reason == FailureReason.FAILED ? "FAILED" : reason.name();
    }

    // sourceId is "<tool>:<uuid>", "<tool>:KEY" or "<tool>:all|recent|pending|<scope>"
    private static SourceView toolView(Source source, String title) {
        String id = source.documentId() == null ? "" : source.documentId();
        String value = id.substring(id.indexOf(':') + 1);
        return new SourceView(source.n(), TOOL_KIND, title, uuid(value), null, ticketKey(value), null, null);
    }

    private static String uuid(Object value) {
        if (value instanceof String text) {
            try {
                String canonical = UUID.fromString(text).toString();
                return canonical.equalsIgnoreCase(text) ? canonical : null;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private static String ticketKey(Object value) {
        return value instanceof String text && TICKET_KEY.matcher(text).matches() ? text : null;
    }

    private static String documentType(Object value) {
        return value instanceof String text
                && Arrays.stream(TemplateDocumentType.values()).anyMatch(type -> type.name().equals(text))
                ? text : null;
    }

    private static Integer revision(Object value) {
        try {
            int parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String string(Object value) {
        return value instanceof String text ? text : null;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
