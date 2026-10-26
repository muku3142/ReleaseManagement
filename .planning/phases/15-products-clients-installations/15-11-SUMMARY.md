---
phase: 15-products-clients-installations
plan: 11
subsystem: api
tags: [spring-mvc, rest, portfolio, read-model]
requires: [15-03, 15-04, 15-07]
provides: [ProductService, ClientService, ClientDetail, ProductController, ClientController]
affects: [15-05]
requirements-completed: [PORT-01, PORT-02, PORT-06]
completed: 2026-10-22
---

# Phase 15 Plan 11: Product and client REST endpoints Summary

Product and client registers, links and the per-client runs read model exposed under `/api/products` and `/api/clients`, matching the plan 03 JSON contract.

## Tasks
1. `ProductService`, `ClientService`, `ClientDetail` and `ClientServiceTest` (8 tests) - c075c7d
2. Controllers, DTOs, `ProductControllerTest` (6) and `ClientControllerTest` (10) - see the following feat(15-11) commit

## Verification
`ClientServiceTest, ProductControllerTest, ClientControllerTest, PortfolioProblemDetailTest, ArchitectureBoundaryTest` all pass.

## Decisions
- Archive/unarchive are idempotent in the service (no repository write when already in the target state).
- `ClientDetail` loads promotion history once per distinct release, covering both history rows and current runs.
- History is sorted newest installedAt first, then newest recordedAt, in the controller mapper (contract ordering); list responses sort by name, archived last.
- `recordedBy` resolved with a single `actorResolution.resolve` call per detail response.

## Deviations from Plan
- Tasks were committed as test plus implementation together rather than separate RED and GREEN commits (no TDD gate commits for Task 1).
- No other deviations; the existing `GlobalExceptionHandler` already mapped every reason code used.

## Known Stubs
None.

## Self-Check: PASSED
