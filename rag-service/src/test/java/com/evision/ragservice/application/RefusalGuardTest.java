package com.evision.ragservice.application;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefusalGuardTest {

    private final List<String> received = new ArrayList<>();
    private final RefusalGuard guard = new RefusalGuard(received::add);

    private void feed(String... tokens) {
        for (String t : tokens) {
            guard.accept(t);
        }
    }

    @Test
    void wholeMarkerIsSuppressed() {
        feed("NO_ANSWER");

        assertThat(guard.refused()).isTrue();
        assertThat(received).isEmpty();
    }

    @Test
    void splitMarkerIsSuppressedAndLaterTokensIgnored() {
        feed("NO", "_ANS", "WER", " more");

        assertThat(guard.refused()).isTrue();
        assertThat(received).isEmpty();
    }

    @Test
    void leadingWhitespaceIsIgnored() {
        feed("  ", "NO_ANSWER.");

        assertThat(guard.refused()).isTrue();
        assertThat(received).isEmpty();
    }

    @Test
    void normalAnswerPassesThroughInOrder() {
        feed("The", " answer");

        assertThat(received).containsExactly("The", " answer");
        assertThat(guard.refused()).isFalse();
        assertThat(guard.emittedAny()).isTrue();
    }

    @Test
    void divergingPrefixIsFlushedOnce() {
        feed("NO", "body knows");

        assertThat(received).containsExactly("NObody knows");
        assertThat(guard.refused()).isFalse();
    }

    @Test
    void truncatedMarkerIsRefusalOnFinish() {
        feed("NO_ANS");
        guard.finish();

        assertThat(guard.refused()).isTrue();
        assertThat(received).isEmpty();
    }

    @Test
    void emptyStreamIsNeitherRefusedNorEmitted() {
        guard.finish();

        assertThat(guard.refused()).isFalse();
        assertThat(guard.emittedAny()).isFalse();
    }
}
