---
phase: 03-release-assembly
plan: 05
subsystem: ui
tags: [react, tanstack-router, tanstack-query, tailwind, tsc-build]

# Dependency graph
requires:
  - phase: 03-release-assembly
    plan: 03
    provides: the /api/releases REST surface and its five DTOs (ReleaseSummaryResponse, ReleaseResponse, ReleaseItemResponse, ManualItemResponse), consumed one-for-one by releases.ts
provides:
  - frontend/src/api/releases.ts (Release/ReleaseSummary/ReleaseItem/ManualItem types, useReleases/useRelease hooks, releaseSearchPath)
  - three new messageForReason entries (RELEASE_NOT_FOUND, RELEASE_FROZEN, RELEASE_DUPLICATE)
  - the release library screen (/releases) and release detail screen (/releases/$releaseId), both registered under authenticatedRoute
  - the Releases nav entry in AppShell
affects: [03-06, 03-07, 03-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "useParams({ from: <route-id> }) for a pathless authenticated layout route (id: 'authenticated', no path) must use the /authenticated-prefixed route id, not the URL path used by to= — the two type spaces diverge in this router config"
    - "tsc -b (build mode with project references) must not also receive a CLI --noEmit flag when a referenced project is composite:true; the outer app project's own tsconfig noEmit:true is sufficient"

key-files:
  created:
    - frontend/src/api/releases.ts
    - frontend/src/routes/ReleaseDetailRoute.tsx
    - frontend/src/routes/ReleasesRoute.tsx
    - frontend/tests/api/releases.test.ts
  modified:
    - frontend/src/api/auth.ts
    - frontend/src/router.tsx
    - frontend/src/components/AppShell.tsx
    - frontend/package.json

key-decisions:
  - "useParams from-literal is /authenticated/releases/$releaseId, not /releases/$releaseId as the plan's example showed — TanStack Router's from-typed route-id space includes the pathless authenticatedRoute's id segment even though to= navigation and the actual URL do not"
  - "frontend/package.json's lint script changed from `tsc -b --noEmit` to `tsc -b` — the CLI --noEmit flag conflicts with the composite tsconfig.node.json project reference (TS6310), a pre-existing repo-wide defect that blocked npm run lint before any of this plan's own file changes"

requirements-completed: [REL-04, REL-05]

# Metrics
duration: 55min
completed: 2026-08-05
---

# Phase 3 Plan 5: Release Library and Release Detail Read Screens Summary

**Release library (/releases) with debounced search and release detail (/releases/$releaseId) with stored ticket-snapshot and manual-item tables, backed by a new releases.ts API module and three new ProblemDetail reason messages**

## Performance

- **Duration:** 55 min
- **Started:** 2026-08-05T14:12:00Z
- **Completed:** 2026-08-05T15:07:00Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments
- `frontend/src/api/releases.ts` — types mirroring the five backend DTOs from plan 03-03 field-for-field, `useReleases`/`useRelease` TanStack Query hooks, `releaseSearchPath` with `encodeURIComponent` on the search term (T-03-30)
- `auth.ts` gained `RELEASE_NOT_FOUND`, `RELEASE_FROZEN`, `RELEASE_DUPLICATE` in `REASON_MESSAGES`, verbatim UI-spec copy
- `ReleaseDetailRoute.tsx` — loading / not-found (no retry, "Back to releases") / generic-error+retry / loaded states; header with name, version, status badge, meta row, frozen banner; Tickets table reusing Phase 2's expand-toggle pattern near-verbatim (stored snapshot, no Jira call, D-03-06); Manual items table using `MANUAL_ITEM_CATEGORY_LABELS`; both empty-state copy variants (OPEN vs FROZEN)
- `ReleasesRoute.tsx` — 300ms debounced search, table with Name (link)/Version/Status/Project/Items/Last refreshed, both empty-state variants ("No releases yet" with a Browse-tickets link vs. "No releases match '{query}'"), error+retry
- `router.tsx` registers `/releases` and `/releases/$releaseId` under `authenticatedRoute`, alongside dashboard/tickets/settings; `AppShell.tsx` gained a Releases nav link between Tickets and Settings
- `frontend/tests/api/releases.test.ts` — 11 vitest cases covering `releaseSearchPath` encoding, query-key stability, the fixed manual-item category enum, a mocked 200 and 404, and all three new `messageForReason` entries
- Full verification loop (`npm run lint`, `npm test`, `npm run build`) green after every task

## Task Commits

Each task was committed atomically:

1. **Task 1: releases.ts API module, the three new reason messages, and their tests** - `2cf4023` (feat)
2. **Task 2: ReleaseDetailRoute — the read surface for REL-04 and REL-08** - `a1a6bb3` (feat)
3. **Task 3: ReleasesRoute library screen, route registration and the Releases nav entry** - `6632ac8` (feat)

_Note: SUMMARY commit follows below (worktree mode — STATE.md/ROADMAP.md updated by orchestrator, not this commit)._

## Files Created/Modified
- `frontend/src/api/releases.ts` - release/ticket-item/manual-item types, `useReleases`/`useRelease` hooks, `releaseSearchPath`
- `frontend/src/api/auth.ts` - +3 `REASON_MESSAGES` entries (RELEASE_NOT_FOUND, RELEASE_FROZEN, RELEASE_DUPLICATE)
- `frontend/src/routes/ReleaseDetailRoute.tsx` - release detail screen: header, frozen banner, tickets table (expand panel), manual items table, both empty-state variants, not-found state
- `frontend/src/routes/ReleasesRoute.tsx` - release library screen: debounced search, releases table, both empty-state variants
- `frontend/src/router.tsx` - registers `releasesRoute` and `releaseDetailRoute` under `authenticatedRoute`
- `frontend/src/components/AppShell.tsx` - Releases nav link between Tickets and Settings
- `frontend/tests/api/releases.test.ts` - 11 vitest cases
- `frontend/package.json` - lint script fix (see Deviations)

## Decisions Made
- `useParams({ from: '/authenticated/releases/$releaseId' })` instead of the plan's `'/releases/$releaseId'` — see Deviations
- `frontend/package.json`'s `lint` script changed from `tsc -b --noEmit` to `tsc -b` — see Deviations

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed the pre-existing `tsc -b --noEmit` / composite-project conflict blocking `npm run lint`**
- **Found during:** Task 1, before any lint verification could run
- **Issue:** `npm run lint` (`tsc -b --noEmit`) failed with `TS6310: Referenced project 'tsconfig.node.json' may not disable emit` on a completely untouched tree (confirmed via `git stash` + rerun before making any code changes). Passing `--noEmit` on the CLI in `tsc -b` (project-references build mode) propagates to every project in the graph, including `tsconfig.node.json`, which is `composite: true` and therefore cannot itself disable emit — a conflict that predates this plan and would have blocked `npm run lint` for every subsequent Phase 3 frontend plan.
- **Fix:** Removed the redundant `--noEmit` CLI flag from `frontend/package.json`'s `lint` script (`tsc -b --noEmit` → `tsc -b`). The app project's own `tsconfig.json` already declares `"noEmit": true`, so the overall no-emit behavior for `src`/`tests` is unchanged; only the CLI-level flag propagation to the composite node project was removed.
- **Files modified:** `frontend/package.json`
- **Verification:** `npm run lint` exits 0; `npm run build` (which also runs `tsc -b`, unaffected by the lint script) still exits 0; no `.js`/`.d.ts` files appear under `src/`
- **Committed in:** `2cf4023` (Task 1 commit)

**2. [Rule 1 - Bug] Corrected the `useParams` `from` literal in `ReleaseDetailRoute.tsx`**
- **Found during:** Task 2, `npm run lint`
- **Issue:** The plan's example code, `useParams({ from: '/releases/$releaseId' })`, does not type-check against this codebase's router: `authenticatedRoute` is registered with `id: 'authenticated'` and no `path` (a pathless layout route), so TanStack Router's generated `from`-literal type space for child routes is prefixed `/authenticated/...` even though the actual URL and every existing `to=` target (e.g. `to="/tickets"`) have no such segment.
- **Fix:** Used `useParams({ from: '/authenticated/releases/$releaseId' })` instead, with a comment explaining the id/path divergence for future authors of parameterized routes under this layout.
- **Files modified:** `frontend/src/routes/ReleaseDetailRoute.tsx`
- **Verification:** `npm run lint` exits 0
- **Committed in:** `a1a6bb3` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 - bugs blocking the plan's own literal verification commands)
**Impact on plan:** Both fixes were required for `npm run lint` (an explicit acceptance criterion on every task) to pass at all; neither changes application behavior beyond making the existing intent type-check/build correctly. No scope creep.

## Issues Encountered
- `frontend/node_modules` did not exist in this worktree at plan start; ran `npm install` before any verification could execute (167 packages, 0 vulnerabilities). Not a deviation — routine environment setup, not a plan or code change.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `/releases` and `/releases/$releaseId` are live, reachable from the nav, and read real data from plan 03-03's endpoints — REL-04 and REL-05 are functionally complete on the frontend
- Plan 03-08's OPEN-only action row (Refresh/Freeze/Delete) has an explicit, empty row-3 slot reserved in `ReleaseDetailRoute.tsx` per the UI spec — no rework needed to add it
- Plan 03-07's Add-to-release picker will navigate to `/releases/$releaseId` on success, which already renders correctly with no assumptions about how the release was reached
- The `tsc -b --noEmit` → `tsc -b` lint-script fix applies to the whole frontend workspace; later plans in this phase inherit a working `npm run lint`, not a re-discovery of the same TS6310 blocker

---
*Phase: 03-release-assembly*
*Completed: 2026-08-05*

## Self-Check: PASSED

All five created files verified present on disk (releases.ts, ReleaseDetailRoute.tsx,
ReleasesRoute.tsx, releases.test.ts, this SUMMARY.md). All three task commits (2cf4023,
a1a6bb3, 6632ac8) verified present in `git log --oneline --all`. Full `npm run lint`,
`npm test` (34 passed), and `npm run build` reran synchronously in the foreground
immediately before writing this summary: all three exit 0.
