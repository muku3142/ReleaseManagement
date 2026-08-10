---
phase: 05-document-generation
plan: 01
subsystem: api
tags: [flexmark, jmustache, docx4j, openhtmltopdf, render-pipeline, archunit]

# Dependency graph
requires:
  - phase: 04-template-authoring
    provides: RenderEnginePort, MustacheFlexmarkRenderAdapter, RenderService (bounded render), the
      package-private parseMarkdown hand-off point (D-04-04), the render-library ArchUnit
      confinement rule
provides:
  - flexmark-ext-tables registered on both the Parser and HtmlRenderer builders, closing the
    server/client (markdown-it) table-rendering divergence Phase 4 shipped
  - MustacheFlexmarkRenderAdapter.renderHtml(Document) - a package-private sibling to
    parseMarkdown giving Phase 5's PDF/DOCX exporters one shared AST and one shared HTML renderer
  - RenderEnginePort.resolve / RenderService.resolveText - a bounded, Markdown-free plain-text
    resolution path for TMPL-08 chrome fields
  - openhtmltopdf and docx4j on the classpath, already confined to adapters.render by ArchUnit,
    with confirmed single-version dependency resolution
affects: [05-04-plan (chrome resolution consumer), 05-05-plan (PDF export adapter),
  05-06-plan (DOCX export adapter)]

# Tech tracking
tech-stack:
  added: [flexmark-ext-tables 0.64.8, openhtmltopdf-pdfbox 1.0.10, docx4j-JAXB-ReferenceImpl 17.0.2]
  patterns: ["Package-private hand-off methods on MustacheFlexmarkRenderAdapter (parseMarkdown,
    renderHtml) let sibling adapters in the same package share one parser/renderer configuration
    without a new abstraction layer", "RenderService.bounded(Callable<String>) is the single
    timeout/cancellation wrapper both render() and resolveText() route through"]

key-files:
  created: []
  modified:
    - pom.xml
    - src/main/java/com/evision/releasemgmt/ports/RenderEnginePort.java
    - src/main/java/com/evision/releasemgmt/adapters/render/MustacheFlexmarkRenderAdapter.java
    - src/main/java/com/evision/releasemgmt/application/RenderService.java
    - src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java
    - src/test/java/com/evision/releasemgmt/adapters/render/MustacheFlexmarkRenderAdapterTest.java
    - src/test/java/com/evision/releasemgmt/application/RenderServiceTest.java

key-decisions:
  - "No dependencyManagement pin was needed for docx4j - mvn dependency:tree confirmed exactly one
    docx4j-JAXB-ReferenceImpl:17.0.2 resolves with no competing transitive version"
  - "Task 2's TDD RED phase wrote the exact named tests Task 3's acceptance criteria required
    (pipeTableRendersAsAnHtmlTable, rawHtmlInsideATableCellIsStillNeutralised,
    renderHtmlOnTheSharedAstMatchesRender, resolveReturnsPlainTextWithNoMarkdownWrapper,
    resolveTextEnforcesTheCollectionCap, resolveTextMarksUnknownTokensLikeRenderDoes), so Task 3
    required no additional code - it is a verification-only task in this execution"

patterns-established:
  - "New render-technology dependencies (any future openhtmltopdf/docx4j consumer) must be added
    to the single renderLibrariesLiveOnlyInTheRenderAdapter ArchUnit rule's package list rather
    than a new rule"

requirements-completed: [DOC-02, DOC-05, DOC-06]

# Metrics
duration: ~25min
completed: 2026-08-10
---

# Phase 5 Plan 01: Render Pipeline Extension Summary

**Registered flexmark-ext-tables to fix a shipped table-rendering gap, added a shared-AST `renderHtml(Document)` hand-off and a bounded Mustache-only `resolveText` for chrome fields, and put openhtmltopdf/docx4j on the classpath under existing ArchUnit confinement.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-08-10T16:00:00+03:00 (approx, first tool call)
- **Completed:** 2026-08-10T16:08:35+03:00 (full suite green)
- **Tasks:** 3 (Task 3 required no additional commit - see Deviations)
- **Files modified:** 7

## Accomplishments
- Closed a real server/client rendering divergence: GFM pipe tables now render as HTML `<table>`
  server-side, matching the client `markdown-it` preview and unblocking the "Updated Services"
  table in the reference document.
- `parseMarkdown`/`renderHtml` now form a matched pair giving Phase 5's PDF and DOCX exporters one
  shared flexmark AST and one shared HTML renderer, per D-04-04's structural parity guarantee.
- `RenderEnginePort.resolve` / `RenderService.resolveText` give chrome fields (cover title,
  running header, footer) a bounded, Markdown-free resolution path with the exact same
  timeout/collection-cap protections as body rendering, sharing one `bounded(Callable<String>)`
  helper (no duplicated bound logic).
- openhtmltopdf and docx4j are on the classpath, confined to `adapters.render` by the existing
  ArchUnit rule, before any code uses them - unblocks plans 05-05/05-06 running in parallel.
- Full backend suite: 433 tests green (427 baseline + 6 new), no regressions.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add render dependencies and extend ArchUnit confinement rule** - `8290b61` (feat)
2. **Task 2: Register TablesExtension, expose renderHtml, add plain-text resolve** - TDD cycle:
   - RED: `b02bf53` (test) - failing tests for tables, shared-AST hand-off, resolve/resolveText
   - GREEN: `d956581` (feat) - TablesExtension registration, resolve/renderHtml implementation,
     RenderService.bounded refactor
3. **Task 3: Tests proving the table gap is closed and the hand-offs behave** - no separate commit
   (see Deviations - all required tests were already written and passing as part of Task 2's RED
   phase)

**Plan metadata:** (this commit, docs: complete plan)

_Note: Task 2 was tdd="true" and used the full RED/GREEN cycle._

## Files Created/Modified
- `pom.xml` - added flexmark-ext-tables 0.64.8, openhtmltopdf-pdfbox 1.0.10, docx4j-JAXB-ReferenceImpl 17.0.2
- `src/main/java/com/evision/releasemgmt/ports/RenderEnginePort.java` - added `resolve(String, Map)`
- `src/main/java/com/evision/releasemgmt/adapters/render/MustacheFlexmarkRenderAdapter.java` -
  TablesExtension on parser+renderer, `resolve()` implementation, package-private `renderHtml(Document)`
- `src/main/java/com/evision/releasemgmt/application/RenderService.java` - `resolveText`, refactored
  `renderBounded` into shared `bounded(Callable<String>)`
- `src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java` - extended render-confinement
  rule package list with `com.openhtmltopdf..`, `org.docx4j..`
- `src/test/java/com/evision/releasemgmt/adapters/render/MustacheFlexmarkRenderAdapterTest.java` -
  4 new tests (table rendering, table-cell raw-HTML neutralization, shared-AST parity, resolve)
- `src/test/java/com/evision/releasemgmt/application/RenderServiceTest.java` - 2 new tests
  (resolveText cap enforcement, resolveText unknown-token marking); fixed the pre-existing
  `slowEngine` lambda to an anonymous class (RenderEnginePort is no longer single-method)

## Decisions Made
- No `<dependencyManagement>` pin was needed for docx4j - `mvn dependency:tree -Dincludes=org.docx4j`
  confirms exactly one `docx4j-JAXB-ReferenceImpl:17.0.2` resolves.
- Task 2's TDD RED phase intentionally wrote the exact test method names Task 3's acceptance
  criteria specified, since they map 1:1 onto Task 2's `<behavior>` bullets. This meant Task 3
  produced no new code - see Deviations.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed `RenderServiceTest`'s `slowEngine` lambda after `RenderEnginePort` gained a second abstract method**
- **Found during:** Task 2 RED phase
- **Issue:** `RenderEnginePort slowEngine = (source, context) -> {...}` in
  `aRenderThatExceedsTheTimeoutTripsTheWallClockBound` relied on `RenderEnginePort` being a
  single-method (functional) interface. Adding `resolve` as specified by the plan breaks that
  lambda's compilation.
- **Fix:** Replaced the lambda with an anonymous class implementing both `render` (the original
  slow behavior) and `resolve` (delegates to `source` unchanged - unused by this test).
- **Files modified:** `src/test/java/com/evision/releasemgmt/application/RenderServiceTest.java`
- **Verification:** Full suite compiles and passes (433/433).
- **Committed in:** `b02bf53` (Task 2 RED commit)

### Process Note (not a Rule 1-4 deviation, documented for traceability)

**Task 3 produced no additional commit.** Task 2's TDD RED phase (per `<tdd_execution>`) wrote
tests directly against Task 2's `<behavior>` bullets, and those bullets describe exactly the same
proofs Task 3's `<action>` asks for by name: `pipeTableRendersAsAnHtmlTable`,
`rawHtmlInsideATableCellIsStillNeutralised`, `renderHtmlOnTheSharedAstMatchesRender`,
`resolveReturnsPlainTextWithNoMarkdownWrapper`, `resolveTextEnforcesTheCollectionCap`, and
`resolveTextMarksUnknownTokensLikeRenderDoes` were all added and passing before Task 3 began. All
of Task 3's grep-based acceptance criteria and its `mvn test` total (>= 433) were independently
re-verified as part of this execution. No files changed for Task 3, so no separate commit was made.

---

**Total deviations:** 1 auto-fixed (1 blocking), plus 1 process note (test consolidation, no
functional impact).
**Impact on plan:** The lambda fix was required for the build to compile at all once the interface
changed exactly as the plan specified - no scope creep. The Task 2/3 test consolidation produced
strictly the same test coverage the plan asked for, verified independently for Task 3's own
acceptance criteria.

## Issues Encountered
None beyond the deviation above.

## Test Results

- `mvn test` (full suite): **433 tests, 0 failures, 0 errors** (427 baseline + 6 new: the exact
  target the plan specified). This is the new baseline for plan 05-02 onward.
- `mvn dependency:tree -Dincludes=org.docx4j`: exactly one `docx4j-JAXB-ReferenceImpl:17.0.2`
  resolves - **no `<dependencyManagement>` pin was needed.**
- `mvn dependency:tree -Dincludes=com.vladsch.flexmark`: `flexmark:0.64.8` and
  `flexmark-ext-tables:0.64.8` both resolve at the same release train version.
- `TemplateSeederIT` green - the three seeded starter templates still validate through the
  tables-enabled parser.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `MustacheFlexmarkRenderAdapter.renderHtml(Document)` and `parseMarkdown(String)` are ready for
  plan 05-05 (PDF export) and plan 05-06 (DOCX export) to consume the shared AST without
  duplicating parser/renderer configuration.
- `RenderService.resolveText` is ready for plan 05-04 to resolve TMPL-08 chrome fields under the
  same bounds as body rendering.
- openhtmltopdf and docx4j are already fenced into `adapters.render` by ArchUnit, so plans 05-05
  and 05-06 can add their own adapter classes in parallel without touching `pom.xml` or
  `ArchitectureBoundaryTest.java` again.
- No blockers.

---
*Phase: 05-document-generation*
*Completed: 2026-08-10*

## Self-Check: PASSED

All claimed files exist on disk (pom.xml, RenderEnginePort.java, MustacheFlexmarkRenderAdapter.java,
RenderService.java, ArchitectureBoundaryTest.java, both test files, this SUMMARY.md). All claimed
commit hashes (8290b61, b02bf53, d956581, 63564a8) are present in `git log`.
