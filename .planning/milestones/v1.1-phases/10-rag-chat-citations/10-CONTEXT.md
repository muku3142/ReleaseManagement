# Phase 10: RAG Chat & Citations - Context

**Gathered:** 2026-10-13
**Status:** Ready for planning (run `/gsd-ai-integration-phase 10` first, per ROADMAP)

<domain>
## Phase Boundary

Adds generation to the existing `rag-service`. An app can:
- ask a question over one or more of its own collections and receive a streamed (SSE) answer grounded in retrieved passages, with citations built by the service and an explicit "I don't know" when the passages do not support an answer (RAG-06, RAG-07, RAG-09);
- send text plus an instruction and receive the transformed text streamed back, with no retrieval (RAG-11, used by Phase 14 document assist).

Every request is bounded, document text is treated as data, Ollama is the only outbound call, and logs carry metadata only (SAFE-01..03).

Not in this phase:
- tool definitions, tool calls and tool results (Phase 11) — no tool-round cap is needed yet;
- ReleaseManagement calling the service (Phase 12);
- any UI (Phase 13);
- the full golden set and model bake-off (SAFE-04, Phase 13). This phase starts the fixture with its first 10 questions.

Phase 9 behaviour (keys, collections, documents, indexing, search, status) must not change, except that the indexing worker pauses during generation (D-10-03).
</domain>

<decisions>
## Implementation Decisions

### Already locked (Phase 9 CONTEXT, PROJECT.md Key Decisions, research/v1.1, ROADMAP scope notes)
- Same standalone `rag-service`, same API-key tenancy: only the caller's own collections are visible; anyone else's is **404**.
- No AI framework. A hand-rolled `RestClient` adapter to Ollama `/api/chat` (NDJSON streaming) behind a provider-neutral port, mirroring the `EmbeddingPort` / `EmbeddingPurpose` seam so a later Bedrock adapter changes no callers. Ollama-specific options stay inside the adapter.
- Ollama options set explicitly: `num_ctx` (the default silently truncates), `keep_alive`, `think:false`, low temperature.
- Streaming is `SseEmitter` from a **POST** endpoint. Events: `status`, `token`, `citations`, `done`, `error` (plus `meta` if research finds it useful).
- **Stateless.** Conversation history is a request parameter; nothing is persisted.
- **Pre-retrieval only**: retrieve, then generate once. No tools.
- Answers are **plain text**. Untrusted passage text is fenced in the prompt as data. The residual prompt-injection risk is accepted and documented (SAFE-02).
- Logs record metadata only (client id, collection ids, counts, timings, token counts, outcome), never question, history, passage or answer text.
- Start the golden fixture: the first 10 questions, including prompt-injection and not-in-the-data cases, plus a curl SSE smoke test.

### Request limits & GPU contention
- **D-10-01: Per-client in-flight limit, reject over it.** A client with its maximum number of generation requests (chat + transform) already in flight gets an immediate **429** with `Retry-After` and reason `TOO_MANY_REQUESTS`. Default limit **1 per client**, configurable. No waiting queue.
- **D-10-02: Deadline 120 s, configurable.** On deadline the service stops generation (cancels the Ollama stream) and sends an `error` event with reason `DEADLINE`. Text already streamed stays with the client. A client disconnect also cancels generation.
- **D-10-03: Indexing pauses during generation.** While any chat or transform is in flight, the indexing worker skips its poll. Queued documents simply stay `PENDING` and catch up afterwards, as in an outage. The query embedding a chat request needs is *not* blocked by this.
- **D-10-04: Fail fast when the model is unavailable.** Before opening the stream, the service returns **503** with reason `OLLAMA_UNAVAILABLE` (Ollama unreachable) or `MODEL_NOT_AVAILABLE` (chat model not pulled), so a UI can show "offline" vs "model missing" later (ASST-08). Generation requests are never queued, unlike indexing. Failures after the stream opened are reported as an `error` event.

### Chat request shape
- **D-10-05: One question can search several collections.** The request lists 1..N of the caller's own collections plus an optional metadata filter using exactly the `/search` grammar (equality and `in` lists, ANDed, same bounds as Phase 9 WR-02). All listed collections must share one embedding model, otherwise **400**. Any collection not owned by the caller is **404**.
- **D-10-06: Caller sends history, the service trims it.** History is a list of prior `user`/`assistant` turns. The service keeps the most recent turns that fit its token budget, dropping the oldest first. It rejects with **400** beyond a raw cap on turn count and size (e.g. 20 turns). Retrieval uses the latest question.
- **D-10-07: The chat model is service configuration only** (`RAG_CHAT_MODEL`, `qwen3:4b` on the 4 GB dev GPU). Callers cannot choose a model. This keeps GPU usage predictable and the golden-set measurement meaningful.
- **D-10-08: Passage count.** The service retrieves a default `k` with a hard maximum chosen to fit `num_ctx`. A caller may pass a smaller `k` but never a larger one.

### Transform endpoint (RAG-11)
- **D-10-09: Caller-owned, free-form instruction.** The request is `{text, instruction}`. The service stays domain-free and ships no presets. ReleaseManagement defines its own "Clean up" / "Summarize" wording in Phase 14. The instruction length is capped (about 2,000 characters).
- **D-10-10: Text must fit the context window.** The size limit derives from `num_ctx` while leaving room for the output. Larger text gets **413** with a clear reason. No silent truncation and no chunk-and-merge in this phase.
- **D-10-11: Transform text is fenced as data,** exactly like chat passages. Only the caller's instruction is an instruction. Output is plain text. The same accepted residual risk applies (SAFE-02).
- **D-10-12: Transform shares chat's limits and stream.** It counts against the same per-client in-flight limit (429), the same 120 s deadline and the same indexing pause, and uses the same SSE events except `citations` (`status`, `token`, `done`, `error`).

### Claude's Discretion
- **Refusal ("I don't know", RAG-09).** The user did not select this area. Recommended default:
  - two layers: a retrieval-score threshold that skips the model entirely when no passage is relevant enough, plus a prompt instruction to refuse when the passages do not support an answer;
  - a refusal is a fixed, recognisable answer with an empty citation list, and is marked as a refusal in the `done` event so callers can detect it without parsing text.
  - Tune the threshold on the golden fixture.
- **Citations (RAG-07).** Built only from passage metadata (document id, title, URL, collection, metadata), deduplicated per document, never from model text. Research should compare "every passage sent to the model" against "only passages the model marks as used" (e.g. bracketed passage numbers validated server-side) and pick what holds up on a 4B model.
- Exact endpoint paths and payload names (e.g. `POST /v1/chat`, `POST /v1/transform`), SSE event payload shapes, and the `meta` event.
- The `num_ctx`, `keep_alive`, temperature and `num_predict` values; the default and maximum `k`; the history token budget; how the token budget is estimated.
- Whether the latest question is rewritten from history before retrieval. The default is no rewriting; revisit it if the golden fixture shows follow-up questions failing.
- SSE mechanics: heartbeats, `X-Accel-Buffering: no`, async dispatch and the `ApiKeyFilter` / `RequestSizeLimitFilter` behaviour on async re-dispatch, cancelling the Ollama stream on client abort.
- Golden fixture format and location (it is reused and extended in Phase 13).
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone and architecture
- `.planning/PROJECT.md` — "Current Milestone: v1.1" and the Key Decisions row "Exception (2026-10-13): a standalone, domain-free RAG service"
- `.planning/REQUIREMENTS.md` — RAG-06, RAG-07, RAG-09, RAG-11, SAFE-01..03 (and SAFE-04 for where the golden set ends up)
- `.planning/ROADMAP.md` — "Phase 10: RAG Chat & Citations": success criteria, research flags, scope notes and the AI-integration flag

### Prior phase (the service this phase extends)
- `.planning/phases/09-rag-service-foundation-index/09-CONTEXT.md` — D-09-01..17: tenancy, filter grammar, outage semantics, Ollama via config, domain-free service
- `.planning/phases/09-rag-service-foundation-index/09-REVIEW.md` and `09-REVIEW-FIX.md` — the CR-01 auth-path fix and the WR-01..04 limits and worker error handling. New endpoints must stay behind the fail-closed `ApiKeyFilter` and the size filter.
- `.planning/phases/09-rag-service-foundation-index/09-SECURITY.md` — threat register and accepted risks to extend
- `rag-service/openapi.json` — committed contract with a drift test. New endpoints extend it.

### Research (v1.1 — not the v1 files in `.planning/research/`)
- `.planning/research/v1.1/SUMMARY.md` — Phase 10 build list, Ollama settings, SSE events, refusal gate, request bounds, verification items. Note: it predates the standalone-service decision and says `/api/assistant/chat` inside the app; in this phase those endpoints live in `rag-service` with caller API keys.
- `.planning/research/v1.1/STACK.md` — model choice (`qwen3:4b` on 4 GB), Ollama `/api/chat`
- `.planning/research/v1.1/PITFALLS.md` — prompt injection, silent `num_ctx` truncation, SSE buffering, small-model failure modes
- `.planning/research/v1.1/ARCHITECTURE.md` — `LlmPort`, prompt fencing, citation design
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `rag-service/.../ports/EmbeddingPort.java` and `adapters/ollama/OllamaEmbeddingAdapter.java` — the pattern for the chat port and adapter: provider-neutral port, Ollama specifics inside the adapter, HTTP/1.1 pinned (h2c upgrade broke the JDK client).
- `config/OllamaRestClientConfig.java` — the RestClient with explicit timeouts. Streaming chat needs its own read-timeout handling.
- `application/SearchService.java` and `ports/ChunkSearch.java` — retrieval with filters for the pre-retrieval step. They need a multi-collection variant (D-10-05).
- `domain/MetadataRules.java` — filter validation and bounds to reuse for the chat filter.
- `adapters/ollama/OllamaHealthIndicator.java` and `application/StatusService.java` — Ollama reachability for the D-10-04 fail-fast check. Model presence needs `/api/tags` or `/api/show`.
- `application/IndexWorker.java` — gets the pause hook (D-10-03).

### Established Patterns
- Hexagonal packages enforced by `ArchitectureBoundaryTest`; ProblemDetail errors with a `reason` via `GlobalExceptionHandler`; Testcontainers ITs on the pgvector image; WireMock for Ollama.
- Controllers follow 09-07's pattern: no class-level `@Validated`, because Spring 7 built-in method validation returns 400 `VALIDATION_FAILED`.
- `RawHttpSecurityIT` runs against real Tomcat. SSE behaviour (async dispatch, client abort) needs the same kind of real-server test, not MockMvc.
- Environment: `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25` for Maven; rag-service's test `application.yml` shadows main's, so new properties go in both; rebuild with `docker compose -f compose.app.yaml up --build -d rag-service`.

### Integration Points
- New controllers under `/v1/**` sit behind `ApiKeyFilter` (client key) and `RequestSizeLimitFilter`.
- `rag-service/openapi.json` plus `OpenApiContractIT` for the new endpoints.
- New config: `RAG_CHAT_MODEL`, deadline, per-client limit, `num_ctx`, `k` bounds. Add them to `application.yml` (main and test), `compose.app.yaml` and `.env.example`.
- The dev host needs `qwen3:4b` pulled (`ollama pull qwen3:4b`). Today only `nomic-embed-text` and `qwen2.5-coder:7b` are installed.
</code_context>

<specifics>
## Specific Ideas
- Callers must be able to tell outcomes apart without parsing prose: busy (429), offline (503 `OLLAMA_UNAVAILABLE`), model missing (503 `MODEL_NOT_AVAILABLE`), deadline (`error` event `DEADLINE`), and refusal (flagged in `done`). Phase 13's offline, busy and model-missing states depend on this.
- Answers on the 4 GB dev GPU are the worst case. Keep every size and time bound configurable so production hardware can raise them without a code change.
</specifics>

<deferred>
## Deferred Ideas
- Query rewriting from history for follow-up questions: only if the golden fixture shows it is needed (Claude's discretion above).
- Chunk-and-merge transforms for documents larger than the context window (D-10-10).
- Caller-selectable chat models from an allowlist (D-10-07).
- Service-owned transform presets (D-10-09).
- A waiting queue instead of 429 (D-10-01).
- Persisted conversations: still deferred, per milestone decisions.
</deferred>

---

*Phase: 10-rag-chat-citations*
*Context gathered: 2026-10-13*
