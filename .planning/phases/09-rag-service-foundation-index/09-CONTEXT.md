# Phase 9: RAG Service Foundation & Index - Context

**Gathered:** 2026-10-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Delivers a running, domain-free `rag-service` that any internal app can use with its own API key. An app can:
- create collections;
- push, replace and delete documents;
- search them by meaning, entirely on the local machine.

Indexing keeps working through Ollama outages, and the API is published as an OpenAPI contract. Requirements: RAG-01..05, RAG-10.

Not in this phase:
- chat, citations and refusal (Phase 10);
- the tool-call protocol (Phase 11);
- ReleaseManagement pushing its content or using the service (Phase 12);
- any UI (Phase 13).

v1 behaviour must not change.
</domain>

<decisions>
## Implementation Decisions

### Already locked (PROJECT.md Key Decisions row "Exception (2026-10-13)", research/v1.1)
- Standalone module `rag-service/` with its **own standalone pom** (the current app build is untouched), its own Dockerfile, and a compose service on **port 8081**.
- Stack:
  - Java 25, Spring Boot 4.1 MVC;
  - Flyway + `JdbcClient` with native SQL;
  - `RestClient` to Ollama;
  - springdoc-openapi.
  - **No AI framework, no ORM for vectors.**
- Postgres image **`pgvector/pgvector:0.8.7-pg18-trixie`** in both compose files and every Testcontainers `*PersistenceIT`.
- Embeddings: `nomic-embed-text`, 768 dims, with `search_document:` / `search_query:` prefixes. `embedding_model` is stored on each chunk; a model change means a re-index.
- **Exact cosine scan, no HNSW/IVFFlat index** until a slow query is measured.
- Embedding runs **outside any DB transaction**. Ollama is the service's **only outbound call**. Logs record metadata only.
- Domain-free: the service knows client apps, collections, documents and chunks — nothing about releases, tickets or Jira.

### API keys & tenancy
- **D-09-01: Clients are registered through an admin endpoint.**
  - `POST /v1/admin/clients`, protected by a single admin key supplied by env (`RAG_ADMIN_KEY`), creates a client app and returns its API key **once**.
  - Only a hash is stored. Rotate and revoke endpoints are included.
  - Adding an app needs no redeploy.
- **D-09-02: Collections are created explicitly** with `PUT /v1/collections/{c}`, which fixes the collection's embedding model (and so its dimension) before any push. Pushing to an unknown collection is an error (404), never an implicit create.
- **D-09-03: Strict isolation, no sharing in v1.1.** An app sees only its own collections and documents. Any other app's collection is reported as **404**, not 403. Cross-app grants are deferred.
- **D-09-04: Keys do not expire.** They stay valid until rotated or revoked. `last_used_at` is recorded so unused keys can be spotted.

### Documents & chunking
- **D-09-05: The service chunks.**
  - Callers send whole text. The service splits on Markdown headings, then paragraphs, at roughly 1,000-1,500 characters with a small overlap.
  - Each chunk is prefixed with the document title as a context header.
  - Callers cannot pre-chunk in v1.1.
- **D-09-06: Text formats are plain text and Markdown only.** Callers convert anything else first. The service never parses HTML, PDF or DOCX (no Apache Tika).
- **D-09-07: Metadata is a flat JSON object of string and number values.** Search filters support **equality and `in` lists, ANDed together** (e.g. `release_id = X AND project_key IN [...]`). There is no query language.
- **D-09-08: Document text is limited to 1 MB.** Larger payloads are rejected with **413**; callers split them.

### Ingestion queue & outages
- **D-09-09: The queue is a Postgres table.**
  - Document rows carry a status: `PENDING`, `INDEXED` or `FAILED`.
  - A worker claims rows with `FOR UPDATE SKIP LOCKED`.
  - It survives restarts and needs no broker.
- **D-09-10: Pushes are accepted while Ollama is down.**
  - A push still returns **202** and the document stays `PENDING`.
  - The worker retries with exponential backoff, capped at about 5 minutes, and resumes by itself when Ollama returns.
  - `GET /v1/status` reports Ollama health and queue depth.
- **D-09-11: Last write wins.**
  - A push replaces the stored text and content hash. The worker always embeds the current text, and an in-flight embedding of stale text is discarded.
  - Callers send no version.
  - Re-pushing unchanged content (same hash) is a no-op.
- **D-09-12: `FAILED` means bad content only.**
  - An Ollama outage never marks a document `FAILED`. Only a content error does, such as text the model rejects.
  - The reason is shown in status. A re-push or re-index retries the document.

### Deployment & database
- **D-09-13: Same Postgres container, separate database and user.**
  - A second database `ragdb`, owned by its own `rag` user, runs in the existing Postgres container (now on the pgvector image).
  - Neither user can read the other's data. `ragdb` can move to its own server later.
- **D-09-14: Database creation.**
  - On fresh volumes, an init script (`/docker-entrypoint-initdb.d`) creates `ragdb`, the `rag` user and `CREATE EXTENSION vector`.
  - Init scripts run only once, so a small documented one-off command does the same on the **existing** volume.
  - The service's own Flyway owns its schema.
- **D-09-15: `rag-service` starts by default** with `docker compose -f compose.app.yaml up`, on 8081, depending on Postgres only. With Ollama down it still starts and just queues work.
- **D-09-16: Ollama is reached through configuration.**
  - `RAG_OLLAMA_BASE_URL` defaults to `http://host.docker.internal:11434`, and `extra_hosts: host.docker.internal:host-gateway` is added for Linux hosts.
  - The service uses the host's Ollama and GPU; there is no Ollama container.
- **D-09-17 (already applied, commit 7fc980b):** Postgres is published on `127.0.0.1:5432` in `compose.app.yaml` for desktop SQL clients, loopback only.

### Claude's Discretion
- Exact table and column names, the API path and payload details beyond those fixed above, and the backoff curve.
- Chunk size and overlap within the 1,000-1,500 character range. Tune these on real text.
- Search response shape (score, passage text, document id, title, URL, metadata) and the `k` default and maximum.
- How the admin key and client keys are hashed: a standard password hash such as BCrypt/Argon2, or a SHA-256 of a high-entropy key. Prefer the simplest secure option for random keys.
- Health and readiness: Ollama health appears in `/v1/status` and Actuator health but **never gates readiness**.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone and architecture
- `.planning/PROJECT.md` — "Current Milestone: v1.1", and the Key Decisions row "Exception (2026-10-13): a standalone, domain-free RAG service" (approved stack and API shape)
- `.planning/REQUIREMENTS.md` — "Milestone v1.1 Requirements", RAG-01..05, RAG-10, plus v1.1 decisions and out-of-scope
- `.planning/ROADMAP.md` — "Phase 9: RAG Service Foundation & Index" (success criteria and research flags)

### Research (v1.1 — not the v1 files in `.planning/research/`)
- `.planning/research/v1.1/SUMMARY.md` — reconciled findings and per-phase verification items
- `.planning/research/v1.1/STACK.md` — versions, pgvector image, Ollama `/api/embed`, `nomic-embed-text` prefixes, `JdbcClient` vector access
- `.planning/research/v1.1/ARCHITECTURE.md` — chunk schema, indexer and diff design, ArchUnit guidance
- `.planning/research/v1.1/PITFALLS.md` — stale-index, model-mismatch, chunking, Docker-to-Ollama and pgvector/Flyway pitfalls

### Existing deployment
- `compose.app.yaml`, `compose.yaml`, `Dockerfile` — current stack (Postgres service, `pgdata` volume, app service)
- `docs/docker.md` — how the stack is run and rebuilt. Note: line 42 claims both stacks publish 5432, which is now true but loopback-only in `compose.app.yaml`.
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- The main app's patterns are the model to copy, not code to share. The service is a separate build with no dependency on ReleaseManagement code:
  - hexagonal packages: `domain` / `application` / `ports` / `adapters`, enforced by `ArchitectureBoundaryTest`;
  - transactions on adapter methods;
  - Flyway naming `V{n}__*.sql`;
  - ProblemDetail errors with a `reason` property via `GlobalExceptionHandler`;
  - Testcontainers ITs.
- `JiraRestClientConfig` shows the `RestClient` + JDK `HttpClient` setup with explicit timeouts.

### Established Patterns
- Environment landmines from project memory:
  - set `JAVA_HOME=$HOME/.jdks/jdk-25` for every Maven call;
  - the test `application.yml` shadows main's, so new properties go in both;
  - use `mvn clean` after public API changes;
  - Docker Desktop must be running for Testcontainers.
- Container rebuilds use `docker compose -f compose.app.yaml up --build -d <service>`.

### Integration Points
- `compose.app.yaml`:
  - the Postgres image switches to pgvector;
  - add an init script mount;
  - add the `rag-service` service, which depends on Postgres healthy and has `extra_hosts`.
- Every `*PersistenceIT` Testcontainers image in the main app switches to the pgvector image, so both builds use the same Postgres.
</code_context>

<specifics>
## Specific Ideas
- The service must be usable by apps in any language: the OpenAPI contract is a first-class deliverable, not documentation.
- A failed or slow Ollama must never make a push fail. Pushes are always accepted, and indexing catches up later.
</specifics>

<deferred>
## Deferred Ideas
- Cross-app collection sharing or read grants (D-09-03): later milestone.
- Caller-supplied pre-chunked sections (D-09-05).
- Server-side extraction of HTML, PDF or DOCX (D-09-06).
- An HNSW index: only after a measured slow query.
- Key expiry policies (D-09-04).
- Amazon Bedrock instead of Ollama: a new `EmbeddingPort` adapter, a Flyway migration off `vector(768)` (Bedrock embedding models output 256/512/1024/1536, not 768) and a full re-index. Reverses the local-only decision. The `EmbeddingPurpose` seam keeps callers unchanged.
</deferred>

---

*Phase: 09-rag-service-foundation-index*
*Context gathered: 2026-10-13*
