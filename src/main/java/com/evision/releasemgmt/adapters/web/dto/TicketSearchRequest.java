package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.jira.StatusCategory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record TicketSearchRequest(
        @Pattern(regexp = "^[0-9]+$", message = "must be a numeric Jira project id")
        String projectId,

        @Pattern(regexp = "^[0-9]+$", message = "must be a numeric Jira version id")
        String fixVersionId,

        @Size(max = 2000, message = "must be at most 2000 characters")
        String jql,

        Set<StatusCategory> statusCategories) {

    @AssertTrue(message = "supply either projectId (fixVersionId optional), or jql - not both")
    public boolean isExactlyOneFilterMode() {
        boolean picker = projectId != null;
        boolean advanced = jql != null && !jql.isBlank();
        // A fix version without a project is meaningless, and one alongside raw JQL would be
        // silently ignored — reject both rather than guessing what was meant.
        boolean orphanFixVersion = fixVersionId != null && projectId == null;
        return (picker ^ advanced) && !orphanFixVersion;
    }
}
