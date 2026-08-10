---
phase: 05-document-generation
plan: 03
subsystem: database
tags: [flyway, postgres, jpa, hibernate, append-only, document-snapshot]

# Dependency graph
requires:
  - phase: 04-template-authoring
    provides: Template aggregate, ChromeFields, LogoImage/LogoMetadata, TemplateDocumentType, the TemplateRepositoryAdapter pattern this plan mirrors
  - phase: 03-release-assembly
    provides: Release aggregate and ReleaseRepository, the release_ticket_item table this plan extends
provides:
  - V5 migration (component column, items_changed_at column, grouping_dimension column, document_snapshot + document_snapshot_logo tables)
  - domain/document/DocumentSnapshot aggregate and its two marker exceptions
  - DocumentSnapshotRepository port - append-only (create plus reads, no update/delete/setActive)
  - DocumentSnapshotRepositoryAdapter, entities and JPA repositories
  - Testcontainers IT proving append-only revisioning and template-deletion survival
affects: [05-04-document-generation-service, 05-08-document-viewer, 05-10-document-readiness]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Append-only aggregate: port exposes create() plus reads only, no update/delete/setActive - mirrors Template's pattern but deliberately inverts the mutation surface"
    - "Template copy, not FK: template_id is a plain UUID with no REFERENCES clause so document_snapshot survives template deletion (D-05-05 + D-04-13)"
    - "Greatest-n-per-group via JPQL correlated subquery (findLatestPerReleaseAndType) - confirmed working under Hibernate 7.4.1, no native DISTINCT ON fallback needed"

key-files:
  created:
    - src/main/resources/db/migration/V5__document_generation.sql
    - src/main/java/com/evision/releasemgmt/domain/document/DocumentSnapshot.java
    - src/main/java/com/evision/releasemgmt/domain/document/DocumentNotFoundException.java
    - src/main/java/com/evision/releasemgmt/domain/document/NoActiveTemplateException.java
    - src/main/java/com/evision/releasemgmt/ports/DocumentSnapshotRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/DocumentSnapshotEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/DocumentSnapshotLogoEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/DocumentSnapshotJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/DocumentSnapshotLogoJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/DocumentSnapshotRepositoryAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/DocumentSnapshotPersistenceIT.java
  modified: []

key-decisions:
  - "Reused TemplateDocumentType directly for DocumentSnapshot.documentType rather than introducing a parallel DocumentType enum (RESEARCH.md Open Question #1, resolved in the plan itself)"
  - "JPQL correlated subquery (not native DISTINCT ON) implements findLatestPerReleaseAndType - it compiled and ran correctly under Hibernate 7.4.1/Postgres 18, so no fallback was needed"

patterns-established:
  - "Append-only repository shape: any future append-only aggregate in this codebase should copy DocumentSnapshotRepository's exact method set (create + reads, zero mutation methods) rather than TemplateRepository's full CRUD shape"

requirements-completed: [DOC-07]

# Metrics
duration: 45min
completed: 2026-08-10
---

# Phase 5 Plan 03: Document Snapshot Schema and Append-Only Aggregate Summary

**V5 migration plus the append-only `DocumentSnapshot` aggregate/port/adapter that every other Phase 5 plan writes to or reads from - template content is copied onto the snapshot at generation time, never referenced.**

## Performance

- **Duration:** 45 min
- **Started:** 2026-08-10T15:58:00Z (approx.)
- **Completed:** 2026-08-10T16:08:15Z
- **Tasks:** 3
- **Files modified:** 11 created, 0 modified

## Accomplishments
- V5 migration lands three column additions (`release_ticket_item.component`, `release.items_changed_at`, `template.grouping_dimension`) and two new tables (`document_snapshot`, `document_snapshot_logo`) in one Flyway version, with no update statement touching any existing row
- `DocumentSnapshot` domain aggregate and `DocumentSnapshotRepository` port ship with zero mutation surface - no `update`, `delete` or `setActive` anywhere on the port or its adapter
- Testcontainers IT (8 tests) proves the two hardest invariants executably: a snapshot's copied template name/body/chrome and its copied logo bytes survive deletion of the source template, and regenerating a document appends a new revision while leaving the prior one byte-for-byte intact
- Full backend suite: **435 tests** (427 baseline + 8 new), all green

## Task Commits

Each task was committed atomically:

1. **Task 1: V5 migration** - `b17eb45` (feat)
2. **Task 2: domain/document aggregate, exceptions and the append-only port** - `b3e0f78` (feat)
3. **Task 3: JPA entities, repositories, adapter and a Testcontainers IT** - `fdfc0af` (feat)

_No plan-metadata commit - orchestrator owns STATE.md/ROADMAP.md writes after all wave agents complete._

## Files Created/Modified
- `src/main/resources/db/migration/V5__document_generation.sql` - three ALTER TABLE statements plus `document_snapshot`/`document_snapshot_logo` CREATE TABLE, with `document_snapshot_revision_unique UNIQUE (release_id, document_type, revision)` and no FK from `document_snapshot` to `template`
- `src/main/java/com/evision/releasemgmt/domain/document/DocumentSnapshot.java` - the append-only record + static factory
- `src/main/java/com/evision/releasemgmt/domain/document/DocumentNotFoundException.java`, `NoActiveTemplateException.java` - marker exceptions, copied verbatim from `TemplateNotFoundException`'s convention
- `src/main/java/com/evision/releasemgmt/ports/DocumentSnapshotRepository.java` - `create(DocumentSnapshot, LogoImage)`, `findLatest`, `findByRevision`, `findAllRevisions`, `findLatestForAllReleases`, `findLogo`, `nextRevision`
- `src/main/java/com/evision/releasemgmt/adapters/persistence/entity/DocumentSnapshotEntity.java`, `DocumentSnapshotLogoEntity.java` - plain getter/setter JPA entities matching `TemplateEntity`'s shape
- `src/main/java/com/evision/releasemgmt/adapters/persistence/repository/DocumentSnapshotJpaRepository.java` - derived queries plus the JPQL correlated-subquery `findLatestPerReleaseAndType`
- `src/main/java/com/evision/releasemgmt/adapters/persistence/repository/DocumentSnapshotLogoJpaRepository.java` - plain `JpaRepository`, no extra methods
- `src/main/java/com/evision/releasemgmt/adapters/persistence/DocumentSnapshotRepositoryAdapter.java` - `@Transactional` `create` with `saveAndFlush` + `DataIntegrityViolationException` translation to `IllegalStateException`; `toEntity`/`toDomain` mapper pair
- `src/test/java/com/evision/releasemgmt/adapters/persistence/DocumentSnapshotPersistenceIT.java` - 8 Testcontainers tests

## Decisions Made
- Reused `TemplateDocumentType` directly rather than adding a parallel `DocumentType` enum - the plan's `<interfaces>` section resolved RESEARCH.md's Open Question #1 explicitly, so this was execution, not a new decision.
- The JPQL correlated subquery for `findLatestPerReleaseAndType` (greatest-n-per-group) compiled and executed correctly under Hibernate 7.4.1 against Postgres 18 - the plan's fallback instruction (native `DISTINCT ON`) was not needed. Recorded here per the plan's own `<output>` requirement, since 05-04/05-08/05-10 code against this method.

## Deviations from Plan

None - plan executed exactly as written. One test-authoring bug was caught and fixed during Task 3 verification (the `snapshotAndItsLogoSurviveDeletingTheSourceTemplate` test's helper initially hardcoded `templateName`/`templateBody` instead of reading them from the actually-created `Template`, causing a spurious assertion failure); this was a bug in the plan's own test code as I wrote it, not a deviation from the plan's instructions, and was fixed inline before the task's commit.

## Issues Encountered
- Same item as above: the IT's `snapshotAndItsLogoSurviveDeletingTheSourceTemplate` test failed on first run because its helper method didn't thread the created `Template`'s actual `name()`/`body()` into the snapshot under test. Fixed by constructing that one test's `DocumentSnapshot` directly from `template.name()`/`template.body()` instead of the shared `snapshot(...)` helper (which uses a fixed literal). Verified green on rerun.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `DocumentSnapshotRepository`'s exact method signatures are stable and ready for 05-04 (generation service), 05-08 (viewer) and 05-10 (readiness) to code against:
  `create(DocumentSnapshot, LogoImage)`, `findLatest(UUID, TemplateDocumentType)`,
  `findByRevision(UUID, TemplateDocumentType, int)`, `findAllRevisions(UUID, TemplateDocumentType)`,
  `findLatestForAllReleases()`, `findLogo(UUID)`, `nextRevision(UUID, TemplateDocumentType)`.
- V5's three column additions (`component`, `items_changed_at`, `grouping_dimension`) are on disk and validated at startup (`ddl-auto: validate`), but **no entity yet maps `release_ticket_item.component` or `release.items_changed_at`** - those land in whichever later plan extends `ReleaseTicketItemEntity`/`ReleaseEntity` (D-05-02/D-05-09 work). This is expected and safe per the plan's own note that `ddl-auto: validate` ignores unmapped columns.
- No blockers for downstream plans.

---
*Phase: 05-document-generation*
*Completed: 2026-08-10*

## Self-Check: PASSED

All 11 created files verified present on disk; all 3 task commits (`b17eb45`, `b3e0f78`, `fdfc0af`) verified present in git history.
