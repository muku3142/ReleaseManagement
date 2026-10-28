---
phase: 06-azure-devops-enrichment
plan: 09
subsystem: enrichment
tags: [jira-changelog, status-history, status-category, insight-03]
requires: [06-02, 06-08]
provides:
  - JiraIssueHistoryPort and adapter (paged /changelog, status id -> category from /status)
  - StatusCategoryService (volatile cache, refresh once on miss)
  - history step in EnrichmentService.enrichOne
affects: [06-10, 08]
tech-stack:
  added: []
  patterns: [build-then-assign volatile cache, history independent of code-links outcome]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/enrichment/StatusTransition.java
    - src/main/java/com/evision/releasemgmt/ports/JiraIssueHistoryPort.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JiraIssueHistoryAdapter.java
    - src/main/java/com/evision/releasemgmt/application/StatusCategoryService.java
    - src/test/java/com/evision/releasemgmt/adapters/jira/JiraIssueHistoryAdapterTest.java
    - src/test/java/com/evision/releasemgmt/application/StatusCategoryServiceTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/application/EnrichmentService.java
    - src/test/java/com/evision/releasemgmt/application/EnrichmentServiceTest.java
key-decisions:
  - "History is computed at the top of enrichOne and passed to every result path (success, UNRECOGNISED, Jira failure, unexpected), so it is captured even when dev-status fails"
  - "The changelog is read only when StatusHistory.needsRead(ticket.updatedAt()); the recorded updated time is the one from the search response"
requirements-completed: [INSIGHT-03, ADO-05]
duration: ~25 min
completed: 2026-10-12
---

# Phase 6 Plan 09: Status history capture Summary

Each Jira-backed item now stores its status transitions with categories mapped by status id, `first_done_at` and `reopen_count`, read from the paginated `/changelog` only when the ticket's Jira `updated` changed; any history failure yields UNAVAILABLE without touching code links or the refresh.

## Tasks

| Task | Commit | Result |
|------|--------|--------|
| 1. Port, adapter, StatusCategoryService | ea24aca | Paged `/changelog` (startAt/total/isLast/empty-page stop, `app.jira.changelog.max-pages` ceiling to RESULT_SET_TOO_LARGE, 401/403/429/5xx/unreachable cascade, no retry), status-field items only, `created` parsed with `JiraTimestamps`; `/status` mapped by id; cache with exactly one refresh on a miss |
| 2. History step in EnrichmentService | c9c6546 | `statusHistory(...)` helper; skip when unchanged; RuntimeException -> `StatusHistory.unavailable()`; unknown category -> `derive` gives UNAVAILABLE with synced time set |

## Verification

Full backend suite: 763 tests, 0 failures, 0 errors (746 before, 17 added). ArchitectureBoundaryTest green.

## Deviations from Plan

- Test-only: `failureKeepsPreviousLinksAndHistory` used an UNAVAILABLE previous history with a null synced time, which now (correctly) triggers a re-read. The fixture was changed to an AVAILABLE history synced at the ticket's `updated` so it still proves history is preserved on a dev-status failure.
- `StatusCategoryService.categoriesFor` refreshes at most once per call, including the first (null-cache) call, so "first call fetches once" holds.

No other deviations.

## Known Stubs

None.

## Threat Flags

None. Mitigations T-06-37 to T-06-40 are in place: page ceiling and stop conditions, skip when `updated` unchanged, id-only mapping with UNAVAILABLE on unknown category, per-item catch.

## Self-Check: PASSED

All six created files exist; commits ea24aca and c9c6546 exist.
