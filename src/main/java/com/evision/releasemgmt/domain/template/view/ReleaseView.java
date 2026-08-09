package com.evision.releasemgmt.domain.template.view;

import java.util.List;

// D-04-03: the release is the only root object a template can reach. There is deliberately no
// generatedAt/generatedBy and no Jira site URL here - when a document was rendered is metadata
// about the PDF, not a fact about the release. Phase 5 records that on the document snapshot.
public record ReleaseView(String name, String version, String status, String jiraProjectKey,
                          String createdAt, String frozenAt, String lastRefreshedAt,
                          List<ItemView> items, List<GroupView> groups) {
}
