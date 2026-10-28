package com.evision.releasemgmt.domain.briefing;

import com.evision.releasemgmt.domain.briefing.BriefingContent.Item;
import com.evision.releasemgmt.domain.briefing.BriefingContent.Section;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Rates how ready the next sprint's (or the top backlog) items are, from Jira fields and description text only. */
public final class SprintCheckRules {

    public enum Readiness {
        YES("Yes"), PARTLY("Partly"), NO("No");

        private final String label;

        Readiness(String label) {
            this.label = label;
        }
    }

    public static final int READINESS_SIGNALS = 3;

    private static final int ITEM_MAX = 300;
    private static final int HEADLINE_MAX = 140;
    private static final int FIX_FIRST = 3;
    private static final Pattern QUESTION_LINE = Pattern.compile("\\?\\s*$", Pattern.MULTILINE);
    private static final Pattern OPEN_MARKER = Pattern.compile("\\b(tbd|tbc)\\b|open question");
    private static final Pattern PAYMENT = Pattern.compile("payment|transfer|\\biso\\b");
    private static final List<String> NEGATIVE_WORDS = List.of("invalid", "error", "fail", "reject", "should not",
            "must not", "negative");

    private SprintCheckRules() { }

    private record Check(List<String> essentialMissing, List<String> advisoryMissing, List<String> notChecked,
            List<String> notMentioned) { }

    private record Rated(BriefingIssue issue, Readiness readiness, Check check) { }

    public static Readiness rate(BriefingIssue issue, BriefingFieldIds fields) {
        return readiness(check(issue, fields));
    }

    // A signal the site cannot check (no AC or Story Points field) counts as not met.
    public static int readinessSignals(BriefingIssue issue, BriefingFieldIds fields) {
        Check c = check(issue, fields);
        return READINESS_SIGNALS - c.essentialMissing().size() - c.notChecked().size();
    }

    public static BriefingContent compute(SprintCheckInput in) {
        String scopeName;
        String checkedLine;
        List<BriefingIssue> scope;
        BriefingIssue.Sprint sprint = in.nextSprint().isEmpty() ? null : earliestFuture(in.nextSprint());
        if (!in.nextSprint().isEmpty()) {
            scopeName = sprint == null ? "the next sprint" : sprint.name();
            checkedLine = "Checked: next sprint " + (sprint == null ? "" : sprint.name());
            scope = sprint == null ? in.nextSprint() : in.nextSprint().stream()
                    .filter(i -> i.sprints().stream().anyMatch(s -> s.id() == sprint.id())).toList();
        } else {
            scope = in.backlog().stream().limit(in.settings().thresholds().backlogItems()).toList();
            scopeName = "the backlog";
            checkedLine = "Checked: top " + scope.size() + " backlog items (no future sprint)";
        }

        List<Rated> rated = scope.stream().map(i -> {
            Check c = check(i, in.fields());
            return new Rated(i, readiness(c), c);
        }).sorted(Comparator.comparing(r -> r.readiness().ordinal(), Comparator.reverseOrder())).toList();

        List<Item> items = new ArrayList<>();
        if (!rated.isEmpty()) {
            items.add(new Item(null, BriefingText.clip(checkedLine.strip(), ITEM_MAX)));
            rated.forEach(r -> items.add(new Item(r.issue().key(), BriefingText.clip(describe(r), ITEM_MAX))));
        }

        long yes = count(rated, Readiness.YES);
        long partly = count(rated, Readiness.PARTLY);
        long no = count(rated, Readiness.NO);
        int attention = (int) (partly + no);
        BriefingStatus status = rated.isEmpty() ? BriefingStatus.QUIET
                : attention > 0 ? BriefingStatus.ATTENTION : BriefingStatus.OK;
        String headline = rated.isEmpty() ? "No sprint or backlog items to check in " + in.productName()
                : attention > 0 ? attention + " of " + rated.size() + " items not ready for " + scopeName + " in "
                        + in.productName()
                : "All " + rated.size() + " items ready for " + scopeName + " in " + in.productName();

        String summary = yes + " Yes, " + partly + " Partly, " + no + " No";
        List<String> fixFirst = rated.stream().filter(r -> r.readiness() != Readiness.YES).limit(FIX_FIRST)
                .map(r -> r.issue().key()).toList();
        if (!fixFirst.isEmpty()) {
            summary += "\nFix first: " + String.join(", ", fixFirst);
        }
        return new BriefingContent(status, attention, BriefingText.clip(headline, HEADLINE_MAX), summary,
                List.of(new Section("Sprint or backlog items checked", items)));
    }

    private static BriefingIssue.Sprint earliestFuture(List<BriefingIssue> issues) {
        Comparator<BriefingIssue.Sprint> order = Comparator
                .comparing(BriefingIssue.Sprint::startDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingLong(BriefingIssue.Sprint::id);
        return issues.stream().flatMap(i -> i.sprints().stream()).filter(s -> "future".equalsIgnoreCase(s.state()))
                .min(order).orElse(null);
    }

    private static long count(List<Rated> rated, Readiness readiness) {
        return rated.stream().filter(r -> r.readiness() == readiness).count();
    }

    private static Readiness readiness(Check c) {
        if (c.essentialMissing().size() >= 2) {
            return Readiness.NO;
        }
        return c.essentialMissing().isEmpty() && c.advisoryMissing().size() <= 1 ? Readiness.YES : Readiness.PARTLY;
    }

    private static String describe(Rated r) {
        Check c = r.check();
        List<String> missing = new ArrayList<>(c.essentialMissing());
        missing.addAll(c.advisoryMissing());
        StringBuilder text = new StringBuilder(r.issue().summary()).append(": ").append(r.readiness().label)
                .append(". Missing: ").append(missing.isEmpty() ? "nothing" : String.join(", ", missing));
        if (!c.notChecked().isEmpty()) {
            text.append("; not checked: ").append(String.join(", ", c.notChecked()));
        }
        if (!c.notMentioned().isEmpty()) {
            text.append("; not mentioned: ").append(String.join(", ", c.notMentioned()));
        }
        return text.toString();
    }

    private static Check check(BriefingIssue i, BriefingFieldIds fields) {
        List<String> essential = new ArrayList<>();
        List<String> notChecked = new ArrayList<>();
        if (fields.acceptanceCriteria() == null) {
            notChecked.add("acceptance criteria");
        } else if (!i.acceptanceCriteriaPresent()) {
            essential.add("acceptance criteria");
        }
        if (fields.storyPoints() == null) {
            notChecked.add("estimate");
        } else if (i.storyPoints() == null) {
            essential.add("estimate");
        }
        if (i.parentKey() == null) {
            essential.add("epic");
        }

        String description = i.description() == null ? "" : i.description();
        String lower = description.toLowerCase(Locale.ROOT);
        List<String> advisory = new ArrayList<>();
        if (i.subtaskCount() <= 0) {
            advisory.add("sub-tasks");
        }
        if (QUESTION_LINE.matcher(description).find() || OPEN_MARKER.matcher(lower).find()) {
            advisory.add("open questions resolved");
        }
        if (!(lower.contains("as a") && (lower.contains("i want") || lower.contains("so that")))) {
            advisory.add("story statement");
        }
        if (NEGATIVE_WORDS.stream().noneMatch(lower::contains)) {
            advisory.add("negative case");
        }
        return new Check(essential, advisory, notChecked, notMentioned(i, lower));
    }

    private static List<String> notMentioned(BriefingIssue i, String lowerDescription) {
        String text = (i.summary() == null ? "" : i.summary().toLowerCase(Locale.ROOT)) + "\n" + lowerDescription;
        List<String> out = new ArrayList<>();
        if (!PAYMENT.matcher(text).find()) {
            return out;
        }
        if (!text.contains("idempoten")) {
            out.add("idempotency");
        }
        if (!text.contains("audit")) {
            out.add("audit");
        }
        if (!text.contains("maker-checker") && !text.contains("maker checker")) {
            out.add("maker-checker");
        }
        if (!text.contains("return") && !text.contains("reversal")) {
            out.add("return path");
        }
        if (!text.contains("iso 20022")) {
            out.add("ISO 20022");
        }
        return out;
    }
}
