---
phase: 05-document-generation
plan: 09
subsystem: api
tags: [spring-boot, jpa, postgres, react, tanstack-query, mustache]

# Dependency graph
requires:
  - phase: 05-document-generation
    provides: "05-03's V5 migration (template.grouping_dimension column) and 05-07's TicketDetail/release_ticket_item.component - both consumed here, neither re-created"
provides:
  - "GroupingDimension enum (ISSUE_TYPE/COMPONENT) as a per-template persisted setting"
  - "ItemView.component and the {{component}} placeholder, resolved through RenderContextMapper and documented in PlaceholderCatalog"
  - "ReleaseViewAssembler.assemble(release, dimension) - the one-argument overload is gone, every call site states its dimension explicitly"
  - "DocumentGenerationService groups by the active template's own persisted dimension, not a hardcoded default"
  - "TemplateDetailRoute's Group by toolbar control, wired into the existing dirty/seed/Save flow"
affects: [05-10, 05-11]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Template becoming a 13-component record forces every hand-built `new Template(...)` and `saveContent(...)` call site across the module (including test fixtures) to update in lockstep - same blast-radius pattern 05-07 documented for TicketDetail"

key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/template/GroupingDimension.java
  modified:
    - src/main/java/com/evision/releasemgmt/domain/template/view/ItemView.java
    - src/main/java/com/evision/releasemgmt/domain/template/view/SampleReleaseView.java
    - src/main/java/com/evision/releasemgmt/application/ReleaseViewAssembler.java
    - src/main/java/com/evision/releasemgmt/application/RenderContextMapper.java
    - src/main/java/com/evision/releasemgmt/domain/template/PlaceholderCatalog.java
    - src/main/java/com/evision/releasemgmt/domain/template/Template.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/TemplateEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/TemplateRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/application/TemplateService.java
    - src/main/java/com/evision/releasemgmt/application/DocumentGenerationService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/TemplateController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/SaveTemplateContentRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/TemplateResponse.java
    - src/main/java/com/evision/releasemgmt/config/TemplateSeeder.java
    - frontend/src/api/templates.ts
    - frontend/src/routes/TemplateDetailRoute.tsx
    - src/test/java/com/evision/releasemgmt/application/ReleaseViewAssemblerTest.java
    - src/test/java/com/evision/releasemgmt/application/RenderServiceTest.java
    - src/test/java/com/evision/releasemgmt/application/DocumentGenerationServiceTest.java
    - src/test/java/com/evision/releasemgmt/application/TemplateServiceTest.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/TemplatePersistenceIT.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/DocumentSnapshotPersistenceIT.java
    - src/test/java/com/evision/releasemgmt/adapters/web/TemplateControllerTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/TemplateLifecycleIT.java
    - src/test/java/com/evision/releasemgmt/adapters/web/TemplateLogoControllerTest.java
    - frontend/tests/components/TemplateDetailRoute.test.tsx
    - frontend/tests/api/templates.test.ts
    - frontend/tests/components/ChromePreview.test.tsx
    - frontend/tests/components/TemplatePreview.test.tsx

key-decisions:
  - "PlaceholderDrawer.tsx needed no edit - both the changed groups description and the new items.component catalog entry flow through the backend /api/templates/catalog endpoint the drawer already renders generically. Removed from the plan's touched-file set in this summary rather than making a no-op edit."
  - "setGroupingDimension appears 2 times in TemplateRepositoryAdapter (toEntity, update), not the 3 the plan's acceptance criteria anticipated - duplicate() constructs a Template and calls repository.create(), which itself calls toEntity(), so there is no third distinct call site. The plan's own acceptance-criteria text pre-authorized this adjustment."
  - "Reworded the D-05-03 explanatory comment above groupKey() to avoid the literal string 'Uncategorised' - identical class of self-contradiction 05-07 found: the plan's own action text asks for a comment containing that word, while its own acceptance criteria greps for exactly one occurrence in the file. Kept the same meaning ('the no-component display default'), satisfying both."
  - "DocumentGenerationService and TemplateController's render-context endpoint were touched inside Task 1's commit even though the plan assigns them to Task 2 - the assemble() signature change makes the module uncompilable without touching every call site in the same commit that changes the signature. DocumentGenerationService temporarily hardcoded GroupingDimension.ISSUE_TYPE in that commit and was corrected to template.groupingDimension() in Task 2's commit, exactly as the plan's own Task 1 action text anticipated ('DocumentGenerationService is updated in Task 2')."

patterns-established: []

requirements-completed: [DOC-02]

# Metrics
duration: ~55min (across two work sessions - the first was ended by a stream-stall watchdog with no commits lost, since nothing had been committed yet at that point)
completed: 2026-08-10
---

# Phase 5 Plan 09: Per-Template Grouping Dimension + {{component}} Summary

**Grouping dimension (issue type or component) is now a persisted per-template setting threaded end-to-end from a toolbar `<select>` through `saveContent`, `Template`, and `DocumentGenerationService.generate` into `ReleaseViewAssembler.assemble`, with `{{component}}` resolving inside `{{#items}}` and `Uncategorised` applied at exactly one layer.**

## Performance

- **Duration:** ~55 min of active work (session was interrupted once by a stream-stall watchdog before any commit existed; resumed cleanly from disk per the coordinator's message)
- **Tasks:** 3
- **Files modified:** 29 (1 created: `GroupingDimension.java`)

## Accomplishments
- `GroupingDimension` enum (`ISSUE_TYPE`/`COMPONENT`) matches V5's `CHECK` constraint exactly; no V6 migration written
- `ItemView.component()` added after `reporter`; mapped from `TicketDetail.component()` (05-07's data) at the assembler layer, `""` for manual items and componentless tickets
- `ReleaseViewAssembler.assemble` now requires `(Release, GroupingDimension)` with **no defaulting overload** - the compiler enumerates every call site, and all three (`TemplateController`'s preview endpoint, `DocumentGenerationService`, every test) now state their dimension explicitly
- `groupKey()` applies `Uncategorised` for a blank component **only at the view-model layer**, sorting alphabetically among real component names (no special-casing to the end) - proven by a dedicated test with an "Aardvark Module" ahead of "Uncategorised"
- `{{component}}` resolves through `RenderContextMapper.itemMap` and is documented in `PlaceholderCatalog`; the `groups` entry's description was updated to the UI-SPEC's exact new copy
- `Template.groupingDimension` persists via `TemplateEntity`/`TemplateRepositoryAdapter` (`toEntity`, `update`, `toDomain`), defaults to `ISSUE_TYPE` on `create`, and `duplicate()` carries the source's setting to the copy
- `SaveTemplateContentRequest.groupingDimension` is bean-validated (`@Pattern(regexp = "^(ISSUE_TYPE|COMPONENT)?$")`) so an unknown value 400s as `VALIDATION_FAILED` before the service runs, while null/blank defaults to `ISSUE_TYPE` for an older client's payload; `TemplateResponse.groupingDimension` round-trips it on the wire
- `DocumentGenerationService.generate` now groups by **the active template's own** `groupingDimension` rather than a hardcoded default, keeping a snapshot's grouping self-consistent with its copied `templateBody` (D-05-53)
- `TemplateDetailRoute` gained one native, labelled `<select aria-label="Group by">` in the toolbar's left cluster, wired into the *existing* seed/dirty/`useBlocker`/Save state machinery - changing it shows "Unsaved changes" and is only persisted on "Save template", exactly like a chrome field
- Full backend suite: **504 tests**, all green (493 baseline + 11 new)
- Full frontend suite: **139 tests / 15 files**, all green (135 baseline + 4 new); `tsc -b` clean on two consecutive runs

## Task Commits

Each task was committed atomically:

1. **Task 1: GroupingDimension, ItemView.component, the assembler dimension and the catalog** - `b5e22c6` (feat)
2. **Task 2: Persist the setting on the template and expose it on the API** - `d4d1d3d` (feat)
3. **Task 3: Group by control in the authoring toolbar and the catalog drawer copy** - `8294c52` (feat)

_No plan-metadata commit - orchestrator owns STATE.md/ROADMAP.md writes after all wave agents complete._

## Files Created/Modified
- `src/main/java/com/evision/releasemgmt/domain/template/GroupingDimension.java` - new enum
- `src/main/java/com/evision/releasemgmt/domain/template/view/ItemView.java` - `component` field added after `reporter`
- `src/main/java/com/evision/releasemgmt/domain/template/view/SampleReleaseView.java` - three fixtures updated per the plan's exact assignment (`"Matching Module"`, `""`, `""`)
- `src/main/java/com/evision/releasemgmt/application/ReleaseViewAssembler.java` - `assemble(release, dimension)`, `groups(items, dimension)`, `groupKey()`
- `src/main/java/com/evision/releasemgmt/application/RenderContextMapper.java` - `component` mapped into the item context
- `src/main/java/com/evision/releasemgmt/domain/template/PlaceholderCatalog.java` - `items.component` entry; `groups` description updated
- `src/main/java/com/evision/releasemgmt/domain/template/Template.java` - `groupingDimension` record component
- `src/main/java/com/evision/releasemgmt/adapters/persistence/entity/TemplateEntity.java` - `grouping_dimension` column mapping
- `src/main/java/com/evision/releasemgmt/adapters/persistence/TemplateRepositoryAdapter.java` - `toEntity`/`update`/`toDomain` carry the field
- `src/main/java/com/evision/releasemgmt/application/TemplateService.java` - `saveContent` gains the parameter; `duplicate`/`rename` carry it through
- `src/main/java/com/evision/releasemgmt/application/DocumentGenerationService.java` - groups by `template.groupingDimension()`
- `src/main/java/com/evision/releasemgmt/adapters/web/TemplateController.java` - `saveContent` parses/defaults the request field; `renderContext` states `ISSUE_TYPE` explicitly; `toResponse` carries it
- `src/main/java/com/evision/releasemgmt/adapters/web/dto/SaveTemplateContentRequest.java` - `groupingDimension` with the bean-validation pattern
- `src/main/java/com/evision/releasemgmt/adapters/web/dto/TemplateResponse.java` - `groupingDimension` field
- `src/main/java/com/evision/releasemgmt/config/TemplateSeeder.java` - starter seeding states `GroupingDimension.ISSUE_TYPE` explicitly (compiler-forced, see Deviations)
- `frontend/src/api/templates.ts` - `GROUPING_DIMENSIONS`/`GROUPING_DIMENSION_LABELS`, `Template.groupingDimension`, `ItemView.component`, `useSaveTemplateContent` sends the dimension
- `frontend/src/routes/TemplateDetailRoute.tsx` - the `Group by` select and its state wiring
- All other listed test files - forced fixture updates (see Deviations) plus the plan's own newly-named tests

## Decisions Made
See `key-decisions` in the frontmatter above for the four load-bearing calls made during execution (the PlaceholderDrawer no-op, the `setGroupingDimension` count adjustment, the reworded comment, and the Task-1/Task-2 compile-forced split).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Plan's own action text contradicted its own acceptance criteria (same class as 05-07)**
- **Found during:** Task 1, acceptance-criteria check
- **Issue:** Task 1's `<action>` specifies a comment above `groupKey()` containing the literal string `"Uncategorised"` ("`"Uncategorised"` is applied HERE..."), but Task 1's own `<acceptance_criteria>` asserts `grep -c 'Uncategorised' ReleaseViewAssembler.java` returns `1`. Writing the comment verbatim plus the code's own use of the literal produces 2 occurrences.
- **Fix:** Reworded the comment to "the no-component display default is applied HERE..." - identical meaning, no literal duplicate.
- **Files modified:** `src/main/java/com/evision/releasemgmt/application/ReleaseViewAssembler.java`
- **Verification:** `grep -c 'Uncategorised' ReleaseViewAssembler.java` now returns `1`; full test suite still green.
- **Committed in:** `b5e22c6` (Task 1 commit)

**2. [Rule 3 - Blocking] Compiler-forced updates to files outside Task 1's declared list, to keep the module compiling**
- **Found during:** Task 1
- **Issue:** Removing the one-argument `assemble(Release)` overload (as the plan explicitly requires) immediately breaks `TemplateController`'s preview endpoint, `DocumentGenerationService`, and every test that called the old signature - none of which are in Task 1's declared `<files>` list, but all of which must change in the same commit that removes the overload, or the build does not compile at all.
- **Fix:** Updated `TemplateController.renderContext` to pass `GroupingDimension.ISSUE_TYPE` explicitly (a preview is not tied to one template, per the plan's own guidance) and `DocumentGenerationService` to pass `GroupingDimension.ISSUE_TYPE` as a **temporary** placeholder, exactly as the plan's own Task 1 text anticipates ("DocumentGenerationService is updated in Task 2"). Also fixed `ReleaseViewAssemblerTest`, `RenderServiceTest` (ItemView's new `component` slot), and `DocumentGenerationServiceTest`'s mock stubs.
- **Files modified:** `TemplateController.java`, `DocumentGenerationService.java`, `ReleaseViewAssemblerTest.java`, `RenderServiceTest.java`, `DocumentGenerationServiceTest.java`
- **Verification:** `mvn test -Dtest='ReleaseViewAssemblerTest,PlaceholderCatalogTest,RenderServiceTest,TemplateServiceTest,DocumentGenerationServiceTest'` green.
- **Committed in:** `b5e22c6` (Task 1 commit)

**3. [Rule 3 - Blocking] Template becoming a 13-component record broke every hand-built construction site, including one the plan did not name**
- **Found during:** Task 2
- **Issue:** Adding `groupingDimension` to `Template` and a parameter to `TemplateService.saveContent` breaks every `new Template(...)` call and every `saveContent(...)` call across the module. The plan named most of these in its Task 2 `<files>` list, but `config/TemplateSeeder.java` (which seeds the three starter templates through the normal `create`/`saveContent`/`activate` path) was not on that list and still had to change, plus six test files: `DocumentSnapshotPersistenceIT.java`, `TemplatePersistenceIT.java`, `TemplateControllerTest.java`, `TemplateLifecycleIT.java`, `TemplateLogoControllerTest.java`, and both `TemplateServiceTest.java`/`DocumentGenerationServiceTest.java` needed more fixture updates than the plan's four named new test methods implied.
- **Fix:** `TemplateSeeder` now states `GroupingDimension.ISSUE_TYPE` explicitly for all three starters (none of the seeded starters names a component-shaped section, so there is nothing for `COMPONENT` to improve on). Every other flagged call site got the new argument/field in position; `DocumentGenerationServiceTest`'s `activeTemplate()` helper gained a 4-arg overload so its stub of `assembler.assemble(release, template.groupingDimension())` tracks whatever dimension a given test's template carries, rather than a value hardcoded independent of the template.
- **Files modified:** `TemplateSeeder.java`, `DocumentSnapshotPersistenceIT.java`, `TemplatePersistenceIT.java`, `TemplateControllerTest.java`, `TemplateLifecycleIT.java`, `TemplateLogoControllerTest.java`, `TemplateServiceTest.java`, `DocumentGenerationServiceTest.java`
- **Verification:** `mvn test` - full suite (504 tests) green.
- **Committed in:** `d4d1d3d` (Task 2 commit)

**4. [Rule 2 - Missing test coverage] Added a persistence round-trip test for the COMPONENT dimension**
- **Found during:** Task 2
- **Issue:** The plan's Task 2 named four new unit-level tests (`TemplateServiceTest`/`DocumentGenerationServiceTest`) but no integration-level assertion that `repository.update` actually persists a non-default `groupingDimension` value against real PostgreSQL.
- **Fix:** Added `updateRoundTripsAComponentGroupingDimension` to `TemplatePersistenceIT` and an assertion on the existing round-trip test that a freshly created template defaults to `ISSUE_TYPE`.
- **Files modified:** `TemplatePersistenceIT.java`
- **Verification:** New test passes against Testcontainers PostgreSQL 18.
- **Committed in:** `d4d1d3d` (Task 2 commit)

**5. [Rule 3 - Blocking] ItemView's new `component` field broke three frontend test fixtures the plan did not name**
- **Found during:** Task 3, `tsc -b`
- **Issue:** `frontend/tests/api/templates.test.ts`'s `Template` fixture and `frontend/tests/components/ChromePreview.test.tsx`/`TemplatePreview.test.tsx`'s `ItemView` fixtures are structurally-typed object literals that fail to compile once `groupingDimension`/`component` become required fields, but none of these three files are in the plan's declared `files_modified` list.
- **Fix:** Added `groupingDimension: 'ISSUE_TYPE'` and `component: ''` to the affected fixtures.
- **Files modified:** `templates.test.ts`, `ChromePreview.test.tsx`, `TemplatePreview.test.tsx`
- **Verification:** `npx tsc -b` clean on two consecutive runs; `npx vitest run` - 139/139 green.
- **Committed in:** `8294c52` (Task 3 commit)

---

**Total deviations:** 5 auto-fixed (1 plan-internal contradiction, 3 compiler-forced blocking, 1 missing-test-coverage addition)
**Impact on plan:** All were mechanically necessary to keep the module compiling or to close a real coverage gap the plan's own test list didn't quite reach. No functional scope creep beyond what Tasks 1-3 specified - epic and label grouping remain explicitly out of scope per D-05-02, matching the coordinator's reminder.

## Known Stubs

None. Every code path this plan touches (grouping, `{{component}}`, the persisted setting, the toolbar control) is fully wired to real data with no hardcoded empty/placeholder values.

## Threat Flags

None - every new surface (`SaveTemplateContentRequest.groupingDimension`, `{{component}}`, the `assemble` call sites) is explicitly covered by the plan's own `<threat_model>` (T-05-49 through T-05-53), and this execution introduced no additional surface beyond what that register anticipated.

## Issues Encountered
- The session was interrupted once by a stream-stall watchdog (transport issue, not a work failure) partway through Task 1, before any commit existed. Resumed per the coordinator's message with no lost work - `git status`/`git log` were re-verified against the coordinator's stated state before continuing.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- The grouping dimension is fully wired end-to-end (persisted setting -> API -> generation -> authoring UI) and ready for any downstream plan that needs to reference a template's chosen grouping.
- `{{component}}` is available in the placeholder catalog and in every rendering context (preview, save-time validation, and generation) for template authors to use immediately, including in the `docs/templates/orchstro-release-notes.md` reference template's five component-shaped sections that this plan was written to unblock.
- No blockers for 05-10/05-11. Epic and label grouping remain explicitly out of scope (D-05-02) and are not started here.

---
*Phase: 05-document-generation*
*Completed: 2026-08-10*

## Self-Check: PASSED

All 9 spot-checked key files verified present via `git ls-files --error-unmatch` (GroupingDimension.java,
ItemView.java, ReleaseViewAssembler.java, Template.java, TemplateEntity.java, DocumentGenerationService.java,
SaveTemplateContentRequest.java, frontend/src/api/templates.ts, frontend/src/routes/TemplateDetailRoute.tsx).
All three task commits (`b5e22c6`, `d4d1d3d`, `8294c52`) verified present in `git log --oneline --all`.
Full backend suite (504 tests) and full frontend suite (139 tests / 15 files) both green as of the
last task commit.
