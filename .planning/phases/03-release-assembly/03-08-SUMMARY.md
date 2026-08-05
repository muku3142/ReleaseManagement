---
phase: 03-release-assembly
plan: 08
subsystem: ui
tags: [react, tanstack-router, tanstack-query, tailwind, native-dialog]

# Dependency graph
requires:
  - phase: 03-release-assembly
    plan: 05
    provides: ReleaseDetailRoute's original structure, useParams route-literal convention, releases.ts base types
  - phase: 03-release-assembly
    plan: 03
    provides: freeze/delete endpoints, RELEASE_* ProblemDetail reason codes
  - phase: 03-release-assembly
    plan: 04
    provides: refresh/items endpoint contracts
  - phase: 03-release-assembly
    plan: 07
    provides: AddToReleaseDialog's native <dialog> mechanism, releaseDetailRoute's validateSearch and add-result banner
provides:
  - The seven remaining release mutation hooks (useRefreshRelease, useFreezeRelease, useDeleteRelease, useRemoveTicketItem, useAddManualItem, useUpdateManualItem, useRemoveManualItem)
  - frontend/src/components/ConfirmDialog.tsx, the shared freeze/delete confirmation
  - The complete release detail screen (OPEN-only action row, per-row Remove, manual-item add/edit form)
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "useReleaseMutation<TVars> factors the setQueryData+invalidateQueries success handler shared by six of the seven new hooks; useDeleteRelease is the sole exception since a 204 DELETE has no body to cache"
    - "Comments documenting an intentionally-absent affordance (e.g. 'no unfreeze hook') must avoid literal substrings the plan's own grep gates scan for - both the plan's example code and this executor's first draft tripped this on 'unfreeze' and '<form>' respectively"
    - "TypeScript does not narrow a const across a nested function-declaration boundary inside the same component body; capture the narrowed field (data.id) into its own const before the closure that uses it"

key-files:
  created:
    - frontend/src/components/ConfirmDialog.tsx
  modified:
    - frontend/src/api/releases.ts
    - frontend/tests/api/releases.test.ts
    - frontend/src/routes/ReleaseDetailRoute.tsx

key-decisions:
  - "Reworded two comments that referenced 'unfreeze' hook-absence and the confirmation dialog's form-less design, because the plan's own acceptance-criteria grep gates (grep -ci 'unfreeze', grep -c '<form') would have matched the literal substring inside the comment text itself - this mirrors the Rule 1 self-contradiction plan 03-04 found in its own test spec"

requirements-completed: [REL-02, REL-03, REL-08]

# Metrics
duration: 55min
completed: 2026-08-06
---

# Phase 3 Plan 8: Release Detail Action Row, Item Removal, and Manual-Item Authoring Summary

**Completes the release detail screen: Refresh/Freeze/Delete behind a shared ConfirmDialog, per-row Remove on both item tables, and an add/edit manual-item form generated from the fixed four-category enum - every write affordance disappears, not disables, when the release is FROZEN**

## Performance

- **Duration:** 55 min
- **Started:** 2026-08-05T23:10:00Z
- **Completed:** 2026-08-06T00:05:00Z
- **Tasks:** 3
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments
- `frontend/src/api/releases.ts` gained seven mutation hooks: `useRefreshRelease`, `useFreezeRelease`, `useRemoveTicketItem`, `useAddManualItem`, `useUpdateManualItem`, `useRemoveManualItem` (all sharing a `useReleaseMutation<TVars>` factory that writes the returned `Release` into the cache and invalidates the releases list) plus `useDeleteRelease` (the sole exception, since `DELETE /api/releases/{id}` returns 204 with no body)
- `frontend/tests/api/releases.test.ts` grew from 17 to 26 cases: mocked 200/409 on refresh and freeze, 204/409 on delete, 200 on remove-ticket, 201 with a `DATABASE_SCRIPT` category and a 400 with `fieldErrors.title` on manual-items, and a D-03-10 assertion that no exported symbol from the module matches `/unfreeze/i`
- `frontend/src/components/ConfirmDialog.tsx` (new, 52 lines) - the shared native `<dialog>` confirmation used by both freeze and delete, reusing `AddToReleaseDialog`'s `showModal()`/`close()` effect. Contains no form element, so confirming an irreversible action requires an explicit click on the named button, never Enter on a bare focus state
- `frontend/src/routes/ReleaseDetailRoute.tsx` (224 -> 536 lines) - row 3's action row (Refresh from Jira / Freeze release / Delete release), gated on `data.status === 'OPEN'` and entirely absent (not disabled) when `FROZEN`; the two `ConfirmDialog` instances with the UI spec's exact freeze/delete copy; a per-row Remove on the Tickets table (no confirmation, since a removed ticket can be re-added at any time while the release stays open) with the expand-panel `colSpan` widened by one when `isOpen`; per-row Edit/Remove on the Manual items table; and the "+ Add manual item" disclosure form whose category `<select>` is generated from `MANUAL_ITEM_CATEGORIES`/`MANUAL_ITEM_CATEGORY_LABELS` so the four values have exactly one definition
- Full verification loop (`npm run lint`, `npm test` - 49 passed across 5 files, `npm run build`) green after every task and once more after all three combined
- All plan-specified grep gates verified passing: zero `unfreeze`/`reopen` identifiers in `releases.ts` and `ReleaseDetailRoute.tsx`, zero `<form` in `ConfirmDialog.tsx`, zero drag/reorder/position affordances, zero ticket-search/project/sprint/JQL references, zero `dangerouslySetInnerHTML`, zero hand-written category `<option value="...">` literals

## Task Commits

Each task was committed atomically:

1. **Task 1: The seven remaining mutation hooks** - `f6075b8` (feat)
2. **Task 2: ConfirmDialog and the OPEN-only action row** - `5523216` (feat)
3. **Task 3: Per-row Remove and the add/edit manual-item form** - `d2bc005` (feat)

_Note: SUMMARY commit follows below (worktree mode - STATE.md/ROADMAP.md updated by orchestrator, not this commit)._

## Files Created/Modified
- `frontend/src/api/releases.ts` - seven new mutation hooks, `useReleaseMutation<TVars>` shared success handler
- `frontend/tests/api/releases.test.ts` - 9 new vitest cases (26 total), including the no-unfreeze-export assertion
- `frontend/src/components/ConfirmDialog.tsx` - shared native `<dialog>` freeze/delete confirmation (new file)
- `frontend/src/routes/ReleaseDetailRoute.tsx` - OPEN-only action row, two `ConfirmDialog` instances, per-row Remove on both item tables, the add/edit manual-item form

## Decisions Made
- Reworded the `useFreezeRelease` doc comment and `ConfirmDialog`'s file-header comment to avoid the literal substrings `unfreeze` and `<form` - see Deviations below.
- Captured `data.id` into a local `currentReleaseId` const before `handleManualItemSubmit`'s closure, because TypeScript's control-flow narrowing of `const data = release.data` does not persist across a nested `function` declaration boundary in this codebase's strict tsconfig.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The plan's own example code for `useFreezeRelease` contained a comment that would fail its own acceptance-criteria grep gate**
- **Found during:** Task 1, before committing
- **Issue:** The plan's literal example code for `useFreezeRelease` includes the comment `// D-03-10: there is a freeze and no inverse. Do not add an unfreeze hook.` immediately above the function. The same task's acceptance criteria requires `grep -ci "unfreeze\|reopen" frontend/src/api/releases.ts` to output `0`. Following the example code verbatim would have failed the plan's own literal verification command - the same class of self-contradiction plan 03-04's summary documented in its own test spec.
- **Fix:** Reworded the comment to `// D-03-10: freeze is one-way. No reverse mutation exists or should be added here.`, preserving the documented intent (do not add a reverse-freeze mutation) without the trigger substring.
- **Files modified:** `frontend/src/api/releases.ts`
- **Verification:** `grep -ci "unfreeze\|reopen" frontend/src/api/releases.ts` outputs `0`; `npm run lint` and `npm test` still exit 0
- **Committed in:** `f6075b8` (Task 1 commit)

**2. [Rule 1 - Bug] My own first-draft `ConfirmDialog.tsx` header comment tripped its own no-`<form>` grep gate**
- **Found during:** Task 2, before committing
- **Issue:** Task 2's acceptance criteria requires `grep -c '<form' frontend/src/components/ConfirmDialog.tsx` to output `0`. The file's header comment explaining the design (no `<form>` element, so confirming requires an explicit click) literally contained the substring `<form>`, which the grep gate matches regardless of it being prose rather than markup.
- **Fix:** Reworded the comment to say "No form element exists here" instead of using the `<form>` tag syntax, preserving the same explanation without the literal substring.
- **Files modified:** `frontend/src/components/ConfirmDialog.tsx`
- **Verification:** `grep -c '<form' frontend/src/components/ConfirmDialog.tsx` outputs `0`
- **Committed in:** `5523216` (Task 2 commit)

**3. [Rule 1 - Bug] `release.id` does not exist on the `useRelease()` query-result type; needed `data.id`**
- **Found during:** Task 2, first `npm run lint` run
- **Issue:** The plan's example code calls `refresh.mutate(release.id)`, `freeze.mutate(release.id, ...)` and `remove.mutate(release.id, ...)`, but `release` is the whole `useQuery` result object (`UseQueryResult<Release>`), not the `Release` itself - `id` lives on `release.data`, already destructured as `const data = release.data` earlier in the component for exactly this purpose.
- **Fix:** Changed all three call sites to `data.id` instead of `release.id`.
- **Files modified:** `frontend/src/routes/ReleaseDetailRoute.tsx`
- **Verification:** `npm run lint` exits 0
- **Committed in:** `5523216` (Task 2 commit)

**4. [Rule 1 - Bug] TypeScript narrowing of `data` did not persist into a nested closure**
- **Found during:** Task 3, first `npm run lint` run
- **Issue:** `handleManualItemSubmit`, declared with the `function` keyword inside the component body after `const data = release.data; if (!data) return null;`, referenced `data.id` twice. TypeScript reported both as `'data' is possibly undefined'` - the control-flow narrowing established by the early-return guard does not carry into a nested function-declaration closure under this repo's strict tsconfig.
- **Fix:** Captured `const currentReleaseId = data.id;` immediately before the closure and used `currentReleaseId` in both `mutate` calls inside `handleManualItemSubmit`.
- **Files modified:** `frontend/src/routes/ReleaseDetailRoute.tsx`
- **Verification:** `npm run lint` exits 0
- **Committed in:** `d2bc005` (Task 3 commit)

---

**Total deviations:** 4 auto-fixed (all Rule 1 - bugs blocking the plan's own literal verification commands or type-checking)
**Impact on plan:** All four fixes were required for `npm run lint`/the plan's own grep-gate acceptance criteria to pass; none change the screen's behavior or the UI spec's contracted copy. No scope creep.

## Issues Encountered
- `frontend/node_modules` did not exist in this worktree at plan start; ran `npm install` before any verification could execute (167 packages, 0 vulnerabilities). Not a deviation - routine environment setup, matching the same note in plans 03-05 and 03-07.
- The execution stream stalled twice mid-task on long uninterrupted tool-call stretches; both times resumed cleanly from the last committed state per the coordinator's verification (no work was lost, only in-progress edits needed re-verification against the actual file on disk before continuing).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Phase 3's four success criteria (REL-02, REL-03, REL-08, plus the frozen-release read-only guarantee) are now all reachable end-to-end from the UI: create -> add tickets -> add manual items -> remove either -> refresh -> freeze -> (delete only while still open)
- No unfreeze affordance exists at any layer (frontend hooks, frontend UI, or backend endpoint per plan 03-03's own test) - three independent gates (grep on `releases.ts`, grep on `ReleaseDetailRoute.tsx`, vitest assertion) all confirm this
- This is the last plan in Phase 3's wave 6 per the roadmap; no further frontend work in this phase depends on `ReleaseDetailRoute.tsx`

---
*Phase: 03-release-assembly*
*Completed: 2026-08-06*
