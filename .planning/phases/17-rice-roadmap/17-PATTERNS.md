# Phase 17: RICE Roadmap + Release Packages Link - Pattern Map

**Mapped:** 2026-10-25
**Files analyzed:** 43 (new + modified)
**Analogs found:** 41 / 43 (2 partial, see "No Analog Found")

Source root abbreviations: `B` = `src/main/java/com/evision/releasemgmt`, `T` = `src/test/java/com/evision/releasemgmt`, `F` = `frontend/src`, `M` = `src/main/resources`.

## File Classification

### Roadmap, backend

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `M/db/migration/V16__roadmap.sql` | migration | CRUD + append-only ledger | `M/db/migration/V14__portfolio.sql` (+ trigger from V10) | exact |
| `B/domain/roadmap/RoadmapItem.java`, `RiceInputs.java` | model (records) | transform | `B/domain/portfolio/Product.java`, `domain/release/Release.java` | role-match |
| `B/domain/roadmap/RiceScorer.java` | utility (pure) | transform | none in repo (use RESEARCH Pattern 1) | no analog |
| `B/domain/roadmap/RoadmapRejection.java` + `RoadmapRejectedException.java` + `RoadmapItemNotFoundException.java` | model (enum + exceptions) | request-response | `domain/portfolio/PortfolioEditRejection.java` + `PortfolioEditRejectedException.java` + `ProductNotFoundException.java` | exact |
| `B/ports/RoadmapRepository.java` | port | CRUD | `B/ports/ProductRepository.java`; append-only half: `ports/PromotionRepository.java` | exact |
| `B/application/RoadmapService.java` | service | CRUD + request-response (Jira) | `application/ProductService.java` (shape), `PromotionService.java` (actor + clock + lock), `ReleaseService.java` (Jira batch fetch) | role-match |
| `B/adapters/persistence/RoadmapRepositoryAdapter.java` | adapter | CRUD | `adapters/persistence/ProductRepositoryAdapter.java`, `PromotionRepositoryAdapter.java` (row lock) | exact |
| `B/adapters/persistence/entity/RoadmapItemEntity.java`, `RoadmapInputHistoryEntity.java` | model (JPA) | CRUD | `entity/ProductEntity.java`, `entity/PromotionEntity.java` | exact |
| `B/adapters/persistence/repository/RoadmapItemJpaRepository.java` (+ history repo) | repository | CRUD | `repository/ProductJpaRepository.java`, `ReleaseJpaRepository.findByIdForUpdate` | exact |
| `B/adapters/web/RoadmapController.java` | controller | request-response | `adapters/web/ProductController.java` + `ReleaseController.java` (Principal) | exact |
| `B/adapters/web/dto/Roadmap*Request/Response.java` | model (DTO) | request-response | `dto/PortfolioNameRequest.java`, `AddTicketsRequest.java`, `ProductListResponse.java` | exact |
| `B/adapters/web/CsvWriter.java` | utility | transform / file-I/O | `adapters/web/ExportFilename.java` (static final utility, allowlist) | partial |
| `B/adapters/web/GlobalExceptionHandler.java` (modify) | middleware | request-response | its own `handlePortfolioEditRejected` (lines 237-256) | exact |
| `T/ArchitectureBoundaryTest.java` (modify) | test | n/a | `promotionRepositoryExposesNoMutation` (lines 125-132) | exact |
| `T/adapters/persistence/V16RoadmapMigrationIT.java` | test | batch | `V14PortfolioMigrationIT.java` | exact |
| `T/adapters/persistence/RoadmapPersistenceIT.java` | test | CRUD | `PortfolioPersistenceIT.java`, `PromotionPersistenceIT.java` | exact |
| `T/adapters/web/RoadmapControllerTest.java` | test | request-response | `ProductControllerTest.java` | exact |
| `T/application/RoadmapServiceTest.java`, `RiceScorerTest`, `CsvWriterTest` | test | transform | `T/application/ReleaseServiceManualItemsTest.java` (fake-port service test) | role-match |

### Packages link, backend

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `M/db/migration/V16__roadmap.sql` (one `ALTER TABLE release ADD COLUMN packages_url`) | migration | CRUD | V14 header comment style; only additive ALTER in repo history: V11/V6 | role-match |
| `B/domain/release/Release.java` (add `packagesUrl`) | model | CRUD | itself | exact |
| `B/domain/release/PackagesUrl.java` (host allow-list) + `ReleaseRejection`-style reason | model/validator | transform | `domain/release/UnfreezeRejection` + `UnfreezeRejectedException`; `domain/portfolio/PortfolioEditRejection` | role-match |
| `B/adapters/persistence/entity/ReleaseEntity.java` | model (JPA) | CRUD | itself (`jira_project_key` column) | exact |
| `B/ports/ReleaseRepository.java` + `ReleaseRepositoryAdapter.java` (`setPackagesUrl`, `toDomain`, `toEntity`) | port + adapter | CRUD | `ReleaseProductRepositoryAdapter.replace` (lock, no status check) + `freeze` (publishes `ReleaseIndexInvalidated`) | exact |
| `B/application/ReleaseService.java` (`setPackagesUrl`) | service | CRUD | `ReleaseService.setProducts` (lines 76-80) | exact |
| `B/adapters/web/ReleaseController.java` + `dto/PackagesUrlRequest.java` + `dto/ReleaseResponse.java` | controller + DTO | request-response | `setProducts` (lines 95-99) + `ReleaseProductsRequest` | exact |
| `B/application/assistant/tools/ReleasesTool.java` (`ReleaseDetail` field) | service (tool) | request-response | itself (lines 34-38, 87-100) | exact |
| `B/application/assistant/IndexDocumentMapper.java` | utility (pure mapper) | transform | itself (`manualDocument`, `line`) | partial |

### Frontend

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `F/api/roadmap.ts` | service (hooks) | CRUD | `F/api/products.ts` (list + mutations), `api/promotions.ts` (per-id mutations) | exact |
| `F/api/roadmap.ts` CSV hook | hook | file-I/O | `useExportDocument` in `api/documents.ts` 129-145 | exact |
| `F/api/roadmap.ts` `parseRoadmapSearch` | utility | transform | `parseDashboardSearch` / `parseProducts` in `api/dashboard.ts` 173-192 | exact |
| `F/routes/RoadmapRoute.tsx` + `router.tsx` route | route | request-response | `routes/PortfolioRoute.tsx`, `router.tsx` portfolioRoute (lines 83-91) | exact |
| `F/components/AppShell.tsx` nav link | component | n/a | its Portfolio `<Link>` (lines 61-66) | exact |
| `F/roadmap/RoadmapTable.tsx`, `InlineScoreCell.tsx`, `RubricPanel.tsx`, `InputHistoryPanel.tsx`, `ItemDetailRow.tsx` | component | CRUD | `components/PromotionSection.tsx`, `ProductsEditor.tsx` (inline save + `messageForReason`) | role-match |
| `F/roadmap/AddFromJiraDialog.tsx`, `AddFromReleaseDialog.tsx`, `AddManualItemDialog.tsx` | component | request-response | `components/AddTicketsToReleaseDialog.tsx`, `RecordInstallDialog.tsx` | exact |
| `F/components/PackagesLink.tsx` | component | CRUD | `components/ProductsEditor.tsx` | exact |
| `F/api/releases.ts` (`packagesUrl` field + `useSetPackagesUrl`) | hook | CRUD | `useSetReleaseProducts` lines 222-239 | exact |
| `F/routes/ReleaseDetailRoute.tsx` (PackagesLink row, ticket checkbox column, "Add to roadmap") | route | request-response | itself lines 295, 414-470 (ConfirmDialog), 549-600 (tickets table) | exact |
| `F/api/auth.ts` `REASON_MESSAGES` | utility | n/a | lines 124-140 | exact |
| `F/api/products.ts` invalidation list (add `'roadmap'`) | hook | CRUD | itself line 24 | exact |
| `F/tests/api/roadmap.test.ts`, `F/tests/roadmap/*.test.tsx` | test | n/a | `F/tests/api/portfolio.test.ts`, `F/tests/components/AddTicketsToReleaseDialog.test.tsx` | role-match |

---

## Pattern Assignments

### `V16__roadmap.sql` (migration)

**Analog:** `M/db/migration/V14__portfolio.sql`

Header comment, additive-only, case-insensitive unique index, NOT NULL + `btrim` CHECK, `ON DELETE RESTRICT` on audit FKs, append-only trigger (V14 lines 1-12, 93-102):

```sql
CREATE TABLE product (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at TIMESTAMPTZ,
    CONSTRAINT product_name_not_blank CHECK (length(btrim(name)) > 0)
);
CREATE UNIQUE INDEX product_name_unique ON product (lower(name));

-- The trigger is the append-only guarantee because the app role owns the tables. There is deliberately no statement-level
-- truncate trigger: test cleanup truncates release with CASCADE and row-level triggers do not fire on truncation (see V10).
CREATE FUNCTION client_deployment_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'client_deployment is append-only (PORT-05)' USING ERRCODE = 'integrity_constraint_violation';
END $$;
CREATE TRIGGER client_deployment_no_update_delete
    BEFORE UPDATE OR DELETE ON client_deployment
    FOR EACH ROW EXECUTE FUNCTION client_deployment_append_only();
```

Use RESEARCH sketch (lines 167-202) for `roadmap_item` / `roadmap_item_input_history`, adding the D-17-07 columns (`status`, `notes`, `target_quarter`, `owner`, `archived_at`) and `jira_unavailable_since`. `product_id UUID REFERENCES product(id) ON DELETE RESTRICT` copies V14's `product_client` FK style. Confidence stored as 100/80/50 with a CHECK `IN (50,80,100)` (D-17-03; RESEARCH sketch only had BETWEEN 0 AND 100). History trigger: copy the V14 function/trigger pair verbatim, renamed. The history rows must not be truncated-protected (see comment above).

PKG half (same file, D-17-19): `ALTER TABLE release ADD COLUMN packages_url VARCHAR(2048);` nullable, no default, no backfill, no CHECK on host (server is the authority, D-17-20). Do not touch `items_changed_at`. No ALTER on `release` exists in V14, so the closest additive-column precedent to read before writing is V11 (`story_points`) / V6 (`logo_render_height`).

---

### `RoadmapRejection` / exceptions (domain)

**Analog:** `B/domain/portfolio/PortfolioEditRejection.java` + `PortfolioEditRejectedException.java`

```java
public enum PortfolioEditRejection {
    PRODUCT_NAME_DUPLICATE,
    ...
}

public class PortfolioEditRejectedException extends RuntimeException {
    private final PortfolioEditRejection reason;
    public PortfolioEditRejectedException(PortfolioEditRejection reason) {
        super(reason.name());
        this.reason = reason;
    }
    public PortfolioEditRejection reason() { return reason; }
}
```

Constant names ARE the ProblemDetail `reason` codes (`PromotionRejection` javadoc: "Constant names are the ProblemDetail reason codes the SPA keys on"). Suggested codes: `ROADMAP_ITEM_NOT_FOUND` (separate exception, 404, like `ProductNotFoundException`), `ROADMAP_REACH_INVALID`, `ROADMAP_IMPACT_INVALID`, `ROADMAP_CONFIDENCE_INVALID`, `ROADMAP_EFFORT_INVALID`, `ROADMAP_PRODUCT_ARCHIVED` (409; D-15-07 no new links), `ROADMAP_TOO_MANY_ITEMS`.

Domain and ports must have no Spring/JPA imports (`ArchitectureBoundaryTest.domainAndPortsHaveNoFrameworkImports`, line 41).

---

### `GlobalExceptionHandler.java` (modify)

**Analog:** same file, lines 237-256 and helper at 496-501.

```java
@ExceptionHandler(PortfolioEditRejectedException.class)
public ProblemDetail handlePortfolioEditRejected(PortfolioEditRejectedException ex) {
    return switch (ex.reason()) {
        case PRODUCT_ARCHIVED -> problem(HttpStatus.CONFLICT, "Product archived",
                "That product is archived. Unarchive it first.", "PRODUCT_ARCHIVED");
        case RELEASE_PRODUCT_REQUIRED -> problem(HttpStatus.UNPROCESSABLE_ENTITY, "Product required",
                "Choose at least one product for this release.", "RELEASE_PRODUCT_REQUIRED");
        ...
    };
}
private ProblemDetail problem(HttpStatus status, String title, String detail, String reason) {
    ProblemDetail pd = ProblemDetail.forStatus(status);
    pd.setTitle(title); pd.setDetail(detail); pd.setProperty("reason", reason);
    return pd;
}
```

Add one `@ExceptionHandler` per new exception with an exhaustive `switch` on the enum (compiler enforces coverage). The packages-URL rejection is a **400** (D-17-20), e.g. `problem(HttpStatus.BAD_REQUEST, "Invalid packages link", "...", "RELEASE_PACKAGES_URL_INVALID")`. Mirror every new reason in `F/api/auth.ts` `REASON_MESSAGES`; `messageForReason` falls back to a generic string otherwise.

---

### `RoadmapRepository` (port) and `RoadmapRepositoryAdapter`

**Analogs:** `B/ports/ProductRepository.java`, `adapters/persistence/ProductRepositoryAdapter.java`, `PromotionRepositoryAdapter.java`

Port style (javadoc names the rejection thrown; no framework types):

```java
public interface ProductRepository {
    /** Archived included, ordered by lower(name). */
    List<Product> findAll();
    Optional<Product> findById(UUID productId);
    /** PRODUCT_NAME_DUPLICATE on a case-insensitive clash. */
    Product add(String name);
    ...
}
```

Adapter: `@Component`, constructor-injected JPA repo, `@Transactional(readOnly = true)` on reads, entity static factory + private static `toDomain`, `saveAndFlush` inside try/catch so a constraint fires inside the catch (ProductRepositoryAdapter lines 262-269):

```java
private void saveRejectingDuplicateName(ProductEntity entity) {
    try {
        // saveAndFlush forces product_name_unique to fire here rather than at commit, where it would escape this catch.
        jpa.saveAndFlush(entity);
    } catch (DataIntegrityViolationException e) {
        throw new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_NAME_DUPLICATE);
    }
}
```

Use that for the unique `jira_key` / `jira_issue_id` indexes if the insert path races (D-17-11 normally pre-checks and reports "already on the roadmap").

**Row-locked input edit + history in one transaction (ROAD-06):** copy `PromotionRepositoryAdapter.appendUnderLock` (lines 42-53); the lock query is `ReleaseJpaRepository.findByIdForUpdate` (lines 43-48):

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT r FROM ReleaseEntity r WHERE r.id = :id")
Optional<ReleaseEntity> findByIdForUpdate(@Param("id") UUID id);
```

```java
@Override
@Transactional
public Promotion appendUnderLock(UUID releaseId, Function<PromotionHistory, Promotion> decide) {
    ReleaseEntity release = releaseJpa.findByIdForUpdate(releaseId).orElseThrow(ReleaseNotFoundException::new);
    Promotion promotion = decide.apply(history(release));
    jpa.saveAndFlush(toEntity(promotion));
    return promotion;
}
```

Roadmap equivalent: `findByIdForUpdate` on `RoadmapItemJpaRepository`, apply a pure `RiceInputs.with(...)`/diff function supplied by the service, write the item and one history row per changed field. No-op saves write nothing.

**Jira-only refresh (D-17-12, Pitfall 4):** the targeted `@Modifying @Query` from RESEARCH lines 241-249. `ProductJpaRepository` shows the repo's `@Modifying`/native-query style (`deleteReleaseProduct`, `insertReleaseProduct`, lines 40+). Never load-modify-save a Jira item on the refresh path.

**Port must expose no mutation on the history ledger:** names matching `(update|delete|remove|save).*` are forbidden on append-only ports (see ArchUnit below). Name history methods `append...`/`record...`/`history...`.

---

### `RoadmapService.java`

**Analogs:** `B/application/ProductService.java` (shape), `PromotionService.java` (actor/clock), `ReleaseService.java` (Jira)

Shape: `@Service`, constructor injection, injected `Clock` (never `Instant.now()` in new code; ProductService/PromotionService use `clock.instant()`):

```java
@Service
public class ProductService {
    private final ProductRepository products;
    private final Clock clock;
    ...
    public void archive(UUID id) {
        if (!find(id).archived()) { products.setArchivedAt(id, clock.instant()); }
    }
    private Product find(UUID id) {
        return products.findById(id).orElseThrow(ProductNotFoundException::new);
    }
}
```

Actor pattern: the service takes the actor string from the controller (`PromotionService.record(releaseId, draft, actor)`); the controller passes `principal.getName()` (the Jira accountId, D-03-12).

Jira key safety and batching (copy from `ReleaseService` lines 40-48, 265-272):

```java
private static final Pattern TICKET_KEY = Pattern.compile("^[A-Z][A-Z0-9]*-[0-9]+$");
private static final int FETCH_BATCH_SIZE = 100;
...
private List<TicketDetail> fetchTickets(List<String> keys) {
    List<String> safe = keys.stream().filter(k -> k != null && TICKET_KEY.matcher(k).matches()).toList();
    ...
    for (int start = 0; start < safe.size(); start += FETCH_BATCH_SIZE) {
        List<String> batch = safe.subList(start, Math.min(start + FETCH_BATCH_SIZE, safe.size()));
```

Jira reads go through `TicketSearchService` (uses the signed-in user's own credentials via `JiraAccessContextService`; do not add a service token):

```java
public Optional<TicketDetail> findByKey(String key) {          // single-key path the adapter already handles
    return search(TicketSearchCriteria.byJql("key = " + key)).stream().findFirst();
}
public List<TicketDetail> search(TicketSearchCriteria criteria, UUID releaseScope) { ... }
```

Refresh rule (D-17-13, Pitfall 1): flag "unavailable" only after a call that succeeded; any `JiraTicketReadException` aborts without flagging; batch returning fewer than requested is re-queried per key with `findByKey`.

Do not reuse `release_ticket_item` / `ReleaseService.refresh` (RESEARCH anti-pattern). Adding from a release (D-17-10b): read tickets with `ReleaseService.get(releaseId).items()` (each `item.ticket()` already carries key, summary, status, `jiraIssueId`), then insert; no Jira call needed.

`ranked(filter)` must be a single public method returning an already-ordered list (CONTEXT discretion: reusable by Phase 20), consumed by both the list endpoint and CSV.

`ProductService.clientCounts()` already exists for the Reach hint (D-17-04); the roadmap response can carry linked-client count per product from `ProductService`/`ProductRepository.clientCounts()`.

---

### `RoadmapController.java` + DTOs

**Analogs:** `B/adapters/web/ProductController.java`, `ReleaseController.java`, `dto/*`

Controller skeleton (ProductController lines 93-135): `@RestController @RequestMapping("/api/...")`, constructor injection, `@Valid @RequestBody`, `@ResponseStatus(CREATED)`, returns the whole refreshed list so the SPA can `setQueryData`:

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ProductListResponse add(@Valid @RequestBody PortfolioNameRequest request) {
    service.add(request.name());
    return respond();
}
```

Actor: `Principal principal` as a method parameter (ReleaseController lines 63-74, 123-126). Comment to keep: every `/api/**` path is `authenticated()` in `SecurityConfig`, so no null check. Actor display in responses: resolve once per response, never per row (ReleaseController lines 79-85):

```java
ResolvedActors actors = actorResolution.resolve(summaries.stream()
        .flatMap(s -> Stream.of(s.createdBy(), s.frozenBy()))
        .toArray(String[]::new));
```

For history rows: `actorResolution.resolve(history.stream().map(...::changedBy).distinct().toArray(String[]::new))` then `actors.display(id)`. `ArchitectureBoundaryTest.actorResolutionResolvesThroughActorDirectoryOnly` forbids resolving any other way.

Product filter query param: reuse `DashboardController.productScope(List<UUID>)` (package-private static, lines 84-91, throws `invalid(...)` 400 over `MAX_PRODUCT_FILTER`), as `PortfolioDashboardController` does:

```java
@GetMapping("/summary")
public PortfolioDashboardResponse.Summary summary(@RequestParam(required = false) List<UUID> productIds,
                                                  @RequestParam(defaultValue = "false") boolean includeArchived) {
    PortfolioDashboardService.Summary s = service.summary(DashboardController.productScope(productIds), includeArchived);
```

DTO validation (boundary only; server is the authority per UI-SPEC):

```java
public record PortfolioNameRequest(@NotBlank @Size(max = 100) String name) {}

public record AddTicketsRequest(
        @NotEmpty
        @Size(max = 500, message = "at most 500 tickets can be added in one request")
        List<@Pattern(regexp = "^[A-Z][A-Z0-9]*-[0-9]+$",
                      message = "must be a Jira issue key such as CNV-1234") String> ticketKeys) {}
```

Reuse that exact `@Pattern` + `@Size` for the roadmap "add from Jira" request (pick a smaller cap; CONTEXT leaves the cap to the planner). Validation failures surface as `VALIDATION_FAILED` + `fieldErrors` (GlobalExceptionHandler ~465), which the manual-item dialog already renders per field.

Add-result DTO: mirror `ReleaseAddResultResponse(release, added, alreadyPresent, notFound)` shape (see `toAddResult` in ReleaseController lines 161-164) so the "{KEY} is already on the roadmap" + id link (D-17-11) is data, not string parsing: `alreadyPresent` must carry `{jiraKey, itemId}`.

---

### `CsvWriter.java` + CSV endpoint (ROAD-07)

**Analog (download headers):** `DocumentController.exportPdf` lines 102-115.

```java
return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION, ExportFilename.attachment(filename))
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(bytes);
```

Use `text/csv; charset=utf-8`, the same three hardening headers, and a fixed filename `roadmap-{yyyy-MM-dd}.csv` built from the injected `Clock` (not free text, so `ExportFilename.sanitize` is not needed; `ExportFilename.attachment(filename)` is reusable as-is, line 142). `ExportFilename` is also the style template for `CsvWriter`: `public final class`, private constructor, static methods, allowlist over denylist (lines 115-163). Formula-injection prefix on text columns only (RESEARCH Pitfall 6), UTF-8 BOM, CRLF row separator. No existing CSV code in the repo.

---

### `ArchitectureBoundaryTest.java` (modify)

**Analog:** lines 125-132

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

Add one for the roadmap history port only if history lives on its own port interface (the main `RoadmapRepository` needs `update...` for items, so split the ledger into `RoadmapHistoryRepository`, or this rule cannot apply). Planner decision.

---

### Migration test `V16RoadmapMigrationIT.java`

**Analog:** `T/adapters/persistence/V14PortfolioMigrationIT.java` lines 28-70. `@Testcontainers`, `PgVectorImage.IMAGE`, migrate to target 15 (V15 is Phase 19's; if not yet present, target 14 is the highest existing, so use `MigrationVersion.fromVersion("14")`), load `db/v1_1-snapshot.sql`, capture aggregates, migrate to latest, assert the pre-existing `release` rows are byte-identical (`RELEASE_AGGREGATE`). Add an assertion that `packages_url` is NULL on every pre-existing release and that `items_changed_at` is unchanged.

---

### Packages link: `Release`, entity, adapter, service

**`Release` record** (`B/domain/release/Release.java` lines 19-22): add `String packagesUrl` as a component. WARNING: `new Release(...)` is called positionally in 2 main sites (`Release.create`, `ReleaseRepositoryAdapter.toDomain` line 479) and ~20 test sites (`ReleaseControllerTest`, `IndexDocumentMapperTest`, `IndexSyncerTest`, `ReleasesToolTest`, `TicketsToolTest`, `InstallsToolTest`, `DocumentGenerationServiceTest`, `EnrichmentServiceTest`, `GenerateThenExportSeamTest`, `ReleaseServiceManualItemsTest`, `DocumentExportControllerTest`). The planner must budget a mechanical sweep (append `null` as the new last argument) in the same plan as the record change, and run `mvn clean` (env landmine: incremental compilation).

**Entity** (`ReleaseEntity.java`, plain getter/setter style):

```java
@Column(name = "jira_project_key")
private String jiraProjectKey;
```
Add `@Column(name = "packages_url") private String packagesUrl;` with getter/setter. `toEntity(Release)` (adapter lines 448-463) copies fields; add the line. `toDomain` (line 478-482) takes `e.getPackagesUrl()`.

**Repository write: copy `ReleaseProductRepositoryAdapter.replace` (lines 55-84)**: lock first, deliberately no status check, and do NOT stamp `items_changed_at` (D-17-21; the `replace` code stamps only when the product set changed, here never):

```java
@Override
@Transactional
public void replace(UUID releaseId, Set<UUID> requested) {
    ReleaseEntity release = releaseJpa.findByIdForUpdate(releaseId).orElseThrow(ReleaseNotFoundException::new);
    // D-15-01: deliberately no status check - products stay editable after FROZEN so v1.1 releases can be tagged.
```

New port method on `ReleaseRepository` (e.g. `Release setPackagesUrl(UUID releaseId, String urlOrNull)`), NOT `lockOpen` (that throws `ReleaseFrozenException`). Then `publisher.publishEvent(new ReleaseIndexInvalidated(releaseId))` as `freeze` does (adapter lines 263-272) so the assistant index re-syncs (D-17-23); the listener is `@TransactionalEventListener(AFTER_COMMIT)`, enqueue-only.

**Service** (copy `ReleaseService.setProducts`, lines 76-80):

```java
// D-15-01: no requireOpen - a FROZEN release's products stay editable ...
public Release setProducts(UUID releaseId, Collection<UUID> productIds) {
    releaseProducts.replace(releaseId, Set.copyOf(productIds));
    return get(releaseId);
}
```
Validate host in the domain/service (new `PackagesUrl.parse(String)` returning the normalized string or throwing a rejection with reason `RELEASE_PACKAGES_URL_INVALID`): parse with `java.net.URI`, require scheme `https` (case-insensitive), non-null host, host equals `onedrive.live.com` / `1drv.ms` or ends with `.sharepoint.com`, reject userinfo, cap length (match the column), blank/null means clear. Use `URI` host, not string `contains`/`endsWith` on the raw URL (userinfo and `evil.com/.sharepoint.com` tricks). No existing URL validator in the repo to copy.

**Controller** (copy `setProducts`, `ReleaseController` lines 95-99):

```java
@PutMapping("/{releaseId}/products")
public ReleaseResponse setProducts(@PathVariable UUID releaseId,
                                   @Valid @RequestBody ReleaseProductsRequest request) {
    return toResponse(service.setProducts(releaseId, request.productIds()));
}
```
New `PUT /{releaseId}/packages-url` with `PackagesUrlRequest(@Size(max = 2048) String url)` (null/blank = clear, D-17-24). Add `packagesUrl` to `ReleaseResponse` (and `toResponse`, ReleaseController lines 166-180). `ReleaseSummaryResponse` does not need it.

---

### Assistant: `ReleasesTool.ReleaseDetail` (D-17-23, PKG-02)

**Analog:** `B/application/assistant/tools/ReleasesTool.java` lines 34-38 and 87-100. Append one component to the record and one argument at the end of the constructor call:

```java
public record ReleaseDetail(UUID id, String name, String version, ReleaseStatus status, Instant createdAt,
                            Instant frozenAt, Instant lastRefreshedAt, Instant itemsChangedAt, int ticketCount,
                            int manualItemCount, Map<String, Long> ticketsByStatusCategory,
                            String currentStage, String nextStage, Instant shippedAt) { }
```
Add `String packagesUrl` (null when unset). Do NOT touch `description()` or `parameters()` (the 6-tool / ~1900-char cap in rag-service is on the tool definitions, not on result JSON; D-16-16). `ReleasesToolTest` constructs `Release` positionally (line 49), so it is in the `new Release(` sweep. Tools are read-only and non-transactional (ArchUnit `toolsCallOnlyAllowlistedReadMethodsOnTheRealClasses`, `toolsAreNotTransactional`): reading `release.packagesUrl()` off the `Release` already returned by `releaseService.get(id)` adds no new call. Check `ToolOutput` size cap (`ToolResultTooLargeException` in rag-service) is not threatened: one URL <= 2048 chars; consider capping URL at ~500 chars on the way in if the cap is tight.

### Assistant: `IndexDocumentMapper` (D-17-23, "indexed release text")

**Analog:** `B/application/assistant/IndexDocumentMapper.java`: `manualDocument` (lines 106-119), `line(...)` helper (158-162), `releaseMetadata` (142-149).

```java
StringBuilder text = new StringBuilder();
line(text, "Title", manual.title());
...
return new IndexDocument(IndexCollection.MANUAL_ITEMS, "manual:" + manual.id(), clean(manual.title()),
        clean(text.toString()), metadata);
```

GAP: the mapper emits only per-ticket, per-code-links, per-manual-item and per-document `IndexDocument`s; there is NO release-level document. "Indexed release text" has no existing home. Options for the planner: (a) add one new `IndexDocument` per release (new id scheme `release:<releaseId>`, text `line(text, "Packages", release.packagesUrl())` plus name/version/status) in an existing collection, because adding an `IndexCollection` enum value makes `IndexSyncer.ensureCollection` create a new rag collection (lines 71-79) and touches rag-service config; (b) append a `Packages:` line into an existing text. (a) with `IndexCollection.DOCUMENTS` is the least invasive but check how `IndexSyncer` diffs ids (stale-id delete, lines ~100-120) and that `DocumentSections`/citation views (`AssistantSourceViews`) tolerate an id prefix they do not know. Update `IndexDocumentMapperTest` (builds `Release` positionally, line 128/147) and `IndexSyncerTest` (line 194). Never emit the URL when null (`put`/`line` already skip null/blank).

---

### `F/api/roadmap.ts`

**Analog:** `F/api/products.ts` (list + shared mutation factory + `setQueryData`), `F/api/promotions.ts` (per-id mutations).

```ts
export const productsQueryKey = ['products'] as const;
export function useProducts() {
  return useQuery({ queryKey: productsQueryKey, queryFn: () => apiFetch<ProductList>(PRODUCTS_URL) });
}
function useProductMutation<TVars>(request: (vars: TVars) => Promise<ProductList>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (list) => {
      queryClient.setQueryData(productsQueryKey, list);
      for (const key of ['clients', 'client', 'release', 'releases', 'installs', 'dashboard']) {
        queryClient.invalidateQueries({ queryKey: [key] });
      }
    },
  });
}
function send(path: string, method: string, body?: unknown) {
  return apiFetch<ProductList>(`${PRODUCTS_URL}${path}`, {
    method,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
}
```

Query key must include the filter (`['roadmap', {products, done, archived}]`) so a single `invalidateQueries({queryKey:['roadmap']})` refreshes all variants (UI-SPEC "Cache"). Modify `products.ts` line 24's key list to add `'roadmap'` (the Reach hint reads client counts, UI-SPEC line 391).

CSV hook: copy `useExportDocument` (documents.ts 129-145):

```ts
const blob = await apiFetchBlob(url);
triggerDownload(blob, exportFilename(...));
```
Both helpers are exported from `F/api/client.ts` (`apiFetchBlob` line 105, `triggerDownload` 116). Pass the same query string the table uses, so "exports what is on screen" is literally the same request params. The filename comes from the server `Content-Disposition` or is built client-side as `roadmap-YYYY-MM-DD.csv` (UI-SPEC).

Search params parser: copy `parseProducts` and `parseDashboardSearch` (dashboard.ts 173-192): UUID-regexp filter, `MAX_PRODUCT_FILTER` slice, `archived === true || archived === 'true' ? true : undefined`. Add `done`, `item` (UUID-validated).

---

### `F/routes/RoadmapRoute.tsx` + `router.tsx` + `AppShell.tsx`

Route registration (router.tsx lines 83-91, 125-136):

```ts
const portfolioRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/portfolio',
  validateSearch: (search: Record<string, unknown>): { tab?: ...; client?: string } => ({ ... }),
  component: PortfolioRoute,
});
...
authenticatedRoute.addChildren([dashboardRoute, ticketsRoute, releasesRoute, releaseDetailRoute, portfolioRoute, assistantRoute, ...])
```
Add `roadmapRoute` with `validateSearch: parseRoadmapSearch` and list it after `portfolioRoute`.

Nav link (AppShell.tsx lines 61-66), insert between Portfolio and the Assistant block:

```tsx
<Link to="/portfolio" className="text-gray-600 hover:text-gray-900"
      activeProps={{ className: 'font-semibold text-gray-900' }}>
  Portfolio
</Link>
```

Filter UI: `ProductFilter` is reused unchanged (`props: products, selected, onChange`, `F/components/ProductFilter.tsx`); URL updates via `navigate({ replace: true })`. See `DashboardRoute.tsx` for the existing consumer.

---

### Add dialogs (`AddFromJiraDialog`, `AddFromReleaseDialog`, `AddManualItemDialog`)

**Analog:** `F/components/AddTicketsToReleaseDialog.tsx` (copy whole structure). `<dialog>` kept mounted, `showModal()`/`close()` in a `useEffect` on `props.open`, mutation `reset()` on open, `TicketBrowser` mounted only while open:

```tsx
useEffect(() => {
  const dialog = dialogRef.current;
  if (!dialog) return;
  if (props.open && !dialog.open) { addTickets.reset(); dialog.showModal(); }
  if (!props.open && dialog.open) dialog.close();
}, [props.open]);
...
<dialog ref={dialogRef} onClose={props.onClose}
  className="w-[min(90vw,72rem)] max-w-none rounded border border-gray-200 bg-white p-6">
...
{props.open && (
  <TicketBrowser releaseId={props.releaseId} actionLabel="Add to this release"
    onAction={(ticketKeys) => addTickets.mutate({ releaseId, ticketKeys }, { onSuccess: (result) => { props.onAdded(result); props.onClose(); } })} />
)}
```
Roadmap: `actionLabel="Add to roadmap"`, `onAction={(keys) => ...}` (the second arg `jiraProjectKey` is ignored). Error block: `role="alert" data-reason={problem.reason}` + `messageForReason(problem)`. Spinner: `Spinner` exported from `components/ExportButtons.tsx`. Manual-item dialog and release checklist: copy `RecordInstallDialog.tsx` (`FIELD = 'w-full rounded border border-gray-300 px-3 py-2 text-sm'`, line 21, module-local; export it or redeclare once).

---

### `F/components/PackagesLink.tsx` (+ `ReleaseDetailRoute` placement)

**Analog:** `F/components/ProductsEditor.tsx` (whole file, 172 lines). Copy: `useState(open)`, `announcement` + `<p role="status" aria-live="polite" className="sr-only">`, `useRef` button focus return on close, `aria-expanded` / `aria-controls={EDITOR_ID}`, inline panel `id=... className="mt-3 rounded border border-gray-200 bg-white p-4"`, problem rendering:

```tsx
const problem = save.isError && save.error instanceof ApiError ? save.error.problem : undefined;
...
<button type="button" disabled={unchanged || save.isPending}
  onClick={() => save.mutate(selected, { onSuccess: () => onClose(true) })}
  className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">
...
{problem && (
  <p role="alert" data-reason={problem.reason} className="mt-2 text-sm text-red-600">
    {messageForReason(problem)}
  </p>
)}
```
Imports to copy: `ApiError` from `../api/client`, `messageForReason` from `../api/auth`, `SMALL_BUTTON` from `./EnvironmentLadderSection`. Do NOT copy the "marks documents out of date" warning (lines 158-163; UI-SPEC explicitly omits it). Mount directly after `<ProductsEditor release={data} />` (`ReleaseDetailRoute.tsx` line 295), unconditional on `isOpen`.

Clear confirmation: `ConfirmDialog` (`F/components/ConfirmDialog.tsx`, props `open,title,body,confirmLabel,confirmClassName,pending,onConfirm,onCancel`). Existing usages at `ReleaseDetailRoute.tsx` lines 414-470; copy the `onSettled` close pattern and the comment at ~422.

Hook (`F/api/releases.ts`, copy `useSetReleaseProducts` lines 222-239):

```ts
export function useSetReleaseProducts(releaseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (productIds: string[]) =>
      apiFetch<Release>(`/api/releases/${releaseId}/products`, { method: 'PUT', body: JSON.stringify({ productIds }) }),
    onSuccess: (release) => {
      queryClient.setQueryData(releaseQueryKey(releaseId), release);
      queryClient.invalidateQueries({ queryKey: ['releases'] });
      ...
```
For packages: `setQueryData(releaseQueryKey(id), release)` only. Do NOT invalidate `['documents','readiness']` or `['dashboard']` (D-17-21/22: never makes documents stale). Add `packagesUrl: string | null` to the `Release` type (releases.ts line 109, not `ReleaseSummary`).

Client pre-check: new `new URL()`-based helper, hostname lowercased, `protocol === 'https:'`, `hostname === 'onedrive.live.com' || '1drv.ms' || endsWith('.sharepoint.com')`. No analog; keep it a tiny pure function next to the component so it is unit-testable.

`REASON_MESSAGES` (F/api/auth.ts ~124-140): add
`RELEASE_PACKAGES_URL_INVALID: 'Use an https link to OneDrive or SharePoint (sharepoint.com, onedrive.live.com or 1drv.ms).'`

---

### `ReleaseDetailRoute.tsx` ticket checkbox column + "Add to roadmap"

Existing table header/rows at lines 549-600: the table uses plain `<th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">` and rows with a click handler that ignores `button, a, input, label` (line ~590), so a checkbox cell is already safe from row-toggle. Selection state/indeterminate header checkbox: copy from `TicketBrowser.tsx` (`selected: ReadonlySet<string>` state, line ~55; select-all handling in the same file). Result copy and "View it"/"Go to Roadmap" links use TanStack `Link` (`ProductsEditor.tsx` line 90 shows `<Link to="/portfolio" className="text-blue-600 underline">`).

---

## Shared Patterns

### Error contract (reason codes)
**Source:** `B/adapters/web/GlobalExceptionHandler.java` (`problem(...)` at 496-501) and `F/api/auth.ts` `REASON_MESSAGES` / `messageForReason` (lines ~143-149).
**Apply to:** every new rejection, backend and frontend. Every new `reason` needs both sides in the same plan. Framework errors get `reason = <HTTP status name>` automatically (line 472-490).

### Frontend error rendering
**Source:** `ProductsEditor.tsx` line 77, 165-169.
**Apply to:** every new mutation surface.
```tsx
const problem = save.isError && save.error instanceof ApiError ? save.error.problem : undefined;
{problem && <p role="alert" data-reason={problem.reason} className="mt-2 text-sm text-red-600">{messageForReason(problem)}</p>}
```

### Authenticated actor
**Source:** `ReleaseController.create` (lines 63-74) and `freeze` (123-126): `Principal principal` -> `principal.getName()`.
**Apply to:** roadmap add, input edit, status/archive edits (history `changed_by`), item `created_by`.

### Row lock for check-then-write
**Source:** `ReleaseJpaRepository.findByIdForUpdate` + `PromotionRepositoryAdapter.appendUnderLock`.
**Apply to:** roadmap input edits (and any history-writing edit). Refresh must use a targeted UPDATE instead.

### Jira reads
**Source:** `TicketSearchService` (`application/TicketSearchService.java`), `ReleaseService.TICKET_KEY` + `FETCH_BATCH_SIZE`.
**Apply to:** roadmap add-by-key and refresh. Keys validated at the REST boundary and again at the sink; Jira is read-only.

### Index invalidation
**Source:** `ReleaseRepositoryAdapter.freeze/unfreeze` -> `publisher.publishEvent(new ReleaseIndexInvalidated(releaseId))`.
**Apply to:** `setPackagesUrl` only (roadmap is not indexed).

### Test environment landmines (from project memory and RESEARCH Pitfall 10)
No `mvnw`; `export JAVA_HOME="$HOME/.jdks/jdk-25"` on every Maven call; `mvn clean` after changing public record shapes (the `Release` change); new config properties go in BOTH `src/main/resources/application.yml` and `src/test/resources/application.yml`; frontend `npm ci` can hit a Vite EPERM; plan grep criteria should assert properties, not counts.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `B/domain/roadmap/RiceScorer.java` | utility | transform | No BigDecimal scoring or ranking comparator exists. Use RESEARCH Pattern 1 (`MathContext(20, HALF_EVEN)`, `compareTo`, total order ending on `id`; D-17-15 tie-breaks: score desc, Impact desc, createdAt asc, id). Unscored bucket: createdAt asc, id asc. |
| `B/adapters/web/CsvWriter.java` | utility | transform | No CSV writer exists (partial analog `ExportFilename` for class style only). RFC 4180 quoting + `=,+,-,@,TAB,CR` prefix guard on text columns + BOM. |
| `B/domain/release/PackagesUrl` host allow-list | validator | transform | No URL/host validator in the repo. Use `java.net.URI` host checks, not substring matching. |
| Release-level index document (D-17-23) | mapper | transform | `IndexDocumentMapper` has no release-level document; see decision note in the assistant section. |

## Metadata

**Analog search scope:** `src/main/java/com/evision/releasemgmt/{domain,ports,application,adapters/{web,persistence},application/assistant}`, `src/main/resources/db/migration`, `src/test/java/.../{adapters,ArchitectureBoundaryTest}`, `frontend/src/{api,components,routes,router.tsx}`.
**Files scanned:** about 45 read in full or in targeted ranges (Phase 15 product slice, release controller/service/adapter, promotion adapter, assistant tool and mapper, frontend products/releases/promotions hooks, ProductsEditor, dialogs, router, AppShell).
**Pattern extraction date:** 2026-10-25
