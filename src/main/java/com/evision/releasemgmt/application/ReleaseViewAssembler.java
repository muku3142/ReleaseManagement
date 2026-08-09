package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.template.view.CommentView;
import com.evision.releasemgmt.domain.template.view.GroupView;
import com.evision.releasemgmt.domain.template.view.ItemView;
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

    public ReleaseView assemble(Release release) {
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
                groups(items));
    }

    // D-04-02: Mustache is logic-less and cannot group, so grouping is always a view-model concern.
    // Issue type is DOC-02's stated default and the only dimension currently fetched (D-02-02);
    // manual items have a category instead, so they group by that. Phase 5 adds component/epic/label
    // as further dimensions on this same shape rather than reshaping the view-model.
    private static List<GroupView> groups(List<ItemView> items) {
        List<GroupView> result = new ArrayList<>();

        // Issue-type groups first, sorted alphabetically by group name.
        Map<String, List<ItemView>> byIssueType = new TreeMap<>();
        for (ItemView item : items) {
            if (!item.isManual()) {
                byIssueType.computeIfAbsent(item.issueType(), k -> new ArrayList<>()).add(item);
            }
        }
        byIssueType.forEach((issueType, groupItems) -> result.add(new GroupView(issueType, List.copyOf(groupItems))));

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

    private static ItemView mapTicketItem(ReleaseItem item) {
        TicketDetail ticket = item.ticket();
        List<CommentView> comments = ticket.comments().stream().map(CommentView::new).toList();
        // commits/pullRequests are always empty until Phase 6 populates them (D-04-18).
        return new ItemView(
                nullToEmpty(ticket.summary()),
                false,
                nullToEmpty(ticket.key()),
                nullToEmpty(ticket.issueType()),
                nullToEmpty(ticket.status()),
                nullToEmpty(ticket.resolution()),
                nullToEmpty(ticket.assignee()),
                nullToEmpty(ticket.reporter()),
                nullToEmpty(ticket.description()),
                "",
                comments,
                List.of(),
                List.of());
    }

    private static ItemView mapManualItem(ManualItem item) {
        return new ItemView(
                nullToEmpty(item.title()),
                true,
                "", "", "", "", "", "",
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
