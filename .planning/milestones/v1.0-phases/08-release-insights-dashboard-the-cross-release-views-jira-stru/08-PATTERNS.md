# Phase 8: Release Insights Dashboard - Pattern Map

**Mapped:** 2026-10-12
**Files analyzed:** 27 new/modified (backend 15, frontend 8, tests 4 groups counted individually below)
**Analogs found:** 26 / 27 (1 partial: the shared churn SQL has no existing aggregate-over-children analog)

Scope notes: no migration, no new dependency (08-RESEARCH). All paths below are relative to the repo root; `J` = `src/main/java/com/evision/releasemgmt`, `T` = `src/test/java/com/evision/releasemgmt`, `F` = `frontend/src`.

## Shared-file warning (concurrent quick task on a separate branch)

The quick task edits `TicketBrowser.tsx`, `api/tickets.ts`, `TicketController`, `TicketSearchService`, `EnrichmentService`, `CodeLinksResponse` and `GlobalExceptionHandler`. Phase 8 overlaps on two of them; use a content-anchored edit (match on a unique surrounding string, not line numbers) and re-read the file immediately before editing:

| Phase 8 need | Shared file | Mitigation |
|--------------|-------------|------------|
| New 400 `VALIDATION_FAILED` mapping for bad dashboard params (from >= to, span > 366 d, limit/offset bounds) | `J/adapters/web/GlobalExceptionHandler.java` | Prefer to add NO new handler: throw a domain/application exception that already maps to `VALIDATION_FAILED`, or use `@Validated` bounds so `handleMethodArgumentNotValid` / `handleTypeMismatch` (lines 289-293, 324-337) catch them. If a handler is unavoidable, insert it directly after `handleTypeMismatch`, anchored on that method's `"VALIDATION_FAILED");` closing line. |
| Per-ticket churn figures on the release page (D-08-07) | `J/adapters/web/dto/CodeLinksResponse.java` (and its mapper `toCodeLinksResponse`, `ReleaseController.java:170-185`) | Do NOT add fields to `CodeLinksResponse`. Add a new `ItemChurnResponse churn` component to `ReleaseItemResponse` (not in the quick task's list) and populate it in `ReleaseController.toItemResponse` (`:162-168`). Same split on the frontend: add `churn` to `ReleaseItem` in `F/api/releases.ts`, leave the `CodeLinks` type alone. |
| (check, no edit planned) `EnrichmentService` | - | Phase 8 reads enrichment columns only; no edit. Touching it would be a scope error. |

`F/api/releases.ts`, `F/api/ladder.ts`, `F/api/promotions.ts`, `F/api/documents.ts` are edited by Phase 8 for query invalidation and the Map index; none are on the quick-task list, but `releases.ts` hosts the `CodeLinks` type the quick task may also extend: re-read before editing.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `J/domain/promotion/PromotionHistory.java` (+`firstProductionSuccess`, `latestEffective`) | domain model | transform | same file (`currentStage`, `standingSuccess`) | exact |
| `J/domain/dashboard/*` (release metrics records, median/outlier helpers) | domain utility | transform | `domain/promotion/Stage.java` (pure record) | role-match |
| `J/ports/DashboardReadRepository.java` | port | CRUD (read-only) | `ports/PromotionRepository.java` | exact |
| `J/application/DashboardService.java` | service | batch/transform | `application/DocumentReadinessService.java` (`all()`) | exact |
| `J/adapters/persistence/DashboardReadRepositoryAdapter.java` | persistence adapter | CRUD (read-only) | `adapters/persistence/PromotionRepositoryAdapter.java` | exact |
| `J/adapters/persistence/repository/DashboardJpaRepository.java` (native queries) + projection rows | repository | request-response | `repository/ReleaseJpaRepository.java` + `ReleaseSummaryRow.java` | exact |
| `J/adapters/web/DashboardController.java` + dto records | controller | request-response | `adapters/web/DocumentReadinessController.java`, `PromotionController.java` | exact |
| `J/adapters/web/dto/ItemChurnResponse.java`, `ReleaseItemResponse.java` (modify), `ReleaseController.java` (modify) | dto/controller | request-response | `dto/ReleaseItemResponse.java`, `ReleaseController.toItemResponse` | exact |
| `J/adapters/persistence/ReleaseRepositoryAdapter.java` (modify `removeItem`, `removeManualItem`) | persistence adapter | CRUD | same file `addItems` (`:99-117`) | exact |
| `J/adapters/persistence/repository/ReleaseTicketItemJpaRepository.java` (modify delete return type) | repository | CRUD | same file | exact |
| `F/routes/DashboardRoute.tsx` (replace in place) | route component | request-response | `F/routes/ReleasesRoute.tsx` | exact |
| `F/router.tsx` (modify `dashboardRoute`: add `validateSearch` only) | config | request-response | `releaseDetailRoute` `validateSearch` (`:64-75`) | exact |
| `F/api/dashboard.ts` | api hooks | request-response | `F/api/promotions.ts`, `F/api/documents.ts` | exact |
| `F/api/documents.ts` (modify: `readinessIndex` Map) | utility | transform | same file `readinessFor` (`:52-60`) | exact |
| `F/routes/ReleasesRoute.tsx` (modify: use Map index) | route component | request-response | itself | exact |
| `F/components/LadderDots.tsx` (extract, optional) | component | transform | `F/components/PromotionSection.tsx:44-68` | exact |
| `F/components/PromotionSection.tsx` (modify only if LadderDots extracted) | component | request-response | itself | exact |
| `F/api/promotions.ts`, `F/api/ladder.ts`, `F/api/releases.ts`, `F/api/documents.ts` (add `['dashboard']` invalidation) | api hooks | event-driven (cache) | `ladder.ts:25-32` | exact |
| `F/routes/ReleaseDetailRoute.tsx` / `F/components/CodeLinks.tsx` (per-ticket churn display) | component | request-response | `CodeLinksChip` (`CodeLinks.tsx:28-60`) | role-match |
| `T/domain/promotion/PromotionHistoryTest.java` (extend) | test | transform | same file | exact |
| `T/application/DashboardServiceTest.java` | test | transform | `T/application/DocumentReadinessServiceTest.java`, `PromotionServiceTest.java` | role-match |
| `T/adapters/persistence/DashboardPersistenceIT.java` | IT | CRUD | `T/adapters/persistence/PromotionPersistenceIT.java` | exact |
| `T/adapters/persistence/ReleasePersistenceIT.java` (extend: no-op remove) | IT | CRUD | same file | exact |
| `T/adapters/web/DashboardControllerTest.java` | web test | request-response | `T/adapters/web/PromotionControllerTest.java` | exact |
| `T/ArchitectureBoundaryTest.java` (verify only; maybe add read-port rule) | arch test | - | same file | exact |
| `frontend/tests/api/documents.test.ts` (extend), `frontend/tests/api/dashboard.test.ts` (new) | test | transform | `frontend/tests/api/documents.test.ts` | exact |
| shared churn SQL (Q4: per-item commits-before/after `first_done_at`) | native query | aggregate | `ReleaseJpaRepository.search` (scalar subquery projection only) | partial |
| `.planning/REQUIREMENTS.md` DOC-09 marker | docs | - | n/a | n/a |

## Pattern Assignments

### `J/domain/promotion/PromotionHistory.java` (domain, transform) - add two pure methods

**Analog:** itself. New methods go beside `currentStage()` and reuse `isEffective`, `LEDGER_ORDER`, `Stage::production`.

**Existing rules to reuse, not restate** (`PromotionHistory.java:33-39`, `:126-134`):
```java
public boolean isEffective(Promotion promotion) {
    return supersededBy(promotion.id()).isEmpty();
}
public Optional<Stage> currentStage() {
    return stages.reversed().stream().filter(s -> hasStandingSuccess(s.id())).findFirst();
}
// D-07-11/D-07-15: superseded and VOIDED entries count toward nothing; D-07-08: FAILED is ignored.
private Optional<Promotion> standingSuccess(UUID stageId) {
    return entries.stream()
            .filter(this::isEffective)
            .filter(e -> e.stageId().equals(stageId))
            .filter(e -> e.outcome() == PromotionOutcome.SUCCESS || e.outcome() == PromotionOutcome.ROLLED_BACK)
            .max(LEDGER_ORDER)
            .filter(e -> e.outcome() == PromotionOutcome.SUCCESS);
}
```
**Add** (from 08-RESEARCH Pattern 1; imports `Comparator`, `Optional`, `Instant` already present):
```java
public Optional<Instant> firstProductionSuccess() {
    return stages.stream().filter(Stage::production).findFirst()
            .flatMap(prod -> entries.stream()
                    .filter(this::isEffective)
                    .filter(e -> e.stageId().equals(prod.id()) && e.outcome() == PromotionOutcome.SUCCESS)
                    .map(Promotion::deployedAt).min(Comparator.naturalOrder()));
}
public Optional<Promotion> latestEffective() {
    return entries.stream().filter(this::isEffective)
            .filter(e -> e.outcome() != PromotionOutcome.VOIDED).max(LEDGER_ORDER);
}
```
Read-only construction works: `new PromotionHistory(releaseId, status == FROZEN, frozenAt, stages, entries)` (as in `PromotionRepositoryAdapter.history`, `:55-64`); `frozen`/`frozenAt` only matter for `record`/`correct`. Do not call `currentStage()` on a history whose `stages` is empty-guarded elsewhere; `stages` is sorted by position in the compact constructor (`:21-24`).

**D-08-15 / D-08-02 helper:** "last promotion failed" = `latestEffective().filter(p -> p.outcome() == FAILED)`; the stage it targeted is `p.stageId()`.

---

### `J/ports/DashboardReadRepository.java` (port, read-only)

**Analog:** `J/ports/PromotionRepository.java` (whole file). Copy the header-comment discipline and keep names free of `update|delete|remove|save` (ArchUnit).
```java
// D-07-11: append-only by construction. There is deliberately no update, delete, remove or save here ...
public interface PromotionRepository {
    PromotionHistory load(UUID releaseId);
    Promotion appendUnderLock(UUID releaseId, Function<PromotionHistory, Promotion> decide);
}
```
New port should expose only reads, returning domain records (not JPA/Spring types): all releases with manual count, all stages, all promotions, per-item churn rows for a set of release ids, and the filtered/paged deployment query. Port signature uses plain `Instant`/`UUID`/`int` (no `Pageable`; limit+1 paging, 08-RESEARCH Pattern 4).

---

### `J/adapters/persistence/DashboardReadRepositoryAdapter.java` (adapter, read-only)

**Analog:** `J/adapters/persistence/PromotionRepositoryAdapter.java`.

**Imports and wiring pattern** (lines 1-34):
```java
@Component
public class PromotionRepositoryAdapter implements PromotionRepository {
    private final PromotionJpaRepository jpa;
    private final EnvironmentStageJpaRepository stageJpa;
    private final ReleaseJpaRepository releaseJpa;
    public PromotionRepositoryAdapter(PromotionJpaRepository jpa, EnvironmentStageJpaRepository stageJpa,
                                      ReleaseJpaRepository releaseJpa) { ... }
```
**Read transaction + entity-to-domain mappers** (`:36-40`, `:66-73`): `@Transactional(readOnly = true)`; reuse `toStage` / `toPromotion` (make them package-private/static-shared rather than copying; the first-use is `findAllByOrderByPositionAsc()` for stages, `jpa.findAll()` for the full ledger).
```java
private static Stage toStage(EnvironmentStageEntity e) {
    return new Stage(e.getId(), e.getName(), e.getPosition(), e.isProduction(), e.getRetiredAt());
}
private static Promotion toPromotion(PromotionEntity e) {
    return new Promotion(e.getId(), e.getReleaseId(), e.getStageId(), e.getOutcome(), e.getDeployedAt(),
            e.getRecordedAt(), e.getRecordedBy(), e.getNote(), e.getSupersedesId(), e.getCorrectionReason());
}
```
Group the full ledger by `releaseId` in Java and build one `PromotionHistory` per release (08-RESEARCH summary). Add `findAll()`-style derived method (or `findAllByOrderByRecordedAtAsc`) to `PromotionJpaRepository` (currently only `findByReleaseIdOrderByRecordedAtAsc` and `findReferencedStageIds`, `PromotionJpaRepository.java:12-16`).

---

### `J/adapters/persistence/repository/DashboardJpaRepository.java` + projection rows (native queries)

**Analog:** `J/adapters/persistence/repository/ReleaseJpaRepository.java:16-41` and `ReleaseSummaryRow.java`.

**Native query + interface projection pattern** (aliases camelCase, bound params only, scalar-subquery count):
```java
@Query(value = """
        SELECT r.id AS id, r.name AS name, ...
               (SELECT count(*) FROM release_ticket_item t WHERE t.release_id = r.id)
             + (SELECT count(*) FROM release_manual_item m WHERE m.release_id = r.id)
                                   AS itemCount
          FROM release r
         WHERE :query = '' OR r.name ILIKE '%' || :query || '%' ...
         ORDER BY r.created_at DESC
        """, nativeQuery = true)
List<ReleaseSummaryRow> search(@Param("query") String query);
```
```java
public interface ReleaseSummaryRow { UUID getId(); String getName(); Instant getCreatedAt(); long getItemCount(); ... }
```
Copy: text-block SQL, `AS camelCase` aliases matching getters, `nativeQuery = true`, getter-interface projection, `long` for counts. For Q1 select `r.id, name, version, status, created_at, frozen_at, items_changed_at, last_refreshed_at` plus `(SELECT count(*) FROM release_manual_item m WHERE m.release_id = r.id) AS manualCount`.

**Q4 per-item churn query and Q5 deployments query:** verbatim SQL in 08-RESEARCH Pattern 3 and Pattern 4. Mandatory details: `CAST(:stageId AS uuid) IS NULL OR p.stage_id = :stageId` (Pitfall 3), `ORDER BY p.deployed_at DESC, p.recorded_at DESC, p.id DESC` (Pitfall 4), `IN (:releaseIds)` for Q4, `count(c.id) FILTER (WHERE ...)` with `<=` / `>` partition (Pitfall 6). Projection getters for nullable columns (`getFirstDoneAt()`, `getReopenCount()` as `Integer`).

---

### `J/application/DashboardService.java` (service, batch/transform)

**Analog:** `J/application/DocumentReadinessService.java` (class header comment + `all()` single-pass grouping with record keys).

**Class shape and grouping pattern** (`:19-34`, `:60-83`):
```java
@Service
public class DocumentReadinessService {
    ...
    public List<ReadinessEntry> all() {
        List<ReleaseSummary> releases = releaseRepository.search("");
        Map<SnapshotKey, Instant> latestGeneratedAt = new HashMap<>();
        for (var snapshot : snapshotRepository.findLatestForAllReleases()) {
            latestGeneratedAt.put(new SnapshotKey(snapshot.releaseId(), snapshot.documentType()), snapshot.generatedAt());
        }
        ...
    }
    public record ReadinessEntry(...) {}
    private record SnapshotKey(UUID releaseId, TemplateDocumentType documentType) {}
}
```
Copy: constructor injection of ports only (no adapters), private nested records as map keys, one bulk load then in-memory join. Inject `java.time.Clock` as `PromotionService` does (`PromotionService.java:12-19`, `Instant now = clock.instant();`) so the 90-day window is testable.

**Do not call** `DocumentReadinessService.state(...)` (readiness comes from the existing endpoint).

Policy implemented here (not in SQL): visible set (D-08-11, D-08-15: visible if `shippedAt` empty OR within 90 d OR `currentStage` position < Production position), medians (08-RESEARCH "Code Examples"), eligibility (AVAILABLE + `first_done_at` not null + `commit_total >= 1` + not incomplete, D-08-17), Tukey fence outliers (D-08-18, >= 4 eligible), per-stage counts, validation of `from < to` and 366-day cap (a constant shared with the UI-SPEC; define once as `MAX_HISTORY_SPAN`).

---

### `J/adapters/web/DashboardController.java` + dto records (controller, request-response)

**Analogs:** `J/adapters/web/DocumentReadinessController.java` (cross-release GET) and `PromotionController.java` (actor resolution, mapping).

**Controller skeleton** (`DocumentReadinessController.java:13-30`):
```java
@RestController
@RequestMapping("/api/documents")
public class DocumentReadinessController {
    private final DocumentReadinessService service;
    public DocumentReadinessController(DocumentReadinessService service) { this.service = service; }
    @GetMapping("/readiness")
    public ReadinessResponse readiness() {
        var entries = service.all().stream().map(entry -> new ReadinessEntryResponse(...)).toList();
        return new ReadinessResponse(entries);
    }
}
```
Use `@RequestMapping("/api/dashboard")`, `@GetMapping("/releases")`, `@GetMapping("/deployments")`. GET only, so no CSRF/`@Valid @RequestBody`. Typed params (`UUID stageId`, `Instant from`, `Instant to`, `@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit`, `@Min(0) int offset`); annotate the controller `@Validated` for param bounds. A bad UUID already maps to `VALIDATION_FAILED` through `handleTypeMismatch`.

**Actor resolution once per response** (`PromotionController.java:70-75`):
```java
ResolvedActors actors = actorResolution.resolve(
        history.entries().stream().map(Promotion::recordedBy).toArray(String[]::new));
...
actors.display(e.recordedBy())
```
Do the same with the page's distinct `recordedBy`. The web package must not import `JiraCredentialRepository` (ArchUnit, `ArchitectureBoundaryTest`).

**Response mapping:** static `toResponse` helpers in the controller as `PromotionController.toResponse` (`:77-91`); response DTOs as records in `adapters/web/dto/` (e.g. `PromotionEntryResponse`, `StageResponse`). Durations on the wire as seconds (A6); the UI-SPEC "Planning input" list (UI-SPEC lines 304-306) is the field checklist: stage position data, last-failed flag plus targeted stage id, `shippedAt`, `frozenAt`, `createdAt`, ticket/manual counts, linked/noLink/failed/noData counts, churn medians, outlier count, reopens total, post-Done share, per-metric no-data counts.

---

### `J/adapters/persistence/ReleaseRepositoryAdapter.java` + `ReleaseTicketItemJpaRepository.java` (folded todo)

**Analog:** `ReleaseRepositoryAdapter.addItems` (`:99-117`), the WR-01 fix.
```java
Set<String> present = Set.copyOf(findTicketKeys(releaseId));
List<ReleaseItem> toInsert = items.stream().filter(i -> !present.contains(i.ticketKey())).toList();
// ... must not stamp items_changed_at, or it would spuriously flip an up-to-date document to stale
if (!toInsert.isEmpty()) {
    insertItems(releaseId, toInsert);
    stampItemsChanged(entity);
    jpa.save(entity);
}
return load(releaseId);
```
**Current defects to change** (anchor edits on these exact bodies, not on line numbers; the todo cites `:99-104`/`:173-179`, the file now has them at `:119-127` and `:280-288`):
```java
public Release removeItem(UUID releaseId, String ticketKey) {
    ReleaseEntity entity = lockOpen(releaseId);
    itemJpa.deleteByReleaseIdAndTicketKey(releaseId, ticketKey);
    stampItemsChanged(entity);   // unconditional
    jpa.save(entity);
    return load(releaseId);
}
public Release removeManualItem(UUID releaseId, UUID manualItemId) {
    ReleaseEntity release = lockOpen(releaseId);
    manualJpa.findByReleaseIdAndId(releaseId, manualItemId).ifPresent(manualJpa::delete);
    stampItemsChanged(release);  // unconditional
    jpa.save(release);
    return load(releaseId);
}
```
Fix: `ReleaseTicketItemJpaRepository.deleteByReleaseIdAndTicketKey` (`:18`, currently `void`) returns `long`; stamp only when `> 0`. In `removeManualItem`, stamp only when `findByReleaseIdAndId(...)` was present (`Optional.isPresent()` -> delete + stamp inside).

---

### `J/adapters/web/ReleaseController.java` + `ReleaseItemResponse` (per-ticket churn, D-08-07)

**Analog:** `ReleaseController.toItemResponse` (`:162-168`) and `ReleaseItemResponse` (record ending `CodeLinksResponse codeLinks`). Add a trailing `ItemChurnResponse churn` component and fill it from the same Q4 query called with a single release id (research: boundary rule defined once in SQL). See shared-file warning: do not touch `CodeLinksResponse`.

---

### `F/routes/DashboardRoute.tsx` (route component) - replace in place

**Analog:** `F/routes/ReleasesRoute.tsx`. Current placeholder is 15 lines (`Link` + heading); keep the export name `DashboardRoute` so `router.tsx:43` is unchanged.

**Imports pattern** (`ReleasesRoute.tsx:1-8`):
```tsx
import { Link } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/auth';
import { readinessFor, useDocumentReadiness } from '../api/documents';
import { DOCUMENT_TYPES, DOCUMENT_TYPE_LABELS } from '../api/templates';
import { ReadinessPill } from '../components/ReadinessPill';
```
**Per-section loading / error / empty branches** (`:44-89`): spinner `h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600`; `problem = query.isError && query.error instanceof ApiError ? query.error.problem : undefined`; error `<p role="alert" data-reason={problem.reason} className="text-sm text-red-600">{messageForReason(problem)}</p>` plus gray-bordered "Try again" calling `refetch()`; empty-state block `rounded bg-gray-50 p-6 text-center` with "Browse tickets" link (copy for the "No releases yet" variant verbatim from `:73-80`, as UI-SPEC requires).

**Table classes** (`:92-133`): wrapper `overflow-x-auto rounded border border-gray-200 bg-white`, `min-w-full text-sm`, `thead border-b border-gray-200`, `th scope="col" px-4 py-2 text-left font-semibold text-gray-700`, `tr border-b border-gray-100 hover:bg-gray-50`, release link `font-semibold text-blue-600 hover:underline` via `<Link to="/releases/$releaseId" params={{ releaseId }}>`.

**Readiness cell (isolated failure, T-05-60)** (`:146-158`):
```tsx
{readiness.isError ? (
  <span className="text-gray-400">—</span>
) : (
  <ReadinessPill state={readinessFor(readiness.data?.entries, r.id, type)?.state ?? 'NOT_GENERATED'} releaseId={r.id} documentType={type} />
)}
```
Replace the lookup with the Map index (below). Note the existing fallback `?? 'NOT_GENERATED'` for a missing entry; keep it consistent and never map `NO_TEMPLATE` to `NOT_GENERATED`.

**Component reuse:** `ReadinessPill` untouched (`F/components/ReadinessPill.tsx`, props `state`, `releaseId`, `documentType`); `CodeLinks` chip colours from `F/components/CodeLinks.tsx:28-33`:
```tsx
const CHIP_COLOURS: Record<CodeLinks['status'], string> = {
  LINKED: 'bg-blue-50 text-blue-800', NO_LINK: 'bg-gray-100 text-gray-600',
  FAILED: 'bg-red-50 text-red-700', NOT_ATTEMPTED: 'bg-gray-50 text-gray-500',
};
```
`CHIP_COLOURS` is module-private: export it (single-line `export` change; `CodeLinks.tsx` is not on the quick task's list but the quick task touches the `CodeLinks` type, so re-read first) or restate only the four inline-bar fills from UI-SPEC lines 122-127.

**Outcome colours and labels:** `OUTCOME_COLOURS` is a private const in `PromotionSection.tsx:7-12`; `OUTCOME_LABELS` is exported from `F/api/promotions.ts:34-39`. The history table needs `OUTCOME_COLOURS` - move it to `api/promotions.ts` (export) and import it in both places, rather than duplicating.

---

### `F/components/LadderDots.tsx` (optional extraction)

**Analog:** `F/components/PromotionSection.tsx:44-68` (dot markup, verbatim):
```tsx
<ol aria-label="Environment ladder" className="mt-3 flex flex-wrap items-center gap-3">
  {drawn.map((stage) => {
    const reached = current !== undefined && stage.position <= current.position;
    const isCurrent = stage.id === history.currentStageId;
    return (
      <li key={stage.id} data-state={reached ? 'reached' : 'ahead'} aria-current={isCurrent ? 'step' : undefined} className="flex items-center gap-1">
        <span className={`inline-block h-3 w-3 rounded-full ${reached ? 'bg-blue-600' : 'bg-gray-300'} ${isCurrent ? 'ring-2 ring-blue-300' : ''}`} />
        <span className="text-xs text-gray-700">{stage.name}{stage.retired ? ' (retired)' : ''}</span>
        {stage.production && <span className="text-xs text-gray-500">Production</span>}
```
`drawn` rule (`:23`): `stages.filter((s) => !s.retired || s.id === currentStageId)`. For the matrix the component takes `compact`, `ariaLabel` (`Environment ladder for {release name}`), and an optional `failedStageId` rendering the hollow dot `border-2 border-red-600 bg-white` (UI-SPEC lines 132-135). If extracted, `PromotionSection.tsx` must be switched to it with a behaviour-neutral diff (existing tests/markup for `data-state` and `aria-current` must keep passing). UI-SPEC lists the extraction as the planner's call; skipping it and writing the dots inline in the route duplicates ~20 lines.

---

### `F/router.tsx` (modify `dashboardRoute`)

**Analog:** `releaseDetailRoute` (`router.tsx:64-75`) and `documentViewerRoute` (`:91-98`).
```tsx
const releaseDetailRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/releases/$releaseId',
  validateSearch: (search: Record<string, unknown>): { added?: number; ... } => ({
    added: typeof search.added === 'number' ? search.added : undefined,
  }),
  component: ReleaseDetailRoute,
});
```
Apply to `dashboardRoute` (`:40-44`), adding only `validateSearch` (08-RESEARCH "Typed search params"): `stage`, `from`, `to` strings, `all` boolean (accept `true`/`'true'`). Per UI-SPEC malformed values drop to defaults; `from`/`to` should additionally be dropped unless they match `^\d{4}-\d{2}-\d{2}$`. No path, nav or tree change (D-08-10). The route component reads params with `dashboardRoute.useSearch()` or `useSearch({ from: '/' })` and writes with `useNavigate()` `navigate({ search, replace: true })`.

---

### `F/api/dashboard.ts` (api hooks)

**Analogs:** `F/api/promotions.ts:1-62` and `F/api/documents.ts:39-50`.

**Types + key factory + query hook pattern** (`promotions.ts`):
```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client';
export const promotionsQueryKey = (releaseId: string) => ['promotions', releaseId] as const;
export function usePromotions(releaseId: string) {
  return useQuery({
    queryKey: promotionsQueryKey(releaseId),
    queryFn: () => apiFetch<PromotionHistory>(`/api/releases/${releaseId}/promotions`),
    retry: false,
  });
}
```
Use key prefix `['dashboard', 'releases', scope]` and `['dashboard', 'deployments', filter]` so one `invalidateQueries({ queryKey: ['dashboard'] })` clears both. The history hook is `useInfiniteQuery` (pages of 50, `offset` = rows loaded, `hasMore`) with `placeholderData: keepPreviousData`; drop an already-shown row id on append (UI-SPEC lines 245-251). Query-string building: encode with `URLSearchParams` (cf. `releaseSearchPath`, `releases.ts:105-107`, which uses `encodeURIComponent`). `queryClient` default is `retry: false` (`F/api/queryClient.ts`), so no per-hook `retry` override is needed; `promotions.ts` sets it explicitly anyway.

---

### `F/api/documents.ts` (Map index) + `ReleasesRoute` call site

**Analog:** `readinessFor` (`documents.ts:52-60`), currently a linear `Array.find` under a comment claiming O(1):
```ts
export function readinessFor(entries, releaseId, documentType) {
  return entries?.find((e) => e.releaseId === releaseId && e.documentType === documentType);
}
```
Replace with the Map version from 08-RESEARCH ("Map-indexed readiness"), keyed `${releaseId}:${documentType}`; build once per render with `useMemo` in each caller, and update `ReleasesRoute.tsx:152` plus every other `readinessFor` caller (grep `readinessFor(` across `F/`, including `DocumentsSection`). Remove or rewrite the now-wrong "O(1)" comment. Tests: extend `frontend/tests/api/documents.test.ts`.

---

### Cache invalidation (`['dashboard']`)

**Analog:** `F/api/ladder.ts:25-32` (`onSuccess` invalidation with comment) and `releases.ts:159,174,194,272` (`invalidateQueries({ queryKey: ['releases'] })` inside `useReleaseMutation`).
```ts
onSuccess: (ladder) => {
  queryClient.setQueryData(ladderQueryKey, ladder);
  return queryClient.invalidateQueries({ queryKey: ['promotions'] });
},
```
Add `queryClient.invalidateQueries({ queryKey: ['dashboard'] })` to: `useRecordPromotion`, `useCorrectPromotion` (`promotions.ts:56-79`, currently only `setQueryData`), `useLadderMutation` (`ladder.ts`), `useReleaseMutation` (single edit covers refresh/freeze/enrichment/remove at `releases.ts:~189-194`), `useCreateRelease`/`useAddTicketsToRelease` (`:159`, `:174`), `useGenerateDocument` (`documents.ts:98`; readiness affects the strip). Re-read `releases.ts` before editing (quick-task overlap risk).

---

### Per-ticket churn display on the release page

**Analog:** `CodeLinksChip` (`F/components/CodeLinks.tsx:42-60`; `plural()` helper at `:26-28`) and its call site `ReleaseDetailRoute.tsx:443`. Add a small sibling component (new file `F/components/ItemChurn.tsx`, preferred to avoid editing `CodeLinks.tsx`) showing commits-to-done, reopens, post-Done commits, "No data" when `churn` is null/ineligible (never 0), the key-tagged label (D-06-09) and the amber outlier flag.

---

### Tests

**`T/adapters/persistence/DashboardPersistenceIT.java`** - analog `T/adapters/persistence/PromotionPersistenceIT.java:30-70`:
```java
@SpringBootTest
@Testcontainers
class PromotionPersistenceIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");
    @Autowired private PromotionRepository promotions;
    @Autowired private ReleaseRepository releaseRepository;
    @Autowired private JdbcTemplate jdbc;
    @AfterEach void clear() { jdbc.execute("TRUNCATE release CASCADE"); }
    private UUID release(String name, boolean freeze) {
        Release release = Release.create(name, "1.0", "CNV", "rm@acme.com", Instant.now().minus(1, ChronoUnit.HOURS));
        releaseRepository.create(release, List.of());
        if (freeze) releaseRepository.freeze(release.id(), "rm@acme.com", Instant.now().minus(30, ChronoUnit.MINUTES));
        return release.id();
    }
```
Seed enrichment columns/commits via `JdbcTemplate` inserts (no domain path writes `authored_at` arbitrarily); seed corrected, voided and rolled-back promotions through `PromotionService.record/correct`. Include the equivalence test (history endpoint rows == `PromotionHistory` effective, non-VOIDED entries), null-stage path, tie-break paging, and a fixed statement count for the matrix load.

**`T/adapters/web/DashboardControllerTest.java`** - analog `T/adapters/web/PromotionControllerTest.java:49-62`:
```java
@WebMvcTest(PromotionController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class, ProblemDetailAuthenticationEntryPoint.class})
class PromotionControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private PromotionService service;
```
Use `jwt()` post-processor (GET, so no `csrf()`), `@MockitoBean DashboardService` and `ActorResolutionService`; assert 400 + `$.reason == "VALIDATION_FAILED"` for bad uuid, `from >= to`, over-span, `limit` out of range.

**`T/application/DashboardServiceTest.java`** - analogs `DocumentReadinessServiceTest`, `PromotionServiceTest` (plain Mockito/fixed `Clock`, no Spring). Cover: 90-day window and `all`, D-08-15 rolled-back stays visible, medians (even/odd/empty), eligibility and no-data rules, Tukey fence needs >= 4, reopens counted without commits.

**`T/domain/promotion/PromotionHistoryTest.java` (extend)** - existing fixtures to reuse (`:22-50`): `dev/test/uat/prod` stages, `entry(stage, outcome, day)`, `retired(...)`, `day(int)`, static imports of `SUCCESS/FAILED/ROLLED_BACK/VOIDED`.

**`frontend/tests/api/dashboard.test.ts`, `documents.test.ts`:** analog `frontend/tests/api/documents.test.ts` (Vitest). Test `readinessIndex` lookup and the search-param parsing/defaults.

## Shared Patterns

### Read-only domain rules live in `PromotionHistory`
**Source:** `J/domain/promotion/PromotionHistory.java:33-39,126-134`
**Apply to:** `DashboardService`, any code that needs stage/shipped/last-failed. The only SQL restatement of "effective" is the deployments query (`NOT EXISTS (... c.supersedes_id = p.id)` + `outcome <> 'VOIDED'`), guarded by the equivalence IT.

### Layering (ArchUnit)
**Source:** `T/ArchitectureBoundaryTest.java:19-55`
**Apply to:** all new Java. Domain and ports import no `org.springframework`, `jakarta`, `org.hibernate`, Jackson; application imports nothing from `adapters.persistence|web|jira|render`; web adapter must not import `ports.JiraCredentialRepository`. Port method names must not start with `update|delete|remove|save` (per 08-RESEARCH; confirm in the remaining test body before writing the port).

### Error taxonomy
**Source:** `J/adapters/web/GlobalExceptionHandler.java:289-293, 324-337, 363-369`
**Apply to:** `DashboardController`. Machine-readable `reason` via the private `problem(status, title, detail, reason)`; param errors reuse `VALIDATION_FAILED`. Frontend turns `reason` into copy with `messageForReason(problem)` (`F/api/auth`) and renders `role="alert" data-reason`.
```java
@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return problem(HttpStatus.BAD_REQUEST, "Malformed path parameter", "...", "VALIDATION_FAILED");
}
```

### Authentication
**Source:** `SecurityConfig` (`/api/**` is `authenticated()`; see `PromotionController.java:52-53`)
**Apply to:** `DashboardController`. No annotations needed; GET-only so the SameSite=Strict CSRF decision is not engaged.

### Actor display
**Source:** `J/application/ActorResolutionService.java` (`resolve(String...)`), `ResolvedActors.display`
**Apply to:** the deployments endpoint, once per response.

### Set-based queries, no N+1
**Source:** `ReleaseJpaRepository.search` scalar subqueries; `DocumentReadinessService.all()` one-pass maps
**Apply to:** Q1-Q5; never add columns to `ReleaseSummary`/`search` (slows `/releases` and `DocumentReadinessService.all()`).

### Frontend isolation of failures
**Source:** `ReleasesRoute.tsx:23-25, 148-150`
**Apply to:** matrix, readiness and history each get their own `isError` branch.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| Q4 per-item churn aggregate (`count(...) FILTER (WHERE authored_at <= first_done_at)` grouped per item) | native query | aggregate | Existing native query only uses scalar count subqueries; use the SQL in 08-RESEARCH Pattern 3 and the V9 index `release_item_commit_item_authored_idx (item_id, authored_at)`. |
| `useInfiniteQuery` + `keepPreviousData` history list | api hook | paged request-response | No infinite/paged query exists in `F/api`; follow TanStack Query v5 docs. |
| Inline-bar / stage-dot-with-failed-marker rendering | component | transform | Only the plain dot markup exists (`PromotionSection`); bar spec is in UI-SPEC lines 119-130. |
| Median / Tukey-fence helper | domain utility | transform | No statistics code exists; implement the 15-line median in 08-RESEARCH "Code Examples" and a quartile helper in `domain/dashboard`. |

## Metadata

**Analog search scope:** `src/main/java/com/evision/releasemgmt` (domain, ports, application, adapters/persistence, adapters/web), `src/test/java/com/evision/releasemgmt`, `frontend/src` (api, routes, components, router), `frontend/tests`.
**Files scanned/read:** ~30 read in full or by range.
**Pattern extraction date:** 2026-10-12
