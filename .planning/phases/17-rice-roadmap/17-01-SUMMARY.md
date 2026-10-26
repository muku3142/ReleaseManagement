---
phase: 17-rice-roadmap
plan: 01
subsystem: persistence
tags: [flyway, postgres, roadmap, release]
requires: []
provides:
  - "Flyway V16: roadmap_item, roadmap_item_input_history (append-only), release.packages_url"
  - "Release.packagesUrl (last record component) and ReleaseRepository.setPackagesUrl"
  - "FlywayConfig enabling out-of-order migrations"
affects: [17-02, 17-03, 17-07]
tech-stack:
  added: []
  patterns: ["append-only trigger pair copied from V14", "FlywayConfigurationCustomizer bean"]
key-files:
  created:
    - src/main/resources/db/migration/V16__roadmap.sql
    - src/test/java/com/evision/releasemgmt/adapters/persistence/V16RoadmapMigrationIT.java
    - src/main/java/com/evision/releasemgmt/config/FlywayConfig.java
    - src/test/java/com/evision/releasemgmt/config/FlywayConfigTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/domain/release/Release.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseEntity.java
    - src/main/java/com/evision/releasemgmt/ports/ReleaseRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/V14PortfolioMigrationIT.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/ReleasePersistenceIT.java
key-decisions:
  - "Flyway out-of-order enabled by FlywayConfig; each migration must not depend on a higher-numbered one."
metrics:
  completed: 2026-10-25
---

# Phase 17 Plan 01: Roadmap schema and packages link Summary

Flyway V16 adds the RICE roadmap tables (DB-enforced value sets, unique Jira keys, trigger-protected history) and a nullable `release.packages_url`, carried through `Release`, the entity and `ReleaseRepository.setPackagesUrl` (any status, no `items_changed_at` stamp, re-indexes the release).

## Tasks

| Task | Commit |
|------|--------|
| 1. V16 migration + IT (V14 IT pinned to target 14) | bcaff04 |
| 2. Release.packagesUrl, setPackagesUrl, 23-call constructor sweep in 13 test files | 55a44b0 |
| 3. FlywayConfig out-of-order | see git log (feat(17-01): enable Flyway out-of-order) |

## Decisions

- Flyway out-of-order enabled by FlywayConfig; each migration must not depend on a higher-numbered one.

## Deviations from Plan

None. The constructor sweep was done with a paren-matching node script, preserving CRLF. Task 3 was written test-first but no separate RED run was recorded.

## Verification

Full `clean test` after Task 2: 1496 tests, 0 failures. After Task 3: FlywayConfigTest, V16RoadmapMigrationIT, V14PortfolioMigrationIT and ReleasePersistenceIT (31 tests) pass. Neither application.yml was touched.

## Self-Check: PASSED
