# Phase 12: ReleaseManagement as First Client - Pattern Map

**Mapped:** 2026-10-15
**Files analyzed:** 34 new/modified (main app only; rag-service is not touched)
**Analogs found:** 31 / 34 (3 have only partial analogs, listed at the end)

Read D-12-11..16 as overriding the earlier text: per-release ticket and code-link ids, 5 tools, `app.assistant.*` prefix (not `rm.assistant.*`; env var stays `RM_ASSISTANT_*`).

Root paths used below: `M` = `src/main/java/com/evision/releasemgmt`, `T` = `src/test/java/com/evision/releasemgmt`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `M/config/AssistantProperties.java` | config (properties record) | config | `rag-service/.../config/ChatProperties.java` (record + `@ConfigurationProperties`) | role-match (main app uses `@Value`, none uses properties records) |
| `M/config/RagRestClientConfig.java` | config | request-response | `M/config/AdoRestClientConfig.java` | exact |
| `M/config/AssistantSchedulingConfig.java` | config | batch/scheduled | `rag-service/.../config/SchedulingConfig.java` | exact (cross-module) |
| `src/main/resources/application.yml` + `src/test/resources/application.yml` | config | - | `app.ado.*` block (lines 43-47 main, 23-27 test) | exact |
| `compose.app.yaml`, `.env.example`, `docs/docker.md` | config/docs | - | `RM_JIRA_DEFAULT_BASE_URL` lines | exact |
| `M/domain/assistant/*` (records: `ReleaseIndexInvalidated`, `IndexDocument`, `DocRef`, `ToolCall`, `ToolResult`, `AssistantAnswer`, `ToolRun`, `Source`, `ChatOutcome`) | model | - | `M/domain/enrichment/*`, `M/domain/dashboard/*` (framework-free records) | role-match |
| `M/ports/RagIndexPort.java`, `M/ports/RagChatPort.java` | port | request-response | `M/ports/AdoRepositoryPort.java`, `M/ports/JiraTicketPort.java` | exact |
| `M/adapters/rag/RagIndexAdapter.java` | adapter | request-response (PUT/DELETE) | `M/adapters/ado/AdoRestAdapter.java` | exact |
| `M/adapters/rag/RagChatAdapter.java` (SSE) | adapter | streaming | `AdoRestAdapter` for the client shell; no SSE analog | partial |
| `M/application/assistant/IndexDocumentMapper.java` | utility/transform | transform | `M/application/RenderContextMapper.java`, `ReleaseViewAssembler.java` | role-match |
| `M/application/assistant/IndexSyncer.java` | service | event-driven + batch | `M/application/EnrichmentService.java` (private virtual-thread executor, quiet failure) | role-match |
| `M/application/assistant/IndexInvalidationListener.java` | component | event-driven | none (first `@TransactionalEventListener`) | no analog |
| `M/application/assistant/IndexSweep.java` | service | batch (scheduled) | `rag-service/.../application/IndexWorker.java` | role-match (cross-module) |
| `M/application/assistant/AssistantService.java` | service | request-response loop | `PromotionService.java` (thin service shape); loop logic from RESEARCH Finding 6 | partial |
| `M/application/assistant/tools/AssistantTool.java`, `ToolRegistry.java`, `ReleaseRef.java` | interface/service | request-response | `M/application/RenderService.java` (small collaborator bean) | role-match |
| `M/application/assistant/tools/{Releases,Tickets,Deployments,Dashboard,Documents}Tool.java` | service | request-response (read) | `PromotionService.history`, `DashboardService` public records | role-match |
| `M/application/ReleaseService.java` (+ `releasesContaining`) | service (modify) | CRUD read | itself, `get`/`search` | exact |
| `M/ports/ReleaseRepository.java`, `M/adapters/persistence/ReleaseRepositoryAdapter.java`, `.../repository/ReleaseTicketItemJpaRepository.java` (read method + event publish) | port/adapter | CRUD | same files | exact |
| `M/adapters/persistence/DocumentSnapshotRepositoryAdapter.java` (publish) | adapter (modify) | CRUD | same file | exact |
| `T/ArchitectureBoundaryTest.java` (+ `T/archfixture/*`) | test | - | itself | exact |
| `T/adapters/rag/RagIndexAdapterTest.java`, `RagChatAdapterTest.java` | test | WireMock | `T/adapters/ado/AdoRestAdapterTest.java` | exact |
| `T/application/assistant/*Test.java` (mapper, syncer, sweep, tools, service) | test | unit (Mockito/fakes) | `T/application/ReleaseServiceTicketsTest`, `DocumentGenerationServiceTest` (`new ...Service(...)`) | exact |
| `T/adapters/persistence/IndexAfterCommitIT.java`, `AssistantSwitchOffIT.java` | test | Testcontainers IT | `T/adapters/persistence/DocumentSnapshotPersistenceIT.java` | exact |

## Pattern Assignments

### `M/config/RagRestClientConfig.java` (config, request-response)

**Analog:** `M/config/AdoRestClientConfig.java` lines 1-28. Copy verbatim, then: two beans (`ragIndexRestClient`, `ragChatRestClient`), class-level `@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")`, add `.baseUrl(...)` and a default `Authorization: Bearer` header on the builder. Keep the "redirects not followed" comment style (only comment the WHY).

```java
@Configuration
public class AdoRestClientConfig {
    @Bean
    RestClient adoRestClient(@Value("${app.ado.connect-timeout}") Duration connectTimeout,
                             @Value("${app.ado.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
```

Note: the two existing rules to extend are `applicationDoesNotDependOnAdapters` (add `"..adapters.rag.."`) and a new `ragClientTypesStayInTheRagAdapter` mirroring `renderLibrariesLiveOnlyInTheRenderAdapter` (`ArchitectureBoundaryTest` lines 57-65).

### `M/config/AssistantProperties.java` (config)

**Analog:** `rag-service/src/main/java/com/evision/ragservice/config/ChatProperties.java` lines 1-11 (record style, `Duration` fields):

```java
@ConfigurationProperties("rag.chat")
public record ChatProperties(String model, int numCtx, ..., Duration deadline, ...) { }
```

Register with `@EnableConfigurationProperties` on the conditional config as rag-service `GenerationConfig` does (line 8 `@EnableConfigurationProperties({ChatProperties.class, ToolProperties.class})`). Use `@ConfigurationProperties("app.assistant")`; compact constructor fails fast if `enabled` and base-url/api-key blank; redact api key in `toString`. The main app otherwise uses `@Value("${app.x}")`, so the properties record is the one deliberate deviation (seven-plus keys).

### `M/config/AssistantSchedulingConfig.java` (config)

**Analog:** `rag-service/.../config/SchedulingConfig.java` lines 6-9. Add `@ConditionalOnProperty` for the switch. Define NO `TaskScheduler`/`Executor` bean.

```java
@Configuration
@EnableScheduling
public class SchedulingConfig { }
```

### `M/application/assistant/IndexSweep.java` (service, scheduled batch)

**Analog:** `rag-service/.../application/IndexWorker.java` lines 58-68:

```java
@Scheduled(initialDelayString = "${rag.worker.initial-delay}", fixedDelayString = "${rag.worker.poll-interval}")
public void tick() { ... runOnce(); }
```
Use `${app.assistant.sweep.initial-delay}` / `${app.assistant.sweep.interval}`. Class is deliberately not `@Transactional` (IndexWorker line 23 comment: slow network call must not hold a DB connection). Body per RESEARCH Finding 3: `index.ensureCollections(); releases.search("").forEach(r -> syncer.sync(r.id()));`.

### `M/application/assistant/IndexSyncer.java` and `IndexInvalidationListener.java`

**Executor analog:** `M/application/EnrichmentService.java` lines 60-66 (`@Service`, `private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();`, slf4j `log`). Same field in `RenderService.java:42`. No bean, no `@Async`.

```java
private static final Logger log = LoggerFactory.getLogger(EnrichmentService.class);
private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
```

**Listener:** no analog exists. Use RESEARCH Code Examples lines 422-433 (`@TransactionalEventListener(phase = AFTER_COMMIT)`, `queued.add` coalescing, one shared `ReentrantLock` in the syncer). Failure logging: metadata only (collection, id, status), never text or key.

### Persistence adapters publishing events (modify)

**Analog:** the files themselves. Constructor-injected, plain `@Component` (`ReleaseRepositoryAdapter.java` lines 45-67; `DocumentSnapshotRepositoryAdapter.java` lines 23-33). Add `ApplicationEventPublisher publisher` to both constructors (only Spring builds them). Mutating `@Transactional` methods to end with `publisher.publishEvent(new ReleaseIndexInvalidated(releaseId))`:

`ReleaseRepositoryAdapter`: `create` (l.69), `addItems` (100), `removeItem` (120), `replaceItemSnapshots` (132), `saveEnrichment` (148), `freeze` (233), `delete` (244), `addManualItem` (251), `updateManualItem` (268), `removeManualItem` (283). `DocumentSnapshotRepositoryAdapter.create` (35). Code example: RESEARCH lines 408-419. Do not publish from read methods or services (services hold no transaction; the listener would silently drop it).

**Read-only addition:** `releasesContaining(ticketKey)` follows `ReleaseService.get/search` (delegates to `ReleaseRepository`) and a new `ReleaseTicketItemJpaRepository.findByTicketKey` derived query beside the existing `deleteByReleaseIdAndTicketKey`.

### `M/adapters/rag/RagIndexAdapter.java` (adapter, request-response)

**Analog:** `M/adapters/ado/AdoRestAdapter.java` (constructor takes `RestClient`, base URL, limits, as seen in `AdoRestAdapterTest.adapter(...)` lines 57-64: `new AdoRestAdapter(RestClient, baseUrl, maxPages)`). Same shape: `RagIndexAdapter(RestClient ragIndexRestClient, ...)`, conditional on the switch. Errors mapped at the adapter boundary into domain outcomes (ADO: `AdoAccessException` with `AdoFailureReason`; messages never contain the secret, test lines 66-71). Treat DELETE 404 as success.

### `M/adapters/rag/RagChatAdapter.java` (streaming)

Only the client shell has an analog (above). SSE parsing: no analog in either module; use RESEARCH Finding 6 (`retrieve().body(InputStream)` plus a ~40-line line parser). Hold until 11-06 publishes `openapi.json`. Keep the signed tool turn as the received `JsonNode`, never re-serialised (Pitfall 8).

### Tools (`M/application/assistant/tools/*`)

**Analog for service shape:** `M/application/PromotionService.java` lines 12-25: `@Service`, final fields, constructor injection, one-line read delegating (`history(UUID) -> promotions.load`). Tool classes: `@Component`, conditional on switch, depend only on the allowlisted services (`ReleaseService`, `PromotionService`, `EnvironmentLadderService`, `DashboardService`, `DocumentReadinessService`, `DocumentGenerationService`, `ActorResolutionService`, `Clock`). `DashboardService` public nested records (`Summary`, `DashboardView`, `DeploymentPage`, lines 40-53) show the existing result shapes to map from. Map explicitly to small JSON-able records, never return `DocumentSnapshot.markdown`, `note`, or `correctionReason`; absent data stays `null`.

### `T/ArchitectureBoundaryTest.java` (extend)

**Analog:** itself. Existing structure (lines 16-26): one static `classes` import with `DO_NOT_INCLUDE_TESTS`, one `@Test` per rule, `allowEmptyShould(true)` (the vacuity risk, hence the required positive cases):

```java
private static final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.evision.releasemgmt");
...
ArchRule rule = ArchRuleDefinition.noMethods()
        .that().areDeclaredIn(PromotionRepository.class)
        .should().haveNameMatching("(update|delete|remove|save).*")
        .allowEmptyShould(true);
rule.check(classes);
```
Copy the `*ExposesNoMutation` style (lines 105-120) for `RagChatPort`. New rules use static helpers taking `JavaClasses` so the same code runs on real tools and on fixtures imported via `new ClassFileImporter().importClasses(...)`; assert `AssertionError` on fixtures and tool-set size equals 5. Allowlist condition sketch: RESEARCH lines 446-465. The test jar `domain`/`ports` rule (lines 20-26) already forbids Spring/Jackson in `domain` and `ports`, so port records must be framework-free.

### Tests

- **WireMock adapter tests:** `T/adapters/ado/AdoRestAdapterTest.java` lines 36-72: `@WireMockTest`, build the adapter with `WireMockRuntimeInfo.getHttpBaseUrl()`, same `JdkClientHttpRequestFactory` timeouts, `assertThat(e.getMessage()).doesNotContain(secret)` for key hygiene.
- **Testcontainers ITs** (`IndexAfterCommitIT`, `AssistantSwitchOffIT`): `T/adapters/persistence/DocumentSnapshotPersistenceIT.java` lines 33-50:

```java
@SpringBootTest
@Testcontainers
class DocumentSnapshotPersistenceIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);
```
  Autowire the port interfaces and `JdbcTemplate`; clean up in `@AfterEach`. For the switch-off IT, run with the default (`app.assistant.enabled=false`) and assert `getBeanNamesForType` is empty.
- Service unit tests construct with `new XService(fakes...)`; adding `ApplicationEventPublisher` only to persistence adapters keeps them untouched.

## Shared Patterns

### Switch-off means not constructed
**Apply to:** every assistant bean (properties registration, `RagRestClientConfig`, scheduling config, adapters, listener, syncer, sweep, `AssistantService`, tools): `@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")`. No existing `@ConditionalOnProperty` in `src/main` (grep found none), so this is a new pattern; do not use `@Profile`. Persistence adapters keep publishing events unconditionally (harmless without a listener).

### Config keys in both yml files
**Source:** `src/main/resources/application.yml` lines 43-47 and `src/test/resources/application.yml` lines 23-27 (`ado:` block, nested under `app:`); env defaulting style `${RM_JIRA_DEFAULT_BASE_URL:https://example.atlassian.net}` (main yml line 33), durations as ISO `PT5S`. Add `app.assistant.*` to BOTH (the test yml shadows main's), enabled `${RM_ASSISTANT_ENABLED:false}`, api-key `${RM_ASSISTANT_API_KEY:}`; the context must start with the switch off and no secrets.

### Compose and .env
**Source:** `compose.app.yaml` lines 53-62 (`RM_CREDENTIAL_KEY: ${...}`, `RM_JIRA_DEFAULT_BASE_URL: ${...:-default}` in the app `environment` block) and `.env.example` lines 17-45 (commented, one var per block). Add `RM_ASSISTANT_ENABLED: ${RM_ASSISTANT_ENABLED:-false}`, `RM_ASSISTANT_BASE_URL: http://rag-service:8081`, `RM_ASSISTANT_API_KEY: ${RM_ASSISTANT_API_KEY:-}` (optional, no `:?`). No `depends_on: rag-service`.

### Logging and error hygiene
**Source:** `EnrichmentService` slf4j `private static final Logger log`. Metadata only (collection, id, status, tool name, round, duration); never question/answer/arguments/results/api key.

### Async without replacing Boot defaults
**Source:** `EnrichmentService.java:66`, `RenderService.java:42` private `Executors.newVirtualThreadPerTaskExecutor()`; rag-service `SchedulingConfig`. Never declare `TaskScheduler`, `TaskExecutor` or `@Bean Executor`.

### Build/test environment
`JAVA_HOME=C:/Users/ucs/.jdks/jdk-25` per Maven call, `-P '!frontend'`, `mvn clean` after the wave that adds the new ports/event record, one build at a time.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `IndexInvalidationListener` | component | event-driven | No `@TransactionalEventListener` or `ApplicationEventPublisher` use exists in the main app; use RESEARCH Code Examples |
| `RagChatAdapter` SSE parsing | adapter | streaming | No SSE/streaming client in either module; use RESEARCH Finding 6 and Phase 11 research wire shapes |
| `AssistantService` tool loop | service | request-response loop | No orchestrator loop exists; use the pseudocode in RESEARCH Finding 6 |
| `@ConditionalOnProperty` wiring | config | - | None in `src/main`; follow Spring Boot docs |
| `@ConfigurationProperties` record in the main app | config | - | Only rag-service uses it (`ChatProperties`); main app uses `@Value` |

## Metadata

**Analog search scope:** `src/main/java/com/evision/releasemgmt/{config,application,adapters/persistence,adapters/ado,ports}`, `src/test/java/.../{ArchitectureBoundaryTest,adapters/ado,adapters/persistence}`, `rag-service/src/main/java/.../{config,application}`, both `application.yml`, `compose.app.yaml`, `.env.example`
**Files scanned:** about 25
**Pattern extraction date:** 2026-10-15
