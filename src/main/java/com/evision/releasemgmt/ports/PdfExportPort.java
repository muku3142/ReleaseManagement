package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;

public interface PdfExportPort {

    /**
     * Renders already-resolved Markdown to PDF bytes, applying the supplied chrome.
     *
     * <p>{@code resolvedMarkdown} and {@code resolvedChrome} come from a stored
     * {@code DocumentSnapshot} and are already Mustache-resolved (D-05-05). Nothing here re-runs
     * the template engine: doing so would resolve against today's release data and destroy the
     * immutability DOC-07 guarantees.
     *
     * @param logo the logo copied onto the snapshot, or {@code null}
     * @throws java.io.UncheckedIOException when rendering fails
     */
    byte[] toPdf(String resolvedMarkdown, ChromeFields resolvedChrome, LogoImage logo);
}
