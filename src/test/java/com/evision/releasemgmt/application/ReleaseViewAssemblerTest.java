package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.enrichment.CommitsSource;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.enrichment.LinkedCommit;
import com.evision.releasemgmt.domain.enrichment.LinkedPullRequest;
import com.evision.releasemgmt.domain.enrichment.StatusHistory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.template.GroupingDimension;
import com.evision.releasemgmt.domain.template.view.CommitView;
import com.evision.releasemgmt.domain.template.view.GroupView;
import com.evision.releasemgmt.domain.template.view.ProductView;
import com.evision.releasemgmt.domain.template.view.PullRequestView;
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
            List.of(), null, "Bug description", List.of(), 0, null, null, null, null, false);

    private static final TicketDetail STORY_TICKET = new TicketDetail(
            "ABC-1", "Story summary", "Story", "Done", "Fixed", "Dev Person", "Reporter Person",
            List.of(), null, "Story description", List.of(), 0, null, null, null, null, false);

    @Test
    void producesThreeItemsWithManualLast() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET, STORY_TICKET),
                List.of(manualItem())), List.of(), GroupingDimension.ISSUE_TYPE);

        assertThat(view.items()).hasSize(3);
        assertThat(view.items().get(2).isManual()).isTrue();
    }

    @Test
    void manualItemHasCategoryLabelAndBlankTicketOnlyFields() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(), List.of(manualItem())),
                List.of(), GroupingDimension.ISSUE_TYPE);

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
                List.of(), GroupingDimension.ISSUE_TYPE);

        ItemView ticketItem = view.items().get(0);
        assertThat(ticketItem.category()).isEmpty();
        assertThat(ticketItem.commits()).isEmpty();
        assertThat(ticketItem.pullRequests()).isEmpty();
    }

    @Test
    void groupsAppearBugThenStoryThenDatabaseScriptWhenGroupedByIssueType() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET, STORY_TICKET),
                List.of(manualItem())), List.of(), GroupingDimension.ISSUE_TYPE);

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
                Instant.parse("2026-01-02T00:00:00Z"), "actor", null, null, List.of(), List.of(), null);
        ReleaseView frozenView = assembler.assemble(frozen, List.of(), GroupingDimension.ISSUE_TYPE);
        assertThat(frozenView.status()).isEqualTo("FROZEN");
        assertThat(frozenView.frozenAt()).isEqualTo("2026-01-02T00:00:00Z");

        Release open = new Release(UUID.randomUUID(), "R", "1.0", ReleaseStatus.OPEN, "PRJ",
                Instant.parse("2026-01-01T00:00:00Z"), "actor", null, null, null, null, List.of(), List.of(), null);
        ReleaseView openView = assembler.assemble(open, List.of(), GroupingDimension.ISSUE_TYPE);
        assertThat(openView.frozenAt()).isEmpty();
    }

    @Test
    void nullResolutionAndAssigneeRenderAsEmptyStringNeverNull() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET), List.of()),
                List.of(), GroupingDimension.ISSUE_TYPE);

        ItemView item = view.items().get(0);
        assertThat(item.resolution()).isEmpty();
        assertThat(item.assignee()).isEmpty();
    }

    // D-05-04: the Phase 4 compatibility guarantee - grouping by ISSUE_TYPE must produce byte-identical
    // group names and membership to the pre-change single-argument assemble, for the same release.
    @Test
    void issueTypeDimensionProducesTheSameGroupsAsBeforeTheDimensionWasAdded() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET, STORY_TICKET), List.of()),
                List.of(), GroupingDimension.ISSUE_TYPE);

        assertThat(view.groups()).extracting(GroupView::groupName).containsExactly("Bug", "Story");
    }

    @Test
    void componentDimensionGroupsTicketItemsByComponentAlphabetically() {
        TicketDetail zebraComponentTicket = new TicketDetail(
                "ABC-3", "Zebra summary", "Task", "Open", null, null, "Reporter Person",
                List.of(), "Zebra Module", "Zebra description", List.of(), 0, null, null, null, null, false);
        TicketDetail matchingComponentTicket = new TicketDetail(
                "ABC-4", "Matching summary", "Bug", "Open", null, null, "Reporter Person",
                List.of(), "Matching Module", "Matching description", List.of(), 0, null, null, null, null, false);

        ReleaseView view = assembler.assemble(
                releaseWith(List.of(zebraComponentTicket, matchingComponentTicket), List.of()),
                List.of(), GroupingDimension.COMPONENT);

        assertThat(view.groups()).extracting(GroupView::groupName)
                .containsExactly("Matching Module", "Zebra Module");
    }

    @Test
    void ticketWithNullOrBlankComponentGroupsUnderUncategorisedWhenDimensionIsComponent() {
        TicketDetail blankComponentTicket = new TicketDetail(
                "ABC-5", "Blank summary", "Task", "Open", null, null, "Reporter Person",
                List.of(), "", "Blank description", List.of(), 0, null, null, null, null, false);

        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET, blankComponentTicket), List.of()),
                List.of(), GroupingDimension.COMPONENT);

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
                List.of(), "", "Blank description", List.of(), 0, null, null, null, null, false);
        TicketDetail aardvarkComponentTicket = new TicketDetail(
                "ABC-7", "Aardvark summary", "Bug", "Open", null, null, "Reporter Person",
                List.of(), "Aardvark Module", "Aardvark description", List.of(), 0, null, null, null, null, false);

        ReleaseView view = assembler.assemble(
                releaseWith(List.of(blankComponentTicket, aardvarkComponentTicket), List.of()),
                List.of(), GroupingDimension.COMPONENT);

        // "Aardvark Module" < "Uncategorised" alphabetically ('A' < 'U'), so it must lead.
        assertThat(view.groups()).extracting(GroupView::groupName)
                .containsExactly("Aardvark Module", "Uncategorised");
    }

    @Test
    void manualItemsGroupByCategoryUnderBothDimensions() {
        Release release = releaseWith(List.of(), List.of(manualItem()));

        ReleaseView issueTypeView = assembler.assemble(release, List.of(), GroupingDimension.ISSUE_TYPE);
        ReleaseView componentView = assembler.assemble(release, List.of(), GroupingDimension.COMPONENT);

        assertThat(issueTypeView.groups()).extracting(GroupView::groupName).containsExactly("Database script");
        assertThat(componentView.groups()).extracting(GroupView::groupName).containsExactly("Database script");
    }

    @Test
    void itemViewComponentIsEmptyForManualItemsAndForTicketsWithNoComponent() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET), List.of(manualItem())),
                List.of(), GroupingDimension.ISSUE_TYPE);

        ItemView ticketItem = view.items().stream().filter(i -> !i.isManual()).findFirst().orElseThrow();
        ItemView manualItem = view.items().stream().filter(ItemView::isManual).findFirst().orElseThrow();
        assertThat(ticketItem.component()).isEmpty();
        assertThat(manualItem.component()).isEmpty();
    }

    @Test
    void linkedItemListsCommitsByAuthoredAtAndOnlyOwnPullRequests() {
        CodeLinks links = codeLinks(EnrichmentStatus.LINKED,
                List.of(commit("bbbbbbb2222", "second", "Dev", "2026-01-02T00:00:00Z"),
                        commit("aaaaaaa1111", "first", null, "2026-01-01T00:00:00Z")),
                List.of(pullRequest("1001", false), pullRequest("1003", true), pullRequest("1002", true)));

        ItemView item = assembler.assemble(releaseWith(links), List.of(), GroupingDimension.ISSUE_TYPE).items().get(0);

        assertThat(item.commits()).extracting(CommitView::id).containsExactly("aaaaaaa", "bbbbbbb");
        assertThat(item.commits().get(0).author()).isEmpty();
        assertThat(item.pullRequests()).extracting(PullRequestView::id).containsExactly("1002", "1003");
    }

    @Test
    void noLinkFailedAndNotAttemptedItemsRenderAsNoLinkedCode() {
        for (EnrichmentStatus status : List.of(EnrichmentStatus.NO_LINK, EnrichmentStatus.FAILED,
                EnrichmentStatus.NOT_ATTEMPTED)) {
            CodeLinks links = codeLinks(status, List.of(commit("aaaaaaa1111", "kept rows", "Dev", "2026-01-01T00:00:00Z")),
                    List.of(pullRequest("1002", true)));

            ItemView item = assembler.assemble(releaseWith(links), List.of(), GroupingDimension.ISSUE_TYPE).items().get(0);

            assertThat(item.commits()).as(status.name()).isEmpty();
            assertThat(item.pullRequests()).as(status.name()).isEmpty();
        }
    }

    private static CodeLinks codeLinks(EnrichmentStatus status, List<LinkedCommit> commits,
                                       List<LinkedPullRequest> pullRequests) {
        return new CodeLinks(status, null, false, Instant.now(), CommitsSource.ADO_COMPLETE, pullRequests, commits,
                List.of(), StatusHistory.notRead());
    }

    private static LinkedCommit commit(String sha, String message, String author, String authoredAt) {
        return new LinkedCommit(sha, message, author, Instant.parse(authoredAt), "https://ado/commit/" + sha);
    }

    private static LinkedPullRequest pullRequest(String id, boolean countsTowardCommits) {
        return new LinkedPullRequest("repo", id, "PR " + id, "MERGED", "https://ado/pr/" + id, "feature/x", "main",
                countsTowardCommits, null);
    }

    private static Release releaseWith(CodeLinks links) {
        Instant now = Instant.now();
        return new Release(UUID.randomUUID(), "R", "1.0", ReleaseStatus.OPEN, "PRJ",
                now, "actor", null, null, null, null, List.of(new ReleaseItem(BUG_TICKET, now, links)), List.of(), null);
    }

    private static ManualItem manualItem() {
        return new ManualItem(UUID.randomUUID(), "Run migration V4", "Apply schema migration",
                ManualItemCategory.DATABASE_SCRIPT, Instant.now());
    }

    private static Release releaseWith(List<TicketDetail> tickets, List<ManualItem> manualItems) {
        Instant now = Instant.now();
        List<ReleaseItem> items = tickets.stream().map(ticket -> new ReleaseItem(ticket, now, CodeLinks.notAttempted())).toList();
        return new Release(UUID.randomUUID(), "R", "1.0", ReleaseStatus.OPEN, "PRJ",
                now, "actor", null, null, null, null, items, manualItems, null);
    }

    // D-17.2-07: bundles first (case-insensitive), then "Other" for ungrouped tickets, then manual categories.
    @Test
    void bundleDimensionGroupsByBundleThenOtherThenManualCategories() {
        Release release = releaseWithBundles(List.of(
                bundled(ticket("ABC-1"), "Payments fixes"),
                bundled(ticket("ABC-2"), "audit"),
                bundled(ticket("ABC-3"), null),
                bundled(ticket("ABC-4"), "Payments fixes")),
                List.of(new ManualItem(UUID.randomUUID(), "Flip flag", "", ManualItemCategory.CONFIG_CHANGE,
                        Instant.now())));

        ReleaseView view = assembler.assemble(release, List.of(), GroupingDimension.BUNDLE);

        assertThat(view.groups()).extracting(GroupView::groupName)
                .containsExactly("audit", "Payments fixes", "Other", "Config change");
        assertThat(view.groups().get(0).items()).extracting(ItemView::key).containsExactly("ABC-2");
        assertThat(view.groups().get(1).items()).extracting(ItemView::key).containsExactly("ABC-1", "ABC-4");
        assertThat(view.groups().get(2).items()).extracting(ItemView::key).containsExactly("ABC-3");
        assertThat(view.groups().get(3).items()).extracting(ItemView::isManual).containsExactly(true);
    }

    @Test
    void bundleDimensionWithNoBundlesPutsEveryTicketUnderOther() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET, STORY_TICKET), List.of(manualItem())),
                List.of(), GroupingDimension.BUNDLE);

        assertThat(view.groups()).extracting(GroupView::groupName).containsExactly("Other", "Database script");
        assertThat(view.groups().get(0).items()).extracting(ItemView::key).containsExactly("ABC-1", "ABC-2");
    }

    @Test
    void bundleDimensionOmitsOtherWhenEveryTicketIsBundled() {
        Release release = releaseWithBundles(List.of(
                bundled(ticket("ABC-1"), "Alpha"), bundled(ticket("ABC-2"), "Beta")), List.of());

        ReleaseView view = assembler.assemble(release, List.of(), GroupingDimension.BUNDLE);

        assertThat(view.groups()).extracting(GroupView::groupName).containsExactly("Alpha", "Beta");
    }

    // A bundle literally named "Other" stays its own group rather than absorbing the ungrouped tickets.
    @Test
    void bundleNamedOtherIsNotMergedWithUngroupedTickets() {
        Release release = releaseWithBundles(List.of(
                bundled(ticket("ABC-1"), "Other"), bundled(ticket("ABC-2"), null)), List.of());

        ReleaseView view = assembler.assemble(release, List.of(), GroupingDimension.BUNDLE);

        assertThat(view.groups()).extracting(GroupView::groupName).containsExactly("Other", "Other");
        assertThat(view.groups().get(0).items()).extracting(ItemView::key).containsExactly("ABC-1");
        assertThat(view.groups().get(1).items()).extracting(ItemView::key).containsExactly("ABC-2");
    }

    private static TicketDetail ticket(String key) {
        return new TicketDetail(key, key + " summary", "Task", "Open", null, null, "Reporter Person",
                List.of(), null, "", List.of(), 0, null, null, null, null, false);
    }

    private static ReleaseItem bundled(TicketDetail ticket, String bundle) {
        return new ReleaseItem(ticket, Instant.now(), CodeLinks.notAttempted(), bundle);
    }

    private static Release releaseWithBundles(List<ReleaseItem> items, List<ManualItem> manualItems) {
        return new Release(UUID.randomUUID(), "R", "1.0", ReleaseStatus.OPEN, "PRJ",
                Instant.now(), "actor", null, null, null, null, items, manualItems, null);
    }

    @Test
    void productNamesAreMappedInTheOrderGiven() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET), List.of()),
                List.of("Billing", "core"), GroupingDimension.ISSUE_TYPE);

        assertThat(view.products())
                .containsExactly(new ProductView("Billing"), new ProductView("core"));
    }
}
