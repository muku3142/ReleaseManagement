# Phase 10: RAG Chat & Citations - Pattern Map

**Mapped:** 2026-10-13
**Files analyzed:** 41 (new or modified, from 10-RESEARCH.md "Codebase mapping" and the 7+1 plan split)
**Analogs found:** 36 / 41 (5 have no analog; use RESEARCH.md)

All paths below are relative to `rag-service/` unless absolute. `M` = `src/main/java/com/evision/ragservice`, `T` = `src/test/java/com/evision/ragservice`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `M/domain/ChatMessage, GenerationOptions, Completion, Cancellation` | domain model | transform | `M/domain/SearchHit.java` (record), `EmbeddingPurpose.java` (enum) | role-match |
| `M/domain/TooManyRequestsException, ModelNotAvailableException, OllamaUnavailableException, TextTooLargeException, MixedEmbeddingModelsException` | domain exception | request-response | `M/domain/CollectionNotFoundException.java`, `EmbeddingUnavailableException` | exact |
| `M/ports/ChatPort.java` | port | streaming | `M/ports/EmbeddingPort.java` | exact |
| `M/ports/EventSink.java` | port | event-driven | `M/ports/EmbeddingPort.java` | partial |
| `M/ports/ChunkSearch.java` (modify) | port | CRUD read | itself | exact |
| `M/domain/SearchHit.java` (+collectionId) | domain model | transform | itself | exact |
| `M/adapters/persistence/ChunkSearchRepositoryAdapter.java` (modify) | persistence adapter | CRUD read | itself | exact |
| `M/adapters/ollama/OllamaChatAdapter.java` | outbound adapter | streaming (NDJSON) | `M/adapters/ollama/OllamaEmbeddingAdapter.java` | role-match (streaming is new) |
| `M/config/OllamaRestClientConfig.java` (+stream bean) | config | request-response | itself | exact |
| `M/config/ChatProperties.java` | config | config | `OllamaRestClientConfig` `@Value` style (deviation allowed) | partial |
| `M/config/GenerationConfig.java` | config | batch/executor | `M/config/SchedulingConfig.java` | role-match |
| `M/application/GenerationGate.java` | service | in-memory state | `M/application/IndexWorker.java` (volatile state) | partial |
| `M/application/IndexWorker.java` (modify tick) | worker | batch | itself | exact |
| `M/application/CollectionService.java` (+requireAll) | service | CRUD read | itself (`require`) | exact |
| `M/application/SearchService.java` (wrap id in List) | service | request-response | itself | exact |
| `M/application/ChatService.java`, `TransformService.java` | service | streaming orchestration | `M/application/SearchService.java` (validate, embed, retrieve) | role-match |
| `M/application/PromptBuilder, CitationBuilder, RefusalGuard, TokenEstimator` | utility | transform | `M/domain/Chunker.java` | role-match |
| `M/adapters/web/ChatController.java`, `TransformController.java` | controller | streaming (SSE) | `M/adapters/web/SearchController.java` | role-match |
| `M/adapters/web/SseSink.java` | adapter | streaming | none | no analog |
| `M/adapters/web/dto/ChatRequest, TransformRequest, event records` | DTO | request-response | `dto/SearchRequest.java`, `dto/SearchResponse.java` | exact |
| `M/adapters/web/GlobalExceptionHandler.java` (+5 handlers) | controller advice | request-response | itself | exact |
| `src/main/resources/application.yml`, `src/test/resources/application.yml` | config | config | themselves | exact |
| `compose.app.yaml`, `.env.example`, `docs/docker.md` (repo root) | config | config | existing rag-service env entries | exact |
| `pom.xml` (golden profile) | config | config | existing surefire block (lines 114-122) | partial |
| `openapi.json` + `T/adapters/web/OpenApiContractIT.java` | contract | request-response | itself | exact |
| `T/ArchitectureBoundaryTest.java` (+2 rules) | test | n/a | itself | exact |
| `T/application/GenerationGateTest, PromptBuilderTest, CitationBuilderTest, RefusalGuardTest, ChatServiceTest` | unit test | transform | `T/application/CollectionServiceTest.java` | exact |
| `T/adapters/ollama/OllamaChatAdapterTest.java` | adapter test | streaming | `T/adapters/ollama/OllamaEmbeddingAdapterTest.java` | exact (+ JDK HttpServer fake, no analog) |
| `T/adapters/web/ChatApiIT, TransformApiIT` | MockMvc IT | request-response | `T/adapters/web/SearchApiIT.java` | exact |
| `T/adapters/web/SseStreamingIT.java`, `LogHygieneIT.java` | real-server IT | streaming | `T/adapters/web/RawHttpSecurityIT.java` | exact (SSE parsing new) |
| `T/adapters/persistence/ChunkSearchPersistenceIT.java` (modify) | persistence IT | CRUD | itself | exact |
| `T/application/IndexWorkerIT.java` (+pause case) | IT | batch | itself | exact |
| `src/test/resources/golden/**`, `GoldenChatIT` | fixture + live IT | streaming | `SearchApiIT` seeding helpers | partial |
| curl SSE smoke script | script | streaming | repo-root `devstatus-capture.sh` (untracked) | weak |

## Pattern Assignments

### `M/ports/ChatPort.java` (port, streaming)
**Analog:** `M/ports/EmbeddingPort.java` (lines 1-15). Framework-free (ArchUnit rule 1); javadoc states which domain exceptions are thrown; provider-neutral.
```java
package com.evision.ragservice.ports;
import com.evision.ragservice.domain.EmbeddingPurpose;
import java.util.List;
public interface EmbeddingPort {
    /** Throws EmbeddingUnavailableException on an outage and EmbeddingRejectedException on a content error. */
    List<float[]> embed(String model, EmbeddingPurpose purpose, List<String> inputs);
    boolean isAvailable();
}
```
Copy: one interface, one line javadoc naming the thrown domain exceptions. `assertReady()` throws `ModelNotAvailableException`/`OllamaUnavailableException`; `stream(...)` takes `Cancellation` and a token callback (no `java.net`, no Spring types).

### `M/adapters/ollama/OllamaChatAdapter.java` (outbound adapter, streaming)
**Analog:** `M/adapters/ollama/OllamaEmbeddingAdapter.java`

**Imports and construction** (lines 1-48): `@Component`, `@Value("${rag.ollama.base-url}")`, health client built inside the constructor with HTTP/1.1 pinned:
```java
JdkClientHttpRequestFactory healthFactory = new JdkClientHttpRequestFactory(
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(healthTimeout).build());
healthFactory.setReadTimeout(healthTimeout);
this.healthClient = RestClient.builder().requestFactory(healthFactory).build();
```
New adapter: inject the stream client by qualifier (`ollamaStreamClient`); keep health client construction as above for `/api/show`. Both existing `RestClient` injection points need `@Qualifier`/distinct names (research table, "Stream client bean").

**Call + error translation pattern** (lines 72-88, 108-118): wrap in `try { ... } catch (RestClientException e) { throw translate(e); }` and a pattern-matching `switch` mapping `HttpClientErrorException.NotFound`, `HttpServerErrorException`, `ResourceAccessException` to domain exceptions with fixed messages (never response bodies):
```java
private static RuntimeException translate(RestClientException e) {
    return switch (e) {
        case HttpClientErrorException.NotFound notFound -> new EmbeddingUnavailableException("Embedding model not available", e);
        case HttpServerErrorException server -> new EmbeddingUnavailableException("Embedding server error", e);
        case ResourceAccessException access -> new EmbeddingUnavailableException("Embedding server unreachable", e);
        default -> new EmbeddingUnavailableException("Embedding call failed", e);
    };
}
```
Chat mapping per RESEARCH C5: show 404 or no `completion` capability -> `ModelNotAvailableException`; connect/timeout/5xx -> `OllamaUnavailableException`; `HttpTimeoutException` after headers -> deadline/failed, not unavailable.

**Wire records** (lines 120-125): private nested records, response with `@JsonIgnoreProperties(ignoreUnknown = true)` from `com.fasterxml.jackson.annotation` (Jackson 3 split: annotations stay `com.fasterxml`, `ObjectMapper` is `tools.jackson.databind`). Needed so `message.thinking` and `prompt_eval_cached_count` are ignored.

**Streaming read loop** has no analog; use RESEARCH "Corrections" (check `cancel.isCancelled()` after the loop, before "ended without done") and `RestClient.exchange(...)`.

---

### `M/config/OllamaRestClientConfig.java` (modify: add stream bean)
**Analog:** itself (lines 15-24)
```java
@Bean
RestClient ollamaRestClient(@Value("${rag.ollama.connect-timeout}") Duration connectTimeout,
                            @Value("${rag.ollama.read-timeout}") Duration readTimeout) {
    HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(connectTimeout).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(readTimeout);
    return RestClient.builder().requestFactory(requestFactory).build();
}
```
Add `ollamaStreamClient` identical in shape with `setReadTimeout(deadline.plusSeconds(5))` (RESEARCH C1 code example).

---

### `M/ports/ChunkSearch.java`, `domain/SearchHit.java`, `adapters/persistence/ChunkSearchRepositoryAdapter.java` (modify)
**Analog:** themselves. Current port line 10: `List<SearchHit> search(UUID collectionId, String embeddingModel, float[] queryVector, MetadataFilter filter, int k);` becomes `List<UUID> collectionIds`. `SearchHit` (lines 5-6) gains `UUID collectionId`.

Adapter SQL, change line 32/35 only; leave the filter loop (lines 37-49) untouched:
```java
SELECT d.external_id, d.title, d.url, d.metadata, c.ordinal, c.content,
       1 - (c.embedding <=> CAST(:q AS vector)) AS score
FROM chunk c JOIN document d ON d.id = c.document_id
WHERE c.collection_id = :collectionId AND c.embedding_model = :model
```
-> `c.collection_id IN (:collectionIds)`, add `c.collection_id` to SELECT, bind `.param("collectionIds", collectionIds)` and add `rs.getObject("collection_id", UUID.class)` to the `new SearchHit(...)` mapper (lines 58-65). Callers to update: `SearchService` line 46 (`chunks.search(List.of(target.id()), ...)`), `T/adapters/persistence/ChunkSearchPersistenceIT` (~line 93). `SearchResponse.Hit.from` (dto lines 12-15) ignores the new component, so `/search` output and `openapi.json` are unchanged.

---

### `M/application/CollectionService.java` (+ `requireAll`)
**Analog:** itself, line 53-55:
```java
public DocumentCollection require(UUID clientId, String name) {
    return collections.find(clientId, name).orElseThrow(() -> new CollectionNotFoundException(name));
}
```
Add `requireAll(UUID clientId, List<String> names)` = `names.stream().distinct().map(n -> require(clientId, n)).toList()`. Ownership first, mixed-model 400 after (Pitfall 7).

---

### `M/application/ChatService.java`, `TransformService.java` (service)
**Analog:** `M/application/SearchService.java`

**Validation + retrieval order** (lines 36-47) to copy: resolve collections first, `MetadataRules.rejectNul`, bound `k` with `InvalidContentException`, `MetadataRules.parseFilter(filter)`, then embed with `EmbeddingPurpose.QUERY`:
```java
DocumentCollection target = collections.require(clientId, collection);
MetadataRules.rejectNul("query", query);
int limit = k == null ? defaultK : k;
if (limit < 1 || limit > maxK) { throw new InvalidContentException("k must be between 1 and " + maxK); }
MetadataFilter parsed = MetadataRules.parseFilter(filter);
float[] vector = embeddings.embed(target.embeddingModel(), EmbeddingPurpose.QUERY, List.of(query)).getFirst();
return chunks.search(target.id(), target.embeddingModel(), vector, parsed, limit);
```
Constructor style: `@Service`, collaborators then `@Value("${rag....}")` ints (lines 26-34); or one `ChatProperties` record (research allows either). Split `prepare` (request thread, throws 400/404/413/429/503) from `run` (executor).

**Logging convention** from `IndexWorker` lines 120-121 and `GlobalExceptionHandler` line 75: log `e.getClass().getSimpleName()` never `getMessage()`; metadata only.
```java
log.error("Indexing document {} failed with {}; retrying in {}", doc.id(), e.getClass().getSimpleName(), lease);
```

---

### `M/application/IndexWorker.java` (modify `tick()` only)
**Analog:** itself, lines 56-62. Add gate as constructor arg (no test constructs it by hand) and one guard; do not touch `runOnce()`:
```java
@Scheduled(initialDelayString = "${rag.worker.initial-delay}", fixedDelayString = "${rag.worker.poll-interval}")
public void tick() {
    if (gate.anyInFlight()) { return; }   // new
    if (Instant.now().isBefore(notBefore)) { return; }
    runOnce();
}
```
Existing in-class mutable state idiom (lines 37-38): `private volatile ...` fields; `GenerationGate` uses `ConcurrentHashMap<UUID, AtomicInteger>` instead.

---

### `M/application/PromptBuilder, CitationBuilder, RefusalGuard, TokenEstimator` (utility, transform)
**Analog:** `M/domain/Chunker.java` (pure, constructed with limits, static helpers like `Chunker.withHeader`), used in `IndexWorker` line 49 `new Chunker(maxChars, overlapChars)` and unit-tested without Spring (`T/domain/ChunkerTest`). Keep these plain classes with no framework imports, constructor-injected limits, tested by direct construction.

---

### `M/adapters/web/ChatController.java`, `TransformController.java` (controller, SSE)
**Analog:** `M/adapters/web/SearchController.java` (lines 16-31)
```java
@RestController
public class SearchController {
    @PostMapping("/v1/collections/{collection}/search")
    public SearchResponse search(
            @Parameter(hidden = true) @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) UUID clientId,
            @PathVariable @Pattern(regexp = CollectionController.NAME_PATTERN) String collection,
            @Valid @RequestBody SearchRequest request) { ... }
}
```
Copy: `@RestController`, no class-level `@Validated`, `@Valid @RequestBody`, `@Parameter(hidden = true) @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) UUID clientId`, `@Pattern(regexp = CollectionController.NAME_PATTERN)` for collection names. Return `ResponseEntity<SseEmitter>`, content type on the entity, **no** `produces =` on the mapping. The emitter flow itself has no analog (RESEARCH Pattern 4 / AI-SPEC section 3).

### `M/adapters/web/dto/ChatRequest, TransformRequest` and event records
**Analog:** `dto/SearchRequest.java` (lines 7-10): record with Bean Validation and a `public static final int MAX_..._CHARS` constant:
```java
public record SearchRequest(@NotBlank @Size(max = SearchRequest.MAX_QUERY_CHARS) String query,
                            Map<String, Object> filter, Integer k) {
    public static final int MAX_QUERY_CHARS = 2000;
}
```
`ChatRequest`: `collections` (`List<@Pattern(NAME_PATTERN) String>` 1..10), `question`, `history`, `filter` (same `Map<String,Object>`), `k`. Response/event records follow `SearchResponse` (nested records plus static `from`).

### `M/adapters/web/GlobalExceptionHandler.java` (+5 handlers)
**Analog:** itself, lines 33-82. Domain exception -> `ProblemDetail` with `reason` via the private `problem(status, title, detail, reason)` helper (lines 125-131):
```java
@ExceptionHandler(EmbeddingUnavailableException.class)
public ProblemDetail handleEmbeddingUnavailable(EmbeddingUnavailableException ex) {
    log.warn("Embedding backend unavailable: {}", ex.getClass().getSimpleName());
    return problem(HttpStatus.SERVICE_UNAVAILABLE, "Embedding unavailable", ex.getMessage(), "EMBEDDING_UNAVAILABLE");
}
```
Add `TOO_MANY_REQUESTS` 429 (return `ResponseEntity<ProblemDetail>` with `Retry-After: 5`, see RESEARCH example; the one handler that deviates from returning bare `ProblemDetail`; precedent for `ResponseEntity` at line 113), `OLLAMA_UNAVAILABLE` 503, `MODEL_NOT_AVAILABLE` 503, `TEXT_TOO_LARGE` 413, `MIXED_EMBEDDING_MODELS` 400. Note `PayloadTooLargeException` -> `PAYLOAD_TOO_LARGE` already exists at line 63 (different reason).

### Domain exceptions
**Analog:** `M/domain/CollectionNotFoundException.java` (lines 3-8): `extends RuntimeException`, constructor builds fixed message, no framework imports.

---

### `M/config/GenerationConfig.java`
**Analog:** `M/config/SchedulingConfig.java` (`@EnableScheduling` exists, so Boot's `taskScheduler` likely available; verify per RESEARCH A3) and `OllamaRestClientConfig` (`@Configuration` + `@Bean` package-private methods).

### Config files
**Analog:** `src/main/resources/application.yml` `rag:` tree (lines under `rag.ollama`, `rag.search`, `rag.worker`): add `rag.chat.*` with the research defaults (`model: ${RAG_CHAT_MODEL:qwen3:4b}`, `deadline: PT120S`, ...). Test `application.yml` shadows main's: add the same keys with `deadline: PT3S`, `heartbeat-interval: PT1S`. Duration style is ISO (`PT2S`), env style `${RAG_X:default}`. `compose.app.yaml`/`.env.example`: mirror the existing `RAG_OLLAMA_BASE_URL` entry.

### `pom.xml` (golden profile)
**Analog:** surefire block lines 114-122 (explicit `<includes>` for `*Test`, `*Tests`, `*IT`). Add the `excludedGroups` property and profile per RESEARCH "Golden exclusion"; verify with two runs (A4).

---

### Tests

**`T/application/*Test` (unit, fakes/mocks)** - Analog `T/application/CollectionServiceTest.java`: plain JUnit + AssertJ + Mockito `mock(Port.class)`, direct constructor, no Spring:
```java
private final CollectionStore store = mock(CollectionStore.class);
private final CollectionService service = new CollectionService(store, mock(DocumentStore.class), "nomic-embed-text", List.of(...));
@Test void unknownModelIsRejected() { assertThatThrownBy(() -> service.put(...)).isInstanceOf(...); }
```
Use fake `ChatPort`/`EventSink` as RESEARCH suggests for `ChatServiceTest`.

**`T/adapters/ollama/OllamaChatAdapterTest`** - Analog `OllamaEmbeddingAdapterTest` (lines 1-80): `@WireMockTest`, build adapter by hand with an HTTP/1.1 `JdkClientHttpRequestFactory` and short timeouts, `stubFor(post(urlEqualTo(...)).willReturn(aResponse()...))`, `verify(postRequestedFor(...).withRequestBody(matchingJsonPath(...)))`. It also has a `SECRET_TEXT` constant for no-leak assertions: reuse for the exception-message test. JDK `HttpServer` fake: no analog (RESEARCH code example).

**`T/adapters/web/ChatApiIT`, `TransformApiIT`** - Analog `T/adapters/web/SearchApiIT.java` (lines 49-150): `@SpringBootTest @AutoConfigureMockMvc @Testcontainers`, `WireMockExtension` for Ollama with `@DynamicPropertySource` override of `rag.ollama.base-url`, `PgVectorImage.IMAGE` container with `@ServiceConnection`, `@BeforeEach` wipes tables in FK order (`chunk`, `document`, `collection`, `client_app`), registers two clients (tenant canary), seeds via `documents.upsert` + `queue.claim` + `queue.complete` with hand-built unit vectors, and stubs `/api/embed`:
```java
for (ClaimedDocument claimed : queue.claim(10, Duration.ofMinutes(10))) {
    queue.complete(claimed.id(), claimed.contentHash(), MODEL, List.of(new ChunkEmbedding(0, claimed.text(), vector)));
}
```
Add a `WireMock` stub for `/api/show` and `/api/chat`. Auth header: `HttpHeaders.AUTHORIZATION, "Bearer " + key`.

**`T/adapters/web/SseStreamingIT`, `LogHygieneIT`** - Analog `T/adapters/web/RawHttpSecurityIT.java` (lines 28-80): `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Testcontainers`, `@Value("${local.server.port}") int port`, `HttpClient`, `HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))`, `createClient` via admin key to obtain a client key. Read the SSE body with `BodyHandlers.ofLines()`/`ofInputStream` (new). Add the no-key 401 case here.

**`T/ArchitectureBoundaryTest`** - Analog itself, lines 51-57 (rule shape to copy for the two new rules):
```java
@Test
void restClientLivesOnlyInTheOllamaAdapterAndConfig() {
    noClasses().that().resideOutsideOfPackages("..adapters.ollama..", "..config..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.web.client..")
            .allowEmptyShould(true).check(classes);
}
```
Add `java.net.http..` equivalent and `application` must not depend on `org.springframework.web.servlet..`. Existing rule 1 (domain/ports free of Spring/jakarta/jackson) already covers the new domain and port types.

**`T/application/IndexWorkerIT`, `T/adapters/persistence/ChunkSearchPersistenceIT`** - extend in place (pause case calls `tick()`; two-collection plus foreign-collection case).

**`openapi.json`** - regenerate: `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25 mvn -f rag-service/pom.xml test -Dtest=OpenApiContractIT -Drag.contract.update=true`; one plan only.

## Shared Patterns

### Tenancy (client scoping)
**Source:** `ApiKeyFilter.CLIENT_ID_ATTRIBUTE` (`ApiKeyFilter.java` line 33) read via `@RequestAttribute` in controllers; `CollectionService.require` for ownership; foreign and unknown both throw `CollectionNotFoundException` -> 404. **Apply to:** ChatController, TransformController (no collections), ChatService.

### Error shape
**Source:** `GlobalExceptionHandler.problem(...)` (lines 125-131), `reason` property. **Apply to:** all new domain exceptions. Detail strings must not contain request text (see `fieldError` comment at line 120).

### Logging
**Source:** `IndexWorker` lines 120-121, `GlobalExceptionHandler` line 75: `e.getClass().getSimpleName()`, counts and ids only. **Apply to:** ChatService, TransformService, OllamaChatAdapter, SseSink.

### Input bounds
**Source:** `SearchRequest.MAX_QUERY_CHARS`, `SearchService` k check, `MetadataRules.parseFilter` / `rejectNul`. **Apply to:** ChatRequest, TransformRequest, ChatService.prepare.

### Filters
`RequestSizeLimitFilter` (`@Order(0)`) and `ApiKeyFilter` run on the initial request only (OncePerRequestFilter defaults); no change needed.

### Config injection
`@Value("${rag.x.y}")` constructor params with `Duration`/`int` (SearchService, IndexWorker, OllamaEmbeddingAdapter). `ChatProperties` record is an allowed deviation; do not scatter keys.

### Test infra
`PgVectorImage.IMAGE` + `@ServiceConnection` Testcontainer; WireMock via `WireMockExtension` + `@DynamicPropertySource` (SearchApiIT lines 56-69); test `application.yml` shadows main's.

### Jackson 3
`tools.jackson.databind.ObjectMapper`, `tools.jackson.core.type.TypeReference` (ChunkSearchRepositoryAdapter lines 12-13); annotations remain `com.fasterxml.jackson.annotation`.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `adapters/web/SseSink.java` (+ `EventSink` semantics) | adapter | SSE streaming | No SseEmitter use exists; use RESEARCH Spring findings and Pattern 4 |
| NDJSON read loop in `OllamaChatAdapter.stream` | adapter | streaming | Only non-streaming `RestClient` calls exist |
| JDK `HttpServer` fake Ollama in tests | test helper | streaming | WireMock only today; RESEARCH code example |
| Golden fixtures (`src/test/resources/golden/**`, `GoldenChatIT` with `@Tag("golden")`) | fixture | batch | No `src/test/resources` data files besides yml; seeding helpers from `SearchApiIT` are reusable |
| Curl SSE smoke script | script | streaming | Repo-root capture scripts (`devstatus-capture.sh`, `phase6-capture.sh`) are untracked and for other purposes |

## Metadata

**Analog search scope:** `rag-service/src/main/java/**`, `rag-service/src/test/java/**`, `rag-service/src/*/resources/application.yml`, `rag-service/pom.xml`
**Files scanned:** about 20 read in full or in part out of 74 Java files
**Pattern extraction date:** 2026-10-13
