---
phase: 03-release-assembly
plan: 04
subsystem: application+web
tags: [spring-mvc, jql-injection-defense, hexagonal-architecture, webmvctest]

# Dependency graph
requires:
  - phase: 03-release-assembly
    plan: 01
    provides: domain/release package (Release aggregate, ReleaseItem, AddTicketsResult), ReleaseRepository port
  - phase: 03-release-assembly
    plan: 02
    provides: ReleaseRepositoryAdapter, the sole ReleaseRepository implementation
  - phase: 03-release-assembly
    plan: 03
    provides: ReleaseService and ReleaseController being extended, DTO/ProblemDetail conventions
  - phase: 02-jira-ticket-browsing
    provides: TicketSearchService (the only sanctioned per-principal Jira read path), TicketDetail
affects: [03-05, 03-06, 03-07, 03-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Ticket keys are validated twice on the way to a JQL string: @Pattern container-element constraint at the REST boundary, TICKET_KEY regex at the composition sink"
    - "createWithTickets fetches from Jira before any repository write, so a Jira failure cannot leave an orphan empty release (D-03-02)"
    - "addTickets diffs requested keys against repository.findTicketKeys() first, so an already-present key never reaches Jira or a write"

key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/AddTicketsRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseAddResultResponse.java
    - src/test/java/com/evision/releasemgmt/application/ReleaseServiceTicketsTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/application/ReleaseService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/CreateReleaseRequest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/ReleaseControllerTest.java

key-decisions:
  - "No plan deviations to production code - implemented exactly as specified, including the TICKET_KEY regex, FETCH_BATCH_SIZE=100 batching, and the exact addTickets/removeTicket/refresh signatures"
  - "Fixed a self-contradictory runtime assertion in the plan's own JQL-injection test spec (doesNotContain('OR') against a string that legitimately contains 'ORDER BY'); narrowed to doesNotContain(' OR ') so the injected clause is still caught without a false positive on the ORDER BY keyword"

requirements-completed: [REL-02, REL-04]

# Metrics
duration: 18min
completed: 2026-08-05
---

# Phase 3 Plan 4: Ticket Materialisation on a Release Summary

**ReleaseService gains createWithTickets/addTickets/removeTicket/refresh, all funneling through `requireOpen()`, and ReleaseController exposes them as three new endpoints plus a changed create response — every Jira read stays on the `TicketSearchService` path and no ticket key that fails `^[A-Z][A-Z0-9]*-[0-9]+$` ever reaches a JQL string**

## Performance

- **Duration:** 18 min
- **Started:** 2026-08-05T17:55:00+03:00
- **Completed:** 2026-08-05T18:13:00+03:00
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments
- `ReleaseService` gains a second constructor-injected collaborator, `TicketSearchService` — the only path that resolves the signed-in user's own credential (AUTH-02). Zero `JiraAccessContext`/`JiraTicketPort` imports remain in the service, confirmed by grep gate and `ArchitectureBoundaryTest`
- `createWithTickets` fetches from Jira before writing anything, so a Jira failure during release-and-tickets creation leaves no orphan empty release (D-03-02); `addTickets` diffs against `repository.findTicketKeys()` first so an already-present key never reaches Jira or a write, and reports `added`/`alreadyPresent` honestly (D-03-13); `removeTicket` and `refresh` both guard on `requireOpen()`, and `refresh` batches Jira reads at 100 keys per call
- Ticket keys are validated twice on the way to a JQL string: `@Pattern(regexp = "^[A-Z][A-Z0-9]*-[0-9]+$")` as a container-element constraint on both `CreateReleaseRequest.ticketKeys` and `AddTicketsRequest.ticketKeys`, and again by the private `TICKET_KEY` regex at the `fetchTickets` composition sink, which silently drops anything non-matching rather than erroring
- `ReleaseController` now exposes `POST /api/releases/{id}/items`, `DELETE /api/releases/{id}/items/{ticketKey}` and `POST /api/releases/{id}/refresh`; `POST /api/releases` returns `ReleaseAddResultResponse` (`{ release, added, alreadyPresent }`) instead of a bare `ReleaseResponse`. `SecurityConfig` is untouched — confirmed by an empty `git diff`
- `ReleaseServiceTicketsTest` (15 cases, plain JUnit + Mockito): materialisation with full `TicketDetail` fidelity, idempotent add with honest counts on empty/partial/duplicate-key requests, the JQL-injection rejection at the fetch sink (`) OR project = SECRET--` payload never reaches the composed query), 100-key batching over 250 items, all three frozen-rejection paths, and the create-does-not-persist-on-Jira-failure guarantee
- `ReleaseControllerTest` grown to 30 cases: the changed create-response shape, all four new/changed endpoints, validation limits (empty list, >500 keys), the reused `RELEASE_FROZEN`/`JIRA_UNREACHABLE` ProblemDetail mappings, and unauthenticated rejection on the new item endpoint
- Full `mvn test` suite: 286 tests, 0 failures, 0 errors, `BUILD SUCCESS`

## Task Commits

Each task was committed atomically:

1. **Task 1: Snapshot materialisation in ReleaseService** - `3c4f37a` (feat)
2. **Task 2: Ticket-item endpoints and the extended create request** - `232c842` (feat)
3. **Task 3: Tests for idempotent add, snapshotting, refresh and frozen rejection** - `edc07ba` (test)

_Note: SUMMARY commit follows below (worktree mode — STATE.md/ROADMAP.md updated by orchestrator, not this commit)._

## Files Created/Modified
- `src/main/java/com/evision/releasemgmt/application/ReleaseService.java` - `createWithTickets`/`addTickets`/`removeTicket`/`refresh`, `TICKET_KEY` guard, batched `fetchTickets` via `TicketSearchService`
- `src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java` - `create` now returns `ReleaseAddResultResponse`; three new endpoints (`items` POST/DELETE, `refresh` POST)
- `src/main/java/com/evision/releasemgmt/adapters/web/dto/CreateReleaseRequest.java` - fourth optional `ticketKeys` component with a container-element `@Pattern`
- `src/main/java/com/evision/releasemgmt/adapters/web/dto/AddTicketsRequest.java` - `@NotEmpty` `ticketKeys`, same key pattern and 500-item cap
- `src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseAddResultResponse.java` - `{ release, added, alreadyPresent }` carrying the whole release so the UI lands on detail without a second request
- `src/test/java/com/evision/releasemgmt/application/ReleaseServiceTicketsTest.java` - 15 tests, mocked `ReleaseRepository`/`TicketSearchService`, no Spring context
- `src/test/java/com/evision/releasemgmt/adapters/web/ReleaseControllerTest.java` - grown from 19 to 30 `@WebMvcTest` cases

## Decisions Made
- Fixed a self-contradictory assertion in the plan's own test spec: asserting a JQL string both equals `key in (CNV-1) ORDER BY key ASC` and `doesNotContain("OR")` is impossible, since `ORDER` itself contains the substring `OR`. Narrowed the negative assertion to `doesNotContain(" OR ")` (the actual injected-clause shape) so the JQL-injection guarantee is still exercised without a false-positive failure on Jira's own `ORDER BY` keyword.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Test-spec assertion in `aKeyThatIsNotAValidJiraIssueKeyNeverReachesJql` was internally inconsistent**
- **Found during:** Task 3, first test run
- **Issue:** `assertThat(jql).doesNotContain("OR")` failed against the very string the same test asserts is the exact expected output (`key in (CNV-1) ORDER BY key ASC`), because `ORDER` contains `OR` as a substring. This is a bug in the plan's literal test-spec text, not in `ReleaseService` — the production JQL-injection defense (the `TICKET_KEY` regex at the fetch sink) is correct and unaffected.
- **Fix:** Narrowed the negative assertion from `doesNotContain("OR")` to `doesNotContain(" OR ")`, matching the actual injected clause shape (`) OR project = SECRET--`) while no longer colliding with `ORDER BY`. The `isEqualTo("key in (CNV-1) ORDER BY key ASC")` assertion immediately below already proves nothing beyond `CNV-1` reached the query.
- **Files modified:** `src/test/java/com/evision/releasemgmt/application/ReleaseServiceTicketsTest.java`
- **Commit:** `edc07ba`

## Issues Encountered
None beyond the deviation above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `POST /api/releases`, `POST /api/releases/{id}/items`, `DELETE /api/releases/{id}/items/{ticketKey}` and `POST /api/releases/{id}/refresh` are all live and tested; plan 03-05 (frontend, running in parallel on `frontend/**` only) can wire the add-to-release picker and release detail screen against this exact contract
- `ArchitectureBoundaryTest` confirms `ReleaseService` still imports no adapter package with the new `TicketSearchService` collaborator in place
- Full `mvn -o test` suite green: 286 tests, 0 failures, 0 errors (includes the 15 new `ReleaseServiceTicketsTest` cases and the 11 new/changed `ReleaseControllerTest` cases, plus every Phase 1-2-3-plan-1/2/3 test unaffected by this plan's additions)

---
*Phase: 03-release-assembly*
*Completed: 2026-08-05*

## Self-Check: PASSED

All key created files verified present on disk (AddTicketsRequest.java, ReleaseAddResultResponse.java,
ReleaseServiceTicketsTest.java, plus the modified ReleaseService.java, ReleaseController.java,
CreateReleaseRequest.java, ReleaseControllerTest.java). All three task commits (3c4f37a, 232c842,
edc07ba) verified present in `git log --oneline`. Full `mvn -o test` run synchronously in the
foreground: 286 tests, 0 failures, 0 errors, `BUILD SUCCESS`.
