---
phase: quick-261026-kyg
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/java/com/evision/releasemgmt/domain/roadmap/RoadmapItem.java
  - src/main/java/com/evision/releasemgmt/domain/roadmap/RoadmapAddResult.java
  - src/main/java/com/evision/releasemgmt/domain/roadmap/RoadmapRejection.java
  - src/main/java/com/evision/releasemgmt/domain/roadmap/RoadmapFilter.java
  - src/main/java/com/evision/releasemgmt/application/RoadmapImportService.java
  - src/main/java/com/evision/releasemgmt/application/RoadmapService.java
  - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/RoadmapManualItemRequest.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/RoadmapDetailsRequest.java
  - src/main/java/com/evision/releasemgmt/ports/RoadmapRepository.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/RoadmapRepositoryAdapter.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/RoadmapItemJpaRepository.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/RoadmapItemEntity.java
  - src/main/resources/db/migration/V20__roadmap_product_required.sql
  - src/test/java/com/evision/releasemgmt/application/RoadmapImportServiceTest.java
  - src/test/java/com/evision/releasemgmt/application/RoadmapServiceTest.java
  - src/test/java/com/evision/releasemgmt/domain/roadmap/RoadmapRankingTest.java
  - src/test/java/com/evision/releasemgmt/adapters/web/RoadmapControllerTest.java
  - src/test/java/com/evision/releasemgmt/adapters/web/RoadmapFlowIT.java
  - src/test/java/com/evision/releasemgmt/adapters/persistence/RoadmapPersistenceIT.java
  - frontend/src/api/roadmap.ts
  - frontend/src/api/reasonMessages.ts
  - frontend/src/roadmap/AddManualItemDialog.tsx
  - frontend/src/roadmap/ItemDetailRow.tsx
  - frontend/src/roadmap/RoadmapAddResult.tsx
  - frontend/tests/roadmap/AddDialogs.test.tsx
  - frontend/tests/roadmap/ItemDetailRow.test.tsx
  - frontend/tests/roadmap/RoadmapAddResult.test.tsx
autonomous: true
requirements: [QUICK-261026-kyg]

must_haves:
  truths:
    - "Every roadmap item has a product: the DB column roadmap_item.product_id is NOT NULL (V20)"
    - "Manual add and details edit without a productId return 400 VALIDATION_FAILED"
    - "Add from Jira / Add from release assigns each item the product that owns its Jira project (CNV-123 -> owner of project CNV)"
    - "A key whose project no product owns is rejected per key with ROADMAP_NO_PRODUCT_FOR_PROJECT; the other keys are still added"
    - "A key whose owning product is archived is rejected per key with ROADMAP_PRODUCT_ARCHIVED"
    - "The roadmap list and CSV filter by product / archived / done in SQL, not in Java; ranking stays in RoadmapRanking"
    - "The manual-add dialog and details editor have no 'No product' option; manual submit is disabled until a product is chosen"
    - "The add-result view shows 'No product owns Jira project XYZ' for each rejected key"
  artifacts:
    - path: "src/main/resources/db/migration/V20__roadmap_product_required.sql"
      contains: "ALTER COLUMN product_id SET NOT NULL"
    - path: "src/main/java/com/evision/releasemgmt/adapters/persistence/repository/RoadmapItemJpaRepository.java"
      provides: "JPQL filter queries with bound parameters"
    - path: "src/main/java/com/evision/releasemgmt/domain/roadmap/RoadmapAddResult.java"
      provides: "rejected list (jiraKey, reason)"
  key_links:
    - from: "RoadmapService.ranked"
      to: "RoadmapRepository.findMatching"
      via: "SQL-filtered read, then RoadmapRanking.rank"
      pattern: "findMatching\\(filter\\)"
    - from: "RoadmapImportService"
      to: "ProductRepository.productIdOwningJiraKey"
      via: "project key = jiraKey before the last '-'"
      pattern: "productIdOwningJiraKey"
    - from: "frontend/src/roadmap/RoadmapAddResult.tsx"
      to: "frontend/src/api/reasonMessages.ts"
      via: "per-key rejection message helper"
      pattern: "ROADMAP_NO_PRODUCT_FOR_PROJECT"
---

<objective>
Make the roadmap product required and move the roadmap list filter into SQL.

User decisions (locked 2026-10-26) supersede Phase 17 D-17-07's "optional product link"; every other Phase 17 roadmap decision still holds (notably D-15-07: an existing link to a since-archived product stays, only a NEW link must be to a live product).

Output: V20 migration, required productId on manual add/edit, Jira-project-derived product on Jira/release imports with a per-key ROADMAP_NO_PRODUCT_FOR_PROJECT rejection, SQL-side filtering in ranked(), and the matching frontend changes. Three tasks, one commit each.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@./CLAUDE.md
@.planning/STATE.md
@.planning/phases/17-rice-roadmap/17-CONTEXT.md

Environment rules (MUST follow):
- Every Maven call: `export JAVA_HOME="C:/Users/ucs/.jdks/jdk-25"` in the same command, then `mvn -B -q -P '!frontend' ...`. Use `clean` in tasks that change public signatures (Task 1 and Task 2 do).
- Docker is running; Testcontainers ITs can run.
- Frontend: run from `frontend/`: `npx tsc -b`, `npx vitest run <paths>`. Never `npm run dev`.
- Known noise: RoadmapRoute test "opens the detail row named by the item param" is flaky; the 2 AssistantPropertiesTest failures in the main tree come from the user's uncommitted yml edits — ignore both.
- NEVER stage compose.app.yaml, docs/docker.md, src/main/resources/application.yml, src/test/resources/application.yml. Stage files by name.
- One commit per task. NO Co-Authored-By trailer. Commit messages explain why.

<interfaces>
From ports/ProductRepository.java (Phase 17.1, already exists):
```java
Optional<Product> findById(UUID productId);              // Product has archived()
Optional<UUID> productIdOwningJiraKey(String key);       // key = Jira PROJECT key, case-insensitive ("CNV")
```

From domain/roadmap/RoadmapItem.java (current):
```java
public static RoadmapItem manual(String title, UUID productId, String actor, Instant now);
public static RoadmapItem fromJira(JiraSnapshot snapshot, String actor, Instant now); // productId hard-coded null
```

From domain/roadmap/RoadmapAddResult.java (current):
```java
public record RoadmapAddResult(List<Added> added, List<AlreadyPresent> alreadyPresent, List<String> notFound) {
    public record Added(UUID itemId, String jiraKey) {}
    public record AlreadyPresent(String jiraKey, UUID itemId) {}
}
```

From domain/roadmap/RoadmapFilter.java (current):
```java
public record RoadmapFilter(Set<UUID> productIds, boolean includeDone, boolean includeArchived) {
    public boolean matches(RoadmapItem item) { ... }   // to be removed
}
```

RoadmapService (current):
```java
public List<RankedItem> ranked(RoadmapFilter filter) {
    return RoadmapRanking.rank(items.findAll().stream().filter(filter::matches).toList());
}
private void requireLinkable(UUID productId, UUID currentProductId) // returns early when productId == null
```

RoadmapImportService constructor (current): `(RoadmapRepository items, TicketSearchService tickets, ReleaseService releases, Clock clock)`; private `insert(JiraSnapshot, actor, now, added, alreadyPresent)` calls `RoadmapItem.fromJira(snapshot, actor, now)`.

GlobalExceptionHandler has an exhaustive `switch (ex.reason())` over RoadmapRejection (no default) around line 322 — a new enum constant needs a case or it will not compile.

RoadmapItemJpaRepository: Spring Data JPA, entity `RoadmapItemEntity` with fields `productId` (UUID), `status` (RoadmapStatus enum), `archivedAt` (Instant); existing queries use JPQL + @Param.

ProductController: `PUT /api/products/{productId}/jira-projects` body `{"keys":["CNV"]}` sets a product's Jira project keys.

Frontend (frontend/src/api/roadmap.ts):
```ts
export type RoadmapDetails = { title; status; productId: string | null; targetQuarter; owner; notes };
export type RoadmapAddResult = { added: {itemId; jiraKey}[]; alreadyPresent: {jiraKey; itemId}[]; notFound: string[] };
// useAddManualItem mutation body: { title: string; productId: string | null }
```
frontend/src/api/reasonMessages.ts: `REASON_MESSAGES: Record<string,string>` (the only place failure text is chosen, D-04) + `messageForReason(problem)`.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Derive each imported item's product from its Jira project, reject unowned projects per key</name>
  <files>
    src/main/java/com/evision/releasemgmt/domain/roadmap/RoadmapItem.java,
    src/main/java/com/evision/releasemgmt/domain/roadmap/RoadmapAddResult.java,
    src/main/java/com/evision/releasemgmt/domain/roadmap/RoadmapRejection.java,
    src/main/java/com/evision/releasemgmt/application/RoadmapImportService.java,
    src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java,
    src/test/java/com/evision/releasemgmt/application/RoadmapImportServiceTest.java,
    src/test/java/com/evision/releasemgmt/application/RoadmapServiceTest.java,
    src/test/java/com/evision/releasemgmt/adapters/persistence/RoadmapPersistenceIT.java,
    src/test/java/com/evision/releasemgmt/adapters/web/RoadmapFlowIT.java
  </files>
  <behavior>
    - addFromJira(["CNV-1"]) with CNV owned by live product P -> added, and the RoadmapItem passed to items.add has productId P
    - addFromJira(["CNV-1","XYZ-2"]) with XYZ owned by nobody -> CNV-1 added; rejected contains (XYZ-2, ROADMAP_NO_PRODUCT_FOR_PROJECT); items.add called once
    - addFromJira(["OLD-1"]) whose owning product is archived -> rejected (OLD-1, ROADMAP_PRODUCT_ARCHIVED), no insert
    - A moved issue (requested CNV-1, Jira returns ABC-9) derives the product from ABC (the stored key's project)
    - Two keys of the same project trigger one productIdOwningJiraKey lookup (per-call cache)
    - addFromRelease applies the same derivation and rejections per key
    - Not-found and already-present keys keep their existing outcome (no product lookup needed for them)
  </behavior>
  <action>
Per locked decision 3 (supersedes D-17-07 for imports):

1. `RoadmapRejection`: add `ROADMAP_NO_PRODUCT_FOR_PROJECT`.
2. `RoadmapAddResult`: add a fourth component `List<Rejected> rejected` and nested `public record Rejected(String jiraKey, RoadmapRejection reason) {}`. Update every constructor call (service + tests). JSON shape becomes `{added, alreadyPresent, notFound, rejected:[{jiraKey, reason}]}`; the controller returns the record directly, so no DTO change.
3. `RoadmapItem.fromJira(JiraSnapshot snapshot, UUID productId, String actor, Instant now)` — productId goes into the product slot instead of `null`. Update callers in RoadmapServiceTest, RoadmapImportServiceTest and RoadmapPersistenceIT (in RoadmapPersistenceIT create a product via the autowired `products.add("...")` the same way the existing test that asserts `stored.productId()` does, and pass its id — do not pass null, Task 2 makes the column NOT NULL).
4. `RoadmapImportService`: inject `ProductRepository products` (constructor param after `releases`). Add a private record `Owner(UUID productId, RoadmapRejection rejection)` and a method `Owner ownerOf(String jiraKey, Map<String, Owner> cache)`: project = `jiraKey.substring(0, jiraKey.lastIndexOf('-'))` (key format is already validated `^[A-Z][A-Z0-9]*-[0-9]+$`); `cache.computeIfAbsent(project, ...)` -> `products.productIdOwningJiraKey(project)`; empty -> `Owner(null, ROADMAP_NO_PRODUCT_FOR_PROJECT)`; present but `products.findById(id)` is archived -> `Owner(null, ROADMAP_PRODUCT_ARCHIVED)`; otherwise `Owner(id, null)`. In both `addFromJira` (at the point where it currently calls `insert(snapshot(ticket, now), ...)`, using `ticket.key()` so moved issues use their current project) and `addFromRelease` (before its `insert`), resolve the owner; if `rejection != null` append `new RoadmapAddResult.Rejected(key, rejection)` and skip the insert; otherwise pass `owner.productId()` into `insert`, which passes it to `RoadmapItem.fromJira`. One `Map<String, Owner>` cache per call. Keep the class non-transactional (each insert its own transaction) so one rejected key never aborts the rest.
5. `GlobalExceptionHandler`: add a case for `ROADMAP_NO_PRODUCT_FOR_PROJECT` -> `problem(HttpStatus.CONFLICT, "No product for project", "No product owns this Jira project.")` (matching the existing case style) — the reason is only reported per key, but the exhaustive switch requires the case.
6. Tests: `RoadmapImportServiceTest` — construct with a mocked `ProductRepository`; default stub in setup so existing tests keep passing (e.g. `productIdOwningJiraKey(any())` -> a live product id, `findById` -> a non-archived Product); add tests for every behavior bullet above, asserting via `ArgumentCaptor<RoadmapItem>` on `items.add` that productId is set. `RoadmapFlowIT` — the stub Jira returns CNV-1/CNV-2: create a second product "Platform" in step 1 and `PUT /api/products/{platform}/jira-projects` `{"keys":["CNV"]}` before the Jira add, so step 7's `productIds=core` filter still returns only "Manual A"; change the step-7 details PUT that sends `"productId":null` to send the Platform id; add one assertion that an add of a key from an unowned project (stub a ticket e.g. `ZZZ-1`) returns `$.rejected[0].reason == "ROADMAP_NO_PRODUCT_FOR_PROJECT"`.
  </action>
  <verify>
    <automated>export JAVA_HOME="C:/Users/ucs/.jdks/jdk-25" && cd /d/IdeaProjects/ReleaseManagement && mvn -B -q -P '!frontend' clean test -Dtest='RoadmapImportServiceTest,RoadmapServiceTest,RoadmapControllerTest,RoadmapFlowIT,RoadmapPersistenceIT'</automated>
  </verify>
  <done>Imports set productId from the owning product of the key's Jira project; unowned/archived projects appear in `rejected` per key while other keys are added; all listed tests green; one commit (e.g. "feat(roadmap): derive imported items' product from their Jira project").</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Make product_id NOT NULL, require productId on manual add/edit, filter the roadmap list in SQL</name>
  <files>
    src/main/resources/db/migration/V20__roadmap_product_required.sql,
    src/main/java/com/evision/releasemgmt/adapters/persistence/entity/RoadmapItemEntity.java,
    src/main/java/com/evision/releasemgmt/adapters/web/dto/RoadmapManualItemRequest.java,
    src/main/java/com/evision/releasemgmt/adapters/web/dto/RoadmapDetailsRequest.java,
    src/main/java/com/evision/releasemgmt/application/RoadmapService.java,
    src/main/java/com/evision/releasemgmt/domain/roadmap/RoadmapFilter.java,
    src/main/java/com/evision/releasemgmt/ports/RoadmapRepository.java,
    src/main/java/com/evision/releasemgmt/adapters/persistence/RoadmapRepositoryAdapter.java,
    src/main/java/com/evision/releasemgmt/adapters/persistence/repository/RoadmapItemJpaRepository.java,
    src/test/java/com/evision/releasemgmt/adapters/persistence/RoadmapPersistenceIT.java,
    src/test/java/com/evision/releasemgmt/application/RoadmapServiceTest.java,
    src/test/java/com/evision/releasemgmt/domain/roadmap/RoadmapRankingTest.java,
    src/test/java/com/evision/releasemgmt/adapters/web/RoadmapControllerTest.java
  </files>
  <behavior>
    - Inserting a roadmap_item row with product_id NULL fails with DataAccessException (V20 applied)
    - POST /api/roadmap/items without productId -> 400 VALIDATION_FAILED; PUT /items/{id}/details with productId null -> 400
    - findMatching(filter(empty, false, false)) returns all non-archived, non-DONE items across all products
    - findMatching(filter({A}, ...)) returns only product A's items; with {A,B} returns both
    - includeDone=true includes DONE; includeArchived=true includes archived
    - RoadmapService.ranked delegates to items.findMatching(filter) and ranks the result (no findAll)
  </behavior>
  <action>
1. Per decision 1: create `V20__roadmap_product_required.sql` containing only `ALTER TABLE roadmap_item ALTER COLUMN product_id SET NOT NULL;` (V17/V18 are reserved by other phases; FlywayConfig enables outOfOrder; live DB has 0 roadmap rows so it fails loudly only if NULLs exist — acceptable per user). `RoadmapItemEntity`: `@Column(name = "product_id", nullable = false)`.
2. Per decision 2: `@NotNull` on `RoadmapManualItemRequest.productId` and `RoadmapDetailsRequest.productId`. `RoadmapService.requireLinkable`: drop the `productId == null` early return (the DTO guarantees non-null); keep `productId.equals(currentProductId)` early return (D-15-07: an existing archived link may stay) and the archived -> `ROADMAP_PRODUCT_ARCHIVED` rejection.
3. Per decision 4: add to `RoadmapRepository` port `/** Items matching the filter, unordered (ranking is RoadmapRanking's job). */ List<RoadmapItem> findMatching(RoadmapFilter filter);` Keep `findAll()` (RoadmapImportService.refreshAll uses it). In `RoadmapItemJpaRepository` add two JPQL queries with bound parameters only:
   - `findMatching(@Param("includeDone") boolean, @Param("includeArchived") boolean, @Param("done") RoadmapStatus)`: `select r from RoadmapItemEntity r where (:includeArchived = true or r.archivedAt is null) and (:includeDone = true or r.status <> :done)`
   - `findMatchingInProducts(@Param("productIds") Collection<UUID>, ...same...)`: same plus `and r.productId in :productIds`.
   Two queries because an empty `IN ()` collection is not portable; the adapter calls `findMatching` when `filter.productIds()` is empty (= all products) and `findMatchingInProducts` otherwise, passing `RoadmapStatus.DONE` as `:done`. Adapter method `@Transactional(readOnly = true)`, maps `RoadmapItemEntity::toDomain`.
   `RoadmapService.ranked`: `return RoadmapRanking.rank(items.findMatching(filter));` — the list endpoint and CSV export already both go through `ranked()`. Delete `RoadmapFilter.matches` (nothing else uses it — confirm with `grep -rn "filter::matches\|\.matches(" src/main/java | grep -i roadmap`); keep the record as the query parameter object.
4. Tests:
   - `RoadmapPersistenceIT`: add `findMatchingFiltersInSql` — create products A and B, insert items (A open, A DONE, A archived, B open) via `roadmap.add`/`updateDetails`/`setArchivedAt`, assert result id sets for: no products/no done/no archived; {A}; {A,B}; includeDone; includeArchived. Add `productIsRequired` asserting a raw `jdbc.update("INSERT INTO roadmap_item (id, source, title, created_at, created_by) VALUES (?, 'MANUAL', 't', now(), 'u')", UUID.randomUUID())` throws DataAccessException. Fix any remaining `RoadmapItem.manual(..., null, ...)` and any `RoadmapItemDetails(..., null /*product*/, ...)` calls to use a real product id (currently around lines 105, 166, 175, 189 — the not-found case at 189 may keep a random UUID since the item does not exist). Check `@AfterEach` cleanup still works with the RESTRICT FK (delete roadmap rows before products).
   - `RoadmapServiceTest`: ranked tests stub `items.findMatching(filter)` instead of `findAll()` and assert ranking only (filter semantics now live in the IT); add a test that `addManual`/`updateDetails` with an archived new product still throw ROADMAP_PRODUCT_ARCHIVED; remove/adjust tests relying on null productId.
   - `RoadmapRankingTest`: delete the `RoadmapFilter.matches` tests (around lines 88-110).
   - `RoadmapControllerTest`: add 400 tests for manual add without productId and details PUT with `"productId":null`; update existing request bodies that omit productId.
   - Do NOT edit V16RoadmapMigrationIT: it migrates only to target 16, so V20 does not affect it.
  </action>
  <verify>
    <automated>export JAVA_HOME="C:/Users/ucs/.jdks/jdk-25" && cd /d/IdeaProjects/ReleaseManagement && mvn -B -q -P '!frontend' clean test -Dtest='Roadmap*Test,Roadmap*IT,V16RoadmapMigrationIT,ProductJiraProjectPersistenceIT'</automated>
  </verify>
  <done>V20 applied in ITs; product_id NOT NULL; @NotNull on both request DTOs; ranked() uses findMatching with SQL filtering; RoadmapFilter has no matches(); all Roadmap* unit tests and ITs green; one commit (e.g. "feat(roadmap): require a product on every item and filter the list in SQL").</done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: Frontend — required product in add/edit, per-key rejection in the add result</name>
  <files>
    frontend/src/api/roadmap.ts,
    frontend/src/api/reasonMessages.ts,
    frontend/src/roadmap/AddManualItemDialog.tsx,
    frontend/src/roadmap/ItemDetailRow.tsx,
    frontend/src/roadmap/RoadmapAddResult.tsx,
    frontend/tests/roadmap/AddDialogs.test.tsx,
    frontend/tests/roadmap/ItemDetailRow.test.tsx,
    frontend/tests/roadmap/RoadmapAddResult.test.tsx
  </files>
  <behavior>
    - Manual-add dialog: label "Product" (not "(optional)"), first option is a disabled placeholder "Choose a product" with value "", no "No product" option; "Add item" button disabled until a product is selected; posts `{title, productId}` with a non-null id
    - Details editor: no "No product" option; product select only offers products (archived current product still shown, per D-15-07)
    - RoadmapAddResult with `rejected: [{jiraKey:'XYZ-2', reason:'ROADMAP_NO_PRODUCT_FOR_PROJECT'}]` renders "XYZ-2: No product owns Jira project XYZ. ..." ; with ROADMAP_PRODUCT_ARCHIVED renders "OLD-1: " + the existing archived-product message
  </behavior>
  <action>
Per decision 5:
1. `api/roadmap.ts`: `RoadmapDetails.productId: string`; manual-add mutation body `{ title: string; productId: string }`; `RoadmapAddResult` gains `rejected: { jiraKey: string; reason: string }[]`.
2. `api/reasonMessages.ts`: add `ROADMAP_NO_PRODUCT_FOR_PROJECT: 'No product owns this Jira project. Link the project to a product on the Products screen, then add it again.'` to `REASON_MESSAGES`, and export `messageForRejectedKey(jiraKey: string, reason: string): string` — for `ROADMAP_NO_PRODUCT_FOR_PROJECT` returns `` `No product owns Jira project ${project}. Link it to a product on the Products screen, then add it again.` `` where `project = jiraKey.slice(0, jiraKey.lastIndexOf('-'))`; otherwise `REASON_MESSAGES[reason] ?? <the existing generic fallback text>`. Text stays in this file (D-04: the only place failure text is chosen).
3. `RoadmapAddResult.tsx`: destructure `rejected` and render one `<p key={jiraKey}>{jiraKey}: {messageForRejectedKey(jiraKey, reason)}</p>` per entry after the notFound lines. When `added.length === 0` the existing "Nothing added" branch is unchanged.
4. `AddManualItemDialog.tsx`: replace `<option value="">No product</option>` with `<option value="" disabled>Choose a product</option>`, label "Product" (drop "(optional)"), submit `disabled={add.isPending || productId === ''}`, post `{ title: trimmed, productId }`.
5. `ItemDetailRow.tsx`: remove the `<option value="">No product</option>`; keep `value={item.product?.id ?? ''}` and the archived-current-product option; change onChange to `save({ productId: event.target.value })`; in `save`, default `productId: item.product?.id ?? ''` only if tsc needs a string (every item has a product after V20).
6. Tests: AddDialogs.test.tsx — the existing `requiresATitleAndSendsNoRequest` and `postsTheTitleAndProductAndReturnsTheItem` must select a product first where submit is used; add `disablesAddUntilAProductIsChosen` and assert no "No product" option. ItemDetailRow.test.tsx — assert no "No product" option; update any test that selected "". RoadmapAddResult.test.tsx — add the two rejection rendering cases; add `rejected: []` to existing result fixtures (also any other fixtures typed `RoadmapAddResult` that `npx tsc -b` flags).
  </action>
  <verify>
    <automated>cd /d/IdeaProjects/ReleaseManagement/frontend && npx tsc -b && npx vitest run tests/roadmap</automated>
  </verify>
  <done>Product is required in both forms, rejected keys display their message, tsc clean, roadmap vitest suite green (ignoring the known flaky RoadmapRoute item-param test); one commit (e.g. "feat(roadmap-ui): require a product and show per-key project rejections").</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser -> /api/roadmap/* | productIds query params, productId bodies, Jira keys are untrusted |
| app -> PostgreSQL | filter values reach SQL |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-kyg-01 | T (SQL injection) | RoadmapItemJpaRepository.findMatching / findMatchingInProducts | mitigate | JPQL with named @Param bindings only (`:productIds`, `:includeDone`, `:includeArchived`, `:done`); productIds are bound as a typed `Collection<UUID>` — no string concatenation of ids or flags anywhere in the query path |
| T-kyg-02 | T | RoadmapImportService project-key derivation | mitigate | Keys are validated against `^[A-Z][A-Z0-9]*-[0-9]+$` at the DTO and in the service before use; the derived project key goes only to the existing parameterised `productIdOwningJiraKey` query |
| T-kyg-03 | T (integrity) | roadmap_item.product_id | mitigate | `@NotNull` at the request DTO boundary plus DB `NOT NULL` (V20) plus existing FK `ON DELETE RESTRICT`; linking to an archived product rejected (ROADMAP_PRODUCT_ARCHIVED) |
| T-kyg-04 | I | per-key rejection payload | accept | Echoes only the caller's own submitted key and an enum reason; product names/ids of other projects are not disclosed |
| T-kyg-05 | D | import with many keys | accept | Existing 100-key cap (ROADMAP_TOO_MANY_ITEMS); product lookups are cached per project within a call |
</threat_model>

<verification>
- `export JAVA_HOME="C:/Users/ucs/.jdks/jdk-25" && mvn -B -q -P '!frontend' clean test` passes (surefire runs *IT too) except the known AssistantPropertiesTest yml noise in the main tree
- `grep -rn "filter::matches" src/main/java` returns nothing
- `grep -v '^--' src/main/resources/db/migration/V20__roadmap_product_required.sql | grep -c "SET NOT NULL"` is 1
- frontend `npx tsc -b && npx vitest run` green (known flaky RoadmapRoute test excepted)
- `git status` shows compose.app.yaml, docs/docker.md and both application.yml still unstaged; 3 new commits, none with a Co-Authored-By trailer
</verification>

<success_criteria>
- Every roadmap item has a product, enforced by DB and API
- Jira/release imports assign the owning product per key; unowned/archived projects rejected per key without blocking other keys
- Roadmap list and CSV filtering happens in SQL with bound parameters; ranking unchanged
- UI cannot submit a productless item and explains rejected keys
</success_criteria>

<output>
After completion, create `.planning/quick/261026-kyg-roadmap-product-required-and-filter-in-t/261026-kyg-SUMMARY.md`
</output>
