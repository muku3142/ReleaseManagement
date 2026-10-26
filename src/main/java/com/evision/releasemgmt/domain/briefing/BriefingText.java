package com.evision.releasemgmt.domain.briefing;

import java.util.Locale;

public final class BriefingText {

    private static final String ELLIPSIS = "…";

    private BriefingText() { }

    public static String clip(String s, int maxCodePoints) {
        if (s == null) {
            return "";
        }
        String stripped = s.strip();
        if (stripped.codePointCount(0, stripped.length()) <= maxCodePoints) {
            return stripped;
        }
        int end = stripped.offsetByCodePoints(0, maxCodePoints);
        return stripped.substring(0, end) + ELLIPSIS;
    }

    public static boolean isBlocker(BriefingIssue i) {
        return blockerReason(i) != null;
    }

    /** First reason that applies, or null when the issue is not a blocker. */
    public static String blockerReason(BriefingIssue i) {
        if (i.flagged()) {
            return "flagged";
        }
        if (i.priority() != null) {
            String p = i.priority().toLowerCase(Locale.ROOT);
            if (p.equals("highest")) {
                return "priority Highest";
            }
            if (p.equals("blocker")) {
                return "priority Blocker";
            }
        }
        if ("Blocked".equalsIgnoreCase(i.status())) {
            return "status Blocked";
        }
        return null;
    }

    public static boolean isStory(BriefingIssue i) {
        return "Story".equalsIgnoreCase(i.issueType());
    }
}
