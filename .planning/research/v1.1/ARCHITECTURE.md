# Architecture Research: v1.1 Release Assistant

**Domain:** Read-only local-LLM assistant (RAG + tool calling) added to an existing hexagonal Spring Boot monolith
**Researched:** 2026-10-13
**Confidence:** MEDIUM-HIGH on integration with the existing code (read directly); MEDIUM on Ollama/Spring AI specifics (not re-verified against current docs here; STACK.md owns those versions)

## Findings That Shape Everything Else

1. **Per-user scoping does not exist on local data.** `ReleaseService.get`, `search`, `DashboardService`, `PromotionService.history`, `DocumentGenerationService.latest/revisions` take no user and apply no ownership filter; any authenticated user sees every release (`/api/**` is `authenticated()` in `SecurityConfig`). Per-user scoping lives only in `JiraAccessContextService.currentContext()` (the user's own Jira token), reached by `TicketSearchService` and `EnrichmentService`. The assistant never calls Jira or ADO at question time, so **"not bypassing scoping" reduces to: the assistant endpoint sits behind the same `/api/**` rule, and tools call the same application services the controllers call, never repositories or Jira/ADO ports.** The only genuinely per-user data the assistant adds is conversations (owner-scoped by accountId).
2. **Residual exposure to acknowledge, not fix:** ticket text in `release_ticket_item` was fetched under one user's Jira permissions and is already visible to every user on the release screen. Vector search exposes the same text. No new leak, but document it in the phase.
3. **`ports` and `domain` may not import Spring, Jakarta, Jackson** (`ArchitectureBoundaryTest.domainAndPortsHaveNoFrameworkImports`), so Spring AI types (`ChatModel`, `Document`, `VectorStore`, `ToolCallback`) can never appear in a port signature. Ports must be plain Java records, `String`, `float[]`, `List`.
4. **`applicationDoesNotDependOnAdapters` lists adapter subpackages explicitly** (`jira`, `persistence`, `web`, `render`). A new `adapters.llm` or `adapters.knowledge` package is **unguarded until added to that list**. First ArchUnit edit of the milestone.
5. **Application services carry no `@Transactional`; adapters do.** Slow Ollama calls must therefore never be made inside an adapter transaction. The index pipeline is shaped around this (embed outside, apply in one short adapter transaction).
6. **`postgres:18` is used in compose.yaml, compose.app.yaml and every `*PersistenceIT` Testcontainer.** The stock image has no pgvector. All of these change to `pgvector/pgvector:pg18` (MEDIUM: confirm tag exists and the existing `pgdata` volume attaches cleanly; same major version, so it should).

## Recommended Architecture

### System Overview

```
 React SPA (/assistant)  --fetch POST + ReadableStream-->  AssistantController (adapters.web)  [SSE]
                                                                  |  accountId passed explicitly
 ----------------------------------------------------------------v--------------------------------
 application.assistant                                      AssistantService  (the bounded loop)
   PromptBuilder   ToolRegistry (read-only whitelist)   CitationCollector   ConversationService
        |                    |                                   |
        |                    +--> ReleaseService, PromotionService, DashboardService,
        |                         DocumentReadinessService, DocumentGenerationService (reads only),
        |                         EnvironmentLadderService.ladder
        |                    +--> KnowledgeSearchPort   (read side of the index)
        v
   LlmPort, EmbeddingPort                      <-- ports (plain Java, no framework types)
 ----------------------------------------------------------------------------------------------
 application.knowledge                        KnowledgeIndexer   (single worker, coalescing queue)
   Chunkers (pure)   ChangeListener (@EventListener)   IndexSweep (@Scheduled + startup)
        |                    ^ ReleaseChanged(releaseId) published by ReleaseService / DocumentGenerationService
        v
   EmbeddingPort, KnowledgeIndexWriterPort, ReleaseRepository + DocumentSnapshotRepository (read source rows)
 ----------------------------------------------------------------------------------------------
 adapters.llm (Ollama, RestClient)    adapters.persistence (KnowledgeIndexAdapter, JdbcClient + pgvector)
      |                                      |
 host.docker.internal:11434            Postgres 18 + pgvector  (knowledge_chunk, knowledge_index_state,
                                                                assistant_conversation, assistant_message)
```

### Component Responsibilities

| Component | Layer | Responsibility | New / Modified |
|-----------|-------|----------------|----------------|
| `LlmPort` | ports | One chat turn: messages + tool specs in, streamed text tokens out via a sink, returns `LlmTurn(text, toolCalls)` | NEW |
| `EmbeddingPort` | ports | `List<float[]> embed(List<String>)`, `String modelId()` | NEW |
| `KnowledgeSearchPort` | ports | `List<ChunkHit> search(float[] q, SearchFilter f, int k)` (read side) | NEW |
| `KnowledgeIndexWriterPort` | ports | `existingFor(releaseId)` -> key+hash+model; `apply(releaseId, deletes, upserts)`; `state` get/put | NEW |
| `ConversationRepository` | ports | Owner-scoped create/list/load/append/delete | NEW (phase 12) |
| `OllamaLlmAdapter`, `OllamaEmbeddingAdapter` | adapters.llm | `RestClient` against Ollama `/api/chat` (NDJSON stream, tools) and `/api/embed`; timeouts, `num_ctx`, `keep_alive` | NEW |
| `KnowledgeIndexAdapter` | adapters.persistence | `JdbcClient` SQL with `pgvector` casts; `<=>` cosine search; transactional `apply` | NEW |
| Chunkers (ticket, manual item, document, code link) | application.knowledge | Source row -> list of `Chunk(sourceType, sourceRef, index, title, text)`; pure functions | NEW |
| `KnowledgeIndexer` | application.knowledge | Diff desired vs existing chunks per release, embed only new/changed, apply | NEW |
| `AssistantService` | application.assistant | Retrieve, tool loop, answer, citations; owns bounds and deadline | NEW |
| `ToolRegistry` | application.assistant | Tool name -> JSON schema + executor; each executor calls one existing service method | NEW |
| `AssistantController` | adapters.web | `POST /api/assistant/chat` (SSE), `GET /api/assistant/status`, conversation CRUD | NEW |
| `ReleaseService`, `DocumentGenerationService` | application | Publish `ReleaseChanged(releaseId)` after successful writes | MODIFIED (1 line per write path) |
| `ArchitectureBoundaryTest` | test | Add new adapter packages + assistant read-only rules | MODIFIED |
| compose.yaml / compose.app.yaml / `*PersistenceIT` | infra/test | pgvector image; `extra_hosts: host.docker.internal:host-gateway` for Linux hosts | MODIFIED |
| `V11__knowledge_index.sql`, `V12__assistant_conversation.sql` | db | Schema below | NEW |

### Recommended Project Structure

```
com.evision.releasemgmt
  ports/            LlmPort, EmbeddingPort, KnowledgeSearchPort, KnowledgeIndexWriterPort, ConversationRepository
  domain/assistant/ ChatMessage, ToolSpec, ToolCall, LlmTurn, Chunk, ChunkHit, Citation, SourceType
  application/knowledge/   KnowledgeIndexer, *Chunker, ReleaseChanged, IndexSweep
  application/assistant/   AssistantService, ToolRegistry, PromptBuilder, ConversationService
  adapters/llm/            OllamaLlmAdapter, OllamaEmbeddingAdapter, Ollama* payload records
  adapters/persistence/    KnowledgeIndexAdapter (+ conversation adapter)
  adapters/web/            AssistantController, dto/
  config/                  OllamaClientConfig (mirrors JiraRestClientConfig), AssistantProperties
```

Sub-packages under `application` keep the new feature greppable and let ArchUnit target `..application.assistant..` precisely.

## Decision: Framework vs Ports

**Recommendation: ports (LlmPort, EmbeddingPort, KnowledgeSearchPort/WriterPort) with a thin hand-written Ollama adapter; do not adopt Spring AI's `VectorStore` or its auto-run tool loop.** Spring AI 2.0 GA (2026-06-12) does target Boot 4.0/4.1, so it is viable, but:

- Its `VectorStore` owns a `vector_store` table with generic metadata. We need `release_id ... ON DELETE CASCADE`, a unique key for idempotent upsert, a content hash and an embedding-model column, all Flyway-owned. That is a bespoke table either way.
- The tool loop is where the correctness lives (iteration bound, deadline, dedupe, per-result size cap, citation capture, status events). That belongs in `application` (testable with a fake `LlmPort`), not inside a framework callback chain. Spring AI can disable internal tool execution, but then it is a transport only, which is what `RestClient` + ~150 lines already gives.
- Ports cannot carry Spring AI types anyway; every Spring AI use would be confined to an adapter and wrapped in conversion code.
- Fits "no over-engineering": two small `RestClient` adapters (same pattern as the existing Jira adapters), no new dependency tree, no starter churn (the 2.0 line moved vendor integrations into separate repos; pgvector starter needed a fix in M8).

Counter-position (acceptable, decide in STACK.md): use Spring AI's `OllamaChatModel`/`OllamaEmbeddingModel` inside `adapters.llm` only, with internal tool execution disabled. Port shape is identical, so this choice is reversible and does not affect any other component. Confidence: MEDIUM.

### Port shapes (plain Java; no framework types)

```java
public interface LlmPort {
    /** Streams assistant text to onToken as it arrives; returns the completed turn (text and/or tool calls). */
    LlmTurn chat(List<ChatMessage> messages, List<ToolSpec> tools, Consumer<String> onToken, Duration deadline);
}
public interface EmbeddingPort {
    List<float[]> embed(List<String> texts);   // batch; adapter splits to <=16 per call
    String modelId();
}
public interface KnowledgeSearchPort {
    List<ChunkHit> search(float[] query, SearchFilter filter, int limit);   // filter: releaseId?, sourceTypes?
}
```

`ToolSpec.parametersJsonSchema` is a `String` (no Jackson in ports). `ChunkHit` carries `releaseId, sourceType, sourceRef, title, text, score`.

### ArchUnit additions (ArchitectureBoundaryTest)

1. Extend `applicationDoesNotDependOnAdapters` package list with `..adapters.llm..` (and `..adapters.knowledge..` if split out).
2. **LLM client libraries only in `adapters.llm`** (same shape as `renderLibrariesLiveOnlyInTheRenderAdapter`): if Spring AI is used, forbid `org.springframework.ai..` elsewhere.
3. **Assistant is structurally read-only:** `noClasses().that().resideInAPackage("..application.assistant..")` should not `callMethodWhere` target name matches `(create|add|remove|refresh|freeze|delete|update|record|correct|generate|save|replace|move|retire|restore|reorder|rename|append).*`. Needed because `ReleaseService` and `DocumentGenerationService` mix reads and writes on one bean, unlike `DashboardReadRepository`. Add `allowEmptyShould(true)` like the existing rules, but also add one positive test that the package is non-empty so the rule cannot rot into a no-op.
4. **No question-time Jira/ADO/credentials:** `..application.assistant..` must not depend on `JiraAccessContextService`, `TicketSearchService`, `EnrichmentService`, `JiraTicketPort`, `JiraDevStatusPort`, `JiraIssueHistoryPort`, `AdoRepositoryPort`, `JiraCredentialRepository`, `AdoCredentialRepository`. This is the enforceable form of "no Jira/ADO call at question time".
5. **Assistant cannot reach the index writer:** `..application.assistant..` must not depend on `KnowledgeIndexWriterPort` (ISP split; the model path only gets the search side).
6. Mirror `dashboardReadRepositoryExposesNoMutation` for `KnowledgeSearchPort` (no `save|delete|update|remove` methods).

## Tool Exposure (existing services -> tools)

Tools are in-process calls to **existing application services**, executed on the request's own thread with explicit parameters. They need no SecurityContext, because none of the called services read the current user (verified: only `JiraAccessContextService` does, and it is excluded by rule 4). This also means SSE work on a separate/virtual thread loses nothing.

| Tool | Backing call | Returns (compact DTO, never the raw domain object) | Citation produced |
|------|--------------|-----------------------------------------------------|-------------------|
| `list_releases(query?)` | `ReleaseService.search` | id, name, version, status, created/frozen dates, ticket count | release |
| `get_release(releaseId)` | `ReleaseService.get` | header, ticket keys + summary + status + type, manual item titles. **No descriptions/comments** (token cost + injection surface; use `get_ticket`) | release |
| `get_ticket(releaseId, key)` | `ReleaseService.get` -> item incl. `CodeLinks` | fields, description (truncated), PRs, commits (capped), enrichment status | ticket (+ commit) |
| `promotion_history(releaseId)` | `PromotionService.history` | stages, effective entries, current stage, voided/corrected flags | release |
| `environment_ladder()` | `EnvironmentLadderService.ladder` | ordered stages, production flag, retired | none |
| `dashboard_summary(all?)` | `DashboardService.releases` | summary + per-release metrics, stage names resolved | release(s) |
| `recent_deployments(stage?, from?, to?, limit<=50)` | `DashboardService.deployments` | rows | release |
| `ticket_churn(releaseId)` | `DashboardService.ticketChurn` | per-ticket commit/reopen figures | release |
| `document_readiness(releaseId?)` | `DocumentReadinessService.all()` filtered | state per (release, type) | release |
| `list_document_revisions(releaseId, type)` | `DocumentGenerationService.revisions` | revision, generatedAt/By, template name only | document revision |
| `get_document(releaseId, type, revision?)` | `latest` / `revision` | markdown, truncated to a cap with a "truncated" flag | document revision |
| `search_knowledge(query, releaseId?, sourceTypes?)` | embed + `KnowledgeSearchPort.search` | top hits with ids | per hit |

Rules for every tool:
- Arguments validated against the schema in `ToolRegistry`; UUIDs/keys parsed strictly (reuse the ticket-key pattern from `ReleaseService`); `limit` clamped.
- A not-found (`ReleaseNotFoundException`, `DocumentNotFoundException`) becomes a tool result `{"error":"not found"}`, never an exception that kills the stream.
- Result text capped (about 6k chars) with an explicit `truncated:true`.
- Wrapped in a delimiter the system prompt declares as untrusted data (`<data>...</data>`); tool and chunk text is never placed in the system role.
- `DashboardService.releases` with `all=true` loads the whole ledger; acceptable at this scale, cap rows returned to the model.
- Not exposed: anything on `TemplateService` (template bodies are user-authored and irrelevant to questions), credential services, `AuthenticationService`, `LogoValidator`, export services.

## Ingestion Pipeline

### What is indexed

| Source type | `source_ref` | Chunking | Notes |
|-------------|--------------|----------|-------|
| `TICKET` | ticket key | one header chunk (key, summary, type, status, component, fix versions, assignee) + description split on paragraph boundaries to about 800-1000 chars with small overlap; each comment its own chunk (cap count) | Every chunk text is prefixed with `Release <name> <version> / <KEY> <summary>` so a bare fragment still retrieves. Stored `description`/`comments` come from `release_ticket_item`; no Jira call. |
| `MANUAL_ITEM` | manual item UUID | one chunk (title, category, description) | |
| `DOCUMENT` | `<TYPE>:<revision>` | split `markdown` on headings, then size cap; prefix with doc type + release | **Latest revision per (release, type) only.** Older revisions are append-only audit data reachable through `get_document`; indexing them would surface superseded text in answers. Superseded chunks are removed by the same diff. |
| `CODE_LINK` | ticket key | per ticket: PR titles/status/branches + commit messages, grouped about 10 commits per chunk, only when `CodeLinks.status == LINKED` | Never embed SHAs or URLs as semantic content; keep them in the citation metadata. |

Not indexed: promotions, dashboard metrics, ladder, readiness. They are structured and exact; embedding them only invites wrong answers. Chat history is never indexed.

### Trigger: event + reconcile, not per-mutation embedding

Do **not** embed inside `ReleaseService.refresh` or `DocumentGenerationService.generate`. A refresh over hundreds of tickets with CPU Ollama would add minutes, and an Ollama outage must not fail a release operation (same stance as `enrichQuietly`).

1. `ReleaseService` (`createWithTickets`, `addTickets`, `removeTicket`, `refresh`, `refreshCodeLinks`, manual-item mutators) and `DocumentGenerationService.generate` call `publisher.publishEvent(new ReleaseChanged(releaseId))` **after** the repository write returned (the adapter transaction has committed by then, since transactions live on adapter methods). `ApplicationEventPublisher` is the only new dependency on the v1 services; with the assistant disabled no listener exists and nothing changes. Modified v1 constructors need their unit tests updated.
2. `KnowledgeIndexer` listens (`@EventListener`, not `@TransactionalEventListener`, as no transaction is active), adds the release id to a de-duplicating `LinkedHashSet`, and a **single** worker thread drains it. Single worker: Ollama serialises per model anyway, and it bounds embedding load against concurrent chat.
3. `IndexSweep` (startup + `@Scheduled` every few minutes) catches everything events miss (restart mid-index, Ollama down, first deployment of v1.1 with existing releases, embedding model changed): enqueue any release with no `knowledge_index_state` row, `state.embedding_model != current`, `state.status = FAILED`, or `greatest(last_refreshed_at, items_changed_at, latest document generated_at) > state.indexed_at`. Frozen releases are effectively one-time (immutable ticket set; documents append-only).

### Idempotency and deletion: desired-set diff per release

```
reindex(releaseId):
  desired  = chunkers(release row + items + manual items + latest doc per type)   // pure, no I/O to Ollama
  existing = writer.existingFor(releaseId)        // (source_type, source_ref, chunk_index) -> content_hash, embedding_model
  toEmbed  = desired where key absent OR hash differs OR model != embedding.modelId()
  toDelete = existing keys not in desired         // removed ticket, superseded revision, shrunk description
  vectors  = embedding.embed(toEmbed texts)       // OUTSIDE any DB transaction, batches of <=16
  writer.apply(releaseId, toDelete, toEmbed+vectors)   // one short adapter transaction, upsert on the unique key
  state := (indexed_at = start-of-run, model, OK)       // use start time: changes during the run re-trigger via sweep
```

Converges from any starting state, so re-running is always safe, a lost event is healed by the sweep, and ticket removal needs no delete-hook. If embedding fails midway nothing is applied and state is `FAILED` with `last_error` (retry by sweep with backoff). Release deletion (`ReleaseService.delete`, OPEN only) cascades via `knowledge_chunk.release_id ... ON DELETE CASCADE`; no code needed.

Re-indexing for a new embedding model: the table is derived data. Changing model with the same dimension = sweep re-embeds via the model-mismatch rule. A different dimension needs a migration (column type is fixed); acceptable, document it as "truncate and rebuild".

## Schema

```sql
-- V11__knowledge_index.sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_chunk (
    id               UUID PRIMARY KEY,
    release_id       UUID         NOT NULL REFERENCES release (id) ON DELETE CASCADE,
    source_type      VARCHAR(16)  NOT NULL CHECK (source_type IN ('TICKET','MANUAL_ITEM','DOCUMENT','CODE_LINK')),
    source_ref       VARCHAR(200) NOT NULL,
    chunk_index      INTEGER      NOT NULL,
    title            VARCHAR(400) NOT NULL,          -- display label for citations
    content          TEXT         NOT NULL,
    content_hash     CHAR(64)     NOT NULL,          -- sha-256 of content (incl. context prefix)
    embedding        vector(768)  NOT NULL,          -- dimension fixed by chosen model (STACK.md)
    embedding_model  VARCHAR(100) NOT NULL,
    indexed_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT knowledge_chunk_key UNIQUE (release_id, source_type, source_ref, chunk_index)
);

CREATE TABLE knowledge_index_state (
    release_id       UUID PRIMARY KEY REFERENCES release (id) ON DELETE CASCADE,
    embedding_model  VARCHAR(100) NOT NULL,
    indexed_at       TIMESTAMPTZ  NOT NULL,
    status           VARCHAR(16)  NOT NULL CHECK (status IN ('OK','FAILED')),
    last_error       TEXT
);
```

- **No ANN index in V11.** At the scale of one team's releases (low tens of thousands of chunks at most) an exact `ORDER BY embedding <=> $1 LIMIT k` is milliseconds, and HNSW + a `release_id` filter brings filtered-recall problems. Add HNSW only after a measured slow query (matches the "measure first" rule).
- Use `vector` (float32). Keep the Flyway SQL pgvector-only; confirm the DB role may `CREATE EXTENSION` (`vector` is created by the compose superuser; a locked-down production Postgres may need DBA action first; flag for the phase).
- `JdbcClient` (already in `spring-boot-starter-jdbc` via JPA starter transitively; verify) in the persistence adapter, with the `pgvector` Java library or a string cast `?::vector` to avoid a dependency. Hibernate cannot map `vector` cleanly; keep it out of JPA entities.
- No separate lexical (tsvector) index in the first cut. Exact identifiers (`ABC-123`, a SHA) are served by tools (`get_ticket`), and the ticket-key prefix in each chunk helps the embedding. If retrieval evaluation shows identifier misses, add a `tsvector` generated column and merge by reciprocal-rank fusion (a contained later change).

```sql
-- V12__assistant_conversation.sql (phase 12)
CREATE TABLE assistant_conversation (
    id UUID PRIMARY KEY, account_id VARCHAR(255) NOT NULL, title VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX assistant_conversation_owner_idx ON assistant_conversation (account_id, updated_at DESC);
CREATE TABLE assistant_message (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES assistant_conversation (id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL CHECK (role IN ('USER','ASSISTANT')),
    content TEXT NOT NULL,
    citations TEXT NOT NULL DEFAULT '[]',     -- JSON, same JsonStringListConverter style as v1
    tool_trace TEXT NOT NULL DEFAULT '[]',    -- tool names + args only, never results (debug/audit)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now());
```

## Request Flow

```
POST /api/assistant/chat {conversationId?, message}
  controller: validate (length <= 2000), auth principal -> accountId, one in-flight request per user
              (a second returns 429/409 ProblemDetail; one local GPU/CPU), return SseEmitter, run on a virtual thread
  AssistantService.ask(accountId, history, message, sink, deadline = now + 120s):
    1. embed(message)                      -> KnowledgeSearchPort.search(k=6, score floor)   ["S1".."S6"]
    2. messages = system prompt (rules, untrusted-data contract, "cite as [S#]/[T#]", "say you don't know")
                  + last <=6 turns + user message + retrieved context (fenced, labelled)
    3. loop i in 1..MAX_ROUNDS (=4):
         if deadline passed or client gone -> stop with error event
         turn = llm.chat(messages, i < MAX_ROUNDS ? tools : none, tokenSink, remaining)
         if turn has no toolCalls -> break
         for each call (cap 4 per round, 8 total): skip exact duplicate (name+args) already run;
             execute via ToolRegistry; append tool result message (fenced, capped);
             emit status event; register citations
    4. citations = parse [S#]/[T#] from the answer, keep only ids actually produced this request
    5. persist (phase 12), emit citations + done
```

Design points:
- **Pre-retrieval is always on, tools are additive.** Small local models are unreliable at deciding when to search; one embedding call plus one SQL query is cheap. This also lets phase 10 ship Q&A with no tools.
- **Last round offers no tools** so the model must answer; if the model still returns only tool calls, emit a controlled "could not complete" error event rather than looping.
- **Server-owned citations.** The model is told to reference opaque ids ([S1] retrieved chunk, [T2] tool result). Citations the UI shows are built from the server-side registry (release/ticket/document revision/commit with deep-link target), never from model-written URLs. Ids not in the registry are dropped. An answer with zero valid citations is flagged to the UI.
- **Ollama specifics that must be explicit in the adapter:** set `options.num_ctx` (Ollama's small default silently truncates the prompt, dropping the system prompt and tool list), set `keep_alive`, `temperature` low for tool turns, and distinguish connect timeout, first-token timeout (cold model load can take tens of seconds) and inter-token stall. Budget prompt size against `num_ctx` in `PromptBuilder`, dropping oldest history and lowest-scoring chunks first.
- **Deadline and cancellation:** a single `Deadline` object is passed down; `SseEmitter.onCompletion/onTimeout/onError` flips a cancelled flag; the adapter closes the HTTP body stream so Ollama stops generating.
- **Prompt injection posture (tools are read-only, so blast radius is a wrong or misleading answer and data egress via rendered content):** system rules in system role only; all retrieved/tool text fenced as data and described as untrusted; frontend renders model output as sanitised Markdown with **no raw HTML, no images, no auto-linking of model-written URLs**; links come only from server citations.

### Streaming endpoint shape

`POST /api/assistant/chat` -> `text/event-stream` via Spring MVC `SseEmitter` (no WebFlux). Events (JSON `data:`):

| event | payload |
|-------|---------|
| `meta` | `{conversationId}` |
| `status` | `{label}` e.g. "Looking up promotion history" (also serves as keep-alive; additionally send a comment heartbeat every ~15 s during cold model load) |
| `token` | `{text}` |
| `citations` | `[{id, type, releaseId, ticketKey?, documentType?, revision?, sha?, label}]` |
| `done` | `{}` |
| `error` | `{code, message}` (mid-stream failures; pre-stream failures such as 401/400/429 remain normal ProblemDetail like every other controller) |

Browser `EventSource` cannot POST, so the frontend uses `fetch` with a `ReadableStream` parser and an `AbortController` (abort -> server cancels). It does not fit a TanStack Query mutation neatly; use a small hook that accumulates state, and TanStack Query for conversation list/load. Send `Cache-Control: no-cache` and `X-Accel-Buffering: no`; check the Vite dev proxy does not buffer.

`GET /api/assistant/status` -> `{enabled, ollamaReachable, chatModel, embeddingModel, indexed: n, total: n, failed: n}` for the UI's degraded state ("assistant offline"). Ollama health must **not** join the readiness/liveness group (compose restarts the app on failure; an absent Ollama must not take the release tool down). Everything v1 keeps working with Ollama off (consistent with the existing graceful-degradation constraint); `rm.assistant.enabled=false` disables listeners, sweep, controller.

### Conversation storage

Persist server-side (V12, phase 12), owner-scoped on `account_id` in every query (this is the one place per-user scoping is real; return 404, not 403, for other users' ids). Reasons against stateless client-held history: client could forge assistant/system turns into the prompt, and history is lost on reload. Store only USER and ASSISTANT final text; tool results are not replayed into later turns (re-run tools instead; data may have changed). Delete conversation endpoint; no retention job until asked for. Phases 10 and 11 take `history` as a parameter and run stateless, so persistence lands without reworking the service.

## Anti-Patterns

### Embedding inside the refresh/generate request
**Wrong because:** adds minutes to user actions and couples release correctness to Ollama uptime. **Do instead:** event + single-worker reconcile with sweep.

### Letting the model write its own citations/links
**Wrong because:** local models fabricate source ids and URLs, and a URL is an exfiltration channel under injection. **Do instead:** server-issued ids, server-built citation objects, sanitised Markdown without images.

### Tools that call controllers or repositories
**Wrong because:** controllers carry HTTP/DTO concerns; repositories skip service rules (e.g. frozen/visibility logic, `DashboardService`'s stage rules that "exist once"). **Do instead:** application services only, guarded by the ArchUnit method-name rule.

### Passing whole domain objects as tool output
**Wrong because:** `Release` with full descriptions and comments blows `num_ctx` and widens the injection surface. **Do instead:** purpose-built compact DTOs plus a cap; deep detail via `get_ticket`.

### HNSW index and hybrid search up front
**Wrong because:** unmeasured complexity for a corpus that fits an exact scan. **Do instead:** exact scan, measure, then add.

### Adopting a vector-store abstraction that owns the table
**Wrong because:** loses FK cascade, idempotent keys, model column, Flyway ownership. **Do instead:** own `knowledge_chunk`.

## Scaling Considerations

| Scale | Adjustment |
|-------|------------|
| This product (one team, tens to hundreds of releases) | Everything above; single index worker; exact vector scan |
| Slow exact scan measured (> ~100k chunks) | HNSW with `hnsw.iterative_scan`, or partition by release |
| Concurrent chat users | Per-user single in-flight already; add a global semaphore sized to Ollama's parallelism; this is Ollama-bound, not app-bound |

First bottleneck is Ollama (cold load, tokens/sec, embedding throughput during first full index), not Postgres or the JVM. The first full index of existing releases is the longest embedding job the system will ever run; report progress through `/status`.

## Integration Points

### External Services

| Service | Pattern | Notes |
|---------|---------|-------|
| Ollama (host) | `RestClient`, base URL `rm.assistant.ollama.base-url` default `http://host.docker.internal:11434` | Windows/Mac Docker Desktop resolves the name; Linux needs `extra_hosts: host.docker.internal:host-gateway`. Ollama must listen on an address reachable from the container (host binding to 127.0.0.1 only is unreachable; set `OLLAMA_HOST`). Verify in phase 9. |
| Postgres + pgvector | image swap, `CREATE EXTENSION vector` in V11 | Existing `pgdata` volume reused; rebuild Testcontainers with `asCompatibleSubstituteFor("postgres")`. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `ReleaseService`/`DocumentGenerationService` -> indexer | Spring application event `ReleaseChanged` | Only change to v1 code paths; fire-and-forget |
| `AssistantService` -> v1 services | Direct calls from `ToolRegistry` only | ArchUnit-enforced read-only and no Jira/ADO |
| `AssistantService` -> index | `KnowledgeSearchPort` | Writer port unreachable from this package |
| `AssistantController` -> service | accountId passed explicitly | No SecurityContext propagation to worker threads required |

## Suggested Phase Build Order (continues at 9)

Dependency chain: index needs embeddings and the pgvector image; retrieval Q&A needs the index and an LLM adapter; tools need the loop; the UI needs a stable stream contract; conversations need the UI.

| Phase | Name | Delivers | Exit gate |
|-------|------|----------|-----------|
| 9 | Embeddings and knowledge index | pgvector image swap (compose x2, ITs), V11, `EmbeddingPort` + Ollama adapter, `KnowledgeIndexWriter/SearchPort` + JDBC adapter, four chunkers, `KnowledgeIndexer` (diff/idempotent), `ReleaseChanged` events, `IndexSweep`, `/status`, ArchUnit rules 1, 2, 5, 6. Model bake-off for embedding model (dimension, quality on real ticket text, speed on the real host). | Re-running index is a no-op; removing a ticket and superseding a document revision remove chunks; Ollama stopped = v1 flows unaffected and sweep heals; container reaches host Ollama. |
| 10 | Retrieval Q&A (no tools) | `LlmPort` + Ollama chat adapter (`num_ctx`, timeouts, streaming), `AssistantService` with pre-retrieval, `PromptBuilder` with fencing and budget, server-owned citations, `POST /api/assistant/chat` SSE, one-in-flight limit. A small golden-question set to judge retrieval and refusal ("I don't know"). Verified with curl. | Free-text questions return cited answers; bounded deadline; client abort cancels generation. |
| 11 | Tool calling | `ToolRegistry` over existing services, bounded loop (rounds, calls, dedupe, caps, last-round-no-tools), `status` events, ArchUnit rules 3 and 4, model bake-off for chat model tool-call reliability. | Structured questions ("what is in prod?", "which docs are stale?") answered via tools; mutation/Jira/ADO calls are compile-time/ArchUnit impossible. |
| 12 | Chat UI and conversations | `/assistant` route, fetch-stream hook, sanitised Markdown, citation chips deep-linking to release/ticket/document revision, offline state from `/status`, V12 conversations + owner-scoped CRUD. | Usable end to end; reload keeps history; other users cannot read it. |

**Ordering rationale:** the user's proposed order holds. Risk to manage: SSE-through-proxy and cancel behaviour are only exercised by a real browser in phase 12; mitigate by smoke-testing the stream with a throwaway page or curl in phase 10 rather than waiting.

**Research flags:**
- Phase 9: needs deeper research (embedding model choice and dimension; pgvector image tag and extension privileges; `host.docker.internal` reachability; Testcontainers substitution).
- Phase 10: needs deeper research (Ollama `/api/chat` streaming + `num_ctx`/`keep_alive` behaviour, `RestClient`/JDK `HttpClient` timeout semantics on a streamed body, chunk-size and score-threshold tuning).
- Phase 11: needs deeper research (tool-calling reliability by model; whether Ollama streams tool calls and text in the same turn).
- Phase 12: standard patterns for the route and Query usage; the fetch-stream hook is small but new to this codebase.

## Open Questions

- Whether chat and embedding models can both stay resident on the target host (memory), which changes whether the index worker can overlap with chat.
- Whether any user must be prevented from seeing a release's ticket text that another user's Jira permissions allowed (currently not enforced anywhere in v1; confirm that is acceptable for assistant answers).
- Whether `items_changed_at` is stamped on manual-item mutations (V5 comment says every item mutation; `saveEnrichment` stamps only on document-visible change). The sweep relies on it only as a backstop; events are the primary trigger.
- Whether the production Postgres allows `CREATE EXTENSION vector` for the app role.

## Sources

- Direct reads (HIGH): `ArchitectureBoundaryTest`, `SecurityConfig`, `ReleaseService`, `DashboardService`, `DocumentReadinessService`, `DocumentGenerationService`, `PromotionService`, `JiraAccessContextService`, ports `ReleaseRepository`, `DocumentSnapshotRepository`, `PromotionRepository`, `DashboardReadRepository`, migrations V1..V10, compose files, `PROJECT.md`.
- https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/ (Spring AI 2.0 GA, Boot 4.0/4.1 support) (MEDIUM; search summary, page not fetched)
- https://newreleases.io/project/github/spring-projects/spring-ai/release/v2.0.1 (2.0.x release line) (LOW-MEDIUM)
- Ollama `num_ctx`/`keep_alive`/tool-calling behaviour and pgvector filtered-HNSW behaviour: from training knowledge, not re-verified here (LOW); phase 9/10/11 research must confirm against current Ollama and pgvector docs.
