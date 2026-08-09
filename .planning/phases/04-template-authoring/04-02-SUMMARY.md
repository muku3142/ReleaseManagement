---
phase: 04-template-authoring
plan: 02
subsystem: database
tags: [postgres, flyway, jpa, hibernate, spring-data, hexagonal]

# Dependency graph
requires:
  - phase: 03-release-assembly
    provides: "the Release/ReleaseRepository/ReleaseRepositoryAdapter three-layer shape this plan copies"
provides:
  - "V4 Flyway migration: template and template_logo tables"
  - "Template domain aggregate, ChromeFields, LogoMetadata, LogoImage records"
  - "TemplateRepository port with 10 methods (create/find/update/setActive/delete/logo CRUD)"
  - "TemplateRepositoryAdapter: JPA implementation enforcing one-active-per-type at the database level"
affects: [04-03, 04-04, 04-05, phase-05-document-generation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Partial unique index (CREATE UNIQUE INDEX ... WHERE active) for a database-enforced one-active-per-type invariant, first use of this syntax in the codebase"
    - "Logo bytes kept out of every list/read path via a separate JPA entity with no association and a JPQL constructor-expression projection for metadata-only reads"

key-files:
  created:
    - src/main/resources/db/migration/V4__template_authoring.sql
    - src/main/java/com/evision/releasemgmt/domain/template/Template.java
    - src/main/java/com/evision/releasemgmt/domain/template/ChromeFields.java
    - src/main/java/com/evision/releasemgmt/domain/template/LogoMetadata.java
    - src/main/java/com/evision/releasemgmt/domain/template/LogoImage.java
    - src/main/java/com/evision/releasemgmt/domain/template/TemplateDocumentType.java
    - src/main/java/com/evision/releasemgmt/domain/template/TemplateNotFoundException.java
    - src/main/java/com/evision/releasemgmt/domain/template/TemplateDuplicateException.java
    - src/main/java/com/evision/releasemgmt/ports/TemplateRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/TemplateEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/TemplateLogoEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/TemplateJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/TemplateLogoJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/TemplateRepositoryAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/TemplatePersistenceIT.java
  modified: []

key-decisions:
  - "setActive deactivates the currently-active row of that document type and flushes before activating the target, avoiding an INSERT/UPDATE ordering conflict with the partial unique index"
  - "TemplateLogoEntity has no JPA association from TemplateEntity; logo metadata is loaded via a JPQL constructor-expression projection (findMetadata) that never selects bytes"

patterns-established:
  - "Partial unique index for a one-active-per-type domain invariant, verified by a raw-SQL negative test in the integration suite rather than trusted to service-layer logic alone"

requirements-completed: [TMPL-01, TMPL-08, TMPL-09]

# Metrics
duration: 8min
completed: 2026-08-09
---

# Phase 4 Plan 2: Template Persistence Summary

**V4 Flyway migration plus the Template aggregate, port and JPA adapter, with one-active-per-document-type enforced by a Postgres partial unique index rather than application code.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-08-09T10:37:00+03:00
- **Completed:** 2026-08-09T10:43:07+03:00
- **Tasks:** 3
- **Files modified:** 15 (14 created, 1 test created)

## Accomplishments
- `V4__template_authoring.sql` creates `template` and `template_logo` with `template_one_active_per_type_idx`, a partial unique index enforcing D-04-12 as a database invariant
- `Template` aggregate and its chrome/logo value types compile as framework-free domain records (`ArchitectureBoundaryTest` passes)
- `TemplateRepository` port and `TemplateRepositoryAdapter` follow the `ReleaseRepository`/`ReleaseRepositoryAdapter` three-layer shape exactly, including `DataIntegrityViolationException` → `TemplateDuplicateException` translation
- `TemplatePersistenceIT` proves the one-active-per-type invariant is enforced by Postgres itself with a direct raw-SQL `UPDATE` that is rejected — not merely by adapter logic

## Task Commits

Each task was committed atomically:

1. **Task 1: Write the V4 migration and the Template domain aggregate** - `25241e2` (feat)
2. **Task 2: Write the TemplateRepository port, JPA entities and repositories** - `3512acc` (feat)
3. **Task 3: Write the persistence adapter and its Testcontainers integration test** - `27f9dbc` (feat)

**Plan metadata:** pending (docs: complete plan, orchestrator commits after all worktree agents in this wave merge)

## Files Created/Modified

- `src/main/resources/db/migration/V4__template_authoring.sql` - `template` and `template_logo` tables, partial unique index
- `src/main/java/com/evision/releasemgmt/domain/template/Template.java` - aggregate record
- `src/main/java/com/evision/releasemgmt/domain/template/ChromeFields.java` - the six TMPL-08 chrome fields
- `src/main/java/com/evision/releasemgmt/domain/template/LogoMetadata.java` - metadata-only logo view, no bytes
- `src/main/java/com/evision/releasemgmt/domain/template/LogoImage.java` - bytes + metadata, upload/download path only
- `src/main/java/com/evision/releasemgmt/domain/template/TemplateDocumentType.java` - RELEASE_NOTES/RUNBOOK/APPROVAL enum
- `src/main/java/com/evision/releasemgmt/domain/template/TemplateNotFoundException.java`, `TemplateDuplicateException.java` - plain no-arg exceptions
- `src/main/java/com/evision/releasemgmt/ports/TemplateRepository.java` - persistence port, 10 methods
- `src/main/java/com/evision/releasemgmt/adapters/persistence/entity/TemplateEntity.java`, `TemplateLogoEntity.java` - JPA entities, no association between them
- `src/main/java/com/evision/releasemgmt/adapters/persistence/repository/TemplateJpaRepository.java`, `TemplateLogoJpaRepository.java` - Spring Data interfaces; `TemplateLogoJpaRepository.findMetadata` is a JPQL constructor-expression projection
- `src/main/java/com/evision/releasemgmt/adapters/persistence/TemplateRepositoryAdapter.java` - JPA implementation of the port
- `src/test/java/com/evision/releasemgmt/adapters/persistence/TemplatePersistenceIT.java` - 8 Testcontainers PostgreSQL 18 tests

## Decisions Made

- Followed the plan's exact SQL, domain record and interface shapes verbatim — no design deviation.
- `setActive` reuses the same JPA-managed `TemplateEntity` instance for the deactivate step when the target template is already the active one (first-level persistence-context cache guarantees this within one transaction), so activating an already-active template is safely idempotent — not separately tested but structurally sound given the flush ordering the plan specified.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Docker was running, JDK 25 was available at `$HOME/.jdks/jdk-25`, and the full `mvn clean test` suite (331 tests across the whole project, including all pre-existing `@SpringBootTest` classes) passed with zero failures/errors after adding `V4` on top of `V1`-`V3`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `TemplateRepository` is ready for plan 04-04 (REST layer) and 04-01's view-model/render work to build on.
- Plan 04-01 is running in parallel in a separate worktree and owns `domain/template/view/`, `PlaceholderCatalog`, `TemplateScanner`, `ScanResult`, `PlaceholderScope`, `PlaceholderKind`, `PlaceholderEntry`, `TemplateMalformedException`, `RenderBoundExceededException` — none of those files were touched here.
- No blockers. `V4` is the only new migration; `V1`-`V3` are untouched.

---
*Phase: 04-template-authoring*
*Completed: 2026-08-09*
