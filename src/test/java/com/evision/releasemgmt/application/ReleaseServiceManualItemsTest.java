package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseFrozenException;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReleaseServiceManualItemsTest {

    @Mock
    private ReleaseRepository repository;

    @Mock
    private TicketSearchService ticketSearchService;

    private ReleaseService service;

    @BeforeEach
    void setUp() {
        service = new ReleaseService(repository, ticketSearchService);
    }

    private static Release release(UUID id, ReleaseStatus status) {
        return new Release(id, "Sprint 42", "2026.08", status, "CNV",
                Instant.parse("2026-08-04T09:00:00Z"), "557058:abc-123", null, null, null, null,
                List.of(), List.of());
    }

    @Test
    void addingAManualItemAssignsAnIdAndTimestampAndTrimsTheTitle() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.addManualItem(eq(id), any())).thenReturn(release(id, ReleaseStatus.OPEN));

        service.addManualItem(id, "  Rebuild search index  ", "Run scripts/reindex.sh",
                ManualItemCategory.DATABASE_SCRIPT);

        ArgumentCaptor<ManualItem> itemCaptor = ArgumentCaptor.forClass(ManualItem.class);
        verify(repository).addManualItem(eq(id), itemCaptor.capture());
        ManualItem captured = itemCaptor.getValue();
        assertThat(captured.title()).isEqualTo("Rebuild search index");
        assertThat(captured.id()).isNotNull();
        assertThat(captured.addedAt()).isNotNull();
        assertThat(captured.category()).isEqualTo(ManualItemCategory.DATABASE_SCRIPT);
    }

    @Test
    void aNullDescriptionIsStoredAsAnEmptyStringNotNull() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.addManualItem(eq(id), any())).thenReturn(release(id, ReleaseStatus.OPEN));

        service.addManualItem(id, "Rebuild search index", null, ManualItemCategory.DATABASE_SCRIPT);

        ArgumentCaptor<ManualItem> itemCaptor = ArgumentCaptor.forClass(ManualItem.class);
        verify(repository).addManualItem(eq(id), itemCaptor.capture());
        assertThat(itemCaptor.getValue().description()).isEqualTo("");
    }

    @Test
    void everyCategoryValueRoundTrips() {
        assertThat(ManualItemCategory.values()).hasSize(4);

        for (ManualItemCategory category : ManualItemCategory.values()) {
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
            when(repository.addManualItem(eq(id), any())).thenReturn(release(id, ReleaseStatus.OPEN));

            service.addManualItem(id, "Item", "Description", category);

            ArgumentCaptor<ManualItem> itemCaptor = ArgumentCaptor.forClass(ManualItem.class);
            verify(repository).addManualItem(eq(id), itemCaptor.capture());
            assertThat(itemCaptor.getValue().category()).isEqualTo(category);
        }
    }

    @Test
    void updatingAManualItemPassesTheSuppliedIdThrough() {
        UUID releaseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(repository.findById(releaseId)).thenReturn(Optional.of(release(releaseId, ReleaseStatus.OPEN)));
        when(repository.updateManualItem(eq(releaseId), any())).thenReturn(release(releaseId, ReleaseStatus.OPEN));

        service.updateManualItem(releaseId, itemId, "Updated title", "Updated description",
                ManualItemCategory.CONFIG_CHANGE);

        ArgumentCaptor<ManualItem> itemCaptor = ArgumentCaptor.forClass(ManualItem.class);
        verify(repository).updateManualItem(eq(releaseId), itemCaptor.capture());
        assertThat(itemCaptor.getValue().id()).isEqualTo(itemId);
    }

    @Test
    void updatingDoesNotAttemptToChangeAddedAt() {
        UUID releaseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(repository.findById(releaseId)).thenReturn(Optional.of(release(releaseId, ReleaseStatus.OPEN)));
        when(repository.updateManualItem(eq(releaseId), any())).thenReturn(release(releaseId, ReleaseStatus.OPEN));

        service.updateManualItem(releaseId, itemId, "Updated title", "Updated description",
                ManualItemCategory.CONFIG_CHANGE);

        ArgumentCaptor<ManualItem> itemCaptor = ArgumentCaptor.forClass(ManualItem.class);
        verify(repository).updateManualItem(eq(releaseId), itemCaptor.capture());
        assertThat(itemCaptor.getValue().addedAt()).isNull();
    }

    @Test
    void removingAManualItemDelegatesWithBothIds() {
        UUID releaseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(repository.findById(releaseId)).thenReturn(Optional.of(release(releaseId, ReleaseStatus.OPEN)));
        when(repository.removeManualItem(releaseId, itemId)).thenReturn(release(releaseId, ReleaseStatus.OPEN));

        service.removeManualItem(releaseId, itemId);

        verify(repository).removeManualItem(releaseId, itemId);
    }

    @Test
    void addingAManualItemToAFrozenReleaseThrowsReleaseFrozenException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.FROZEN)));

        assertThatThrownBy(() -> service.addManualItem(id, "Title", "Description",
                ManualItemCategory.OTHER))
                .isInstanceOf(ReleaseFrozenException.class);

        verify(repository, never()).addManualItem(any(), any());
    }

    @Test
    void updatingAManualItemOnAFrozenReleaseThrowsReleaseFrozenException() {
        UUID id = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.FROZEN)));

        assertThatThrownBy(() -> service.updateManualItem(id, itemId, "Title", "Description",
                ManualItemCategory.OTHER))
                .isInstanceOf(ReleaseFrozenException.class);

        verify(repository, never()).updateManualItem(any(), any());
    }

    @Test
    void removingAManualItemFromAFrozenReleaseThrowsReleaseFrozenException() {
        UUID id = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.FROZEN)));

        assertThatThrownBy(() -> service.removeManualItem(id, itemId))
                .isInstanceOf(ReleaseFrozenException.class);

        verify(repository, never()).removeManualItem(any(), any());
    }

    @Test
    void operatingOnAnUnknownReleaseThrowsReleaseNotFoundException() {
        UUID id = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addManualItem(id, "Title", "Description",
                ManualItemCategory.OTHER))
                .isInstanceOf(ReleaseNotFoundException.class);
        assertThatThrownBy(() -> service.updateManualItem(id, itemId, "Title", "Description",
                ManualItemCategory.OTHER))
                .isInstanceOf(ReleaseNotFoundException.class);
        assertThatThrownBy(() -> service.removeManualItem(id, itemId))
                .isInstanceOf(ReleaseNotFoundException.class);
    }
}
