---
phase: 23-ui-enhancement-logo
plan: 12
subsystem: frontend
tags: [motion, reduced-motion, dashboard, data-table, login]
requires: ["23-06", "23-08", "23-09", "23-11"]
provides:
  - FilterTile KPI count-up and hover lift
  - Dashboard release-matrix first-render stagger
  - One-time status pulse on ReadinessPill and LadderDots
  - Login logo connect-in
  - Clickable /releases rows and animated Release link underline
affects: [DashboardTiles, DashboardRoute, ReadinessPill, LadderDots, LoginRoute, ReleasesRoute]
tech-stack:
  added: []
  patterns:
    - "23-11 hooks applied as-is (useCountUp, usePulseOnChange, useEntranceStagger); no new CSS"
key-files:
  created:
    - frontend/tests/components/LadderDots.test.tsx
  modified:
    - frontend/src/components/DashboardTiles.tsx
    - frontend/src/routes/DashboardRoute.tsx
    - frontend/src/components/ReadinessPill.tsx
    - frontend/src/components/LadderDots.tsx
    - frontend/src/routes/LoginRoute.tsx
    - frontend/src/routes/ReleasesRoute.tsx
    - frontend/tests/components/DashboardTiles.test.tsx
    - frontend/tests/components/DashboardRoute.test.tsx
    - frontend/tests/components/ReadinessPill.test.tsx
    - frontend/tests/routes/LoginRoute.test.tsx
    - frontend/tests/components/ReleasesRoute.test.tsx
decisions:
  - "Release-link clicks on /releases are left to the Link; DataTable's existing closest('a, button, ...') guard keeps the row handler from navigating a second time"
metrics:
  duration: ~35min
  completed: 2026-10-27
  tasks: 3
  files: 12
---

# Phase 23 Plan 12: Motion applied Summary

Dashboard KPI tiles count up over a real-number text node and lift on hover. The release matrix staggers in once. Readiness pills and ladder dots pulse once when their status changes after mount. The login mark connects in. /releases rows open the release on click, and the Release link uses `link-underline`. Every effect is gated by `motion-safe:` or the 23-11 reduced-motion hooks.

## Tasks

| Task | Commit | What |
|------|--------|------|
| 1 | 93fedb4 | `FilterTile` uses `useCountUp` and sets `data-count-display` plus the `count-up` class. The text stays `{figure}`. Added lift classes. `ReleaseMatrix` rows use `useEntranceStagger(rows.length > 0)` |
| 2 | 0665e53 | `ReadinessPill` calls `usePulseOnChange(state)` and passes the class and `onAnimationEnd` to every Badge. `LadderDots` has two pulses (`currentStageId`, `failedStageId`), and only the matching dot animates |
| 3 | 706650b | `<ProductOpsLogo variant="login" ... animate />`. `/releases` DataTable gets `onRowClick` → `navigate({ to: '/releases/$releaseId', params })`, and the Release link class becomes `link-underline` |

## Verification

- `npx tsc -b`: clean
- Task test sets: Task 1 had 4 files and 84 tests passing (DashboardTiles, DashboardRoute, StageChart, DashboardPortfolioTab). Task 2 had 4 files and 64 passing (ReadinessPill, LadderDots, PromotionSection, DocumentsSection). Task 3 had 2 files and 27 passing.
- Full suite `npx vitest run`: 87 files, 1017 tests passed. The known-flaky RoadmapRoute test passed on the first run.
- `npm run build`: green, and the built CSS contains `attr(data-count-display)`.

## Deviations from Plan

- **Test harness only:** the `@tanstack/react-router` `Link` mock in `ReadinessPill.test.tsx` now forwards `onAnimationEnd`. Before, it dropped every prop except `className`. The mock in `ReleasesRoute.test.tsx` gained `useNavigate`. `renderRoute` in `DashboardRoute.test.tsx` gained an optional `fresh` flag so a rerender reuses the same QueryClient. No existing assertion changed.
- `@testing-library/react` is not installed, so `fireEvent.animationEnd` became a dispatched `new Event('animationend', { bubbles: true })`, which React handles the same way.
- The stagger presence check uses a MutationObserver because later query settles legitimately clear the class within `renderRoute`.

## Known Stubs

None.

## Threat Flags

None. T-23-42: navigation uses the typed route with params. T-23-43: `data-count-display` only ever holds a `useCountUp` number. T-23-44: reduced-motion branches are tested for tiles, pills and dots.

## Self-Check: PASSED

- frontend/tests/components/LadderDots.test.tsx exists
- Commits 93fedb4, 0665e53 and 706650b are present on worktree-agent-a871deff4ee64ff49
