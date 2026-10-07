package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.document.DocumentReadinessState;
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
        assertThat(service.state(ReleaseStatus.OPEN, null, null, false, T0))
                .isEqualTo(DocumentReadinessState.NO_TEMPLATE);
        assertThat(service.state(ReleaseStatus.OPEN, null, null, false, null))
                .isEqualTo(DocumentReadinessState.NO_TEMPLATE);
    }

    @Test
    void templateWithNoSnapshotIsNotGenerated() {
        assertThat(service.state(ReleaseStatus.OPEN, null, null, true, null))
                .isEqualTo(DocumentReadinessState.NOT_GENERATED);
    }

    @Test
    void snapshotGeneratedAfterBothTimestampsIsUpToDate() {
        assertThat(service.state(ReleaseStatus.OPEN, T0, T0, true, T1))
                .isEqualTo(DocumentReadinessState.UP_TO_DATE);
    }

    @Test
    void snapshotGeneratedBeforeLastRefreshedAtIsStale() {
        assertThat(service.state(ReleaseStatus.OPEN, T2, T0, true, T1))
                .isEqualTo(DocumentReadinessState.STALE);
    }

    @Test
    void snapshotGeneratedBeforeItemsChangedAtIsStale() {
        assertThat(service.state(ReleaseStatus.OPEN, T0, T2, true, T1))
                .isEqualTo(DocumentReadinessState.STALE);
    }

    @Test
    void frozenReleaseWithASnapshotIsUpToDateEvenWhenTimestampsWouldOtherwiseSayStale() {
        assertThat(service.state(ReleaseStatus.FROZEN, T2, T2, true, T0))
                .isEqualTo(DocumentReadinessState.UP_TO_DATE);
    }

    @Test
    void bothTimestampsNullWithASnapshotPresentIsUpToDate() {
        assertThat(service.state(ReleaseStatus.OPEN, null, null, true, T0))
                .isEqualTo(DocumentReadinessState.UP_TO_DATE);
    }

    // D-05-09: this is the exact scenario itemsChangedAt exists for - a removal leaves no
    // timestamp anywhere else, so without it this comparison could never see the change.
    @Test
    void removingATicketFromAnOpenReleaseFlipsAPreviouslyUpToDateDocumentToStale() {
        DocumentReadinessState beforeRemoval = service.state(ReleaseStatus.OPEN, null, T0, true, T1);
        assertThat(beforeRemoval).isEqualTo(DocumentReadinessState.UP_TO_DATE);

        DocumentReadinessState afterRemoval = service.state(ReleaseStatus.OPEN, null, T2, true, T1);
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

    private static ReleaseSummary summary(UUID id, ReleaseStatus status) {
        return new ReleaseSummary(id, "R", "1.0", status, "PRJ", T0, "actor", null, null, null, null, 0);
    }
}
