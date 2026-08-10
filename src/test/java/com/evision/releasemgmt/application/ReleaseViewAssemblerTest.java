package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
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
            List.of(), null, "Bug description", List.of(), 0);

    private static final TicketDetail STORY_TICKET = new TicketDetail(
            "ABC-1", "Story summary", "Story", "Done", "Fixed", "Dev Person", "Reporter Person",
            List.of(), null, "Story description", List.of(), 0);

    @Test
    void producesThreeItemsWithManualLast() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET, STORY_TICKET),
                List.of(manualItem())));

        assertThat(view.items()).hasSize(3);
        assertThat(view.items().get(2).isManual()).isTrue();
    }

    @Test
    void manualItemHasCategoryLabelAndBlankTicketOnlyFields() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(), List.of(manualItem())));

        ItemView manual = view.items().get(0);
        assertThat(manual.isManual()).isTrue();
        assertThat(manual.category()).isEqualTo("Database script");
        assertThat(manual.key()).isEmpty();
        assertThat(manual.issueType()).isEmpty();
        assertThat(manual.status()).isEmpty();
        assertThat(manual.resolution()).isEmpty();
        assertThat(manual.assignee()).isEmpty();
        assertThat(manual.reporter()).isEmpty();
    }

    @Test
    void ticketItemHasEmptyCategoryAndNoLinkedCode() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET), List.of()));

        ItemView ticketItem = view.items().get(0);
        assertThat(ticketItem.category()).isEmpty();
        assertThat(ticketItem.commits()).isEmpty();
        assertThat(ticketItem.pullRequests()).isEmpty();
    }

    @Test
    void groupsAppearBugThenStoryThenDatabaseScript() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET, STORY_TICKET),
                List.of(manualItem())));

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
                Instant.parse("2026-01-02T00:00:00Z"), "actor", null, List.of(), List.of());
        ReleaseView frozenView = assembler.assemble(frozen);
        assertThat(frozenView.status()).isEqualTo("FROZEN");
        assertThat(frozenView.frozenAt()).isEqualTo("2026-01-02T00:00:00Z");

        Release open = new Release(UUID.randomUUID(), "R", "1.0", ReleaseStatus.OPEN, "PRJ",
                Instant.parse("2026-01-01T00:00:00Z"), "actor", null, null, null, List.of(), List.of());
        ReleaseView openView = assembler.assemble(open);
        assertThat(openView.frozenAt()).isEmpty();
    }

    @Test
    void nullResolutionAndAssigneeRenderAsEmptyStringNeverNull() {
        ReleaseView view = assembler.assemble(releaseWith(List.of(BUG_TICKET), List.of()));

        ItemView item = view.items().get(0);
        assertThat(item.resolution()).isEmpty();
        assertThat(item.assignee()).isEmpty();
    }

    private static ManualItem manualItem() {
        return new ManualItem(UUID.randomUUID(), "Run migration V4", "Apply schema migration",
                ManualItemCategory.DATABASE_SCRIPT, Instant.now());
    }

    private static Release releaseWith(List<TicketDetail> tickets, List<ManualItem> manualItems) {
        Instant now = Instant.now();
        List<ReleaseItem> items = tickets.stream().map(ticket -> new ReleaseItem(ticket, now)).toList();
        return new Release(UUID.randomUUID(), "R", "1.0", ReleaseStatus.OPEN, "PRJ",
                now, "actor", null, null, null, items, manualItems);
    }
}
