---
phase: quick-261012-nqd
plan: 01
subsystem: ticket-browser
tags: [enrichment, jira, ado, tickets-api, react]
key-files:
  modified:
    - src/main/java/com/evision/releasemgmt/application/EnrichmentService.java
    - src/main/java/com/evision/releasemgmt/application/TicketSearchService.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JiraRestTicketAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/web/TicketController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/CodeLinksResponse.java
    - frontend/src/api/tickets.ts
    - frontend/src/components/TicketBrowser.tsx
  created:
    - src/main/java/com/evision/releasemgmt/domain/jira/TicketNotFoundException.java
    - frontend/tests/components/TicketBrowserCodeLinks.test.tsx
completed: 2026-10-12
---

# Quick 261012-nqd: Ticket browser "Show linked commits"

On-demand, read-only commits/PR lookup in the expanded row of the shared TicketBrowser (browse screen and Add-tickets dialog), backed by `GET /api/tickets/{key}/code-links`.

## What changed

- `EnrichmentService.lookup(TicketDetail)`: same dev-status + ADO path, same permits/deadline/commit cap, but skips the changelog and never touches `ReleaseRepository`. `enrichOne` was split into `discover(...)` and the semaphore wrapper generalised to `bounded(previous, Supplier)`; `enrich` behaviour unchanged.
- `TicketSearchService.findByKey` resolves via the existing `/search/jql` path (`key = X`).
- `GET /api/tickets/{key}/code-links`: key validated with `^[A-Z][A-Z0-9]*-[0-9]+$` (400 before any Jira call), unknown key 404 `TICKET_NOT_FOUND`, lookup failures are 200 with status FAILED. `CodeLinksResponse.from` replaces `ReleaseController.toCodeLinksResponse`, so both endpoints emit identical JSON.
- Frontend: `useTicketCodeLinks` mutation + `TicketCodeLinks` component; fetch only on button click, renders `CodeLinksDetail`, error shows `messageForReason` and keeps the button for retry.

## Deviations from Plan

**1. [Requested addition] Unknown key returned HTTP 400 from Jira, not an empty page**
- Jira `/search/jql` with `key = <nonexistent>` returns 400 "An issue with key ... does not exist", which the adapter mapped to JIRA_ERROR (502) instead of our 404.
- Fix: in `JiraRestTicketAdapter.searchAll`, a `BadRequest` whose body contains "An issue with key" returns an empty list; every other 400 (and all other 4xx/5xx) still throws `JIRA_ERROR` as before. This sits at the adapter because `findByKey` cannot tell a 400 from a 5xx (both map to JIRA_ERROR).
- Tests: two WireMock tests in `JiraRestTicketAdapterTest` (unknown-key body -> empty; other 400 body -> JIRA_ERROR), plus `findByKey` empty test.

**2. [Rule 3 - test harness] 404 problem field is `reason`, not `code`.** The controller test asserts `$.reason` = `TICKET_NOT_FOUND` (that is the ProblemDetail property the handler sets).

The plan's `ArchitectureBoundaryTest` and `ReleaseControllerTest` passed unchanged. Working-tree files were CRLF (autocrlf); touched files were normalised to LF, git index is LF.

## Commits

- ce9d8e6 feat: read-only code-links lookup and key-based ticket resolution (service, adapter, tests)
- df410e2 feat: GET /api/tickets/{key}/code-links (controller, DTO, exception handler, tests)
- c44f3e5 feat: Show linked commits button in the ticket browser (frontend + test)

## Verification

- Backend full suite (`mvn -P '!frontend' clean test`): 881 tests, 0 failures, 0 errors, BUILD SUCCESS.
- Frontend: `npx tsc -b` clean; `npx vitest run` 28 files, 289 tests passed (4 new).

## Known Stubs

None.

## Self-Check: PASSED

Commits ce9d8e6, df410e2, c44f3e5 exist; created files present.
