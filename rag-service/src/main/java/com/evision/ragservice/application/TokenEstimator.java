package com.evision.ragservice.application;

public final class TokenEstimator {

    private final double charsPerToken;

    public TokenEstimator(double charsPerToken) {
        this.charsPerToken = charsPerToken;
    }

    public int estimate(String s) {
        return estimateChars(s.length());
    }

    public int estimateChars(int chars) {
        return (int) Math.ceil(chars / charsPerToken);
    }

    public int maxChars(int tokens) {
        return (int) Math.floor(tokens * charsPerToken);
    }
}
