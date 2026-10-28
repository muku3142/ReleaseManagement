---
phase: 17-rice-roadmap
plan: 06
subsystem: roadmap
tags: [jira, import, refresh]
requires: [17-02]
provides: [RoadmapImportService]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/RoadmapImportService.java
    - src/test/java/com/evision/releasemgmt/application/RoadmapImportServiceTest.java
requirements-completed: [ROAD-01, ROAD-04]
completed: 2026-10-25
---

# Phase 17 Plan 06: Roadmap import and refresh Summary

RoadmapImportService adds items from Jira keys or a release snapshot, and refreshes Jira fields with missing-versus-failed classification.

## Accomplishments
- addFromJira: key regex re-checked before JQL, cap of 100, batches of 100 with per-key findByKey fallback, dedupe (including moved issues and insert races) reported with item ids; only summary/status/key/issue id/updated time stored.
- addFromRelease: stored snapshot, no Jira call; syncedAt is the later of release.lastRefreshedAt and item.addedAt.
- refreshAll/refresh: fetch phase completes before any write; match by jiraIssueId then key; only an empty result from a successful findByKey flags an item unavailable; a Jira exception changes nothing; moved key kept off when another item owns it; cap of 500.

## Verification
RoadmapImportServiceTest: 18 tests, 0 failures (`clean test`).

## Commits
- 7b221fd test: failing import tests
- 2027610 feat: add from Jira / release
- 25281b1 test: failing refresh tests
- 6af7a8b feat: refresh

## Deviations from Plan
None. The first session was cut off before any commit; work restarted from base 18408f0.
