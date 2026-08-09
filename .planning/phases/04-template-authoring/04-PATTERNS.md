# Phase 4: Template Authoring - Pattern Map

**Mapped:** 2026-08-06
**Files analyzed:** ~34 (backend ~20, frontend ~14)
**Analogs found:** 28 / 34 (6 flagged as genuine gaps — see "No Analog Found")

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `domain/template/Template.java` | model (aggregate) | CRUD | `domain/release/Release.java` | exact |
| `domain/template/TemplateDocumentType.java` | model (enum) | CRUD | `domain/release/ReleaseStatus.java` | exact |
| `domain/template/ChromeFields.java` (or inline on `Template`) | model (value object) | CRUD | `domain/release/ManualItem.java` (nested value shape) | role-match |
| `domain/template/TemplateNotFoundException.java` | model (domain exception) | request-response | `domain/release/ReleaseNotFoundException.java` | exact |
| `domain/template/TemplateMalformedException.java` | model (domain exception, carries detail) | request-response | `domain/auth/JiraAuthenticationException.java` (reason-carrying exception) | role-match |
| `domain/template/RenderBoundExceededException.java` | model (domain exception) | request-response | `domain/release/ReleaseRefreshIncompleteException.java` (plain no-arg domain exception) | role-match |
| `domain/template/view/ReleaseView.java`, `ItemView.java`, `GroupView.java`, `CommitView.java`, `PullRequestView.java` | model (flattened view-model) | transform | **no analog — new concept** (closest in spirit: `adapters/web/dto/ReleaseResponse.java` flattening) | none |
| `ports/TemplateRepository.java` | port | CRUD | `ports/ReleaseRepository.java` | exact |
| `ports/RenderEnginePort.java` | port | transform | `ports/JiraTicketPort.java` | role-match |
| `adapters/render/MustacheFlexmarkRenderAdapter.java` (or `JMustacheFlexmarkAdapter`) | service (external-library adapter) | transform | `adapters/jira/JiraRestTicketAdapter.java` (library behind a port) + `adapters/jira/AdfFlattener.java` (bounding pattern) | role-match |
| `application/RenderService.java` | service | transform, bounded | `application/ReleaseService.java` (service shape) + `adapters/jira/AdfFlattener.java` (MAX_NODES/MAX_DEPTH bounding, lines 7-8, 64-74) | role-match |
| `application/TemplateService.java` | service | CRUD | `application/ReleaseService.java` | exact |
| `application/TemplateViewAssemblerService.java` (or similar — release → view-model) | service | transform | **no analog — new concept** (nearest shape: `adapters/web/ReleaseController.java`'s private `toResponse`/`toItemResponse` flattening helpers, but those build a wire DTO, not a render-time view-model) | none |
| `adapters/persistence/TemplateRepositoryAdapter.java` | service (persistence adapter) | CRUD | `adapters/persistence/ReleaseRepositoryAdapter.java` | exact |
| `adapters/persistence/entity/TemplateEntity.java` | model (JPA entity) | CRUD | `adapters/persistence/entity/ReleaseEntity.java` | exact |
| `adapters/persistence/repository/TemplateJpaRepository.java` | model (Spring Data repo) | CRUD | `adapters/persistence/repository/ReleaseJpaRepository.java` (`@Lock(PESSIMISTIC_WRITE)` + native query patterns) | exact |
| `adapters/persistence/converter/LogoBytesConverter.java` (if needed) or plain `byte[]` column | utility (column conversion) | file-I/O | `adapters/persistence/converter/JsonStringListConverter.java` (`AttributeConverter` shape) — **content-sniffing/dimension validation itself has no analog** | partial |
| `adapters/web/TemplateController.java` | controller | request-response | `adapters/web/ReleaseController.java` | exact |
| `adapters/web/dto/CreateTemplateRequest.java`, `UpdateTemplateBodyRequest.java`, `UpdateChromeRequest.java` | model (request DTO) | request-response | `adapters/web/dto/CreateReleaseRequest.java`, `ManualItemRequest.java` | exact |
| `adapters/web/dto/TemplateResponse.java`, `TemplateSummaryResponse.java` | model (response DTO) | request-response | `adapters/web/dto/ReleaseResponse.java`, `ReleaseSummaryResponse.java` | exact |
| `adapters/web/dto/PlaceholderCatalogResponse.java` (if served) | model (response DTO) | request-response | `adapters/web/dto/JiraSiteConfigResponse.java` (small static-shaped GET response) | role-match |
| `adapters/web/GlobalExceptionHandler.java` (modified) | controller (advice) | request-response | itself — extend existing file, same `problem(...)` helper | exact |
| `src/main/resources/db/migration/V4__template_authoring.sql` | migration | batch | `src/main/resources/db/migration/V3__release_assembly.sql` | exact |
| `config/TemplateSeeder.java` | config (startup seeding) | batch | `config/JiraSiteConfigSeeder.java` (`ApplicationRunner` + `seedIfAbsent`) | exact |
| `frontend/src/routes/TemplatesRoute.tsx` | route/component | request-response | `frontend/src/routes/ReleasesRoute.tsx` | exact |
| `frontend/src/routes/TemplateDetailRoute.tsx` | route/component | request-response | `frontend/src/routes/ReleaseDetailRoute.tsx` | exact |
| `frontend/src/router.tsx` (modified) | provider (route registration) | request-response | itself — extend existing file | exact |
| `frontend/src/components/AppShell.tsx` (modified) | component (nav) | request-response | itself — extend existing file | exact |
| `frontend/src/components/PlaceholderDrawer.tsx` | component (non-modal slide-in panel) | request-response | **no close analog** — nearest is `frontend/src/components/ConfirmDialog.tsx` for the `<dialog>`/ref-toggle convention, which does **not** transfer (drawer is non-modal, absolutely-positioned, no backdrop) | none |
| `frontend/src/components/ConfirmDialog.tsx` (reused as-is) | component | request-response | itself — reused verbatim for delete/discard confirmations | exact |
| `frontend/src/components/NewTemplateDialog.tsx` | component (dialog + form) | request-response | `frontend/src/components/AddToReleaseDialog.tsx` | role-match |
| `frontend/src/components/LogoUpload.tsx` | component (file upload) | file-I/O | **no analog — new concept**, no file-upload control exists anywhere in the frontend | none |
| `frontend/src/components/TemplatePreview.tsx` | component (client-side render) | transform | **no analog — new concept**, first client-side Mustache+Markdown renderer | none |
| `frontend/src/api/templates.ts` | hook/module (API client) | CRUD | `frontend/src/api/releases.ts` | exact |
| `frontend/src/api/client.ts` (reused as-is) | utility | request-response | itself — `apiFetch` reused verbatim | exact |

## Pattern Assignments

### `domain/template/Template.java` (model, CRUD)

**Analog:** `src/main/java/com/evision/releasemgmt/domain/release/Release.java`

**Record + static factory + guard pattern** (whole file, `Release.java:1-29`):
```java
public record Release(UUID id, String name, String version, ReleaseStatus status,
                      String jiraProjectKey, Instant createdAt, String createdBy,
                      Instant frozenAt, String frozenBy, Instant lastRefreshedAt,
                      List<ReleaseItem> items, List<ManualItem> manualItems) {

    public static Release create(String name, String version, String jiraProjectKey,
                                 String actor, Instant now) {
        return new Release(UUID.randomUUID(), name, version, ReleaseStatus.OPEN, jiraProjectKey,
                now, actor, null, null, null, List.of(), List.of());
    }

    public boolean isFrozen() {
        return status == ReleaseStatus.FROZEN;
    }

    public void requireOpen() {
        if (isFrozen()) {
            throw new ReleaseFrozenException();
        }
    }
}
```
Copy this shape for `Template`: a record with `id`, `name`, `documentType`, `body`, the six chrome
fields, `active`, `logoId`/logo metadata, audit fields, `create(...)` static factory. There is no
`requireOpen()`-style guard needed (D-04-13: freely editable/deletable, no lifecycle guard) — this
is a simpler aggregate than `Release`, not a state machine.

### `domain/template/TemplateDocumentType.java` (model, enum)

**Analog:** `src/main/java/com/evision/releasemgmt/domain/release/ReleaseStatus.java`
```java
public enum ReleaseStatus {
    OPEN,
    FROZEN
}
```
Same shape for `RELEASE_NOTES`, `RUNBOOK`, `APPROVAL`.

### `domain/template/TemplateNotFoundException.java` (model, domain exception)

**Analog:** `src/main/java/com/evision/releasemgmt/domain/release/ReleaseNotFoundException.java`
```java
public class ReleaseNotFoundException extends RuntimeException {
}
```
Plain no-arg `RuntimeException` subclass — copy verbatim as `TemplateNotFoundException`.

### `domain/template/TemplateMalformedException.java` (model, domain exception carrying detail)

**Analog:** `src/main/java/com/evision/releasemgmt/domain/auth/JiraAuthenticationException.java` (reason-carrying pattern)
```java
public class JiraAuthenticationException extends RuntimeException {

    private final JiraAuthFailureReason reason;

    public JiraAuthenticationException(JiraAuthFailureReason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public JiraAuthFailureReason reason() {
        return reason;
    }
}
```
`TEMPLATE_MALFORMED` needs a human-readable, dynamic `detail` (e.g. "Unclosed section: `{{#items}}`
has no matching `{{/items}}`.") rather than a fixed enum reason — carry a `String detail` field
(and optionally a `String field` naming which chrome field/body failed) the same way this class
carries `reason`, then read it in `GlobalExceptionHandler` instead of switching on an enum.
`RenderBoundExceededException` can stay a plain no-arg exception like
`ReleaseRefreshIncompleteException` since its copy is fixed (Copywriting Contract, "validation took
too long or produced too much content").

### `domain/template/view/*` — the view-model (transform) — **NO ANALOG**

There is no existing type in this codebase that is "a domain object deliberately reshaped and
stripped down for an external consumer that must never see the real aggregate." The closest thing
in spirit is `adapters/web/dto/ReleaseResponse.java` + `ReleaseController.toItemResponse`
(`adapters/web/ReleaseController.java:137-143`), which flattens `ReleaseItem`/`TicketDetail` into a
wire DTO — but that DTO is JSON-serialized for the SPA, carries every field, and has no concept of
"safe to expose to untrusted template authors." The view-model's defining property (PROJECT.md
§Key Decisions: "never a domain record, never a JPA entity") has no precedent to copy. **Design it
fresh** as plain records in `domain/template/view/` (still domain-layer types, since the render
port depends on them and jmustache/flexmark must not leak into `domain`), using `Release.java`'s
"root object" idea only as loose inspiration, not as a structural template. `TicketDetail.java`
(`domain/jira/TicketDetail.java:5-17`) is the field source for `ItemView`'s ticket-backed half, per
D-04-17 — copy its field names/types (`key`, `summary`, `issueType`, `status`, `resolution`,
`assignee`, `reporter`, `description`, `comments`) directly, do not add `component`/`epic`/`label`.

### `ports/TemplateRepository.java` (port, CRUD)

**Analog:** `src/main/java/com/evision/releasemgmt/ports/ReleaseRepository.java`
```java
public interface ReleaseRepository {
    Release create(Release release, List<ReleaseItem> items);
    Optional<Release> findById(UUID id);
    List<ReleaseSummary> search(String query);
    ...
    void delete(UUID releaseId);
}
```
Copy the shape: `create`, `findById`, `findByDocumentType`/`search`, `update`, `delete`,
`setActive(UUID templateId, TemplateDocumentType type)` (the "exactly one active per type" mutation
— see the JPA repository note below on enforcing this at the database level, not just here).
Javadoc `@throws` conventions on the interface (see `addItems`/`updateManualItem` docs at
`ReleaseRepository.java:15-21,44-48`) are the right place to document `TemplateNotFoundException`.

### `ports/RenderEnginePort.java` (port, transform) + adapter

**Analog for the port shape:** `src/main/java/com/evision/releasemgmt/ports/JiraTicketPort.java`
```java
public interface JiraTicketPort {
    List<TicketDetail> searchAll(JiraAccessContext access, String jql, Map<TicketField, String> fieldIds);
}
```
One narrow method, domain types only in the signature — no `JsonNode`, no HTTP types. Model
`RenderEnginePort` the same way: something like
`RenderResult render(String templateBody, ReleaseView context)` returning a domain-level result
(rendered Markdown/HTML + list of unknown-placeholder tokens), never a jmustache/flexmark type.

**Analog for the adapter (external library behind a port):**
`src/main/java/com/evision/releasemgmt/adapters/jira/JiraRestTicketAdapter.java` — a `@Component`
implementing the port, constructor-injecting config via `@Value`, translating library-native
exceptions into domain exceptions (`JiraRestTicketAdapter.java:26-41`, injected `pageSize`/`maxPages`
mirrors how a render timeout/collection cap would be injected). jmustache and flexmark calls belong
in `adapters/render/`, never in `application/` or `domain/` — `ArchitectureBoundaryTest` enforces
this (per CONTEXT.md's Established Patterns note).

**Analog for the bounding pattern specifically:**
`src/main/java/com/evision/releasemgmt/adapters/jira/AdfFlattener.java:7-8,64-74`
```java
private static final int MAX_NODES = 5000;
private static final int MAX_DEPTH = 50;
...
private boolean bounded(StringBuilder out, int depth, int[] state) {
    if (state[1] == 1) {
        return true;
    }
    if (state[0] >= MAX_NODES || depth >= MAX_DEPTH) {
        state[1] = 1;
        out.append("\n[content truncated]");
        return true;
    }
    return false;
}
```
`RenderService`/the render adapter should declare its own named constants (or `@Value`-injected
config, since D-04-11 wants the cap and timeout configurable and shared between save-time
validation and Phase 5 generation) and check them the same "count as you go, stop and flag rather
than throw mid-walk" way. **Also copy the Markdown vocabulary this file already emits**
(`AdfFlattener.java:76-88` — `**strong**`, `*em*`, backtick code, `- ` list items via
`applyMarks`/the `listItem` case) — this is the concrete evidence D-04-04 cites that ticket
descriptions need no conversion before sitting inside a Mustache+Markdown template body.

### `application/RenderService.java` (service, transform, bounded)

**Analog for service shape:** `src/main/java/com/evision/releasemgmt/application/ReleaseService.java`
— constructor-injected port(s), public methods with no HTTP/JPA concerns, private helpers at the
bottom (`fetchTickets`, `distinct`, `blankToNull` at `ReleaseService.java:157-177`). `RenderService`
should follow the same shape: inject `RenderEnginePort`, expose something like
`RenderResult validate(Template template, ReleaseView sampleContext)` used both by save-time
validation (this phase) and Phase 5's document generation (per D-04-11 — "one render service used
by every caller").

**Timeout enforcement:** no existing service in this codebase wraps a call in a wall-clock timeout
— this specific mechanism (likely a bounded `ExecutorService`/`CompletableFuture.orTimeout` around
the render call) has no analog and should be designed fresh, but it belongs at the same layer
`ReleaseService` occupies (`application/`), not inside the adapter, so both save-time validation and
Phase 5 generation inherit it uniformly.

### `application/TemplateService.java` (service, CRUD)

**Analog:** `src/main/java/com/evision/releasemgmt/application/ReleaseService.java` — full CRUD
service shape: `get(id)` throwing the not-found exception (`ReleaseService.java:57-59`), a
`create...` method assembling the aggregate then delegating to the repository
(`ReleaseService.java:44-55`), individual `update.../remove...` methods each re-fetching first for a
guard check. `TemplateService` has no "must be OPEN" guard to repeat (D-04-13), but should still
follow the "load domain object, call its behavior/validation, delegate to repository" shape, and
should call `RenderService.validate(...)` at save time before persisting, recording warnings on the
returned/stored template rather than blocking (D-04-07).

### `adapters/persistence/TemplateRepositoryAdapter.java` (service, persistence adapter, CRUD)

**Analog:** `src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java`

**Duplicate-constraint translation** (`ReleaseRepositoryAdapter.java:46-58`):
```java
@Override
@Transactional
public Release create(Release release, List<ReleaseItem> items) {
    try {
        jpa.saveAndFlush(toEntity(release));
    } catch (DataIntegrityViolationException e) {
        throw new ReleaseDuplicateException();
    }
    insertItems(release.id(), items);
    return load(release.id());
}
```
Same shape for creating a `TemplateEntity` — catch `DataIntegrityViolationException` if template
names must be unique per type (check UI spec: "Duplicate" flow disambiguates collisions
server-side, so this path is exercised).

**Pessimistic locking pattern for "exactly one active per type"** (`ReleaseRepositoryAdapter.java:168-174`):
```java
private ReleaseEntity lockOpen(UUID releaseId) {
    ReleaseEntity entity = jpa.findByIdForUpdate(releaseId).orElseThrow(ReleaseNotFoundException::new);
    if (entity.getStatus() == ReleaseStatus.FROZEN) {
        throw new ReleaseFrozenException();
    }
    return entity;
}
```
D-04-12 says "exactly one active per type" must be a **database invariant** (partial unique index),
not just application logic — so the JPA repository's `setActive` should be a two-step
`UPDATE ... SET active = false WHERE document_type = ? AND active = true` then
`UPDATE ... SET active = true WHERE id = ?` inside one `@Transactional` method, relying on the
partial unique index (see migration section) as the actual guarantee, the same way freeze relies on
`SELECT ... FOR UPDATE` rather than trusting the application not to race.

**`@Transactional` placement, `Component` stereotype, constructor injection of multiple JPA repos**
— copy verbatim from `ReleaseRepositoryAdapter.java:31-44`.

### `adapters/persistence/entity/TemplateEntity.java` (model, JPA entity)

**Analog:** `src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseEntity.java` —
plain `@Entity`/`@Table`, `@Id UUID`, `@Enumerated(EnumType.STRING)` for the document-type column,
getter/setter pairs, no-arg constructor. Copy this exactly; add a `@Lob`/`byte[]` column for the
logo (see Logo section below) and plain `@Column` strings for the six chrome fields.

### `adapters/persistence/repository/TemplateJpaRepository.java` (model, Spring Data repo)

**Analog:** `src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseJpaRepository.java`
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT r FROM ReleaseEntity r WHERE r.id = :id")
Optional<ReleaseEntity> findByIdForUpdate(@Param("id") UUID id);
```
Copy the `@Lock(PESSIMISTIC_WRITE)` + `@Param` idiom if `setActive` needs to lock the row(s) being
flipped. The native-query `search` method (`ReleaseJpaRepository.java:20-40`) is the pattern for any
listing query needing computed columns; a simple `findByDocumentTypeOrderByNameAsc` derived-query
method is closer to what `/templates` needs (no search box per UI spec, so the parameterized
`ILIKE` native query likely isn't needed here — a plain derived query suffices).

### Logo storage (file-I/O) — **partial analog only, report gap**

**Column-conversion shape (transfers):** `adapters/persistence/converter/JsonStringListConverter.java`
(full file) shows the `AttributeConverter` idiom used in this codebase — `@Component @Converter`,
inject `ObjectMapper`, convert both directions. If the logo is stored as a raw `byte[]` column this
converter isn't even needed (JPA maps `byte[]` to `bytea` natively); if it is wrapped in a small
value object (e.g. `LogoAsset(byte[] bytes, String contentType, int width, int height)`), follow
this converter's shape to (de)serialize it, or simply map each field to its own column on
`TemplateEntity` (simpler, avoids a converter entirely — prefer this given "no over-abstraction").

**What does NOT transfer:** content-sniffing (magic-byte detection to distinguish PNG/JPEG from
SVG/anything else, per D-04-16 "validated by content rather than extension"), byte-size caps, and
pixel-dimension caps have **no precedent anywhere in this codebase**. The closest *conceptual*
cousin is `UpdateJiraSiteConfigRequest`'s `@Pattern` regex validation
(`adapters/web/dto/UpdateJiraSiteConfigRequest.java:6-11`) and `JiraSiteConfigService.seedIfAbsent`'s
fail-fast `IllegalStateException` on a malformed configured URL
(`application/JiraSiteConfigService.java:42-47`) — both are "validate untrusted input at the
boundary, reject with a clear reason" in spirit, but neither does binary/image inspection. Design
the content-sniffing validator fresh (e.g. checking PNG's 8-byte magic number and the JPEG SOI
marker `0xFFD8`, then decoding just far enough to read pixel dimensions without fully decompressing
an oversized image) and expose its rejection through the same `ProblemDetail` + `reason` contract as
everything else — `LOGO_INVALID_TYPE` / `LOGO_TOO_LARGE` / `LOGO_DIMENSIONS_EXCEEDED`.
`EncryptedTokenConverter.java` was also examined as a candidate analog for "binary-ish column
handling" but its AES-GCM envelope logic is encryption-specific and irrelevant here — noted only to
rule it out.

### `adapters/web/TemplateController.java` (controller, request-response)

**Analog:** `src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java`

**Full CRUD controller shape** (`ReleaseController.java:35-149`):
```java
@RestController
@RequestMapping("/api/releases")
public class ReleaseController {

    private final ReleaseService service;

    public ReleaseController(ReleaseService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReleaseAddResultResponse create(@Valid @RequestBody CreateReleaseRequest request,
                                           Principal principal) {
        return toAddResult(service.createWithTickets(request.name(), request.version(),
                request.jiraProjectKey(),
                request.ticketKeys() == null ? List.of() : request.ticketKeys(),
                principal.getName()));
    }

    @GetMapping("/{releaseId}")
    public ReleaseResponse get(@PathVariable UUID releaseId) {
        return toResponse(service.get(releaseId));
    }

    @DeleteMapping("/{releaseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID releaseId) {
        service.delete(releaseId);
    }
    ...
    private static ReleaseResponse toResponse(Release release) { ... }
}
```
Map onto `/api/templates`: `POST` (create), `GET` (list, likely `?documentType=`), `GET /{id}`,
`PUT /{id}` (rename — UI spec says rename saves independently on blur), `PUT /{id}/body`,
`PUT /{id}/chrome`, `POST /{id}/activate` (mirrors `POST /{releaseId}/freeze`'s
"principal-authenticated state-change" shape at `ReleaseController.java:84-87`), `POST /{id}/duplicate`,
`DELETE /{id}`, plus logo endpoints (`POST/DELETE /{id}/logo`, multipart). `Principal principal` for
the actor on create/edit is already proven at `ReleaseController.java:47-55` — reuse verbatim
(Phase 01 D-03, per canonical refs). Private static `toResponse`/`toSummaryResponse` mapper methods
at the bottom of the controller (`ReleaseController.java:117-148`) are the exact pattern for
`TemplateResponse`/`TemplateSummaryResponse` construction.

### `adapters/web/dto/*` (request/response DTOs, request-response)

**Analog for request validation:** `adapters/web/dto/CreateReleaseRequest.java` and
`adapters/web/dto/ManualItemRequest.java` — `@NotBlank`, `@Size(max = ...)`, `@Pattern` on record
components, Bean Validation only, nothing custom:
```java
public record ManualItemRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotNull ManualItemCategory category) {
}
```
Chrome field DTOs and body-update DTOs should use the same `@Size` caps and `@NotBlank`/`@NotNull`
combination; no custom validator annotation exists anywhere in this codebase for "well-formed
Mustache", so that check stays a service-layer call into `RenderService`, surfaced as a 4xx via
`TemplateMalformedException`, not a Bean Validation constraint.

**Analog for response DTOs:** `adapters/web/dto/ReleaseResponse.java` — flat record mirroring the
domain aggregate's fields 1:1, `List<ReleaseItemResponse>` for nested collections. Same shape for
`TemplateResponse` (full detail, including body/chrome) and `TemplateSummaryResponse` (list-row
shape: id, name, documentType, active, hasWarnings — matching the list screen's row content).

### `adapters/web/GlobalExceptionHandler.java` (modified)

**Analog:** the file itself — extend it, don't create a parallel scheme. The per-exception handler
method + shared `problem(...)` helper pattern (`GlobalExceptionHandler.java:76-93,170-176`):
```java
@ExceptionHandler(ReleaseNotFoundException.class)
public ProblemDetail handleReleaseNotFound(ReleaseNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "Release not found",
            "This release doesn't exist or has been deleted.", "RELEASE_NOT_FOUND");
}
...
private ProblemDetail problem(HttpStatus status, String title, String detail, String reason) {
    ProblemDetail pd = ProblemDetail.forStatus(status);
    pd.setTitle(title);
    pd.setDetail(detail);
    pd.setProperty("reason", reason);
    return pd;
}
```
Add handlers for `TemplateNotFoundException` (404, `TEMPLATE_NOT_FOUND`), `TemplateMalformedException`
(422 or 400, `TEMPLATE_MALFORMED`, `detail` sourced from the exception's own message/field rather than
a fixed string — this is the one handler in the file that needs to read a dynamic message instead of
returning a canned one), `RenderBoundExceededException` (422, `RENDER_BOUND_EXCEEDED`, fixed copy
matching the UI spec exactly), and the logo-rejection family (`LOGO_INVALID_TYPE`/`LOGO_TOO_LARGE`/
`LOGO_DIMENSIONS_EXCEEDED` — UI-SPEC's Planning Input #1 leaves the single-vs-family choice to the
planner). The `MethodArgumentNotValidException` override (`GlobalExceptionHandler.java:131-144`)
already produces `fieldErrors` for Bean Validation failures on chrome-field size caps — no change
needed there.

### `src/main/resources/db/migration/V4__template_authoring.sql` (migration, batch)

**Analog:** `src/main/resources/db/migration/V3__release_assembly.sql`

**Header-comment-cites-decisions convention** (`V3__release_assembly.sql:1-4`):
```sql
-- The Release aggregate: the first entity this system owns rather than reads from Jira.
-- Ticket items store a full materialised copy of the Jira ticket (D-03-06) so that opening a
-- release never issues a Jira call, and so a frozen release keeps the field values it had at
-- freeze rather than whatever Jira says today.
```
Open `V4` the same way, citing D-04-12/D-04-13/D-04-16.

**CHECK-constraint-for-enum convention** (`V3__release_assembly.sql:10,43-44`):
```sql
status             VARCHAR(16)   NOT NULL CHECK (status IN ('OPEN', 'FROZEN')),
...
category     VARCHAR(32)  NOT NULL
             CHECK (category IN ('CONFIG_CHANGE', 'DATABASE_SCRIPT', 'INFRASTRUCTURE', 'OTHER')),
```
Same for `document_type CHECK (document_type IN ('RELEASE_NOTES', 'RUNBOOK', 'APPROVAL'))`.

**Partial unique index — NEW, no direct precedent in V1-V3, but standard Postgres and directly
required by D-04-12.** `V3` has only a plain `UNIQUE (name, version)` constraint
(`V3__release_assembly.sql:17`); "exactly one active per type" needs:
```sql
CREATE UNIQUE INDEX template_one_active_per_type_idx
    ON template (document_type) WHERE active;
```
This is new syntax for this codebase's migrations but is the direct SQL implementation of
D-04-12's "enforce in the database... rather than only in service code" instruction — call this out
explicitly to the planner since no prior migration models it.

**FK + cascade delete convention** (`V3__release_assembly.sql:22`):
```sql
release_id     UUID          NOT NULL REFERENCES release (id) ON DELETE CASCADE,
```
Not directly needed for `template` (it's a single table, no child rows) unless the logo is split
into its own table — if so, `ON DELETE CASCADE` from `template_logo.template_id -> template.id`
matches this precedent.

### `config/TemplateSeeder.java` (config, batch — starter template seeding)

**Analog:** `src/main/java/com/evision/releasemgmt/config/JiraSiteConfigSeeder.java` (whole file):
```java
@Component
public class JiraSiteConfigSeeder implements ApplicationRunner {

    private final JiraSiteConfigService service;
    private final String defaultBaseUrl;

    public JiraSiteConfigSeeder(JiraSiteConfigService service,
                                 @Value("${app.jira.default-base-url}") String defaultBaseUrl) {
        this.service = service;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        service.seedIfAbsent(defaultBaseUrl);
    }
}
```
And the corresponding `seedIfAbsent` on the service (`application/JiraSiteConfigService.java:42-51`):
```java
public void seedIfAbsent(String baseUrl) {
    if (!JIRA_CLOUD_URL.matcher(baseUrl).matches()) {
        throw new IllegalStateException(...);
    }
    if (repository.find().isEmpty()) {
        repository.save(new JiraSiteConfig(baseUrl, Instant.now(), SEED_ACTOR));
    }
}
```
**This is the confirmed better mechanism over a Flyway `INSERT`** — checked `V1`/`V2`/`V3` for
precedent and none seed rows via SQL; the only existing seeding path in this codebase is
`ApplicationRunner` + an idempotent `seedIfAbsent` guard on the service/repository. Use the same
shape for `TemplateSeeder`: run at startup, check `repository.findByDocumentType(type).isEmpty()`
per document type, and if empty, insert the one hard-coded starter template body/chrome for that
type and mark it active. Unlike the Jira seeder (one row, one config value from `@Value`), this
seeder embeds three full template bodies as Java text blocks or classpath resources — a reasonable
extension of the same pattern, not a different one.

## Frontend Pattern Assignments

### `frontend/src/routes/TemplatesRoute.tsx` (route, request-response)

**Analog:** `frontend/src/routes/ReleasesRoute.tsx` (whole file)

**Loading/error/empty/data state branches** (`ReleasesRoute.tsx:38-140`):
```tsx
{releases.isPending && (
  <div className="mt-6 flex items-center gap-3">
    <span className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600" aria-hidden="true" />
    <p className="text-sm text-gray-600">Loading releases…</p>
  </div>
)}

{problem && (
  <div className="mt-6">
    <p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
      {messageForReason(problem)}
    </p>
    <button type="button" onClick={() => releases.refetch()} className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700">
      Try again
    </button>
  </div>
)}
```
`TemplatesRoute` needs three stacked sections (one per document type) instead of one table — loop
`['RELEASE_NOTES', 'RUNBOOK', 'APPROVAL']` and filter/group the single templates query client-side
(small dataset, per UI spec — no per-section query needed). Reuse the exact loading/error markup;
each section gets its own empty-state branch per the Copywriting Contract instead of one page-level
empty state (D-04-14 guarantees the top-level "no templates at all" case is unreachable on first
run).

### `frontend/src/routes/TemplateDetailRoute.tsx` (route, request-response)

**Analog:** `frontend/src/routes/ReleaseDetailRoute.tsx` (whole file, 554 lines)

**Route param + search-param extraction** (`ReleaseDetailRoute.tsx:42-45`):
```tsx
const { releaseId } = useParams({ from: '/authenticated/releases/$releaseId' });
```
Same for `templateId`.

**`ApiError`/`problem` aggregation across multiple mutations** (`ReleaseDetailRoute.tsx:68-78`):
```tsx
const actionProblem =
  (refresh.isError && refresh.error instanceof ApiError && refresh.error.problem) ||
  (freeze.isError && freeze.error instanceof ApiError && freeze.error.problem) ||
  (remove.isError && remove.error instanceof ApiError && remove.error.problem) ||
  ...
  undefined;
```
`TemplateDetailRoute` will aggregate save/logo-upload/activate/delete mutation errors the same way.

**Pending/loading and not-found early returns** (`ReleaseDetailRoute.tsx:80-120`) — copy verbatim
pattern (spinner block, then a specific `RELEASE_NOT_FOUND`/`TEMPLATE_NOT_FOUND` branch with a link
back to the list, then a generic problem branch with Try again).

**`ConfirmDialog` usage for a destructive + a non-destructive-but-blocking action**
(`ReleaseDetailRoute.tsx:249-276`):
```tsx
<ConfirmDialog
  open={confirming === 'delete'}
  title={`Delete ${data.name} ${data.version}?`}
  body="This permanently deletes the release and its items. This cannot be undone."
  confirmLabel="Delete release"
  confirmClassName="rounded bg-red-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
  pending={remove.isPending}
  onConfirm={() => remove.mutate(data.id, { onSuccess: () => navigate({ to: '/releases' }), onSettled: () => setConfirming(null) })}
  onCancel={() => setConfirming(null)}
/>
```
Reuse `ConfirmDialog` unchanged for both "Delete template?" and "Discard unsaved changes?" — the
`onConfirm`/`onCancel` wiring pattern transfers directly; only title/body/confirmLabel change.

**What does NOT transfer:** the two-pane editor/preview layout, the tab switcher, and the
navigation-blocker for unsaved changes have no precedent in `ReleaseDetailRoute` (that route has no
draft/dirty-state concept — every mutation there is immediate). Build the dirty-tracking and
`beforeLoad`/navigation-blocker logic fresh using TanStack Router's blocker API; `ReleaseDetailRoute`
only supplies the surrounding page shell conventions (header row, banner, problem/loading markup).

### `frontend/src/router.tsx` (modified — route registration)

**Analog:** the file itself (`router.tsx:55-76`):
```tsx
const releasesRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/releases',
  component: ReleasesRoute,
});

const releaseDetailRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/releases/$releaseId',
  validateSearch: (search) => ({ ... }),
  component: ReleaseDetailRoute,
});

const routeTree = rootRoute.addChildren([
  authenticatedRoute.addChildren([dashboardRoute, ticketsRoute, releasesRoute, releaseDetailRoute, settingsRoute]),
  loginRoute,
]);
```
Add `templatesRoute` (`/templates`) and `templateDetailRoute` (`/templates/$templateId`) nested
under `authenticatedRoute` the same way, inserted into `addChildren(...)`.

### `frontend/src/components/AppShell.tsx` (modified — nav entry)

**Analog:** the file itself (`AppShell.tsx:49-55`):
```tsx
<Link
  to="/releases"
  className="text-gray-600 hover:text-gray-900"
  activeProps={{ className: 'font-semibold text-gray-900' }}
>
  Releases
</Link>
```
Insert an identical `<Link to="/templates">Templates</Link>` between the Releases and Settings
links, per UI-SPEC's nav ordering instruction.

### `frontend/src/components/NewTemplateDialog.tsx` (component, dialog + form)

**Analog:** `frontend/src/components/AddToReleaseDialog.tsx`

**`<dialog>` ref + `showModal`/`close` effect** (`AddToReleaseDialog.tsx:37-50`):
```tsx
useEffect(() => {
  const dialog = dialogRef.current;
  if (!dialog) return;
  if (props.open && !dialog.open) {
    setName('');
    ...
    dialog.showModal();
  }
  if (!props.open && dialog.open) dialog.close();
}, [props.open]);
```
**Field-error extraction from `ProblemDetail.fieldErrors`** (`AddToReleaseDialog.tsx:105-116`):
```tsx
const nameError = createProblem?.reason === 'VALIDATION_FAILED' ? createProblem.fieldErrors?.name : undefined;
```
`NewTemplateDialog` is much simpler (one `name` field, document type fixed by which section's "+
New template" was clicked) — copy the reset-on-open effect and the single-field validation-error
pattern, skip the release-picker/filter logic (not needed for a name-only dialog).

### `frontend/src/components/ConfirmDialog.tsx` — reused verbatim

**Analog:** itself, no modification needed (full file, 53 lines) — used for both delete-template and
discard-unsaved-changes confirmations. `confirmClassName` prop already supports the red destructive
styling this phase needs (`bg-red-600` for delete) and a plain-bordered variant (`border-red-600` for
"Discard changes", per Copywriting Contract) — pass different `confirmClassName` strings per use, no
component change required.

### `frontend/src/api/templates.ts` (API client module, CRUD)

**Analog:** `frontend/src/api/releases.ts` (whole file, 235 lines)

**Query-key + `useQuery` pattern** (`releases.ts:66-90`):
```ts
export const releasesQueryKey = (query: string) => ['releases', query] as const;
export const releaseQueryKey = (releaseId: string) => ['release', releaseId] as const;

export function useReleases(query: string) {
  return useQuery({
    queryKey: releasesQueryKey(query),
    queryFn: () => apiFetch<ReleaseSearchResponse>(releaseSearchPath(query)),
  });
}

export function useRelease(releaseId: string) {
  return useQuery({
    queryKey: releaseQueryKey(releaseId),
    queryFn: () => apiFetch<Release>(`/api/releases/${releaseId}`),
    retry: false,
  });
}
```
**Shared mutation helper collapsing repeated `onSuccess` cache-write boilerplate** (`releases.ts:154-163`):
```ts
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
Copy this exact shape as `useTemplateMutation` for save-body/save-chrome/activate/rename mutations
that all return the whole `Template`. Logo upload/remove (D-04-21: takes effect immediately, not
part of the dirty-tracker) is its own `useMutation`, structured like `useDeleteRelease`
(`releases.ts:222-234`) since it also doesn't fit the shared-return-type helper (it returns updated
logo metadata, not necessarily the whole template).

### `frontend/src/api/client.ts` — reused verbatim

**Analog:** itself, no modification — `apiFetch` (`client.ts:62-77`) already handles CSRF header
injection, session-refresh-then-retry, and `INVALID_CREDENTIALS` signaling. All new `templates.ts`
functions call `apiFetch` exactly like `releases.ts` does; no client changes needed unless the logo
upload endpoint needs `multipart/form-data` — in that case, `apiFetch`'s
`if (init.body !== undefined) headers.set('Content-Type', 'application/json')`
(`client.ts:26`) **will need a guard added** so it doesn't stomp a `FormData` body's own
`multipart/form-data; boundary=...` content type. Flag this to the planner as a required, minimal
edit to `client.ts`, not a new client.

## Genuinely New Frontend Components — No Analog

### `frontend/src/components/PlaceholderDrawer.tsx`

No non-modal overlay exists anywhere in this frontend — every existing overlay
(`ConfirmDialog.tsx`, `AddToReleaseDialog.tsx`) is a native `<dialog>` opened with `showModal()`,
which is backdrop-modal and makes the rest of the page inert by design. The placeholder drawer is
explicitly required to be the opposite: non-modal, absolutely positioned, editor stays interactive
underneath. **What transfers from `ConfirmDialog`:** nothing structural — not the `<dialog>` element,
not the `showModal`/`close` ref-effect. **What might transfer:** the `×` dismiss glyph convention
(reused per UI spec) and the `aria-label="Dismiss"` idiom seen on the banner dismiss button in
`ReleaseDetailRoute.tsx:286`. Build this as a plain absolutely-positioned `<div>` with its own
open/close boolean state, Escape-key and outside-click listeners added by hand (no existing hook for
either in this codebase).

### `frontend/src/components/LogoUpload.tsx`

No file-input/drag-and-drop control exists anywhere in this frontend. Build fresh: hidden
`<input type="file">` triggered by a button click, `dragover`/`drop` handlers on the drop-zone
`<div>`, immediate upload via its own mutation (not gated behind the Save button, per UI spec). The
only transferable convention is the in-flight "border-spin + text" loading indicator used everywhere
else in this app (e.g. `ReleaseDetailRoute.tsx:208-216`) and the inline `red-600` rejection-text
placement style used for form field errors elsewhere (e.g. `titleError` at
`ReleaseDetailRoute.tsx:506-510`).

### `frontend/src/components/TemplatePreview.tsx`

No client-side rendering pipeline exists in this frontend today — this is the first component that
takes user-authored text and a data object and produces rendered markup entirely in the browser via
new runtime dependencies (a Mustache-spec JS implementation + CommonMark-conformant Markdown
renderer, per D-04-09 and UI-SPEC's Design System section). No existing component parses or renders
arbitrary text client-side. Package selection and wiring are implementation decisions with no
in-repo precedent to copy; the one binding structural constraint from UI-SPEC is that the rendered
output must be injected into a container scoped with `prose prose-gray max-w-none` (Tailwind
typography plugin) and nothing outside that container.

## Shared Patterns

### Actor from `Principal` on every state change
**Source:** `adapters/web/ReleaseController.java:47-55` (comment explains why: every `/api/**` path
is `authenticated()` in `SecurityConfig`, so `Principal` is guaranteed).
**Apply to:** `TemplateController`'s create/rename/save/activate/duplicate/delete endpoints — Phase
01 D-03 requires the acting user recorded on every state change; `Template` needs `createdBy`/
audit fields the same way `Release` does.

### `ProblemDetail` + machine-readable `reason`
**Source:** `adapters/web/GlobalExceptionHandler.java` (whole-file pattern) — every domain exception
gets its own `@ExceptionHandler` method returning `problem(status, title, detail, reason)`; the SPA
keys all error copy off `reason` (`ApiError.problem.reason`, consumed via `messageForReason` — see
`frontend/src/api/auth.ts`, not read in full but referenced consistently across every route file
read).
**Apply to:** every new backend exception in `domain/template/` and every new frontend
`role="alert" data-reason={problem.reason}` block.

### `apiFetch` as the single HTTP boundary
**Source:** `frontend/src/api/client.ts:62-77`.
**Apply to:** every function in the new `frontend/src/api/templates.ts` — reused unmodified except
for the possible `FormData` content-type guard noted above.

### Bounding/truncation-not-throwing for untrusted content walks
**Source:** `adapters/jira/AdfFlattener.java:7-8,64-74` (`MAX_NODES`/`MAX_DEPTH`, "count as you go,
stop and flag rather than throw mid-walk, append a truncation marker").
**Apply to:** `RenderService`'s collection cap enforcement (D-04-11) — the same "stop iterating,
surface what was already produced, and signal truncation" shape, not a hard exception thrown
mid-render for the *collection cap* case (the *timeout* case is genuinely different — a wall-clock
budget breach there legitimately becomes `RenderBoundExceededException` per D-04-11's "surfaces as a
`ProblemDetail`... not a generic 500").

### `@Component @Converter` for column-level (de)serialization
**Source:** `adapters/persistence/converter/JsonStringListConverter.java` (whole file).
**Apply to:** only if a chrome-fields or logo-metadata value object needs its own column
serialization; a flat set of `@Column` fields on `TemplateEntity` is simpler and preferred unless a
genuine repeated-serialization need appears (per CLAUDE.md's no-over-abstraction rule).

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `domain/template/view/ReleaseView.java` + `ItemView`/`GroupView`/`CommitView`/`PullRequestView` | model (view-model) | transform | No existing type in this codebase is deliberately reshaped/stripped for an untrusted external consumer; `ReleaseResponse`-style DTOs are the nearest *shape* but serve a different purpose (full-fidelity wire format for a trusted SPA, not a minimal safe surface for template authors) |
| `application/TemplateViewAssemblerService.java` | service (transform) | transform | No existing service builds a view-model from a domain aggregate; `ReleaseController`'s private `toResponse` helpers are the nearest shape but live in the web layer and build wire DTOs, not a domain/application-layer view-model |
| Content-sniffing / dimension-cap logo validation | utility | file-I/O | No binary/image validation exists anywhere in this codebase; `EncryptedTokenConverter`/`JsonStringListConverter` are text/string converters, and `UpdateJiraSiteConfigRequest`'s `@Pattern` is regex-only |
| `frontend/src/components/PlaceholderDrawer.tsx` | component (non-modal drawer) | request-response | Every existing overlay in this frontend is a modal `<dialog>`; no absolutely-positioned, non-blocking panel pattern exists to copy |
| `frontend/src/components/LogoUpload.tsx` | component (file upload) | file-I/O | No file-input or drag-and-drop control exists anywhere in this frontend |
| `frontend/src/components/TemplatePreview.tsx` | component (client-side render) | transform | No client-side text-rendering pipeline exists; this is the first frontend feature to add runtime rendering dependencies |

## Metadata

**Analog search scope:** `src/main/java/com/evision/releasemgmt/**` (domain, ports, application,
adapters/{web,persistence,jira}, config), `src/main/resources/db/migration/`,
`frontend/src/{routes,components,api}/`, `frontend/src/router.tsx`.
**Files scanned:** ~40 backend files, ~14 frontend files.
**Pattern extraction date:** 2026-08-06.
