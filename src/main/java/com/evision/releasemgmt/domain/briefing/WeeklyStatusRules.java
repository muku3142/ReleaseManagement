package com.evision.releasemgmt.domain.briefing;

import com.evision.releasemgmt.domain.briefing.BriefingContent.Item;
import com.evision.releasemgmt.domain.briefing.BriefingContent.Section;
import com.evision.releasemgmt.domain.jira.StatusCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The weekly status for the week that started on Sunday, computed from Jira fields only; unknowns read TBC. */
public final class WeeklyStatusRules {

    public enum Overall {
        ON_TRACK("On track"), AT_RISK("At risk"), OFF_TRACK("Off track"), TBC("TBC");

        private final String label;

        Overall(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final int ITEM_MAX = 300;
    private static final int HEADLINE_MAX = 140;
    private static final int OFF_TRACK_RISKS = 3;
    private static final int SPRINT_END_WORKING_DAYS = 2;
    private static final double HALF = 0.5;
    private static final String TBC = "TBC";

    private WeeklyStatusRules() { }

    private record Facts(List<BriefingIssue> completed, List<BriefingIssue> inProgress, List<BriefingIssue> blocked,
            List<BriefingIssue> added, List<BriefingIssue> overdue, List<BriefingIssue> sprint,
            BriefingIssue.Sprint activeSprint, Overall overall, String reason) { }

    public static Overall overall(WeeklyInput in) {
        return facts(in).overall();
    }

    public static BriefingContent compute(WeeklyInput in) {
        Facts f = facts(in);
        long decisions = f.blocked().stream().filter(i -> i.assignee() == null).count();
        int risks = f.blocked().size() + f.overdue().size();
        int attention = (int) (risks + decisions);
        boolean quiet = f.completed().isEmpty() && f.inProgress().isEmpty() && f.blocked().isEmpty()
                && f.added().isEmpty() && f.overdue().isEmpty();
        BriefingStatus status = attention > 0 || f.overall() == Overall.AT_RISK || f.overall() == Overall.OFF_TRACK
                ? BriefingStatus.ATTENTION : quiet ? BriefingStatus.QUIET : BriefingStatus.OK;

        String headline = f.overall().label() + ": " + f.completed().size() + " completed, " + f.blocked().size()
                + " blocked, " + f.overdue().size() + " overdue in " + in.productName();
        String summary = f.completed().size() + " completed, " + f.inProgress().size() + " in progress, "
                + f.blocked().size() + " blocked, " + f.added().size() + " newly added, " + f.overdue().size()
                + " overdue";

        List<Item> blockedItems = f.blocked().stream()
                .map(i -> item(i, i.summary() + " (" + BriefingText.blockerReason(i) + ")")).toList();
        return new BriefingContent(status, attention, BriefingText.clip(headline, HEADLINE_MAX), summary, List.of(
                new Section("Completed", summaries(f.completed())),
                new Section("In progress", summaries(f.inProgress())),
                new Section("Blocked", blockedItems),
                new Section("Newly added", summaries(f.added())),
                new Section("Sprint committed vs done", List.of(plain(scope(f)))),
                new Section("Overdue", summaries(f.overdue())),
                new Section("Executive draft", executive(f, in.settings().calendar())),
                new Section("Engineering draft", engineering(f))));
    }

    private static Facts facts(WeeklyInput in) {
        WorkCalendar calendar = in.settings().calendar();
        Instant weekStart = calendar.weekStart(in.now());
        LocalDate today = calendar.today(in.now());

        List<BriefingIssue> completed = in.completed().stream().filter(i -> i.statusCategory() == StatusCategory.DONE
                && i.resolved() != null && !i.resolved().isBefore(weekStart)).toList();
        List<BriefingIssue> inProgress = in.inProgress().stream()
                .filter(i -> i.statusCategory() == StatusCategory.IN_PROGRESS).toList();
        Map<String, BriefingIssue> blockedByKey = new LinkedHashMap<>();
        in.inProgress().stream().filter(BriefingText::isBlocker).forEach(i -> blockedByKey.putIfAbsent(i.key(), i));
        in.activeSprintAll().stream().filter(i -> i.statusCategory() != StatusCategory.DONE)
                .filter(BriefingText::isBlocker).forEach(i -> blockedByKey.putIfAbsent(i.key(), i));
        List<BriefingIssue> blocked = new ArrayList<>(blockedByKey.values());
        List<BriefingIssue> added = in.added().stream()
                .filter(i -> i.created() != null && !i.created().isBefore(weekStart)).toList();
        List<BriefingIssue> overdue = in.dueCandidates().stream().filter(i -> i.statusCategory() != StatusCategory.DONE
                && i.dueDate() != null && i.dueDate().isBefore(today)).toList();

        BriefingIssue.Sprint sprint = activeSprint(in.activeSprintAll());
        int risks = blocked.size() + overdue.size();
        long done = in.activeSprintAll().stream().filter(i -> i.statusCategory() == StatusCategory.DONE).count();
        Overall overall;
        String reason;
        if (in.activeSprintAll().isEmpty() && risks == 0) {
            overall = Overall.TBC;
            reason = "no active sprint found";
        } else if (risks >= OFF_TRACK_RISKS) {
            overall = Overall.OFF_TRACK;
            reason = risks + " blocked or overdue items";
        } else if (!in.activeSprintAll().isEmpty() && (double) done / in.activeSprintAll().size() < HALF
                && sprint != null && sprint.endDate() != null
                && calendar.workingDaysElapsed(in.now(), sprint.endDate()) <= SPRINT_END_WORKING_DAYS) {
            overall = Overall.OFF_TRACK;
            reason = "only " + done + " of " + in.activeSprintAll().size() + " sprint issues done and the sprint ends "
                    + calendar.today(sprint.endDate());
        } else if (risks >= 1) {
            overall = Overall.AT_RISK;
            reason = risks + " blocked or overdue " + (risks == 1 ? "item" : "items");
        } else {
            overall = Overall.ON_TRACK;
            reason = "no blocked or overdue items";
        }
        return new Facts(completed, inProgress, blocked, added, overdue, in.activeSprintAll(), sprint, overall,
                reason);
    }

    private static BriefingIssue.Sprint activeSprint(List<BriefingIssue> issues) {
        return issues.stream().flatMap(i -> i.sprints().stream()).filter(s -> "active".equalsIgnoreCase(s.state()))
                .findFirst().orElse(null);
    }

    private static String scope(Facts f) {
        if (f.sprint().isEmpty()) {
            return "TBC: no active sprint found";
        }
        List<BriefingIssue> done = f.sprint().stream().filter(i -> i.statusCategory() == StatusCategory.DONE)
                .toList();
        return "Current sprint scope: " + f.sprint().size() + " issues (" + points(f.sprint()) + " points); done "
                + done.size() + " (" + points(done) + " points)";
    }

    private static String points(List<BriefingIssue> issues) {
        if (issues.stream().anyMatch(i -> i.storyPoints() == null)) {
            return TBC;
        }
        return issues.stream().map(BriefingIssue::storyPoints).reduce(BigDecimal.ZERO, BigDecimal::add)
                .stripTrailingZeros().toPlainString();
    }

    private static List<Item> executive(Facts f, WorkCalendar calendar) {
        List<String> needDecision = f.blocked().stream().filter(i -> i.assignee() == null).map(BriefingIssue::key)
                .toList();
        String milestone = f.activeSprint() == null || f.activeSprint().endDate() == null ? TBC
                : f.activeSprint().name() + " ends " + calendar.today(f.activeSprint().endDate());
        return List.of(
                plain("Overall status: " + f.overall().label() + " — " + f.reason()),
                plain("Outcomes delivered: " + f.completed().size() + " issues completed"),
                plain(f.blocked().isEmpty() && f.overdue().isEmpty() ? "Top risks: none known"
                        : "Top risks: " + f.blocked().size() + " blocked, " + f.overdue().size() + " overdue"),
                plain(needDecision.isEmpty() ? "Decisions needed: none"
                        : "Decisions needed: " + String.join(", ", needDecision) + " (owner TBC, date TBC)"),
                plain("Next milestone: " + milestone));
    }

    private static List<Item> engineering(Facts f) {
        return List.of(
                plain("Completed: " + keys(f.completed(), BriefingIssue::key)),
                plain("In progress: " + keys(f.inProgress(), BriefingIssue::key)),
                plain("Blocked: " + keys(f.blocked(), i -> i.key() + " (" + BriefingText.blockerReason(i) + ")")),
                plain("Next week's focus: TBC"),
                plain("Dependencies: TBC"));
    }

    private static String keys(List<BriefingIssue> issues, Function<BriefingIssue, String> label) {
        return issues.isEmpty() ? "none" : String.join(", ", issues.stream().map(label).toList());
    }

    private static List<Item> summaries(List<BriefingIssue> issues) {
        return issues.stream().map(i -> item(i, i.summary())).toList();
    }

    private static Item item(BriefingIssue issue, String text) {
        return new Item(issue.key(), BriefingText.clip(text, ITEM_MAX));
    }

    private static Item plain(String text) {
        return new Item(null, BriefingText.clip(text, ITEM_MAX));
    }
}
