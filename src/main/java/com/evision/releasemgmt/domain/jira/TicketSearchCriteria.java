package com.evision.releasemgmt.domain.jira;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public record TicketSearchCriteria(String projectId, String fixVersionId, String jql,
                                   Set<StatusCategory> statusCategories) {

    // JIRA-07's default lives here, server-side, so a client that omits the field still gets
    // the Done-only set rather than every ticket ever raised on the version.
    public static final Set<StatusCategory> DEFAULT_STATUS_CATEGORIES = EnumSet.of(StatusCategory.DONE);

    public static TicketSearchCriteria byFixVersion(String projectId, String fixVersionId,
                                                    Set<StatusCategory> statusCategories) {
        return new TicketSearchCriteria(projectId, fixVersionId, null,
                statusCategories == null ? DEFAULT_STATUS_CATEGORIES : statusCategories);
    }

    public static TicketSearchCriteria byJql(String jql) {
        return new TicketSearchCriteria(null, null, jql, Set.of());
    }

    // Composed from Jira numeric ids and code-controlled category literals, never from display
    // names or caller strings: an id cannot carry a quote or a clause separator, and the
    // category names come from the enum, so there is nothing to escape and nothing to escape
    // wrongly.
    public String toJql() {
        if (jql != null) {
            return jql;
        }
        String statusClause = statusCategories == null || statusCategories.isEmpty()
                ? ""
                : " AND statusCategory IN (" + statusCategories.stream()
                        .map(category -> "\"" + category.jqlName() + "\"")
                        .collect(Collectors.joining(", ")) + ")";
        return "project = " + projectId + " AND fixVersion = " + fixVersionId
                + statusClause + " ORDER BY key ASC";
    }
}
