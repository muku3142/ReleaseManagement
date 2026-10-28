---
phase: 16-portfolio-dashboard-assistant-answers
plan: 06
subsystem: frontend-dashboard-releases-view
tags: [react, dashboard, product-filter, document-tiles]
requires: [16-04]
provides: [ProductFilter, DashboardTiles (Tile, FilterTile, HelpToggle), product-narrowed release view and history, clickable document tiles]
affects: [16-07]
key-files:
  created:
    - frontend/src/components/DashboardTiles.tsx
    - frontend/src/components/ProductFilter.tsx
    - frontend/tests/components/DashboardTiles.test.tsx
    - frontend/tests/components/ProductFilter.test.tsx
  modified:
    - frontend/src/routes/DashboardRoute.tsx
    - frontend/src/components/DeploymentHistory.tsx
    - frontend/tests/components/DashboardRoute.test.tsx
    - frontend/tests/components/DeploymentHistory.test.tsx
decisions:
  - "Clear link wording is 'Clear products' (popover and empty state); docs chip keeps 'Clear filter'"
  - "Docs-filter empty bodies: missing -> all documents generated and current; stale -> up-to-date documents"
  - "Documents out of date help uses the refrozen wording (see deviation)"
metrics:
  tasks: 3
  completed: 2026-10-22
---

# Phase 16 Plan 06: Product filter and explained document tiles Summary

Multi-select product filter in the URL narrowing tiles, matrix and deployment history, plus "Releases missing documents" / "Documents out of date" tiles that explain themselves with a "?" and filter the matrix, degrading to "—" when readiness fails.

## Commits
- 785a6b4 feat(16-06): add explained filter tiles and a multi-select product filter
- 3100d24 feat(16-06): narrow the release view and history by selected products
- 4e9bd46 feat(16-06): explain the document tiles and let them filter the release matrix

## Tests
- `npx tsc -b` clean; `vitest run tests/components` 430 passed (35 files). New: 11 (tiles + filter), 7 product-filter route cases, 8 document-tile cases, 1 history products case; the history clear-filters test now asserts `products`/`archived` survive.

## Deviations from Plan

**1. [Decision 4 - wording] "Documents out of date" help copy differs from D-16-14**
- `DocumentReadinessService.state` (after 15.1) returns STALE for a FROZEN release whose items changed before the latest freeze and whose document predates the change, so a frozen release can be counted. The second sentence is therefore "A frozen release is only counted if it was unfrozen and its tickets changed." instead of "Frozen releases are never counted...".

**2. [Rule 1 - Bug] Existing show-all test clicked the first checkbox**
- The product filter adds checkboxes ahead of "Show all releases"; the test now selects the checkbox by its label.

**3. Product scope text**: names are spelled out only when 1-2 ids all resolve to known products, otherwise "{k} products" (covers three selected products and products still loading).

## Known Stubs
None.

## Notes
- STATE.md and ROADMAP.md untouched (orchestrator owns them). The Releases/Portfolio tab bar is 16-07.
- New files are LF in the working tree; git normalises to CRLF on commit like the rest of the frontend.

## Self-Check: PASSED
