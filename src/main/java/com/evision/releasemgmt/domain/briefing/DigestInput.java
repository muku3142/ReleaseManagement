package com.evision.releasemgmt.domain.briefing;

import java.time.Instant;
import java.util.List;

public record DigestInput(String productName, List<BriefingIssue> activeSprintOpen,
        List<BriefingIssue> staleCandidates, List<BriefingIssue> nextSprint, List<BriefingIssue> created,
        List<BriefingIssue> done, List<BriefingIssue> reopened, List<BriefingIssue> dueCandidates, Instant since,
        Instant now, BriefingRunSettings settings, BriefingFieldIds fields) { }
