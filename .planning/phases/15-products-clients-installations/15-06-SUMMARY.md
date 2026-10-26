---
phase: 15-products-clients-installations
plan: 06
subsystem: frontend-releases
tags: [react, products, release-detail, create-release]
requires: [15-03]
provides:
  - ProductsEditor (chips, inline editor, never-empty lock) on release detail
  - Required Products picker in the create-release form
  - Products column on the Releases table
requirements: [PORT-03]
key-files:
  created:
    - frontend/src/components/ProductsEditor.tsx
    - frontend/tests/components/ProductsEditor.test.tsx
  modified:
    - frontend/src/routes/ReleaseDetailRoute.tsx
    - frontend/src/routes/ReleasesRoute.tsx
    - frontend/src/components/AddToReleaseDialog.tsx
    - frontend/src/api/releases.ts
    - frontend/tests/components/ReleaseDetailRoute.test.tsx
    - frontend/tests/components/ReleasesRoute.test.tsx
    - frontend/tests/components/AddToReleaseDialog.test.tsx
decisions:
  - Editor body is a sub-component so /api/products is fetched only when the editor opens; likewise the create picker fetches only when the New release form opens.
  - PORTFOLIO_PATH is exported from ProductsEditor as a cast constant because the typed router does not know /portfolio until plan 15-05 merges.
metrics:
  tasks: 2
  completed: 2026-10-21
---

# Phase 15 Plan 06: Release products UI Summary

Release-product assignment UI (PORT-03): a required product checklist on create, chips plus an inline editor on release detail that cannot submit an empty set, and a Products column on the Releases table.

## Commits
- 22edf37 feat(15-06): products line and inline editor on release detail
- 68c1d39 feat(15-06): required product picker on create and Products column on Releases

## Deviations from Plan

**1. [Rule 3 - Blocking] /portfolio route not yet registered**
- Typed `Link to="/portfolio"` fails `tsc -b` on this base because plan 15-05 adds the route.
- Fix: `PORTFOLIO_PATH = '/portfolio' as '/'` in ProductsEditor.tsx, reused by AddToReleaseDialog. Runtime href is `/portfolio`. Can be replaced by a plain literal once 15-05 is merged.

**2. [Rule 1 - Bug, test only] Existing create-form test helper never populated React state**
- `submitCreateForm` set `input.value` directly, so React's value tracker ignored the event and name/version state stayed empty (tests passed only because they dispatch submit past the disabled button). Left as is; my new disabled-button test uses the native value setter.

Releases table column indexes in ReleasesRoute.test.tsx shifted by one for the new Products column.

## Verification
`npx tsc -b` clean; `npx vitest run` 49 files, 565 tests pass.

## Known Stubs
None.

## Self-Check: PASSED
