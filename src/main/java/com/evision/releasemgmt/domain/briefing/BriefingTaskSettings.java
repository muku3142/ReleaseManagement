package com.evision.releasemgmt.domain.briefing;

/** A null threshold means: use briefings/config.json, then the built-in default. */
public record BriefingTaskSettings(BriefingTask task, boolean enabled, String cron,
        Integer staleAfterWorkingDays, Integer dueSoonDays, Integer backlogItems) { }
