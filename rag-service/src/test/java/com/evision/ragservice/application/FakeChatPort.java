package com.evision.ragservice.application;

import com.evision.ragservice.domain.Cancellation;
import com.evision.ragservice.domain.ChatMessage;
import com.evision.ragservice.domain.Completion;
import com.evision.ragservice.domain.GenerationOptions;
import com.evision.ragservice.ports.ChatPort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

class FakeChatPort implements ChatPort {

    volatile RuntimeException readyFailure;
    volatile List<String> tokens = List.of();
    volatile Completion completion = new Completion("stop", 100, 10, 5L, 50L, null);
    volatile Duration delay = Duration.ZERO;
    volatile boolean blockUntilCancelled;
    volatile RuntimeException failAfterFirstToken;

    final List<List<ChatMessage>> streamCalls = new CopyOnWriteArrayList<>();
    volatile List<ChatMessage> lastMessages;
    volatile GenerationOptions lastOptions;
    volatile int readyCalls;
    volatile RuntimeException toolsReadyFailure;
    volatile int toolsReadyCalls;

    @Override
    public void assertReady() {
        readyCalls++;
        if (readyFailure != null) {
            throw readyFailure;
        }
    }

    @Override
    public void assertToolsReady() {
        toolsReadyCalls++;
        if (toolsReadyFailure != null) {
            throw toolsReadyFailure;
        }
    }

    @Override
    public Completion stream(List<ChatMessage> messages, GenerationOptions options, Consumer<String> onToken,
                             Cancellation cancel) {
        streamCalls.add(new ArrayList<>(messages));
        lastMessages = messages;
        lastOptions = options;
        if (blockUntilCancelled) {
            CountDownLatch latch = new CountDownLatch(1);
            cancel.onCancel(latch::countDown);
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Completion.cancelled(cancel.reason());
        }
        if (!delay.isZero()) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        for (String token : tokens) {
            onToken.accept(token);
            if (failAfterFirstToken != null) {
                throw failAfterFirstToken;
            }
        }
        return completion;
    }
}
