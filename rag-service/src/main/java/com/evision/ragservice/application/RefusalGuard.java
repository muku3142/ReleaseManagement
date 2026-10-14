package com.evision.ragservice.application;

import java.util.function.Consumer;

// Not thread-safe by design: called only from the generation thread.
public final class RefusalGuard {

    public static final String MARKER = "NO_ANSWER";
    public static final String REFUSAL_TEXT =
            "I don't know. The available sources do not contain the answer to this question.";

    private enum State { BUFFERING, PASSING, REFUSED }

    private final Consumer<String> downstream;
    private final StringBuilder buffer = new StringBuilder();
    private State state = State.BUFFERING;
    private boolean emitted;

    public RefusalGuard(Consumer<String> downstream) {
        this.downstream = downstream;
    }

    public void accept(String token) {
        switch (state) {
            case PASSING -> emit(token);
            case REFUSED -> { }
            case BUFFERING -> {
                buffer.append(token);
                String s = buffer.toString().stripLeading();
                if (s.startsWith(MARKER)) {
                    refuse();
                } else if (!MARKER.startsWith(s)) {
                    flush();
                }
            }
        }
    }

    public void finish() {
        if (state != State.BUFFERING || buffer.toString().isBlank()) {
            return;
        }
        if (MARKER.startsWith(buffer.toString().strip())) {
            refuse();
        } else {
            flush();
        }
    }

    public boolean refused() {
        return state == State.REFUSED;
    }

    public boolean emittedAny() {
        return emitted;
    }

    private void refuse() {
        state = State.REFUSED;
        buffer.setLength(0);
    }

    private void flush() {
        state = State.PASSING;
        emit(buffer.toString());
        buffer.setLength(0);
    }

    private void emit(String text) {
        emitted = true;
        downstream.accept(text);
    }
}
