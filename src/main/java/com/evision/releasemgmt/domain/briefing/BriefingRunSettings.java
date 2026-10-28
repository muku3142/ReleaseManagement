package com.evision.releasemgmt.domain.briefing;

public record BriefingRunSettings(BriefingThresholds thresholds, WorkCalendar calendar, int keepRuns) {

    public static final int DEFAULT_KEEP_RUNS = 60;
}
