package com.evision.ragservice.domain;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class Cancellation {

    private final AtomicReference<CancelReason> reason = new AtomicReference<>();
    private final CopyOnWriteArrayList<Runnable> callbacks = new CopyOnWriteArrayList<>();

    public void cancel(CancelReason r) {
        if (reason.compareAndSet(null, r)) {
            callbacks.forEach(Cancellation::runQuietly);
        }
    }

    // Callbacks must be idempotent: one registered during a concurrent cancel() may run twice, and closing a stream twice is harmless
    public void onCancel(Runnable callback) {
        callbacks.add(callback);
        if (isCancelled()) {
            runQuietly(callback);
        }
    }

    public boolean isCancelled() {
        return reason.get() != null;
    }

    public CancelReason reason() {
        return reason.get();
    }

    private static void runQuietly(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException ignored) {
            // one failing close must not stop the others
        }
    }
}
