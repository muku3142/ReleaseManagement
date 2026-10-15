package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.DocumentGenerationService;
import com.evision.releasemgmt.application.DocumentReadinessService;
import com.evision.releasemgmt.application.DocumentReadinessService.ReadinessEntry;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.domain.document.DocumentReadinessState;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class DocumentsTool implements AssistantTool {

    static final int MAX_REVISIONS = 10;
    static final int MAX_RELEASES = 25;

    public record Revision(int revision, Instant generatedAt, String generatedBy, String templateName) {
    }

    public record TypeStatus(TemplateDocumentType type, DocumentReadinessState state, Integer latestRevision,
                             List<Revision> revisions) {
    }

    public record ReleaseDocuments(List<TypeStatus> documents) {
    }

    public record PendingRelease(String name, String version, Map<TemplateDocumentType, DocumentReadinessState> states) {
    }

    public record PendingDocuments(List<PendingRelease> releases, boolean truncated) {
    }

    private final DocumentReadinessService readinessService;
    private final DocumentGenerationService generationService;
    private final ActorResolutionService actors;
    private final ReleaseService releaseService;
    private final ReleaseRef releaseRef;

    public DocumentsTool(DocumentReadinessService readinessService, DocumentGenerationService generationService,
                         ActorResolutionService actors, ReleaseService releaseService, ReleaseRef releaseRef) {
        this.readinessService = readinessService;
        this.generationService = generationService;
        this.actors = actors;
        this.releaseService = releaseService;
        this.releaseRef = releaseRef;
    }

    @Override
    public String name() {
        return "documents";
    }

    @Override
    public String description() {
        return "Document readiness: per type for `release`, else releases not up to date.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object",
                "properties", Map.of("release", Map.of("type", "string", "maxLength", ReleaseRef.MAX_LENGTH)),
                "additionalProperties", false);
    }

    @Override
    public ToolOutput call(Map<String, Object> arguments) {
        Object release = arguments.get("release");
        if (release == null) {
            return new ToolOutput(pending(), "documents:pending", "ReleaseManagement: documents needing work");
        }
        UUID id = releaseRef.resolve(release);
        return new ToolOutput(forRelease(id), "documents:" + id, "ReleaseManagement: documents");
    }

    private ReleaseDocuments forRelease(UUID releaseId) {
        List<TypeStatus> statuses = readinessService.all().stream()
                .filter(entry -> entry.releaseId().equals(releaseId))
                .map(entry -> typeStatus(releaseId, entry))
                .toList();
        return new ReleaseDocuments(statuses);
    }

    private TypeStatus typeStatus(UUID releaseId, ReadinessEntry entry) {
        List<DocumentSnapshot> newestFirst = generationService.revisions(releaseId, entry.documentType()).stream()
                .sorted(Comparator.comparingInt(DocumentSnapshot::revision).reversed())
                .limit(MAX_REVISIONS)
                .toList();
        ResolvedActors resolved = actors.resolve(newestFirst.stream().map(DocumentSnapshot::generatedBy)
                .toArray(String[]::new));
        List<Revision> revisions = newestFirst.stream()
                .map(s -> new Revision(s.revision(), s.generatedAt(), resolved.display(s.generatedBy()),
                        s.templateName()))
                .toList();
        return new TypeStatus(entry.documentType(), entry.state(),
                revisions.isEmpty() ? null : revisions.getFirst().revision(), revisions);
    }

    private PendingDocuments pending() {
        Map<UUID, Map<TemplateDocumentType, DocumentReadinessState>> byRelease = new LinkedHashMap<>();
        readinessService.all().forEach(entry -> byRelease
                .computeIfAbsent(entry.releaseId(), id -> new LinkedHashMap<>())
                .put(entry.documentType(), entry.state()));
        Map<UUID, ReleaseSummary> summaries = releaseService.search("").stream()
                .collect(Collectors.toMap(ReleaseSummary::id, s -> s, (a, b) -> a));
        List<PendingRelease> outstanding = byRelease.entrySet().stream()
                .filter(e -> e.getValue().containsValue(DocumentReadinessState.NOT_GENERATED)
                        || e.getValue().containsValue(DocumentReadinessState.STALE)
                        || e.getValue().containsValue(DocumentReadinessState.NO_TEMPLATE))
                .filter(e -> summaries.containsKey(e.getKey()))
                .map(e -> new PendingRelease(summaries.get(e.getKey()).name(), summaries.get(e.getKey()).version(),
                        e.getValue()))
                .toList();
        return new PendingDocuments(outstanding.stream().limit(MAX_RELEASES).toList(),
                outstanding.size() > MAX_RELEASES);
    }
}
