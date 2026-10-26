---
phase: 06-azure-devops-enrichment
plan: 05
subsystem: enrichment
tags: [jira-dev-status, virtual-threads, best-effort, rest-api]
requires: [06-01, 06-03]
provides:
  - EnrichmentService.enrich(releaseId, tickets) bounded parallel code-link discovery
  - POST /api/releases/{id}/code-links/refresh[?ticketKey=]
  - codeLinks object on every release item in the release payload
affects: [06-07, 06-08, 06-09]
tech-stack:
  added: []
  patterns: [semaphore-capped virtual-thread fan-out with one shared deadline, single write after all HTTP work]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/EnrichmentService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/CodeLinksResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/PullRequestLinkResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/CommitLinkResponse.java
    - src/test/java/com/evision/releasemgmt/application/EnrichmentServiceTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/application/ReleaseService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseItemResponse.java
    - src/test/java/com/evision/releasemgmt/application/ReleaseServiceTicketsTest.java
    - src/test/java/com/evision/releasemgmt/application/ReleaseServiceManualItemsTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/ReleaseControllerTest.java
key-decisions:
  - "Enrichment hooks into create/add/refresh via enrichQuietly after the core write; refreshCodeLinks is not quiet and propagates only the final-write exceptions"
  - "Commit cap keeps the newest commits by authoredAt and flags the item incomplete with reason COMMIT_LIMIT"
requirements-completed: [ADO-01, ADO-02, ADO-03, ADO-05, INSIGHT-03]
duration: ~25 min
completed: 2026-10-12
---

# Phase 6 Plan 05: Enrichment service and endpoint Summary

Jira dev-status code links are now stored for every Jira-backed item on create, add, refresh and a new standalone "Refresh code links" action, discovered in parallel under a service-wide cap of 6 and a 45 s deadline, and returned as `codeLinks` on each release item.

## Tasks

| Task | Commit | Result |
|------|--------|--------|
| 1. EnrichmentService | f93d0b2 | Virtual-thread fan-out, service-wide Semaphore, one shared deadline with cancel(true), exactly one saveEnrichment call, failure mapping to EnrichmentReason, previous links/history preserved on failure, commit cap |
| 2. Hooks, endpoint, DTOs | 4d4056c | enrichQuietly in create/add/refresh, refreshCodeLinks, POST /{id}/code-links/refresh, CodeLinksResponse / PullRequestLinkResponse / CommitLinkResponse, ReleaseItemResponse.codeLinks |

## Verification

Full backend suite `mvn -P '!frontend' clean test`: 723 tests, 0 failures, 0 errors (704 baseline plus 19 new). ArchitectureBoundaryTest green.

## Deviations from Plan

**1. [Rule 3 - Blocking] Existing service tests used strict stubs on return values no longer consumed.** `repository.addItems(...)` and `replaceItemSnapshots(...)` return values are now ignored because the result is re-read with `get(releaseId)` after enrichment, so those stubs raised UnnecessaryStubbingException. I removed them. Create tests now stub `findById` for the created id. Files: ReleaseServiceTicketsTest.

**2. [Environment] Worktree base reset.** The worktree started at 4d1d621, so I ran the prescribed `git reset --hard 2ad0081` after the HEAD assertions passed.

Otherwise the plan was executed as written. `@PreDestroy shutdown()` uses `shutdownNow()` (RenderService uses `shutdown()`); in-flight enrichment calls should be interrupted on shutdown.

## Notes for later plans

- Workers never touch the repository or SecurityContext; `enrichOne` is package-private and is the extension point for 06-08 (ADO completion) and 06-09 (history).
- A ticket with a null `jiraIssueId` (pre-Phase-6 row not yet re-fetched) gets no result and is left untouched until the next code-links refresh.
- Commits without an author date are dropped in `enrichOne` before capping, because `release_item_commit.authored_at` is NOT NULL (06-03). Tested.

## Known Stubs

None.

## Threat Flags

None. T-06-21 to T-06-25 are mitigated as planned (key filtered by TICKET_KEY and membership in the release, semaphore/deadline/commit cap, context resolved on the request thread, errors caught, logs carry only ticket key and release id).

## Self-Check: PASSED

Both commits (f93d0b2, 4d4056c) exist; all created files are present.
