# Phase 7: Promotion Tracking & Audit Trail - Research

**Researched:** 2026-10-12
**Domain:** Append-only promotion ledger + editable single environment ladder on the existing Spring Boot / JPA / Flyway / React stack. No new library is needed.
**Confidence:** HIGH (everything rests on code read in this session plus one Postgres doc check; rule-semantics gaps are listed as Open Questions, not guessed)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
(Copied from `07-CONTEXT.md` `## Implementation Decisions`.)

### Ladder
- **D-07-01: Initial stages are DEV → TEST → UAT → PROD.** They are seeded by migration, and PROD carries the Production flag.
- **D-07-02: The ladder is editable in Settings.** The user chose this over fixing it in code. It stays **one** ladder for the whole instance: this is not PROV-02's per-application ladders. Edits are rename, reorder, add, and retire.
- **D-07-03: Stage ids are permanent; a used stage is retired, never deleted.**
  - A rename changes the label everywhere, history included, because history references the id.
  - A stage that any promotion references can only be **retired**: hidden from new promotions, still shown in history. An unused stage may be deleted.
  - Reordering changes ladder position from then on. Current stage is always computed from the current order, never stored.
- **D-07-04: Exactly one stage is flagged Production.** It starts on PROD. The flag can move to another active stage but can never be absent, and the Production stage cannot be retired while flagged. Phase 8 reads this flag, not a name or the last position (D-08-01).
- **D-07-05: No application dimension.** Releases move through the single ladder individually.

### Promotion rules
- **D-07-06: Only frozen releases can be promoted** (D-08-14, carried forward). Promoting an `OPEN` release is rejected server-side.
- **D-07-07: The target must be the next stage or a redeploy to the current stage.**
  - Skipping stages is rejected server-side.
  - "Next" and "current" are computed from the ladder order at the time of recording, ignoring retired stages.
  - A release with no successful promotion can only target the first active stage.
- **D-07-08: Outcomes are SUCCESS / FAILED / ROLLED_BACK.**
  - A rollback is a new entry on the stage being rolled back, with outcome `ROLLED_BACK`.
  - **Current stage** = the highest stage, by current order, holding a SUCCESS that no later ROLLED_BACK on that stage cancels.
  - FAILED never advances the stage (D-08-02).
  - A rolled-back Production release drops back down the ladder. Its "shipped" date in Phase 8 is still its **first** successful Production promotion (D-08-01), unchanged by later rollbacks.

### Recording and corrections
- **D-07-09: One entry, recorded after the fact.** The release manager records the outcome once it is known: target stage, outcome, optional note (DEPL-04). There is no in-progress or "started" state. This tool records deployments; it never runs them.
- **D-07-10: Two timestamps.**
  - `recorded_at`: server time, immutable.
  - `deployed_at`: user-entered, defaults to now. It may not be in the future, and may not precede the release's `frozen_at`.
  - Phase 8 durations and deployment history use `deployed_at`. The audit view shows both.
- **D-07-11: Corrections supersede; nothing is updated or deleted (AUDIT-03).**
  - A correction is a new entry that references the entry it corrects (`supersedes_id`), carries the corrected values, and requires a reason.
  - The original stays visible with a "superseded by" marker.
  - Current stage, rule checks (D-07-07) and every Phase 8 metric use only **effective** entries, i.e. non-superseded ones.
  - An entry can be superseded at most once; correcting a correction supersedes the correction.
  - The promotion table has **no UPDATE or DELETE path** in code. Enforce append-only at the database too (trigger or revoked privileges are the planner's call). The repository port exposes no update or delete.
- **D-07-12: Any signed-in user may record or correct promotions, and edit the ladder.** No role model exists, and adding one is out of scope. The actor is the accountId (`principal.getName()`) and is displayed via the 05.3 actor resolution (`ActorResolutionService`, batched, never per row).

### Claude's Discretion
- Table shapes: e.g. `environment_stage(id, name, position, is_production, retired_at)` and an append-only `promotion(id, release_id, stage_id, outcome, deployed_at, recorded_at, recorded_by, note, supersedes_id, correction_reason)`.
  - Index for Phase 8's filter on `(stage_id, deployed_at)` (D-08-14).
  - A partial unique index enforces "superseded at most once" (`UNIQUE (supersedes_id) WHERE supersedes_id IS NOT NULL`).
  - Promotions survive release deletion trivially, because only open releases are deletable (A2 in the Phase 8 scan) and those can have no promotions. Use `ON DELETE RESTRICT`.
- REST shape following existing controller conventions, e.g.:
  - `GET/POST /api/releases/{id}/promotions`
  - `POST /api/releases/{id}/promotions/{promotionId}/corrections`
  - `GET/PUT /api/environment-ladder`, or per-stage endpoints
- New ProblemDetail reason codes for: promoting an open release; a skipped stage; a retired target; a correction of an already-superseded entry; a future or pre-freeze `deployed_at`; removing or retiring the Production flag holder.
- UI:
  - the release page shows current stage as stepped dots, a "Record promotion" dialog (target preselected to the next stage), and a history list with a "Correct" action and superseded entries marked;
  - Settings gets a "Environment ladder" section.
  - Reuse the plain-Tailwind patterns. No timeline component (DEPL-06 is v2).
- AUDIT-02: the document revision history already exists (Phase 5; it shows the template name since 05.4). This phase links it into the release's audit view. Do not rebuild it.
- Phase is `**Mode:** mvp` in ROADMAP: plan vertical slices.

### Deferred Ideas (OUT OF SCOPE)
- Paperwork gate (block or warn on promoting with stale or missing documents): offered as a gray area and not selected; candidate for a later phase.
- Named-approver sign-off (DEPL-05), visual timeline (DEPL-06), per-application ladders (PROV-02): all v2.
- An "in progress" deployment state: not chosen (D-07-09).
- Out of scope per Phase Boundary: running deployments or reading CI/CD status, roles/permissions, AUDIT-04 cross-release history (Phase 8).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DEPL-01 | See the environment ladder and which stage each release sits at | Ladder table + pure `PromotionHistory.currentStage()`; computed on read, never stored; returned by `GET /api/releases/{id}/promotions` (Patterns 1, 3) |
| DEPL-02 | Record promotion of a frozen release to the next environment | Domain rule class + `PromotionService.record`; row lock on the release under `FOR UPDATE` (Patterns 2, 4; Pitfalls 1, 2) |
| DEPL-03 | Each promotion is an immutable entry: environment, timestamp, actor, outcome | Append-only `promotion` table, `@Immutable` entity, DB trigger rejecting UPDATE/DELETE (Pattern 2; Pitfall 3) |
| DEPL-04 | Optional free-text note | `note` column, Bean Validation `@Size`, rendered as React text (never HTML) (Security Domain) |
| AUDIT-01 | Complete promotion history of a release | History list returns all entries (effective and superseded, flagged), actors resolved in one batch (Pattern 5) |
| AUDIT-02 | Every generated document incl. superseded revisions | Already shipped (`GET .../documents/{type}/revisions`, `DocumentsSection` revision history). Phase 7 only links it into the audit area; no backend work (Pattern 6) |
| AUDIT-03 | History append-only; correction adds a new entry | `supersedes_id` model + partial unique index + composite FK + trigger (Pattern 2) |
</phase_requirements>

## Summary

Phase 7 is almost entirely additive. The codebase already has every convention it needs: hexagonal layout enforced by `ArchitectureBoundaryTest` (domain/ports free of framework imports, application not depending on adapters), JPA entities behind a `*RepositoryAdapter`, Flyway SQL-first migrations with header comments citing decision ids, a single `GlobalExceptionHandler` that maps domain exceptions to `ProblemDetail` with a `reason` the SPA keys on, `ActorResolutionService.resolve(String...)` for batched actor display, and `ReleaseJpaRepository.findByIdForUpdate` for the row-lock-inside-the-transaction idiom. No new dependency is required. [VERIFIED: codebase grep]

The one non-obvious engineering choice is append-only enforcement. The application connects to Postgres as the same role that Flyway migrates with (`RM_DB_USER`, `application.yml`), and that role owns the tables, so "revoked privileges" would not bind it. A row-level `BEFORE UPDATE OR DELETE` trigger that raises an exception is the enforceable option. Do **not** add a `BEFORE TRUNCATE` trigger: existing integration tests clean up with `TRUNCATE release CASCADE`, which will cascade into `promotion` once it has an FK to `release`, and a TRUNCATE trigger would break every such test. Postgres row-level triggers do not fire on TRUNCATE. [CITED: postgresql.org/docs/current/sql-createtrigger.html] [VERIFIED: `DocumentSnapshotPersistenceIT.clearAll`]

The rest is domain logic: effective entries, current stage, next stage and target validation are pure functions over (ordered stages, effective entries). Put them in one pure domain class so every D-07-07/08 property is a plain unit test and the service/adapters stay thin. Phase 7 should avoid touching Phase 6's hot files: all new backend files, a new `api/promotions.ts`, and two small mount points in the frontend (see Merge-Conflict Map). Several semantic gaps in CONTEXT.md need a user call before planning is final; see Open Questions (notably what "later" means for rollback ordering, and whether a mistaken entry can ever be voided).

**Primary recommendation:** New `domain/promotion` package with a pure `PromotionHistory` rule class; two ports (`EnvironmentLadderRepository`, `PromotionRepository`) with JPA adapters; one migration `V10__environment_ladder_promotion.sql` (confirm the number at planning time) with a row-level append-only trigger; two controllers; no changes to `Release`, `ReleaseService`, `ReleaseRepositoryAdapter` or `ReleaseResponse`.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Ladder definition and edits (rename/reorder/add/retire/delete, Production flag) | API / Backend (service + domain invariants) | Database (unique/partial indexes, deferrable position constraint) | Invariants (exactly one Production, used stage never deleted) must hold regardless of client; DB constraints are the backstop |
| Promotion rules (frozen-only, next-or-redeploy, no skip, retired target) | API / Backend (pure domain class) | — | D-07-06/07 say server-side; the browser only preselects a default |
| Current stage computation | API / Backend (domain, on read) | Browser (displays) | D-07-03 forbids storing it; ordering changes must re-derive it |
| Append-only guarantee | Database (trigger) | API (no update/delete in port, `@Immutable`) | Defence in depth; the DB is the only layer that survives a future code path that forgets |
| Superseded-once guarantee | Database (partial unique index) | API (maps violation to 409) | Race between two concurrent corrections can only be settled by the index |
| Actor identity on entries | API (`principal.getName()`) | — | Never from the request body (same rule as 05.4) |
| Actor display | API (`ActorResolutionService`, batched) | — | 05.3 rule; display path must not touch the credential port (ArchUnit-enforced) |
| Record-promotion dialog, history list, ladder editor | Browser / Client | — | Plain React + Tailwind, TanStack Query |
| Document revision history (AUDIT-02) | Already built (Phase 5) | Browser (link/anchor) | Do not rebuild |
| Timestamp parsing of `deployed_at` | Browser (local time to ISO instant) | API (validates instant) | `datetime-local` has no zone; conversion must happen client-side |

## Standard Stack

No additions. Everything is already declared in `pom.xml` / `frontend/package.json`. [VERIFIED: codebase]

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot (Web, Data JPA, Validation) | 4.1.0 (project BOM) | Controllers, entities, bean validation | Already the project baseline |
| Flyway + `flyway-database-postgresql` | Boot-managed | `V10__...sql` incl. seed rows, trigger, indexes | SQL-first; migrations are the only schema path (`ddl-auto: validate`) |
| PostgreSQL | 18 (tests use `postgres:18` Testcontainers) | Partial unique indexes, deferrable constraints, triggers | All needed features are core Postgres; `gen_random_uuid()` is built in |
| React 19.2.8, TanStack Query 5.101.4, TanStack Router 1.170.18, Tailwind 4 | as pinned | UI | Existing patterns; no new component library |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Testcontainers `postgres:18` + `@ServiceConnection` | 2.0.5 | Append-only, constraint and migration ITs | Every DB-level property in "Specifics" |
| ArchUnit | existing | Keep new classes inside boundary rules | Extend only if a new rule is wanted (see Validation) |
| Vitest + jsdom | ^4 | Frontend component tests | Dialog, history list, ladder editor |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Row-level trigger | Revoked UPDATE/DELETE privileges | Does not bind: app role owns the tables and runs Flyway. Would need a second role plus Flyway config split. Reject |
| Row-level trigger | Hibernate `@Immutable` only | Code-level only; any native SQL/JdbcTemplate path bypasses it. Use as the second layer, not the only one |
| Computed "effective" via `NOT EXISTS` | Stored `superseded_by` column | A stored column needs an UPDATE on the original row, violating append-only. Reject |
| Hand-built stage state machine | Any workflow/state-machine library | Over-engineering: the rule is ~30 lines of pure Java |

**Installation:** none.

**Version verification:** versions are the project's already-pinned ones (CLAUDE.md, `pom.xml`, `package.json` read this session). No registry lookup needed because nothing new is added. Local note: the machine JDK is 17 (`java -version`); project memory says set `JAVA_HOME` per Maven call.

## Architecture Patterns

### System Architecture Diagram

```
Browser (ReleaseDetailRoute > PromotionSection)         Browser (SettingsRoute > EnvironmentLadderSection)
   |  GET  /api/releases/{id}/promotions                    |  GET/PUT /api/environment-ladder (+ stage endpoints)
   |  POST /api/releases/{id}/promotions                    |
   |  POST /api/releases/{id}/promotions/{pid}/corrections  |
   v                                                        v
PromotionController  --principal.getName()-->  EnvironmentLadderController
   |                                                        |
   v                                                        v
PromotionService (@Transactional boundary via adapter)   EnvironmentLadderService
   |  1. lock release row FOR UPDATE; read status+frozen_at   |  validates: one Production, no retire of Production,
   |  2. load ordered stages + effective entries              |  delete only if unreferenced
   |  3. PromotionHistory (PURE domain) -> validate target,   v
   |     deployed_at bounds, supersede-once              EnvironmentLadderRepository (port)
   |  4. append row (INSERT only)                             |
   v                                                        v
PromotionRepository (port) -------------------> JPA adapters (adapters/persistence)
                                                        |
                                                        v
                         PostgreSQL: environment_stage, promotion
                         - BEFORE UPDATE OR DELETE trigger on promotion -> RAISE EXCEPTION
                         - UNIQUE (supersedes_id) WHERE NOT NULL
                         - UNIQUE (is_production) WHERE is_production
                         - FK promotion.release_id ON DELETE RESTRICT, promotion.stage_id RESTRICT

ActorResolutionService.resolve(recorded_by...) -- once per response --> display names
Documents: existing GET .../documents/{type}/revisions (unchanged) <- linked from the audit area
```

### Recommended Project Structure
```
src/main/java/com/evision/releasemgmt/
├── domain/promotion/         # Stage, Promotion, PromotionOutcome, PromotionHistory (pure), reason exceptions
├── ports/                    # EnvironmentLadderRepository, PromotionRepository
├── application/              # PromotionService, EnvironmentLadderService
├── adapters/persistence/     # EnvironmentLadderRepositoryAdapter, PromotionRepositoryAdapter (+ entity/, repository/)
└── adapters/web/             # PromotionController, EnvironmentLadderController (+ dto/)
src/main/resources/db/migration/V10__environment_ladder_promotion.sql   # number: next free after Phase 6's V8/V9
frontend/src/api/promotions.ts, ladder.ts
frontend/src/components/PromotionSection.tsx, RecordPromotionDialog.tsx, EnvironmentLadderSection.tsx
```

### Pattern 1: Pure rule class (the single place D-07-07/08 live)
**What:** `PromotionHistory(List<Stage> orderedStages, List<Promotion> effectiveEntries)` exposes `currentStage()`, `nextStage()`, `requireValidTarget(stageId)`. No Spring, no clock, no I/O; satisfies `domainAndPortsHaveNoFrameworkImports`.
**When to use:** Always; the service only loads data, calls it, and appends.
**Example:**
```java
// Source: derived from 07-CONTEXT D-07-07/D-07-08 (project design, not a library API)
public Optional<Stage> currentStage() {
    return activeOrRetiredStagesDescending().stream()          // by CURRENT position, highest first
            .filter(s -> hasStandingSuccess(s.id()))
            .findFirst();
}
private boolean hasStandingSuccess(UUID stageId) {
    // latest SUCCESS on the stage not cancelled by a ROLLED_BACK ordered after it (see Open Question 1)
}
public Optional<Stage> nextStage() {
    int from = currentStage().map(Stage::position).orElse(Integer.MIN_VALUE);
    return stages.stream().filter(s -> !s.retired() && s.position() > from)
            .min(comparingInt(Stage::position));
}
public void requireValidTarget(UUID target) {
    Stage t = stage(target);
    if (t.retired()) throw new PromotionTargetRetiredException();
    boolean isNext = nextStage().filter(n -> n.id().equals(target)).isPresent();
    boolean isRedeploy = currentStage().filter(c -> c.id().equals(target)).isPresent();
    if (!isNext && !isRedeploy) throw new PromotionStageSkippedException();
}
```
Note: a release currently sitting on a retired stage can only go to the next active stage (redeploy-current is a retired target, rejected per Specifics). Retired stages still count in "current" (history), they only disappear as targets.

### Pattern 2: Append-only table, enforced in three layers
**What:** (1) DB trigger, (2) `@Immutable` entity + port with only `append`/`find*`, (3) tests that prove (1).
**Example:**
```sql
-- Source: PostgreSQL docs, CREATE TRIGGER (row-level BEFORE UPDATE OR DELETE)
CREATE FUNCTION promotion_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'promotion is append-only (AUDIT-03)' USING ERRCODE = 'integrity_constraint_violation';
END $$;
CREATE TRIGGER promotion_no_update_delete
    BEFORE UPDATE OR DELETE ON promotion
    FOR EACH ROW EXECUTE FUNCTION promotion_append_only();
-- Deliberately NO BEFORE TRUNCATE trigger: tests clean up with TRUNCATE release CASCADE (Pitfall 3).
```
Suggested table (CONTEXT lists the columns; these are the constraints to add):
```sql
CREATE TABLE environment_stage (
    id            UUID PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    position      INTEGER      NOT NULL,
    is_production BOOLEAN      NOT NULL DEFAULT FALSE,
    retired_at    TIMESTAMPTZ,
    CONSTRAINT environment_stage_position_unique UNIQUE (position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT environment_stage_production_not_retired CHECK (NOT (is_production AND retired_at IS NOT NULL))
);
CREATE UNIQUE INDEX environment_stage_name_unique ON environment_stage (lower(name));
CREATE UNIQUE INDEX environment_stage_one_production ON environment_stage (is_production) WHERE is_production;

CREATE TABLE promotion (
    id                UUID PRIMARY KEY,
    release_id        UUID         NOT NULL REFERENCES release (id) ON DELETE RESTRICT,
    stage_id          UUID         NOT NULL REFERENCES environment_stage (id) ON DELETE RESTRICT,
    outcome           VARCHAR(16)  NOT NULL CHECK (outcome IN ('SUCCESS','FAILED','ROLLED_BACK')),
    deployed_at       TIMESTAMPTZ  NOT NULL,
    recorded_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    recorded_by       VARCHAR(255) NOT NULL,
    note              VARCHAR(2000),
    supersedes_id     UUID,
    correction_reason VARCHAR(2000),
    CONSTRAINT promotion_id_release_unique UNIQUE (id, release_id),
    CONSTRAINT promotion_supersedes_same_release
        FOREIGN KEY (supersedes_id, release_id) REFERENCES promotion (id, release_id),
    CONSTRAINT promotion_correction_has_reason
        CHECK ((supersedes_id IS NULL) = (correction_reason IS NULL))
);
CREATE UNIQUE INDEX promotion_superseded_once ON promotion (supersedes_id) WHERE supersedes_id IS NOT NULL;
CREATE INDEX promotion_stage_deployed_idx ON promotion (stage_id, deployed_at);   -- D-08-14
CREATE INDEX promotion_release_deployed_idx ON promotion (release_id, deployed_at);
```
Notes: the "exactly one Production, never absent" rule: the partial unique index gives at-most-one, the CHECK blocks retiring the flagged stage, and the service owns "move the flag" as one atomic swap (clear old, set new, same transaction; the non-deferred partial unique index is satisfied if the clear is flushed first). "At least one" cannot be a plain constraint; the API simply exposes no operation that clears the flag without setting another, and a test asserts that. Seed `DEV, TEST, UAT, PROD` with `gen_random_uuid()` positions 1..4 and `is_production = TRUE` on PROD. Ladder names are case-insensitively unique across retired stages too, so a history label is never ambiguous (confirm: Open Question 4).

### Pattern 3: Lock-then-validate-then-append in one transaction
**What:** `PromotionRepositoryAdapter` method (single `@Transactional`) does `releaseJpa.findByIdForUpdate(releaseId)` (existing method), checks `FROZEN` and `frozenAt`, loads effective entries, and inserts. This mirrors `ReleaseRepositoryAdapter.lockOpen` and uses the same "the check that actually holds" reasoning documented there. Lock the **release row**, not a new lock table: two simultaneous records for one release serialise, so both cannot pass "next stage".
**Detail:** because the domain rule class needs the entries and stages, structure the port so the lock, the loads and the insert share one transaction, e.g. `PromotionRepository.appendUnderLock(releaseId, Function<PromotionContext, Promotion> decide)` or have the application service carry `@Transactional` (application already may use Spring; `ReleaseService` has none, adapters carry it). Pick one; do not validate outside the lock.

### Pattern 4: Corrections validate fields, not ladder sequence (recommended; needs user confirm)
A correction is `Promotion` with `supersedes_id`, new stage/outcome/`deployed_at`/note and a mandatory reason. Validate: original exists on this release, is effective (else `PROMOTION_ALREADY_SUPERSEDED`), target stage is not retired unless unchanged from the original, `deployed_at` bounds (D-07-10). Do not replay the whole ladder sequence on a correction: fixing an early mistake (e.g. DEV recorded as SUCCESS but it FAILED) would otherwise be rejected because later entries "depend" on it. Current stage is recomputed from effective entries either way. See Open Question 2.

### Pattern 5: Batched actors, controller-side mapping
Copy `DocumentController.revisions`: one `actorResolution.resolve(entries.stream().map(Promotion::recordedBy).toArray(String[]::new))`, then map with `actors.display(...)`. Keep the mapper `static`/pure-over-`ResolvedActors` as `ReleaseController.toSummaryResponse` does so a row cannot resolve for itself. Controllers must not depend on `JiraCredentialRepository` (ArchUnit rule already enforces).

### Pattern 6: AUDIT-02 is a link, not a feature
`DocumentsSection` (rendered at the bottom of `ReleaseDetailRoute.tsx`, line ~599) already lists every revision, with the template name. Put `PromotionSection` directly above it under one "History" heading and give `DocumentsSection` an `id="documents"` so the history area can carry an in-page link ("Document revisions"). Cover with one frontend test; no backend change.

### Pattern 7: REST shape (follows ReleaseController conventions)
- `GET /api/releases/{id}/promotions` returns `{ stages:[{id,name,position,production,retired}], currentStageId, nextStageId, entries:[{id,stageId,outcome,deployedAt,recordedAt,recordedBy(display),note,supersedesId,supersededById,correctionReason,effective}] }`. One call renders the stepped dots, the dialog default and the history (no N+1, no second ladder fetch).
- `POST /api/releases/{id}/promotions` body `{stageId, outcome, deployedAt?, note?}`; `201` with the same response shape as GET (existing controllers return the refreshed aggregate).
- `POST /api/releases/{id}/promotions/{promotionId}/corrections` body `{stageId, outcome, deployedAt, note?, reason}`.
- `GET /api/environment-ladder`; mutations: `POST /stages` (add), `PUT /stages/{id}` (rename), `PUT /order` (ordered id list, one atomic reorder), `POST /stages/{id}/retire`, `PUT /production-stage` (body `{stageId}`), `DELETE /stages/{id}` (unused only). Per-stage endpoints keep each invariant check small; a single whole-ladder PUT would force diffing.
- Reason codes (new, all `ProblemDetail` with `reason`, 409 unless noted): `RELEASE_NOT_FROZEN`, `PROMOTION_STAGE_SKIPPED`, `PROMOTION_TARGET_RETIRED`, `PROMOTION_ALREADY_SUPERSEDED`, `PROMOTION_DEPLOYED_AT_INVALID` (422), `PROMOTION_NOT_FOUND` (404), `STAGE_NOT_FOUND` (404), `STAGE_IN_USE` (delete of a referenced stage), `STAGE_NAME_DUPLICATE`, `PRODUCTION_STAGE_PROTECTED` (retire/delete flag holder). Existing `RELEASE_NOT_FOUND` is reused.

### Anti-Patterns to Avoid
- **Storing `current_stage` anywhere** (D-07-03, and Phase 8 constraint).
- **`save()` on an update path:** the port has `append` only; no `update`, no `delete`, no `saveAll` of loaded entities.
- **Validating outside the release row lock** (race on "next stage").
- **Using `ReleaseRepository.findById` to read status/frozen_at:** it materialises every ticket and manual item, and Phase 6 (06-03) adds commits, PRs and status-change child rows to that load. Read the two columns through the promotion adapter using the existing `ReleaseJpaRepository.findByIdForUpdate`.
- **Resolving actors per row** (05.3 rule) or from the credential port.
- **Deriving Production from name or last position** (D-07-04/D-08-01).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Append-only enforcement | App-level guards only | Postgres row trigger + `@Immutable` | Only the DB survives future code paths |
| Superseded-at-most-once | Check-then-insert in Java | `UNIQUE (supersedes_id) WHERE NOT NULL` + map `DataIntegrityViolationException` to 409 (same trick as `ReleaseDuplicateException`, use `saveAndFlush`) | Two concurrent corrections race |
| Correction belongs to same release | Service check | Composite FK `(supersedes_id, release_id)` | One-line DB invariant |
| One Production stage | Service loop | Partial unique index + CHECK | At-most-one is a DB fact |
| Position reordering uniqueness | Temp negative positions | `DEFERRABLE INITIALLY DEFERRED` unique constraint | Swaps pass at commit |
| UUID generation in seed | App seeder | `gen_random_uuid()` in the migration | D-07-01 says migration; built in on PG 13+ |
| Actor display | New lookup | `ActorResolutionService` | 05.3 contract |
| ProblemDetail mapping | New error format | `GlobalExceptionHandler.problem(...)` | SPA keys on `reason` |
| Date/time input | Custom picker | native `datetime-local` + `new Date(v).toISOString()` | Standard; no new dependency |

**Key insight:** the domain is small and the dangerous parts (ordering of concurrent writes, immutability, uniqueness) are exactly what Postgres already provides. Spend code on the pure rule class and tests, not on infrastructure.

## Merge-Conflict Map against Phase 6 (planned, unexecuted)

Phase 6 owns `V8__ado_credential.sql` (06-02) and `V9__ado_enrichment.sql` (06-03); no Phase 6 plan claims V10 or higher. [VERIFIED: grep of 06-0*-PLAN.md] Phase 7 takes the next free number at planning/execution time (expected V10); if Phase 6 slips and Phase 7 is executed first, renumber, never hard-code.

| File | Phase 6 touches (plan) | Phase 7 plan | Risk / mitigation |
|------|------------------------|--------------|-------------------|
| `ReleaseService`, `ReleaseRepository`, `ReleaseRepositoryAdapter`, `Release*Response`, `ReleaseController` | 06-03, 06-05 modify | **Do not touch** (promotion adapter reads release columns via existing `ReleaseJpaRepository`) | None if respected |
| `GlobalExceptionHandler` | 06-04 adds handlers | Adds handlers | Trivial textual conflict (both append); sequential execution resolves |
| `ArchitectureBoundaryTest` | 06-04 edits | Optional new rule | Same; keep additions at end of class |
| `SettingsRoute.tsx` | 06-04 adds ADO section | Mount `<EnvironmentLadderSection />` only | Keep Phase 7's JSX to one line plus an import; component lives in its own file |
| `ReleaseDetailRoute.tsx` | 06-07 edits heavily (items table, freeze dialog, `useRefreshCodeLinks`) | Mount `<PromotionSection releaseId isFrozen />` once, above `<DocumentsSection>` | Highest-risk file; one import + one JSX line; read the file fresh at execution (06-07 itself warns line numbers move) |
| `frontend/tests/components/ReleaseDetailRoute.test.tsx` | 06-07 adds fixtures | Prefer a new `PromotionSection.test.tsx`; the detail-route test only needs an `apiFetch` mock extended for the new GET | Mock harness must tolerate the extra request (Pitfall 7) |
| `DocumentsSection.tsx` | untouched | Add `id="documents"` | Safe |
| `router.tsx`, `AppShell.tsx` | untouched | untouched (ladder is a Settings section, audit lives on the release page) | None |

## Runtime State Inventory

Not a rename/refactor phase; greenfield tables. One item worth stating: the migration **seeds** `environment_stage` rows. Existing ITs clean with `TRUNCATE release CASCADE`/`TRUNCATE template CASCADE`; none truncates `environment_stage`, so seed rows survive, but any new IT that edits the ladder must restore it (or truncate and re-seed in `@AfterEach`) and must never `TRUNCATE environment_stage CASCADE` without re-inserting. Nothing else to migrate: no existing release has promotions.

## Common Pitfalls

### Pitfall 1: "Next stage" race
**What goes wrong:** two requests both read "current = TEST", both pass "target = UAT", both insert.
**Why:** validation outside the lock. **Avoid:** Pattern 3 (row lock on `release` inside the transaction). **Warning sign:** a concurrency IT that fires two POSTs and gets two 201s.

### Pitfall 2: Ambiguous "later ROLLED_BACK"
**What goes wrong:** D-07-08 says a ROLLED_BACK "later" than a SUCCESS cancels it. Entries are recorded after the fact, so `recorded_at` order and `deployed_at` order can differ. Different orderings give different current stages. **Avoid:** pick one ordering and test it (recommended: `deployed_at`, tie-broken by `recorded_at`, because Phase 8 treats `deployed_at` as truth). Open Question 1.

### Pitfall 3: Append-only trigger vs `TRUNCATE`
**What goes wrong:** a `BEFORE TRUNCATE` trigger (or a naive "block everything") breaks every IT's `TRUNCATE release CASCADE` cleanup, because the FK pulls `promotion` into the truncate. **Avoid:** row-level UPDATE/DELETE trigger only. Row triggers do not fire on TRUNCATE. [CITED: postgresql.org/docs/current/sql-createtrigger.html] Accepting that TRUNCATE (an operator/test action) is outside the application's threat model is a deliberate trade-off; document it in the migration header.

### Pitfall 4: JPA `save` on an assigned id silently becomes `merge`
**What goes wrong:** Spring Data treats an entity with a non-null assigned id (no `@Version`) as not-new and does `SELECT` then `INSERT` (merge). Harmless for inserts but it would also happily issue an UPDATE for a loaded entity that is dirtied, which the trigger then rejects at flush as a 500. **Avoid:** `@Immutable` on `PromotionEntity`, never load-then-modify, use `saveAndFlush` on the insert so a constraint violation (superseded-once) fires inside the adapter's try/catch, not at commit. [VERIFIED: same pattern in `ReleaseRepositoryAdapter.create`]

### Pitfall 5: Reorder collides on `UNIQUE (position)`
**What goes wrong:** swapping two positions row by row violates a non-deferred unique constraint. **Avoid:** `DEFERRABLE INITIALLY DEFERRED` and a single atomic `PUT /order` that rewrites positions 1..n in one transaction. Retired stages keep a position (they stay in history order); decide that explicitly (Open Question 4 covers position of retired stages).

### Pitfall 6: Time zones and "future" checks
**What goes wrong:** `datetime-local` yields `2026-10-12T14:30` with no zone; sent raw it parses on the server as an arbitrary zone. A browser clock a few seconds ahead of the server also makes a default "now" arrive in the future and be rejected. **Avoid:** convert with `new Date(value).toISOString()` in the client; when the user does not edit the field send no `deployedAt` and let the server default it to its own `now`; compare against server `Instant.now()` with a small explicit skew allowance (Assumption A2). Inject a `java.time.Clock` bean into `PromotionService` so the bounds are unit-testable (no `Clock` bean exists today; `ReleaseService` calls `Instant.now()` directly, so this is the one deliberate deviation, justified by testability of D-07-10).

### Pitfall 7: Release page tests break on the extra request
`ReleaseDetailRoute.test.tsx` mocks `apiFetch`/fetch per URL. Mounting `PromotionSection` adds `GET /api/releases/{id}/promotions`; unmocked, it returns undefined and the section throws. Give `PromotionSection` its own query and make the detail-route test's mock return an empty-ladder response for it (or mock the component).

### Pitfall 8: Orphaned release id / delete interplay
Only OPEN releases are deletable (`ReleaseService.delete`, `lockOpen`) and only FROZEN releases can have promotions, so `ON DELETE RESTRICT` is never exercised by the app. Keep it anyway (CONTEXT) and add one IT asserting the FK rejects deleting a promoted release at SQL level.

### Pitfall 9: Retired-stage UX gap
A stage retired while releases sit on it leaves them unable to redeploy there (rejected by Specifics). That is intended, but the UI must say why (disabled option with "retired"), not just omit it.

## Code Examples

### Port shape (no update, no delete)
```java
// Source: project convention (ports/*.java); constraint from D-07-11
public interface PromotionRepository {
    /** Inserts only. @throws PromotionAlreadySupersededException on the supersedes_id unique violation */
    Promotion append(Promotion promotion);
    List<Promotion> findByRelease(UUID releaseId);          // all entries, oldest first
    Optional<ReleaseFreezeState> findFreezeStateForUpdate(UUID releaseId); // status + frozenAt, row-locked
}
```
(An optional ArchUnit/reflection test asserts no method name in this port starts with `update`, `delete`, `remove` or `save`; "assert properties, not counts" per project memory.)

### Effective entries in SQL (Phase 8 will reuse this definition)
```sql
-- Source: D-07-11 (effective = not superseded)
SELECT p.* FROM promotion p
 WHERE NOT EXISTS (SELECT 1 FROM promotion s WHERE s.supersedes_id = p.id);
```
Served by `promotion_superseded_once` (index on `supersedes_id`). Do not create a SQL view now; Phase 8 can add one if it wants.

### Append-only IT (property, not grep)
```java
// Source: pattern of DocumentSnapshotPersistenceIT (Testcontainers postgres:18, JdbcTemplate)
assertThatThrownBy(() -> jdbc.update("UPDATE promotion SET note = 'x' WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class);
assertThatThrownBy(() -> jdbc.update("DELETE FROM promotion WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class);
```

### Controller mapping, batched actors
```java
// Source: DocumentController.revisions / ReleaseController.list (existing code)
ResolvedActors actors = actorResolution.resolve(
        entries.stream().map(Promotion::recordedBy).toArray(String[]::new));
```

### Frontend deployedAt conversion
```ts
// Source: standard Date API; datetime-local carries no zone
const deployedAt = edited ? new Date(inputValue).toISOString() : undefined;
```

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Stored `current_stage` column | Derived from append-only ledger + ladder order | Required by D-07-03 / Phase 8; rollbacks and reorders stay correct |
| Soft-delete flag + UPDATE for history | Insert-only supersede chain | Enables DB-enforced immutability |

**Deprecated/outdated:** none relevant; nothing in this phase depends on a fast-moving library.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The app DB role owns the tables and runs Flyway, so revoking privileges would not bind it (inferred from a single `RM_DB_USER` in `application.yml`; production role setup not visible) | Pattern 2, Alternatives | If production uses split roles the trigger is still correct, just redundant with grants. Low |
| A2 | A small clock-skew allowance (suggest 1 to 5 minutes) on the "not in the future" check is acceptable | Pitfall 6 | A strict zero tolerance causes spurious rejections for users with fast clocks; needs user OK on the number |
| A3 | `deployed_at` ordering (tie-break `recorded_at`) is the right meaning of "later" for rollback cancellation | Pitfall 2, Pattern 1 | Wrong ordering gives wrong current stage; Open Question 1 |
| A4 | Corrections validate field bounds only, not full ladder sequence | Pattern 4 | If the user wants sequence replay, correcting early history becomes impossible without a cascade; Open Question 2 |
| A5 | Stage names are unique case-insensitively across active and retired stages | Pattern 2 | A user may expect to reuse a retired name; Open Question 4 |
| A6 | No SQL view for effective entries in this phase | Code Examples | Phase 8 may want one; trivial to add later |
| A7 | Posting/recording does not require the release to be unmodified since documents were generated (no paperwork gate) | Scope | Matches the deferred idea; stated in CONTEXT, not an assumption beyond that |

## Open Questions

1. **What does "later ROLLED_BACK" mean: later by `deployed_at` or by `recorded_at`?**
   - Known: D-07-08 defines current stage via "no later ROLLED_BACK on that stage"; D-07-10 says Phase 8 uses `deployed_at`; entries are recorded after the fact so the orders can differ.
   - Unclear: which wins when they disagree (e.g. a rollback recorded today for a time before the success it cancels).
   - Recommendation: order by `deployed_at`, tie-break `recorded_at`, and additionally reject a ROLLED_BACK whose `deployed_at` precedes the SUCCESS it targets (or has no standing SUCCESS on that stage). Needs the user's confirmation.

2. **Do corrections have to pass the ladder-sequence rules?**
   - Known: D-07-11 says corrections carry corrected values and a reason; D-07-07 rules apply to "recording".
   - Unclear: whether a correction may produce a history that would have been rejected as a fresh entry (e.g. DEV recorded SUCCESS, TEST SUCCESS exists, DEV corrected to FAILED).
   - Recommendation: allow it (field-level validation only); current stage is derived anyway.

3. **Can an entry be voided (recorded against the wrong release or by mistake)?**
   - Known: no update, no delete, no "void" outcome; a correction must carry valid corrected values.
   - Unclear: a promotion recorded on the wrong release has no correct values to supersede with, and the table can never drop it. It would permanently count as a FAILED/SUCCESS on that release.
   - Recommendation: surface this to the user; options are (a) accept it, correct to FAILED with a note, or (b) add a fourth outcome such as `VOIDED` that is excluded from stage computation and Phase 8 metrics. (b) changes D-07-08's outcome set so it is the user's call.

4. **Ladder edit details.**
   - Do retired stages keep their position and still occupy a slot in ordering? Recommendation: yes (they stay in history order, are skipped for targets).
   - Can a retired stage be un-retired? Recommendation: yes, cheap, since ids are permanent. Not in CONTEXT; confirm or omit.
   - Is a retired name reusable? Recommendation: no (A5).
   - What if the ladder has no active stage left? Recommendation: reject retiring the last active stage.

5. **Does the release list or release header need the current stage in Phase 7?**
   - Known: DEPL-01 says "which stage each release currently sits at"; ROADMAP criterion 1.
   - Unclear: only on the release page, or also in the releases list (which would need a batched query and touches `ReleaseSummary` and Phase 6's files).
   - Recommendation: release page only in Phase 7 (stepped dots); the cross-release stage view is already Phase 8 criterion 2. Confirm.

6. **Is a promotion note length limit acceptable at 2000 characters, and the correction reason mandatory non-blank?** Recommendation: yes; trivial, listed so it is not silently invented.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | Backend build/tests | ✓ | 17.0.16 on PATH (project baseline Java 25; Boot 4.1 supports 17) | Set `JAVA_HOME` per Maven call (project memory) |
| Docker | Testcontainers ITs (`postgres:18`) | ✓ | 29.1.3 | ITs cannot run without it |
| Node | Frontend build/tests | ✓ | 20.19.0 (Vite 8 floor 20.19+) | — |
| PostgreSQL | runtime | via Docker compose / Testcontainers | 18 | — |

**Missing dependencies with no fallback:** none.

## Validation Architecture

> `.planning/config.json` has `workflow.nyquist_validation: false`; this section is included because the orchestrator asked for it explicitly.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter + AssertJ + Mockito, `@WebMvcTest` for controllers, `@SpringBootTest` + Testcontainers `postgres:18` for ITs; Vitest 4 + jsdom for the frontend |
| Config file | none beyond Boot defaults; `frontend/package.json` (`vitest run`) |
| Quick run command | `mvn -q -Dtest=PromotionHistoryTest,PromotionServiceTest,PromotionControllerTest test` (set `JAVA_HOME` first); `cd frontend && npx vitest run tests/components/PromotionSection.test.tsx` |
| Full suite command | `mvn verify`; `cd frontend && npm test && npm run typecheck` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DEPL-01 | Current stage derived from effective entries and current order; reorder changes it | unit | `-Dtest=PromotionHistoryTest` | ❌ Wave 0 |
| DEPL-02 | OPEN release rejected; skip rejected; next and redeploy accepted; retired target rejected even if next | unit + WebMvc | `-Dtest=PromotionServiceTest,PromotionControllerTest` | ❌ Wave 0 |
| DEPL-02 | Two concurrent records for one release: only one succeeds | IT | `-Dtest=PromotionPersistenceIT` | ❌ Wave 0 |
| DEPL-03 | DB rejects UPDATE and DELETE on `promotion` | IT | `-Dtest=PromotionPersistenceIT` | ❌ Wave 0 |
| DEPL-03 | FAILED never advances; rollback lowers stage | unit | `-Dtest=PromotionHistoryTest` | ❌ Wave 0 |
| DEPL-04 | Note stored and returned; over-length rejected 400 | WebMvc | `-Dtest=PromotionControllerTest` | ❌ Wave 0 |
| AUDIT-01 | History returns all entries, superseded flagged, actors resolved once per response | WebMvc (verify `resolve` called once) | `-Dtest=PromotionControllerTest` | ❌ Wave 0 |
| AUDIT-02 | Release page shows the document revision history entry point | Vitest | `npx vitest run tests/components/DocumentsSection.test.tsx PromotionSection.test.tsx` | partial (DocumentsSection test exists) |
| AUDIT-03 | Superseded-once (index), same-release (composite FK), correction needs reason, superseded entry ignored by current stage | IT + unit | `-Dtest=PromotionPersistenceIT,PromotionHistoryTest` | ❌ Wave 0 |
| D-07-02..04 | Rename relabels history; used stage cannot be deleted; Production flag never absent / not retirable; reorder swap succeeds | IT + service unit | `-Dtest=EnvironmentLadderPersistenceIT,EnvironmentLadderServiceTest` | ❌ Wave 0 |
| D-07-10 | `deployed_at` future and pre-freeze rejected (use injected `Clock`) | unit | `-Dtest=PromotionServiceTest` | ❌ Wave 0 |
| Boundary | New domain/ports have no framework imports; port has no update/delete method | ArchUnit/reflection | `-Dtest=ArchitectureBoundaryTest` | ✅ (extend) |

### Sampling Rate
- **Per task commit:** the quick command for the touched layer.
- **Per wave merge:** `mvn verify` and `npm test && npm run typecheck`.
- **Phase gate:** full suite green before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `PromotionHistoryTest` (pure rules), `PromotionServiceTest`, `EnvironmentLadderServiceTest`
- [ ] `PromotionPersistenceIT`, `EnvironmentLadderPersistenceIT` (copy `DocumentSnapshotPersistenceIT` skeleton; clean with `TRUNCATE release CASCADE` and restore the seeded ladder)
- [ ] `PromotionControllerTest`, `EnvironmentLadderControllerTest` (copy the `ReleaseControllerTest` `@WebMvcTest` + `jwt()` + `csrf()` harness)
- [ ] `frontend/tests/components/PromotionSection.test.tsx`, `EnvironmentLadderSection.test.tsx`
- [ ] Framework install: none.

## Security Domain

> `security_enforcement` is enabled, ASVS level 1 (`.planning/config.json`).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (unchanged) | existing Jira-backed login |
| V3 Session Management | no (unchanged) | existing cookie JWT; CSRF rests on SameSite=Strict plus `csrf.spa()` (locked decision) |
| V4 Access Control | yes | Every `/api/**` is `authenticated()` in `SecurityConfig`; D-07-12 deliberately grants any signed-in user all rights; resource ownership check = promotion must belong to the path's release (composite FK + service lookup by `(releaseId, promotionId)`) |
| V5 Input Validation | yes | Bean Validation on request records (`@NotNull`, `@Size`); enum binding for outcome; UUID path variables (existing `MethodArgumentTypeMismatchException` handler); instant parsing |
| V6 Cryptography | no | no secrets or crypto in this phase |
| V7 Error handling / logging | yes | `ProblemDetail` with fixed copy, no stack traces; log nothing sensitive |
| V8 Data protection | minimal | actor is an accountId already stored elsewhere |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Forged actor via request body | Spoofing | Actor taken from `principal.getName()` only; request records have no `recordedBy` field |
| Rewriting history via direct SQL or a future code path | Tampering / Repudiation | Row trigger rejects UPDATE/DELETE; port exposes no update/delete; `@Immutable` entity |
| Two concurrent writes violating the ladder rule | Tampering | Row lock on `release` inside the transaction; partial unique indexes |
| IDOR: correcting an entry through another release's URL | Elevation / Tampering | Look up by `(releaseId, promotionId)`; composite FK `(supersedes_id, release_id)` |
| Stored XSS via note, correction reason or stage name | Tampering | Render as React text nodes only (never `dangerouslySetInnerHTML`); server-side length limits; stage names length-limited and trimmed |
| SQL injection | Tampering | Spring Data/JPQL bound parameters; no string-built SQL (migration SQL is static) |
| Unbounded input (huge note, huge reorder list) | DoS | `@Size` on note/reason/name; reorder list must equal the stage count (reject otherwise) |
| Missing audit of ladder edits | Repudiation | Ladder edits are not append-only in CONTEXT; stage rename relabels history by design. Residual risk accepted by D-07-03; optionally record `updated_by`/`updated_at` on stages (not required) |
| CSRF on new mutating endpoints | Tampering | Covered by existing `csrf.spa()` + SameSite=Strict; frontend sends `X-XSRF-TOKEN` via `apiFetch` automatically |

## Sources

### Primary (HIGH confidence)
- Codebase read this session: `Release.java`, `ReleaseRepositoryAdapter.java` (`lockOpen`, `freeze`, `delete`), `ReleaseJpaRepository.java` (`findByIdForUpdate`), `ReleaseService.java`, `ReleaseController.java`, `DocumentController.java`, `GlobalExceptionHandler.java`, `ActorResolutionService.java`, `ResolvedActors.java`, `ArchitectureBoundaryTest.java`, `SecurityConfig.java`, `application.yml`, `V3`, `V5`, `V7` migrations, `DocumentSnapshotPersistenceIT`, `ReleaseControllerTest`, `router.tsx`, `SettingsRoute.tsx`, `ReleaseDetailRoute.tsx`, `ConfirmDialog.tsx`, `api/client.ts`, `api/releases.ts`
- `.planning/phases/07-.../07-CONTEXT.md`, `07-DISCUSSION-LOG.md`, `ROADMAP.md` Phase 7 and 8, `REQUIREMENTS.md`, Phase 8 `08-CONTEXT.md` (D-08-01, 02, 11 to 14), Phase 6 `06-RESEARCH.md` structure and `06-0*-PLAN.md` file lists / migration numbers
- https://www.postgresql.org/docs/current/sql-createtrigger.html: row-level triggers do not fire on TRUNCATE; TRUNCATE triggers are statement-level only

### Secondary (MEDIUM confidence)
- none

### Tertiary (LOW confidence)
- none

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, nothing new is introduced and all versions come from the repo.
- Architecture: HIGH, every pattern copies an existing, read file; DB constraint design is core Postgres.
- Pitfalls: HIGH for the TRUNCATE, lock, merge and time-zone items (verified in code or docs); MEDIUM for rule-semantics items, which are open questions rather than facts.

**Research date:** 2026-10-12
**Valid until:** 2026-11-11 (stable; revalidate migration numbering and Phase 6 file overlaps if Phase 6 execution reorders)
