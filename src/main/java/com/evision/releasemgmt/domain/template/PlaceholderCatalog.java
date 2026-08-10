package com.evision.releasemgmt.domain.template;

import java.util.List;
import java.util.Optional;

// D-04-20: the TMPL-03 catalog is a hand-written constant, not derived by reflection at runtime,
// so it stays a stable, documented contract. PlaceholderCatalogTest is the drift guard that proves
// this list matches the view-model's actual fields exactly, in both directions.
public final class PlaceholderCatalog {

    public static final List<PlaceholderEntry> ENTRIES = List.of(
            // ROOT
            new PlaceholderEntry(PlaceholderScope.ROOT, "release.name", PlaceholderKind.VARIABLE, null,
                    "The release's name.", List.of("Chrome + Body")),
            new PlaceholderEntry(PlaceholderScope.ROOT, "release.version", PlaceholderKind.VARIABLE, null,
                    "The release's version string.", List.of("Chrome + Body")),
            new PlaceholderEntry(PlaceholderScope.ROOT, "release.status", PlaceholderKind.VARIABLE, null,
                    "Either OPEN or FROZEN.", List.of("Chrome + Body")),
            new PlaceholderEntry(PlaceholderScope.ROOT, "release.jiraProjectKey", PlaceholderKind.VARIABLE, null,
                    "The advisory Jira project key, empty if none was set.", List.of("Chrome + Body")),
            new PlaceholderEntry(PlaceholderScope.ROOT, "release.createdAt", PlaceholderKind.VARIABLE, null,
                    "When the release was created, in ISO-8601 UTC.", List.of("Chrome + Body")),
            new PlaceholderEntry(PlaceholderScope.ROOT, "release.frozenAt", PlaceholderKind.VARIABLE, null,
                    "When the release was frozen, in ISO-8601 UTC. Empty while the release is still open.",
                    List.of("Chrome + Body")),
            new PlaceholderEntry(PlaceholderScope.ROOT, "release.lastRefreshedAt", PlaceholderKind.VARIABLE, null,
                    "When the release's ticket snapshots were last refreshed from Jira, in ISO-8601 UTC. "
                            + "Empty if never refreshed.",
                    List.of("Chrome + Body")),
            new PlaceholderEntry(PlaceholderScope.ROOT, "items", PlaceholderKind.SECTION, PlaceholderScope.ITEM,
                    "Loops over every release item, ticket-backed and manual, in one unified collection.",
                    List.of("Body only")),
            new PlaceholderEntry(PlaceholderScope.ROOT, "groups", PlaceholderKind.SECTION, PlaceholderScope.GROUP,
                    "Items pre-grouped by this template's chosen dimension (issue type or component).",
                    List.of("Body only")),

            // ITEM
            new PlaceholderEntry(PlaceholderScope.ITEM, "title", PlaceholderKind.VARIABLE, null,
                    "The ticket summary, or the manual item's title.", List.of("Body only")),
            new PlaceholderEntry(PlaceholderScope.ITEM, "isManual", PlaceholderKind.SECTION, null,
                    "True for manual items. Use {{#isManual}}…{{/isManual}} or "
                            + "{{^isManual}}…{{/isManual}} to branch.",
                    List.of("Body only")),
            new PlaceholderEntry(PlaceholderScope.ITEM, "key", PlaceholderKind.VARIABLE, null,
                    "The Jira issue key. Empty for manual items.", List.of("Body only", "Ticket only")),
            new PlaceholderEntry(PlaceholderScope.ITEM, "issueType", PlaceholderKind.VARIABLE, null,
                    "The Jira issue type, such as Bug or Story. Empty for manual items.",
                    List.of("Body only", "Ticket only")),
            new PlaceholderEntry(PlaceholderScope.ITEM, "status", PlaceholderKind.VARIABLE, null,
                    "The Jira status. Empty for manual items.", List.of("Body only", "Ticket only")),
            new PlaceholderEntry(PlaceholderScope.ITEM, "resolution", PlaceholderKind.VARIABLE, null,
                    "The Jira resolution. Empty for manual items and for unresolved tickets.",
                    List.of("Body only", "Ticket only")),
            new PlaceholderEntry(PlaceholderScope.ITEM, "assignee", PlaceholderKind.VARIABLE, null,
                    "The assignee's display name. Empty for manual items and for unassigned tickets.",
                    List.of("Body only", "Ticket only")),
            new PlaceholderEntry(PlaceholderScope.ITEM, "reporter", PlaceholderKind.VARIABLE, null,
                    "The reporter's display name. Empty for manual items.", List.of("Body only", "Ticket only")),
            new PlaceholderEntry(PlaceholderScope.ITEM, "component", PlaceholderKind.VARIABLE, null,
                    "The ticket's Jira component, or 'Uncategorised' if none is set. Empty for "
                            + "manual items.", List.of("Body only", "Ticket only")),
            new PlaceholderEntry(PlaceholderScope.ITEM, "description", PlaceholderKind.VARIABLE, null,
                    "The ticket description or the manual item's description, already flattened to Markdown.",
                    List.of("Body only")),
            new PlaceholderEntry(PlaceholderScope.ITEM, "category", PlaceholderKind.VARIABLE, null,
                    "The manual item's category. Empty for ticket-backed items.",
                    List.of("Body only", "Manual only")),
            new PlaceholderEntry(PlaceholderScope.ITEM, "comments", PlaceholderKind.SECTION, PlaceholderScope.COMMENT,
                    "Loops over the ticket's comments. Empty for manual items.",
                    List.of("Body only", "Ticket only")),
            new PlaceholderEntry(PlaceholderScope.ITEM, "commits", PlaceholderKind.SECTION, PlaceholderScope.COMMIT,
                    "Loops over the ticket's linked Azure DevOps commits.",
                    List.of("Body only", "Ticket only", "Always empty until Phase 6")),
            new PlaceholderEntry(PlaceholderScope.ITEM, "pullRequests", PlaceholderKind.SECTION,
                    PlaceholderScope.PULL_REQUEST,
                    "Loops over the ticket's linked Azure DevOps pull requests.",
                    List.of("Body only", "Ticket only", "Always empty until Phase 6")),

            // GROUP
            new PlaceholderEntry(PlaceholderScope.GROUP, "groupName", PlaceholderKind.VARIABLE, null,
                    "The group's label — an issue type for ticket-backed items or a category for "
                            + "manual items.",
                    List.of("Body only")),
            new PlaceholderEntry(PlaceholderScope.GROUP, "items", PlaceholderKind.SECTION, PlaceholderScope.ITEM,
                    "Loops over the items in this group, with the same fields as the root items collection.",
                    List.of("Body only")),

            // COMMENT
            new PlaceholderEntry(PlaceholderScope.COMMENT, "text", PlaceholderKind.VARIABLE, null,
                    "The comment body, already flattened to Markdown.", List.of("Body only")),

            // COMMIT
            new PlaceholderEntry(PlaceholderScope.COMMIT, "id", PlaceholderKind.VARIABLE, null,
                    "The commit's short identifier.", List.of("Body only", "Always empty until Phase 6")),
            new PlaceholderEntry(PlaceholderScope.COMMIT, "message", PlaceholderKind.VARIABLE, null,
                    "The commit message.", List.of("Body only", "Always empty until Phase 6")),
            new PlaceholderEntry(PlaceholderScope.COMMIT, "author", PlaceholderKind.VARIABLE, null,
                    "The commit author's display name.", List.of("Body only", "Always empty until Phase 6")),
            new PlaceholderEntry(PlaceholderScope.COMMIT, "url", PlaceholderKind.VARIABLE, null,
                    "A link to the commit in Azure DevOps.", List.of("Body only", "Always empty until Phase 6")),

            // PULL_REQUEST
            new PlaceholderEntry(PlaceholderScope.PULL_REQUEST, "id", PlaceholderKind.VARIABLE, null,
                    "The pull request's identifier.", List.of("Body only", "Always empty until Phase 6")),
            new PlaceholderEntry(PlaceholderScope.PULL_REQUEST, "title", PlaceholderKind.VARIABLE, null,
                    "The pull request title.", List.of("Body only", "Always empty until Phase 6")),
            new PlaceholderEntry(PlaceholderScope.PULL_REQUEST, "status", PlaceholderKind.VARIABLE, null,
                    "The pull request status, such as completed or active.",
                    List.of("Body only", "Always empty until Phase 6")),
            new PlaceholderEntry(PlaceholderScope.PULL_REQUEST, "url", PlaceholderKind.VARIABLE, null,
                    "A link to the pull request in Azure DevOps.",
                    List.of("Body only", "Always empty until Phase 6")));

    private PlaceholderCatalog() {
    }

    public static Optional<PlaceholderEntry> find(PlaceholderScope scope, String token) {
        return ENTRIES.stream()
                .filter(e -> e.scope() == scope && e.token().equals(token))
                .findFirst();
    }
}
