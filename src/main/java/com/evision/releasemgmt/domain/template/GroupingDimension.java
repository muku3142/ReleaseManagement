package com.evision.releasemgmt.domain.template;

// D-05-04: a per-template setting. ISSUE_TYPE is the default so every template authored in Phase 4
// keeps rendering {{#groups}} exactly as it did - the section shape and {{groupName}} do not change,
// only which field keys the grouping. Values must match V5's CHECK constraint on
// template.grouping_dimension.
public enum GroupingDimension {
    ISSUE_TYPE,
    COMPONENT
}
