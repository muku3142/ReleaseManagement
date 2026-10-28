---
phase: 16-portfolio-dashboard-assistant-answers
plan: 03
subsystem: dashboard
tags: [portfolio, read-model, rest]
requires: [16-01, 16-02]
provides: [PortfolioDashboardService (summary, behind, matrix), GET /api/dashboard/portfolio/{summary,behind,matrix}]
affects: [16-04, 16-05]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/PortfolioDashboardService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/PortfolioDashboardController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/PortfolioDashboardResponse.java
    - src/test/java/com/evision/releasemgmt/application/PortfolioDashboardServiceTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/PortfolioDashboardControllerTest.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/PortfolioDashboardPersistenceIT.java
decisions:
  - "Frozen order is built over all products and clients before scoping, so archiving never changes N"
  - "In Production only from PromotionHistory.firstProductionSuccess over promotions grouped once (no N+1, no stage positions)"
  - "Totals are a distinct release-id set; per-status bucket order is in Production, then open, then frozen"
metrics:
  tasks: 3
  completed: 2026-10-22
---

# Phase 16 Plan 03: Portfolio dashboard read model Summary

`PortfolioDashboardService` computes tiles, per-product rows with de-duplicated totals, the behind / Not installed lists and the client x product matrix from one ledger snapshot, exposed as three authenticated read-only GETs that reuse `DashboardController.productScope`.

## Commits
- dd9cc44 feat(16-03): compute portfolio summary, behind list and matrix from one snapshot
- 52f8474 feat(16-03): expose portfolio summary, behind and matrix endpoints
- 9d810c5 test(16-03): pin de-duplicated portfolio totals and behind over a real database

## Tests
- PortfolioDashboardServiceTest: 10 passing
- PortfolioDashboardControllerTest: 6 passing
- PortfolioDashboardPersistenceIT: 4 passing (totals 1/1/1 vs row sum 4, behind, pitfall 8 product add, superseding correction)

## Deviations from Plan
None. The IT uses `is_production` (the actual column name) for the Production stage lookup. Files written CRLF. STATE.md and ROADMAP.md untouched per orchestrator instruction.

## Known Stubs
None.

## Threat Flags
None. T-16-10, 11 and 14 mitigated as planned (401 and 400 tests, shared cap, distinct-set totals).

## Self-Check: PASSED
