package com.evision.releasemgmt.domain.briefing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A run produced by the app itself; slotAt is null for manual runs. */
public record AppBriefingRun(UUID id, BriefingTask task, UUID productId, Instant slotAt, BriefingTrigger trigger,
        BriefingStatus status, String reason, Integer attention, String headline, String summary,
        List<BriefingContent.Section> sections, Instant startedAt, Instant finishedAt) {

    public static final String REASON_CLAUDE_COVERED = "CLAUDE_COVERED";
    public static final String REASON_NO_JIRA_PROJECT = "NO_JIRA_PROJECT";
}
