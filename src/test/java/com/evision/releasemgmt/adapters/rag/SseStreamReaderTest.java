package com.evision.releasemgmt.adapters.rag;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseStreamReaderTest {

    private static final class Recorder implements SseStreamReader.Handler {
        final List<String> events = new ArrayList<>();
        int comments;
        boolean stopOnFirst;

        @Override
        public void onComment() {
            comments++;
        }

        @Override
        public boolean onEvent(String event, String data) {
            events.add(event + "|" + data);
            return stopOnFirst;
        }
    }

    private static boolean read(String input, SseStreamReader.Handler handler) throws IOException {
        return SseStreamReader.read(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), handler);
    }

    @Test
    void dispatchesEventWithData() throws IOException {
        Recorder recorder = new Recorder();

        boolean stopped = read("event: a\ndata: {\"x\":1}\n\n", recorder);

        assertThat(recorder.events).containsExactly("a|{\"x\":1}");
        assertThat(stopped).isFalse();
    }

    @Test
    void commentLineIsNotAnEvent() throws IOException {
        Recorder recorder = new Recorder();

        read(": hb\n", recorder);

        assertThat(recorder.comments).isEqualTo(1);
        assertThat(recorder.events).isEmpty();
    }

    @Test
    void dataLinesJoinAndCrlfParsesTheSame() throws IOException {
        Recorder recorder = new Recorder();

        read("event: a\r\ndata: one\r\ndata: two\r\n\r\n", recorder);

        assertThat(recorder.events).containsExactly("a|one\ntwo");
    }

    @Test
    void blockWithoutEventNameDispatchesNothing() throws IOException {
        Recorder recorder = new Recorder();

        read("data: orphan\n\n", recorder);

        assertThat(recorder.events).isEmpty();
    }

    @Test
    void handlerReturningTrueStopsReading() throws IOException {
        Recorder recorder = new Recorder();
        recorder.stopOnFirst = true;

        boolean stopped = read("event: a\ndata: 1\n\nevent: b\ndata: 2\n\n", recorder);

        assertThat(recorder.events).containsExactly("a|1");
        assertThat(stopped).isTrue();
    }

    @Test
    void handlerExceptionPropagatesUnchanged() {
        IllegalStateException boom = new IllegalStateException("boom");
        SseStreamReader.Handler handler = new SseStreamReader.Handler() {
            @Override
            public void onComment() {
            }

            @Override
            public boolean onEvent(String event, String data) {
                throw boom;
            }
        };

        assertThatThrownBy(() -> read("event: a\ndata: 1\n\n", handler)).isSameAs(boom);
    }
}
