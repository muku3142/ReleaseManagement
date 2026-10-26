package com.evision.releasemgmt.adapters.rag;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class SseStreamReader {

    interface Handler {

        void onComment();

        // Returns true when the stream is finished and reading should stop.
        boolean onEvent(String event, String data) throws IOException;
    }

    private SseStreamReader() {
    }

    // Returns true when the handler stopped the read, false when the stream ended first.
    static boolean read(InputStream stream, Handler handler) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String event = null;
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(":")) {
                handler.onComment();
            } else if (line.isEmpty()) {
                if (event != null && handler.onEvent(event, data.toString())) {
                    return true;
                }
                event = null;
                data.setLength(0);
            } else if (line.startsWith("event:")) {
                event = line.substring("event:".length()).strip();
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).stripLeading());
            }
        }
        return false;
    }
}
