---
phase: 02-jira-ticket-browsing
plan: 05
subsystem: ticket-search-endpoint
tags: [jira-cloud, jql-composition, status-category, auth-02, bean-validation, problem-detail]
dependency-graph:
  requires:
    - "01-05: JiraAccessContextService.currentContext() — the sole AUTH-02 credential path"
    - "01-03: GlobalExceptionHandler's handleMethodArgumentNotValid override (VALIDATION_FAILED + fieldErrors)"
    - "02-01: JiraTicketReadException + RESULT_SET_TOO_LARGE"
    - "02-03: FieldMetadataService.resolve, JiraTicketPort.searchAll, TicketDetail, TicketField"
  provides:
    - "domain/jira/StatusCategory: TO_DO / IN_PROGRESS / DONE with their Jira JQL names"
    - "domain/jira/TicketSearchCriteria: byProject / byJql factories + toJql() composition, DEFAULT_STATUS_CATEGORIES = {DONE}"
    - "application/TicketSearchService: credential + field resolution + port delegation"
    - "POST /api/tickets/search returning {\"tickets\": [...]}"
    - "dto/TicketSearchRequest with the XOR filter-mode rule and numeric-id format rules"
  affects:
    - "02-06: the SPA calls this endpoint and renders TicketResponse; statusCategories is picker-mode only"
    - "02-07: row expansion reads description/comments/commentTotal off the same TicketResponse"
    - "Phase 3 release assembly: consumes the selected ticket keys from this response shape"
tech-stack:
  added: []
  patterns:
    - "POST-for-search when the query cannot fit a URL: gets @Valid + the existing handleMethodArgumentNotValid override, where query parameters would raise BindException and yield a ProblemDetail with no reason property"
    - "JQL composed only from validated numeric ids and enum-supplied literals — no caller string reaches the query, so there is nothing to escape"
    - "A filter default that matters is enforced server-side as well as client-side, so an API caller that omits it is not silently given a wider result set"
    - "An explicitly empty filter set means 'no constraint' and is never re-defaulted — re-defaulting makes a control disobey its user"
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/jira/StatusCategory.java
    - src/main/java/com/evision/releasemgmt/domain/jira/TicketSearchCriteria.java
    - src/main/java/com/evision/releasemgmt/application/TicketSearchService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/TicketController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/TicketSearchRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/TicketSearchResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/TicketResponse.java
    - src/test/java/com/evision/releasemgmt/domain/jira/TicketSearchCriteriaTest.java
    - src/test/java/com/evision/releasemgmt/application/TicketSearchServiceTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/TicketControllerTest.java
  modified:
    - src/test/java/com/evision/releasemgmt/config/SecurityFilterChainIT.java
decisions:
  - "D-PLAN-05-A applied: POST /api/tickets/search, not GET with query parameters — unbounded JQL length plus the fact that only a request body reaches the existing VALIDATION_FAILED override"
  - "D-PLAN-05-B applied: composed JQL uses numeric ids only, both validated against ^[0-9]+$ at the DTO boundary"
  - "D-PLAN-05-C applied: ORDER BY key ASC on the composed path, nothing appended on the JQL path"
  - "D-PLAN-05-D applied: no /search/approximate-count call — an extra round trip for a number the UI-SPEC forbids treating as authoritative"
  - "D-PLAN-05-E applied: response is {\"tickets\": [...]} so an advisory count or truncation flag can be added later without a breaking change"
  - "JIRA-07 (added mid-phase): the status filter is statusCategory, not status name — Jira status names are workflow-configurable per project while the category is a fixed three-value concept, so the filter needs no per-project configuration"
  - "JIRA-07's Done-only default lives in TicketSearchCriteria, not only in the SPA, so an API caller omitting statusCategories gets release-ready tickets rather than the whole version"
  - "statusCategories carries no Bean Validation annotation: Jackson rejects an unknown enum value at deserialization, and the enum is the only vocabulary toJql can emit"
metrics:
  duration_minutes: 0
  tasks_completed: 2
  files_created: 10
  completed_date: "2026-08-04"
---

# Phase 02 Plan 05: Ticket Search Endpoint Summary

Wires wave 2's machinery into one endpoint: `POST /api/tickets/search` takes either a project + fix-version selection or raw JQL and returns the complete matching ticket set in a single response.

## What Was Built

**Task 1 — `TicketSearchCriteria` + `TicketSearchService`.** `StatusCategory` is a framework-free enum over Jira's three fixed categories, each carrying its JQL name. `TicketSearchCriteria` has two factories and one `toJql()`: the picker path composes `project = <id> AND fixVersion = <id> [AND statusCategory IN (...)] ORDER BY key ASC` from validated numeric ids and enum-supplied literals only, so no caller string reaches the query and there is nothing to escape. The raw-JQL path is forwarded verbatim — no appended `ORDER BY`, and deliberately **no** injected status clause, because silently narrowing a query its author wrote would change its meaning. `TicketSearchService` resolves `currentContext()`, then all nine `TicketField` ids via `EnumSet.allOf`, then delegates to the port.

**JIRA-07 — status filter (added mid-phase, at the user's request).** The screen previously had no status control at all: the only way to filter status was the raw-JQL escape hatch, so the default view was every ticket ever raised on a fix version. The filter is on `statusCategory` rather than status *name* because Jira status names are workflow-configurable per project ("Done" may be "Closed", "Released", "Resolved") while the category is a fixed three-value Jira concept — so it works on every project with no per-project setup. `DEFAULT_STATUS_CATEGORIES = {DONE}` lives in the domain record, so a caller that omits the field still gets release-ready tickets. An explicitly empty set stays empty and means every status; it is never re-defaulted back to Done.

**Task 2 — `POST /api/tickets/search` + authorization proof.** `TicketSearchRequest` carries `@Pattern(^[0-9]+$)` on both ids, `@Size(max = 2000)` on the JQL, and one `@AssertTrue` XOR method for the mutual-exclusivity rule — no custom constraint class, no `Validator` bean. All of it runs before any Jira call, so a malformed filter costs zero upstream requests. `SecurityFilterChainIT` gained two full-chain assertions rather than trusting the matcher by inspection: that `/api/tickets/**` did not inherit the public `GET /api/settings/jira-site` carve-out, and that a hard refresh on `/tickets` is forwarded to `index.html`. `SecurityConfig` is unchanged.

## Verification

- `mvn clean test` — **207 backend tests, 0 failures, 0 errors** (180 before this plan; +27).
- `SecurityFilterChainIT` — 9 tests (was 7), confirmed by surefire.
- All task 1 criteria pass, including the absence assertions: no framework imports in either domain type, no display-name interpolation into JQL, `adapters.*` imports in the service = 0.
- All task 2 criteria pass. Two criteria needed disambiguation and both were grep artifacts rather than defects: the `@Test` count of 10 in `SecurityFilterChainIT` included the `@Testcontainers` class annotation (9 real tests, matching surefire), and the `ExceptionHandler` count of 8 in `GlobalExceptionHandler` counted imports, the class name and the superclass — there are exactly **4** real `@ExceptionHandler` annotations, in a file this plan never touched (`git diff` empty).
- Commits: `a4c1684` (Task 1), `3e8e04e` (Task 2).

## Deviations from Plan

The plan was amended before execution to add the JIRA-07 status filter (committed as `425937b`), which added `StatusCategory.java`, a fourth component on `TicketSearchCriteria`, a fourth field on `TicketSearchRequest`, and four tests (2 criteria + 2 controller). Implementation then followed the amended plan exactly.

## Superseded After Execution (2026-08-04, commit `822dcd4`)

**Fix version became optional at the user's request**, after a live run showed the picker sitting empty for a project with no Jira Versions defined — which left `Load tickets` permanently disabled with no route forward but the JQL hatch. Changes to this plan's artifacts:

- `TicketSearchCriteria.byFixVersion(...)` is renamed **`byProject(...)`**; a null or blank `fixVersionId` omits the `fixVersion` JQL clause instead of matching an empty value.
- `TicketSearchRequest.isExactlyOneFilterMode()` now requires only `projectId` for picker mode, and additionally rejects a `fixVersionId` supplied with no project or alongside raw JQL — neither has a meaningful reading, and both were previously accepted or silently ignored.
- Three criteria tests and three controller tests added; the former `halfFilledPickerIsRejected` is replaced by `projectWithoutFixVersionIsAValidSearch`.

Backend total moved 207 → **212**.

## Issues Encountered

None.

## Known Stubs

**Known limitation carried forward from D-PLAN-05-C:** the raw-JQL path appends no `ORDER BY`, so a raw query without its own ordering is paged over an unordered result set, which Jira does not guarantee is stable across cursor pages. The release manager who reaches for raw JQL owns their ordering. The picker path is always explicitly ordered.

`statusCategories` is ignored on the JQL path by design — plan 06 must not send it there, or the UI would imply a filter that is not applied.

## Threat Flags

The JQL-injection surface is the material one and it is closed at two layers: `^[0-9]+$` on both ids at the HTTP boundary, and composition from ids and enum literals only in the domain. The 2000-character JQL cap bounds the escape hatch. AUTH-02 is proven by call order (`InOrder`), not by convention — the credential resolves before any Jira read, and the port is never reached when credential or field resolution fails.

## Self-Check: PASSED

All 10 created and 1 modified file verified on disk. Both task commits verified in `git log`. Full suite re-run green.
