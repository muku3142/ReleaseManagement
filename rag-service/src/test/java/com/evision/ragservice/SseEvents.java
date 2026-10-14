package com.evision.ragservice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

public final class SseEvents {

    public record Event(String name, String data) {}

    private SseEvents() {}

    public static List<Event> parse(String body) {
        List<Event> events = new ArrayList<>();
        String name = null;
        StringBuilder data = null;
        for (String line : body.split("\r?\n", -1)) {
            if (line.isEmpty()) {
                if (name != null || data != null) {
                    events.add(new Event(name, data == null ? "" : data.toString()));
                }
                name = null;
                data = null;
            } else if (!line.startsWith(":")) {
                String value = fieldValue(line);
                if (line.startsWith("event:")) {
                    name = value;
                } else if (line.startsWith("data:")) {
                    data = data == null ? new StringBuilder(value) : data.append('\n').append(value);
                }
            }
        }
        return events;
    }

    /** Reads one event incrementally; returns null at end of stream. Comment lines are skipped. */
    public static Event next(BufferedReader reader) {
        try {
            String name = null;
            StringBuilder data = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (name != null || data != null) {
                        return new Event(name, data == null ? "" : data.toString());
                    }
                } else if (!line.startsWith(":")) {
                    String value = fieldValue(line);
                    if (line.startsWith("event:")) {
                        name = value;
                    } else if (line.startsWith("data:")) {
                        data = data == null ? new StringBuilder(value) : data.append('\n').append(value);
                    }
                }
            }
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String fieldValue(String line) {
        String value = line.substring(line.indexOf(':') + 1);
        return value.startsWith(" ") ? value.substring(1) : value;
    }
}
