---
phase: 09-rag-service-foundation-index
fixed_at: 2026-10-13T12:00:00Z
review_path: .planning/phases/09-rag-service-foundation-index/09-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 7
skipped: 0
status: all_fixed
---

# Phase 9: Code Review Fix Report

**Fixed at:** 2026-10-13
**Source review:** .planning/phases/09-rag-service-foundation-index/09-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (CR-01, WR-01..WR-06; IN-* out of scope)
- Fixed: 7
- Skipped: 0
- rag-service suite: 149/149 green (was 138; 11 regression tests added). No API surface change, so `rag-service/openapi.json` is unchanged and OpenApiContractIT stays green.

## Fixed Issues

### CR-01: Unauthenticated admin access via percent-encoded path (auth bypass)

**Files modified:** `rag-service/src/main/java/com/evision/ragservice/adapters/web/ApiKeyFilter.java`, `rag-service/src/main/java/com/evision/ragservice/adapters/web/RequestSizeLimitFilter.java`, `rag-service/src/test/java/com/evision/ragservice/adapters/web/RawHttpSecurityIT.java` (new)
**Commit:** 80e280c
**Applied fix:** The filter now fails closed and decides on the same thing MVC routes on. It uses `ServletRequestPathUtils.parseAndCache(request).pathWithinApplication()`, matched with `PathPattern`, so segments are decoded and matrix parameters stripped exactly as handler matching does. This differs from the review's `UrlPathHelper` suggestion and is consistent with MVC by construction.
- Only an explicit allow-list skips authentication: `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**` and `/swagger-ui.html`.
- The admin branch is `/v1/admin/**`.
- `RequestSizeLimitFilter` now applies to every request; its raw-prefix `shouldNotFilter` was removed.
- Side effect: unauthenticated requests to unknown paths now get 401 instead of 404.

The new IT runs against real Tomcat over the JDK HttpClient, not MockMvc. It checks that `/%761/admin/clients`, `/v1/%61dmin/clients`, `/v1//admin/clients` and `/v1;x=1/admin/clients` return 401 for both GET and POST without a key. It also checks that a client key cannot list or create clients through any of those paths. All four tests were confirmed to fail before the fix.

### WR-01: Request-size limit is bypassed by chunked transfer encoding

**Files modified:** `rag-service/src/main/java/com/evision/ragservice/adapters/web/RequestSizeLimitFilter.java`, `rag-service/src/main/java/com/evision/ragservice/adapters/web/GlobalExceptionHandler.java`, `rag-service/src/test/java/com/evision/ragservice/adapters/web/RawHttpSecurityIT.java`
**Commit:** 0b43a53
**Applied fix:**
- Requests with an unknown length (chunked) are wrapped in an `HttpServletRequestWrapper`. Its `ServletInputStream` counts bytes and throws `PayloadTooLargeException` once `max-request-bytes` is passed.
- Jackson wraps that exception, so `handleHttpMessageNotReadable` maps a `PayloadTooLargeException` root cause to 413 `PAYLOAD_TOO_LARGE`.
- Tests: a chunked body over the limit gets 413 and no row is written (this test failed before the fix), and a small chunked body still gets 201.
- Trade-off: only `getInputStream()` is bounded, not `getReader()`. No endpoint reads through the reader, since every endpoint is JSON via message converters.

### WR-02: Search filter has no bound on clause count or value length

**Files modified:** `rag-service/src/main/java/com/evision/ragservice/domain/MetadataRules.java`, `rag-service/src/test/java/com/evision/ragservice/domain/MetadataRulesTest.java`
**Commit:** 384c527
**Applied fix:**
- `parseFilter` now applies `MAX_KEYS`.
- The string-length check moved into `requireScalar`, so every metadata value and filter value, including `in`-list elements, is capped at `MAX_STRING_VALUE_LENGTH`. Error messages are unchanged.
- The optional total-value cap was not added: 50 keys x 100 values = 5,000 bind parameters, well under PostgreSQL's 65,535.

### WR-03: A per-document or per-collection embedding error stalls indexing for all tenants

**Files modified:** `rag-service/src/main/java/com/evision/ragservice/application/IndexWorker.java`, `rag-service/src/test/java/com/evision/ragservice/application/IndexWorkerIT.java`
**Commit:** 711b444
**Applied fix:** On `EmbeddingUnavailableException` the worker now calls `embeddings.isAvailable()`.
- If Ollama is up, the failure is treated as specific to that document or model. The document keeps its lease, so it is retried when the lease expires (`rag.worker.lease`, 10 min), and the rest of the batch continues with no global backoff.
- If Ollama is down, the outage behaviour is unchanged.
- New IT: a 500 on one document while `/api/version` is up leaves that document PENDING and leased, indexes the other, and sets no backoff.

**Trade-offs (smallest safe fix):**
- Retry timing reuses the existing lease expiry rather than adding a `next_attempt_at` column or bumping `updated_at`. `updated_at` reflects the last push, so bumping it would be misleading. A persistently failing document takes one batch slot every 10 minutes but never blocks the queue.
- A wrong-dimension model is now retried this way rather than validated at startup. Startup validation would need Ollama to be up at boot, which contradicts D-09-15.
- Such documents stay PENDING with no status reason; IN-04 covers the related model-level error classification.

### WR-04: Unexpected exceptions escape `runOnce`

**Files modified:** `rag-service/src/main/java/com/evision/ragservice/application/IndexWorker.java`, `rag-service/src/test/java/com/evision/ragservice/application/IndexWorkerIT.java`
**Commit:** d3715db
**Applied fix:** The per-document body is now wrapped in a try block.
- `InvalidContentException` (for example from `Chunker`) calls `queue.fail` with the exception's message. These messages never include content.
- Any other `RuntimeException` is logged with the document id and exception class only. The document's lease is kept so it retries later, and the loop continues.
- New ITs:
  - an unchunkable title leads to FAILED with reason "title is too long..." while the next document is indexed;
  - an infinite vector component makes `complete()` throw `DataIntegrityViolationException`; that document stays PENDING and leased, and the next document is indexed.

### WR-05: Documented one-off command blanks `RAG_DB_PASSWORD`

**Files modified:** `docs/docker.md`
**Commit:** a157afd
**Applied fix:** Dropped `-e RAG_DB_PASSWORD="$RAG_DB_PASSWORD"` from the `exec` command, and added a sentence explaining that the container already has the value from `.env`.

### WR-06: Re-running the init script does not change the `rag` password

**Files modified:** `docker/initdb/10-ragdb.sh` (kept LF), `rag-service/src/test/java/com/evision/ragservice/InitScriptPrivilegeIT.java`
**Commit:** a356677
**Applied fix:**
- The script creates the role (`LOGIN`) only if it is missing, then always runs `ALTER ROLE rag PASSWORD :'rag_pw'`, so a re-run converges the password. The `.env.example` comment is now accurate.
- New IT: re-running with a new password makes it work and the old one fail, then restores the original in `finally`.

---

_Fixed: 2026-10-13_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
