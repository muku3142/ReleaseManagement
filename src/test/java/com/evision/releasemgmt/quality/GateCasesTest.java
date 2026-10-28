package com.evision.releasemgmt.quality;

import com.evision.releasemgmt.quality.GateCases.Case;
import com.evision.releasemgmt.quality.GateCases.CaseSet;
import com.evision.releasemgmt.quality.GateCases.Expect;
import com.evision.releasemgmt.quality.GateCases.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class GateCasesTest {

    private static final String RESOURCE = "quality-gate/cases-v1.json";

    @Test
    void theShippedFixtureIsWellFormed() {
        CaseSet set = GateCases.load(RESOURCE);

        assertThat(set.version()).isEqualTo(1);
        assertThat(GateCases.violations(set)).isEmpty();
        assertThat(set.cases()).extracting(Case::category).containsAll(GateCases.CATEGORIES);
    }

    @Test
    void safetyAndToolFlagsFollowTheCategories() {
        CaseSet set = GateCases.load(RESOURCE);

        assertThat(set.cases()).allSatisfy(c ->
                assertThat(c.safety()).isEqualTo(GateCases.SAFETY_CATEGORIES.contains(c.category())));
        assertThat(set.cases().stream().filter(c -> c.category().equals(GateCases.TOOL_FACT)))
                .allSatisfy(c -> assertThat(c.requiresTools()).isTrue());
        assertThat(set.cases().stream().filter(c -> c.category().equals(GateCases.SCOPE_LEAK)))
                .allSatisfy(c -> assertThat(c.expect().noSourceFromOtherRelease()).isTrue());
    }

    @Test
    void injectionCasesForbidTheirCanary() {
        CaseSet set = GateCases.load(RESOURCE);

        assertThat(set.cases().stream().filter(c -> c.category().equals(GateCases.INJECTION)))
                .allSatisfy(c -> assertThat(c.expect().mustNotContain()).isNotEmpty());
        assertThat(set.cases().stream().flatMap(c -> c.expect().mustNotContain().stream()).toList())
                .containsAll(GateSeed.CANARIES);
    }

    @Test
    void aCountOutsideTheBoundsIsRejected() {
        List<Case> base = GateCases.load(RESOURCE).cases();

        assertThat(GateCases.violations(withCases(base.subList(0, 29)))).anyMatch(v -> v.contains("case count 29"));
        List<Case> tooMany = new ArrayList<>(base);
        IntStream.range(0, 9).forEach(i -> tooMany.add(withId(base.getFirst(), "EXTRA-" + i)));
        assertThat(GateCases.violations(withCases(tooMany))).anyMatch(v -> v.contains("case count 51"));
    }

    @Test
    void aDuplicateIdIsRejected() {
        assertThat(violationsAfterReplacing(1, c -> withId(c, "TF-01"))).anyMatch(v -> v.contains("duplicate id TF-01"));
    }

    @Test
    void aMissingCategoryIsRejected() {
        List<Case> withoutInjection = GateCases.load(RESOURCE).cases().stream()
                .filter(c -> !c.category().equals(GateCases.INJECTION)).toList();
        List<Case> padded = new ArrayList<>(withoutInjection);
        IntStream.range(0, 8).forEach(i -> padded.add(withId(withoutInjection.getFirst(), "PAD-" + i)));

        assertThat(GateCases.violations(withCases(padded))).contains("no case in category injection");
    }

    @Test
    void safetyFlagsThatContradictTheCategoryAreRejected() {
        int notInData = indexOf(GateCases.NOT_IN_DATA);
        int retrieval = indexOf(GateCases.RETRIEVAL);

        assertThat(violationsAfterReplacing(notInData, c -> new Case(c.id(), c.category(), false, c.requiresTools(),
                c.scope(), c.history(), c.question(), c.expect(), c.rubric())))
                .anyMatch(v -> v.contains("contradicts category"));
        assertThat(violationsAfterReplacing(retrieval, c -> new Case(c.id(), c.category(), true, c.requiresTools(),
                c.scope(), c.history(), c.question(), c.expect(), c.rubric())))
                .anyMatch(v -> v.contains("contradicts category"));
    }

    @Test
    void aScopeOutsideTheSeedIsRejected() {
        assertThat(violationsAfterReplacing(indexOf(GateCases.SCOPED), c -> new Case(c.id(), c.category(), c.safety(),
                c.requiresTools(), "Atlas 9.9.9", c.history(), c.question(), c.expect(), c.rubric())))
                .anyMatch(v -> v.contains("scope is not a seeded release"));
    }

    @Test
    void aCitedTicketOutsideTheSeedIsRejected() {
        Expect unknownTicket = new Expect(false, null, null, null, List.of("ZZZ-1"), null, false);

        assertThat(violationsAfterReplacing(indexOf(GateCases.RETRIEVAL), c -> withExpect(c, unknownTicket)))
                .anyMatch(v -> v.contains("cites a ticket outside the seed: ZZZ-1"));
    }

    @Test
    void aFollowUpWithoutHistoryIsRejected() {
        assertThat(violationsAfterReplacing(indexOf(GateCases.FOLLOW_UP), c -> new Case(c.id(), c.category(),
                c.safety(), c.requiresTools(), c.scope(), List.<Message>of(), c.question(), c.expect(), c.rubric())))
                .anyMatch(v -> v.contains("follow-up without history"));
    }

    @Test
    void aScopeLeakWithoutTheNoOtherReleaseFlagIsRejected() {
        Expect lenient = new Expect(null, null, null, null, null, null, false);

        assertThat(violationsAfterReplacing(indexOf(GateCases.SCOPE_LEAK), c -> withExpect(c, lenient)))
                .anyMatch(v -> v.contains("scope-leak case without a scope and noSourceFromOtherRelease=true"));
    }

    @Test
    void anEmptyRubricIsRejected() {
        assertThat(violationsAfterReplacing(0, c -> new Case(c.id(), c.category(), c.safety(), c.requiresTools(),
                c.scope(), c.history(), c.question(), c.expect(), " ")))
                .anyMatch(v -> v.contains("empty rubric"));
    }

    private static int indexOf(String category) {
        List<Case> cases = GateCases.load(RESOURCE).cases();
        return IntStream.range(0, cases.size()).filter(i -> cases.get(i).category().equals(category)).findFirst()
                .orElseThrow();
    }

    private static List<String> violationsAfterReplacing(int index, UnaryOperator<Case> change) {
        List<Case> cases = new ArrayList<>(GateCases.load(RESOURCE).cases());
        cases.set(index, change.apply(cases.get(index)));
        return GateCases.violations(withCases(cases));
    }

    private static CaseSet withCases(List<Case> cases) {
        return new CaseSet(1, null, null, cases);
    }


    private static Case withId(Case c, String id) {
        return new Case(id, c.category(), c.safety(), c.requiresTools(), c.scope(), c.history(), c.question(),
                c.expect(), c.rubric());
    }

    private static Case withExpect(Case c, Expect expect) {
        return new Case(c.id(), c.category(), c.safety(), c.requiresTools(), c.scope(), c.history(), c.question(),
                expect, c.rubric());
    }
}
