---
phase: 03-release-assembly
plan: 07
subsystem: ui
tags: [react, tanstack-router, tanstack-query, tailwind, native-dialog]

# Dependency graph
requires:
  - phase: 03-release-assembly
    plan: 04
    provides: POST /api/releases (now returning added/alreadyPresent), POST /api/releases/{id}/items, the RELEASE_FROZEN/RELEASE_DUPLICATE ProblemDetail reasons
  - phase: 03-release-assembly
    plan: 05
    provides: frontend/src/api/releases.ts (Release/ReleaseSummary types, useReleases/useRelease), the /releases and /releases/$releaseId routes, the three messageForReason entries
provides:
  - useCreateRelease/useAddTicketsToRelease mutations and openReleases derivation in frontend/src/api/releases.ts
  - frontend/src/components/AddToReleaseDialog.tsx, the app's first native <dialog> modal
  - the /tickets action bar (Add to release button) and the release detail add-result banner
affects: [03-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Native <dialog> element (showModal()/close()) driven by a useEffect keyed on an `open` prop, rather than a portal/library modal - browser primitive gives focus trap, Escape-to-close and backdrop for free"
    - "TS 4.4+ aliased-condition narrowing (const showBanner = a !== undefined && b !== undefined; then `{showBanner && <uses a, b as non-undefined>}`) works under this repo's strict/noUnusedLocals tsconfig without an explicit non-null assertion"

key-files:
  created:
    - frontend/src/components/AddToReleaseDialog.tsx
  modified:
    - frontend/src/api/releases.ts
    - frontend/tests/api/releases.test.ts
    - frontend/src/routes/TicketsRoute.tsx
    - frontend/src/router.tsx
    - frontend/src/routes/ReleaseDetailRoute.tsx

key-decisions: []

requirements-completed: [REL-01, REL-02]

# Metrics
duration: 45min
completed: 2026-08-05
---

# Phase 3 Plan 7: Add-to-Release Picker and the /tickets → /releases Push Flow Summary

**The phase's primary gesture end to end: select tickets on /tickets, "Add to release" opens a native `<dialog>` picker that lists OPEN releases or creates one inline in a single request, and lands on `/releases/$releaseId` with a dismissible, honestly-worded add-result banner**

## Performance

- **Duration:** 45 min
- **Started:** 2026-08-05T18:12:00Z
- **Completed:** 2026-08-05T18:57:00Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments
- `frontend/src/api/releases.ts` gained `useCreateRelease` (`POST /api/releases`) and `useAddTicketsToRelease` (`POST /api/releases/{id}/items`), both caching the returned `Release` and invalidating the releases list on success, plus `openReleases` which filters to `status === 'OPEN'` preserving server order (no client sort — most recently created release, the likeliest target, stays on top)
- `frontend/tests/api/releases.test.ts` grew from 11 to 17 cases: the OPEN-filter order-preservation test, mocked 201/409/400 on create, and mocked 200 (including the all-duplicate `{ added: 0, alreadyPresent: 3 }` success case)/409 on add-tickets
- `frontend/src/components/AddToReleaseDialog.tsx` — the app's first native `<dialog>` modal (`showModal()`/`close()` via a `useEffect` on `props.open`). Lists OPEN releases with a client-side filter past 8 entries, a "New release…" disclosure form (auto-expanded when zero open releases exist) that creates-and-adds in one request, inline `VALIDATION_FAILED` field errors and the exact `RELEASE_DUPLICATE` sentence, and navigates to the target release carrying `added`/`alreadyPresent` on either success path. Contains no ticket search, project picker, sprint picker or JQL field (D-03-01), and no `jiraProjectKey` form input (D-03-05) — both enforced by grep gates
- `frontend/src/routes/TicketsRoute.tsx` — the `{n} selected` block became a flex row with a new "Add to release" button, present only when `selected.size > 0` (never disabled), opening the dialog with the current selection and the browsed project's Jira key (`browsedProjectKey`, distinct from the existing `selectedProjectKey` empty-state fallback string). The `aria-live="polite"` region is byte-preserved
- `frontend/src/router.tsx` — `releaseDetailRoute` gained a `validateSearch` mirroring `loginRoute`'s shape, coercing `added`/`alreadyPresent` to `number | undefined` so the banner survives navigation and a crafted URL can't inject markup (T-03-44/T-03-45)
- `frontend/src/routes/ReleaseDetailRoute.tsx` — a dismissible `role="status"` banner (`bg-blue-50`, no red anywhere) between the header and item sections, rendering the correct one of D-03-13's three copy variants via `addResultMessage`, with a real `<button aria-label="Dismiss">×</button>`
- Full verification loop (`npm run lint`, `npm test` — 40 passed, `npm run build`) green after every task and once more after all three combined

## Task Commits

Each task was committed atomically:

1. **Task 1: Create/add mutation hooks and the OPEN-release derivation** - `f59caa2` (feat)
2. **Task 2: AddToReleaseDialog — the app's first native `<dialog>` modal** - `f0301ca` (feat)
3. **Task 3: /tickets action bar, the detail route's search contract, and the add-result banner** - `1e3aa82` (feat)

_Note: SUMMARY commit follows below (worktree mode — STATE.md/ROADMAP.md updated by orchestrator, not this commit)._

## Files Created/Modified
- `frontend/src/api/releases.ts` - `useCreateRelease`, `useAddTicketsToRelease`, `openReleases`, `ReleaseAddResult`/`CreateReleaseInput` types
- `frontend/tests/api/releases.test.ts` - 6 new vitest cases (17 total)
- `frontend/src/components/AddToReleaseDialog.tsx` - native `<dialog>` Add-to-release picker (new file)
- `frontend/src/routes/TicketsRoute.tsx` - "Add to release" button, `browsedProjectKey`, `<AddToReleaseDialog>` render
- `frontend/src/router.tsx` - `releaseDetailRoute` gains `validateSearch` for `added`/`alreadyPresent`
- `frontend/src/routes/ReleaseDetailRoute.tsx` - dismissible add-result banner, `addResultMessage` helper

## Decisions Made
None beyond what the plan specified — no deviations required.

## Deviations from Plan

None - plan executed exactly as written. `frontend/node_modules` did not exist in this worktree at plan start (routine `npm install`, 167 packages, 0 vulnerabilities — not a deviation, environment setup only, same as plan 03-05 noted).

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Plan 03-08 (Refresh/Freeze/Delete action row, manual items, remove buttons) can extend `ReleaseDetailRoute.tsx`'s row-3 action slot and the two item tables without touching the add-result banner or header this plan added
- `AddToReleaseDialog` reuses `useReleases('')` exactly as `ReleasesRoute` does, so no separate release-list fetch path exists to keep in sync
- The `validateSearch` precedent on `releaseDetailRoute` (mirroring `loginRoute`) is now established for any future route that needs to carry transient state through navigation

---
*Phase: 03-release-assembly*
*Completed: 2026-08-05*

## Self-Check: PASSED

`frontend/src/components/AddToReleaseDialog.tsx` verified present on disk (231 lines, exceeds the
130-line minimum). All three task commits (`f59caa2`, `f0301ca`, `1e3aa82`) verified present in
`git log --oneline`. Full `npm run lint`, `npm test` (40 passed), and `npm run build` reran
synchronously in the foreground immediately before writing this summary: all three exit 0. All
plan-specified grep gates (D-03-01 no ticket/project/sprint/JQL picker, D-03-05 no
`jiraProjectKey` input, no `dangerouslySetInnerHTML`, no `.sort()`, no raw `fetch(`, single
`aria-live="polite"` region preserved, banner block free of `red-600`) verified passing.
