# Phase 16: Portfolio Dashboard & Assistant Answers - Pattern Map

**Mapped:** 2026-10-22
**Files analyzed:** 27 (new and modified)
**Analogs found:** 27 / 27 (all from `main`, not the -live worktree)

Paths below are relative to `D:/IdeaProjects/ReleaseManagement`. `J` = `src/main/java/com/evision/releasemgmt`, `T` = `src/test/java/com/evision/releasemgmt`, `F` = `frontend`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `J/application/DashboardService.java` (MOD: `releases(all, productIds)`, `deployments(..., productIds)`) | service | request-response | itself (lines 68-97, 140-143) | exact |
| `J/ports/DashboardReadRepository.java` (MOD: `releaseIdsCoveringAny`, `findDeployments(..., productIds)`) | port | request-response | itself | exact |
| `J/adapters/persistence/DashboardReadRepositoryAdapter.java` (MOD) | adapter | request-response | itself (lines 68-77) | exact |
| `J/adapters/persistence/repository/DashboardJpaRepository.java` (MOD: EXISTS predicate, id query) | repository | request-response | itself (lines 48-65) | exact |
| `J/adapters/web/DashboardController.java` (MOD: `productIds` params, cap) | controller | request-response | itself (lines 42-82) | exact |
| `J/application/PortfolioDashboardService.java` (NEW) | service | transform (load once, compute in Java) | `J/application/DashboardService.java` + `J/application/ClientService.java` | role-match |
| `J/domain/portfolio/` pure behind computation (NEW, e.g. `ClientsBehind`) | domain | transform | `J/domain/dashboard/Stats` (pure static helpers) and `J/domain/portfolio/CurrentRun.java` records | role-match |
| `J/ports/PortfolioReadRepository.java` (NEW, read-only) | port | request-response | `J/ports/DashboardReadRepository.java` | exact |
| `J/adapters/persistence/PortfolioReadRepositoryAdapter.java` (NEW) | adapter | request-response | `J/adapters/persistence/DashboardReadRepositoryAdapter.java` | exact |
| `J/adapters/persistence/repository/PortfolioJpaRepository.java` + projection rows (NEW) | repository | request-response | `DashboardJpaRepository.java` + `ClientDeploymentJpaRepository.currentRunsOfClient` + `CurrentRunRow.java` | exact |
| `J/adapters/web/PortfolioDashboardController.java` (NEW, `/api/dashboard/portfolio/*`) | controller | request-response | `J/adapters/web/DashboardController.java` | exact |
| `J/adapters/web/dto/PortfolioDashboardResponse.java` (NEW) | DTO | request-response | `J/adapters/web/dto/DashboardResponse.java`, `ClientDetailResponse.java` | exact |
| `J/application/assistant/tools/InstallsTool.java` (NEW) | tool | request-response | `J/application/assistant/tools/DeploymentsTool.java` (+ `ReleaseRef.java`) | exact |
| `J/application/assistant/tools/DashboardTool.java` (MOD: `releases(all, Set.of())`) | tool | request-response | itself, line ~70 | exact |
| `J/application/assistant/tools/DeploymentsTool.java` (MOD: pass `Set.of()` at line 115) | tool | request-response | itself | exact |
| `src/main/resources/application.yml` (MOD: `tool-descriptions.installs`) | config | n/a | existing `tool-descriptions` block (lines ~74-79) | exact |
| `rag-service/src/main/resources/application.yml`, rag-service test `application.yml`, `ToolPropertiesTest` (MOD: max-tools 6, chars ~1900) | config/test | n/a | `rag.tools` block (lines 78-84) | exact |
| `T/application/assistant/tools/ToolDefinitionsTest.java` (MOD) | test | n/a | itself | exact |
| `T/application/assistant/tools/InstallsToolTest.java` (NEW) | test | request-response | `T/application/assistant/tools/DeploymentsToolTest.java` | exact |
| `T/application/PortfolioDashboardServiceTest.java`, `ClientsBehindTest` (NEW) | test | transform | `T/application/DashboardServiceTest.java` | exact |
| `T/application/DashboardServiceTest.java` (MOD) | test | transform | itself | exact |
| `T/adapters/persistence/PortfolioDashboardPersistenceIT.java` (NEW) and `DashboardPersistenceIT` (MOD) | IT test | CRUD | `T/adapters/persistence/DashboardPersistenceIT.java` (also `ClientDeploymentPersistenceIT`, `PortfolioPersistenceIT`) | exact |
| `T/adapters/web/DashboardControllerTest.java` (MOD), `DashboardSpanContractTest` (MOD) | test | request-response | themselves | exact |
| `F/src/api/dashboard.ts` (MOD: search schema, `productIds`, portfolio hooks) | api/hook | request-response | itself | exact |
| `F/src/routes/DashboardRoute.tsx` (MOD: tab shell, clickable tiles) | route/component | request-response | itself + `F/src/routes/PortfolioRoute.tsx` (tab bar) | exact |
| `F/src/components/ProductFilter.tsx`, `PortfolioTab.tsx` (+ sections) (NEW) | component | request-response | `DashboardRoute.tsx` sections, `PortfolioRegisterPanel.tsx` ("Show archived") | role-match |
| `F/src/api/installs.ts`, `clients.ts`, `products.ts`, `releases.ts` (MOD: invalidate `['dashboard']`) | api/hook | CRUD | `installs.ts` `useInstallMutation` (lines 56-68) | exact |
| `F/tests/components/DashboardRoute.test.tsx` (MOD), `DashboardPortfolioTab.test.tsx` (NEW), `tests/api/dashboard.test.ts`, `tests/api/portfolio.test.ts` (MOD) | test | n/a | `DashboardRoute.test.tsx`, `portfolio.test.ts` `runMutation` | exact |

## Pattern Assignments

### `DashboardService.java` MOD: product filter (service, request-response)

**Analog:** itself, `J/application/DashboardService.java`.

**Where the filter goes** (lines 68-84): filter inside the loop over `ledger.releases()` before the `PromotionHistory`/visibility logic. `summarize(..., ledger.releases().size())` at line 96 stays the global `totalReleases` (research decision).
```java
public DashboardView releases(boolean all) {
    DashboardLedger ledger = repository.loadLedger();
    Map<UUID, List<Promotion>> promotionsByRelease = ledger.promotions().stream()
            .collect(Collectors.groupingBy(Promotion::releaseId));
    ...
    List<Candidate> visible = new ArrayList<>();
    for (DashboardRelease release : ledger.releases()) {
        PromotionHistory history = new PromotionHistory(release.id(), release.status() == ReleaseStatus.FROZEN,
                release.frozenAt(), ledger.stages(), promotionsByRelease.getOrDefault(release.id(), List.of()));
```
Change to `releases(boolean all, Set<UUID> productIds)`; `Set<UUID> inScope = productIds.isEmpty() ? null : repository.releaseIdsCoveringAny(productIds);` then `if (inScope != null && !inScope.contains(release.id())) continue;`. No one-arg overload (CLAUDE.md: no compat shims). Update callers: `DashboardController:49`, `DashboardTool:~70`, `DashboardServiceTest`, `DashboardToolTest`, `DashboardControllerTest` lines 98/128.

**Deployments** (lines 140-143): add `Set<UUID> productIds` param; `DeploymentsTool:115` passes `Set.of()`.

**Empty-input guard** to copy for the id-set port method (`DashboardReadRepositoryAdapter` lines 54-57):
```java
if (releaseIds.isEmpty()) {
    return List.of();
}
```

### `DashboardJpaRepository.java` MOD: EXISTS predicate and id query (repository)

**Analog:** itself lines 48-65. Optional-parameter idiom is `CAST(:stageId AS uuid) IS NULL OR ...`; add (never JOIN):
```sql
AND (:filterByProduct = false OR EXISTS (SELECT 1 FROM release_product rp
        WHERE rp.release_id = p.release_id AND rp.product_id IN (:productIds)))
```
Adapter substitutes a single nil UUID into `:productIds` when the set is empty (pattern from `findChurnItems` guard). Keep file header comment: "Read-only: extends the bare Repository marker ... Every value is a bound parameter." Also add `SELECT DISTINCT release_id FROM release_product WHERE product_id IN (:ids)` here (or in the new Portfolio repo).

### `PortfolioJpaRepository.java` + rows (NEW)

**Analog:** `ClientDeploymentJpaRepository.currentRunsOfClient` (lines 31-38) and `CurrentRunRow` interface projection. Extend to all clients:
```java
@Query(value = """
        SELECT c.product_id AS productId, c.release_id AS releaseId, r.name AS releaseName,
               r.version AS releaseVersion, c.installed_at AS installedAt
          FROM current_client_release c
          JOIN release r ON r.id = c.release_id
         WHERE c.client_id = :clientId
        """, nativeQuery = true)
List<CurrentRunRow> currentRunsOfClient(@Param("clientId") UUID clientId);
```
New query: same select plus `c.client_id AS clientId`, no WHERE; second query over `effective_client_deployment d WHERE d.outcome='SUCCESS' AND d.installed_at >= :since` returning `productId, clientId`; third `release_product` all pairs. Interface projection style (getters, `UUID getX()`) from `CurrentRunRow.java` / `DashboardReleaseRow.java`. Declare as `extends Repository<ReleaseEntity, UUID>` like `DashboardJpaRepository` (read-only marker). Views: `current_client_release` / `effective_client_deployment` in `V14__portfolio.sql` lines 105-115 (no migration needed).

### `PortfolioReadRepository.java` (port) and `PortfolioReadRepositoryAdapter.java` (NEW)

**Analog:** `J/ports/DashboardReadRepository.java` (header: "Read-only by construction: an update, delete, remove or save method here is a defect.") and `DashboardReadRepositoryAdapter`:
```java
@Component
public class DashboardReadRepositoryAdapter implements DashboardReadRepository {
    ...
    @Override
    @Transactional(readOnly = true)
    public List<DeploymentRow> findDeployments(...) {
        return jpa.deployments(...).stream().map(r -> new DeploymentRow(r.getId(), ...)).toList();
    }
```
Releases and promotions for "in Production" come from the existing `DashboardReadRepository.loadLedger()` (do not duplicate). Products/clients from `ProductRepository.findAll()` / `ClientRepository.findAll()`; `Client` has `productIds()`, `archived()`, `linkedTo(productId)`; `Product` has `archived()`.

### `PortfolioDashboardService.java` (NEW, service, transform)

**Analog 1:** `DashboardService` (constructor with port + injected `Clock`, nested public records for views, `@Service`, comment header stating invariants). **Analog 2:** `ClientService.detail` for per-release "reached production" (replace its per-release `promotions.load` with the grouped ledger).

Reuse this exact construction for `inProduction` (DashboardService lines 70, 77-79):
```java
Map<UUID, List<Promotion>> promotionsByRelease = ledger.promotions().stream()
        .collect(Collectors.groupingBy(Promotion::releaseId));
PromotionHistory history = new PromotionHistory(release.id(), release.status() == ReleaseStatus.FROZEN,
        release.frozenAt(), ledger.stages(), promotionsByRelease.getOrDefault(release.id(), List.of()));
... history.firstProductionSuccess()
```
Anti-pattern to avoid (ClientService lines 80-83, comment says "Phase 16 batches this for the matrix"):
```java
Map<UUID, Boolean> inProduction = new HashMap<>();
installs.forEach(i -> inProduction.computeIfAbsent(i.entry().releaseId(), this::reachedProduction));
```
Name-sorted case-insensitive idiom (ClientService line 93): `String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name())`. Time: `clock.instant().minus(Duration.ofDays(30))`. Behind: frozen releases per product sorted by `(frozenAt, id)`; `behind = size - 1 - rank`; absent => 0 (see RESEARCH Pattern 3).

### `PortfolioDashboardController.java` (NEW) and DashboardController MOD

**Analog:** `J/adapters/web/DashboardController.java`. Copy class shape, validation helper, and DTO mapping:
```java
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    ...
    @GetMapping("/releases")
    public DashboardResponse releases(@RequestParam(defaultValue = "active") String scope) {
        boolean all = switch (scope) {
            case "active" -> false;
            case "all" -> true;
            default -> throw invalid("scope must be 'active' or 'all'");
        };
    ...
    private static ErrorResponseException invalid(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Request validation failed");
        problem.setProperty("reason", "VALIDATION_FAILED");
        return new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
    }
```
Add `@RequestParam(required = false) List<UUID> productIds`, cap constant with the contract-comment style at lines 28-29 ("Must equal MAX_... in frontend/src/api/dashboard.ts (DashboardSpanContractTest keeps them in step)"). Put `invalid` in the new controller too (it is `private static`; either duplicate or make package-private and share, planner decides; avoid a new abstraction). Records-to-response mapping via private static `toResponse` methods (lines 90-117).

### `PortfolioDashboardResponse.java` (NEW DTO)

**Analog:** `DashboardResponse.java` (single record with nested records) and `ClientDetailResponse` (`Current(UUID releaseId, String releaseName, String releaseVersion, Instant installedAt, boolean inProduction)`; reuse that field set for the matrix `run`).
```java
public record DashboardResponse(Summary summary, List<StageResponse> stages, List<ReleaseRow> releases) {
    public record Summary(int visibleReleases, int totalReleases, ...) { }
```

### `InstallsTool.java` (NEW, tool, request-response)

**Analog:** `J/application/assistant/tools/DeploymentsTool.java`.

**Class header / registration** (lines 26-28; ToolRegistry picks up beans, no manual registry edit seen):
```java
@Component
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class DeploymentsTool implements AssistantTool {
```
**Contract** (lines 68-98): `name()` lower_snake, one-sentence `description()` <= 300 chars, flat `parameters()` with `maxLength` only (no per-property descriptions to save chars), `call` branches on `arguments.get("release")`:
```java
@Override
public Map<String, Object> parameters() {
    return Map.of("type", "object",
            "properties", Map.of(
                    "release", Map.of("type", "string", "maxLength", ReleaseRef.MAX_LENGTH),
                    "stage", Map.of("type", "string", "maxLength", STAGE_MAX_LENGTH),
                    ...),
            "additionalProperties", false);
}
public ToolOutput call(Map<String, Object> arguments) {
    Object release = arguments.get("release");
    if (release != null) {
        UUID id = releaseRef.resolve(release);
        return new ToolOutput(history(...), "deployments:" + id, "ReleaseManagement: deployment history");
    }
    return new ToolOutput(recent(...), "deployments:recent", "ReleaseManagement: recent deployments");
}
```
Result is a public nested `record` of structured fields only (lines 36-48). Name resolution for client/product: copy `stageId(String)` (lines 125-131): case-insensitive exact match, failure via `ToolArgumentException("unknown ... , <candidates>")`. Release resolution via injected `ReleaseRef` (`resolve(Object)` returns UUID; throws `ToolArgumentException` for ambiguity). Result must include `state` (`RUNS`/`NOT_INSTALLED`/`NOT_LINKED`) and `inProduction`; no free-text note fields. Scoped chat (Pitfall 3): `release`-only call must work.

### Tool-budget config and tests (rag-service yml x2, `ToolPropertiesTest`, `ToolDefinitionsTest`)

**Analog:** `rag-service/src/main/resources/application.yml` lines 78-84:
```yaml
  tools:
    ...
    max-calls: 4
    max-tools: 5
    definitions-max-chars: 1500
```
Change to 6 / ~1900 here, in rag-service test `application.yml` (~line 78), `ToolPropertiesTest` (~line 63). Do NOT edit `prompts:` (promptHash). Host `application.yml` `tool-descriptions` (lines ~74-79) add `installs: "..."` (<= 300 chars) alongside the five existing entries.

`ToolDefinitionsTest` (host), lines 28-31, 41-46, 61-69: update constants and add the tool to the list:
```java
private static final int MAX_TOOLS = 5;
private static final int DEFINITIONS_MAX_CHARS = 1500;
...
return List.of(
        new ReleasesTool(releaseService, promotionService, releaseRef),
        ...
        new DeploymentsTool(promotionService, dashboardService, ladder, actors, releaseRef, Clock.systemUTC()),
        new DocumentsTool(...));
```
Rename test `fiveUniquelyNamedToolsFitThePhase11Budget` to reflect six.

### `InstallsToolTest.java` (NEW)

**Analog:** `T/application/assistant/tools/DeploymentsToolTest.java`: Mockito mocks of collaborators, `mock(ReleaseRef.class)` with `when(releaseRef.resolve("R")).thenReturn(id)`, `JsonMapper mapper = JsonMapper.builder().build()`, `mapper.valueToTree(tool.call(Map.of("release", "R")).result())`, assert on JSON nodes, and a `CANARY` string proving free-text fields never leak.

### `PortfolioDashboardServiceTest` / `ClientsBehindTest` / `DashboardServiceTest` MOD

**Analog:** `T/application/DashboardServiceTest.java`: mock the read port, fixed clock, helper builders for releases and promotions:
```java
private final DashboardReadRepository repository = mock(DashboardReadRepository.class);
private final DashboardService service = new DashboardService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
private DashboardRelease release(Instant createdAt, Instant frozenAt) { ... new DashboardRelease(UUID.randomUUID(), "R"+n, "1."+n, status, createdAt, frozenAt, 0) ... }
private void promote(DashboardRelease r, Stage stage, PromotionOutcome outcome, Instant deployedAt) { promotions.add(new Promotion(UUID.randomUUID(), r.id(), stage.id(), outcome, deployedAt, deployedAt, "acc", null, null, null)); }
```
`ClientsBehindTest` is pure (no Mockito): follow the RESEARCH unit-case list.

### `PortfolioDashboardPersistenceIT.java` (NEW) and `DashboardPersistenceIT` MOD

**Analog:** `T/adapters/persistence/DashboardPersistenceIT.java` lines 38-80:
```java
@SpringBootTest
@Testcontainers
class DashboardPersistenceIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);
    ...
    @AfterEach
    void clear() { jdbc.execute("TRUNCATE release CASCADE"); }
    private UUID release(String name, boolean freeze, List<ReleaseItem> items) {
        Release release = Release.create(name, "1.0", "CNV", "rm@acme.com", Instant.now().minus(1, ChronoUnit.HOURS));
        releaseRepository.create(release, items);
```
Check `ClientDeploymentPersistenceIT` and `PortfolioPersistenceIT` for product/client/install fixture helpers before writing new ones (RESEARCH Wave 0).

### `DashboardSpanContractTest` MOD

Extend it to read the max product-filter constant from `frontend/src/api/dashboard.ts` the same way it reads `MAX_HISTORY_SPAN_DAYS` (frontend line 8-9 carries the "Must equal DashboardController..." comment the test reads).

---

### Frontend: `F/src/api/dashboard.ts` MOD

**Analog:** itself.

**Search validator** (lines 106-115), extend with `tab`, `products`, `docs`, `archived`; already wired as `validateSearch: parseDashboardSearch` in `F/src/router.tsx:44-48`:
```ts
export function parseDashboardSearch(search: Record<string, unknown>): DashboardSearch {
  const { stage, from, to, all } = search;
  return {
    stage: typeof stage === 'string' && UUID.test(stage) ? stage : undefined,
    from: parseDay(from) ? (from as string) : undefined,
    to: parseDay(to) ? (to as string) : undefined,
    all: all === true || all === 'true' ? true : undefined,
  };
}
```
**Counting rule to share** (lines 149-159): extract per-release predicates used by both `paperworkCounts` and the `docs` matrix filter:
```ts
const states = DOCUMENT_TYPES.map((type) => stateOf(id, type) ?? 'NOT_GENERATED');
if (states.some((s) => s === 'NOT_GENERATED' || s === 'STALE')) needPaperwork += 1;
if (states.some((s) => s === 'STALE')) stale += 1;
```
**Paths and keys** (lines 161-172, 181-190): add `productIds` to `deploymentsPath` (URLSearchParams) and `DeploymentFilter`; keep `['dashboard', ...]` key prefix so `invalidateQueries({queryKey:['dashboard']})` covers them; include `[...ids].sort().join(',')`:
```ts
export const dashboardReleasesQueryKey = (all: boolean) => ['dashboard', 'releases', all ? 'all' : 'active'] as const;
export function useDashboardReleases(all: boolean) {
  return useQuery({
    queryKey: dashboardReleasesQueryKey(all),
    queryFn: () => apiFetch<DashboardData>(`/api/dashboard/releases?scope=${all ? 'all' : 'active'}`),
  });
}
```
Portfolio hooks (`usePortfolioSummary`, `usePortfolioBehind`, `usePortfolioMatrix`) use the same `useQuery` + `apiFetch` shape, one per section so failures are independent.

### `F/src/routes/DashboardRoute.tsx` MOD

**Analog:** itself plus `F/src/routes/PortfolioRoute.tsx` for the tab bar.

**Tab bar markup to copy** (PortfolioRoute lines 85-111): `role="tablist"`, per-tab `id`, `role="tab"`, `aria-selected`, `aria-controls`, roving `tabIndex`, arrow-key handler (`onTabKeyDown` lines 70-77), active classes `-mb-px border-b-2 border-blue-600 font-semibold text-gray-900`, inactive `text-gray-600 hover:text-gray-900`; panel `role="tabpanel"`.

**Navigation idiom** (DashboardRoute near the end):
```tsx
onToggleAll={(checked) =>
  navigate({ to: '/', search: (prev) => ({ ...prev, all: checked ? true : undefined }), replace: true })
}
```
Use the same for `tab`, `products`, `docs`, `archived`.

**Tile to make clickable** (lines 38-46, 71-72). Current:
```tsx
function Tile({ caption, children }) { return (<div className="rounded border border-gray-200 bg-white p-4"> ... </div>); }
<Tile caption="Need paperwork">{counts?.needPaperwork ?? '—'}</Tile>
<Tile caption="Stale">{counts?.stale ?? '—'}</Tile>
```
Replace with button tile (`aria-pressed`, `aria-disabled` when readiness failed, `?` toggle as sibling). Keep degradation: `counts` is already `undefined` when `readiness.isError` (route lines ~335-338); also ignore `docs` when `readiness.isError` (Pitfall 6).

**ReleaseMatrix empty states** (lines 256-279): condition `summary.totalReleases > 0 && releases.length === 0` needs the product-filter/`docs`-aware variants.

**Spinner / error block** (route lines 342-366): reuse `Spinner`, the `role="alert" data-reason={problem?.reason}` paragraph with `messageForReason(problem)` and the bordered "Try again" button for each portfolio section.

**Extract to `F/src/components/`:** `ProductFilter`, `PortfolioTab` sections (route file is 447 lines). `DeploymentHistory` already takes `search` and `onSearchChange`; add `productIds`.

### `PortfolioTab` components (NEW)

**Analog:** `ReleaseMatrix` table markup (constants `TH`/`TD` lines 17-18, `overflow-x-auto rounded border border-gray-200 bg-white`, `sticky left-0 bg-white` first column, `Link to="/releases/$releaseId" params={{ releaseId }}` with `font-semibold text-blue-600 hover:underline`). "Show archived" + sort pattern from `PortfolioRegisterPanel.tsx`:
```tsx
const visible = props.items
  .filter((item) => showArchived || !item.archived)
  .sort((a, b) => Number(a.archived) - Number(b.archived) || a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
```
(on the dashboard it is `archived` URL param instead of local state). Client link target `/portfolio?tab=clients&client={id}` per `PortfolioRoute` `go(next, client)` search shape. Product list for filter from existing `useProducts()` (`F/src/api/products.ts`).

### Mutation hooks: add `['dashboard']` invalidation (installs/clients/products/releases)

**Analog:** `F/src/api/installs.ts` lines 56-68:
```ts
onSuccess: (history) => {
  queryClient.setQueryData(installsQueryKey(releaseId), history);
  queryClient.invalidateQueries({ queryKey: ['client'] });
  queryClient.invalidateQueries({ queryKey: ['clients'] });
},
```
Add `queryClient.invalidateQueries({ queryKey: ['dashboard'] });` here, in `clients.ts` (~77, 113-114), `products.ts` (~25), `releases.ts` (~229-231).

### Frontend tests

**Analog:** `F/tests/components/DashboardRoute.test.tsx` lines 1-70: `vi.mock('@tanstack/react-router', ...)` with `Link` rendering `<a href>`, mutable `searchParams`, `useNavigate: () => navigate`; typed fixture builders `row(overrides)` and `data(releases, totalReleases)`; `createRoot` + `act` inside `QueryClientProvider`. Update strings "Need paperwork"/"Stale". **Analog for cache tests:** `F/tests/api/portfolio.test.ts` `runMutation(useHook, vars, response)` returning `{url, init, queryClient}`; assert `queryClient` invalidation of `['dashboard']`. **Analog for validator tests:** `F/tests/api/dashboard.test.ts`.

---

## Shared Patterns

### Read-only ports, bound parameters
**Source:** `J/ports/DashboardReadRepository.java` header, `DashboardJpaRepository.java` header.
**Apply to:** `PortfolioReadRepository`, `PortfolioJpaRepository`, all new SQL. No mutation method names; every value a bound parameter; no `IN ()` with an empty collection; `EXISTS`, never JOIN, on `release_product`.

### Validation errors as problem+JSON
**Source:** `DashboardController.invalid(...)` (lines 77-82), reason `VALIDATION_FAILED`.
**Apply to:** `productIds` cap and `includeArchived` parsing in both controllers; frontend maps via `messageForReason`.

### Single-home business rules
**Source:** `PromotionHistory.firstProductionSuccess()` (in-Production), `current_client_release` / `effective_client_deployment` views (V14), `ReleaseRef.resolve` (release names).
**Apply to:** `PortfolioDashboardService`, `InstallsTool`. Never restate in SQL or UI.

### Injected Clock
**Source:** `DashboardService` constructor `(DashboardReadRepository repository, Clock clock)`; tests use `Clock.fixed(NOW, ZoneOffset.UTC)`.
**Apply to:** `PortfolioDashboardService` (30-day window), `InstallsTool` if time is needed.

### Assistant tool error handling
**Source:** `DeploymentsTool.stageId(...)`, `ReleaseRef.resolve(...)`: throw `ToolArgumentException(message with candidates)`; `ToolOutput(result, sourceId, sourceTitle)` with `"installs:<id>"` / `"ReleaseManagement: ..."` style citation ids.
**Apply to:** `InstallsTool`.

### Frontend error and loading blocks
**Source:** `DashboardRoute.tsx` lines 342-366 (Spinner, `role="alert"` with `data-reason`, "Try again").
**Apply to:** each portfolio section.

### React text only (XSS)
**Source:** all existing cells render names as JSX text children.
**Apply to:** all new cells; never `dangerouslySetInnerHTML`.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| Multi-select `<details>` product filter (`ProductFilter.tsx`) | component | request-response | No multi-select popover exists; compose from native `<details>`, checkboxes, and the `PortfolioRegisterPanel` checkbox styling (`focus-visible:ring-2 focus-visible:ring-blue-600`). Follow UI-SPEC. |
| Inline "?" help toggle / clickable `Tile` button | component | n/a | No help-toggle exists in the codebase; build per UI-SPEC (sibling button, `aria-expanded`, `aria-controls`). |
| Pure behind computation (`releasesBehind`) | domain | transform | No ranking-by-frozen-date helper exists; write from RESEARCH "Behind computation" sketch; place in `J/domain/portfolio/`. |
| Array search params in `validateSearch` | api | n/a | No existing array param; RESEARCH assumption A1 (TanStack serialises arrays as JSON); validator must accept array or single string. Verify with a router test. |

## Metadata

**Analog search scope:** `src/main/java/com/evision/releasemgmt/{application,adapters,ports,domain}`, `src/test/java/.../{application,adapters}`, `frontend/src/{api,routes,components}`, `frontend/tests`, `rag-service/src/main/resources/application.yml`.
**Files read:** about 25 (DashboardService, DashboardController, ClientService, DeploymentsTool, ReleaseRef, DashboardJpaRepository, DashboardReadRepositoryAdapter, DashboardReadRepository, ClientDeploymentJpaRepository, ClientDeploymentRepository(+adapter head), CurrentRunRow, DashboardReleaseRow, Client, Product, ReleaseProductRepository, DashboardResponse, ClientDetailResponse, ToolOutput, AssistantTool, ToolDefinitionsTest, DeploymentsToolTest, DashboardServiceTest, DashboardPersistenceIT, dashboard.ts, DashboardRoute.tsx, PortfolioRoute.tsx, PortfolioRegisterPanel.tsx, installs.ts, DashboardRoute.test.tsx, portfolio.test.ts, V14 views).
**Not read (planner should open before editing):** `DashboardTool.java` line ~70, `ToolRegistry.java`, `ClientController.java`/`ProductController.java`, `clients.ts`, `products.ts`, `releases.ts` invalidation sites, `ClientDeploymentPersistenceIT`, `PortfolioPersistenceIT`, rag-service `ToolPropertiesTest`.
**Pattern extraction date:** 2026-10-22
