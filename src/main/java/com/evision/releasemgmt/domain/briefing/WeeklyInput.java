package com.evision.releasemgmt.domain.briefing;

import java.time.Instant;
import java.util.List;

public record WeeklyInput(String productName, List<BriefingIssue> completed, List<BriefingIssue> inProgress,
        List<BriefingIssue> activeSprintAll, List<BriefingIssue> added, List<BriefingIssue> dueCandidates,
        Instant now, BriefingRunSettings settings) { }
