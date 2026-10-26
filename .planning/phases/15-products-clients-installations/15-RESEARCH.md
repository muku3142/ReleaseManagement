# Phase 15: Products, Clients & Installations - Research

**Researched:** 2026-10-20
**Domain:** Spring Boot 4 / JPA / Flyway V14 schema, append-only ledger (copy of the `promotion` pattern), Mustache view-model extension, React + TanStack admin UI
**Confidence:** HIGH (everything below was read from this repo's code; no new libraries are involved)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Carried forward (decided at milestone scoping, not re-asked)**
- Client <-> product is many-to-many (`product_client`).
- An install requires the release to be FROZEN and to cover the product; if the release has not reached the Production stage, show a warning but allow it.
- `client_deployment` is its own ledger, a copy of the `promotion` pattern: trigger-enforced append-only, corrections via `supersedes_id`, composite FKs `(release_id, product_id) -> release_product` and `(product_id, client_id) -> product_client`, current state derived on read. It carries no stage.
- Flyway V14 (pre-assigned). Test it against a realistic v1.1 snapshot, not only an empty DB.
- Products and clients are archived, never deleted.

**Products on releases**
- **D-15-01:** A release's products can be changed at any time, while OPEN and after FROZEN, so existing v1.1 releases (all frozen) can be tagged and receive installs. Guard: a product cannot be removed from a release once any install record exists for that release + product (the composite FK enforces this; the service returns a clear error first).
- **D-15-02:** Any product change, on OPEN or FROZEN releases alike, bumps `releases.items_changed_at`, so existing documents show stale. Research must confirm that documents of a FROZEN release can be regenerated; if they cannot, flag it to the user rather than silently changing the rule.
- **D-15-03:** The create-release form gets an optional multi-select product picker; products are also editable on the release detail page. A release with zero products is valid and shows "Unassigned".

**Install record shape**
- **D-15-04:** Outcomes mirror promotion: `SUCCESS | FAILED | ROLLED_BACK`, plus `VOIDED` only as a correction (same CHECK as `promotion_voided_only_by_correction`).
- **D-15-05:** "Currently runs" for (client, product) = the effective row (not superseded, not VOIDED) with outcome SUCCESS and the newest `installed_at`, tie-broken by `recorded_at`. FAILED and ROLLED_BACK rows never change what a client runs. Back-dating an older install never overrides a newer one. Keep the effective rule in one place (e.g. an `effective_client_deployment` view) because Phase 16 reads it too.
- **D-15-06:** Corrections work like promotion corrections: "Correct" opens a form prefilled from the original (release, installed time, outcome, note) with a required reason and writes a superseding row; "Void" writes a VOIDED superseding row. The original stays visible, shown as superseded.
- **D-15-07:** Archived products and clients keep all links and history (shown with an "archived" label) but cannot be newly assigned to a release or receive new installs. They can be unarchived.

**Where things live in the UI**
- **D-15-08:** A new top-level "Portfolio" nav page with Products and Clients tabs handles create, rename, archive/unarchive and client <-> product links. Settings is not used for this. Phases 16 and 17 build on this page.
- **D-15-09:** Installs are recorded only from the release detail page, in a "Client installs" section shown for FROZEN releases: pick a product of the release, tick one or more of that product's active clients, and set the installed time (default now, back-datable), outcome and note. Ticking several clients is the bulk install (PORT-07). The not-in-Production warning appears in this form. The release's install history is listed in the same section.
- **D-15-10:** A bulk install is all or nothing: one transaction; if any selected client is invalid, nothing is saved and the response names the offending clients.

**Per-client "runs" view**
- **D-15-11:** PORT-06 is a client detail view on the Portfolio page: each product linked to the client with its current release (name, version, installed date, not-in-Production marker), followed by the client's full install history including superseded rows. The cross-client matrix is Phase 16.
- **D-15-12:** A linked product with no effective SUCCESS install shows "Not installed" explicitly; it is not hidden.
- **D-15-13:** A client <-> product link cannot be removed once the client has install records for that product (the composite FK blocks it). The UI explains why and offers archiving the client instead. Links without installs can be removed.

### Claude's Discretion
- Template placeholder shape for PORT-08 (e.g. `release.products` as a list of `{name}` in `ReleaseView`, additive, still within the safe-template allow-list rules).
- Name uniqueness (case-insensitive unique product names and client names), length limits, sort orders.
- Endpoint paths, DTO shapes, row-lock discipline (copy `PromotionService.appendUnderLock`).
- Whether a client-product link can be added to an archived product (keep consistent with D-15-07).
- Audit-trail entries for create/rename/archive/link/install, following the existing audit pattern.

### Deferred Ideas (OUT OF SCOPE)
- Recording installs from the client page (only release detail in this phase).
- Soft-unlinking a client from a product while keeping history (rejected in favour of archiving the client).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PORT-01 | Create, rename, archive products | `product` table with `archived_at`, `lower(name)` unique index; `ProductService`/`ProductRepository` copy of `EnvironmentLadderService` retire semantics (section Patterns 5) |
| PORT-02 | Create, rename, archive clients; link client to one or more products | `client`, `product_client`; link/unlink with FK-backed guard (Pitfall 2) |
| PORT-03 | Assign products to a release; legacy releases show "Unassigned" | `release_product` (M:N, zero rows = Unassigned, no backfill); `ReleaseProductRepository.replace` under release row lock, bypassing `requireOpen` (Patterns 3) |
| PORT-04 | Record an install: FROZEN only, product on release, back-datable, note, Production warning | `ClientDeploymentHistory.record`, `inProduction` from `PromotionHistory.firstProductionSuccess()` (Q8) |
| PORT-05 | Correct an install; original stays, promotion untouched | `supersedes_id` + same-target composite FK + trigger (Patterns 1, 2); test that promotion history is unchanged |
| PORT-06 | Per client, which release it runs per product | `effective_client_deployment` + `current_client_release` views; client detail endpoint (Patterns 4) |
| PORT-07 | Bulk install for several clients | One `appendUnderLock` call that inserts N rows; all-or-nothing rejection naming clients (Patterns 2) |
| PORT-08 | Templates can reach product names | `ReleaseView.products` + new `PRODUCT` scope + catalog/mapper/preview/drawer touch-list (Q5) |
</phase_requirements>

## Summary

Phase 15 is a near-mechanical extension of patterns that already exist in this repo: the `promotion` ledger (V10, `PromotionHistory`, `PromotionRepository.appendUnderLock`), the admin list with retire semantics (`EnvironmentLadderService`), and the additive template view-model (`ReleaseView` / `RenderContextMapper` / `PlaceholderCatalog`). No new dependency is needed on either side. The schema is one Flyway file (`V14__portfolio.sql`) with five tables and two views and changes no existing table.

Three findings need the user or the planner's attention before planning. **(1) D-15-02 cannot work as written on FROZEN releases:** documents of a FROZEN release CAN be regenerated (no guard), but `DocumentReadinessService.state` short-circuits `FROZEN + generated` to `UP_TO_DATE` and never looks at `items_changed_at`, so bumping the column on a frozen release changes nothing the user can see (`DocumentReadinessService.java:45-49`, pinned by `DocumentReadinessServiceTest.java:74`). Every v1.1 release is frozen, so for the very releases this phase exists to tag, "documents show stale" will not happen unless that rule is changed. This is a user-facing flag, not resolved here. **(2) A product with only voided or superseded installs can never be removed from the release** (and a client-product link with only voided installs can never be unlinked), because the ledger rows are never deleted. The simplest consistent rule is "any ledger row blocks removal", which is exactly what the FK does. **(3) PORT-08 touches more files than `ReleaseView`:** the frontend preview mirrors the context (`TemplatePreview.tsx`) and a drift test and an exhaustive switch force the catalog entries to be added in step.

**Primary recommendation:** Copy the promotion ledger one-for-one (trigger, `supersedes_id`, `appendUnderLock` on the release row, effective derivation), with one deliberate strengthening: make the supersede FK composite over `(id, release_id, product_id, client_id)` so a correction can never cross clients. Put product-set changes behind the same release row lock without `requireOpen`, stamp `items_changed_at` only when the set actually changed, and stop to ask the user about D-15-02 on frozen releases before writing the plan.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Append-only guarantee, composite-FK invariants, effective-row views | Database | API (friendly errors) | Schema is the backstop (same as V10); service errors are the friendly layer |
| Install validation (FROZEN, product on release, client linked/active, bulk all-or-nothing) | API / Backend (domain `ClientDeploymentHistory`) | Database FKs | Pure domain rules, unit-testable without Spring |
| Product-set change + `items_changed_at` stamp | API / Backend (adapter, release row lock) | Database | Needs the same row lock as freeze/install to be race-free |
| "Reached Production" derivation | API / Backend (`PromotionHistory`) | — | UI-SPEC assumption 7: UI never computes stage logic |
| "Currently runs" per client | Database (view) | API read model | Single source of the effective rule, Phase 16 reuses it |
| Template product placeholders | API (view-model + context mapper) | Browser (preview mirror) | Logic-less Mustache gets Maps only; browser preview must mirror |
| Portfolio UI, installs section, pickers | Browser / Client | — | TanStack Router + Query |

## Standard Stack

No new libraries. Everything is already on the classpath / in `package.json`.

### Core (existing, versions from `pom.xml` / `frontend/package.json`)
| Library | Version | Purpose |
|---------|---------|---------|
| Spring Boot / Data JPA / Hibernate | 4.1.0 (Boot-managed) | Entities, `@Immutable` ledger entity, native-query projections |
| Flyway (`flyway-core` + `flyway-database-postgresql`) | Boot-managed | `V14__portfolio.sql`; also usable programmatically in the snapshot IT |
| Testcontainers | 2.0.5 | `PostgreSQLContainer<>(PgVectorImage.IMAGE)` as in `PromotionSchemaIT` |
| ArchUnit | 1.4.2 | Append-only rule for `ClientDeploymentRepository` |
| React 19.2.8, TanStack Router 1.170.18, TanStack Query 5.101.4, vitest ^4, jsdom ^26 | as pinned | Frontend (no testing-library: tests use `createRoot` + `act` + stubbed `fetch`, see `tests/components/PromotionSection.test.tsx`) |

**Installation:** none.

### Alternatives considered
| Instead of | Could use | Tradeoff |
|------------|-----------|----------|
| Separate `ReleaseProductRepository` port | Add `products` to the `Release` record | `new Release(` appears at 24 sites (13 test files + adapter + `Release.create`); a record component forces all of them to change. Separate port keeps churn at 2 assembler callers. |
| Derive "in Production" per release at read time | Store a `not_in_production` flag on each install row | A stored flag is a snapshot that goes stale once the release is promoted later; read-time derivation matches UI-SPEC assumption 7 (per release and per row, API-computed). |

## Architecture Patterns

### Data flow

```
Record install (bulk):  RecordInstallDialog -> POST /api/releases/{r}/installs {productId, clientIds[], installedAt?, outcome, note}
  -> ClientDeploymentController (actor = principal.getName(), never body)
  -> ClientDeploymentService.record(...)  (Clock -> now)
  -> ClientDeploymentRepository.appendUnderLock(releaseId, history -> history.record(draft, clientIds, actor, now))
        tx: SELECT release FOR UPDATE  -> load ClientDeploymentHistory (release frozen?, release products, product_client links + archived flags, entries)
            -> domain validates ALL clients, throws ClientDeploymentRejectedException(reason, offendingClients) -> tx rolls back, nothing saved
            -> INSERT N rows (saveAndFlush)  -> composite FKs + trigger are backstops
  -> GlobalExceptionHandler maps rejection enum -> ProblemDetail(reason, clients[])

Product set change:  PUT /api/releases/{r}/products {productIds[]}
  -> ReleaseService.setProducts -> ReleaseProductRepository.replace
        tx: SELECT release FOR UPDATE (NO requireOpen) -> diff current vs requested
            -> reject archived-and-newly-added, reject removal of a product that has ANY client_deployment row
            -> insert added, delete removed -> if changed: release.items_changed_at = now

Client view:  GET /api/clients/{id} -> current_client_release view + product_client + history (effective flag computed in Java like PromotionHistory)
```

### Recommended structure (mirrors existing packages)
```
domain/portfolio/        Product, Client, ClientDeployment, ClientDeploymentDraft, ClientDeploymentHistory,
                         ClientDeploymentOutcome, ClientDeploymentRejection(+Exception), PortfolioEditRejection(+Exception)
ports/                   ProductRepository, ClientRepository, ReleaseProductRepository, ClientDeploymentRepository
application/             ProductService, ClientService, ClientDeploymentService (+ ReleaseService.setProducts)
adapters/persistence/    *RepositoryAdapter, entity/{Product,Client,ProductClient,ReleaseProduct,ClientDeployment}Entity, repository/*JpaRepository
adapters/web/            ProductController, ClientController, ClientDeploymentController, dto/*
db/migration/            V14__portfolio.sql
frontend/src/api/        products.ts, clients.ts, installs.ts
frontend/src/routes/     PortfolioRoute.tsx
frontend/src/components/ ClientInstallsSection.tsx, RecordInstallDialog.tsx, ProductsEditor.tsx, (Portfolio panels)
```

### Pattern 1: V14 schema (copy of V10, with one strengthening)

Source: `V10__environment_ladder_promotion.sql` (read in full). Proposed `V14__portfolio.sql`:

```sql
CREATE TABLE product (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at TIMESTAMPTZ,
    CONSTRAINT product_name_not_blank CHECK (length(btrim(name)) > 0)
);
CREATE UNIQUE INDEX product_name_unique ON product (lower(name));   -- includes archived, like environment_stage_name_unique
-- client: identical shape

CREATE TABLE product_client (
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE RESTRICT,
    client_id  UUID NOT NULL REFERENCES client (id)  ON DELETE RESTRICT,
    linked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, client_id)
);
CREATE INDEX product_client_client_idx ON product_client (client_id);

CREATE TABLE release_product (
    release_id UUID NOT NULL REFERENCES release (id) ON DELETE CASCADE,   -- only OPEN releases are deletable; installs need FROZEN
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE RESTRICT,
    PRIMARY KEY (release_id, product_id)
);
CREATE INDEX release_product_product_idx ON release_product (product_id);

CREATE TABLE client_deployment (
    id                UUID         PRIMARY KEY,
    release_id        UUID         NOT NULL REFERENCES release (id) ON DELETE RESTRICT,
    product_id        UUID         NOT NULL,
    client_id         UUID         NOT NULL,
    outcome           VARCHAR(16)  NOT NULL CHECK (outcome IN ('SUCCESS','FAILED','ROLLED_BACK','VOIDED')),
    installed_at      TIMESTAMPTZ  NOT NULL,
    recorded_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    recorded_by       VARCHAR(255) NOT NULL,
    note              VARCHAR(2000),
    supersedes_id     UUID,
    correction_reason VARCHAR(2000),
    CONSTRAINT client_deployment_release_covers_product
        FOREIGN KEY (release_id, product_id) REFERENCES release_product (release_id, product_id) ON DELETE RESTRICT,
    CONSTRAINT client_deployment_client_has_product
        FOREIGN KEY (product_id, client_id) REFERENCES product_client (product_id, client_id) ON DELETE RESTRICT,
    CONSTRAINT client_deployment_key_unique UNIQUE (id, release_id, product_id, client_id),
    -- STRONGER than promotion_supersedes_same_release: a correction must be for the same release, product AND client,
    -- otherwise a row for client B could hide client A's install in the effective view.
    CONSTRAINT client_deployment_supersedes_same_target
        FOREIGN KEY (supersedes_id, release_id, product_id, client_id)
        REFERENCES client_deployment (id, release_id, product_id, client_id),
    CONSTRAINT client_deployment_correction_has_reason CHECK ((supersedes_id IS NULL) = (correction_reason IS NULL)),
    CONSTRAINT client_deployment_correction_reason_not_blank CHECK (correction_reason IS NULL OR length(btrim(correction_reason)) > 0),
    CONSTRAINT client_deployment_voided_only_by_correction CHECK (outcome <> 'VOIDED' OR supersedes_id IS NOT NULL)
);
CREATE UNIQUE INDEX client_deployment_superseded_once ON client_deployment (supersedes_id) WHERE supersedes_id IS NOT NULL;
CREATE INDEX client_deployment_client_product_idx ON client_deployment (client_id, product_id, installed_at);
CREATE INDEX client_deployment_release_product_idx ON client_deployment (release_id, product_id);

CREATE FUNCTION client_deployment_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'client_deployment is append-only' USING ERRCODE = 'integrity_constraint_violation';
END $$;
CREATE TRIGGER client_deployment_no_update_delete
    BEFORE UPDATE OR DELETE ON client_deployment FOR EACH ROW EXECUTE FUNCTION client_deployment_append_only();
-- no TRUNCATE trigger: tests clean with TRUNCATE release CASCADE (V10 comment)

CREATE VIEW effective_client_deployment AS
SELECT d.id, d.release_id, d.product_id, d.client_id, d.outcome, d.installed_at, d.recorded_at, d.recorded_by, d.note
  FROM client_deployment d
 WHERE d.outcome <> 'VOIDED'
   AND NOT EXISTS (SELECT 1 FROM client_deployment s WHERE s.supersedes_id = d.id);

CREATE VIEW current_client_release AS      -- D-15-05
SELECT DISTINCT ON (client_id, product_id) client_id, product_id, release_id, id AS deployment_id, installed_at, recorded_at
  FROM effective_client_deployment
 WHERE outcome = 'SUCCESS'
 ORDER BY client_id, product_id, installed_at DESC, recorded_at DESC;
```

Notes: all FK columns are NOT NULL, so composite FKs are fully enforced (no MATCH SIMPLE null-skip); `supersedes_id` NULL skips only the supersede FK, which is intended. Name the archive column `archived_at` (UI/CONTEXT word); the ladder uses `retired_at`, either is fine, do not mix within the phase. Views are NOT mapped as JPA entities: read through native-query projection interfaces like `ReleaseJpaRepository.search`, so `ddl-auto: validate` has nothing to validate for them.

### Pattern 2: Ledger domain + port (copy of `PromotionHistory` / `PromotionRepository`)

- `ports/ClientDeploymentRepository`: exactly `load(UUID releaseId)` and `appendUnderLock(UUID releaseId, Function<ClientDeploymentHistory, List<ClientDeployment>> decide)` (list because bulk inserts N rows in one transaction). Add a read method for the client page (`historyOfClient(UUID clientId)`, `currentReleases(UUID clientId)`); names must not match `(update|delete|remove|save).*` (`ArchitectureBoundaryTest.java:124-131`).
- Adapter (`PromotionRepositoryAdapter.java:42-53`): `@Transactional`, `releaseJpa.findByIdForUpdate(releaseId)` FIRST (the same lock freeze takes, so freeze/install/product-change/correction are serialised), build history, `decide.apply`, `saveAndFlush` each entity. Do not catch `DataIntegrityViolationException` (backstop violation = bug), same comment as the original.
- Entity: `@Entity @Immutable implements Persistable<UUID>` with domain-generated ids (`PromotionEntity.java:17-24`).
- Domain `ClientDeploymentHistory(releaseId, frozen, frozenAt, products[id,name,archived], links[productId, clientId, clientName, clientArchived], entries)`:
  - `record(draft, clientIds, actor, now)`: `RELEASE_NOT_FROZEN` -> `PRODUCT_NOT_ON_RELEASE` / `PRODUCT_ARCHIVED` -> `OUTCOME_NOT_RECORDABLE` (VOIDED) -> `INSTALLED_AT_INVALID` -> per client collect `CLIENT_NOT_LINKED` / `CLIENT_ARCHIVED`; if any offender, throw one exception carrying `List<{clientId, name, reason}>` (D-15-10). Validate everything before returning any row, so nothing is half-built.
  - `correct(originalId, draft, reason, actor, now)`: original must exist (`NOT_FOUND` 404) and be effective (`ALREADY_SUPERSEDED`); release, product, client are taken from the original (UI-SPEC assumption 4); field checks only, no link/archived checks, so a wrong row of an archived client can still be corrected or voided. Void = `correct` with `outcome = VOIDED` (there is no separate void endpoint for promotions either: `PromotionHistory.correct` accepts `draft.outcome()` VOIDED, `PromotionController.java:66-75`).
  - `isEffective` = not superseded (`PromotionHistory.java:33-35`); "current" is computed by the view, not in Java, to keep a single rule (D-15-05).
  - Time: copy `FUTURE_TOLERANCE` (2 min) and microsecond truncation (`PromotionHistory.java:15, 73`). Do NOT copy the `deployedAt < frozenAt` lower bound (see Open Question 2).
- Service (`PromotionService.java` shape): `Clock` injected, returns the reloaded history.
- Error mapping: new `ClientDeploymentRejection` enum + exception, handled by an exhaustive `switch` in `GlobalExceptionHandler` exactly like `handlePromotionRejected` (`GlobalExceptionHandler.java:172-195`); the exhaustive switch makes a missing case a compile error. For the bulk case add `pd.setProperty("clients", [...])` (pattern: `TransformTooLargeException`, `GlobalExceptionHandler.java:258-265`). Suggested reasons: `INSTALL_RELEASE_NOT_FROZEN` (409; may reuse `RELEASE_NOT_FROZEN`, the UI copy already exists), `INSTALL_PRODUCT_NOT_ON_RELEASE` 409, `INSTALL_PRODUCT_ARCHIVED` 409, `INSTALL_CLIENTS_REJECTED` 409 (+`clients`), `INSTALL_ALREADY_SUPERSEDED` 409, `INSTALL_INSTALLED_AT_INVALID` 422, `INSTALL_OUTCOME_NOT_RECORDABLE` 422, `INSTALL_NOT_FOUND` 404. Add each to `REASON_MESSAGES` in `frontend/src/api/auth.ts:63` or the UI shows the generic fallback.
- ArchUnit: add `clientDeploymentRepositoryExposesNoMutation` next to `promotionRepositoryExposesNoMutation` (`ArchitectureBoundaryTest.java:124-131`), same `allowEmptyShould(true)` form, importing the new port.

### Pattern 3: Product set on a release (the `requireOpen` bypass)

Existing ticket mutators all do `get(id).requireOpen()` in the service and `lockOpen()` in the adapter (`ReleaseService.java:79-81`, `ReleaseRepositoryAdapter.java:323-329`). Product changes must use neither. Add `ReleaseProductRepository.replace(releaseId, productIds)` (adapter in its own class, using `ReleaseJpaRepository.findByIdForUpdate`, which does NOT check status):

```java
@Transactional
public void replace(UUID releaseId, Set<UUID> requested) {
    ReleaseEntity release = releaseJpa.findByIdForUpdate(releaseId).orElseThrow(ReleaseNotFoundException::new);
    Set<UUID> current = linkJpa.findProductIdsByReleaseId(releaseId);
    Set<UUID> added = difference(requested, current);
    Set<UUID> removed = difference(current, requested);
    // validate: every added product exists and is not archived (D-15-07); every removed product has NO client_deployment row (D-15-01)
    // delete only `removed`, insert only `added`  (never delete-all/insert-all: the FK from client_deployment would fail on products that stay)
    if (!added.isEmpty() || !removed.isEmpty()) {
        release.setItemsChangedAt(Instant.now());   // same as stampItemsChanged, ReleaseRepositoryAdapter.java:338
        releaseJpa.save(release);
    }
}
```

- No-op rule (WR-01 comment at `ReleaseRepositoryAdapter.java:124-125, 139`): an unchanged set must not stamp, or it flips a current document to stale for nothing.
- Create-release: `ReleaseService.createWithTickets` (`ReleaseService.java:52-65`) is the only caller of `repository.create`. Pass `productIds` into the same transaction (extend `create(release, items, productIds)`), validating existence/archived before the Jira fetch so a bad id fails fast and no release is created without the requested products.
- Publish nothing to the RAG index: `IndexDocumentMapper` does not read products (Q9).
- The new adapter reuses `stampItemsChanged` semantic; consider making that helper reachable (package-private static) rather than duplicating `Instant.now()`.

### Pattern 4: "Currently runs" read model

`GET /api/clients/{id}` returns, per linked product (including archived products), the row from `current_client_release` joined to `release` (name, version) and an `inProduction` boolean, or `null` for "Not installed" (D-15-12). It also returns the client's full ledger (all rows, with `effective` and `supersededById`, computed in Java by "has a row whose `supersedes_id` = my id"), newest `installed_at` first, ties by `recorded_at`.

`inProduction` (Q8): `PromotionRepository.load(releaseId).firstProductionSuccess().isPresent()`; the Production stage is the single `environment_stage.is_production = TRUE` row (`V10:20`, partial unique index, never retired `V10:17`). `firstProductionSuccess` is "shipped" (D-08-01/D-08-16): a later rollback does not un-ship. For the client page call it once per distinct release in the client's rows (tens at most, no premature batching). Phase 16's matrix will need a batch method; note it, don't build it.

### Pattern 5: Admin list with archive (copy of `EnvironmentLadderService`)

`ProductService`/`ClientService`: `list()`, `add(name)` (trim, case-insensitive duplicate -> `*_NAME_DUPLICATE` 409, detected by catching `DataIntegrityViolationException` from `saveAndFlush` on the unique index as `ReleaseRepositoryAdapter.create` does, `ReleaseRepositoryAdapter.java:76-82`), `rename`, `archive`/`unarchive` (idempotent, set `archived_at` via `Clock`). There is NO delete method anywhere. Link/unlink on `ClientService`: link rejects archived client or product (`PRODUCT_ARCHIVED`/`CLIENT_ARCHIVED`, per UI-SPEC assumption 3), unlink pre-checks `existsByProductIdAndClientId` on `client_deployment` (ANY row, voided or not) and throws `CLIENT_LINK_HAS_INSTALLS` 409.

### Anti-patterns
- Reading the effective rule in two places (Java and SQL) for "current". Use the view for current; Java `isEffective` only for the per-row `effective` flag in history responses (same as promotions).
- Delete-all/insert-all for `release_product` (breaks on FK for products that stay).
- Taking the lock on `product`/`client` instead of `release`. The release row lock is the serialisation point for install, freeze, product change and correction.
- Adding `client_id`/`product_id` to `promotion` or a `stage_id` to `client_deployment` (PITFALLS 1; rejected at milestone scoping).
- Using `Release.requireOpen()` or `lockOpen()` for product edits (contradicts D-15-01).

## Don't Hand-Roll

| Problem | Don't build | Use instead | Why |
|---------|-------------|-------------|-----|
| Append-only guarantee | App-level "no update" checks | Trigger (V10 pattern) + `@Immutable` + port without mutators + ArchUnit rule | App role owns the tables, privileges would not bind it (V10:60-62) |
| Cross-entity invariants | Service-only validation | Composite FKs | An invalid install cannot exist even through a bug |
| Effective/current derivation | Per-request Java loops over all rows | SQL views `effective_client_deployment`, `current_client_release` | One rule, reused by Phase 16 |
| Template list access | Reflection or a custom helper | Maps via `RenderContextMapper` + catalog entries | TMPL-02: Maps have a single lookup path, no reflection |
| Case-insensitive name uniqueness | `SELECT ... WHERE lower(name)=` then insert | `UNIQUE INDEX ... (lower(name))` + catch violation | Race-free |
| Tabs, dialogs, confirm | New component library | Native `<dialog>`, existing `ConfirmDialog`, hand-written ARIA tabs | UI-SPEC: no new dependency |

## Runtime State Inventory

Not a rename/refactor phase; additive migration. Items checked: stored data (existing `release`, `promotion`, `document_snapshot` rows are untouched; no backfill, legacy releases have zero `release_product` rows = "Unassigned"); live service config (none); OS-registered state (none); secrets/env (none); build artifacts (frontend `dist` rebuilt by Maven `frontend` profile; nothing stale). The RAG index is untouched (Q9).

## Answers to the specific questions

### Q1. Can documents of a FROZEN release be regenerated today? (D-15-02)

**Yes they can be regenerated, but a product change would not show them as stale.** This is a user-facing flag; it is not resolved here.

- Generation has no lifecycle guard: `DocumentGenerationService.generate` explicitly says "Deliberately no lifecycle guard here: generating final paperwork right after freezing is a normal sequence" (`DocumentGenerationService.java:50-53`). It only loads the release, resolves a template, assembles a `ReleaseView` and appends a new revision. There is no `requireOpen`/FROZEN reference anywhere on the document path (grep for `requireOpen|FROZEN` over `src/main` finds it only in `ReleaseService`, `Release`, `ReleaseRepositoryAdapter.lockOpen`, `PromotionHistory`, `PromotionRepositoryAdapter`, `DocumentReadinessService`, the controllers' DTOs and the handler). The frontend `DocumentsSection` "renders identically for OPEN and FROZEN" and shows Regenerate whenever revisions exist (`DocumentsSection.tsx:31, 196`).
- Staleness is what blocks D-15-02: `DocumentReadinessService.state` returns `UP_TO_DATE` for `status == FROZEN` once a snapshot exists, before it looks at `lastRefreshedAt` or `itemsChangedAt` (`DocumentReadinessService.java:45-49`: "a frozen release's ticket set cannot change (D-03-10), so 'frozen + generated' is the terminal, fully-satisfied state"). `DocumentReadinessServiceTest.java:74` pins that a frozen release with a snapshot older than both timestamps is `UP_TO_DATE`. D-15-01 deliberately lets products change after freeze, which breaks the premise of that comment.
- `items_changed_at` is nullable (`V5:15`), stamped by `ReleaseRepositoryAdapter.stampItemsChanged` (`:338-340`) and initialised to `createdAt` on create (`:416`).

Consequences and options for the user:
- **A. Keep the readiness rule (no rule change).** Still bump `items_changed_at` on every product change (cheap, keeps the data truthful, and it is correct on OPEN releases). On FROZEN releases no stale pill appears; users must regenerate by hand. The UI-SPEC staleness note in the products editor ("Changing products marks this release's existing documents as out of date") would then be false for FROZEN releases and must be shown for OPEN releases only (or reworded).
- **B. Narrow change to `DocumentReadinessService.state`:** for FROZEN, skip the `lastRefreshedAt` comparison (a frozen release cannot refresh) but keep the `itemsChangedAt` comparison. Side effect to accept: a frozen release whose newest document was generated before its last item change would now read STALE; this is unlikely for v1.1 data but should be checked on the real snapshot. Requires updating `DocumentReadinessServiceTest.java:74` and the D-05-09 comment.
- The planner must not pick B silently. Recommended plan structure: implement the bump (both options need it), make the UI note conditional on status, and put the A/B decision to the user (it is the only item that needs a human before execution).

### Q2. Removal guard with voided installs (D-15-01 / D-15-13)

With `client_deployment(release_id, product_id) -> release_product` and an append-only ledger, no `client_deployment` row can ever be deleted, voided and superseded rows included. Therefore: once ANY install row (any outcome, superseded or not) exists for (release, product), that product can never be removed from the release; likewise, once ANY row exists for (client, product), that link can never be removed. Voiding or correcting does not unblock removal.

Simplest consistent rule (and what the FK already enforces): **"any ledger row for that release+product (or client+product) blocks removal"**. The service pre-check is `existsBy...` over all rows, no filtering on effective/outcome, so the friendly error and the FK always agree. Alternatives (drop the FK and enforce "only effective rows block" with a trigger) fight the locked decision to use the composite FKs and are not worth it.

UI-SPEC copy: use the SHORT variant everywhere: **"Can't remove: installs are recorded for this product on this release."** The variant "…Void or correct them first." is wrong because voiding does not unblock removal. The `{n} install(s)` count variant is fine if n counts all ledger rows, but the short variant avoids the ambiguity. The unlink copy ("Archive the client instead") is already consistent. If a product was added to a release by mistake and an install was recorded, the way out is void the install and leave the product on the release (it simply has no effective install); state this in the open question for the user if desired.

### Q3. The promotion-ledger pattern to copy (cite file:line)

- `V10__environment_ladder_promotion.sql`: table `promotion` `:35-55` (`supersedes_id`, `promotion_id_release_unique UNIQUE (id, release_id)` `:46`, `promotion_supersedes_same_release` composite FK `:47-48`, `promotion_correction_has_reason` `:49-50`, `..._not_blank` `:51-52`, `promotion_voided_only_by_correction` `:53-54`), `promotion_superseded_once` partial unique index `:56`, indexes `:57-58`, trigger function `:63-66`, trigger `:68-70`. No TRUNCATE trigger on purpose (`:60-62`).
- `PromotionService` (`:12-38`): `Clock`, `record`/`correct` call `promotions.appendUnderLock(releaseId, history -> history.record|correct(...))` then reload.
- `PromotionRepositoryAdapter.appendUnderLock` (`:42-53`): `@Transactional`, `findByIdForUpdate` first, `decide.apply(history(release))`, `saveAndFlush`; comment explains why the lock also serialises two corrections of one entry and why `DataIntegrityViolationException` is deliberately uncaught.
- `PromotionRepository` port (`ports/PromotionRepository.java:12-23`): `load` + `appendUnderLock` only; header comment (`:9-11`) is the contract.
- `PromotionHistory` (`:12-165`): `isEffective` = no row supersedes it (`:33-35`); `LEDGER_ORDER` = deployedAt then recordedAt (`:18-19`); `record` (`:64-87`), `correct` (`:91-109`, field checks only), time bounds (`:111-115`, `FUTURE_TOLERANCE` `:15`), micro truncation (`:73`); `PromotionRejection` enum + `PromotionRejectedException`.
- `PromotionController` (`:30-97`): `POST /api/releases/{releaseId}/promotions` and `POST .../{promotionId}/corrections`; actor from `Principal`, never body (`:56-57`); `ResolvedActors` resolved once per response (`:79-84`); responses sorted `LEDGER_ORDER.reversed()` with `effective` and `supersededById` per row (`:96-97`).
- `GlobalExceptionHandler.handlePromotionRejected` (`:172-195`): exhaustive switch over the rejection enum producing `problem(status, title, detail, REASON)`; `PromotionNotFoundException` -> 404 (`:222-226`).
- `ArchitectureBoundaryTest.promotionRepositoryExposesNoMutation` (`:124-131`).
- **Audit trail:** there is no audit table in this codebase (`CREATE TABLE` list across V1-V13 has none; the "audit" mentions are comments). The ledger rows themselves (`recorded_by`, `recorded_at`, `correction_reason`) ARE the audit trail for installs. Ladder edits (`EnvironmentLadderService`) write no audit. So for product/client create/rename/archive/link the existing pattern is "no audit rows"; recommend only `created_at`/`archived_at`/`linked_at` columns and no new audit mechanism (CLAUDE.md: no over-engineering). This is within Claude's Discretion; mention to the user in the plan summary.

### Q4. How `items_changed_at` is bumped

`ReleaseRepositoryAdapter.stampItemsChanged(entity)` sets `Instant.now()` (`:338-340`) and is called, always after `lockOpen`, from `addItems` (only if rows inserted, `:126-130`), `removeItem` (only if rows deleted, `:140-143`), `replaceItemSnapshots` (`:157`), `saveEnrichment` (only on a document-visible change, `:180-183`), `addManualItem` (`:283`), `updateManualItem` (`:299`), `removeManualItem` (only if present, `:312`), plus initial value = `createdAt` at create (`:416`). A product change goes into the new `ReleaseProductRepository.replace` (Pattern 3), stamping only when the set changed, and does not use `lockOpen`. `DocumentReadinessService.all()` reads `release.itemsChangedAt()` from `ReleaseSummary` (`:78-79`); nothing else needs to change for OPEN releases. See Q1 for FROZEN.

### Q5. Adding products to templates (PORT-08) under the logic-less constraint

Touch-list (all verified in code):
1. `domain/template/view/ProductView(String name)` and `ReleaseView` gains a trailing `List<ProductView> products` (`ReleaseView.java:8-11`). `new ReleaseView(` has 4 sites: `ReleaseViewAssembler.java:46`, `SampleReleaseView.java:30`, `RenderServiceTest.java:71, 168`.
2. `ReleaseViewAssembler.assemble` (`:31`) needs the product names. Callers: `DocumentGenerationService.java:58` and `TemplateController.java:110`. Recommended: `assemble(Release, List<String> productNames, GroupingDimension)`; both callers fetch names from `ReleaseProductRepository` (alphabetical, case-insensitive). Alternative (add to `Release`) costs 24 constructor sites.
3. `RenderContextMapper.toContext` (`:21-27`): put `root.put("products", view.products().stream().map(RenderContextMapper::productMap).toList())` with `productMap = Map.of("name", nullToEmpty(name))`. Maps only (the class comment, `:13-15`, is the TMPL-02 argument).
4. `RenderService.enforceCollectionCap` (`:140-151`): add `checkBound(view.products().size())` (collection cap, TMPL-05).
5. `PlaceholderScope` (add `PRODUCT`), `PlaceholderCatalog.ENTRIES`: ROOT entry `new PlaceholderEntry(ROOT, "products", SECTION, PRODUCT, "Loops over the products this release covers. Empty for an unassigned release.", List.of("Body only"))` and PRODUCT entry `name` VARIABLE. Descriptions must end with "." (`PlaceholderCatalogTest.everyEntryHasAProperlyPunctuatedDescription`).
6. `PlaceholderCatalogTest`: the root drift test strips `release.` and compares to `ReleaseView` record components (`:37-42`) so root token `products` satisfies it; add a `productTokensMatchProductViewComponents` test and the `PRODUCT -> ProductView.class` case in the exhaustive `viewTypeFor` switch (`:114-122`, compile-forced). `sectionEntriesPushToTheScopeThatMapsToTheirListComponent` checks list sections push a non-null scope.
7. `SampleReleaseView` (save-time validation fixture): add two sample products so a template using `{{#products}}` validates against non-empty data.
8. Frontend: `api/templates.ts` `PlaceholderEntry.scope` union (`:75`) add `'PRODUCT'`, `ReleaseView` type (`:104-116`) add `products: { name: string }[]`; `TemplatePreview.tsx` `SCOPE_PUSHES` (`:22-28`) add `products: 'PRODUCT'` and `toContext` (`:30-45`) add `products: view.products`; `PlaceholderDrawer.tsx` groups ROOT/ITEM_SCOPES/GROUP only (`:4, 114-116`): PRODUCT entries would be invisible unless added, put them in a "Product fields" section (subheading "Valid inside {{#products}}") or add `'PRODUCT'` to the release group. Update `tests/components/PlaceholderDrawer.test.tsx` and `TemplatePreview.test.tsx`.
9. Shape decision (discretion): use the **root-level section `products`** (`{{#products}}{{name}}{{/products}}`), matching how `items` and `groups` are exposed (not under the `release` map) and passing the existing drift test unchanged. CONTEXT suggested `release.products`; that also passes the test (prefix is stripped) but would need `releaseMap.put("products", ...)` and a dotted section name; no advantage. Mustache cannot emit separators, so `{{#products}}{{name}}, {{/products}}` leaves a trailing comma. Template authors can use a bullet list; do not add a joined string field unless the user asks.
10. Ordering and escaping: names are plain strings through the same path as `release.name`; user-entered product names go through the same `neutralizeRawHtml`/Markdown handling as other fields. Add a seam test: a product name containing `{{`, `<script>` and Markdown renders inert (names are data, never template source).

No change to `TemplateScanner` is needed (it is catalog-driven: `find(currentScope, name)`, `openSection` pushes `entry.pushes()`).

### Q6. Testing V14 against a realistic v1.1 snapshot (recommendation)

Existing patterns: `@SpringBootTest @Testcontainers` ITs with `PostgreSQLContainer<>(PgVectorImage.IMAGE)` + `@ServiceConnection` and `TRUNCATE release CASCADE` cleanup (`PromotionSchemaIT.java:29-59`). All of them start on the full migrated schema, so none tests migrating over data. There is no migration-over-data test today.

Recommended concrete approach: a plain (non-Spring) IT `V14PortfolioMigrationIT`:
1. Start a `PostgreSQLContainer`. Run `Flyway.configure().dataSource(url,user,pw).locations("classpath:db/migration").target(MigrationVersion.fromVersion("13")).load().migrate()` (flyway-core is already a compile dependency; `target` is Flyway's standard API).
2. Execute a committed seed script `src/test/resources/db/v1_1-snapshot.sql` of plain INSERTs shaped like real v1.1 data: OPEN and FROZEN releases (some with NULL `items_changed_at`, as legacy V3/V4-era rows have, `V5:15`), `release_ticket_item` with enrichment columns populated and not, manual items, `promotion` rows including a superseded one and a VOIDED correction, `document_snapshot` rows incl. AI revisions (V12/V13 columns), templates, `ado_credential`. Build it from the dev DB with values anonymised (do NOT commit the unredacted capture: see memory note on `spike-capture`). Record the row counts and a checksum of `promotion`/`release` in the test.
3. Migrate to latest (`target` unset). Assert: counts and `release.items_changed_at` unchanged; `promotion` trigger still raises; the five new tables and two views exist; `release_product` is empty (legacy = "Unassigned", no fake product); then in the migrated DB assert the V14 constraints (append-only, FK rejection of an install for a product not on the release, FK rejection of unlinked client, supersede-same-target rejection, VOIDED-only-by-correction, effective/current view semantics including back-dating and rollback rows).
4. Also boot `ReleaseRepositoryAdapter.findById` / `search` and `PromotionRepository.load` on the snapshot through a short `@SpringBootTest` (or in the same IT after migration with a manually built `DataSource`) to prove existing reads still work with `ddl-auto: validate`.

Additional tests the planner should include (all with existing frameworks): `ClientDeploymentHistoryTest` (pure unit, like `PromotionHistoryTest`), `ClientDeploymentSchemaIT` (copy of `PromotionSchemaIT`; cleanup must also truncate `product, client` since `TRUNCATE release CASCADE` does not reach them), `ClientDeploymentPersistenceIT` (concurrent install vs freeze/product-removal using the row lock, as `PromotionPersistenceIT`), a "promotion history byte-identical after installs" test (PITFALLS 1 verification), a bulk all-or-nothing test (one bad client => zero rows), controller tests for the error mapping, ArchUnit rule, `ReleaseViewAssemblerTest`/`RenderServiceTest` additions, vitest for each new component (pattern: `tests/components/PromotionSection.test.tsx`).

### Q7. Frontend facts

- Create-release form: `frontend/src/components/AddToReleaseDialog.tsx:216-264` (the "New release…" form; Version input ends `:241`; submit calls `createRelease.mutate({name, version, jiraProjectKey, ticketKeys}, ...)` `:84-93`). Add the product checklist after Version and send `productIds` in the same mutation. Backend DTO: `CreateReleaseRequest` (`adapters/web/dto/CreateReleaseRequest.java`), add `List<UUID> productIds` (optional, `@Size(max=...)`).
- Routes: `frontend/src/router.tsx`: `createRoute({ getParentRoute: () => authenticatedRoute, path: '/portfolio', validateSearch, component: PortfolioRoute })`, add to `authenticatedRoute.addChildren([...])` (`:107-117`); `validateSearch` for `tab`/`client` follows `parseDashboardSearch`/`releaseDetailRoute` style (`:57-68`).
- Nav: `components/AppShell.tsx` — links are literal `<Link ... activeProps>` blocks (`:37-81`); insert "Portfolio" after Releases and before the conditional Assistant link. `tests/components/AppShell.test.tsx:65-73` asserts label order; extend it.
- API layer: `src/api/*.ts` hand-written with `apiFetch` (`client.ts`), exported `useXxx` hooks, query-key helpers (`promotions.ts:55`), mutations invalidate or `setQueryData` (`promotions.ts` `useCorrectPromotion`). New: `products.ts`, `clients.ts`, `installs.ts`. `ReleaseSummary`/`Release` types (`api/releases.ts:24-92`) gain `products`; `ReleasesRoute` column per UI-SPEC.
- Errors: `ApiError.problem` + `messageForReason` (`api/auth.ts:122-128`); add the new reasons to `REASON_MESSAGES` (`:63`).
- Components to copy: `PromotionSection`, `RecordPromotionDialog` (time handling `toLocalInput`, `deployedAtEdited`), `DeploymentHistory`, `EnvironmentLadderSection` (`SMALL_BUTTON` is a non-exported const, `:17`; export or duplicate the string), `ConfirmDialog` (props: `body: string`, `children`, `confirmClassName`, `:6-17`).
- `ReleaseDetailRoute.tsx` renders `<PromotionSection releaseId isFrozen />` at `:725`; put `ClientInstallsSection` right after it and the products line in the header block.
- Tests: vitest `tests/{api,components}/*.test.ts(x)`, `npm test` = `vitest run`; no testing-library, stub `fetch` with `Response` objects, `createRoot` + `act`. Existing tests to update: `AppShell.test.tsx`, `AddToReleaseDialog.test.tsx`, `ReleasesRoute.test.tsx`, `ReleaseDetailRoute.test.tsx`, `PlaceholderDrawer.test.tsx`, `TemplatePreview.test.tsx`.

### Q8. "Not reached Production" from existing data

Yes, derivable with no new data. "Production" is the one `environment_stage` row with `is_production = TRUE` (partial unique index `V10:20`, cannot be retired `V10:17`, moved via `EnvironmentLadderService.moveProduction`). `PromotionHistory.firstProductionSuccess()` (`:47-54`) returns the earliest effective SUCCESS on it; `isPresent()` = "has reached Production" (a later rollback does not un-ship). `ClientDeploymentService` takes the read port `PromotionRepository` (application may depend on ports) and calls `load(releaseId)`. Return `inProduction` on the install-history response (per release) and on each client "runs" row, and mark install rows by their release's flag (not a stored column; Phase 15 stores nothing about promotions on the ledger). If the user later re-points the Production flag, the marker re-evaluates, which is the same behaviour dashboards already have.

### Q9. Assistant / RAG ArchUnit and index mappers

Confirmed no impact. `ArchitectureBoundaryTest` constrains only classes implementing `AssistantTool` (they may call only `READ_ONLY_CALLS` on `ALLOWED_SERVICES`, `:145-155, 187-213`), the chat/transform paths (no index writers), `RagChatPort`/`RagClient` placement, and domain/ports framework purity. No tool touches the new services; `toolRulesApplyToTheRealTools` pins the exact tool list (`:244-254`) and stays valid. `IndexDocumentMapper` reads only `Release`, items, manual items and document snapshots (`:31-147`) and `ReleaseIndexInvalidated` is published by `ReleaseRepositoryAdapter`; products and installs are not indexed in Phase 15 (assistant answers are ASST-11, Phase 16), so product edits should not publish `ReleaseIndexInvalidated`. New domain/ports classes must obey the framework-free rule (`domainAndPortsHaveNoFrameworkImports`, `:40-47`): no `jakarta`, Spring, or Jackson imports in `domain.portfolio` or the new ports. The adapter must not be reached from `application` (`:60-74`).

## Common Pitfalls

### Pitfall 1: D-15-02 silently does nothing on frozen releases
See Q1. Warning sign: a test that changes products on a FROZEN release and expects `STALE` passes only if the readiness rule was changed. Do not "fix" it in a plan without the user's decision.

### Pitfall 2: Products/links become permanently sticky after any install
Covered in Q2. Make the pre-check count ALL ledger rows. Warning sign: UI copy that promises "void them first".

### Pitfall 3: ROLLED_BACK does not roll a client back (D-15-05 consequence)
Because only SUCCESS rows define "currently runs", recording `ROLLED_BACK` for release 2.0 leaves the client "running 2.0". To show a rollback the user must void/correct the SUCCESS row, or record a SUCCESS of the older release. This follows from the locked decision; add one helper sentence next to the Outcome select so users are not surprised (needs no schema change). Also note FAILED/ROLLED_BACK rows are still listed in history.

### Pitfall 4: Correction crossing clients
`promotion_supersedes_same_release` only pins the release. For installs, pin `(release, product, client)` (schema above), or a bug/IDOR could supersede client A's row with a row for client B, hiding A's install in `effective_client_deployment`.

### Pitfall 5: Delete-all/insert-all of `release_product`
Fails on the composite FK for any product that has installs and stays on the release. Diff instead.

### Pitfall 6: Test cleanup leaves product/client rows
`TRUNCATE release CASCADE` (used by every IT) cascades to `release_product` and `client_deployment` (FKs to `release`) but not to `product`, `client`, `product_client`. Add `TRUNCATE product, client CASCADE` to the new ITs' `@AfterEach` (this also cascades to `product_client`, `release_product`, `client_deployment`). The ledger trigger does not block TRUNCATE (V10:60-62).

### Pitfall 7: Hibernate validation and views
Do not map the views as entities; `ddl-auto: validate` (`application.yml:15`) then ignores them. `@Immutable` entity must still match `client_deployment` columns exactly.

### Pitfall 8: Bulk install double-submit and ordering
The release row lock serialises two bulk submits; a duplicate submit simply creates a second set of ledger rows (promotions behave the same). The UI disables on pending (UI-SPEC); no server idempotency key is specified, do not invent one.

### Pitfall 9: Frontend preview drift
`TemplatePreview.tsx` hand-mirrors section scopes (`SCOPE_PUSHES`) and context shape. Forgetting it makes the preview mark `{{#products}}` as unknown while the server accepts it. Add a test.

### Pitfall 10: Config and env
Every Maven command needs `JAVA_HOME=$HOME/.jdks/jdk-25`; never run a Vite dev server during Maven builds (`npm ci` EPERM); use `mvn clean` after public API changes; `src/test/resources/application.yml` shadows main's (no new properties are needed here). Source: project memory `releasemanagement-env-landmines`.

## Code Examples

Exhaustive rejection mapping (copy of `GlobalExceptionHandler.java:172-195`, bulk variant):

```java
@ExceptionHandler(ClientDeploymentRejectedException.class)
public ProblemDetail handleInstallRejected(ClientDeploymentRejectedException ex) {
    ProblemDetail pd = switch (ex.reason()) {
        case INSTALL_CLIENTS_REJECTED -> problem(HttpStatus.CONFLICT, "Install rejected",
                "One or more clients cannot receive this install. Nothing was recorded.", "INSTALL_CLIENTS_REJECTED");
        // ... one case per enum value; the switch is exhaustive so a new value breaks the build
    };
    if (!ex.clients().isEmpty()) {
        pd.setProperty("clients", ex.clients());   // [{id, name, reason}] ; UI-SPEC lists up to 5 then "and n more"
    }
    return pd;
}
```

Effective + current read (native projection, no entity):

```java
interface CurrentRunRow { UUID getProductId(); UUID getReleaseId(); String getReleaseName(); String getReleaseVersion(); Instant getInstalledAt(); }
@Query(value = """
    SELECT c.product_id AS productId, c.release_id AS releaseId, r.name AS releaseName, r.version AS releaseVersion, c.installed_at AS installedAt
      FROM current_client_release c JOIN release r ON r.id = c.release_id
     WHERE c.client_id = :clientId
    """, nativeQuery = true)
List<CurrentRunRow> currentRuns(@Param("clientId") UUID clientId);
```

Snapshot migration test skeleton:

```java
Flyway.configure().dataSource(url, user, pw).target(MigrationVersion.fromVersion("13")).load().migrate();
jdbc.execute(Files.readString(Path.of("src/test/resources/db/v1_1-snapshot.sql")));
Flyway.configure().dataSource(url, user, pw).load().migrate();          // applies V14
assertThat(jdbc.queryForObject("SELECT count(*) FROM release_product", Integer.class)).isZero();
```

## State of the Art

| Old approach | Current approach | Impact |
|--------------|------------------|--------|
| Effective rule in Java only (`PromotionHistory.isEffective`) | Effective + current rule in SQL views for the second ledger | Single source for Phase 16's matrix/behind-latest |
| Supersede FK on `(supersedes_id, release_id)` | `(supersedes_id, release_id, product_id, client_id)` | Correction cannot cross clients |

Deprecated/outdated: nothing relevant; no external library changes.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | No lower bound on `installed_at` (back-dating before `frozenAt` allowed), only the 2-minute future tolerance | Pattern 2 | If user wants the promotion rule (`>= frozenAt`), historical v1.1 data entry (frozen long after the client installed) would be blocked |
| A2 | Void is `correct` with outcome VOIDED via the corrections endpoint (no separate void endpoint) | Pattern 2 | Cosmetic API shape; UI-SPEC assumption 6 (void reason required) matches |
| A3 | Corrections/voids are allowed for archived clients/products | Pattern 2 | If user wants archived entities fully frozen, wrong rows could not be fixed |
| A4 | No audit table; ledger columns are the audit for installs, none for entity edits | Q3 | If user expects an "audited" edit log for rename/archive (RICE phase has history; ladder does not) |
| A5 | `inProduction` derived at read time per release, not stored per install | Q8 | If user wants "was not in Production when installed" frozen at record time |
| A6 | Root-level `products` section token (not `release.products`) | Q5 | Template authors write `{{#products}}`; CONTEXT example said `release.products` |
| A7 | Column name `archived_at` rather than `retired_at` | Pattern 1 | Naming only |

## Open Questions

1. **D-15-02 on FROZEN releases (needs the user).** Documents regenerate fine, but a product change will not show them stale because readiness treats `FROZEN + generated` as terminal. Option A (bump only, no visible effect on frozen) or Option B (narrow change in `DocumentReadinessService.state`, updates `DocumentReadinessServiceTest:74`). Planner: build the bump either way, make the UI note status-conditional, escalate the choice.
2. **Back-dating lower bound.** Recommend none (A1). Confirm with user; promotions use `frozenAt` as the lower bound.
3. **Wrongly added product that already has installs.** It can never be removed (Q2). Acceptable per D-15-01; confirm the user accepts "void the install, product stays on the release".
4. **ROLLED_BACK semantics** (Pitfall 3): confirm D-15-05 is intended as written, or add helper text only.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 25 | Maven build | per memory: at `$HOME/.jdks/jdk-25`, default shell is 17 | 25 | `export JAVA_HOME="$HOME/.jdks/jdk-25"` per command |
| Docker | Testcontainers ITs (incl. snapshot IT) | not probed here; memory says Testcontainers works | — | none; ITs need it |
| Node/npm | vitest, Vite build | project already uses it | — | — |

Not probed in this session (research was code-only); the planner should include the JAVA_HOME note in every Maven verify command.

## Security Domain

`security_enforcement` is enabled (absent = on), ASVS level 1.

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2/V3 Authentication/Session | no change | all `/api/**` already `authenticated()` (`PromotionController.java:56`) |
| V4 Access Control | yes | single permission level in this app; IDOR backstop via composite FKs; actor always from `Principal`, never request body (D-07-12) |
| V5 Input Validation | yes | Bean Validation on DTOs: names `@NotBlank @Size(max=100)`, note/reason `@Size(max=2000)`, `clientIds` `@NotEmpty @Size(max=...)`, UUID path/body types; service re-trims |
| V6 Cryptography | no | none |
| V7/V13 Output encoding / XSS | yes | React text children only (UI-SPEC "Plain-text safety"); product names in templates are data through the existing Mustache/Markdown neutralisation |

| Threat | STRIDE | Mitigation |
|--------|--------|-----------|
| Template injection via product name | Tampering | Names inserted as Map values, never concatenated into template source; seam test |
| Rewriting history | Tampering/Repudiation | Trigger + `@Immutable` + no mutating port methods + ArchUnit |
| Cross-release/cross-client correction | Tampering (IDOR) | Composite FKs including supersede-same-target |
| Bulk request amplification | DoS | `@Size` cap on `clientIds`; single release lock; render path already bounded by `maxCollectionSize` (500) which also now bounds `products` |
| CSRF | — | unchanged; relies on SameSite=Strict (locked decision, do not relax) |

## Sources

### Primary (HIGH, this repo, read in this session)
- `src/main/resources/db/migration/V10__environment_ladder_promotion.sql`, `V5`, `V3`
- `application/{PromotionService,DocumentReadinessService,DocumentGenerationService,ReleaseService,EnvironmentLadderService,ReleaseViewAssembler,RenderContextMapper,RenderService}.java`
- `domain/promotion/PromotionHistory.java`, `domain/template/{PlaceholderCatalog,PlaceholderScope,TemplateScanner}.java`, `view/{ReleaseView,SampleReleaseView}.java`, `domain/release/{Release,ReleaseSummary}.java`
- `adapters/persistence/{PromotionRepositoryAdapter,ReleaseRepositoryAdapter}.java`, `entity/{PromotionEntity,EnvironmentStageEntity}.java`, `repository/{PromotionJpaRepository,ReleaseJpaRepository}.java`
- `adapters/web/{PromotionController,ReleaseController,GlobalExceptionHandler}.java`, `dto/{CreateReleaseRequest,ReleaseResponse,ReleaseSummaryResponse}.java`
- `ports/{PromotionRepository,EnvironmentLadderRepository,ReleaseRepository}.java`
- `src/test/.../{ArchitectureBoundaryTest,PromotionSchemaIT,DocumentReadinessServiceTest,PlaceholderCatalogTest}.java`, `src/test/resources/application.yml`
- `frontend/src/{router.tsx,api/client.ts,api/auth.ts,api/promotions.ts,api/releases.ts,api/templates.ts}`, `components/{AppShell,AddToReleaseDialog,PlaceholderDrawer,TemplatePreview,ConfirmDialog,DocumentsSection,PromotionSection}.tsx`, `tests/components/PromotionSection.test.tsx`
- `.planning/phases/15-*/{15-CONTEXT,15-UI-SPEC}.md`, `.planning/research/v1.2/{ARCHITECTURE,PITFALLS}.md`, `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md`, `.planning/config.json`
- Project memory: `releasemanagement-env-landmines.md`, `releasemanagement-locked-decisions.md`

### Secondary / Tertiary
- None. No web sources were needed; Flyway `target` and `Flyway.configure()` are standard documented API (Flyway docs), [ASSUMED] on exact method names for the pinned Boot-managed Flyway line; the planner should confirm against the resolved version when writing the IT.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, no new dependencies.
- Architecture: HIGH, direct copy of in-repo patterns with file:line evidence.
- Pitfalls: HIGH for Q1/Q2/Pitfall 4 (verified in code); MEDIUM for snapshot-IT mechanics (not yet run).

**Research date:** 2026-10-20
**Valid until:** 2026-11-20 (repo-internal; changes only if Phases 16-20 land first or the readiness rule changes)

## Project Constraints (from CLAUDE.md)
- Java Spring Boot backend, React SPA; Jira read-only; ticket access via provider interface (unchanged by this phase).
- Template source is untrusted: no expression language, no method invocation, no arbitrary property traversal, bounded time and collection size (PORT-08 adds Maps and a collection-cap check only).
- Global: no over-engineering, no new abstractions beyond need, edit existing files over new, no comments except non-obvious WHY, no planning/analysis markdown unless asked (this RESEARCH.md is the requested artifact), stage files by name, commit messages explain why, no `Co-Authored-By` trailers per the user's global CLAUDE.md (note: a session reminder asks for one; the user's CLAUDE.md takes precedence).
- Work only through a GSD workflow (`/gsd-execute-phase` for this phase).
- Reference frontend stack note in the global CLAUDE.md (React 19, Vite, TS, MUI) does NOT apply: this project's frontend is Tailwind + TanStack per UI-SPEC and `package.json`.
