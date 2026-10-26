---
phase: 16-portfolio-dashboard-assistant-answers
plan: 07
subsystem: frontend-dashboard-portfolio-tab
tags: [react, dashboard, portfolio, tabs]
requires: [16-03, 16-04, 16-06]
provides: [Releases/Portfolio tab bar, PortfolioTab, PortfolioBehind, PortfolioMatrix, PortfolioStates]
affects: [16-08]
key-files:
  created:
    - frontend/src/components/PortfolioTab.tsx
    - frontend/src/components/PortfolioBehind.tsx
    - frontend/src/components/PortfolioMatrix.tsx
    - frontend/src/components/PortfolioStates.tsx
    - frontend/tests/components/DashboardPortfolioTab.test.tsx
  modified:
    - frontend/src/routes/DashboardRoute.tsx
    - frontend/tests/components/DashboardRoute.test.tsx
decisions:
  - "Matrix 'Up to date' keys off the API upToDate flag (releasesBehind is 0 there, null only when the product has no frozen release); the UI never compares counts"
  - "Loading/error blocks shared by the three sections live in PortfolioStates.tsx to avoid a PortfolioTab <-> PortfolioBehind import cycle"
metrics:
  tasks: 3
  completed: 2026-10-25
---

# Phase 16 Plan 07: Portfolio tab Summary

URL-driven Releases / Portfolio tab bar on the dashboard and a Portfolio tab with four tiles, a per-product table with de-duplicated totals and click-to-filter rows, a clients-behind list with a Not installed group, and a client x product matrix; each section loads and fails independently.

## Commits
- f3b9fd1 feat(16-07): add the Portfolio tab with product summary and tiles
- 9fe4e8c feat(16-07): list clients behind the newest frozen release and those not installed
- 385576b feat(16-07): show which release each client runs per product

## Tests
- `npx tsc -b` clean; full `vitest run` 696 passed (57 files). New: 24 in DashboardPortfolioTab.test.tsx, 6 tab cases in DashboardRoute.test.tsx.

## Deviations from Plan

**1. [Rule 3 - Structure] Extra file `PortfolioStates.tsx`**
- Shared SectionLoading/SectionError, created to avoid a circular import between PortfolioTab and PortfolioBehind.

**2. [Rule 1 - Bug avoided] Matrix up-to-date rule**
- The plan text implies releasesBehind is null for up to date; the backend returns 0 with `upToDate: true` and null only without a frozen release. The cell renders on `upToDate` first, then the behind marker.

## Known Stubs
None.

## Notes
- Portfolio data is only fetched when the Portfolio tab is mounted. The release query in the route still runs on both tabs (hooks are unconditional).
- STATE.md and ROADMAP.md untouched (orchestrator owns them).

## Self-Check: PASSED
