---
phase: 08-release-insights-dashboard
plan: 02
subsystem: persistence, frontend-documents
tags: [staleness, readiness, tanstack-query]
requires: []
provides:
  - "removeItem/removeManualItem stamp items_changed_at only on a real removal"
  - "readinessIndex(entries) Map lookup (replaces readinessFor)"
  - "useGenerateDocument invalidates ['dashboard']"
affects: [08 dashboard plans]
key-files:
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseTicketItemJpaRepository.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/ReleasePersistenceIT.java
    - frontend/src/api/documents.ts
    - frontend/src/routes/ReleasesRoute.tsx
    - frontend/src/components/DocumentsSection.tsx
    - frontend/src/routes/DocumentViewerRoute.tsx
    - frontend/tests/api/documents.test.ts
decisions:
  - "Derived delete returns long; count > 0 gates the stamp under the existing lockOpen row lock"
metrics:
  completed: 2026-10-12
---

# Phase 8 Plan 02: Truthful paperwork readiness Summary

No-op removals no longer stamp items_changed_at (so paperwork is not falsely Stale), and readiness is a Map lookup built once per response, with document generation refreshing the dashboard query key.

## Commits
- 2edab15: fix(08-02) remove paths stamp only when something was removed; 4 new ReleasePersistenceIT cases; todo moved to completed
- bf81014: perf(08-02) readinessIndex, callers use useMemo, ['dashboard'] invalidation, tests

## Verification
- Backend: ReleasePersistenceIT (29), ReleaseServiceManualItemsTest (10), ReleaseServiceTicketsTest (28) pass.
- Frontend: tsc -b clean; vitest 289 passed (last known baseline 285; not re-measured before edits). No `readinessFor` references remain.

## Deviations from Plan
- Worktree base was reset to 8beab20 per the startup instruction.
- Edits made via the Edit tool (no python available). The todo file was moved with git mv in the first commit.
- Added a dashboard-invalidation assertion to the existing generate-invalidation test.

## Known Stubs
None.

## Self-Check: PASSED
