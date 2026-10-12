---
phase: 07-promotion-tracking-audit-trail
plan: 05
subsystem: environment-ladder
tags: [ladder, settings, rest, jpa, react]
requires: ["07-03"]
provides:
  - "EnvironmentLadderService (ladder, add, rename, reorder, retire, restore, moveProduction, delete) with no operation that clears the Production flag"
  - "EnvironmentLadderRepository port and adapter (flush-between-clear-and-set Production move, deferred-position reorder, unique/FK violations mapped to domain rejections)"
  - "/api/environment-ladder endpoints"
  - "Settings 'Environment ladder' section (EnvironmentLadderSection, api/ladder.ts)"
affects: [07-06, 07-07, phase-08]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/ports/EnvironmentLadderRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/EnvironmentLadderRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/application/EnvironmentLadderService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/EnvironmentLadderController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ (EnvironmentLadderResponse, LadderStageResponse, StageNameRequest, StageOrderRequest, ProductionStageRequest)
    - frontend/src/api/ladder.ts
    - frontend/src/components/EnvironmentLadderSection.tsx
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/PromotionJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/EnvironmentStageEntity.java
    - frontend/src/routes/SettingsRoute.tsx
    - frontend/tests/components/SettingsRoute.test.tsx
requirements-completed: [DEPL-01]
metrics:
  tasks: 3
  completed: 2026-10-12
---

# Phase 7 Plan 05: Environment ladder editing Summary

The release manager can add, rename, reorder, retire, restore, delete (unused) and move the Production flag of stages from Settings. Used stages, the Production stage and the last active stage are protected, and history follows renames because it references stage ids.

## Commits
- f74e8b7: service, port, adapter, `EnvironmentLadderServiceTest` (12), `EnvironmentLadderPersistenceIT` (8)
- 6b3f067: controller, DTOs, `EnvironmentLadderControllerTest` (9)
- a94bb76: `ladder.ts`, `EnvironmentLadderSection`, SettingsRoute mount (2 added lines), tests (9 new + 1 in SettingsRoute)

## Verification
- Backend targeted: EnvironmentLadderServiceTest, EnvironmentLadderPersistenceIT, PromotionPersistenceIT, EnvironmentLadderControllerTest, PromotionControllerTest, ArchitectureBoundaryTest all green (full backend suite not run; the plan's verify commands were).
- Frontend: `npx tsc -b` clean; `npx vitest run` 263 passed / 26 files.

## Deviations from Plan

**1. [Rule 3 - Blocking] Public factory on EnvironmentStageEntity**
- The adapter lives in a different package from the entity and its constructor is protected, so `add` could not instantiate one. Added `EnvironmentStageEntity.create(name, position)` (additive, assigns the id).

**2. Worktree base reset**
- Worktree started at 4d1d621; reset to 35af1bd per the branch check before any work.

**3. Environment**
- `SettingsRoute.test.tsx` existed; its fetch mock now answers `/api/environment-ladder` with `{ stages: [] }` and one heading test was added. Existing assertions unchanged.
- No frontend baseline count was measured before editing; the full suite is green.

## Known Stubs
None.

## Threat Flags
None beyond the plan's threat model (T-07-18..23 mitigations implemented: service rules plus FK/unique backstops, `@Size(max = 50)` order list, names rendered as React text, CSRF tests).

## Self-Check: PASSED
All created files exist; commits f74e8b7, 6b3f067 and a94bb76 exist.
