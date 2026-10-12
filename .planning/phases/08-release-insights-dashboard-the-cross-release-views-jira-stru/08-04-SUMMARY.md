---
phase: 08-release-insights-dashboard
plan: 04
subsystem: dashboard-api
tags: [dashboard, rest, read-model, validation]
requires: [08-01, 08-03]
provides: [GET /api/dashboard/releases, GET /api/dashboard/deployments, DashboardService]
affects: [08-06 frontend client]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/DashboardService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/DashboardController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/DashboardResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/DeploymentPageResponse.java
    - src/test/java/com/evision/releasemgmt/application/DashboardServiceTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/DashboardControllerTest.java
key-decisions:
  - "Parameter errors use ErrorResponseException with a ProblemDetail reason VALIDATION_FAILED; the reason survives handleExceptionInternal, so GlobalExceptionHandler is unchanged (no fallback handler needed)."
requirements-completed: [AUDIT-04, INSIGHT-01, INSIGHT-02, INSIGHT-03]
duration: ~25 min
completed: 2026-10-12
---

# Phase 8 Plan 04: Dashboard API Summary

Two authenticated GET endpoints serve the dashboard from this system's database only: a visible-set matrix with durations, medians and churn built from one ledger load and one churn-item query, and a validated, server-filtered, limit+1 paged deployment history with actors resolved once per response.

## Commits
- 8c1c449: feat(08-04) DashboardService (visible set, durations, medians, per-stage counts, churn, paging)
- Second task commit: DashboardController, DashboardResponse, DeploymentPageResponse, DashboardControllerTest

## Deviations from Plan
None. The worktree base was behind the required base (4d1d621 vs 3d9c27d), so it was reset to 3d9c27d at start as the branch check instructs.

The `reason` property survived `handleExceptionInternal` (controller tests assert `$.reason == VALIDATION_FAILED` for every 400 case), so the documented fallback handler was not added. `GlobalExceptionHandler.java` is unchanged.

## Verification
- DashboardServiceTest (13 cases), DashboardControllerTest (11 cases), ArchitectureBoundaryTest and PromotionControllerTest pass.
- Full backend suite: 942 tests, 0 failures, BUILD SUCCESS.
- N+1 guard: `loadLedger` once and `findChurnItems` once with the visible ids (verified with times(1)).

## Known Stubs
None.

## Threat Flags
None. All new surface is under the existing `/api/**` authenticated rule; DTOs carry no credential fields.

## Notes
- `DashboardSpanContractTest` (referenced in the controller comment) is not part of this plan; the constant `MAX_HISTORY_SPAN_DAYS = 366` matches `frontend/src/api/dashboard.ts`.
- `ticketChurn(releaseId)` exists in the service but has no endpoint in this plan.

## Self-Check: PASSED
