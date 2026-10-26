# Phase 10: RAG Chat & Citations - Research

**Researched:** 2026-10-13
**Domain:** Streamed RAG generation inside the standalone `rag-service` (Spring MVC `SseEmitter`, hand-rolled Ollama `/api/chat` NDJSON adapter, server-built citations, request bounds)
**Confidence:** HIGH for Ollama wire facts and Spring 7.0.8 behaviour (observed / read from source); MEDIUM for qwen3:4b behaviour and GPU fit (model not pulled, so these are measurement tasks)

## Summary

`10-AI-SPEC.md` is a sound baseline and the plan should follow it. This research verified its "verify" items against the live Ollama 0.34.0 and against the Spring Framework 7.0.8 sources actually resolved in `~/.m2`, and found **five places where the AI-SPEC is wrong or incomplete for this codebase**. They change task content and are listed first under "Corrections to AI-SPEC". The most consequential: Spring 7.0.8's `JdkClientHttpRequestFactory.setReadTimeout` bounds the **whole exchange including the streamed body** (not just headers), and Ollama sends no response headers until the model is loaded, so the stream client's timeout must be derived from the deadline rather than set to "about 60 s".

Everything else maps cleanly onto existing seams: `ChunkSearch` needs a list-of-collections signature, `SearchHit` needs a `collectionId`, `CollectionService` needs a `requireAll`, `IndexWorker.tick()` needs a one-line pause check against a `GenerationGate`, `GlobalExceptionHandler` needs five new reasons, and both filters already behave correctly on async dispatch (verified in source). Collections are addressed by **name** in Phase 9, so the chat request carries `collections: [names]`, not UUIDs, and citations carry the caller's `external_id` as the document id.

**Primary recommendation:** Build exactly the AI-SPEC architecture with the corrections below. Stream client `readTimeout` = deadline + 5 s (a whole-exchange backstop), watchdog at the deadline closes the stream, truncation is prevented by the pre-send budget (the `prompt_eval_count` heuristic does not detect it), and the real-server SSE tests use a tiny JDK `HttpServer` fake Ollama (to observe client aborts), with WireMock for the status/shape cases.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
Already locked (Phase 9 CONTEXT, PROJECT.md Key Decisions, research/v1.1, ROADMAP scope notes):
- Same standalone `rag-service`, same API-key tenancy: only the caller's own collections are visible; anyone else's is **404**.
- No AI framework. A hand-rolled `RestClient` adapter to Ollama `/api/chat` (NDJSON streaming) behind a provider-neutral port, mirroring the `EmbeddingPort` / `EmbeddingPurpose` seam so a later Bedrock adapter changes no callers. Ollama-specific options stay inside the adapter.
- Ollama options set explicitly: `num_ctx` (the default silently truncates), `keep_alive`, `think:false`, low temperature.
- Streaming is `SseEmitter` from a **POST** endpoint. Events: `status`, `token`, `citations`, `done`, `error` (plus `meta` if research finds it useful).
- **Stateless.** Conversation history is a request parameter; nothing is persisted.
- **Pre-retrieval only**: retrieve, then generate once. No tools.
- Answers are **plain text**. Untrusted passage text is fenced in the prompt as data. The residual prompt-injection risk is accepted and documented (SAFE-02).
- Logs record metadata only (client id, collection ids, counts, timings, token counts, outcome), never question, history, passage or answer text.
- Start the golden fixture: the first 10 questions, including prompt-injection and not-in-the-data cases, plus a curl SSE smoke test.

Request limits & GPU contention:
- **D-10-01: Per-client in-flight limit, reject over it.** A client with its maximum number of generation requests (chat + transform) already in flight gets an immediate **429** with `Retry-After` and reason `TOO_MANY_REQUESTS`. Default limit **1 per client**, configurable. No waiting queue.
- **D-10-02: Deadline 120 s, configurable.** On deadline the service stops generation (cancels the Ollama stream) and sends an `error` event with reason `DEADLINE`. Text already streamed stays with the client. A client disconnect also cancels generation.
- **D-10-03: Indexing pauses during generation.** While any chat or transform is in flight, the indexing worker skips its poll. Queued documents simply stay `PENDING` and catch up afterwards, as in an outage. The query embedding a chat request needs is *not* blocked by this.
- **D-10-04: Fail fast when the model is unavailable.** Before opening the stream, the service returns **503** with reason `OLLAMA_UNAVAILABLE` (Ollama unreachable) or `MODEL_NOT_AVAILABLE` (chat model not pulled). Generation requests are never queued, unlike indexing. Failures after the stream opened are reported as an `error` event.

Chat request shape:
- **D-10-05: One question can search several collections.** The request lists 1..N of the caller's own collections plus an optional metadata filter using exactly the `/search` grammar (equality and `in` lists, ANDed, same bounds as Phase 9 WR-02). All listed collections must share one embedding model, otherwise **400**. Any collection not owned by the caller is **404**.
- **D-10-06: Caller sends history, the service trims it.** History is a list of prior `user`/`assistant` turns. The service keeps the most recent turns that fit its token budget, dropping the oldest first. It rejects with **400** beyond a raw cap on turn count and size (e.g. 20 turns). Retrieval uses the latest question.
- **D-10-07: The chat model is service configuration only** (`RAG_CHAT_MODEL`, `qwen3:4b` on the 4 GB dev GPU). Callers cannot choose a model.
- **D-10-08: Passage count.** The service retrieves a default `k` with a hard maximum chosen to fit `num_ctx`. A caller may pass a smaller `k` but never a larger one.

Transform endpoint (RAG-11):
- **D-10-09: Caller-owned, free-form instruction.** The request is `{text, instruction}`. The service stays domain-free and ships no presets. The instruction length is capped (about 2,000 characters).
- **D-10-10: Text must fit the context window.** The size limit derives from `num_ctx` while leaving room for the output. Larger text gets **413** with a clear reason. No silent truncation and no chunk-and-merge in this phase.
- **D-10-11: Transform text is fenced as data,** exactly like chat passages. Only the caller's instruction is an instruction. Output is plain text. The same accepted residual risk applies (SAFE-02).
- **D-10-12: Transform shares chat's limits and stream.** It counts against the same per-client in-flight limit (429), the same 120 s deadline and the same indexing pause, and uses the same SSE events except `citations` (`status`, `token`, `done`, `error`).

### Claude's Discretion
- **Refusal ("I don't know", RAG-09).** Recommended default: two layers (retrieval-score threshold that skips the model, plus a prompt instruction to refuse); a refusal is a fixed, recognisable answer with an empty citation list, marked as a refusal in the `done` event. Tune the threshold on the golden fixture.
- **Citations (RAG-07).** Built only from passage metadata (document id, title, URL, collection, metadata), deduplicated per document, never from model text. Research should compare "every passage sent to the model" against "only passages the model marks as used" and pick what holds up on a 4B model.
- Exact endpoint paths and payload names (e.g. `POST /v1/chat`, `POST /v1/transform`), SSE event payload shapes, and the `meta` event.
- The `num_ctx`, `keep_alive`, temperature and `num_predict` values; the default and maximum `k`; the history token budget; how the token budget is estimated.
- Whether the latest question is rewritten from history before retrieval. Default is no rewriting.
- SSE mechanics: heartbeats, `X-Accel-Buffering: no`, async dispatch and the `ApiKeyFilter` / `RequestSizeLimitFilter` behaviour on async re-dispatch, cancelling the Ollama stream on client abort.
- Golden fixture format and location (it is reused and extended in Phase 13).

### Deferred Ideas (OUT OF SCOPE)
- Query rewriting from history for follow-up questions: only if the golden fixture shows it is needed.
- Chunk-and-merge transforms for documents larger than the context window (D-10-10).
- Caller-selectable chat models from an allowlist (D-10-07).
- Service-owned transform presets (D-10-09).
- A waiting queue instead of 429 (D-10-01).
- Persisted conversations: still deferred, per milestone decisions.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| RAG-06 | Streamed (SSE) answer over one or more collections | `POST /v1/chat`, `ResponseEntity<SseEmitter>`; multi-collection `ChunkSearch`; Ollama NDJSON shape observed; Spring 7.0.8 emitter semantics verified |
| RAG-07 | Citations built by the service from passage metadata | Per-document numbering, hybrid MARKED/RETRIEVED basis (AI-SPEC) with `external_id` as the document id; `SearchHit` gains `collectionId` |
| RAG-09 | Honest "I don't know" | Two-layer refusal; measured nomic score ranges (below) put the gate near 0.55 and show it cannot separate "topically close but absent", so the `NO_ANSWER` layer is required |
| RAG-11 | Transform text with an instruction, no retrieval | Same gate/deadline/stream; `TextTooLargeException` -> 413 `TEXT_TOO_LARGE`; budget formula in AI-SPEC |
| SAFE-01 | Bounded requests | `GenerationGate` (429), deadline watchdog + whole-exchange read timeout backstop, disconnect cancel, `num_predict`, size caps |
| SAFE-02 | Document text is data; residual risk accepted | Nonce fences + neutraliser (AI-SPEC); injection cases G08/G09 and T03; accepted-risk entry in a 10-SECURITY register |
| SAFE-03 | Ollama is the only outbound call; metadata-only logs | New ArchUnit rule (no `java.net.http` outside `adapters.ollama`/`config`); log-canary test; no exception messages echoing bodies |
</phase_requirements>

## Corrections to AI-SPEC (read before planning)

| # | AI-SPEC says | Verified reality | Plan impact |
|---|--------------|------------------|-------------|
| C1 | "The JDK client's request timeout bounds the wait for response headers, not the body"; set `readTimeout` to about 60 s | **Wrong for Spring 7.0.8.** `JdkClientHttpRequest.TimeoutHandler` starts a timer at send and cancels it only when the response `InputStream` is **closed**. If it fires after headers, it closes the body stream. So `readTimeout` is a **whole-exchange limit** [VERIFIED: spring-web-7.0.8-sources.jar, `JdkClientHttpRequest.executeInternal`/`TimeoutHandler`]. Ollama also sends **no headers until the model is loaded** (cold chat call: headers after 5.76 s) [VERIFIED: live Ollama]. | Stream client `readTimeout = deadline + 5 s` (bean takes the chat deadline). It is both the cold-load bound and a hard backstop; the watchdog at `deadline` wins and gives the clean `DEADLINE` reason. Do not reuse `ollamaRestClient` (60 s would cut a 120 s answer) and do not use 60 s. The adapter must map `HttpTimeoutException` (wrapped in `ResourceAccessException`) to `DEADLINE`/failed, not `OLLAMA_UNAVAILABLE`, when it fires after headers. |
| C2 | Truncation detectable by `prompt_eval_count >= 0.95 * num_ctx` | **Does not detect it.** An over-long prompt reported `prompt_eval_count` of 258 at `num_ctx` 512 and 1026 at `num_ctx` 2048 (about half the window), `done_reason:"stop"`, no error [VERIFIED: live Ollama 0.34.0, qwen2.5-coder:7b]. | The pre-send budget is the only real defence. Replace the check with an **estimator calibration signal**: log `estimatedPromptTokens` and `prompt_eval_count`; WARN when `estimated < prompt_eval_count` (the estimator under-counted, so the budget is not safe) and when `prompt_eval_count <= 0.6 * estimated` with `estimated > 0.8 * num_ctx` (probable truncation). Add a golden-run assertion that the estimate is never below the actual count. Keep `done.truncated` but set it from these two conditions. |
| C3 | Request carries `collectionIds: List<UUID>`; citations carry a UUID `documentId` | Phase 9 addresses collections by **name** (`/v1/collections/{collection}`, `NAME_PATTERN = [A-Za-z0-9._:-]{1,200}`, `CollectionService.require(clientId, name)`), and `/search` returns `documentId` = the caller's `external_id` string. The AI-SPEC golden example already uses names. | Chat request: `collections: List<String>` (1..10, each `@Pattern(NAME_PATTERN)`, de-duplicated). Citation: `documentId` is the caller's `external_id` (String), plus `collection` (name). Fixes AI-SPEC `CitationItem`. |
| C4 | Opt-in via `mvn ... verify -Pgolden` (failsafe) | The pom has **no failsafe**; `*IT.java` runs under surefire 3.5.6 (`**/*Test.java`, `**/*Tests.java`, `**/*IT.java`) [VERIFIED: rag-service/pom.xml; Boot 4.1.0 manages surefire 3.5.6]. | Use `mvn -f rag-service/pom.xml test -Pgolden`. Mechanism under "Golden exclusion" below. |
| C5 | `assertReady()` via `/api/show` (404 body unspecified); `done` fields list | `/api/show` 404 body is exactly `{"error":"model 'qwen3:4b' not found"}`. A present model returns a **~50 KB** body (embeds the licence) with `"capabilities":["completion","tools","insert"]`; an embedding model shows `["embedding"]`. Chat against an embedding model is `400 {"error":"\"nomic-embed-text\" does not support chat"}`. The `done` line also carries a new `prompt_eval_cached_count`. | `assertReady()`: 200 and `capabilities` contains `completion` -> ready; 404 -> `MODEL_NOT_AVAILABLE`; 200 without `completion`, or a 400 "does not support chat" on `/api/chat` -> `MODEL_NOT_AVAILABLE` (misconfiguration, not a caller error); connect/timeout/5xx -> `OLLAMA_UNAVAILABLE`. Do not log or forward the show body. Qwen3 should also list `thinking`; do not require it because `think:false` is accepted either way (observed). |

Also correct in the adapter sketch: after the read loop, **check `cancel.isCancelled()` before throwing "Stream ended without a done message"**. Closing the `InputStream` from the watchdog usually ends `readLine()` with `null` (EOF) rather than an `IOException`, so the AI-SPEC snippet (which only checks cancellation in the `catch`) would report a deadline as `GENERATION_FAILED` [ASSUMED: JDK `HttpResponseInputStream.close()` wakes the reader with end-of-stream; the adapter test in Plan 02 must prove whichever behaviour occurs].

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Auth, tenant scoping of chat/transform | API / Backend (`ApiKeyFilter`, `CollectionService.requireAll`) | Database (`collection_id IN (...)` on owned ids) | Same fail-closed filter; 404 parity for foreign and unknown names |
| Status-code validation (400/404/413/429/503) | API / Backend (`prepare` on the request thread) | — | Must complete before the response commits |
| Retrieval, refusal gate, citation building | API / Backend (application layer) | Database (exact cosine scan) | Server-owned so citations never come from model text |
| Generation + NDJSON parsing | Outbound adapter (`adapters.ollama`) | — | Ollama specifics stay behind `ChatPort` |
| SSE framing, heartbeats, disconnect detection | API / Backend (`adapters.web.SseSink`) | — | Keeps `SseEmitter` out of `application` |
| In-flight limit and indexing pause | API / Backend (`GenerationGate`, in-memory) | — | Single-instance accepted constraint (AI-SPEC) |
| Proxy buffering control | CDN / edge (caller's proxy) | API (`X-Accel-Buffering: no`) | Header only; no proxy exists in this repo's compose |

## Standard Stack

No new dependency. Everything below is already in `rag-service/pom.xml` (Boot 4.1.0 parent) [VERIFIED: pom.xml].

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-web (Spring Framework 7.0.8, Tomcat 11.0.22 in `~/.m2`) | 4.1.0 / 7.0.8 | `SseEmitter`, `RestClient`, MVC | Project constraint; AI-SPEC Section 2 |
| Jackson 3 (`tools.jackson.databind.ObjectMapper`; annotations in `com.fasterxml.jackson.annotation`) | Boot-managed | NDJSON line parsing, SSE payloads | Same split already used in `ApiKeyFilter` and `OllamaEmbeddingAdapter` |
| JDK `java.net.http.HttpClient` (HTTP/1.1 pinned) via `JdkClientHttpRequestFactory` | JDK 25 | Streaming client | Mirrors `OllamaRestClientConfig` |
| wiremock-standalone | 3.13.2 | Ollama stubs for shape/status tests | Already used by `IndexWorkerIT`, `OllamaEmbeddingAdapterTest` |
| `jdk.httpserver` (`com.sun.net.httpserver.HttpServer`) | JDK 25 | Fake streaming Ollama that can observe client aborts | No dependency; WireMock cannot observe a client disconnect |
| Testcontainers 2.0.5 (pgvector 0.8.7-pg18) | existing | Real Postgres in ITs | `PgVectorImage` |
| ArchUnit 1.4.2 | existing | Boundary rules | `ArchitectureBoundaryTest` |
| maven-surefire-plugin | 3.5.6 (Boot-managed) | Test run + `golden` tag exclusion | Already the only runner |

**Do not add:** Spring AI, LangChain4j, an Ollama client jar, a tracing platform (AI-SPEC Sections 2 and 7).

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| One `@ConfigurationProperties("rag.chat")` record in `config` | ~15 `@Value` constructor params (the Phase 9 style) | The AI-SPEC lists about 15 keys; a validated properties record is less code and one place for defaults. It is a deliberate deviation from the `@Value` style; planner may choose either, but do not scatter the keys across many classes. |
| Spring `TaskScheduler` bean for watchdog + heartbeat | A dedicated `ScheduledExecutorService` | `@EnableScheduling` already exists (`SchedulingConfig`), so Boot's `taskScheduler` bean is present; reusing it adds no config. A plain `ScheduledExecutorService` is equally fine; either way watchdog tasks are tiny. [ASSUMED: bean name/availability, confirm with an `@Autowired TaskScheduler` in the first IT] |

## Verified Ollama 0.34.0 wire facts (live, 2026-10-13)

Installed: `nomic-embed-text:latest`, `qwen2.5-coder:7b`. `qwen3:4b` is NOT pulled. `qwen2.5-coder:7b` was used as a stand-in for shape tests only [VERIFIED: live Ollama].

**`GET /api/version`** -> `{"version":"0.34.0"}`

**`GET /api/tags`** -> `{"models":[{"name":"nomic-embed-text:latest","model":"nomic-embed-text:latest","modified_at":"...","size":274302450,"digest":"...","details":{"parent_model":"","format":"gguf","family":"nomic-bert","families":["nomic-bert"],"parameter_size":"137M","quantization_level":"F16","context_length":2048,"embedding_length":768},"capabilities":["embedding"]}, {"name":"qwen2.5-coder:7b", ... "capabilities":["completion","tools","insert"]}]}`. Names carry a `:latest` suffix when no tag was given, so a naive `name.equals(configuredModel)` check breaks for untagged config values. This is why `/api/show` is the better readiness probe (it normalises the name).

**`POST /api/show {"model":"qwen3:4b"}` (missing)** -> `HTTP/1.1 404 Not Found`, `Content-Type: application/json; charset=utf-8`, body `{"error":"model 'qwen3:4b' not found"}`.
**`POST /api/show` (present)** -> 200, about 50,700 bytes, keys include `license`, `capabilities` (`["completion","tools","insert"]`). Fits easily in a 2 s health timeout on loopback.

**`POST /api/chat` for a missing model** -> `404 {"error":"model 'qwen3:4b' not found"}` (same body; status is real, before any stream).
**`POST /api/chat` against an embedding model** -> `400 {"error":"\"nomic-embed-text\" does not support chat"}`.
**`POST /api/chat` with a bad option type** (`"num_ctx":"abc"`) -> `500 {"error":"option \"num_ctx\" must be of type integer"}`.
**Malformed JSON** -> `400 {"error":"invalid character 'b' looking for beginning of object key string"}`.
**Unreachable host** (`http://localhost:1`) -> TCP connect refused (curl exit 7); in Java this is `ResourceAccessException` wrapping `ConnectException`.

**Streaming `/api/chat` (success)**, `Content-Type: application/x-ndjson`, `Transfer-Encoding: chunked`:
```
{"model":"qwen2.5-coder:7b","created_at":"2026-10-13T13:01:26.5304998Z","message":{"role":"assistant","content":"Hello"},"done":false}
{"model":"qwen2.5-coder:7b","created_at":"...","message":{"role":"assistant","content":" there"},"done":false}
{"model":"qwen2.5-coder:7b","created_at":"...","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","total_duration":8052377200,"load_duration":7197755900,"prompt_eval_count":35,"prompt_eval_cached_count":0,"prompt_eval_duration":528477000,"eval_count":4,"eval_duration":314952000}
```
**`done_reason:"length"`** (confirmed with `num_predict:5`):
```
{"model":"qwen2.5-coder:7b","created_at":"...","message":{"role":"assistant","content":""},"done":true,"done_reason":"length","total_duration":1072811800,"load_duration":2082400,"prompt_eval_count":41,"prompt_eval_cached_count":24,"prompt_eval_duration":681265000,"eval_count":5,"eval_duration":386274000}
```
**Mid-stream error:** could not be provoked on this host. The Ollama docs state a mid-stream error "will be returned as an object in the `application/x-ndjson` format with an `error` property", e.g. `{"error":"an error was encountered while running the model"}`, and "the status code of the response will not be changed" (so it arrives with HTTP 200) [CITED: https://docs.ollama.com/api/errors]. Author the WireMock/HttpServer fixture by hand from that shape.

**Other observations**
- `think:false` on a non-thinking model (qwen2.5-coder) is accepted with no error and no `message.thinking` field. Behaviour on qwen3 is a measurement task (M3).
- **Cold-load behaviour:** after `keep_alive:0` unload, a streaming chat returned response headers after 5.76 s (total 6.06 s). Ollama does not send headers until the model is loaded, so the Java client blocks inside `exchange()` for the whole load. No bytes arrive, so a client disconnect during load is only noticeable on the next heartbeat write, and cancellation before headers cannot close a stream that does not exist yet (see C1 and Pitfall 3).
- **Prompt cache works:** a repeated identical system+user prompt reported `prompt_eval_count:28, prompt_eval_cached_count:27` and 188 ms total (vs 6.5 s cold). A stable system-prompt prefix is worth keeping byte-identical across requests (`PROMPT_VERSION` constant).
- `prompt_eval_count` includes cached tokens (28 total, 27 cached).
- `ollama ps` for the 7B stand-in: `58%/42% CPU/GPU` at `num_ctx` 8192 and `55%/45%` at 2048. A 7B does not fit this 4 GB card, which is exactly why qwen3:4b was chosen.
- `keep_alive:"1m"`/`"30m"` strings and top-level `think` accepted.

**Retrieval score ranges (nomic-embed-text, `search_query:`/`search_document:` prefixes, cosine similarity = the `score` column), 3 invented release documents** [VERIFIED: live embeddings, toy corpus, MEDIUM confidence]:

| Query kind | Best score across the 3 docs |
|------------|------------------------------|
| On-topic ("What tickets are in release 4.2.0?", "Has QA signed off on 4.2.0?") | 0.668, 0.804 |
| Topically close but absent ("Who approved the 4.3.0 change?", "Was 4.2.0 tested in UAT?") | 0.667, 0.694 (not separable from on-topic) |
| Unrelated ("capital of France", "bake sourdough bread") | 0.444, 0.512 |

Start `min-score` at **0.55**. It cleanly stops unrelated questions (G06) but cannot stop "close but absent" (G07/G05), which is the model-level `NO_ANSWER` layer's job. Tune on the golden fixture, never on this toy corpus.

## Spring Framework 7.0.8 findings (read from the resolved sources)

| Question | Finding | Source |
|----------|---------|--------|
| Is `ResponseBodyEmitter.send` thread-safe for heartbeat + token? | **Yes.** `send`, `complete`, `completeWithError` and the callback registrations all take a shared `ReentrantLock writeLock`. A send after `complete()` throws `IllegalStateException` ("already completed") via `Assert.state`; a send after the container reports an error throws `AsyncRequestNotUsableException` (an `IOException`). Sends before the handler is attached are buffered in `earlySendAttempts`, so calling `send` from the worker before the controller has returned is safe. Route every send through `SseSink` anyway so `IOException | IllegalStateException` maps to `CLIENT_GONE` in one place. | `ResponseBodyEmitter.java`, `AsyncRequestNotUsableException.java` (extends `IOException`) |
| Is the response committed when the controller returns? | Yes: the return-value handler flushes right after attaching the emitter, so status 200 and headers are fixed once the controller returns. All 400/404/413/429/503 must be thrown in `prepare` **before** returning the emitter. | `ResponseBodyEmitterReturnValueHandler` (flush at attach) |
| Callback ordering and threads | `onTimeout`, `onError`, `onCompletion` run on the **container thread**. `onCompletion` also fires after a normal `complete()`, so a `CLIENT_GONE` cancel from it is harmless but must not rewrite a recorded outcome: decide and log the outcome in `run()` before `complete()`. `onCompletion` handlers run while holding the async request's `stateLock`: they must be non-blocking (cancel flag + closing a stream is fine) and must not call `emitter.send`. | `StandardServletAsyncWebRequest.onTimeout/onError/onComplete` |
| Emitter timeout | Set per emitter via `new SseEmitter(timeoutMs)`. Use `deadline + 15 s` so the watchdog (deadline) always fires first; the timeout is only a backstop. | `StandardServletAsyncWebRequest.startAsync` |
| `OncePerRequestFilter` on async dispatch | `shouldNotFilterAsyncDispatch()` defaults to **true** and `shouldNotFilterErrorDispatch()` also defaults true. Neither `ApiKeyFilter` nor `RequestSizeLimitFilter` overrides them, so both run once, on the initial request (where the body is parsed and the `rag.clientId` attribute is set), and are skipped on the ASYNC re-dispatch. `ApiKeyFilter` has no `@Order` (lowest precedence), `RequestSizeLimitFilter` is `@Order(0)`: size check first, then auth. No change needed to either filter. A key revoked mid-stream is not re-checked (accept and document). | `OncePerRequestFilter.skipDispatch` (lines 126, 205-216) |
| Problem+json when the client sends `Accept: text/event-stream` | `AbstractMessageConverterMethodProcessor` falls back to RFC 9457 media types when the body is a `ProblemDetail` and nothing matches the Accept header, so existing `GlobalExceptionHandler` methods still return `application/problem+json`. This holds only if the mapping does **not** declare `produces = text/event-stream` (a mapping-level `produces` sets the producible-types attribute and re-introduces 406). Keep the AI-SPEC rule: set the content type on the `ResponseEntity`, and add the 429-with-`Accept: text/event-stream` IT. | `AbstractMessageConverterMethodProcessor` lines 277-280 |
| JDK `HttpClient` timeout semantics | See C1. The Spring wrapper's `readTimeout` is a whole-exchange timer cancelled only by closing the response stream. `RestClient.exchange(...)` closes the response after the callback returns, which cancels the timer. | `JdkClientHttpRequest.TimeoutHandler` |
| Request thread and virtual threads | `spring.threads.virtual.enabled=true` is already set in both `application.yml` files, so Tomcat request threads and `@Scheduled` run on virtual threads. Generation still goes to its own executor so the request thread returns immediately. | `application.yml` |

## Architecture Patterns

### System Architecture Diagram

```
caller app --POST /v1/chat (Bearer client key)--> RequestSizeLimitFilter -> ApiKeyFilter (sets rag.clientId)
                                                          |
                                         ChatController.chat()  [request thread]
                                                          |
                                  ChatService.prepare(clientId, request)
        400 shape/k/history/mixed models | 404 foreign or unknown name | 503 assertReady | 429 gate
                                                          |  (only on success)
                       new SseEmitter(deadline+15s) --> return 200 text/event-stream  (committed)
                                                          |
                          generation executor (virtual thread) -> ChatService.run(plan, sink)
   +--------------------------------------------------------------------------------------+
   | watchdog@deadline -> cancel(DEADLINE)      heartbeat every 15s -> sink.heartbeat()   |
   | status{SEARCHING} -> embed(question, QUERY) -> ChunkSearch.search(owned ids, filter) |
   |    best < min-score? --yes--> token(refusal text), citations([]), done{refusal}      |
   |    no -> PromptBuilder(fenced, budgeted) -> status{GENERATING}                        |
   |          ChatPort.stream() --NDJSON--> Ollama /api/chat (stream client, HTTP/1.1)     |
   |          tokens -> RefusalGuard (NO_ANSWER prefix) -> sink.send(token)                |
   |          done -> CitationBuilder([n] markers or fallback) -> citations -> done        |
   |   finally: gate.release() (also unpauses IndexWorker), cancel watchdog+heartbeat      |
   +--------------------------------------------------------------------------------------+
emitter.onCompletion/onError (client gone) --> cancel(CLIENT_GONE) --> closes Ollama InputStream
IndexWorker.tick(): if gate.anyInFlight() return;   (query embedding in run() is NOT paused)
```

### Recommended Project Structure (additions only)
```
rag-service/src/main/java/com/evision/ragservice/
├── domain/        ChatMessage, Citation, GenerationOptions, Completion, Cancellation(+Reason),
│                  TooManyRequestsException, ModelNotAvailableException, OllamaUnavailableException,
│                  TextTooLargeException, MixedEmbeddingModelsException   (no framework imports)
├── ports/         ChatPort, EventSink                                    (new)
│                  ChunkSearch (changed signature)
├── application/   ChatService, TransformService, PromptBuilder, CitationBuilder, RefusalGuard,
│                  GenerationGate, TokenEstimator; CollectionService.requireAll; IndexWorker (pause check)
├── adapters/ollama/  OllamaChatAdapter (new)
├── adapters/web/  ChatController, TransformController, SseSink, dto/ChatRequest, dto/TransformRequest,
│                  dto/ event records; GlobalExceptionHandler (+5 handlers)
└── config/        OllamaRestClientConfig (+ streamRestClient bean), ChatProperties, GenerationConfig
rag-service/src/test/resources/golden/   chat-v1.json, transform-v1.json, seed/, clients.json
```

### Pattern 1: Multi-collection retrieval (minimal change)
`ChunkSearch.search(UUID collectionId, ...)` becomes `search(List<UUID> collectionIds, String embeddingModel, float[] queryVector, MetadataFilter filter, int k)`. In `ChunkSearchRepositoryAdapter` replace `c.collection_id = :collectionId` with `c.collection_id IN (:collectionIds)` and bind the `List<UUID>`; add `c.collection_id` to the SELECT and a `UUID collectionId` component to `SearchHit`. The filter-clause loop (`d.metadata @> CAST(:fN_M AS jsonb)`) is untouched, so filter semantics and the WR-02 bounds (`MetadataRules.parseFilter`) are reused as-is. `SearchService.search` calls `chunks.search(List.of(target.id()), ...)`; `SearchResponse.Hit.from` ignores the new component, so `/search` output and `openapi.json` do not change. Existing call sites: `SearchService:46` and `ChunkSearchPersistenceIT:93` only [VERIFIED: grep].

Use `IN (:ids)` with a `List<UUID>` (Spring named-parameter list expansion), not `= ANY(:ids)` with an array: `NamedParameterUtils` expands collections for `IN`, whereas array binding to `ANY` has driver-specific pitfalls [ASSUMED]. Cover with a `ChunkSearchPersistenceIT` case: two collections of one client, a third collection of another client that must never appear.

Ordering across collections is valid only because D-10-05 forces one embedding model (cosine scores are then comparable). `ChatService.prepare` asserts that (all `DocumentCollection.embeddingModel()` equal) and throws `MixedEmbeddingModelsException` -> 400 `MIXED_EMBEDDING_MODELS`. The chat request's `k` bounds are chat's own (`k-default` 5, `k-max` 8), not `rag.search.max-k` (50).

`CollectionService.requireAll(UUID clientId, List<String> names)`: `names.stream().distinct().map(n -> require(clientId, n)).toList()` (first miss throws `CollectionNotFoundException(name)`, same 404 body as today).

### Pattern 2: Generation gate and indexing pause
`GenerationGate` (application): `ConcurrentHashMap<UUID, AtomicInteger>` per client plus a global `AtomicInteger`. `tryAcquire(clientId)` is compare-and-increment up to `max-in-flight-per-client`; `release(clientId)` in `run`'s `finally` and on executor rejection; `anyInFlight()` for the worker. In `IndexWorker.tick()` add `if (gate.anyInFlight()) { return; }` as the first line. **Do not touch `runOnce()`**: `IndexWorkerIT` and the golden harness call `runOnce()` directly, and `tick()` is only the scheduler entry point. `IndexWorker` takes the gate as a new constructor argument; no test constructs it by hand (all use Spring wiring) [VERIFIED: grep `new IndexWorker` found none]. An in-flight embedding batch already running when generation starts finishes normally; only new polls are skipped.

### Pattern 3: Cancellation covering the pre-header phase
The AI-SPEC `Cancellation` registers a stream-close callback only after headers arrive. Before headers (cold load, up to tens of seconds) there is nothing to close. Two acceptable options, pick one in Plan 02 and test it:
1. (Simplest) Rely on the `readTimeout = deadline + 5 s` backstop (C1) for the pre-header phase and accept that a disconnect during cold load is noticed only when the heartbeat write fails and then waits for the load to finish. The deadline contract still holds.
2. Register `Thread.currentThread()::interrupt` as a cancel callback before the call; the blocking `responseFuture.get()` in Spring's wrapper handles `InterruptedException` by cancelling the future. Clear the interrupt flag (`Thread.interrupted()`) before `run` sends its final `error` event.

Recommendation: option 1 (CLAUDE.md: no over-engineering); the heartbeat still frees the gate right after the load completes because the first token send fails.

### Pattern 4: SSE controller and sink
Follow AI-SPEC Section 3 item 4 verbatim, with these changes: request field `collections` (names); `emitter.onCompletion/onError` call `cancel(CLIENT_GONE)`; `emitter.onTimeout` calls `cancel(DEADLINE)` (backstop); executor rejection releases the gate. `SseSink.send` catches `IOException | IllegalStateException`.

### Anti-Patterns to Avoid
- **`produces = TEXT_EVENT_STREAM_VALUE` on the mapping** (re-introduces 406 for error responses).
- **Overriding `shouldNotFilterAsyncDispatch()` to false** in either filter (would try to write a 401 after commit).
- **`completeWithError` for post-open failures:** send an `error` event then `complete()`.
- **Holding a `@Transactional` or DB connection across the stream.** Retrieval is one short JDBC query.
- **Forwarding `message.thinking`** or logging any exception message that can echo a body.
- **Trusting `prompt_eval_count` to detect truncation** (C2).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SSE framing and flush | Raw servlet `AsyncContext` writer | `SseEmitter` + `SseEmitter.event()` | Lock-protected sends, early-send buffering, async lifecycle already handled (verified above) |
| HTTP client + chunked body | Raw sockets | `RestClient.exchange` over `JdkClientHttpRequestFactory` | Matches `OllamaEmbeddingAdapter`; closing the stream aborts the exchange |
| Scheduling watchdog/heartbeat | Custom thread loops | Spring `TaskScheduler` (or `ScheduledExecutorService`) | Tiny, standard |
| JSON for NDJSON lines and SSE payloads | String concatenation | Jackson 3 records | Escaping of model text inside JSON is the classic bug |
| Request validation | Manual if-chains | Bean Validation records, no class-level `@Validated` (09-07 pattern) + `MetadataRules` for the filter | Existing 400 `VALIDATION_FAILED` / `INVALID_CONTENT` handling |
| Token counting | A tokenizer dependency | `ceil(chars / 3)` estimator + calibration check (C2) | AI-SPEC decision; no tokenizer for qwen3 on the JVM |
| In-test streaming fake | WireMock for abort tests | `com.sun.net.httpserver.HttpServer` | WireMock cannot tell you the client disconnected |

## Common Pitfalls

### Pitfall 1: Whole-exchange read timeout cuts long answers
**What goes wrong:** Reusing `ollamaRestClient` (60 s) or the AI-SPEC "60 s header timeout" truncates a 90 s answer, or fails the stream client at the wrong time.
**Why:** Spring's timer runs until the response stream is closed (C1).
**Avoid:** Dedicated stream client, `readTimeout = deadline + 5 s`, configured from `rag.chat.deadline`. Test with the JDK fake: deadline 3 s in test, a 2 s stream succeeds, a 5 s stream ends with `DEADLINE`.
**Warning signs:** Streams ending at exactly the configured timeout with `ResourceAccessException`/`HttpTimeoutException`.

### Pitfall 2: EOF after cancel reported as a failure
**What goes wrong:** The watchdog closes the stream, `readLine()` returns `null`, the adapter throws "ended without done", and the user sees `GENERATION_FAILED` instead of `DEADLINE`.
**Avoid:** After the loop, `if (cancel.isCancelled()) return Completion.cancelled(cancel.reason())` before the failure throw. Unit-test both the `IOException` and the EOF variants.

### Pitfall 3: Cold load is silent and uncancellable
**What goes wrong:** Headers do not arrive until the model is loaded (5.8 s for a 7B here; qwen3:4b unmeasured). During it, no tokens flow and there is no stream to close.
**Avoid:** Send `status{SEARCHING}` and heartbeats from the start; treat the pre-header wait as bounded by the read-timeout backstop (Pattern 3). Log `load_duration` from the `done` line.

### Pitfall 4: `done.truncated` from a bad heuristic
See C2. A "probable truncation" flag that never fires gives false comfort; budget before sending and calibrate the estimator against real `prompt_eval_count`.

### Pitfall 5: Heartbeats and tokens interleaving
Verified safe at the emitter (lock), but two threads (worker + scheduler) both calling `emitter.send` must both handle `IllegalStateException` after `complete()`. A heartbeat firing between the final send and `complete()` is fine; one firing after `complete()` must be swallowed (and the heartbeat future cancelled in `finally`).

### Pitfall 6: Outcome overwritten by `onCompletion`
`onCompletion` fires on normal completion too. Record the outcome (`OK`, `REFUSED`, `DEADLINE`, ...) in `run()` before calling `sink.complete()`; the callback must only call `cancel(CLIENT_GONE)`, which is a no-op when the first reason is already set.

### Pitfall 7: Foreign vs unknown collection leaks through ordering
Resolve all names before any other check, and throw the same `CollectionNotFoundException` for both, so a 404 never reveals existence. Do **not** do the model-mismatch 400 check before ownership: a foreign collection with a different model would turn into a 400 and reveal that it exists.

### Pitfall 8: Log content leaks
Spring/Jackson parse exceptions can carry request fragments, and the AI-SPEC rule forbids logging `e.getMessage()` in the chat/transform path. Log `e.getClass().getSimpleName()` only (the Phase 9 convention, T-09-19). The log test must also cover a malformed-JSON chat body containing the canary.

### Pitfall 9: Mixed `Accept` handling for errors
Tested clients (EventSource polyfills, `fetch`) send `Accept: text/event-stream`; see the Spring table. A regression test for 429/503/400 with that header is required.

### Pitfall 10: Test `application.yml` shadowing
`rag-service/src/test/resources/application.yml` replaces main's (project note). Every new `rag.chat.*` key goes in **both**, and tests that need a live fake override `rag.ollama.base-url` with `@DynamicPropertySource`, as `IndexWorkerIT` does. Test config should use short values (deadline PT3S, heartbeat PT1S).

## Code Examples

### Stream client bean (deadline-derived timeout)
```java
// Source: pattern of rag-service config/OllamaRestClientConfig.java; semantics from spring-web 7.0.8 JdkClientHttpRequest
@Bean
RestClient ollamaStreamClient(ChatProperties chat, @Value("${rag.ollama.connect-timeout}") Duration connect) {
    HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(connect).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(chat.deadline().plusSeconds(5));   // whole exchange, not headers only
    return RestClient.builder().requestFactory(factory).build();
}
```

### Readiness probe
```java
// POST {base}/api/show {"model": <chatModel>} with the 2 s health client
// 200 + capabilities contains "completion" -> ready
// 404 or 200 without "completion"           -> ModelNotAvailableException  (503 MODEL_NOT_AVAILABLE)
// ResourceAccessException / 5xx / timeout    -> OllamaUnavailableException  (503 OLLAMA_UNAVAILABLE)
```

### 429 with Retry-After (GlobalExceptionHandler)
```java
@ExceptionHandler(TooManyRequestsException.class)
public ResponseEntity<ProblemDetail> handleTooMany(TooManyRequestsException ex) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "5")
            .body(problem(HttpStatus.TOO_MANY_REQUESTS, "Too many requests", ex.getMessage(), "TOO_MANY_REQUESTS"));
}
```
New reasons and statuses: `TOO_MANY_REQUESTS` 429, `OLLAMA_UNAVAILABLE` 503, `MODEL_NOT_AVAILABLE` 503, `TEXT_TOO_LARGE` 413, `MIXED_EMBEDDING_MODELS` 400. Reuse `INVALID_CONTENT` (400) for the history/question/instruction/k bounds and `COLLECTION_NOT_FOUND` (404). Existing `EMBEDDING_UNAVAILABLE` is the reason when the query embedding fails; since embedding happens inside `run`, it surfaces as an `error` event, so add `EMBEDDING_UNAVAILABLE` to the `ErrorReason` enum.

### Fake streaming Ollama for abort/deadline tests (JDK only)
```java
HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
server.createContext("/api/chat", ex -> {
    ex.getResponseHeaders().add("Content-Type", "application/x-ndjson");
    ex.sendResponseHeaders(200, 0);                       // 0 = chunked
    try (OutputStream out = ex.getResponseBody()) {
        for (int i = 0; i < 100; i++) {
            out.write(("{\"message\":{\"role\":\"assistant\",\"content\":\"t" + i + " \"},\"done\":false}\n").getBytes(UTF_8));
            out.flush();                                  // IOException here == the service closed the connection
            Thread.sleep(100);
        }
    } catch (IOException aborted) { streamAborted.set(true); }
});
```
Point `rag.ollama.base-url` at it with `@DynamicPropertySource`; assert (a) first `token` event reaches the client before the fake finishes, (b) after the client closes its socket `streamAborted` becomes true and `GenerationGate` is empty within about 1 s, (c) with deadline PT2S the client sees `error` with `DEADLINE` and `streamAborted` is true.

## Testing approach

**Real-Tomcat SSE test (`SseStreamingIT`)**, same shape as `RawHttpSecurityIT`: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Testcontainers` pgvector container + `HttpClient`. Read the SSE body incrementally with `HttpResponse.BodyHandlers.ofLines()` (or `ofInputStream`) so assertions see events as they arrive; parse `event:`/`data:` lines by hand (about 15 lines). To simulate a disconnect, `close()` the `ofInputStream` body; the JDK client then drops the connection and Tomcat's next write (a token or the 1 s heartbeat) fails, which drives `onError/onCompletion`. Seed a client, a collection and indexed chunks directly through `JdbcClient`/services as `StatusApiIT` does (no worker needed; insert chunk rows with a fixed 768-d vector, and stub `/api/embed` on WireMock returning that same vector so retrieval hits). Required cases: first token before the fake finishes; client abort frees the gate within about 1 s and stops the fake; deadline gives `error{DEADLINE}`; second concurrent request gets 429 problem+json (and again with `Accept: text/event-stream`); no key gives 401 problem+json and never an event stream; indexing resumes after a disconnect (`GenerationGate.anyInFlight()` false and a `PENDING` document is claimed by `runOnce`/`tick`); a mid-stream `{"error":...}` line becomes an `error` event.

**WireMock (`OllamaChatAdapterTest`, `@WireMockTest`)** for deterministic shape cases: success stream with `done_reason` `stop` and `length`; `message.thinking` ignored; unknown fields ignored; `{"error":...}` line mid-stream; `/api/show` 404, 200-without-`completion`, 200-with-`completion`; connection refused; 5xx. WireMock's `withChunkedDribbleDelay(n, ms)` can slow a body but cannot observe aborts; that is why the abort cases use the JDK fake above.

**Fakes in unit tests:** a fake `ChatPort` and a fake `EventSink` drive `ChatService`/`TransformService`/`PromptBuilder`/`CitationBuilder`/`RefusalGuard` without Spring; `ArchitectureBoundaryTest` keeps `SseEmitter` out of `application`.

**Log hygiene test:** attach a Logback `ListAppender` to the root logger for the duration of a chat run with a canary in question, history, document text and a scripted answer (and one malformed-body request), then assert no event message, argument or throwable message contains the canary. Run at default levels and once with `org.springframework.web` at DEBUG.

**Golden exclusion (`golden` tag):**
```xml
<!-- pom.xml -->
<properties><excludedGroups>golden</excludedGroups></properties>
<!-- surefire config already present; no change besides honouring the user property -->
<profiles><profile><id>golden</id>
  <properties><excludedGroups>none</excludedGroups><groups>golden</groups></properties>
</profile></profiles>
```
Surefire resolves `excludedGroups`/`groups` from project properties, so the default build excludes the `golden` tag and `-Pgolden` runs only it (`excludedGroups` set to a tag nothing carries, because an empty value is not reliably "unset") [CITED: https://maven.apache.org/surefire/maven-surefire-plugin/examples/junit-platform.html; ASSUMED for the property-resolution detail, so Plan 07 must run `mvn test` and `mvn test -Pgolden` once and show that the tagged class is skipped, then selected]. Golden test classes carry `@Tag("golden")`, are named `*IT`/`*Test` so surefire's include list matches them, begin with `assumeTrue(ollamaReachable())` and a model-present check (`/api/show` for `qwen3:4b` and `nomic-embed-text`), and index their seed through the public API then call `IndexWorker.runOnce()` until `PENDING` is zero. Command: `mvn -f rag-service/pom.xml test -Pgolden` with `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25`, `rag.ollama.base-url` overridden to `http://localhost:11434` and `rag.worker.initial-delay` irrelevant (the harness calls `runOnce()`).

## Measurement tasks that need `qwen3:4b` (explicit plan tasks; do not pull in research)

Prerequisite (human): `ollama pull qwen3:4b`. Record every result in `10-VERIFICATION` notes.

| # | Question | Command | Pass / decision rule |
|---|----------|---------|----------------------|
| M1 | Does `num_ctx` 8192 fit fully on the 4 GB GPU? | `curl -s http://localhost:11434/api/chat -d '{"model":"qwen3:4b","messages":[{"role":"user","content":"hi"}],"stream":false,"think":false,"keep_alive":"5m","options":{"num_ctx":8192,"num_predict":8}}'` then `ollama ps` | `PROCESSOR` must read `100% GPU` and `CONTEXT` `8192`. If not, repeat at 6144 and 4096 and set the config default to the largest that is 100% GPU; update the AI-SPEC token budget (about 6,970 tokens needs 8192; at 6144 drop `k-max` to 5 and history budget to 1,000). The 7B stand-in already showed 42% GPU at 8192, so expect a close call. |
| M2 | Cold-load time and silent window | unload with `curl -s http://localhost:11434/api/generate -d '{"model":"qwen3:4b","keep_alive":0}'`, then `curl -s -o /dev/null -w 'headers=%{time_starttransfer}s total=%{time_total}s\n' http://localhost:11434/api/chat -d '{...stream:true, num_ctx:8192, num_predict:4}'` | Headers time is the cold-load wait; it must be well below the deadline and below the `deadline + 5 s` read timeout. Record it as the baseline for "cold" UI messaging (Phase 13). |
| M3 | `think:false` behaviour on qwen3 | stream a short question with `"think":false` and again omitting `think`; inspect lines for a `message.thinking` field and the time to the first non-empty `content` | With `think:false` the first lines must carry `content` immediately and no `thinking`. If `thinking` appears anyway, the adapter ignore-rule (already planned) is mandatory; record the version-specific behaviour. |
| M4 | Embedder + chat residency | run one full chat (query embed then generation), then `ollama ps` | Both models listed or chat alone; measure `load_duration` of the second chat call after an embed. If swaps add seconds, document `OLLAMA_MAX_LOADED_MODELS=2` as an ops note (AI-SPEC flag 5). No code. |
| M5 | Estimator calibration | golden run logs `estimatedPromptTokens` and `prompt_eval_count` per case | `estimated >= prompt_eval_count` on every case; if any case violates, change the divisor from 3 to 2.5 (config) before Phase 13. |
| M6 | `min-score` on the real golden corpus | log `topScore` for G01-G10 (without content) | Choose a threshold between the max top score of the unrelated cases (G06) and the min of the answerable ones; start 0.55 (this research's toy corpus). |
| M7 | Time to first token and tokens per second (warm) | from `done`: `eval_count / (eval_duration/1e9)`; TTFT from the harness | Report only (AI-SPEC targets are assumptions). |

## Codebase mapping (where each piece plugs in)

| Concern | File(s) | Change |
|---------|---------|--------|
| Chat port | `ports/ChatPort.java` (new), `domain/ChatMessage`, `GenerationOptions`, `Completion`, `Cancellation` | New; no framework imports (ArchUnit rule 1) |
| Ollama chat adapter | `adapters/ollama/OllamaChatAdapter.java` (new) | Mirrors `OllamaEmbeddingAdapter` (health client for `assertReady`, stream client for `stream`) |
| Stream client bean | `config/OllamaRestClientConfig.java` | Add a second `RestClient` bean (qualifier `ollamaStreamClient`); the existing `RestClient ollamaRestClient` injection by parameter name keeps working only if the new bean has a different name, so qualify both injection points |
| Multi-collection retrieval | `ports/ChunkSearch`, `adapters/persistence/ChunkSearchRepositoryAdapter`, `domain/SearchHit` (+`collectionId`), `application/SearchService` (wrap id in `List.of`), `ChunkSearchPersistenceIT:93` | See Pattern 1 |
| Ownership of N collections | `application/CollectionService.requireAll` | New method |
| Filter reuse | `domain/MetadataRules.parseFilter` / `rejectNul` | Called unchanged by `ChatService.prepare` (also `rejectNul` on question, history and instruction) |
| Indexing pause | `application/IndexWorker.tick()` | One guard line + constructor arg |
| Error reasons | `adapters/web/GlobalExceptionHandler` | 5 new handlers (429 returns `ResponseEntity` for `Retry-After`) |
| Controllers + DTOs | `adapters/web/ChatController`, `TransformController`, `SseSink`, `dto/*` | Use `@RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE)` with `@Parameter(hidden = true)` exactly as `SearchController` does |
| OpenAPI | `rag-service/openapi.json`, `OpenApiContractIT` | `everyHandlerIsDocumented` already fails until `POST /v1/chat` and `/v1/transform` appear in `/v3/api-docs`; regenerate with `mvn -f rag-service/pom.xml test -Dtest=OpenApiContractIT -Drag.contract.update=true` and commit. Document the SSE 200 as `text/event-stream` (string schema) with the event vocabulary and `ErrorReason`/`CitationBasis` enums in the description or referenced schemas, plus the 400/404/413/429/503 problem responses. Only the plan that finishes the controllers regenerates the file. [ASSUMED: springdoc 3.1.1 renders `ResponseEntity<SseEmitter>` as a useless schema unless annotated; verify the generated JSON] |
| ArchUnit | `ArchitectureBoundaryTest` | Add (1) no `java.net.http..` and no `org.springframework.web.client..` outside `adapters.ollama`/`config` (SAFE-03 "only outbound call"); (2) `application` must not depend on `org.springframework.web.servlet..` (keeps `SseEmitter` out); existing rules already keep `RestClient` in `adapters.ollama`/`config` |
| Config keys | `src/main/resources/application.yml`, `src/test/resources/application.yml`, `compose.app.yaml` (rag-service `environment`: `RAG_CHAT_MODEL: ${RAG_CHAT_MODEL:-qwen3:4b}`), `.env.example` (commented `RAG_CHAT_MODEL=qwen3:4b`), `docs/docker.md` Ollama section (`ollama pull qwen3:4b`) | Defaults: `rag.chat.model` qwen3:4b, `num-ctx` 8192, `num-predict` 768, `temperature` 0.2, `keep-alive` 30m, `deadline` PT120S, `heartbeat-interval` PT15S, `max-in-flight-per-client` 1, `k-default` 5, `k-max` 8, `history-max-turns` 20, `history-token-budget` 1500, `min-score` 0.55; test yml: deadline PT3S, heartbeat PT1S |
| Executor/scheduler | `config/GenerationConfig` (virtual-thread executor + global cap) | Small; no new dependency |

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `RestTemplate` / WebFlux for streaming | `RestClient.exchange` + blocking read on a virtual thread | No reactive stack needed (STACK.md) |
| `produces = text/event-stream` on SSE mappings | Content type on the `ResponseEntity`, no `produces` | Error responses stay problem+json |
| Jackson 2 `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.ObjectMapper`, annotations unchanged | Copy-pasted Jackson 2 snippets will not compile |

## Runtime State Inventory

Not a rename/refactor/migration phase: omitted. (Phase 9 data is unchanged; there is no schema migration in this phase because the chat path is stateless. `V1__rag_schema.sql` needs no change.)

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Closing the JDK `HttpResponseInputStream` from another thread wakes a blocked `readLine()` (EOF or `IOException`) and drops the HTTP/1.1 connection so Ollama stops | Corrections, Pitfall 2 | Cancel would not stop generation; the abort IT (fake `HttpServer`) proves it, and the fallback is thread interrupt (Pattern 3 option 2) |
| A2 | `IN (:ids)` with a `List<UUID>` expands correctly in `JdbcClient`; `= ANY(array)` is avoided | Pattern 1 | Persistence IT fails fast; trivial to adjust |
| A3 | Spring Boot's `taskScheduler` bean is available for watchdog/heartbeat | Alternatives | Use a plain `ScheduledExecutorService` instead |
| A4 | Surefire resolves `excludedGroups`/`groups` from project properties for the `golden` profile | Testing approach | Use `<configuration>` overrides inside the profile instead; Plan 07 verifies with two runs |
| A5 | springdoc 3.1.1 needs explicit annotations to document the SSE response | Codebase mapping | Contract test shows it immediately |
| A6 | qwen3:4b weights about 2.5 GB, KV cache about 1.2 GB at 8192 (36 layers, 8 KV heads, 128 dim), may not fit 4 GB fully on GPU with compute buffers | M1 | Measured by M1; only the `num_ctx` default changes |
| A7 | Mid-stream error line shape beyond the documented `{"error":"..."}` | Ollama facts | Fixture authored from docs; parser already treats any `error` field as failure |
| A8 | Retrieval-score ranges (0.44-0.80) generalise beyond the 3-document toy corpus | Ollama facts, M6 | Mis-set threshold; tuned on the golden fixture before sign-off |

## Open Questions

1. **Should the pre-header wait be interruptible (Pattern 3 option 2)?**
   - Known: headers are withheld until load; the read-timeout backstop bounds it.
   - Unclear: whether qwen3:4b cold load on this host is long enough to matter (M2).
   - Recommendation: ship option 1; revisit only if M2 shows loads above about 20 s.
2. **Does the Phase 12 caller want `Retry-After` semantics beyond a fixed 5 s?** Recommendation: fixed 5 s, configurable later.
3. **`/v1/status` should expose the chat-model state?** Out of scope for Phase 10 (ASST-08 is Phase 13); `assertReady` already produces the two 503 reasons Phase 13 needs.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Ollama (host) | live tier, golden, manual curl | yes | 0.34.0 at localhost:11434 | Hermetic tests need none |
| `qwen3:4b` | chat default model, M1-M7, golden | **no** (not pulled; do not pull during research) | - | Human task `ollama pull qwen3:4b`; hermetic tests use stubs |
| `nomic-embed-text` | embeddings | yes | latest | - |
| JDK 25 | build | yes (`C:/Users/ucs/.jdks/jdk-25`, set `JAVA_HOME` per Maven call) | 25 | - |
| Docker | Testcontainers ITs | assumed running (existing ITs need it) | - | `docker compose` stack must not be touched by tests |
| Node 20.19 | scratch scripts only | yes | 20.19.0 | - |

**Missing, blocking for the live tier only:** `qwen3:4b`. The hermetic tier (the Phase 10 default gate) is unaffected.

## Validation Architecture

> `workflow.nyquist_validation` is `false` in `.planning/config.json`, but the orchestrator explicitly requested this section.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter (Boot 4.1 / JUnit 6 line) + AssertJ + WireMock 3.13.2 + Testcontainers 2.0.5 + ArchUnit 1.4.2 |
| Config file | `rag-service/pom.xml` (surefire 3.5.6 includes `*Test`, `*Tests`, `*IT`), `src/test/resources/application.yml` |
| Quick run command | `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25 mvn -f rag-service/pom.xml test -Dtest='PromptBuilderTest,CitationBuilderTest,RefusalGuardTest,GenerationGateTest,OllamaChatAdapterTest,ArchitectureBoundaryTest'` |
| Full suite command | `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25 mvn -f rag-service/pom.xml test` (golden excluded) |
| Live opt-in | `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25 mvn -f rag-service/pom.xml test -Pgolden` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| RAG-06 | SSE order status, token*, citations, done; first token before stream end | real-Tomcat IT | `-Dtest=SseStreamingIT` | no, Wave 0 |
| RAG-06 | Multi-collection retrieval, tenant canary never returned, mixed models 400, foreign 404 parity | persistence IT + MockMvc IT | `-Dtest=ChunkSearchPersistenceIT,ChatApiIT` | extend / no |
| RAG-07 | Citations only from sent docs; invented `[9]` dropped; zero markers -> RETRIEVED capped at 3; per-document dedupe by (collection, external_id) | unit | `-Dtest=CitationBuilderTest` | no |
| RAG-09 | Score gate refuses with zero model calls; `NO_ANSWER` prefix suppressed; refusal has empty citations and `done.refusal=true` | unit + IT | `-Dtest=RefusalGuardTest,ChatServiceTest` | no |
| RAG-11 | Transform streams without retrieval, no `citations` event; oversize text 413 `TEXT_TOO_LARGE`; instruction over cap 400 | MockMvc IT + unit | `-Dtest=TransformApiIT` | no |
| SAFE-01 | 429 + `Retry-After` (also with `Accept: text/event-stream`); deadline -> `error{DEADLINE}`; abort frees gate <= about 1 s and stops the fake; indexing resumes; 503 reasons before stream; `num_predict` and `done_reason=length` | real-Tomcat IT, adapter test | `-Dtest=SseStreamingIT,OllamaChatAdapterTest,GenerationGateTest,IndexWorkerIT` | partly |
| SAFE-02 | Neutraliser strips `<<<`, nonce, `<|im_start|>`/`<|im_end|>` from passages, titles, history, transform text; fence nonce random per request; rules only in system message | unit | `-Dtest=PromptBuilderTest` | no |
| SAFE-02 | Injection cases G08/G09/T03 pass 3/3 on the live model | golden (opt-in) | `-Pgolden` | no |
| SAFE-03 | No canary text in logs (incl. malformed body, error path); only `adapters.ollama`/`config` use `java.net.http`/`RestClient` | log-capture IT + ArchUnit | `-Dtest=LogHygieneIT,ArchitectureBoundaryTest` | no / extend |
| Contract | `openapi.json` includes new endpoints, event/enum schemas | contract IT | `-Dtest=OpenApiContractIT` | extend + regenerate |
| Auth | no key -> 401 problem+json, never an event stream; filters not re-run on async dispatch | real-Tomcat IT | `-Dtest=RawHttpSecurityIT` (add case) | extend |

### Sampling Rate
- **Per task commit:** the quick command above, scoped to the files touched.
- **Per wave merge:** full hermetic suite.
- **Phase gate:** full hermetic suite green, then one recorded manual live run (`-Pgolden`, 3 runs per case) plus the curl SSE smoke test; safety cases 3/3, quality cases reported only (AI-SPEC Section 5).

### Wave 0 Gaps
- [ ] `adapters/ollama/OllamaChatAdapterTest` (WireMock + JDK fake), `adapters/web/SseStreamingIT`, `ChatApiIT`, `TransformApiIT`, `LogHygieneIT`
- [ ] `application/PromptBuilderTest`, `CitationBuilderTest`, `RefusalGuardTest`, `GenerationGateTest`, `ChatServiceTest` (fake ports)
- [ ] `src/test/resources/golden/` fixture files and a shared SSE-parsing test helper
- [ ] `pom.xml` `golden` profile and default `excludedGroups`
- [ ] Framework install: none

## Security Domain

`security_enforcement` is true (ASVS level 1, block on high).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (inherited) | `ApiKeyFilter` fail-closed, unchanged; new routes under `/v1/**` need no allow-list change |
| V3 Session Management | no | Stateless |
| V4 Access Control | yes | `CollectionService.requireAll` (404 parity), `collection_id IN (owned ids)` in SQL, never a caller-supplied id in a bare query |
| V5 Input Validation | yes | Bean Validation records, `MetadataRules.parseFilter`/`rejectNul`, `@Pattern(NAME_PATTERN)`, size caps, 413 for transform text |
| V6 Cryptography | no | No new crypto |
| V7 Error Handling and Logging | yes | Metadata-only logs, class names not messages, canary test |
| V12 Resources (DoS) | yes | Gate, deadline, `num_predict`, whole-exchange read timeout, history and text caps |

### Known Threat Patterns
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection via document/history/transform text | Tampering | Per-request nonce fence, neutraliser, rules only in system message; residual risk accepted and recorded (AR entry in a new `10-SECURITY.md`) |
| Cross-tenant retrieval | Information disclosure | Owned-id SQL scope; foreign name 404 identical to unknown; canary IT (G10) |
| Collection existence probing via mixed-model 400 | Information disclosure | Ownership check first (Pitfall 7) |
| GPU exhaustion / hung generation | Denial of service | Per-client gate, deadline watchdog, read-timeout backstop, `num_predict`, global concurrency cap; in-memory gate accepted for single instance |
| Content in logs | Information disclosure | Log rules; capture-appender test; no exception messages |
| Revoked key mid-stream | Elevation/Spoofing | Accepted: filters do not re-run on async dispatch; stream bounded by the deadline |
| Outbound exfiltration | Information disclosure | Only `adapters.ollama` uses an HTTP client (ArchUnit); the compose service has no other egress configured (document, do not enforce here) |
| Ollama unauthenticated on the network | Spoofing | Existing AR-09-21; unchanged |

Add a `10-SECURITY.md` register continuing the T-09-xx format (T-10-xx) at verification time; this phase adds the accepted risks: SAFE-02 residual injection, in-memory gate (single instance), no mid-stream key re-check.

## Plan-shaping recommendations

Suggested split (7 plans, 5 waves). Files named per plan are exclusive to that plan within a wave.

| Wave | Plan | Scope | Owns (no overlap in wave) |
|------|------|-------|---------------------------|
| 1 | 10-01 Foundations | Domain types/exceptions, `ChatPort`, `EventSink`, `GenerationGate` + `GenerationGateTest`, `ChatProperties`, `IndexWorker.tick()` pause + test, `GlobalExceptionHandler` additions, both `application.yml`, `compose.app.yaml`, `.env.example`, `docs/docker.md`, pom `golden` profile + `excludedGroups` | `domain/*` (new), `ports/ChatPort, EventSink`, `application/GenerationGate, IndexWorker`, `config/ChatProperties`, `GlobalExceptionHandler`, yml, compose, env, docs, `pom.xml` |
| 2 | 10-02 Ollama chat adapter | `OllamaChatAdapter` (stream + `assertReady`), stream client bean, `OllamaChatAdapterTest` incl. JDK fake, C1/C2/Pitfall 2 behaviours | `adapters/ollama/OllamaChatAdapter`, `config/OllamaRestClientConfig`, its tests |
| 2 | 10-03 Multi-collection retrieval | `ChunkSearch` list signature, adapter SQL, `SearchHit.collectionId`, `SearchService` wrap, `CollectionService.requireAll`, persistence IT additions | `ports/ChunkSearch`, `adapters/persistence/ChunkSearchRepositoryAdapter`, `domain/SearchHit`, `application/SearchService, CollectionService`, `ChunkSearchPersistenceIT` |
| 2 | 10-04 Prompt/citation/refusal logic | `TokenEstimator`, `PromptBuilder` (fence, neutraliser, budget), `CitationBuilder`, `RefusalGuard`, prompt constants + `PROMPT_VERSION`, unit tests | `application/PromptBuilder, CitationBuilder, RefusalGuard, TokenEstimator` + tests |
| 3 | 10-05 Chat + transform services and web | `ChatService`, `TransformService`, `GenerationConfig`, `SseSink`, controllers, DTOs/events, MockMvc ITs (400/404/413/429/503 problem bodies), `ChatServiceTest` with fakes | `application/ChatService, TransformService`, `adapters/web/*Controller, SseSink, dto/*`, `config/GenerationConfig`, `ChatApiIT, TransformApiIT, ChatServiceTest` |
| 4 | 10-06 Real-server verification and contract | `SseStreamingIT` (deadline, abort, 429, 401, indexing resume, mid-stream error), `LogHygieneIT`, `RawHttpSecurityIT` additions, ArchUnit rules, `openapi.json` regeneration, `10-SECURITY.md` | tests only plus `openapi.json`, `ArchitectureBoundaryTest`, `.planning/.../10-SECURITY.md` |
| 4 | 10-07 Golden fixture and smoke | `golden/` seed + `chat-v1.json` (10 cases) + `transform-v1.json` (3), live runner class tagged `golden`, `scripts` curl SSE smoke test, verify default `mvn test` skips it and `-Pgolden` selects it | `src/test/resources/golden/**`, `GoldenChatIT`, smoke script (disjoint from 10-06) |
| 5 | 10-08 Live measurement checkpoint (`autonomous: false`) | Human pulls `qwen3:4b`, runs M1-M7 and one `-Pgolden` x3 run, tunes `num_ctx`/`min-score`/divisor, records results | `10-VERIFICATION` notes only |

Notes for the planner:
- `openapi.json`, `GlobalExceptionHandler`, `application.yml` (both) and `pom.xml` are hot files: assign each to exactly one plan as above.
- 10-05 depends on 10-01..04; 10-06/07 depend on 10-05. Waves 2 plans are independent once 10-01 lands the ports and domain types.
- Plan acceptance criteria should assert properties (e.g. "the abort IT passes", "default `mvn test` runs no `golden` class"), not grep counts (project memory: brittle grep criteria).
- Keep commands `JAVA_HOME`-prefixed per call; run `docker compose` rebuild only manually after the wave that changes the image, never inside tests.

## Sources

### Primary (HIGH confidence)
- Live Ollama 0.34.0 on this machine: `/api/version`, `/api/tags`, `/api/show` (404 and 200), `/api/chat` streaming (`stop`, `length`, 404, 400, 500, unload/cold-load timing, prompt cache, truncation behaviour), `/api/embed` (score survey), 2026-10-13
- Spring Framework 7.0.8 sources (`spring-webmvc-7.0.8-sources.jar`, `spring-web-7.0.8-sources.jar`, same version as `~/.m2`): `ResponseBodyEmitter`, `ResponseBodyEmitterReturnValueHandler`, `StandardServletAsyncWebRequest`, `DeferredResult`, `AsyncRequestNotUsableException`, `OncePerRequestFilter`, `JdkClientHttpRequest`, `AbstractMessageConverterMethodProcessor`
- Repository: `rag-service/pom.xml`, `application.yml` (both), `ChunkSearch*`, `SearchService`, `MetadataRules`, `CollectionService`, `ApiKeyFilter`, `RequestSizeLimitFilter`, `IndexWorker`, `GlobalExceptionHandler`, `OpenApiContractIT`, `RawHttpSecurityIT`, `ArchitectureBoundaryTest`, `compose.app.yaml`, `.env.example`, `09-SECURITY.md`
- https://docs.ollama.com/api/errors (mid-stream error shape)

### Secondary (MEDIUM confidence)
- `10-AI-SPEC.md` Sections 3-6 (design baseline; corrected above where verification disagreed)
- Surefire JUnit Platform docs (tags/groups) for the `golden` mechanism

### Tertiary (LOW confidence)
- qwen3:4b memory arithmetic (A6) and JDK input-stream close semantics (A1): to be proven by M1 and the adapter test

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, no new dependencies, all already in the pom
- Architecture: HIGH, maps onto verified existing seams and verified Spring behaviour
- Pitfalls: HIGH for C1/C2/C3 (observed or read from source); MEDIUM for model-specific behaviour pending `qwen3:4b`

**Research date:** 2026-10-13
**Valid until:** about 2026-11-12 (Ollama moves fast; re-check the `done` fields and `think` behaviour if the host upgrades)
