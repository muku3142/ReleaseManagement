package com.evision.releasemgmt.domain.document;

// DOC-09/D-05-09: the four states the release library's readiness columns and release detail's
// Documents section both render, computed once here and never re-derived on the frontend.
public enum DocumentReadinessState {
    NO_ACTIVE_TEMPLATE,
    NOT_GENERATED,
    STALE,
    UP_TO_DATE
}
