---
phase: 15-products-clients-installations
plan: 04
subsystem: persistence
tags: [jpa, native-queries, release-lock, portfolio]
requires: ["15-01", "15-02"]
provides:
  - "ProductRepositoryAdapter, ClientRepositoryAdapter, ReleaseProductRepositoryAdapter"
  - "ProductEntity, ClientEntity and their JPA repositories (link tables via native queries)"
affects: [15-07, 15-08]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ProductEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ClientEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ProductJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ClientJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/ProductRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/ClientRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseProductRepositoryAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/PortfolioPersistenceIT.java
key-decisions:
  - "replace() rejects an empty set before any other check, under the release row lock (D-15-18)"
  - "replace() diffs against the current set and stamps items_changed_at only when it changed"
requirements-completed: [PORT-01, PORT-02, PORT-03]
completed: 2026-10-21
---

# Phase 15 Plan 04: Product, client and release-product persistence Summary

Three portfolio ports are implemented over the V14 tables: archive-only product and client registers with case-insensitive duplicate rejection, client-product links guarded by archive and install rules, and a release product set that can be replaced on OPEN and FROZEN releases alike under the release row lock.

## Tasks

| Task | Commit | Result |
|------|--------|--------|
| 1. Product and client persistence | c058dc6 | Compiles; no delete path for products or clients |
| 2. ReleaseProductRepositoryAdapter and PortfolioPersistenceIT | see git log (feat(15-04) replace) | Compiles; IT written (8 tests) |

## Verification

Run: `mvn clean test -Dtest='PortfolioPersistenceIT,DocumentReadinessServiceTest,ReleasePersistenceIT,ClientDeploymentSchemaIT,ArchitectureBoundaryTest'`.
32 tests passed, including ArchitectureBoundaryTest and DocumentReadinessServiceTest. The 3 Testcontainers classes (PortfolioPersistenceIT, ReleasePersistenceIT, ClientDeploymentSchemaIT) errored with "Can't get Docker image", because Docker Desktop was not running. **The new IT has therefore not been executed**; it must be run once Docker is up.

## Deviations from Plan

None to the code. `ReleaseProductRepositoryAdapter` was written during task 1 and committed with task 2. `DocumentReadinessService` is untouched, and the adapter has no `lockOpen`/`requireOpen`.

## Known Stubs

None.

## Threat Flags

None.

## Self-Check: PASSED (files and commits exist); IT execution pending Docker.
