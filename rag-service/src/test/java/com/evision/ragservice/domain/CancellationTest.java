package com.evision.ragservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CancellationTest {

    @Test
    void freshCancellationIsNotCancelled() {
        Cancellation c = new Cancellation();
        assertThat(c.isCancelled()).isFalse();
        assertThat(c.reason()).isNull();
    }

    @Test
    void firstReasonWinsAndCallbacksRunOnce() {
        Cancellation c = new Cancellation();
        AtomicInteger runs = new AtomicInteger();
        c.onCancel(runs::incrementAndGet);
        c.cancel(CancelReason.DEADLINE);
        c.cancel(CancelReason.CLIENT_GONE);
        assertThat(c.reason()).isEqualTo(CancelReason.DEADLINE);
        assertThat(runs).hasValue(1);
    }

    @Test
    void callbackRegisteredAfterCancelRunsImmediately() {
        Cancellation c = new Cancellation();
        c.cancel(CancelReason.CLIENT_GONE);
        AtomicInteger runs = new AtomicInteger();
        c.onCancel(runs::incrementAndGet);
        assertThat(runs).hasValue(1);
    }

    @Test
    void throwingCallbackDoesNotStopOthersOrPropagate() {
        Cancellation c = new Cancellation();
        AtomicInteger runs = new AtomicInteger();
        c.onCancel(() -> { throw new IllegalStateException("boom"); });
        c.onCancel(runs::incrementAndGet);
        c.cancel(CancelReason.DEADLINE);
        assertThat(runs).hasValue(1);
    }
}
