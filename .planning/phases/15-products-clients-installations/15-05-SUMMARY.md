---
phase: 15-products-clients-installations
plan: 05
subsystem: frontend-portfolio
tags: [react, tanstack-router, portfolio, products, clients]
requires: ["15-03"]
provides:
  - /portfolio route with ARIA Products and Clients tabs (tab and client in URL search)
  - PortfolioRegisterPanel shared list/add/rename/archive panel
  - ClientDetailPanel runs table, link/unlink, read-only install history
  - Portfolio nav item between Releases and Assistant
key-files:
  created:
    - frontend/src/routes/PortfolioRoute.tsx
    - frontend/src/components/PortfolioRegisterPanel.tsx
    - frontend/src/components/ClientDetailPanel.tsx
    - frontend/tests/components/PortfolioRoute.test.tsx
    - frontend/tests/components/ClientDetailPanel.test.tsx
  modified:
    - frontend/src/router.tsx
    - frontend/src/components/AppShell.tsx
    - frontend/tests/components/AppShell.test.tsx
metrics:
  tasks: 2
  completed: 2026-10-21
---

# Phase 15 Plan 05: Portfolio page Summary

Portfolio page with Products and Clients tabs (create, rename, archive with confirm, unarchive), and a per-client Runs table with link/unlink rules and read-only install history, following the UI-SPEC copy.

## Commits
- 0348b21 feat(15-05): Portfolio route with Products and Clients register tabs (includes ClientDetailPanel, needed for the route to compile)
- test(15-05): ClientDetailPanel tests (second commit)

## Deviations from Plan
- ClientDetailPanel was written in full during Task 1 (the route imports it) and committed there; Task 2's commit contains only its tests. No behaviour change.
- `useSearch` uses `from: '/authenticated/portfolio'` (the pathless layout route id), matching the existing DashboardRoute convention.

## Verification
`npx tsc -b` clean; full vitest suite passes (50 files, 555 tests).

## Known Stubs
None.

## Self-Check: PASSED
