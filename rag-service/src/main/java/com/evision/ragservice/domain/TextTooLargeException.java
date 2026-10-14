package com.evision.ragservice.domain;

public class TextTooLargeException extends RuntimeException {

    public TextTooLargeException(int maxChars) {
        super("Text exceeds the maximum of " + maxChars + " characters for this model's context window");
    }
}
