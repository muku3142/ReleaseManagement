---
phase: 17-rice-roadmap
plan: 05
subsystem: application
tags: [roadmap, service, rice]
requires: [17-02]
provides:
  - "RoadmapService: ranked, get, addManual, setInputs, updateDetails, archive, restore, history"
affects: [17-07, 17-10]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/RoadmapService.java
    - src/test/java/com/evision/releasemgmt/application/RoadmapServiceTest.java
requirements-completed: [ROAD-02, ROAD-03, ROAD-05, ROAD-06]
completed: 2026-10-25
---

# Phase 17 Plan 05: Roadmap service Summary

RoadmapService with a single `ranked(filter)` ordering, validated input edits whose archived check runs inside the locked decide function, D-15-07 product-link rules, and archive/restore only (no delete).

## Tasks
1. Test-first: `test(17-05)` failing tests (3cc5cac), then `feat(17-05)` service (24e2084).

Tests: RoadmapServiceTest, 10 tests, all pass with `clean`.

## Deviations from Plan
None. The session limit interrupted the run after the test file was written; it was resumed from the untracked file at base 18408f0.

## Known Stubs
None.

## Self-Check: PASSED
