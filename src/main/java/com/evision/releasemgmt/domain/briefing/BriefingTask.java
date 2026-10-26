package com.evision.releasemgmt.domain.briefing;

import java.util.Arrays;
import java.util.Optional;

// The five briefings are fixed; their ids are the only file names the app ever reads.
public enum BriefingTask {
    JIRA_DIGEST("jira_digest", "Daily Jira digest", "Sun-Thu 08:44 · Africa/Cairo", true),
    SPRINT_CHECK("sprint_check", "Sprint backlog check", "Wed 09:41 · Africa/Cairo", true),
    WEEKLY_STATUS("weekly_status", "Weekly status update", "Thu 13:41 · Africa/Cairo", true),
    REG_WATCH("reg_watch", "Payments regulation watch", "Sun 08:36 · Africa/Cairo", false),
    GULF_INVEST("gulf_invest", "Saudi & Gulf investment brief", "Mon 09:33 · Africa/Cairo", false);

    private final String id;
    private final String displayName;
    private final String schedule;
    private final boolean appFallback;

    BriefingTask(String id, String displayName, String schedule, boolean appFallback) {
        this.id = id;
        this.displayName = displayName;
        this.schedule = schedule;
        this.appFallback = appFallback;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String schedule() {
        return schedule;
    }

    public boolean appFallback() {
        return appFallback;
    }

    public static Optional<BriefingTask> fromId(String id) {
        return Arrays.stream(values()).filter(t -> t.id.equals(id)).findFirst();
    }
}
