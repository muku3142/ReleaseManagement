# Phase 16: Portfolio Dashboard & Assistant Answers - Research

**Researched:** 2026-10-22
**Domain:** Spring Boot read model (Java 25 / Boot 4.1 / JPA native queries) + React 19 / TanStack Router search params + assistant tool protocol (rag-service intake limits)
**Confidence:** HIGH (all findings are from reading the codebase on `main`; no external library is introduced)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-16-01:** The dashboard gets two tabs. **Releases** is today's view: summary strip, release matrix and deployment history. **Portfolio** holds the new views. The selected tab is a URL search param, like the existing `all` flag.
- **D-16-02:** The product filter narrows **everything**: the Releases tab tiles, the release matrix, the deployment history and the Portfolio tab views. It lives in the URL, so it survives a reload and a shared link. A release in two selected products is counted once in every total (PDASH-01; needs a fixture-backed test).
- **D-16-03:** The filter is **multi-select**. Selecting nothing means all products.
- **D-16-04:** "Releases grouped by product" is a **per-product summary table**. Each product has one row with these columns: releases open / frozen / in Production; the newest frozen release; clients on the newest frozen release vs linked clients. Clicking a product row sets the filter to that product.
- **D-16-05:** A matrix cell shows: the release name/version, linking to release detail; the installed date; an amber "not in Production" marker when that release has not reached Production; a "behind" marker (see D-16-09..12). Same facts as Phase 15's client detail runs table. "Currently runs" comes from the `current_client_release` view (D-15-05) and is not re-derived.
- **D-16-06:** Two empty states: client not linked to the product: blank or a dash; linked but no effective successful install: "Not installed" (consistent with D-15-12).
- **D-16-07:** Archived clients and products are **hidden by default**. A "Show archived" checkbox brings them back, labelled "archived", as on the Portfolio page (D-15-07).
- **D-16-08:** The client name opens Portfolio > Clients > that client (existing client detail). The release in a cell links to the release detail page. No inline popover.
- **D-16-09:** The comparison target is the **newest FROZEN release that contains the product**, whether or not it has reached Production.
- **D-16-10:** "Newest" is ordered **by frozen date**, not by version.
- **D-16-11:** The view shows **how far behind**: "N releases behind". N is the number of frozen releases of that product that are newer than the one the client runs. The newest release's name is shown too.
- **D-16-12:** Linked clients with nothing installed form a **separate "Not installed" group** in the behind view. They are not counted as behind.
- **D-16-13:** "Need paperwork" tile: renamed **"Releases missing documents"**; help tooltip stating the rule; **clickable** (filters the release matrix). Current rule is `paperworkCounts` in `frontend/src/api/dashboard.ts:149`. Reverses the Phase 8 UI-SPEC rule "tiles are plain numbers, no filtering".
- **D-16-14:** "Stale" tile: renamed **"Documents out of date"**; tooltip (tickets changed after the document was generated; frozen releases never go stale, D-05-09); click-to-filter.
- **D-16-15:** Portfolio tab gets four tiles, all respecting the product filter: **Clients behind** (client/product pairs behind; click shows the behind list), **Not installed** (linked pairs with no successful install), **Active products / clients** (counts), **Installs last 30 days** (effective SUCCESS installs).

### Claude's Discretion
- **ASST-11 (not discussed; user accepted the default):** add an assistant tool that reads `current_client_release` and the ledger **live**, not the search index, so its answers always match the dashboard. Phase 12 kept the tool set consolidated, so either extend an existing tool or add one, whichever fits that design. It returns structured fields only, like the other tools. "Which clients run release X" uses `ReleaseRef` resolution.
- How a release that is frozen but later rolled back in promotion is treated as a "behind" target. The planner decides and states it; the expected default is that it still counts while it stays frozen.
- Tooltip mechanism, tile styling, and how the click-to-filter state is shown and cleared (e.g. a chip above the matrix).
- Whether the per-product summary and the matrix come from one dashboard endpoint or separate ones.

### Deferred Ideas (OUT OF SCOPE)
- Phase 21 License Keys and Phase 22 GitHub Publishing & Hosted Deployment (roadmap items only, not Phase 16 scope).
- AI deployment notes from commits is Phase 19, unchanged.
- Out of scope per phase boundary: recording or correcting installs (Phase 15), usage tracking (Phase 18), the daily planner (Phase 20).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PDASH-01 | Releases grouped by product, filter by product, no double count | Release-set filter semantics (EXISTS, never JOIN) in `DashboardService.releases` and `findDeployments`; per-product table + de-duplicated totals computed in Java from one ledger; fixture test design in Validation section |
| PDASH-02 | Client x product matrix of the release each client runs | New `PortfolioDashboardService` reads `current_client_release` (V14) + `release_product` + products/clients; no new migration |
| PDASH-03 | Clients behind the newest installable release of their product | "Behind" computed in Java from frozen releases per product ordered by `(frozen_at, id)`; Java chosen over SQL because "in Production" needs `PromotionHistory` anyway |
| ASST-11 | Assistant answers which release a client runs / which clients run a release | New `installs` tool needs rag-service intake limit bump (5 tools / 1500 chars) OR extend an existing tool; recommendation and trade-off below |
</phase_requirements>

## Summary

Phase 16 is a read-model and UI phase over data Phase 15 already created. **No new Flyway migration is needed**: `current_client_release` and `effective_client_deployment` (V14) give "what each client currently runs" and "effective SUCCESS installs"; `release_product` gives the product set of a release; `release.status/frozen_at` give the frozen ordering. This answers the V15-V18 numbering question: Phase 16 takes no number and nothing needs flagging. [VERIFIED: V14__portfolio.sql, release table columns in V3]

The dashboard today is a Java-side read model: `DashboardReadRepository.loadLedger()` loads every release, stage and promotion, `DashboardService.releases(all)` runs `PromotionHistory` per release to find current stage/shipped, then summarises. The product filter therefore threads in as a **release-id set filter applied once, right after the ledger is loaded** (before visibility, medians, per-stage counts, notPromoted). Because the filter is "release has ANY selected product" evaluated against a `Set<UUID>` of release ids, a release in two selected products is one element and cannot be double counted by construction. The only place a double count can sneak in is SQL that JOINs `release_product` (the deployments query): use `EXISTS`. The per-product table is the one deliberate place where a release appears under several products, and its `tfoot` must be the de-duplicated count.

The portfolio views (tiles, products table, behind list, not-installed group, matrix) should come from a new `PortfolioDashboardService` that loads once (ledger releases + promotions for "reached Production", `release_product`, products, clients, `current_client_release`, 30-day install count) and computes everything in Java, exposed as three read-only GET endpoints so each UI section loads and fails independently (UI-SPEC). The big ASST-11 trap is the Phase 11/12 **tool-definition budget**: rag-service rejects more than 5 tools or more than 1500 serialized characters, and `ToolDefinitionsTest` pins exactly 5 tools; a sixth tool means a coordinated change in rag-service config (main + test yml, `ToolPropertiesTest`) and the host `ToolDefinitionsTest`.

**Primary recommendation:** Add `productIds` (release-id set filter) to `DashboardService.releases/deployments`, add one `PortfolioDashboardService` computed in Java over existing tables (no migration), expose three `/api/dashboard/portfolio/*` GETs, and implement ASST-11 as a single new `installs` tool with the rag-service limits raised to 6 tools / 1900 chars in one explicit task (fallback: extend `releases`).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Product filter on release list, summary and history | API / Backend | Browser (URL state) | Medians, per-stage counts and "N visible releases" are computed server-side; filtering client-side would leave summary wrong. Browser owns only the URL param. |
| "Reached Production" per release | API / Backend | -- | Defined by `PromotionHistory.firstProductionSuccess` (effective/VOIDED/rollback rules exist once); never re-derive in SQL or UI |
| Currently-runs per client/product | Database (`current_client_release` view) | API | D-15-05 rule lives only in the view |
| Behind count / newest frozen target | API / Backend | -- | UI-SPEC: UI renders what it receives, never counts or compares dates |
| Per-product counts + de-duplicated totals | API / Backend | -- | Dedup is the PDASH-01 acceptance; must be testable server-side |
| Tab, product filter, docs filter, archived flag state | Browser (URL search params) | -- | D-16-01/02/07, no localStorage |
| Paperwork tile counts and docs filter | Browser | API (readiness endpoint) | Rule stays in `paperworkCounts` over server-computed readiness states (T-05-55); readiness failure degrades only these tiles (T-05-60) |
| ASST-11 answers | API / Backend (tool in-process) | rag-service (tool protocol/limits) | Tool reads live tables; rag-service only enforces intake caps |

## Standard Stack

No new dependency. Everything is the existing stack. [VERIFIED: codebase]

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot / Spring Web MVC | 4.1.0 | New read-only GET controllers | Existing `DashboardController` pattern |
| Spring Data JPA native `@Query` on bare `Repository` | Boot-managed | New read queries against views | Existing `DashboardJpaRepository` pattern (read-only marker interface, bound parameters, interface projections) |
| React 19, Vite, TypeScript | per `frontend/package.json` | Tabs, filter, tables | Existing |
| `@tanstack/react-router` | 1.170.18 (pinned) | `validateSearch` for `/` | Existing: `parseDashboardSearch` is the validator |
| `@tanstack/react-query` | existing | One hook per portfolio section | Existing `useQuery` + `apiFetch` pattern |
| JUnit 6 / AssertJ / Mockito / Testcontainers (pgvector image) | per CLAUDE.md | Tests | Existing `DashboardPersistenceIT`, `DashboardServiceTest` |
| Vitest 4 + jsdom + hand-rolled `createRoot`/`act` | existing | Frontend tests | Existing `frontend/tests/components/DashboardRoute.test.tsx` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Behind computed in Java | One SQL with window functions | SQL cannot answer "in Production" (needs `PromotionHistory`), so the Java path is needed anyway; a second SQL restatement of frozen ordering buys nothing at tens of releases. Choose Java. |
| New migration adding a `current_behind` view | -- | Rejected: no new view needed; avoids the V15-V18 numbering conflict entirely. |
| `releasesContaining` JOIN for filter | EXISTS / id-set | JOIN duplicates rows for multi-product releases (the exact PDASH-01 bug). Use set/EXISTS only. |

**Installation:** none. **Version verification:** not applicable (no package added).

## Architecture Patterns

### System Architecture Diagram

```
Browser  /?tab=&products=&docs=&archived=&all=&stage=&from=&to=
  |  validateSearch (parseDashboardSearch: drops malformed values)
  v
DashboardRoute
  |-- Product filter (details/fieldset) --- useProducts() (existing GET /api/products)
  |-- Releases tab
  |     |-- GET /api/dashboard/releases?scope=&productIds=a,b   --> DashboardService.releases(all, productIds)
  |     |        ledger = loadLedger() -> filter to releases in productIds (id Set, ANY-match)
  |     |        -> PromotionHistory per release -> visibility window -> summarize   (unchanged logic, smaller input)
  |     |-- GET /api/documents/readiness (existing, unfiltered) -> readinessIndex -> paperworkCounts + docs filter (client side)
  |     '-- GET /api/dashboard/deployments?...&productIds=      --> findDeployments(..., productIds)  (EXISTS on release_product)
  '-- Portfolio tab (three independent queries)
        |-- GET /api/dashboard/portfolio/summary?productIds=&includeArchived=  --> tiles + products table (+ dedup totals)
        |-- GET /api/dashboard/portfolio/behind?...                            --> behind[] + notInstalled[]
        '-- GET /api/dashboard/portfolio/matrix?...                            --> products[], clients[], cells[]
                         |
                         v
              PortfolioDashboardService.snapshot(productIds, includeArchived)   (one load, Java compute)
                reads: DashboardReadRepository.loadLedger()          (releases, stages, promotions -> inProduction)
                       release_product (all rows)                    (product sets, frozen target per product)
                       ProductRepository.findAll / ClientRepository.findAll   (names, archived, links)
                       current_client_release (all rows)             (what each client runs)
                       effective_client_deployment count (30 days)   (tile 4)

Assistant:  AssistantService --> ToolRegistry --> InstallsTool (new) --> PortfolioDashboardService / ClientService / ReleaseRef
```

### Recommended Project Structure
```
src/main/java/com/evision/releasemgmt/
  application/PortfolioDashboardService.java        # new; mirrors DashboardService (records for views, Clock injected)
  application/DashboardService.java                 # releases(all, Set<UUID> productIds); deployments(..., Set<UUID> productIds)
  application/assistant/tools/InstallsTool.java     # new (or extension, see ASST-11)
  adapters/web/DashboardController.java             # productIds params
  adapters/web/PortfolioDashboardController.java    # new, /api/dashboard/portfolio/*
  adapters/web/dto/PortfolioDashboardResponse.java  # new, nested records like DashboardResponse
  adapters/persistence/PortfolioReadRepositoryAdapter.java + repository/PortfolioJpaRepository.java + projection rows
  ports/PortfolioReadRepository.java                # read-only port (same "no mutation method" rule as DashboardReadRepository)
  domain/dashboard/ (or domain/portfolio/)          # pure behind computation + records (unit-testable without Spring)
frontend/src/api/dashboard.ts                        # search schema + portfolio hooks (or api/portfolioDashboard.ts)
frontend/src/routes/DashboardRoute.tsx               # tab shell; extract PortfolioTab + ProductFilter into components/
```

### Pattern 1: Product filter as a release-id set, applied right after the ledger load
**What:** `DashboardReadRepository` gains one method returning release ids covering any of the given products (`SELECT DISTINCT release_id FROM release_product WHERE product_id IN (:ids)`), or the service loads all `release_product` pairs once and filters in Java. `DashboardService.releases(all, productIds)` then does `ledger.releases().stream().filter(r -> allowed.contains(r.id()))` before the visibility loop. Everything downstream (visible list, `summarize`, `totalReleases`, medians, per-stage, notPromoted) is untouched and automatically consistent.
**When to use:** `productIds` empty means "no filter", skip the lookup entirely (do not pass an empty `IN ()` to SQL; the existing `findChurnItems` guards empty input the same way).
**Decision to state in the plan:** keep `Summary.totalReleases` = **all** releases (unfiltered), so the "No releases yet" state still means "none anywhere"; `visibleReleases` and `releases` are filtered. The new "No releases for these products" state is then `productIds.nonEmpty && totalReleases > 0 && releases.isEmpty()`. (`ReleaseMatrix` currently branches on `totalReleases === 0` and `releases.length === 0`; both branches need the filter-aware condition.) [VERIFIED: DashboardRoute.tsx:256-279]
**Example:**
```java
// Source: pattern from DashboardService.releases (existing); adaptation is the plan's
public DashboardView releases(boolean all, Set<UUID> productIds) {
    DashboardLedger ledger = repository.loadLedger();
    Set<UUID> inScope = productIds.isEmpty() ? null : repository.releaseIdsCoveringAny(productIds);
    for (DashboardRelease release : ledger.releases()) {
        if (inScope != null && !inScope.contains(release.id())) continue;
        ... // existing PromotionHistory / visibility logic
    }
}
```

### Pattern 2: Deployment history filter via EXISTS, never JOIN
```sql
-- Source: DashboardJpaRepository.deployments (existing) + one predicate
AND (:filterByProduct = false OR EXISTS (SELECT 1 FROM release_product rp
                                          WHERE rp.release_id = p.release_id AND rp.product_id IN (:productIds)))
```
Bind `:productIds` to a non-empty placeholder list when not filtering (Hibernate expands an empty collection to invalid SQL on some versions); the existing code uses `CAST(:stageId AS uuid) IS NULL` for the optional stage. Mirror that: pass a boolean flag plus the id collection, and have the adapter substitute a single nil UUID when the set is empty. The `LIMIT/OFFSET` paging stays correct only because EXISTS keeps one row per promotion (a JOIN would break `hasMore` and offsets).

### Pattern 3: Portfolio snapshot computed once in Java (behind, matrix, counts)
**What:** One private method builds an immutable `Snapshot` from the loaded data; the three endpoints (and the assistant tool) read slices of it.
**Algorithm (all in Java, pure, unit-testable):**
1. `frozenByProduct`: for each product, the frozen releases covering it, sorted by `(frozenAt, id)` ascending. Index in this list = rank. (Ties on `frozen_at` are broken by id so the order is total and deterministic.)
2. `newest(product)` = last element, or none.
3. For each `current_client_release` row `(client, product, release, installedAt)`: `behind = frozenByProduct[product].size() - 1 - rank(release)`. The running release is always in the list (installs require FROZEN; `release_product` rows referenced by installs cannot be removed, D-15-15; there is no unfreeze path: `grep -ri unfreeze src/main` finds nothing, `ReleaseStatus` is OPEN/FROZEN). If it is somehow absent, treat `behind = 0` rather than throwing (defensive, one line).
4. `behind > 0` -> row in the behind list; `behind == 0` -> "Up to date"; product has no frozen release -> no status.
5. Linked pair (`client.productIds` contains product) with no `current_client_release` row -> "Not installed" group (D-16-12), carrying `newest(product)` or null.
6. `inProduction(release)` = `PromotionHistory.firstProductionSuccess().isPresent()` for the release, built from the ledger promotions already loaded (same construction as `DashboardService.releases`). Do **not** call `promotions.load(releaseId)` per release (what `ClientService.detail` does with the comment "Phase 16 batches this for the matrix"); group `ledger.promotions()` by release id once.
**Decision on rolled-back target (CONTEXT discretion):** A release that is frozen but rolled back in promotion still counts as the "newest frozen" target while it is FROZEN. Rationale: D-16-09 defines the target by frozen status only, and ROLLED_BACK promotions never change `release.status`. State this in the plan and in the "Clients behind" help text is not needed (UI-SPEC copy already says "whether or not it has reached Production").
**Archived handling:** compute behind/newest over **all** frozen releases of the product regardless of archived flags (archiving hides rows, it must not change N). Apply `includeArchived=false` only when emitting rows/tiles/columns: drop archived clients, archived products, and pairs involving them. A product explicitly in `productIds` that is archived is still honoured (UI-SPEC assumption 7).
**Tile semantics:** `Clients behind` = count of behind pairs after archived filtering; `Not installed` = linked pairs without a current row; `Active products / clients` = non-archived counts (when `includeArchived` is on, all); `Installs last 30 days` = effective SUCCESS rows with `installed_at >= now - 30d` for non-archived (per `includeArchived`) clients/products in scope (see SQL below).

### Pattern 4: 30-day installs and the current-runs read (SQL against the views)
```sql
-- current runs for every client/product (single query; join release only for name/version/frozen_at)
SELECT c.client_id AS clientId, c.product_id AS productId, c.release_id AS releaseId, c.installed_at AS installedAt,
       r.name AS releaseName, r.version AS releaseVersion
  FROM current_client_release c JOIN release r ON r.id = c.release_id

-- tile "Installs last 30 days" (effective SUCCESS, so VOIDED and superseded rows are excluded by the view)
SELECT d.product_id AS productId, d.client_id AS clientId
  FROM effective_client_deployment d
 WHERE d.outcome = 'SUCCESS' AND d.installed_at >= :since
```
Return rows (not a bare count) so archived/product-filter rules are applied in the same Java pass as everything else and cannot disagree with the matrix. Volume is tiny. `:since` = `clock.instant().minus(Duration.ofDays(30))`, `Clock` injected (as `DashboardService` does) so tests are deterministic.
Index check: `client_deployment_client_product_idx (client_id, product_id, installed_at)` already exists; no new index is justified without a measured problem (CLAUDE.md optimisation rule). [VERIFIED: V14]

### Pattern 5: Search-param schema (frontend)
Extend `DashboardSearch` and `parseDashboardSearch` (the single validator already wired as `validateSearch: parseDashboardSearch` in `router.tsx:47`):
```ts
// Source: extends frontend/src/api/dashboard.ts parseDashboardSearch
export type DashboardSearch = {
  stage?: string; from?: string; to?: string; all?: boolean;
  tab?: 'portfolio';                 // absent = 'releases'
  products?: string[];               // uuid-validated, de-duplicated, capped
  docs?: 'missing' | 'stale';
  archived?: true;
};
```
Rules: `tab` only `'portfolio'` is stored (default dropped so URLs stay clean, like `all`); `products` = array filtered by the existing `UUID` regex, de-duplicated, capped at the same number the server accepts (use one shared constant, add a contract test like `DashboardSpanContractTest`), dropped to `undefined` when empty; accept both a JSON array (TanStack's default parse) and a single string (hand-typed URL). `docs` only the two literals; `archived` only `true`/`'true'`. TanStack Router 1.x JSON-serialises array search params (`?products=["id1","id2"]`); this is fine and survives reload/share, but it means `parseDashboardSearch` must tolerate already-parsed arrays and strings. [ASSUMED: default stringify of arrays is JSON; verify with a router test during implementation]
Tab switching clears `docs` and keeps `products`/`archived` (UI-SPEC) via `navigate({ to: '/', search: prev => ({...prev, tab, docs: undefined}), replace: true })`, exactly the existing `onToggleAll` idiom.
Wire the filter into: `useDashboardReleases(all, productIds)` (query key `['dashboard','releases',scope, ids.join(',')]`), `DeploymentFilter.productIds` (add to `deploymentsPath` and `deploymentsQueryKey`), and the three portfolio hooks (`['dashboard','portfolio','summary'|'behind'|'matrix', ids, archived]`). Keep the `['dashboard', ...]` prefix so existing `invalidateQueries({queryKey:['dashboard']})` calls cover them.

### Anti-Patterns to Avoid
- **JOIN on `release_product` in any list/aggregate query:** duplicates multi-product releases. Use `EXISTS` / id sets.
- **Counting releases per product and summing for the "total":** that is the PDASH-01 defect. Totals come from the de-duplicated release set.
- **Re-deriving "in Production" or "currently runs" in SQL or the UI:** only `PromotionHistory` and `current_client_release` own those rules.
- **Per-release `promotions.load(id)` in a loop** (N+1) for the matrix.
- **Computing behind/N in React:** UI-SPEC says it renders what it receives.
- **Using `docs` filter to narrow the summary tiles:** the strip is computed over the product-filtered set, not the `docs`-filtered set (UI-SPEC).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| "Reached Production" / current stage / shipped | A SQL restatement or UI logic | `PromotionHistory.firstProductionSuccess()` over the loaded ledger | Effective/VOIDED/rollback rules exist once (D-08-01/D-08-16) |
| "Currently runs" | `MAX(installed_at)` queries | `current_client_release` view | Single home of D-15-05; ROLLED_BACK/FAILED/VOIDED/superseded handled |
| Effective SUCCESS installs | Re-implementing supersede/void exclusion | `effective_client_deployment` view | Same |
| Release name resolution for the assistant | New fuzzy matching | `ReleaseRef.resolve` (exact name/version/"name version"/UUID, ambiguity candidates) | Already enforces length and ambiguity errors |
| Client/product name resolution for the assistant | Free-text LIKE | Case-insensitive exact match against `ClientRepository.findAll()` / `ProductRepository.findAll()`, ambiguity/no-match raised as `ToolArgumentException` with candidate list (same style as `ReleaseRef`) | Names are unique case-insensitively (`client_name_unique`, `product_name_unique` on `lower(name)`) so exact match is unambiguous; tiny tables |
| Problem+JSON validation errors | Custom exception types | `DashboardController.invalid(...)` (`ErrorResponseException`, reason `VALIDATION_FAILED`) | Existing contract; frontend `messageForReason` already maps it |
| Tab bar / "Show archived" markup | New components | Copy from `PortfolioRoute.tsx` / `PortfolioRegisterPanel.tsx` (optionally extract a shared `TabBar`) | UI-SPEC |

**Key insight:** The risk in this phase is semantic drift (two definitions of "runs", "behind", "in Production", "visible"), not technical difficulty. Every rule has exactly one existing home; the plan should route all new code through them.

## Runtime State Inventory

Not applicable: this is a read-model/UI phase, not a rename/refactor/migration. No stored data, service config, OS registration, secrets or build artifacts carry a renamed string. The only schema-adjacent finding is "no new migration needed". [VERIFIED: V14 views sufficient]

## Common Pitfalls

### Pitfall 1: Multi-product release double count
**What goes wrong:** Filtering by products [A,B] shows a release twice, medians weight it twice, `visibleReleases` is inflated, history rows duplicate and paging offsets shift.
**Why:** A JOIN on `release_product`, or summing per-product counts.
**How to avoid:** Id-set (`Set<UUID>`) filter + `EXISTS`; per-product table rows are the only multi-count surface and its `tfoot` uses the set.
**Warning signs:** `summary.visibleReleases` > distinct ids in `releases`; history `hasMore` flapping.

### Pitfall 2: Tool-definition budget breaks the assistant at runtime
**What goes wrong:** Adding a 6th tool passes host unit tests (after editing them) but rag-service answers every chat with a 400/`InvalidContentException("at most 5 tools")` because `rag.tools.max-tools: 5` and `definitions-max-chars: 1500` are enforced in `ToolSchema.requireValidDefinitions` (`ChatService.java:151`).
**Why:** Limits live in the separate `rag-service` project (`application.yml` lines 82-84, test yml line 78, `ToolPropertiesTest` line 63) and are mirrored by `ToolDefinitionsTest` in the host.
**How to avoid:** If a sixth tool is chosen, change all four places in one task and note that rag-service must be redeployed together with the host. If tools are served by a rag-service that is not yet updated, the whole assistant breaks (not just the new tool). Current definitions already use roughly 1250 of 1500 chars (estimate: five tools at ~250 each, [ASSUMED] - measure by running `ToolDefinitionsTest` after the change, it asserts the total).
**Warning signs:** `ToolDefinitionsTest.fiveUniquelyNamedToolsFitThePhase11Budget` fails; assistant chat returns tool-intake errors.

### Pitfall 3: Release-scoped chat overwrites tool arguments
**What goes wrong:** In a release-scoped chat, `AssistantService.execute` replaces **all** arguments with `{"release": <scope id>}` for any tool whose schema declares a `release` property (`declaresRelease`), and tools without a `release` property are not offered at all in scoped chats.
**Implication for ASST-11:** a tool with `release`, `client` and `product` properties would, when scoped, lose `client`/`product` and answer "which clients run THIS release" (which is actually the correct scoped behaviour for the "who runs release X" half) but never the "which release does client C run for product P" half. Decide explicitly: either accept that scoped chats only answer "who runs this release" (recommended; the client question is a portfolio-level question, not a release-level one), and make the tool handle `{release}`-only arguments cleanly; the client/product half is then available only in unscoped chat. Add a test pinning this.

### Pitfall 4: Dashboard cache not invalidated by install/link/archive mutations
**What goes wrong:** Recording an install, linking/unlinking a client, archiving a client/product, or editing a release's products leaves the Portfolio tab stale because those mutations invalidate only `['client']`, `['clients']`, `['products']`, `['installs', id]`, `['releases']` (`installs.ts:65-66`, `clients.ts:77,113-114`, `products.ts:25`, `releases.ts:229-231`) and not `['dashboard']`. UI-SPEC "Cache" requires them to.
**How to avoid:** Add `queryClient.invalidateQueries({ queryKey: ['dashboard'] })` to: record/correct/void install (`installs.ts`), link/unlink/archive/unarchive/add/rename client (`clients.ts`), add/rename/archive/unarchive product (`products.ts`), set release products (`releases.ts:229`). Test with the existing `runMutation` helper in `frontend/tests/api/portfolio.test.ts` (it inspects the cache after one mutation).

### Pitfall 5: Existing tests encode the old strings and signatures
**What goes wrong:** `DashboardControllerTest` stubs `service.releases(false)`/`releases(true)` (lines 98, 128); `DashboardToolTest` and `DashboardServiceTest` call `releases(boolean)`; `DashboardRoute.test.tsx` asserts "Need paperwork"/"Stale" (check) and builds `DashboardData` fixtures; `ToolDefinitionsTest` constructs tools by hand.
**How to avoid:** Change signatures once (`releases(boolean, Set<UUID>)`) and update all callers in the same task: `DashboardController:49`, `DashboardTool:70`, three tests. Do not keep a one-arg overload "for compatibility" (CLAUDE.md: no compat shims). `DeploymentsTool:115` calls `dashboardService.deployments(stageId, from, to, LIMIT, 0)`; add `Set.of()` there.

### Pitfall 6: Readiness degradation must survive click-to-filter
**What goes wrong:** With `docs=missing` in the URL and the readiness request failing, `stateOf` returns `undefined`, `?? 'NOT_GENERATED'` makes every release "missing", and the filtered matrix silently shows everything as missing.
**How to avoid:** When `readiness.isError`, ignore `docs` (render unfiltered, tiles `—` and `aria-disabled`, per UI-SPEC "Document status unavailable"). Keep `paperworkCounts` as the single rule and reuse it per release (extract a `needsPaperwork(releaseId, stateOf)` / `isStale(...)` predicate used by both `paperworkCounts` and the matrix filter so the tile figure equals the row count). Also unit-test `paperworkCounts` unit confirmation: both counts are **releases** (it increments once per release), so the UI-SPEC unit word "releases" is correct. [VERIFIED: dashboard.ts:149-159]

### Pitfall 7: Malformed or hostile `productIds`
**What goes wrong:** Unbounded `productIds` list, non-UUIDs, or unknown ids.
**How to avoid:** Server: `@RequestParam(required = false) List<UUID> productIds` (Spring converts; a non-UUID yields a 400 from `MethodArgumentTypeMismatchException`; verify the existing handler turns this into the same problem+JSON shape, otherwise map it); cap list size (e.g. 50) and reject larger with `invalid(...)`; de-duplicate into a `Set`. Unknown ids are not an error: they simply match nothing (no existence oracle needed; all authenticated users may read all products). Bound parameters only; never concatenate ids into SQL (existing repository comment: "Every value is a bound parameter").

### Pitfall 8: Frozen release product set is editable
**What goes wrong:** Assuming "frozen release contains product P" is immutable. `ReleaseProductRepository.replace` locks the row "without a status check (D-15-01)", so a frozen release can gain a product (and can lose one only if it has no install rows for it).
**Implication:** Compute the frozen target from the **current** `release_product` at request time, not from any cached/derived column. A newly added product on an already-frozen release legitimately becomes a newer target by frozen date order (it keeps its original `frozen_at`, so it may land mid-list, changing N for clients: correct per D-16-10 and worth one fixture).

### Pitfall 9: Windows/Maven environment (from project memory)
Run Maven with `export JAVA_HOME="$HOME/.jdks/jdk-25"` set per call; ITs use Testcontainers (Docker needed); `npx vitest run` can hit Vite-vs-`npm ci` EPERM. Another agent edits the `-live` worktree: do not touch it.

## Code Examples

### Fixture-backed no-double-count test (success criterion 1)
```java
// Source: pattern from DashboardPersistenceIT (Testcontainers, @SpringBootTest, TRUNCATE release CASCADE in @AfterEach)
// Fixture: products A and B; release R covers {A,B} and is FROZEN and promoted to Production;
//          release S covers {A} only (OPEN); release T covers {B} only (FROZEN, never promoted).
// 1. dashboardService.releases(true, Set.of(A, B)) -> releases ids == {R,S,T}, visibleReleases == 3 (R once),
//    perStage / notPromoted / medians each count R exactly once.
// 2. dashboardService.releases(true, Set.of(A)) -> {R,S};  Set.of(B) -> {R,T};  Set.of() -> all three.
// 3. dashboard.findDeployments(null, FROM, TO, 100, 0, Set.of(A, B)) -> exactly one row for R's promotion (not two);
//    paging: limit=1 offset=0/1 never repeats a promotion id.
// 4. portfolio summary with productIds {A,B}: products table has rows A and B each listing R,
//    tfoot totals: open=1 (S), frozen=1 (T), inProduction=1 (R)  == de-duplicated, sum of rows would be 4.
```

### Behind computation (pure domain function, unit-test without Spring)
```java
// Source: derived from D-16-09..12; plan finalises names
record FrozenRelease(UUID id, String name, String version, Instant frozenAt) {}

static int releasesBehind(List<FrozenRelease> frozenOfProduct /* sorted by frozenAt, id */, UUID runningReleaseId) {
    int rank = indexOf(frozenOfProduct, runningReleaseId);
    return rank < 0 ? 0 : frozenOfProduct.size() - 1 - rank;
}
```
Unit cases: running newest -> 0; running oldest of 3 -> 2; equal `frozen_at` tie broken by id; product with no frozen release -> no status; running release absent from list -> 0; client who installed an older release after a newer one (current = latest `installed_at`) -> behind computed from the running release, not install order.

### Controller parameter handling
```java
// Source: pattern from DashboardController.releases / invalid()
@GetMapping("/releases")
public DashboardResponse releases(@RequestParam(defaultValue = "active") String scope,
                                  @RequestParam(required = false) List<UUID> productIds) {
    ...
    return toResponse(service.releases(all, productScope(productIds)));   // productScope: null->empty, size>MAX_PRODUCT_FILTER -> invalid(...)
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Dashboard tiles are plain numbers (08-UI-SPEC assumption 1) | Two tiles are clickable filters with help text | D-16-13/14 | Tile components become `<button aria-pressed>`; update 08-era tests |
| `ClientService.detail` loads promotions per release | Portfolio snapshot groups the already-loaded ledger promotions | this phase (the code comment anticipates it) | No N+1 across the whole matrix |

**Deprecated/outdated:** none relevant; no library changes.

## ASST-11 design (recommendation)

**Facts.** Five tools exist (`dashboard`, `deployments`, `documents`, `releases`, `tickets`). Phase 11 intake caps: max 5 tools, 1500 chars of serialized definitions, description <= 300 chars, flat schema keys only (`type, properties, required, additionalProperties`; property keys `type, description, enum, maxLength`; types string/integer/number/boolean). `AssistantProperties.prompts.toolDescriptions` (host `application.yml` lines 74-79) overrides code descriptions per tool and is validated <= 300 chars; keep both in step when adding a tool. Scoped chats pin `release` (Pitfall 3). Tool results are JSON of structured fields only (D-12-06), capped by `toolResultMaxChars` (8000), citations come from `ToolOutput(sourceId, sourceTitle)`.

**Options.**
1. **New tool `installs` (recommended).** Parameters `release` (maxLength `ReleaseRef.MAX_LENGTH`), `client` (maxLength 100), `product` (maxLength 100). Behaviour: `release` given -> clients currently running it (`[{client, product, installedAt, archived}]`, capped, `total`, plus `inProduction`); else `client` (+ optional `product`) -> that client's current release per linked product (`runs[]` with `release`, `version`, `installedAt`, `inProduction`, and `state`: `RUNS` / `NOT_INSTALLED` / `NOT_LINKED`, matching the three UI-SPEC answer shapes). Needs ~330 serialized chars. Requires raising rag-service `max-tools` 5 -> 6 and `definitions-max-chars` 1500 -> about 1900 in rag-service `application.yml` and test `application.yml`, `ToolPropertiesTest`, and `ToolDefinitionsTest` (`MAX_TOOLS`, `DEFINITIONS_MAX_CHARS`, the added `new InstallsTool(...)`). A larger definitions block costs prompt tokens on every chat (4096-token budget, D-12-13), so keep the description to one short sentence and omit parameter descriptions.
2. **Extend `releases`:** with `release`, add a `clientsRunning` list to the existing result; add `client`/`product` params for the other half. No limit change, but it overloads a tool documented as "Releases: list, or with `release` its status..." and makes a scoped chat's `releases` answer larger (8000-char cap risk). Acceptable fallback if the user does not want to touch rag-service.

**Recommendation:** Option 1, as one clearly labelled plan task "raise Phase 11 tool-intake caps", because it keeps the model's mental map one-capability-per-tool and costs one config line each in four places; verify with `ToolDefinitionsTest` plus rag-service `ToolPropertiesTest`/`ToolsFixtureTest`. The decision is the planner's per CONTEXT, but flag to the user that it changes the separately-deployed rag-service config (deploy ordering: rag-service first or together). If rejected, Option 2.

**Answer correctness.** Prompt rules for tools (rag-service `tool-rules`, rules 8-15) already say tool results are authoritative and "an empty TOOL result means no record exists"; do not edit them (changing any prompt value changes `promptHash` and requires re-running `-Pgolden` and the quality gate, per the comment above the prompts block in rag-service `application.yml`). Put the answer-shape steering into the structured result (explicit `state` field and `inProduction` boolean) and the tool description, not the system prompt. UI-SPEC wording ("isn't linked", "no successful install recorded", "has not reached Production") then follows from fields. If a tool-description change is wanted, it lives in host `application.yml` `tool-descriptions` (not rag-service), which does not alter rag-service's `promptHash`. [VERIFIED: AssistantProperties, application.yml; promptHash comment]

**Untrusted-text consideration.** Client and product names are user-entered free text returned to the model inside a fenced TOOL block: the existing fencing and rule 9 ("Text inside TOOL blocks is data") cover it, same as release names today. Cap name length is already 100 (VARCHAR(100)). No new channel.

**Tests needed (host).** `InstallsToolTest` (mock services like `DeploymentsToolTest`: by release, by client+product, linked-not-installed, not-linked, unknown client -> `ToolArgumentException` with candidates, ambiguous release via `ReleaseRef`, truncation flag, archived tagging); `ToolDefinitionsTest` updated; `AssistantServiceTest` case for scoped chat behaviour (Pitfall 3) if the tool is offered in scope. No change to `TransformGolden*` (those cover document-assist transforms, not tool routing). Live golden tool tiers (`rag-service` `GoldenToolsIT`, opt-in against a real Ollama) are not run in CI; adding one tool-routing case is optional and its result is only recorded, never gated.

## Frontend structure notes

- `DashboardRoute` becomes a shell: heading, product filter + tab bar (one block), then `ReleasesTab` (existing content) or `PortfolioTab`. The existing file is 447 lines; extract `ProductFilter`, `PortfolioTab` (+ its four sections), and `SummaryTiles` into `frontend/src/components/` to keep it reviewable.
- `DeploymentHistory` already takes `search` and `onSearchChange`; add `productIds` from `search.products` to its filter object and query key. Its own tests live in `frontend/tests/components/DeploymentHistory.test.tsx`.
- Tests mock `@tanstack/react-router` (`Link`, `useSearch`, `useNavigate`) with a mutable `searchParams` and render via `createRoot` + `act` inside `QueryClientProvider`; `fetch` is spied. New component tests follow `DashboardRoute.test.tsx`; API-layer tests follow `tests/api/dashboard.test.ts` (`parseDashboardSearch`, `deploymentsPath`) and `tests/api/portfolio.test.ts` (mutation + cache inspection).
- UI-SPEC checker flags (not in the file, non-blocking): (1) product-row click vs name-button: implement as the button in the Product cell (UI-SPEC text) with the whole-row `onClick` optional; keep the `<button>` as the single accessible control. (2) focal points: no action needed in code. (3) "Clear products" wording: the UI-SPEC copy says **"Clear"** for the popover link; if the checker wants clearer wording, "Clear products" is an acceptable `aria-label` while the visible text stays "Clear". Treat as a copy decision for the planner; do not block.
- Query-key hygiene: include `[...productIds].sort().join(',')` in keys so `?products=[a,b]` and `[b,a]` share cache.
- Matrix size: columns = products, rows = clients, each cell O(1) lookups; the API should return `cells` as a flat list keyed `(clientId, productId)` plus `products[]` and `clients[]` (already filtered and ordered), so the UI builds a map once. Do not return a nested N x M grid with nulls.

## API sketch (for the planner; names are suggestions)

```
GET /api/dashboard/releases?scope=active|all&productIds=<uuid>,<uuid>      (existing + param)
GET /api/dashboard/deployments?...&productIds=                               (existing + param)
GET /api/dashboard/portfolio/summary?productIds=&includeArchived=false
  -> { tiles:{clientsBehind,notInstalled,activeProducts,activeClients,installsLast30Days},
       products:[{id,name,archived,open,frozen,inProduction,newestFrozen:{releaseId,name,version,frozenAt}|null,
                  linkedClients,clientsOnNewest}],
       totals:{open,frozen,inProduction}, unassigned:{open,frozen,inProduction}|null }
GET /api/dashboard/portfolio/behind?productIds=&includeArchived=
  -> { behind:[{clientId,clientName,clientArchived,productId,productName,runs:{releaseId,name,version,installedAt,inProduction},
                releasesBehind,newest:{releaseId,name,version,frozenAt}}],
       notInstalled:[{clientId,clientName,clientArchived,productId,productName,newest|null}] }
GET /api/dashboard/portfolio/matrix?productIds=&includeArchived=
  -> { products:[{id,name,archived}], clients:[{id,name,archived}],
       cells:[{clientId,productId,linked:true,run:{...}|null,releasesBehind:int|null,newest|null,upToDate:boolean}] }
```
All three share the one `snapshot(productIds, includeArchived)` so numbers cannot disagree. "Active products" tile counts products in scope after the archived rule; "linkedClients/clientsOnNewest" in the products table count non-archived clients unless `includeArchived`. `clientsOnNewest` = linked clients whose current release for the product is the newest frozen one. Releases per status for the table: `open` = status OPEN; `frozen` = FROZEN and not yet in Production; `inProduction` = reached Production (UI-SPEC column definitions). A frozen release rolled back after reaching Production still counts as "in Production" because `firstProductionSuccess` is sticky (D-08-16); say so in the plan.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 25 | Backend build/tests | ✓ (path from project memory) | `$HOME/.jdks/jdk-25` | -- |
| Maven | Backend build | ✓ (per project workflow) | -- | -- |
| Docker / Testcontainers (pgvector image) | `*IT` tests incl. the no-double-count IT | ✓ assumed from existing ITs (not probed this session) | -- | Unit tests with a fake `DashboardReadRepository` cover the Java logic; the SQL `EXISTS` predicate needs the IT |
| Node + npm + Vitest | Frontend tests | ✓ assumed (not probed) | Vitest ^4 | EPERM caveat in project memory |
| Ollama | Live golden tool tests only | not required for this phase | -- | Opt-in tier, skipped |

[ASSUMED: tool availability taken from project memory and existing test setup; not probed in this session because the research task forbids side effects on the live worktree.]

## Validation Architecture

(`workflow.nyquist_validation` is `false` in `.planning/config.json`; included because the orchestrator requested it.)

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 6 + AssertJ + Mockito + Testcontainers (backend); Vitest 4 + jsdom (frontend) |
| Config file | `pom.xml` / `frontend/vitest` config in `package.json` (`"test": "vitest run"`) |
| Quick run command | Backend: `export JAVA_HOME="$HOME/.jdks/jdk-25" && mvn -P '!frontend' test -Dtest=DashboardServiceTest,PortfolioDashboardServiceTest,DashboardControllerTest,InstallsToolTest,ToolDefinitionsTest`; Frontend: `cd frontend && npx vitest run tests/api/dashboard.test.ts tests/components/DashboardRoute.test.tsx` |
| Full suite command | Backend: `export JAVA_HOME="$HOME/.jdks/jdk-25" && mvn -P '!frontend' test`; Frontend: `cd frontend && npx tsc -b && npx vitest run`; rag-service (only if limits change): `cd rag-service && mvn test` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PDASH-01 | Release in 2 selected products counted once (releases list, summary, medians, per-stage, notPromoted) | unit (service, fake repo) | `mvn -P '!frontend' test -Dtest=DashboardServiceTest` | ✅ extend |
| PDASH-01 | Deployments query returns one row per promotion under multi-product filter; paging stable | integration (Testcontainers) | `mvn -P '!frontend' test -Dtest=DashboardPersistenceIT` | ✅ extend |
| PDASH-01 | Products table rows vs de-duplicated `tfoot` totals (fixture R in A and B) | unit + IT | `-Dtest=PortfolioDashboardServiceTest,PortfolioDashboardPersistenceIT` | ❌ Wave 0 |
| PDASH-01 | `parseDashboardSearch` drops malformed `products`/`tab`/`docs`/`archived`; array/string tolerance | unit (vitest) | `npx vitest run tests/api/dashboard.test.ts` | ✅ extend |
| PDASH-02 | Matrix cells: not linked / Not installed / runs (+ not in Production); archived hidden by default; uses current_client_release (incl. FAILED/ROLLED_BACK/VOIDED/superseded ignored) | IT (view) + unit | `-Dtest=PortfolioDashboardPersistenceIT,PortfolioDashboardServiceTest` | ❌ Wave 0 |
| PDASH-03 | Behind N by frozen date, ties, older-install-after-newer, no frozen release, rolled-back-but-frozen target, Not installed group excluded from behind, archived does not change N | unit (pure function) | `-Dtest=ClientsBehindTest` | ❌ Wave 0 |
| PDASH-03 | Product frozen-set change (product added to frozen release) shifts N | IT | `-Dtest=PortfolioDashboardPersistenceIT` | ❌ Wave 0 |
| D-16-13/14 | Tiles are buttons, `aria-pressed`, help toggle outside the button, readiness failure disables, `docs` ignored on readiness error, chip count equals tile | component (vitest) | `npx vitest run tests/components/DashboardRoute.test.tsx` | ✅ extend |
| D-16-15 | Portfolio tiles/sections render, independent errors/loading, product-row click sets/clears filter, scroll/focus targets | component | `npx vitest run tests/components/DashboardPortfolioTab.test.tsx` | ❌ Wave 0 |
| Cache | Install/link/archive/product-edit mutations invalidate `['dashboard']` | unit (`runMutation`) | `npx vitest run tests/api/portfolio.test.ts` | ✅ extend |
| Contract | Frontend and server agree on max product-filter size | unit | `-Dtest=DashboardSpanContractTest` (extend) | ✅ extend |
| ASST-11 | `installs` tool: by release, by client(+product), 3 states, ambiguity/unknown, cap | unit | `-Dtest=InstallsToolTest` | ❌ Wave 0 |
| ASST-11 | Tool budget: tool count, description <= 300, definitions total <= new cap, flat schema | unit | `-Dtest=ToolDefinitionsTest` | ✅ update |
| ASST-11 | rag-service accepts 6 tools / new char cap | unit | `cd rag-service && mvn test -Dtest=ToolPropertiesTest,ToolsFixtureTest` | ✅ update |
| ASST-11 | Assistant answer matches dashboard figures | IT: assert tool result equals `PortfolioDashboardService` matrix for the same fixture | `-Dtest=InstallsToolIT` or fold into the portfolio IT | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** the targeted `-Dtest=` / single vitest file for the touched area.
- **Per wave merge:** full backend suite and `npx tsc -b && npx vitest run`.
- **Phase gate:** both full suites green (plus rag-service suite if its yml changed) before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `PortfolioDashboardServiceTest`, `ClientsBehindTest` (pure), `PortfolioDashboardPersistenceIT` (fixtures: multi-product release, frozen-order ties, archived rows, voided/superseded installs)
- [ ] `InstallsToolTest`
- [ ] `frontend/tests/components/DashboardPortfolioTab.test.tsx` and `ProductFilter` test
- [ ] Shared test fixture builders for products/clients/installs (reuse `ClientDeploymentPersistenceIT` / `PortfolioPersistenceIT` helpers; check them before writing new ones)

## Security Domain

### Applicable ASVS Categories (Level 1, `security_enforcement: true`, block on high)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no change | Existing JWT-in-cookie; new routes sit under `/api/**` which `SecurityConfig` requires authenticated (only login/refresh/logout, jira-site GET and actuator health are `permitAll`) [VERIFIED: SecurityConfig.java:43-45] |
| V3 Session Management | no change | Existing; read-only GETs, no CSRF exposure (CSRF rests on SameSite=Strict per project decisions) |
| V4 Access Control | yes | The app has a single authenticated-user model for portfolio data (`ClientController`/`ProductController` expose the same data to any authenticated user). New endpoints follow the same rule; add a controller/IT test that an unauthenticated request gets 401. No per-record ownership exists to enforce. |
| V5 Input Validation | yes | `productIds`: typed `List<UUID>`, size cap, de-dup, bound parameters; `includeArchived` boolean; `scope` already whitelisted; frontend `parseDashboardSearch` drops malformed URL params (URL is attacker-controllable) |
| V6 Cryptography | no | None involved |
| V7 Error handling/logging | yes | Problem+JSON via existing handler; no stack traces; assistant tool errors are `ToolArgumentException` messages containing only user-supplied text and candidate names |
| V12/V13 Output & API | yes | React text children only (UI-SPEC "Plain-text safety"); no `dangerouslySetInnerHTML` for product/client/release names |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection via filter ids | Tampering | Bound parameters only in native queries; ids typed as UUID before reaching the repository |
| Resource exhaustion via huge `productIds` or unbounded matrix | DoS | Cap `productIds` (server + shared frontend constant, contract-tested); matrix size bounded by catalogue size (tens); assistant results capped (`MAX_ROWS`, `total` + `truncated`) and by `toolResultMaxChars` |
| Stored XSS through product/client names (user-entered, 100 chars) | Tampering / Info disclosure | React auto-escaping; never HTML-render names; tool results are JSON-fenced for the model |
| Prompt injection through client/product names in tool results | Tampering | Existing fencing and tool rule 9 (data, never instructions); structured fields only (D-12-06); no new free-text field is returned |
| Scope-leak in release-scoped assistant chat | Info disclosure | `AssistantService` forces the scoped release into tool arguments; the new tool must be safe when only `release` is supplied (test it); do not add a path that lets a scoped chat read other releases' installs |
| Open redirect / param tampering in URL state | Tampering | `validateSearch` normalisation; links are router `Link`s built from ids |
| Audit-trail mutation | Tampering | All new ports are read-only (no mutation methods, same rule as `DashboardReadRepository`); `client_deployment` append-only trigger untouched |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | TanStack Router 1.170.x serialises array search params as JSON by default (`?products=["id"]`) and hands them to `validateSearch` already parsed | Pattern 5 | URL shape differs; validator may need to accept strings only. Cheap to verify with a router test; behaviour is tolerant in either case if the validator accepts string and array |
| A2 | Current five tool definitions use roughly 1250 of the 1500 serialized characters | Pitfall 2 | If headroom is larger, a 6th tool might fit under the existing char cap; the 5-tool cap still blocks it. Measure by running `ToolDefinitionsTest`. |
| A3 | Spring maps a non-UUID `List<UUID>` request param to a 400 problem+JSON shaped like the existing `VALIDATION_FAILED` | Pitfall 7 | A differently shaped 400 reaches the UI; add a controller test and map in `GlobalExceptionHandler` if needed |
| A4 | Docker/Node/JDK are available as per prior phases | Environment | Tests cannot run locally; project memory lists known landmines |
| A5 | Counting a frozen release rolled back after Production as "in Production" in the products table (sticky `firstProductionSuccess`) is the desired reading of "in Production" | API sketch | Counts differ by rolled-back releases; consistent with the Releases tab "shipped" semantics, so low risk. User may confirm |
| A6 | Option 1 (new `installs` tool + raising rag-service caps) is acceptable to the user | ASST-11 | If not, fall back to extending `releases` (Option 2); decision flagged for the planner/user |

## Open Questions

1. **ASST-11: new tool vs extend `releases`?**
   - Known: new tool needs rag-service cap changes in a separate project; extension needs none but overloads `releases`.
   - Unclear: whether the user accepts changing and co-deploying rag-service config.
   - Recommendation: Option 1, isolated in its own task with explicit deploy note; fall back to Option 2 if declined.
2. **Scoped assistant chats and the `installs` tool.** Recommend: offered when scoped (declares `release`), answers "which clients run this release" only; client/product questions are unscoped-only. Confirm acceptable.
3. **`totalReleases` semantics under a product filter.** Recommendation above: keep global. Confirm with the UI empty-state wording.
4. **Max `productIds` size.** Suggest 50; one constant mirrored in `dashboard.ts` and the controller with a contract test.
5. **Single vs three portfolio endpoints.** Recommendation: three endpoints over one shared service snapshot (matches UI-SPEC independent failure). Planner may merge into one if it accepts a single loading/error state.

## Sources

### Primary (HIGH confidence, read from the repository on `main`)
- `src/main/resources/db/migration/V14__portfolio.sql` (views, constraints, indexes, append-only trigger)
- `application/DashboardService.java`, `adapters/web/DashboardController.java`, `adapters/persistence/DashboardReadRepositoryAdapter.java`, `repository/DashboardJpaRepository.java`, `ports/DashboardReadRepository.java`, `domain/dashboard/*`
- `application/ClientService.java`, `ClientDetail.java`, `ports/ClientDeploymentRepository.java`, `ClientRepository.java`, `ProductRepository.java`, `ReleaseProductRepository.java`, `adapters/web/ClientController.java`, `ProductController.java`, `adapters/persistence/ClientDeploymentRepositoryAdapter.java`
- `application/assistant/AssistantService.java`, `tools/*` (`ToolRegistry`, `AssistantTool`, `DashboardTool`, `DeploymentsTool`, `ReleaseRef`, `ToolOutput`), `config/AssistantProperties.java`, host `src/main/resources/application.yml` (tool-descriptions)
- `rag-service/src/main/resources/application.yml` (`rag.tools`, prompts, promptHash comment), `config/ToolProperties.java`, `domain/ToolSchema.java`, `application/ChatService.java:151`
- `src/test/.../ToolDefinitionsTest.java`, `DashboardPersistenceIT.java`, `DashboardSpanContractTest.java`, `DashboardControllerTest.java`
- `frontend/src/api/dashboard.ts`, `routes/DashboardRoute.tsx`, `router.tsx`, `api/installs.ts`, `clients.ts`, `products.ts`, `releases.ts`, `frontend/tests/components/DashboardRoute.test.tsx`, `tests/api/portfolio.test.ts`
- `16-CONTEXT.md`, `16-UI-SPEC.md`, `REQUIREMENTS.md`, `CLAUDE.md` (project and global), `.planning/config.json`, `.planning/milestones/v1.1-phases/12-.../12-CONTEXT.md` (D-12-06, D-12-13), `docs/assistant-security.md`

### Secondary / Tertiary
- None. No external documentation was needed; no library is introduced. TanStack array search-param serialisation is an unverified assumption (A1).

## Project Constraints (from CLAUDE.md)

- Backend Java Spring Boot; Boot 4.1 / Java 25 baseline; frontend React SPA. Follow the existing hexagonal layout (ports, adapters, application services); prefer editing existing files; no over-engineering or compat shims; no dead code or commented-out code; comments only for non-obvious WHY.
- Enterprise-grade: secure, observable (log tool/endpoint outcomes the way `AssistantService` does), maintainable; SOLID; validate only at system boundaries (the new `productIds`/`includeArchived` params and tool arguments).
- Optimisation rule: do not add caching/indexes/batching without a measured bottleneck (justifies no new index or migration).
- Security: never commit secrets; sanitize external input; Jira read-only (unaffected).
- Git: stage specific files; commit messages explain WHY; no `Co-Authored-By` trailers (user CLAUDE.md overrides the attribution reminder); never merge master into main; orchestrator commits this research.
- GSD workflow enforcement: file edits only through a GSD command; **research must not edit code and must not touch the concurrently edited worktree `D:\IdeaProjects\ReleaseManagement-live`**.
- Project memory landmines: JAVA_HOME per Maven call, Vite-vs-`npm ci` EPERM, plan grep criteria are brittle (assert properties, not counts), ROADMAP/STATE writes can silently no-op (verify).
- The CLAUDE.md note that the ADO PAT/Jira constraints apply to other phases; nothing in Phase 16 calls Jira or ADO (dashboard reads this system's tables only, preserving the Phase 8 criterion 6 invariant: no Jira or ADO call on the dashboard path).

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, no new dependencies, all patterns read from the repo.
- Architecture (filter threading, Java-side behind, no migration): HIGH, derived from reading the actual queries and views.
- ASST-11 budget/scoping behaviour: HIGH on the limits and scope-pinning (read in code); MEDIUM on the exact remaining char headroom (A2).
- Pitfalls: HIGH (cache invalidation gaps and signature churn verified by grep).

**Research date:** 2026-10-22
**Valid until:** 2026-11-21 (stable; invalidated by changes to Phase 15 views or Phase 17+ migrations touching `release_product`/`client_deployment`)
