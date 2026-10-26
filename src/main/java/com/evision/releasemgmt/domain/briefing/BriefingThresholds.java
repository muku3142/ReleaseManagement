package com.evision.releasemgmt.domain.briefing;

public record BriefingThresholds(int staleAfterWorkingDays, int dueSoonDays, int backlogItems) {

    public static final BriefingThresholds DEFAULTS = new BriefingThresholds(3, 5, 15);
}
