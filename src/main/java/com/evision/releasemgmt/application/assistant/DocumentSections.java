package com.evision.releasemgmt.application.assistant;

import com.evision.releasemgmt.domain.assistant.TransformTooLargeException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits a Markdown document into the parts a sectioned transform sends to the model and the parts it copies
 * verbatim. Heading lines and the blank lines around each body are copied, so ticket titles and keys never pass
 * through the model; joining every part's text in order gives back the original document exactly.
 */
public final class DocumentSections {

    public record Part(String text, boolean transform) {
    }

    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s.*");
    private static final Pattern FENCE = Pattern.compile("^\\s{0,3}(```|~~~).*");

    private DocumentSections() {
    }

    /** @throws TransformTooLargeException if a single paragraph or code block exceeds {@code maxChars} */
    public static List<Part> split(String markdown, int maxChars) {
        List<Part> parts = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        boolean inFence = false;
        for (String line : lines(markdown)) {
            String content = line.stripTrailing();
            if (FENCE.matcher(content).matches()) {
                inFence = !inFence;
            }
            if (!inFence && HEADING.matcher(content).matches()) {
                addBody(parts, body.toString(), maxChars);
                body.setLength(0);
                parts.add(new Part(line, false));
            } else {
                body.append(line);
            }
        }
        addBody(parts, body.toString(), maxChars);
        return parts;
    }

    public static long transformCount(List<Part> parts) {
        return parts.stream().filter(Part::transform).count();
    }

    private static void addBody(List<Part> parts, String body, int maxChars) {
        if (body.isBlank()) {
            if (!body.isEmpty()) {
                parts.add(new Part(body, false));
            }
            return;
        }
        int start = leadingBlankLines(body);
        int end = body.length() - trailingWhitespace(body);
        if (start > 0) {
            parts.add(new Part(body.substring(0, start), false));
        }
        addCore(parts, body.substring(start, end), maxChars);
        if (end < body.length()) {
            parts.add(new Part(body.substring(end), false));
        }
    }

    // A body over the per-request limit is split at blank lines outside code blocks, packing paragraphs greedily.
    private static void addCore(List<Part> parts, String core, int maxChars) {
        if (core.length() <= maxChars) {
            parts.add(new Part(core, true));
            return;
        }
        StringBuilder piece = new StringBuilder();
        for (String paragraph : paragraphs(core)) {
            String text = paragraph.stripTrailing();
            if (text.length() > maxChars) {
                throw new TransformTooLargeException(text.length(), maxChars);
            }
            if (!piece.isEmpty() && piece.length() + text.length() > maxChars) {
                flushPiece(parts, piece);
            }
            piece.append(paragraph);
        }
        flushPiece(parts, piece);
    }

    private static void flushPiece(List<Part> parts, StringBuilder piece) {
        String text = piece.toString();
        int end = text.length() - trailingWhitespace(text);
        parts.add(new Part(text.substring(0, end), true));
        if (end < text.length()) {
            parts.add(new Part(text.substring(end), false));
        }
        piece.setLength(0);
    }

    // Each paragraph keeps the blank lines that follow it, so the pieces still join back to the original text.
    private static List<String> paragraphs(String core) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inFence = false;
        boolean sawBlank = false;
        for (String line : lines(core)) {
            boolean blank = line.isBlank();
            if (!inFence && sawBlank && !blank) {
                paragraphs.add(current.toString());
                current.setLength(0);
                sawBlank = false;
            }
            if (FENCE.matcher(line.stripTrailing()).matches()) {
                inFence = !inFence;
            }
            current.append(line);
            sawBlank = sawBlank || (blank && !inFence);
        }
        if (!current.isEmpty()) {
            paragraphs.add(current.toString());
        }
        return paragraphs;
    }

    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        int from = 0;
        while (from < text.length()) {
            int newline = text.indexOf('\n', from);
            int to = newline < 0 ? text.length() : newline + 1;
            lines.add(text.substring(from, to));
            from = to;
        }
        return lines;
    }

    private static int leadingBlankLines(String body) {
        int end = 0;
        for (String line : lines(body)) {
            if (!line.isBlank()) {
                break;
            }
            end += line.length();
        }
        return end;
    }

    private static int trailingWhitespace(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.length() - end;
    }
}
