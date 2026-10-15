package com.evision.releasemgmt.application.assistant;

import com.evision.releasemgmt.domain.assistant.IndexCollection;
import com.evision.releasemgmt.domain.assistant.IndexDocument;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.enrichment.LinkedCommit;
import com.evision.releasemgmt.domain.enrichment.LinkedPullRequest;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// Pure mapping from a release and its latest snapshots to the exact set of documents the index should hold.
public final class IndexDocumentMapper {

    private IndexDocumentMapper() {
    }

    public static List<IndexDocument> map(Release release, List<DocumentSnapshot> latestSnapshots) {
        List<IndexDocument> documents = new ArrayList<>();
        for (ReleaseItem item : release.items()) {
            documents.add(ticketDocument(release, item.ticket()));
            codeLinksDocument(release, item).ifPresent(documents::add);
        }
        for (ManualItem manual : release.manualItems()) {
            documents.add(manualDocument(release, manual));
        }
        latestPerType(latestSnapshots).forEach(snapshot -> documents.add(documentOf(release, snapshot)));
        return documents;
    }

    private static IndexDocument ticketDocument(Release release, TicketDetail ticket) {
        Map<String, Object> metadata = releaseMetadata(release);
        put(metadata, "project_key", projectKeyOf(ticket.key()));
        put(metadata, "ticket_key", ticket.key());
        put(metadata, "ticket_status", ticket.status());
        put(metadata, "issue_type", ticket.issueType());

        StringBuilder text = new StringBuilder();
        line(text, "Type", ticket.issueType());
        line(text, "Status", ticket.status());
        line(text, "Resolution", ticket.resolution());
        line(text, "Assignee", ticket.assignee());
        line(text, "Reporter", ticket.reporter());
        line(text, "Fix versions", ticket.fixVersions() == null ? null : String.join(", ", ticket.fixVersions()));
        line(text, "Component", ticket.component());
        line(text, "Story points", ticket.storyPointsRead() && ticket.storyPoints() != null
                ? ticket.storyPoints().stripTrailingZeros().toPlainString() : null);
        if (ticket.description() != null && !ticket.description().isBlank()) {
            text.append("\nDescription:\n").append(ticket.description()).append('\n');
        }
        List<String> comments = ticket.comments() == null ? List.of() : ticket.comments();
        if (!comments.isEmpty()) {
            text.append("\nComments (showing ").append(comments.size()).append(" of ")
                    .append(ticket.commentTotal()).append("):\n");
            comments.forEach(comment -> text.append("- ").append(comment).append('\n'));
        }
        return new IndexDocument(IndexCollection.TICKETS, "ticket:" + release.id() + ":" + ticket.key(),
                clean(ticket.key() + " " + ticket.summary()), clean(text.toString()), metadata);
    }

    private static Optional<IndexDocument> codeLinksDocument(Release release, ReleaseItem item) {
        CodeLinks links = item.codeLinks();
        if (links == null || (links.pullRequests().isEmpty() && links.commits().isEmpty())) {
            return Optional.empty();
        }
        TicketDetail ticket = item.ticket();
        Map<String, Object> metadata = releaseMetadata(release);
        put(metadata, "project_key", projectKeyOf(ticket.key()));
        put(metadata, "ticket_key", ticket.key());
        put(metadata, "ticket_status", ticket.status());
        put(metadata, "issue_type", ticket.issueType());

        StringBuilder text = new StringBuilder();
        if (!links.pullRequests().isEmpty()) {
            text.append("Pull requests:\n");
            for (LinkedPullRequest pr : links.pullRequests()) {
                text.append("- ").append(pr.title()).append(" (").append(pr.status()).append("), ")
                        .append(pr.sourceBranch()).append(" -> ").append(pr.targetBranch()).append('\n');
            }
        }
        if (!links.commits().isEmpty()) {
            text.append("Commits:\n");
            for (LinkedCommit commit : links.commits()) {
                text.append("- ").append(commit.shortSha()).append(' ').append(commit.author()).append(": ")
                        .append(commit.message()).append('\n');
            }
        }
        return Optional.of(new IndexDocument(IndexCollection.CODE_LINKS,
                "codelinks:" + release.id() + ":" + ticket.key(), clean(ticket.key() + " code links"),
                clean(text.toString()), metadata));
    }

    private static IndexDocument manualDocument(Release release, ManualItem manual) {
        Map<String, Object> metadata = releaseMetadata(release);
        put(metadata, "project_key", release.jiraProjectKey());
        put(metadata, "category", manual.category() == null ? null : manual.category().name());

        StringBuilder text = new StringBuilder();
        line(text, "Title", manual.title());
        line(text, "Category", manual.category() == null ? null : manual.category().name());
        if (manual.description() != null && !manual.description().isBlank()) {
            text.append("\nDescription:\n").append(manual.description()).append('\n');
        }
        return new IndexDocument(IndexCollection.MANUAL_ITEMS, "manual:" + manual.id(), clean(manual.title()),
                clean(text.toString()), metadata);
    }

    private static IndexDocument documentOf(Release release, DocumentSnapshot snapshot) {
        Map<String, Object> metadata = releaseMetadata(release);
        put(metadata, "project_key", release.jiraProjectKey());
        put(metadata, "doc_type", snapshot.documentType().name());
        put(metadata, "revision", snapshot.revision());
        put(metadata, "generated_at", snapshot.generatedAt() == null ? null : snapshot.generatedAt().toString());
        String title = label(snapshot.documentType()) + " - " + release.name() + " " + release.version()
                + " (revision " + snapshot.revision() + ")";
        return new IndexDocument(IndexCollection.DOCUMENTS,
                "document:" + release.id() + ":" + snapshot.documentType().name(), clean(title),
                clean(snapshot.markdown()), metadata);
    }

    private static Collection<DocumentSnapshot> latestPerType(List<DocumentSnapshot> snapshots) {
        return snapshots.stream()
                .collect(Collectors.toMap(DocumentSnapshot::documentType, Function.identity(),
                        (a, b) -> a.revision() >= b.revision() ? a : b, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing(DocumentSnapshot::documentType))
                .toList();
    }

    private static Map<String, Object> releaseMetadata(Release release) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "release_id", release.id().toString());
        put(metadata, "release_name", release.name());
        put(metadata, "release_version", release.version());
        put(metadata, "release_status", release.status().name());
        return metadata;
    }

    private static void put(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private static void line(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) {
            text.append(label).append(": ").append(value).append('\n');
        }
    }

    private static String projectKeyOf(String ticketKey) {
        int dash = ticketKey.indexOf('-');
        return dash > 0 ? ticketKey.substring(0, dash) : null;
    }

    private static String label(TemplateDocumentType type) {
        return switch (type) {
            case RELEASE_NOTES -> "Release notes";
            case RUNBOOK -> "Runbook";
            case APPROVAL -> "Approval";
        };
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace("\u0000", "");
    }
}
