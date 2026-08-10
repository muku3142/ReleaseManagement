package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;

public interface DocxExportPort {

    /**
     * Renders already-resolved Markdown and already-resolved chrome fields to a {@code .docx}
     * byte array.
     *
     * <p>{@code resolvedMarkdown} and {@code resolvedChrome} have already been through
     * {@link RenderEnginePort} at generation time (D-05-05) - this method does not re-run the
     * template engine and never sees an unresolved Mustache placeholder.
     *
     * @param logo the logo copied onto the source document snapshot, or {@code null} when the
     *             template had none
     */
    byte[] toDocx(String resolvedMarkdown, ChromeFields resolvedChrome, LogoImage logo);
}
