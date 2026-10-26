package com.evision.releasemgmt.domain.briefing;

import com.evision.releasemgmt.domain.jira.StatusCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BriefingIssue(String key, String summary, String issueType, String status, StatusCategory statusCategory,
        String priority, boolean flagged, String assignee, Instant created, Instant updated, Instant resolved,
        LocalDate dueDate, BigDecimal storyPoints, String parentKey, int subtaskCount,
        boolean acceptanceCriteriaPresent, String description, List<Sprint> sprints) {

    public record Sprint(long id, String name, String state, Instant startDate, Instant endDate) { }
}
