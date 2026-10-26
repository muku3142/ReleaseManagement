---
phase: 17-rice-roadmap
plan: 12
subsystem: frontend-roadmap
tags: [roadmap, rice, route, table]
requires: ["17-03", "17-08", "17-09"]
provides:
  - "RoadmapTable, RoadmapRoute, /roadmap route and Roadmap nav link"
key-files:
  created:
    - frontend/src/roadmap/RoadmapTable.tsx
    - frontend/src/routes/RoadmapRoute.tsx
    - frontend/tests/roadmap/RoadmapTable.test.tsx
    - frontend/tests/routes/RoadmapRoute.test.tsx
  modified:
    - frontend/src/router.tsx
    - frontend/src/components/AppShell.tsx
    - frontend/src/roadmap/RoadmapAddResult.tsx
    - frontend/tests/components/AppShell.test.tsx
    - frontend/tests/roadmap/RoadmapAddResult.test.tsx
metrics:
  tasks: 2
  completed: 2026-10-25
---

# Phase 17 Plan 12: Roadmap page Summary

Ranked roadmap table plus the /roadmap page (add dialogs, Jira refresh, CSV export, URL filters) and the nav link after Portfolio.

## Commits
- 3c5e74d feat(17-12): add the ranked roadmap table
- see git log: feat(17-12): add the Roadmap page and nav link

## Verification
`npx tsc -b` clean; `npx vitest run tests/routes/RoadmapRoute.test.tsx tests/components/AppShell.test.tsx tests/roadmap`: 8 files, 60 tests passed. Full suite and `npm run build` are left to the orchestrator after merge (low memory; vitest workers timed out when run in parallel with the other executors).

## Deviations from Plan
- [Rule 3] `RoadmapAddResult.tsx` and its test (not in files_modified): "View it" and "Go to Roadmap" now use the router `Link`; "View it" sets `item`, `done` and `archived` so the row is visible. The test mocks the router.
- [Rule 1] The existing AppShell test asserted Assistant directly after Portfolio; updated to Portfolio, Roadmap, Assistant.
- "No items at all" versus "only done/archived items exist" cannot be told apart without an extra query, so an empty list with no filters shows "No roadmap items yet".
- `npm run build` and the whole suite were not run here.

## Known Stubs
None.

## Self-Check: PASSED
