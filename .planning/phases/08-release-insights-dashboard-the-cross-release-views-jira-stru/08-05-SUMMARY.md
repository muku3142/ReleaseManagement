---
phase: 08-release-insights-dashboard
plan: 05
subsystem: release-page-churn
tags: [churn, release-detail, dashboard-invalidation]
requires: [08-04]
provides: [ReleaseItemResponse.churn, ItemChurn component]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ItemChurnResponse.java
    - frontend/src/components/ItemChurn.tsx
    - frontend/tests/components/ItemChurn.test.tsx
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseItemResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java
    - src/test/java/com/evision/releasemgmt/adapters/web/ReleaseControllerTest.java
    - frontend/src/api/releases.ts
    - frontend/src/routes/ReleaseDetailRoute.tsx
    - frontend/tests/components/ReleaseDetailRoute.test.tsx
key-decisions:
  - "Per-ticket churn rides on a new trailing churn field; CodeLinksResponse and CodeLinks.tsx untouched."
requirements-completed: [INSIGHT-03]
completed: 2026-10-12
---

# Phase 8 Plan 05: Per-ticket churn on the release page Summary

Each ticket row on the release page now shows commits-to-done, reopens and post-Done commits from `DashboardService.ticketChurn` (the same query and ReleaseChurn rules as the matrix), with explicit "No data", key-tagged-only and Outlier markers; release mutations also invalidate the `['dashboard']` query.

## Commits
- 1a970e8: feat(08-05) churn on ReleaseItemResponse (ItemChurnResponse, ReleaseController wiring, 3 new controller tests)
- 3b32dac: feat(08-05) ItemChurn cell, Churn column, ReleaseItem type, dashboard invalidation (7 component tests, 1 route test)

## Deviations from Plan

**1. [Rule 1 - Bug] Expanded-row colSpan** - Adding the Churn column made the expanded detail row's `colSpan` one short; raised from 10/9 to 11/10 in `ReleaseDetailRoute.tsx`. Included in 3b32dac.

Worktree base was behind the required base (4d1d621 vs 0d34138) and was reset per the branch-check step.

## Verification
- Backend: `mvn -P '!frontend' clean test` 945 tests, 0 failures, BUILD SUCCESS.
- Frontend: `npx vitest run` 313 tests in 30 files pass; `npx tsc -b` clean.
- CodeLinksResponse, CodeLinks.tsx, TicketBrowser.tsx, api/tickets.ts unchanged; no `dangerouslySetInnerHTML` in ItemChurn.tsx.

## Known Stubs
None.

## Threat Flags
None.

## Self-Check: PASSED
