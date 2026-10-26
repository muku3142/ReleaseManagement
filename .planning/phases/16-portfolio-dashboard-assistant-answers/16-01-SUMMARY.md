---
phase: 16-portfolio-dashboard-assistant-answers
plan: 01
subsystem: dashboard
tags: [dashboard, product-filter, spring-data, native-sql]
requires: [15.1]
provides: [product-filtered releases list, summary and deployment history (PDASH-01)]
affects: [16-03, 16-04]
key-files:
  modified:
    - src/main/java/com/evision/releasemgmt/ports/DashboardReadRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/DashboardReadRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/DashboardJpaRepository.java
    - src/main/java/com/evision/releasemgmt/application/DashboardService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/DashboardController.java
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/DashboardTool.java
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/DeploymentsTool.java
decisions:
  - "Release list filters by a release-id set from releaseIdsCoveringAny; history SQL uses EXISTS, never JOIN release_product"
  - "summary.totalReleases stays the unfiltered count"
  - "productScope and invalid are package-private static for reuse by 16-03"
metrics:
  tasks: 2
  completed: 2026-10-22
---

# Phase 16 Plan 01: Product filter on the Releases-tab read model Summary

A multi-select `productIds` filter now flows from `DashboardController` through `DashboardService` to the read repository, so a release covering several selected products appears and counts once in the list, summary and deployment history.

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 1 Product filter in port, SQL, service | f94c9d1 | releaseIdsCoveringAny, EXISTS predicate, service signatures with Set<UUID> |
| 2 productIds params with cap | 65f907b | MAX_PRODUCT_FILTER = 50, productScope, invalid made package-private |

## Verification

DashboardServiceTest 20, DashboardPersistenceIT 11, DashboardControllerTest 15, DashboardSpanContractTest 1, DashboardToolTest 2, DeploymentsToolTest 3: all green.

## Deviations from Plan

**1. [Rule 3 - Blocking] Minimal controller arity change landed in the Task 1 commit.** Changing the service signatures breaks compilation of `DashboardController` and `DashboardControllerTest`, so Task 1 included passing `Set.of()` there (and updating the existing test stubs). Task 2 then added the real params, cap and new tests.

Otherwise the plan was executed as written. No one-argument overloads remain.

## Known Stubs

None.

## Threat Flags

None. T-16-01..03 are mitigated as planned (typed UUID list, bound parameters, cap, EXISTS plus fixture tests).

## Self-Check: PASSED
