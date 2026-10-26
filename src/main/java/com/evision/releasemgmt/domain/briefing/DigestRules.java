package com.evision.releasemgmt.domain.briefing;

import com.evision.releasemgmt.domain.briefing.BriefingContent.Item;
import com.evision.releasemgmt.domain.briefing.BriefingContent.Section;
import com.evision.releasemgmt.domain.jira.StatusCategory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** The daily digest, computed from Jira fields only. */
public final class DigestRules {

    private static final int ITEM_MAX = 300;
    private static final int HEADLINE_MAX = 140;
    private static final int SUMMARY_MAX = 400;

    private DigestRules() { }

    public static BriefingContent compute(DigestInput in) {
        WorkCalendar calendar = in.settings().calendar();
        LocalDate today = calendar.today(in.now());

        List<BriefingIssue> blockerIssues = in.activeSprintOpen().stream().filter(BriefingText::isBlocker).toList();
        List<Item> blockers = blockerIssues.stream()
                .map(i -> item(i, i.summary() + " (" + BriefingText.blockerReason(i) + ")")).toList();

        int staleAfter = in.settings().thresholds().staleAfterWorkingDays();
        List<BriefingIssue> staleIssues = in.staleCandidates().stream()
                .filter(i -> i.statusCategory() == StatusCategory.IN_PROGRESS && i.updated() != null
                        && calendar.workingDaysElapsed(i.updated(), in.now()) >= staleAfter)
                .toList();
        List<Item> stale = staleIssues.stream().map(i -> item(i, i.summary() + ": no update for "
                + calendar.workingDaysElapsed(i.updated(), in.now()) + " working days ("
                + (i.assignee() == null ? "Unassigned" : i.assignee()) + ")")).toList();

        List<Item> notReady = notReady(in);
        List<Item> changes = changes(in);

        List<BriefingIssue> dueIssues = in.dueCandidates().stream()
                .filter(i -> i.statusCategory() != StatusCategory.DONE && i.dueDate() != null
                        && !i.dueDate().isAfter(today.plusDays(in.settings().thresholds().dueSoonDays())))
                .toList();
        List<Item> due = dueIssues.stream().map(i -> item(i, i.dueDate().isBefore(today)
                ? i.summary() + ": overdue since " + i.dueDate()
                : i.summary() + ": due " + i.dueDate())).toList();

        int attention = blockers.size() + stale.size() + notReady.size() + due.size();
        boolean quiet = attention == 0 && changes.isEmpty();
        BriefingStatus status = attention > 0 ? BriefingStatus.ATTENTION : quiet ? BriefingStatus.QUIET
                : BriefingStatus.OK;

        String counts = blockers.size() + " blockers, " + stale.size() + " stale, " + notReady.size()
                + " not ready, " + due.size() + " due soon or overdue";
        String headline = attention > 0 ? counts + " in " + in.productName()
                : "Nothing needs attention in " + in.productName();
        BriefingIssue overdue = firstOf(dueIssues, i -> i.dueDate().isBefore(today));
        BriefingIssue top = !blockerIssues.isEmpty() ? blockerIssues.get(0)
                : overdue != null ? overdue : staleIssues.isEmpty() ? null : staleIssues.get(0);
        String summary = counts + ", " + changes.size() + " changes since the last run"
                + (top == null ? "" : "\nMost important: " + top.key() + " " + top.summary());

        return new BriefingContent(status, attention, BriefingText.clip(headline, HEADLINE_MAX),
                BriefingText.clip(summary, SUMMARY_MAX),
                List.of(new Section("Blockers", blockers), new Section("Stale in progress", stale),
                        new Section("Not ready", notReady), new Section("Changes since the last run", changes),
                        new Section("Due soon or overdue", due)));
    }

    private static List<Item> notReady(DigestInput in) {
        Map<String, BriefingIssue> stories = new LinkedHashMap<>();
        for (List<BriefingIssue> list : List.of(in.activeSprintOpen(), in.nextSprint())) {
            list.stream().filter(BriefingText::isStory).forEach(i -> stories.putIfAbsent(i.key(), i));
        }
        List<Item> items = new ArrayList<>();
        for (BriefingIssue story : stories.values()) {
            List<String> missing = new ArrayList<>();
            if (in.fields().acceptanceCriteria() != null && !story.acceptanceCriteriaPresent()) {
                missing.add("acceptance criteria");
            }
            if (in.fields().storyPoints() != null && story.storyPoints() == null) {
                missing.add("estimate");
            }
            if (story.parentKey() == null) {
                missing.add("epic");
            }
            if (!missing.isEmpty()) {
                items.add(item(story, story.summary() + ": missing " + String.join(", ", missing)));
            }
        }
        return items;
    }

    private static List<Item> changes(DigestInput in) {
        List<Item> items = new ArrayList<>();
        in.created().stream().filter(i -> i.created() != null && !i.created().isBefore(in.since()))
                .forEach(i -> items.add(item(i, i.summary() + ": created")));
        in.done().stream().filter(i -> i.resolved() != null && !i.resolved().isBefore(in.since()))
                .forEach(i -> items.add(item(i, i.summary() + ": moved to Done")));
        in.reopened().stream().filter(i -> i.updated() != null && !i.updated().isBefore(in.since()))
                .forEach(i -> items.add(item(i, i.summary() + ": reopened")));
        return items;
    }

    private static Item item(BriefingIssue issue, String text) {
        return new Item(issue.key(), BriefingText.clip(text, ITEM_MAX));
    }

    private static BriefingIssue firstOf(List<BriefingIssue> issues, Predicate<BriefingIssue> test) {
        return issues.stream().filter(test).findFirst().orElse(null);
    }
}
