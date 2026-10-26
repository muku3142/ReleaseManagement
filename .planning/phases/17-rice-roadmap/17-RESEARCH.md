# Phase 17: RICE Roadmap - Research

**Researched:** 2026-10-22
**Domain:** Prioritisation list (RICE) over Jira-sourced and manual items, inside the existing Spring Boot + React app
**Confidence:** HIGH on codebase fit and patterns; MEDIUM on Jira edge behaviour (flagged `[ASSUMED]`, verify with WireMock/real site)

> No CONTEXT.md exists (`/gsd-discuss-phase 17` has not run). Nothing below is a locked user decision. User decisions the planner needs are listed in "Open Questions for discuss-phase" and are NOT decided here. `nyquist_validation` is `false` in `.planning/config.json`, so no Validation Architecture section; a short test list is under Common Pitfalls.

## Summary

Phase 17 adds zero dependencies. It is ordinary domain code copying patterns already in the repo: a hexagonal slice (`domain/roadmap`, `ports/RoadmapRepository`, `application/RoadmapService` + pure `RiceScorer`, `adapters/persistence` JPA adapter, `adapters/web/RoadmapController`), Flyway `V16__roadmap.sql` (pre-assigned; V14 already exists in `src/main/resources/db/migration`, V15 belongs to Phase 19) and a `frontend/src/roadmap/` folder with a `/roadmap` route. Jira reads reuse `TicketSearchService` -> `JiraTicketPort.searchAll` (`/rest/api/3/search/jql`, cursor pagination, already correct) through the signed-in user's own credentials via `JiraAccessContextService`. Jira stays read-only; scores are never written back. The optional product link FKs the Phase 15 `product` table (already built: `ProductRepository`, `ProductEntity`, `useProducts`).

The three genuinely tricky areas are: (1) the Jira refresh must update only `jira_*` columns and must correctly separate "Jira says this issue is gone/invisible" from "the call failed" (the existing adapter turns a 400 "An issue with key" into an empty list for the WHOLE batch, so a single vanished key in a `key in (...)` batch makes every key look missing); (2) the score arithmetic and deterministic ranking (BigDecimal, one Java implementation, total-order comparator ending in a unique id); (3) CSV export, which has no existing code in the repo and needs RFC 4180 quoting plus spreadsheet formula-injection neutralisation because titles come from Jira or free text.

**Primary recommendation:** Single `roadmap_item` table with local columns (RICE inputs, product link) and `jira_*` snapshot columns, refresh implemented as a targeted UPDATE that touches only `jira_*` columns; `roadmap_item_input_history` append-only, written in the same transaction under a row lock; ranking in `RiceScorer` + a `Comparator` in the application layer; CSV produced server-side from the same ranked list.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| RICE score computation | API / Backend (`domain/roadmap/RiceScorer`, pure) | — | Single source of truth, never stored, never recomputed in SQL or JS (ARCHITECTURE Anti-Pattern 5). UI displays the server value. |
| Ranking + tie-break | API / Backend (`RoadmapService`) | — | In-memory over tens to low hundreds of rows; same ordering feeds list, CSV and (Phase 20) planner. |
| Input validation (Impact set, Confidence 0-100, Effort > 0, Reach >= 0) | API / Backend (domain value object) | Database CHECK constraints; Browser (form hints) | Server is authoritative; DB CHECKs are the backstop; UI only mirrors for UX. |
| Jira import / refresh | API / Backend (`RoadmapService` via `TicketSearchService`) | — | Needs the caller's Jira credentials; no background job; explicit user action only. |
| Jira snapshot + unavailable flag | Database (`jira_*` columns) | API | Persisted so the flag and `fetched_at` survive reloads and are shared. |
| Input change history | Database (append-only table) | API | Written transactionally with the update; read-only to the UI. |
| Product link and filter | API + Database (FK to `product`) | Browser (filter control) | Reuses Phase 15 tables; filter is a query parameter on the list endpoint. |
| CSV export | API / Backend (streams `text/csv`) | Browser (download trigger) | Must match server ranking exactly and neutralise formula injection server-side. |
| Rubric/units display, inline editing | Browser / Client | — | Presentation only; rubric text defined once in a frontend constant. |

## Phase Requirements

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ROAD-01 | Add items from Jira (key or JQL) and manual items | Reuse `TicketBrowser` (props `actionLabel`, `onAction(keys, projectKey)`) for JQL/project search plus a key box; server re-fetches by key via `TicketSearchService.search(byJql("key in (...)"))` (never trust client summaries); partial unique index on `jira_key`; result counts added/alreadyPresent/notFound like `AddTicketsResult`. |
| ROAD-02 | Set R/I/C/E with units + rubric visible; invalid rejected | `RiceInputs` domain record validates; DB CHECKs backstop; `PUT /api/roadmap/items/{id}/inputs` full replace of 4 nullable fields; rejection via new enum reason handled in `GlobalExceptionHandler` (pattern: `PortfolioEditRejection`). |
| ROAD-03 | Ranked by app-computed score; missing inputs last, deterministic | `RiceScorer` (BigDecimal) + total-order comparator ending in `id`; unscored bucket ordered deterministically too. |
| ROAD-04 | Refresh updates summary/status + shows fetched time, never touches RICE inputs; vanished item kept and flagged | Targeted UPDATE of `jira_*` columns only; batched fetch + per-key fallback to distinguish missing from error; `jira_synced_at`, `jira_unavailable_since`. |
| ROAD-05 | Optional product link; filter by product | Nullable `product_id` FK (RESTRICT) to Phase 15 `product`; archived products keep link, cannot be newly assigned (D-15-07 convention). |
| ROAD-06 | History of RICE input changes (who, when, old to new) | Append-only `roadmap_item_input_history`; one transaction with the update; actor from `Principal.getName()` resolved to email via `ActorResolutionService`. |
| ROAD-07 | Export ranked roadmap as CSV | Server-side `CsvWriter` in web adapter; RFC 4180 + formula-injection prefixing; static filename. |
</phase_requirements>

## Standard Stack

Zero new dependencies (milestone rule, SUMMARY.md: "Do not add ... a RICE rules engine").

### Core (all already in the project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot / Spring MVC | 4.1.0 (project BOM) | REST controller | Project constraint `[CITED: CLAUDE.md]` |
| Spring Data JPA / Hibernate | Boot-managed | Persistence adapter | Same as `ProductRepositoryAdapter` `[VERIFIED: codebase]` |
| Flyway + `flyway-database-postgresql` | Boot-managed | `V16__roadmap.sql` | V14 exists, V16 pre-assigned `[VERIFIED: ROADMAP.md, db/migration listing]` |
| Jackson (`tools.jackson`) | Boot-managed | JSON | Existing |
| Bean Validation | Boot-managed | Request DTO shape (`@Size`, `@Pattern`, `@NotBlank`) | Existing; domain does the business rules |
| React 19 + Vite + TanStack Router/Query + Tailwind | project frontend | `roadmap/` folder, `/roadmap` route | `[VERIFIED: frontend/src/router.tsx, api/products.ts]` |
| Vitest 4 | `^4` | Frontend tests | `[VERIFIED: frontend/package.json]` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hand-written ~25-line `CsvWriter` | `commons-csv` | Adds a dependency against the milestone's zero-dependency rule for ~25 lines; but the formula-injection step is custom either way. Recommend hand-written + exhaustive unit tests. If the user prefers a library, it is a trivial swap. |
| Score in SQL / generated column | — | Rejected by ARCHITECTURE Decision 4 (formula in two places). Revisit only past ~5k items. |

**Installation:** none.

## Architecture Patterns

### System Architecture Diagram

```
Browser (/roadmap)                                  Jira Cloud (read-only)
  |  list(product?)      |  add by key / JQL (TicketBrowser)      ^
  |  inline edit inputs  |  refresh (one / all)                   |  POST /rest/api/3/search/jql
  |  export CSV          |                                        |  (cursor pagination, existing adapter)
  v                      v                                        |
RoadmapController (Principal -> actor)                            |
  |                                                               |
  v                                                               |
RoadmapService --- add/refresh ---> TicketSearchService ----------+
  |   |                              (JiraAccessContextService = caller's own token)
  |   +-- rank(): RiceScorer.score() + comparator (in memory)
  |   +-- updateInputs(): lock row -> validate RiceInputs -> diff -> write item + history rows (one tx)
  v
RoadmapRepository (port) -> RoadmapRepositoryAdapter (JPA)
  |  roadmap_item (RICE + product_id + jira_* snapshot)  --FK--> product (Phase 15)
  |  roadmap_item_input_history (append-only)
  v
CSV: RoadmapController.exportCsv -> RoadmapService.ranked(filter) -> CsvWriter -> text/csv
Phase 20 (later): PlanningContextAssembler -> RoadmapService.topRanked(n)
```

### Recommended Project Structure
```
src/main/java/com/evision/releasemgmt/
  domain/roadmap/        RoadmapItem, RiceInputs, RiceScorer, RoadmapItemSource(JIRA|MANUAL), InputChange,
                         RoadmapRejection (enum), RoadmapRejectedException, RoadmapItemNotFoundException
  ports/                 RoadmapRepository
  application/           RoadmapService (CRUD, import, refresh, ranked/topRanked, history)
  adapters/persistence/  RoadmapRepositoryAdapter, entity/RoadmapItemEntity, entity/RoadmapInputHistoryEntity,
                         repository/RoadmapItemJpaRepository (+ @Modifying jira-only update, @Lock PESSIMISTIC_WRITE finder)
  adapters/web/          RoadmapController, CsvWriter, dto/Roadmap*Request|Response
  src/main/resources/db/migration/V16__roadmap.sql
frontend/src/
  api/roadmap.ts         types, query keys, hooks (hand-written fetch layer, like api/products.ts)
  roadmap/               RoadmapTable, InlineScoreCell, RubricPanel, ImportFromJiraDialog, InputHistoryPanel
  routes/RoadmapRoute.tsx + route in router.tsx (+ nav link in AppShell)
```
Mirror naming from Phase 15 (`ProductService` / `ProductRepository` / `ProductRepositoryAdapter`). `domain` and `ports` must stay free of Spring/JPA imports (`ArchitectureBoundaryTest.domainAndPortsHaveNoFrameworkImports`) and `application` must not import adapters.

### Pattern 1: Pure score + total-order ranking
**What:** `RiceScorer.score(RiceInputs)` returns `Optional<BigDecimal>` (empty if any of the four inputs is null). Ranking comparator: scored before unscored; score desc; then fixed tie-breaks; final tie-break on `id` so the order is a total order.
**When to use:** list, CSV and `topRanked` all call the same method.
```java
// Pattern; exact tie-break columns are an Open Question for discuss-phase
static final MathContext MC = new MathContext(20, RoundingMode.HALF_EVEN);

Optional<BigDecimal> score(RiceInputs i) {
    if (!i.complete()) return Optional.empty();
    return Optional.of(i.reach().multiply(i.impact())
            .multiply(i.confidencePercent()).divide(BigDecimal.valueOf(100), MC)
            .divide(i.effort(), MC));            // effort > 0 guaranteed by RiceInputs
}
// Compare on the unrounded value; round to 2 dp only for display/CSV, so two items that differ at the 3rd
// decimal never swap order between the table and the CSV.
```
Ranking uses `BigDecimal.compareTo` (never `equals`, which is scale-sensitive: `2.0` vs `2.00`). Unscored bucket order: deterministic, e.g. `createdAt asc, id asc` (Open Question).

### Pattern 2: Jira fields isolated from local fields
**What:** One table, two column groups. The refresh path never loads-and-saves the full entity (that would write RICE columns back from a stale read and lose a concurrent inline edit). Use a `@Modifying @Query("update RoadmapItemEntity ... set title=:t, jiraStatus=:s, jiraUpdatedAt=:u, jiraSyncedAt=:now, jiraUnavailableSince=null where id=:id")` that names only Jira columns. Likewise the inputs update never touches `jira_*`.
**Test:** integration test that edits inputs concurrently with refresh, plus a reflection/JPQL assertion that the refresh statement's SET list contains no RICE column.

### Pattern 3: Input update + history under one lock
Copy the discipline of `PromotionService.appendUnderLock` / `PromotionRepository`: `findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`), validate new `RiceInputs`, diff against current, write item and one history row per changed field in one `@Transactional`. No-op saves (nothing changed) write no history. `roadmap_item_input_history` is append-only: no update/delete methods on the port (extend `ArchitectureBoundaryTest` with a "no mutation" rule like `clientDeploymentRepositoryExposesNoMutation`); optionally also a DB trigger as in V10/V14 (the ledger trigger pattern exists; see `V10__environment_ladder_promotion.sql`).

### Pattern 4: Import by key or JQL
- JQL/project search: reuse `TicketBrowser` with `actionLabel="Add to roadmap"`; `onAction(ticketKeys, jiraProjectKey)` posts only the selected keys.
- By key: a small key box (one or several keys, each matched against `^[A-Z][A-Z0-9]*-[0-9]+$` at the REST boundary AND at the sink, as `ReleaseService.TICKET_KEY` does) feeding the same endpoint.
- Server: `POST /api/roadmap/items/jira {keys:[...]}` (`@Size(max=...)` cap), re-fetches via `ticketSearchService.search(TicketSearchCriteria.byJql("key in (...)"))` in batches of 100 (`FETCH_BATCH_SIZE` pattern in `ReleaseService.fetchTickets`), inserts new rows, reports `{added, alreadyPresent, notFound}`. The user-supplied JQL path already exists and is forwarded verbatim by `TicketController.search`; Jira is read-only so that is not a write risk.

### Anti-Patterns to Avoid
- **Storing the score** (column, generated column, JSON) — stale after a rubric change, formula in two places.
- **Reusing `release_ticket_item` / `ReleaseService.refresh`** — wrong semantics (overwrites whole snapshot, requires OPEN release, ADO enrichment) (ARCHITECTURE Decision 4).
- **Treating story points as Effort** — units differ (PITFALLS #5). Do not silently prefill; if offered, label it as a hint and require the user to confirm.
- **Mirroring a whole Jira project** — explicit add only (PITFALLS #5/#6).
- **Background Jira refresh job** — forbidden by ARCHITECTURE (needs the user's own credentials).
- **Adding timeline/Gantt/sprint/capacity/WSJF/ICE/custom formulas** — out of scope (FEATURES anti-features, PITFALLS #6).
- **LLM-suggested RICE values** — explicit anti-feature.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Jira search + pagination + auth errors | A new HTTP client | `TicketSearchService` / `JiraRestTicketAdapter` | Already uses `/search/jql` with `nextPageToken`, maps 401/403/429/5xx/unreachable to `JiraTicketReadException` (project constraint) |
| Per-user Jira credentials | A shared service token | `JiraAccessContextService.currentContext()` | AUTH-02: only the caller's own identity |
| Actor display (who) | Joining user tables | `ActorResolutionService.resolve(...)` once per response | Enforced by ArchUnit (`actorResolutionResolvesThroughActorDirectoryOnly`) |
| Unique-name / conflict mapping | Ad hoc 500s | `saveAndFlush` + catch `DataIntegrityViolationException` -> rejection enum (as `ProductRepositoryAdapter`) | Constraint fires inside the catch, not at commit |
| Problem+JSON errors | New envelope | `GlobalExceptionHandler.problem(...)` with a `reason` code | Frontend `ApiError`/`messageForReason` already consume `reason` |
| Product list/labels | New product lookup | `useProducts`, `ProductRepository` | Phase 15 |
| CSV quoting | `String.join(",")` | A single small `CsvWriter` with tests | Commas, quotes, CR/LF in titles; plus formula injection |

**Key insight:** every hard part (Jira transport, auth scoping, actor resolution, conflict mapping, append-only discipline) already exists. The only new logic is the scorer, the comparator, the diff-to-history step, the Jira "missing vs failed" classification and the CSV writer.

## Data Model (V16 proposal, subject to discuss-phase answers)

```sql
-- Sketch, not final DDL. Column set follows ARCHITECTURE.md Decision 4 + findings below.
CREATE TABLE roadmap_item (
  id UUID PRIMARY KEY,
  source VARCHAR(8) NOT NULL CHECK (source IN ('JIRA','MANUAL')),
  jira_key VARCHAR(32),                       -- display + lookup; may change if issue moved
  jira_issue_id VARCHAR(32),                  -- stable identity for matching refresh results
  title VARCHAR(500) NOT NULL,                -- manual: user text; JIRA: summary snapshot
  jira_status VARCHAR(100),
  jira_updated_at TIMESTAMPTZ,
  jira_synced_at TIMESTAMPTZ,
  jira_unavailable_since TIMESTAMPTZ,
  product_id UUID REFERENCES product(id) ON DELETE RESTRICT,
  reach NUMERIC(12,2) CHECK (reach >= 0),
  impact NUMERIC(3,2) CHECK (impact IN (0.25,0.5,1,2,3)),
  confidence NUMERIC(5,2) CHECK (confidence BETWEEN 0 AND 100),
  effort NUMERIC(8,2) CHECK (effort > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by VARCHAR(255) NOT NULL,
  archived_at TIMESTAMPTZ,                    -- only if archive is chosen (Open Question)
  CONSTRAINT roadmap_source_shape CHECK ((source='JIRA') = (jira_key IS NOT NULL)),
  CONSTRAINT roadmap_title_not_blank CHECK (length(btrim(title)) > 0)
);
CREATE UNIQUE INDEX roadmap_item_jira_key_unique ON roadmap_item (jira_key) WHERE jira_key IS NOT NULL;
CREATE INDEX roadmap_item_product_idx ON roadmap_item (product_id);

CREATE TABLE roadmap_item_input_history (
  id UUID PRIMARY KEY,
  item_id UUID NOT NULL REFERENCES roadmap_item(id) ON DELETE RESTRICT,
  field VARCHAR(16) NOT NULL CHECK (field IN ('REACH','IMPACT','CONFIDENCE','EFFORT')),
  old_value NUMERIC, new_value NUMERIC,       -- NULL = unset
  changed_at TIMESTAMPTZ NOT NULL, changed_by VARCHAR(255) NOT NULL
);
CREATE INDEX roadmap_input_history_item_idx ON roadmap_item_input_history (item_id, changed_at);
```
Notes: `impact` CHECK with `NUMERIC(3,2)` stores `0.25/0.5/1/2/3` exactly. `jira_issue_id` unique partial index too. Test V16 against a realistic v1.1+V14 snapshot like `V14PortfolioMigrationIT` (it only adds tables, so risk is low). `src/test/resources/application.yml` shadows main's: any new config property must be added to both (env landmine).

## Common Pitfalls

### Pitfall 1: One vanished key makes the whole refresh batch look missing
**What goes wrong:** `JiraRestTicketAdapter.searchAll` converts a 400 whose body contains "An issue with key" into `List.of()`. For `key in (A, B, C)` where B was deleted/moved/not visible, Jira answers 400 for the whole query `[ASSUMED: same error text as the single-key case the adapter already handles; verify against a real site or a captured response]`. The refresh then sees zero results and would flag every item unavailable.
**How to avoid:** Only classify as unavailable after a *successful* call. If a batch returns fewer items than requested, re-query the missing keys individually (`key = X`, a path the adapter already handles) or bisect; an individual empty result = unavailable. Any `JiraTicketReadException` (401/403/429/5xx/unreachable) must abort the refresh WITHOUT flagging anything and surface the existing Jira error reason. Cap per-request refresh volume (max keys) to stay clear of rate limits; no retry/backoff loops (adapter deliberately has none).
**Warning signs:** Refresh-all flags everything after one deletion; tests only cover the all-present case.

### Pitfall 2: Moved/renamed issues return a different key `[ASSUMED]`
Jira resolves old keys to the moved issue. Match refresh results on `jira_issue_id` (present on `TicketDetail.jiraIssueId`), not only on key, and update `jira_key` if it changed (watch the unique index). Verify the behaviour with a real Jira before relying on it; if unverifiable, document as a known limitation.

### Pitfall 3: "Unavailable" is per viewer
A user without Browse permission looks identical to a deleted issue. Label it "Unavailable in Jira for the last refresher (deleted, moved or not visible)", keep the row and its local data, show last-known title/status and `jira_synced_at`, and clear the flag on the next successful refresh. Ranking uses only local inputs, so rank never depends on who is logged in (PITFALLS #5). Whether to persist the flag or compute per refresh is an Open Question (recommended: persist `jira_unavailable_since`).

### Pitfall 4: Refresh overwrites local inputs / lost updates
Load-modify-save of the entity writes all columns. Use targeted UPDATE statements per column group (Pattern 2) and a row lock for input edits (Pattern 3).

### Pitfall 5: Decimal arithmetic and ordering
`double` loses exactness; `BigDecimal.divide` without a `MathContext` throws on non-terminating results; `equals` vs `compareTo`. Use the Pattern 1 scorer; round only at presentation; sort on the unrounded value; unit-test: Effort 0/negative rejected, Impact 0.75 rejected, Confidence -1/101 rejected, Reach 0 gives score 0 (scored, ranks above unscored), null input gives unscored, equal-score determinism with shuffled input.

### Pitfall 6: CSV injection and encoding
Titles come from Jira or free text. Cells whose text starts with `=`, `+`, `-`, `@`, TAB or CR must be neutralised (prefix with `'`) `[CITED: OWASP CSV Injection guidance, owasp.org/www-community/attacks/CSV_Injection]`; apply to text columns only (title, key, status, product, actor), not to numeric columns. RFC 4180: quote fields containing `,`, `"`, CR or LF and double embedded quotes; CRLF row separator. Decide on a UTF-8 BOM (Excel opens BOM-less UTF-8 as ANSI and garbles non-ASCII titles) — small Open Question. Header `Content-Type: text/csv; charset=utf-8`; filename is a fixed/date-only string (no free text), so `ExportFilename` is not needed. Existing browser download pattern: `ExportButtons`/`useExportDocument` in `frontend/src/api/documents.ts` (fetch + blob); a plain GET works with the SameSite=Strict cookie. Rank in the CSV must come from the same `RoadmapService.ranked(filter)`.

### Pitfall 7: Rubric drift and false precision
Keep the rubric text (units, Impact labels, Confidence meaning, Effort unit) in ONE frontend constant and reference it from the table header, the input controls and the CSV header docs. Show the formula and state that the rank is advisory (PITFALLS #4). Impact is a select of exactly five values, not a number box. Unit choices are Open Questions.

### Pitfall 8: XSS from titles
Render titles/notes as plain text (React default escaping), never Markdown/HTML (PITFALLS threat table). Jira `description` and comments are fetched by the shared search but must NOT be stored for roadmap items (ARCHITECTURE: summary snapshot only; also keeps the Phase 20 external-provider payload minimal).

### Pitfall 9: Heavy Jira payload
`TicketSearchService.search` requests every `TicketField` including description and comments for up to 100 issues per page. Acceptable at this scale; do NOT try to shrink via a subset of `fieldIds` because `JiraRestTicketAdapter.field()` calls `fields.path(ids.get(f))` and a missing id yields a null path. A lean field set would need an adapter change; not recommended for this phase.

### Pitfall 10: Test and environment landmines (from project memory)
Every Maven call needs `export JAVA_HOME="$HOME/.jdks/jdk-25"`; use `mvn clean` after changing public APIs; frontend `npm ci` can hit a Vite-vs-npm-ci EPERM; new config properties go in both `application.yml` files. Suggested test set: `RiceScorerTest`, `RiceInputsTest`, `RoadmapServiceTest` (refresh classification with a fake port), `RoadmapPersistenceIT` + `V16RoadmapMigrationIT`, `RoadmapControllerTest` (validation reasons, CSV bytes), `CsvWriterTest`, ArchUnit "no mutation" rule for the history port, frontend Vitest for the table/inline cell/rubric.

## Code Examples

### Jira-only refresh update (adapter, pattern)
```java
// Names only jira_* columns; RICE columns and product_id are structurally unreachable from this statement.
@Modifying
@Query("""
    update RoadmapItemEntity r set r.title = :title, r.jiraStatus = :status, r.jiraUpdatedAt = :updated,
           r.jiraSyncedAt = :now, r.jiraUnavailableSince = null, r.jiraKey = :key
    where r.id = :id""")
int applyJiraSnapshot(UUID id, String key, String title, String status, Instant updated, Instant now);
```

### Actor + controller shape (copy from `ClientDeploymentController`)
```java
@PutMapping("/{itemId}/inputs")
public RoadmapItemResponse setInputs(@PathVariable UUID itemId,
                                     @Valid @RequestBody RiceInputsRequest request, Principal principal) {
    // Actor always from the principal, never the body (D-07-12). Business rules in RiceInputs, not the DTO.
    return respond(service.setInputs(itemId, request.toInputs(), principal.getName()));
}
```

### CSV cell (pattern)
```java
static String cell(String text) {
    String safe = (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) ? "'" + text : text;
    return needsQuoting(safe) ? '"' + safe.replace("\"", "\"\"") + '"' : safe;
}
```

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Jira `/rest/api/*/search` (410 Gone) | `/rest/api/3/search/jql` + `nextPageToken` | Already implemented in `JiraRestTicketAdapter`; do not add a second client `[CITED: CLAUDE.md]` |
| Terminate pagination on `isLast` | Terminate on absent `nextPageToken` | Already handled in the adapter |

RICE definition: Score = (Reach x Impact x Confidence) / Effort; Impact in {3, 2, 1, 0.5, 0.25}; Confidence percentage (commonly 100/80/50) `[CITED: Intercom RICE prioritisation, via v1.2 FEATURES.md; MEDIUM — not re-verified live this session]`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Jira answers a `key in (...)` query containing one nonexistent/invisible key with HTTP 400 "An issue with key ... does not exist" for the whole query (same text the adapter already special-cases for `key = X`) | Pitfall 1 | If Jira instead silently omits the key, the fallback is harmless extra logic; if 400 and unhandled, mass false "unavailable" flags |
| A2 | JQL `key = OLD-1` resolves moved issues to their new key; result key may differ from stored key | Pitfall 2 | Duplicate rows or false unavailable for moved issues |
| A3 | Items count stays in tens to low hundreds (in-memory ranking is fine) | Pattern 1 | Revisit SQL ordering past ~5k (ARCHITECTURE scale table) |
| A4 | OWASP CSV-injection prefixing (`'`) is the accepted mitigation | Pitfall 6 | Different spreadsheet behaviour; low |
| A5 | The RICE rubric (Intercom) is MEDIUM confidence; unit choices are the project's to define | Summary | Rubric must be confirmed by the TPMs |
| A6 | A pessimistic row lock plus full-replace PUT is adequate concurrency control (vs `@Version`) | Pattern 3 | Lost-update UX if two TPMs edit the same row simultaneously; history still records both |

## Open Questions for discuss-phase (user decisions, NOT decided here)

1. **Reach unit and type.** What does Reach count (clients affected per quarter, users per month, events)? Integer or decimal? Max value? (SUMMARY open question 6; PITFALLS suggests a single documented unit, e.g. clients per quarter.) Optional hint from Phase 15 client counts is a differentiator; in or out of this phase?
2. **Effort unit and precision.** Person-months (Intercom default) or points/weeks? Minimum (> 0 only, or e.g. >= 0.1)? Decimal places?
3. **Confidence input.** Free 0-100 (as ROAD-02 and the ROADMAP criterion say) or constrained picker {50, 80, 100}? The requirement text says free 0-100; PITFALLS recommends fixed levels. Which wins?
4. **Tie-break order.** Candidates: score desc, then Confidence desc, then Effort asc, then oldest `created_at`, then `id`. Sources disagree (PITFALLS: Effort asc then created_at; FEATURES: Confidence then title). Plus ordering inside the unscored bucket (created_at asc? title?). Should the tie-break be visible in the UI?
5. **Item lifecycle.** Requirements have no remove/edit. Allow edit of manual title/description and remove? Archive (consistent with products, keeps history, hidden by default) or hard delete (history cascade)? Should Jira items with status category Done be hidden by default?
6. **Manual item fields.** Title only, or also description/notes (plain text) and a rationale field?
7. **Where it lives in the UI.** New top-level "Roadmap" nav entry, or a tab on the Portfolio page (D-15-08 said Phases 16 and 17 "build on" Portfolio)?
8. **Refresh scope.** Per-item refresh only, refresh-all button, or both? Cap on number of keys per import/refresh? Persist the "unavailable" flag (recommended) or show it only after the last refresh?
9. **Visibility of Jira detail.** Roadmap membership and ranking are shared; Jira detail is fetched with the refreshing user's rights. Confirm "show last stored snapshot to everyone; flag unavailable" (recommended) vs hiding detail from viewers who cannot read the issue.
10. **Story points.** Never prefill Effort from Jira story points (recommended), or show them as a labelled hint the user must accept?
11. **Product link rules.** Same as D-15-07 (archived product keeps the link and an "archived" label, cannot be newly assigned)? Filter includes archived products? "Unassigned" filter option? Optional link to a release once shipped (FEATURES differentiator) deferred?
12. **CSV details.** Columns (rank, key, title, status, product, R, I, C, E, score, scored flag, last changed?), whether export respects the current product filter and archived/done visibility, UTF-8 BOM yes/no, include unscored rows (recommended yes, flagged), score rounding (2 dp).
13. **History granularity.** One row per changed field (recommended; simple "old to new" display) vs one row per save with all four old/new values. Should history also record product link and title changes, or RICE inputs only (ROAD-06 scope)?
14. **Phase 20 contract.** Confirm `RoadmapService.topRanked(n)` (id, key, title, score, product) as the only surface Phase 20 consumes.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 25 | Backend build | ✓ (needs `JAVA_HOME=$HOME/.jdks/jdk-25` per Maven call) | 25 | — |
| PostgreSQL (Testcontainers) | ITs, V16 | ✓ (used by existing ITs; Docker required) | 18.x | — |
| Node/npm + Vite | Frontend | ✓ (existing; watch Vite-vs-`npm ci` EPERM) | — | — |
| Live Jira site | Verifying A1/A2 | ✗ not probed (no credentials in research) | — | WireMock contract tests with captured responses |

No blocking missing dependencies.

## Security Domain

`security_enforcement` is enabled (ASVS level 1, block on high).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (inherited) | All `/api/**` already `authenticated()`; Jira calls use caller's own credential via `JiraAccessContextService` |
| V3 Session Management | inherited | Existing cookie auth, `SameSite=Strict`, `csrf.spa()` (state-changing calls send `X-XSRF-TOKEN` via `apiFetch`) |
| V4 Access Control | yes | Single permission level (project decision); actor from `Principal`, never request body; no per-item ACL |
| V5 Input Validation | yes | Bean Validation on DTO shape/size caps + domain `RiceInputs` rules + DB CHECKs; Jira key regex at boundary and sink |
| V6 Cryptography | no | No new secrets |
| V12 Files/Downloads | yes | CSV formula-injection neutralisation, fixed filename, `Content-Disposition: attachment` |

### Known Threat Patterns
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| JQL injection via key list | Tampering | Keys validated against `^[A-Z][A-Z0-9]*-[0-9]+$` before composing `key in (...)` (existing `ReleaseService` pattern) |
| CSV/formula injection | Tampering | Prefix risky leading characters in text cells (Pitfall 6) |
| Stored XSS via title | Tampering | Plain-text render only |
| Score manipulation | Tampering/Repudiation | Server-side validation + change history (who/when/old to new) |
| Jira write-back | Tampering | None exists; read-only scope; no POST/PUT to Jira beyond the JQL search POST |
| Mass import DoS / Jira rate limit | Denial of service | Key count cap, batch 100, no retry loops, surface `JIRA_RATE_LIMITED` |
| Cross-user data via Jira credentials | Information disclosure | Never expose Jira detail fetched under another user's rights beyond the stored summary/status snapshot (Open Question 9) |

## Sources

### Primary (HIGH confidence, read this session)
- `D:/IdeaProjects/ReleaseManagement/CLAUDE.md` — constraints (Jira read-only, `/search/jql`, provider interface)
- `.planning/ROADMAP.md` Phase 17 section; `.planning/REQUIREMENTS.md` ROAD-01..07
- `.planning/research/v1.2/ARCHITECTURE.md` (Decision 4, Anti-Pattern 5), `PITFALLS.md` (#4, #5, #6, threat table), `FEATURES.md` (section 2), `SUMMARY.md`
- `.planning/phases/15-products-clients-installations/15-CONTEXT.md` (D-15-07, D-15-08, patterns)
- Codebase: `ports/JiraTicketPort`, `adapters/jira/JiraRestTicketAdapter`, `application/TicketSearchService`, `JiraAccessContextService`, `ReleaseService` (fetchTickets/refresh), `ProductService/Controller/RepositoryAdapter`, `ClientDeploymentController`, `GlobalExceptionHandler`, `ExportFilename`, `ArchitectureBoundaryTest`, `V14__portfolio.sql`, `frontend/src/router.tsx`, `api/products.ts`, `api/tickets.ts`, `components/TicketBrowser.tsx`, `ExportButtons.tsx`, `api/client.ts`

### Secondary (MEDIUM)
- RICE definition and failure modes (Intercom), as recorded in the v1.2 FEATURES/PITFALLS research; not re-fetched live

### Tertiary (LOW)
- Jira behaviour for multi-key `key in (...)` with missing/moved keys (A1, A2): not verified live

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; all pieces verified in the codebase
- Architecture: HIGH — mirrors Phase 15 slice and ARCHITECTURE Decision 4
- Pitfalls: MEDIUM — codebase-derived ones are HIGH; Jira edge behaviour is `[ASSUMED]`

**Research date:** 2026-10-22
**Valid until:** 2026-11-21 (stable domain; re-check if Phase 15/16 change the Portfolio route or `product` schema)
