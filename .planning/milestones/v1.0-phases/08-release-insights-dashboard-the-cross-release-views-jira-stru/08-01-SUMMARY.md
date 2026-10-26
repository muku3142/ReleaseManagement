---
phase: 08-release-insights-dashboard
plan: 01
subsystem: domain
tags: [promotion, dashboard, churn, statistics]
requires: []
provides:
  - PromotionHistory.firstProductionSuccess() and latestEffective()
  - domain.dashboard Stats, ChurnItem, TicketChurn, ReleaseChurn, DashboardRelease, DashboardLedger, DeploymentRow
affects: [08-03, 08-04]
tech-stack:
  added: []
  patterns: [pure domain records, null-means-no-data]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/dashboard/Stats.java
    - src/main/java/com/evision/releasemgmt/domain/dashboard/ChurnItem.java
    - src/main/java/com/evision/releasemgmt/domain/dashboard/TicketChurn.java
    - src/main/java/com/evision/releasemgmt/domain/dashboard/ReleaseChurn.java
    - src/main/java/com/evision/releasemgmt/domain/dashboard/DashboardRelease.java
    - src/main/java/com/evision/releasemgmt/domain/dashboard/DashboardLedger.java
    - src/main/java/com/evision/releasemgmt/domain/dashboard/DeploymentRow.java
    - src/test/java/com/evision/releasemgmt/domain/dashboard/StatsTest.java
    - src/test/java/com/evision/releasemgmt/domain/dashboard/ReleaseChurnTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/domain/promotion/PromotionHistory.java
    - src/test/java/com/evision/releasemgmt/domain/promotion/PromotionHistoryTest.java
key-decisions:
  - "Tukey fence needs at least 4 commit-data tickets; 2+ reopens is an outlier regardless of commit data"
requirements-completed: [INSIGHT-01, INSIGHT-02, INSIGHT-03]
duration: 15min
completed: 2026-10-12
---

# Phase 8 Plan 01: Dashboard domain rules Summary

Pure domain rules for the insights dashboard: shipped date and latest-effective promotion on `PromotionHistory`, plus `ReleaseChurn` composition/churn/outlier policy and the read-model records for 08-03/08-04.

## Tasks

1. `PromotionHistory.firstProductionSuccess()` / `latestEffective()` with 10 new tests - commit 2628c80
2. `domain/dashboard` package (Stats, ChurnItem, TicketChurn, ReleaseChurn, DashboardRelease, DashboardLedger, DeploymentRow) with StatsTest (5) and ReleaseChurnTest (10) - second commit (see git log, "add dashboard churn policy")

## Test baseline

Full backend suite after this plan: 906 tests, 0 failures (baseline before the plan: 891).

## Deviations from Plan

None - plan executed as written. The worktree was reset to the expected base 8beab20 at start.

## Known Stubs

None.

## Self-Check: PASSED
