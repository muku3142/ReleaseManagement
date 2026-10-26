---
phase: 15-products-clients-installations
plan: 02
subsystem: portfolio-domain
tags: [domain, ports, install-ledger, problem-detail]
requires: []
provides:
  - domain/portfolio types and install rules (ClientDeploymentHistory.record/correct)
  - ProductRepository, ClientRepository, ReleaseProductRepository, ClientDeploymentRepository ports
  - ProblemDetail reason codes for every install and portfolio-edit rejection
affects: [15-03, 15-04, 15-07, 15-08, 15-09, 15-11, 15-12]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/portfolio/ (16 types + ClientDeploymentHistory)
    - src/main/java/com/evision/releasemgmt/ports/{ProductRepository,ClientRepository,ReleaseProductRepository,ClientDeploymentRepository}.java
    - src/test/java/com/evision/releasemgmt/domain/portfolio/ClientDeploymentHistoryTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/PortfolioProblemDetailTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
key-decisions:
  - "Installed time is bounded only against the future (2 min tolerance), no lower bound (D-15-14)"
  - "Bulk record validates all clients before building any row; one exception lists every offender (D-15-10)"
  - "Corrections copy release/product/client from the original and do field checks only"
requirements-completed: [PORT-01, PORT-02, PORT-03, PORT-04, PORT-05, PORT-07]
duration: ~25 min
completed: 2026-10-21
---

# Phase 15 Plan 02: Portfolio domain, ports and error mapping Summary

Framework-free portfolio domain with all-or-nothing bulk install rules, four ports for later plans, and stable ProblemDetail reason codes (exhaustive switches) for every new rejection.

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 1 Domain types and ports | f1187dc | `ClientDeploymentHistory` stubbed, ArchitectureBoundaryTest green |
| 2 History rules (TDD) | fb4b036 (RED), 7aba67b (GREEN) | 16 unit tests |
| 3 ProblemDetail mapping | 178bfe6 | 5 tests; PromotionControllerTest still green |

## Deviations from Plan

- [Rule 1 - test bug] The first draft of `recordHasNoLowerBound` used a nanosecond-precision instant, which the micros truncation altered; fixed the test to use a micros-precise instant. No production change.
- Mapping tests call handler methods directly (plain JUnit) as planned; the not-found handlers are covered too.

## Issues Encountered
None. No Docker needed.

## Known Stubs
None.

## Self-Check: PASSED
52 tests green across ClientDeploymentHistoryTest, PortfolioProblemDetailTest, PromotionControllerTest, ArchitectureBoundaryTest. STATE.md and ROADMAP.md untouched.
