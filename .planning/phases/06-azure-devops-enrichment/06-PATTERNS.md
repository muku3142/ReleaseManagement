# Phase 6: Azure DevOps Enrichment - Pattern Map

**Mapped:** 2026-10-12
**Files analyzed:** 38 (new and modified, backend + frontend + tests)
**Analogs found:** 36 / 38 (no analog: fan-out with deadline is only partly covered, and the ADO throttle gate)

Paths are relative to `D:\IdeaProjects\ReleaseManagement`. `B` = `src/main/java/com/evision/releasemgmt`,
`T` = `src/test/java/com/evision/releasemgmt`, `F` = `frontend`.

Migration numbering: latest is `V7__template_user_default.sql`. Phase 6 takes `V8` (and `V9` if split). `07-CONTEXT.md:29-30` says
Phase 7 "uses the next free number after Phase 6's migrations" and never hard-codes `V8`, so Phase 7 needs no edit. Run
`ls src/main/resources/db/migration` at each plan start.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/resources/db/migration/V8__ado_enrichment.sql` (new) | migration | CRUD | `V1__init_schema.sql` (`jira_site_config`, `jira_credential`) + `V3__release_assembly.sql` (`release_ticket_item`) | exact |
| `B/domain/enrichment/*` (new: status/reason enums, `ItemEnrichment`, `LinkedPullRequest`, `LinkedCommit`, `StatusChange`, `DevStatusLinks`, `AdoRepoRef`, `TicketBranchRule`) | domain model | transform | `domain/jira/StatusCategory`, `domain/jira/TicketDetail`, `domain/auth/JiraAuthFailureReason` | role-match |
| `B/ports/JiraDevStatusPort.java`, `JiraIssueHistoryPort.java`, `AdoRepositoryPort.java`, `AdoCredentialRepository.java` (new) | port | request-response / CRUD | `ports/JiraCredentialRepository.java`, `ports/JiraFieldMetadataPort.java` | exact |
| `B/adapters/jira/devstatus/JiraDevStatusAdapter.java` (promote from `JiraDevStatusSpikeAdapter`) | adapter | request-response | `JiraDevStatusSpikeAdapter.java` | exact |
| `B/adapters/jira/JiraIssueHistoryAdapter.java` (new) | adapter | request-response (paged) | `JiraDevStatusSpikeAdapter.get` + `JiraRestTicketAdapter` paging loop | role-match |
| `B/adapters/ado/AdoRestAdapter.java` (new) | adapter | request-response (paged) | `JiraDevStatusSpikeAdapter.java` | role-match |
| `B/config/AdoRestClientConfig.java` (new) | config | n/a | `config/JiraRestClientConfig.java` | exact |
| `B/application/EnrichmentService.java` (new) | service | batch / fan-out | `application/RenderService.java` (virtual-thread executor + semaphore) | partial |
| `B/application/AdoCredentialService.java` (new) | service | CRUD | `application/JiraSiteConfigService` + `JiraCredentialRepositoryAdapter.upsert` | role-match |
| `B/application/FieldMetadataService` -> new `StatusCategoryCache` inside history path | service cache | request-response | `application/FieldMetadataService.java` volatile cache | exact |
| `B/adapters/persistence/entity/AdoCredentialEntity.java` (new) | model | CRUD | `entity/JiraCredentialEntity.java` + `entity/JiraSiteConfigEntity.java` | exact |
| `B/adapters/persistence/repository/AdoCredentialJpaRepository.java` (new) | repository | CRUD | `repository/JiraCredentialJpaRepository.java` | exact |
| `B/adapters/persistence/AdoCredentialRepositoryAdapter.java` (new) | adapter | CRUD | `JiraCredentialRepositoryAdapter.java` | exact |
| `B/adapters/persistence/entity/ReleaseItemPullRequestEntity.java`, `ReleaseItemCommitEntity.java` (new) | model | CRUD | `entity/ReleaseTicketItemEntity` (child of release, cascade) | role-match |
| `B/adapters/persistence/entity/ReleaseTicketItemEntity.java` (modify) | model | CRUD | itself | exact |
| `B/adapters/persistence/ReleaseRepositoryAdapter.java` (modify: `saveEnrichment`, `overwriteSnapshot`, `toDomain`) | adapter | CRUD | itself (`replaceItemSnapshots`, `lockOpen`) | exact |
| `B/ports/ReleaseRepository.java` (modify) | port | CRUD | itself | exact |
| `B/domain/jira/TicketDetail.java` + `adapters/jira/JiraRestTicketAdapter.toTicketDetail` (modify) | model + adapter | request-response | themselves | exact |
| `B/application/ReleaseService.java` (modify: `refresh`, `createWithTickets`, `addTickets`, new `refreshCodeLinks`) | service | request-response | itself (`refresh`, lines 99-113) | exact |
| `B/application/ReleaseViewAssembler.java` (modify `mapTicketItem`) | service | transform | itself (lines 112-130) | exact |
| `B/domain/template/PlaceholderCatalog.java` (modify lines 70-111) | config/catalog | n/a | itself | exact |
| `B/domain/template/view/SampleReleaseView.java` (modify) | utility | transform | itself | exact |
| `B/domain/template/view/CommitView.java`, `PullRequestView.java` (comment-only edit) | model | transform | themselves | exact |
| `B/adapters/web/AdoCredentialController.java` + DTOs (new) | controller | request-response | `adapters/web/JiraSiteConfigController.java` | exact |
| `B/adapters/web/ReleaseController.java` (modify: `POST /{id}/code-links/refresh`) | controller | request-response | itself (`/refresh`, `/freeze`) | exact |
| `B/adapters/web/dto/ReleaseItemResponse.java` (modify: nested `codeLinks`) | DTO | request-response | itself | exact |
| `B/adapters/web/GlobalExceptionHandler.java` (modify) | exception handler | request-response | itself (`problem()`) | exact |
| `src/main/resources/application.yml` AND `src/test/resources/application.yml` (modify) | config | n/a | `app.jira.*` block (main:32-38, test:12-18) | exact |
| `src/test/resources/fixtures/*` (new redacted ado/jira fixtures) | test fixture | n/a | `fixtures/devstatus-cnv-2896-*.json` | exact |
| `T/adapters/jira/devstatus/JiraDevStatusAdapterTest.java` (rename of Spike test) | test (WireMock) | request-response | `JiraDevStatusSpikeAdapterTest.java` | exact |
| `T/adapters/ado/AdoRestAdapterTest.java`, `T/adapters/jira/JiraIssueHistoryAdapterTest.java` (new) | test (WireMock) | request-response | `JiraDevStatusSpikeAdapterTest.java` | exact |
| `T/adapters/persistence/AdoCredentialPersistenceIT.java` (new) | test (Testcontainers IT) | CRUD | `JiraCredentialPersistenceIT.java` | exact |
| `T/application/EnrichmentServiceTest.java`, `T/domain/enrichment/TicketBranchRuleTest.java`, `StatusHistoryTest.java` (new) | test (unit) | transform | `T/application/FieldMetadataServiceTest.java` | role-match |
| `T/application/ReleaseViewAssemblerTest.java`, `T/domain/template/PlaceholderCatalogTest.java` (modify) | test | transform | themselves | exact |
| `F/src/api/adoCredential.ts`, `F/src/components/AdoCredentialBanner.tsx` (new) | hook + component | request-response | `F/src/api/jiraSite.ts`, `F/src/components/CredentialAlert.tsx` | role-match |
| `F/src/routes/SettingsRoute.tsx` (modify), `ReleaseDetailRoute.tsx` (modify), `F/src/api/releases.ts` (modify), `PlaceholderDrawer.tsx` (modify) | component | request-response | `SettingsRoute.tsx` sections | exact |

## Pattern Assignments

### `V8__ado_enrichment.sql` (migration, CRUD)

**Analog:** `V1__init_schema.sql:1-6` (singleton-row table) and `V3__release_assembly.sql:20-36` (`release_ticket_item`, FK + cascade, named UNIQUE).

**Singleton row** (V1 lines 1-6; `ado_credential` copies this shape and adds `encrypted_token TEXT NOT NULL, key_version SMALLINT NOT NULL` from V1 `jira_credential` lines 8-12):
```sql
CREATE TABLE jira_site_config (
    id          SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    base_url    VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(255) NOT NULL
);
```

**Child table pattern** (V3 lines 20-36): `release_id UUID NOT NULL REFERENCES release (id) ON DELETE CASCADE` plus a named `CONSTRAINT ..._unique UNIQUE (...)`. Child tables `release_item_pull_request` and
`release_item_commit` follow the same shape with `item_id ... REFERENCES release_ticket_item (id) ON DELETE CASCADE`. Take the DDL sketch from `06-RESEARCH.md` "Persistence Design"
as the column list. Use `CHECK (... IN (...))` for enum columns, as V3/V7 do (`V7` header comments cite decision IDs; do the same with D-06-xx).

Header-comment convention from V7: cite decision IDs and say why a constraint lives in SQL.

---

### `B/adapters/ado/AdoRestAdapter.java` + `B/config/AdoRestClientConfig.java` (adapter + config, request-response)

**Analog (config, exact copy):** `B/config/JiraRestClientConfig.java:12-25`. The `HttpClient` is built without `followRedirects`, so it keeps `Redirect.NEVER`. A bad ADO PAT returns **302** (capture 2026-10-12), so enabling redirects would turn it into a 200 HTML page.
```java
@Configuration
public class JiraRestClientConfig {

    @Bean
    RestClient jiraRestClient(@Value("${app.jira.connect-timeout}") Duration connectTimeout,
                              @Value("${app.jira.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
```
Bean must be named `adoRestClient` with `app.ado.connect-timeout` / `app.ado.read-timeout`. Existing constructors inject by parameter name `jiraRestClient`; keep bean and parameter names explicit.
Do NOT add `.followRedirects(...)`, and add a comment saying why.

**Analog (adapter):** `B/adapters/jira/devstatus/JiraDevStatusSpikeAdapter.java:48-71` (Basic header, `.retrieve().body(JsonNode.class)`, exception cascade mapped to a domain reason).
```java
private JsonNode get(JiraAccessContext access, String uriTemplate, Object... uriVariables) {
    String basic = Base64.getEncoder().encodeToString(
            (access.email() + ":" + access.apiToken()).getBytes(StandardCharsets.UTF_8));
    try {
        return restClient.get()
                .uri(uriTemplate, uriVariables)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(JsonNode.class);
    } catch (HttpClientErrorException.Unauthorized e) {
        throw new JiraTicketReadException(JiraAuthFailureReason.INVALID_CREDENTIALS);
    } catch (HttpClientErrorException.TooManyRequests e) { ... }
    } catch (HttpServerErrorException e) { ... }
    } catch (ResourceAccessException e) { ... }
    } catch (RestClientException e) { ... }
}
```
Differences for ADO (from RESEARCH "Code Examples"): Basic value is `":" + pat` (empty username); add `.onStatus(s -> s.is3xxRedirection() || s.value() == 203 || 401 || 403, ...)` throwing an ADO-unauthorized exception *before* the body is decoded (Spring's default handler ignores 3xx, so the HTML body would otherwise reach Jackson); use `.toEntity(JsonNode.class)` to read the `x-ms-continuationtoken` header; send an explicit `$top=100` and a page ceiling. Never GET a URL taken from dev-status; build from validated `(org, project, repositoryId, prId)` against host `dev.azure.com`. Never put the PAT in an exception message.
Imports to copy: lines 3-17 of the spike adapter (`HttpHeaders`, `MediaType`, `RestClient`, `tools.jackson.databind.JsonNode`, `StandardCharsets`, `Base64`). Note Jackson 3 package `tools.jackson.databind`, and `JsonNode.asString()` (not `asText`) in the spike; `JiraRestTicketAdapter` uses `asText(null)`. Both exist; follow the file you copy from.

---

### `B/adapters/jira/devstatus/JiraDevStatusAdapter.java` (adapter, request-response; promoted from the spike)

**Analog:** `JiraDevStatusSpikeAdapter.java` itself (lines 19-46). Keep the class comment (line 19: `/rest/dev-status/* is undocumented ...`). Keep the injection `RestClient jiraRestClient` (parameter name matters). `resolveNumericIssueId` is no longer needed (A4: search carries `id`), so delete it rather than keep dead code. Keep the numeric-id comment, moved to the detail method. Adapter must map `JsonNode` to domain `DevStatusLinks`, so no `JsonNode` crosses the port (`ArchitectureBoundaryTest`).

Summary call shape: `GET {site}/rest/dev-status/1.0/issue/summary?issueId={id}`; check `summary.pullrequest|repository|branch.byInstanceType` for the key `AzureRepos` (fixture `devstatus-cnv-2896-summary.json`: `summary.pullrequest.byInstanceType.AzureRepos`). Envelope guard per RESEARCH call plan step 3 (`detail` array, `_instance.type == "AzureRepos"`), failing to `DEV_STATUS_UNRECOGNISED`.

Timestamps: dev-status/changelog use `yyyy-MM-dd'T'HH:mm:ss.SSSxx` (`+0300`, `+0000`, no colon). Share one formatter constant; ADO dates parse with `Instant.parse`.

---

### `B/adapters/jira/JiraIssueHistoryAdapter.java` (adapter, paged request-response)

**Analog:** `B/adapters/jira/JiraRestTicketAdapter.java:60-95` for paging termination and the full error cascade (reuse verbatim, including the 429 rule: no backoff in the adapter).
```java
pageToken = page.nextPageToken();
if (++pagesFetched >= maxPages && pageToken != null && !pageToken.isBlank()) {
    throw new JiraTicketReadException(JiraAuthFailureReason.RESULT_SET_TOO_LARGE);
}
} while (pageToken != null && !pageToken.isBlank());
...
} catch (HttpClientErrorException.TooManyRequests e) {
    // Surfaced immediately, deliberately with no backoff loop of any kind ...
    throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_RATE_LIMITED);
```
The changelog is `startAt`-based, not token-based: loop `startAt += values.size()`, stop on `isLast`, an empty page, or `startAt >= total`, with a page ceiling (RESEARCH "Jira Cloud"). Page ceiling property follows the existing `app.jira.search.max-pages` style. Map status items by `fieldId == "status"`, using `from`/`to` ids; category by **id**, never by name.

---

### `B/application/EnrichmentService.java` (service, batch fan-out)

**Analog (partial):** `B/application/RenderService.java` (virtual-thread executor + `Semaphore`; read it in the plan task, it was not re-read here). RESEARCH Pattern 1 and Pattern 2 give the I/O adaptation: resolve `JiraAccessContext` once on the request thread; workers receive the value; no worker touches the DB or SecurityContext; every worker body catches `RuntimeException` and returns an `ItemEnrichment` with a reason; persist once afterwards.

**Error-reason mapping pattern** (from `JiraAuthFailureReason` use in the adapters above): map `JiraTicketReadException.reason()` to the new `EnrichmentReason` enum. It is its own enum (D-06-02), not an extension of `JiraAuthFailureReason`.

**Core rules to copy from CONTEXT/RESEARCH, not from code:** ADO failures never produce `FAILED` (they set `incomplete` + reason on a `LINKED` item); branch-only is `NO_LINK` (D-06-10); counted set = own-branch PR commits UNION key-tagged commits, de-duplicated by sha (D-06-11); a failed re-fetch keeps old rows and flips to `FAILED` (D-06-12).

---

### `B/application/` status-category cache (cache, request-response)

**Analog:** `B/application/FieldMetadataService.java:27, 89-114`. Copy the `volatile` field, build-then-assign refresh, lazy fill, and the "refresh once when a key is missing" loop.
```java
private volatile Map<String, List<JiraField>> fieldsByLowerCasedName;
...
public void invalidate() { fieldsByLowerCasedName = null; }

private Map<String, List<JiraField>> currentCache() {
    Map<String, List<JiraField>> cache = fieldsByLowerCasedName;
    if (cache == null) { cache = refreshCache(); }
    return cache;
}

private Map<String, List<JiraField>> refreshCache() {
    Map<String, List<JiraField>> built = buildCache();
    fieldsByLowerCasedName = built;
    return built;
}
```
Differences: key is status **id** (`Map<String, StatusCategory>`), not name; a historical id still missing after one refresh maps to `UNKNOWN` and makes history `UNAVAILABLE` (Pitfall 6). Note the cache is built from the *calling user's* credential (`accessContextService.currentContext()` in `buildCache`); in worker threads pass the already-resolved `JiraAccessContext` instead, because the SecurityContext is not inherited (Pattern 1). The project-scoped statuses visible differ per user; acceptable per RESEARCH.

---

### `AdoCredentialEntity` / `AdoCredentialJpaRepository` / `AdoCredentialRepositoryAdapter` (persistence, CRUD, non-decrypting)

**Entity analog:** `JiraCredentialEntity.java:12-37` for the converter annotation, and `JiraSiteConfigEntity.java:12-27` for the singleton `Short id`.
```java
@Column(name = "encrypted_token", nullable = false)
@Convert(converter = EncryptedTokenConverter.class)
private String token;

@Column(name = "key_version", nullable = false)
private short keyVersion;
```
Adds `organization`, `expiresOn` (`LocalDate`), `updatedAt`, `updatedBy`.

**Repository analog:** `JiraCredentialJpaRepository.java:20-56`. Class Javadoc (lines 14-19) states the rule: write and metadata paths must never load the entity. Copy the `@Modifying @Query` update, the scalar `@Query`, and the interface projection.
```java
@Modifying
@Query("""
        update JiraCredentialEntity e
           set e.jiraEmail = :email, e.token = :token, e.keyVersion = :keyVersion, e.updatedAt = :now, ...
         where e.jiraAccountId = :accountId
        """)
int replaceCredential(...);

/** A Spring Data interface projection: two scalar columns, no entity, so no converter runs. */
interface AccountEmail { String getAccountId(); String getEmail(); }
```
For ADO: `where e.id = 1`; projection `AdoCredentialStatus { organization, expiresOn, updatedAt, updatedBy }`; a second JPQL `update ... set e.expiresOn` for expiry-only changes.

**Adapter analog:** `JiraCredentialRepositoryAdapter.java:42-57` (update-then-insert-if-zero, with the Javadoc explaining why).
```java
@Override
@Transactional
public void upsert(String accountId, String email, String apiToken) {
    Instant now = Instant.now();
    if (jpa.replaceCredential(accountId, email, apiToken, (short) activeKeyVersion, now) > 0) {
        return;
    }
    JiraCredentialEntity created = new JiraCredentialEntity();
    ...
    jpa.save(created);
}
```
Constructor takes `@Value("${app.credential.key-version}") int activeKeyVersion` (lines 20-24). Use-time read (`findForUse`) is the only path that decrypts; catch `IllegalStateException` from decrypt in the caller and degrade to Jira-only with `ADO_CREDENTIAL_UNREADABLE`. Do NOT reuse `JiraSiteConfigRepositoryAdapter.save` (findById + mutate).

Architecture rule: web controllers must not depend on a token-bearing port; the status controller uses the projection-only method.

---

### `B/adapters/web/AdoCredentialController.java` (controller, request-response)

**Analog:** `B/adapters/web/JiraSiteConfigController.java:1-50`.
```java
@RestController
@RequestMapping("/api/settings/jira-site")
public class JiraSiteConfigController {
    @GetMapping
    public JiraSiteConfigResponse get() { return toResponse(service.get()); }

    @PutMapping
    public JiraSiteConfigResponse update(@Valid @RequestBody UpdateJiraSiteConfigRequest request, Principal principal) {
        // PUT falls under SecurityConfig's /api/** authenticated rule, so Spring Security
        // guarantees a principal by the time this runs
        return toResponse(service.updateBaseUrl(request.baseUrl(), principal.getName()));
    }

    private JiraSiteConfigResponse toResponse(JiraSiteConfig config) {
        ResolvedActors actors = actorResolution.resolve(config.updatedBy());
        return new JiraSiteConfigResponse(config.baseUrl(), config.updatedAt(), actors.display(config.updatedBy()));
    }
}
```
Route `/api/settings/azure-devops`. Request DTO analog: `dto/UpdateJiraSiteConfigRequest.java` and `dto/ReplaceJiraTokenRequest.java` (Bean Validation: org regex `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`, token non-blank, `expiresOn` not in the past). Response never carries the token; includes computed `state` OK / EXPIRING (<= 14 days) / EXPIRED, computed on read. Resolve `updatedBy` through `ActorResolutionService` like the analog. PUT saves only after the validating `GET {org}/_apis/git/repositories` succeeds; a rejected token returns a ProblemDetail with a machine-readable `reason` and writes nothing.

---

### `ReleaseRepositoryAdapter` (modify: `saveEnrichment`, snapshot columns)

**Analog:** itself. `replaceItemSnapshots` (lines 106-118) and `lockOpen` (lines 181-191).
```java
@Override
@Transactional
public Release replaceItemSnapshots(UUID releaseId, List<ReleaseItem> items, Instant refreshedAt) {
    ReleaseEntity release = lockOpen(releaseId);
    for (ReleaseItem item : items) {
        itemJpa.findByReleaseIdAndTicketKey(releaseId, item.ticketKey())
                .ifPresent(entity -> overwriteSnapshot(entity, item.ticket()));
    }
    release.setLastRefreshedAt(refreshedAt);
    stampItemsChanged(release);
    jpa.save(release);
    return load(releaseId);
}

private ReleaseEntity lockOpen(UUID releaseId) {
    ReleaseEntity entity = jpa.findByIdForUpdate(releaseId).orElseThrow(ReleaseNotFoundException::new);
    if (entity.getStatus() == ReleaseStatus.FROZEN) { throw new ReleaseFrozenException(); }
    return entity;
}
```
`saveEnrichment(releaseId, results, boolean stampOnChange)` must: call `lockOpen` first inside one `@Transactional` method (never hold a transaction across HTTP); delete+insert child rows per item (keep prior rows when the result is `FAILED`, D-06-12); stamp via `stampItemsChanged(release)` (line 200) **only** when the document-visible fingerprint differs on the standalone action (Pitfall 4: `FAILED -> NO_LINK` does not stamp, `LINKED -> FAILED` does); never stamp on `attempted_at`, reason, history or dates alone.
`overwriteSnapshot` (lines 230-241) gains `setJiraIssueId`, `setJiraUpdatedAt`, `setJiraStatusCategory`, and must never touch `history_synced_updated_at`. The existing `itemJpa` repository (`findByReleaseIdAndTicketKey`) is the lookup.
Note: the inline comment at lines 181-184 explains why the service pre-check is not enough; reuse its reasoning for the `ReleaseFrozenException` swallow in the best-effort path.

---

### `B/application/ReleaseService.java` (modify: refresh, create, add, `refreshCodeLinks`)

**Analog:** itself, `refresh` lines 99-113.
```java
public Release refresh(UUID releaseId) {
    Release release = get(releaseId);
    release.requireOpen();
    List<String> keys = repository.findTicketKeys(releaseId);
    Instant now = Instant.now();
    List<ReleaseItem> refreshed = fetchTickets(keys).stream().map(ticket -> new ReleaseItem(ticket, now)).toList();
    if (refreshed.size() != keys.size()) {
        throw new ReleaseRefreshIncompleteException();
    }
    return repository.replaceItemSnapshots(releaseId, refreshed, now);
}
```
Keep that path untouched; after `replaceItemSnapshots`, call `enrichmentService.enrich(...)` in its own `try { } catch (Exception e) { log }` and return `get(releaseId)` (D-06-04: enrichment must not reuse `ReleaseRefreshIncompleteException`). The standalone action starts with `get(releaseId).requireOpen()` like `removeTicket` (line 94-95) and surfaces `ReleaseFrozenException` as the normal 409.

---

### `B/adapters/web/ReleaseController.java` (modify)

**Analog:** itself, lines 90-97.
```java
@PostMapping("/{releaseId}/refresh")
public ReleaseResponse refresh(@PathVariable UUID releaseId) {
    return toResponse(service.refresh(releaseId));
}
```
Add `POST /{releaseId}/code-links/refresh` with an optional ticket-key parameter for per-item retry (RESEARCH Open Question 6). `ReleaseItemResponse` gains a nested `codeLinks` record; build it in `toResponse`, copying the existing item mapping.

---

### `B/application/ReleaseViewAssembler.java` (modify `mapTicketItem`, transform)

**Analog:** itself, lines 108-130 (hard-coded `List.of(), List.of()` at the end, comment at line 112 "always empty until Phase 6 populates them (D-04-18)").
```java
List<CommentView> comments = ticket.comments().stream().map(CommentView::new).toList();
// commits/pullRequests are always empty until Phase 6 populates them (D-04-18).
return new ItemView(nullToEmpty(ticket.summary()), false, ... , "", comments, List.of(), List.of());
```
Replace the two trailing lists with mapped views only when `enrichment_status == LINKED` (D-06-07: `FAILED` renders like `NO_LINK`; no failure flag to templates). `CommitView(id, message, author, url)`: `id` = first 7 chars of sha, message first line, truncated; order by `authored_at` then sha; PRs by numeric id. Remove the stale comment. Use `nullToEmpty` for nullable author. Do not add components to `CommitView` / `PullRequestView` (keeps `PlaceholderCatalogTest` bidirectional tests green); only edit their "always empty until Phase 6" comments (`CommitView.java:3-4`, `PullRequestView.java:3`).

---

### `PlaceholderCatalog.java` (modify) and `PlaceholderCatalogTest` (modify)

**Analog:** itself, lines 70-111. Remove the `"Always empty until Phase 6"` entry from the tag lists on the 2 ITEM entries (`commits`, `pullRequests`) and the 4 COMMIT + 4 PULL_REQUEST entries (10 total; RESEARCH says 8 but the file shows 10 occurrences, count in the task). Keep the `List.of("Body only", "Ticket only")` pattern used by sibling entries, and keep every description ending with "." (tested). Reword the PR status description (line ~108: "such as completed or active") to the dev-status values: `"The pull request status, such as OPEN or MERGED."`. Add a note that without an Azure DevOps credential commits are those whose message names the ticket key.
Test: replace `commitsAndPullRequestsAreTaggedAlwaysEmptyUntilPhaseSix` with an assertion that the tag is gone. Also update `F/src/components/PlaceholderDrawer.tsx`.

---

### `SampleReleaseView.java` (modify)

**Analog:** itself, lines 10-32. `TICKET_NO_LINKED_CODE` (line 10-14) already ends `List.of(new CommentView("Verified on staging.")), List.of(), List.of());`. Keep it for the "no linked code" case. Add `CommitView`/`PullRequestView` sample data to `TICKET_UNRESOLVED` (lines 16-19, currently `List.of(), List.of(), List.of()`) so save-time validation exercises `{{#commits}}`. Use invented data only. Check `TemplateServiceTest` / `MustacheFlexmarkRenderAdapterTest` item-count expectations.

---

### `TicketDetail.java` + `JiraRestTicketAdapter.toTicketDetail` (modify)

**Analog:** `JiraRestTicketAdapter.java:97-140`. `String key = issue.path("key").asText(null);` is the sibling read for the top-level `id`:
```java
JsonNode fields = issue.path("fields");
String key = issue.path("key").asText(null);
String status = field(fields, fieldIds, TicketField.STATUS).path("name").asText("Unknown");
```
Add `jiraIssueId = issue.path("id").asText(null)`, `updatedAt` from literal field id `updated` (add it to the requested ids; it is a system field, not routed through name resolution), and `statusCategory` from `fields.status.statusCategory.key` (`new` -> TO_DO, `indeterminate` -> IN_PROGRESS, `done` -> DONE, reuse `domain/jira/StatusCategory`). Parse `updated` with the `SSSxx` formatter (value `+0300`). 17 `new TicketDetail(` sites across 8 files change; run `mvn clean` (incremental compilation hides broken tests).

---

### Fixtures (new) -- how real captures became committed fixtures

**Analog:** `src/test/resources/fixtures/devstatus-cnv-2896-{summary,detail,repository}.json` and `JiraDevStatusSpikeAdapterTest.java:50-54` (`fixture(name)` helper, `okJson(fixture(...))`). Fixtures are kept verbatim in shape and redacted in content (D-02-05). For Phase 6, redact `.planning/phases/06-azure-devops-enrichment/spike-capture/*` (git-excluded, contains real names and emails) into `src/test/resources/fixtures/`:
`search-jql`, `changelog-page1`, `changelog-small-page`, `statuses`, `ado-pr-commits-top3` (+ headers), `ado-pr-commits-all`, `ado-repositories`, and the invalid-PAT 302 (status and content type only). Replace names, emails, account ids with synthetic values (the existing tests use `557058:abc-123`, `rm@acme.com`, `EXAMPLE-1`), but keep field names, timestamp offsets (`+0300`), the `x-ms-continuationtoken` header and both PR commit pagination shapes. Name them `<system>-<scenario>.json`. Do not copy any personal data into planning docs.

---

### `JiraDevStatusAdapterTest` / `AdoRestAdapterTest` / `JiraIssueHistoryAdapterTest` (WireMock tests)

**Analog:** `T/adapters/jira/devstatus/JiraDevStatusSpikeAdapterTest.java:33-143`.
```java
@WireMockTest
class JiraDevStatusSpikeAdapterTest {
    private JiraDevStatusSpikeAdapter adapter() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        return new JiraDevStatusSpikeAdapter(RestClient.builder().requestFactory(requestFactory).build());
    }
    private JiraAccessContext access(WireMockRuntimeInfo wm) {
        return new JiraAccessContext(wm.getHttpBaseUrl(), "557058:abc-123", "rm@acme.com", "tok-123");
    }
```
Copy the exception-mapping tests (lines 114-132: `unauthorized()`, `serverError()`), the query-parameter verification (lines 102-112), the envelope-key assertions (lines 79-87: keep them, they are the schema-drift guard), and the "empty detail is an ordinary result" test (134-142). For the ADO adapter, the test `HttpClient` must also be built without redirects, and a WireMock stub returning `302` with a `Location` header must assert `ADO_UNAUTHORIZED`. ADO tests point the adapter at `wm.getHttpBaseUrl()`, so the adapter needs a configurable base URL (default `https://dev.azure.com`) while production URL building stays pinned; keep it as a constructor/property value, not a request-time input.

---

### `AdoCredentialPersistenceIT` (Testcontainers IT)

**Analog:** `T/adapters/persistence/JiraCredentialPersistenceIT.java:1-60`.
```java
@SpringBootTest
@Testcontainers
class JiraCredentialPersistenceIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EncryptedTokenConverter converter;
```
Must include the key-rotation case (write ciphertext this key cannot decrypt via `jdbcTemplate`, then read status through the projection and replace through the adapter without exception); the analog's Javadoc at lines 48-57 describes the simulation technique. Also assert the token never appears in any response. A persistence IT for `saveEnrichment` follows `ReleasePersistenceIT`: frozen release rejects writes (`lockOpen`), and the fingerprint stamp rules.

---

### `application.yml` x2 (config)

**Analog:** `src/main/resources/application.yml:32-38` and `src/test/resources/application.yml:12-18` (`app.jira.connect-timeout PT5S`, `read-timeout PT10S`, `search.page-size`, `search.max-pages`). Add `app.ado.*` (`connect-timeout`, `read-timeout`, `max-pages-per-pr`) and `app.enrichment.*` (`max-concurrent: 6`, `deadline: PT45S`, `max-commits-per-ticket: 400`) to BOTH files. The test file shadows main's, so a property missing from it fails every full-context IT on an unresolved placeholder (Pitfall 10).

---

### Frontend: banner, Settings section, release detail

**Banner analog:** `F/src/components/CredentialAlert.tsx` (amber styling, `role="alert"`, `data-reason`, `Link to="/settings"`), mounted in `AppShell.tsx:72` beside `<CredentialAlert />`.
```tsx
<div role="alert" data-reason="INVALID_CREDENTIALS" className="border-b border-amber-300 bg-amber-50 px-6 py-3 text-sm text-amber-900">
  Your Jira credential is no longer valid.{' '}
  <Link to="/settings" className="font-medium underline">Replace your Jira API token</Link>
</div>
```
`credentialStatus.ts` is an event-driven `useSyncExternalStore` flag (set on a 401); the ADO banner state is known from a query, so do NOT copy the store. Use a TanStack Query hook (`useAdoCredentialStatus`, modelled on `F/src/api/jiraSite.ts` `useJiraSite`) returning `state`; render the banner for EXPIRING/EXPIRED only, with the same classes.

**Settings analog:** `F/src/routes/SettingsRoute.tsx:1-151`. Each section is a heading + `<form>` + own state + mutation hook, e.g. lines 78-110 (Jira API token section):
```tsx
<h2 className="mt-8 text-base font-semibold text-gray-900">Jira API token</h2>
...
<form onSubmit={handleReplaceToken} className="mt-4 space-y-2">
  <label htmlFor="apiToken" ...>New Jira API token</label>
  <input id="apiToken" type="password" required autoComplete="off" ... className="w-full rounded border border-gray-300 px-3 py-2 text-sm" />
  <button type="submit" disabled={replaceToken.isPending} className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50">Replace token</button>
  {tokenReplaced && !replaceToken.isError && (<p role="status" className="text-sm text-green-600">Token replaced.</p>)}
  {tokenProblem && (<p role="alert" data-reason={tokenProblem.reason} className="text-sm text-red-600">{messageForReason(tokenProblem)}</p>)}
</form>
```
Add a third section the same way: organization, token (password, never displayed), expiry date, help text including **"select one organization"**, an expiry-state indicator, and error display through `ApiError.problem.reason` + `messageForReason` (add the new reasons to `REASON_MESSAGES` in `F/src/api/auth.ts`).

**Release page:** `ReleaseDetailRoute.tsx` (602 lines; refresh button ~line 215, freeze `ConfirmDialog` ~263). Read the relevant regions at task time. Chip "3 commits · 1 PR" with expandable list; "No link" vs "Couldn't fetch (reason)" visually distinct; freeze dialog count derived client-side from items already in the payload (no new endpoint); render any stored `url` as `href` only when it starts with `https://dev.azure.com/` (Pitfall 11). Invalidate the release query after code-link refresh.

## Shared Patterns

### Non-decrypting credential access (locked decision)
**Source:** `JiraCredentialJpaRepository.java:14-19, 22-36, 52-56`; `JiraCredentialRepositoryAdapter.java:32-57`
**Apply to:** `AdoCredentialJpaRepository`, `AdoCredentialRepositoryAdapter`, `AdoCredentialController`
Writes are JPQL updates (insert only when count is 0), status reads are interface projections, only use-time reads decrypt, and a key rotation must never lock Settings.

### Error cascade to a domain reason, never a leaked HTTP type
**Source:** `JiraDevStatusSpikeAdapter.java:58-70`, `JiraRestTicketAdapter.java:78-92`
**Apply to:** `JiraDevStatusAdapter`, `JiraIssueHistoryAdapter`, `AdoRestAdapter`
Order: Unauthorized, Forbidden, TooManyRequests (no retry in the adapter), `HttpServerErrorException`, `ResourceAccessException` (unreachable), then `RestClientException` catch-all. ADO adds 3xx/203 via `onStatus`.

### Lock-then-mutate inside one transaction
**Source:** `ReleaseRepositoryAdapter.java:106-118, 181-191`
**Apply to:** `saveEnrichment`, any new mutator
`lockOpen(releaseId)` first, no HTTP inside the transaction, `stampItemsChanged` only when document-visible content changed.

### Best-effort optional dependency
**Source:** `ReleaseService.refresh` (99-113) for what NOT to reuse; D-06-04 for the rule
**Apply to:** `refresh`, `createWithTickets`, `addTickets`, `refreshCodeLinks`
Enrichment runs after the core write, inside its own catch-all, and logs rather than throws. The all-or-nothing `ReleaseRefreshIncompleteException` check applies to snapshots only.

### Singleton settings controller
**Source:** `JiraSiteConfigController.java`, `UpdateJiraSiteConfigRequest`, `JiraSiteConfigResponse`
**Apply to:** `AdoCredentialController`
`@Valid @RequestBody`, `Principal principal` for `updated_by`, `ActorResolutionService` for display name, under the existing `/api/**` authenticated rule. No CSRF change (SameSite=Strict stays).

### Test fixtures and WireMock harness
**Source:** `JiraDevStatusSpikeAdapterTest.java:33-58`, fixtures dir
**Apply to:** all new adapter tests
`@WireMockTest`, 1 s timeouts, `fixture(name)` classpath loader, synthetic identities.

### Testcontainers IT
**Source:** `JiraCredentialPersistenceIT.java:26-43`
**Apply to:** `AdoCredentialPersistenceIT`, enrichment persistence IT
`@SpringBootTest @Testcontainers`, `postgres:18` with `@ServiceConnection`.

### Environment landmines
`export JAVA_HOME="$HOME/.jdks/jdk-25"` per Maven call; `mvn -P '!frontend' clean test` when public records change; no Vite dev server while the frontend profile runs `npm ci`; add config keys to both `application.yml` files.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| Run-scoped PR de-dup (`ConcurrentHashMap<PrKey, CompletableFuture<...>>`) in `EnrichmentService` | service | fan-out | `RenderService` bounds CPU work, but no existing code de-duplicates in-flight I/O; use RESEARCH Pattern 2 |
| Shared ADO throttle gate (`AtomicReference<Instant> notBefore`, `Retry-After` honoured once) | utility | request-response | No existing backoff exists (Jira adapter deliberately has none); use RESEARCH Pattern 3 |

## Metadata

**Analog search scope:** `src/main/java/com/evision/releasemgmt/{adapters,application,config,domain,ports}`, `src/test/java/.../adapters`, `src/main/resources/db/migration`, `frontend/src/{api,components,routes}`, prior `05.4-PATTERNS.md` format.
**Files read in full or in targeted ranges:** about 24.
**Not re-read (read at task time):** `RenderService.java`, `ReleaseDetailRoute.tsx`, `JiraSiteConfigService`, `ReleaseItemResponse.java`, `ReleaseTicketItemEntity.java`.
**Pattern extraction date:** 2026-10-12
