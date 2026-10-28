package com.evision.releasemgmt.domain.briefing;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum BriefingStatus {
    OK, ATTENTION, QUIET, FAILED, NEEDS_CREDENTIAL, SKIPPED;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<BriefingStatus> fromWire(String s) {
        return Arrays.stream(values()).filter(v -> v.wire().equals(s)).findFirst();
    }

    // Claude rows only ever carry these four; "setup" placeholders and anything else are not runs.
    public static Optional<BriefingStatus> fromClaudeRow(String s) {
        return fromWire(s).filter(v -> v == OK || v == ATTENTION || v == QUIET || v == FAILED);
    }
}
