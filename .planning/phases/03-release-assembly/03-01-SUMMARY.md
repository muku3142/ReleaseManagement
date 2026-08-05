---
phase: 03-release-assembly
plan: 01
subsystem: database
tags: [flyway, postgres, jpa, hibernate, jackson3, hexagonal-architecture]

# Dependency graph
requires:
  - phase: 01-foundation
    provides: hexagonal package layout (domain/ports/adapters), no-Lombok entity convention, EncryptedTokenConverter AttributeConverter mechanism, ArchitectureBoundaryTest boundary rules
  - phase: 02-jira-ticket-browsing
    provides: TicketDetail record that ReleaseItem wraps whole
provides:
  - V3 Flyway migration with release / release_ticket_item / release_manual_item tables
  - domain/release package (Release aggregate, ReleaseItem, ManualItem, ReleaseSummary, AddTicketsResult, ReleaseStatus, ManualItemCategory, three exceptions)
  - ReleaseRepository port — the full 12-method persistence contract for the rest of the phase
  - JsonStringListConverter, ReleaseEntity, ReleaseTicketItemEntity, ReleaseManualItemEntity
affects: [03-02, 03-03, 03-04, 03-05, 03-06, 03-07, 03-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Aggregate mutation via record + requireOpen() guard + repository call, never in-place mutation (records are immutable)"
    - "Repository port methods return the reloaded aggregate so callers never need a follow-up read"
    - "JPA entities have no relationship annotations (@OneToMany/@ManyToOne); the release_id column is a plain FK, item repositories query by it directly in plan 03-02"

key-files:
  created:
    - src/main/resources/db/migration/V3__release_assembly.sql
    - src/main/java/com/evision/releasemgmt/domain/release/Release.java
    - src/main/java/com/evision/releasemgmt/domain/release/ReleaseItem.java
    - src/main/java/com/evision/releasemgmt/domain/release/ManualItem.java
    - src/main/java/com/evision/releasemgmt/domain/release/ReleaseStatus.java
    - src/main/java/com/evision/releasemgmt/domain/release/ManualItemCategory.java
    - src/main/java/com/evision/releasemgmt/domain/release/ReleaseSummary.java
    - src/main/java/com/evision/releasemgmt/domain/release/AddTicketsResult.java
    - src/main/java/com/evision/releasemgmt/domain/release/ReleaseNotFoundException.java
    - src/main/java/com/evision/releasemgmt/domain/release/ReleaseFrozenException.java
    - src/main/java/com/evision/releasemgmt/domain/release/ReleaseDuplicateException.java
    - src/main/java/com/evision/releasemgmt/ports/ReleaseRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/converter/JsonStringListConverter.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseTicketItemEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseManualItemEntity.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/converter/JsonStringListConverterTest.java
  modified: []

key-decisions:
  - "No plan deviations — implemented exactly as specified, including the Jackson 3 tools.jackson.* import path and the no-relationship-annotation entity design"

patterns-established:
  - "Release aggregate mutation pattern: immutable record + requireOpen() guard funneling every state change through one check, repository does the actual persistence"
  - "ReleaseItem wraps TicketDetail whole rather than re-listing its fields, so the two records cannot drift apart"

requirements-completed: [REL-01, REL-02, REL-03, REL-08]

# Metrics
duration: 7min
completed: 2026-08-05
---

# Phase 3 Plan 1: Release Assembly Storage & Domain Foundation Summary

**V3 Flyway migration plus the domain/release package, ReleaseRepository port, and three no-Lombok JPA entities that every later plan in Phase 3 codes against**

## Performance

- **Duration:** 7 min
- **Started:** 2026-08-05T13:12:00+03:00
- **Completed:** 2026-08-05T13:19:00+03:00
- **Tasks:** 3
- **Files modified:** 17

## Accomplishments
- V3 migration creates `release`, `release_ticket_item`, `release_manual_item` with `UNIQUE (name, version)`, `UNIQUE (release_id, ticket_key)`, and CHECK constraints locking `status` and `category` to their fixed value sets — no position/order column, no sprint column, no application FK
- `domain/release` package (10 files) with zero framework imports: `Release` aggregate funnels every mutation through `requireOpen()`; `ReleaseItem` wraps `TicketDetail` whole so the two records can never drift
- `ReleaseRepository` port declares the complete 12-method persistence contract (create, findById, search, findTicketKeys, addItems, removeItem, replaceItemSnapshots, freeze, delete, addManualItem, updateManualItem, removeManualItem) that plan 03-02 implements
- `JsonStringListConverter` round-trips `List<String>` through Jackson 3's `tools.jackson.databind.ObjectMapper`, applied to `fix_versions`/`comments` on `ReleaseTicketItemEntity`; three entities use hand-written accessors, no Lombok, no `@OneToMany`/`@ManyToOne`

## Task Commits

Each task was committed atomically:

1. **Task 1: Write the V3 Flyway migration** - `e0d3638` (feat)
2. **Task 2: Create the domain/release package and the ReleaseRepository port** - `04724ce` (feat)
3. **Task 3: Create the three JPA entities and the JSON list converter** - `a3b4c40` (feat)

_Note: SUMMARY commit follows below (worktree mode — STATE.md/ROADMAP.md updated by orchestrator, not this commit)._

## Files Created/Modified
- `src/main/resources/db/migration/V3__release_assembly.sql` - three tables, uniqueness and CHECK constraints for the Release aggregate
- `src/main/java/com/evision/releasemgmt/domain/release/Release.java` - aggregate record with `create()` factory and `requireOpen()` guard
- `src/main/java/com/evision/releasemgmt/domain/release/ReleaseItem.java` - wraps `TicketDetail` plus `addedAt`
- `src/main/java/com/evision/releasemgmt/domain/release/ManualItem.java` - title/description/category manual line item
- `src/main/java/com/evision/releasemgmt/domain/release/ReleaseStatus.java` - `OPEN`/`FROZEN` only, one-way door
- `src/main/java/com/evision/releasemgmt/domain/release/ManualItemCategory.java` - four fixed categories
- `src/main/java/com/evision/releasemgmt/domain/release/ReleaseSummary.java` - list/search row shape with item count
- `src/main/java/com/evision/releasemgmt/domain/release/AddTicketsResult.java` - reloaded release plus added/alreadyPresent counts
- `src/main/java/com/evision/releasemgmt/domain/release/ReleaseNotFoundException.java`, `ReleaseFrozenException.java`, `ReleaseDuplicateException.java` - one exception per failure mode, no shared reason enum
- `src/main/java/com/evision/releasemgmt/ports/ReleaseRepository.java` - the full persistence contract for the phase
- `src/main/java/com/evision/releasemgmt/adapters/persistence/converter/JsonStringListConverter.java` - `List<String>` <-> JSON text, never returns null
- `src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseEntity.java`, `ReleaseTicketItemEntity.java`, `ReleaseManualItemEntity.java` - JPA entities matching V3 exactly
- `src/test/java/com/evision/releasemgmt/adapters/persistence/converter/JsonStringListConverterTest.java` - 7 tests covering null/empty/round-trip incl. quotes, newlines, non-ASCII

## Decisions Made
None - plan executed exactly as specified.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Plan 03-02 can implement `ReleaseRepository` against the entities and converter built here
- `ArchitectureBoundaryTest` confirms `domain/release` and `ports` respect the hexagonal boundary (zero framework imports, zero dependency on adapters/config/application)
- Full `mvn clean test` suite green: 228 tests, 0 failures, 0 errors (includes the 7 new `JsonStringListConverterTest` cases plus all Phase 1-2 tests unaffected by the new schema/domain additions)

---
*Phase: 03-release-assembly*
*Completed: 2026-08-05*

## Self-Check: PASSED

All key created files verified present on disk (V3 migration, domain/release package, ReleaseRepository port, JsonStringListConverter, three JPA entities, converter test, this SUMMARY). All four task/summary commits (e0d3638, 04724ce, a3b4c40, 7a2b6e3) verified present in git log.
