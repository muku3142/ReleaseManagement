---
phase: 09-rag-service-foundation-index
reviewed: 2026-10-13T00:00:00Z
depth: standard
files_reviewed: 102
files_reviewed_list:
  - .env.example
  - .gitattributes
  - compose.app.yaml
  - compose.yaml
  - docker/initdb/10-ragdb.sh
  - docs/docker.md
  - rag-service/.dockerignore
  - rag-service/Dockerfile
  - rag-service/pom.xml
  - rag-service/src/main/java/com/evision/ragservice/RagServiceApplication.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/ollama/OllamaEmbeddingAdapter.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/ollama/OllamaHealthIndicator.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/persistence/ChunkSearchRepositoryAdapter.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/persistence/ClientRepositoryAdapter.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/persistence/CollectionRepositoryAdapter.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/persistence/DocumentRepositoryAdapter.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/persistence/IndexQueueRepositoryAdapter.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/persistence/VectorLiteral.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/AdminController.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/ApiKeyFilter.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/CollectionController.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/DocumentController.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/GlobalExceptionHandler.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/RequestSizeLimitFilter.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/SearchController.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/StatusController.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/ClientResponse.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/CollectionResponse.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/CreateClientRequest.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/DocumentResponse.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/IssuedKeyResponse.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/PutCollectionRequest.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/PutDocumentRequest.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/ReindexResponse.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/SearchRequest.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/SearchResponse.java
  - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/StatusResponse.java
  - rag-service/src/main/java/com/evision/ragservice/application/ClientService.java
  - rag-service/src/main/java/com/evision/ragservice/application/CollectionService.java
  - rag-service/src/main/java/com/evision/ragservice/application/DocumentService.java
  - rag-service/src/main/java/com/evision/ragservice/application/IndexWorker.java
  - rag-service/src/main/java/com/evision/ragservice/application/SearchService.java
  - rag-service/src/main/java/com/evision/ragservice/application/StatusService.java
  - rag-service/src/main/java/com/evision/ragservice/config/OllamaRestClientConfig.java
  - rag-service/src/main/java/com/evision/ragservice/config/OpenApiConfig.java
  - rag-service/src/main/java/com/evision/ragservice/config/SchedulingConfig.java
  - rag-service/src/main/java/com/evision/ragservice/domain/ApiKeys.java
  - rag-service/src/main/java/com/evision/ragservice/domain/ChunkEmbedding.java
  - rag-service/src/main/java/com/evision/ragservice/domain/Chunker.java
  - rag-service/src/main/java/com/evision/ragservice/domain/ClaimedDocument.java
  - rag-service/src/main/java/com/evision/ragservice/domain/ClientApp.java
  - rag-service/src/main/java/com/evision/ragservice/domain/ClientNameTakenException.java
  - rag-service/src/main/java/com/evision/ragservice/domain/ClientNotFoundException.java
  - rag-service/src/main/java/com/evision/ragservice/domain/CollectionModelConflictException.java
  - rag-service/src/main/java/com/evision/ragservice/domain/CollectionNotFoundException.java
  - rag-service/src/main/java/com/evision/ragservice/domain/CollectionStatus.java
  - rag-service/src/main/java/com/evision/ragservice/domain/ContentHash.java
  - rag-service/src/main/java/com/evision/ragservice/domain/DocumentCollection.java
  - rag-service/src/main/java/com/evision/ragservice/domain/DocumentContent.java
  - rag-service/src/main/java/com/evision/ragservice/domain/DocumentNotFoundException.java
  - rag-service/src/main/java/com/evision/ragservice/domain/DocumentStatus.java
  - rag-service/src/main/java/com/evision/ragservice/domain/DocumentView.java
  - rag-service/src/main/java/com/evision/ragservice/domain/EmbeddingModelNotAllowedException.java
  - rag-service/src/main/java/com/evision/ragservice/domain/EmbeddingPurpose.java
  - rag-service/src/main/java/com/evision/ragservice/domain/EmbeddingRejectedException.java
  - rag-service/src/main/java/com/evision/ragservice/domain/EmbeddingUnavailableException.java
  - rag-service/src/main/java/com/evision/ragservice/domain/InvalidContentException.java
  - rag-service/src/main/java/com/evision/ragservice/domain/MetadataFilter.java
  - rag-service/src/main/java/com/evision/ragservice/domain/MetadataRules.java
  - rag-service/src/main/java/com/evision/ragservice/domain/PayloadTooLargeException.java
  - rag-service/src/main/java/com/evision/ragservice/domain/QueueDepth.java
  - rag-service/src/main/java/com/evision/ragservice/domain/SearchHit.java
  - rag-service/src/main/java/com/evision/ragservice/ports/ChunkSearch.java
  - rag-service/src/main/java/com/evision/ragservice/ports/ClientStore.java
  - rag-service/src/main/java/com/evision/ragservice/ports/CollectionStore.java
  - rag-service/src/main/java/com/evision/ragservice/ports/DocumentStore.java
  - rag-service/src/main/java/com/evision/ragservice/ports/EmbeddingPort.java
  - rag-service/src/main/java/com/evision/ragservice/ports/IndexQueue.java
  - rag-service/src/main/resources/application.yml
  - rag-service/src/main/resources/db/migration/V1__rag_schema.sql
  - rag-service/src/test/java/com/evision/ragservice/ArchitectureBoundaryTest.java
  - rag-service/src/test/java/com/evision/ragservice/InitScriptPrivilegeIT.java
  - rag-service/src/test/java/com/evision/ragservice/PgVectorImage.java
  - rag-service/src/test/java/com/evision/ragservice/adapters/ollama/OllamaEmbeddingAdapterTest.java
  - rag-service/src/test/java/com/evision/ragservice/adapters/persistence/ChunkSearchPersistenceIT.java
  - rag-service/src/test/java/com/evision/ragservice/adapters/persistence/ClientPersistenceIT.java
  - rag-service/src/test/java/com/evision/ragservice/adapters/persistence/CollectionDocumentPersistenceIT.java
  - rag-service/src/test/java/com/evision/ragservice/adapters/persistence/IndexQueuePersistenceIT.java
  - rag-service/src/test/java/com/evision/ragservice/adapters/persistence/SchemaPersistenceIT.java
  - rag-service/src/test/java/com/evision/ragservice/adapters/web/AdminAndAuthIT.java
  - rag-service/src/test/java/com/evision/ragservice/adapters/web/CollectionApiIT.java
  - rag-service/src/test/java/com/evision/ragservice/adapters/web/DocumentApiIT.java
  - rag-service/src/test/java/com/evision/ragservice/adapters/web/OpenApiContractIT.java
  - rag-service/src/test/java/com/evision/ragservice/adapters/web/SearchApiIT.java
  - rag-service/src/test/java/com/evision/ragservice/adapters/web/StatusApiIT.java
  - rag-service/src/test/java/com/evision/ragservice/application/CollectionServiceTest.java
  - rag-service/src/test/java/com/evision/ragservice/application/IndexWorkerIT.java
  - rag-service/src/test/java/com/evision/ragservice/domain/ChunkerTest.java
  - rag-service/src/test/java/com/evision/ragservice/domain/ContentHashAndApiKeysTest.java
  - rag-service/src/test/java/com/evision/ragservice/domain/MetadataRulesTest.java
  - rag-service/src/test/resources/application.yml
  - src/test/java/com/evision/releasemgmt/PgVectorImage.java
findings:
  critical: 1
  warning: 6
  info: 4
  total: 11
status: issues_found
---

# Phase 9: Code Review Report

**Reviewed:** 2026-10-13
**Depth:** standard
**Files Reviewed:** 102
**Status:** issues_found

## Summary

I reviewed the new `rag-service` module end to end: auth filter, tenancy scoping on every query, SQL construction, the lease-based queue and worker, the Ollama adapter, the compose files, the init script, the Dockerfile and the docs.

**Tenant isolation holds.**
- Every client-facing query resolves the collection by `(client_id, name)` first and then scopes by `collection_id`.
- `queueDepth` scopes by `client_id`.
- The metadata filter is fully parameterised. Keys are regex-checked and serialised through Jackson into bound `jsonb` parameters.

**Hash-guarded write-back is correct.** `complete()` and `fail()` both carry `content_hash`, so stale embeddings are discarded.

**Authentication is the problem.** It is decided by a raw `getRequestURI().startsWith(...)` prefix check, but Spring MVC matches handlers on percent-decoded path segments. An unauthenticated caller can reach every admin endpoint. **This was confirmed against the running local container:** `GET /%761/admin/clients` with no `Authorization` header returned **200**, while `GET /v1/admin/clients` returned 401.

**The remaining findings:**
- The request-size limit can be bypassed.
- Filter-clause count has no bound.
- Head-of-line blocking in the worker stalls indexing for all tenants when one document or collection hits a non-outage 404/5xx.
- Two docs/config errors break the one-off DB setup.

## Critical Issues

### CR-01: Unauthenticated admin access via percent-encoded path (auth bypass)

**File:** `rag-service/src/main/java/com/evision/ragservice/adapters/web/ApiKeyFilter.java:47-49, 59`
**Issue:** `shouldNotFilter` skips authentication when the raw request URI does not start with `/v1/`. The admin check likewise relies on a raw `startsWith("/v1/admin/")`.

`request.getRequestURI()` returns the undecoded URI. Spring MVC's `PathPattern` matching, however, compares *decoded* segments. Verified with spring-web 7.0.8: `/%761/admin/clients` and `/v1/%61dmin/clients` both match `/v1/admin/clients`.

As a result:
- `POST /%761/admin/clients` skips the filter entirely and reaches `AdminController.create`. Any unauthenticated network caller can mint client keys, rotate (take over) or revoke any client, and list all clients.
- Confirmed live against the running container on `localhost:8081`: `curl http://localhost:8081/%761/admin/clients` returned 200 with no credentials.
- `/v1/%61dmin/...` with any *client* key goes down the client branch and also reaches the admin endpoints, which is privilege escalation.

The port is published on all host interfaces (`compose.app.yaml:86`), so this is reachable from the network.

The same raw-prefix flaw also lets `RequestSizeLimitFilter.shouldNotFilter` (line 31-32) be skipped.

**Fix:** Fail closed. Authenticate everything except an explicit allow-list, and decide on the decoded, normalised lookup path rather than the raw URI. Reject any request whose raw URI contains `%` in the path, or compare against the same decoded path Spring uses:
```java
private static final UrlPathHelper PATHS = UrlPathHelper.defaultInstance; // decodes + normalises

@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = PATHS.getLookupPathForRequest(request);
    return path.startsWith("/actuator/") || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui");
}
// in doFilterInternal:
String path = PATHS.getLookupPathForRequest(request);
if (path.startsWith(ADMIN_PREFIX)) { ... admin check ... } else { ... client check ... }
```
Add an IT asserting that `/%761/admin/clients`, `/v1/%61dmin/clients` and `/v1//admin/clients` all return 401 without the admin key.

## Warnings

### WR-01: Request-size limit is bypassed by chunked transfer encoding

**File:** `rag-service/src/main/java/com/evision/ragservice/adapters/web/RequestSizeLimitFilter.java:38`
**Issue:** The filter only checks the declared `Content-Length`. A request with `Transfer-Encoding: chunked` reports `-1` and passes, and neither Tomcat (`max-http-form-post-size` does not apply to JSON) nor Spring caps JSON bodies.

An authenticated client can therefore stream an arbitrarily large body. Jackson materialises the entire `metadata`/`filter` map, plus `text` strings up to Jackson's 20M-char default, before `DocumentService` checks the 1 MB limit. The result is heap exhaustion for the shared service and denial of service to every tenant.
**Fix:** Wrap the request input stream in a counting stream that throws once `maxRequestBytes` is exceeded, and map that to 413. Alternatively, reject requests with an unknown length on `/v1/` routes:
```java
if (request.getContentLengthLong() < 0 && hasBody(request)) { reject413(); return; }
```
A bounded `HttpServletRequestWrapper` around `getInputStream()` is the robust option.

### WR-02: Search filter has no bound on clause count or value length (500 on large filters)

**File:** `rag-service/src/main/java/com/evision/ragservice/domain/MetadataRules.java:37-47`; `rag-service/src/main/java/com/evision/ragservice/adapters/persistence/ChunkSearchRepositoryAdapter.java:39-49`
**Issue:** `validateMetadata` enforces `MAX_KEYS` and `MAX_STRING_VALUE_LENGTH`, but `parseFilter` enforces neither. Each clause can carry 100 values, and each value becomes a separate bind parameter. A filter with a few hundred keys therefore exceeds PostgreSQL's 65,535 bind-parameter limit, the driver throws, and the response is an unhandled 500. Arbitrarily long filter strings are also accepted and bound.
**Fix:** In `parseFilter`, apply `MAX_KEYS` to `filter.size()` and `MAX_STRING_VALUE_LENGTH` to every string element, mirroring `validateMetadata`. Optionally cap the total value count, for example at 500.

### WR-03: A per-document or per-collection embedding error stalls indexing for all tenants

**File:** `rag-service/src/main/java/com/evision/ragservice/application/IndexWorker.java:80-86`; `rag-service/src/main/java/com/evision/ragservice/adapters/ollama/OllamaEmbeddingAdapter.java:91-117`; `rag-service/src/main/java/com/evision/ragservice/adapters/persistence/IndexQueueRepositoryAdapter.java:29-31`
**Issue:** Every `EmbeddingUnavailableException` is treated as a global outage. The worker releases the rest of the batch, backs off for up to 5 minutes, and on the next run claims the same rows first again: `ORDER BY updated_at`, and `release()` does not change `updated_at`.

The adapter maps several conditions that are not outages onto this exception:
- `404` (model not pulled for that collection's model);
- any `5xx` Ollama returns for specific input;
- a wrong-dimension response (`toVectors`, a model in `allowed-models` that is not 768-d).

One collection or document hitting any of these therefore permanently blocks the head of the shared queue. Every other client's documents stay `PENDING` forever. `/v1/status` keeps reporting Ollama `UP`, because `isAvailable()` only probes `/api/version`.
**Fix:**
- After an embed failure, call `embeddings.isAvailable()`. If Ollama is up, the failure is document- or model-specific: bump that document's `updated_at` (or a `next_attempt_at` column) so it goes to the back of the queue, and continue with the rest of the batch instead of backing off globally.
- Treat a dimension mismatch as a configuration error. Validate `allowed-models` dimensions at startup, or `fail()` the document with a reason.

### WR-04: Unexpected exceptions escape `runOnce`, leaving the batch leased and the poison document at the head

**File:** `rag-service/src/main/java/com/evision/ragservice/application/IndexWorker.java:68-104`
**Issue:** Only `EmbeddingUnavailableException` and `EmbeddingRejectedException` are caught. Any other runtime exception propagates out of `tick()`, the scheduler logs it, and every remaining claimed document keeps its 10-minute lease. Examples:
- `InvalidContentException` from `Chunker.chunk` (line 38 there);
- a `DataAccessException` in `complete()` (for example, a constraint or vector-cast failure);
- an `IndexOutOfBoundsException` if the adapter ever returns fewer vectors.

After the lease expires, the same document is claimed first again and fails again. Indexing of everything behind it is delayed in 10-minute steps indefinitely, and the document never reaches `FAILED` with a visible reason.
**Fix:** Catch `RuntimeException` per document. Call `queue.fail(doc.id(), doc.contentHash(), "Indexing error")` for content-attributable errors, or release just that document and move it to the back of the queue. Log metadata only, then continue the loop.

### WR-05: Documented one-off command blanks `RAG_DB_PASSWORD` and fails

**File:** `docs/docker.md` (new "First-time setup on an existing volume" block)
**Issue:** `exec -e RAG_DB_PASSWORD="$RAG_DB_PASSWORD"` expands the variable in the *host* shell. Compose reads `.env` for interpolation, but `.env` does not export variables into the user's shell, so the documented setup gives `-e RAG_DB_PASSWORD=` (empty). That overrides the correct value the `postgres` service already has from `compose.app.yaml:19`. The script then aborts on `: "${RAG_DB_PASSWORD:?}"`, and users following the docs cannot provision `ragdb`.
**Fix:** Drop the override; the container already has the variable:
```bash
docker compose -f compose.app.yaml exec postgres bash /docker-entrypoint-initdb.d/10-ragdb.sh
```

### WR-06: Re-running the init script does not change the `rag` password, contrary to `.env.example`

**File:** `.env.example:50-52`; `docker/initdb/10-ragdb.sh:5`
**Issue:** `.env.example` says to "re-run the one-off in docs/docker.md after changing it". The script creates the role only `WHERE NOT EXISTS`, so a re-run leaves the old password in place. `rag-service` then starts with the new password and fails authentication against Postgres.
**Fix:** Make the script converge the password:
```sql
SELECT format('CREATE ROLE rag LOGIN PASSWORD %L', :'rag_pw') WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'rag') \gexec
SELECT format('ALTER ROLE rag PASSWORD %L', :'rag_pw') \gexec
```
If you would rather not do that, correct the `.env.example` comment to give the `ALTER ROLE` command instead.

## Info

### IN-01: Leases carry no owner token

**File:** `rag-service/src/main/java/com/evision/ragservice/adapters/persistence/IndexQueueRepositoryAdapter.java:52-58, 63-75`
**Issue:** `release()` clears `lease_until` by id alone, and `complete()` does not check that the caller still holds the lease. Today this is harmless with a single scheduler thread. With more than one replica, or a lease that expires while a 1 MB document is being embedded (around 25 Ollama batches, each with a 60s read timeout), a second worker can process the same document, and an outage-time `release()` can clear another worker's live lease.
**Fix:** Store a `lease_token UUID` on claim. Include `AND lease_token = :token` in `release`; `complete` can stay hash-guarded.

### IN-02: Aggregate `/actuator/health` reports DOWN whenever Ollama is down

**File:** `rag-service/src/main/java/com/evision/ragservice/adapters/ollama/OllamaHealthIndicator.java:8-22`; `rag-service/src/main/resources/application.yml:15-26`
**Issue:** Readiness correctly excludes Ollama. The default `/actuator/health` group still includes the `ollama` indicator, though, and returns 503 during an outage. Any external monitor pointed at the root health endpoint will flag the service down, which contradicts "never gates readiness".
**Fix:** Configure `management.endpoint.health.group` so that the default group excludes `ollama`, or expose Ollama health only through `/v1/status`.

### IN-03: rag-service published on all interfaces; dev compose uses a fixed `rag` password on a public 5432

**File:** `compose.app.yaml:85-86`; `compose.yaml:8-11`; `rag-service/src/main/resources/application.yml:7`; `.env.example:52`
**Issue:** Postgres in `compose.app.yaml` is loopback-only, but `rag-service` binds `0.0.0.0:8081`. In `compose.yaml`, the new `rag` role has the fixed password `rag` and port 5432 is on all interfaces. The `application.yml` default is also `rag`.
**Fix:** Bind `127.0.0.1:${RAG_PORT:-8081}:8081` unless network access is required. Give `RAG_DB_PASSWORD` no default (`:?`) in `compose.app.yaml`, as `RAG_ADMIN_KEY` already has.

### IN-04: Any Ollama 400 permanently fails the document

**File:** `rag-service/src/main/java/com/evision/ragservice/adapters/ollama/OllamaEmbeddingAdapter.java:80-82`
**Issue:** Ollama also returns 400 for model-level problems, for example a model that does not support embeddings, or a missing or invalid model name. All of these become `EmbeddingRejectedException`, so every document in that collection is marked `FAILED` as "content rejected". That contradicts D-09-12 ("FAILED means bad content only").
**Fix:** Inspect the error body, or validate that each allowed model supports embeddings at startup, so that only genuine input errors map to `EmbeddingRejectedException`.

---

_Reviewed: 2026-10-13_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
