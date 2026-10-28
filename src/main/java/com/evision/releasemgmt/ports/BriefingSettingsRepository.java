package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.briefing.BriefingSettings;
import com.evision.releasemgmt.domain.briefing.BriefingTaskSettings;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface BriefingSettingsRepository {

    BriefingSettings get();

    /** The three app-fallback tasks in BriefingTask order. */
    List<BriefingTaskSettings> tasks();

    /** Never changes scheduler_account_id. */
    void saveSchedule(int graceMinutes, Set<DayOfWeek> workDays, Integer keepRuns, List<BriefingTaskSettings> tasks,
            Instant at, String by);

    /** Stores only the account id (no credential read, no decryption). */
    void setSchedulerAccount(String accountId, Instant at);

    /** Changes only the "Claude unavailable" switch and the audit columns (D-17.1-24). */
    void setFallbackEnabled(boolean enabled, Instant at, String by);
}
