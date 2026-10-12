# Phase 7: Promotion Tracking & Audit Trail - Pattern Map

**Mapped:** 2026-10-12
**Files analyzed:** 41 (new and modified, backend + frontend + tests)
**Analogs found:** 39 / 41 (no analog: the pure rule class `PromotionHistory`, the append-only trigger migration)

Paths are relative to `D:\IdeaProjects\ReleaseManagement`. `B` = `src/main/java/com/evision/releasemgmt`,
`T` = `src/test/java/com/evision/releasemgmt`, `F` = `frontend`.

Migration numbering: latest on disk is `V7__template_user_default.sql`. Phase 6 (planned, unexecuted) claims `V8` and `V9`. Phase 7 takes
the next free number (expected `V10`). Run `ls src/main/resources/db/migration` at each plan start; never hard-code.

Codebase facts that differ from RESEARCH.md assumptions (verified this session):
- `F/src/components/DocumentsSection.tsx:36` ALREADY has `<section id="documents" ...>`. No edit to that file is needed for the AUDIT-02 anchor.
- `B/application/*` has NO `@Transactional` anywhere, and there is no `java.time.Clock` bean and no `@Immutable` use anywhere. Transactions live on
  adapter methods (`ReleaseRepositoryAdapter`, `DocumentSnapshotRepositoryAdapter`). A `Clock` bean and `@Immutable` are both new to this codebase.
- `ArchitectureBoundaryTest` only forbids framework imports in `..domain..` and `..ports..`; `..application..` may use Spring (`@Service`) but not `adapters.jira/persistence/web/render`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/resources/db/migration/V10__environment_ladder_promotion.sql` (new) | migration | CRUD + seed | `V5__document_generation.sql` (append-only table, header comments) + `V7` (child FK) | role-match (trigger is new) |
| `B/domain/promotion/Stage.java`, `Promotion.java`, `PromotionOutcome.java` (new records/enum) | domain model | transform | `domain/release/Release.java`, `domain/release/ManualItemCategory.java`, `domain/document/DocumentSnapshot.java` | exact |
| `B/domain/promotion/PromotionHistory.java` (new, pure rule class) | domain rule | transform | none (closest style: `Release.requireOpen`) | no analog |
| `B/domain/promotion/*Exception.java` (new reason exceptions) | domain exception | request-response | `domain/release/ReleaseFrozenException.java`, `ReleaseDuplicateException.java` | exact |
| `B/ports/PromotionRepository.java` (new) | port | CRUD (insert-only) | `ports/DocumentSnapshotRepository.java` | exact |
| `B/ports/EnvironmentLadderRepository.java` (new) | port | CRUD | `ports/TemplateRepository.java` | role-match |
| `B/adapters/persistence/entity/PromotionEntity.java` (new) | model | CRUD | `entity/DocumentSnapshotEntity.java` | exact (+ `@Immutable`) |
| `B/adapters/persistence/entity/EnvironmentStageEntity.java` (new) | model | CRUD | `entity/ReleaseEntity.java` | exact |
| `B/adapters/persistence/repository/PromotionJpaRepository.java`, `EnvironmentStageJpaRepository.java` (new) | repository | CRUD | `repository/DocumentSnapshotJpaRepository.java`, `ReleaseJpaRepository.java` | exact |
| `B/adapters/persistence/PromotionRepositoryAdapter.java` (new) | adapter | CRUD, row-lock-then-insert | `ReleaseRepositoryAdapter.java` (`lockOpen`, `freeze`, `create`) + `DocumentSnapshotRepositoryAdapter.create` | exact |
| `B/adapters/persistence/EnvironmentLadderRepositoryAdapter.java` (new) | adapter | CRUD | `TemplateRepositoryAdapter.java` (`create`/`update` + duplicate mapping) | role-match |
| `B/application/PromotionService.java` (new) | service | request-response | `application/DocumentGenerationService.java` | role-match |
| `B/application/EnvironmentLadderService.java` (new) | service | CRUD | `application/JiraSiteConfigService` / `TemplateService` | role-match |
| `B/config/ClockConfig.java` or a `@Bean Clock` in an existing config (new) | config | n/a | `config/JiraRestClientConfig.java` (bean style) | partial |
| `B/adapters/web/PromotionController.java` + DTOs (new) | controller | request-response | `adapters/web/DocumentController.java` (`revisions`) + `ReleaseController.java` | exact |
| `B/adapters/web/EnvironmentLadderController.java` + DTOs (new) | controller | request-response | `adapters/web/JiraSiteConfigController.java`, `ReleaseController.java` | role-match |
| `B/adapters/web/GlobalExceptionHandler.java` (modify, SHARED with Phase 6) | exception handler | request-response | itself (lines 87-120, `problem()` at 276) | exact |
| `T/ArchitectureBoundaryTest.java` (modify, SHARED with Phase 6) | test | n/a | itself | exact |
| `T/domain/promotion/PromotionHistoryTest.java` (new) | test (unit) | transform | `T/application/ReleaseServiceManualItemsTest.java` style (plain JUnit + AssertJ) | role-match |
| `T/application/PromotionServiceTest.java`, `EnvironmentLadderServiceTest.java` (new) | test (unit, Mockito) | request-response | `T/application/ReleaseServiceTicketsTest.java` | role-match |
| `T/adapters/persistence/PromotionPersistenceIT.java`, `EnvironmentLadderPersistenceIT.java` (new) | test (Testcontainers IT) | CRUD | `T/adapters/persistence/DocumentSnapshotPersistenceIT.java` | exact |
| `T/adapters/web/PromotionControllerTest.java`, `EnvironmentLadderControllerTest.java` (new) | test (`@WebMvcTest`) | request-response | `T/adapters/web/ReleaseControllerTest.java` | exact |
| `F/src/api/promotions.ts`, `F/src/api/ladder.ts` (new) | hook | request-response | `F/src/api/documents.ts` (revisions query) + `F/src/api/releases.ts` (mutations) | exact |
| `F/src/components/PromotionSection.tsx` (new) | component | request-response | `F/src/components/DocumentsSection.tsx` | role-match |
| `F/src/components/RecordPromotionDialog.tsx` (new, also hosts Correct form) | component | request-response | `F/src/components/AddToReleaseDialog.tsx` + `ConfirmDialog.tsx` | exact |
| `F/src/components/EnvironmentLadderSection.tsx` (new) | component | CRUD | `F/src/routes/SettingsRoute.tsx` sections | role-match |
| `F/src/routes/ReleaseDetailRoute.tsx` (modify, SHARED, highest risk) | component | request-response | itself (line 599) | exact |
| `F/src/routes/SettingsRoute.tsx` (modify, SHARED) | component | request-response | itself | exact |
| `F/tests/components/PromotionSection.test.tsx`, `EnvironmentLadderSection.test.tsx` (new) | test (Vitest) | request-response | `F/tests/components/DocumentsSection.test.tsx` | exact |
| `F/tests/components/ReleaseDetailRoute.test.tsx` (modify, SHARED) | test | request-response | itself | exact |
| `src/main/resources/application.yml` AND `src/test/resources/application.yml` (modify only IF a skew property is chosen) | config | n/a | `app.jira.*` block | exact |

## Pattern Assignments

### `V10__environment_ladder_promotion.sql` (migration, CRUD + seed)

**Analog:** `V5__document_generation.sql` (append-only table, decision-id header comments, `CREATE INDEX` naming) and `V7__template_user_default.sql`.

**Header-comment convention** (V5 lines 16-31): comment every constraint with the decision id and WHY it lives in SQL:
```sql
-- Generated documents (DOC-07). Append-only (D-05-06): there is deliberately no updated_at,
-- no updated_by and no soft-delete flag, because nothing is ever overwritten or pruned ...
CREATE TABLE document_snapshot (
    id                   UUID PRIMARY KEY,
    release_id           UUID         NOT NULL REFERENCES release (id) ON DELETE CASCADE,
    document_type        VARCHAR(32)  NOT NULL
                         CHECK (document_type IN ('RELEASE_NOTES', 'RUNBOOK', 'APPROVAL')),
    ...
    generated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    generated_by         VARCHAR(255) NOT NULL,
    CONSTRAINT document_snapshot_revision_unique UNIQUE (release_id, document_type, revision)
);
CREATE INDEX document_snapshot_release_type_idx ON document_snapshot (release_id, document_type, revision DESC);
```
Differences to apply: `ON DELETE RESTRICT` (not CASCADE) for `promotion.release_id` and `promotion.stage_id` (D-07 discretion); `actor` column has no FK (V7 comment: "no FK because no user table exists", `VARCHAR(255)`); enum column uses `CHECK (outcome IN ('SUCCESS','FAILED','ROLLED_BACK','VOIDED'))` (D-07-15 adds VOIDED; RESEARCH.md's DDL sketch lists only three, so it must be amended).

**No analog for the trigger.** Use RESEARCH.md Pattern 2 verbatim (row-level `BEFORE UPDATE OR DELETE`, no `BEFORE TRUNCATE`). Also from RESEARCH.md: partial unique indexes `UNIQUE (supersedes_id) WHERE supersedes_id IS NOT NULL` and `UNIQUE (is_production) WHERE is_production`, composite FK `(supersedes_id, release_id)`, `DEFERRABLE INITIALLY DEFERRED` on `UNIQUE (position)`, seed via `gen_random_uuid()`. Add a DB-level `CHECK` that VOIDED rows must carry `supersedes_id` (new entries cannot be VOIDED, D-07-15).

---

### `B/domain/promotion/*` (domain model + exceptions)

**Analog:** `B/domain/release/Release.java:10-32` (immutable record with static factory and rule method):
```java
public record Release(UUID id, String name, ..., Instant createdAt, String createdBy, ...) {
    public static Release create(String name, String version, String jiraProjectKey, String actor, Instant now) {
        return new Release(UUID.randomUUID(), name, version, ReleaseStatus.OPEN, ...);
    }
    public boolean isFrozen() { return status == ReleaseStatus.FROZEN; }
    public void requireOpen() { if (isFrozen()) { throw new ReleaseFrozenException(); } }
}
```
Copy: `Promotion.record(...)` / `Promotion.correct(...)` static factories taking `Instant now` as a parameter (no clock inside the domain), `Stage` record with `retired()` helper. Package must stay free of `org.springframework`, `jakarta`, `org.hibernate`, Jackson (ArchUnit rule 1).

**Exceptions:** `domain/release/ReleaseFrozenException.java` is the whole pattern: `public class XException extends RuntimeException {}` with no body. One class per reason code (`PromotionStageSkippedException`, `PromotionTargetRetiredException`, `PromotionAlreadySupersededException`, `PromotionDeployedAtInvalidException`, `PromotionNotFoundException`, `StageNotFoundException`, `StageInUseException`, `StageNameDuplicateException`, `ProductionStageProtectedException`, `ReleaseNotFrozenException`). Enum outcome: copy `domain/release/ManualItemCategory.java`.

**Pure rule class `PromotionHistory`:** no analog. Take the skeleton from RESEARCH.md Pattern 1 and apply the locked decisions D-07-13 (later = `deployed_at`, tie-break `recorded_at`; ROLLED_BACK rejected without a standing SUCCESS on that stage), D-07-14 (corrections: field checks only), D-07-15 (effective VOIDED counts toward nothing). Inputs are `(List<Stage> orderedStages, List<Promotion> effectiveEntries)`.

---

### `B/ports/PromotionRepository.java` (port, insert-only)

**Analog:** `B/ports/DocumentSnapshotRepository.java:11-45`. Copy the header comment verbatim in spirit (it already anticipates Phase 7):
```java
// D-05-06: append-only by construction. There is deliberately no update, no delete and no setActive
// here - contrast TemplateRepository, which has all three. Do not add one "just for tests": a
// mutation method on this port is a defect ...
public interface DocumentSnapshotRepository {
    DocumentSnapshot create(DocumentSnapshot snapshot, LogoImage logo);
    Optional<DocumentSnapshot> findLatest(UUID releaseId, TemplateDocumentType documentType);
    List<DocumentSnapshot> findAllRevisions(UUID releaseId, TemplateDocumentType documentType);
    ...
}
```
Phase 7 shape: `append(...)`, `findByRelease(UUID)`, plus a freeze-state read under lock (RESEARCH.md "Port shape"). Port may only import `domain` types (no Spring/jakarta).

**`EnvironmentLadderRepository`:** analog `ports/TemplateRepository.java` (create/update/delete/find). Mutation methods are allowed here (ladder is editable); promotions are not.

---

### `B/adapters/persistence/PromotionRepositoryAdapter.java` (adapter, lock-validate-append)

**Primary analog:** `ReleaseRepositoryAdapter.java` (lock idiom, `saveAndFlush` + constraint-to-exception mapping).

**Row-lock idiom** (lines 42-52) - the "check that actually holds" is inside the mutating transaction:
```java
private ReleaseEntity lockOpen(UUID releaseId) {
    ReleaseEntity entity = jpa.findByIdForUpdate(releaseId).orElseThrow(ReleaseNotFoundException::new);
    if (entity.getStatus() == ReleaseStatus.FROZEN) { throw new ReleaseFrozenException(); }
    return entity;
}
```
Phase 7 inverts the check: lock the release row via the existing `ReleaseJpaRepository.findByIdForUpdate` (`repository/ReleaseJpaRepository.java:43-48`), throw `ReleaseNotFoundException` if absent and `ReleaseNotFrozenException` if still `OPEN`, then read `frozenAt` for the D-07-10 lower bound. Do NOT use `ReleaseRepository.findById` (it materialises every item; Phase 6 adds more child rows).

**Constraint mapping** (lines 46-58 `create`; same in `DocumentSnapshotRepositoryAdapter.java:34-43`):
```java
@Override
@Transactional
public Release create(Release release, List<ReleaseItem> items) {
    try {
        jpa.saveAndFlush(toEntity(release));
    } catch (DataIntegrityViolationException e) {
        // UNIQUE (name, version) - D-03-08. saveAndFlush forces the constraint to fire here
        // rather than at commit, where it would escape this catch.
        throw new ReleaseDuplicateException();
    }
    ...
}
```
Map the `promotion_superseded_once` violation to `PromotionAlreadySupersededException` the same way.

**Transactional structure decision for the planner:** application services here carry no `@Transactional`; adapters do. So the lock + load stages/entries + insert must be one adapter method (e.g. `appendUnderLock(releaseId, Function<PromotionContext, Promotion>)`) or the first `@Transactional` in `application/`. Prefer the adapter method to match convention (RESEARCH.md Pattern 3 lists both).

**Entity:** analog `DocumentSnapshotEntity.java:14-80` (plain `@Entity`, `@Table`, `@Column(name=...)`, `@Enumerated(EnumType.STRING)`, no-arg ctor, getters/setters). Add Hibernate `@Immutable` (new to the codebase; Hibernate is allowed in `adapters.persistence`). Do not add `@Version`.

---

### `B/adapters/persistence/EnvironmentLadderRepositoryAdapter.java` (adapter, CRUD)

**Analog:** `TemplateRepositoryAdapter.java` (`create` at 43-50 throws `TemplateDuplicateException` on unique violation; `update` 81-101 same; `delete` 121). Copy the try/`saveAndFlush`/catch-`DataIntegrityViolationException` shape for `STAGE_NAME_DUPLICATE`. Reorder is one `@Transactional` method rewriting positions 1..n (constraint is `DEFERRABLE INITIALLY DEFERRED`). Delete: check for referencing promotions first and throw `StageInUseException`; the `ON DELETE RESTRICT` FK is the backstop.

---

### `B/application/PromotionService.java` + `EnvironmentLadderService.java` (service)

**Analog:** `B/application/DocumentGenerationService.java:1-50`:
```java
@Service
public class DocumentGenerationService {
    private final ReleaseRepository releaseRepository;
    ...
    public DocumentGenerationService(ReleaseRepository releaseRepository, ...) { ... }
    public DocumentSnapshot generate(UUID releaseId, TemplateDocumentType documentType, UUID templateIdOrNull, String actor) {
```
Copy: constructor injection of ports only, `String actor` passed in by the controller, domain exceptions thrown, no Spring Web types. `Instant.now()` is called directly in `ReleaseService` (`ReleaseService.java:46`); Phase 7 deliberately deviates by injecting a `java.time.Clock` (RESEARCH.md Pitfall 6) so D-07-10 bounds are unit-testable. Add one `@Bean Clock` (`Clock.systemUTC()`) next to the existing config beans (`B/config/`).

---

### `B/adapters/web/PromotionController.java` (controller, request-response)

**Primary analog:** `DocumentController.java:37-91` (batched actor display) and `ReleaseController.java:38-60,95-98,143-148` (principal as actor, `@Valid` records, static mappers).

**Class shell and actor from principal** (`DocumentController.java:54-66`, `ReleaseController.java:50-60`):
```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public DocumentResponse generate(@PathVariable UUID releaseId, ..., Principal principal) {
    // Every /api/** path is authenticated() in SecurityConfig, so Spring Security guarantees a
    // principal here. ... The actor always comes from the principal, never from the body.
    return toResponse(service.generate(releaseId, documentType, templateId, principal.getName()));
}
```

**Batched actors, one call per response** (`DocumentController.java:82-90`):
```java
// D-05.3-05: resolved once for the whole history, not inside the map - a per-row lookup here is
// the N+1 this phase exists to avoid.
ResolvedActors actors = actorResolution.resolve(
        all.stream().map(DocumentSnapshot::generatedBy).toArray(String[]::new));
List<DocumentRevisionResponse> revisions = all.stream()
        .map(s -> new DocumentRevisionResponse(s.revision(), ..., actors.display(s.generatedBy()), s.templateName()))
        .toList();
return new DocumentRevisionListResponse(revisions);
```
Copy exactly for `entries[].recordedBy`. Keep mappers `static` taking `ResolvedActors` (`ReleaseController.toSummaryResponse`, line 143) so a row cannot resolve for itself. Controllers must depend on `ActorResolutionService`, never `JiraCredentialRepository` (ArchUnit rule `theWebAdapterCannotReachTheTokenBearingCredentialPort`).

**Request DTO validation** (`dto/CreateReleaseRequest.java`): records with `@NotBlank @Size(max=...)`; apply to `note`/`reason` (`@Size(max=2000)`), `@NotNull` on `stageId`/`outcome`. Request records must have no `recordedBy` field. Mutating endpoints return the refreshed aggregate (same GET shape) per RESEARCH.md Pattern 7. Use `@RequestMapping("/api/releases/{releaseId}/promotions")` (path var name `releaseId`, as in `DocumentController`).

**`EnvironmentLadderController`:** shell analog `JiraSiteConfigController.java:16-43` (`@GetMapping`/`@PutMapping`, `@Valid @RequestBody`, service call, static-ish `toResponse`); per-stage endpoints per RESEARCH.md Pattern 7.

---

### `B/adapters/web/GlobalExceptionHandler.java` (modify - SHARED with Phase 6, additive-only)

**Analog:** itself. One handler per exception, using the private `problem(status, title, detail, reason)` helper (line 276). Existing neighbors to copy:
```java
@ExceptionHandler(ReleaseFrozenException.class)
public ProblemDetail handleReleaseFrozen(ReleaseFrozenException ex) {
    return problem(HttpStatus.CONFLICT, "Release frozen",
            "This release is frozen and can no longer be changed.", "RELEASE_FROZEN");
}
@ExceptionHandler(ReleaseDuplicateException.class) ... "RELEASE_DUPLICATE"
```
**Phase 6 overlap:** 06-04 also appends handlers. Phase 7 touch point: add its handlers as one contiguous block directly after `handleReleaseDuplicate` (line ~120) or immediately before `handleTypeMismatch`, plus the imports. Do not reorder or edit existing handlers. Do not touch `handleUnexpected` / `handleExceptionInternal`. Reuse `RELEASE_NOT_FOUND`. Reason codes: `RELEASE_NOT_FROZEN`, `PROMOTION_STAGE_SKIPPED`, `PROMOTION_TARGET_RETIRED`, `PROMOTION_ALREADY_SUPERSEDED`, `PROMOTION_DEPLOYED_AT_INVALID` (422, matches `REFRESH_INCOMPLETE`/`TEMPLATE_TYPE_MISMATCH` precedent), `PROMOTION_NOT_FOUND`, `STAGE_NOT_FOUND`, `STAGE_IN_USE`, `STAGE_NAME_DUPLICATE`, `PRODUCTION_STAGE_PROTECTED`; add a reason for a rejected rollback (no standing SUCCESS, D-07-13) and for a VOIDED new entry.

---

### `T/ArchitectureBoundaryTest.java` (modify - SHARED with Phase 6, optional)

**Analog:** itself (rules are independent `@Test` methods, each `noClasses()...allowEmptyShould(true)`). New domain and ports packages are already covered by rules 1 and 2 with no edit. If Phase 7 adds the "port has no update/delete/remove/save method" property, append ONE new `@Test` at the END of the class (Phase 6's 06-04 also edits this file; both only append). Shape for a method-name rule: ArchUnit `noMethods().that().areDeclaredIn("com.evision.releasemgmt.ports.PromotionRepository").should().haveNameMatching("(update|delete|remove|save).*")` with `.allowEmptyShould(true)`.

---

### `T/adapters/persistence/PromotionPersistenceIT.java`, `EnvironmentLadderPersistenceIT.java` (Testcontainers IT)

**Analog:** `T/adapters/persistence/DocumentSnapshotPersistenceIT.java:30-90`:
```java
@SpringBootTest
@Testcontainers
class DocumentSnapshotPersistenceIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");
    @Autowired private DocumentSnapshotRepository repository;
    @Autowired private ReleaseRepository releaseRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearAll() {
        jdbcTemplate.execute("TRUNCATE release CASCADE");
        jdbcTemplate.execute("TRUNCATE template CASCADE");
    }
    private Release release(String name) {
        Release release = Release.create(name, "1.0", "CNV", "rm@acme.com", Instant.now());
        return releaseRepository.create(release, List.of());
    }
```
Adaptations: `TRUNCATE release CASCADE` will cascade into `promotion`; this is why the migration has no TRUNCATE trigger. A promotion needs a FROZEN release: call `releaseRepository.freeze(id, actor, at)` after `create`. Never `TRUNCATE environment_stage` without re-seeding (seed rows survive `TRUNCATE release CASCADE` only because environment_stage is referenced BY promotion, not the reverse). Property assertions from the CONTEXT "Specifics": `assertThatThrownBy(() -> jdbc.update("UPDATE promotion ..."))` / `DELETE`, FK rejects deleting a promoted release, superseded-once under two inserts, composite-FK rejects cross-release `supersedes_id`, DB rejects second Production stage and retiring the Production stage, used stage cannot be deleted, rename relabels history.

---

### `T/adapters/web/PromotionControllerTest.java`, `EnvironmentLadderControllerTest.java` (`@WebMvcTest`)

**Analog:** `T/adapters/web/ReleaseControllerTest.java:56-100`:
```java
@WebMvcTest(ReleaseController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class ReleaseControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ReleaseService service;
    @MockitoBean private ActorResolutionService actorResolution;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"));
    }
    private void stubActors() {
        when(actorResolution.resolve(any(String[].class)))
                .thenReturn(new ResolvedActors(Map.of("557058:abc-123", "rm@acme.com")));
    }
    // post("/api/releases").with(csrf()).with(authenticated()).contentType(APPLICATION_JSON).content(...)
    // ArgumentCaptor on service call proves actor == principal subject, not body.
```
Reuse as-is: `verify(actorResolution, times(1)).resolve(...)` proves "resolved once per response" (AUDIT-01); ArgumentCaptor proves the actor is the JWT subject.

---

### `F/src/api/promotions.ts` + `ladder.ts` (hooks)

**Analog:** `F/src/api/documents.ts:6-60` (typed query + key factory) and `F/src/api/releases.ts:61-80,105-115` (`useQuery` with `apiFetch`, mutations that write the response into the cache):
```ts
export const releaseQueryKey = (releaseId: string) => ['release', releaseId] as const;
export function useRelease(releaseId: string) {
  return useQuery({
    queryKey: releaseQueryKey(releaseId),
    queryFn: () => apiFetch<Release>(`/api/releases/${releaseId}`),
    retry: false,   // a 404 is a permanent answer for a fixed id
  });
}
function useReleaseMutation<TVars>(request: (vars: TVars) => Promise<Release>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (release) => {
      queryClient.setQueryData(releaseQueryKey(release.id), release);
      queryClient.invalidateQueries({ queryKey: ['releases'] });
    },
  });
}
```
Copy: `promotionsQueryKey = (releaseId) => ['promotions', releaseId] as const`; mutations `setQueryData` with the returned aggregate; stage mutations invalidate `['ladder']` AND `['promotions']` (a rename or reorder relabels every release's history and current stage). `apiFetch` (`F/src/api/client.ts:60-80`) adds `X-XSRF-TOKEN` on writes automatically and throws `ApiError` carrying `problem.reason`; key UI copy off `reason`. Types are plain `type` aliases mirroring the DTOs (`ProblemDetail` in `client.ts:3-9`). Timestamps: `new Date(local).toISOString()`, send no `deployedAt` when untouched.

---

### `F/src/components/PromotionSection.tsx` + `RecordPromotionDialog.tsx`

**Section analog:** `F/src/components/DocumentsSection.tsx:20-48` (a self-contained `<section>` that owns its queries, so the host route only mounts it):
```tsx
<section id="documents" className="mt-6 rounded border border-gray-200 bg-white p-4">
  <h3 className="text-base font-semibold text-gray-900">Documents</h3>
```
Copy the card classes, own the `usePromotions(releaseId)` query inside the section, and give it `id="history"`. Include an in-page link to `#documents` ("Document revisions", AUDIT-02) as a plain `<a href="#documents">`; `DocumentsSection` already carries that id (line 36). Use per-row sub-components for error isolation like `DocumentRow` (line 68+). Render user text (note, reason, stage name) only as React text nodes (security domain).

**Dialog analog:** `AddToReleaseDialog.tsx:17-60` and `ConfirmDialog.tsx`: native `<dialog>` + `showModal()` driven by an `open` prop in a `useEffect`, `onClose` routed to cancel, reset form and mutation state when it opens:
```tsx
useEffect(() => {
  const dialog = dialogRef.current;
  if (!dialog) return;
  if (props.open && !dialog.open) { setName(''); ...; createRelease.reset(); dialog.showModal(); }
  if (!props.open && dialog.open) dialog.close();
}, [props.open]);
```
Error display: `ApiError` -> `error.problem` -> `messageForReason(problem)` from `../api/auth` (used in `DocumentsSection`, `SettingsRoute`). Retired stages render as disabled options labelled "retired" (RESEARCH.md Pitfall 9). The section takes `isFrozen` and shows an explanatory empty state for OPEN releases rather than an enabled form.

---

### `F/src/components/EnvironmentLadderSection.tsx` (component, CRUD)

**Analog:** `F/src/routes/SettingsRoute.tsx:50-151`: sections are `<h2 className="mt-8 text-base font-semibold text-gray-900">` followed by `<form className="mt-4 space-y-2">` with `rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50` buttons, `role="status"` success text (`text-green-600`) and `role="alert"` + `data-reason={problem.reason}` error text (`text-red-600`). Copy those class strings and role/`data-reason` attributes. Destructive actions (delete unused stage) use `ConfirmDialog` (`confirmClassName` is passed by the caller). No timeline component (DEPL-06 is v2).

---

### SHARED FILE touch points (Phase 6 also modifies these)

| File | Phase 6 plan(s) | Phase 7 additive-only touch point |
|---|---|---|
| `B/adapters/web/GlobalExceptionHandler.java` | 06-04 | one contiguous block of new `@ExceptionHandler` methods + their imports; no edits to existing methods |
| `T/ArchitectureBoundaryTest.java` | 06-04 | optional single new `@Test` appended at end of class; imports only if needed |
| `F/src/routes/ReleaseDetailRoute.tsx` | 06-07 (heavy: items table, freeze dialog, code-links refresh) | ONE import line (`PromotionSection`) and ONE JSX line directly above `<DocumentsSection .../>` (currently line 599; `isFrozen` is defined at line 130). Re-read the file fresh at execution: line numbers will have moved |
| `F/src/routes/SettingsRoute.tsx` | 06-04 (ADO section) | ONE import line and ONE `<EnvironmentLadderSection />` line inside the returned `<div className="max-w-xl">`, after Phase 6's section if present, otherwise after the token form (before the closing `</div>` at line ~150). Note the container is `max-w-xl`; if the ladder editor needs more width, wrap inside the new component, not the route |
| `F/tests/components/ReleaseDetailRoute.test.tsx` | 06-07 (fixtures) | extend the existing fetch/`apiFetch` mock so `GET /api/releases/r1/promotions` returns an empty-ladder shape (RESEARCH.md Pitfall 7); prefer testing behavior in the new `PromotionSection.test.tsx` |
| `src/main/resources/application.yml` + `src/test/resources/application.yml` | 06-04 | Phase 7 needs NO new property unless the planner chooses a configurable clock-skew allowance (RESEARCH.md A2). If so, add a separate `app.promotion.*` block mirroring `app.jira.*` in BOTH files; otherwise do not touch |
| `F/src/api/releases.ts` | 06-07 | untouched by Phase 7 (new `promotions.ts` instead) |
| `B/application/ReleaseService`, `ReleaseRepository(+Adapter)`, `ReleaseResponse`, `ReleaseController` | 06-03, 06-05 | NOT touched by Phase 7 (promotion adapter reads release columns through `ReleaseJpaRepository.findByIdForUpdate`) |
| `V10` migration | 06-02 (V8), 06-03 (V9) | next free number; renumber if Phase 6 slips, never hard-code |

## Shared Patterns

### Actor identity (write and display)
**Source:** `DocumentController.java:54-66,82-90`, `ActorResolutionService.java:26-32`, `ResolvedActors.java:45-47`.
**Apply to:** `PromotionController` (record, correct, list). Write path: `principal.getName()` only, never a body field. Read path: one `actorResolution.resolve(String...)` per response (varargs tolerates nulls), `actors.display(id)` falls back to the raw accountId.

### Append-only (code level)
**Source:** `ports/DocumentSnapshotRepository.java:11-15` header comment; `DocumentSnapshotRepositoryAdapter.create` (insert via `saveAndFlush`, no update path).
**Apply to:** `PromotionRepository` port and adapter. No `update*`, `delete*`, `remove*`, `save*` method names; DB trigger is the second layer.

### Constraint-violation to domain exception
**Source:** `ReleaseRepositoryAdapter.java:46-58`, `TemplateRepositoryAdapter.java:43-50`.
**Apply to:** superseded-once, stage name duplicate. Always `saveAndFlush` inside the adapter's try/catch so the violation fires before commit.

### Row lock inside the mutating transaction
**Source:** `ReleaseJpaRepository.java:43-48` (`@Lock(PESSIMISTIC_WRITE)` query) and `ReleaseRepositoryAdapter.lockOpen`.
**Apply to:** `PromotionRepositoryAdapter` record and correct paths (serialises concurrent "next stage" checks).

### Error mapping to ProblemDetail
**Source:** `GlobalExceptionHandler.java:93-97,276-282`. Fixed copy, `reason` token, no stack traces. SPA keys on `problem.reason`.
**Apply to:** every new exception.

### Bean Validation at the boundary only
**Source:** `dto/CreateReleaseRequest.java`, `ReleaseController.create` (`@Valid @RequestBody`). Validation failures come back as `VALIDATION_FAILED` with `fieldErrors` (`GlobalExceptionHandler.java:237-250`).
**Apply to:** all new request records; domain-rule checks (frozen-only, skip, bounds) stay server-side in service/domain, not in annotations.

### Hexagonal boundary
**Source:** `ArchitectureBoundaryTest.java:19-52`. `domain/promotion` and the two new ports import nothing from Spring, jakarta, Hibernate, Jackson, adapters, config or application. `application/*Service` imports only ports and domain.

### Frontend error and success presentation
**Source:** `SettingsRoute.tsx:120-148` (`role="status"` / `role="alert" data-reason=...`), `messageForReason` from `F/src/api/auth`.
**Apply to:** `RecordPromotionDialog`, correction form, ladder editor.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `B/domain/promotion/PromotionHistory.java` | domain rule | transform | No pure ledger-derivation rule class exists; use RESEARCH.md Pattern 1 plus D-07-13/14/15. Style reference only: `Release.requireOpen()` (`Release.java:27-31`) |
| Append-only trigger section of `V10__...sql` | migration | n/a | No trigger, function or `plpgsql` exists in V1-V7. Use RESEARCH.md Pattern 2 (row-level `BEFORE UPDATE OR DELETE`, no `BEFORE TRUNCATE`) |
| `@Immutable` on `PromotionEntity`, `java.time.Clock` bean | persistence / config | n/a | Neither used anywhere yet; introduce deliberately, document in a one-line comment citing D-07-11 and D-07-10 |

## Metadata

**Analog search scope:** `src/main/java/com/evision/releasemgmt/{domain,ports,application,adapters/{persistence,web},config}`, `src/main/resources/db/migration`, `src/test/java/.../{adapters,application}`, `frontend/src/{api,components,routes}`, `frontend/tests/components`, Phase 6 `06-PATTERNS.md` (format precedent).
**Files scanned:** about 45 read in whole or in part.
**Pattern extraction date:** 2026-10-12
