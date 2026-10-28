package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.document.AiMode;
import com.evision.releasemgmt.domain.document.DocumentReadinessState;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import com.evision.releasemgmt.ports.TemplateRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentReadinessServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-08-03T00:00:00Z");

    private final ReleaseRepository releaseRepository = mock(ReleaseRepository.class);
    private final TemplateRepository templateRepository = mock(TemplateRepository.class);
    private final DocumentSnapshotRepository snapshotRepository = mock(DocumentSnapshotRepository.class);
    private final DocumentReadinessService service =
            new DocumentReadinessService(releaseRepository, templateRepository, snapshotRepository);

    @Test
    void noTemplateWinsRegardlessOfWhetherASnapshotExists() {
        assertThat(service.state(ReleaseStatus.OPEN, null, null, null, false, T0))
                .isEqualTo(DocumentReadinessState.NO_TEMPLATE);
        assertThat(service.state(ReleaseStatus.OPEN, null, null, null, false, null))
                .isEqualTo(DocumentReadinessState.NO_TEMPLATE);
    }

    @Test
    void templateWithNoSnapshotIsNotGenerated() {
        assertThat(service.state(ReleaseStatus.OPEN, null, null, null, true, null))
                .isEqualTo(DocumentReadinessState.NOT_GENERATED);
    }

    @Test
    void snapshotGeneratedAfterBothTimestampsIsUpToDate() {
        assertThat(service.state(ReleaseStatus.OPEN, null, T0, T0, true, T1))
                .isEqualTo(DocumentReadinessState.UP_TO_DATE);
    }

    @Test
    void snapshotGeneratedBeforeLastRefreshedAtIsStale() {
        assertThat(service.state(ReleaseStatus.OPEN, null, T2, T0, true, T1))
                .isEqualTo(DocumentReadinessState.STALE);
    }

    @Test
    void snapshotGeneratedBeforeItemsChangedAtIsStale() {
        assertThat(service.state(ReleaseStatus.OPEN, null, T0, T2, true, T1))
                .isEqualTo(DocumentReadinessState.STALE);
    }

    @Test
    void frozenReleaseWithATicketChangeBeforeTheFreezeAndAfterGenerationIsStale() {
        assertThat(service.state(ReleaseStatus.FROZEN, T2, null, T1, true, T0))
                .isEqualTo(DocumentReadinessState.STALE);
    }

    @Test
    void frozenReleaseWithAProductEditAfterTheFreezeStaysUpToDate() {
        assertThat(service.state(ReleaseStatus.FROZEN, T1, null, T2, true, T0))
                .isEqualTo(DocumentReadinessState.UP_TO_DATE);
    }

    @Test
    void frozenReleaseRefrozenWithoutAnEditStaysUpToDate() {
        assertThat(service.state(ReleaseStatus.FROZEN, T2, null, T0, true, T1))
                .isEqualTo(DocumentReadinessState.UP_TO_DATE);
    }

    @Test
    void frozenReleaseRefreshedAfterGenerationIsStale() {
        assertThat(service.state(ReleaseStatus.FROZEN, T2, T1, null, true, T0))
                .isEqualTo(DocumentReadinessState.STALE);
    }

    @Test
    void frozenReleaseWithBothTimestampsNullIsUpToDate() {
        assertThat(service.state(ReleaseStatus.FROZEN, T1, null, null, true, T0))
                .isEqualTo(DocumentReadinessState.UP_TO_DATE);
    }

    @Test
    void bothTimestampsNullWithASnapshotPresentIsUpToDate() {
        assertThat(service.state(ReleaseStatus.OPEN, null, null, null, true, T0))
                .isEqualTo(DocumentReadinessState.UP_TO_DATE);
    }

    // D-05-09: this is the exact scenario itemsChangedAt exists for - a removal leaves no
    // timestamp anywhere else, so without it this comparison could never see the change.
    @Test
    void removingATicketFromAnOpenReleaseFlipsAPreviouslyUpToDateDocumentToStale() {
        DocumentReadinessState beforeRemoval = service.state(ReleaseStatus.OPEN, null, null, T0, true, T1);
        assertThat(beforeRemoval).isEqualTo(DocumentReadinessState.UP_TO_DATE);

        DocumentReadinessState afterRemoval = service.state(ReleaseStatus.OPEN, null, null, T2, true, T1);
        assertThat(afterRemoval).isEqualTo(DocumentReadinessState.STALE);
    }

    @Test
    void allDoesNotQueryPerRelease() {
        ReleaseSummary release1 = summary(UUID.randomUUID(), ReleaseStatus.OPEN);
        ReleaseSummary release2 = summary(UUID.randomUUID(), ReleaseStatus.OPEN);
        when(releaseRepository.search("")).thenReturn(List.of(release1, release2));
        when(templateRepository.existsByDocumentType(any())).thenReturn(false);
        when(snapshotRepository.findLatestForAllReleases()).thenReturn(List.of());

        List<DocumentReadinessService.ReadinessEntry> entries = service.all();

        assertThat(entries).hasSize(2 * TemplateDocumentType.values().length);
        verify(releaseRepository, times(1)).search("");
        verify(snapshotRepository, times(1)).findLatestForAllReleases();
        verify(templateRepository, times(3)).existsByDocumentType(any());
        verify(templateRepository, never()).findActive(any());
    }

    @Test
    void allBlocksOnlyTheTypesThatHaveNoTemplateAtAll() {
        UUID releaseId = UUID.randomUUID();
        when(releaseRepository.search("")).thenReturn(List.of(summary(releaseId, ReleaseStatus.OPEN)));
        when(templateRepository.existsByDocumentType(TemplateDocumentType.RELEASE_NOTES)).thenReturn(true);
        when(snapshotRepository.findLatestForAllReleases()).thenReturn(List.of());

        List<DocumentReadinessService.ReadinessEntry> entries = service.all();

        assertThat(entries).extracting(DocumentReadinessService.ReadinessEntry::documentType,
                        DocumentReadinessService.ReadinessEntry::state)
                .containsExactlyInAnyOrder(
                        tuple(TemplateDocumentType.RELEASE_NOTES, DocumentReadinessState.NOT_GENERATED),
                        tuple(TemplateDocumentType.RUNBOOK, DocumentReadinessState.NO_TEMPLATE),
                        tuple(TemplateDocumentType.APPROVAL, DocumentReadinessState.NO_TEMPLATE));
    }

    // D-14-01: readiness compares the staleness basis, never the AI revision's own save time.
    @Test
    void aiRevisionOfAStaleDocumentStaysStale() {
        UUID releaseId = UUID.randomUUID();
        stubRelease(releaseId, ReleaseStatus.OPEN, T1, null);
        when(snapshotRepository.findLatestForAllReleases()).thenReturn(List.of(aiRevision(releaseId, T2, T0)));

        assertThat(runbookState(releaseId)).isEqualTo(DocumentReadinessState.STALE);
    }

    @Test
    void aiRevisionOfAnUpToDateDocumentBecomesStaleWhenTheTicketSetLaterChanges() {
        UUID releaseId = UUID.randomUUID();
        when(snapshotRepository.findLatestForAllReleases()).thenReturn(List.of(aiRevision(releaseId, T2, T1)));

        stubRelease(releaseId, ReleaseStatus.OPEN, T0, null);
        assertThat(runbookState(releaseId)).isEqualTo(DocumentReadinessState.UP_TO_DATE);

        stubRelease(releaseId, ReleaseStatus.OPEN, T0, T2);
        assertThat(runbookState(releaseId)).isEqualTo(DocumentReadinessState.STALE);
    }

    @Test
    void generatedSnapshotUsesItsGenerationTimeAsBasis() {
        UUID releaseId = UUID.randomUUID();
        stubRelease(releaseId, ReleaseStatus.OPEN, T2, null);
        when(snapshotRepository.findLatestForAllReleases()).thenReturn(List.of(generated(releaseId, T1)));

        assertThat(runbookState(releaseId)).isEqualTo(DocumentReadinessState.STALE);
    }

    @Test
    void frozenReleaseAiRevisionWhoseBasisPredatesAPreFreezeChangeIsStale() {
        UUID releaseId = UUID.randomUUID();
        stubRelease(releaseId, ReleaseStatus.FROZEN, T2, null, T1);
        when(snapshotRepository.findLatestForAllReleases()).thenReturn(List.of(aiRevision(releaseId, T2, T0)));

        assertThat(runbookState(releaseId)).isEqualTo(DocumentReadinessState.STALE);
    }

    @Test
    void frozenReleaseAiRevisionWhoseBasisFollowsTheLastChangeIsUpToDate() {
        UUID releaseId = UUID.randomUUID();
        stubRelease(releaseId, ReleaseStatus.FROZEN, T2, null, T0);
        when(snapshotRepository.findLatestForAllReleases()).thenReturn(List.of(aiRevision(releaseId, T2, T1)));

        assertThat(runbookState(releaseId)).isEqualTo(DocumentReadinessState.UP_TO_DATE);
    }

    private void stubRelease(UUID releaseId, ReleaseStatus status, Instant lastRefreshedAt, Instant itemsChangedAt) {
        stubRelease(releaseId, status, null, lastRefreshedAt, itemsChangedAt);
    }

    private void stubRelease(UUID releaseId, ReleaseStatus status, Instant frozenAt, Instant lastRefreshedAt,
                             Instant itemsChangedAt) {
        when(releaseRepository.search("")).thenReturn(List.of(new ReleaseSummary(releaseId, "R", "1.0", status,
                "PRJ", T0, "actor", frozenAt, frozenAt == null ? null : "actor", lastRefreshedAt, itemsChangedAt, 0)));
        when(templateRepository.existsByDocumentType(any())).thenReturn(true);
    }

    private DocumentReadinessState runbookState(UUID releaseId) {
        return service.all().stream()
                .filter(e -> e.documentType() == TemplateDocumentType.RUNBOOK && e.releaseId().equals(releaseId))
                .findFirst().orElseThrow().state();
    }

    private static DocumentSnapshot generated(UUID releaseId, Instant generatedAt) {
        return DocumentSnapshot.create(releaseId, TemplateDocumentType.RUNBOOK, 1, "# Doc", UUID.randomUUID(), "T",
                "body", ChromeFields.empty(), "actor", generatedAt);
    }

    private static DocumentSnapshot aiRevision(UUID releaseId, Instant savedAt, Instant sourceGeneratedAt) {
        return DocumentSnapshot.createAiRevision(generated(releaseId, sourceGeneratedAt), 2, "# AI",
                AiMode.CLEAN_UP, "instr", "actor", savedAt);
    }

    private static ReleaseSummary summary(UUID id, ReleaseStatus status) {
        return new ReleaseSummary(id, "R", "1.0", status, "PRJ", T0, "actor", null, null, null, null, 0);
    }
}
