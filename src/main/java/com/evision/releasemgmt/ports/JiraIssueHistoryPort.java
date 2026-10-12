package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.enrichment.StatusTransition;
import com.evision.releasemgmt.domain.jira.StatusCategory;

import java.util.List;
import java.util.Map;

public interface JiraIssueHistoryPort {

    /** Status-field transitions only, oldest first, from the paginated /changelog (never expand=changelog, which truncates). */
    List<StatusTransition> statusTransitions(JiraAccessContext access, String issueKey);

    /** Status id -> category for every status visible to this user. Ids, never names: names collide across projects. */
    Map<String, StatusCategory> statusCategories(JiraAccessContext access);
}
