package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.AssistantChatEvents.SourceView;
import com.evision.releasemgmt.adapters.web.dto.AssistantChatRequest;
import com.evision.releasemgmt.domain.assistant.FailureReason;
import com.evision.releasemgmt.domain.assistant.Source;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantSourceViewsTest {

    private static final String ID = UUID.randomUUID().toString();

    private static Source passage(String collection, Map<String, Object> metadata) {
        return new Source(1, collection, "doc", "Title", null, "PASSAGE", metadata);
    }

    private static Source tool(String documentId) {
        return new Source(2, null, documentId, "Tool", null, "TOOL", Map.of());
    }

    @Test
    void ticketPassageCarriesReleaseAndKey() {
        SourceView view = AssistantSourceViews.of(
                passage("rm-tickets", Map.of("release_id", ID, "ticket_key", "PROJ-123")));

        assertThat(view.kind()).isEqualTo("TICKET");
        assertThat(view.releaseId()).isEqualTo(ID);
        assertThat(view.ticketKey()).isEqualTo("PROJ-123");
    }

    @Test
    void documentPassageCarriesTypeAndRevision() {
        Map<String, Object> meta = Map.of("release_id", ID, "release_version", "4.2.0",
                "doc_type", "RELEASE_NOTES", "revision", 3);

        SourceView view = AssistantSourceViews.of(passage("rm-documents", meta));

        assertThat(view.kind()).isEqualTo("DOCUMENT");
        assertThat(view.releaseVersion()).isEqualTo("4.2.0");
        assertThat(view.documentType()).isEqualTo("RELEASE_NOTES");
        assertThat(view.revision()).isEqualTo(3);
    }

    @Test
    void revisionGivenAsStringIsParsed() {
        SourceView view = AssistantSourceViews.of(passage("rm-documents", Map.of("revision", "3")));

        assertThat(view.revision()).isEqualTo(3);
    }

    @Test
    void manualItemAndCodeLinks() {
        SourceView manual = AssistantSourceViews.of(passage("rm-manual-items", Map.of("release_id", ID)));
        SourceView code = AssistantSourceViews.of(
                passage("rm-code-links", Map.of("release_id", ID, "ticket_key", "A_B-1")));

        assertThat(manual.kind()).isEqualTo("MANUAL_ITEM");
        assertThat(manual.releaseId()).isEqualTo(ID);
        assertThat(manual.title()).isEqualTo("Title");
        assertThat(code.kind()).isEqualTo("CODE_LINKS");
        assertThat(code.ticketKey()).isEqualTo("A_B-1");
    }

    @Test
    void toolSourcesParseIdsFromTheSourceId() {
        SourceView byRelease = AssistantSourceViews.of(tool("deployments:" + ID));
        SourceView byTicket = AssistantSourceViews.of(tool("tickets:PROJ-9"));
        SourceView recent = AssistantSourceViews.of(tool("deployments:recent"));

        assertThat(byRelease.kind()).isEqualTo("TOOL");
        assertThat(byRelease.releaseId()).isEqualTo(ID);
        assertThat(byRelease.ticketKey()).isNull();
        assertThat(byTicket.ticketKey()).isEqualTo("PROJ-9");
        assertThat(byTicket.releaseId()).isNull();
        assertThat(recent.releaseId()).isNull();
        assertThat(recent.ticketKey()).isNull();
    }

    @Test
    void invalidFieldsAreDroppedAndTheRestKept() {
        Map<String, Object> meta = Map.of("release_id", "not-a-uuid", "ticket_key", "<script>",
                "doc_type", "OTHER", "revision", 0, "release_version", "1.0");

        SourceView view = AssistantSourceViews.of(passage("rm-documents", meta));

        assertThat(view.releaseId()).isNull();
        assertThat(view.ticketKey()).isNull();
        assertThat(view.documentType()).isNull();
        assertThat(view.revision()).isNull();
        assertThat(view.releaseVersion()).isEqualTo("1.0");
        assertThat(AssistantSourceViews.of(passage("rm-documents", Map.of("revision", "x"))).revision()).isNull();
    }

    @Test
    void unknownCollectionFallsBackWithoutIds() {
        SourceView view = AssistantSourceViews.of(passage("rm-other", Map.of("release_id", ID)));

        assertThat(view.kind()).isEqualTo("DOCUMENT");
        assertThat(view.releaseId()).isNull();
    }

    @Test
    void longFieldsAreTruncated() {
        Source source = new Source(1, "rm-documents", "d", "t".repeat(400), null, "PASSAGE",
                Map.of("release_version", "v".repeat(150)));

        SourceView view = AssistantSourceViews.of(source);

        assertThat(view.title()).hasSize(300);
        assertThat(view.releaseVersion()).hasSize(100);
    }

    @Test
    void errorOutcomesMapRejectedAndFailedToFailed() {
        assertThat(AssistantSourceViews.ofError(FailureReason.REJECTED)).isEqualTo("FAILED");
        assertThat(AssistantSourceViews.ofError(FailureReason.FAILED)).isEqualTo("FAILED");
        assertThat(AssistantSourceViews.ofError(FailureReason.BUSY)).isEqualTo("BUSY");
        assertThat(AssistantSourceViews.ofError(FailureReason.DEADLINE)).isEqualTo("DEADLINE");
    }

    @Test
    void requestToStringHidesContent() {
        AssistantChatRequest request = new AssistantChatRequest("SECRET-QUESTION",
                List.of(new AssistantChatRequest.HistoryMessage("user", "SECRET-HISTORY")), null);

        assertThat(request.toString()).doesNotContain("SECRET");
        assertThat(request.history().get(0).toString()).doesNotContain("SECRET").contains("user");
    }
}
