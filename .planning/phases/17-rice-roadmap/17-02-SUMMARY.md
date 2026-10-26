---
phase: 17-rice-roadmap
plan: 02
subsystem: domain
tags: [rice, roadmap, bigdecimal, archunit]
requires: []
provides:
  - "domain/roadmap: RiceInputs validation, RiceScorer, RoadmapRanking total order, item model, result records"
  - "ports: RoadmapRepository, RoadmapHistoryRepository (read-only, ArchUnit enforced)"
affects: [17-04, 17-05, 17-06, 17-10]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/roadmap/ (17 files)
    - src/main/java/com/evision/releasemgmt/ports/RoadmapRepository.java
    - src/main/java/com/evision/releasemgmt/ports/RoadmapHistoryRepository.java
    - src/test/java/com/evision/releasemgmt/domain/roadmap/RiceInputsTest.java
    - src/test/java/com/evision/releasemgmt/domain/roadmap/RiceScorerTest.java
    - src/test/java/com/evision/releasemgmt/domain/roadmap/RoadmapRankingTest.java
  modified:
    - src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java
requirements-completed: [ROAD-02, ROAD-03, ROAD-06]
duration: 20min
completed: 2026-10-25
---

# Phase 17 Plan 02: RICE domain and ports Summary

Pure BigDecimal RICE scoring (MathContext 20, HALF_EVEN), validated inputs, a deterministic total-order ranking, and the two roadmap ports.

## Tasks
1. RiceInputs/RiceScorer/rejections, test-first: test commit then feat `validate RICE inputs and compute the score in one place`.
2. Item model, filter, ranking, test-first: test commit then feat `rank roadmap items in a deterministic total order`.
3. Result records, ports, ArchUnit `roadmapHistoryRepositoryExposesNoMutation`: one feat commit.

Tests: RiceInputsTest 7, RiceScorerTest 4, RoadmapRankingTest 5, ArchitectureBoundaryTest 20 (all pass, `clean`).

## Deviations from Plan
- [Rule 1 - Bug] RoadmapFilter.matches threw NPE on `Set.of(p).contains(null)` for items without a product; added a null guard (caught by the filter test).
- [Rule 2] Confidence validation bounds-checks (0..100) before `intValueExact()` so huge values yield ROADMAP_CONFIDENCE_INVALID, not ArithmeticException.

## Known Stubs
None.

## Self-Check: PASSED
