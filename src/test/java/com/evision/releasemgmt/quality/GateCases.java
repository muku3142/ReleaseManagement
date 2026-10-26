package com.evision.releasemgmt.quality;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The SAFE-04 question set: loader plus the well-formedness rules the fixture test enforces. */
public final class GateCases {

    public static final String TOOL_FACT = "tool-fact";
    public static final String RETRIEVAL = "retrieval";
    public static final String SCOPED = "scoped";
    public static final String FOLLOW_UP = "follow-up";
    public static final String NOT_IN_DATA = "not-in-data";
    public static final String INJECTION = "injection";
    public static final String SCOPE_LEAK = "scope-leak";

    public static final List<String> CATEGORIES =
            List.of(TOOL_FACT, RETRIEVAL, SCOPED, FOLLOW_UP, NOT_IN_DATA, INJECTION, SCOPE_LEAK);

    public static final Set<String> SAFETY_CATEGORIES = Set.of(NOT_IN_DATA, INJECTION, SCOPE_LEAK);

    static final int MIN_CASES = 30;
    static final int MAX_CASES = 50;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private GateCases() {
    }

    public record CaseSet(int version, String labeling, String labelsApprovedBy, List<Case> cases) {
    }

    public record Message(String role, String content) {
    }

    /** scope is a seeded "name version" or null; history is the prior turns, oldest first. */
    public record Case(String id, String category, boolean safety, boolean requiresTools, String scope,
                       List<Message> history, String question, Expect expect, String rubric) {

        public Case {
            history = history == null ? List.of() : List.copyOf(history);
        }
    }

    /**
     * refusal null means "either is acceptable". mustContain is a case-insensitive substring match per entry,
     * mustNotContain the same negated. sourceKinds: every listed kind must occur among done.sources. citedTicketKeys:
     * every listed key must occur among the source ticket keys. toolsMustRun: every listed tool name must appear in
     * done.tools.
     */
    public record Expect(Boolean refusal, List<String> mustContain, List<String> mustNotContain,
                         List<String> sourceKinds, List<String> citedTicketKeys, List<String> toolsMustRun,
                         boolean noSourceFromOtherRelease) {

        public Expect {
            mustContain = mustContain == null ? List.of() : List.copyOf(mustContain);
            mustNotContain = mustNotContain == null ? List.of() : List.copyOf(mustNotContain);
            sourceKinds = sourceKinds == null ? List.of() : List.copyOf(sourceKinds);
            citedTicketKeys = citedTicketKeys == null ? List.of() : List.copyOf(citedTicketKeys);
            toolsMustRun = toolsMustRun == null ? List.of() : List.copyOf(toolsMustRun);
        }
    }

    public static CaseSet load(String classpathResource) {
        try (InputStream in = GateCases.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalArgumentException("missing resource " + classpathResource);
            }
            return MAPPER.readValue(in, CaseSet.class);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + classpathResource, e);
        }
    }

    /** Every way the set can be malformed; empty means well-formed. */
    public static List<String> violations(CaseSet set) {
        List<String> problems = new ArrayList<>();
        List<Case> cases = set.cases() == null ? List.of() : set.cases();
        if (cases.size() < MIN_CASES || cases.size() > MAX_CASES) {
            problems.add("case count " + cases.size() + " outside " + MIN_CASES + ".." + MAX_CASES);
        }
        Set<String> ids = new HashSet<>();
        Set<String> seenCategories = new HashSet<>();
        for (Case c : cases) {
            checkCase(c, problems);
            if (!ids.add(c.id())) {
                problems.add("duplicate id " + c.id());
            }
            seenCategories.add(c.category());
        }
        CATEGORIES.stream().filter(category -> !seenCategories.contains(category))
                .forEach(category -> problems.add("no case in category " + category));
        return problems;
    }

    private static void checkCase(Case c, List<String> problems) {
        String id = c.id();
        if (id == null || id.isBlank() || c.question() == null || c.question().isBlank()) {
            problems.add("case " + id + " has a blank id or question");
        }
        if (!CATEGORIES.contains(c.category())) {
            problems.add(id + " has unknown category " + c.category());
            return;
        }
        if (c.safety() != SAFETY_CATEGORIES.contains(c.category())) {
            problems.add(id + " safety=" + c.safety() + " contradicts category " + c.category());
        }
        if (c.scope() != null && !GateSeed.RELEASE_NAMES.contains(c.scope())) {
            problems.add(id + " scope is not a seeded release: " + c.scope());
        }
        if (c.expect() == null) {
            problems.add(id + " has no expect");
            return;
        }
        c.expect().citedTicketKeys().stream().filter(key -> !GateSeed.TICKET_KEYS.contains(key))
                .forEach(key -> problems.add(id + " cites a ticket outside the seed: " + key));
        if (FOLLOW_UP.equals(c.category()) && c.history().isEmpty()) {
            problems.add(id + " is a follow-up without history");
        }
        if (SCOPE_LEAK.equals(c.category()) && (!c.expect().noSourceFromOtherRelease() || c.scope() == null)) {
            problems.add(id + " is a scope-leak case without a scope and noSourceFromOtherRelease=true");
        }
        if (c.rubric() == null || c.rubric().isBlank()) {
            problems.add(id + " has an empty rubric");
        }
    }
}
