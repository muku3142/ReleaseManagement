---
phase: 08-release-insights-dashboard
plan: 06
subsystem: frontend-dashboard-data-layer
tags: [tanstack-query, url-state, validation]
requires: ["08-02"]
provides:
  - "api/dashboard.ts: contract types, query keys, useDashboardReleases, useDeploymentHistory, parseDashboardSearch, historyRange, defaultHistoryDates, formatDuration, paperworkCounts, mergeDeploymentPages, nextDeploymentOffset, deploymentsPath"
  - "dashboardRoute.validateSearch on /"
  - "['dashboard'] invalidation on promotion record/correct and ladder mutations"
key-files:
  created:
    - frontend/src/api/dashboard.ts
    - frontend/tests/api/dashboard.test.ts
  modified:
    - frontend/src/router.tsx
    - frontend/src/api/promotions.ts
    - frontend/src/api/ladder.ts
decisions:
  - "Invalid typed range is modelled as a null filter: query disabled, keepPreviousData keeps prior rows"
metrics:
  completed: 2026-10-12
---

# Phase 8 Plan 06: Dashboard data layer Summary

Typed dashboard API contract and pure rules (whitelist URL parsing, half-open browser-zone date ranges capped at 366 days, duration formatting, paperwork counts, de-duplicating paging) plus validateSearch on `/` and dashboard cache invalidation from promotion and ladder mutations.

## Commits
- 3fbdc8c: feat(08-06) api/dashboard.ts and 16 tests
- see git log: feat(08-06) router validateSearch and invalidation edits

## Verification
- `npx tsc -b` clean; `npx vitest run` 305 passed (29 files), up from 289.

## Deviations from Plan
- Worktree base was reset to f1ffc8a per the startup instruction.
- Added a fallback message "Enter a valid start and end date." in historyRange for unparsable input (plan only specified the two range messages).
- The invalidations are fire-and-forget (not returned) so mutations do not wait on dashboard refetch; no dedicated test added for them beyond existing suites passing.

## Known Stubs
None.

## Self-Check: PASSED
