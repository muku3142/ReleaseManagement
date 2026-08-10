---
phase: 05-document-generation
plan: 07
subsystem: api
tags: [jira, jpa, postgres, jackson, react-types]

# Dependency graph
requires:
  - phase: 05-document-generation
    provides: "05-03's V5 migration, which already created the release_ticket_item.component column - no V6 needed here"
provides:
  - "TicketDetail.component() - fetched from Jira's fields.components[0].name via the existing FieldMetadataService resolution mechanism (TicketField.COMPONENT, display name 'Components')"
  - "release_ticket_item.component materialisation: entity mapping, overwriteSnapshot wiring, toDomain reconstruction"
  - "ReleaseItemResponse.component / TicketResponse.component on the REST API"
  - "frontend ReleaseItem.component / Ticket.component: string | null types (no UI change)"
affects: [05-09-grouping-dimension, 05-04-document-generation-service]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Record-component addition to TicketDetail forces every construction site across the module to update in lockstep - the compiler enumerates the full blast radius, which is why this plan's actual diff touches more test files than its declared files_modified list"

key-files:
  created: []
  modified:
    - src/main/java/com/evision/releasemgmt/domain/jira/TicketField.java
    - src/main/java/com/evision/releasemgmt/domain/jira/TicketDetail.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JiraRestTicketAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseTicketItemEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseItemResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/TicketResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/TicketController.java
    - frontend/src/api/releases.ts
    - frontend/src/api/tickets.ts
    - src/test/java/com/evision/releasemgmt/adapters/jira/JiraRestTicketAdapterTest.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/ReleasePersistenceIT.java
    - src/test/java/com/evision/releasemgmt/application/FieldMetadataServiceTest.java
    - src/test/java/com/evision/releasemgmt/application/TicketSearchServiceTest.java
    - src/test/java/com/evision/releasemgmt/application/ReleaseViewAssemblerTest.java
    - src/test/java/com/evision/releasemgmt/application/ReleaseServiceTicketsTest.java
    - src/test/java/com/evision/releasemgmt/application/TicketSearchPathTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/ReleaseControllerTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/TicketControllerTest.java

key-decisions:
  - "TicketField.COMPONENT display name is 'Components' (plural, capital C) per the plan's own assumption - NOT independently confirmed against the live evisionegypt.atlassian.net tenant's /rest/api/3/field response, because this execution environment has no network/credential access to reach it. See Deviations for the risk this leaves and why it fails loudly rather than silently if wrong."
  - "Reworded the D-05-02 explanatory comment in JiraRestTicketAdapter to avoid the literal string 'Uncategorised' - the plan's own <action> text and its own acceptance criteria contradicted each other (action tells you to write that word in a comment; acceptance criteria greps for zero occurrences of it). Kept the same meaning, different wording."

patterns-established: []

requirements-completed: [DOC-02]

# Metrics
duration: 55min
completed: 2026-08-10
---

# Phase 5 Plan 07: Fetch and Materialise Ticket Component Summary

**Jira's `fields.components[0].name` now flows through `TicketDetail.component()`, resolved by display name (no hardcoded `customfield_NNNNN`), into `release_ticket_item.component`, and out through both the release and ticket REST responses plus their frontend types.**

## Performance

- **Duration:** ~55 min
- **Tasks:** 2
- **Files modified:** 20 (0 created)

## Accomplishments
- `TicketField.COMPONENT("Components")` added; resolved through the existing `FieldMetadataService` mechanism exactly like every other field - no second lookup path, no hardcoded field id
- `JiraRestTicketAdapter.toTicketDetail` maps the first entry of `fields.components` to a single `component` string, defensively handling absent/non-array/empty/nameless cases by returning `null` rather than throwing (mirrors the existing `fixVersions` block's defensive style)
- `release_ticket_item.component` (already created by 05-03's V5 migration) is now mapped by `ReleaseTicketItemEntity`, written by `ReleaseRepositoryAdapter.overwriteSnapshot` (covering create/addItems/refresh in one hook), and read back in `toDomain`
- `ReleaseItemResponse`/`TicketResponse` and their two mapping call sites carry `component` onto the wire; `frontend/src/api/releases.ts`/`tickets.ts` carry a matching `component: string | null` type (no UI surface change - deliberately narrow per the plan)
- Full backend suite: **451 tests**, all green (baseline + 10 new: 7 in `JiraRestTicketAdapterTest`, 4 in `ReleasePersistenceIT`, minus test-count churn from fixture edits elsewhere)
- Full frontend suite: **135 tests / 15 files**, unchanged and green (type-only change, no new frontend tests needed)
- No `V6` migration; no `customfield_` anywhere in `src/main/java`; no `Uncategorised` substitution at the adapter layer

## Task Commits

Each task was committed atomically:

1. **Task 1: Fetch component from Jira and carry it on TicketDetail** - `280251e` (feat)
2. **Task 2: Materialise component onto the release snapshot and expose it on the API** - `2002f2e` (feat)

_No plan-metadata commit - orchestrator owns STATE.md/ROADMAP.md writes after all wave agents complete._

## Files Created/Modified
- `src/main/java/com/evision/releasemgmt/domain/jira/TicketField.java` - `COMPONENT("Components")` added after `FIX_VERSIONS`
- `src/main/java/com/evision/releasemgmt/domain/jira/TicketDetail.java` - `component` record component added after `fixVersions`
- `src/main/java/com/evision/releasemgmt/adapters/jira/JiraRestTicketAdapter.java` - components-array-to-first-name mapping, defensive against absent/empty/nameless
- `src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseTicketItemEntity.java` - nullable `component` column, getter/setter
- `src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java` - `overwriteSnapshot` writes it, `toDomain` reads it
- `src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseItemResponse.java`, `TicketResponse.java` - `component` field added
- `src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java`, `TicketController.java` - mapping call sites updated
- `frontend/src/api/releases.ts`, `frontend/src/api/tickets.ts` - `component: string | null` type field added
- `src/test/java/com/evision/releasemgmt/adapters/jira/JiraRestTicketAdapterTest.java` - 7 new tests covering every `<behavior>` bullet (single component, absent, empty array, two components/first-wins, no-name-object, field-id-requested)
- `src/test/java/com/evision/releasemgmt/adapters/persistence/ReleasePersistenceIT.java` - 4 new tests: round-trip, null round-trip, refresh-overwrites, no-backfill-on-existing-rows
- All other listed test files - forced fixture updates (see Deviations)

## Decisions Made
- Kept the entity/adapter component wiring (`ReleaseTicketItemEntity`, `ReleaseRepositoryAdapter`) in the Task 1 commit rather than strictly deferring it to Task 2, because `TicketDetail`'s new record component made the module uncompilable without it. Task 2's commit contains the DTO/controller/frontend/persistence-test work that was actually independent.
- Live-tenant verification of the "Components" display name (requested by the plan's Task 1 action text) was **not performed** - this sandboxed execution environment has no network path to `https://evisionegypt.atlassian.net`. See Deviations.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Compiler-forced updates to files outside this plan's declared `files_modified`**
- **Found during:** Task 1
- **Issue:** Adding `component` to `TicketDetail`'s record shape breaks every existing `new TicketDetail(...)` call site across the module, and adding `TicketField.COMPONENT` to the enum breaks every test that hand-builds a Jira field-metadata fixture without a "Components" entry (because `TicketSearchService` requests `EnumSet.allOf(TicketField.class)`).
- **Fix:** Updated every flagged call site to pass `null`/the new field, and added a `components`/`"Components"` entry to every hand-built field-metadata fixture. Files affected beyond the plan's list: `FieldMetadataServiceTest.java` (added a `Components` `JiraField` to three fixture lists; `hasSize(9)` → `hasSize(10)` in two places; `times(7)` → `times(8)` in the missing-field-count assertion, since one more field is now missing from the deliberately-sparse fixture), `TicketSearchServiceTest.java`, `ReleaseViewAssemblerTest.java`, `ReleaseServiceTicketsTest.java`, `ReleaseControllerTest.java`, `TicketControllerTest.java`, `TicketSearchPathTest.java` (added `"components":"Components"` to its tenant-field fixture, or `null` args to `TicketDetail` constructors).
- **Files modified:** the 7 files named above, plus `ReleaseTicketItemEntity.java`/`ReleaseRepositoryAdapter.java` for the same reason (their `toDomain`/`overwriteSnapshot` methods construct/read `TicketDetail`).
- **Verification:** `mvn test` - full suite (451 tests) green, no other test class affected.
- **Committed in:** `280251e` (Task 1 commit)

**2. [Rule 1 - Bug] Plan's own action text contradicted its own acceptance criteria**
- **Found during:** Task 1, acceptance-criteria check
- **Issue:** Task 1's `<action>` instructs writing a comment containing the literal string `"Uncategorised"` ("`Uncategorised` is NOT substituted here..."), but Task 1's own `<acceptance_criteria>` asserts `grep -c 'Uncategorised' ... adapter.java` returns `0`. As written, following the action verbatim fails the plan's own acceptance gate.
- **Fix:** Reworded the comment to preserve the identical meaning ("No placeholder default is substituted here...") without the literal string, satisfying both the design-documentation intent and the acceptance criterion.
- **Files modified:** `src/main/java/com/evision/releasemgmt/adapters/jira/JiraRestTicketAdapter.java`
- **Verification:** `grep -c 'Uncategorised' JiraRestTicketAdapter.java` now returns `0`; full backend suite still green.
- **Committed in:** `280251e` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking/compiler-forced, 1 plan-internal contradiction)
**Impact on plan:** Both were mechanically necessary to keep the module compiling and the plan's own acceptance criteria satisfiable. No functional scope creep - no new behavior was added beyond what Task 1/Task 2 specified.

## Issues Encountered
- **Live-tenant field-name verification could not be performed.** The plan's Task 1 action explicitly asks to "verify this against the live tenant's `/rest/api/3/field` response during execution" for the `COMPONENT` display name, warning that getting it wrong breaks *every* ticket search (T-05-37 in the plan's own threat model). This execution environment (a git worktree with Maven/npm but no configured Jira credentials or outbound network path to `https://evisionegypt.atlassian.net`) cannot make that call. The value used, `"Components"`, is the plan's own stated assumption and matches Jira Cloud's documented system-field naming convention, but **remains unverified against the live CNV tenant**. If wrong, `FieldMetadataService.resolve` will throw `JIRA_ERROR` on every ticket search the next time a real user signs in - loud, not silent, per the existing safety net T-05-37 already documents. **Recommended follow-up:** a human with tenant access should run `GET /rest/api/3/field` against `evisionegypt.atlassian.net` once and confirm the field is literally named "Components" before this reaches production, or exercise the app against the real tenant and watch for `JIRA_ERROR`/500s on ticket search.

## User Setup Required

None - no external service configuration required. (See Issues Encountered above for the one verification step a human with live Jira access should still perform.)

## Next Phase Readiness
- `TicketDetail.component()` and `release_ticket_item.component` are fully wired end-to-end (Jira fetch → snapshot → API → frontend types) and ready for **05-09** to consume as a grouping dimension (D-05-04) without further schema or fetch-layer work.
- No blockers for downstream plans. The one open item is the live-tenant field-name confirmation noted above, which is an operational verification step, not a code blocker.

---
*Phase: 05-document-generation*
*Completed: 2026-08-10*

## Self-Check: PASSED

All 13 checked files (11 main/test source files plus this SUMMARY.md, and the frontend
`releases.ts`/`tickets.ts` type files) verified present on disk; both task commits
(`280251e`, `2002f2e`) verified present in `git log --oneline --all`.
