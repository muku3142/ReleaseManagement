package com.evision.releasemgmt.domain.briefing;

import java.util.List;

public record SprintCheckInput(String productName, List<BriefingIssue> nextSprint, List<BriefingIssue> backlog,
        BriefingRunSettings settings, BriefingFieldIds fields) { }
