---
phase: 02-jira-ticket-browsing
plan: 03
subsystem: jira-ticket-search
tags: [jira-cloud, cursor-pagination, field-metadata, adf, wiremock, http2-pitfall]
dependency-graph:
  requires:
    - "01-05: JiraAccessContextService.currentContext() — AUTH-02 credential resolution"
    - "02-01: AdfFlattener for description and comment bodies"
    - "02-01: JiraTicketReadException + RESULT_SET_TOO_LARGE reason code"
    - "02-01: app.jira.search.page-size / max-pages in both application.yml files"
  provides:
    - "ports/JiraFieldMetadataPort + adapters/jira/JiraFieldMetadataAdapter: GET /rest/api/3/field"
    - "application/FieldMetadataService: cached, collision-safe display-name to field-id resolution with one refresh on miss"
    - "ports/JiraTicketPort + adapters/jira/JiraRestTicketAdapter: POST /rest/api/3/search/jql paged to exhaustion"
    - "domain/jira/TicketDetail: the flattened 11-field ticket record every later phase consumes"
    - "domain/jira/TicketField enum + JiraField record"
  affects:
    - "02-05: TicketSearchService composes FieldMetadataService.resolve() with JiraTicketPort.searchAll()"
    - "02-06: renders TicketDetail fields in the results table"
    - "Phase 5 document generation: TicketDetail is the ticket half of the template view-model; commentTotal lets a template show that comments were truncated"
tech-stack:
  added: []
  patterns:
    - "Cursor pagination terminates on the ABSENCE or blankness of nextPageToken, never on isLast — isLast is informational on this endpoint and does not flip true"
    - "A hard max-pages ceiling converts a token-chaining bug into a loud RESULT_SET_TOO_LARGE instead of a pinned thread; silent truncation and infinite looping are mirror failures and both are designed against"
    - "429 is surfaced immediately with no retry and no Retry-After sleep — retrying is precisely what would worsen throttling upstream"
    - "Colliding field display names throw rather than guess, closing the silent-wrong-field trap before Phase 5 depends on it"
    - "WireMock + JDK HttpClient: pin HTTP_1_1 for POST-with-body tests; the HTTP_2 default attempts an h2c cleartext upgrade that Jetty answers by closing the connection"
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/jira/TicketField.java
    - src/main/java/com/evision/releasemgmt/domain/jira/JiraField.java
    - src/main/java/com/evision/releasemgmt/domain/jira/TicketDetail.java
    - src/main/java/com/evision/releasemgmt/ports/JiraFieldMetadataPort.java
    - src/main/java/com/evision/releasemgmt/ports/JiraTicketPort.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JiraFieldPayload.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JiraFieldMetadataAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JqlSearchRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JqlSearchPage.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JiraRestTicketAdapter.java
    - src/main/java/com/evision/releasemgmt/application/FieldMetadataService.java
    - src/test/java/com/evision/releasemgmt/adapters/jira/JiraFieldMetadataAdapterTest.java
    - src/test/java/com/evision/releasemgmt/application/FieldMetadataServiceTest.java
    - src/test/java/com/evision/releasemgmt/adapters/jira/JiraRestTicketAdapterTest.java
  modified: []
decisions:
  - "FieldMetadataService caches in a single volatile Map field rather than a Spring cache abstraction — one map, one process, no eviction policy needed; invalidate() exists for an explicit refresh and a miss triggers exactly one refresh before failing"
  - "Both the not-found and the name-collision cases map to JIRA_ERROR rather than a new reason code: both are Jira-configuration faults the release manager cannot act on differently, and each logs the specific field name at WARN for the operator"
  - "The JDK HttpClient is pinned to HTTP_1_1 in JiraRestTicketAdapterTest only, not in production JiraRestClientConfig — production reaches Jira Cloud over HTTPS where HTTP/2 is negotiated by ALPN and the broken h2c cleartext upgrade path is never taken"
metrics:
  duration_minutes: 0
  tasks_completed: 3
  files_created: 14
  completed_date: "2026-08-04"
---

# Phase 02 Plan 03: Field Metadata & Cursor Pagination Summary

The highest-cost-if-wrong work in the phase: resolving every ticket field by name through Jira field metadata, and paging `POST /rest/api/3/search/jql` to exhaustion on its cursor model with a hard ceiling.

## What Was Built

**Task 1 — Field metadata resolution (JIRA-04).** `TicketField` enumerates the nine fields D-02-02 selected, each carrying its Jira display name. `JiraFieldMetadataAdapter` reads `GET /rest/api/3/field`; `FieldMetadataService` builds a lower-cased-name → list-of-ids map in one `volatile` field, resolves requested fields against it, and refreshes exactly once on a miss before failing. Two failure modes throw rather than degrade: a name that resolves to **no** id, and a name that resolves to **two or more** ids. The collision case is the important one — Jira does not enforce unique display names, and guessing between colliding ids would ship a silent wrong-field bug into Phase 5's custom-field usage. Both log the offending field name at WARN and surface `JIRA_ERROR`.

**Task 2 — `JiraTicketPort` + cursor-paginating adapter (JIRA-03/05/06).** `JiraRestTicketAdapter.searchAll` POSTs to `/rest/api/3/search/jql` in a `do/while` that terminates only when `nextPageToken` is absent or blank — never on `isLast`, which is informational on this endpoint and does not flip true. Breaching `app.jira.search.max-pages` while a token is still live throws `RESULT_SET_TOO_LARGE`. Every field lookup goes through the resolved id map, never a literal field name. Description and comment bodies pass through `AdfFlattener`, and `commentTotal` carries Jira's true count alongside the flattened bodies so the UI can show that more comments exist rather than presenting a partial list as complete. The six Jira HTTP failure modes each map to their reason code; 429 is surfaced immediately with no retry.

**Task 3 — WireMock proof of the pagination contract.** 18 tests, all against a stubbed Jira. The four that matter most: pages concatenate to exhaustion with the second request carrying the first's token; the first request omits `nextPageToken` entirely; `isLast: true` alongside a live token does **not** stop the loop; and a permanently-token-returning upstream throws `RESULT_SET_TOO_LARGE` after exactly `maxPages` requests, bounded and loud rather than hanging. Plus field mapping, null assignee/resolution, ADF flattening, comment totals, Basic auth, resolved-id request bodies, and one test per error branch — including `verify(1, ...)` on 429 proving no retry.

## Verification

- `mvn clean test` — **180 backend tests, 0 failures, 0 errors** (119 at Phase 1 close). Docker was started so the four Testcontainers ITs run rather than error.
- `cd frontend && npm test` — 15/15 green.
- `mvn test -Dtest=JiraRestTicketAdapterTest` — exit 0 in **15 s**, well inside the 60 s ceiling that proves the safety-ceiling test does not hang.
- Acceptance greps: `@Test` = 18 (≥18), `inScenario` = 6 (≥4), `RESULT_SET_TOO_LARGE` = 1 (≥1), `isLast` = 4 (≥1). `grep -rn 'customfield_' src/main/java/` → empty. Field-name literals in the ticket adapter → empty (every lookup is via the resolved id map).
- Key links: `adfFlattener.flatten` ×2 in the adapter, `fieldMetadataPort.fetchAllFields` ×1 in the service, test file 342 lines (min 150).
- Commits: `f99c014` (Task 1), `d295355` (Task 2), `dc5771c` (Task 3).

## Deviations from Plan

None in implementation — all three tasks match the plan. **Process deviation:** tasks 1 and 2 were committed in a git worktree by an earlier session that was interrupted partway through task 3. The branch was merged to `main` intact (merge `78c8c33`), task 3's in-flight test file was recovered from the worktree before removal, its one blocking failure was diagnosed and fixed, and it was committed as `dc5771c`.

## Issues Encountered

**All 18 task-3 tests failed as `JIRA_UNREACHABLE`, instantly (~8 ms each).** The interrupted session had left a debugging `e.printStackTrace()` in the adapter's `ResourceAccessException` branch, which was discarded — the committed adapter was correct and never the cause.

Root cause: the JDK `HttpClient` defaults to `HTTP_2`. On a plaintext connection it attempts an h2c cleartext upgrade, and WireMock's Jetty answers a **POST carrying a body** by closing the connection — surfacing as `EOFException` → `ResourceAccessException` → `JIRA_UNREACHABLE`. The existing `JiraRestAuthAdapterTest` never hit this because it only issues GETs, which survive the upgrade negotiation. Confirmed by a throwaway probe that printed the full cause chain (`Http2Connection$Http2TubeSubscriber.onComplete` → `EOFException: EOF reached while reading`) rather than by guessing.

Fix: pin `HttpClient.Version.HTTP_1_1` in the test's adapter factory, with a comment recording why. **Production was deliberately left unpinned** — Jira Cloud is HTTPS, where HTTP/2 is negotiated by ALPN and the broken cleartext-upgrade path is never taken. This is a test-harness artifact, not a production defect.

Worth carrying forward: any future WireMock test that POSTs a body needs the same pin.

## Known Stubs

Nothing user-visible lands in this plan by design — plan 05 wires it to `POST /api/tickets/search` and plan 06 to the screen. `FieldMetadataService.invalidate()` has no caller yet; it exists so an operator-triggered refresh is possible without a restart.

## Threat Flags

`T-02-03-01` (pagination DoS) and `T-02-03-02` (self-inflicted throttling) are the material ones and both are now proven by test rather than asserted: the ceiling test bounds the loop at exactly `maxPages` requests, and the 429 test asserts exactly one request. `T-02-03-03` (silent wrong field) is closed by the collision-throws rule.

## Self-Check: PASSED

All 14 created files verified on disk. All three task commits verified in `git log`. Full suite re-run green after the fix.
