package com.evision.releasemgmt.domain.template.view;

import java.util.List;

// TMPL-04's save-time validation context. Deterministic and shared by every caller so save-time
// validation always sees the same fixture: a ticket with no linked code (D-04-18), a ticket with a
// blank resolution, a manual item, at least one comment, and at least two groups.
public final class SampleReleaseView {

    private static final ItemView TICKET_NO_LINKED_CODE = new ItemView(
            "Fix duplicate invoice lines", false, "CNV-2896", "Bug", "Done", "Fixed",
            "Dev Person", "Reporter Person", "Matching Module",
            "Invoice lines were duplicated when a shipment was split.", "",
            List.of(new CommentView("Verified on staging.")), List.of(), List.of());

    private static final ItemView TICKET_UNRESOLVED = new ItemView(
            "Add export button", false, "CNV-2901", "Story", "Done", "",
            "", "Reporter Person", "", "Adds a CSV export.", "",
            List.of(), List.of(), List.of());

    private static final ItemView MANUAL_ITEM = new ItemView(
            "Run migration V4", true, "", "", "", "", "", "", "",
            "Apply the template schema migration before deploy.", "Database script",
            List.of(), List.of(), List.of());

    private static final ReleaseView SAMPLE = new ReleaseView(
            "Sample Release", "2026.08", "OPEN", "CNV",
            "2026-08-01T09:00:00Z", "", "2026-08-05T14:30:00Z",
            List.of(TICKET_NO_LINKED_CODE, TICKET_UNRESOLVED, MANUAL_ITEM),
            List.of(new GroupView("Bug", List.of(TICKET_NO_LINKED_CODE)),
                    new GroupView("Story", List.of(TICKET_UNRESOLVED)),
                    new GroupView("Database script", List.of(MANUAL_ITEM))));

    private SampleReleaseView() {
    }

    public static ReleaseView instance() {
        return SAMPLE;
    }
}
