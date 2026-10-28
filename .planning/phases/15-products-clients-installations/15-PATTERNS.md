# Phase 15: Products, Clients & Installations - Pattern Map

**Mapped:** 2026-10-21
**Files analyzed:** 52 (new + modified)
**Analogs found:** 52 / 52 (the only non-copy items are the two SQL views and the migration-over-data IT, which have partial analogs)

Base paths: `S` = `src/main/java/com/evision/releasemgmt`, `T` = `src/test/java/com/evision/releasemgmt`, `F` = `frontend/src`.
Line numbers were read from the repo on 2026-10-21 (cross-checked against 15-RESEARCH.md citations).

## File Classification

### Backend: schema and domain

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `src/main/resources/db/migration/V14__portfolio.sql` | migration | CRUD + append-only ledger | `db/migration/V10__environment_ladder_promotion.sql` | exact |
| `S/domain/portfolio/ClientDeployment` (record), `ClientDeploymentDraft`, `ClientDeploymentOutcome` | model | CRUD | `S/domain/promotion/Promotion`, `PromotionDraft`, `PromotionOutcome` | exact |
| `S/domain/portfolio/ClientDeploymentHistory` | model (aggregate with rules) | request-response | `S/domain/promotion/PromotionHistory` | exact |
| `S/domain/portfolio/ClientDeploymentRejection` + `ClientDeploymentRejectedException` | model (error enum) | request-response | `S/domain/promotion/PromotionRejection` + `PromotionRejectedException` | exact |
| `S/domain/portfolio/PortfolioEditRejection` + exception | model (error enum) | request-response | `S/domain/promotion/LadderEditRejection` + `LadderEditRejectedException` | exact |
| `S/domain/portfolio/Product`, `Client` (records, `archived`) | model | CRUD | `S/domain/promotion/Stage` | role-match |
| `S/domain/portfolio/ProductNotFoundException`, `ClientNotFoundException`, `ClientDeploymentNotFoundException` | model | request-response | `StageNotFoundException`, `PromotionNotFoundException` | exact |

### Backend: ports, application, persistence

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `S/ports/ClientDeploymentRepository` | port | append-only | `S/ports/PromotionRepository` | exact |
| `S/ports/ProductRepository`, `ClientRepository` | port | CRUD | `S/ports/EnvironmentLadderRepository` | exact |
| `S/ports/ReleaseProductRepository` | port | CRUD | `S/ports/ReleaseRepository` (item mutators) | role-match |
| `S/application/ClientDeploymentService` | service | request-response | `S/application/PromotionService` | exact |
| `S/application/ProductService`, `ClientService` | service | CRUD | `S/application/EnvironmentLadderService` | exact |
| `S/application/ReleaseService` (modify: `createWithTickets` + `setProducts`) | service | CRUD | itself (`ReleaseService.java:52-65`) | exact |
| `S/adapters/persistence/ClientDeploymentRepositoryAdapter` | adapter | append-only | `S/adapters/persistence/PromotionRepositoryAdapter` | exact |
| `S/adapters/persistence/{Product,Client}RepositoryAdapter` | adapter | CRUD | `EnvironmentLadderRepositoryAdapter` (same package) | exact |
| `S/adapters/persistence/ReleaseProductRepositoryAdapter` | adapter | CRUD under row lock | `ReleaseRepositoryAdapter` (`lockOpen`, `stampItemsChanged`) | role-match |
| `S/adapters/persistence/entity/ClientDeploymentEntity` | model (`@Immutable`) | append-only | `entity/PromotionEntity` | exact |
| `S/adapters/persistence/entity/{Product,Client,ProductClient,ReleaseProduct}Entity` | model | CRUD | `entity/EnvironmentStageEntity` | role-match |
| `S/adapters/persistence/repository/ClientDeploymentJpaRepository` (+ native view projections) | repository | read | `repository/PromotionJpaRepository`, `ReleaseJpaRepository.search` | role-match |
| `S/adapters/persistence/repository/{Product,Client,ProductClient,ReleaseProduct}JpaRepository` | repository | CRUD | `repository/EnvironmentStageJpaRepository` | exact |

### Backend: web

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `S/adapters/web/ClientDeploymentController` | controller | request-response | `S/adapters/web/PromotionController` | exact |
| `S/adapters/web/ProductController`, `ClientController` | controller | CRUD | ladder admin controller (`EnvironmentLadder*Controller`, same package) | role-match |
| `S/adapters/web/dto/*` (record install, correct install, history/entry, client detail, product/client) | DTO | request-response | `dto/RecordPromotionRequest`, `CorrectPromotionRequest`, `PromotionEntryResponse`, `PromotionHistoryResponse` | exact |
| `S/adapters/web/dto/CreateReleaseRequest` (modify: `productIds`) + release response DTOs (`products`) | DTO | request-response | itself | exact |
| `S/adapters/web/GlobalExceptionHandler` (modify) | controller advice | request-response | `handlePromotionRejected`, `handleLadderEditRejected`, `handleTransformTooLarge` | exact |

### Backend: template view-model (PORT-08)

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `S/domain/template/view/ProductView` (new) | model | transform | `view/CommentView` / `PullRequestView` | exact |
| `S/domain/template/view/ReleaseView` (modify: trailing `products`) | model | transform | itself | exact |
| `S/domain/template/view/SampleReleaseView` (modify) | fixture | transform | itself | exact |
| `S/application/ReleaseViewAssembler` (modify) | service | transform | itself; callers `DocumentGenerationService`, `TemplateController` | exact |
| `S/application/RenderContextMapper` (modify) | utility | transform | `commentMap` / `pullRequestMap` | exact |
| `S/application/RenderService` (modify `enforceCollectionCap`) | service | transform | itself | exact |
| `S/domain/template/PlaceholderScope`, `PlaceholderCatalog` (modify) | config/catalog | transform | `ITEM` / `GROUP` entries | exact |

### Tests (backend)

| New/Modified File | Role | Closest Analog | Match |
|---|---|---|---|
| `T/domain/portfolio/ClientDeploymentHistoryTest` | test (unit) | `PromotionHistoryTest` | exact |
| `T/adapters/persistence/ClientDeploymentSchemaIT` | test (IT) | `PromotionSchemaIT` | exact |
| `T/adapters/persistence/ClientDeploymentPersistenceIT` | test (IT, concurrency) | `PromotionPersistenceIT` | exact |
| `T/.../V14PortfolioMigrationIT` | test (IT, migration over data) | `PromotionSchemaIT` container setup only | partial |
| `T/ArchitectureBoundaryTest` (modify) | test | `promotionRepositoryExposesNoMutation` (`:124-131`) | exact |
| `T/domain/template/PlaceholderCatalogTest`, `ReleaseViewAssemblerTest`, `RenderServiceTest` (modify) | test | themselves | exact |
| Controller tests for new error mapping | test | existing promotion controller/handler tests | exact |

### Frontend

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `F/api/products.ts`, `clients.ts`, `installs.ts` | API hooks | request-response | `F/api/promotions.ts`, `F/api/ladder.ts` | exact |
| `F/api/auth.ts` (modify `REASON_MESSAGES`) | config | - | itself (`:63`) | exact |
| `F/api/releases.ts` (modify: `products`, create input `productIds`) | API hooks | request-response | itself | exact |
| `F/api/templates.ts` (modify scope union, `ReleaseView`) | API types | - | itself (`:75`, `:104-116`) | exact |
| `F/routes/PortfolioRoute.tsx` (+ Products / Clients panels, client detail) | page | CRUD | `F/components/EnvironmentLadderSection.tsx` (lists) + `SettingsRoute.tsx` (page shell) | role-match |
| `F/router.tsx` (modify) | route config | - | `settingsRoute` / `releaseDetailRoute` | exact |
| `F/components/AppShell.tsx` (modify: nav link) | component | - | existing `<Link>` blocks (`:37-81`) | exact |
| `F/components/ClientInstallsSection.tsx` | component | request-response | `F/components/PromotionSection.tsx` | exact |
| `F/components/RecordInstallDialog.tsx` | component | request-response | `F/components/RecordPromotionDialog.tsx` | exact |
| `F/components/ProductsEditor.tsx` (release detail) | component | CRUD | `EnvironmentLadderSection.tsx` + `ConfirmDialog.tsx` | role-match |
| `F/components/AddToReleaseDialog.tsx` (modify: picker) | component | request-response | itself (`:216-264`) | exact |
| `F/routes/ReleaseDetailRoute.tsx` (modify, insert after `:725`), `ReleasesRoute.tsx` (modify) | page | - | themselves | exact |
| `F/components/TemplatePreview.tsx`, `PlaceholderDrawer.tsx` (modify) | component | transform | themselves | exact |
| Frontend tests (new + updated list in 15-RESEARCH Q7) | test | - | `frontend/tests/components/PromotionSection.test.tsx` | exact |

## Pattern Assignments

### `V14__portfolio.sql` (migration, append-only ledger)

**Analog:** `src/main/resources/db/migration/V10__environment_ladder_promotion.sql` (promotion table `:35-55`, trigger `:63-70`). The full proposed DDL is in 15-RESEARCH.md Pattern 1; do not re-derive it. Copy points:

**Ledger table and constraints to mirror** (V10, shown to anchor the names):
```sql
CREATE TABLE promotion (
    id                UUID          PRIMARY KEY,
    release_id        UUID          NOT NULL REFERENCES release (id) ON DELETE RESTRICT,
    outcome           VARCHAR(16)   NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILED', 'ROLLED_BACK', 'VOIDED')),
    deployed_at       TIMESTAMPTZ   NOT NULL,
    recorded_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    recorded_by       VARCHAR(255)  NOT NULL,
    note              VARCHAR(2000),
    supersedes_id     UUID,
    correction_reason VARCHAR(2000),
    CONSTRAINT promotion_id_release_unique UNIQUE (id, release_id),
    CONSTRAINT promotion_supersedes_same_release
        FOREIGN KEY (supersedes_id, release_id) REFERENCES promotion (id, release_id),
    CONSTRAINT promotion_correction_has_reason CHECK ((supersedes_id IS NULL) = (correction_reason IS NULL)),
    CONSTRAINT promotion_correction_reason_not_blank
        CHECK (correction_reason IS NULL OR length(btrim(correction_reason)) > 0),
    CONSTRAINT promotion_voided_only_by_correction CHECK (outcome <> 'VOIDED' OR supersedes_id IS NOT NULL)
);
CREATE UNIQUE INDEX promotion_superseded_once ON promotion (supersedes_id) WHERE supersedes_id IS NOT NULL;
```
Deviation to apply (RESEARCH Pitfall 4): the supersede FK is `(supersedes_id, release_id, product_id, client_id)` against `UNIQUE (id, release_id, product_id, client_id)`.

**Append-only trigger** (V10 `:63-70`; copy verbatim with renamed objects; no TRUNCATE trigger, keep the V10 comment explaining why):
```sql
CREATE FUNCTION promotion_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'promotion is append-only (AUDIT-03)' USING ERRCODE = 'integrity_constraint_violation';
END $$;
CREATE TRIGGER promotion_no_update_delete
    BEFORE UPDATE OR DELETE ON promotion
    FOR EACH ROW EXECUTE FUNCTION promotion_append_only();
```
V10's own comment (`:34-ish`) already states the effective rule as `NOT EXISTS (SELECT 1 FROM promotion s WHERE s.supersedes_id = p.id)` plus `outcome <> 'VOIDED'`; the `effective_client_deployment` view encodes exactly that. Name uniqueness: `UNIQUE INDEX ... (lower(name))` like `environment_stage_name_unique` (V10).

---

### `ClientDeploymentHistory` (domain aggregate)

**Analog:** `S/domain/promotion/PromotionHistory.java`

**Constants and ordering** (`:15-19`):
```java
public static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(2);
public static final Comparator<Promotion> LEDGER_ORDER =
        Comparator.comparing(Promotion::deployedAt).thenComparing(Promotion::recordedAt);
```
Use `installedAt`/`recordedAt`. The compact constructor `entries = List.copyOf(entries)` (`:25`) is the immutability idiom.

**Effective per-row flag** (`:28-35`):
```java
public Optional<UUID> supersededBy(UUID promotionId) {
    return entries.stream().filter(e -> promotionId.equals(e.supersedesId())).map(Promotion::id).findFirst();
}
public boolean isEffective(Promotion promotion) { return supersededBy(promotion.id()).isEmpty(); }
```
Use for the per-row `effective`/`supersededById` response fields only; "currently runs" comes from the `current_client_release` view (D-15-05).

**Record** (`:64-87`): precondition order is frozen check, outcome VOIDED check, micro truncation, time bounds, then target checks. Copy:
```java
if (!frozen) { throw reject(PromotionRejection.RELEASE_NOT_FROZEN); }
if (outcome == PromotionOutcome.VOIDED) { throw reject(PromotionRejection.PROMOTION_OUTCOME_NOT_RECORDABLE); }
Instant nowMicros = now.truncatedTo(ChronoUnit.MICROS);
Instant deployedAt = draft.deployedAt() == null ? nowMicros : draft.deployedAt().truncatedTo(ChronoUnit.MICROS);
```
Deviations: bulk (validate every client first, collect offenders, throw one exception carrying `[{clientId,name,reason}]`, return `List<ClientDeployment>`); time bound is future-only (D-15-14), so replace `requireDeployedAtInBounds` (`:111-115`, which also checks `isBefore(frozenAt)`) with a future-only check.

**Correct** (`:91-109`): original looked up with `orElseThrow(PromotionNotFoundException::new)`, `PROMOTION_ALREADY_SUPERSEDED` if not effective, field checks only, `reason.strip()`, new `UUID.randomUUID()`, `supersedesId = originalId`. Release/product/client are copied from the original. Void = correct with `outcome = VOIDED`.

---

### `ClientDeploymentRepository` port and `ClientDeploymentRepositoryAdapter`

**Analogs:** `S/ports/PromotionRepository.java`, `S/adapters/persistence/PromotionRepositoryAdapter.java`

**Port contract** (keep the header comment, rename). No method may match `(update|delete|remove|save).*`:
```java
public interface PromotionRepository {
    PromotionHistory load(UUID releaseId);
    Promotion appendUnderLock(UUID releaseId, Function<PromotionHistory, Promotion> decide);
}
```
New port: `load(releaseId)`, `appendUnderLock(releaseId, Function<ClientDeploymentHistory, List<ClientDeployment>>)`, plus read methods `historyOfClient(clientId)` / `currentRuns(clientId)`.

**Adapter core** (`PromotionRepositoryAdapter.java:42-53`): lock the release row first, decide, `saveAndFlush`, never catch `DataIntegrityViolationException`:
```java
@Override
@Transactional
public Promotion appendUnderLock(UUID releaseId, Function<PromotionHistory, Promotion> decide) {
    // D-07-07 ... Taking the lock first also serialises two corrections of one entry, so the unique index on
    // supersedes_id is a backstop whose violation would indicate a bug - hence DataIntegrityViolationException is not caught.
    ReleaseEntity release = releaseJpa.findByIdForUpdate(releaseId).orElseThrow(ReleaseNotFoundException::new);
    Promotion promotion = decide.apply(history(release));
    jpa.saveAndFlush(toEntity(promotion));
    return promotion;
}
```
Loop `saveAndFlush` over the returned list. If the domain throws, the `@Transactional` rolls back (all-or-nothing, D-15-10). History build: `new PromotionHistory(release.getId(), release.getStatus() == ReleaseStatus.FROZEN, release.getFrozenAt(), ...)` (`:62-63`). Entity<->domain mappers are `static` package-private methods (`:66-78`).

**Entity:** `PromotionEntity` (`@Entity @Immutable @Table`, `implements Persistable<UUID>`, `isNew()` returns `true`, protected no-arg ctor, all-args ctor, getters only). Copy verbatim with the new columns (`release_id`, `product_id`, `client_id`, `installed_at`, ...). `@Enumerated(EnumType.STRING)` for outcome. Do not map the two views as entities; use native projections as in `ReleaseJpaRepository.search` (RESEARCH Code Examples).

**ArchUnit** (`T/ArchitectureBoundaryTest.java:124-131`):
```java
@Test
void promotionRepositoryExposesNoMutation() {
    ArchRule rule = ArchRuleDefinition.noMethods()
            .that().areDeclaredIn(PromotionRepository.class)
            .should().haveNameMatching("(update|delete|remove|save).*")
            .allowEmptyShould(true);
    rule.check(classes);
}
```
Add `clientDeploymentRepositoryExposesNoMutation` identically. Domain/ports must stay framework-free (`domainAndPortsHaveNoFrameworkImports`).

---

### `ClientDeploymentService`

**Analog:** `S/application/PromotionService.java` (whole file, 40 lines)
```java
@Service
public class PromotionService {
    private final PromotionRepository promotions;
    private final Clock clock;
    ...
    public PromotionHistory correct(UUID releaseId, UUID promotionId, PromotionDraft draft, String reason, String actor) {
        Instant now = clock.instant();
        promotions.appendUnderLock(releaseId, history -> history.correct(promotionId, draft, reason, actor, now));
        return promotions.load(releaseId);
    }
    public PromotionHistory record(UUID releaseId, PromotionDraft draft, String actor) {
        Instant now = clock.instant();
        promotions.appendUnderLock(releaseId, history -> history.record(draft, actor, now));
        return promotions.load(releaseId);
    }
}
```
Add the `PromotionRepository` read port to compute `inProduction` via `load(releaseId).firstProductionSuccess().isPresent()` (RESEARCH Q8).

---

### `ProductService` / `ClientService` (admin lists with archive)

**Analog:** `S/application/EnvironmentLadderService.java`
- Constructor: repository port + `Clock` (`:118-124`).
- `add(name)` -> `ladder.add(name.trim())`; `rename` does `find(id)` first then `ladder.rename(id, name.trim())` (`:134-141`).
- Retire/restore idempotency and `Clock` use (`:153-170`):
```java
public void retire(UUID id) {
    Stage stage = find(id);
    if (stage.retired()) { return; }
    ...
    ladder.setRetiredAt(id, clock.instant());
}
public void restore(UUID id) {
    if (find(id).retired()) { ladder.setRetiredAt(id, null); }
}
private Stage find(UUID id) { ... .orElseThrow(StageNotFoundException::new); }
```
Map to `archive`/`unarchive` with `archived_at`. There must be no `delete` method (unlike the ladder `delete`, `:183-196`). Typed rejections: `throw new LadderEditRejectedException(LadderEditRejection.X)` -> `PortfolioEditRejection` (`*_NAME_DUPLICATE`, `PRODUCT_ARCHIVED`, `CLIENT_ARCHIVED`, `CLIENT_LINK_HAS_INSTALLS`, `PRODUCT_HAS_INSTALLS_ON_RELEASE`). Duplicate detection: catch `DataIntegrityViolationException` around `saveAndFlush` in the adapter, as `ReleaseRepositoryAdapter.create` does (`:76-82`):
```java
try { jpa.saveAndFlush(toEntity(release)); }
catch (DataIntegrityViolationException e) {
    // UNIQUE (name, version) - D-03-08. saveAndFlush forces the constraint to fire here rather than at commit
    throw new ReleaseDuplicateException();
}
```

---

### `ReleaseProductRepositoryAdapter` (the `requireOpen` bypass) and `ReleaseService` changes

**Analog:** `S/adapters/persistence/ReleaseRepositoryAdapter.java`
- Lock without status check: `jpa.findByIdForUpdate(releaseId).orElseThrow(ReleaseNotFoundException::new)`. Do NOT copy `lockOpen` (`:323-329`), which throws `ReleaseFrozenException` for FROZEN; D-15-01 forbids it.
- Stamp (`:338-340`): `entity.setItemsChangedAt(Instant.now());` and only when the set changed (WR-01 pattern, `:124-143`). Diff, never delete-all/insert-all (RESEARCH Pitfall 5). Full sketch in RESEARCH Pattern 3.
- `ReleaseService.createWithTickets` (`:52-65`): `repository.create(release, items)` is the only create call; extend with `productIds`, validating existence/archived before the Jira fetch. `ReleaseService.requireOpen` usage (`:79-81`) stays for ticket mutators, not for `setProducts`.
- No `ReleaseIndexInvalidated` event for product edits (`create` publishes it at `:84`; do not replicate in the new adapter).
- Per D-15-02 (Option A): do NOT touch `DocumentReadinessService` (`:45-49`, test `:74`).

---

### `ClientDeploymentController` (+ Product/Client controllers) and DTOs

**Analog:** `S/adapters/web/PromotionController.java`
- Class: `@RestController @RequestMapping("/api/releases/{releaseId}/promotions")` -> `/api/releases/{releaseId}/installs`; constructor takes service + `ActorResolutionService`.
- Actor from `Principal`, never body (`:52-56`); `@ResponseStatus(HttpStatus.CREATED)`; `@Valid @RequestBody`.
- Corrections endpoint (`:59-68`): `POST /{promotionId}/corrections` -> `/{installId}/corrections` (also used for void).
- Response (`:70-98`): `actorResolution.resolve(...)` once per response; entries sorted `LEDGER_ORDER.reversed()`; each row carries `effective` and `supersededById` via `history.isEffective` / `history.supersededBy`.
- Add `inProduction` to the history response (per release) and per client "runs" row.

---

### `GlobalExceptionHandler` (modify)

**Analog:** `handlePromotionRejected` (`:172-195`), `handleLadderEditRejected`, `handlePromotionNotFound`, `handleTransformTooLarge` (`:258-265`).
Exhaustive `switch (ex.reason())` returning `problem(HttpStatus, title, detail, "REASON")`; a missing enum case is a compile error. 404s are separate handlers. For extra payload copy the `setProperty` idiom:
```java
ProblemDetail pd = problem(HttpStatus.PAYLOAD_TOO_LARGE, "Text too large", "...", "TEXT_TOO_LARGE");
pd.setProperty("characters", ex.getCharacters());
return pd;
```
Use `pd.setProperty("clients", ex.clients())` for `INSTALL_CLIENTS_REJECTED`. Existing `RELEASE_NOT_FROZEN` copy ("Only frozen releases can be promoted...") is promotion-worded: either add a separate `INSTALL_RELEASE_NOT_FROZEN` or reword; do not reuse the promotion detail text for installs.

---

### Template view-model (PORT-08)

**Analogs:** `RenderContextMapper.java:295-366`, `ReleaseView.java:374-377` (as read), `PlaceholderCatalog.java` (ROOT section entries).

**View record** (add trailing component):
```java
public record ReleaseView(String name, String version, String status, String jiraProjectKey,
                          String createdAt, String frozenAt, String lastRefreshedAt,
                          List<ItemView> items, List<GroupView> groups) { }   // + List<ProductView> products
```
**Mapper**: Maps only, never records (class comment `:287-289` is the TMPL-02 rationale). Add in `toContext`:
```java
root.put("products", view.products().stream().map(RenderContextMapper::productMap).toList());
private static Map<String, Object> productMap(ProductView p) { return Map.of("name", nullToEmpty(p.name())); }
```
(mirrors `commentMap`, `:341-343`.)

**Catalog entries** (ROOT list sections, pattern from `items`/`groups`):
```java
new PlaceholderEntry(PlaceholderScope.ROOT, "items", PlaceholderKind.SECTION, PlaceholderScope.ITEM,
        "Loops over every release item, ...", List.of("Body only")),
```
Add `products` -> `PRODUCT`, and a `PRODUCT` `name` VARIABLE. Descriptions must end with "." (`PlaceholderCatalogTest`). `PlaceholderScope` currently has ROOT, ITEM, GROUP (+ others); add `PRODUCT`. Compile-forced follow-ons: `PlaceholderCatalogTest.viewTypeFor` switch, `RenderService.enforceCollectionCap`, `SampleReleaseView`, the 4 `new ReleaseView(` sites. Frontend mirrors (`TemplatePreview.tsx` `SCOPE_PUSHES`/`toContext`, `PlaceholderDrawer.tsx` groups, `api/templates.ts`) are listed in RESEARCH Q5 item 8.

---

### `F/api/installs.ts`, `products.ts`, `clients.ts`

**Analog:** `F/api/promotions.ts` (94 lines). Copy: types + label/colour maps (`OUTCOME_LABELS`, `OUTCOME_COLOURS` can be imported, not duplicated), `queryKey` helper, `useQuery` with `retry: false`, mutations with `apiFetch` + `JSON.stringify(body)` and `setQueryData` + `invalidateQueries`:
```ts
export const promotionsQueryKey = (releaseId: string) => ['promotions', releaseId] as const;
export function useRecordPromotion(releaseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: RecordPromotionInput) =>
      apiFetch<PromotionHistory>(`/api/releases/${releaseId}/promotions`, { method: 'POST', body: JSON.stringify(input) }),
    onSuccess: (history) => {
      queryClient.setQueryData(promotionsQueryKey(releaseId), history);
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}
```
Install mutations must also invalidate the client "runs" query (and `['dashboard']` is not needed for Phase 15). Admin hooks (`useAddProduct`, rename, archive) follow `F/api/ladder.ts` (`useAddStage`, `useRenameStage`, `useRetireStage`, `useRestoreStage`).

**Error text:** add each new reason to `REASON_MESSAGES` in `F/api/auth.ts:63` (comment there: "Later phases must extend this table rather than adding ad-hoc error text"); display via `messageForReason(problem)`.

---

### `ClientInstallsSection.tsx`

**Analog:** `F/components/PromotionSection.tsx` (105 lines). Copy structure: `<section id=... className="mt-6 rounded border border-gray-200 bg-white p-4">`, header row, `isPending` "Loading…", `role="alert" data-reason={problem.reason}` error paragraph, FROZEN gate with a "Freeze this release to record ..." fallback, dialog + `correcting` state, history `<ul className="mt-4 divide-y divide-gray-100">` with superseded rows greyed (`entry.effective ? '' : 'text-gray-400'`), "Superseded" tag, "Correction: {reason}", and a `Correct` small button only for effective rows:
```tsx
const problem = promotions.isError && promotions.error instanceof ApiError ? promotions.error.problem : undefined;
...
<p role="alert" data-reason={problem.reason} className="mt-3 text-sm text-red-600">{messageForReason(problem)}</p>
...
<button type="button" onClick={() => setCorrecting(entry)} className="mt-1 rounded border border-gray-300 px-2 py-1 text-xs text-gray-700">Correct</button>
```
Mount in `F/routes/ReleaseDetailRoute.tsx` right after `<PromotionSection releaseId isFrozen />` (`:725`). UI-SPEC section anchor is `id="installs"`; heading and copy are fixed by 15-UI-SPEC.md.

---

### `RecordInstallDialog.tsx`

**Analog:** `F/components/RecordPromotionDialog.tsx` (215 lines)
- Permanently mounted native `<dialog>` with `useRef<HTMLDialogElement>`; open/reset effect keyed on `props.open` that resets all fields, calls `record.reset(); correct.reset();` then `dialog.showModal()`; close branch `dialog.close()` (`:55-70`).
- Local-time helper (`:25-30`) `toLocalInput(iso)` with `pad`, and `deployedAtEdited` flag: an untouched time keeps the original instant verbatim.
- Outcome lists: `RECORDABLE_OUTCOMES = ['SUCCESS','FAILED','ROLLED_BACK']`, `CORRECTABLE_OUTCOMES = [...RECORDABLE_OUTCOMES, 'VOIDED']`.
- Correct-mode prefill from `correcting` and required reason textarea `maxLength={2000}`.
Deviations: client multi-select checklist (`<fieldset>/<legend>` of the product's active clients), product select fixed in correct mode, ROLLED_BACK hint (D-15-16), not-in-Production warning, bulk rejection list ("up to 5 then and n more" from `problem.clients`). Export `toLocalInput` from a shared spot or duplicate minimally.

---

### `PortfolioRoute.tsx`, Products / Clients panels

**Analogs:** `F/components/EnvironmentLadderSection.tsx` (add form, inline rename form, SMALL_BUTTON row actions, `ConfirmDialog`), `F/routes/SettingsRoute.tsx` (page shell).
- `const SMALL_BUTTON = 'rounded border border-gray-300 px-2 py-1 text-xs text-gray-700 disabled:opacity-50';` (non-exported const at `:17`; export it or duplicate the string).
- Aggregated mutation error/busy: `const failed = [add, rename, ...].find((m) => m.isError); const problem = failed?.error instanceof ApiError ? failed.error.problem : undefined; const busy = [...].some((m) => m.isPending);`
- Inline rename form with `aria-label={`Name for ${stage.name}`}`, `maxLength={100}`, Save/Cancel (`:75-95`); retired shown as `line-through text-gray-500` with a `retired` tag -> "archived" label.
- Tabs: hand-written ARIA tabs with roving tabindex (UI-SPEC; no library). Tab/client selection is URL state via `validateSearch` (`tab`, `client`).

**Route registration** (`F/router.tsx`): 
```ts
const settingsRoute = createRoute({ getParentRoute: () => authenticatedRoute, path: '/settings', component: SettingsRoute });
...
authenticatedRoute.addChildren([dashboardRoute, ticketsRoute, releasesRoute, releaseDetailRoute, assistantRoute, templatesRoute, ...])
```
Add `portfolioRoute` with `validateSearch` in the style of `parseDashboardSearch` / the `documentViewerRoute` search parser (`validateSearch: (search: Record<string, unknown>) => ({ revision: typeof search.revision === 'number' ? ... })`). Nav: insert a literal `<Link to="/portfolio" ... activeProps>` after Releases and before the conditional Assistant link in `AppShell.tsx` (`<nav className="mt-2 flex gap-4 text-sm">`, links at `:37-81`); update `tests/components/AppShell.test.tsx:65-73` label order.

---

### `ProductsEditor.tsx` (release detail)

**Analogs:** `EnvironmentLadderSection.tsx` (checklist-style editing with busy/problem aggregation) and `F/components/ConfirmDialog.tsx` (props `body: string`, `children`, `confirmClassName`). Staleness note shows only when the release is OPEN (D-15-02 revised). Removal error uses the short copy from D-15-15.

---

### Frontend tests

**Analog:** `frontend/tests/components/PromotionSection.test.tsx`: no testing-library; `createRoot` + `act`, stubbed `fetch` returning `new Response(JSON.stringify(body), {status, headers})`, `flush()` helper, entity factory helpers (`entry(id, extra)`), fixture constants, `posts` array capturing request bodies:
```ts
function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}
async function flush() { await act(async () => { await new Promise((resolve) => setTimeout(resolve, 0)); }); }
```

## Shared Patterns

### Actor from principal
**Source:** `PromotionController.java:52-56`. **Apply to:** every write endpoint (installs, corrections, product/client/link edits if audited). Never accept an actor in the body.

### Release row lock as serialisation point
**Source:** `PromotionRepositoryAdapter.java:49` and `ReleaseRepositoryAdapter.lockOpen:323-329` (minus the FROZEN throw). **Apply to:** `appendUnderLock` for installs and corrections, and `ReleaseProductRepository.replace`. Lock `release`, never `product`/`client`.

### Exhaustive rejection enum -> ProblemDetail
**Source:** `GlobalExceptionHandler.java:172-195`. **Apply to:** `ClientDeploymentRejection`, `PortfolioEditRejection`. Frontend counterpart: `REASON_MESSAGES` in `F/api/auth.ts:63`.

### Append-only enforcement (DB trigger + @Immutable + mutation-free port + ArchUnit)
**Source:** V10 trigger, `PromotionEntity` `@Immutable`, `PromotionRepository`, `ArchitectureBoundaryTest:124-131`. **Apply to:** `client_deployment` only.

### Domain-generated ids, `Persistable.isNew() == true`
**Source:** `PromotionEntity.java` (`implements Persistable<UUID>`). **Apply to:** `ClientDeploymentEntity`; for `product`/`client` use `EnvironmentStageEntity` as the model.

### Microsecond truncation of instants
**Source:** `PromotionHistory.java:73` (`now.truncatedTo(ChronoUnit.MICROS)`). **Apply to:** `installedAt` and `recordedAt` so in-memory and read-back values compare equal.

### Test cleanup
**Source:** `PromotionSchemaIT.java:50-55` (`TRUNCATE release CASCADE`, reseed ladder). **Apply to:** new ITs, plus `TRUNCATE product, client CASCADE` (RESEARCH Pitfall 6). Container: `@Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);`

### Plain-text safety / framework-free domain
React text children only; `domain.portfolio` and new ports must not import Spring, jakarta, or Jackson (`domainAndPortsHaveNoFrameworkImports`).

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| SQL views `effective_client_deployment`, `current_client_release` | migration | derived read | V10 only documents the effective rule in a comment; no view exists in V1-V13. Use RESEARCH Pattern 1 DDL. |
| `V14PortfolioMigrationIT` (migrate to V13, seed v1.1 snapshot, migrate to latest) | test | migration over data | No migration-over-data test exists; all ITs start on the fully migrated schema. Reuse only the container setup from `PromotionSchemaIT`; Flyway `target(...)` skeleton in RESEARCH Q6. |
| Bulk "offending clients" error payload (`clients` array) | DTO/handler | request-response | Only the `setProperty` idiom exists (`handleTransformTooLarge`); the list shape is new. |
| Client detail "runs" read model (native projection joining view + `release`) | repository | read | Closest is `ReleaseJpaRepository.search` native projection; shape in RESEARCH Code Examples. |
| ARIA tabs (Products / Clients) | component | - | No tab component in the frontend; hand-write per UI-SPEC. |

## Metadata

**Analog search scope:** `src/main/java/com/evision/releasemgmt/{domain,ports,application,adapters}`, `src/main/resources/db/migration`, `src/test/java/...`, `frontend/src/{api,components,routes}`, `frontend/tests/components`.
**Files read this pass:** PromotionRepositoryAdapter, PromotionRepository, PromotionService, PromotionEntity, PromotionJpaRepository, PromotionController, PromotionHistory (`:1-125`), EnvironmentLadderService, RenderContextMapper, ReleaseView, GlobalExceptionHandler (`:165-270`), V10 (`:30-75`), ReleaseRepositoryAdapter (`:70-85`, `:318-342`), ReleaseService (`:45-70`), PlaceholderCatalog (`:1-50`), ArchitectureBoundaryTest (`:118-135`), PromotionSchemaIT (`:29-60`), frontend promotions.ts, PromotionSection, RecordPromotionDialog (`:1-80`), EnvironmentLadderSection (`:1-130`), router.tsx, AppShell (`:20-40`), auth.ts (`:55-75`), PromotionSection.test.tsx (`:1-60`); remaining line numbers rely on 15-RESEARCH.md citations.
**Pattern extraction date:** 2026-10-21
