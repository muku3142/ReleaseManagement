---
phase: 23-ui-enhancement-logo
plan: 06
subsystem: frontend
tags: [recharts, chart, lazy-loading, reduced-motion, a11y, dashboard]
requires: ["23-03"]
provides:
  - "usePrefersReducedMotion(): live prefers-reduced-motion hook (useSyncExternalStore over matchMedia, false when absent)"
  - "StageChart (default export, lazy-loadable) + StageChartRow: token-coloured horizontal bar chart with an aria-label text equivalent"
  - "Dashboard 'Releases per stage' tile renders StageChart through React.lazy + Suspense with a Skeleton fallback; the old list stays as sr-only"
affects: [23.1]
tech-stack:
  added: []
  patterns:
    - "Charts load via React.lazy(() => import('../components/X')) so Recharts never enters the main graph; only `import type` from the chart module in routes"
    - "ChartConfig is a module constant with static keys/colours; user data only flows into chart rows"
    - "Bar colours per row via <Cell fill='var(--token)'>; isAnimationActive={!usePrefersReducedMotion()}"
key-files:
  created:
    - frontend/src/hooks/usePrefersReducedMotion.ts
    - frontend/src/components/StageChart.tsx
    - frontend/tests/components/StageChart.test.tsx
  modified:
    - frontend/src/routes/DashboardRoute.tsx
    - frontend/tests/components/DashboardRoute.test.tsx
decisions:
  - "Zero-count bars use minPointSize={2}: Recharts 3 drops zero-width bars together with their LabelList entry, so without a stub a '0' value would vanish for sighted users."
  - "The tile's chart rows reuse the list's filter (active stages, retired stages with releases labelled '{name} (retired)', then 'Not promoted'), so chart, aria-label and sr-only list cannot disagree."
  - "ChartContainer gets aspect-auto so the explicit rows x 28px height wins over its default aspect-video."
metrics:
  duration: "~35 min"
  completed: 2026-10-26
  tasks: 2
  files: 5
---

# Phase 23 Plan 06: Lazy StageChart on the Dashboard Summary

**The dashboard "Releases per stage" tile is now a token-coloured Recharts horizontal bar chart. It loads lazily into its own 308 kB chunk behind a skeleton, carries an aria-label listing every value, keeps the old list as sr-only text, and does not animate when the user asks for reduced motion.**

## What was built

- `usePrefersReducedMotion` uses `useSyncExternalStore` over `window.matchMedia?.('(prefers-reduced-motion: reduce)')` and follows `change` events.
- `StageChart` uses a static `chartConfig = { count: { label: 'Releases', color: 'var(--chart-1)' } }`. Stage bars are `var(--chart-1)` and "Not promoted" is `var(--muted-foreground)`. Each value sits at the bar end as a `LabelList` (`fill-foreground text-xs font-semibold tabular-nums`). The Y axis uses 12px `var(--foreground)` ticks. There is no X axis, grid, tooltip or legend, and no hex colours. Height is rows x 28px. `role="img"` with `aria-label="Releases per stage: QA 1, Production 0, Not promoted 0."`.
- `DashboardRoute` uses `lazy(() => import('../components/StageChart'))` inside `<Suspense fallback={<Skeleton className="h-24" />}>`. The existing `<ul>` is now `sr-only` with unchanged content, and the caption stays under the chart. The rest of the dashboard is untouched.

## Verification

- `npx tsc -b`: clean.
- `npx vitest run tests/components/StageChart.test.tsx tests/components/DashboardRoute.test.tsx`: 51/51 (7 StageChart, 44 DashboardRoute including 2 new).
- `npx vitest run` (full): **906/906 passed**. One earlier full run hit only the known-flaky RoadmapRoute 'opens the detail row named by the item param', which also failed when run alone. The next full run was all green.
- `npm run build`: succeeds. The pre-existing >500 kB warning (main chunk) remains.

| Chunk | Before (base 3058cf1) | After |
|-------|-----------------------|-------|
| `index-*.js` (main) | 774.90 kB / 229.37 kB gzip | 802.73 kB / 241.66 kB gzip |
| `StageChart-*.js` (lazy) | (none) | 308.18 kB / 93.12 kB gzip |

- Lazy-split gate: `grep -l recharts-bar-rectangle dist/assets/*.js` matches only `StageChart-DgrjNIGU.js`. A sourcemap breakdown shows recharts, @reduxjs/toolkit, immer, react-redux, d3-*, decimal.js-light and es-toolkit only in the StageChart chunk.
- The +27.8 kB raw / +12.3 kB gzip on main is the `cn` class-merge package (shadcn's clsx + tailwind-merge replacement). `Skeleton` is the first shadcn primitive on a main-graph route, so it pulls `cn` in. Every later shadcn component would bring it too. None of the growth is Recharts. (RESEARCH's 740/219 kB figure predates 23-01..05; the before column above is measured at this plan's base.)
- Grep gates: no hex in StageChart, no computed ChartConfig keys, `isAnimationActive={!` and `export default` present, no `from 'recharts'` in DashboardRoute.

## Commits

- `f404600` feat(23-06): add the token-driven StageChart and reduced-motion hook
- `b8ce815` feat(23-06): show releases per stage as a lazy-loaded bar chart

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] A zero-count stage lost its value label**
- **Found during:** Task 1
- **Issue:** Recharts 3 filters out zero-width bar rectangles, and their `LabelList` entries go with them. A stage with 0 releases showed no "0".
- **Fix:** `minPointSize={2}` on the Bar keeps a 2px stub so the label renders. A one-line comment explains why.
- **Files modified:** frontend/src/components/StageChart.tsx
- **Commit:** f404600

**2. [Rule 1 - Bug] Test-only: the lazy chunk outlasted vi.waitFor under full-suite load**
- **Found during:** Task 2
- **Issue:** Under full-suite load, the first real import of the chart chunk (Recharts transform) took more than 1s. `vi.waitFor` timed out with an `act()` still running, and the overlapping act scopes left every later DashboardRoute render unflushed (empty container, 41 failures). Run alone, the file passed.
- **Fix:** A `beforeAll` warms the module with `vi.importActual` (30s hook timeout), and the chart wait uses a 5s timeout. The fallback is still observable because a hoisted gate in the `vi.mock` factory holds the chunk back until the chart test releases it.
- **Files modified:** frontend/tests/components/DashboardRoute.test.tsx
- **Commit:** b8ce815

**3. [Rule 3 - Blocking] jsdom layout for StageChart tests**
- As the plan anticipated, `HTMLElement.prototype.getBoundingClientRect` is stubbed to 320x84 in StageChart.test.tsx only, and restored by `vi.restoreAllMocks`.

## TDD Gate Compliance

Each task landed as one `feat` commit, as the plan's commit messages specify, and not as separate test/feat commits. For Task 2 the new tests were written first and failed (2 failed, 42 passed) before the implementation. For Task 1 the component and its tests were written together and run before the commit.

## Threat Flags

None. T-23-20 (static ChartConfig, grep gate), T-23-21 (labels only as React text/attributes) and T-23-22 (lazy chunk, build gate) are mitigated as planned.

## Self-Check: PASSED

- FOUND: frontend/src/hooks/usePrefersReducedMotion.ts, frontend/src/components/StageChart.tsx, frontend/tests/components/StageChart.test.tsx
- FOUND commits: f404600, b8ce815
