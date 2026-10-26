# Phase 9: RAG Service Foundation & Index - Pattern Map

**Mapped:** 2026-10-13
**Files analyzed:** 27 new/modified (rag-service is a new module; main app touched only for image swap and compose)
**Analogs found:** 21 / 27 (6 have no in-repo analog: see bottom)

Paths below are relative to `D:\IdeaProjects\ReleaseManagement`. Main-app source root: `src/main/java/com/evision/releasemgmt` (abbreviated `MAIN/`). The main app is the model to copy in SHAPE, not code to share.

## Key divergences of rag-service from the main app (read first)

| Main app | rag-service | Consequence |
|---|---|---|
| `spring-boot-starter-data-jpa`, entities, `*JpaRepository` | `spring-boot-starter-jdbc` + `JdbcClient`, native SQL | No analog for persistence adapters' bodies. Copy only class-level shape (`@Component` adapter implements port, `@Transactional` on adapter methods). `JdbcClient` is NOT used anywhere in the main app (only `JdbcTemplate` in ITs). |
| `spring-boot-starter-security`, JWT cookie, `SecurityConfig` | No Spring Security. Servlet filter / `HandlerInterceptor` with SHA-256 lookup | Auth is net-new. Do not copy `adapters/security/*`. |
| `@Value("${app.*}")` scalar injection | Same convention works (no `@ConfigurationProperties` records exist in main) | Keep `@Value` or a small record; main-app precedent is `@Value`. |
| No `@Scheduled` / `@EnableScheduling` anywhere in main | Worker is `@Scheduled` | Worker is net-new. |
| `spring-boot-docker-compose` runtime dep | Omit | Per RESEARCH. |
| Single DB `releasemgmt` | `ragdb`, user `rag` | New env names `RAG_DB_URL/USER/PASSWORD`. |
| `pom` has `frontend` profile + frontend-maven-plugin | Omit | No frontend. |

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `rag-service/pom.xml` | config | build | `pom.xml` | exact (minus JPA, Security, docs libs, frontend profile) |
| `rag-service/Dockerfile` | config | build | `Dockerfile` | exact (drop frontend, port 8081, readiness healthcheck) |
| `compose.app.yaml` (modify: image, initdb mount, `rag-service` service) | config | n/a | `compose.app.yaml` app service | exact |
| `compose.yaml` (modify: image, initdb mount, service-connection label) | config | n/a | itself | exact |
| `docker/initdb/10-ragdb.sh` | config | batch | none | no analog |
| `rag-service/src/main/resources/db/migration/V1__rag_schema.sql` | migration | CRUD | `src/main/resources/db/migration/V8__ado_credential.sql` (+ `V1__init_schema.sql`) | role-match (no pgvector/jsonb in main) |
| `rag-service/src/main/resources/application.yml` | config | n/a | `src/main/resources/application.yml` | exact |
| `rag-service/src/test/resources/application.yml` | config | n/a | `src/test/resources/application.yml` | exact (shadowing landmine) |
| `.../config/OllamaRestClientConfig.java` | config | request-response | `MAIN/config/JiraRestClientConfig.java` | exact |
| `.../adapters/ollama/OllamaEmbeddingAdapter.java` | adapter (outbound) | request-response | `MAIN/adapters/ado/AdoRestAdapter.java` | exact (error translation) |
| `.../ports/EmbeddingPort.java` + `domain` failure exception/enum | port/domain | request-response | `MAIN/ports/AdoRepositoryPort`, `domain/ado/AdoAccessException`, `AdoFailureReason` | exact |
| `.../adapters/persistence/*Repository.java` (client, collection, document, chunk, search) | adapter (persistence) | CRUD | `MAIN/adapters/persistence/AdoCredentialRepositoryAdapter.java` | role-match (JPA vs JdbcClient) |
| `.../adapters/web/*Controller.java` (admin, collection, document, search, status) | controller | request-response | `MAIN/adapters/web/AdoCredentialController.java` | role-match (no Principal) |
| `.../adapters/web/dto/*Request.java` | DTO | request-response | `MAIN/adapters/web/dto/UpdateAdoCredentialRequest.java` | exact |
| `.../adapters/web/GlobalExceptionHandler.java` | controller advice | request-response | `MAIN/adapters/web/GlobalExceptionHandler.java` | exact |
| `.../adapters/web/ApiKeyFilter.java` (auth) | middleware | request-response | none (main uses Spring Security) | no analog |
| `.../application/*Service.java` | service | CRUD | `MAIN/application/AdoCredentialService.java` | role-match |
| `.../domain/Chunker.java`, `ContentHash`, `MetadataFilter` | utility | transform | none (nearest style: `LogoValidator`, `ExportFilename`) | no analog |
| `.../application/IndexWorker.java` (`@Scheduled`) | service | batch/event-driven | none | no analog (partial: `AdoRestAdapter.awaitGate` backoff state) |
| `.../application/ApiKeyGenerator` (SecureRandom + SHA-256) | utility | transform | none | no analog |
| OpenAPI config / `OpenApiCoverageIT` | config/test | request-response | none | no analog |
| `ArchitectureBoundaryTest` | test | n/a | `src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java` | exact |
| `*PersistenceIT` (rag) | test | CRUD | `src/test/.../persistence/AdoCredentialPersistenceIT.java` | exact |
| `OllamaEmbeddingAdapterTest` (WireMock) | test | request-response | `src/test/.../adapters/ado/AdoRestAdapterTest.java` | exact |
| 18 existing `*IT` (modify: image constant) | test | n/a | `AdoCredentialPersistenceIT.java:29` | exact |
| `.env.example`, `docs/docker.md` (modify) | docs | n/a | existing | exact |

## Pattern Assignments

### `rag-service/pom.xml` (config)

**Analog:** `pom.xml` (root)

Copy parent, properties, surefire includes, and the test dependency block verbatim; drop JPA/Security/oauth2/render libs/docker-compose/frontend profile; swap in `spring-boot-starter-jdbc` and springdoc.

**Parent + properties** (pom.xml lines 7-28):
```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.1.0</version>
  <relativePath/>
</parent>
<groupId>com.evision</groupId>
<artifactId>release-management</artifactId>   <!-- rag: artifactId rag-service -->
<properties>
  <java.version>25</java.version>
  <maven.compiler.release>25</maven.compiler.release>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  <archunit.version>1.4.2</archunit.version>
  <wiremock.version>3.13.2</wiremock.version>
  <testcontainers.version>2.0.5</testcontainers.version>
</properties>
```

**Flyway pair + driver** (lines 47-54, 102-106): `spring-boot-starter-flyway` and `org.flywaydb:flyway-database-postgresql` (no version); `org.postgresql:postgresql` scope runtime.

**Test deps** (lines 114-157): `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, `archunit` `${archunit.version}`, `org.wiremock:wiremock-standalone` `${wiremock.version}`, `testcontainers-postgresql` + `testcontainers-junit-jupiter` `${testcontainers.version}`, `spring-boot-testcontainers`. Omit `spring-security-test`.

**Surefire includes** (lines 166-176): `**/*Test.java`, `**/*Tests.java`, `**/*IT.java` are all run by surefire (no failsafe split). Copy this.

---

### `rag-service/Dockerfile` (config)

**Analog:** `Dockerfile` (root). Context is `./rag-service`, so paths are `pom.xml`/`src` (no `frontend`).

**Reuse verbatim:** build stage (lines 7-30: `maven:3.9-eclipse-temurin-25`, `dependency:go-offline` with `--mount=type=cache,target=/root/.m2`, `-DskipTests package`, `java -Djarmode=tools -jar target/<artifact>-*.jar extract --layers --destination extracted`); runtime stage (33-56: `eclipse-temurin:25-jre`, curl install, non-root `app` uid 1001, four layered `COPY --from=build --chown=app:app`, rename to `/app/app.jar` via `find ... -name '<artifact>-*.jar'`); `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`; `SPRING_DOCKER_COMPOSE_ENABLED=false` may be dropped (dep not present); `ENTRYPOINT ["java","-jar","/app/app.jar"]`.

**Differences:**
- `EXPOSE 8081`.
- HEALTHCHECK must NOT hit `/actuator/health` (root line 70-71). Per RESEARCH Pitfall 4 use:
```dockerfile
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
  CMD curl -fsS http://localhost:8081/actuator/health/readiness || exit 1
```

---

### `compose.app.yaml` (modify)

**Analog:** same file. Existing postgres service lines 13-34, app service lines 36-62.

**Edits to postgres service:** `image: postgres:18` (line 14) -> `pgvector/pgvector:0.8.7-pg18-trixie`; add `RAG_DB_PASSWORD: ${RAG_DB_PASSWORD:-rag}` to `environment`; add under `volumes` (after line 26) `- ./docker/initdb:/docker-entrypoint-initdb.d:ro`. Keep the `pgdata:/var/lib/postgresql` mount and its comment (lines 23-26) untouched. Keep the loopback port line 21.

**New `rag-service` service: mirror `app` (lines 36-62):**
```yaml
  app:
    build:
      context: .
      dockerfile: Dockerfile
    image: release-management:0.1.0-SNAPSHOT
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      RM_DB_URL: jdbc:postgresql://postgres:5432/releasemgmt
      RM_DB_USER: releasemgmt
      RM_DB_PASSWORD: ${RM_DB_PASSWORD:-releasemgmt}
      RM_CREDENTIAL_KEY: ${RM_CREDENTIAL_KEY:?set RM_CREDENTIAL_KEY, e.g. openssl rand -base64 32}
    ports:
      - "${RM_PORT:-9292}:8080"
    restart: unless-stopped
```
rag version: `context: ./rag-service`, `image: rag-service:0.1.0-SNAPSHOT`, `RAG_*` env, `RAG_ADMIN_KEY: ${RAG_ADMIN_KEY:?set RAG_ADMIN_KEY, e.g. openssl rand -base64 32}` (fail-fast convention matches line 56-58; RESEARCH Open Question 2), `extra_hosts: ["host.docker.internal:host-gateway"]`, `ports: ["${RAG_PORT:-8081}:8081"]`. Update the header comment (line 10 "Requires RM_CREDENTIAL_KEY and RM_JWT_SECRET") to include `RAG_ADMIN_KEY`. Add no healthcheck-gated `depends_on` from `app` to `rag-service` (independent).

Note `pg_isready -U releasemgmt -d releasemgmt` (line 30) stays valid.

### `compose.yaml` (modify)

Entire file is 10 lines (`image: postgres:18`, env, `restart: always`, `ports: "5432:5432"`). Change image; add `RAG_DB_PASSWORD` env (the init script requires it via `${RAG_DB_PASSWORD:?}`, so a default is needed here: `RAG_DB_PASSWORD: rag`); add `volumes: ./docker/initdb:/docker-entrypoint-initdb.d:ro`; add `labels: org.springframework.boot.service-connection: postgres` (the image-name match for Boot docker-compose integration breaks otherwise). compose.yaml has no pgdata volume (ephemeral), so the init script always runs.

---

### `rag-service/src/main/resources/application.yml` and test `application.yml` (config)

**Analog:** `src/main/resources/application.yml` and `src/test/resources/application.yml`.

**Datasource/env convention** (main lines 4-7):
```yaml
spring:
  datasource:
    url: ${RM_DB_URL:jdbc:postgresql://localhost:5432/releasemgmt}
    username: ${RM_DB_USER:releasemgmt}
    password: ${RM_DB_PASSWORD:releasemgmt}
  flyway:
    enabled: true
  threads:
    virtual:
      enabled: true
management:
  endpoints:
    web:
      exposure:
        include: health
```
rag: same shape with `RAG_DB_*` and `ragdb`/`rag`. Drop `jpa`, `servlet.multipart`, `docker.compose`. Add `management.endpoint.health.probes.enabled: true` and `management.endpoint.health.group.readiness.include: readinessState,db` (RESEARCH Pitfall 4). Note `threads.virtual.enabled: true` is already the project convention (main line 23-25); with virtual threads and `@Scheduled`, fine.

**Custom property block convention** (main lines 31-35): namespace under `app:` with ISO-8601 durations and explicit timeouts, no magic numbers in code:
```yaml
app:
  jira:
    default-base-url: ${RM_JIRA_DEFAULT_BASE_URL:https://example.atlassian.net}
    connect-timeout: PT5S
    read-timeout: PT10S
```
rag: `rag.ollama.base-url: ${RAG_OLLAMA_BASE_URL:http://host.docker.internal:11434}`, `connect-timeout`, `read-timeout`, `batch-size: 32`; chunk size/overlap; `k` default/max; `max-body-bytes: 1048576`; worker backoff min/max (`PT1S`/`PT5M`), lease; admin key `${RAG_ADMIN_KEY}` (no default, as `app.credential.key: ${RM_CREDENTIAL_KEY}` line 64).

**Test yml landmine:** the test `application.yml` (lines 1-49) SHADOWS main's (same classpath name). It re-declares every `app.*` property with literal test values (e.g. line 13 `default-base-url: https://test.atlassian.net`, line 41 a literal base64 credential key). Every new `rag.*` property must be added to both, with the admin key a literal >= 32 chars, and `ollama.base-url` pointing at a placeholder (WireMock overrides via `@DynamicPropertySource`/constructor). The test yml has no datasource (comes from `@ServiceConnection`).

---

### `.../config/OllamaRestClientConfig.java` (config, request-response)

**Analog:** `MAIN/config/JiraRestClientConfig.java` (lines 1-25, copy almost verbatim):
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
Imports: `org.springframework.http.client.JdkClientHttpRequestFactory`, `org.springframework.web.client.RestClient`, `java.net.http.HttpClient`, `java.time.Duration`. Rag change: bean `ollamaRestClient`, properties `rag.ollama.*`. Read timeout must exceed a cold model load; a 32-chunk batch took 0.6 s warm, model reload 6.5 s (RESEARCH Pitfall 6).

---

### `.../adapters/ollama/OllamaEmbeddingAdapter.java` (adapter, request-response)

**Analog:** `MAIN/adapters/ado/AdoRestAdapter.java`

**Class shape / constructor injection** (lines 32-58): `@Component` implements port; `RestClient` + `@Value` base URL injected via constructor (tests construct it directly with a WireMock URL, see test excerpt below).
```java
@Component
public class AdoRestAdapter implements AdoRepositoryPort {
    private final RestClient adoRestClient;
    private final String baseUrl;
    public AdoRestAdapter(RestClient adoRestClient, @Value("${app.ado.base-url}") String baseUrl, ...) {
```
Note: ADO has a separate `AdoRestClientConfig` bean injected by type; with a single RestClient bean in rag-service this is unambiguous.

**Request building** (lines 120-131): `adoRestClient.get().uri(template, vars).header(...).accept(MediaType.APPLICATION_JSON).retrieve().onStatus(pred, handler)`. For Ollama use `.post().uri(baseUrl + "/api/embed").contentType(JSON).body(Map/record).retrieve()`; map `onStatus(400)` to the content-error exception BEFORE the generic translation. JSON type in this stack is `tools.jackson.databind.JsonNode` (Jackson 3; line 19), NOT `com.fasterxml`.

**Error translation to a domain reason** (lines 178-188), the core pattern to copy:
```java
private static AdoAccessException translate(RuntimeException e) {
    return switch (e) {
        case AdoAccessException ado -> ado;
        case HttpClientErrorException.NotFound ignored -> new AdoAccessException(AdoFailureReason.NOT_FOUND);
        case HttpClientErrorException.TooManyRequests ignored -> new AdoAccessException(AdoFailureReason.RATE_LIMITED);
        case HttpServerErrorException ignored -> new AdoAccessException(AdoFailureReason.ERROR);
        case ResourceAccessException ignored -> new AdoAccessException(AdoFailureReason.UNREACHABLE);
        case RestClientException ignored -> new AdoAccessException(AdoFailureReason.ERROR);
        default -> throw e;
    };
}
```
Rag mapping per RESEARCH Pattern 6 table: `ResourceAccessException`, `HttpServerErrorException`, 404 model-not-found => `OllamaUnavailableException` (outage, retry); `HttpClientErrorException.BadRequest` => `EmbeddingRejectedException` (content error, doc `FAILED`). Callers (worker) catch the two types. Keep exception messages free of text/keys (the test asserts `getMessage()` does not contain the secret, AdoRestAdapterTest lines 66-70; the rag equivalent: no document text in exceptions or logs).

**Adapter-wide backoff state** (line 49-50, 149-176) is the only in-repo precedent for in-memory time-gate state (`AtomicReference<Instant> notBefore`). The worker backoff is similar in spirit but implemented in the worker, not the adapter.

---

### `.../ports/EmbeddingPort.java`, `.../domain/*Exception.java` (port/domain)

**Analog:** `MAIN/ports/AdoRepositoryPort`, `MAIN/domain/ado/AdoAccessException`, `AdoFailureReason`. Ports and domain carry no framework imports (enforced by ArchitectureBoundaryTest). Domain exceptions are plain classes with a `reason()` enum accessor, so the web `GlobalExceptionHandler` can `switch` on it (see below). Read these two files before writing; they were not excerpted here.

---

### `.../adapters/persistence/*RepositoryAdapter.java` (adapter, CRUD, JdbcClient)

**Analog (shape only):** `MAIN/adapters/persistence/AdoCredentialRepositoryAdapter.java`

**What to copy** (lines 16-17, 31-33, 55-56): `@Component` class implementing a `ports.*` interface; `@Transactional` (from `org.springframework.transaction.annotation`) on write methods only; read methods un-annotated; constructor injection:
```java
@Component
public class AdoCredentialRepositoryAdapter implements AdoCredentialRepository {
    @Override
    @Transactional
    public void save(...) { ... }

    @Override
    public Optional<AdoCredentialStatus> findStatus() { ... }
```
**What differs:** body uses injected `JdbcClient` (`jdbc.sql("...:name...").param("name", v).update()/.query(mapper).optional()/.list()`) instead of `*JpaRepository`. There is NO JdbcClient precedent in the repo. `@Transactional` needs a `DataSourceTransactionManager`, which `spring-boot-starter-jdbc` auto-configures; transactions on adapter methods is the project convention (CONTEXT code_context). The worker's claim and write-back are two separate adapter methods, each `@Transactional`, with the Ollama call between them (never a `@Transactional` service wrapping the loop).

**Domain return types:** adapters map rows to `domain` records (see `AdoCredentialStatus`/`AdoCredential`); never leak row types through ports.

SQL for claim, guarded write-back, search, jsonb `@>` filters, `CAST(:q AS vector)`: take from RESEARCH Patterns 4 and 5 verbatim (no codebase analog).

---

### `.../adapters/web/*Controller.java` (controller, request-response)

**Analog:** `MAIN/adapters/web/AdoCredentialController.java`

**Imports + class shape** (lines 1-27):
```java
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/azure-devops")
public class AdoCredentialController {
    private final AdoCredentialService service;
    public AdoCredentialController(AdoCredentialService service) { this.service = service; }

    @PutMapping
    public AdoCredentialResponse update(@Valid @RequestBody UpdateAdoCredentialRequest request, Principal principal) {
        return toResponse(service.save(request.organization(), request.token(), request.expiresOn(), principal.getName()));
    }
```
Pattern: thin controller, `@Valid @RequestBody` record, delegates to an application service, returns a response DTO record, no try/catch (exceptions go to the advice). Rag differences: base path `/v1/...` (D-09-02 `PUT /v1/collections/{c}`, `PUT/DELETE /v1/collections/{c}/documents/{id}`, `POST .../search`, `POST .../reindex`, `GET /v1/status`, `/v1/admin/clients`); the authenticated client id comes from a request attribute set by the key filter (not `Principal`; there is no Spring Security). Return `ResponseEntity.accepted()` (202) for document PUT. Use `@PathVariable` with a `@Pattern`-constrained id (`[A-Za-z0-9._:-]{1,200}`) -- add `@Validated` on the controller for path-variable constraints.

### `.../adapters/web/dto/*Request.java` (DTO)

**Analog:** `MAIN/adapters/web/dto/UpdateAdoCredentialRequest.java` (lines 1-18): Java `record` with Bean Validation annotations on components (`@NotBlank`, `@Pattern`, `@Size`, `@NotNull`), regex constants referenced from the domain (`AdoRepoRef.ORGANIZATION_REGEX`). Rag: `PutDocumentRequest(@NotBlank title, @NotNull text, url, metadata)`, with the 1 MB UTF-8 byte check and NUL rejection done in the service/controller boundary (Bean Validation `@Size` counts chars, not bytes).

---

### `.../adapters/web/GlobalExceptionHandler.java` (controller advice)

**Analog:** `MAIN/adapters/web/GlobalExceptionHandler.java`

**Class shape + helper** (lines 45-48, 370-376):
```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ProblemDetail problem(HttpStatus status, String title, String detail, String reason) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle(title);
        pd.setDetail(detail);
        pd.setProperty("reason", reason);
        return pd;
    }
```
**Handler pattern** (lines 119-122 region, via grep: `@ExceptionHandler(ReleaseNotFoundException.class) public ProblemDetail handleReleaseNotFound(...) { return problem(HttpStatus.NOT_FOUND, "Release not found", "...", "RELEASE_NOT_FOUND"); }`) and the reason-switch pattern (lines 56-73, `switch (ex.reason()) { case UNAUTHORIZED -> problem(...) ... }`).

Rag handlers needed: `CollectionNotFound` (404, also for foreign collection per D-09-03), `DocumentNotFound` (404), `ModelNotAllowed` (400), `PayloadTooLarge` (413), `InvalidKey`/missing key (401, `reason: INVALID_API_KEY`), invalid input (400 via `ResponseEntityExceptionHandler` + `MethodArgumentNotValidException`; main already imports `FieldError`/`MethodArgumentNotValidException`, lines 33-34). Use `reason` codes in SCREAMING_SNAKE. Also `log.error` only metadata (see line 139 `log.error("Document export failed", ex)` precedent; never log text). Drop all release/Jira/ADO handlers. The 401 body for the filter must be a ProblemDetail too; main's `adapters/security/ProblemDetailAuthenticationEntryPoint.java` is the precedent for writing a ProblemDetail from outside MVC (read it; filter exceptions do not reach `@RestControllerAdvice`).

---

### `.../application/*Service.java` (service, CRUD)

**Analog:** `MAIN/application/AdoCredentialService.java` (not excerpted; read before planning the service tasks). Convention: application depends on `ports` and `domain` only (ArchUnit rule `applicationDoesNotDependOnAdapters`); no JPA types; transactions live on adapter methods, not services.

---

### `Chunker`, `ContentHash`, `MetadataFilter` (domain, transform)

No analog. Rules: pure Java in `..domain..`, no `org.springframework`/`jakarta`/`tools.jackson` imports (ArchUnit). `ContentHash` uses `java.security.MessageDigest` (JDK is allowed). Metadata filter model must be a domain record; JSON <-> map conversion happens in the persistence adapter. Unit-test style: plain JUnit + AssertJ like `src/test/java/com/evision/releasemgmt/application/LogoValidatorTest.java`.

---

### `ArchitectureBoundaryTest` (test)

**Analog:** `src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java` (lines 1-60)

```java
private static final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.evision.releasemgmt");

@Test
void domainAndPortsHaveNoFrameworkImports() {
    ArchRule rule = noClasses()
            .that().resideInAnyPackage("..domain..", "..ports..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..", "com.fasterxml.jackson..", "tools.jackson..")
            .allowEmptyShould(true);
    rule.check(classes);
}

@Test
void domainAndPortsDoNotDependOnAdaptersConfigOrApplication() { ... "..adapters..", "..config..", "..application.." ... }

@Test
void applicationDoesNotDependOnAdapters() { ... "..adapters.jira..", "..adapters.persistence..", "..adapters.web..", "..adapters.render.." ... }
```
Rag: change package to `com.evision.ragservice`, drop `org.hibernate..`, replace adapter sub-packages with `..adapters.web..`, `..adapters.persistence..`, `..adapters.ollama..`; keep `.allowEmptyShould(true)`. Optional extra rule in the same spirit as `renderLibrariesLiveOnlyInTheRenderAdapter`: `org.springframework.jdbc..` only in `..adapters.persistence..`, `org.springframework.web.client..` only in `..adapters.ollama..`/`..config..`. The main test has no `@SpringBootTest`; it is a plain unit test.

---

### `*PersistenceIT` and worker ITs (test, CRUD)

**Analog:** `src/test/java/com/evision/releasemgmt/adapters/persistence/AdoCredentialPersistenceIT.java` (lines 1-40)

```java
@SpringBootTest
@Testcontainers
class AdoCredentialPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired private AdoCredentialRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM ado_credential");
    }
```
Imports: `org.springframework.boot.testcontainers.service.connection.ServiceConnection`, `org.testcontainers.containers.PostgreSQLContainer` (deprecated class the repo uses; keep for consistency), `org.testcontainers.junit.jupiter.{Container,Testcontainers}`, `org.springframework.jdbc.core.JdbcTemplate`. Pattern: autowire the PORT, assert via port, assert raw storage via `JdbcTemplate` (e.g. `key_hash` is not the raw key), clean tables in `@BeforeEach`.

**Rag change (Pitfall 1):** the pgvector image requires `asCompatibleSubstituteFor`:
```java
static final DockerImageName PGVECTOR =
    DockerImageName.parse("pgvector/pgvector:0.8.7-pg18-trixie").asCompatibleSubstituteFor("postgres");
```
Put this constant in one shared test class in each build (rag test tree; main test tree). Use `JdbcClient`/`JdbcTemplate` in ITs. Worker IT: `@SpringBootTest` with `@DynamicPropertySource` pointing `rag.ollama.base-url` at WireMock; disable the real `@Scheduled` tick in tests (property flag or invoke the worker method directly) so tests are deterministic.

### Existing 18 `*IT` files (modify)

Grep shows 18 files contain `"postgres:18"` (e.g. `AdoCredentialPersistenceIT.java:29`). Change `new PostgreSQLContainer<>("postgres:18")` to `new PostgreSQLContainer<>(PgVectorImage.POSTGRES)` (new shared test constant class in `src/test/java/com/evision/releasemgmt/`). Find all with `grep -rl "postgres:18" src/test`. Keep `@ServiceConnection`.

---

### `OllamaEmbeddingAdapterTest` (WireMock test)

**Analog:** `src/test/java/com/evision/releasemgmt/adapters/ado/AdoRestAdapterTest.java` (lines 1-75)

```java
@WireMockTest
class AdoRestAdapterTest {
    private AdoRestAdapter adapter(WireMockRuntimeInfo wm) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        return new AdoRestAdapter(RestClient.builder().requestFactory(requestFactory).build(), wm.getHttpBaseUrl(), maxPages);
    }
    private void assertReason(WireMockRuntimeInfo wm, AdoFailureReason reason) {
        assertThatThrownBy(() -> adapter(wm).validate("example-org", "pat-123"))
                .isInstanceOfSatisfying(AdoAccessException.class, e -> {
                    assertThat(e.reason()).isEqualTo(reason);
                    assertThat(e.getMessage()).doesNotContain("pat-123");
                });
    }
```
Static imports: `com.github.tomakehurst.wiremock.client.WireMock.{aResponse,okJson,stubFor,get,verify,...}`; `Scenario` for outage-then-recovery sequences; fixtures read from `src/test/resources/fixtures` via `Files.readString(Path.of(...))`. Rag cases: 200 with 768-dim vectors, 400 (content error), 404 model-not-found (outage), 500, connect refused / fixed delay beyond read timeout (outage), request body has `"truncate":false` and `search_document:` prefix.

---

### `docker/initdb/10-ragdb.sh` (config, batch)

No analog in repo (root has only `devstatus-capture.sh`, `phase6-capture.sh`, `rsync.sh`; `.gitattributes` contains exactly `*.sh text eol=lf`, so LF is already pinned). Copy RESEARCH Pattern 1 verbatim. It uses `POSTGRES_USER`/`POSTGRES_DB` from the container env (compose sets `releasemgmt` for both).

---

### `V1__rag_schema.sql` (migration)

**Analog:** `src/main/resources/db/migration/V8__ado_credential.sql` (header comment explaining the requirement/why, `CREATE TABLE` with aligned columns, `TIMESTAMPTZ NOT NULL DEFAULT now()`, `CHECK` constraints, ASCII comments). Main uses uppercase SQL types and `SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1)`.
```sql
-- ADO-04: one shared, org-scoped Azure DevOps PAT (Phase 6 credential model).
CREATE TABLE ado_credential (
    id               SMALLINT     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    ...
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
```
Naming: `V{n}__snake_case.sql` (main V1..V10). Rag starts its own history at `V1__rag_schema.sql`; body from RESEARCH Pattern 3. Keep LF (Pitfall 9; `.gitattributes` has no `*.sql` rule: add `*.sql text eol=lf` if a worktree CRLF problem recurs, per memory).

---

## Shared Patterns

### ProblemDetail error body with `reason`
**Source:** `MAIN/adapters/web/GlobalExceptionHandler.java` lines 370-376 (`problem(status, title, detail, reason)`).
**Apply to:** every error response in rag-service, including the API-key filter's 401 and the 413 body-size rejection. Clients branch on `reason`, never on `detail`.

### Domain exception with `reason()` enum switched in the advice
**Source:** `AdoAccessException` / `AdoFailureReason`; switch at `GlobalExceptionHandler` lines 56-73 (`return switch (ex.reason()) { case X -> problem(...) }`).
**Apply to:** Ollama failure kinds (`UNAVAILABLE`, `REJECTED`), key failures.

### Hexagonal packages and boundary
**Source:** `ArchitectureBoundaryTest.java`; layout `domain/ports/application/adapters/{web,persistence,ollama}/config`.
**Apply to:** all rag-service main code. Domain and ports import no Spring/Jakarta/Jackson. Application never imports adapters.

### Configuration over magic numbers
**Source:** `application.yml` `app:` block (ISO-8601 durations, limits as properties) injected through `@Value("${...}")`.
**Apply to:** timeouts, batch size, chunk size/overlap, `k` default/max, 1 MB limit, lease, backoff bounds, model allow-list.

### Transactions on adapter methods only
**Source:** `AdoCredentialRepositoryAdapter` lines 31-33, 49-53.
**Apply to:** all JdbcClient adapters; worker claim and write-back are separate adapter calls; Ollama call outside both.

### Fail-fast secrets from env, no defaults
**Source:** `application.yml` line 64 `key: ${RM_CREDENTIAL_KEY}`; `compose.app.yaml` lines 54-58 `${VAR:?message}`.
**Apply to:** `RAG_ADMIN_KEY` (also validate length >= 32 at startup per RESEARCH Security table).

### Test application.yml shadowing
**Source:** `src/test/resources/application.yml` shadows main's.
**Apply to:** rag-service tests. Every new property goes in both files.

### Build env landmines
Set `JAVA_HOME=$HOME/.jdks/jdk-25` on every Maven call (`mvn -f rag-service/pom.xml test`); Docker Desktop must be running for Testcontainers; `mvn clean` after public API changes.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `ApiKeyFilter` (servlet filter / interceptor) | middleware | request-response | Main app authenticates via Spring Security JWT cookie; rag has no Security. Net-new; use RESEARCH "API key hashing" example. The 401 ProblemDetail writer can follow `adapters/security/ProblemDetailAuthenticationEntryPoint.java`. |
| `IndexWorker` (`@Scheduled`, backoff, lease) | service | batch/event-driven | No `@Scheduled`/`@EnableScheduling` in main. Use RESEARCH Pattern 4. Needs `@EnableScheduling` on a rag `@Configuration`. Only partial precedent: in-memory time gate in `AdoRestAdapter` lines 49-50, 149-176. |
| JdbcClient repositories (SQL bodies) | adapter | CRUD | Main uses JPA exclusively; `JdbcClient` is absent. Use RESEARCH Patterns 4-5 for SQL. |
| `Chunker`, `ContentHash`, `MetadataFilter` | utility | transform | Net-new domain logic; unit-tested pure classes. |
| `ApiKeyGenerator` (SecureRandom + SHA-256) | utility | transform | Net-new; RESEARCH Code Examples. |
| springdoc config + `OpenApiCoverageIT` | config/test | request-response | No OpenAPI in main. Dependency `springdoc-openapi-starter-webmvc-ui:3.1.1`; IT asserts every route appears in `/v3/api-docs` (use `@SpringBootTest` + MockMvc from `spring-boot-starter-webmvc-test`; MockMvc/ITs in `src/test/.../adapters/web/` e.g. `TemplateLifecycleIT.java` show the style but use Spring Security, so do not copy their auth setup). |
| `docker/initdb/10-ragdb.sh` | config | batch | First init script in repo. |

## Metadata

**Analog search scope:** `pom.xml`, `Dockerfile`, `compose*.yaml`, `.gitattributes`, `src/main/java/com/evision/releasemgmt/{config,adapters/{ado,persistence,web},ports,application}`, `src/main/resources/{application.yml,db/migration}`, `src/test/{java/.../ArchitectureBoundaryTest,adapters/{persistence,ado,web},resources}`.
**Files scanned:** ~25 read, ~300 listed.
**Pattern extraction date:** 2026-10-13
