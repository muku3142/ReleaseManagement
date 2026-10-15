package com.evision.releasemgmt.application.assistant;

import com.evision.releasemgmt.domain.release.ReleaseSummary;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndexSweepTest {

    private final ReleaseRepository releases = mock(ReleaseRepository.class);
    private final IndexSyncer syncer = mock(IndexSyncer.class);
    private final IndexSweep sweep = new IndexSweep(releases, syncer);

    @Test
    void ensuresCollectionsThenSyncsEveryReleaseThenOrphanedRecordedOnes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        when(releases.search("")).thenReturn(List.of(summary(a), summary(b)));
        when(syncer.recordedReleaseIds()).thenReturn(Set.of(a, gone));

        sweep.sweep();

        InOrder order = inOrder(syncer);
        order.verify(syncer).ensureCollections();
        order.verify(syncer).sync(a);
        order.verify(syncer).sync(b);
        order.verify(syncer).sync(gone);
        verify(syncer, times(1)).sync(a);
    }

    @Test
    void searchFailureIsSwallowed() {
        when(releases.search("")).thenThrow(new IllegalStateException("db down"));

        assertThatCode(sweep::sweep).doesNotThrowAnyException();
        verify(syncer, never()).sync(any());
    }

    private static ReleaseSummary summary(UUID id) {
        return new ReleaseSummary(id, "n", "1", null, null, null, null, null, null, null, null, 0);
    }
}
