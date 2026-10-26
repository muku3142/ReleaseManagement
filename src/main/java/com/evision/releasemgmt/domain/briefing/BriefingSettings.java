package com.evision.releasemgmt.domain.briefing;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;

public record BriefingSettings(String schedulerAccountId, int graceMinutes, Set<DayOfWeek> workDays, Integer keepRuns,
        boolean fallbackEnabled, Instant updatedAt, String updatedBy) { }
