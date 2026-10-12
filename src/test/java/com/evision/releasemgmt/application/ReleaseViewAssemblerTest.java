package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.template.GroupingDimension;
import com.evision.releasemgmt.domain.template.view.GroupView;
import com.evision.releasemgmt.domain.template.view.ItemView;
import com.evision.releasemgmt.domain.template.view.ReleaseView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseViewAssemblerTest {

    private final ReleaseViewAssembler assembler = new ReleaseViewAssembler();

    private static final TicketDetail BUG_TICKET = new TicketDetail(
            "ABC-2", "Bug summary", "Bug", "Open", null, null, "Reporter Person",
            List.of(), null, "Bug description", List.of(), 0, null, null, null);

    private static final TicketDetail STORY_TICKET = new TicketDetail(
            "ABC-1", "Story summary", "Story", "Done", "Fixed", "Dev Person", "Reporter Person",
            List.of(), null, "Story description", List.of(), 0, null, null, null);

    @Test
    void producesThreeItemsWithManualLast() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET, STORY_TICKET),
                List.of(manualItem())), GroupingDimension.ISSUE_TYPE);

        assertThat(view.items()).hasSize(3);
        assertThat(view.items().get(2).isManual()).isTrue();
    }

    @Test
    void manualItemHasCategoryLabelAndBlankTicketOnlyFields() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(), List.of(manualItem())),
                GroupingDimension.ISSUE_TYPE);

        ItemView manual = view.items().get(0);
        assertThat(manual.isManual()).isTrue();
        assertThat(manual.category()).isEqualTo("Database script");
        assertThat(manual.key()).isEmpty();
        assertThat(manual.issueType()).isEmpty();
        assertThat(manual.status()).isEmpty();
        assertThat(manual.resolution()).isEmpty();
        assertThat(manual.assignee()).isEmpty();
        assertThat(manual.reporter()).isEmpty();
        assertThat(manual.component()).isEmpty();
    }

    @Test
    void ticketItemHasEmptyCategoryAndNoLinkedCode() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET), List.of()),
                GroupingDimension.ISSUE_TYPE);

        ItemView ticketItem = view.items().get(0);
        assertThat(ticketItem.category()).isEmpty();
        assertThat(ticketItem.commits()).isEmpty();
        assertThat(ticketItem.pullRequests()).isEmpty();
    }

    @Test
    void groupsAppearBugThenStoryThenDatabaseScriptWhenGroupedByIssueType() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET, STORY_TICKET),
                List.of(manualItem())), GroupingDimension.ISSUE_TYPE);

        assertThat(view.groups()).extracting(GroupView::groupName)
                .containsExactly("Bug", "Story", "Database script");
        assertThat(view.groups().get(0).items()).extracting(ItemView::key).containsExactly("ABC-2");
        assertThat(view.groups().get(1).items()).extracting(ItemView::key).containsExactly("ABC-1");
        assertThat(view.groups().get(2).items()).extracting(ItemView::isManual).containsExactly(true);
    }

    @Test
    void frozenReleaseRendersStatusAndFrozenAtOpenReleaseRendersEmptyFrozenAt() {
        Release frozen = new Release(UUID.randomUUID(), "R", "1.0", ReleaseStatus.FROZEN, "PRJ",
                Instant.parse("2026-01-01T00:00:00Z"), "actor",
                Instant.parse("2026-01-02T00:00:00Z"), "actor", null, null, List.of(), List.of());
        ReleaseView frozenView = assembler.assemble(frozen, GroupingDimension.ISSUE_TYPE);
        assertThat(frozenView.status()).isEqualTo("FROZEN");
        assertThat(frozenView.frozenAt()).isEqualTo("2026-01-02T00:00:00Z");

        Release open = new Release(UUID.randomUUID(), "R", "1.0", ReleaseStatus.OPEN, "PRJ",
                Instant.parse("2026-01-01T00:00:00Z"), "actor", null, null, null, null, List.of(), List.of());
        ReleaseView openView = assembler.assemble(open, GroupingDimension.ISSUE_TYPE);
        assertThat(openView.frozenAt()).isEmpty();
    }

    @Test
    void nullResolutionAndAssigneeRenderAsEmptyStringNeverNull() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET), List.of()),
                GroupingDimension.ISSUE_TYPE);

        ItemView item = view.items().get(0);
        assertThat(item.resolution()).isEmpty();
        assertThat(item.assignee()).isEmpty();
    }

    // D-05-04: the Phase 4 compatibility guarantee - grouping by ISSUE_TYPE must produce byte-identical
    // group names and membership to the pre-change single-argument assemble, for the same release.
    @Test
    void issueTypeDimensionProducesTheSameGroupsAsBeforeTheDimensionWasAdded() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET, STORY_TICKET), List.of()),
                GroupingDimension.ISSUE_TYPE);

        assertThat(view.groups()).extracting(GroupView::groupName).containsExactly("Bug", "Story");
    }

    @Test
    void componentDimensionGroupsTicketItemsByComponentAlphabetically() {
        TicketDetail zebraComponentTicket = new TicketDetail(
                "ABC-3", "Zebra summary", "Task", "Open", null, null, "Reporter Person",
                List.of(), "Zebra Module", "Zebra description", List.of(), 0, null, null, null);
        TicketDetail matchingComponentTicket = new TicketDetail(
                "ABC-4", "Matching summary", "Bug", "Open", null, null, "Reporter Person",
                List.of(), "Matching Module", "Matching description", List.of(), 0, null, null, null);

        ReleaseView view = assembler.assemble(
                releaseWith(List.of(zebraComponentTicket, matchingComponentTicket), List.of()),
                GroupingDimension.COMPONENT);

        assertThat(view.groups()).extracting(GroupView::groupName)
                .containsExactly("Matching Module", "Zebra Module");
    }

    @Test
    void ticketWithNullOrBlankComponentGroupsUnderUncategorisedWhenDimensionIsComponent() {
        TicketDetail blankComponentTicket = new TicketDetail(
                "ABC-5", "Blank summary", "Task", "Open", null, null, "Reporter Person",
                List.of(), "", "Blank description", List.of(), 0, null, null, null);

        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET, blankComponentTicket), List.of()),
                GroupingDimension.COMPONENT);

        assertThat(view.groups()).extracting(GroupView::groupName).containsExactly("Uncategorised");
        assertThat(view.groups().get(0).items()).extracting(ItemView::key)
                .containsExactlyInAnyOrder("ABC-2", "ABC-5");
    }

    // D-05-03: no special-casing to the end - "Uncategorised" sorts alphabetically among real
    // component names, purely on its own letters.
    @Test
    void uncategorisedSortsAlphabeticallyAmongOtherComponentNamesRatherThanBeingPinnedLast() {
        TicketDetail blankComponentTicket = new TicketDetail(
                "ABC-6", "Blank summary", "Task", "Open", null, null, "Reporter Person",
                List.of(), "", "Blank description", List.of(), 0, null, null, null);
        TicketDetail aardvarkComponentTicket = new TicketDetail(
                "ABC-7", "Aardvark summary", "Bug", "Open", null, null, "Reporter Person",
                List.of(), "Aardvark Module", "Aardvark description", List.of(), 0, null, null, null);

        ReleaseView view = assembler.assemble(
                releaseWith(List.of(blankComponentTicket, aardvarkComponentTicket), List.of()),
                GroupingDimension.COMPONENT);

        // "Aardvark Module" < "Uncategorised" alphabetically ('A' < 'U'), so it must lead.
        assertThat(view.groups()).extracting(GroupView::groupName)
                .containsExactly("Aardvark Module", "Uncategorised");
    }

    @Test
    void manualItemsGroupByCategoryUnderBothDimensions() {
        Release release = releaseWith(List.of(), List.of(manualItem()));

        ReleaseView issueTypeView = assembler.assemble(release, GroupingDimension.ISSUE_TYPE);
        ReleaseView componentView = assembler.assemble(release, GroupingDimension.COMPONENT);

        assertThat(issueTypeView.groups()).extracting(GroupView::groupName).containsExactly("Database script");
        assertThat(componentView.groups()).extracting(GroupView::groupName).containsExactly("Database script");
    }

    @Test
    void itemViewComponentIsEmptyForManualItemsAndForTicketsWithNoComponent() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET), List.of(manualItem())),
                GroupingDimension.ISSUE_TYPE);

        ItemView ticketItem = view.items().stream().filter(i -> !i.isManual()).findFirst().orElseThrow();
        ItemView manualItem = view.items().stream().filter(ItemView::isManual).findFirst().orElseThrow();
        assertThat(ticketItem.component()).isEmpty();
        assertThat(manualItem.component()).isEmpty();
    }

    private static ManualItem manualItem() {
        return new ManualItem(UUID.randomUUID(), "Run migration V4", "Apply schema migration",
                ManualItemCategory.DATABASE_SCRIPT, Instant.now());
    }

    private static Release releaseWith(List<TicketDetail> tickets, List<ManualItem> manualItems) {
        Instant now = Instant.now();
        List<ReleaseItem> items = tickets.stream().map(ticket -> new ReleaseItem(ticket, now, CodeLinks.notAttempted())).toList();
        return new Release(UUID.randomUUID(), "R", "1.0", ReleaseStatus.OPEN, "PRJ",
                now, "actor", null, null, null, null, items, manualItems);
    }
}
