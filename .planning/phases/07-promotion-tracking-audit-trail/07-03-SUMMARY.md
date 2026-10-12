---
phase: 07-promotion-tracking-audit-trail
plan: 03
subsystem: promotion-api
tags: [promotion, rest, jpa, row-lock, problemdetail]
requires: ["07-01", "07-02"]
provides:
  - "GET/POST /api/releases/{releaseId}/promotions"
  - "PromotionRepository insert-only port (load, appendUnderLock) with row-locking JPA adapter"
  - "PromotionService (history, record) and a java.time.Clock bean"
  - "ProblemDetail mappings for PromotionRejection, LadderEditRejection, StageNotFound, PromotionNotFound"
affects: [07-04, 07-05, 07-06, phase-08]
tech-stack:
  added: []
  patterns: ["lock release row then decide then insert in one adapter transaction", "@Immutable + Persistable entity for append-only table"]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/EnvironmentStageEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/PromotionEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/EnvironmentStageJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/PromotionJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/PromotionRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/ports/PromotionRepository.java
    - src/main/java/com/evision/releasemgmt/config/ClockConfig.java
    - src/main/java/com/evision/releasemgmt/application/PromotionService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/PromotionController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/RecordPromotionRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/PromotionHistoryResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/StageResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/PromotionEntryResponse.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/PromotionPersistenceIT.java
    - src/test/java/com/evision/releasemgmt/application/PromotionServiceTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/PromotionControllerTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
    - src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java
key-decisions:
  - "PromotionEntity implements Persistable (isNew always true) up front, so saveAndFlush persists rather than merges an entity whose id is domain-generated"
requirements-completed: [DEPL-01, DEPL-02, DEPL-03, DEPL-04, AUDIT-01, AUDIT-03]
metrics:
  tasks: 2
  files: 18
  completed: 2026-10-12
---

# Phase 7 Plan 03: Promotion API and lock-then-append persistence Summary

A signed-in client can read a release's ladder, derived current/next stage and full entry history, and record a promotion of a frozen release. The rule check runs under a `SELECT ... FOR UPDATE` on the release row, and nothing in code can mutate a stored entry.

## Commits
- c69c7ee: persistence (entities, port, adapter, Clock bean, service) with `PromotionServiceTest` (4), `PromotionPersistenceIT` (5, including the D-07-07 race) and the ArchUnit rule
- d42550a: controller, DTOs, handler mappings, `PromotionControllerTest` (10)

## Verification
Full backend suite: 827 tests, 0 failures, 0 errors (807 baseline from wave 1 plus 20 new).

## Deviations from Plan

**1. [Rule 1 - Test bug] CSRF test omits the JWT**
- **Issue:** The plan says "POST without csrf() -> 403". With `jwt()` applied the request carries a bearer credential, which Spring's resource-server wiring exempts from CSRF (documented in `SecurityFilterChainIT`), so the request reached the controller and failed 500 on an unstubbed mock.
- **Fix:** `postWithoutCsrfIsForbidden` sends no credential and no token, mirroring `SecurityFilterChainIT`. Production behaviour is unchanged.

**2. Worktree base reset**
- The worktree started at 4d1d621 (an ancestor); reset to the required base fdc656f before any work, per the branch check.

**3. Persistable decided up front**
- The plan made `Persistable` conditional on Hibernate refusing `saveAndFlush`. Because ids are domain-generated, a plain `save` would issue a SELECT and a merge, so it was implemented directly. No `@Version` was added.

## Notes for later plans
- `EnvironmentStageEntity` has setters for 07-05; its constructor is protected, so 07-05 loads entities through the repository (or adds a factory).
- Phase 6 files (ReleaseService, ReleaseRepository(+Adapter), ReleaseJpaRepository, ReleaseResponse, ReleaseController) are untouched. The `GlobalExceptionHandler` and `ArchitectureBoundaryTest` edits are additive.
- Handlers for `LadderEditRejectedException` and `PromotionNotFoundException` are in place but have no test here; 07-05/07-06 exercise them.

## Known Stubs
None.

## Threat Flags
None beyond the plan's threat model.

## Self-Check: PASSED
All created files exist; commits c69c7ee and d42550a exist.
