package com.evision.releasemgmt.application.assistant;

import com.evision.releasemgmt.domain.assistant.IndexCollection;
import com.evision.releasemgmt.domain.assistant.IndexDocument;
import com.evision.releasemgmt.domain.document.AiMode;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.enrichment.LinkedCommit;
import com.evision.releasemgmt.domain.enrichment.LinkedPullRequest;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class IndexDocumentMapperTest {

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,200}");
    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");
    private static final UUID RELEASE_ID = UUID.randomUUID();
    private static final UUID MANUAL_ID = UUID.randomUUID();

    @Test
    void mapsEverySourceToItsStableId() {
        List<IndexDocument> documents = IndexDocumentMapper.map(release("PAY"), List.of(snapshot(3, "# Notes")));

        assertThat(documents).extracting(IndexDocument::id).containsExactlyInAnyOrder(
                "ticket:" + RELEASE_ID + ":PAY-1", "codelinks:" + RELEASE_ID + ":PAY-1",
                "ticket:" + RELEASE_ID + ":PAY-2", "manual:" + MANUAL_ID,
                "document:" + RELEASE_ID + ":RELEASE_NOTES");
        assertThat(documents).allSatisfy(d -> assertThat(d.id()).matches(ID_PATTERN));
    }

    @Test
    void ticketMetadataIsFlatAndOmitsNulls() {
        IndexDocument ticket = byId(IndexDocumentMapper.map(release("PAY"), List.of()), "ticket:" + RELEASE_ID + ":PAY-1");

        assertThat(ticket.collection()).isEqualTo(IndexCollection.TICKETS);
        assertThat(ticket.metadata()).containsEntry("release_id", RELEASE_ID.toString())
                .containsEntry("release_name", "Spring").containsEntry("release_version", "1.0")
                .containsEntry("release_status", "OPEN").containsEntry("project_key", "PAY")
                .containsEntry("ticket_key", "PAY-1").containsEntry("ticket_status", "Done")
                .containsEntry("issue_type", "Bug");
        assertThat(ticket.metadata().values()).doesNotContainNull();
        assertThat(ticket.title()).isEqualTo("PAY-1 Fix payment");
    }

    @Test
    void ticketTextHasFieldsAndEveryComment() {
        IndexDocument ticket = byId(IndexDocumentMapper.map(release("PAY"), List.of()), "ticket:" + RELEASE_ID + ":PAY-1");

        assertThat(ticket.text()).contains("Type: Bug", "Status: Done", "Assignee: Ann", "Story points: 5",
                "Description:\nBroken", "Comments (showing 2 of 3)", "- first", "- second");
    }

    @Test
    void manualItemAndDocumentCarryReleaseProjectKeyWhenKnown() {
        List<IndexDocument> with = IndexDocumentMapper.map(release("PAY"), List.of(snapshot(3, "# Notes")));
        List<IndexDocument> without = IndexDocumentMapper.map(release(null), List.of(snapshot(3, "# Notes")));

        IndexDocument manual = byId(with, "manual:" + MANUAL_ID);
        assertThat(manual.metadata()).containsEntry("project_key", "PAY").containsEntry("category", "OTHER");
        assertThat(manual.text()).contains("Title: Run script", "Category: OTHER", "Do it");
        assertThat(byId(without, "manual:" + MANUAL_ID).metadata()).doesNotContainKey("project_key");
        assertThat(byId(without, "document:" + RELEASE_ID + ":RELEASE_NOTES").metadata())
                .doesNotContainKey("project_key");
    }

    @Test
    void documentCarriesRevisionAndMarkdownOnly() {
        IndexDocument document = byId(IndexDocumentMapper.map(release("PAY"), List.of(snapshot(2, "old"),
                snapshot(3, "# Notes"))), "document:" + RELEASE_ID + ":RELEASE_NOTES");

        assertThat(document.collection()).isEqualTo(IndexCollection.DOCUMENTS);
        assertThat(document.text()).isEqualTo("# Notes");
        assertThat(document.title()).isEqualTo("Release notes - Spring 1.0 (revision 3)");
        assertThat(document.metadata()).containsEntry("doc_type", "RELEASE_NOTES").containsEntry("revision", 3)
                .containsEntry("generated_at", NOW.toString());
    }

    // D-14-01/D-12-11: an AI revision is just the latest revision; no ai_* metadata is added.
    @Test
    void latestAiRevisionIsIndexedAsTheCurrentDocumentWithoutAiMetadata() {
        DocumentSnapshot generated = snapshot(3, "# Generated");
        DocumentSnapshot ai = DocumentSnapshot.createAiRevision(generated, 4, "# AI text", AiMode.CLEAN_UP,
                "instr", "u", NOW.plusSeconds(60));

        List<IndexDocument> documents = IndexDocumentMapper.map(release("PAY"), List.of(generated, ai));

        List<IndexDocument> indexed = documents.stream()
                .filter(d -> d.collection() == IndexCollection.DOCUMENTS).toList();
        assertThat(indexed).hasSize(1);
        IndexDocument document = indexed.get(0);
        assertThat(document.id()).isEqualTo("document:" + RELEASE_ID + ":RELEASE_NOTES");
        assertThat(document.text()).isEqualTo("# AI text");
        assertThat(document.metadata()).containsEntry("revision", 4);
        assertThat(document.metadata().keySet()).noneMatch(key -> key.startsWith("ai_"));
    }

    @Test
    void codeLinksListPullRequestsAndCommitsAndSkipTicketsWithoutThem() {
        List<IndexDocument> documents = IndexDocumentMapper.map(release("PAY"), List.of());

        IndexDocument links = byId(documents, "codelinks:" + RELEASE_ID + ":PAY-1");
        assertThat(links.collection()).isEqualTo(IndexCollection.CODE_LINKS);
        assertThat(links.text()).contains("Add retry (completed), feature/x -> main", "abcdef1 Bob: Fix retry");
        assertThat(documents).extracting(IndexDocument::id).doesNotContain("codelinks:" + RELEASE_ID + ":PAY-2");
    }

    @Test
    void nulCharactersAreRemovedAndMappingIsDeterministic() {
        Release release = release("PAY");
        TicketDetail dirty = ticket("PAY-9", "Sum\u0000mary", "Bro\u0000ken", List.of());
        Release withDirty = new Release(release.id(), release.name(), release.version(), release.status(),
                release.jiraProjectKey(), NOW, "u", null, null, null, null,
                List.of(new ReleaseItem(dirty, NOW, null)), List.of(), null);

        List<IndexDocument> first = IndexDocumentMapper.map(withDirty, List.of());

        assertThat(first.get(0).title()).doesNotContain("\u0000");
        assertThat(first.get(0).text()).doesNotContain("\u0000");
        assertThat(IndexDocumentMapper.map(withDirty, List.of())).isEqualTo(first);
    }

    @Test
    void packagesLinkIsIndexedAsAReleaseDocumentOnlyWhenSet() {
        Release base = release("PAY");
        String url = "https://contoso.sharepoint.com/x";
        Release withLink = new Release(base.id(), base.name(), base.version(), base.status(), base.jiraProjectKey(),
                NOW, "u", null, null, null, null, base.items(), base.manualItems(), url);

        IndexDocument document = byId(IndexDocumentMapper.map(withLink, List.of()), "release:" + RELEASE_ID);

        assertThat(document.collection()).isEqualTo(IndexCollection.DOCUMENTS);
        assertThat(document.text()).contains("Packages: " + url);
        assertThat(IndexDocumentMapper.map(base, List.of())).extracting(IndexDocument::id)
                .noneMatch(id -> id.startsWith("release:"));
    }

    private static IndexDocument byId(List<IndexDocument> documents, String id) {
        return documents.stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
    }

    private static Release release(String projectKey) {
        CodeLinks links = new CodeLinks(EnrichmentStatus.LINKED, null, false, NOW, null,
                List.of(new LinkedPullRequest("r", "7", "Add retry", "completed", null, "feature/x", "main", true, NOW)),
                List.of(new LinkedCommit("abcdef123456", "Fix retry\nbody", "Bob", NOW, null)), List.of(), null);
        return new Release(RELEASE_ID, "Spring", "1.0", ReleaseStatus.OPEN, projectKey, NOW, "u", null, null, null, null,
                List.of(new ReleaseItem(ticket("PAY-1", "Fix payment", "Broken", List.of("first", "second")), NOW, links),
                        new ReleaseItem(ticket("PAY-2", "Other", null, List.of()), NOW, CodeLinks.notAttempted())),
                List.of(new ManualItem(MANUAL_ID, "Run script", "Do it", ManualItemCategory.OTHER, NOW)), null);
    }

    private static TicketDetail ticket(String key, String summary, String description, List<String> comments) {
        return new TicketDetail(key, summary, "Bug", "Done", null, "Ann", null, List.of(), null, description, comments,
                comments.isEmpty() ? 0 : 3, "1", NOW, StatusCategory.DONE, new BigDecimal("5.0"), true);
    }

    private static DocumentSnapshot snapshot(int revision, String markdown) {
        return new DocumentSnapshot(UUID.randomUUID(), RELEASE_ID, TemplateDocumentType.RELEASE_NOTES, revision,
                markdown, null, null, null, null, NOW, "u", null);
    }
}
