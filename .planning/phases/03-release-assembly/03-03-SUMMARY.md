---
phase: 03-release-assembly
plan: 03
subsystem: web
tags: [spring-mvc, problemdetail, hexagonal-architecture, webmvctest]

# Dependency graph
requires:
  - phase: 03-release-assembly
    plan: 01
    provides: domain/release package (Release aggregate, requireOpen() guard, three exceptions), ReleaseRepository port
  - phase: 03-release-assembly
    plan: 02
    provides: ReleaseRepositoryAdapter, the sole ReleaseRepository implementation
provides:
  - ReleaseService — create/get/search/freeze/delete orchestration over ReleaseRepository
  - ReleaseController — the /api/releases REST surface (5 endpoints)
  - Six flat DTOs (CreateReleaseRequest, ReleaseResponse, ReleaseItemResponse, ManualItemResponse, ReleaseSummaryResponse, ReleaseSearchResponse)
  - Three new ProblemDetail reason codes (RELEASE_NOT_FOUND, RELEASE_FROZEN, RELEASE_DUPLICATE) plus MethodArgumentTypeMismatchException -> VALIDATION_FAILED
affects: [03-04, 03-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Actor plumbing: Principal.getName() -> service method, never a request-body field (prevents actor spoofing, T-03-14)"
    - "ReleaseItemResponse's first eleven fields are ordered identically to TicketResponse so the release detail screen can reuse Phase 2's ticket table markup unchanged"

key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/ReleaseService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/CreateReleaseRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseItemResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ManualItemResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseSummaryResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseSearchResponse.java
    - src/test/java/com/evision/releasemgmt/adapters/web/ReleaseControllerTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java

key-decisions:
  - "No plan deviations — implemented exactly as specified, including the exact ProblemDetail detail-string wording and the MethodArgumentTypeMismatchException -> VALIDATION_FAILED mapping"

requirements-completed: [REL-01, REL-03, REL-04, REL-05]

# Metrics
duration: 15min
completed: 2026-08-05
---

# Phase 3 Plan 3: Release Lifecycle on HTTP Summary

**ReleaseService plus ReleaseController expose create/get/list-search/freeze/delete at `/api/releases`, with the actor always taken from the authenticated principal and three new ProblemDetail reason codes (RELEASE_NOT_FOUND, RELEASE_FROZEN, RELEASE_DUPLICATE) extending the existing error taxonomy**

## Performance

- **Duration:** 15 min
- **Started:** 2026-08-05T17:50:00+03:00
- **Completed:** 2026-08-05T18:05:00+03:00
- **Tasks:** 3
- **Files modified:** 9

## Accomplishments
- `ReleaseService` (`@Service`, constructor-injecting `ReleaseRepository` only, zero adapter imports — confirmed by `ArchitectureBoundaryTest`) implements create/get/search/freeze/delete, funneling freeze and delete through `Release.requireOpen()` so a write against a `FROZEN` release is rejected server-side (D-03-10, D-03-11)
- `GlobalExceptionHandler` gains four handlers in this plan: `RELEASE_NOT_FOUND` (404), `RELEASE_FROZEN` (409), `RELEASE_DUPLICATE` (409), and `MethodArgumentTypeMismatchException` -> `VALIDATION_FAILED` (400) so a malformed path UUID no longer falls through to the generic 500 handler
- `ReleaseController` exposes all five endpoints exactly as specified — `POST /api/releases` (201), `GET /api/releases` (200, search), `GET /api/releases/{id}` (200), `POST /api/releases/{id}/freeze` (200), `DELETE /api/releases/{id}` (204) — with the actor on `create` and `freeze` taken exclusively from `Principal.getName()` (T-03-14); no `SecurityConfig` change was needed or made (`git diff` empty, confirmed)
- Six flat DTOs with no Jackson annotations; `ReleaseItemResponse`'s first eleven fields mirror `TicketResponse` field-for-field so Phase 2's ticket table markup can be reused unchanged in plan 03-05
- `ReleaseControllerTest`: 19 `@WebMvcTest` cases covering every endpoint, every ProblemDetail reason mapping, the actor-capture assertions on create/freeze, the negative "no unfreeze endpoint" test, and both authentication-rejection cases
- Full `mvn test` suite: 260 tests, 0 failures, 0 errors

## Task Commits

Each task was committed atomically:

1. **Task 1: ReleaseService and the three new ProblemDetail reason codes** - `e94cd57` (feat)
2. **Task 2: ReleaseController and the six DTOs** - `2478161` (feat)
3. **Task 3: ReleaseControllerTest covering every endpoint and every failure mapping** - `1c9a1f1` (test)

_Note: SUMMARY commit follows below (worktree mode — STATE.md/ROADMAP.md updated by orchestrator, not this commit)._

## Files Created/Modified
- `src/main/java/com/evision/releasemgmt/application/ReleaseService.java` - create/get/search/freeze/delete orchestration, `requireOpen()` guard on freeze and delete
- `src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java` - +4 `@ExceptionHandler` methods (RELEASE_NOT_FOUND, RELEASE_FROZEN, RELEASE_DUPLICATE, MethodArgumentTypeMismatchException), reusing the existing `problem()` helper
- `src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java` - the `/api/releases` REST surface, five endpoints, actor from `Principal`
- `src/main/java/com/evision/releasemgmt/adapters/web/dto/CreateReleaseRequest.java` - `@NotBlank`/`@Size`-validated request record, no semver `@Pattern` on version (D-03-08)
- `src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseResponse.java`, `ReleaseItemResponse.java`, `ManualItemResponse.java`, `ReleaseSummaryResponse.java`, `ReleaseSearchResponse.java` - flat response records, enums exposed as `.name()`
- `src/test/java/com/evision/releasemgmt/adapters/web/ReleaseControllerTest.java` - 19 tests, every endpoint and every failure mapping

## Decisions Made
None - plan executed exactly as specified.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `POST /api/releases` currently takes name, version and jiraProjectKey only, as scoped — plan 03-04 extends `CreateReleaseRequest` with `ticketKeys` and swaps the create response to `ReleaseAddResultResponse` once ticket-materialisation lands; no consumer exists in between, so nothing is left half-built
- `ArchitectureBoundaryTest` confirms `ReleaseService` imports no adapter package
- Full `mvn test` suite green: 260 tests, 0 failures, 0 errors (includes the 19 new `ReleaseControllerTest` cases plus all Phase 1-3-plan-1-plan-2 tests unaffected by this plan's additions)

---
*Phase: 03-release-assembly*
*Completed: 2026-08-05*

## Self-Check: PASSED

All nine key created/modified files verified present on disk (ReleaseService, ReleaseController, six DTOs, ReleaseControllerTest, GlobalExceptionHandler modification). All three task commits (e94cd57, 2478161, 1c9a1f1) verified present in `git log --oneline`. Full `mvn -o test` run synchronously in the foreground: 260 tests, 0 failures, 0 errors (surefire report aggregate across 29 report files).
