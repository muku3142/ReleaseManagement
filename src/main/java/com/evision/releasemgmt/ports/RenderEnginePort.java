package com.evision.releasemgmt.ports;

import java.util.Map;

public interface RenderEnginePort {

    /**
     * Resolves the Mustache placeholders in {@code source} against {@code context}, then parses the
     * resulting Markdown and renders it to HTML.
     *
     * <p>The context is plain data only - String, Boolean, List and Map. Passing a domain record or
     * a JPA entity here would hand the engine a reflection surface and defeat TMPL-02.
     *
     * <p>Raw HTML in {@code source} or in any interpolated value is escaped, never emitted as
     * markup (D-04-05).
     *
     * @throws com.evision.releasemgmt.domain.template.TemplateMalformedException
     *         when the engine rejects the source
     */
    String render(String source, Map<String, Object> context);

    /**
     * Resolves the Mustache placeholders in {@code source} against {@code context} and returns the
     * resolved text verbatim - no Markdown parse, no HTML.
     *
     * <p>TMPL-08 chrome fields (cover title/subtitle, header, footer) are single-line plain text,
     * not Markdown documents. Passing them through {@link #render} would wrap a running header in a
     * {@code <p>} element and escape characters a plain-text header must keep.
     *
     * <p>The same context rules as {@link #render} apply: plain data only.
     *
     * @throws com.evision.releasemgmt.domain.template.TemplateMalformedException
     *         when the engine rejects the source
     */
    String resolve(String source, Map<String, Object> context);
}
