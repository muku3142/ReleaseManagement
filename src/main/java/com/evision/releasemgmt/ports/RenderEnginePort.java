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
}
