---
phase: 09
slug: rag-service-foundation-index
status: verified
threats_total: 64
threats_closed: 64
threats_open: 0
asvs_level: 1
created: 2026-10-13
---

# Phase 09 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Client app -> rag-service /v1 | Bearer API key per client; admin key for /v1/admin/** | Document text, metadata, queries (tenant data) |
| rag-service -> Ollama | Host-local HTTP, no auth | Document and query text for embedding |
| rag-service -> Postgres ragdb | Role `rag`, CONNECT revoked from PUBLIC | Documents, chunks, vectors, key hashes |
| Host -> Postgres / rag-service ports | 127.0.0.1:5432 (compose.app.yaml); 8081 published | All of the above |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation / Evidence | Status |
|-----------|----------|-----------|-------------|-----------------------|--------|
| T-09-01 | Information disclosure | rag role reading releasemgmt | mitigate | docker/initdb/10-ragdb.sh:10 REVOKE CONNECT FROM PUBLIC; InitScriptPrivilegeIT:69 asserts permission denied | closed |
| T-09-02 | Elevation of privilege | rag role | mitigate | 10-ragdb.sh:5 CREATE ROLE rag LOGIN only; extension created by superuser (:11) | closed |
| T-09-03 | Information disclosure | releasemgmt superuser reading ragdb | accept | See Accepted Risks Log AR-09-03 | closed |
| T-09-04 | Tampering | SQL injection through RAG_DB_PASSWORD | mitigate | 10-ragdb.sh:4,6 psql -v rag_pw bound via :'rag_pw' in a quoted heredoc | closed |
| T-09-05 | Information disclosure | Postgres port exposure | mitigate | compose.app.yaml:22 `127.0.0.1:5432:5432` (see note below on dev compose.yaml) | closed |
| T-09-06 | Information disclosure | GlobalExceptionHandler | mitigate | GlobalExceptionHandler.java:89-122 field name + constraint message only; fixed text for parameter and parse errors | closed |
| T-09-07 | Information disclosure | Foreign collection probing | mitigate | CollectionService.require -> CollectionNotFoundException -> 404 only (GEH:35); no 403 path | closed |
| T-09-08 | Tampering | Chunk/document integrity | mitigate | V1__rag_schema.sql: ON DELETE CASCADE, UNIQUE(collection_id, external_id), status CHECK | closed |
| T-09-09 | Elevation of privilege | Flyway as rag | mitigate | V1__rag_schema.sql:2 CREATE EXTENSION IF NOT EXISTS; tables only | closed |
| T-09-10 | Information disclosure | Admin key in config | mitigate | application.yml `rag.admin-key: ${RAG_ADMIN_KEY}` no default | closed |
| T-09-11 | Spoofing | ApiKeys.generate | mitigate | ApiKeys.java:12-20 SecureRandom 256-bit; SHA-256 hash :24-26 | closed |
| T-09-12 | Information disclosure | Stored keys | mitigate | ClientRepositoryAdapter.java:29 stores hash only; AdminAndAuthIT:76 asserts stored value is the hash | closed |
| T-09-13 | Tampering | Filter keys reaching SQL | mitigate | MetadataRules.java:15 key regex; :112-118 String/Number values only | closed |
| T-09-14 | Denial of service | Oversized metadata/filters | mitigate | MetadataRules.java:10-13 caps, enforced :53,:69,:86 (WR-02, 384c527) | closed |
| T-09-15 | Denial of service | Chunker pathological input | mitigate | Chunker hard cut; ChunkerTest 5,000-char unbroken input | closed |
| T-09-16 | Information disclosure | Exception messages | mitigate | MetadataRules messages name field/rule only (:55,:70,:77,:87) | closed |
| T-09-17 | Tampering | Malformed Ollama response | mitigate | OllamaEmbeddingAdapter.java:91-97 count and DIMENSIONS checks | closed |
| T-09-18 | Denial of service | Hung Ollama call | mitigate | application.yml PT2S/PT60S/PT2S; OllamaRestClientConfig.java:19-22; adapter :45-46 | closed |
| T-09-19 | Information disclosure | Exceptions/logs | mitigate | OllamaEmbeddingAdapter.java:81-116 fixed messages without inputs | closed |
| T-09-20 | Denial of service | Outage marks documents FAILED | mitigate | OllamaEmbeddingAdapter.java:80 only HTTP 400 -> EmbeddingRejectedException; others unavailable (:111-116) | closed |
| T-09-21 | Spoofing | Ollama has no auth on the network | accept | See Accepted Risks Log AR-09-21 | closed |
| T-09-22 | Tampering | SQL injection | mitigate | JdbcClient named params across persistence adapters; CAST(:metadata AS jsonb); no value concatenation | closed |
| T-09-23 | Information disclosure | Cross-client data | mitigate | Persistence scoped by client_id / resolved collection id; DocumentRepositoryAdapter.java:101 | closed |
| T-09-24 | Spoofing | Revoked/rotated keys | mitigate | ClientRepositoryAdapter.java:39 `revoked_at IS NULL`; :56 rotate replaces hash | closed |
| T-09-25 | Information disclosure | Key hash exposure | mitigate | ClientRepositoryAdapter selects (:31,:69,:75) never load key_hash into ClientApp | closed |
| T-09-26 | Denial of service | last_used_at write amplification | mitigate | ClientRepositoryAdapter.java:47-48 update only when older than 1 minute | closed |
| T-09-27 | Tampering | Filter SQL construction | mitigate | ChunkSearchRepositoryAdapter.java:131-132 generated param names, jsonb binds | closed |
| T-09-28 | Information disclosure | Cross-collection search | mitigate | ChunkSearchRepositoryAdapter.java:123 `c.collection_id = :collectionId` | closed |
| T-09-29 | Tampering | Stale embedding overwrites new text | mitigate | IndexQueueRepositoryAdapter.java:223,253 `content_hash = :hash` | closed |
| T-09-30 | Denial of service | Locks held during Ollama calls | mitigate | claim (:181-195) and complete (:222-235) are separate; embedding only in IndexWorker | closed |
| T-09-31 | Denial of service | Crashed worker strands rows | mitigate | IndexQueueRepositoryAdapter.java:185 `lease_until < now()` reclaims | closed |
| T-09-32 | Spoofing | ApiKeyFilter | mitigate | ApiKeyFilter.java:45-49 min 32 chars; :70 MessageDigest.isEqual; client path hash lookup; fail-closed allow-list :55-58 (CR-01, 80e280c); RawHttpSecurityIT | closed |
| T-09-33 | Elevation of privilege | Client key on admin routes | mitigate | ApiKeyFilter.java:67-74 /v1/admin/** accepts only admin key, matched on decoded PathContainer; RawHttpSecurityIT percent-encoded, matrix and double-slash cases | closed |
| T-09-34 | Information disclosure | Foreign collection | mitigate | CollectionService.require -> 404 for absent and foreign | closed |
| T-09-35 | Information disclosure | Key leakage | mitigate | ClientResponse.java has no key field; no log calls in ClientService/AdminController | closed |
| T-09-36 | Repudiation | Unused/abused keys | mitigate | ClientRepositoryAdapter.java:47 last_used_at; exposed as ClientResponse.lastUsedAt | closed |
| T-09-37 | Tampering | Path variable injection | mitigate | CollectionController.java:38,55,63, DocumentController.java:40-61, SearchController.java:28 @Pattern; parameterised SQL | closed |
| T-09-38 | Spoofing | Admin key over plain HTTP | accept | See Accepted Risks Log AR-09-38 | closed |
| T-09-39 | Denial of service | Oversized bodies | mitigate | RequestSizeLimitFilter.java:36 Content-Length > 2 MB -> 413, @Order(0), now applies to every request (CR-01); DocumentService.java:35 1 MB text check; RawHttpSecurityIT | closed |
| T-09-40 | Denial of service | Unbounded chunked bodies | accept -> mitigate | Accepted at plan time, superseded by WR-01 (0b43a53): RequestSizeLimitFilter.java:44-46,60-107 BoundedInputStream counts chunked bytes; 413 via GEH:113; RawHttpSecurityIT | closed |
| T-09-41 | Tampering | NUL bytes causing 500s | mitigate | DocumentService.java:44-46 rejectNul on title/text/url; SearchService.java:39 on query | closed |
| T-09-42 | Information disclosure | Cross-client document access | mitigate | DocumentService.java:48,53,58 all via collections.require(clientId, ...) | closed |
| T-09-43 | Denial of service | Ollama outage exhausting DB pool | mitigate | IndexWorker.java:80-101 embedding outside transactions, release(remaining) and backOff(); backoff-max PT5M | closed |
| T-09-44 | Information disclosure | Logs | mitigate | IndexWorker.java:86-125 ids/counts/durations only; GEH:75 class name only; 09-11 log grep 0 matches | closed |
| T-09-45 | Tampering | Stored XSS via citation URL in a later UI | mitigate | PutDocumentRequest.java:9 `@Pattern(regexp = "https?://\\S{1,2040}")` | closed |
| T-09-46 | Tampering | Prompt-injection text in documents | accept | See Accepted Risks Log AR-09-46 | closed |
| T-09-47 | Information disclosure | Cross-client search | mitigate | SearchService.java:38 require() before embedding; search scoped by collection id | closed |
| T-09-48 | Denial of service | Large k / long query | mitigate | SearchService.java:40-42 k 1..max-k (50); SearchRequest.java:7,10 query max 2000 | closed |
| T-09-49 | Tampering | Filter injection | mitigate | MetadataRules.parseFilter; bound as jsonb (ChunkSearchRepositoryAdapter.java:132) | closed |
| T-09-50 | Information disclosure | Queue depth across tenants | mitigate | DocumentRepositoryAdapter.java:96-101 queueDepth(clientId) filtered on c.client_id | closed |
| T-09-51 | Information disclosure | Query text in logs | mitigate | SearchService.java has no logger or log calls | closed |
| T-09-52 | Denial of service | Ollama outage on search path | mitigate | read-timeout PT60S; EmbeddingUnavailable -> 503 (GEH:76); readiness group readinessState,db | closed |
| T-09-53 | Information disclosure | Secrets in repo | mitigate | .env.example:48 empty `RAG_ADMIN_KEY=`; compose.app.yaml:80 `${RAG_ADMIN_KEY:?...}` fail-fast | closed |
| T-09-54 | Elevation of privilege | Container user | mitigate | rag-service/Dockerfile groupadd/useradd uid 1001, `USER app` | closed |
| T-09-55 | Spoofing | Ollama exposed on Linux bridge | mitigate | docs/docker.md:359-360 firewall guidance when OLLAMA_HOST is widened | closed |
| T-09-56 | Denial of service | Healthcheck flapping on Ollama outage | mitigate | Dockerfile HEALTHCHECK on /actuator/health/readiness; readiness = readinessState,db | closed |
| T-09-57 | Information disclosure | releasemgmt superuser reads ragdb | accept | See Accepted Risks Log AR-09-57 | closed |
| T-09-58 | Tampering | API contract drift | mitigate | OpenApiContractIT present; rag-service/openapi.json committed; suite 149/149 green (REVIEW-FIX) | closed |
| T-09-59 | Information disclosure | rag-service on 0.0.0.0:8081 | accept | See Accepted Risks Log AR-09-59 | closed |
| T-09-60 | Tampering | v1 data during image swap | mitigate | 09-11-SUMMARY steps 3-4: pg_dump 327,748 bytes exit 0; flyway history 10 before and after | closed |
| T-09-61 | Information disclosure | Keys in SUMMARY/output | mitigate | 09-11-SUMMARY step 2 "No values were printed"; step 10 both smoke clients revoked (204) | closed |
| T-09-62 | Denial of service | Ollama left stopped after the drill | mitigate | 09-11-SUMMARY outage drill: Ollama answered 9 s after restart, document INDEXED, Ollama running again | closed |
| T-09-63 | Tampering | Accidental orphan removal or volume deletion | mitigate | 09-11-SUMMARY step 1 provenance checked; no --remove-orphans / down -v recorded; v1 app stayed up | closed |
| T-09-64 | Information disclosure | Content in container logs | mitigate | 09-11-SUMMARY step 9: 0 matches for document texts, query and titles in container log | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-09-03 | T-09-03 | One-way isolation: releasemgmt is the cluster superuser and can read ragdb; own server deferred (research correction to D-09-13) | Phase 09 plan | 2026-10-13 |
| AR-09-21 | T-09-21 | Ollama has no auth; host-local and loopback-bound on this host; Linux bind/firewall guidance in docs/docker.md:359 | Phase 09 plan | 2026-10-13 |
| AR-09-38 | T-09-38 | Internal service on a host network, same exposure model as the v1 app; TLS termination is a deployment concern outside this phase | Phase 09 plan | 2026-10-13 |
| AR-09-46 | T-09-46 | Prompt-injection text stored as data only; fencing is Phase 10 | Phase 09 plan | 2026-10-13 |
| AR-09-57 | T-09-57 | Same as AR-09-03: documented one-way isolation; own server deferred | Phase 09 plan | 2026-10-13 |
| AR-09-59 | T-09-59 | Same exposure model as the v1 app port; every /v1 route requires a key; actuator exposes health only | Phase 09 plan | 2026-10-13 |

T-09-40 (unbounded chunked bodies) was accepted at plan time but later mitigated by WR-01 (0b43a53), so it is closed by mitigation and is not in this log.

---

## Unregistered Flags and Notes

- No SUMMARY `## Threat Flags` mapped to unregistered attack surface.
- T-09-05: the dev-only `compose.yaml` publishes Postgres as `5432:5432` on all interfaces with default credentials. It is pre-existing and documented (docs/docker.md:42). The declared mitigation (loopback in compose.app.yaml) is present.
- `application.yml` and compose default `RAG_DB_PASSWORD` to `rag`; a development default, not a register item.
- T-09-60..64 rest on 09-11-SUMMARY evidence; the human checkpoint outcomes there were asserted, not observed.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-13 | 64 | 64 | 0 | gsd-security-auditor |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-13
