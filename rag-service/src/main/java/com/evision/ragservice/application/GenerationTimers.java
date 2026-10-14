package com.evision.ragservice.application;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

// Private to generation so a long indexing tick can never delay a deadline
@Component
public class GenerationTimers implements DisposableBean {

    private final ScheduledExecutorService timers =
            Executors.newScheduledThreadPool(1, Thread.ofVirtual().name("generation-timer-", 0).factory());

    // Each task runs on its own virtual thread so a slow client write never delays another request's deadline
    public ScheduledFuture<?> after(Duration delay, Runnable task) {
        return timers.schedule(() -> Thread.startVirtualThread(task), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> every(Duration period, Runnable task) {
        return timers.scheduleAtFixedRate(() -> Thread.startVirtualThread(task), period.toMillis(),
                period.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        timers.shutdownNow();
    }
}
