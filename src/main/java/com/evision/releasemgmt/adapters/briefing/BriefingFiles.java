package com.evision.releasemgmt.adapters.briefing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

// Bounded, symlink-refusing reads of files the Claude tasks write, and cleaning of the text inside them.
final class BriefingFiles {

    static final long MAX_BYTES = 1_048_576;

    @FunctionalInterface
    interface Reader {
        Optional<byte[]> read(Path base, String fileName) throws IOException;
    }

    private BriefingFiles() {
    }

    // Empty when the directory or file does not exist; IOException for anything unusable.
    static Optional<byte[]> readBounded(Path base, String fileName) throws IOException {
        Path root = base.normalize();
        Path path = root.resolve(fileName).normalize();
        if (!path.startsWith(root)) {
            throw new IOException("path escapes the briefing directory");
        }
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attrs.isSymbolicLink() || !attrs.isRegularFile()) {
            throw new IOException("not a regular file");
        }
        try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes((int) MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new IOException("file too large");
            }
            return Optional.of(bytes);
        }
    }

    static String cleanText(String s, int maxCodePoints) {
        if (s == null) {
            return null;
        }
        String cleaned = s.codePoints()
                .filter(cp -> !isControlOrBidi(cp))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .strip();
        String cut = cleaned.codePoints().limit(maxCodePoints)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        return cut.isEmpty() ? null : cut;
    }

    private static boolean isControlOrBidi(int cp) {
        return cp <= 0x1F || cp == 0x7F || (cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069);
    }
}
