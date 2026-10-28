package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.DocumentGenerationService;
import com.evision.releasemgmt.application.DocumentReadinessService;
import com.evision.releasemgmt.application.DocumentReadinessService.ReadinessEntry;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.domain.document.DocumentReadinessState;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentsToolTest {

    private static final String CANARY = "CANARY-TEXT";

    private final DocumentReadinessService readiness = mock(DocumentReadinessService.class);
    private final DocumentGenerationService generation = mock(DocumentGenerationService.class);
    private final ActorResolutionService actors = mock(ActorResolutionService.class);
    private final ReleaseService releaseService = mock(ReleaseService.class);
    private final ReleaseRef releaseRef = mock(ReleaseRef.class);
    private final DocumentsTool tool = new DocumentsTool(readiness, generation, actors, releaseService, releaseRef);
    private final JsonMapper mapper = JsonMapper.builder().build();

    private static DocumentSnapshot snapshot(UUID releaseId, int revision) {
        return new DocumentSnapshot(UUID.randomUUID(), releaseId, TemplateDocumentType.RUNBOOK, revision, CANARY, null,
                "Runbook", CANARY, null, Instant.EPOCH, "acc-1", null);
    }

    @Test
    void releaseListsStatesAndNewestTenRevisionsWithoutMarkdown() {
        UUID id = UUID.randomUUID();
        when(releaseRef.resolve("R")).thenReturn(id);
        when(readiness.all()).thenReturn(List.of(
                new ReadinessEntry(id, TemplateDocumentType.RUNBOOK, DocumentReadinessState.STALE),
                new ReadinessEntry(UUID.randomUUID(), TemplateDocumentType.RUNBOOK, DocumentReadinessState.UP_TO_DATE)));
        when(generation.revisions(id, TemplateDocumentType.RUNBOOK)).thenReturn(
                IntStream.rangeClosed(1, 12).mapToObj(i -> snapshot(id, i)).toList());
        when(actors.resolve(any(String[].class))).thenReturn(new ResolvedActors(Map.of("acc-1", "a@x.com")));

        JsonNode json = mapper.valueToTree(tool.call(Map.of("release", "R")).result());

        JsonNode runbook = json.get("documents").get(0);
        assertThat(json.get("documents")).hasSize(1);
        assertThat(runbook.get("state").asText()).isEqualTo("STALE");
        assertThat(runbook.get("latestRevision").asInt()).isEqualTo(12);
        assertThat(runbook.get("revisions")).hasSize(10);
        assertThat(runbook.get("revisions").get(0).get("generatedBy").asText()).isEqualTo("a@x.com");
        assertThat(json.toString()).doesNotContain(CANARY);
    }

    @Test
    void withoutReleaseOnlyOutstandingReleasesAreListed() {
        UUID stale = UUID.randomUUID();
        UUID fine = UUID.randomUUID();
        when(readiness.all()).thenReturn(List.of(
                new ReadinessEntry(stale, TemplateDocumentType.APPROVAL, DocumentReadinessState.NOT_GENERATED),
                new ReadinessEntry(fine, TemplateDocumentType.APPROVAL, DocumentReadinessState.UP_TO_DATE)));
        when(releaseService.search("")).thenReturn(List.of(summary(stale, "Payments"), summary(fine, "Billing")));

        JsonNode json = mapper.valueToTree(tool.call(Map.of()).result());

        assertThat(json.get("releases")).hasSize(1);
        assertThat(json.get("releases").get(0).get("name").asText()).isEqualTo("Payments");
        assertThat(json.get("releases").get(0).get("states").get("APPROVAL").asText()).isEqualTo("NOT_GENERATED");
        assertThat(json.get("truncated").asBoolean()).isFalse();
    }

    private static ReleaseSummary summary(UUID id, String name) {
        return new ReleaseSummary(id, name, "1.0", ReleaseStatus.OPEN, "PAY", Instant.EPOCH, "a", null, null, null,
                null, 0);
    }
}
