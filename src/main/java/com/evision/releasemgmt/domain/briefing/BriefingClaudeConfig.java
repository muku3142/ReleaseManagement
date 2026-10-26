package com.evision.releasemgmt.domain.briefing;

import java.util.Map;
import java.util.Set;

/** What briefings/config.json says: the Jira projects Claude covers per task, and default thresholds (null = absent). */
public record BriefingClaudeConfig(boolean readable, Map<BriefingTask, Set<String>> coveredProjects,
        Integer staleAfterWorkingDays, Integer dueSoonDays, Integer backlogItems, Integer keepRuns) {

    public static final BriefingClaudeConfig UNREADABLE =
            new BriefingClaudeConfig(false, Map.of(), null, null, null, null);
}
