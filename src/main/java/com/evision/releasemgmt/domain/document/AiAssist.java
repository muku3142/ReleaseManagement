package com.evision.releasemgmt.domain.document;

import java.time.Instant;

public record AiAssist(AiMode mode, String instruction, int sourceRevision, Instant basisGeneratedAt) {

    public AiAssist {
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("instruction is required");
        }
        if (sourceRevision < 1) {
            throw new IllegalArgumentException("sourceRevision must be >= 1");
        }
        if (basisGeneratedAt == null) {
            throw new IllegalArgumentException("basisGeneratedAt is required");
        }
    }

    // SAFE-03: the instruction is user text and must not reach logs.
    @Override
    public String toString() {
        return "AiAssist[mode=" + mode + ", sourceRevision=" + sourceRevision
                + ", basisGeneratedAt=" + basisGeneratedAt + ", instructionChars=" + instruction.length() + "]";
    }
}
