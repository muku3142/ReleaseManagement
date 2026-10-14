package com.evision.releasemgmt.domain.dashboard;

import com.evision.releasemgmt.domain.enrichment.CommitsSource;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.enrichment.HistoryState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseStoryPointsTest {

    private static ChurnItem item(String points, boolean read) {
        return new ChurnItem(UUID.randomUUID(), "CNV-1", EnrichmentStatus.LINKED, false, CommitsSource.ADO_COMPLETE,
                HistoryState.AVAILABLE, Instant.parse("2026-10-05T10:00:00Z"), 0, 0, 0, 0,
                points == null ? null : new BigDecimal(points), read);
    }

    @Test
    void sumsEstimatesAndCountsUnestimatedAndUnreadSeparately() {
        ReleaseStoryPoints sp = ReleaseStoryPoints.of(List.of(item("5", true), item("3", true),
                item(null, true), item(null, false)));

        assertThat(sp.total()).isEqualByComparingTo("8");
        assertThat(sp.estimated()).isEqualTo(2);
        assertThat(sp.unestimated()).isEqualTo(1);
        assertThat(sp.unread()).isEqualTo(1);
    }

    @Test
    void sumsFractionalPointsWithoutFloatDrift() {
        ReleaseStoryPoints sp = ReleaseStoryPoints.of(List.of(item("0.5", true), item("1.25", true)));

        assertThat(sp.total()).isEqualByComparingTo("1.75");
    }

    @Test
    void emptyReleaseHasZeroEverything() {
        ReleaseStoryPoints sp = ReleaseStoryPoints.of(List.of());

        assertThat(sp).isEqualTo(new ReleaseStoryPoints(BigDecimal.ZERO, 0, 0, 0));
    }

    @Test
    void allUnreadCountsEveryTicketAsUnread() {
        ReleaseStoryPoints sp = ReleaseStoryPoints.of(List.of(item(null, false), item(null, false), item(null, false)));

        assertThat(sp.total()).isEqualByComparingTo("0");
        assertThat(sp.estimated()).isZero();
        assertThat(sp.unestimated()).isZero();
        assertThat(sp.unread()).isEqualTo(3);
    }
}
