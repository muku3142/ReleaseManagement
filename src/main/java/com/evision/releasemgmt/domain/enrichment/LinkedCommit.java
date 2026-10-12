package com.evision.releasemgmt.domain.enrichment;

import java.time.Instant;

// The author date is kept because a later phase classifies commits against first-Done.
public record LinkedCommit(String sha, String message, String author, Instant authoredAt, String url) {

    private static final int MESSAGE_LIMIT = 500;
    private static final int AUTHOR_LIMIT = 255;
    private static final int SHORT_SHA_LENGTH = 7;

    public LinkedCommit {
        message = firstLine(message);
        author = author != null && author.length() > AUTHOR_LIMIT ? author.substring(0, AUTHOR_LIMIT) : author;
    }

    public String shortSha() {
        return sha.length() <= SHORT_SHA_LENGTH ? sha : sha.substring(0, SHORT_SHA_LENGTH);
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        String line = message.split("\\r?\\n", 2)[0].trim();
        return line.length() > MESSAGE_LIMIT ? line.substring(0, MESSAGE_LIMIT) : line;
    }
}
