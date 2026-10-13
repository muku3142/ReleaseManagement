# Research Summary — v1.1 Release Assistant

**Synthesized:** 2026-10-13 from STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS.md (this folder)
**Confidence:** MEDIUM overall. Library versions and code integration points: HIGH. Ollama runtime behaviour and model tool-calling quality and latency on 4 GB VRAM: LOW-MEDIUM, unmeasured.

## Executive Summary

v1.1 is a **bounded, tool-using assistant with a retrieval fallback**, not a general chatbot:
- **Exact questions** (stage, dates, counts, readiness, history, churn) go to typed read-only tools over existing application services.
- **Free-text questions** go to pgvector search over ticket text, manual items, the latest generated documents and code-link text.
- **The backend owns citations and refusal.**
- **The index is derived data,** kept current by events plus a reconcile sweep. There is no Jira or ADO call at question time.

All four research files converge on one build:
- No AI framework: two thin `RestClient` adapters (`/api/chat`, `/api/embed`) behind plain-Java ports, so zero new Maven dependencies.
- A Flyway-owned `knowledge_chunk` table queried with native SQL through `JdbcClient`.
- The assistant loop lives in `application`, testable with a fake `LlmPort`.
- SSE over POST, consumed in the SPA with `fetch`. No chat library.
- The only infrastructure change is a pgvector Postgres image.

The main risks are behavioural, not architectural:
- small local models choose tools badly, invent numbers and follow injected text;
- the production host is unknown;
- per-user data scoping is a user decision.

## Key Findings

### Stack
- **Ollama client:** hand-rolled `RestClient`. Spring AI 2.0.1 and LangChain4j 1.22.0 were rejected: the tool allowlist, bounds and citations are ours, frameworks want to own the vector table, and Spring AI 2.x is still churning.
- **pgvector:** image `pgvector/pgvector:0.8.7-pg18-trixie` (tag verified on Docker Hub 2026-10-13), used in both compose files and every `*PersistenceIT`. Native SQL, no `pgvector-java` or `hibernate-vector`.
- **Chat model:** `qwen3:4b` on the 4 GB dev GPU; `qwen3:8b` or `granite4.1:8b` on a 12-16 GB production GPU. The model is a config property. Not `qwen2.5-coder:7b`: it is code-tuned and does not fit 4 GB.
- **Embeddings:** `nomic-embed-text`, 768 dims, with `search_document:` / `search_query:` prefixes. `embedding_model` is stored per chunk, so changing the model means a full re-index.
- **Ollama settings to set explicitly:** `num_ctx` (the default silently truncates), `keep_alive`, `think:false`, and low temperature on tool turns.
- **Streaming:** `SseEmitter` from a POST endpoint, with events `meta`, `status`, `token`, `citations`, `done`, `error`.
- **Rendering:** plain text first; `react-markdown` only if needed, and never with `rehype-raw`.

### Features
- **Must-have:**
  - streamed chat with visible failure states;
  - backend-built citations with deep links;
  - a refusal gate, which also refuses write or action requests;
  - 8-12 coarse read-only tools plus vector retrieval;
  - an index kept current with no question-time Jira or ADO call;
  - prompt-injection hardening;
  - request bounds: one in-flight request per user, about 4 loop rounds, a deadline.
- **Should-have:** "Ask about this release", a "how I answered" panel, an index-freshness footer, starter questions, and follow-ups from the last 4-6 turns.
- **Defer:** persisted history, feedback thumbs, and an admin re-index UI (an endpoint or CLI is enough).
- **Never:** write tools, agent frameworks, free-text-to-SQL, cloud LLM fallback.

### Architecture
- **Ports and adapters:** plain-Java ports `LlmPort`, `EmbeddingPort`, `KnowledgeSearchPort` and `KnowledgeIndexWriterPort`; adapters `adapters.llm` (Ollama) and a JDBC `KnowledgeIndexAdapter`.
- **Indexing:**
  - v1 write paths publish `ReleaseChanged(releaseId)` after commit.
  - A single-worker `KnowledgeIndexer` diffs the desired chunks against the stored ones (content hash plus embedding model) and embeds outside any DB transaction.
  - An `IndexSweep` heals missed events, Ollama outages and model changes.
- **Tools** call existing application services in-process, never controllers or repositories.
- **ArchUnit:**
  - add the new adapter packages to `applicationDoesNotDependOnAdapters`;
  - a method-name read-only rule on the assistant package, plus a non-vacuous positive test;
  - the assistant package has no dependency on Jira, ADO or credential classes, or on the index writer;
  - `KnowledgeSearchPort` has no mutators.
- **Ollama health** is reported on `/api/assistant/status`, never in readiness or liveness.
- **Scoping finding (from the code):** v1 local data has no per-user filter, so the assistant's data boundary equals the app's existing one. Conversations are the only genuinely per-user data the assistant would add.

### Top Pitfalls
1. **Indirect prompt injection.**
   - Fence untrusted text and validate tool arguments server-side.
   - Render with no HTML, images or auto-links, and build citations on the server.
   - Put injection cases in the golden set and record the residual risk as accepted.
2. **Invented numbers.**
   - Route by question shape, with tools returning precomputed fields.
   - Label superseded revisions as-of, and check that numbers in the answer appear in the context.
3. **Small-model tool failures and runaway loops.** Few flat schemas, validation with one retry, and caps in code on rounds, calls, result size, deadline and `num_predict`. Pick the model by the golden set.
4. **Stale or mismatched index.** Hash-keyed upserts, cascading deletes, async embedding after commit (never inside refresh or generate), and a startup guard on the embedding model.
5. **Ollama operations.**
   - Docker-to-host reachability (`host.docker.internal`, Linux `extra_hosts`, the `OLLAMA_HOST` bind).
   - Per-hop timeouts, and no proxy buffering for SSE.
   - Cold model load.
   - Serialisation: an app-level gate where chat has priority over embedding.
   - Silent `num_ctx` truncation.

## Conflicts and Recommendations

| # | Topic | Positions | Recommendation |
|---|---|---|---|
| 1 | Per-user scoping | ARCHITECTURE and PITFALLS: data is already shared, so a shared index adds no exposure. STACK: store `project_key` and filter by the caller's visible Jira projects. | **User decision** (see below) |
| 2 | Phase order | FEATURES: tools first. ARCHITECTURE: index → retrieval Q&A → tools → UI. | **Index-first** (retires the infrastructure risk early), with the golden fixture and an SSE curl smoke test started in phase 10 |
| 3 | ANN index | STACK: HNSW now. ARCHITECTURE: exact scan. | **Exact scan** until a slow query is measured. HNSW with filters under-returns unless `hnsw.iterative_scan` is set |
| 4 | Document revisions | FEATURES: index all. ARCHITECTURE: latest only, older ones via a tool. | **Latest only.** Old revisions crowd top-k with near-duplicates |
| 5 | Conversation persistence | ARCHITECTURE: V12 in phase 12. FEATURES: defer. | **Defer.** Keep it in the browser session; phases 10-11 are stateless with `history` passed as a parameter |

Smaller reconciliations, no user decision needed:
- Use the pinned image tag, not `pg18`.
- Defer hybrid lexical search: identifiers go through `get_ticket`, and each chunk carries a ticket-key header.
- Keep pre-retrieval in phase 10. From phase 11, tool results are authoritative for numbers, dates and status.

## Suggested Phases (continuing at 9)

- **Phase 9 — Embeddings and knowledge index.**
  - Builds: the pgvector image swap; V11 with `knowledge_chunk` and `knowledge_index_state` (no ANN index); `EmbeddingPort` and its Ollama adapter; the index ports and JDBC adapter; 4 chunkers (ticket, manual item, latest document, code link); the indexer, events and sweep; `/status`; the ArchUnit changes; a metadata-only logging policy; the `rm.assistant.enabled` switch.
  - Exit: re-running the index is a no-op; removals and superseded revisions drop their chunks; Ollama being down leaves v1 unaffected; the container reaches host Ollama.
- **Phase 10 — Retrieval Q&A, no tools.** `LlmPort` and its Ollama chat adapter (`num_ctx`, `keep_alive`, three timeout kinds, cancel on disconnect), `AssistantService` with pre-retrieval, prompt fencing and a token budget, server citations, the refusal gate, `POST /api/assistant/chat` over SSE with heartbeats, a one-in-flight-per-user gate, the first 10 golden questions, and a curl SSE smoke test.
- **Phase 11 — Tool calling.**
  - The `ToolRegistry` with 8-12 coarse tools: list and get release, get ticket, promotion history, ladder, dashboard summary, recent deployments, ticket churn, document readiness, list and get document revisions, and `search_knowledge`.
  - A bounded loop with status events and "how I answered" data, the read-only and no-Jira/ADO ArchUnit rules, and a chat-model bake-off on the golden set.
- **Phase 12 — Chat UI.**
  - The `/assistant` route and a fetch-stream hook with `AbortController`; plain-text answers with citation chips.
  - "Ask about this release", starter questions, the freshness footer, and offline, busy and model-missing states.
  - Session-only follow-ups.
  - Exit gate: the full golden set (30-50 questions, 3 runs each), plus an injection threat-model entry and a log/security review.

## User Decisions Needed Before Requirements

1. **Per-user scoping of the index.**
   - **A — shared index:** matches v1. No new exposure, simplest, no `JiraAccessContextService` dependency. But the assistant makes the existing shared visibility far easier to reach, so it must never be described as honouring Jira permissions.
   - **B — `project_key` per chunk, filtered by the caller's visible projects:** needs a cached per-user project list or a question-time Jira call. The tools need the same filter, and the result is inconsistent with the release screens, which are unfiltered.
   - **Hedge under either option:** add the `project_key` column now, because adding it later means re-embedding everything.
2. **Conversation persistence in v1.1.** Recommended: no.
3. **Index inclusion list:** only what the screens already show. Also check whether restricted-visibility Jira comments are stored today.
4. **Production hardware and Ollama topology:** GPU and VRAM, host OS, host vs containerised Ollama, same machine or separate.
5. **Production Postgres:** compose or managed, and can the migration role run `CREATE EXTENSION vector`?
6. **Accept the prompt-injection residual risk explicitly.** The assistant is advisory, with citations and "verify in source".
7. **Confirm the index-first phase order.**
8. **Minor, can default:** answer language; ship "Ask about this release" and the "how I answered" panel (recommended); REQ categories ASST, IDX, SAFE.

## Items Each Phase Must Verify
- **Ollama API (9-11):** `/api/embed`; `/api/chat` NDJSON; `num_ctx`, `keep_alive` and `think`; tool `arguments` as an object; the `role:"tool"` shape; the default `num_ctx` and `OLLAMA_NUM_PARALLEL` on the installed version.
- **pgvector (9):** the `0.8.7-pg18-trixie` image pulls, attaches the existing `pgdata` volume, and works in compose and Testcontainers. `CREATE EXTENSION` privilege in production.
- **Models (11):** golden tool set × 3 runs per candidate, recording pass rate, tokens/s and time-to-first-token. Dev numbers are the worst case; re-measure on the target host in phase 12.
- **Embedding (9):** dimension, context limit, prefixes, quality on real ticket text, and whether the chat and embedding models can both stay resident on the GPU.
- **SSE (10):** auth on async dispatch, no proxy buffering (`X-Accel-Buffering: no` and the Vite proxy), and a client abort cancelling generation.
- **Read-only rules (11):** the ArchUnit rules are active and not vacuous.

## Confidence

| Area | Confidence | Notes |
|---|---|---|
| Stack | MEDIUM-HIGH | Versions and tags verified 2026-10-13. Ollama, nomic and pgvector 0.8 specifics from training knowledge. Quality and latency unmeasured. |
| Features | MEDIUM | Established pattern. |
| Architecture | MEDIUM-HIGH | Integration points read directly from the code. |
| Pitfalls | MEDIUM | Mechanisms well known. Ollama defaults and Docker networking are version-dependent. |
