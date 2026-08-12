package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.jira.StatusCategory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record TicketSearchRequest(
        @Pattern(regexp = "^[0-9]+$", message = "must be a numeric Jira project id")
        String projectId,

        @Pattern(regexp = "^[0-9]+$", message = "must be a numeric Jira sprint id")
        String sprintId,

        @Size(max = 2000, message = "must be at most 2000 characters")
        String jql,

        Set<StatusCategory> statusCategories,

        // Optional and orthogonal to the filter mode: present means "hide what this release already
        // holds", absent means the unfiltered browse screen (D-05.2-05).
        UUID releaseId) {

    @AssertTrue(message = "supply either projectId (sprintId optional), or jql - not both")
    public boolean isExactlyOneFilterMode() {
        boolean picker = projectId != null;
        boolean advanced = jql != null && !jql.isBlank();
        // A sprint without a project is meaningless, and one alongside raw JQL would be
        // silently ignored — reject both rather than guessing what was meant.
        boolean orphanSprint = sprintId != null && projectId == null;
        return (picker ^ advanced) && !orphanSprint;
    }
}
