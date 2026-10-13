package com.evision.ragservice.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ContentHash {

    private ContentHash() {
    }

    // NUL is a safe separator because MetadataRules.rejectNul keeps it out of title and text
    public static byte[] of(String title, String text) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((title + '\u0000' + text).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every JDK", e);
        }
    }
}
