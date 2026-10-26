package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.briefing.BriefingFieldIds;
import com.evision.releasemgmt.domain.briefing.BriefingIssue;

import java.util.List;

public interface JiraBriefingPort {

    /**
     * Read-only cursor-paged search; returns at most maxIssues (BriefingQueries.UNCAPPED means no cap).
     * Failures throw JiraTicketReadException with a JiraAuthFailureReason.
     */
    List<BriefingIssue> search(JiraAccessContext access, String jql, BriefingFieldIds fieldIds, int maxIssues);
}
