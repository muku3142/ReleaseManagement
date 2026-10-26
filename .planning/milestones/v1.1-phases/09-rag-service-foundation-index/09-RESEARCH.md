# Phase 9: RAG Service Foundation & Index - Research

**Researched:** 2026-10-13
**Domain:** Standalone Spring Boot 4.1 service, pgvector in Postgres 18, Ollama `/api/embed`, Postgres-table work queue, API-key tenancy, OpenAPI
**Confidence:** HIGH (every environment flag was probed on this machine; remaining MEDIUM items are tagged)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Already locked (PROJECT.md Key Decisions row "Exception (2026-10-13)", research/v1.1)**
- Standalone module `rag-service/` with its **own standalone pom** (the current app build is untouched), its own Dockerfile, and a compose service on **port 8081**.
- Stack: Java 25, Spring Boot 4.1 MVC; Flyway + `JdbcClient` with native SQL; `RestClient` to Ollama; springdoc-openapi. **No AI framework, no ORM for vectors.**
- Postgres image **`pgvector/pgvector:0.8.7-pg18-trixie`** in both compose files and every Testcontainers `*PersistenceIT`.
- Embeddings: `nomic-embed-text`, 768 dims, with `search_document:` / `search_query:` prefixes. `embedding_model` is stored on each chunk; a model change means a re-index.
- **Exact cosine scan, no HNSW/IVFFlat index** until a slow query is measured.
- Embedding runs **outside any DB transaction**. Ollama is the service's **only outbound call**. Logs record metadata only.
- Domain-free: the service knows client apps, collections, documents and chunks - nothing about releases, tickets or Jira.

**API keys & tenancy**
- **D-09-01: Clients are registered through an admin endpoint.** `POST /v1/admin/clients`, protected by a single admin key supplied by env (`RAG_ADMIN_KEY`), creates a client app and returns its API key **once**. Only a hash is stored. Rotate and revoke endpoints are included. Adding an app needs no redeploy.
- **D-09-02: Collections are created explicitly** with `PUT /v1/collections/{c}`, which fixes the collection's embedding model (and so its dimension) before any push. Pushing to an unknown collection is an error (404), never an implicit create.
- **D-09-03: Strict isolation, no sharing in v1.1.** An app sees only its own collections and documents. Any other app's collection is reported as **404**, not 403. Cross-app grants are deferred.
- **D-09-04: Keys do not expire.** They stay valid until rotated or revoked. `last_used_at` is recorded so unused keys can be spotted.

**Documents & chunking**
- **D-09-05: The service chunks.** Callers send whole text. The service splits on Markdown headings, then paragraphs, at roughly 1,000-1,500 characters with a small overlap. Each chunk is prefixed with the document title as a context header. Callers cannot pre-chunk in v1.1.
- **D-09-06: Text formats are plain text and Markdown only.** Callers convert anything else first. The service never parses HTML, PDF or DOCX (no Apache Tika).
- **D-09-07: Metadata is a flat JSON object of string and number values.** Search filters support **equality and `in` lists, ANDed together** (e.g. `release_id = X AND project_key IN [...]`). There is no query language.
- **D-09-08: Document text is limited to 1 MB.** Larger payloads are rejected with **413**; callers split them.

**Ingestion queue & outages**
- **D-09-09: The queue is a Postgres table.** Document rows carry a status: `PENDING`, `INDEXED` or `FAILED`. A worker claims rows with `FOR UPDATE SKIP LOCKED`. It survives restarts and needs no broker.
- **D-09-10: Pushes are accepted while Ollama is down.** A push still returns **202** and the document stays `PENDING`. The worker retries with exponential backoff, capped at about 5 minutes, and resumes by itself when Ollama returns. `GET /v1/status` reports Ollama health and queue depth.
- **D-09-11: Last write wins.** A push replaces the stored text and content hash. The worker always embeds the current text, and an in-flight embedding of stale text is discarded. Callers send no version. Re-pushing unchanged content (same hash) is a no-op.
- **D-09-12: `FAILED` means bad content only.** An Ollama outage never marks a document `FAILED`. Only a content error does, such as text the model rejects. The reason is shown in status. A re-push or re-index retries the document.

**Deployment & database**
- **D-09-13: Same Postgres container, separate database and user.** A second database `ragdb`, owned by its own `rag` user, runs in the existing Postgres container (now on the pgvector image). Neither user can read the other's data. `ragdb` can move to its own server later.
- **D-09-14: Database creation.** On fresh volumes, an init script (`/docker-entrypoint-initdb.d`) creates `ragdb`, the `rag` user and `CREATE EXTENSION vector`. Init scripts run only once, so a small documented one-off command does the same on the **existing** volume. The service's own Flyway owns its schema.
- **D-09-15: `rag-service` starts by default** with `docker compose -f compose.app.yaml up`, on 8081, depending on Postgres only. With Ollama down it still starts and just queues work.
- **D-09-16: Ollama is reached through configuration.** `RAG_OLLAMA_BASE_URL` defaults to `http://host.docker.internal:11434`, and `extra_hosts: host.docker.internal:host-gateway` is added for Linux hosts. The service uses the host's Ollama and GPU; there is no Ollama container.
- **D-09-17 (already applied, commit 7fc980b):** Postgres is published on `127.0.0.1:5432` in `compose.app.yaml` for desktop SQL clients, loopback only.

### Claude's Discretion
- Exact table and column names, the API path and payload details beyond those fixed above, and the backoff curve.
- Chunk size and overlap within the 1,000-1,500 character range. Tune these on real text.
- Search response shape (score, passage text, document id, title, URL, metadata) and the `k` default and maximum.
- How the admin key and client keys are hashed: a standard password hash such as BCrypt/Argon2, or a SHA-256 of a high-entropy key. Prefer the simplest secure option for random keys.
- Health and readiness: Ollama health appears in `/v1/status` and Actuator health but **never gates readiness**.

### Deferred Ideas (OUT OF SCOPE)
- Cross-app collection sharing or read grants (D-09-03): later milestone.
- Caller-supplied pre-chunked sections (D-09-05).
- Server-side extraction of HTML, PDF or DOCX (D-09-06).
- An HNSW index: only after a measured slow query.
- Key expiry policies (D-09-04).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| RAG-01 | App with own API key creates collections, pushes/replaces/deletes documents by own ids, sees only own collections | Client/collection/document schema, SHA-256 key hashing, 404-on-foreign-collection scoping query pattern, admin key flow |
| RAG-02 | Pushed documents searchable by meaning; unchanged content not re-embedded; deleted never appears | Hash covers title+text; `/api/embed` verified shapes; chunk table with cascade delete; exact cosine SQL |
| RAG-03 | Async ingestion survives Ollama outages | `FOR UPDATE SKIP LOCKED` claim with lease, worker-level backoff, outage-vs-content error classification verified against real Ollama error responses |
| RAG-04 | Re-index a collection; see per-collection status and last-indexed time | Collection status endpoint from document status counts + `indexed_at`; re-index = reset documents to PENDING |
| RAG-05 | Search with metadata filters, ranked passages with source references | jsonb containment filters (no `?` operator), `<=>` cosine ordering, response shape |
| RAG-10 | OpenAPI contract published | springdoc-openapi 3.1.1 (Boot 4.1 line), IT that asserts every route is in `/v3/api-docs` |
</phase_requirements>

## Summary

All of the environment research flags resolve cleanly, with three corrections to assumptions in CONTEXT.md that the planner must act on. (1) `CREATE EXTENSION vector` cannot be run by the non-superuser `rag` role: `vector.control` has no `trusted = true`, and the attempt fails with "Must be superuser". The extension must be created by the superuser in the init script and the one-off command; the service's Flyway migration keeps `CREATE EXTENSION IF NOT EXISTS vector`, which is a verified no-op for `rag` once the extension exists. (2) D-09-13 "neither user can read the other's data" is only true one way: `releasemgmt` is the `POSTGRES_USER` and therefore a **superuser**, so the main app's role can read `ragdb`. The `rag` role can be locked out of `releasemgmt` with `REVOKE CONNECT ... FROM PUBLIC` (verified). Document this honestly. (3) The Ollama context limit is 2048 tokens and the default `truncate=true` silently truncates; the service must send `truncate:false` so over-length input becomes a 400 (a content error, `FAILED`) rather than a silently wrong vector.

The pgvector image `pgvector/pgvector:0.8.7-pg18-trixie` pulls (PG 18.6, vector 0.8.7), and a byte copy of the live `pgdata` volume booted cleanly under it with Flyway history intact. Docker Desktop containers reach the loopback-bound host Ollama at `host.docker.internal:11434` with or without `extra_hosts`. `nomic-embed-text` returns 768-dim L2-normalised vectors from `/api/embed`; on a 5-ticket sample it ranked the right document first for 5 of 5 queries, with and without prefixes, but prefixes widened the margin to the runner-up. Embedding and a partly-offloaded 7B chat model were resident together on the 4 GB GPU.

The architecture is a small hexagonal Spring Boot app copied in shape (not code) from the main app, with `JdbcClient` native SQL, a `@Scheduled` single worker that claims documents with `SKIP LOCKED` in a short transaction, calls Ollama outside any transaction, and writes chunks back in a second short transaction guarded by the content hash so stale embeds are discarded.

**Primary recommendation:** Build `rag-service/` as a standalone Maven module (Boot 4.1.0 parent, `JdbcClient`, springdoc 3.1.1, no JPA), with `vector(768)` fixed in the schema, SHA-256-hashed random API keys, a document-row queue claimed with a lease, and an init script that runs as superuser to create `rag`, `ragdb` and the extension.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| API-key authentication and tenant scoping | API / Backend (servlet filter/interceptor) | Database (FK scoping by `client_id`) | Every query is scoped by the authenticated client id; foreign collection = 404 |
| Collection and document CRUD, 413/400 validation | API / Backend | Database | Controller validates, adapter persists |
| Chunking (heading/paragraph split, header, overlap) | API / Backend (pure domain code) | - | Deterministic, framework-free, unit-testable |
| Embedding generation | External (host Ollama) via outbound adapter | - | Only outbound call; behind a port |
| Async indexing queue and retry/backoff | API / Backend (scheduled worker) | Database (queue state, lease) | Postgres table is the queue (D-09-09) |
| Vector storage and similarity search | Database (pgvector, exact scan) | API / Backend (SQL assembly) | `vector(768)` + `<=>`; no ANN index |
| DB/role/extension bootstrap | Database / container init (superuser) | Flyway (schema only) | Non-superuser cannot create the extension |
| OpenAPI contract | API / Backend (springdoc) | - | Generated from controllers, asserted by a test |
| Ollama health reporting | API / Backend (`/v1/status`, Actuator) | - | Informational only; never gates readiness |

## Project Constraints (from CLAUDE.md)

- Minimal code, no over-engineering, no over-abstraction; follow the framework's official patterns; three similar lines beat a premature abstraction.
- Validate only at system boundaries; no error handling for impossible scenarios; no feature flags/back-compat shims; no multi-line comment blocks, comment only the non-obvious WHY.
- Enterprise-grade: secure, observable, resilient; SOLID; no magic numbers (put sizes, timeouts, limits in configuration properties).
- Do not add planning/analysis markdown files beyond what is asked; prefer editing existing files.
- Git: stage specific files by name; commit messages explain WHY; no `--no-verify`; never commit secrets. (The Claude Code attribution reminder for commits overrides the global "no Co-Authored-By" rule only where the harness instructs it.)
- Project: the app build is untouched; v1 behaviour must not change; Postgres image switch must keep every existing `*PersistenceIT` green.
- Environment landmines (memory): set `JAVA_HOME=$HOME/.jdks/jdk-25` on every Maven call; the test `application.yml` shadows main's, so new properties go in both; `mvn clean` after public API changes; Docker Desktop must be running for Testcontainers; CRLF can appear from worktrees (`.gitattributes` already pins `*.sh text eol=lf`).
- GSD: all file edits go through a GSD workflow (this research writes only the RESEARCH.md).

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-parent | 4.1.0 | Standalone pom parent | Same as main app; project constraint [VERIFIED: /d/IdeaProjects/ReleaseManagement/pom.xml] |
| Java | 25 | Runtime | Main app uses 25; `~/.jdks/jdk-25` present [VERIFIED: filesystem] |
| spring-boot-starter-web | Boot-managed | REST API | Locked (MVC) |
| spring-boot-starter-jdbc | Boot-managed | `JdbcClient` + HikariCP | Locked: native SQL, no ORM; the main app gets JdbcClient via the JPA starter, this module needs only JDBC |
| spring-boot-starter-validation | Boot-managed | Bean Validation on request DTOs | Boundary validation |
| spring-boot-starter-actuator | Boot-managed | Health, container healthcheck | Same as main app |
| spring-boot-starter-flyway + `org.flywaydb:flyway-database-postgresql` | Boot-managed | Schema migration | Same artifacts the main app uses [VERIFIED: main pom] |
| org.postgresql:postgresql | Boot-managed (runtime) | JDBC driver | |
| org.springdoc:springdoc-openapi-starter-webmvc-ui | 3.1.1 | OpenAPI 3 generation (+ Swagger UI) | 3.1.x is the line that targets Boot 4.1.0 [VERIFIED: Maven Central metadata 3.1.1 lastUpdated 2026-09-06; github.com/springdoc/springdoc-openapi/releases: v3.1.0 "Upgrades to Boot 4.1.0"] |

### Supporting (test scope)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| spring-boot-starter-test, spring-boot-starter-webmvc-test | Boot-managed | JUnit 6, AssertJ, Mockito, MockMvc | Always (mirrors main pom) |
| org.testcontainers:testcontainers-postgresql, testcontainers-junit-jupiter | 2.0.5 | Real pgvector Postgres in ITs | All persistence/worker ITs [VERIFIED: main pom, jars in ~/.m2] |
| spring-boot-testcontainers | Boot-managed | `@ServiceConnection` | Same pattern as main ITs |
| org.wiremock:wiremock-standalone | 3.13.2 | Stub Ollama (outage, 400, 404, slow) | Adapter and worker ITs; the main pom uses the standalone artifact [VERIFIED: main pom] |
| com.tngtech.archunit:archunit | 1.4.2 | Hexagonal boundary test | Mirror `ArchitectureBoundaryTest` [VERIFIED: main pom] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hand-written chunker (~60 lines, pure domain) | Spring AI `TokenTextSplitter`, LangChain4j splitters, flexmark AST | Locked "no AI framework"; the split rules (headings, paragraphs, 1,000-1,500 chars, overlap, header) are small and need exact control |
| SHA-256 of a 256-bit random key | BCrypt/Argon2 | Slow hashes defend low-entropy passwords; for 256-bit random keys SHA-256 is standard, supports indexed lookup by hash, and needs no dependency. Recommended (Claude's discretion) |
| `@Scheduled` fixed-delay worker | Dedicated thread / `SmartLifecycle` loop | `@Scheduled` is the least code; a single worker bean with in-memory backoff state suffices |
| Servlet filter/interceptor for keys | spring-boot-starter-security | Security is not needed for header-key auth plus a hash lookup; fewer moving parts. Add Security only if a later phase needs it |

**Installation (pom.xml dependencies, versions Boot-managed unless shown):**
```xml
spring-boot-starter-web, spring-boot-starter-jdbc, spring-boot-starter-validation,
spring-boot-starter-actuator, spring-boot-starter-flyway, org.flywaydb:flyway-database-postgresql,
org.postgresql:postgresql (runtime), org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1
<!-- test -->
spring-boot-starter-test, spring-boot-starter-webmvc-test, spring-boot-testcontainers,
org.testcontainers:testcontainers-postgresql:2.0.5, org.testcontainers:testcontainers-junit-jupiter:2.0.5,
org.wiremock:wiremock-standalone:3.13.2, com.tngtech.archunit:archunit:1.4.2
```
Properties block copied from the main pom: `java.version` 25, `maven.compiler.release` 25, UTF-8. Do NOT add `spring-boot-docker-compose` to the rag pom (the main app uses it for `mvn spring-boot:run`; the rag service gets its DB from env/compose).

## Architecture Patterns

### System Architecture Diagram

```
 client app --HTTPS/HTTP + "Authorization: Bearer <key>"--> [ApiKeyFilter]
                                                              |  SHA-256(key) -> client_app row (revoked? -> 401), touch last_used_at
                                                              v
     PUT /v1/collections/{c}      ------------------>  CollectionController -> CollectionService
     PUT/DELETE /v1/collections/{c}/documents/{id} -->  DocumentController   -> DocumentService
     POST /v1/collections/{c}/search ---------------->  SearchController     -> SearchService
     POST /v1/collections/{c}/reindex, GET status -->   (same services)
     /v1/admin/clients (admin key) ------------------>  AdminController      -> ClientService
                                                              |
        every repository call scoped by client_id (foreign collection => empty => 404)
                                                              v
                         +------------------ Postgres ragdb (pgvector, exact scan) ------------------+
                         | client_app | collection | document(status, content_hash, lease) | chunk(vector) |
                         +-------------------------------------------------------------------------+
                              ^ push: upsert doc row, status=PENDING (202) - never calls Ollama
                              |
   IndexWorker (@Scheduled, single bean, in-memory backoff)
     1. tx A: claim N PENDING docs due now (FOR UPDATE SKIP LOCKED), set lease_until; commit
     2. NO TRANSACTION: chunk text, call OllamaEmbeddingPort.embed(batches)  --> host Ollama /api/embed
          connect/timeout/5xx/404-model  => outage: release claim, worker backoff (cap 5 min), doc stays PENDING
          400 "input length exceeds"     => content error: doc FAILED + reason
     3. tx B: if document.content_hash still == claimed hash: delete old chunks, insert new chunks,
              status=INDEXED, indexed_at=now(); else discard (a newer push already re-queued it)
   Search: embed("search_query: "+q) -> SELECT ... ORDER BY embedding <=> CAST(:q AS vector) LIMIT :k
```

### Recommended Project Structure
```
rag-service/
├── pom.xml                      # standalone, parent spring-boot-starter-parent 4.1.0
├── Dockerfile                   # multi-stage, context ./rag-service (mirror of root Dockerfile minus frontend)
├── src/main/java/com/evision/ragservice/
│   ├── domain/                  # Chunker, ContentHash, MetadataFilter, status enum, records (no framework imports)
│   ├── ports/                   # EmbeddingPort, DocumentStore/ClientStore/ChunkSearch ports
│   ├── application/             # ClientService, CollectionService, DocumentService, SearchService, IndexingService
│   ├── adapters/
│   │   ├── web/                 # controllers, ApiKeyFilter, GlobalExceptionHandler (ProblemDetail + reason)
│   │   ├── persistence/         # JdbcClient repositories (native SQL)
│   │   └── ollama/              # OllamaEmbeddingAdapter (RestClient)
│   └── config/                  # RestClient bean, properties records, IndexWorker scheduling
├── src/main/resources/db/migration/V1__rag_schema.sql
├── src/main/resources/application.yml
└── src/test/...                 # ArchitectureBoundaryTest, *PersistenceIT, worker IT with WireMock, OpenAPI IT
docker/initdb/10-ragdb.sh        # at repo root, mounted into postgres in BOTH compose files
```
Package root name is the planner's choice; mirror the `com.evision.*` convention. Transactions go on adapter methods (the main app's convention); the worker's two short transactions are therefore two separate adapter calls with the Ollama call between them.

### Pattern 1: Init script creates role, database, extension (superuser), idempotent
Verified on a fresh `pgvector/pgvector:0.8.7-pg18-trixie` container and re-run against it afterwards (idempotent). Keep as `docker/initdb/10-ragdb.sh` (LF; `.gitattributes` already pins `*.sh`).
```bash
#!/bin/bash
set -euo pipefail
: "${RAG_DB_PASSWORD:?}"
psql -v ON_ERROR_STOP=1 -v rag_pw="$RAG_DB_PASSWORD" --username "$POSTGRES_USER" --dbname postgres <<'EOSQL'
SELECT 'CREATE ROLE rag LOGIN PASSWORD ' || quote_literal(:'rag_pw') WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'rag') \gexec
SELECT 'CREATE DATABASE ragdb OWNER rag' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ragdb') \gexec
REVOKE CONNECT ON DATABASE ragdb FROM PUBLIC;
EOSQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname ragdb -c 'CREATE EXTENSION IF NOT EXISTS vector'
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -c "REVOKE CONNECT ON DATABASE \"$POSTGRES_DB\" FROM PUBLIC"
```
Source: probe run 2026-10-13 [VERIFIED: docker]. Notes: `rag` then owns `ragdb`, can create tables in `public` and use the `vector` type; `rag` connecting to `releasemgmt` gets "permission denied for database" after the last REVOKE.

**Existing volume (init scripts do not re-run):** recreate the postgres container so the script is mounted (`docker compose -f compose.app.yaml up -d postgres` after the compose edit), then run the same script as the documented one-off:
`docker compose -f compose.app.yaml exec -e RAG_DB_PASSWORD=<pw> postgres bash /docker-entrypoint-initdb.d/10-ragdb.sh`. Rerun was verified idempotent (REVOKE, `NOTICE: extension "vector" already exists, skipping`). [ASSUMED: the compose-exec form on the real stack was not executed to avoid disturbing the running containers; the script itself was executed in the probe container.]

### Pattern 2: Compose changes (both files)
```yaml
# compose.app.yaml and compose.yaml, postgres service
image: pgvector/pgvector:0.8.7-pg18-trixie
environment:
  RAG_DB_PASSWORD: ${RAG_DB_PASSWORD:-rag}
volumes:
  - pgdata:/var/lib/postgresql                      # unchanged (compose.app.yaml)
  - ./docker/initdb:/docker-entrypoint-initdb.d:ro
# compose.yaml ONLY (Spring Boot docker-compose integration matches by image name "postgres"):
labels:
  org.springframework.boot.service-connection: postgres
```
```yaml
# compose.app.yaml
rag-service:
  build: { context: ./rag-service, dockerfile: Dockerfile }
  image: rag-service:0.1.0-SNAPSHOT
  depends_on: { postgres: { condition: service_healthy } }
  environment:
    RAG_DB_URL: jdbc:postgresql://postgres:5432/ragdb
    RAG_DB_USER: rag
    RAG_DB_PASSWORD: ${RAG_DB_PASSWORD:-rag}
    RAG_ADMIN_KEY: ${RAG_ADMIN_KEY:?set RAG_ADMIN_KEY, e.g. openssl rand -base64 32}
    RAG_OLLAMA_BASE_URL: ${RAG_OLLAMA_BASE_URL:-http://host.docker.internal:11434}
  extra_hosts: ["host.docker.internal:host-gateway"]
  ports: ["${RAG_PORT:-8081}:8081"]
  restart: unless-stopped
```
`${RAG_ADMIN_KEY:?}` follows the `RM_CREDENTIAL_KEY` fail-fast convention but means every existing `docker compose -f compose.app.yaml up` fails until `.env` has the key; update `.env.example` and `docs/docker.md` (and fix the stale "both stacks publish 5432" line 42). This is a planner/user choice (see Open Questions).

### Pattern 3: Schema (Flyway `V1__rag_schema.sql`, illustrative; names are discretion)
```sql
CREATE EXTENSION IF NOT EXISTS vector;   -- no-op when the init script already created it (verified as non-superuser)

CREATE TABLE client_app (
  id uuid PRIMARY KEY, name text NOT NULL UNIQUE, key_hash bytea NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(), revoked_at timestamptz, last_used_at timestamptz);
CREATE TABLE collection (
  id uuid PRIMARY KEY, client_id uuid NOT NULL REFERENCES client_app(id),
  name text NOT NULL, embedding_model text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (client_id, name));
CREATE TABLE document (
  id uuid PRIMARY KEY, collection_id uuid NOT NULL REFERENCES collection(id) ON DELETE CASCADE,
  external_id text NOT NULL, title text NOT NULL, url text, metadata jsonb NOT NULL DEFAULT '{}',
  body text NOT NULL, content_hash bytea NOT NULL,
  status text NOT NULL CHECK (status IN ('PENDING','INDEXED','FAILED')),
  status_reason text, indexed_hash bytea, indexed_at timestamptz,
  lease_until timestamptz, updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (collection_id, external_id));
CREATE INDEX document_queue ON document (status, updated_at) WHERE status = 'PENDING';
CREATE TABLE chunk (
  id bigserial PRIMARY KEY, document_id uuid NOT NULL REFERENCES document(id) ON DELETE CASCADE,
  collection_id uuid NOT NULL, ordinal int NOT NULL, content text NOT NULL,
  embedding vector(768) NOT NULL, embedding_model text NOT NULL, UNIQUE (document_id, ordinal));
CREATE INDEX chunk_collection ON chunk (collection_id);   -- plain btree; NO hnsw/ivfflat
CREATE INDEX document_metadata ON document USING gin (metadata jsonb_path_ops);  -- optional, only if filters measure slow
```
Design points the planner must carry:
- `vector(768)` fixes the dimension in DDL. D-09-02 says the collection fixes "model (and so dimension)": in v1.1 only 768-dim models can be accepted. Validate `PUT /v1/collections/{c}` against a configured allow-list (`nomic-embed-text`) and return 400 otherwise (see Open Questions).
- `content_hash` = SHA-256 over **title + body** (both are embedded, since the title is the chunk header). URL and metadata are updated in place on every push without re-embedding, so filters and source references never go stale. Same hash + same collection model => no status change, no re-embed.
- `ON DELETE CASCADE` on `chunk` makes "a deleted document never appears" a single statement. Search joins `document` only for `status`-agnostic metadata and must also require `chunk.embedding_model = collection.embedding_model`.
- Replacing a document: old chunks stay searchable until tx B swaps them (serve-old-until-new-ready). Decide and document; the alternative (hide on push) makes a document vanish for the whole outage.
- `FAILED` documents keep any old chunks only if they were INDEXED earlier; a re-push or re-index sets them `PENDING` again.

### Pattern 4: Claim, embed outside the transaction, guarded write-back
```sql
-- tx A (short): claim with a lease so a crashed worker's rows return to the pool
UPDATE document SET lease_until = now() + :lease
WHERE id IN (SELECT id FROM document
             WHERE status = 'PENDING' AND (lease_until IS NULL OR lease_until < now())
             ORDER BY updated_at LIMIT :batch FOR UPDATE SKIP LOCKED)
RETURNING id, collection_id, title, body, content_hash;
```
```sql
-- tx B (short): only if the document was not re-pushed meanwhile
UPDATE document SET status='INDEXED', indexed_hash=:hash, indexed_at=now(), lease_until=NULL, status_reason=NULL
WHERE id=:id AND content_hash=:hash;           -- 0 rows => stale, discard, do not insert chunks
-- same transaction, only when 1 row updated: DELETE FROM chunk WHERE document_id=:id; INSERT chunks...
```
A push of changed content does `UPDATE document SET body=..., content_hash=..., status='PENDING', lease_until=NULL` so a stale in-flight embed fails the `content_hash` guard (D-09-11). Backoff is **worker-level** (in-memory: on any outage double the delay from ~1 s to the 5 min cap, reset on success), not per-document attempts: an outage then costs one failed call per tick rather than one per queued document, and no row is touched. On outage release the claim (`lease_until=NULL`) so recovery is immediate.

### Pattern 5: Search
```sql
SELECT d.external_id, d.title, d.url, d.metadata, c.ordinal, c.content,
       1 - (c.embedding <=> CAST(:q AS vector)) AS score
FROM chunk c JOIN document d ON d.id = c.document_id
WHERE c.collection_id = :collectionId AND c.embedding_model = :model
  AND d.metadata @> CAST(:f1 AS jsonb)                       -- equality filter
  AND (d.metadata @> CAST(:in1a AS jsonb) OR d.metadata @> CAST(:in1b AS jsonb))  -- `in` list
ORDER BY c.embedding <=> CAST(:q AS vector) LIMIT :k
```
Pass the query vector as the string `"[0.1,0.2,...]"` bound to a `CAST(:q AS vector)`; no `pgvector-java` needed. Build the optional filter clauses in Java with generated parameter names; metadata values are bound as jsonb parameters, never concatenated. `@>` is type-sensitive (string vs number) which matches D-09-07. Do NOT use the jsonb `?`/`?|` operators: `?` collides with JDBC placeholders. Return chunk-level hits (a document can appear more than once; leave grouping to callers or collapse to the best chunk per document in SQL with `DISTINCT ON` - discretion).

### Pattern 6: Ollama adapter (verified shapes)
`POST {base}/api/embed` body `{"model":"nomic-embed-text","input":["search_document: <title>\n<chunk>", ...],"truncate":false}`; response `{"model","embeddings":[[768 floats]...],"total_duration","load_duration","prompt_eval_count"}`. Use the main app's `JiraRestClientConfig` shape (JDK `HttpClient` with connect timeout + `JdkClientHttpRequestFactory.setReadTimeout`), timeouts from properties. Batch at most ~32 inputs per call (32 x 1,500 chars took 0.6 s warm on this GPU). Keep the request body **identical in `options`** across calls (see Pitfall 6).

Error classification (all observed on Ollama 0.34.0, see Sources):
| Ollama response | Meaning | Action |
|---|---|---|
| connect refused / timeout / 5xx | outage | worker backoff; doc stays `PENDING` |
| 404 `{"error":"model \"x\" not found, try pulling it first"}` | operator problem (model not pulled) | treat as outage, surface in `/v1/status`; never `FAILED` |
| 400 `{"error":"the input length exceeds the context length"}` | content error | `FAILED` with reason (D-09-12); only reachable with `truncate:false` |
| 200 with blank input | returns a (meaningless) embedding | reject/skip blank chunks and blank documents before calling |

### Anti-Patterns to Avoid
- **Holding a DB transaction/lock across the Ollama call** (violates D-09-09/locked rule and starves the Hikari pool during outages).
- **Per-document attempt counters for outages:** hammers Ollama once per queued row and risks accidentally reaching `FAILED`.
- **A `@Transactional` on the application service that wraps the worker loop.**
- **Registering the Ollama health indicator in the default health group while the container healthcheck hits `/actuator/health`** (see Pitfall 4).
- **Implicit collection creation on push** (D-09-02 forbids it).
- **403 for another app's collection** (must be 404; scope every SQL by `client_id`, including joins from chunk/document).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Constant-time key comparison | `String.equals` on secrets | Lookup by SHA-256 hash (indexed unique column); `MessageDigest.isEqual` for the single `RAG_ADMIN_KEY` | Plain JDK; no timing oracle on the secret itself |
| Random API keys | `Random`/UUID | `SecureRandom` 32 bytes, base64url | 256-bit entropy is what makes plain SHA-256 safe |
| OpenAPI document | Hand-written JSON/YAML | springdoc generation + an IT asserting coverage | Contract must match code |
| ProblemDetail error bodies | Custom error JSON | Spring `ProblemDetail` + `@RestControllerAdvice` with a `reason` property (main app convention) | Standard (RFC 9457) |
| Embedding HTTP plumbing | Spring AI / LangChain4j | `RestClient` + JDK HttpClient (main app's `JiraRestClientConfig` shape) | Locked decision; two endpoints only |
| Vector type binding | `pgvector-java`, `hibernate-vector` | Text literal + `CAST(:q AS vector)` | Zero dependencies, verified type works through psql; confirm via IT |
| Queue broker | RabbitMQ/Kafka/Redis | `document` table + `FOR UPDATE SKIP LOCKED` | Locked decision |
| Migrations | Manual DDL | Flyway (`V{n}__*.sql`) | Locked |

**Hand-roll deliberately (small, framework-free):** the chunker. It is the one piece of domain logic and should be a pure class with unit tests.

## Runtime State Inventory

Not a rename/refactor phase, but it changes the image of a live stateful service, so the equivalent inventory is:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | Volume `releasemanagement_pgdata` holds the running `releasemgmt` DB (PG 18.4 data layout `/var/lib/postgresql/18/docker`, Flyway history 10 rows) | None for data; image swap 18.4 to 18.6 is a minor upgrade. A byte-copy of the volume booted under the pgvector image (crash recovery, no errors) [VERIFIED: docker] |
| Live service config | Running containers `releasemanagement-postgres-1` (postgres:18) and `releasemanagement-app-1`; no `ragdb`/`rag` yet | `docker compose up -d` recreates postgres on the new image; run the one-off script after |
| OS-registered state | None - verified: no Task Scheduler/pm2 entries involved; the app is an "orphan container" per project memory | None |
| Secrets / env vars | `.env` lacks `RAG_ADMIN_KEY` and `RAG_DB_PASSWORD`; `.env` is gitignored | Add to `.env.example`; document; user must set `RAG_ADMIN_KEY` if `:?` is chosen |
| Build artifacts | 18 `*IT` files hardcode `"postgres:18"` (`grep` on `src/test`); Docker image `pgvector/pgvector:0.8.7-pg18-trixie` now cached locally (digest `sha256:9d9c9302...`) | Code edit of the 18 constructors (see Pitfall 1) |

## Common Pitfalls

### Pitfall 1: Testcontainers rejects the pgvector image name
**What goes wrong:** `new PostgreSQLContainer<>("pgvector/pgvector:...")` throws an incompatible-image error. **Why:** the container constructor calls `DockerImageName.assertCompatibleWith(postgres)` [VERIFIED: javap on testcontainers-postgresql 2.0.5, both the deprecated `org.testcontainers.containers.PostgreSQLContainer` the repo imports and the new `org.testcontainers.postgresql` one]. **How to avoid:** `DockerImageName.parse("pgvector/pgvector:0.8.7-pg18-trixie").asCompatibleSubstituteFor("postgres")`. 18 files change; put the image in one shared test constant so a future bump is one edit (a legitimate dedupe: one fact, 18 uses). Testcontainers ships its own `PgVectorContainerProvider`, so the substitution is the supported route. **Warning sign:** `IllegalStateException: Failed to verify that image ... is a compatible substitute`. Note: the ITs were not run against the new image in this research; success criterion 1 requires the full `mvn` suite to be run on it (it is the same Postgres 18 server plus an extension, and the container logs the same readiness line).

### Pitfall 2: Non-superuser cannot `CREATE EXTENSION vector`
`vector.control` (0.8.7) has no `trusted = true` [VERIFIED: docker cat]; as `rag`: `ERROR: permission denied to create extension "vector" ... Must be superuser`. Create it in the init script/one-off as superuser; keep `CREATE EXTENSION IF NOT EXISTS vector` in V1 because Testcontainers' default user is a superuser (so ITs work with no init script) and for `rag` it is a verified no-op. One IT should replay the real init script into a Testcontainers Postgres and run Flyway as `rag` to prove the production privilege path.

### Pitfall 3: "Neither user can read the other's data" is one-way
`releasemgmt` is `POSTGRES_USER`, hence superuser [VERIFIED: `rolsuper = t`], and can read `ragdb`. Only `rag` is truly confined (after `REVOKE CONNECT ... FROM PUBLIC` on `releasemgmt`, `rag` is refused). Do not claim stronger isolation in docs. Moving `ragdb` to its own server (already deferred) is the real fix.

### Pitfall 4: Container healthcheck flaps when Ollama is down
If an Ollama `HealthIndicator` joins the default group and the Dockerfile `HEALTHCHECK` hits `/actuator/health` (as the main app's does), an Ollama outage turns the container unhealthy, contradicting D-09-15 and the "never gates readiness" rule. **Avoid:** health-check `/actuator/health/readiness` with `management.endpoint.health.group.readiness.include=readinessState,db` (Boot docs: the group property and the `readinessState`+`db` form) [CITED: docs.spring.io/spring-boot/reference/actuator/endpoints.html]; keep the Ollama indicator out of that group (it may stay in the default `/actuator/health` aggregate for operators but nothing automated may depend on it - or simply report Ollama in `/v1/status` only). Confirm `management.endpoint.health.probes.enabled=true` in the compose/Docker context since probe groups auto-enable on some platforms only [ASSUMED: set it explicitly; the doc text read here said probe groups are enabled automatically, so verify with a curl in the IT].

### Pitfall 5: Silent truncation and context overrun
Ollama defaults `truncate=true` and the model context is **2048 tokens** (`nomic-bert.context_length 2048`, `/api/ps` `context_length: 2048`). Measured tokens for a 1,500-char input (+ prefix): prose 379, digits 930, base64 1,066, CJK 1,506. So the 1,500-char cap (header and prefix included) is safe for all of these; send `truncate:false` so anything pathological returns 400 and becomes a `FAILED` content error instead of an incomplete vector.

### Pitfall 6: Changing request `options` reloads the model
A request with `options.num_gpu:0` reloaded the embedding model on CPU (11.3 s for a 32-chunk batch vs 0.6 s on GPU) and the next normal request reloaded it again (6.5 s). Never vary `options`/`keep_alive` per call; send none (or constants). Do not use per-request CPU forcing to save VRAM.

### Pitfall 7: Prose-text NUL characters and oversize bodies
Postgres `text`/`jsonb` reject `\u0000` -> an unhandled 500. Reject text/title/metadata strings containing NUL with 400. The 1 MB limit (D-09-08): Spring has no JSON body limit property, so check `Content-Length` (413 early) and re-check `body` length in UTF-8 bytes after parsing; chunked requests without a length are only bounded by the post-parse check [ASSUMED: acceptable for an internal, key-authenticated service].

### Pitfall 8: Linux hosts and Ollama bind address
Ollama listens on `127.0.0.1:11434` here (`netstat`) and Docker Desktop for Windows still reaches it through `host.docker.internal`, with or without `extra_hosts` [VERIFIED: `docker run curlimages/curl` returned `{"version":"0.34.0"}` and a successful `/api/embed` both ways]. On a native Linux host a container reaches the host only via the bridge gateway, so Ollama must listen on `0.0.0.0` or the bridge IP (`OLLAMA_HOST`), with firewalling because Ollama has no auth [CITED: research/v1.1/STACK.md and PITFALLS.md Pitfall 11; ASSUMED for this version, not testable on this Windows host]. Document in `docs/docker.md`.

### Pitfall 9: Flyway checksum/CRLF and the shadowing test `application.yml`
Migrations are checksummed; a CRLF flip changes the checksum. Keep `.sql` files LF (add `*.sql text eol=lf` to `.gitattributes` if worktrees have bitten before; memory notes CRLF from worktrees). The module has its own `src/test/resources/application.yml` that shadows main's: every new property goes in both.

### Pitfall 10: `docker compose up` ordering and the init script on existing volumes
Init scripts run only when the data directory is empty; the existing volume will never run `10-ragdb.sh`. The service will then fail Flyway ("database ragdb does not exist"). Document the one-off prominently and make the service log a clear message; do not auto-create databases from the app (it lacks the privilege by design).

## Code Examples

### API key hashing and lookup (JDK only)
```java
// registration: raw key shown once, only the digest persisted
byte[] raw = new byte[32]; new SecureRandom().nextBytes(raw);
String key = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(UTF_8));
// request path: hash the presented bearer token and SELECT ... WHERE key_hash = :hash AND revoked_at IS NULL
```
Rotate = replace `key_hash` in one UPDATE (old key dead immediately); revoke = set `revoked_at`. Touch `last_used_at` at most once per minute to avoid a write per request (discretion).

### Boundary test pattern (mirror of the main app)
```java
noClasses().that().resideInAnyPackage("..domain..", "..ports..")
  .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "tools.jackson..", "..adapters..", "..config..", "..application..")
  .allowEmptyShould(true).check(classes);
```

### Testcontainers shared image
```java
static final DockerImageName PGVECTOR =
    DockerImageName.parse("pgvector/pgvector:0.8.7-pg18-trixie").asCompatibleSubstituteFor("postgres");
@Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR);
```

### Ollama calls (verified live; curl-equivalent)
```
POST http://localhost:11434/api/embed
{"model":"nomic-embed-text","input":["search_document: ...","..."],"truncate":false}
-> 200 {"model":"nomic-embed-text","embeddings":[[...768...],[...]],"total_duration":..,"load_duration":..,"prompt_eval_count":15}
```
Vectors have L2 norm 1.000, so cosine distance `<=>` and inner product agree; keep `<=>` (locked cosine).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Ollama `/api/embeddings` (single `prompt`) | `/api/embed` (`input` string or array, `embeddings` array) | Ollama 0.3+ | Batch support; locked in CONTEXT |
| `org.testcontainers.containers.PostgreSQLContainer` | `org.testcontainers.postgresql.PostgreSQLContainer` (TC 2.x); old class still present, deprecated | TC 2.0 | Repo keeps the old import; both enforce the postgres-compat check |
| springdoc 2.x | springdoc 3.1.x for Boot 4.1 | 2026-08-01 (v3.1.0) | Use 3.1.1 |
| Handwritten ANN index up front | Exact scan until measured | pgvector guidance | Locked: no HNSW now |

**Deprecated/outdated:** `RestTemplate`, `pgvector-java`/`hibernate-vector` (not needed), Spring AI vector store (owns its table shape).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The compose-exec form of the one-off script works on the real running stack (script verified in an isolated probe container, stack not touched) | Pattern 1 | Doc command needs a tweak (e.g., `docker exec`) |
| A2 | Native Linux hosts need `OLLAMA_HOST=0.0.0.0`/bridge bind for container access | Pitfall 8 | Documentation inaccuracy only; Windows dev unaffected |
| A3 | Health probe groups need explicit `probes.enabled=true` outside Kubernetes | Pitfall 4 | Healthcheck path 404s; caught by a curl in an IT |
| A4 | Unbounded chunked JSON bodies are acceptable risk on an internal key-authenticated service | Pitfall 7 | Memory pressure from a hostile/buggy client |
| A5 | The 18 existing `*PersistenceIT`-style tests pass unmodified on the pgvector image apart from the constructor change | Pitfall 1 | Success criterion 1 fails; must run the full suite |
| A6 | Serving old chunks until the new embedding lands is the right replace semantic | Pattern 3 | Users may prefer hide-on-push; product call |
| A7 | `qwen3:4b` (2.5 GB) plus embeddings fit together on the 4 GB card | Environment | Phase 10 concern; not installed so untested |

## Open Questions

1. **Dimension vs "collection fixes the embedding model and dimension" (D-09-02)**
   - Known: `vector(768)` is fixed in DDL; `nomic-embed-text` is 768.
   - Unclear: whether another dimension must be accepted in v1.1.
   - Recommendation: allow-list models in config (only `nomic-embed-text` now); a different dimension later is a new migration plus re-index. Returns 400 for unlisted models.
2. **`RAG_ADMIN_KEY` fail-fast (`:?`) vs default**
   - D-09-15 says `docker compose up` starts rag-service by default; `:?` makes existing `up` fail until `.env` is updated.
   - Recommendation: keep `:?` (matches the `RM_*` secrets convention), update `.env.example` and docs; flag to the user.
3. **Search result granularity:** chunk-level hits vs best chunk per document. Recommendation: chunk-level with `documentId`, `title`, `url`, `metadata`, `chunkIndex`, `text`, `score`; collapse per document only if Phase 10 needs it.
4. **Re-index semantics:** reset all docs to `PENDING` and keep old chunks until each swap lands (consistent with A6), so search keeps working during a re-index.
5. **Retention of `FAILED` docs:** they stay until re-push/re-index; no automatic retry (D-09-12).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker Desktop | compose, Testcontainers | yes | 29.1.3 | - |
| pgvector image `0.8.7-pg18-trixie` | DB | yes (pulled, amd64) | PG 18.6, vector 0.8.7 | - |
| Existing stack | regression | running, healthy | postgres:18 (18.4) | - |
| Ollama (host) | embeddings | yes | 0.34.0, loopback-bound | - |
| `nomic-embed-text` | embeddings | yes | 274 MB, 768-dim, ctx 2048, F16 | - |
| Chat model for Phase 10 | later | `qwen2.5-coder:7b` only; `qwen3:4b` not pulled | - | pull later |
| GPU | Ollama | RTX 3050 Laptop, 4096 MiB (2661 MiB used with embed + 7B loaded) | - | CPU (slow: 11 s per 32-chunk batch) |
| JDK 25 | build | yes `~/.jdks/jdk-25` | 25 | - |
| Maven | build | yes | 3.9.11 | - |
| Node | n/a (no frontend in this phase) | yes | - | - |
| python / jq | scripts | no | - | use node/psql |

**Missing with no fallback:** none. **Missing with fallback:** chat model (not needed in Phase 9).

Probes performed (all cleaned up; the running stack was not touched): copy of the live volume booted under the pgvector image; fresh container with the init script; extension privileges; cross-database connect; container-to-host Ollama; embedding calls. GPU residency: `/api/ps` showed `nomic-embed-text` (0.32 GB) and `qwen2.5-coder:7b` (5.12 GB total, 2.28 GB in VRAM, rest on CPU) resident simultaneously with 2,661 MiB used of 4,096 MiB; chat+embed co-residence is possible but the 7B chat is mostly CPU-offloaded here. Ollama env defaults were not changed.

## Validation Architecture

Skipped: `workflow.nyquist_validation` is `false` in `.planning/config.json`. (Test infrastructure to create in Wave 0 is covered above: JUnit/Testcontainers/WireMock/ArchUnit mirrors of the main module; commands `JAVA_HOME=$HOME/.jdks/jdk-25 mvn -f rag-service/pom.xml test` and the main suite `JAVA_HOME=... mvn test` for criterion 1.)

## Security Domain

`security_enforcement` is enabled (ASVS level 1, block on high).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Long random API keys (256-bit), stored as SHA-256, bearer header; single admin key from env compared with `MessageDigest.isEqual`; reject missing/invalid with 401 |
| V3 Session Management | no | Stateless API |
| V4 Access Control | yes | Every query scoped by `client_id`; foreign collection = 404; admin endpoints only with admin key (clients cannot call them) |
| V5 Input Validation | yes | Bean Validation on DTOs; id charset/length (`[A-Za-z0-9._:-]{1,200}` recommended); metadata flat string/number only; NUL rejection; 1 MB limit; `k` max; filter keys bound as parameters |
| V6 Cryptography | yes | `SecureRandom`, SHA-256 from JDK; no custom crypto |
| V7 Logging | yes | Metadata only: never log document text, titles, queries or keys; log client id, collection, counts |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection via filter keys/values | Tampering | `JdbcClient` named parameters; filters assembled as `@>` jsonb parameters, never string concatenation |
| Tenant crossover via document/chunk id guessing | Information disclosure | Scope joins through `collection.client_id`; 404 not 403 |
| API key leakage | Information disclosure | Return once, store hash only, never log, not in OpenAPI examples |
| Oversized payload / memory exhaustion | DoS | 1 MB text limit, `k` max, batch size cap, Hikari pool unaffected by Ollama calls |
| Ollama exposure on the network | Spoofing/DoS | Ollama is the only outbound call; document loopback/firewall; service has no inbound path to it |
| Admin key brute force | Spoofing | High-entropy key from env; fail-fast if blank or short (e.g., require >= 32 chars) |
| Stored prompt injection text | Tampering | Out of scope here (Phase 10 fences passages); this phase stores text as data only |

## Sources

### Primary (HIGH confidence)
- Local probes on 2026-10-13: Docker 29.1.3; `pgvector/pgvector:0.8.7-pg18-trixie` pull, volume attach, init script, extension privileges, role isolation; Ollama 0.34.0 `/api/version`, `/api/show`, `/api/ps`, `/api/embed` (shapes, errors, token counts, batch timing, quality sample); container-to-host reachability; `javap` of testcontainers-postgresql 2.0.5.
- `D:\IdeaProjects\ReleaseManagement\pom.xml`, `compose.app.yaml`, `compose.yaml`, `Dockerfile`, `docs/docker.md`, `JiraRestClientConfig.java`, `ArchitectureBoundaryTest.java`, `src/test/resources/application.yml`, 18 `*IT` files.
- https://docs.spring.io/spring-boot/reference/actuator/endpoints.html (health groups, readiness include syntax)
- https://docs.spring.io/spring-boot/reference/features/dev-services.html (service-connection label; matches by image name, `postgres` not `pgvector/pgvector`)
- https://github.com/springdoc/springdoc-openapi/releases (v3.1.0 targets Boot 4.1.0) and Maven Central `springdoc-openapi-starter-webmvc-ui` metadata (3.1.1, 2026-09-06)

### Secondary (MEDIUM confidence)
- `.planning/research/v1.1/STACK.md`, `PITFALLS.md` (Ollama API details, Linux networking) - training-knowledge-based in part; the Ollama parts above were re-verified empirically.

### Tertiary (LOW confidence)
- None relied upon.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - versions read from the main pom and Maven Central; springdoc line confirmed against release notes.
- Architecture: HIGH for DB/Ollama/queue mechanics (probed); MEDIUM for application-layer details (discretionary naming/shapes).
- Pitfalls: HIGH for items marked VERIFIED; MEDIUM for A2-A4.
- Quality: LOW-MEDIUM - 5 tickets x 5 queries is a smoke check only (right document ranked first 5/5; prefix margins e.g. 0.712 vs 0.499 with, 0.649 vs 0.429 without). Correct-hit cosine scores were 0.58-0.76, so no absolute relevance cutoff should be assumed; a golden set belongs to Phase 10/13.

**Research date:** 2026-10-13
**Valid until:** 2026-11-12 (Ollama and Boot minor releases move quickly; re-check springdoc and image tags if planning slips)
