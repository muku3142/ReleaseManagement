---
phase: 03-release-assembly
plan: 02
subsystem: persistence
tags: [spring-data-jpa, postgres, testcontainers, hexagonal-architecture]

# Dependency graph
requires:
  - phase: 03-release-assembly
    plan: 01
    provides: V3 migration, domain/release package, ReleaseRepository port, three no-Lombok JPA entities
provides:
  - ReleaseRepositoryAdapter — the single implementation of ReleaseRepository, proven against real PostgreSQL 18
  - ReleaseJpaRepository / ReleaseTicketItemJpaRepository / ReleaseManualItemJpaRepository / ReleaseSummaryRow
  - Injection-safe, wildcard-safe release search (REL-05)
affects: [03-03, 03-04, 03-05, 03-06, 03-07, 03-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Adapter escapes LIKE metacharacters (%, _, backslash) before binding a user search term to a native ILIKE query — bound parameters stop SQL structure injection but not LIKE-pattern wildcard injection, which needed a separate fix"
    - "Compound writes (create-with-items) use saveAndFlush inside @Transactional so a unique-constraint violation surfaces inside the same method call rather than at commit, letting the adapter translate it to a domain exception"

key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseTicketItemJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseManualItemJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseSummaryRow.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/ReleasePersistenceIT.java
  modified: []

key-decisions:
  - "ReleaseRepositoryAdapter.search() escapes %, _ and backslash in the caller's query string before it reaches the native ILIKE pattern — the plan's parameterised query stops SQL injection but a literal % or _ typed into the search box would otherwise still act as a wildcard once concatenated server-side; caught by the IT's searchTreatsSqlWildcardsAndQuotesAsLiteralText test"

requirements-completed: [REL-01, REL-04, REL-05]

# Metrics
duration: 22min
completed: 2026-08-05
---

# Phase 3 Plan 2: Release Persistence Summary

**ReleaseRepositoryAdapter — the sole implementation of the ReleaseRepository port, backed by three Spring Data repositories and proven against a real PostgreSQL 18 Testcontainer across 13 assertions**

## Performance

- **Duration:** 22 min
- **Started:** 2026-08-05T17:14:00+03:00
- **Completed:** 2026-08-05T17:36:00+03:00
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments
- Three Spring Data repositories plus a native-query projection: `ReleaseJpaRepository` (parameterised ILIKE search summing item counts across both item tables, REL-05), `ReleaseTicketItemJpaRepository` (ticket-key-ascending order, D-03-04), `ReleaseManualItemJpaRepository` (title-ascending order plus release-scoped lookups that prevent cross-release manual-item edits)
- `ReleaseRepositoryAdapter` implements all twelve `ReleaseRepository` methods: `create` wraps `saveAndFlush` + item insert in one `@Transactional` call and translates a unique-constraint violation into `ReleaseDuplicateException` (D-03-02, D-03-08); `replaceItemSnapshots` overwrites every ticket field except `addedAt` and stamps `lastRefreshedAt` (D-03-07); `freeze` is a one-way door with no inverse method anywhere in the codebase (D-03-10)
- `ReleasePersistenceIT` proves migration, entities and adapter agree under `ddl-auto: validate` against a real PostgreSQL 18 container — 13 tests covering one-transaction create, duplicate rejection, deterministic ordering of both item kinds, add/remove/refresh/freeze/delete, cascading delete, and case-insensitive, injection-safe, wildcard-safe search
- Full suite: 241 tests, 0 failures, 0 errors, `BUILD SUCCESS`

## Task Commits

Each task was committed atomically:

1. **Task 1: Spring Data repositories and the release summary projection** - `9682c26` (feat)
2. **Task 2: ReleaseRepositoryAdapter — the single implementation of the port** - `a3d9c5f` (feat)
3. **Task 3: ReleasePersistenceIT against a real PostgreSQL 18 container** - `9da26a6` (test, includes the search-escaping fix below)

_Note: SUMMARY commit follows below (worktree mode — STATE.md/ROADMAP.md updated by orchestrator, not this commit)._

## Files Created/Modified
- `src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseSummaryRow.java` - interface projection matching the search SELECT aliases
- `src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseJpaRepository.java` - parameterised native ILIKE search plus `findByStatusOrderByCreatedAtDesc`
- `src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseTicketItemJpaRepository.java` - ticket-key-ascending order, release+key lookup, release+key delete
- `src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseManualItemJpaRepository.java` - title-ascending order, release-scoped id lookup
- `src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java` - the only implementation of `ReleaseRepository`; entity-to-domain mapping both directions; LIKE-wildcard escaping in `search()`
- `src/test/java/com/evision/releasemgmt/adapters/persistence/ReleasePersistenceIT.java` - 13 Testcontainers assertions against PostgreSQL 18

## Decisions Made
- Escaped `%`, `_` and `\` in `ReleaseRepositoryAdapter.search()` before the value reaches the native ILIKE pattern (see Deviations below) — this is a new decision beyond the plan's literal SQL text, not a change to it.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `ReleaseJpaRepository.search()`'s literal `%` / `_` characters acted as ILIKE wildcards instead of literal text**
- **Found during:** Task 3, while implementing `searchTreatsSqlWildcardsAndQuotesAsLiteralText`
- **Issue:** The plan's Task 1 SQL (implemented verbatim) binds `:query` as a parameter, which correctly stops SQL *structure* injection (the `'; DROP TABLE release; --` case). But once that bound value is concatenated server-side into the pattern `'%' || :query || '%'`, any `%` or `_` characters *within* the value still function as ILIKE wildcards — Postgres has no way to distinguish "wildcard from the query text" vs. "wildcard from the bound value" once they're combined into one pattern string. A search for a literal `%` character would therefore have matched every row instead of none, failing the IT's own required assertion.
- **Fix:** Added a private `escapeLikeWildcards` helper in `ReleaseRepositoryAdapter` that escapes `\`, `%` and `_` (Postgres' default LIKE/ILIKE escape character is backslash) before the value is passed to `ReleaseJpaRepository.search()`. The native query text itself is untouched, so Task 1's literal-SQL acceptance criteria still hold exactly as written.
- **Files modified:** `src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java`
- **Commit:** `9da26a6`

## Issues Encountered
None beyond the deviation above.

## User Setup Required

None - no external service configuration required. (A running Docker daemon is required to execute `ReleasePersistenceIT`; this was available and used during this plan's execution.)

## Next Phase Readiness
- `ReleaseRepository` now has exactly one implementation; plan 03-03 and later can build the release-assembly application/web layer against the port only
- `ArchitectureBoundaryTest` confirms only `ReleaseRepositoryAdapter` and the repository interfaces themselves import the release JPA repositories
- Full `mvn test` suite green: 241 tests, 0 failures, 0 errors (includes the 13 new `ReleasePersistenceIT` cases plus all Phase 1-3-plan-1 tests unaffected by this plan's additions)

---
*Phase: 03-release-assembly*
*Completed: 2026-08-05*

## Self-Check: PASSED

All six key created files verified present on disk (four repository/projection files, the adapter, and the IT). All four task/summary commits (9682c26, a3d9c5f, 9da26a6, 04b1142) verified present in `git log --oneline --all`. Full `mvn -o test` run synchronously in the foreground: 241 tests, 0 failures, 0 errors, `BUILD SUCCESS` (Docker was available; `ReleasePersistenceIT`'s 13 assertions ran against a real PostgreSQL 18 Testcontainer, not skipped).
