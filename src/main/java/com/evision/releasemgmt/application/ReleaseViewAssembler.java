package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.template.GroupingDimension;
import com.evision.releasemgmt.domain.template.view.CommentView;
import com.evision.releasemgmt.domain.template.view.GroupView;
import com.evision.releasemgmt.domain.template.view.CommitView;
import com.evision.releasemgmt.domain.template.view.ItemView;
import com.evision.releasemgmt.domain.template.view.PullRequestView;
import com.evision.releasemgmt.domain.template.view.ReleaseView;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// D-03-06 already materialised every ticket copy locally, so this reads only the passed aggregate -
// no release/ticket persistence port and no Jira call - which is what makes preview and save-time
// validation Jira-free.
@Service
public class ReleaseViewAssembler {

    public ReleaseView assemble(Release release, GroupingDimension dimension) {
        List<ItemView> ticketItems = release.items().stream()
                .sorted(Comparator.comparing(ReleaseItem::ticketKey))
                .map(ReleaseViewAssembler::mapTicketItem)
                .toList();
        List<ItemView> manualItems = release.manualItems().stream()
                .sorted(Comparator.comparing(ManualItem::addedAt))
                .map(ReleaseViewAssembler::mapManualItem)
                .toList();

        // D-04-01: ONE collection for ticket-backed and manual items, tickets first.
        List<ItemView> items = new ArrayList<>(ticketItems.size() + manualItems.size());
        items.addAll(ticketItems);
        items.addAll(manualItems);

        return new ReleaseView(
                nullToEmpty(release.name()),
                nullToEmpty(release.version()),
                release.status().name(),
                nullToEmpty(release.jiraProjectKey()),
                instantOrEmpty(release.createdAt()),
                instantOrEmpty(release.frozenAt()),
                instantOrEmpty(release.lastRefreshedAt()),
                List.copyOf(items),
                groups(items, dimension));
    }

    // D-04-02: Mustache is logic-less and cannot group, so grouping is always a view-model concern.
    // Issue type is DOC-02's stated default; Phase 5 (D-05-04) added component as the one other
    // supported dimension - epic and label were considered and declined (D-05-02). Manual items
    // always have a category instead, so they group by that regardless of dimension.
    private static List<GroupView> groups(List<ItemView> items, GroupingDimension dimension) {
        List<GroupView> result = new ArrayList<>();

        // Ticket-backed groups first, sorted alphabetically by group name.
        Map<String, List<ItemView>> byKey = new TreeMap<>();
        for (ItemView item : items) {
            if (!item.isManual()) {
                byKey.computeIfAbsent(groupKey(item, dimension), k -> new ArrayList<>()).add(item);
            }
        }
        byKey.forEach((key, groupItems) -> result.add(new GroupView(key, List.copyOf(groupItems))));

        // Then manual-category groups, in ManualItemCategory declaration order.
        for (ManualItemCategory category : ManualItemCategory.values()) {
            String label = categoryLabel(category);
            List<ItemView> groupItems = items.stream()
                    .filter(item -> item.isManual() && label.equals(item.category()))
                    .toList();
            if (!groupItems.isEmpty()) {
                result.add(new GroupView(label, groupItems));
            }
        }

        return List.copyOf(result);
    }

    // D-05-03: the no-component display default is applied HERE, not in the Jira adapter and not
    // in the database. The stored snapshot records what Jira actually said - including nothing -
    // and the view-model is where a display default belongs.
    private static String groupKey(ItemView item, GroupingDimension dimension) {
        if (dimension == GroupingDimension.COMPONENT) {
            return item.component().isBlank() ? "Uncategorised" : item.component();
        }
        return item.issueType();
    }

    private static ItemView mapTicketItem(ReleaseItem item) {
        TicketDetail ticket = item.ticket();
        List<CommentView> comments = ticket.comments().stream().map(CommentView::new).toList();
        CodeLinks links = item.codeLinks();
        // D-06-07: only LINKED items reach templates; FAILED renders exactly like NO_LINK and no failure flag is exposed.
        List<CommitView> commits = links.documentCommits().stream()
                .map(c -> new CommitView(c.shortSha(), c.message(), nullToEmpty(c.author()), nullToEmpty(c.url())))
                .toList();
        List<PullRequestView> pullRequests = links.documentPullRequests().stream()
                .map(p -> new PullRequestView(p.id(), nullToEmpty(p.title()), nullToEmpty(p.status()),
                        nullToEmpty(p.url())))
                .toList();
        return new ItemView(
                nullToEmpty(ticket.summary()),
                false,
                nullToEmpty(ticket.key()),
                nullToEmpty(ticket.issueType()),
                nullToEmpty(ticket.status()),
                nullToEmpty(ticket.resolution()),
                nullToEmpty(ticket.assignee()),
                nullToEmpty(ticket.reporter()),
                nullToEmpty(ticket.component()),
                nullToEmpty(ticket.description()),
                "",
                comments,
                commits,
                pullRequests);
    }

    private static ItemView mapManualItem(ManualItem item) {
        return new ItemView(
                nullToEmpty(item.title()),
                true,
                "", "", "", "", "", "", "",
                nullToEmpty(item.description()),
                categoryLabel(item.category()),
                List.of(),
                List.of(),
                List.of());
    }

    // Matches frontend/src/api/releases.ts's MANUAL_ITEM_CATEGORY_LABELS exactly - the UI and the
    // generated document must show the same label for the same category.
    private static String categoryLabel(ManualItemCategory category) {
        return switch (category) {
            case CONFIG_CHANGE -> "Config change";
            case DATABASE_SCRIPT -> "Database script";
            case INFRASTRUCTURE -> "Infrastructure";
            case OTHER -> "Other";
        };
    }

    private static String instantOrEmpty(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
