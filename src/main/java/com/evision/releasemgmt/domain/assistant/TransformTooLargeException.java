package com.evision.releasemgmt.domain.assistant;

public class TransformTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int characters;
    private final int maxCharacters;

    public TransformTooLargeException(int characters, int maxCharacters) {
        super("The document is too long for the assistant.");
        this.characters = characters;
        this.maxCharacters = maxCharacters;
    }

    public int getCharacters() {
        return characters;
    }

    public int getMaxCharacters() {
        return maxCharacters;
    }
}
