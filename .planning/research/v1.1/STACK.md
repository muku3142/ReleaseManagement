# Technology Stack: v1.1 Release Assistant (additions only)

**Project:** ReleaseManagement
**Researched:** 2026-10-13
**Scope:** Only what the read-only, Ollama-backed chat assistant adds to the v1 stack (Java 25, Boot 4.1.0, Spring MVC, JPA/Hibernate 7.x, Flyway, PostgreSQL 18, React/Vite/TS/TanStack Query). The v1 stack is in `.planning/research/STACK.md` and is untouched.

## Recommendation in One Paragraph

Add **no AI framework**. Write two thin outbound adapters (`OllamaChatAdapter`, `OllamaEmbeddingAdapter`) on the Spring `RestClient` already used for Jira/ADO, behind two new ports. Use **pgvector 0.8.x** in the existing Postgres through plain native SQL (no ORM vector mapping, no new Java dependency). Chat model: **`qwen3:4b`** on the 4 GB dev GPU, **`qwen3:8b`** (or `granite4.1:8b`) on a production host with >= 12 GB VRAM. Embeddings: keep **`nomic-embed-text`** (768 dims). Stream to the SPA with **`SseEmitter` over POST, consumed with `fetch`**. **No frontend chat or markdown library.**

## Recommended Stack

### New backend capabilities

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Ollama HTTP API (`/api/chat`, `/api/embed`) via existing `RestClient` | n/a (Boot-managed client) | Chat with tool calling, streaming, embeddings | Same pattern as `AdoRestAdapter` / Jira adapters; zero new dependencies; see comparison below |
| pgvector (Postgres extension) | 0.8.7 | Vector similarity over free text | Runs in the existing Postgres; image tag `pgvector/pgvector:0.8.7-pg18-trixie` verified on Docker Hub 2026-10-13 |
| Flyway migration (`V__pgvector_index.sql`) | Boot-managed | `CREATE EXTENSION IF NOT EXISTS vector;` + chunk table + HNSW index | Same mechanism as every other schema change |
| `jdbc` native SQL (`JdbcClient` / `JdbcTemplate`, already on classpath via JPA starter) | Boot-managed | `ORDER BY embedding <=> CAST(:q AS vector) LIMIT :k` | Pass the vector as the text literal `'[0.1,0.2,...]'` and cast. Avoids `pgvector-java` (0.1.6) and `hibernate-vector` entirely |

No new Maven dependency is required for the backend. Test scope only: swap the Testcontainers Postgres image to `pgvector/pgvector:0.8.7-pg18-trixie` via `DockerImageName.parse(...).asCompatibleSubstituteFor("postgres")`.

### Ollama models

| Role | Model | Download | Context | Why |
|------|-------|----------|---------|-----|
| Chat, dev (4 GB VRAM) | `qwen3:4b` | 2.5 GB | 256K (page) | Tools + thinking capability tags on ollama.com/library/qwen3; fits fully in 4 GB VRAM with a modest `num_ctx`, so no CPU offload |
| Chat, production | `qwen3:8b` (5.2 GB) or `granite4.1:8b` (5.3 GB) | 5.2-5.3 GB | 40K / 128K | 8B-class is the practical floor for reliable multi-tool selection; needs about 8-12 GB VRAM incl. KV cache. Granite 4.1 is Apache-2.0, explicitly lists RAG, function calling and JSON output, and was updated about 5 months ago |
| Chat, production with a big GPU | `qwen3.5:9b` (6.6-7.6 GB) or `qwen3:30b` (19 GB, 24 GB card) | | 256K | Newer; Qwen3.5 page is tagged tools/thinking and updated 1 week ago, but I could not verify tool-call reliability on it. Evaluate, do not assume |
| Embeddings | `nomic-embed-text` (already installed) | 274 MB | 2K | 768 dims. Keep it |

**Do not use `qwen2.5-coder:7b` as the assistant model.** It is code-tuned (ollama.com tags it `tools`, so it works mechanically) but the answers here are release/ticket prose, not code. At 4.7 GB it also does not fully fit a 4 GB card, so it is the slow choice and not the best one. Keep it installed if you like, but make the model a config property (`rm.assistant.chat-model`).

### Frontend

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Native `fetch` + `ReadableStream` + `TextDecoder` | browser | Consume the SSE stream from a POST | `EventSource` cannot POST a body; `fetch` sends the existing auth cookie. About 30 lines of parser |
| React state + existing TanStack Query | existing | Conversation list, mutation for non-streaming parts | No chat UI library needed |
| `react-markdown` | 10.1.0 (npm, 2026-10-13) | OPTIONAL: render model Markdown | Escapes raw HTML by default. Only add if plain-text answers prove too ugly. Never add `rehype-raw`; never `dangerouslySetInnerHTML` (model output is attacker-influenced via ticket text) |

## Q1. Integration choice: hand-rolled vs Spring AI vs LangChain4j

Verified versions (Maven Central metadata, fetched 2026-10-13):

| | Spring AI | LangChain4j | Hand-rolled |
|---|---|---|---|
| Current GA | 2.0.1 (2.0.0 GA 2026-06-12, designed for Boot 4.0/4.1 and Framework 7); 2.1.0-M1 is a milestone (2026-09-24) | 1.22.0 core BOM (2026-10-07); community BOM still `1.22.0-beta32` | n/a |
| Ollama chat | In core (`spring-ai-starter-model-ollama`) | `langchain4j-ollama` 1.22.0 | `RestClient` |
| Tool calling | `ChatClient` + `@Tool`; GA 2.0 moved the tool loop into an auto-registered `ToolCallingAdvisor` | `AiServices` + `@Tool` | You write the loop (about 60 lines) |
| pgvector | `spring-ai-starter-vector-store-pgvector` (owns its table schema) | `langchain4j-pgvector` (owns its table schema) | SQL |
| Spring Boot 4 fit | Official, but 2.0 is a major break from 1.x (Jackson 3, builder options) and the line is moving (2.1 milestones already) | Boot starter lives in beta community modules | Native |

**Verdict: hand-rolled, behind ports.** Reasons, in order of weight:

1. **The hard parts are ours, not the framework's.** Per-request tool allowlist limited to read-only services, a hard cap on tool-loop iterations, capturing which tool results and chunks fed the answer (for citations), scoping every tool call and every vector search to what the signed-in user's Jira credentials may read, and treating tool results as untrusted data. Both frameworks give you a loop but you still write all of this, and with their loop you have to work around it instead of owning it.
2. **Frameworks own the vector table.** Spring AI/LangChain4j `PgVectorStore` create their own `vector_store` shape (id, content, jsonb metadata). We need our own columns: `source_type`, `source_id`, `release_id`/`project`, `revision`, `embedding_model`, content hash for idempotent re-indexing, and delete-by-source on ticket refresh. That is a Flyway-owned table with a typed index regardless; the framework store adds nothing.
3. **Fit with the project rules and hexagon.** The code base already hand-rolls Jira and ADO with `RestClient` and explicitly rejected SDKs for narrow surfaces. Ollama's surface here is two endpoints. "No over-abstraction / minimal code" favours it. A framework `ChatClient` would also tempt `@Tool` annotations onto application services, dragging a framework import into `application`/`domain` that `ArchitectureBoundaryTest` is there to prevent.
4. **Churn risk.** Spring AI 2.0 just broke 1.x and 2.1 is already in milestones; LangChain4j's Spring/community modules are `beta`. A 300-line adapter has no upgrade path to maintain.

Cost accepted: you maintain the Ollama JSON shapes (small, documented, stable) and NDJSON stream parsing. If a later milestone needs multiple providers or MCP, revisit Spring AI 2.x then; the port keeps that a one-adapter swap.

**Port and ArchUnit layout (fits the existing hexagon):**
- `ports`: `ChatModelPort` (messages + tool specs in, streamed events out), `EmbeddingPort` (`List<float[]> embed(List<String>)`), `ChunkIndexPort` (upsert/delete-by-source/search).
- `adapters/ollama` (new package): `OllamaChatAdapter`, `OllamaEmbeddingAdapter`. Same dependency rules as `adapters/jira`: may depend on `ports`/`domain`, nothing may depend on it.
- `adapters/persistence`: the pgvector `ChunkIndexPort` implementation (JdbcClient + native SQL).
- `application`: `AssistantService` (tool loop, prompt assembly, citations) and the read-only tool registry. Tools are plain methods/lambdas over existing application services, described by JSON schema built by hand. Extend `ArchitectureBoundaryTest`: no class in `domain`/`application` imports `adapters.ollama`; assistant code may call only query-side services (enforce with a naming/interface marker so write services cannot be registered as tools).
- Indexing hooks into the existing ticket-refresh and document-generation flows via application events (`@TransactionalEventListener(AFTER_COMMIT)`) plus an async executor, so a slow/absent Ollama never fails a refresh or generation (same degrade-gracefully principle as ADO).

**Ollama API specifics to rely on** (training knowledge, MEDIUM; verify during the phase against docs.ollama.com/api):
- `POST /api/chat` with `tools: [{type:"function", function:{name, description, parameters}}]`; the response `message.tool_calls[].function.{name, arguments}` has `arguments` as a JSON object (not a string). Reply with `role:"tool"` messages carrying `tool_name`.
- `stream:true` returns NDJSON (one JSON object per line), not SSE.
- `think:false` switches off Qwen3 reasoning for latency; otherwise `<think>` content arrives in `message.thinking` and must not be shown or stored.
- `POST /api/embed` takes `input` as string or array and returns `embeddings: [[...]]`. Use this, not the older `/api/embeddings`.
- `options.num_ctx` must be set explicitly per request. Ollama's default context is small and prompts that exceed it are truncated silently, which looks like the model "ignoring" retrieved context. Use 8192 on the dev GPU.
- `keep_alive` (e.g. `30m`) avoids a cold model load on every first question.

## Q2. Chat model reliability and latency on 4 GB VRAM

- **What fits:** `qwen3:4b` is 2.5 GB, so weights plus an 8K-token KV cache fit in 4 GB. Windows and the display also consume VRAM on a laptop GPU, so budget about 3.3 GB usable. `qwen3:8b` (5.2 GB) and `qwen2.5-coder:7b` (4.7 GB) do not fit and Ollama will split layers between GPU and CPU.
- **Expected latency (LOW confidence: estimates from general experience, not measured on this machine; measure in phase 1 of the milestone):**
  - Fully on GPU (4B): roughly 20-40 tokens/s generation, a few seconds to first token for a 2-4K token prompt.
  - 7-8B with partial offload on a laptop CPU: roughly 3-8 tokens/s, and prompt processing of a 4K-token RAG prompt can take 10-30 s before the first token. Usable for a demo, painful as a daily tool.
  - Thinking mode multiplies time-to-answer several-fold. Disable it by default (`think:false`).
- **Tool-calling reliability:** 4B models call a single obvious tool well but degrade on choosing among many tools or chaining two. Mitigations that belong in the design: keep the tool set small (about 8-10 coarse, well-described tools, not one per endpoint); `required`-style flat parameter schemas; validate arguments server-side and return a readable error to the model on bad calls; cap the loop at about 4 iterations; route question types cheaply (structured vs free text) in the system prompt rather than relying on the model to infer it.
- **Production host recommendation:** a machine with a 12-16 GB GPU running `qwen3:8b` or `granite4.1:8b` (Q4, about 5-6 GB weights, leaves room for 16K context and concurrent requests), or 24 GB for `qwen3:30b`. A CPU-only server is not recommended for interactive use. Set `OLLAMA_NUM_PARALLEL` low (1-2) and `OLLAMA_MAX_LOADED_MODELS=2` so chat and embedding models stay resident together. Bind Ollama to localhost or a private interface only: it has no authentication.
- **Docker networking:** the app container reaches host Ollama at `http://host.docker.internal:11434` (works on Docker Desktop for Windows). On a Linux host add `extra_hosts: ["host.docker.internal:host-gateway"]` to `app` in `compose.app.yaml`, and set `OLLAMA_HOST=0.0.0.0` (or the docker bridge IP) on the host, since Ollama listens on 127.0.0.1 by default and containers cannot reach that. Expose as `RM_OLLAMA_BASE_URL`.
- Model quality figures for 2026 models (qwen3.5, qwen3.8, ornith, etc.) were not benchmarked here; the ollama.com listing only confirms capability tags and sizes. Run a 15-20 question golden set against the candidate model before committing to it.

## Q3. Embeddings and pgvector

**Embedding model.** Keep `nomic-embed-text`: already installed, 274 MB (runs on CPU or GPU without crowding the chat model), 768 dimensions (model card; the Ollama page does not state it, MEDIUM). Context is 2K tokens per the Ollama page, so chunk at about 1,000-1,500 characters with small overlap. The model was trained with task prefixes: prepend `search_document: ` when indexing and `search_query: ` when querying (from the Nomic model card, MEDIUM; the Ollama page does not mention it). Skipping prefixes works but measurably lowers retrieval quality.

Alternatives, only if retrieval quality tests fail: `qwen3-embedding:0.6b` (639 MB, 32K context, flexible 32-4096 dims is documented for the 8B; 0.6b dimension not confirmed) or `embeddinggemma` (622 MB, 2K). Neither is needed now. Whatever is chosen, store `embedding_model` on each row and treat a model change as a full re-index (vectors from different models are not comparable).

**Dimension.** `vector(768)`. Column dimension is fixed at the DDL level, so a model with different dimensions needs a new migration and re-index.

**Postgres image.** `postgres:18` does not ship pgvector. Change both `compose.yaml` and `compose.app.yaml` to `pgvector/pgvector:0.8.7-pg18-trixie` (tag verified on Docker Hub 2026-10-13; it is the official Postgres 18 image plus the extension, built on Debian trixie like `postgres:18`). The existing volume mount at `/var/lib/postgresql` (PG 18 layout) is unchanged and the `pgdata` volume stays valid because the major version is the same. Production managed Postgres must offer the `vector` extension (RDS, Azure Flexible Server and Cloud SQL all do) - check before choosing the production database.

**Flyway.** `CREATE EXTENSION IF NOT EXISTS vector;` in the first migration. The docker `POSTGRES_USER` is a superuser so this succeeds locally; in production confirm the migration role may create the extension (pgvector is installable by non-superusers on most managed services after allow-listing, otherwise a DBA runs it once). Verify on first migration run.

**Index.** Use **HNSW**, `USING hnsw (embedding vector_cosine_ops)`, not IVFFlat. IVFFlat needs data present to build good centroids and periodic re-tuning; HNSW works on an empty table that is populated incrementally, which matches "index kept current as tickets refresh". Defaults (`m=16`, `ef_construction=64`) are fine. Honest scale note: with a few thousand to tens of thousands of chunks an exact sequential scan is already fast, so the index is cheap insurance, not a requirement. Use cosine distance (`<=>`); nomic vectors are not guaranteed normalised.

**Filtered search gotcha (pgvector 0.8):** a `WHERE` (release, project, source type, permission scope) combined with an HNSW index can return fewer than `k` rows because filtering happens after the index scan. Set `SET LOCAL hnsw.iterative_scan = relaxed_order` (new in 0.8.0) for the query transaction, or keep filters selective enough to prefer a sequential scan.

**Suggested table (illustrative, Flyway-owned):** `assistant_chunk(id, source_type, source_id, source_revision, project_key, chunk_index, content, content_hash, embedding vector(768), embedding_model, created_at)`; unique on `(source_type, source_id, source_revision, chunk_index)`; `content_hash` skips re-embedding unchanged text; delete-by-source before re-insert on refresh. Old document revisions are separate `source_revision` values, so the "old revisions" requirement needs no special mechanism.

**Access scoping (design flag, not a library).** Per-user Jira credentials scope what each user may read. A shared vector index can leak text a user could not fetch from Jira. Decide early: store `project_key` per chunk and filter retrieval by the projects the caller can see, or accept an app-wide visibility rule explicitly. This is the most important pitfall for the vector side.

## Q4. Streaming to the SPA

**Recommendation: stream, using `SseEmitter` from a POST endpoint.**

- Why stream at all: even the 4B model takes seconds, and the 8B CPU-offloaded case takes tens of seconds. A spinner for that long reads as broken. Token streaming plus early `source` events makes it feel alive.
- Spring MVC `SseEmitter` (`produces = text/event-stream`) is the standard, needs no WebFlux, and works with the current servlet stack. Do the model call on a separate executor (virtual threads are available on Java 25: `spring.threads.virtual.enabled=true` if not already set, or a dedicated `TaskExecutor`) and send named events: `token`, `source`, `tool` (optional "looking up release X..." status), `done`, `error`. Set an explicit emitter timeout above the worst-case answer time, and cancel the Ollama call on `onCompletion`/`onTimeout`/client disconnect so abandoned questions do not hold the GPU.
- Frontend: `EventSource` is GET-only and cannot carry a question body, so use `fetch('/api/assistant/chat', {method:'POST', credentials:'same-origin', ...})` and read `response.body.getReader()` with a `TextDecoder`, splitting on blank lines. CSRF stays covered by the locked `SameSite=Strict` decision since this is still a same-site cookie-authenticated POST. Abort with `AbortController` for a Stop button.
- Security pitfalls specific to this setup: (a) Spring Security re-checks authorization on the async dispatch, so confirm the JWT-cookie filter and `SecurityContext` propagation work for `SseEmitter` (write one MockMvc/integration test); (b) any reverse proxy in front must disable response buffering for this path (`X-Accel-Buffering: no` / `proxy_buffering off`) or tokens arrive in one lump; (c) never run the emitter work on the request thread.
- Plain request/response is the fallback and is acceptable for a first slice (single JSON with answer + sources), but the UX cost is high on this hardware. Since the SSE endpoint is a small increment over the JSON one, build streaming from the start.
- Tool-call turns do not stream text; stream only the final answer turn. Emit a `tool` status event while tools run.
- Ollama-to-backend leg is NDJSON, parsed with a `BufferedReader` over `RestClient`'s `exchange(...)`/`retrieve().body(InputStream.class)`; Jackson (already present, Jackson 3 in Boot 4) maps each line.

**Chat UI:** build with existing primitives (the repo's Tailwind/shadcn setup): a scrolling list, a textarea, a source-chip row. Render answer text as plain text with `whitespace-pre-wrap` and render `[n]` citation markers as links to the internal release/ticket/document routes. Do not add `@chatscope`, `ai` (Vercel AI SDK), `assistant-ui`, or similar; they assume their own transport or backend protocol and add more than the 100-150 lines they save.

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Ollama client | Hand-rolled `RestClient` | Spring AI 2.0.1 (`spring-ai-starter-model-ollama`, `spring-ai-starter-vector-store-pgvector`) | Valid, supported on Boot 4.1; rejected for table-schema ownership, tool-loop control, `@Tool` pressure on the hexagon, and 2.x churn. First thing to revisit if scope grows to many providers/MCP |
| Ollama client | Hand-rolled | LangChain4j 1.22.0 | Same objections; Boot integration is in beta community artifacts |
| Vector access | Native SQL via `JdbcClient` | `hibernate-vector` 7.4.1 (exists on Central) / `pgvector-java` 0.1.6 | Extra dependency to map one column and one operator; native SQL is two statements. Reconsider only if chunks become JPA entities with many queries |
| Vector store | pgvector | Separate store (Qdrant, Chroma) | Locked decision; also no second datastore to back up or secure |
| Index | HNSW | IVFFlat | Needs populated data and retuning; poor fit for incremental indexing |
| Chat model (dev) | `qwen3:4b` | `qwen2.5-coder:7b` | Code-tuned, does not fit 4 GB, so slow |
| Transport | SSE over POST + fetch | WebSocket | Heavier, needs separate security wiring; the flow is one-directional |
| Transport | SSE over POST + fetch | `EventSource` | GET only, cannot send the question body |

## Installation

```bash
# Backend: no new Maven dependencies.
# Test infra only: use pgvector/pgvector:0.8.7-pg18-trixie as the Testcontainers Postgres image.

# Compose (both compose.yaml and compose.app.yaml):
#   image: pgvector/pgvector:0.8.7-pg18-trixie
# compose.app.yaml app service:
#   RM_OLLAMA_BASE_URL: http://host.docker.internal:11434
#   (Linux host) extra_hosts: ["host.docker.internal:host-gateway"]

# Ollama (dev machine)
ollama pull qwen3:4b
ollama pull nomic-embed-text   # already installed

# Production host (>= 12 GB VRAM)
ollama pull qwen3:8b           # or granite4.1:8b

# Frontend: no new packages (optionally: npm install react-markdown@10.1.0)
```

## What NOT to Add

- Spring AI / LangChain4j starters (see Q1), Spring WebFlux, `WebClient`.
- Any hosted LLM or embedding API (locked: no data egress). Also no Ollama "cloud" model tags that appear in the ollama.com listing: they proxy to remote servers and would violate that rule. Pin local tags only.
- A second vector database, or a Redis/queue just for indexing: use the existing Postgres and an async executor/outbox table if reliability demands it.
- MCP server/client (not needed for in-process read-only tools).
- Write-capable tools of any kind; fine-tuning; agent frameworks; LangGraph-style orchestration.
- Chat UI kits or the Vercel AI SDK on the frontend.

## Confidence

| Area | Level | Notes |
|------|-------|-------|
| Library versions (Spring AI 2.0.1, LangChain4j 1.22.0, pgvector image 0.8.7-pg18-trixie, hibernate-vector 7.4.1, pgvector-java 0.1.6, react-markdown 10.1.0) | HIGH | Maven Central metadata, Docker Hub tag API and npm registry, fetched 2026-10-13 |
| Spring AI 2.0 targets Boot 4.0/4.1, Ollama in core | HIGH | spring.io GA blog 2026-06-12 |
| Model sizes, capability tags | HIGH | ollama.com/library pages fetched 2026-10-13 |
| Model tool-calling quality and latency numbers | LOW | Not benchmarked on this hardware; measure with a golden set |
| Ollama API details (`think`, `num_ctx`, tool message shape) | MEDIUM | Training knowledge, not re-verified against docs.ollama.com in this pass |
| nomic 768 dims and `search_*` prefixes | MEDIUM | Model card knowledge, Ollama page silent |
| pgvector 0.8 iterative scan, HNSW trade-offs | MEDIUM | Project README knowledge, not re-fetched |
| Extension creation privileges in production Postgres | LOW | Unknown production database; check |

## Sources

- https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/ (Spring AI 2.0 GA, Boot 4.0/4.1, Ollama in core, tool-loop change)
- https://repo1.maven.org/maven2/org/springframework/ai/spring-ai-bom/maven-metadata.xml (2.0.1 latest GA, 2.1.0-M1 milestone)
- https://repo1.maven.org/maven2/dev/langchain4j/langchain4j-bom/maven-metadata.xml (1.22.0), langchain4j-community-bom (1.22.0-beta32)
- https://repo1.maven.org/maven2/org/hibernate/orm/hibernate-vector/ and https://repo1.maven.org/maven2/com/pgvector/pgvector/ (7.4.1.Final present; 0.1.6)
- https://hub.docker.com/v2/repositories/pgvector/pgvector/tags?name=pg18 (0.8.7-pg18-trixie, 0.8.7-pg18-bookworm)
- https://ollama.com/library/qwen3, /granite4.1, /qwen3.5, /qwen3.8, /qwen2.5-coder, /nomic-embed-text, /qwen3-embedding, /embeddinggemma (sizes, capability tags)
- https://registry.npmjs.org/react-markdown/latest (10.1.0)
- https://docs.ollama.com/api (to re-verify chat/embed/tool shapes during phase planning)
