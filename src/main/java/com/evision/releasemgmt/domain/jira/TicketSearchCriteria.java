package com.evision.releasemgmt.domain.jira;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record TicketSearchCriteria(String projectId, List<String> projectKeys, String sprintId, String jql,
                                   Set<StatusCategory> statusCategories) {

    // JIRA-07's default lives here, server-side, so a client that omits the field still gets
    // the Done-only set rather than every ticket ever raised on the version.
    public static final Set<StatusCategory> DEFAULT_STATUS_CATEGORIES = EnumSet.of(StatusCategory.DONE);

    // sprintId is optional: a project may have no scrum board, or the release manager may want
    // everything in the project regardless of sprint. A null or blank id omits the clause
    // entirely rather than matching on an empty value.
    public static TicketSearchCriteria byProject(String projectId, String sprintId,
                                                 Set<StatusCategory> statusCategories) {
        return new TicketSearchCriteria(projectId, null, sprintId, null,
                statusCategories == null ? DEFAULT_STATUS_CATEGORIES : statusCategories);
    }

    // Keys come only from product_jira_project (DB CHECK ^[A-Z][A-Z0-9_]{1,9}$), never from the
    // request; they are still quoted because a short key can be a JQL reserved word such as IN.
    public static TicketSearchCriteria byProjectKeys(List<String> projectKeys,
                                                     Set<StatusCategory> statusCategories) {
        return new TicketSearchCriteria(null, List.copyOf(projectKeys), null, null,
                statusCategories == null ? DEFAULT_STATUS_CATEGORIES : statusCategories);
    }

    public static TicketSearchCriteria byJql(String jql) {
        return new TicketSearchCriteria(null, null, null, jql, Set.of());
    }

    // Composed from Jira numeric ids, stored project keys and code-controlled category literals,
    // never from display names or caller strings: none can carry a quote or a clause separator,
    // and the category names come from the enum, so there is nothing to escape and nothing to
    // escape wrongly.
    public String toJql() {
        if (jql != null) {
            return jql;
        }
        String projectClause = projectKeys == null
                ? "project = " + projectId
                : "project in (" + projectKeys.stream()
                        .map(key -> "\"" + key + "\"")
                        .collect(Collectors.joining(", ")) + ")";
        // `sprint` is a JQL clause name Jira resolves against the Sprint field, and it accepts the
        // numeric sprint id — so no custom-field id lookup is needed here.
        String sprintClause = sprintId == null || sprintId.isBlank()
                ? ""
                : " AND sprint = " + sprintId;
        String statusClause = statusCategories == null || statusCategories.isEmpty()
                ? ""
                : " AND statusCategory IN (" + statusCategories.stream()
                        .map(category -> "\"" + category.jqlName() + "\"")
                        .collect(Collectors.joining(", ")) + ")";
        return projectClause + sprintClause + statusClause + " ORDER BY key ASC";
    }
}
