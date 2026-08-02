# Phase 2: Jira Ticket Browsing - Pattern Map

**Mapped:** 2026-08-02
**Files analyzed:** 24 (16 backend, 8 frontend)
**Analogs found:** 24 / 24 (Phase 1 shipped a complete vertical slice — every role has a direct analog)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `domain/jira/TicketSummary.java` | model | transform | `domain/auth/JiraIdentity.java` (record) | role-match |
| `domain/jira/TicketDetail.java` | model | transform | `domain/auth/JiraIdentity.java` (record) | role-match |
| `domain/jira/JiraField.java` | model | transform | `domain/auth/JiraIdentity.java` (record) | role-match |
| `domain/jira/JiraProjectSummary.java` / `JiraVersion.java` | model | transform | `domain/jira/JiraSiteConfig.java` (record) | exact |
| `domain/jira/JiraTicketReadException.java` | model (exception) | request-response | `domain/auth/JiraAuthenticationException.java` | exact |
| `domain/auth/JiraAuthFailureReason.java` (extend enum) | model (enum) | request-response | itself (existing file, add `RESULT_SET_TOO_LARGE`) | exact |
| `ports/JiraTicketPort.java` | port | CRUD (read) | `ports/JiraAuthPort.java` | exact |
| `ports/JiraFieldMetadataPort.java` | port | CRUD (read) | `ports/JiraAuthPort.java` | exact |
| `ports/JiraProjectPort.java` | port | CRUD (read) | `ports/JiraAuthPort.java` | exact |
| `adapters/jira/JiraRestTicketAdapter.java` | service (outbound adapter) | streaming/batch (paginated read) | `adapters/jira/JiraRestAuthAdapter.java` | exact |
| `adapters/jira/JiraFieldMetadataAdapter.java` | service (outbound adapter) | CRUD (read) | `adapters/jira/JiraRestAuthAdapter.java` | exact |
| `adapters/jira/JiraProjectAdapter.java` | service (outbound adapter) | CRUD (read, offset-paginated) | `adapters/jira/JiraRestAuthAdapter.java` | exact |
| `adapters/jira/AdfFlattener.java` | utility (pure function) | transform | none (no existing pure-function adapter) — see "No Analog Found" | none |
| `adapters/jira/devstatus/JiraDevStatusSpikeAdapter.java` | service (outbound adapter, spike) | request-response | `adapters/jira/JiraRestAuthAdapter.java` | role-match |
| Jira response DTOs (`JqlSearchResponse`, `JiraFieldResponse`, `JiraProjectResponse`, `JiraVersionResponse`, etc.) | model (wire DTO) | transform | `adapters/jira/JiraMyselfResponse.java` | exact |
| `application/TicketSearchService.java` | service | request-response (orchestration) | `application/JiraAccessContextService.java` + `application/JiraSiteConfigService.java` | exact |
| `application/FieldMetadataService.java` | service | CRUD (cached read) | `application/JiraSiteConfigService.java` | role-match |
| `adapters/web/TicketController.java` | controller | request-response | `adapters/web/JiraSiteConfigController.java` | exact |
| `adapters/web/dto/TicketSearchRequest.java` (or query params) | model (request DTO) | request-response | `adapters/web/dto/UpdateJiraSiteConfigRequest.java` | exact |
| `adapters/web/dto/TicketSummaryResponse.java` / `TicketDetailResponse.java` | model (response DTO) | transform | `adapters/web/dto/JiraSiteConfigResponse.java` | exact |
| `adapters/web/GlobalExceptionHandler.java` (extend) | controller-advice | request-response | itself (existing file, add `RESULT_SET_TOO_LARGE` branch) | exact |
| WireMock adapter tests (`JiraRestTicketAdapterTest`, `JiraFieldMetadataAdapterTest`, `JiraProjectAdapterTest`, `JiraDevStatusSpikeAdapterTest`) | test | request-response | `adapters/jira/JiraRestAuthAdapterTest.java` | exact |
| `adapters/web/TicketControllerTest.java` | test (controller slice) | request-response | `adapters/web/JiraSiteConfigControllerTest.java` | exact |
| `config/SecurityFilterChainIT.java` (extend) | test (full-context IT) | request-response | itself (existing file, add `/api/tickets` assertions) | exact |
| `AdfFlattenerTest.java` | test (pure unit test) | transform | none — new pattern (no existing pure-function unit test) | none |
| `frontend/src/api/tickets.ts` | hook (TanStack Query) | request-response | `frontend/src/api/jiraSite.ts` | exact |
| `frontend/src/api/jiraProjects.ts` | hook (TanStack Query) | request-response | `frontend/src/api/jiraSite.ts` | exact |
| `frontend/src/routes/TicketsRoute.tsx` | component (route) | request-response | `frontend/src/routes/SettingsRoute.tsx` | exact |
| `frontend/src/router.tsx` (extend) | router config | request-response | itself (existing file, add `/tickets` child route) | exact |
| `frontend/src/api/auth.ts` (extend `REASON_MESSAGES`) | utility | transform | itself (existing file, add `RESULT_SET_TOO_LARGE` entry) | exact |

## Pattern Assignments

### `ports/JiraTicketPort.java`, `ports/JiraFieldMetadataPort.java`, `ports/JiraProjectPort.java` (port, CRUD)

**Analog:** `src/main/java/com/evision/releasemgmt/ports/JiraAuthPort.java`

**Full pattern** (entire file, 9 lines):
```java
package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.auth.JiraIdentity;

public interface JiraAuthPort {

    JiraIdentity validate(String siteBaseUrl, String email, String apiToken);
}
```

Port interfaces in this codebase are minimal: no Spring annotations, single method (or a small number), domain types only in the signature (never wire DTOs). Follow this shape exactly:
```java
public interface JiraTicketPort {
    List<TicketDetail> searchAll(String jql, List<String> fieldIds);
}
public interface JiraFieldMetadataPort {
    Map<String, String> fetchAllFields(); // id/key/name -> resolved by application layer
}
public interface JiraProjectPort {
    List<JiraProjectSummary> listProjects();
    List<JiraVersion> listVersions(String projectKey);
}
```
ArchUnit (`ArchitectureBoundaryTest.domainAndPortsHaveNoFrameworkImports`) forbids any Spring/Jakarta/Hibernate/Jackson import in `ports/` — these interfaces must stay framework-free.

---

### `adapters/jira/JiraRestTicketAdapter.java`, `JiraFieldMetadataAdapter.java`, `JiraProjectAdapter.java` (service/outbound adapter)

**Analog:** `src/main/java/com/evision/releasemgmt/adapters/jira/JiraRestAuthAdapter.java` (full file, 58 lines)

**Imports pattern** (lines 1-18):
```java
package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.auth.JiraIdentity;
import com.evision.releasemgmt.ports.JiraAuthPort;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
```

**Component/DI pattern** (lines 20-28): `@Component` + constructor injection of the shared `RestClient jiraRestClient` bean (from `JiraRestClientConfig`, reuse as-is — do not create a second RestClient bean). No `@Profile` needed for ticket adapters unless a stub variant is required for local/dev (Phase 1 only stubbed auth because login needs to work without a real Jira account; ticket browsing likely does not need a stub — confirm with planner, but if one is added, mirror `StubJiraAuthAdapter`'s `@Profile("stub-jira")` gate).

**Core pattern — building the request and calling Jira** (lines 30-43, adapted for the pagination loop per RESEARCH.md Pattern 1):
```java
@Override
public List<TicketDetail> searchAll(String jql, List<String> fieldIds) {
    List<TicketDetail> results = new ArrayList<>();
    String pageToken = null;
    int pagesFetched = 0;
    do {
        JqlSearchResponse page = restClient.post()
                .uri(siteBaseUrl + "/rest/api/3/search/jql")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth(email, apiToken))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .body(new JqlSearchRequest(jql, fieldIds, MAX_RESULTS_PER_PAGE, pageToken))
                .retrieve()
                .body(JqlSearchResponse.class);
        results.addAll(page.issues().stream().map(this::toTicketDetail).toList());
        pageToken = page.nextPageToken();
        if (++pagesFetched > maxPagesCeiling) {
            throw new JiraTicketReadException(JiraAuthFailureReason.RESULT_SET_TOO_LARGE);
        }
    } while (pageToken != null);
    return results;
}
```
Note the Basic-auth header construction (`Base64.getEncoder().encodeToString((email + ":" + apiToken)...)`) is identical to `JiraRestAuthAdapter` lines 32-36 — extract nothing new, just repeat the same two lines per outbound call (the codebase has no shared HTTP-header-builder abstraction yet; do not introduce one for two call sites — no over-engineering).

**Error handling pattern** (lines 44-56, copy verbatim, substituting the exception type):
```java
} catch (HttpClientErrorException.Unauthorized e) {
    throw new JiraTicketReadException(JiraAuthFailureReason.INVALID_CREDENTIALS);
} catch (HttpClientErrorException.Forbidden e) {
    throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_FORBIDDEN);
} catch (HttpClientErrorException.TooManyRequests e) {
    throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_RATE_LIMITED);
} catch (HttpServerErrorException e) {
    throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
} catch (ResourceAccessException e) {
    throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_UNREACHABLE);
} catch (RestClientException e) {
    throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
}
```
This exact six-branch catch cascade is the project's one and only Jira-HTTP-error-to-taxonomy mapping pattern — reuse verbatim in every new Jira adapter (ticket search, field metadata, project/version listing, dev-status spike).

**Wire DTO pattern:** `src/main/java/com/evision/releasemgmt/adapters/jira/JiraMyselfResponse.java` (full file, 7 lines):
```java
package com.evision.releasemgmt.adapters.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record JiraMyselfResponse(String accountId, String emailAddress, String displayName, Boolean active) {
}
```
Package-private record, `@JsonIgnoreProperties(ignoreUnknown = true)` on every Jira response DTO (Jira adds fields without notice — this is the project's defensive-deserialization convention). Apply to `JqlSearchResponse`, `JqlIssue`, `JiraFieldResponse`, `JiraProjectResponse`, `JiraVersionResponse`, and the dev-status spike response DTO.

---

### `adapters/jira/AdfFlattener.java` (utility, transform) — NO ANALOG, use RESEARCH.md code directly

No existing pure-function/no-Spring-dependency class exists in `adapters/jira` to pattern-match against (every existing adapter is a `@Component` RestClient wrapper). Build directly from RESEARCH.md's "Code Examples > ADF flattening" section (lines 380-425 of `02-RESEARCH.md`) — a plain `public final class` with a recursive `JsonNode` walker, no Spring imports, unit-testable standalone. This is consistent with the ArchUnit constraint that only `domain`/`ports` are framework-free by rule, but this class should still avoid framework dependency by convention/testability even though it lives in `adapters/jira`.

---

### `application/TicketSearchService.java`, `FieldMetadataService.java` (application service)

**Analog:** `src/main/java/com/evision/releasemgmt/application/JiraAccessContextService.java` (full file, 40 lines) — **this is the mandatory entry point**. Every new service in this phase MUST call `jiraAccessContextService.currentContext()` to obtain `siteBaseUrl`/`email`/`apiToken` before delegating to a port; there is no other way to get credentials (AUTH-02).

**Constructor-injection + orchestration pattern** (lines 17-30):
```java
@Service
public class JiraAccessContextService {

    private final CurrentUserPort currentUserPort;
    private final JiraCredentialRepository credentialRepository;
    private final JiraSiteConfigService jiraSiteConfigService;

    public JiraAccessContextService(CurrentUserPort currentUserPort,
                                     JiraCredentialRepository credentialRepository,
                                     JiraSiteConfigService jiraSiteConfigService) {
        this.currentUserPort = currentUserPort;
        this.credentialRepository = credentialRepository;
        this.jiraSiteConfigService = jiraSiteConfigService;
    }

    public JiraAccessContext currentContext() {
        String accountId = currentUserPort.currentAccountId()
                .orElseThrow(() -> new JiraAuthenticationException(JiraAuthFailureReason.INVALID_CREDENTIALS));
        JiraCredential credential = credentialRepository.findByAccountId(accountId)
                .orElseThrow(() -> new JiraAuthenticationException(JiraAuthFailureReason.INVALID_CREDENTIALS));
        String siteBaseUrl = jiraSiteConfigService.get().baseUrl();
        return new JiraAccessContext(siteBaseUrl, accountId, credential.email(), credential.apiToken());
    }
}
```

`TicketSearchService` shape (per RESEARCH.md's Architecture Patterns section):
```java
@Service
public class TicketSearchService {
    private final JiraAccessContextService accessContextService;
    private final FieldMetadataService fieldMetadataService;
    private final JiraTicketPort ticketPort;
    private final JiraProjectPort projectPort; // if project/version listing lives here too, else split

    public List<TicketDetail> search(TicketSearchCriteria criteria) {
        JiraAccessContext ctx = accessContextService.currentContext();
        String jql = criteria.toJql(); // ids only, never raw display-name interpolation - see Security note below
        Map<String, String> fieldIds = fieldMetadataService.resolve(FIXED_FIELD_NAMES);
        return ticketPort.searchAll(jql, new ArrayList<>(fieldIds.values()));
    }
}
```

**FieldMetadataService caching pattern** — no direct analog for TTL/cache in Phase 1 (JiraSiteConfigService has no caching at all, it always reads through the repository). Follow RESEARCH.md Pattern 2 verbatim (a `ConcurrentHashMap` populated on first use, refreshed once on a resolution miss, no Caffeine/TTL library — per RESEARCH.md's explicit "no over-engineering" guidance).

---

### `adapters/web/TicketController.java` (controller, request-response)

**Analog:** `src/main/java/com/evision/releasemgmt/adapters/web/JiraSiteConfigController.java` (full file, 42 lines)

**Full controller shape to copy:**
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
        return toResponse(service.updateBaseUrl(request.baseUrl(), principal.getName()));
    }

    private JiraSiteConfigResponse toResponse(JiraSiteConfig config) {
        return new JiraSiteConfigResponse(config.baseUrl(), config.updatedAt(), config.updatedBy());
    }
}
```
Constructor-injected single service, no `Principal` needed unless recording an actor (ticket reads don't need one — `TicketController` will be simpler than `JiraSiteConfigController`, closer to its `GetMapping` half only). DTOs are built via a private `toResponse` mapper — never expose domain records directly if a response-shape/field mismatch would leak internal structure; for `TicketSummaryResponse`/`TicketDetailResponse` follow the same private-mapper convention.

**Request DTO with validation** — `src/main/java/com/evision/releasemgmt/adapters/web/dto/UpdateJiraSiteConfigRequest.java` (full file, 11 lines):
```java
public record UpdateJiraSiteConfigRequest(
        @NotBlank
        @Pattern(regexp = "^https://[A-Za-z0-9][A-Za-z0-9-]*\\.atlassian\\.net$",
                 message = "must be a Jira Cloud site URL of the form https://<site>.atlassian.net with no trailing slash")
        String baseUrl) {
}
```
Jakarta Bean Validation annotations directly on record components. For the ticket search request (project+fixVersion path vs. JQL path), model it as query params or a request DTO with `@NotBlank` where required — but note the mutual-exclusivity rule from UI-SPEC (project+fixVersion XOR raw JQL) is a cross-field constraint Bean Validation annotations alone can't express; validate that in the service/controller layer, not via annotations (consistent with "validate at system boundaries, trust internal code" — the boundary here is "did the client send a coherent request shape", handled in code, not a custom validator class, per no-over-engineering).

---

### Error mapping — `GlobalExceptionHandler.java` (extend, do not replace)

**Analog:** `src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java`, lines 32-64 (the `JiraAuthenticationException` switch)

```java
@ExceptionHandler(JiraAuthenticationException.class)
public ProblemDetail handleJiraAuthFailure(JiraAuthenticationException ex) {
    return switch (ex.reason()) {
        case INVALID_CREDENTIALS -> problem(HttpStatus.UNAUTHORIZED, "Jira credential rejected",
                "Your Jira credential is no longer valid. Enter your Jira email and a current API token.",
                "INVALID_CREDENTIALS");
        case JIRA_FORBIDDEN -> problem(HttpStatus.FORBIDDEN, "Jira access denied", "...", "JIRA_FORBIDDEN");
        case JIRA_RATE_LIMITED -> problem(HttpStatus.TOO_MANY_REQUESTS, "Jira rate limit reached", "...", "JIRA_RATE_LIMITED");
        case JIRA_UNREACHABLE -> problem(HttpStatus.SERVICE_UNAVAILABLE, "Jira unreachable", "...", "JIRA_UNREACHABLE");
        case JIRA_ERROR -> problem(HttpStatus.BAD_GATEWAY, "Jira returned an error", "...", "JIRA_ERROR");
        case SITE_NOT_CONFIGURED -> problem(HttpStatus.CONFLICT, "Jira site not configured", "...", "SITE_NOT_CONFIGURED");
        case ACCOUNT_MISMATCH -> problem(HttpStatus.CONFLICT, "Token belongs to a different Jira account", "...", "ACCOUNT_MISMATCH");
    };
}

private ProblemDetail problem(HttpStatus status, String title, String detail, String reason) {
    ProblemDetail pd = ProblemDetail.forStatus(status);
    pd.setTitle(title);
    pd.setDetail(detail);
    pd.setProperty("reason", reason);
    return pd;
}
```

**Required change per UI-SPEC's binding "Planning input — new reason code required":** add a `RESULT_SET_TOO_LARGE` case to both `JiraAuthFailureReason` enum (`src/main/java/com/evision/releasemgmt/domain/auth/JiraAuthFailureReason.java`) and this switch — e.g.:
```java
case RESULT_SET_TOO_LARGE -> problem(HttpStatus.PAYLOAD_TOO_LARGE,
        "Too many matching tickets",
        "This query matched too many tickets to load. Narrow your fix version or JQL filter.",
        "RESULT_SET_TOO_LARGE");
```
Since `JiraTicketReadException` (new) is a distinct exception type from `JiraAuthenticationException`, either (a) have it extend `JiraAuthenticationException` and reuse the existing `@ExceptionHandler(JiraAuthenticationException.class)`, or (b) add a new sibling `@ExceptionHandler(JiraTicketReadException.class)` with the same switch shape reusing `JiraAuthFailureReason`. Given the reason enum is explicitly meant to be shared ("extend `JiraAuthFailureReason`... rather than inventing a parallel error shape" — RESEARCH.md), option (a) — `JiraTicketReadException extends JiraAuthenticationException` — needs the least duplicated switch logic; flag this exact choice for the planner since it isn't fully settled in CONTEXT/RESEARCH.

The generic `Exception.class` catch-all (lines 95-100) needs no change — the ADF flattener's bounded-recursion guard and pagination ceiling are the two new failure modes, both already routed through the taxonomy above rather than falling through to unexpected-error.

---

### WireMock contract tests

**Analog:** `src/test/java/com/evision/releasemgmt/adapters/jira/JiraRestAuthAdapterTest.java` (full file, 139 lines)

**Adapter-under-test factory pattern** (lines 36-44, reuse verbatim, only the adapter class changes):
```java
@WireMockTest
class JiraRestTicketAdapterTest {
    private JiraRestTicketAdapter adapter(WireMockRuntimeInfo wm) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new JiraRestTicketAdapter(restClient, /* maxPagesCeiling */ 100);
    }
```

**Per-error-branch test pattern** (lines 80-138) — one test method per HTTP status → reason mapping. Copy this table verbatim, substituting endpoint path and exception type:
```java
@Test
void mapsTooManyRequestsToRateLimited(WireMockRuntimeInfo wm) {
    stubFor(post(urlEqualTo("/rest/api/3/search/jql")).willReturn(aResponse().withStatus(429)));
    assertThatThrownBy(() -> adapter(wm).searchAll("project = 10023", List.of("summary")))
            .isInstanceOf(JiraTicketReadException.class)
            .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                    .isEqualTo(JiraAuthFailureReason.JIRA_RATE_LIMITED));
}
```

**Pagination-specific test to add (new, no direct Phase-1 analog since auth has no pagination)** — build against RESEARCH.md Pattern 1: stub two sequential `/search/jql` responses (first with a `nextPageToken`, second without), assert both pages' issues are concatenated; a third test stubs a response that always returns a `nextPageToken` to assert the safety-ceiling throws `RESULT_SET_TOO_LARGE` rather than looping forever — this is the single most important new test in the phase per JIRA-03.

**Dev-status spike fixture test** — stub `/rest/dev-status/1.0/issue/detail` using the **captured, redacted real response** from `CNV-2896` (per CONTEXT.md D-02-05) as the WireMock response body, not a synthetic JSON literal — this is the one adapter test in the phase that must load a fixture file rather than an inline string, unlike every other test in `JiraRestAuthAdapterTest` which inlines JSON directly via `okJson("...")`.

---

### Controller slice test

**Analog:** `src/test/java/com/evision/releasemgmt/adapters/web/JiraSiteConfigControllerTest.java` (full file, 111 lines)

**`@WebMvcTest` setup pattern** (lines 33-41):
```java
@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class, ProblemDetailAuthenticationEntryPoint.class})
class TicketControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private TicketSearchService service;
```

**Authenticated-request pattern** (lines 62-75, `.with(jwt().jwt(...))`) — every ticket-read endpoint requires authentication (unlike `GET /api/settings/jira-site`, which is deliberately public per `SecurityFilterChainIT.jiraSiteLookupIsPublicToAnonymousCallers` — `/api/tickets` must NOT reuse that public-GET exemption; confirm the `SecurityConfig` matcher covers `/api/tickets/**` under the general `/api/**` authenticated rule, not the specific carve-out for jira-site GET).

**Validation-rejection test pattern** (lines 77-88): assert `400` + `reason=VALIDATION_FAILED` for a malformed request shape (e.g. neither project+fixVersion nor JQL supplied).

---

### Full-context IT — extend `SecurityFilterChainIT.java`

**Analog:** existing file, lines 42-46 (`jiraSiteLookupIsPublicToAnonymousCallers`) and lines 48-57 (`protectedWriteWithoutAuthenticationIsUnauthorizedWithProblemDetail`) — add equivalent assertions for `GET /api/tickets` (should be `401` without a cookie, matching the protected-write pattern, since ticket reads require a signed-in Jira credential per AUTH-02). Reuse the `@Testcontainers` + `PostgreSQLContainer` + `@ActiveProfiles({"stub-jira", "local"})` setup verbatim (lines 26-34) — no new IT infrastructure needed.

**Config file note:** `src/test/resources/application.yml` shadows `src/main/resources/application.yml` (does not merge) — any new `app.jira.*` property (e.g. `app.jira.search.max-results-ceiling`) added to main must be duplicated in test resources or full-context ITs fail on unresolved placeholders. Current main config (`application.yml` lines 27-31):
```yaml
app:
  jira:
    default-base-url: ${RM_JIRA_DEFAULT_BASE_URL:https://example.atlassian.net}
    connect-timeout: PT5S
    read-timeout: PT10S
```
Add the new ceiling property under this same `app.jira` block, e.g. `app.jira.search.max-results-ceiling: 100`, and mirror it in `src/test/resources/application.yml`.

---

### Frontend: `frontend/src/api/tickets.ts`, `frontend/src/api/jiraProjects.ts` (TanStack Query hooks)

**Analog:** `frontend/src/api/jiraSite.ts` (full file, 21 lines):
```typescript
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client';

export type JiraSiteConfig = { baseUrl: string; updatedAt: string; updatedBy: string };
export const jiraSiteQueryKey = ['jira-site'] as const;

export function useJiraSite() {
  return useQuery({
    queryKey: jiraSiteQueryKey,
    queryFn: () => apiFetch<JiraSiteConfig>('/api/settings/jira-site'),
  });
}

export function useUpdateJiraSite() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (baseUrl: string) =>
      apiFetch<JiraSiteConfig>('/api/settings/jira-site', { method: 'PUT', body: JSON.stringify({ baseUrl }) }),
    onSuccess: (config) => queryClient.setQueryData(jiraSiteQueryKey, config),
  });
}
```
Pattern: exported `type` alias matching the response shape, `queryKey` array constant, one `useQuery` per GET, one `useMutation` per write, `apiFetch<T>` generic wrapper for every call (never raw `fetch`). For tickets:
```typescript
export type TicketDetail = { key: string; summary: string; issueType: string; status: string; resolution: string | null; assignee: string | null; reporter: string; description: string; comments: string[] };
export function useTicketSearch(criteria: TicketSearchCriteria, options: { enabled: boolean }) {
  return useQuery({
    queryKey: ['tickets', criteria],
    queryFn: () => apiFetch<TicketDetail[]>(`/api/tickets?${toQueryString(criteria)}`),
    enabled: options.enabled, // manual trigger, not auto-fetch on picker change, per UI-SPEC's "Load tickets" button contract
  });
}
```
Note the UI-SPEC's explicit "do not auto-fetch on picker change" requirement means this hook needs `enabled: false` until the "Load tickets" button fires (via `refetch()` or a `useMutation`-style manual trigger) — `jiraSite.ts`'s `useJiraSite` auto-fetches on mount, which is the wrong sub-pattern here; instead mirror `auth.ts`'s `useLogin` mutation-trigger style (manual `.mutate()`/`.refetch()` call) for the actual fetch trigger, even though the underlying call is logically a GET.

**Error-message reuse pattern:** `frontend/src/api/auth.ts` lines 63-83 (`REASON_MESSAGES` + `messageForReason`) — **zero new entries needed** except `RESULT_SET_TOO_LARGE` (see UI-SPEC binding requirement). Add:
```typescript
RESULT_SET_TOO_LARGE: 'This query matched too many tickets to load. Narrow your fix version or JQL filter.',
```
to the `REASON_MESSAGES` map in `auth.ts` — do not create a parallel message table in a new file.

---

### Frontend: `frontend/src/routes/TicketsRoute.tsx` (route component)

**Analog:** `frontend/src/routes/SettingsRoute.tsx` (full file, 152 lines)

**State/derived-error pattern** (lines 1-36) — local `useState` for form inputs, derive `ApiError`-typed problem objects from query/mutation `.error`, a boolean derived from `problem.reason === 'X'` for state branching (mirrors `notConfigured` at line 20-21). Reuse this exact idiom for the empty-vs-error state distinction UI-SPEC demands:
```typescript
const problem = search.isError && search.error instanceof ApiError ? search.error.problem : undefined;
const isEmpty = search.isSuccess && search.data.length === 0;
```

**Alert/error rendering pattern** (lines 143-147):
```tsx
{tokenProblem && (
  <p role="alert" data-reason={tokenProblem.reason} className="text-sm text-red-600">
    {messageForReason(tokenProblem)}
  </p>
)}
```
This exact `role="alert" data-reason={...} className="text-sm text-red-600"` triple is the project's only error-presentation idiom — reuse verbatim for JIRA-06's error state, and add the "Try again" button UI-SPEC requires (re-invoke the last search) alongside it, which has no direct analog (nothing in Phase 1 has a retry action) — build as a plain button calling the same `refetch`/`mutate` function used for the original "Load tickets" click.

**Success/status rendering pattern** (line 138-142, `role="status"` for non-error confirmations) — reuse for the UI-SPEC-mandated `aria-live="polite"` "{n} selected" counter: `role="status"` already satisfies an `aria-live="polite"` equivalent per ARIA spec (implicit live region), but UI-SPEC explicitly asks for `aria-live="polite"` OR `role="status"` — either satisfies the binding checker requirement.

**Native form control styling** (lines 86-94, 121-130) — `rounded border border-gray-300 px-3 py-2 text-sm` for inputs/selects, `rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50` for primary buttons. Apply verbatim to the project/fix-version `<select>` pickers and "Load tickets" button. Per UI-SPEC's typography rule, new Phase 2 elements use `font-semibold` not `font-medium` — note this is a deliberate *deviation* from the literal class string above; copy the structural pattern, swap `font-medium` → `font-semibold` on new elements only (existing Settings code stays untouched).

---

### Frontend: router registration

**Analog:** `frontend/src/router.tsx`, lines 40-48 (the `settingsRoute` definition and `routeTree` registration):
```typescript
const settingsRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/settings',
  component: SettingsRoute,
});

const routeTree = rootRoute.addChildren([
  authenticatedRoute.addChildren([dashboardRoute, settingsRoute]),
  loginRoute,
]);
```
Add `ticketsRoute` as a sibling nested under `authenticatedRoute` exactly like `settingsRoute` — inherits the session guard and `AppShell`/`CredentialAlert` for free, per UI-SPEC's routing note. No new route-guard logic needed.

## Shared Patterns

### AUTH-02 credential access (MANDATORY for every Jira read)
**Source:** `src/main/java/com/evision/releasemgmt/application/JiraAccessContextService.java`
**Apply to:** `TicketSearchService`, `FieldMetadataService`, any project/version-listing service, the dev-status spike service
Every one of these MUST call `jiraAccessContextService.currentContext()` and use its returned `siteBaseUrl`/`email`/`apiToken` — there is no other credential path, and none should be added (ArchUnit + the explicit AUTH-02 requirement both enforce this).

### Jira HTTP error → taxonomy mapping
**Source:** `src/main/java/com/evision/releasemgmt/adapters/jira/JiraRestAuthAdapter.java` lines 44-56
**Apply to:** every new adapter in `adapters/jira/` (`JiraRestTicketAdapter`, `JiraFieldMetadataAdapter`, `JiraProjectAdapter`, dev-status spike adapter)
Six-branch catch cascade: `Unauthorized→INVALID_CREDENTIALS`, `Forbidden→JIRA_FORBIDDEN`, `TooManyRequests→JIRA_RATE_LIMITED`, `HttpServerErrorException→JIRA_ERROR`, `ResourceAccessException→JIRA_UNREACHABLE`, generic `RestClientException→JIRA_ERROR`.

### ProblemDetail + reason error contract
**Source:** `src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java`
**Apply to:** `TicketController` and all new exception types
Extend the existing `JiraAuthFailureReason` enum and `GlobalExceptionHandler` switch rather than building a parallel scheme; add exactly one new value, `RESULT_SET_TOO_LARGE`, per the UI-SPEC's binding "Planning input" note.

### Frontend error message table
**Source:** `frontend/src/api/auth.ts` lines 63-83 (`REASON_MESSAGES`, `messageForReason`)
**Apply to:** `TicketsRoute.tsx` and any component rendering a ticket-read error
Zero new entries needed except `RESULT_SET_TOO_LARGE` — every other JIRA-06 reason code (`JIRA_UNREACHABLE`, `JIRA_RATE_LIMITED`, `INVALID_CREDENTIALS`, `JIRA_FORBIDDEN`, `JIRA_ERROR`, `SITE_NOT_CONFIGURED`) already exists verbatim.

### RestClient reuse
**Source:** `src/main/java/com/evision/releasemgmt/config/JiraRestClientConfig.java`
**Apply to:** every new outbound adapter
Reuse the single `RestClient jiraRestClient` bean (`JdkClientHttpRequestFactory`, explicit `app.jira.connect-timeout`/`app.jira.read-timeout`) — do not create a second `RestClient` bean or introduce `ClientHttpRequestFactorySettings`/`Builder` (confirmed absent from this Boot 4.1 classpath per RESEARCH.md).

### Test resources shadow warning
**Source:** `src/test/resources/application.yml`
**Apply to:** any new `app.jira.*` property (e.g. `app.jira.search.max-results-ceiling`)
Must be added to both `src/main/resources/application.yml` and `src/test/resources/application.yml` — the test file shadows, not merges, so a property added only to main leaves full-context ITs with an unresolved placeholder.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `adapters/jira/AdfFlattener.java` | utility (pure function) | transform | No existing pure-function/no-Spring-dependency class in `adapters/jira` — every existing adapter is a `@Component` RestClient wrapper. Build directly from RESEARCH.md's cited code example (lines 380-425 of `02-RESEARCH.md`), which is already concrete and ready to use. |
| `AdfFlattenerTest.java` | test (pure unit test) | transform | No existing plain JUnit unit test without WireMock/MockMvc/Spring context in the codebase's Jira-adjacent test packages — write a standard AssertJ-based unit test with hand-built `JsonNode` fixtures (via `ObjectMapper().readTree(...)`), no new pattern needed beyond standard JUnit 5 + AssertJ already used everywhere else. |
| `TicketSearchCriteria` mutual-exclusivity validation (project+fixVersion XOR JQL) | validation logic | request-response | No existing cross-field validator in the codebase (Phase 1's only validation is single-field `@Pattern`/`@NotBlank`). Implement as a plain `if`/`else` check in the controller or service layer, not a custom Bean Validation constraint class — consistent with no-over-engineering. |

## Metadata

**Analog search scope:** `src/main/java/com/evision/releasemgmt/` (adapters/jira, adapters/web, adapters/persistence, application, domain, ports, config), `src/test/java/com/evision/releasemgmt/` (same subtree + ArchitectureBoundaryTest), `frontend/src/` (api, routes, components, router.tsx)
**Files scanned:** 51 (35 backend Java, 16 frontend TS/TSX)
**Pattern extraction date:** 2026-08-02
