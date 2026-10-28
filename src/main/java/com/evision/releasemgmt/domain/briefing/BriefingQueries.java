package com.evision.releasemgmt.domain.briefing;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** JQL for briefings. Keys are validated and quoted; date prefilters are relative so no date text is built. */
public final class BriefingQueries {

    public static final Pattern PROJECT_KEY = Pattern.compile("^[A-Z][A-Z0-9_]{1,9}$");
    public static final Pattern ISSUE_KEY = Pattern.compile("^[A-Z][A-Z0-9_]{1,9}-\\d+$");
    public static final int UNCAPPED = Integer.MAX_VALUE;

    private static final String NOT_DONE = " AND statusCategory != Done";
    private static final String RANKED = " ORDER BY Rank ASC";
    private static final int MAX_DAYS_BACK = 30;

    private BriefingQueries() { }

    public static String activeSprintOpen(Collection<String> keys) {
        return scope(keys) + " AND sprint in openSprints()" + NOT_DONE + RANKED;
    }

    public static String activeSprintAll(Collection<String> keys) {
        return scope(keys) + " AND sprint in openSprints()";
    }

    public static String nextSprint(Collection<String> keys) {
        return scope(keys) + " AND sprint in futureSprints()" + NOT_DONE + RANKED;
    }

    public static String backlog(Collection<String> keys) {
        return scope(keys) + NOT_DONE + " AND (sprint is EMPTY OR sprint not in (openSprints(), futureSprints()))"
                + RANKED;
    }

    public static String staleCandidates(Collection<String> keys, int staleAfterWorkingDays) {
        return inProgress(keys) + " AND updated <= -" + staleAfterWorkingDays + "d";
    }

    public static String inProgress(Collection<String> keys) {
        return scope(keys) + " AND statusCategory = \"In Progress\"";
    }

    public static String createdSince(Collection<String> keys, int daysBack) {
        return scope(keys) + " AND created >= -" + daysBack + "d";
    }

    public static String doneSince(Collection<String> keys, int daysBack) {
        return scope(keys) + " AND statusCategory = Done AND resolutiondate >= -" + daysBack + "d";
    }

    public static String reopenCandidates(Collection<String> keys, int daysBack) {
        return scope(keys) + NOT_DONE + " AND updated >= -" + daysBack + "d ORDER BY updated DESC";
    }

    public static String dueWithin(Collection<String> keys, int days) {
        return scope(keys) + NOT_DONE + " AND duedate <= " + days + "d";
    }

    public static int daysBack(Instant since, Instant now) {
        long seconds = Math.max(0, Duration.between(since, now).getSeconds());
        long days = (seconds + 86_399) / 86_400 + 1;
        return (int) Math.min(MAX_DAYS_BACK, days);
    }

    private static String scope(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("at least one project key is required");
        }
        for (String key : keys) {
            if (key == null || !PROJECT_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("invalid project key");
            }
        }
        return keys.stream().map(k -> "\"" + k + "\"").collect(Collectors.joining(",", "project in (", ")"));
    }
}
