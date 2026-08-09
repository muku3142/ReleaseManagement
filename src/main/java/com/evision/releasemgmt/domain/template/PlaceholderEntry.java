package com.evision.releasemgmt.domain.template;

import java.util.List;

/**
 * @param pushes the scope a SECTION opens; null for VARIABLE and for boolean sections
 *               (which keep the enclosing scope, e.g. {{#isManual}}).
 */
public record PlaceholderEntry(PlaceholderScope scope, String token, PlaceholderKind kind,
                               PlaceholderScope pushes, String description, List<String> tags) {
}
