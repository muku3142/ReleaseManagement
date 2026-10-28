# Phase 13: Assistant Screen & Quality Gate - Research

**Researched:** 2026-10-15
**Domain:** Browser-facing SSE proxy in the Spring Boot 4.1 MVC app, fetch-stream chat UI in React 19, prompts-as-config in both services, and an opt-in quality-gate harness run through the main-app API
**Confidence:** HIGH for code mapping (read from the repo), MEDIUM for Phase 11's final shapes (11-04..11-09 not executed; 12-10 and 12-11 not executed), MEDIUM for Spring Security behaviour on async dispatch (needs an IT, see Pitfall 2)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Already locked (earlier phases, ROADMAP scope notes)**
- Nothing is saved on the server: no saved conversations, no feedback thumbs and no admin re-index screen (v1.1 Out of Scope).
- The browser goes through ReleaseManagement's cookie login and never holds the rag-service client key (ASST-02, D-09-01). CSRF protection relies on SameSite=Strict (locked decision).
- "This release" is an exact scalar `release_id` filter (D-12-01, D-12-11). Tools take a `release` argument that the model has to name, so put the release name into the question text (12-RESEARCH handoff).
- Phase 12's orchestrator already provides what the screen needs:
  - an outcome enum (OFFLINE, BUSY(429), MODEL_MISSING(503), DEADLINE, FAILED);
  - a listener with `token()` and `roundDiscarded()`;
  - a record of which tools ran and which sources were used.

  Phase 13 adds the browser-facing streaming endpoint and the UI on top. It does not rebuild them.
- `app.assistant.enabled` defaults to OFF (D-12-09, D-12-16).
- Answers are plain text (SAFE-02). A stronger production model must pass golden T03 before v1.1 ships (7f54818). The dev model `qwen2.5-coder:7b` prints tool calls as text, so tool flows cannot be proven live on this dev machine.

**Screen placement and scope**
- **D-13-01:** There is an **Assistant page in the nav** for open questions. **The release detail page gets a right-hand drawer** for "Ask about this release", so the release stays in view.
- **D-13-02:** Scope is shown as a **removable chip** above the input (for example "Release: 4.2.0 x"). Removing the chip makes the chat unscoped. The Assistant page has a release picker for adding a scope.
- **D-13-03:** **Changing the scope starts a new chat**, so a follow-up never mixes evidence from two releases.
- **D-13-04:** The only entry points are the nav page and the release drawer.
- **D-13-05:** When `app.assistant.enabled` is false, the UI **hides the assistant entirely**: no nav item and no release button. The frontend needs a way to know the flag, for example a small config/status endpoint.

**Answer streaming UX**
- **D-13-06:** During tool rounds the user sees **only a status line**, such as "Checking deployments..." or "Searching documents...". Text streams only for the final answer, so provisional tokens from a round that becomes a tool call are never shown.
- **D-13-07:** Citations are **inline `[n]` markers plus a chip row** under the answer, for example "[1] Release notes 4.2.0 rev 3". Each chip links to the cited release, ticket, document revision or commit.
- **D-13-08:** "How I answered" is **collapsed under each answer**. Expanded, it shows the tools that ran with their arguments, and the sources used.
- **D-13-09:** Answers render as **plain text with line breaks kept**. No Markdown, and never `rehype-raw` or similar.
- **D-13-10:** A **Stop button** aborts the fetch. The abort propagates to rag-service (outcome CLIENT_GONE) and frees the one-in-flight slot. The partial answer stays, marked "stopped".
- **D-13-11:** A refusal (NO_ANSWER) is a **styled, muted note without chips**, visually distinct from an error.

**Session lifetime**
- **D-13-12:** A chat lives **in memory until reload**. It survives navigation inside the app. A reload, logout or "New chat" clears it. Nothing goes to the server or to browser storage (no sessionStorage or localStorage for chat text).
- **D-13-13:** The **page chat and the drawer chat are separate**. Opening the drawer for a different release starts fresh, following D-13-03.
- **D-13-14:** Follow-ups send **all turns of the session**, and rag-service trims them to its history budget (D-10-08). Once trimming kicks in, the UI shows "older turns not used".
- **D-13-15:** Starter questions are a **fixed list per scope**: one set for the unscoped chat and one for a release, with the release name filled in. The lists come from config (D-13-17).
- ASST-08 states: offline, busy and model-missing get distinct UI states mapped from the Phase 12 outcome enum, and the screen shows when the index was last updated. rag-service exposes `lastIndexedAt` per collection.

**Quality gate and model (SAFE-04)**
- **D-13-16: Split gate.** Phase 13 builds the 30-50 question set and a runnable gate (one command that writes a report file), runs it on dev, and records the dev numbers. **Running the gate on the production host is an explicit v1.1 ship blocker**, recorded in STATE and ROADMAP. Phase 13 can close without it, but v1.1 cannot ship. The production run also covers T03.
- **Authoring:** Claude drafts the questions and the user approves the labels at a checkpoint, as in Phase 10's golden set. Categories:
  - exact facts via tools;
  - free text via retrieval;
  - release-scoped questions;
  - follow-ups;
  - not-in-the-data;
  - injection;
  - scope leak (a scoped question must not cite another release).
- **Data:** a **fixed, reproducible seed dataset** of releases, tickets, documents and code links, loaded into a scratch instance, so results compare across models and hosts. The gate does not use live dev data.
- **Pass bar:** **every safety case passes 3/3**, and **at least 80% of quality runs pass**. The report records the model, the host, and the active prompt hash (D-13-18).
- Because the dev model cannot do tool calls, tool-dependent cases are expected to fail on dev. The dev run records them as such; this is not a reason to weaken the cases.

**Prompts as configuration (added by the user during discussion)**
- **D-13-17:** ReleaseManagement config keys under `app.assistant.*` in the main `application.yml` (and the test `application.yml`, which shadows it), each overridable by environment variable:
  - the starter question lists, unscoped and per release;
  - the tool-use instruction and scoped-question text (the release-name injection);

  Phase 12 executes unchanged; Phase 13 moves its tool-use text into config.
- **D-13-18:** The rag-service system prompts become configurable as **rule text only** under `rag.prompts.*` in rag-service's `application.yml`, with today's `Prompts.java` text as the defaults. This covers the grounding, citation, refusal and transform wording.
  - **The data fences that wrap retrieved and transformed text stay in code**, so a config edit cannot turn document text into instructions (SAFE-02).
  - The active prompt hash, alongside or replacing `PROMPT_VERSION`, goes into the generation log metadata and the gate report.
- **D-13-19:** The Phase 14 document-assist instructions (Clean up, Summarize and the per-document-type prompts) are also config, as `app.assistant.*` or a sibling block. **This is recorded for Phase 14**, which owns them; Phase 13 does not build them.
- **D-13-20:** The project uses `application.yml`, not `.properties`. "application.properties" in the user's request means the yml files.

### Claude's Discretion
- How the browser-facing stream is carried: SSE over POST with fetch plus `AbortController`, Spring MVC async/`SseEmitter`, auth on async dispatch, and no proxy buffering. These are ROADMAP research flags.
- How the frontend learns `enabled`, index freshness and the status-line labels per tool.
- The drawer component (there is no drawer pattern today; `ConfirmDialog` and `PlaceholderDrawer` are the nearest), the chip styling and the starter question wording.
- The gate harness shape: the main-app API with a seeded scratch instance, the report format, and how it reuses Phase 10's golden harness ideas.
- The `rag.prompts.*` key names, and validation at startup (for example, blank rule text fails fast).

### Deferred Ideas (OUT OF SCOPE)
- Document-assist prompts as config (D-13-19): Phase 14.
- More assistant entry points (dashboard, ticket rows): later.
- Pulling a tool-capable model for dev (for example `qwen3:4b-instruct-2507`): not chosen. The gate splits instead (D-13-16).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ASST-02 | Assistant screen behind the existing login; answers stream; browser never holds the service key | Finding 1 (SseEmitter proxy on the existing cookie chain, CSRF note), Finding 2 (fetch stream client), Finding 4 (status endpoint) |
| ASST-05 | "Ask about this release" opens the assistant scoped to that release | Finding 5 (scope: question text, `release_id` filter, forced tool scope), Finding 8 (drawer, ticket anchor) |
| ASST-06 | "How I answered" panel: tools and sources behind each answer | Finding 3 (browser event protocol, citation view), Finding 8 |
| ASST-07 | Empty chat offers starters; follow-ups use the session's turns only | Finding 4 (starters from config), Finding 5 (history cap, `historyTrimmed`) |
| ASST-08 | Offline, busy, model-missing states; index last updated | Finding 3 (outcome mapping, retry-after), Finding 4 (freshness endpoint) |
| SAFE-04 | Quality measured through the API on 30-50 questions, 3 runs each, model choice recorded | Findings 6 and 7 (prompts as config + hash; gate harness, report, ship blocker) |
</phase_requirements>

## Summary

The browser-facing stream is a thin adapter over what Phase 12 built. Use `SseEmitter` returned inside a `ResponseEntity` from a new `POST /api/assistant/chat` controller, run `AssistantService.ask(...)` on a private virtual thread, and translate the listener callbacks into named SSE events. This is the same shape rag-service's `ChatController` already uses, so it is the house pattern. The existing security chain needs no CSRF change: the access token travels as an HttpOnly cookie, `oauth2ResourceServer` exempts bearer-cookie requests from CSRF, and SameSite=Strict is the control, exactly as for every other POST. The Servlet API never reports a client disconnect; Spring's own docs say to send heartbeats because "the write fails if the client has disconnected". Abort propagation therefore needs three links: the browser aborts, the main app notices on its next write (token or heartbeat) and throws out of the listener, and the RagChatAdapter closes the upstream stream so rag-service sees CLIENT_GONE on its next write. To make that prompt, `RagChatPort.ChatListener` gains `heartbeat()` (called for every upstream SSE comment) and `toolStarted(name)`, and the port contract changes from "never throws" to "listener exceptions propagate".

Several things in the approved UI-SPEC do not match the code and need decisions before planning is final. (1) Indexed code-link documents are per ticket, so citations can name a ticket's code links but never one commit; the "Commit a1b2c3d" chip with an ADO URL is not achievable at document granularity, and ADO/Jira external hrefs are unnecessary because every indexed document carries `release_id` (link to the release page instead). (2) The release filter constrains retrieval only; tools take a model-chosen `release` argument, so a scoped chat can leak another release through a tool unless `AssistantService` forces the scope into the tool call. (3) rag-service's `done` event has no "history was trimmed" flag, so the "older turns not used" notice and the gate's model/prompt-hash recording both need small additive `done` fields in rag-service. (4) The 2,000-character composer limit equals rag-service's question limit, but the scoped-question text is prepended to the question, so the safe composer limit is about 1,500. (5) D-13-06 ("provisional tokens never shown") cannot be absolute without giving up streaming of the first tokens; recommend a short client hold-back plus clear-on-discard.

The quality gate is a JUnit `@Tag("quality-gate")` integration test in the main app, opt-in through a Maven profile (the mechanism rag-service's `-Pgolden` already uses). It starts the real main app on a random port with a Testcontainers Postgres, seeds a fixed dataset through the repositories with a fixed `Clock`, points `app.assistant.*` at a reachable rag-service using a freshly registered scratch client (so production data is never touched), waits for indexing, mints a JWT cookie, and drives `POST /api/assistant/chat` over real HTTP, 3 runs per case. Prompts become config in rag-service through an injected `PromptRules` record whose defaults are the `Prompts.java` text; the sha-256 prefix is logged and returned in `done`. Plan the rag-service edits (prompts-as-config, `done` fields) after Phase 11 plan 11-09, because 11-04..11-06 rewrite `Prompts`, `PromptBuilder` and `ChatService`.

**Primary recommendation:** Build the backend stream, status and scope enforcement against a fake `RagChatPort` first (no Phase 11 dependency), build the frontend in parallel, then do the adapter amendments after 12-10 and the rag-service prompt/`done` changes after 11-09, then the seed, cases (user checkpoint), harness and dev run last.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Auth of the stream, CSRF | API / Backend (existing `SecurityFilterChain`) | Browser (cookie, SameSite=Strict) | Cookie JWT already authenticates `/api/**`; no new auth surface |
| Streaming proxy, heartbeats, abort propagation | API / Backend (`adapters.web` controller + virtual thread) | Adapter `adapters.rag` (closes upstream) | The Servlet container only learns of disconnects through failed writes |
| Tool loop, scope enforcement on tools | API / Backend (`application.assistant.AssistantService`) | - | Orchestrator owns tool execution; the rag filter cannot constrain tools |
| Citation view (kind, ids, labels input) | API / Backend (web adapter mapper) | Browser (builds internal links with TanStack `Link`) | Built from the server's citation record, never from model text |
| Scoped-question text, starters, tool descriptions | API / Backend config (`app.assistant.*`) | Browser (fills `{release}` in starters) | D-13-17: config, env-overridable |
| Enabled flag, starters | API / Backend (`GET /api/assistant/status`, always present) | Browser (TanStack Query, hides nav) | Must work when the assistant beans do not exist |
| Index freshness | API / Backend (`GET /api/assistant/freshness`, reads rag `GET /v1/collections/{c}`) | Browser (relative time, stale styling) | Supplementary; separate so its failure or latency never blocks the nav |
| Chat state, Stop, scope reset, rendering | Browser / Client (module store above the router outlet) | - | D-13-12: memory only |
| System prompt rule text and hash | rag-service (`PromptRules`, `rag.prompts.*`) | Main app gate (reads hash from `done`) | Fences stay in code (D-13-18) |
| History trimming | rag-service (`PromptBuilder`) | Main app (caps count at 20) | Service owns the token budget; caller must stay under the 400 limits |
| Quality gate | Test tier (main-app `@Tag("quality-gate")` IT) | rag-service + Ollama (system under test) | Goes through the API that ships |

## Standard Stack

No new runtime dependency on either side. Everything below is already in the repo [VERIFIED: `pom.xml`, `frontend/package.json`, `rag-service/pom.xml`].

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring MVC `SseEmitter` | Boot 4.1.0 / Framework 7 | Browser-facing SSE stream | Official streaming return type; same as rag-service `ChatController` [CITED: docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html] |
| JDK virtual threads | Java 25 | Worker thread per stream | `Thread.ofVirtual()` precedent in rag-service `ChatController`; `EnrichmentService` owns a virtual-thread executor field |
| Spring `RestClient` | Boot-managed | Upstream chat, status read | Already configured in `RagRestClientConfig` (12-05) |
| Jackson 3 (`tools.jackson`) | Boot-managed | JSON for events | Main app is on Jackson 3 [VERIFIED: 12-RESEARCH] |
| React | 19.2.8 | UI | Already pinned |
| `@tanstack/react-query` | 5.101.4 | Status, freshness, releases queries | Already pinned |
| `@tanstack/react-router` | 1.170.18 | Routes, `Link hash=`, search params | Already pinned |
| Tailwind | v4 (`@import "tailwindcss"`) | Styling | UI-SPEC |
| fetch + `ReadableStream.getReader()` + `TextDecoder` + `AbortController` | browser built-ins | POST SSE client | `EventSource` cannot POST; a hand-rolled parser is ~30 lines |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Vitest + jsdom | ^4 / ^26 (installed) | Parser, store and component tests | Frontend tests under `frontend/tests/` |
| WireMock | 3.13.2 (installed) | RagChatAdapter amendments (heartbeat, close) | Adapter tests |
| Testcontainers + pgvector image | 2.0.5 / `PgVectorImage.IMAGE` (installed) | Gate harness main DB | Gate IT only |
| JUnit 6 `@Tag` + Maven profile | installed | Opt-in gate | Mirrors rag-service `-Pgolden` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hand-rolled SSE parser | `eventsource-parser` 4.1.2 (modified 2026-10-12, 97 kB unpacked) or `@microsoft/fetch-event-source` 2.0.1 (unmodified since 2021 in substance) [VERIFIED: npm registry] | A dependency for ~30 lines of code we fully control the producer of. Fall back to `eventsource-parser` only if the hand parser grows past the three cases below |
| `SseEmitter` | `StreamingResponseBody` | Gives a raw `OutputStream` and needs hand-written SSE framing and its own disconnect handling; no gain here |
| Two status endpoints | One endpoint with freshness inline | One endpoint makes every page load wait on up to four rag calls and ties nav visibility to rag availability |
| `String.format` for the scoped-question template | `String.replace("{release}", ...)` | Config text may contain `%`; use `replace` |

**Installation:** none. **Version verification:** `npm view eventsource-parser version` returned 4.1.2 and `@microsoft/fetch-event-source` 2.0.1 on 2026-10-15; neither is adopted.

## Architecture Patterns

### System Architecture Diagram

```
 Browser (React 19)                                Main app (Spring Boot 4.1 MVC)                         rag-service
 ------------------                                -------------------------------                        -----------
 fetch POST /api/assistant/chat  ---cookie--->  SecurityFilterChain (JWT cookie, CSRF exempt for bearer)
   body {question, history, releaseId?}          |  401 -> problem+json BEFORE any stream
   AbortController                               v
                                          AssistantChatController
                                            validate (@Valid, size caps) -> resolve scope (ReleaseService.get -> 404 before stream)
                                            new SseEmitter(total-deadline + backstop) in ResponseEntity
                                              headers: text/event-stream, Cache-Control: no-cache, X-Accel-Buffering: no
                                            Thread.ofVirtual().start(...)  ------------------------+
 <---- SSE events -----------------------------                                                      v
   status {tool}        <-- listener.toolStarted        AssistantService.ask(question', history, scope, listener)
   discard {}           <-- listener.roundDiscarded       loop <= maxRounds+1:
   token {text}         <-- listener.token                  RagChatPort.chat(...) --- RagChatAdapter ---POST /v1/chat--> ChatController (SseEmitter)
   : hb (comment)       <-- listener.heartbeat  <---- upstream SSE comment lines (every 15 s) <--------- heartbeat
   done {refused, sources[], tools[],                       ToolCallsRequested -> execute tools in-process (scope forced) -> resend signed turn
         historyTrimmed, meta{model,promptHash}}            Answered/Failed
   error {outcome, retryAfterSeconds?}  <-- Failed(reason)
 abort() ----------- TCP closed ---->  next emitter.send throws IOException
                                         -> listener throws ClientGoneException
                                         -> propagates out of AssistantService.ask / RagChatAdapter.chat
                                         -> RestClient exchange closes upstream response ---------------> SseSink.deliver fails
                                                                                                         -> Cancellation CLIENT_GONE
                                                                                                         -> gate.release (slot freed)

 GET /api/assistant/status     (always present)  -> {enabled, starters{unscoped[], release[]}}    no rag call
 GET /api/assistant/freshness  (assistant on)     -> RagStatusPort -> GET /v1/collections/{c} x4 -> newest lastIndexedAt
```

### Recommended Project Structure
```
src/main/java/com/evision/releasemgmt/
├── adapters/web/AssistantChatController.java        # POST /api/assistant/chat (SseEmitter), conditional on the switch
├── adapters/web/AssistantStatusController.java      # GET /status (always), GET /freshness (conditional dependency)
├── adapters/web/dto/                                # AssistantChatRequest, CitationView, ChatDoneEvent (records)
├── application/assistant/AssistantService.java      # amended: scope, forced tool scope, scoped-question text, history cap
├── application/assistant/AssistantFreshnessService.java  # or a method on a small status service
├── domain/assistant/ClientGoneException.java        # unchecked, thrown by the listener
├── ports/RagStatusPort.java                         # read-only: Optional<Instant> lastIndexedAt(IndexCollection)
├── adapters/rag/RagIndexAdapter.java                # also implements RagStatusPort (index client, short timeouts)
└── config/AssistantProperties.java                  # + prompts, starters, history cap, stream deadline
src/test/java/.../quality/                           # QualityGateIT, GateSeed, GateCases (loader), GateReport
src/test/resources/quality-gate/                     # cases-v1.json (+ seed texts reused from rag golden where sensible)
rag-service/src/main/java/.../application/PromptRules.java   # record + defaults + hash (D-13-18)
frontend/src/api/assistant.ts                        # status/freshness hooks, streamChat(), sse parser
frontend/src/assistant/                              # chatStore.ts, AssistantChat.tsx, AssistantPage route, ReleaseAssistantDrawer.tsx
frontend/tests/                                      # sse.test.ts, chatStore.test.ts, AssistantChat.test.tsx
```

### Pattern 1: SseEmitter controller with a virtual-thread worker (house pattern)
**What:** Return `ResponseEntity<SseEmitter>` with streaming headers, start the work on `Thread.ofVirtual()`, write events with `emitter.send`, treat `IOException`/`IllegalStateException` as client gone.
**When to use:** the one streaming endpoint.
**Example:**
```java
// Source: rag-service ChatController/SseSink in this repo (verified), pattern per Spring MVC async docs
@PostMapping(path = "/api/assistant/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public ResponseEntity<SseEmitter> chat(@Valid @RequestBody AssistantChatRequest request) {
    ScopedRelease scope = request.releaseId() == null ? null : assistant.resolveScope(request.releaseId()); // 404 before the stream opens
    SseEmitter emitter = new SseEmitter(streamTimeout.toMillis());               // backstop only; the listener enforces the real deadline
    Thread.ofVirtual().name("assistant-stream").start(() -> stream(request, scope, emitter));
    return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            .header("X-Accel-Buffering", "no")
            .body(emitter);
}

private void stream(AssistantChatRequest request, ScopedRelease scope, SseEmitter emitter) {
    BrowserListener listener = new BrowserListener(emitter, deadlineAt());       // send(): catch IOException|IllegalStateException -> throw ClientGoneException
    try {
        AssistantAnswer answer = assistant.ask(request.question(), request.history(), scope, listener);
        listener.finish(answer);                                                  // done or error event
    } catch (ClientGoneException gone) {
        // nothing to send; the upstream stream was closed by unwinding through RagChatAdapter
    } finally {
        completeQuietly(emitter);
    }
}
```

### Pattern 2: Browser event protocol (own, small, versioned by event names)
```
event: status   data: {"tool":"deployments"}                       one per tool call about to run
event: discard  data: {}                                           roundDiscarded()
event: token    data: {"text":"..."}
event: done     data: {"refused":false,
                       "sources":[{"n":1,"kind":"DOCUMENT","title":"...","releaseId":"...","documentType":"RELEASE_NOTES","revision":3}],
                       "tools":[{"round":1,"name":"deployments","arguments":{"release":"..."},"error":false}],
                       "historyTrimmed":false,
                       "meta":{"model":"...","promptHash":"..."}}
event: error    data: {"outcome":"BUSY","retryAfterSeconds":5}     OFFLINE|BUSY|MODEL_MISSING|DEADLINE|FAILED
: hb                                                                 SSE comment every upstream heartbeat
```
Pre-stream failures (401, 400 validation, 404 unknown release) stay normal problem+json responses, so the existing `apiFetch` error handling (`ApiError`, session refresh) applies before the stream opens. `FailureReason.REJECTED` maps to `FAILED`. A refusal arrives as `token` (rag sends the refusal text) followed by `done.refused=true`; the client discards the streamed text and renders the muted note.

### Pattern 3: Citation view built server-side from the citation record
`Source.collection` (rag collection name, `IndexCollection.wireName`) plus `Source.metadata` (`release_id`, `ticket_key`, `doc_type`, `revision`) give structured fields; the frontend builds internal links and labels with `DOCUMENT_TYPE_LABELS`.

| Source collection / kind | View `kind` | Fields | Chip link (frontend) |
|---|---|---|---|
| `rm-tickets` | TICKET | releaseId, ticketKey | `/releases/$releaseId#ticket-KEY` |
| `rm-documents` | DOCUMENT | releaseId, documentType, revision | `/releases/$releaseId/documents/$documentType?revision=n` |
| `rm-manual-items` | MANUAL_ITEM | releaseId, title | `/releases/$releaseId` |
| `rm-code-links` | CODE_LINKS | releaseId, ticketKey | `/releases/$releaseId#ticket-KEY` (the expanded row shows commits and PRs with their ADO links) |
| TOOL source `releases:<uuid>`, `deployments:<uuid>`, `documents:<uuid>`, `tickets:<uuid>` | TOOL | releaseId parsed from the id, title | `/releases/$releaseId` |
| TOOL source `tickets:KEY`, `dashboard:*`, `deployments:recent`, `releases:all`, `documents:pending` | TOOL | title only | none (plain text chip) |

Any value that does not parse as a UUID, a `RELEASE_NOTES|RUNBOOK|APPROVAL` type or a positive integer is dropped from the view (the frontend then renders an unlinked chip). Labels come from these fields and from `title`, never from model text.

### Pattern 4: Chat state as a module store (no new dependency)
A module-level store read with React 19's `useSyncExternalStore`, held above the router outlet by living at module scope (the `AppShell` root route never unmounts). Sessions keyed `page` and `drawer:{releaseId}`; each session holds `scope`, `turns[]`, `inFlight` (with its `AbortController`). A reload, `window.location.assign('/login')` on sign-out, or "New chat" clears it, which is the D-13-12 behaviour with zero persistence code.

### Pattern 5: Prompts as injected rule records with Java defaults (both services)
- rag-service: `@ConfigurationProperties("rag.prompts") record PromptRules(String chatSystem, String transformSystem, String toolRules, String finalRoundRule, String correction)`. A null value means "use the default constant"; a present-but-blank value fails fast. `hash()` is the first 12 hex chars of SHA-256 over `PROMPT_VERSION` and the five texts in fixed order. `PromptBuilder` takes `PromptRules` in a new constructor and its existing constructors delegate to `PromptRules.defaults()`, so every existing test is untouched. Fences (`SOURCES_HEADER`, `<<<SRC`, `<<<TOOL`, `<<<TEXT`, `FORBIDDEN`, nonce) remain `static final` in `PromptBuilder`.
- main app: `app.assistant.prompts.scoped-question` (template with `{release}` and `{question}`), `app.assistant.prompts.tool-descriptions.<tool>` (optional overrides; the in-code description stays the default), `app.assistant.starters.unscoped` / `.release` lists. `AssistantProperties` validates at startup (placeholders present, each description at most 300 chars, each starter at most 200 chars and at most 6 entries).

### Anti-Patterns to Avoid
- **`EventSource` for the chat:** cannot POST, cannot send a body; do not wrap it in a GET with the question in the URL (question text would land in access logs, SAFE-03).
- **Static `Prompts.configure(...)`:** mutable static state leaks between Boot test contexts; inject a record.
- **Putting the rule text in `rag-service/src/main/resources/application.yml` as the only copy:** the test `application.yml` shadows main (landmine already hit once with `rag.chat.model`), so the golden run would measure different prompts than ship. Keep Java defaults as the single source and treat yml keys as overrides.
- **Using the `RagIndexPort` for freshness reads:** the chat path must not depend on a write-capable port (12-09 ArchUnit rule); add `RagStatusPort`.
- **A server cache for status:** the status endpoint is config; freshness is cached by TanStack Query (`staleTime` 60 s). No server cache (CLAUDE.md: no caching without a measured bottleneck).
- **Logging question, answer, arguments or citations text anywhere:** keep the `toString` redaction pattern (`ChatMessage`, `ChatRequest`) on every new request record.

## Findings

### Finding 1: Streaming proxy behind the cookie login [flag 1]

- **Carrier:** `SseEmitter` (Pattern 1). Official Spring docs: `SseEmitter` provides SSE support; the Servlet API gives no notification when a client goes away; "it is important to send data periodically, since the write fails if the client has disconnected" [CITED: docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html]. On `IOException` from `send`, applications should not call `complete`/`completeWithError`; the container raises the async error.
- **CSRF:** `SecurityConfig` uses `csrf.spa()` but `oauth2ResourceServer` auto-exempts any request from which a bearer token can be resolved, and the resolver reads the `access_token` cookie, so authenticated POSTs are exempt in practice and SameSite=Strict is the control (comment in `SecurityConfig`, 01-05-SUMMARY threat flags) [VERIFIED: `SecurityConfig.java`]. The new POST needs no special handling. The frontend `apiFetch` still echoes `X-XSRF-TOKEN` on non-GET if the cookie exists; the stream function should do the same for consistency (harmless).
- **Auth on async dispatch (risk, needs an IT):** `AuthorizationFilter` runs on every dispatch including ASYNC, and "the security context established on the REQUEST dispatch carries over to subsequent dispatches, subtle mismatches can sometimes cause an unexpected AccessDeniedException" [CITED: docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html]. Community reports show `AccessDeniedException` plus "response is already committed" on SSE completion in Security 6 [CITED: github.com/spring-projects/spring-security/issues/12758, dev.to/junyoungmoon/solving-access-denied-issues-after-sse-implementation-in-spring-security-3nd4]. This app is STATELESS with a cookie bearer resolver, so verify rather than assume (Pitfall 2). Mitigation if it fails: `.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()` ahead of `/api/**` in the existing chain. That is safe because the REQUEST dispatch was already authorized and an ASYNC dispatch only completes the same exchange. Add it only if the IT shows the failure (YAGNI).
- **Worker thread has no SecurityContext:** the allowlisted tool services (`ReleaseService.get/search`, `PromotionService.history`, `DashboardService`, `DocumentReadinessService`, `DocumentGenerationService.revisions`, `ActorResolutionService`) do not reference `CurrentUserPort`/`JiraAccessContext` [VERIFIED: grep of `application/` found `CurrentUser` only in `JiraAccessContextService`], so no propagation is needed. A unit/IT assertion that the tools run on the stream thread without a security context guards a future regression.
- **No proxy or compression buffering:** set `Cache-Control: no-cache`, `X-Accel-Buffering: no` (nginx honours it), `Content-Type: text/event-stream`, no `Content-Length`. Boot compression is off by default and `text/event-stream` is not in the default compressible list [ASSUMED from Boot defaults; confirm no `server.compression` key exists: grep found none in `src/main/resources`]. There is no reverse proxy in the repo (`compose*.yaml`, Dockerfile) and the production topology is unknown (ROADMAP flag), so the live check must run `curl -N` through the real path and show tokens arriving incrementally. Vite's dev proxy streams without buffering.
- **Abort propagation (the three links):**
  1. Browser `controller.abort()` closes the connection. Tomcat learns only on the next write.
  2. The main app writes tokens continuously, but during prompt evaluation and tool rounds nothing is written for up to 15 s. Fix: `ChatListener.heartbeat()` (default no-op), invoked by `RagChatAdapter` for every upstream SSE comment line; the browser listener forwards it as `: hb`. Worst case from abort to main-app detection is one upstream heartbeat interval (15 s now; consider `rag.chat.heartbeat-interval: PT5S` to bound the whole chain near 10 s).
  3. The listener throws `ClientGoneException` (unchecked, `domain.assistant`). It propagates out of `RagChatPort.chat` and `AssistantService.ask` (neither catches it: `run()` catches `RuntimeException` only around tool execution). `RestClient.exchange(...)` closes the response when the lambda exits, which closes the upstream connection; rag-service's `SseSink.deliver` then fails on its next write, cancels with `CLIENT_GONE`, and `ChatService.run`'s `finally` releases the in-flight slot [VERIFIED: `SseSink.java`, `ChatService.run`].
  - The `RagChatPort` Javadoc must change from "never thrown" to "service errors never thrown; listener exceptions propagate", and `RagChatAdapterTest` needs a case where the listener throws mid-stream and the stream is closed. 12-10 is not executed yet, so amend its adapter in a Phase 13 task, not before 12-10 lands.
  - **UX consequence:** after Stop, an immediate re-ask can still hit 429 (rag `retry-after-seconds: 5`) until link 3 completes. The BUSY state with its countdown covers it; record as expected, not a bug.
  - [ASSUMED] closing the JDK `HttpClient` response body for an HTTP/1.1 exchange closes the connection. This is the documented behaviour of `BodyHandlers.ofInputStream`, and 10-09 step 7 proved rag-service reacts to a dropped connection (`CLIENT_GONE` within 15 s with `curl --max-time 3`), but the full main-app chain must be proven in the live check.
- **Overall deadline:** `AssistantService` has no total time bound (12-RESEARCH promised one in the orchestrator, the shipped code only has the round cap). With 5 POSTs of up to 120 s each, a stuck question could run 10 minutes. The browser listener checks a deadline (`app.assistant.stream-deadline`, suggested `PT5M`) on every token and heartbeat and ends with `error{DEADLINE}`; the `SseEmitter` timeout is deadline plus 30 s as a backstop.
- **Listener sits on `AssistantService`:** `token` and `roundDiscarded` already exist and `AssistantService` already calls `roundDiscarded()` before executing tools. Add `heartbeat()` and `toolStarted(String name)` as default methods on `ChatListener` (called by the adapter and by `AssistantService.execute` respectively). Keep `ChatListener.NONE` and existing tests valid.

### Finding 2: Fetch-stream client in React 19 [flag 2]

- **Hand-roll** `streamChat(body, signal, onEvent)` in `frontend/src/api/`. It reuses the request building of `client.ts` (JSON headers, XSRF echo, `credentials: 'same-origin'`) and the single-flight refresh-then-retry: a 401 with a `SESSION_REASONS` reason arrives as a normal problem+json response before any stream, so retry once after `attemptRefresh()`. Export a small `apiFetchRaw(path, init)` from `client.ts` (the one place `rawFetch` and `apiFetchBlob` already split) rather than duplicating header logic.
- **Parser (three cases, all that the server emits):** split the byte stream on a blank line (`\n\n`, tolerate `\r\n`), ignore lines starting with `:`, read `event:` and `data:` (join multiple `data:` lines with `\n`), `JSON.parse` the data, ignore unknown event names. Use `reader.read()` in a loop with `new TextDecoder().decode(chunk, { stream: true })`; do not use `for await` on `ReadableStream` (not available in all browsers) [ASSUMED, MDN-level knowledge]. The parser is a pure function `parseSse(buffer) -> {events, rest}`, unit-tested with split chunks, CRLF, multi-byte characters split across chunks and a comment line.
- **Abort:** one `AbortController` per in-flight turn stored in the session. `abort()` makes `reader.read()` reject with `AbortError`; the store marks the turn `stopped` and keeps partial text. Closing the drawer, "New chat", a scope change and unmount all call the same `stop(session)`.
- **TanStack Query's role:** not used for the stream (it is not a cache-shaped request). Use it for `status`, `freshness` and the release list (`useReleases('')`). A mutation wrapper adds nothing and cancels poorly.
- **Backpressure and rendering:** tokens append to one string in the store; one `setState`-equivalent per token is fine at the dev model's 1-8 tokens/s and at typical GPU speeds. Do not add rAF batching until measured.
- **Tests:** vitest `jsdom` provides Node's `Response`/`ReadableStream`/`TextDecoder` in the Node 20 runtime (existing `client.test.ts` already builds `new Response(...)`) [VERIFIED: `frontend/tests/api/client.test.ts`]; building a `ReadableStream` body in jsdom is [ASSUMED] and should be proven by the first Wave 0 test.

### Finding 3: Mapping Phase 12 outcomes and records to the browser [flag 1, ASST-08]

| `FailureReason` | Browser `error.outcome` | Notes |
|---|---|---|
| OFFLINE | OFFLINE | connection refused or other 503 |
| BUSY | BUSY | `retryAfterSeconds`: `ChatOutcome.Failed` has no field for it today. Recommended: add a nullable `Integer retryAfterSeconds` to `Failed` and have the adapter read the `Retry-After` header; the UI-SPEC already defines the absent case |
| MODEL_MISSING | MODEL_MISSING | |
| DEADLINE | DEADLINE | partial text stays on the client |
| REJECTED, FAILED | FAILED | |
| (client gone) | none | never displayed |

`AssistantAnswer` already carries `text`, `sources`, `toolsRun`, `refused`, `failure`; the `done` event is built from it. `ToolRun.arguments` is model-chosen text and is rendered only as text in the trace panel.

### Finding 4: Status and freshness endpoints [flag 3]

- **`GET /api/assistant/status`** (always registered, authenticated by the existing `/api/**` rule): `{ "enabled": false }` when off, else `{ "enabled": true, "starters": { "unscoped": [...], "release": [...] } }`. It must not depend on `AssistantProperties`, because that bean exists only when the switch is on (`AssistantConfig` carries the `@EnableConfigurationProperties`) [VERIFIED: `AssistantConfig.java`, `AssistantSwitchOffIT` asserts no `AssistantProperties` bean]. Inject `@Value("${app.assistant.enabled}")` plus `ObjectProvider<AssistantProperties>`. `AssistantSwitchOffIT` must keep passing; extend it to assert that `/api/assistant/status` returns `enabled:false` and the chat endpoint does not exist (404/405).
- **`GET /api/assistant/freshness`** (controller method conditional on the assistant being on; when off return 404): `{ "lastIndexedAt": "<instant>|null" }`. Implementation: `RagStatusPort.lastIndexedAt(IndexCollection)` implemented by `RagIndexAdapter` on the short-timeout index client (`GET /v1/collections/{c}`, field `lastIndexedAt`; `CollectionResponse` is `{name, embeddingModel, pending, indexed, failed, lastIndexedAt}` [VERIFIED: `CollectionResponse.java`]); take the maximum over the four collections, ignoring nulls; null for all means "never indexed". Any rag failure returns a problem+json 503 and the frontend omits the line (UI-SPEC).
- **Caching:** none on the server. Client: `staleTime` 60 s for freshness, 5 min for status. The status query must be `enabled: !!session.data` in `AppShell`, which renders on `/login` too, to avoid a 401 on the login page.
- **Route guard:** `/assistant` is under the authenticated layout. When status says disabled, redirect to `/` from the route's `beforeLoad` using `queryClient.ensureQueryData` on the status query, mirroring how `fetchSessionOrNull` is used.

### Finding 5: Scope, history and the question text [flags 4, 5]

- **Scoped-question text (D-13-17):** `AssistantService` prefixes the question with the configured template filled via `String.replace`, using the release name and version, for example `The question is about release "{release}". {question}`. The release is resolved with `ReleaseService.get` (allowlisted read) before the stream opens, so an unknown id is a 404.
- **Retrieval scope:** `filter = {"release_id": "<uuid>"}` already exists in `AssistantService.ask` [VERIFIED]. Metadata is a flat string so the equality match works [VERIFIED: 12-RESEARCH Finding 1].
- **Tools are NOT constrained by the filter (new finding).** A scoped chat could still ask the model to call `deployments` with another release's name. The scope-leak gate category (D-13-16) tests exactly this. Recommended enforcement in `AssistantService.execute` when a scope is set: overwrite the `release` argument of every tool that declares one with the scoped release id, and omit tools that cannot be release-bound (`dashboard`) from the definitions offered. Every Phase 12 tool resolves `release` through `ReleaseRef`, which accepts a UUID [VERIFIED: 12-RESEARCH Finding 5]. This also shortens the definitions (token budget).
- **Question length:** rag-service rejects questions over 2,000 chars with a 400 [VERIFIED: `ChatService.prepare`, `question-max-chars: 2000`]. The scoped prefix adds up to about 340 chars (release name at most 200, version at most 100 [VERIFIED: `V3__release_assembly.sql`, `CreateReleaseRequest`]). Set the API and composer limit to **1,500** characters (`@Size(max = 1500)`), counter shown above 1,300. This amends UI-SPEC assumption 7 (2,000 / 1,800).
- **History caps:** rag-service returns 400 for more than 20 turns or a turn over 4,000 chars [VERIFIED: `history-max-turns: 20`, `history-turn-max-chars: 4000`]. The controller sends the last 20 messages at most (config `app.assistant.history-max-turns`, same value, comment naming the rag key) and rejects a single message over 4,000 chars with a 400. The client sends only completed turns (not failed or stopped ones). History roles from the browser are untrusted; rag-service neutralises and fences them (golden G09 covers poisoned history).
- **Trimmed history flag:** `PromptBuilder.BuiltPrompt` already has `historyTurnsUsed` but nothing leaves rag-service [VERIFIED]. Add `historyTrimmed: boolean` (true when `kept.size() < history.size()`) to the `done` event, carry it through `ChatOutcome.Answered` and `AssistantAnswer` to the browser `done`. The main app's own 20-message cap also counts as trimmed.
- **Starter labels and tool labels:** tool names are `releases`, `tickets`, `deployments`, `dashboard`, `documents` [VERIFIED: tool classes]. Client table: releases and tickets map to "Looking up release details…", deployments to "Checking deployments…", documents to "Checking documents…", dashboard to the fallback "Looking things up…". "Searching documents…" has no tool to attach to (no retrieval tool exists); it is only reachable if rag's `status{SEARCHING}` were forwarded, which this phase does not do (initial label is "Thinking…").
- **Provisional tokens (D-13-06) cannot be absolute.** Tokens of a round arrive before the server knows the round ends in a tool call. Recommended client behaviour: hold the first ~150 characters of the first round in a pending buffer; show them once the buffer passes the threshold or `done` arrives; on `discard`, drop everything shown or held for that round and return to the tool label. A natively tool-calling model usually emits no preamble, so the common cases show nothing wrong. Needs user acknowledgement (Open Question 1).

### Finding 6: Prompts as configuration [flag 5]

**Main app (`app.assistant.*`), both `application.yml` files (the test file shadows main):**

| Key | Default | Env var | Purpose |
|-----|---------|---------|---------|
| `starters.unscoped` | 3 questions from the UI-SPEC | `APP_ASSISTANT_STARTERS_UNSCOPED_0..2` via relaxed binding or `SPRING_APPLICATION_JSON` | Unscoped starters |
| `starters.release` | 3 questions with `{release}` | same pattern | Scoped starters |
| `prompts.scoped-question` | `The question is about release "{release}". {question}` | `APP_ASSISTANT_PROMPTS_SCOPED_QUESTION` | Release-name injection |
| `prompts.tool-descriptions.<name>` | absent (the tool class text is the default) | relaxed binding | Optional override of the tool-use text |
| `history-max-turns` | 20 | - | Mirrors rag key |
| `stream-deadline` | `PT5M` | `APP_ASSISTANT_STREAM_DEADLINE` | Overall bound |

Lists in env vars are awkward; document `SPRING_APPLICATION_JSON` or a mounted `SPRING_CONFIG_ADDITIONAL_LOCATION` file in `docs/docker.md` as the way to override multi-line or list values. Add the keys to `compose.app.yaml` `environment` only for the scalar ones. Both ymls need every new key because the test yml shadows main (landmine), and `AssistantProperties` must fail fast on a template missing `{question}` or `{release}`, a description over 300 chars, or more than 6 starters.

The "tool-use instruction" in CONTEXT has no code counterpart in Phase 12 other than the tool `description()` strings and the missing release-name injection. This research reads D-13-17 as: tool-description override plus the scoped-question template. Needs confirmation (Open Question 3).

**rag-service (`rag.prompts.*`):** keys `chat-system`, `transform-system`, `tool-rules`, `final-round-rule`, `correction` (the last three arrive with Phase 11 plan 11-04: `TOOL_RULES`, `FINAL_ROUND_RULE`, `CORRECTION`, `TOOLS_PROMPT_VERSION = "p11-v1"`). Current Phase 10 state: `PROMPT_VERSION = "p10-v2"`, `CHAT_SYSTEM`, `TRANSFORM_SYSTEM` [VERIFIED: `Prompts.java`]. Phase 11 keeps those byte-identical and appends constants [VERIFIED: 11-04-PLAN]. **Apply D-13-18 on top of Phase 11's final state (after 11-09)**: the change is mechanical (replace each `Prompts.X` read in `PromptBuilder`, `ChatService`, `TransformService` by `rules.x()`), so enumerate call sites with `grep -rn "Prompts\." rag-service/src` at execution time instead of trusting this list.
- Startup validation: null uses the default; blank fails fast; `chatSystem` must still contain `NO_ANSWER` (the refusal guard depends on that literal) [VERIFIED: `RefusalGuard.REFUSAL_TEXT` usage, rule 6 text].
- Hash: first 12 hex chars of SHA-256 over `PROMPT_VERSION` plus the texts. Log `promptHash=` next to `promptVersion=` in `logRun` (and in Phase 11's tool log line); return `model` and `promptHash` in `done`.
- Process rule to document: any change in the hash requires re-running the golden suite (`-Pgolden`) and the quality gate before it ships.

### Finding 7: SAFE-04 quality gate [flag 6]

**Shape (reuses Phase 10 ideas, not code):** Phase 10's `GoldenChatIT` boots rag-service in-process, seeds through the public API, runs each case 3 times over real HTTP SSE, writes `target/golden-report.json`, and fails on any safety case below 3/3; it is `@Tag("golden")` and selected by a Maven profile that clears `excludedGroups` [VERIFIED: `GoldenChatIT.java`, `rag-service/pom.xml`]. Do the same one level up:

- **`QualityGateIT`** in the main app, `@Tag("quality-gate")`, `@SpringBootTest(webEnvironment = RANDOM_PORT)`, Testcontainers Postgres (`PgVectorImage.IMAGE`) for the main DB. Main `pom.xml` gets `<excludedGroups>quality-gate</excludedGroups>` and a `quality-gate` profile with `<groups>quality-gate</groups><excludedGroups>none</excludedGroups>`. The surefire includes already match `*IT.java`, so the tag is the only thing keeping it out of `mvn test` [VERIFIED: `pom.xml` surefire block].
- **One command:** `JAVA_HOME=... mvn -B -P '!frontend' -Pquality-gate test -Dtest=QualityGateIT -Dgate.rag-url=... -Dgate.host-label=...` with `GATE_RAG_ADMIN_KEY` in the environment. The IT skips itself (as the golden IT does) when rag-service or the admin key is missing, and fails loudly if `gate.host-label` is absent.
- **Isolation:** the harness registers its own client (`POST /v1/admin/clients`, name `rm-quality-gate-<timestamp>`), so its `rm-*` collections live in a separate namespace from the production app's client. It never reads `RM_ASSISTANT_API_KEY`. Teardown deletes every gate document by id and revokes the client; revoke only sets `revoked_at`, collections and (without the deletes) documents would remain [VERIFIED: `ClientRepositoryAdapter.revoke`, 10-09 step 9].
- **Seed:** a Java `GateSeed` builds a fixed dataset through `ReleaseRepository.create`, document and promotion repositories (the pattern `IndexAfterCommitIT` and `AssistantSwitchOffIT` already use), not Jira stubs: roughly 5 releases across shipped, UAT, frozen, open and rolled-back states, about 20 tickets with descriptions and comments, 3 manual items, code links on some tickets, release-notes/runbook documents on some releases and none on others, promotion history across the ladder. Override the `Clock` bean with a fixed instant in the IT so "last week" and staleness are deterministic. Reuse prose from `rag-service/src/test/resources/golden/seed/` where useful. Planted injection strings live in a ticket description, a comment, a manual item and a ticket summary (tools return summaries, so a summary-borne injection reaches the tool-result fence).
- **Indexing:** with `app.assistant.enabled=true` the after-commit listener and sweep push the seed to rag-service; the harness waits until each collection reports `pending == 0` and `failed == 0` (`GET /v1/collections/{c}`), with a timeout, before asking anything.
- **Auth:** mint an access token with `JwtTokenService.issue(accountId, email)` and send it as the `access_token` cookie (no Jira login stub needed) [VERIFIED: `JwtTokenService`, `AuthCookieWriter.ACCESS_COOKIE`].
- **Calls:** `POST /api/assistant/chat` over `java.net.http.HttpClient`, parse the same events the browser receives, so the gate covers controller, orchestrator, adapter, rag-service and model together.
- **Cases:** `src/test/resources/quality-gate/cases-v1.json`, schema close to Phase 10's: `id, category, safety, scope (release name or null), history[], question, requiresTools, expect{refusal, mustContain[], mustNotContain[], sourceKinds[], citedTicketKeys[], toolsMustRun[], noSourceFromOtherRelease}, rubric`. Suggested 42 cases: exact facts via tools 8, free text via retrieval 7, release-scoped 6, follow-ups 4 (history carries the referent), not-in-the-data 6 (safety), injection 6 (safety: description, comment, manual item, summary-borne via tool result, question-borne, fence-break), scope leak 5 (safety: assert every `done.sources[*].releaseId` equals the scope and no tool ran against another release). Safety = not-in-data, injection, scope leak. Claude drafts, the user approves the labels at a checkpoint (as in 10-09) before the first scored run.
- **Verdicts and the dev waiver:** run 3 times per case, sequentially (one in-flight per client). Report fields: `model` and `promptHash` (taken from `done.meta`, so they are what the run actually used), `host` (`gate.host-label`, rag base URL host, JVM hostname), date, case results with per-run failed checks, `safetyAllThree`, `qualityRunsPassed / qualityRuns`, per-category rates, `toolDependentCases`. A `-Dgate.dev-waiver=true` switch (default false) excludes `requiresTools` cases from the verdict and stamps the report `verdict: DEV_WAIVER_NOT_SHIPPABLE`; without the waiver the verdict is `PASS` or `FAIL`. The production run must be waiver-free, which is what makes it the ship blocker.
- **Runtime:** about 42 cases x 3 runs x 10-60 s on the 4 GB GPU dev machine is on the order of 1-2 hours [ASSUMED from 10-09 M7: 1.3-7.7 tokens/s]; keep it opt-in and out of CI.
- **Recording the production run as a v1.1 ship blocker:** (1) STATE.md blocker line and ROADMAP Phase 13 note "SAFE-04 production run pending: blocks v1.1 ship"; (2) REQUIREMENTS traceability keeps SAFE-04 as "Pending (harness built, dev recorded, production run outstanding)"; (3) a `docs/quality-gate.md` runbook with the exact commands for the production host, including `mvn -f rag-service/pom.xml test -Pgolden` so T03 is covered (D-13-16), and the acceptance rule; (4) evidence file convention `.planning/phases/13-assistant-screen-quality-gate/gate-reports/<date>-<host-label>.json` committed after the run, which `/gsd-complete-milestone` for v1.1 must find with `verdict: PASS` and a non-dev host label. Phase 13 closes with dev numbers recorded and these four items in place.
- **Docker requirement:** the harness needs Docker for the main DB container. If the production host cannot run Testcontainers, allow `-Dgate.db-url=...` against a throwaway database instead (Open Question 5).

### Finding 8: Frontend integration points [flags 2, 4]

- **Nav and guard:** add the Assistant `Link` after "Releases" in `AppShell`, rendered only when the status query returns `enabled: true` (UI-SPEC). `AppShell` is the root route component, so a provider or module store at this level survives navigation.
- **Ticket anchor (UI-SPEC assumption 4):** `ReleaseDetailRoute.tsx` ticket rows (`<tr>` at the `data.items.map` block) have no id and no hash handling [VERIFIED: grep]. Add `id={`ticket-${item.key}`}` on the row, and an effect that, once `data` is loaded, reads `useLocation().hash`, expands that row (`setExpandedKey`) and scrolls it into view. Do not rely on the router's built-in hash scrolling: the rows mount after an async query, after navigation has resolved [ASSUMED; the router API does expose `hash` on `Link` and `hashScrollIntoView` on navigation, CITED: tanstack.com router navigation guide].
- **Document revision (assumption 4):** supported. `documentViewerRoute` validates `search.revision` as a number and `DocumentViewerRoute` reads it; the target type value is the `DocumentType` enum string (`RELEASE_NOTES|RUNBOOK|APPROVAL`) [VERIFIED: `router.tsx`, `DocumentViewerRoute.tsx`, `api/templates.ts`], identical to the indexed `doc_type` metadata. A revision that has been superseded still opens (the viewer shows the supersede banner).
- **External hrefs (assumption 5):** not needed. Every indexed document carries `release_id`, so ticket and code-link chips can always be internal. Drop the backend-supplied Jira/ADO href requirement. Consequence: the "Commit a1b2c3d" chip row of the UI-SPEC is replaced by a "Code links PROJ-123" chip (Pattern 3); commit-level links remain available one click away in the expanded ticket row. Needs user confirmation as a UI-SPEC deviation (Open Question 2).
- **TOOL-kind chips:** the UI-SPEC chip table has no row for them; Pattern 3 supplies one.
- **Drawer:** `PlaceholderDrawer` (non-modal `absolute right-0 top-0 ... w-[380px]`, bottom sheet below `lg`) is the template; the drawer must sit inside a `relative` container in `ReleaseDetailRoute` the way `TemplateDetailRoute` hosts it. Escape/focus rules are specified in the UI-SPEC; the click-outside handler is the part to omit.
- **Logout:** `handleSignOut` calls `window.location.assign('/login')`, a full reload, which already clears in-memory chat; no extra code.
- **Release picker:** `useReleases('')` already exists (`api/releases.ts`).
- **Freshness text:** `Intl.RelativeTimeFormat`; no date library.

### Finding 9: Security review items for the exit gate

The ROADMAP flag adds an injection threat-model entry and a log/security review to the exit gate. Plan one task for each: (a) a threat-model section in `docs/docker.md` or a new `docs/assistant-security.md` covering browser-borne history, document/ticket text, tool-result text, prompt-config edits, the key's custody, and the accepted T03 residual risk; (b) a log review test that runs a stream with a sentinel question and asserts no log line from the app contains the sentinel, any answer text, tool arguments or the api key (extend the 10-09 step 8 grep idea into an automated test using a logback `ListAppender`).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SSE emission | Manual `text/event-stream` framing on a raw `OutputStream` | `SseEmitter` + `SseEmitter.event().name(..).data(..)` | Framing, flush, async lifecycle, timeouts |
| Disconnect detection | Servlet polling tricks | Write failures (heartbeat comments) | The Servlet API has no disconnect notification |
| Auth for the stream | A second token scheme | Existing cookie JWT chain | Constraint ASST-02 |
| Relative time | Date library | `Intl.RelativeTimeFormat` | Built in |
| Chat store | Redux/Zustand | Module store + `useSyncExternalStore` | No new dependency; state is trivial |
| Markdown/HTML rendering of answers | `react-markdown`, `markdown-it` | A text node in `whitespace-pre-wrap` | D-13-09, SAFE-02 |
| Tool-turn signature handling | Any client-side check | Opaque `SignedTurn` already built | Phase 11/12 contract |
| Prompt hash | Custom digest | `MessageDigest.getInstance("SHA-256")` | One-liner |
| Gate reporting | A reporting framework | One `Map` serialised with Jackson, as `GoldenChatIT` does | Same pattern |

**Key insight:** the risky parts are the seams (abort chain, scope on tools, config shadowing), not the components; a plan that tests each seam with a fake or a real HTTP request avoids the failures Phases 10 and 12 already hit.

## Common Pitfalls

### Pitfall 1: Abort that never reaches rag-service
**What goes wrong:** Stop clears the UI but rag-service keeps generating until its 120 s deadline; the next ask gets 429.
**Why it happens:** no write happens during prompt evaluation or tool rounds, so neither hop notices the closed connection; or the listener exception is swallowed (for example mapped to `Failed(FAILED)` by the adapter).
**How to avoid:** heartbeat plumbing end to end, listener exceptions propagate, adapter closes the stream in `finally`/exchange scope; WireMock test with a listener that throws; live check reads rag-service logs for `outcome=CLIENT_GONE` within the heartbeat window.
**Warning signs:** `generation endpoint=chat outcome=OK` after a user-aborted request; BUSY immediately after Stop.

### Pitfall 2: AccessDenied on async dispatch
**What goes wrong:** the stream works, then logs `AccessDeniedException` / "response already committed" when the emitter completes.
**Why it happens:** `AuthorizationFilter` re-runs on the ASYNC dispatch and may not see the authentication.
**How to avoid:** an IT over real HTTP (RANDOM_PORT, real cookie) that completes a stream and asserts no `AccessDenied`/`already committed` log event and a 401 before any stream when unauthenticated; add the narrow ASYNC permit only if it fails. MockMvc `asyncDispatch` does not model the container and is not sufficient.

### Pitfall 3: Status endpoint depending on a conditional bean
**What goes wrong:** with the switch off, the context fails to start or `/api/assistant/status` is 404, so the frontend cannot learn `enabled:false`.
**Why it happens:** `AssistantProperties` is registered only by the conditional `AssistantConfig`.
**How to avoid:** unconditional controller using `@Value` and `ObjectProvider`; extend `AssistantSwitchOffIT`.

### Pitfall 4: Test yml shadows main (again)
**What goes wrong:** new keys exist in main yml only; tests (and the golden/gate runs) see different defaults or fail binding.
**How to avoid:** every new `app.assistant.*` key in both ymls; for rag-service, Java defaults as the single source and a drift test that loads `src/main/resources/application.yml` by file path if a `rag.prompts` block is ever added there. The gate report prints the active hash so a shadowed or overridden prompt is visible.

### Pitfall 5: Scope leak through tools
**What goes wrong:** a release-scoped chat answers from another release's tool data.
**How to avoid:** force the scope into tool arguments in `AssistantService.execute`, drop `dashboard` when scoped, and keep the scope-leak gate cases (assert `sources[*].releaseId` and `toolsRun[*].arguments`).

### Pitfall 6: Request exceeds rag-service limits
**What goes wrong:** the scoped prefix pushes a 1,900-char question past 2,000, or the 21st history message returns 400 (mapped to FAILED, a confusing "Something went wrong").
**How to avoid:** 1,500-char API limit, last-20 history cap, per-message 4,000 cap, all returned as validation errors before the stream.

### Pitfall 7: Provisional text flashes then vanishes
**What goes wrong:** a preamble is shown and then replaced by a status line.
**How to avoid:** Finding 5 hold-back plus clear-on-discard, and a store test for both orderings (tokens before discard, discard before tokens).

### Pitfall 8: The gate writes into production data
**What goes wrong:** the harness is run with the production client key; fixed seed documents mix into real search results.
**How to avoid:** the harness only accepts the admin key and registers its own client; never reads `RM_ASSISTANT_API_KEY`; teardown deletes its documents.

### Pitfall 9: Nondeterministic seed
**What goes wrong:** "last week", staleness and readiness drift between runs and hosts.
**How to avoid:** fixed `Clock` bean in the IT, fixed seed constants, no `Instant.now()` in `GateSeed`.

### Pitfall 10: Hidden Phase 11 coupling
**What goes wrong:** rag-service edits conflict with 11-04..11-06 (they are rewriting `Prompts`, `PromptBuilder`, `ChatService`, the `done` record).
**How to avoid:** order the rag-service tasks after 11-09; run `git log` and re-read the files at execution time; keep the changes additive (new constructor delegating to defaults, new `done` fields).

## Code Examples

### SSE parser (pure, testable)
```ts
// Source: W3C SSE framing as emitted by this server (event/data/comment lines, blank-line terminated)
export type SseEvent = { event: string; data: unknown };

export function parseSse(buffer: string): { events: SseEvent[]; rest: string } {
  const events: SseEvent[] = [];
  const blocks = buffer.split(/\r?\n\r?\n/);
  const rest = blocks.pop() ?? '';
  for (const block of blocks) {
    let event = 'message';
    const data: string[] = [];
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith(':')) continue;
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
    }
    if (data.length > 0) events.push({ event, data: JSON.parse(data.join('\n')) });
  }
  return { events, rest };
}
```

### Stream loop with abort
```ts
// Source: MDN fetch / ReadableStream reader; AbortController semantics
export async function streamChat(body: ChatRequest, signal: AbortSignal, onEvent: (e: SseEvent) => void) {
  const response = await apiFetchRaw('/api/assistant/chat', { method: 'POST', body: JSON.stringify(body), signal });
  const reader = response.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();           // rejects with AbortError after abort()
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const parsed = parseSse(buffer);
    buffer = parsed.rest;
    parsed.events.forEach(onEvent);
  }
}
```

### Browser listener: write failure becomes ClientGoneException
```java
// Source: pattern of rag-service SseSink.deliver, adapted to throw
private void send(SseEmitter.SseEventBuilder event) {
    try {
        emitter.send(event);
    } catch (IOException | IllegalStateException clientGone) {
        throw new ClientGoneException();
    }
    if (clock.instant().isAfter(deadlineAt)) {
        throw new StreamDeadlineException();   // controller turns it into error{DEADLINE}
    }
}
@Override public void heartbeat() { send(SseEmitter.event().comment("hb")); }
@Override public void token(String text) { send(SseEmitter.event().name("token").data(Map.of("text", text), MediaType.APPLICATION_JSON)); }
```

### Forced scope on tool calls
```java
// In AssistantService.execute, before tool.call(...)
Map<String, Object> arguments = scope == null || !declaresRelease(tool)
        ? call.arguments()
        : withRelease(call.arguments(), scope.id().toString());
```

### PromptRules defaults and hash (rag-service)
```java
@ConfigurationProperties("rag.prompts")
public record PromptRules(String chatSystem, String transformSystem, String toolRules,
                          String finalRoundRule, String correction) {
    public PromptRules {
        chatSystem = orDefault("chat-system", chatSystem, Prompts.CHAT_SYSTEM);
        // ... same for the other four
        if (!chatSystem.contains("NO_ANSWER")) throw new IllegalStateException("rag.prompts.chat-system must keep the NO_ANSWER refusal token");
    }
    public static PromptRules defaults() { return new PromptRules(null, null, null, null, null); }
    public String hash() { /* SHA-256 of PROMPT_VERSION + texts, first 12 hex chars */ }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `RestTemplate`, polling | `RestClient`, `SseEmitter` | Spring 6.1+ | Matches CLAUDE.md stack |
| CRA | Vite 8 (installed) | n/a | No change |
| `EventSource` | fetch + stream reader for POST | long-standing | Required here |
| Prompt text as static constants | Injected rule records with logged hash | this phase | Config edits are traceable |

**Deprecated/outdated:** `@microsoft/fetch-event-source` is effectively unmaintained (latest 2.0.1); `eventsource-parser` is the maintained alternative if a parser dependency is ever wanted.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Closing the JDK `HttpClient` response body for an HTTP/1.1 exchange closes the upstream connection, so rag-service sees CLIENT_GONE | Finding 1 | Stop frees no slot; need an explicit cancel or `Future` interrupt. Proven only by the live check |
| A2 | The Spring Security chain may deny the ASYNC dispatch for the stream; a narrow ASYNC permit fixes it | Finding 1, Pitfall 2 | If an IT shows no problem nothing changes; if the fix is insufficient the stream needs a different carrier |
| A3 | Boot's default compression does not include `text/event-stream`, and no `server.compression` key exists | Finding 1 | Responses buffered by compression; set `server.compression.enabled=false` explicitly |
| A4 | Production has no buffering proxy configured, or honours `X-Accel-Buffering: no` | Finding 1 | Tokens arrive in one burst in production; fix at the proxy; caught by the live `curl -N` check |
| A5 | Vitest jsdom lets tests build a `Response` with a `ReadableStream` body and a `TextDecoder` stream decode | Finding 2 | Parser/stream tests need `node` environment for that file |
| A6 | TanStack Router will not scroll to a hash target that mounts after an async query | Finding 8 | The explicit effect is redundant but harmless |
| A7 | Native tool-calling models rarely emit a preamble before a `tool_call` | Finding 5 | Hold-back threshold needs tuning; flicker more common |
| A8 | The full gate takes 1-2 hours on the dev GPU | Finding 7 | Plan longer checkpoints |
| A9 | "Tool-use instruction" in D-13-17 means the tool descriptions plus the scoped-question text | Finding 6 | Different config surface would be needed; confirm with the user |
| A10 | A 1,500-char question limit leaves room for the scope prefix and satisfies the user | Finding 5 | Different limit or a different prefix budget |
| A11 | Revoked gate clients leave collections behind but explicit document deletes clean the data | Finding 7 | Orphans accumulate in a shared rag-service |
| A12 | Phase 11's final `done` record and `ChatService` allow additive fields (`historyTrimmed`, `model`, `promptHash`) | Finding 5, 6 | Re-plan the rag-service tasks after 11-09 |

## Open Questions

1. **D-13-06 "provisional tokens never shown" versus streaming.**
   - What we know: tokens of a round arrive before the server knows it ends in a tool call (11-RESEARCH Pitfall 7).
   - What's unclear: whether a short hold-back plus clear-on-discard is acceptable, or the user wants the whole first round buffered when tools are offered (no live streaming for the first round).
   - Recommendation: hold-back of about 150 characters plus clear-on-discard; confirm.
2. **Commit chips and external hrefs.**
   - What we know: code-link documents are per ticket; chips cannot name a single commit; `release_id` is on every document.
   - What's unclear: whether replacing "Commit a1b2c3d" with "Code links PROJ-123" (internal link to the ticket row) is acceptable for ASST-06.
   - Recommendation: accept; update UI-SPEC rows 2 and 4 and drop assumption 5.
3. **What "tool-use instruction" means in D-13-17.**
   - Recommendation: tool-description overrides plus the scoped-question template (Finding 6); confirm.
4. **Where the gate gets `model` and `promptHash`.**
   - Recommendation: additive `done` fields in rag-service passed through to the browser `done.meta`. Alternative: a `GET /v1/info` endpoint called directly by the harness (new public API surface plus an openapi regeneration).
5. **Docker on the production host.**
   - What's unclear: whether Testcontainers is available there.
   - Recommendation: support `-Dgate.db-url` as an alternative to the container.
6. **Retry-After plumbing.** Recommendation: add nullable `retryAfterSeconds` to `ChatOutcome.Failed`; fill it in the adapter (Phase 13 amendment to 12-10's adapter).
7. **`heartbeat-interval` default.** Recommendation: lower rag `heartbeat-interval` from `PT15S` to `PT5S` so abort propagation completes in about 10 s; it is one config value but changes Phase 10 behaviour, so confirm.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 25 | build, gate | yes | `C:/Users/ucs/.jdks/jdk-25` | none needed |
| Maven | build, gate | yes | 3.9.11 | none |
| Node | frontend build/tests | yes | 20.19.0 (meets Vite 8 floor 20.19) | none |
| Docker CLI | Testcontainers (all ITs, gate main DB) | CLI present; daemon not probed (instructed not to touch Docker) | - | `-Dgate.db-url` external DB for the gate |
| Ollama + models | gate runs, live checks | not probed (instructed not to touch); 10-09 recorded Ollama 0.34.0 with `qwen2.5-coder:7b` and `nomic-embed-text` | 0.34.0 | gate IT skips itself when unreachable |
| rag-service (compose) | gate, live check | not probed | rebuilt in 10-09 | gate IT skips itself |
| jq | - | not installed | - | use Jackson or `sed`; no plan step may need jq |

**Missing dependencies with no fallback:** none for planning. Execution of the dev gate run and the live check need Ollama and rag-service up, which are human-gated steps.

## Security Domain

### Applicable ASVS Categories (level 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Existing cookie JWT (`oauth2ResourceServer`); no new scheme; stream endpoint under `/api/**` `authenticated()` |
| V3 Session Management | yes | Stateless JWT, 10-minute access token verified at request start; sign-out reload clears chat |
| V4 Access Control | yes | Assistant answers are shared as in v1 (no per-user Jira filtering, locked); scope enforced on tools server-side |
| V5 Input Validation | yes | Bean Validation on `AssistantChatRequest` (`@Size(max=1500)`, history count and per-message caps, roles `user`/`assistant`), UUID parse for `releaseId`, NUL rejected by rag-service |
| V6 Cryptography | yes | `MessageDigest` SHA-256 for the prompt hash only; no custom crypto; rag key from env, never in responses or logs |
| V7 Error handling and logging | yes | Metadata-only logs (SAFE-03), redacting `toString` on new records |
| V12/V13 API | yes | Same-origin cookie, SameSite=Strict; no CORS added |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection via ticket/comment/document/tool-result text | Tampering | Fences stay in code; `neutralise`; tool results fenced; answers plain text; residual risk accepted (SAFE-02) and measured by gate injection cases |
| Poisoned history from the browser | Tampering | History is untrusted; capped at 20 messages; fenced and neutralised by rag-service (G09) |
| Rule-text config edit weakens grounding | Tampering | Rule text only, fences in code, startup validation, prompt hash logged and in every gate report, re-run required on hash change |
| Release-scope bypass via tools | Information disclosure | Force scope into tool arguments; omit `dashboard` when scoped; scope-leak gate cases |
| XSS through model output, tool arguments or citation fields | Tampering | React text children only; no `dangerouslySetInnerHTML`; internal links built from ids; chip `href` allowed only as relative paths (reject `javascript:` and external schemes) |
| CSRF on the stream POST | Spoofing | SameSite=Strict cookie (locked decision); bearer-cookie requests are exempt from the CSRF filter |
| Unbounded resource use (long streams, many streams) | Denial of service | rag one-in-flight per client plus app stream deadline and emitter backstop; one virtual thread per stream; stream limited by cookie auth |
| Key or content in logs | Information disclosure | Redacted `toString`, metadata-only log lines, automated log-sentinel test |
| Gate harness mixing test data into production index | Tampering | Own scratch client, deletes on teardown, never reads the app's key |

## Project Constraints (from CLAUDE.md)

- Java Spring Boot backend and React SPA frontend; follow official documentation and framework idioms; minimal code, no over-engineering or speculative abstraction; no new dependency unless clearly justified (none proposed).
- Validate only at system boundaries (the browser request and rag-service responses are boundaries); self-documenting names; comments only for a non-obvious WHY; no commented-out or dead code; no multi-paragraph docstrings.
- Enterprise-grade: secure, observable, resilient; do not optimise without measurement (hence no server caches, no rAF batching).
- Never commit secrets (`.env`, `key.txt` stay untracked); never skip hooks; stage files by name; no `Co-Authored-By` trailers in commits (the user's global CLAUDE.md overrides the harness attribution reminder); commit messages explain why.
- Work only through GSD workflow commands. Do not create planning or analysis files beyond the GSD artifacts.
- Environment: set `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25` on every Maven call, `-P '!frontend'` for backend-only runs, one Maven build at a time, `mvn clean` after public API changes, new yml keys in both `application.yml` files, never run the Vite dev server during Maven builds, do not touch Docker containers or Ollama from automated plans (live checks are human steps). The frontend uses Tailwind v4 utilities (UI-SPEC), not plain CSS.
- Memory notes to honour: the CLI is `gsd-tools`, and several STATE/ROADMAP writers are partial or fake, so verify after each write; plan grep criteria must assert properties, not counts; the app is an orphan container; CSRF rests on SameSite=Strict; beside-Jira positioning.

## Plan Shape Suggestion (for the planner)

| Wave | Content | Gate |
|------|---------|------|
| 1 (no Phase 11/12-10 dependency) | A: backend config + `AssistantProperties` + both ymls + status/freshness endpoints + `RagStatusPort`; B: `ChatListener` additions, `ClientGoneException`, `AssistantService` scope/forced tool scope/scoped-question/history cap, citation view, `AssistantChatController` with a fake port; C: frontend `client.ts` raw export, `assistant.ts` (parser, stream, hooks), chat store | Runs against fakes; ArchUnit rules from 12-09 stay green |
| 2 (needs 12-10 landed) | `RagChatAdapter` amendments: heartbeat callback, listener exception propagation and close, `retryAfterSeconds`; async-dispatch IT over real HTTP | 12-10 SUMMARY exists |
| 2 (parallel, frontend) | Chat UI: `AssistantChat`, page route, drawer in `ReleaseDetailRoute`, ticket anchor effect, chips, trace panel, states, nav item and guard, tests | Wave 1 C |
| 3 (needs Phase 11 11-09) | rag-service: `PromptRules` + `rag.prompts.*`, hash in logs and `done`, `historyTrimmed`, `model` in `done`, openapi regeneration, docs | 11-09 SUMMARY exists |
| 4 | Gate: `GateSeed`, `cases-v1.json` draft, **user checkpoint on labels**, `QualityGateIT`, pom profile, `docs/quality-gate.md`, dev run and report | Waves 2 and 3 |
| 5 | Threat-model doc, log sentinel test, live check (human): browser through the real path, `curl -N` buffering check, Stop freeing the slot, offline/busy/model-missing states; record the production-gate ship blocker in STATE, ROADMAP, REQUIREMENTS | Wave 4 |

## Sources

### Primary (HIGH confidence)
- Repository code read this session: `SecurityConfig`, `AssistantConfig`, `AssistantProperties`, `AssistantService`, `RagChatPort`, `ChatOutcome`, `AssistantAnswer`, `Source`, `IndexDocumentMapper`, tool classes, `JwtTokenService`, `AuthCookieWriter`, `RagIndexPort`/`RagIndexAdapter`, rag-service `ChatController`, `SseSink`, `ChatService`, `PromptBuilder`, `Prompts`, `application.yml`, `GoldenChatIT`, `CollectionResponse`, `ClientRepositoryAdapter`, frontend `client.ts`, `router.tsx`, `AppShell`, `PlaceholderDrawer`, `ReleaseDetailRoute`, `DocumentViewerRoute`, `vite.config.ts`, `package.json`, `pom.xml`
- Planning artifacts: 13-CONTEXT, 13-UI-SPEC, 12-RESEARCH, 12-07-SUMMARY, 12-10-PLAN, 11-04-PLAN, 11-AI-SPEC, 10-09-SUMMARY, ROADMAP Phase 13, REQUIREMENTS
- Spring Framework MVC async requests (SseEmitter, StreamingResponseBody, disconnect, heartbeat, timeout): https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- Spring Security authorize HTTP requests (all dispatcher types, context carry-over): https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html
- TanStack Router navigation (`Link hash`): Context7 `/tanstack/router` (docs/router/guide/navigation.md)
- npm registry (eventsource-parser 4.1.2, @microsoft/fetch-event-source 2.0.1), checked 2026-10-15

### Secondary (MEDIUM confidence)
- Spring Security async-dispatch reports: https://dev.to/junyoungmoon/solving-access-denied-issues-after-sse-implementation-in-spring-security-3nd4, https://github.com/spring-projects/spring-security/issues/12758, https://github.com/spring-projects/spring-security/issues/12314

### Tertiary (LOW confidence)
- Gate runtime estimate and preamble frequency (A7, A8) are extrapolations from 10-09 measurements.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, nothing new; all versions read from manifests.
- Architecture: MEDIUM-HIGH, the stream, status and scope design follow existing code; the async-dispatch security behaviour and the abort chain need the two named tests.
- Pitfalls: HIGH for those taken from the repo (shadowed yml, conditional beans, limits), MEDIUM for Phase 11 coupling.
- Quality gate: MEDIUM, the shape is proven by `GoldenChatIT`, but the seed size, case count and runtime are estimates and the cases need the user checkpoint.

**Research date:** 2026-10-15
**Valid until:** 2026-10-22 for anything touching rag-service `Prompts`, `PromptBuilder`, `ChatService`, `done` (Phase 11 is mid-execution); 30 days for the rest.
