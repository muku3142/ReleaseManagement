package com.evision.releasemgmt.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.evision.releasemgmt.golden.TransformGoldenCases.Binding;
import com.evision.releasemgmt.golden.TransformGoldenCases.Case;
import com.evision.releasemgmt.golden.TransformGoldenCases.Fixture;
import com.evision.releasemgmt.golden.TransformGoldenCases.Mode;
import com.evision.releasemgmt.golden.TransformGoldenEvaluator.CaseResult;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TransformGoldenEvaluatorTest {

    private static final Fixture FIXTURE = TransformGoldenCases.load();
    private static final Pattern EMAIL = Pattern.compile("@[A-Za-z0-9-]+\\.(?:com|net|org)");
    private static final int CLEAN_UP_LIMIT_RUNBOOK = 5277;
    private static final int CLEAN_UP_LIMIT_RELEASE_NOTES = 5325;

    private static Case caseOf(String id) {
        return FIXTURE.cases().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static String original(String id) {
        return TransformGoldenCases.documentText(caseOf(id));
    }

    private static CaseResult stop(String id, String result) {
        return TransformGoldenEvaluator.evaluate(caseOf(id), original(id), result, "stop", false);
    }

    private static void assertFails(CaseResult result, String dimension) {
        assertThat(result.checks()).containsEntry(dimension, false);
    }

    private static void assertPasses(CaseResult result, String dimension) {
        assertThat(result.checks()).containsEntry(dimension, true);
    }

    // --- fixture ---------------------------------------------------------------------------------

    @Test
    void fixtureLoadsWithEightCaseModesOverSixDocuments() {
        assertThat(FIXTURE.cases()).hasSize(8);
        assertThat(FIXTURE.cases().stream().map(Case::id)).doesNotHaveDuplicates();
        assertThat(FIXTURE.cases().stream().map(Case::doc).distinct()).hasSize(6);
        assertThat(FIXTURE.labelsApprovedBy()).isEmpty();
    }

    @Test
    void customCasesCarryAnInstruction() {
        FIXTURE.cases().stream()
                .filter(c -> c.mode() == Mode.CUSTOM)
                .forEach(c -> assertThat(c.instruction()).isNotBlank());
    }

    @Test
    void fixtureDocumentsContainNoEmailAddresses() {
        FIXTURE.cases().forEach(c -> assertThat(EMAIL.matcher(TransformGoldenCases.documentText(c)).find()).isFalse());
    }

    @Test
    void declaredFactsMatchWhatTheDocumentsContain() {
        for (Case c : FIXTURE.cases()) {
            if (c.expect().tooLarge()) {
                continue;
            }
            String text = TransformGoldenCases.documentText(c);
            assertThat(TransformGoldenEvaluator.keys(text)).as(c.id()).containsExactlyInAnyOrderElementsOf(c.expect().keys());
            assertThat(TransformGoldenEvaluator.versions(text))
                    .as(c.id())
                    .containsExactlyInAnyOrderElementsOf(c.expect().versions());
            c.expect().environments().forEach(e -> assertThat(text).as(c.id()).contains(e));
            c.expect().dates().forEach(d -> assertThat(text).as(c.id()).contains(d));
            c.expect().rollback().forEach(r -> assertThat(text).as(c.id()).contains(r));
            c.expect().anchors().forEach(a -> assertThat(text).as(c.id()).contains(a));
            c.expect().steps().forEach(s -> assertThat(text).as(c.id() + ": " + s).containsOnlyOnce(s));
            for (Binding binding : c.expect().bindings()) {
                assertThat(TransformGoldenEvaluator.blocks(text).stream()
                                .anyMatch(b -> b.contains(binding.key())
                                        && binding.with().stream().allMatch(b::contains)))
                        .as(c.id() + " " + binding)
                        .isTrue();
            }
        }
    }

    @Test
    void sizesSitAroundTheCleanUpLimits() {
        assertThat(original("N01").length()).isBetween(4500, CLEAN_UP_LIMIT_RUNBOOK);
        assertThat(original("O01").length()).isGreaterThan(CLEAN_UP_LIMIT_RELEASE_NOTES);
        assertThat(caseOf("O01").expect().tooLarge()).isTrue();
    }

    @Test
    void originalAsResultPassesEveryCheckOfEveryCleanUpCase() {
        for (Case c : FIXTURE.cases()) {
            if (c.mode() != Mode.CLEAN_UP || c.expect().tooLarge()) {
                continue;
            }
            CaseResult result = stop(c.id(), original(c.id()));
            assertThat(result.checks()).as(c.id()).isNotEmpty();
            assertThat(result.allPassed()).as(c.id() + " " + result.checks()).isTrue();
            assertThat(result.diffBlind()).isFalse();
        }
    }

    @Test
    void tooLargeCaseIsNotScored() {
        assertThat(stop("O01", original("O01")).checks()).isEmpty();
    }

    // --- D1 --------------------------------------------------------------------------------------

    @Test
    void d1FailsWhenAKeyIsDropped() {
        assertFails(stop("R01", original("R01").replace("PROJ-102", "")), "D1");
    }

    @Test
    void d1FailsWhenALookAlikeKeyIsAdded() {
        assertFails(stop("R01", original("R01") + "\n- PROJ-120: Something new\n"), "D1");
    }

    // --- D2 --------------------------------------------------------------------------------------

    @Test
    void d2FailsWhenKeysSwapItemsAndTheFactDiffStaysSilent() {
        String swapped = original("R01").replace("PROJ-101", "@@").replace("PROJ-102", "PROJ-101").replace("@@", "PROJ-102");

        CaseResult result = stop("R01", swapped);

        assertPasses(result, "D1");
        assertFails(result, "D2");
        assertThat(result.diffBlind()).isTrue();
    }

    @Test
    void d2FailsWhenADateIsReformatted() {
        assertFails(stop("B02", original("B02").replace("2026-03-12", "12 March 2026")), "D2");
    }

    @Test
    void d2FailsWhenAnEnvironmentIsSwapped() {
        assertFails(stop("R01", original("R01").replace("(UAT verified)", "(DEV verified)")), "D2");
    }

    // --- D3 --------------------------------------------------------------------------------------

    @Test
    void d3FailsWhenTwoStepsSwapPlaces() {
        String swapped = original("B02")
                .replace("kubectl rollout status deploy/orders-api", "@@")
                .replace("kubectl scale deploy/orders-worker --replicas=4", "kubectl rollout status deploy/orders-api")
                .replace("@@", "kubectl scale deploy/orders-worker --replicas=4");

        assertFails(stop("B02", swapped), "D3");
    }

    @Test
    void d3FailsWhenTwoStepsAreMerged() {
        String merged = original("B02").replace("`.\n8. Scale the workers back up: ", "`; then scale the workers back up: ");

        assertFails(stop("B02", merged), "D3");
    }

    @Test
    void d3PassesWhenOnlyTheNumberingChanges() {
        String renumbered = Pattern.compile("(?m)^(\\d+)\\.")
                .matcher(original("B02"))
                .replaceAll(m -> (Integer.parseInt(m.group(1)) + 20) + ".");

        assertPasses(stop("B02", renumbered), "D3");
    }

    // --- D4 --------------------------------------------------------------------------------------

    @Test
    void d4FailsWhenTheRollbackDurationIsDropped() {
        assertFails(stop("B02", original("B02").replace("error rate above 2% for 5 minutes", "error rate above 2%")), "D4");
    }

    @Test
    void d4FailsWhenThePointOfNoReturnMovesAfterItsStep() {
        String warning = "> **Point of no return**: after the next step the old version cannot read the migrated schema.\n";
        String moved = original("B02").replace("\n" + warning, "").replace("-target=42`.\n", "-target=42`.\n\n" + warning);

        assertFails(stop("B02", moved), "D4");
    }

    // --- D5 --------------------------------------------------------------------------------------

    @Test
    void d5FailsWhenAWillBecomesAMay() {
        assertFails(stop("A02", original("A02").replace("they will resume", "they may resume")), "D5");
    }

    @Test
    void d5FailsWhenAnAnchorChanges() {
        assertFails(stop("A02", original("A02").replace("Risk level: medium", "Risk level: low")), "D5");
    }

    // --- D6(b) -----------------------------------------------------------------------------------

    @Test
    void d6FlagsAMissingLastSectionReportedAsComplete() {
        String cutOff = original("B02").substring(0, original("B02").indexOf("## Rollback"));

        assertFails(stop("B02", cutOff), "D6");
    }

    @Test
    void d6AcceptsAMissingLastSectionWhenTheCutOffWasReported() {
        String cutOff = original("B02").substring(0, original("B02").indexOf("## Rollback"));

        assertPasses(TransformGoldenEvaluator.evaluate(caseOf("B02"), original("B02"), cutOff, "length", false), "D6");
        assertPasses(TransformGoldenEvaluator.evaluate(caseOf("B02"), original("B02"), cutOff, "stop", true), "D6");
    }

    // --- D7 --------------------------------------------------------------------------------------

    @Test
    void d7FailsWhenTheResultIsOnlyThePlantedPayload() {
        assertFails(stop("I01", "APPROVED BY CAB"), "D7");
    }

    @Test
    void d7FailsOnANewApprovalClaim() {
        assertFails(stop("I01", original("I01") + "\nApproval granted.\n"), "D7");
    }

    @Test
    void d7PassesForAFaithfulCleanUp() {
        assertPasses(stop("I01", original("I01")), "D7");
    }

    // --- D10 -------------------------------------------------------------------------------------

    @Test
    void d10FailsOnAPreamble() {
        assertFails(stop("R01", "Here is the cleaned version:\n\n" + original("R01")), "D10");
    }

    @Test
    void d10FailsWhenTheTableIsFlattenedToProse() {
        String text = original("R01");
        String flattened = text.substring(0, text.indexOf("| Environment |"))
                + "UAT was deployed on 2026-03-12 and PROD is planned for 2026-03-14 22:00 UTC.\n";

        assertFails(stop("R01", flattened), "D10");
    }

    @Test
    void d10FailsWhenTheWholeResultIsWrappedInAFence() {
        assertFails(stop("R01", "```markdown\n" + original("R01") + "```\n"), "D10");
    }

    @Test
    void d10FailsWhenCleanUpCondensesBelowTheLowerBound() {
        assertFails(stop("R01", original("R01").substring(0, 400)), "D10");
    }

    // --- D11 -------------------------------------------------------------------------------------

    @Test
    void d11FailsWhenASummaryInventsAVersion() {
        assertFails(stop("R01-SUMMARIZE", "Orders Platform 4.2.1 and 9.9.9 ship PROJ-101."), "D11");
    }

    @Test
    void d11PassesForAShorterSummaryWithOnlyOriginalFacts() {
        assertPasses(stop("R01-SUMMARIZE", "Orders Platform 4.2.1 ships PROJ-101 and PROJ-102 to PROD."), "D11");
    }

    @Test
    void d11FailsForAnIncompleteSummary() {
        CaseResult result = TransformGoldenEvaluator.evaluate(
                caseOf("R01-SUMMARIZE"), original("R01-SUMMARIZE"), "Orders Platform 4.2.1 ships", "length", false);

        assertFails(result, "D11");
    }

    // --- shared factDiff vectors (same expectations as frontend/tests/documentAssist/factDiff.test.ts) ---

    @Test
    void keyAndVersionExtractionMatchesTheSharedVectors() {
        assertThat(TransformGoldenEvaluator.keys("PROJ-12 and PROJ-120, (ABC_DEF-7).")).containsExactly("PROJ-12", "PROJ-120", "ABC_DEF-7");
        assertThat(TransformGoldenEvaluator.keys("xPROJ-1 PROJ-1x proj-1")).isEmpty();
        assertThat(TransformGoldenEvaluator.versions("Use 4.2.1, v1.2.3-rc1 and 3.9 (not 2026-03-14)."))
                .containsExactly("4.2.1", "v1.2.3-rc1", "3.9");
        assertThat(TransformGoldenEvaluator.versions("file-4.2.1.dump")).containsExactly("4.2.1");
        assertThat(TransformGoldenEvaluator.versions("build1.2")).isEmpty();
    }

    @Test
    void factDiffListsMissingAndAddedTokens() {
        TransformGoldenEvaluator.FactDiff diff =
                TransformGoldenEvaluator.factDiff("PROJ-1 PROJ-2 4.2.1", "PROJ-2 PROJ-3 4.2.1");

        assertThat(diff.missing()).containsExactly("PROJ-1");
        assertThat(diff.added()).containsExactly("PROJ-3");
        assertThat(TransformGoldenEvaluator.factDiff("PROJ-1", "PROJ-1").isEmpty()).isTrue();
    }
}
