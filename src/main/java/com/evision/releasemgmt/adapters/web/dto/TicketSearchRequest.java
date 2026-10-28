package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.jira.StatusCategory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public record TicketSearchRequest(
        @Pattern(regexp = "^[0-9]+$", message = "must be a numeric Jira project id")
        String projectId,

        // Searches every Jira project mapped to this product; the keys are resolved server-side.
        UUID productId,

        @Pattern(regexp = "^[0-9]+$", message = "must be a numeric Jira sprint id")
        String sprintId,

        @Size(max = 2000, message = "must be at most 2000 characters")
        String jql,

        Set<StatusCategory> statusCategories,

        // Optional and orthogonal to the filter mode: present means "hide what this release already
        // holds", absent means the unfiltered browse screen (D-05.2-05).
        UUID releaseId,

        // Also orthogonal to the mode: hides tickets on any release, whatever its status (D-17.2-06).
        // Boxed because Jackson 3 rejects an absent primitive; absent means false.
        Boolean hideAlreadyInRelease) {

    public boolean hidesAlreadyInRelease() {
        return Boolean.TRUE.equals(hideAlreadyInRelease);
    }

    @AssertTrue(message = "supply exactly one of projectId (sprintId optional), productId, or jql")
    public boolean isExactlyOneFilterMode() {
        boolean picker = projectId != null;
        boolean product = productId != null;
        boolean advanced = jql != null && !jql.isBlank();
        // A sprint without a project is meaningless, and one alongside a product or raw JQL would
        // be silently ignored — reject rather than guessing what was meant.
        boolean orphanSprint = sprintId != null && projectId == null;
        long modes = Stream.of(picker, product, advanced).filter(Boolean::booleanValue).count();
        return modes == 1 && !orphanSprint;
    }
}
