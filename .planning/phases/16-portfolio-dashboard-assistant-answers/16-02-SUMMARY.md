---
phase: 16-portfolio-dashboard-assistant-answers
plan: 02
subsystem: portfolio
tags: [portfolio, read-model, jpa, native-query]
requires: [V14 views current_client_release, effective_client_deployment]
provides: [FrozenReleaseOrder, PortfolioReadRepository]
affects: [16-03, 16-05]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/portfolio/FrozenRelease.java
    - src/main/java/com/evision/releasemgmt/domain/portfolio/FrozenReleaseOrder.java
    - src/main/java/com/evision/releasemgmt/domain/portfolio/ClientCurrentRun.java
    - src/main/java/com/evision/releasemgmt/domain/portfolio/RecentInstall.java
    - src/main/java/com/evision/releasemgmt/ports/PortfolioReadRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/PortfolioReadRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/PortfolioJpaRepository.java
    - src/test/java/com/evision/releasemgmt/domain/portfolio/FrozenReleaseOrderTest.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/PortfolioReadPersistenceIT.java
decisions:
  - "Newest/behind ordered by current frozen_at then id, never by version (D-16-09..11)"
metrics:
  completed: 2026-10-22
---

# Phase 16 Plan 02: Portfolio ordering and read port Summary

Pure `FrozenReleaseOrder` (newest, releasesBehind by frozen date with id tie-break) plus a read-only `PortfolioReadRepository` (currentRuns, productsByRelease, successfulInstallsSince) over the existing V14 views, no migration.

## Commits
- 397da37 feat(16-02): order frozen releases by frozen date to count releases behind
- Task 2 commit: feat(16-02): add read-only portfolio port over current installs and release products

## Tests
- FrozenReleaseOrderTest: 7 passing
- PortfolioReadPersistenceIT: 3 passing (superseded, voided, rolled-back, time window)

## Deviations from Plan
None. Verification command was run from the worktree with `clean` (public APIs added). Files written with CRLF to match the repo.

## Known Stubs
None.

## Self-Check: PASSED
