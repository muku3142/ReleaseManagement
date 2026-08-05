# Phase 3: Release Assembly - Pattern Map

**Mapped:** 2026-08-04
**Files analyzed:** 31 (23 backend, 8 frontend/test)
**Analogs found:** 27 / 31 (4 have no direct analog — new-shape items, listed below)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `domain/release/Release.java` | domain (aggregate) | CRUD | `domain/jira/TicketSearchCriteria.java` (record w/ factory methods + business logic) | role-match |
| `domain/release/ReleaseItem.java` | domain (value/entity) | CRUD | `domain/jira/TicketDetail.java` (record, materialised field snapshot) | exact (snapshot shape) |
| `domain/release/ManualItem.java` | domain (value) | CRUD | `domain/jira/TicketDetail.java` | role-match |
| `domain/release/ReleaseStatus.java` (enum: OPEN, FROZEN) | domain (enum) | n/a | `domain/jira/StatusCategory.java` | exact |
| `domain/release/ManualItemCategory.java` (enum) | domain (enum) | n/a | `domain/jira/StatusCategory.java` | exact |
| `domain/release/ReleaseNotFoundException.java` | domain (exception) | n/a | `application/JiraSiteConfigService.JiraSiteNotConfiguredException` (RuntimeException nested/co-located with service) | exact |
| `domain/release/ReleaseFrozenException.java` | domain (exception) | n/a | `domain/auth/JiraAuthenticationException.java` (reason-carrying domain exception) | exact |
| `domain/release/ReleaseDuplicateException.java` | domain (exception) | n/a | `domain/auth/JiraAuthenticationException.java` | role-match |
| `ports/ReleaseRepository.java` | port (interface) | CRUD | `ports/JiraSiteConfigRepository.java` (simple) / `ports/JiraCredentialRepository.java` (richer, multi-op) | exact |
| `application/ReleaseService.java` | service | CRUD + orchestration | `application/JiraSiteConfigService.java` (thin CRUD+exception) + `application/TicketSearchService.java` (orchestrates a port call) | role-match (composite) |
| `adapters/persistence/entity/ReleaseEntity.java` | model (JPA entity) | CRUD | `adapters/persistence/entity/JiraSiteConfigEntity.java` | role-match |
| `adapters/persistence/entity/ReleaseItemEntity.java` (JSONB columns) | model (JPA entity) | CRUD | `adapters/persistence/entity/JiraCredentialEntity.java` (entity with an `@Convert`-mapped column) | role-match (converter pattern only; JSONB itself has **no analog** — see below) |
| `adapters/persistence/entity/ManualItemEntity.java` | model (JPA entity) | CRUD | `adapters/persistence/entity/JiraSiteConfigEntity.java` | role-match |
| `adapters/persistence/converter/StringListJsonConverter.java` (or similar, for JSONB `comments`) | utility (JPA AttributeConverter) | transform | `adapters/persistence/crypto/EncryptedTokenConverter.java` (only existing `AttributeConverter`) | role-match (mechanism only, not JSON) — **no analog for JSON shape, see "No Analog Found"** |
| `adapters/persistence/repository/ReleaseJpaRepository.java` | repository (Spring Data) | CRUD | `adapters/persistence/repository/JiraCredentialJpaRepository.java` (custom `@Query`/`@Modifying`) + `adapters/persistence/repository/JiraSiteConfigJpaRepository.java` (plain `JpaRepository`) | exact (mixed) |
| `adapters/persistence/repository/ReleaseItemJpaRepository.java` | repository (Spring Data) | CRUD | `adapters/persistence/repository/JiraSiteConfigJpaRepository.java` | role-match |
| `adapters/persistence/ReleaseRepositoryAdapter.java` | service (port impl) | CRUD | `adapters/persistence/JiraCredentialRepositoryAdapter.java` (multi-method adapter, `@Transactional` writes) | exact |
| `adapters/web/ReleaseController.java` | controller | request-response (CRUD + search) | `adapters/web/TicketController.java` (POST + service delegation) + `adapters/web/JiraSiteConfigController.java` (GET/PUT + `Principal`) | exact (composite) |
| `adapters/web/dto/ReleaseResponse.java` | DTO | transform | `adapters/web/dto/TicketResponse.java` | exact |
| `adapters/web/dto/ReleaseItemResponse.java` | DTO | transform | `adapters/web/dto/TicketResponse.java` | exact |
| `adapters/web/dto/ManualItemResponse.java` | DTO | transform | `adapters/web/dto/TicketResponse.java` | exact |
| `adapters/web/dto/CreateReleaseRequest.java` | DTO (request, validated) | request-response | `adapters/web/dto/UpdateJiraSiteConfigRequest.java` (simple `@Valid` record) | exact |
| `adapters/web/dto/AddTicketsRequest.java` (create-and-add, D-03-02) | DTO (request, validated) | request-response | `adapters/web/dto/TicketSearchRequest.java` (cross-field `@AssertTrue` validation) | role-match |
| `adapters/web/dto/AddManualItemRequest.java` | DTO (request, validated) | request-response | `adapters/web/dto/UpdateJiraSiteConfigRequest.java` | exact |
| `adapters/web/dto/ReleaseSearchResponse.java` | DTO | transform | `adapters/web/dto/TicketSearchResponse.java` | exact |
| `GlobalExceptionHandler.java` (modified — 3 new `@ExceptionHandler`s) | controller (advice) | request-response | itself, existing `handleJiraSiteNotConfigured`/`handleJiraAuthFailure` blocks | exact |
| `src/main/resources/db/migration/V3__release_assembly.sql` | migration | batch/DDL | `V1__init_schema.sql` + `V2__session_invalidation.sql` | exact |
| `frontend/src/api/releases.ts` | api module (query+mutation hooks) | request-response | `frontend/src/api/tickets.ts` (mutation) + `frontend/src/api/jiraProjects.ts` (query) | exact (composite) |
| `frontend/src/routes/ReleasesRoute.tsx` (`/releases`) | component (route) | request-response | `frontend/src/routes/TicketsRoute.tsx` (table + filter + loading/error/empty states) | role-match |
| `frontend/src/routes/ReleaseDetailRoute.tsx` (`/releases/$releaseId`) | component (route) | request-response | `frontend/src/routes/TicketsRoute.tsx` (expandable ticket table) + `frontend/src/routes/SettingsRoute.tsx` (inline forms w/ fieldErrors) | role-match (composite) |
| `frontend/src/components/AddToReleaseDialog.tsx` (native `<dialog>`) | component | request-response | none existing (first modal in the app) — `frontend/src/routes/SettingsRoute.tsx`'s form pattern for the inline "New release…" sub-form | no direct analog for `<dialog>` — see below |
| `frontend/src/router.tsx` (modified) | route registration | n/a | itself — `ticketsRoute`/`settingsRoute` registration block | exact |
| `frontend/src/components/AppShell.tsx` (modified) | component | n/a | itself — existing `<Link>` nav entries | exact |
| `frontend/src/routes/TicketsRoute.tsx` (modified — add-to-release action bar) | component | request-response | itself — `selected.size` status line at :259-263 | exact |
| `frontend/src/api/auth.ts` (modified — 3 new `REASON_MESSAGES` entries) | utility | transform | itself — existing `REASON_MESSAGES` table | exact |
| Backend test: `ReleaseControllerTest.java` | test | request-response | `src/test/java/.../adapters/web/TicketControllerTest.java` (`@WebMvcTest` + Problem mapping) + `JiraSiteConfigControllerTest.java` (GET/PUT + `Principal`/jwt) | exact |
| Backend test: `ReleasePersistenceIT.java` | test | CRUD | `src/test/java/.../adapters/persistence/JiraSiteConfigPersistenceIT.java` | exact |
| Backend test: `EncryptedTokenConverterTest.java`-equivalent for the JSON converter | test | transform | `src/test/java/.../adapters/persistence/crypto/EncryptedTokenConverterTest.java` | role-match |
| Frontend test: `releases.test.ts` | test | request-response | `frontend/tests/api/tickets.test.ts` | exact |

## Pattern Assignments

### `domain/release/Release.java`, `ReleaseItem.java`, `ManualItem.java`, `ReleaseStatus.java`, `ManualItemCategory.java` (domain, CRUD)

**Analog:** `src/main/java/com/evision/releasemgmt/domain/jira/TicketSearchCriteria.java`, `domain/jira/TicketDetail.java`, `domain/jira/StatusCategory.java`

Domain classes in this codebase are plain records (or enums) with **zero framework imports** — `ArchitectureBoundaryTest.domainAndPortsHaveNoFrameworkImports` enforces this at build time (no `org.springframework..`, `jakarta..`, `org.hibernate..`, `com.fasterxml.jackson..`, `tools.jackson..`). Business rules live as instance methods on the record itself (see `TicketSearchCriteria.toJql()`), and construction happens through **named static factory methods**, not a public canonical constructor used directly by callers:

```java
// domain/jira/TicketSearchCriteria.java:7-25
public record TicketSearchCriteria(String projectId, String sprintId, String jql,
                                   Set<StatusCategory> statusCategories) {

    public static final Set<StatusCategory> DEFAULT_STATUS_CATEGORIES = EnumSet.of(StatusCategory.DONE);

    public static TicketSearchCriteria byProject(String projectId, String sprintId,
                                                 Set<StatusCategory> statusCategories) {
        return new TicketSearchCriteria(projectId, sprintId, null,
                statusCategories == null ? DEFAULT_STATUS_CATEGORIES : statusCategories);
    }

    public static TicketSearchCriteria byJql(String jql) {
        return new TicketSearchCriteria(null, null, jql, Set.of());
    }
```

`Release` will need mutable-feeling state transitions (freeze, add/remove item) that a record can't hold in-place — model these as **methods that return a new `Release`** (records are immutable) or, if mutation-in-place is simpler for the aggregate, this is the one place a plain class (not a record) is justified — precedent: `domain/jira/JiraSiteConfig.java` is a record too, so check its shape before deciding; there is no existing *mutable* domain class to copy from, so pick the record-with-transition-methods style unless the aggregate genuinely needs identity-based mutation.

**Enum pattern** (exact copy target):
```java
// domain/jira/StatusCategory.java — read via Grep, same directory
```
Use the same flat, no-suffix enum style (`OPEN`, `FROZEN` — see `ReleaseStatus`; `CONFIG_CHANGE`, `DATABASE_SCRIPT`, `INFRASTRUCTURE`, `OTHER` — see `ManualItemCategory`, matching D-03-03 exactly).

---

### `domain/release/ReleaseNotFoundException.java`, `ReleaseFrozenException.java`, `ReleaseDuplicateException.java` (domain, exceptions)

**Analog:** `application/JiraSiteConfigService.java:53-54` (nested, no-arg exception) and `domain/auth/JiraAuthenticationException.java` (reason-carrying exception consumed by a `switch` in `GlobalExceptionHandler`)

Two exception shapes exist in the codebase — pick per case:

1. **No-arg, single-meaning exception**, nested as a static class next to the service that throws it:
```java
// application/JiraSiteConfigService.java:53-54
public static class JiraSiteNotConfiguredException extends RuntimeException {
}
```
Use this shape for `ReleaseNotFoundException` (single meaning, no reason enum needed) and `ReleaseDuplicateException` (the `UNIQUE(name, version)` violation — catch the `DataIntegrityViolationException` in the adapter/service and rethrow this).

2. **Reason-carrying exception**, domain-level (not nested), consumed via `switch (ex.reason())` in the handler — copy this shape only if a single class needs to represent multiple related failures (not needed here since each of the three new reasons is its own scenario; prefer three separate no-arg exception classes over inventing a `ReleaseFailureReason` enum for a 1:1 mapping — simpler, matches "no over-abstraction").

---

### `ports/ReleaseRepository.java` (port)

**Analog:** `src/main/java/com/evision/releasemgmt/ports/JiraCredentialRepository.java` (multi-op) — read directly for the exact shape (not yet read line-by-line above, but its adapter was read in full and mirrors this interface 1:1: `findByAccountId`, `upsert`, `findSessionsInvalidatedAt`, `markSessionsInvalidated`).

**Simple analog** (single-purpose):
```java
// ports/JiraSiteConfigRepository.java:7-12
public interface JiraSiteConfigRepository {
    Optional<JiraSiteConfig> find();
    void save(JiraSiteConfig config);
}
```

`ReleaseRepository` needs more surface (create, findById, list/search, save-mutated, delete) — model it after the adapter-side method list actually needed by `ReleaseService`, keeping each method a single verb returning/accepting the domain type, never the JPA entity (ports never import `adapters.persistence.entity..` — `ArchitectureBoundaryTest.domainAndPortsDoNotDependOnAdaptersConfigOrApplication` will fail the build otherwise).

---

### `application/ReleaseService.java` (service, CRUD + orchestration)

**Analog:** `application/JiraSiteConfigService.java` (imports pattern, `Instant.now()` for timestamps, exception-throwing on missing entity) + `application/TicketSearchService.java` (constructor-injects multiple collaborators, delegates the actual I/O to a port)

**Imports pattern** (`application/JiraSiteConfigService.java:1-8`):
```java
import com.evision.releasemgmt.domain.jira.JiraSiteConfig;
import com.evision.releasemgmt.ports.JiraSiteConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.regex.Pattern;
```

**Core CRUD pattern with not-found exception** (`application/JiraSiteConfigService.java:25-33`):
```java
public JiraSiteConfig get() {
    return repository.find().orElseThrow(JiraSiteNotConfiguredException::new);
}

public JiraSiteConfig updateBaseUrl(String baseUrl, String actor) {
    JiraSiteConfig config = new JiraSiteConfig(baseUrl, Instant.now(), actor);
    repository.save(config);
    return config;
}
```

**Constructor-injection of multiple collaborators + a read from another port** (`application/TicketSearchService.java:15-38`):
```java
@Service
public class TicketSearchService {
    private final JiraAccessContextService accessContextService;
    private final FieldMetadataService fieldMetadataService;
    private final JiraTicketPort ticketPort;

    public TicketSearchService(JiraAccessContextService accessContextService,
                               FieldMetadataService fieldMetadataService,
                               JiraTicketPort ticketPort) {
        this.accessContextService = accessContextService;
        this.fieldMetadataService = fieldMetadataService;
        this.ticketPort = ticketPort;
    }

    public List<TicketDetail> search(TicketSearchCriteria criteria) {
        JiraAccessContext access = accessContextService.currentContext();
        Map<TicketField, String> fieldIds = fieldMetadataService.resolve(FIELDS);
        return ticketPort.searchAll(access, criteria.toJql(), fieldIds);
    }
}
```
`ReleaseService.addTickets(...)` (D-03-06 materialisation, D-03-13 idempotent add) is the direct structural equivalent: it takes ticket keys, calls `TicketSearchService`/`JiraTicketPort` to fetch current `TicketDetail`s, and maps each into a `ReleaseItem` snapshot before persisting via `ReleaseRepository`. The "create-and-add is one transactional operation" requirement from D-03-02 means this method (or its caller) needs `@Transactional` — no existing service in this codebase uses `@Transactional` at the *application* layer (only the persistence adapter does, e.g. `JiraCredentialRepositoryAdapter.upsert` at line 44), so place `@Transactional` on `ReleaseRepositoryAdapter`'s combined create-and-add method rather than the application service, matching that precedent.

**Actor plumbing (D-03-12)** — copy from `JiraSiteConfigService.updateBaseUrl(String baseUrl, String actor)`: the actor is a plain `String` parameter supplied by the controller via `CurrentUserPort`/`Principal`, never resolved inside the service.

---

### `adapters/persistence/entity/ReleaseEntity.java`, `ManualItemEntity.java` (JPA entity, plain columns)

**Analog:** `adapters/persistence/entity/JiraSiteConfigEntity.java` (full file, 60 lines — copy the whole shape: `@Entity`/`@Table`, `@Id`, `@Column(name=..., nullable=...)`, no-arg constructor, plain getters/setters, no Lombok anywhere in this codebase):
```java
// adapters/persistence/entity/JiraSiteConfigEntity.java:1-27 (imports + class opening + first two columns)
package com.evision.releasemgmt.adapters.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "jira_site_config")
public class JiraSiteConfigEntity {

    @Id
    private Short id;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;
    ...
```
No Lombok, no `@Data` — every getter/setter is hand-written throughout the codebase; match that.

---

### `adapters/persistence/entity/ReleaseItemEntity.java` (JPA entity with a converter-mapped column — D-03-06's JSONB)

**Analog for the `@Convert` mechanism:** `adapters/persistence/entity/JiraCredentialEntity.java:22-25`
```java
@Column(name = "encrypted_token", nullable = false)
@Convert(converter = EncryptedTokenConverter.class)
private String token;
```

**Analog for the converter class itself:** `adapters/persistence/crypto/EncryptedTokenConverter.java` — full file (98 lines), already read in full. It is a `@Component @Converter implements AttributeConverter<String, String>`, constructor-injected via `@Value`, with `convertToDatabaseColumn`/`convertToEntityAttribute` doing the transform, throwing `IllegalStateException` on failure. **This is the closest analog for the required JSON `AttributeConverter`** (structurally, not semantically — it encrypts, D-03-06's needs to (de)serialize).

**No analog exists for JSON/JSONB itself** — this codebase has never mapped a JSONB column. Recommended approach per CLAUDE.md's stack doc and Jackson-3 caveat (`RESEARCH`/CONTEXT note: "Jackson 3 (`tools.jackson.*`) is Boot 4.1's managed databind — `com.fasterxml.jackson.databind.ObjectMapper` does not resolve"):
- Column: `@Column(name = "description", columnDefinition = "jsonb")` / `@Column(name = "comments", columnDefinition = "jsonb")` for the bulky fields D-03-06 calls out (description as text, comments as `List<String>` need JSON).
- A small `@Component @Converter` implementing `AttributeConverter<List<String>, String>`, following `EncryptedTokenConverter`'s exact skeleton (constructor injection if it needs an `ObjectMapper`, `convertToDatabaseColumn`/`convertToEntityAttribute`, exceptions wrapped as `IllegalStateException` to match the converter's "single exception type contract" comment at line 79-80) but serializing/deserializing via `tools.jackson.databind.ObjectMapper` instead of AES.
- Flyway: declare the column as `JSONB NOT NULL` (see V1 migration pattern below) — Hibernate does not need `@JdbcTypeCode` if the converter round-trips through `String` and the column is genuinely `jsonb` (Postgres will accept a JSON-text string write); confirm during planning whether `@JdbcTypeCode(SqlTypes.JSON)` (native Hibernate 6/7 JSON mapping, no converter needed) is preferred over the converter approach — **planner's call, no existing precedent to defer to**.

**Scalar identity columns** (key, summary, issueType, status, resolution, assignee — D-03-06's suggested shape) map with the plain `JiraSiteConfigEntity` pattern above, not the converter pattern.

---

### `adapters/persistence/repository/ReleaseJpaRepository.java`, `ReleaseItemJpaRepository.java` (Spring Data)

**Plain analog:**
```java
// adapters/persistence/repository/JiraSiteConfigJpaRepository.java (inferred from adapter usage — findById/save only)
public interface JiraSiteConfigJpaRepository extends JpaRepository<JiraSiteConfigEntity, Short> {
}
```

**Custom-query analog** (`adapters/persistence/repository/JiraCredentialJpaRepository.java`, full file, 43 lines) — copy this shape for any bulk/JPQL-update operation (e.g., a bulk "mark refreshed" or the idempotent-add existence check per D-03-13):
```java
@Modifying
@Query("""
        update JiraCredentialEntity e
           set e.jiraEmail = :email, ...
         where e.jiraAccountId = :accountId
        """)
int replaceCredential(@Param("accountId") String accountId, ...);
```
For REL-05's search (`/releases?query=...`), no existing repository does a `LIKE`/substring search — build a derived-query method (`findByNameContainingIgnoreCaseOrVersionContainingIgnoreCase`) or a `@Query` with `ILIKE`; no analog exists for this, treat it as new (Postgres `ILIKE` is idiomatic and simplest — matches "follow standards").

---

### `adapters/persistence/ReleaseRepositoryAdapter.java` (port implementation)

**Analog:** `adapters/persistence/JiraCredentialRepositoryAdapter.java`, full file (70 lines, already read in full) — the multi-method adapter shape to copy: constructor-injects the JPA repository (+ `@Value` config if needed), maps entity ↔ domain in both directions, uses `@Transactional` on the compound write:
```java
// adapters/persistence/JiraCredentialRepositoryAdapter.java:42-57
@Override
@Transactional
public void upsert(String accountId, String email, String apiToken) {
    Instant now = Instant.now();
    if (jpa.replaceCredential(accountId, email, apiToken, (short) activeKeyVersion, now) > 0) {
        return;
    }
    JiraCredentialEntity created = new JiraCredentialEntity();
    created.setJiraAccountId(accountId);
    ...
    jpa.save(created);
}
```
This is the direct analog for D-03-02's "create-and-add is one transactional operation" — put `@Transactional` here, at the adapter, exactly as this codebase already does.

---

### `adapters/web/ReleaseController.java` (controller, request-response)

**Analog A — simple GET/PUT with `Principal`:** `adapters/web/JiraSiteConfigController.java`, full file (42 lines):
```java
@RestController
@RequestMapping("/api/settings/jira-site")
public class JiraSiteConfigController {
    private final JiraSiteConfigService service;

    public JiraSiteConfigController(JiraSiteConfigService service) {
        this.service = service;
    }

    @GetMapping
    public JiraSiteConfigResponse get() {
        return toResponse(service.get());
    }

    @PutMapping
    public JiraSiteConfigResponse update(@Valid @RequestBody UpdateJiraSiteConfigRequest request, Principal principal) {
        // PUT falls under SecurityConfig's /api/** authenticated rule, so Spring Security
        // guarantees a principal by the time this runs
        return toResponse(service.updateBaseUrl(request.baseUrl(), principal.getName()));
    }
    ...
```
This is the exact pattern for D-03-12's actor plumbing (`Principal principal` → `principal.getName()`, no `CurrentUserPort` needed at the controller — it's already resolved by Spring Security).

**Analog B — POST with validated request body and DTO mapping helper:** `adapters/web/TicketController.java`, full file (40 lines):
```java
@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    private final TicketSearchService service;

    @PostMapping("/search")
    public TicketSearchResponse search(@Valid @RequestBody TicketSearchRequest request) {
        TicketSearchCriteria criteria = request.jql() != null && !request.jql().isBlank()
                ? TicketSearchCriteria.byJql(request.jql())
                : TicketSearchCriteria.byProject(request.projectId(), request.sprintId(), request.statusCategories());
        return new TicketSearchResponse(
                service.search(criteria).stream().map(TicketController::toResponse).toList());
    }

    private static TicketResponse toResponse(TicketDetail t) { ... }
}
```
`ReleaseController` needs both shapes: `GET /api/releases` (list/search, like A's GET), `POST /api/releases` (create, like B), `POST /api/releases/{id}/items` (add tickets — D-03-02's create-and-add lives here too, or as a separate `POST /api/releases/create-and-add` — planner's call), `DELETE /api/releases/{id}/items/{itemId}` (remove), `POST /api/releases/{id}/freeze`, `DELETE /api/releases/{id}` — model each endpoint on whichever of A/B is structurally closer; `Principal` for the actor on every mutating endpoint per D-03-12.

---

### `adapters/web/dto/*Response.java`, `*Request.java` (DTOs)

**Response analog** (`adapters/web/dto/TicketResponse.java`, full file):
```java
public record TicketResponse(String key, String summary, String issueType, String status,
                             String resolution, String assignee, String reporter,
                             List<String> fixVersions, String description,
                             List<String> comments, int commentTotal) {
}
```
Flat record, field order matches the domain type, no Jackson annotations needed (Boot 4.1's default record serialization handles it).

**Request validation analog, simple field:** `adapters/web/dto/UpdateJiraSiteConfigRequest.java` (referenced, not re-read — same simple-record-with-`@Valid`-annotations shape as `TicketSearchRequest` below, minus the cross-field check).

**Request validation analog, cross-field `@AssertTrue`:** `adapters/web/dto/TicketSearchRequest.java`, full file:
```java
public record TicketSearchRequest(
        @Pattern(regexp = "^[0-9]+$", message = "must be a numeric Jira project id")
        String projectId,
        ...
        Set<StatusCategory> statusCategories) {

    @AssertTrue(message = "supply either projectId (sprintId optional), or jql - not both")
    public boolean isExactlyOneFilterMode() {
        ...
    }
}
```
Use this shape for `CreateReleaseRequest` (`@NotBlank name`, `@NotBlank version`) and any request needing a cross-field rule (e.g., a combined "create-and-add" request needs both release fields and a non-empty ticket-key list — model the `@AssertTrue` guard on `isExactlyOneFilterMode()`).

---

### `GlobalExceptionHandler.java` (modified)

**Analog:** itself, `adapters/web/GlobalExceptionHandler.java`, full file (114 lines, already read in full) — the exact pattern to extend:
```java
@ExceptionHandler(JiraSiteNotConfiguredException.class)
public ProblemDetail handleJiraSiteNotConfigured(JiraSiteNotConfiguredException ex) {
    return problem(HttpStatus.CONFLICT, "Jira site not configured",
            "No Jira Cloud site is configured yet.", "SITE_NOT_CONFIGURED");
}
```
and the shared helper at the bottom:
```java
private ProblemDetail problem(HttpStatus status, String title, String detail, String reason) {
    ProblemDetail pd = ProblemDetail.forStatus(status);
    pd.setTitle(title);
    pd.setDetail(detail);
    pd.setProperty("reason", reason);
    return pd;
}
```
Add three new `@ExceptionHandler` methods, one per new domain exception:
- `ReleaseNotFoundException` → `problem(HttpStatus.NOT_FOUND, "Release not found", "This release doesn't exist or has been deleted.", "RELEASE_NOT_FOUND")`
- `ReleaseFrozenException` → `problem(HttpStatus.CONFLICT, "Release frozen", "This release is frozen and can no longer be changed.", "RELEASE_FROZEN")`
- `ReleaseDuplicateException` → `problem(HttpStatus.CONFLICT, "Duplicate release", "...", "RELEASE_DUPLICATE")` — copy `MethodArgumentNotValidException`'s `fieldErrors`-population style (lines 85-97) if the duplicate needs to surface as field-scoped rather than top-level, per the UI spec's "inline in the picker's New release… form" requirement; otherwise a plain top-level `detail` string is enough since `messageForReason` on the frontend already formats `{name}`/`{version}` into the message itself (check whether the UI needs the raw name/version back from the ProblemDetail `properties` map, or can reconstruct the message client-side from its own already-known form values — likely the latter, avoiding a fieldErrors dependency here).

No new `JiraAuthFailureReason`-style enum is needed for these three — each maps 1:1 to its own exception type, consistent with "no over-abstraction."

---

### `src/main/resources/db/migration/V3__release_assembly.sql`

**Analog:** `V1__init_schema.sql` (naming/constraint style) + `V2__session_invalidation.sql` (comment style for a non-obvious column), both full files already read:
```sql
-- V1__init_schema.sql
CREATE TABLE jira_site_config (
    id          SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    base_url    VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(255) NOT NULL
);

CREATE TABLE jira_credential (
    jira_account_id  VARCHAR(255) PRIMARY KEY,
    ...
);
```
```sql
-- V2__session_invalidation.sql
-- Logout previously only cleared cookies, so a refresh token captured before sign-out
-- stayed valid for its full TTL and could be chained to the absolute cap. ...
ALTER TABLE jira_credential ADD COLUMN sessions_invalidated_at TIMESTAMPTZ;
```
Conventions to copy exactly: lower_snake_case table/column names, aligned column definitions (padded to a common column for readability), `TIMESTAMPTZ NOT NULL DEFAULT now()` for system timestamps, a leading comment block only when the WHY is non-obvious (matches CLAUDE.md's own comment rule), `PRIMARY KEY` inline, explicit `CHECK` constraints for small closed value sets rather than relying only on the Java enum (D-03-08's `UNIQUE (name, version)` is exactly this kind of DB-level constraint — declare it as `UNIQUE(name, version)` on the `release` table). Use `V3` as the filename number (next after `V2`).

---

### `frontend/src/api/releases.ts` (api module)

**Analog A — mutation hook returning typed response:** `frontend/src/api/tickets.ts`, full file:
```typescript
import { useMutation } from '@tanstack/react-query';
import { apiFetch } from './client';

export function useTicketSearch() {
  return useMutation({
    mutationFn: (criteria: TicketSearchCriteria) =>
      apiFetch<TicketSearchResponse>('/api/tickets/search', {
        method: 'POST',
        body: JSON.stringify(toSearchBody(criteria)),
      }),
  });
}
```

**Analog B — query hook, list + parameterised:** `frontend/src/api/jiraProjects.ts`, full file:
```typescript
export function useJiraProjects() {
  return useQuery({
    queryKey: jiraProjectsQueryKey,
    queryFn: () => apiFetch<JiraProject[]>('/api/jira/projects'),
  });
}

export function useJiraSprints(projectId: string) {
  return useQuery({
    queryKey: ['jira-sprints', projectId] as const,
    queryFn: () => apiFetch<JiraSprint[]>(`/api/jira/projects/${projectId}/sprints`),
    enabled: projectId !== '',
  });
}
```
`releases.ts` needs: `useReleases(query: string)` (query hook, key includes the debounced search string, analog B), `useRelease(releaseId)` (query hook, analog B), `useCreateRelease()` / `useAddTicketsToRelease()` / `useCreateAndAddRelease()` / `useFreezeRelease()` / `useDeleteRelease()` / `useRefreshRelease()` / `useAddManualItem()` / `useRemoveItem()` (mutation hooks, analog A) — each following the exact `apiFetch<ResponseType>(path, { method, body: JSON.stringify(...) })` shape, and each mutation that changes list-visible data should `invalidateQueries` on the releases list key in its `onSuccess`, mirroring how `useLogin`/`useLogout` in `api/auth.ts` call `queryClient.setQueryData`/`queryClient.clear()` (see Shared Patterns below).

---

### `frontend/src/routes/ReleasesRoute.tsx` (`/releases`)

**Analog:** `frontend/src/routes/TicketsRoute.tsx` — copy its loading/error/empty/loaded-table state machine (lines 219-388), its `role="alert" data-reason={...}` + `messageForReason` error pattern (lines 231-244), and its debounced-input pattern is **not present in TicketsRoute** (it triggers on button click) — for the 300ms debounce the UI spec requires, there is no existing debounce hook in the codebase; implement with a plain `useEffect`+`setTimeout` (no new dependency — matches "minimal code for minimal maintenance").

```typescript
// frontend/src/routes/TicketsRoute.tsx:231-244 — error + retry pattern to copy verbatim
{searchProblem && (
  <div className="mt-6">
    <p role="alert" data-reason={searchProblem.reason} className="text-sm text-red-600">
      {messageForReason(searchProblem)}
    </p>
    <button type="button" onClick={runSearch} className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700">
      Try again
    </button>
  </div>
)}
```

---

### `frontend/src/routes/ReleaseDetailRoute.tsx` (`/releases/$releaseId`)

**Analog A — expandable ticket table:** `frontend/src/routes/TicketsRoute.tsx:265-388` — the `Fragment`-per-row + `expandedKey` state + `aria-expanded`/`aria-label` toggle pattern (lines 336-348, 350-382) is a **direct, near-verbatim copy target** for the release detail's Tickets section (UI spec explicitly says "reuses Phase 2's exact pattern").

**Analog B — inline form with fieldErrors, dual independent forms on one screen:** `frontend/src/routes/SettingsRoute.tsx`, full file — copy its two-independent-`useState`-forms-per-screen structure for the "+ Add manual item" disclosure form and its Edit-in-place variant:
```typescript
// SettingsRoute.tsx:23-47 — submit handler + mutation-error-to-field-error mapping
function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSaved(false);
    updateJiraSite.mutate(baseUrl, { onSuccess: () => setSaved(true) });
}
const mutationProblem = updateJiraSite.isError && updateJiraSite.error instanceof ApiError ? updateJiraSite.error.problem : undefined;
const fieldError = mutationProblem?.reason === 'VALIDATION_FAILED' ? mutationProblem.fieldErrors?.baseUrl : undefined;
```

**Route param access** — no existing route in `router.tsx` takes a path param (`/tickets`, `/settings`, `/` are all static). Use TanStack Router's standard `path: '/releases/$releaseId'` + `Route.useParams()` — no in-repo analog, follow the library's own documented pattern (per CLAUDE.md's "follow official documentation" rule).

---

### `frontend/src/components/AddToReleaseDialog.tsx` (native `<dialog>` modal)

**No analog exists** — this is the first `<dialog>` element in the app (UI-SPEC.md explicitly calls this out as new). Use the native `showModal()`/`close()` API directly (no wrapper library — the UI spec is explicit: "This is a browser primitive, not a library"). For the internal list-vs-form content, reuse `TicketsRoute.tsx`'s loading/error list-rendering pattern (analog for the open-releases list) and `SettingsRoute.tsx`'s inline-form pattern (analog for the "New release…" sub-form, including its `fieldError` extraction shown above).

---

### `frontend/src/router.tsx` (modified — add releases routes)

**Analog:** itself, lines 47-56 (the `ticketsRoute` registration) — copy verbatim shape for `releasesRoute` and `releaseDetailRoute`, nested under `authenticatedRoute`:
```typescript
const ticketsRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/tickets',
  component: TicketsRoute,
});
...
const routeTree = rootRoute.addChildren([
  authenticatedRoute.addChildren([dashboardRoute, ticketsRoute, settingsRoute]),
  loginRoute,
]);
```
Add `releasesRoute` (`path: '/releases'`) and `releaseDetailRoute` (`path: '/releases/$releaseId'`, `getParentRoute: () => authenticatedRoute`) to the `authenticatedRoute.addChildren([...])` array.

---

### `frontend/src/components/AppShell.tsx` (modified — add "Releases" nav link)

**Analog:** itself, lines 42-48 (`/tickets` `<Link>`) — copy verbatim between the Tickets and Settings links:
```typescript
<Link to="/tickets" className="text-gray-600 hover:text-gray-900" activeProps={{ className: 'font-semibold text-gray-900' }}>
  Tickets
</Link>
```

---

### `frontend/src/routes/TicketsRoute.tsx` (modified — "Add to release" action bar)

**Analog:** itself, lines 259-263 (existing `{selected.size} selected` block) — the UI spec says to change this exact block into a flex row; copy the surrounding `aria-live="polite"` region unmodified and add the button beside it:
```typescript
// TicketsRoute.tsx:259-263 — current block to wrap in a flex row
{!search.isPending && loaded !== null && loaded.length > 0 && (
  <p role="status" aria-live="polite" className="mt-6 text-sm font-semibold text-blue-600">
    {selected.size} selected
  </p>
)}
```

---

### `frontend/src/api/auth.ts` (modified — 3 new `REASON_MESSAGES` entries)

**Analog:** itself, lines 63-76 (`REASON_MESSAGES` table) — append the three new entries here, per the UI spec's explicit instruction ("All three need entries added to the frontend's `messageForReason` table (`frontend/src/api/auth.ts`)"):
```typescript
const REASON_MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: '...',
  ...
  RESULT_SET_TOO_LARGE: 'This query matched too many tickets to load. Narrow your fix version or JQL filter.',
  // add here: RELEASE_NOT_FOUND, RELEASE_FROZEN, RELEASE_DUPLICATE
};
```
`RELEASE_NOT_FOUND` is handled specially in the UI spec (link to `/releases` instead of "Try again"), so its message string still belongs in this table, but the retry-vs-link branching happens at the call site (`ReleaseDetailRoute.tsx`), not in `messageForReason` itself — same separation of concerns this file already has (`messageForReason` only produces text, callers decide the surrounding affordance).

---

## Shared Patterns

### Authentication / actor resolution
**Source:** `adapters/web/JiraSiteConfigController.java:32-36`
**Apply to:** every mutating `ReleaseController` endpoint (create, add items, remove, freeze, delete, refresh, add/edit manual item)
```java
public JiraSiteConfigResponse update(@Valid @RequestBody UpdateJiraSiteConfigRequest request, Principal principal) {
    // PUT falls under SecurityConfig's /api/** authenticated rule, so Spring Security
    // guarantees a principal by the time this runs
    return toResponse(service.updateBaseUrl(request.baseUrl(), principal.getName()));
}
```

### Error handling / ProblemDetail
**Source:** `adapters/web/GlobalExceptionHandler.java` (full file)
**Apply to:** all new domain exceptions (`ReleaseNotFoundException`, `ReleaseFrozenException`, `ReleaseDuplicateException`) and the `ReleaseController`/DTO validation path (reuses `handleMethodArgumentNotValid` unmodified — no new code needed there)
```java
private ProblemDetail problem(HttpStatus status, String title, String detail, String reason) {
    ProblemDetail pd = ProblemDetail.forStatus(status);
    pd.setTitle(title);
    pd.setDetail(detail);
    pd.setProperty("reason", reason);
    return pd;
}
```

### Frontend error rendering
**Source:** `frontend/src/routes/TicketsRoute.tsx:231-244`, `frontend/src/api/auth.ts:78-84` (`messageForReason`)
**Apply to:** `ReleasesRoute.tsx`, `ReleaseDetailRoute.tsx`, `AddToReleaseDialog.tsx`
```tsx
{someProblem && (
  <p role="alert" data-reason={someProblem.reason} className="text-sm text-red-600">
    {messageForReason(someProblem)}
  </p>
)}
```

### TanStack Query fetch wrapper
**Source:** `frontend/src/api/client.ts` (full file — `apiFetch`, `ApiError`, CSRF header injection, single-flight refresh-then-retry)
**Apply to:** every function in `frontend/src/api/releases.ts` — always call through `apiFetch`, never raw `fetch`.

### JPA entity shape (no Lombok, hand-written accessors)
**Source:** `adapters/persistence/entity/JiraSiteConfigEntity.java` (full file)
**Apply to:** `ReleaseEntity`, `ReleaseItemEntity`, `ManualItemEntity`

### Hexagonal boundary constraints (ArchUnit-enforced)
**Source:** `src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java` (full file, 52 lines, already read in full)
**Apply to:** every new backend file
- `domain/release/**` and `ports/**`: zero imports from `org.springframework..`, `jakarta..`, `org.hibernate..`, `com.fasterxml.jackson..`, `tools.jackson..`, and zero imports from `..adapters..`, `..config..`, `..application..`.
- `application/**` (`ReleaseService`): may not import `..adapters.jira..`, `..adapters.persistence..`, or `..adapters.web..` — only `ports/**` and `domain/**`. (`adapters.security` is exempted but irrelevant to this phase.)
- Concretely: `ReleaseService` depends on `ReleaseRepository` (a port), never on `ReleaseRepositoryAdapter` or `ReleaseJpaRepository` directly.

### `src/test/resources/application.yml` shadow trap
**Source:** CONTEXT.md's own "Established Patterns" note — confirmed structurally by the existence of both `src/main/resources/application.yml` and (implicitly, per the persistence IT depending on `${app.jira.default-base-url}`) a mirrored test config.
**Apply to:** any new config property this phase introduces (e.g., a JSON-converter `ObjectMapper` bean name, or none if no new property is needed — most likely this phase needs zero new config properties since JSONB needs no secret key, unlike `EncryptedTokenConverter`).

---

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| JSONB `AttributeConverter` for `List<String>`/rich-text columns (D-03-06) | utility | transform | No JSONB or Jackson-`AttributeConverter` precedent exists anywhere in the codebase — `EncryptedTokenConverter` is structurally the closest (same `AttributeConverter<X,String>` mechanism) but semantically unrelated (encryption, not serialization). Planner must decide converter-based vs. native `@JdbcTypeCode(SqlTypes.JSON)` mapping — no prior decision to defer to. |
| `frontend/src/components/AddToReleaseDialog.tsx` (native `<dialog>`) | component | request-response | First interrupting/modal flow in the app — Phase 1/2 built none. UI-SPEC.md is explicit this is new. Use the browser's native `showModal()`/`close()` API per the spec; no wrapper component exists to copy. |
| Route with a path parameter (`/releases/$releaseId`) | route | request-response | All three existing routes (`/`, `/tickets`, `/settings`) are static paths — no `Route.useParams()` precedent in this codebase. Follow TanStack Router's own documented dynamic-segment convention. |
| Debounced search input (300ms, `/releases` search box) | component (hook) | request-response | `TicketsRoute.tsx`'s search is button-triggered, not debounced. No debounce utility exists in the codebase; implement inline with `useEffect`/`setTimeout`, no new dependency. |

## Metadata

**Analog search scope:** `src/main/java/com/evision/releasemgmt/**`, `src/test/java/com/evision/releasemgmt/**`, `src/main/resources/db/migration/**`, `frontend/src/**`, `frontend/tests/**`
**Files scanned:** ~65 backend Java files (all production + all test), 2 Flyway migrations, 16 frontend source/test files
**Pattern extraction date:** 2026-08-04
