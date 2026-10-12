package com.evision.releasemgmt.domain.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.evision.releasemgmt.domain.enrichment.CommitsSource;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.enrichment.HistoryState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReleaseChurnTest {

    private static final UUID RELEASE = UUID.randomUUID();
    private static final Instant DONE = Instant.parse("2026-10-05T10:00:00Z");

    private static ChurnItem item(String key, EnrichmentStatus status, boolean incomplete, CommitsSource source,
                                  HistoryState history, Instant firstDone, Integer reopens, long toDone, long postDone) {
        return new ChurnItem(RELEASE, key, status, incomplete, source, history, firstDone, reopens, toDone, postDone,
                toDone + postDone);
    }

    private static ChurnItem withData(String key, long toDone) {
        return item(key, EnrichmentStatus.LINKED, false, CommitsSource.ADO_COMPLETE, HistoryState.AVAILABLE, DONE, 0,
                toDone, 0);
    }

    private static ChurnItem withStatus(EnrichmentStatus status, boolean incomplete) {
        return item("T-" + status, status, incomplete, CommitsSource.ADO_COMPLETE, HistoryState.NOT_READ, null, null, 0, 0);
    }

    @Test
    void compositionCountsLinkedIncompleteAsLinked() {
        ReleaseChurn churn = ReleaseChurn.of(List.of(
                withStatus(EnrichmentStatus.LINKED, false), withStatus(EnrichmentStatus.LINKED, true),
                withStatus(EnrichmentStatus.NO_LINK, false), withStatus(EnrichmentStatus.FAILED, false),
                withStatus(EnrichmentStatus.NOT_ATTEMPTED, false)));
        assertThat(churn.tickets()).isEqualTo(5);
        assertThat(churn.linked()).isEqualTo(2);
        assertThat(churn.noLink()).isEqualTo(1);
        assertThat(churn.failed()).isEqualTo(1);
        assertThat(churn.noData()).isEqualTo(1);
    }

    @Test
    void ineligibleItemsAreCommitNoData() {
        List<ChurnItem> items = List.of(
                withData("OK", 3),
                item("INC", EnrichmentStatus.LINKED, true, CommitsSource.ADO_COMPLETE, HistoryState.AVAILABLE, DONE, 0, 3, 0),
                item("UNAV", EnrichmentStatus.LINKED, false, CommitsSource.ADO_COMPLETE, HistoryState.UNAVAILABLE, DONE, null, 3, 0),
                item("NOTREAD", EnrichmentStatus.LINKED, false, CommitsSource.ADO_COMPLETE, HistoryState.NOT_READ, DONE, null, 3, 0),
                item("NODONE", EnrichmentStatus.LINKED, false, CommitsSource.ADO_COMPLETE, HistoryState.AVAILABLE, null, 0, 3, 0),
                item("ZERO", EnrichmentStatus.LINKED, false, CommitsSource.ADO_COMPLETE, HistoryState.AVAILABLE, DONE, 0, 0, 0));
        ReleaseChurn churn = ReleaseChurn.of(items);
        assertThat(churn.commitNoData()).isEqualTo(5);
        assertThat(churn.medianCommitsToDone()).isEqualTo(3.0);
    }

    @Test
    void medianAndPostDoneShareUseCommitDataItemsOnly() {
        ReleaseChurn churn = ReleaseChurn.of(List.of(
                item("A", EnrichmentStatus.LINKED, false, CommitsSource.ADO_COMPLETE, HistoryState.AVAILABLE, DONE, 0, 3, 1),
                item("B", EnrichmentStatus.LINKED, false, CommitsSource.ADO_COMPLETE, HistoryState.AVAILABLE, DONE, 0, 5, 3),
                withStatus(EnrichmentStatus.NO_LINK, false)));
        assertThat(churn.medianCommitsToDone()).isCloseTo(4.0, within(1e-9));
        assertThat(churn.postDoneShare()).isCloseTo(4.0 / 12.0, within(1e-9));
    }

    @Test
    void reopensCountItemsWithoutCommitsAndFlagUnavailableHistory() {
        ReleaseChurn churn = ReleaseChurn.of(List.of(
                item("R", EnrichmentStatus.NO_LINK, false, null, HistoryState.AVAILABLE, DONE, 2, 0, 0),
                item("U", EnrichmentStatus.LINKED, false, CommitsSource.ADO_COMPLETE, HistoryState.UNAVAILABLE, null, null, 0, 0)));
        assertThat(churn.reopens()).isEqualTo(2);
        assertThat(churn.reopenNoData()).isEqualTo(1);
        assertThat(churn.perTicket().get(1).reopens()).isNull();
    }

    @Test
    void tukeyFenceFlagsOnlyTheExtremeTicket() {
        ReleaseChurn churn = ReleaseChurn.of(List.of(
                withData("A", 1), withData("B", 2), withData("C", 3), withData("D", 4), withData("E", 100)));
        assertThat(churn.perTicket()).filteredOn(TicketChurn::outlier).extracting(TicketChurn::ticketKey)
                .containsExactly("E");
        assertThat(churn.outliers()).isEqualTo(1);
    }

    @Test
    void fenceNeedsFourTicketsWithCommitData() {
        ReleaseChurn churn = ReleaseChurn.of(List.of(withData("A", 1), withData("B", 1), withData("C", 50)));
        assertThat(churn.outliers()).isZero();
    }

    @Test
    void twoReopensFlagAnOutlierEvenWithoutCommitData() {
        ReleaseChurn churn = ReleaseChurn.of(List.of(
                item("R", EnrichmentStatus.NO_LINK, false, null, HistoryState.AVAILABLE, DONE, 2, 0, 0)));
        assertThat(churn.outliers()).isEqualTo(1);
    }

    @Test
    void keyTaggedIsCountedPerReleaseAndPerTicket() {
        ReleaseChurn churn = ReleaseChurn.of(List.of(
                item("K", EnrichmentStatus.LINKED, false, CommitsSource.KEY_TAGGED, HistoryState.AVAILABLE, DONE, 0, 2, 0),
                withData("C", 2)));
        assertThat(churn.keyTagged()).isEqualTo(1);
        assertThat(churn.perTicket().get(0).keyTaggedOnly()).isTrue();
        assertThat(churn.perTicket().get(1).keyTaggedOnly()).isFalse();
    }

    @Test
    void noDataTicketHasNullFiguresNeverZero() {
        TicketChurn ticket = ReleaseChurn.of(List.of(withStatus(EnrichmentStatus.FAILED, false))).perTicket().get(0);
        assertThat(ticket.commitsToDone()).isNull();
        assertThat(ticket.postDoneCommits()).isNull();
        assertThat(ticket.reopens()).isNull();
    }

    @Test
    void emptyReleaseHasNoFigures() {
        ReleaseChurn churn = ReleaseChurn.of(List.of());
        assertThat(churn.tickets()).isZero();
        assertThat(churn.medianCommitsToDone()).isNull();
        assertThat(churn.postDoneShare()).isNull();
        assertThat(churn.outliers()).isZero();
    }
}
