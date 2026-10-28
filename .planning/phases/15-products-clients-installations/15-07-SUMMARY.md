---
phase: 15-products-clients-installations
plan: 07
subsystem: persistence
tags: [jpa, append-only, ledger, archunit, postgres]
requires: [15-02, 15-04]
provides: [ClientDeploymentRepositoryAdapter, ClientDeploymentEntity, ClientDeploymentJpaRepository]
affects: [15-11, 15-12]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ClientDeploymentEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ClientDeploymentJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ClientInstallRow.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/CurrentRunRow.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/ClientDeploymentRepositoryAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/ClientDeploymentPersistenceIT.java
  modified:
    - src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java
requirements-completed: [PORT-04, PORT-05, PORT-06, PORT-07]
duration: ~25min
completed: 2026-10-21
---

# Phase 15 Plan 07: Client deployment ledger adapter Summary

Insert-only `ClientDeploymentRepository` over `client_deployment`, mirroring the promotion adapter: `@Immutable` entity, release-row lock before history is read, bulk appends in one transaction, views read through native projections.

## Tasks
1. Entity, JPA repository (two projection interfaces for the history and current-run native queries), adapter, ArchUnit rule `clientDeploymentRepositoryExposesNoMutation` - ea7f3df
2. `ClientDeploymentPersistenceIT` (8 tests) - see git log (test(15-07) commit)

## Verification
`ClientDeploymentPersistenceIT, ClientDeploymentSchemaIT, PromotionPersistenceIT, ArchitectureBoundaryTest`: 45 tests, 0 failures (Docker was up; all ITs ran).

## Deviations from Plan
None in production code. Two test-authoring fixes during the IT: AssertJ `isNotInstanceOf` fails on null (replaced with plain instanceof checks), and "effective" in the domain means "not superseded" (the final VOIDED row is itself effective), so test 4 asserts only that the original and the correction are not effective.

The plan's seven test scenarios are covered by 8 tests (history including superseded/voided rows is its own test). Client D is unlinked, C is linked then archived; A is additionally linked to P2 so the race test can install on P2.

## Known Stubs
None.

## Self-Check: PASSED
