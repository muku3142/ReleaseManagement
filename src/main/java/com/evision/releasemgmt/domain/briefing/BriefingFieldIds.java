package com.evision.releasemgmt.domain.briefing;

/** Resolved custom field ids for the site; null means the site has no such field and the rule reads "not checked". */
public record BriefingFieldIds(String storyPoints, String flagged, String sprint, String acceptanceCriteria) { }
