---
phase: 17-rice-roadmap
plan: 04
subsystem: persistence
tags: [jpa, roadmap, postgres, pessimistic-lock]
requires: [17-01, 17-02]
provides:
  - "RoadmapRepositoryAdapter and RoadmapHistoryRepositoryAdapter implementing the roadmap ports"
  - "RoadmapPersistenceIT pinning ROAD-04 and ROAD-06 persistence rules"
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/RoadmapItemEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/RoadmapInputHistoryEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/RoadmapItemJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/RoadmapInputHistoryJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/RoadmapRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/RoadmapHistoryRepositoryAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/RoadmapPersistenceIT.java
requirements-completed: [ROAD-01, ROAD-04, ROAD-05, ROAD-06]
completed: 2026-10-25
---

# Phase 17 Plan 04: Roadmap persistence Summary

Both roadmap ports over JPA: Jira refresh and details edits are targeted `@Modifying` JPQL updates (never load-modify-save), input edits run under `PESSIMISTIC_WRITE` and write one history row per changed field in the same transaction, and a duplicate Jira key maps to `ROADMAP_JIRA_KEY_DUPLICATE`.

## Tasks
1. Entities, JPA repositories, adapters: `feat(17-04): persist roadmap items and their input history`.
2. `RoadmapPersistenceIT` (8 tests): `test(17-04): pin roadmap persistence rules against Postgres`.

Tests: RoadmapPersistenceIT 8, ArchitectureBoundaryTest 20, all pass after `clean test-compile`.

## Deviations from Plan
- The `applyJiraSnapshot` query parameter is named `:jstatus` (not `:status`) so the query text never contains a bare status assignment.
- The IT compares inputs with `changedFields` rather than `equals` because NUMERIC(3,2) returns `2.00` for `2` (test fix, not a code bug).
- Modifying queries use `flushAutomatically` and `clearAutomatically` so re-reads in the same transaction see the update.
- New files are LF, not CRLF.

## Known Stubs
None.

## Self-Check: PASSED
