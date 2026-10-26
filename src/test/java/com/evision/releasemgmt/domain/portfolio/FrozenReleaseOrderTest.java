package com.evision.releasemgmt.domain.portfolio;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FrozenReleaseOrderTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static FrozenRelease release(String version, long hours) {
        return new FrozenRelease(UUID.randomUUID(), "R" + version, version, T0.plusSeconds(hours * 3600));
    }

    @Test
    void countsReleasesFrozenAfterTheRunningOne() {
        FrozenRelease f1 = release("1.0", 1);
        FrozenRelease f2 = release("2.0", 2);
        FrozenRelease f3 = release("3.0", 3);

        FrozenReleaseOrder order = FrozenReleaseOrder.of(List.of(f1, f2, f3));

        assertThat(order.newest()).contains(f3);
        assertThat(order.releasesBehind(f3.id())).isZero();
        assertThat(order.releasesBehind(f2.id())).isEqualTo(1);
        assertThat(order.releasesBehind(f1.id())).isEqualTo(2);
    }

    @Test
    void inputOrderDoesNotMatter() {
        FrozenRelease f1 = release("1.0", 1);
        FrozenRelease f2 = release("2.0", 2);
        FrozenRelease f3 = release("3.0", 3);

        FrozenReleaseOrder order = FrozenReleaseOrder.of(List.of(f3, f1, f2));

        assertThat(order.newest()).contains(f3);
        assertThat(order.releasesBehind(f1.id())).isEqualTo(2);
        assertThat(order.releasesBehind(f2.id())).isEqualTo(1);
    }

    @Test
    void equalFrozenDatesAreBrokenByIdAscending() {
        UUID low = new UUID(0, 1);
        UUID high = new UUID(0, 2);
        FrozenRelease a = new FrozenRelease(high, "A", "1.0", T0);
        FrozenRelease b = new FrozenRelease(low, "B", "1.0", T0);

        FrozenReleaseOrder order = FrozenReleaseOrder.of(List.of(a, b));

        assertThat(order.newest()).contains(a);
        assertThat(order.releasesBehind(low)).isEqualTo(1);
        assertThat(order.releasesBehind(high)).isZero();
    }

    @Test
    void versionTextIsIgnored() {
        FrozenRelease old = release("10.0", 1);
        FrozenRelease recent = release("2.0", 3);

        FrozenReleaseOrder order = FrozenReleaseOrder.of(List.of(old, recent));

        assertThat(order.newest()).contains(recent);
        assertThat(order.releasesBehind(old.id())).isEqualTo(1);
    }

    @Test
    void aRefrozenReleaseCountsFromItsCurrentFrozenDate() {
        FrozenRelease refrozen = release("1.0", 9);
        FrozenRelease f3 = release("3.0", 3);

        FrozenReleaseOrder order = FrozenReleaseOrder.of(List.of(refrozen, f3));

        assertThat(order.newest()).contains(refrozen);
        assertThat(order.releasesBehind(f3.id())).isEqualTo(1);
    }

    @Test
    void anEmptyOrderHasNoNewest() {
        FrozenReleaseOrder order = FrozenReleaseOrder.of(List.of());

        assertThat(order.newest()).isEmpty();
        assertThat(order.isEmpty()).isTrue();
    }

    @Test
    void aRunningReleaseOutsideTheOrderIsZeroBehind() {
        FrozenReleaseOrder order = FrozenReleaseOrder.of(List.of(release("1.0", 1)));

        assertThat(order.releasesBehind(UUID.randomUUID())).isZero();
    }
}
