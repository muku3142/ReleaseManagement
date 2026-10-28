# Phase 8: Release Insights Dashboard - Research

**Researched:** 2026-10-12
**Domain:** Read-only cross-release aggregation over the Phase 5/6/7 tables, on the existing Spring Boot / JPA / Flyway / React stack. No new library, no new migration, no new index.
**Confidence:** HIGH (everything rests on code and migrations read in this session; the few rule gaps are listed as Assumptions and Open Questions, not guessed)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
(Copied from `08-CONTEXT.md` `## Implementation Decisions`.)

- **D-08-01: "In Production" = the first successful promotion to Production.** Failed attempts don't count, and later re-promotions don't move the date.
- **D-08-02: Failed promotions are visible but never advance the stage.** They are listed in deployment history with a failed marker. A release whose latest promotion failed is flagged "last promotion failed".
- **D-08-03: Durations are shown per release, plus the median across the visible set.** created → frozen, and frozen → production. Medians, not averages.
- **D-08-04: ADO coverage denominator = Jira-backed items only.** Manual items are excluded. Three counts: linked / no link / enrichment failed (ADO-03). `LINKED` items flagged incomplete count as linked (D-06-03). `NOT_ATTEMPTED` items are a separate "no data" count, never "no link".
- **D-08-05: Two signals kept distinct.** Development churn = commits with an author date before the ticket's **first** transition into status category Done. Testing rework = **reopen count** (Done-category → non-Done transitions) and **commits made after first Done**. (Caused-by bugs dropped 2026-10-07.)
- **D-08-06: "Done" = Jira status category Done**, from Phase 6's scoped, cached `/changelog` capture, not from the resolution date.
- **D-08-07: Display.** Per ticket on the release page: commits-to-done, reopen count, post-Done commits. Per release in the matrix: median commits-to-done, total reopens, post-Done commit share, with outlier tickets flagged. Tickets with no history or no linked commits are excluded from medians and counted as "no data", never as zero. These measure this release's code and test churn, not Jira cycle time; state that in the plans.
- **D-08-08: A release matrix plus sections, on one screen.** Summary strip (outstanding-paperwork counts, releases per stage, median durations); release matrix (doc pills, ladder stage as stepped dots, durations, composition with ADO coverage, churn figures; each row links to its release); deployment history panel.
- **D-08-09: Numbers and inline bars only.** No chart library.
- **D-08-10: The dashboard is the home route.** Replace `DashboardRoute` in place at `/`. No router or nav change.
- **D-08-11: By default, in-flight releases plus those shipped in the last 90 days.** In-flight means open, frozen, or not yet in Production. Shipped means first successful Production promotion within 90 days. A toggle shows all. Medians and summary counts are computed over the visible set.
- **D-08-12: Deployment history is a section on the dashboard, not its own route.** Lists promotions (release, environment, outcome, when, who) with environment and date-range filters applied **server-side**.
- **D-08-13: Default filter: all environments, last 30 days, failed promotions included and marked.** Filters are reflected in URL search params.
- **D-08-14: Only frozen releases can be promoted** (carried into Phase 7 as D-07-06). The promotion record shape Phase 8 needs was delivered by V10.
- **Resolved by Phase 7 (2026-10-07):** use the **Production flag** (D-07-04), not a stage name; `deployed_at` for durations and history (D-07-10); **effective (non-superseded)** entries only (D-07-11); current stage = SUCCESS minus later ROLLED_BACK by current ladder order (D-07-08), and a rolled-back Production release keeps its first-success shipped date; **retired stages** still appear in deployment history but not as filter defaults or matrix columns.
- **Amended 2026-10-12 (D-07-13..15):** "later" rollback means later by `deployed_at` (tie-break `recorded_at`), and an effective **`VOIDED`** entry counts toward no metric, no current stage and no history count. Every Phase 8 query must exclude it.

### Claude's Discretion
- **Read model:** a dedicated dashboard query/endpoint with set-based aggregate subqueries, not more columns on `ReleaseSummary`. No per-release queries (N+1).
- **Readiness:** reuse `GET /api/documents/readiness` and `ReadinessPill` as they are (T-05-55: never re-derive readiness on the client). Code against `NO_TEMPLATE`. Replace `readinessFor`'s linear `Array.find` with a Map index.
- **Styling:** plain Tailwind tables, pills and badges as in `ReleasesRoute.tsx`. No component library.
- **Cache invalidation:** promotion and enrichment mutations must invalidate the dashboard query key.
- **DOC-09 bookkeeping:** DOC-09 is satisfied per `05-VERIFICATION.md:193` but still Pending in `REQUIREMENTS.md:200`. Correct the marker when this phase closes; do not rebuild the feature.
- **Folded todo:** fix `ReleaseRepositoryAdapter.removeItem` and `removeManualItem` so they stamp `items_changed_at` only when something was actually removed (same way WR-01 fixed `addItems`).

### Deferred Ideas (OUT OF SCOPE)
- Duration trend charts (chart library declined, D-08-09).
- Per-reopen-cycle commit breakdown.
- PR review findings as a quality signal.
- Out of scope per Phase Boundary: Jira agile reports, any live Jira or ADO call while rendering, charts beyond inline bars, paging of the release matrix.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUDIT-04 | Deployment history across all releases, filterable by environment and date range | Pattern 4: one native query over `promotion` JOIN `release` JOIN `environment_stage`, effective-and-not-VOIDED predicate, `(stage_id, deployed_at)` index already present, limit+1 paging |
| INSIGHT-01 | created → frozen and frozen → production durations | Pattern 2: `created_at`/`frozen_at` from the release row, first Production success from a new pure method on `PromotionHistory`; median in Java over the visible set |
| INSIGHT-02 | Composition: ticket count, manual vs ticket-backed, ADO coverage share | Pattern 3: one per-item row query (enrichment_status, incomplete) plus a manual count subquery; counts assembled in Java |
| INSIGHT-03 | Per ticket and per release: commits-to-done vs reopens and post-Done commits | Pattern 3 and Pitfalls 4-6: one SQL query shared by the dashboard and the release page, so the boundary rule lives in one place |
| DOC-09 (bookkeeping) | Cross-release readiness | Already built; reuse endpoint and `ReadinessPill`; Map index; flip the REQUIREMENTS marker |
</phase_requirements>

## Summary

Phase 8 needs **no schema change**. V9 already stores exactly what INSIGHT-02/03 read (`release_ticket_item.enrichment_status/enrichment_incomplete/commits_source/history_state/first_done_at/reopen_count`, `release_item_commit.authored_at`), and V10 already carries the index Phase 8 was promised (`promotion_stage_deployed_idx (stage_id, deployed_at)`), the superseded-once unique index on `supersedes_id` (which serves the "effective" anti-join), and the Production partial unique index. [VERIFIED: `V9__ado_enrichment.sql`, `V10__environment_ladder_promotion.sql`]

The central design choice is where the "current stage" rules live. `PromotionHistory` (pure domain record) already implements D-07-08/11/13/15 correctly: `isEffective` (not superseded), `standingSuccess` (effective SUCCESS/ROLLED_BACK only, so VOIDED is ignored, max by `LEDGER_ORDER = deployedAt then recordedAt`, must still be SUCCESS), and `currentStage` (highest-position stage with a standing success, retired stages included). [VERIFIED: `PromotionHistory.java:33-39,127-134`] **Recommendation: load all stages and all promotion rows once, group by release id in Java, build one `PromotionHistory` per release and call it.** Do not re-express current stage in SQL. A window-function version would duplicate three interacting rules (superseded, VOIDED, rollback ordering by `deployed_at` then `recorded_at`) and these rules have already been amended once (2026-10-12); a second copy would silently diverge on the next amendment. The ledger is human-recorded and tiny (tens of releases, a handful of entries each), so a full load is cheap. The two other derived facts Phase 8 needs (first Production success, latest effective entry) go on `PromotionHistory` as two small pure methods so the rules stay in one class.

Everything else is set-based SQL or trivial Java: the deployment history is one filtered, paged native query (the only SQL that restates "effective", as a two-line predicate guarded by an equivalence integration test); composition and churn are one per-item row query whose boundary rule (commit author date vs `first_done_at`) is defined once in SQL and reused by the release page; medians and visible-set filtering are Java. Two new read endpoints (`GET /api/dashboard/releases`, `GET /api/dashboard/deployments`) plus the existing readiness endpoint give three requests per dashboard load and about five SQL statements for the matrix, one for history.

**Primary recommendation:** `PromotionHistory` in Java for stage/shipped/last-failed; one per-item aggregate query for composition + churn; one filtered, paged query for deployment history; two endpoints under `/api/dashboard`; no migration, no chart library, no new dependency.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Current stage, shipped date, last-failed flag per release | API / Backend (domain `PromotionHistory`) | — | Derived from ladder order and effective entries; D-07-03 forbids storing it; one rule class |
| Visible-set selection (in-flight + shipped within 90 days) and medians | API / Backend (application service) | — | Depends on the derived shipped date; summary must be computed over the same set the matrix shows |
| Composition and churn per item | Database (set-based aggregate) | API (median/outlier/no-data policy) | Counting commits against `first_done_at` is a join; policy on top is Java |
| Deployment history filter and paging | Database (indexed query) | API (parameter validation, actor resolution) | D-08-12 requires server-side filtering |
| Documentation readiness | API (existing `DocumentReadinessService`) | Browser (renders states, counts them) | T-05-55: never re-derive staleness in the client |
| Filter state | Browser (URL search params via TanStack Router) | — | D-08-13 |
| Date-range to instant conversion | Browser | API (validates `from < to`) | `<input type="date">` has no zone; same rule as Phase 7's `deployed_at` |
| Inline bars, stepped dots | Browser (Tailwind) | — | D-08-09 |

## Standard Stack

No additions. [VERIFIED: codebase]

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot Web + Data JPA (native `@Query` projections) | 4.1.0 (project BOM) | Two controllers, native read queries | Same pattern as `ReleaseJpaRepository.search` |
| Flyway | Boot-managed | **None needed this phase** | No schema change (verified against V9/V10) |
| React 19 + TanStack Router/Query | project versions | Route, search params, queries | Already used; `releaseDetailRoute` shows `validateSearch` |
| Tailwind | project version | Tables, pills, inline bars | D-08-09, `ReleasesRoute` patterns |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `PromotionHistory` in Java | SQL window functions for current stage | Faster per row, but duplicates the rule set (superseded + VOIDED + rollback ordering). Rejected: divergence risk outweighs a speed gain nobody measured |
| Server computes medians in Java | `percentile_cont(0.5)` in Postgres | The visible set is determined in Java (shipped date), so SQL medians would need the id set round-tripped. Java is simpler |
| limit+1 paging | `Pageable` with count query / keyset | A count query doubles the work for a number the UI does not need; keyset is over-engineering for an internal ledger |
| Two dashboard endpoints | One endpoint, or one per widget | Filters on history change independently of the matrix; separate query keys avoid refetching the matrix on every filter change |

**Installation:** none.

## Architecture Patterns

### System Architecture Diagram

```
Browser  GET /  (DashboardRoute, ?stage=&from=&to=&all=)
  |
  +--> GET /api/documents/readiness ---------> DocumentReadinessService.all()  (existing, 5 queries)
  |
  +--> GET /api/dashboard/releases?scope=active|all
  |        DashboardController -> DashboardService
  |          Q1 releases (+ manual_count subquery)        --+
  |          Q2 environment_stage (all, incl. retired)      |  one transaction, readOnly
  |          Q3 promotion (all rows, no filter)             |
  |             group by release_id -> PromotionHistory     |
  |             currentStage / firstProductionSuccess /     |
  |             latestEffective  (pure domain)              |
  |          visible set = in-flight OR shipped <= 90d      |
  |          Q4 per-item rows for visible release ids     --+
  |             (enrichment, history, commits-before/after-done counts)
  |          Java: composition counts, medians, outliers, per-stage counts
  |        -> DashboardResponse { summary, releases[] , stages[] }
  |
  +--> GET /api/dashboard/deployments?stageId=&from=&to=&limit=&offset=
           Q5 promotion JOIN release JOIN environment_stage
              WHERE effective AND outcome <> 'VOIDED' AND deployed_at in [from,to)
              ORDER BY deployed_at DESC, recorded_at DESC, id DESC LIMIT n+1
           Q6 ActorResolutionService.resolve(distinct recordedBy)  (one batch)
        -> { entries[], hasMore }

Release page GET .../releases/{id}  (per-ticket churn) -> same Q4 query with a single id
```

### Recommended Project Structure
```
src/main/java/com/evision/releasemgmt/
  domain/promotion/PromotionHistory.java      # + firstProductionSuccess(), latestEffective()
  domain/dashboard/                           # small pure records + median helper (no framework imports)
  ports/DashboardReadRepository.java          # read-only port (names must not start update/delete/remove/save)
  application/DashboardService.java
  adapters/persistence/DashboardReadRepositoryAdapter.java
  adapters/persistence/repository/            # native-query interface(s) + projection rows
  adapters/web/DashboardController.java       # + dto records
frontend/src/
  api/dashboard.ts                            # types, query keys, hooks
  routes/DashboardRoute.tsx                   # replaced in place
  components/                                 # only what the route cannot hold: LadderDots, InlineBar if reused
```
ArchUnit constraints to respect: domain/ports free of Spring/JPA imports; application must not depend on `adapters.persistence`; ports must not have `update|delete|remove|save` method names (`ArchitectureBoundaryTest`). [VERIFIED: `ArchitectureBoundaryTest.java`]

### Pattern 1: Reuse `PromotionHistory`; add two pure methods beside `currentStage`
**What:** `new PromotionHistory(releaseId, status == FROZEN, frozenAt, stages, entriesOfThisRelease)` is valid for read-only use (the frozen/frozenAt arguments only matter for `record`/`correct`).
```java
// Both live in PromotionHistory so "effective" has one definition.
// D-08-01/D-07-08: the shipped date is the earliest effective SUCCESS on the Production-flagged stage;
// a later rollback does not move it. A SUCCESS that a correction re-labelled no longer counts.
public Optional<Instant> firstProductionSuccess() {
    return stages.stream().filter(Stage::production).findFirst()
            .flatMap(prod -> entries.stream()
                    .filter(this::isEffective)
                    .filter(e -> e.stageId().equals(prod.id()) && e.outcome() == PromotionOutcome.SUCCESS)
                    .map(Promotion::deployedAt).min(Comparator.naturalOrder()));
}

// D-08-02: "last promotion failed" = the latest effective, non-VOIDED entry is FAILED.
public Optional<Promotion> latestEffective() {
    return entries.stream().filter(this::isEffective)
            .filter(e -> e.outcome() != PromotionOutcome.VOIDED).max(LEDGER_ORDER);
}
```
**When to use:** in `DashboardService` for each release. `isEffective` is O(n) per call so the per-release cost is O(n^2) in that release's entry count (single digits); no optimisation needed.

### Pattern 2: Durations and visible set
- created → frozen = `frozen_at - created_at` (frozen releases only). frozen → production = `firstProductionSuccess - frozen_at`; both non-negative because `deployed_at >= frozen_at` is enforced on record and on correction (`requireDeployedAtInBounds`). [VERIFIED: `PromotionHistory.java:93-97`]
- Visible default: `shippedAt == null || shippedAt >= now - 90d`; the `all` toggle skips the filter. Medians and summary counts run over the visible list only. Median of an even count = mean of the two middle values; releases lacking the measure are excluded from that median (an unfrozen release has no created → frozen duration). Return durations as seconds; the client formats.
- Releases per stage for the summary strip: count by `currentStage`, plus a "not yet promoted" bucket (`currentStage` empty). Matrix dots use **active** stages only; if the current stage is retired, show its name as text next to the dots.

### Pattern 3: Composition and churn from one per-item row query (shared with the release page)
```sql
-- Q4: one row per Jira-backed item in the given releases. Commit counts are the single definition of
-- the dev/test split: dev = authored at or before first_done_at, post-Done = strictly after.
SELECT i.release_id, i.id AS item_id, i.ticket_key,
       i.enrichment_status, i.enrichment_incomplete, i.commits_source,
       i.history_state, i.first_done_at, i.reopen_count,
       count(c.id) FILTER (WHERE i.first_done_at IS NOT NULL AND c.authored_at <= i.first_done_at) AS commits_to_done,
       count(c.id) FILTER (WHERE i.first_done_at IS NOT NULL AND c.authored_at >  i.first_done_at) AS post_done_commits,
       count(c.id)                                                                                   AS commit_total
  FROM release_ticket_item i
  LEFT JOIN release_item_commit c ON c.item_id = i.id
 WHERE i.release_id IN (:releaseIds)
 GROUP BY i.id
```
`release_item_commit_item_authored_idx (item_id, authored_at)` serves the join. [VERIFIED: V9] Manual item count per release: `(SELECT count(*) FROM release_manual_item m WHERE m.release_id = r.id)` inside Q1, the pattern already in `ReleaseJpaRepository.search`.

Java then derives, per release:
- **Composition (D-08-04):** ticket count = rows; manual = Q1 subquery; linked = `LINKED` (incomplete still linked); no link = `NO_LINK`; failed = `FAILED`; no data = `NOT_ATTEMPTED`. Denominator is Jira-backed items only; every figure carries its denominator in a tooltip.
- **Churn-eligible item** = `history_state = AVAILABLE` AND `first_done_at IS NOT NULL` AND `commit_total >= 1` AND NOT `enrichment_incomplete` (see Pitfall 5). Median commits-to-done, post-Done share (`sum(post_done) / sum(commit_total)` over eligible items), over eligible items only. Everything else is "no data", never zero (D-08-07).
- **Total reopens** = `sum(reopen_count)` over items with `history_state = AVAILABLE` (reopens do not need commits; see Assumption A3).
- **Outliers:** flag an eligible ticket whose `commits_to_done` exceeds the Tukey upper fence of the release's eligible values (`Q3 + 1.5*IQR`), only when the release has at least 4 eligible tickets, or whose `reopen_count >= 2` (Assumption A4: thresholds are not in CONTEXT).
- **Source label:** count eligible items with `commits_source = KEY_TAGGED`; show "n of m tickets count key-tagged commits only" (D-06-09 requires the label).

Release page: call the same query with a single id and serve the per-ticket figures from the existing release-detail response, so the boundary rule is not re-implemented in Java.

### Pattern 4: Deployment history query (AUDIT-04)
```sql
SELECT p.id, p.release_id, r.name AS release_name, r.version AS release_version,
       p.stage_id, s.name AS stage_name, s.retired_at AS stage_retired_at,
       p.outcome, p.deployed_at, p.recorded_by, p.note,
       (p.supersedes_id IS NOT NULL) AS corrected
  FROM promotion p
  JOIN release r           ON r.id = p.release_id
  JOIN environment_stage s ON s.id = p.stage_id
 WHERE p.outcome <> 'VOIDED'                                                  -- D-07-15
   AND NOT EXISTS (SELECT 1 FROM promotion c WHERE c.supersedes_id = p.id)    -- D-07-11, uses promotion_superseded_once
   AND (CAST(:stageId AS uuid) IS NULL OR p.stage_id = :stageId)
   AND p.deployed_at >= :from AND p.deployed_at < :to
 ORDER BY p.deployed_at DESC, p.recorded_at DESC, p.id DESC
 LIMIT :limit OFFSET :offset
```
- Page by `limit + 1` rows to compute `hasMore`; clamp `limit` to 100 server-side (default 50). The deterministic tie-break (`recorded_at`, `id`) is required or OFFSET paging shows duplicates and gaps.
- The index `promotion_stage_deployed_idx` serves a stage-filtered range. With "all environments" the leading column is unconstrained; on PostgreSQL 18 a B-tree skip scan can still use it, and on a ledger this size a sequential scan is fine either way. **Do not add an index** (measure first, per project rules). [CITED: postgresql.org/about/news/postgresql-18-released-3142]
- The stage filter list should include retired stages (suffix "retired", sorted last) so their history stays reachable; the matrix and the default do not.
- `VOIDED` rows and superseded rows are absent; a correcting entry shows with a "corrected" marker linking to the release page where the full chain lives (AUDIT-01).
- Actors: collect distinct `recorded_by` from the page and call `ActorResolutionService.resolve(...)` once; display with `ResolvedActors.display`. [VERIFIED: `ActorResolutionService.java`, `ResolvedActors.java`]
- Dates: the client converts the URL dates to a half-open instant range `[startOfFromDay, startOfDayAfterTo)` in the browser's zone and sends instants; the server validates `from < to` and a maximum span (reject, 400 `VALIDATION_FAILED`).

### Pattern 5: Frontend
- `dashboardRoute` gains a `validateSearch` returning `{ stage?: string; from?: string; to?: string; all?: boolean }`, mirroring `releaseDetailRoute`'s typed validator (`router.tsx`). Defaults (last 30 days, all environments) are applied in the component when the params are absent. No change to nav or route registration (D-08-10).
- Hooks in `api/dashboard.ts` with key prefix `['dashboard', ...]`: `useDashboardReleases(scope)` and `useDeploymentHistory(filter)`. Readiness reuses `useDocumentReadiness()`; replace `readinessFor`'s `Array.find` with a Map built once (`readinessIndex(entries)` keyed `releaseId:type`) and update `ReleasesRoute` to use it. Keep `ReadinessPill` untouched.
- Summary-strip paperwork counts: count server-provided states (`STALE`, `NOT_GENERATED`; show `NO_TEMPLATE` separately) for the visible release ids. Counting states is not re-deriving staleness.
- Isolate failure: matrix, readiness and history each have their own `isError` branch (the `ReleasesRoute` pattern, T-05-60), so a readiness failure does not blank the matrix.
- Invalidation: the app default is `staleTime: 0` with `retry: false` (`queryClient.ts`), and the dashboard unmounts while mutations run on other routes, so it refetches on return. Still add `invalidateQueries({ queryKey: ['dashboard'] })` in `useRecordPromotion`, `useCorrectPromotion`, the ladder mutations (`ladder.ts` currently invalidates only `['promotions']`), and the freeze/refresh/enrichment/remove mutations in `releases.ts` (they invalidate `['releases']` only), as CONTEXT requires. Cheap and robust to a later `staleTime` change.
- Inline bars: a `div` with a percentage width; ladder dots: a small map over active stages, filled up to the current position, with a failed marker on the stage of a failed latest entry. No library.

### Anti-Patterns to Avoid
- **Adding columns to `ReleaseSummary` / `search`:** slows `/releases` and `DocumentReadinessService.all()` (which reuses `search("")`). Use a dedicated read port.
- **Current stage in SQL:** duplicates the amended rules. Use `PromotionHistory`.
- **One query per release:** use one grouped load and one `IN (:ids)` item query.
- **Calling `DocumentReadinessService.state` from the dashboard service:** use the existing endpoint's output.
- **Treating `NOT_ATTEMPTED` as "no link", or a missing churn figure as zero.**

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Current stage / effective entries / rollback ordering | A second implementation in SQL or JS | `PromotionHistory` (plus two small methods) | Three interacting rules, already amended once |
| Readiness states | Client staleness logic | `GET /api/documents/readiness` + `ReadinessPill` | T-05-55 |
| Actor display names | Per-row lookups | `ActorResolutionService.resolve` once per response | 05.3 rule, N+1 |
| Date-range UI | A date picker library | `<input type="date">` x2 | No dependency, matches no-library stance |
| Charts | A chart library | Percentage-width divs | D-08-09 |
| URL filter state | `useState` + manual `history` calls | TanStack Router `validateSearch` + `navigate({ search })` | Already the project's router |

**Key insight:** this phase should add almost no rules. Every rule it needs (effective, VOIDED, ordering, Done category, reopen count, key-tagged vs complete) is already computed and stored by Phases 6 and 7; the work is selecting, counting and presenting, and the main way to get it wrong is restating a rule a second time.

## Runtime State Inventory

Not a rename/refactor/migration phase. Omitted.

## Common Pitfalls

### Pitfall 1: VOIDED and superseded entries leaking into a metric
**What goes wrong:** a voided Production success still sets the shipped date, or a superseded SUCCESS still counts in history.
**Why it happens:** any hand-written query that forgets one of the two exclusions (`supersedes_id` anti-join, `outcome <> 'VOIDED'`).
**How to avoid:** stage/shipped/last-failed come only from `PromotionHistory`; the single SQL restatement (Pattern 4) is covered by an equivalence IT that seeds a corrected, a voided and a rolled-back scenario and asserts the endpoint's rows equal `entries.filter(isEffective && outcome != VOIDED)` from `PromotionRepository.load`.
**Warning signs:** history count differs from the release page's effective entries.

### Pitfall 2: Shipped date after the Production flag moves
**What goes wrong:** D-07-04 lets the Production flag move to another stage. `firstProductionSuccess` reads the **currently** flagged stage, so entries on the former PROD stage stop counting as "shipped".
**How to avoid:** accept and document (the flag moving is a deliberate ladder redefinition, and promotions are tied to stage ids); do not try to snapshot the flag. See Open Question 2 only if the user expects otherwise.

### Pitfall 3: Null UUID parameter in a native query
**What goes wrong:** `:stageId IS NULL` makes PostgreSQL fail with "could not determine data type of parameter" when the value is null.
**How to avoid:** `CAST(:stageId AS uuid) IS NULL OR p.stage_id = :stageId` (as in Pattern 4). Cover the null-stage path in an IT.

### Pitfall 4: Unstable paging
**What goes wrong:** ties on `deployed_at` reorder between pages.
**How to avoid:** order by `deployed_at DESC, recorded_at DESC, id DESC`. New entries between page loads can still shift OFFSET pages; acceptable for an audit list (state it in the plan).

### Pitfall 5: Churn numbers that look precise but are partial
**What goes wrong:** (a) `commits_source = KEY_TAGGED` tickets only count commits whose message mentions the key (D-06-09), so commits-to-done is understated without an ADO PAT; (b) a ticket capped at `maxCommitsPerTicket` keeps the **newest** commits (`EnrichmentService` limit branch), dropping the older pre-Done ones, so commits-to-done is biased low and the item is flagged `enrichment_incomplete` with reason `COMMIT_LIMIT`; (c) `history_state = UNAVAILABLE` includes the "unknown status category" case, where `first_done_at` and `reopen_count` are NULL by construction (`StatusHistory.derive`).
**How to avoid:** the eligibility rule in Pattern 3 (AVAILABLE + Done + at least one commit + not incomplete), the key-tagged label, and a visible "n tickets: no data" count with a tooltip listing the reasons.

### Pitfall 6: Boundary and partition of dev vs post-Done commits
**What goes wrong:** `<` and `>` leave commits exactly at `first_done_at` in neither bucket, so dev + post-Done != total.
**How to avoid:** `<=` / `>` partition as in Pattern 3 (Assumption A2). Git author dates have second resolution, so exact ties are possible but harmless.

### Pitfall 7: Frozen releases keep stale enrichment
**What goes wrong:** enrichment writes go through `lockOpen`, so a frozen release's history and commits are as of freeze (`ReleaseRepositoryAdapter.saveEnrichment`). A ticket reopened in Jira after freeze never shows. This is intended (D-06-01) but means the dashboard figures are "as of the last refresh before freeze".
**How to avoid:** label the churn column "as of last refresh"; do not offer a refresh for frozen releases.

### Pitfall 8: Median over the wrong set
**What goes wrong:** medians computed before the visibility filter, or including releases with no value.
**How to avoid:** compute after filtering, over only releases that have the measure; show "n" next to each median.

### Pitfall 9: `removeItem` / `removeManualItem` stamp on a no-op (folded todo)
**What goes wrong:** `removeItem` (`ReleaseRepositoryAdapter.java:121-127`) and `removeManualItem` (`:282-288`) stamp `items_changed_at` unconditionally, so the dashboard falsely shows STALE.
**How to avoid:** `deleteByReleaseIdAndTicketKey` currently returns `void` (`ReleaseTicketItemJpaRepository.java:18`); change it to return `long`/`int` and stamp only when it is greater than zero; in `removeManualItem` stamp only when `findByReleaseIdAndId` was present. Mirror the `addItems` filter-then-stamp shape and its test. [VERIFIED: codebase]

## Code Examples

### Visible set and median (application layer, pure Java)
```java
boolean visible = all || shippedAt.isEmpty() || !shippedAt.get().isBefore(now.minus(Duration.ofDays(90)));

static OptionalDouble median(List<Long> values) {          // null-free input
    var s = values.stream().sorted().toList();
    int n = s.size();
    if (n == 0) return OptionalDouble.empty();
    return OptionalDouble.of(n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0);
}
```

### Typed search params (matches `releaseDetailRoute`)
```ts
validateSearch: (s: Record<string, unknown>): { stage?: string; from?: string; to?: string; all?: boolean } => ({
  stage: typeof s.stage === 'string' ? s.stage : undefined,
  from: typeof s.from === 'string' ? s.from : undefined,
  to: typeof s.to === 'string' ? s.to : undefined,
  all: s.all === true || s.all === 'true' ? true : undefined,
}),
```

### Map-indexed readiness
```ts
export function readinessIndex(entries: ReadinessEntry[] | undefined): Map<string, DocumentReadinessState> {
  return new Map((entries ?? []).map((e) => [`${e.releaseId}:${e.documentType}`, e.state]));
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Count query for pagination totals | limit+1 `hasMore` | project choice | one query per page |
| Index-less multi-column filter on a non-leading column | B-tree skip scan in PostgreSQL 18 | PG 18 (2025) | the existing `(stage_id, deployed_at)` index is usable for date-only filters; irrelevant at this data size |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Deployment history lists effective, non-VOIDED entries only (superseded originals live on the release page), with a "corrected" marker | Pattern 4 | History shows duplicates if the user expected superseded rows too |
| A2 | Dev commits are `authored_at <= first_done_at`, post-Done are `> first_done_at` (CONTEXT says "before" / "after") | Pattern 3, Pitfall 6 | Exact-tie commits land in the other bucket; negligible |
| A3 | Total reopens counts every history-AVAILABLE item, including those with no commits; commit-based figures need the full eligibility rule | Pattern 3 | If the user wants whole-ticket exclusion, reopens read lower |
| A4 | Outlier rule: Tukey fence on commits-to-done (min 4 eligible tickets) or `reopen_count >= 2` | Pattern 3 | CONTEXT says "outlier tickets flagged" without a rule; thresholds are a product call |
| A5 | Items with `enrichment_incomplete` (incl. `COMMIT_LIMIT`) are excluded from commit-based churn and counted as no data | Pattern 3, Pitfall 5 | Excluding too much, or including biased counts if wrong |
| A6 | Medians use mean of the two middle values for even counts; seconds on the wire | Pattern 2 | Cosmetic |
| A7 | Date filters are interpreted in the browser's time zone and sent as a half-open instant range; a shared URL shows a different instant range in another zone | Pattern 4 | Small boundary differences between viewers |
| A8 | Ledger and release counts stay small (tens to low hundreds), so a full-ledger load per dashboard request is acceptable | Summary | If the ledger grows to many thousands of rows, restrict Q3 to visible candidates or add caching, after measuring |

## Open Questions

1. **Does a release rolled back out of Production, shipped long ago, stay visible by default?**
   - What we know: D-08-11 says in-flight = "open, frozen, or not yet in Production"; shipped = first Production success within 90 days. A release whose first Production success is older than 90 days but is currently rolled back is neither "not yet in Production" (it has a shipped date) nor recent.
   - Recommendation: treat a release whose current stage is below Production as in-flight regardless of shipped date (it is unresolved), i.e. visible = `shippedAt` null, or shipped within 90 days, or `currentStage` position < Production position. Needs a user yes/no.
2. **If the Production flag is moved to another stage, should earlier PROD successes still count as "shipped"?**
   - Recommendation: no, follow the current flag (Pitfall 2). Confirm only if the ladder is expected to be re-flagged in practice.
3. **Outlier definition (A4) and exclusion of incomplete items (A5).** Recommend the defaults above; the user should confirm because they decide what the dashboard calls "bad".
4. **Latest-promotion-failed scope.** Recommend: latest effective non-VOIDED entry across all stages is FAILED (a later SUCCESS clears it). Confirm this is the intended meaning of D-08-02.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker (Testcontainers `postgres:18`) | persistence ITs, equivalence IT | not probed this session | — | Project ITs already depend on it (`PromotionPersistenceIT`) |
| Node / Vitest | frontend tests | Vitest ^4 declared in `package.json` | — | — |

No new external dependency. No Jira or ADO call is made at render time (success criterion 6).

## Validation Architecture

> `workflow.nyquist_validation` is `false` in `.planning/config.json`; this compact section is included because the request asked for it.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 6 + AssertJ + Testcontainers `postgres:18` (backend); Vitest ^4 (frontend) |
| Config file | existing `pom.xml`, `frontend/package.json` |
| Quick run command | `mvn -q -Dtest=DashboardServiceTest,PromotionHistoryTest test` (set `JAVA_HOME` per Maven call, per project memory) |
| Full suite command | `mvn verify` and `npm test` in `frontend/` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INSIGHT-01 | `firstProductionSuccess` ignores superseded/VOIDED/FAILED, survives a later rollback; durations and median over the visible set | unit | `-Dtest=PromotionHistoryTest` | extend existing |
| INSIGHT-01 | 90-day visibility, `all` toggle, Open Question 1 rule | unit | `-Dtest=DashboardServiceTest` | Wave 0 |
| AUDIT-04 | filter by stage and range, null stage, VOIDED/superseded excluded, tie-break paging, equivalence with `PromotionHistory` | integration | `-Dtest=DashboardPersistenceIT` | Wave 0 |
| INSIGHT-02 | linked/no-link/failed/not-attempted counts, manual excluded, incomplete counts as linked | integration | `-Dtest=DashboardPersistenceIT` | Wave 0 |
| INSIGHT-03 | partition at `first_done_at`, UNAVAILABLE/NOT_READ/no-commit/incomplete -> no data, reopens, key-tagged label | integration + unit | `-Dtest=DashboardPersistenceIT,DashboardServiceTest` | Wave 0 |
| AUDIT-04 | controller: param validation (bad uuid, from >= to, limit clamp) -> 400 ProblemDetail | web | `-Dtest=DashboardControllerTest` | Wave 0 |
| DOC-09 | `readinessIndex` Map lookup; `NO_TEMPLATE` rendering unchanged | unit | `npm test -- documents` | Wave 0 |
| Folded todo | remove of a missing item / manual item does not stamp `items_changed_at` | integration | `-Dtest=ReleasePersistenceIT` | extend existing |
| Query count | matrix load issues a fixed number of statements regardless of release count | integration | `-Dtest=DashboardPersistenceIT` (Hibernate statistics or datasource proxy) | Wave 0 |

### Sampling Rate
- **Per task commit:** the quick command for the touched class.
- **Per wave merge:** `mvn verify` and `npm test`.
- **Phase gate:** full suites green plus a manual walk-through of the dashboard.

### Wave 0 Gaps
- [ ] `DashboardPersistenceIT` (seed releases, items, commits, promotions with corrections, voids, rollbacks)
- [ ] `DashboardServiceTest`, `DashboardControllerTest`
- [ ] frontend test for `readinessIndex` and the search-param defaults

## Security Domain

### Applicable ASVS Categories (Level 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (inherited) | Existing JWT filter chain; `/api/**` is `authenticated()` in `SecurityConfig`; new endpoints add nothing |
| V3 Session Management | no (inherited) | Unchanged |
| V4 Access Control | yes | No role model exists (D-07-12); all signed-in users see all releases, same as `/api/releases`. Endpoints are GET-only, so the SameSite=Strict CSRF decision is not engaged |
| V5 Input Validation | yes | Typed binding for `UUID`/`Instant`/`int`; `@Validated` bounds on `limit`/`offset`; `from < to` and max span checked in the service; native queries use bound parameters only |
| V6 Cryptography | no | Nothing stored or transmitted that is new; encrypted credentials are not read |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection via filter params | Tampering | Bound parameters in the native queries; no string concatenation; stage id is a typed `UUID` |
| Unbounded result set / expensive range (DoS) | Denial of service | `limit` clamp (max 100), max date span, no unpaged history endpoint |
| Stored XSS via release name, version, note, ticket key rendered on the dashboard | Tampering / Information disclosure | Render as React text only; never `dangerouslySetInnerHTML`; notes are user-entered free text (Phase 7 note field) |
| Credential or token leakage in the dashboard payload | Information disclosure | DTOs carry no credential fields; actors resolved to display email via `ActorResolutionService` only (ArchUnit rule forbids touching the credential port) |
| Information disclosure of ADO detail | Information disclosure | Payload carries counts and statuses, not PR/commit content; no per-commit messages |
| Error detail leakage | Information disclosure | Existing `GlobalExceptionHandler` ProblemDetail with machine-readable `reason`; add a `VALIDATION_FAILED` mapping for the new bad-parameter cases, not stack traces |
| Open redirect / deep-link tampering via URL search params | Tampering | Search params are only read as filters, validated by `validateSearch`; never used as navigation targets |

## Sources

### Primary (HIGH confidence)
- `src/main/resources/db/migration/V9__ado_enrichment.sql`, `V10__environment_ladder_promotion.sql`, `V3__release_assembly.sql`, `V5__document_generation.sql`: schema, indexes, constraints.
- `domain/promotion/PromotionHistory.java`, `Promotion.java`, `Stage.java`; `domain/enrichment/StatusHistory.java`, `CodeLinks.java`: rule implementations.
- `adapters/persistence/PromotionRepositoryAdapter.java`, `ReleaseRepositoryAdapter.java` (`saveEnrichment`, remove paths), `ReleaseJpaRepository.java` (set-based subquery pattern), `ReleaseSummaryRow.java`.
- `application/DocumentReadinessService.java`, `ActorResolutionService.java`, `ResolvedActors.java`, `EnrichmentService.java` (commit limit and ADO completion).
- `adapters/web/PromotionController.java`, `DocumentReadinessController.java`, `GlobalExceptionHandler.java`.
- `frontend/src/routes/DashboardRoute.tsx`, `ReleasesRoute.tsx`, `router.tsx`, `api/documents.ts`, `api/promotions.ts`, `api/ladder.ts`, `api/releases.ts`, `api/queryClient.ts`, `components/ReadinessPill.tsx`.
- `src/test/.../ArchitectureBoundaryTest.java`, `PromotionPersistenceIT.java` (test conventions).
- `.planning/phases/08-*/08-CONTEXT.md`, `06-CONTEXT.md`, `07-CONTEXT.md`, `07-RESEARCH.md`, `ROADMAP.md`, `REQUIREMENTS.md`, `.planning/config.json`.

### Secondary (MEDIUM confidence)
- PostgreSQL 18 release notes (B-tree skip scan): https://www.postgresql.org/about/news/postgresql-18-released-3142/ (not re-fetched this session; cited in project CLAUDE.md sources).

### Tertiary (LOW confidence)
- None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, no additions; verified against existing code.
- Architecture: HIGH, every recommendation reuses an existing seam; the `PromotionHistory` reuse was checked line by line against D-07-08/11/13/15.
- Pitfalls: HIGH for items traced to code (1, 3, 5, 7, 9); MEDIUM for paging behaviour under concurrent inserts.
- Open product rules (outliers, incomplete handling, rolled-back visibility): flagged as Assumptions and Open Questions.

**Research date:** 2026-10-12
**Valid until:** 2026-11-12 (stable; revisit if Phase 6/7 human checks change the schema or `PromotionHistory`)
