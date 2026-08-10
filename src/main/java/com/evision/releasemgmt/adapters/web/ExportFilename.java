package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.domain.template.TemplateDocumentType;

/**
 * Builds a safe Content-Disposition filename from free-text release data.
 *
 * <p>This is the first place in this codebase that interpolates user-supplied free text into an
 * HTTP header value. Release name and version are unvalidated (D-03-08), so an ALLOWLIST is used
 * rather than a denylist: every character outside [A-Za-z0-9 ._-] is replaced. That single rule
 * closes header injection (no CR/LF/quote survives), path traversal (no slash or backslash
 * survives) and encoding ambiguity (no non-ASCII survives) at once.
 *
 * <p>TemplateController.downloadLogo is NOT a safe copy for this: its filename comes from a
 * validated upload's own metadata, not from free text.
 */
public final class ExportFilename {

    private static final int MAX_LENGTH = 120;

    private ExportFilename() {
    }

    public static String sanitize(String releaseName, String version, TemplateDocumentType type,
                                  int revision, String extension) {
        String raw = releaseName + " " + version + " " + label(type) + " r" + revision;
        String candidate = collapseAndStrip(allowlist(raw));
        if (candidate.isEmpty()) {
            candidate = "document";
        }
        int maxBase = MAX_LENGTH - extension.length() - 1;
        if (candidate.length() > maxBase) {
            candidate = collapseAndStrip(candidate.substring(0, maxBase));
            if (candidate.isEmpty()) {
                candidate = "document";
            }
        }
        return candidate + "." + extension;
    }

    // No RFC 5987/6266 extended-notation ("ext-value") header parameter is emitted here: the
    // sanitiser folds to ASCII deliberately, so there is nothing an extended UTF-8 encoded form
    // would add except a second, differently-escaped copy of the same value to keep correct.
    public static String attachment(String filename) {
        return "attachment; filename=\"" + filename + "\"";
    }

    private static String label(TemplateDocumentType type) {
        return switch (type) {
            case RELEASE_NOTES -> "Release Notes";
            case RUNBOOK -> "Runbook";
            case APPROVAL -> "Approval";
        };
    }

    private static String allowlist(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean allowed = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == ' ' || c == '.' || c == '_' || c == '-';
            sb.append(allowed ? c : '_');
        }
        return sb.toString();
    }

    private static String collapseAndStrip(String s) {
        String collapsed = s.replaceAll("_+", "_").replaceAll(" +", " ");
        int start = 0;
        int end = collapsed.length();
        while (start < end && isStrippable(collapsed.charAt(start))) {
            start++;
        }
        while (end > start && isStrippable(collapsed.charAt(end - 1))) {
            end--;
        }
        return collapsed.substring(start, end);
    }

    private static boolean isStrippable(char c) {
        return c == '_' || c == ' ' || c == '.';
    }
}
