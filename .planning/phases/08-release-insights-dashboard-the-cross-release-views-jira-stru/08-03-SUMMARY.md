---
phase: 08-release-insights-dashboard
plan: 03
subsystem: persistence
tags: [dashboard, read-model, native-sql, archunit]
requires: [08-01]
provides:
  - ports.DashboardReadRepository (loadLedger, findChurnItems, findDeployments)
  - DashboardReadRepositoryAdapter over DashboardJpaRepository (three native queries)
affects: [08-04]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/ports/DashboardReadRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/DashboardReadRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/DashboardJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/DashboardReleaseRow.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ChurnItemRow.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/DeploymentHistoryRow.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/DashboardPersistenceIT.java
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/persistence/PromotionRepositoryAdapter.java
    - src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java
requirements-completed: [AUDIT-04, INSIGHT-02, INSIGHT-03]
completed: 2026-10-12
---

# Phase 8 Plan 03: Dashboard read model Summary

Read-only port plus adapter serving the raw ledger, per-item commit churn (split at first_done_at) and a filtered, deterministically ordered, effective-only deployment history.

## Tasks

1. Port, `DashboardJpaRepository` (extends bare `Repository`, so no CRUD inherited), three projections, adapter; `toStage`/`toPromotion` made package-private for reuse - commit "feat(08-03)".
2. `DashboardPersistenceIT` (7 tests: equivalence against `PromotionHistory.isEffective`, stage filter, half-open range, stable paging on tied deployed_at, ledger, churn split, empty id list) and ArchUnit rule `dashboardReadRepositoryExposesNoMutation` - commit "test(08-03)".

Verification: DashboardPersistenceIT 7, PromotionPersistenceIT 7, ArchitectureBoundaryTest 9, all passing. No migration changed.

## Decisions

- Null stage filter shipped as the single query with `CAST(:stageId AS uuid) IS NULL`; it worked on Postgres 18, so the two-method split was not needed.

## Deviations from Plan

None. The worktree was reset to the expected base f1ffc8a at start.

## Known Stubs

None.

## Self-Check: PASSED
