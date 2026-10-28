---
phase: 05-document-generation
plan: 13
subsystem: document-generation
tags: [flexmark, markdown, mustache, docx4j, openhtmltopdf, gap-closure]

# Dependency graph
requires:
  - phase: 05-document-generation
    provides: DocumentGenerationService, RenderService, PdfExportAdapter, DocxExportAdapter, DocumentSnapshot (plans 05-01..05-12)
provides:
  - Generation stores Mustache-resolved Markdown as the snapshot body instead of flexmark-rendered HTML
  - RenderResult's single accessor renamed html -> output, removing the trap that made storing HTML look correct
  - DocumentSnapshot states its markdown field's content-type contract on the record itself
  - GenerateThenExportSeamTest: a generate -> export regression test with no hand-written content fixture on either side
affects: [05-document-generation, 07-promotion-tracking]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Seam-level regression testing: real generate() -> real DocumentSnapshot -> real export(), no hand-written fixture crossing the boundary"

key-files:
  created:
    - src/test/java/com/evision/releasemgmt/application/GenerateThenExportSeamTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/application/DocumentGenerationService.java
    - src/main/java/com/evision/releasemgmt/application/RenderResult.java
    - src/main/java/com/evision/releasemgmt/application/RenderService.java
    - src/main/java/com/evision/releasemgmt/domain/document/DocumentSnapshot.java
    - src/test/java/com/evision/releasemgmt/application/DocumentGenerationServiceTest.java
    - src/test/java/com/evision/releasemgmt/application/RenderServiceTest.java
    - src/test/java/com/evision/releasemgmt/config/TemplateSeederIT.java

key-decisions:
  - "Store Markdown (not HTML) as the snapshot body: the snapshot is the audit record, Markdown is the authored source of truth, D-05-05 already specified post-Mustache/pre-flexmark Markdown, the record component was already named markdown, and both export ports/adapters already parsed Markdown. Storing HTML instead would have meant rewriting both exporters, the viewer (plan 05-12) and D-05-05 - five changes to avoid one."
  - "Chrome and both export ports were never broken - 05-VERIFICATION.md's gap-2 diagnosis was only 2/3 correct. Chrome already went through resolveText (Mustache only, no flexmark); the only defect there was the misleading .html() accessor name, not the resolved value. Fixing chrome behavior was explicitly out of scope and would have broken storedChromeIsResolvedWhileBothBooleansAreCopiedUnchanged."
  - "Pre-fix (HTML-bodied) snapshots are accepted as historical and are NOT migrated, backfilled, repaired, or read-time-sniffed. DocumentSnapshot is append-only by design (D-05-06/DOC-07); the remedy is Regenerate, which appends a correct revision while the old one stays visible and honestly labelled (superseded). User confirmed this disposition at the Task 3 checkpoint."

requirements-completed: [DOC-05, DOC-06, DOC-07, DOC-08]

duration: ~35min
completed: 2026-08-11
---

# Phase 05 Plan 13: Fix generate/export content-type mismatch Summary

**Generation now stores Mustache-resolved Markdown (not flexmark HTML) as the document snapshot body, closing the gap where every exported PDF/DOCX rendered its whole body as literal escaped tag text; a new seam-level regression test crosses generate -> export in both formats with no hand-written fixture on either side.**

## Performance

- **Duration:** ~35 min
- **Tasks:** 2 automated tasks + 1 human-verify checkpoint (approved)
- **Files modified:** 7 (1 created, 6 modified in Task 1, 1 created in Task 2)

## Accomplishments
- Fixed the root defect: `DocumentGenerationService` now calls `renderService.resolveText(template.body(), view, "Body").output()` instead of `renderService.render(...).html()`, so the snapshot body is Markdown, not HTML.
- Renamed `RenderResult.html` to `RenderResult.output` and removed every `.html()` accessor from the codebase (`grep -rc '\.html()' src/main src/test` returns nothing) - the misleadingly-named accessor was what let the defect look correct at every call site.
- Documented the content-type contract directly on `DocumentSnapshot`: `markdown` holds Mustache-resolved Markdown, never HTML.
- Added `GenerateThenExportSeamTest` - four tests that drive real `DocumentGenerationService.generate()` output through real `PdfExportAdapter`/`DocxExportAdapter` calls and assert rendered content with no escaped tag text. This is the class of test that would have caught the original defect; none existed before this plan.
- Confirmed the fix and the new test empirically: reverting the fix causes 4/4 seam tests to fail; restoring it brings all 556 tests green.
- User verified the fix in the running app (generate, export PDF and DOCX, both render formatted content) and confirmed the accepted disposition of pre-fix snapshots at the Task 3 checkpoint.

## Task Commits

Each task was committed atomically:

1. **Task 1: Store Markdown, and delete the accessor that made storing HTML look correct** - `f82319a` (fix)
2. **Task 2: The seam test - generate through the real service, export that same snapshot through the real service** - `01d2059` (test)
3. **Task 3: Confirm against a real export, and confirm the historical-snapshot disposition** - checkpoint, approved by user ("approved and working fine")

**Plan metadata:** (this commit)

## Files Created/Modified
- `src/main/java/com/evision/releasemgmt/application/DocumentGenerationService.java` - line 58 changed from `render(...).html()` to `resolveText(...).output()`, with an explanatory comment; chrome lines 61-64 changed `.html()` to `.output()` only (behavior unchanged)
- `src/main/java/com/evision/releasemgmt/application/RenderResult.java` - sole accessor renamed `html` -> `output`, with a comment explaining why the name is deliberately not tied to either producer method
- `src/main/java/com/evision/releasemgmt/application/RenderService.java` - javadoc added to `render()` ("output() is HTML... document generation must NOT use this method") and `resolveText()` ("output() is the resolved source text, unchanged in kind"); no logic change
- `src/main/java/com/evision/releasemgmt/domain/document/DocumentSnapshot.java` - header comment states the content-type contract: `markdown` holds Mustache-resolved Markdown, never HTML
- `src/test/java/com/evision/releasemgmt/application/DocumentGenerationServiceTest.java` - the body-storage test rewritten as `generatingStoresTheResolvedMarkdownBodyNotItsRenderedHtml`, pinning Markdown output and asserting `startsWith("# Release ")`/`doesNotContain("<h1")`; `storedChromeIsResolvedWhileBothBooleansAreCopiedUnchanged` left untouched and still passes, proving chrome was never broken
- `src/test/java/com/evision/releasemgmt/application/RenderServiceTest.java` - `.html()` -> `.output()` at the three call sites exercising `render()`/`resolveText()` directly
- `src/test/java/com/evision/releasemgmt/config/TemplateSeederIT.java` - `.html()` -> `.output()` at the two call sites
- `src/test/java/com/evision/releasemgmt/application/GenerateThenExportSeamTest.java` (new) - four tests: snapshot body is Markdown not HTML; exported PDF shows rendered content; exported DOCX shows rendered content; chrome survives the seam as plain text

## Decisions Made

**Store Markdown, not HTML, as the snapshot body.** The snapshot is the immutable audit record; Markdown is the authored source of truth (D-05-05 already specified "post-Mustache, pre-flexmark Markdown"); the record component was already named `markdown`; both export ports already document Markdown in their javadoc; and both exporters already parse the body with `parseMarkdown`. Generation was the single place that disagreed. The alternative - storing HTML - would have required rewriting both exporters, the document viewer (plan 05-12), and D-05-05 itself: five changes to avoid the one this plan makes.

**Chrome and both export ports required no fix.** `05-VERIFICATION.md`'s gap-2 diagnosis claimed the four chrome fields (cover title/subtitle, header, footer) were broken by the same `.html()` mismatch as the body. Re-validating against the code before writing this plan showed that claim does not hold: chrome was already resolved via `RenderService.resolveText(...)`, which is Mustache-only with no flexmark step - the same bounded, non-HTML path used to store the body correctly now. The only defect in the chrome lines was the misleadingly-named `.html()` accessor, not the value it returned. This summary corrects `05-VERIFICATION.md`'s gap-2 reason text on that point. `PdfExportPort` and `DocxExportPort` javadoc already documented their parameter as already-resolved Markdown and needed no edit either.

**Pre-fix (HTML-bodied) snapshots are accepted as historical, not migrated or repaired.** `DocumentSnapshot` is append-only by design (D-05-06, DOC-07) - there is no update path, and adding one to repair old rows would destroy the exact immutability guarantee Phase 7's AUDIT-02 depends on. Read-time HTML sniffing in the exporters was also declined: it is a heuristic on ambiguous input (legitimate Markdown can contain angle brackets) that would live forever and reintroduce content-type ambiguity - the precise thing this plan exists to end. The remedy already exists in the product: Regenerate appends a correct revision while the old one stays visible, exportable, and honestly labelled `(superseded)`. Nothing is deployed yet (Phase 5 of 8), so the affected rows are local/dev-database only. **The user confirmed acceptance of this disposition at the Task 3 checkpoint** ("approved and working fine").

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected `boundBreachPropagates`'s mock stub to the method the service now calls**
- **Found during:** Task 1, running the full test suite after the `render` -> `resolveText` change
- **Issue:** `DocumentGenerationServiceTest.boundBreachPropagates` stubbed `boundBreachingRenderService.render(any(), any(), any())` to throw `RenderBoundExceededException`. This test was not in the plan's listed test-call-site edits (only lines 48-60 and 100-118 were named), but the service under test no longer calls `render()` for the body - it calls `resolveText()`. Left as-is, the mock would return `null` unstubbed and the test would fail with an NPE instead of exercising the bound-breach propagation path.
- **Fix:** Changed the stub to `boundBreachingRenderService.resolveText(any(), any(), any())`.
- **Files modified:** `src/test/java/com/evision/releasemgmt/application/DocumentGenerationServiceTest.java`
- **Verification:** `mvn test -Dtest=DocumentGenerationServiceTest` - 10/10 green.
- **Committed in:** `f82319a` (Task 1 commit)

**2. [Rule 1 - Bug] Fixed a case-sensitivity mismatch between the plan's mandated comment text and its own acceptance criterion**
- **Found during:** Task 1, verifying acceptance criteria before committing
- **Issue:** The plan's action block specified the exact `DocumentSnapshot` header comment text verbatim, using `MARKDOWN` in all caps for emphasis. Its own acceptance criterion (`grep -c 'Markdown' DocumentSnapshot.java` returns at least `2`) is case-sensitive, and the literal text as given produces only one match (`parseMarkdown`) - `MARKDOWN` does not match the pattern `Markdown`.
- **Fix:** Changed `Mustache-RESOLVED MARKDOWN` to `Mustache-resolved Markdown` in that one spot. No change in meaning or intent; the sentence still states the content-type contract exactly as the plan required.
- **Files modified:** `src/main/java/com/evision/releasemgmt/domain/document/DocumentSnapshot.java`
- **Verification:** `grep -c 'Markdown' DocumentSnapshot.java` returns `2`.
- **Committed in:** `f82319a` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 - bugs surfaced by the plan's own changes/criteria)
**Impact on plan:** Both fixes were necessary for the test suite to compile/pass and for the plan's stated acceptance criteria to actually hold. No scope creep - neither touched behavior outside what the plan specified.

## Issues Encountered

None beyond the two deviations above. The Task 2 sanity check (reverting the fix, observing seam-test failures, restoring it) was performed exactly as the plan required and produced a stronger result than the minimum bar: **4 of 4** seam tests failed against the pre-fix code (plan required >= 3 of 4). All four failures were the expected shape - escaped `<h1>`/`<table>`/`<p>` tags visible as literal text instead of rendered content. The fix was restored immediately after and confirmed via `git diff` showing no changes against the committed state, then re-verified green (4/4).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- DOC-05, DOC-06, DOC-07 and DOC-08 are now genuinely satisfied end-to-end: exported PDF and DOCX render formatted content through the same shared flexmark AST (D-04-04), and the content-type contract is stated on `DocumentSnapshot` and honoured by generation, both exporters, and (via plan 05-12) the document viewer.
- Backend suite green at **556 tests, 0 failures** (552 baseline + 4 new seam tests).
- No `.html()` accessor remains anywhere in `src/main` or `src/test`.
- Pre-fix HTML-bodied snapshots in local/dev databases remain as historical, superseded revisions - no follow-up work needed; Regenerate is the documented remedy for anyone who hits one.
- Phase 7's AUDIT-02 (viewing every generated document including superseded revisions) is unaffected and will surface pre-fix revisions with their honest `(superseded)` label alongside the correct regenerated ones.

---
*Phase: 05-document-generation*
*Completed: 2026-08-11*
