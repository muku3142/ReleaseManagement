---
phase: 04-template-authoring
plan: 01
subsystem: templates
tags: [java, records, mustache-subset-scanner, reflection-test, view-model]

# Dependency graph
requires:
  - phase: 03-release-assembly
    provides: Release/ReleaseItem/ManualItem/ManualItemCategory aggregate and TicketDetail's exact field set
provides:
  - Six flattened, dependency-free view-model records under domain/template/view
  - SampleReleaseView.instance() as the deterministic TMPL-04 validation context
  - PlaceholderCatalog.ENTRIES — the exhaustive TMPL-03 catalog constant with scope-aware find()
  - TemplateScanner.scan — enforces the D-04-06 Mustache subset, collects unknown tokens
affects: [04-02, 04-03, 04-04, 04-05, phase-05-document-generation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Hand-written character scan (not regex) for untrusted template syntax validation"
    - "Reflection-based drift test (getRecordComponents) proving a hand-written catalog constant matches its view-model 1:1"

key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/template/view/ReleaseView.java
    - src/main/java/com/evision/releasemgmt/domain/template/view/ItemView.java
    - src/main/java/com/evision/releasemgmt/domain/template/view/GroupView.java
    - src/main/java/com/evision/releasemgmt/domain/template/view/CommentView.java
    - src/main/java/com/evision/releasemgmt/domain/template/view/CommitView.java
    - src/main/java/com/evision/releasemgmt/domain/template/view/PullRequestView.java
    - src/main/java/com/evision/releasemgmt/domain/template/view/SampleReleaseView.java
    - src/main/java/com/evision/releasemgmt/domain/template/PlaceholderScope.java
    - src/main/java/com/evision/releasemgmt/domain/template/PlaceholderKind.java
    - src/main/java/com/evision/releasemgmt/domain/template/PlaceholderEntry.java
    - src/main/java/com/evision/releasemgmt/domain/template/PlaceholderCatalog.java
    - src/main/java/com/evision/releasemgmt/domain/template/TemplateScanner.java
    - src/main/java/com/evision/releasemgmt/domain/template/ScanResult.java
    - src/main/java/com/evision/releasemgmt/domain/template/TemplateMalformedException.java
    - src/main/java/com/evision/releasemgmt/domain/template/RenderBoundExceededException.java
    - src/test/java/com/evision/releasemgmt/domain/template/PlaceholderCatalogTest.java
    - src/test/java/com/evision/releasemgmt/domain/template/TemplateScannerTest.java
  modified: []

key-decisions:
  - "View-model records carry only D-04-17's field set — no component/epic/label/priority/fixVersions/commentTotal on ItemView"
  - "CommitView and PullRequestView exist now and are catalogued as always empty until Phase 6 (D-04-18)"
  - "PlaceholderCatalog is a hand-written constant, not derived by reflection, with a reflection-based drift test as the safety net (D-04-20)"
  - "TemplateScanner is a character walk, not a regex, because a regex cannot reliably distinguish {{{x}}} from {{ + {x} + }}"

patterns-established:
  - "Pattern: bound untrusted-content walks with a named MAX_* constant and stop-rather-than-throw-mid-walk, mirroring AdfFlattener's MAX_NODES/MAX_DEPTH idiom"
  - "Pattern: unknown-but-well-formed tokens are collected as warnings, not thrown — only structurally illegal Mustache constructs throw TemplateMalformedException"

requirements-completed: [TMPL-02, TMPL-03, TMPL-04]

# Metrics
duration: 19min
completed: 2026-08-09
---

# Phase 04 Plan 01: View-Model, Placeholder Catalog and Template Scanner Summary

**Fixed flattened view-model (ReleaseView/ItemView/GroupView/CommentView/CommitView/PullRequestView), a hand-written exhaustive placeholder catalog with a reflection-based drift test, and a character-scan TemplateScanner that enforces the D-04-06 Mustache subset — the three pure-domain artefacts phases 4 and 5 render every document through.**

## Performance

- **Duration:** 19 min
- **Started:** 2026-08-09T10:23:34+03:00
- **Completed:** 2026-08-09T10:42:03+03:00
- **Tasks:** 3
- **Files modified:** 17 (16 created backend, 1 test-only addition folded into count above)

## Accomplishments
- Six dependency-free `domain/template/view` records flatten `Release`/`ReleaseItem`/`ManualItem`/`TicketDetail` into the only shape a template can ever see, with `ItemView.isManual` unifying ticket-backed and manual items into one collection (D-04-01)
- `SampleReleaseView.instance()` is a deterministic TMPL-04 validation fixture: a ticket with no linked code, a ticket with a blank resolution, a manual item, a comment, and three groups
- `PlaceholderCatalog.ENTRIES` is an exhaustive, hand-written catalog of every legal token in every scope, verified against the view-model's actual record components by `PlaceholderCatalogTest` via reflection — the catalog cannot silently drift from what the code actually exposes
- `TemplateScanner.scan` rejects partials, unescaped/triple-mustache variables, custom delimiters and unbalanced/mismatched sections with specific, actionable messages, while collecting out-of-catalog or misspelled tokens as non-blocking "unknown" warnings — scope leakage (e.g. `{{status}}` at root, `{{release.name}}` inside `{{#items}}`) is caught by the same scope-aware lookup used by the catalog

## Task Commits

Each task was committed atomically:

1. **Task 1: Define the view-model records and the fixed sample context** - `c0fe782` (feat)
2. **Task 2: Write the placeholder catalog and its view-model drift test** - `5fbb0c0` (feat)
3. **Task 3: Write the template scanner that enforces the allowed Mustache subset** - `f311b3c` (feat)

**Plan metadata:** committed separately by orchestrator after wave merge (per parallel-execution contract, this executor does not touch STATE.md/ROADMAP.md)

## Files Created/Modified
- `src/main/java/com/evision/releasemgmt/domain/template/view/ReleaseView.java` - root view-model record (7 scalars + items + groups)
- `src/main/java/com/evision/releasemgmt/domain/template/view/ItemView.java` - unified ticket/manual item shape with `isManual`
- `src/main/java/com/evision/releasemgmt/domain/template/view/GroupView.java` - pre-computed grouping shape (issue type / category)
- `src/main/java/com/evision/releasemgmt/domain/template/view/CommentView.java` - wraps a comment string so templates avoid Mustache's implicit `{{.}}`
- `src/main/java/com/evision/releasemgmt/domain/template/view/CommitView.java` - always empty until Phase 6
- `src/main/java/com/evision/releasemgmt/domain/template/view/PullRequestView.java` - always empty until Phase 6
- `src/main/java/com/evision/releasemgmt/domain/template/view/SampleReleaseView.java` - deterministic TMPL-04 validation context
- `src/main/java/com/evision/releasemgmt/domain/template/PlaceholderScope.java` - ROOT/ITEM/GROUP/COMMENT/COMMIT/PULL_REQUEST
- `src/main/java/com/evision/releasemgmt/domain/template/PlaceholderKind.java` - VARIABLE/SECTION
- `src/main/java/com/evision/releasemgmt/domain/template/PlaceholderEntry.java` - one catalog row
- `src/main/java/com/evision/releasemgmt/domain/template/PlaceholderCatalog.java` - the exhaustive TMPL-03 constant + `find`
- `src/main/java/com/evision/releasemgmt/domain/template/TemplateScanner.java` - the D-04-06 subset enforcement scan
- `src/main/java/com/evision/releasemgmt/domain/template/ScanResult.java` - unknown-token result carrier
- `src/main/java/com/evision/releasemgmt/domain/template/TemplateMalformedException.java` - field + dynamic detail exception
- `src/main/java/com/evision/releasemgmt/domain/template/RenderBoundExceededException.java` - fixed-copy bound-breach exception
- `src/test/java/com/evision/releasemgmt/domain/template/PlaceholderCatalogTest.java` - D-04-20 reflection drift guard
- `src/test/java/com/evision/releasemgmt/domain/template/TemplateScannerTest.java` - 18 tests, one per behavior spec bullet

## Decisions Made
None beyond what the plan specified — implementation followed the plan's exact field lists, catalog table and scanner algorithm.

## Deviations from Plan

None functionally. One cosmetic note: `ReleaseView.java`'s mandated doc comment (verbatim from the plan's `<action>` block) explains *why* there is no `generatedAt`/`generatedBy` field, which means the literal string "generatedAt" appears in a comment. The plan's own acceptance-criteria grep for `generatedAt|generatedBy` returning "no matches" against this file therefore technically flags a hit — but it is a comment explaining the field's deliberate *absence*, not the field itself. `ReleaseView`'s record components are exactly the seven scalars plus `items`/`groups`, verified by both `ArchitectureBoundaryTest` and `PlaceholderCatalogTest`'s reflection-based component match. No code change was warranted.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- The view-model, catalog and scanner are ready for plan 04-02 (persistence: `Template` aggregate, `V4` migration) and later plans that build `RenderService`/`RenderEnginePort` on top of `TemplateScanner`'s validation and `PlaceholderCatalog`'s lookup
- `SampleReleaseView.instance()` is ready to be reused as-is for save-time validation once `RenderService` exists
- No blockers identified

---
*Phase: 04-template-authoring*
*Completed: 2026-08-09*

## Self-Check: PASSED

All 17 created files (16 source/test files + this SUMMARY.md) verified present on disk. All 4 commit hashes (`c0fe782`, `5fbb0c0`, `f311b3c`, `b95fc40`) verified present in `git log --all`. Full `mvn clean test` (JAVA_HOME=jdk-25) exits 0.
