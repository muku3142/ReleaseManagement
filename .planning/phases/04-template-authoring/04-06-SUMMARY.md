---
phase: 04-template-authoring
plan: 06
subsystem: templates
tags: [seeding, application-runner, testcontainers, mustache, integration-test]

# Dependency graph
requires:
  - phase: 04-template-authoring
    plan: 01
    provides: "PlaceholderCatalog, TemplateScanner, SampleReleaseView - the starters validate against these"
  - phase: 04-template-authoring
    plan: 02
    provides: "Template aggregate, ChromeFields, TemplateRepository"
  - phase: 04-template-authoring
    plan: 04
    provides: "TemplateService (create/saveContent/activate/duplicate/delete) - the seeder calls these unmodified"
provides:
  - "Three shipped starter template bodies (release notes, runbook, approval) under src/main/resources/templates/"
  - "TemplateSeeder: idempotent ApplicationRunner seeding, one active template per document type on first start"
  - "Testcontainers proof that the seeded starters validate clean and the full backend template lifecycle works"
affects: [phase-05-document-generation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Idempotency guard keyed on 'any template of this type exists', not 'an active template of this type exists' - a document type with zero active templates is a legal state (D-04-13) that must not trigger re-seeding"
    - "Classpath starter bodies loaded via getClass().getResourceAsStream, read with readAllBytes/UTF_8, no ResourceLoader injection needed for three fixed paths"

key-files:
  created:
    - src/main/resources/templates/starter-release-notes.md
    - src/main/resources/templates/starter-runbook.md
    - src/main/resources/templates/starter-approval.md
    - src/main/java/com/evision/releasemgmt/config/TemplateSeeder.java
    - src/test/java/com/evision/releasemgmt/config/TemplateSeederIT.java
    - src/test/java/com/evision/releasemgmt/adapters/web/TemplateLifecycleIT.java
  modified: []

key-decisions:
  - "TemplateLifecycleIT drives the round trip through TemplateService directly rather than an authenticated MockMvc client, per the plan's own guidance that the point is the persistence/service round trip, not the HTTP transport (TemplateControllerTest in plan 04-04 already covers the transport as a slice test)"
  - "TemplateSeederIT tests are ordered with @TestMethodOrder(OrderAnnotation) - read-only assertions against the ApplicationRunner's startup-seeded state run first (@Order 1-5), then mutating idempotency tests run last (@Order 6-9), so no test depends on another test's leftover mutation"

patterns-established:
  - "Pattern: seeder idempotency test suite structure - startup-state assertions before mutation tests, explicit seeder.run(null) re-invocation for each mutation scenario rather than relying on a fresh application context per test"

requirements-completed: [TMPL-01, TMPL-04]

# Metrics
duration: 42min
completed: 2026-08-09
---

# Phase 04 Plan 06: Starter Template Seeding Summary

**Three realistic starter templates (release notes, runbook, approval) seeded idempotently at startup via `TemplateSeeder`, with Testcontainers proof that every seeded body/chrome field validates clean and the full create/save/activate/delete/duplicate lifecycle round-trips against real PostgreSQL 18.**

## Performance

- **Duration:** ~42 min
- **Started:** 2026-08-09T15:15:00+03:00
- **Completed:** 2026-08-09T15:57:00+03:00
- **Tasks:** 3
- **Files modified:** 6 (all created)

## Accomplishments

- Three shipped Markdown starter bodies exercise `{{#groups}}`, unified `{{#items}}`, the
  `{{^isManual}}`/`{{#isManual}}` ticket/manual branch, an inverted section over the `items` and
  `commits` collections, and a section over a catalogued variable (`{{#resolution}}`) - every token
  used is in `PlaceholderCatalog`, and no partial/unescaped/custom-delimiter construct appears
  anywhere in the three files.
- `TemplateSeeder` follows `JiraSiteConfigSeeder`'s established `@Component implements
  ApplicationRunner` shape exactly, seeding through `TemplateService.create`/`saveContent`/`activate`
  so each starter is validated by the same TMPL-04 save-time render every hand-authored template
  gets. The idempotency guard is "this document type has any template", not "has an active
  template" - deleting the only template of a type legitimately brings its starter back on the next
  start (D-04-13), and that reasoning is recorded as a comment at the guard itself.
- `TemplateSeederIT` (9 tests) proves: exactly three templates seed on first start, one active per
  type; every seeded body and every non-blank chrome field renders with zero unknown tokens and zero
  stored warnings; the release-notes and runbook starters render real, sample-specific content
  (`Fix duplicate invoice lines`, `CNV-2896`, `Run migration V4`, `Database script`, and the
  `No linked code for this item.` inverted-section idiom); and re-running the seeder is idempotent
  whether nothing changed, the table was truncated, one type's only template was deleted, or an
  extra template of a type was added alongside the starter.
- `TemplateLifecycleIT` proves the full backend round trip against a real database in one test:
  create an inactive draft, save a body with a typo'd placeholder (warns, does not block), attempt a
  save with `{{>shared}}` (blocked, `TemplateMalformedException`, prior body untouched), activate the
  draft (exactly one active `RELEASE_NOTES` template, starter deactivated), delete the now-active
  draft (zero-active is a legal, tolerated state - `list()` still succeeds), and duplicate the
  remaining template twice (`"… copy"`, then `"… copy 2"`, both inactive).
- Full suite: 410 tests (400 baseline + 10 new: 9 `TemplateSeederIT` + 1 `TemplateLifecycleIT`), zero
  failures/errors, `ArchitectureBoundaryTest` green.

## Task Commits

Each task was committed atomically:

1. **Task 1: Write the three starter template bodies** - `946af98` (feat)
2. **Task 2: Write the TemplateSeeder** - `5268bff` (feat)
3. **Task 3: Write the seeder and lifecycle integration tests** - `f96f3ec` (test)

**Plan metadata:** committed separately by orchestrator after wave merge (per parallel-execution contract, this executor does not touch STATE.md/ROADMAP.md).

## Files Created/Modified

- `src/main/resources/templates/starter-release-notes.md` - the release-notes starter body
- `src/main/resources/templates/starter-runbook.md` - the deployment runbook starter body
- `src/main/resources/templates/starter-approval.md` - the change approval starter body
- `src/main/java/com/evision/releasemgmt/config/TemplateSeeder.java` - `ApplicationRunner` that
  idempotently seeds and activates the three starters
- `src/test/java/com/evision/releasemgmt/config/TemplateSeederIT.java` - 9 Testcontainers tests
  proving clean validation and idempotency under every mutation scenario
- `src/test/java/com/evision/releasemgmt/adapters/web/TemplateLifecycleIT.java` - 1 Testcontainers
  test proving the full backend lifecycle including the zero-active state

## Decisions Made

- Followed the plan's exact starter bodies, chrome field values and seeding algorithm verbatim - no
  design deviation on content.
- `TemplateLifecycleIT` drives through `TemplateService` rather than an authenticated `MockMvc`
  client, exactly as the plan permitted ("the point is the round trip, not the transport") - the HTTP
  transport for these same operations is already covered by `TemplateControllerTest` (plan 04-04).
- `TemplateSeederIT`'s nine tests use `@TestMethodOrder(OrderAnnotation.class)` so the five read-only
  assertions against the `ApplicationRunner`'s startup-seeded state run before the four mutating
  idempotency tests, avoiding any dependency on JUnit's default (unspecified) method ordering.

## Deviations from Plan

None functionally. One process note: the plan's Task 2 `<verify>` command
(`mvn -Dtest=TemplateSeederIT test`) names a test class that is only written in Task 3, so it could
not be run standalone at Task 2's commit point. Task 2 was verified instead with a plain `mvn compile`
plus every acceptance-criteria grep from the plan (all passed), and `TemplateSeederIT` was run for
real - together with `TemplateLifecycleIT` - as part of Task 3's own verification, which exercises
both. No code behavior was affected; this is purely about which shell command validated which commit.

## Issues Encountered

None. Docker was running, JDK 25 was available at `$HOME/.jdks/jdk-25`, and both the targeted test run
(`TemplateSeederIT,TemplateLifecycleIT` - 10/10 passing) and the full `mvn clean test` suite
(410/410 passing, up from the 400-test baseline) completed with zero failures/errors.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Every document type now has a working, realistic starter template active from first start, so
  Phase 5's DOC-01..DOC-04 document generation has something real to render on day one without
  requiring a release manager to hand-author a template first.
- `TemplateSeederIT`'s clean-validation test is a standing regression guard: if a future view-model
  change ever invalidates a starter's placeholder vocabulary, this test fails the build rather than
  silently shipping a broken artefact.
- No blockers identified. Plan 04-05 (logo upload/enforcement) ran concurrently in this same wave and
  was not touched by this plan - no file overlap.

---
*Phase: 04-template-authoring*
*Completed: 2026-08-09*

## Self-Check: PASSED

All 6 created files verified present on disk. All 3 task commits (`946af98`, `5268bff`, `f96f3ec`)
verified present in `git log --all`. Full `mvn clean test` (JAVA_HOME=jdk-25) exits 0 with 410 tests
passing (400 baseline + 10 new), including `ArchitectureBoundaryTest`.
