---
phase: 17-rice-roadmap
plan: 03
subsystem: frontend-api
tags: [roadmap, rice, api-client, react-query]
requires: []
provides:
  - "Typed roadmap hooks, query keys, parseRoadmapSearch, CSV download (frontend/src/api/roadmap.ts)"
  - "Shared rubric copy and option lists (frontend/src/roadmap/rubric.ts)"
  - "useSetPackagesUrl and Release.packagesUrl"
  - "12 Phase 17 REASON_MESSAGES entries"
affects: [17-08, 17-09, 17-11, 17-12]
key-files:
  created:
    - frontend/src/api/roadmap.ts
    - frontend/src/roadmap/rubric.ts
    - frontend/tests/api/roadmap.test.ts
  modified:
    - frontend/src/api/dashboard.ts
    - frontend/src/api/releases.ts
    - frontend/src/api/auth.ts
    - frontend/src/api/products.ts
    - frontend/tests/api/releases.test.ts
    - frontend/tests/components/ProductsEditor.test.tsx
    - frontend/tests/components/ReleaseDetailRoute.test.tsx
decisions:
  - "All roadmap mutations share one factory that invalidates the ['roadmap'] prefix; packages link only sets the release cache (D-17-21)."
metrics:
  tasks: 2
  completed: 2026-10-25
---

# Phase 17 Plan 03: Roadmap API client Summary

Contract-first frontend API layer for the RICE roadmap and the packages link, with the rubric copy defined once.

## Commits
- 64b1a95 feat(17-03): add the roadmap API client and shared rubric
- bc9c92c feat(17-03): add the packages-link client and Phase 17 reason messages

## Verification
`npx tsc -b` clean; `npx vitest run tests/api tests/components/ProductsEditor.test.tsx tests/components/ReleaseDetailRoute.test.tsx`: 13 files, 185 tests passed.

## Deviations from Plan
None of substance. `parseProducts` and `sortedIds` exported from dashboard.ts as planned; `parseProducts` already caps at MAX_PRODUCT_FILTER so parseRoadmapSearch reuses it. New files were written with CRLF to match the repo.

## Known Stubs
None.

## Self-Check: PASSED
