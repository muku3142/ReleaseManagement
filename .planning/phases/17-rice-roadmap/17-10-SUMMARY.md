---
phase: 17-rice-roadmap
plan: 10
subsystem: web
tags: [roadmap, rest, csv]
requires: [17-04, 17-05, 17-06, 17-07]
provides:
  - "/api/roadmap REST API per the 17-03 contract"
  - "CsvWriter: RFC 4180, UTF-8 BOM, formula-injection prefix"
  - "RoadmapRejectedException / RoadmapItemNotFoundException mapped to reason codes"
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/web/RoadmapController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/CsvWriter.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ (8 Roadmap* DTOs)
    - src/test/java/com/evision/releasemgmt/adapters/web/RoadmapControllerTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/CsvWriterTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
requirements-completed: [ROAD-01, ROAD-02, ROAD-03, ROAD-04, ROAD-05, ROAD-06, ROAD-07]
completed: 2026-10-25
---

# Phase 17 Plan 10: Roadmap REST API and CSV Summary

All contracted roadmap endpoints exist; list and CSV both call `RoadmapService.ranked(filter)`, and the CSV is UTF-8 BOM, CRLF, RFC 4180 with an apostrophe prefix on leading `= + - @ TAB CR`.

## Commits
- 86d424a feat(17-10): add a formula-safe CSV writer
- feat(17-10): expose the roadmap REST API and CSV export

## Tests
CsvWriterTest 6, RoadmapControllerTest 12, ArchitectureBoundaryTest 20: all pass after `clean`.

## Deviations from Plan
- CsvWriter had no separate RED commit: the test cannot compile without the class, so test and implementation went in one commit after a green run.
- Single-item responses (create, inputs, details) carry `scoreDisplay` computed with `RiceScorer` and `rank` null.
- `impact`/`effort` are stripped then reset to scale 0 when negative, so 10 serialises as `10` and not `1E+1`.
- Reason messages already existed in `frontend/src/api/reasonMessages.ts` (11 ROADMAP entries); no frontend change made.
- GlobalExceptionHandler edited with the Edit tool; CRLF preserved. New files are LF.

## Known Stubs
None.

## Self-Check: PASSED
