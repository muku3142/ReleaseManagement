package com.evision.releasemgmt.domain.template.view;

// D-04-18: defined now, always empty until Phase 6. Templates authored today can already write
// {{^commits}}No linked code{{/commits}} and will not need rewriting when ADO enrichment lands.
public record CommitView(String id, String message, String author, String url) {
}
