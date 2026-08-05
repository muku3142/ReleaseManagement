---
phase: 03-release-assembly
plan: 06
subsystem: application+web
tags: [spring-mvc, hexagonal-architecture, webmvctest, jackson-typed-enum]

# Dependency graph
requires:
  - phase: 03-release-assembly
    plan: 01
    provides: ManualItem, ManualItemCategory, ReleaseRepository port's three manual-item methods
  - phase: 03-release-assembly
    plan: 03
    provides: ReleaseService/ReleaseController conventions, DTO and ProblemDetail patterns
  - phase: 03-release-assembly
    plan: 04
    provides: the ReleaseService/ReleaseController shape being extended in this plan
affects: [03-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Typed-enum request field (ManualItemCategory category, not String) rejects an unknown category value at Jackson deserialisation, before the service or database ever see it (D-03-03, T-03-35)"
    - "Edit funnels through the same requireOpen() guard as every other release mutation - no separate lock/state model for manual items"

key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ManualItemRequest.java
    - src/test/java/com/evision/releasemgmt/application/ReleaseServiceManualItemsTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/application/ReleaseService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java
    - src/test/java/com/evision/releasemgmt/adapters/web/ReleaseControllerTest.java

key-decisions:
  - "No plan deviations - implemented exactly as specified, including the exact addManualItem/updateManualItem/removeManualItem signatures and the nullToEmpty helper for the NOT NULL description column"

requirements-completed: [REL-08]

# Metrics
duration: 9min
completed: 2026-08-05
---

# Phase 3 Plan 6: Manual Release Items Summary

**ReleaseService gains addManualItem/updateManualItem/removeManualItem, all funneling through `requireOpen()`, and ReleaseController exposes them as three new endpoints on `/api/releases/{releaseId}/manual-items[/{manualItemId}]` — the category is a typed `ManualItemCategory` enum end to end, so an unrecognised value is a 400 at Jackson deserialisation, never a value that reaches the service or database**

## Performance

- **Duration:** 9 min
- **Started:** 2026-08-05T18:12:50+03:00
- **Completed:** 2026-08-05T18:21:41+03:00
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- `ManualItemRequest` validates `title` (`@NotBlank @Size(max = 200)`), `description` (`@Size(max = 5000)`, optional) and `category` (`@NotNull ManualItemCategory`) — the typed enum component means Jackson rejects any value outside the four fixed categories with a deserialisation failure that `GlobalExceptionHandler`'s existing `HttpMessageNotReadableException` handling already maps to a 4xx, so no new handler was needed
- `ReleaseService` gains `addManualItem`/`updateManualItem`/`removeManualItem`, each opening with `get(releaseId).requireOpen()` exactly like every other release mutation (D-03-13); `updateManualItem` deliberately passes `addedAt = null` in the constructed `ManualItem` so the adapter's existing `updateManualItem` (from plan 03-02) never overwrites the stored timestamp
- `ReleaseController` exposes `POST /api/releases/{releaseId}/manual-items` (201), `PUT /api/releases/{releaseId}/manual-items/{manualItemId}` (200) and `DELETE /api/releases/{releaseId}/manual-items/{manualItemId}` (200), all reusing the existing `toResponse(Release)` mapper so the release detail screen can replace its cached release in one hop, matching the ticket-item endpoints from plan 03-04
- No `SecurityConfig` change (`/api/**` was already `authenticated()`), no new migration (V3 already has `release_manual_item`), no new controller — confirmed by empty `git diff` on both and a zero-count grep for `ManualItemController`
- `ReleaseServiceManualItemsTest` (10 cases, plain JUnit + Mockito + AssertJ): id/timestamp assignment and title trimming, null-description-to-empty-string, all four `ManualItemCategory` values round-tripping (`values().length == 4`, locking D-03-03 against silent growth), `updateManualItem`'s id passthrough and `addedAt`-not-overwritten guarantee, delegation with both ids on remove, all three frozen-rejection paths, and not-found on all three methods
- `ReleaseControllerTest` grown from 30 to 41 `@WebMvcTest` cases: 201-with-body, blank-title/unknown-category/missing-category/overlong-description rejection, no-description acceptance, update/remove success, all three frozen-409 mappings in one test, not-found-404, and unauthenticated rejection
- Full `mvn -o test` suite: 307 tests, 0 failures, 0 errors, `BUILD SUCCESS`

## Task Commits

Each task was committed atomically:

1. **Task 1: Manual-item service methods, request DTO and endpoints** - `8cc934c` (feat)
2. **Task 2: Tests for manual-item add, edit, remove, category validation and frozen rejection** - `fe83a62` (test)

_Note: SUMMARY commit follows below (worktree mode — STATE.md/ROADMAP.md updated by orchestrator, not this commit)._

## Files Created/Modified
- `src/main/java/com/evision/releasemgmt/adapters/web/dto/ManualItemRequest.java` - validated add/edit payload, typed `ManualItemCategory` component
- `src/main/java/com/evision/releasemgmt/application/ReleaseService.java` - `addManualItem`/`updateManualItem`/`removeManualItem`, `nullToEmpty` helper
- `src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java` - three new endpoints on `/manual-items[/{manualItemId}]`
- `src/test/java/com/evision/releasemgmt/application/ReleaseServiceManualItemsTest.java` - 10 tests, mocked `ReleaseRepository`/`TicketSearchService`, no Spring context
- `src/test/java/com/evision/releasemgmt/adapters/web/ReleaseControllerTest.java` - grown from 30 to 41 `@WebMvcTest` cases

## Decisions Made
None - plan executed exactly as specified.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `POST`, `PUT` and `DELETE` on `/api/releases/{releaseId}/manual-items[/{manualItemId}]` are live and tested; plan 03-08 (frontend, disclosure form per the UI-SPEC) can wire the add/edit form and inline edit variant against this exact contract, OPEN-only per the same `requireOpen()` server-side control
- `ArchitectureBoundaryTest` confirms `ReleaseService` still imports no adapter package with no new collaborators added in this plan
- Full `mvn -o test` suite green: 307 tests, 0 failures, 0 errors (includes the 10 new `ReleaseServiceManualItemsTest` cases and the 11 new `ReleaseControllerTest` cases, plus every Phase 1-2-3 plan 1/2/3/4 test unaffected by this plan's additions)

## Threat Flags

None - all new surface (the three manual-item endpoints and the typed-category validation path) was already registered in this plan's own `<threat_model>` (T-03-35 through T-03-40) and no new surface beyond that register was introduced.

---
*Phase: 03-release-assembly*
*Completed: 2026-08-05*

## Self-Check: PASSED

All key created files verified present on disk (ManualItemRequest.java, ReleaseServiceManualItemsTest.java,
this SUMMARY, plus the modified ReleaseService.java, ReleaseController.java, ReleaseControllerTest.java).
Both task commits (8cc934c, fe83a62) verified present in `git log --oneline --all`. Full `mvn -o test` run
synchronously in the foreground: 307 tests, 0 failures, 0 errors, `BUILD SUCCESS`.
