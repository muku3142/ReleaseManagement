# Phase 11: Tool-Call Protocol - Research

**Researched:** 2026-10-14
**Domain:** Stateless tool-call protocol on the Spring Boot `rag-service` chat path (Ollama native tools, HMAC-signed tool turns)
**Confidence:** HIGH for the code mapping and wire shapes (read from the repo and probed live), MEDIUM for model behaviour (qwen3:4b reasoning length with real prompts is unmeasured)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-11-01 Fully stateless resume: after a `tool_call` event the stream ends; the app POSTs `/v1/chat` again with the original question, history, the model's tool-call turn and the tool results. No session state, no continuation token.
- D-11-02 The tool-call turn is signed: the `tool_call` event carries an HMAC over the calls (id, name, arguments) keyed by a service secret; the app returns the turn unchanged; a missing, tampered or invented turn is 400. New config secret; rotating invalidates only in-flight rounds.
- D-11-03 Several calls per round; the app runs all and returns every result together.
- D-11-04 Pre-retrieval stays; caller tools added on top; no built-in search tool.
- D-11-05 Tool results win conflicts (number, date, status); prompt says so; citations show both sources.
- D-11-06 No score-gate refusal when tools are present (gate skips the model only for requests without tools). Model-level `NO_ANSWER` still applies after tools run.
- D-11-07 At most 4 tool rounds per question, configurable; round count travels in the signed turn; at the cap the service tells the model to answer with what it has or say it cannot; never a fifth round. A cap on calls per round also applies (about 4).
- D-11-08 The deadline applies per request (120 s per POST); the app's tool time is not counted.
- D-11-09 Tool failures are results (`{"error": "..."}`); the model must not invent the value. A malformed model call (unknown tool, arguments failing the caller's schema) is retried once with a correction message, then ends in an honest failure.
- D-11-10 Tool results must fit the context budget, 413 beyond (per-result cap about 8,000 chars configurable, plus overall budget check), never silent truncation.
- D-11-11 Compare only installed models: `qwen3:4b` (2507 thinking build) and `qwen2.5-coder:7b`, golden tool questions x 3 runs, record pass rate, tokens/s and time to first token. Report honestly even if neither passes.
- D-11-12 The Phase 10 model decision comes first; Phase 11 then confirms or replaces it with measured data.
- Carried from Phase 9/10: standalone domain-free service, per-client API keys, Ollama the only outbound call, no AI framework (hand-rolled `RestClient` adapter behind `ChatPort`), stateless chat (D-10-06), caller-owned instructions (D-10-09), per-client in-flight limit with 429 (D-10-01), fail-fast 503 (D-10-04), indexing pauses during generation (D-10-03), plain text answers, untrusted text fenced, metadata-only logs, citations built by the service (RAG-07). Phase 10 behaviour for requests without tools must not change.

### Claude's Discretion
- Tool definition format and supported JSON Schema subset, max tools per request, max calls per round.
- `tool_call` event payload shape and the request shape for returning results.
- HMAC algorithm/encoding, secret config key, how round count and original question are bound into the signature.
- How tool-result sources are supplied and rendered as citations.
- Prompt changes (bump `PROMPT_VERSION`) and the golden tool fixture.

### Deferred Ideas (OUT OF SCOPE)
- Built-in `search_knowledge` tool instead of pre-retrieval.
- Continuation-token resume.
- Whole-question deadline carried across rounds.
- Comparing larger or newly pulled models (8B, `qwen3:4b-instruct-2507`).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| RAG-08 | An app can supply tool definitions; the service returns tool calls, the app runs them and returns results, and the service answers with them; the service never calls into apps | Signed stateless turn (Architecture), adapter wire contract, ArchitectureBoundaryTest rules, plan split below |
</phase_requirements>

## Summary

`11-AI-SPEC.md` is the baseline and is not re-derived. Its wire contract for Ollama 0.34.0 still holds: I re-probed three points live on 2026-10-14 (no pulls, no config change). (1) `qwen2.5-coder:7b` accepts `think:false` (200), so Phase 10's always-`think:false` request is safe for both models. (2) With tools sent, coder still prints the call as bare JSON in `message.content` even with `think:false`. (3) Replaying `assistant{tool_calls}` + `tool{tool_name,content}` with **no `tools` field** (the round-cap path) returns 200 and a correct answer, so "omit tools at the cap" works [VERIFIED: live Ollama 0.34.0, qwen2.5-coder:7b; not re-run on qwen3:4b to avoid a model swap, so qwen3 at the cap is [ASSUMED] from the shared Qwen template].

The code mapping is small if the tool logic lives in new classes. Phase 10's hot files (`Prompts`, `PromptBuilder`, `CitationBuilder`, `ChatService`, `GoldenChatIT`, `chat-v1.json`) can be touched by additive seams only: a new `chatWithTools` method, a `TOOL_RULES` constant appended to the system message (no edit of `CHAT_SYSTEM`), a separate `ToolProperties` record (four tests call `new ChatProperties(...)`, so adding fields there would break them), and a separate `GoldenToolsIT`. The plan split below puts everything independent of Phase 10 in waves 1-2 and the Phase-10-coupled files in their own plans.

Corrections to the AI-SPEC found while mapping (details in Pitfalls): the gate must be released before the `tool_call`/`done` events are sent (finally-release races an immediate resume into a spurious 429); canonical signing must not encode numbers as `"n:..."` strings (type confusion); the golden command is `mvn test -Pgolden` (no failsafe), not `verify`; the textual fallback should not be built.

**Primary recommendation:** Build the protocol as new domain/application classes plus additive seams; do not build the qwen2.5-coder textual fallback (report config B as a finding); release the generation gate before emitting `tool_call`; execute waves 1-2 (config, domain, signer, adapter) before Phase 10 closes and hold the prompt, `ChatService` and golden plans until it does.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Tool definition intake and schema subset | API / Backend (`domain`, `adapters.web` DTO) | - | Bounded validation in the style of `MetadataRules`; web only maps and applies Bean Validation |
| Signing / verifying the tool turn | API / Backend (`application`) | - | Stateless trust anchor; JDK `Mac`, no adapter involvement |
| Ollama tools wire shapes, think-by-capability | Adapter (`adapters.ollama`) | - | Ollama shapes must not leak through `ChatPort` |
| Round / call caps, correction retry | API / Backend (`application`) | - | Caps in code, not prompt |
| Fencing tool results, token budget | API / Backend (`application.PromptBuilder`) | - | Reuses nonce fence and estimator |
| Executing tools, holding app data | External app (Phase 12) | - | RAG-08: service never calls into an app |
| `tool_call` SSE event | Adapter (`adapters.web`) | `domain.StreamEvents` | Event records live in domain, serialisation by `SseSink` |
| Tool citations | API / Backend (`application.CitationBuilder`) | - | Built from the service's own list (RAG-07) |

## Project Constraints (from CLAUDE.md)

- Minimal code, no over-engineering or abstraction; follow official docs; no new dependencies unless justified (none needed here: `javax.crypto.Mac`, `MessageDigest.isEqual`, Jackson 3, JUnit params are already present).
- Enterprise-grade: secure, observable; validate only at system boundaries; no commented-out code; comments only for non-obvious WHY.
- Security: never log secrets; no `--no-verify`; stage files by name; no `Co-Authored-By` trailer in commits (project rule overrides the harness attribution reminder).
- Work only through GSD workflow commands.
- Environment: `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25` per Maven call; one Maven build at a time; do not pull models, change OLLAMA_* env, restart Ollama or touch docker.

## Standard Stack

No new dependency. All versions are the repo's (Boot 4.1.0, Spring Framework 7, Java 25, Jackson 3 `tools.jackson`, annotations in `com.fasterxml.jackson.annotation`) [VERIFIED: rag-service/pom.xml, existing imports].

| Need | Use | Notes |
|------|-----|-------|
| HMAC | `javax.crypto.Mac` `HmacSHA256`, `MessageDigest.isEqual` | Key = UTF-8 bytes of `RAG_TOOL_SIGNING_SECRET`, min 32 chars, same convention as `RAG_ADMIN_KEY` (`ApiKeyFilter` ctor) [VERIFIED: ApiKeyFilter.java:45] |
| Canonical JSON | Jackson `ObjectMapper` over `TreeMap`-normalised maps | see Pitfall 2 for the number encoding fix |
| Validation | `ToolSchema` in `domain`, style of `MetadataRules` | no JSON Schema library |
| Parameterised tests | JUnit Jupiter params (tamper table) | already in the Jupiter aggregate [ASSUMED: confirm `junit-jupiter-params` resolves; it ships in `junit-jupiter`] |

## Architecture Patterns

### System Architecture Diagram

```
App -- POST /v1/chat {collections, question, history, tools?, toolTurns?} --> ApiKeyFilter -> ChatController (@Valid ChatRequest)
   -> ChatService.prepare: 404 collections, 400 question/history, 400 tool intake (ToolSchema),
        400 turn chain (ToolTurnSigner.verify, pairing), 413 budget, 503 assertReady (+tools capability), 429 gate
   -> run: embed question -> search (Phase 10, re-run every resume)
        no tools: score gate (unchanged)   |   tools: gate skipped (D-11-06)
      -> PromptBuilder.chatWithTools(sources, history, question, tools, toolTurns, roundsUsed)
           [system][history][user: SOURCES+QUESTION][assistant tool_calls][tool: fenced result]...
           roundsUsed == maxRounds -> tools omitted + "answer now" rule
      -> ChatPort.stream (Ollama /api/chat, think by capability) 
           |-- calls empty: RefusalGuard -> CitationBuilder (PASSAGE + TOOL) -> citations, done
           |-- calls present: ToolPolicy.check (names, schema, count, duplicates)
                  ok  -> sign -> release gate -> SSE tool_call{round, questionId, calls, signature} -> done{finishReason:tool_calls}
                  bad -> one correction retry (not a round) -> still bad -> error TOOL_CALL_INVALID
App runs tools (own data, own credentials) -> POST again with the signed turn UNCHANGED + results
```

### Code mapping (what changes, what is new)

| File | Change | Phase-10-hot? |
|------|--------|---------------|
| `domain/ToolDefinition, ToolCall, ToolResult, ToolTurn` | NEW records | no |
| `domain/ToolSchema` | NEW: `requireSupportedSchema` (intake), `violations` (model args) | no |
| `domain/ToolTurnRejectedException, ToolResultTooLargeException` | NEW (400 `TOOL_TURN_INVALID`, 413 `TOOL_RESULT_TOO_LARGE`) | no |
| `domain/ErrorReason` | add `TOOL_CALL_INVALID` | tiny |
| `domain/StreamEvents` | add `TOOL_CALL` const, `ToolCallEvent`, `Done.toolCalls()` factory | tiny |
| `domain/ChatMessage` | add `Role.TOOL`, optional `toolCalls`, `toolName` via additive components + keep 2-arg constructor | tiny |
| `domain/Completion` | add `toolCalls` + keep 6-arg constructor (22 call sites of Completion/GenerationOptions/ChatMessage constructors exist) | tiny |
| `domain/GenerationOptions` | add `tools` (nullable list) + keep 3-arg constructor | tiny |
| `domain/Citation` | add `kind` (`PASSAGE` or `TOOL`) | hot (CitationBuilder tests) |
| `ports/ChatPort` | add `assertToolsReady()` (503 `MODEL_NOT_AVAILABLE` when capabilities lack `tools`); `stream` signature unchanged | no |
| `config/ToolProperties` | NEW `@ConfigurationProperties("rag.tools")`; register in `GenerationConfig` | no |
| `application/ToolTurnSigner`, `ToolPolicy` | NEW | no |
| `application/Prompts` | NEW constants `TOOL_RULES`, `TOOLS_PROMPT_VERSION`; do not edit `CHAT_SYSTEM` | hot |
| `application/PromptBuilder` | NEW method `chatWithTools`; extend `FORBIDDEN` for tool content only via a second array (do not change `neutralise` for no-tool requests) | hot |
| `application/CitationBuilder` | add overload taking tool sources | hot |
| `application/ChatService` | Command gains `tools`, `toolTurns`; seams in `prepare`/`run` (see Pitfall 1 for gate) | hot |
| `adapters/ollama/OllamaChatAdapter` | tools/`tool_calls`/`tool` wire records, capability cache from `assertReady`, `think:true` only for tool requests when `thinking` listed | maybe (think) |
| `adapters/web/dto/ChatRequest` + new tool DTOs | request shape, `toString` without content | no |
| `adapters/web/ChatController` | EVENTS description only | no |
| `adapters/web/GlobalExceptionHandler` | two handlers | no |
| yml x2 (+ test yml), `compose.app.yaml`, `.env.example`, `docs/docker.md` | `RAG_TOOL_SIGNING_SECRET` and `rag.tools.*` | no |
| `openapi.json` | regenerate in exactly one plan | - |
| `ArchitectureBoundaryTest` | add: no `java.net.http`/`RestClient` outside ollama/config already exists; add rule that `application` tool classes do not depend on adapters (already covered) and a rule that nothing in `domain`/`application` references an app URL type | small |
| tests: `FakeOllamaServer`, `FakeChatPort`, `GoldenToolsIT`, `golden/tools-v1.json` | extend / NEW | `GoldenChatIT` untouched |

### Config keys (separate record, prefix `rag.tools`)

`signing-secret` (`${RAG_TOOL_SIGNING_SECRET}`, required, >=32 chars, startup failure otherwise, same as admin key), `max-rounds: 4`, `max-calls: 4`, `max-tools: 5`, `definitions-max-chars: 1500`, `result-max-chars: 8000`, `num-predict: 768`. Test yml gets a fixed secret. Compose uses `${RAG_TOOL_SIGNING_SECRET:?...}` like `RAG_ADMIN_KEY` (decision: required, so there is no "tools disabled" state to code and test) [ASSUMED: user accepts a new mandatory env var; Phase 12 is the sole planned consumer].

### Request / event shapes (discretion, recommended)

- Request adds `tools: [{name, description, parameters}]` (<=5) and `toolTurns: [{round, questionId, calls:[{id,name,arguments}], signature, results:[{callId, content, isError, source?{id,title,url}}]}]` (<=4).
- Result carries only `callId`; the service derives the tool name from the signed call, never from caller data. Results may arrive in any order; the service re-sorts to call order (friendlier than the AI-SPEC's "in call order" rule, same safety).
- `tool_call` event: `{round, questionId, calls, signature}`. `questionId` = `requestId` of the round-1 POST, signed into every turn; it gives multi-POST log correlation without logging any content hash (SAFE-03 decision below).
- Tokens streamed before a `tool_call` event are provisional: document in the contract that the app discards them (non-thinking models may emit a preamble); replayed assistant content is `""`.
- `source.url` must match the same `https?://\S{1,2040}` pattern as documents (`PutDocumentRequest`) [VERIFIED: PutDocumentRequest.java:9]. Error results never produce a citation and their `source` is ignored.

## Settled AI-SPEC open points

1. **qwen2.5-coder textual fallback: do not build it.** (a) It adds a hold-back state machine on the token stream of every tool request plus a JSON-parse path in the hottest adapter code, for a coder model running about 42% on GPU. (b) D-11-11 asks for a measured comparison of the installed models; "never emits native `tool_calls`" is exactly such a finding (re-confirmed live today with `think:false`). (c) The production model follows Phase 10 and a native-tool model is the sensible target. (d) CLAUDE.md: no speculative code. Consequence: two bake-off configs (A qwen3 native, B coder strict); drop AI-SPEC config C, the `{`/`<` hold-back guardrail and the `callPath` metric. Raw call JSON reaching the user on config B is recorded as part of the finding. [ASSUMED: user accepts dropping config C]
2. **`think:true` on tool requests vs D-10 `think:false`: no conflict.** Requests without tools keep `think:false` (byte-identical body; accepted by both installed models, verified live). Tool requests send `think:true` only when `/api/show` capabilities contain `thinking`; the adapter caches capabilities in `assertReady()` (called in `prepare()` before every request). A non-thinking model (for example an instruct build chosen for Phase 10) never gets `think:true` (coder returns 400). `message.thinking` stays ignored (absent from `ChunkMsg`; also SAFE-03). Residual risk: reasoning tokens count against `num_predict` 768; AI-SPEC probes used trivial prompts, while the Phase 10 debug saw about 2,500 reasoning tokens on a transform. A `length` finish with empty content is `GENERATION_FAILED`; the bake-off reports the `length` count; keep `rag.tools.num-predict` configurable.
3. **Logging `questionHash` / `callNames` under SAFE-03.** Do not log a question hash (short, low-entropy questions are dictionary-matchable). Use `questionId` (random, equal to the round-1 `requestId`, signed into the turn) plus `round`. `callNames` are acceptable: caller-defined identifiers validated at intake to `[A-Za-z0-9_]{1,64}`; log only names matching a supplied definition and a count for unknown model-invented names. Never log arguments, results, source titles, signatures or the secret; extend `LogHygieneIT` canaries to all of these.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HMAC | custom hash/MAC | `javax.crypto.Mac` HmacSHA256 + `MessageDigest.isEqual` | constant-time, standard |
| JSON Schema validation | library or full validator | flat subset in `ToolSchema`; reject everything else at intake | an ignored constraint is a silent gap |
| NDJSON streaming | new parser | extend `readNdjson` | cancellation and error mapping already tested |
| Concurrency gate | new limiter | `GenerationGate` with a release-once guard | Pitfall 1 |
| Tool execution / MCP | any client into apps | nothing (RAG-08) | architecture test enforces |

## Common Pitfalls

### Pitfall 1: Gate released in `finally` races the app's immediate resume
The app receives `done` and POSTs the resume within milliseconds; the server thread may still be before `finally { gate.release }`, so with `max-in-flight-per-client: 1` the resume gets a spurious 429. `GenerationGate.release` is not idempotent (decrements per call), so a naive double release can free another request's slot [VERIFIED: GenerationGate.java]. **Avoid:** in the tool-call branch release the gate through a local `releaseOnce` before sending `tool_call` and `done`; `finally` calls the same `releaseOnce`. Test over real Tomcat: resume immediately after `done`, 20 repeats, never 429.

### Pitfall 2: Canonical form type confusion and non-finite numbers
AI-SPEC `canonical` maps numbers to the string `"n:1"`, so a caller can swap number `1` for string `"n:1"` with the signature still valid; a `Double` Infinity makes `new BigDecimal(n.toString())` throw (500). **Avoid:** keep numbers as `BigDecimal.stripTrailingZeros()` values written by Jackson as JSON numbers (injective encoding), catch `NumberFormatException` and reject with 400. Tests: `1.0` vs `1`, key reorder, `"n:1"` vs `1`.

### Pitfall 3: Signature scope
The MAC binds client, question hash, round, `questionId`, previous signature and calls. It does not bind history, tools or filter; replaying an old turn only reproduces the model's own earlier call, so no expiry is added (accepted, T-11-05). Do not add a tools hash.

### Pitfall 4: Template tokens and fences
Strip `<tool_response>`, `</tool_response>`, `<tool_call>`, `</tool_call>` from tool content and names via a second array used only by `chatWithTools`, so no-tool requests keep Phase 10 `neutralise` byte for byte. Tool name and `id=` come from service data; the source title never enters the prompt.

### Pitfall 5: Prefix cache, retrieval re-run, hardcoded budgets
Retrieval re-runs on each resume and passages shrink as the exchange grows; keep tool order and the system+tools prefix stable. Never hardcode token counts in tests: Phase 10 prompt hardening will change `CHAT_SYSTEM`.

### Pitfall 6: Golden command and model switching
There is no failsafe: use `mvn -f rag-service/pom.xml test -Pgolden -Dtest=GoldenToolsIT` (so the 51-minute Phase 10 golden is not repeated); the AI-SPEC `verify` is wrong [VERIFIED: pom has surefire only; Phase 10 C4]. The service model is set by `-Drag.chat.model=<tag>`; `GoldenChatIT` uses `rag.golden.model` only to probe, so `GoldenToolsIT` must read one property for both.

### Pitfall 7: Tokens before `tool_call`
Non-thinking models may emit text before calls; contract says the app discards tokens preceding a `tool_call` event. On the tool-call branch do not call `guard.finish()` (no flush of a held buffer).

### Pitfall 8: Citation shape
Adding `kind` to `Citation` changes the no-tools wire output additively. Recommended: always emit `kind` (`PASSAGE` or `TOOL`); consumers (Phase 12/13) do not exist yet. Flag in the plan so `CitationBuilderTest` and golden checks are updated knowingly.

## Code Examples

The validators and signer in `11-AI-SPEC.md` Section 4 are the reference. Changes required by this research:

```java
// canonical(): numbers stay numbers (injective); non-finite rejected
case Number n -> { try { yield new BigDecimal(n.toString()).stripTrailingZeros(); }
                   catch (NumberFormatException e) { throw new ToolTurnRejectedException(); } }

// ChatService.run, tool-call branch: free the slot before the app can resume
releaseOnce.run();   // idempotent wrapper over gate.release(clientId)
sink.send(StreamEvents.TOOL_CALL, new ToolCallEvent(round, questionId, calls, signature));
sink.send(StreamEvents.DONE, Done.toolCalls());
```

Scripted fake Ollama (`FakeOllamaServer` currently has one `chatLines` script and `lastChatBody`): add `chatScript(List<List<String>>)` (request i uses script[min(i,last)]), `chatBodies()` (every request body), `toolCallLine(id, name, argsJson)` emitting one call per line as live, and make `reset()` clear both. `show(...)` already allows adding a `thinking` capability.

## State of the Art

| Old | Current | Impact |
|-----|---------|--------|
| In-process tool loops (Spring AI, LangChain4j) | return-to-caller with signed transcript | hand-rolled |
| detect calls by `done_reason:"tool_calls"` | accumulate `message.tool_calls` across chunks; the done chunk has none | Ollama 0.34.0 live |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | User accepts dropping the textual fallback and AI-SPEC bake-off config C | Settled points | Fallback plan must be added (about 1 plan) |
| A2 | `RAG_TOOL_SIGNING_SECRET` may be a mandatory env var (compose `:?`) | Config keys | Need a disabled-tools state and a 503 path |
| A3 | qwen3:4b accepts replay with tool messages and no `tools` field (verified only on coder) | Summary | Cap path needs another encoding; found in the first live run |
| A4 | Always emitting `Citation.kind` is acceptable | Pitfall 8 | Emit `kind` only for TOOL |
| A5 | `junit-jupiter-params` is on the test classpath | Standard Stack | add test dependency (test scope only) |
| A6 | Reasoning under real SOURCES+tools prompts fits `num_predict` 768 on qwen3:4b | Settled point 2 | Config A fails on `length`; reported as a finding |

## Open Questions

1. **Which chat model does Phase 10 end with?** Undecided (STATE blocker). It changes only `think` behaviour (handled by capability) and the golden outcome; no Phase 11 code choice depends on it.
2. **Restrict source `url` beyond `https?://`?** Recommended no; Phase 13 renders links and must still escape.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 25 | build | yes | C:/Users/ucs/.jdks/jdk-25 | - |
| Ollama | live bake-off only | yes | 0.34.0 | hermetic tier needs none |
| qwen3:4b, qwen2.5-coder:7b, nomic-embed-text | live bake-off | yes (installed, no pulls) | - | - |
| Docker (Testcontainers Postgres) | ITs | not probed (docker must not be touched) | - | unit tests still run |

## Validation Architecture

> `workflow.nyquist_validation` is `false` in `.planning/config.json`; included because the orchestrator requested it.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit (Boot-managed), AssertJ, Mockito, WireMock 3.13.2, Testcontainers 2.0.5, ArchUnit |
| Config | `rag-service/pom.xml` (surefire only; the golden tag is excluded by default) |
| Quick run | `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25 mvn -B -f rag-service/pom.xml test -Dtest=Tool*Test,ChatServiceTest,PromptBuilderTest,CitationBuilderTest,OllamaChatAdapterTest` |
| Full suite | `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25 mvn -B -f rag-service/pom.xml test` (baseline 251 green; one Maven build at a time) |

### Behaviour to Test Map
| Behaviour | Type | Class | Wave |
|-----------|------|-------|------|
| Intake bounds, schema subset, violation texts name arguments only | unit | `ToolSchemaTest` | 1 |
| Signer tamper table (argument, call id, name, round, dropped or reordered turn, other client, other question, invented, rotated secret); `1.0` vs `1`, `"n:1"` vs `1`, key order | unit, parameterised | `ToolTurnSignerTest` | 1 |
| Policy: unknown tool, schema violation, over max-calls, duplicate of an answered call | unit | `ToolPolicyTest` | 1 |
| Wire: tools body, `tool_calls` accumulation across chunks, arguments object, think by capability, no-tools body byte-identical to Phase 10, content-free `Msg.toString` | adapter | `OllamaChatAdapterTest` + scripted `FakeOllamaServer` | 2 |
| Fences, forbidden tokens, budget and 413, cap omits tools, no-tool prompt unchanged | unit | `PromptBuilderTest` | 2 |
| TOOL citations numbered after passages, error results uncited, invented ids | unit | `CitationBuilderTest` | 2 |
| One retry then `TOOL_CALL_INVALID`; retry is not a round; fifth round impossible; gate released once; score gate skipped with tools | unit | `ChatServiceTest` | 3 |
| `tool_call` then `done{tool_calls}` over real Tomcat; immediate resume never 429; tamper gives 400 with zero Ollama requests; 413; per-POST deadline | IT | `SseStreamingIT`, `ChatApiIT` | 4 |
| Canaries absent from logs (arguments, results, titles, signature, secret) | IT | `LogHygieneIT` | 4 |
| No outbound route except Ollama | ArchUnit | `ArchitectureBoundaryTest` | 4 |
| Contract | IT | `OpenApiContractIT` (`-Drag.contract.update=true` once) | 4 |
| Model behaviour TC01-TC07, TC11; deterministic TC08-TC10 over live HTTP | live | `GoldenToolsIT` with `-Pgolden -Dtest=GoldenToolsIT` | 5/6 |

Sampling: per task the quick command on touched classes; per wave the full suite; phase gate is the full suite green plus one recorded live run per configuration (A and B).

### Wave 0 Gaps
None separate: `FakeOllamaServer` scripting, `FakeChatPort` tool scripting and signer fixtures are created in the plans that first need them.

## Security Domain

| ASVS | Applies | Control |
|------|---------|---------|
| V4 Access control | yes | signature binds client id; other clients collections stay 404 |
| V5 Input validation | yes | Bean Validation sizes, `ToolSchema`, NUL rejection, source url pattern |
| V6 Cryptography | yes | JDK HMAC-SHA256, constant-time compare, secret >= 32 chars, never logged |
| V7 Logging | yes | metadata only, canary tests |

| ID | Threat | STRIDE | Mitigation |
|----|--------|--------|------------|
| T-11-01 | Forged or tampered tool turn | Tampering | HMAC chain (client, question, round, questionId, previous signature, calls); 400 before gate and model |
| T-11-02 | Cross-client or cross-question replay | Spoofing | client id and question hash inside the MAC |
| T-11-03 | Injection through tool results | Tampering | nonce fence, forbidden tokens, prompt rule; residual accepted as Phase 10 |
| T-11-04 | Runaway loop or oversized result | DoS | caps in code, 413 never truncation, per-POST deadline, release-once gate |
| T-11-05 | Replay of an old valid turn | Repudiation | accepted: reproduces only the model own call; results are caller-controlled by design |
| T-11-06 | Content or secret in logs or toString | Info disclosure | metadata-only lines, content-free toString, canary tests |
| T-11-07 | Service calls into an app or holds app credentials | Elevation | no route or type for it; ArchUnit |
| T-11-08 | Non-finite or huge numbers crash canonicalisation | DoS | reject with 400 |
| T-11-09 | Hostile source url | Tampering | `https?://` pattern and length cap; escape in Phase 13 |
| T-11-10 | Weak or missing signing secret | Spoofing | startup failure under 32 chars |

## Plan-shaping recommendations

Phase 10 dependence: files Phase 10 may still change are `Prompts`, `PromptBuilder`, `CitationBuilder`, `ChatService`, `OllamaChatAdapter` (think only), `GoldenChatIT`, `chat-v1.json`, docs model text. Waves 1-2 (except 11-04) do not need Phase 10 closed. Plans 11-04, 11-05 and 11-07 must wait for Phase 10 to close; 11-08 needs the user Phase 10 model decision (D-11-12).

| Wave | Plan | Scope | Owns (no same-wave overlap) | Phase 10 dependent |
|------|------|-------|-----------------------------|--------------------|
| 1 | 11-01 Config and ops | `ToolProperties` (+ secret validation), `GenerationConfig` registration, both `application.yml`, test yml, `compose.app.yaml`, `.env.example`, `docs/docker.md` (config and tool contract section) | config/ToolProperties, GenerationConfig, yml x2, test yml, compose, env, docs | no |
| 1 | 11-02 Domain, signer, policy | tool records, `ToolSchema`, `ToolTurnSigner`, `ToolPolicy`, two exceptions, `ErrorReason`, `StreamEvents`, additive `ChatMessage`/`Completion`/`GenerationOptions`, `ChatPort.assertToolsReady`, `FakeChatPort` update, unit tests incl. tamper table | domain/*, application/ToolTurnSigner, ToolPolicy, ports/ChatPort, FakeChatPort, their tests | no |
| 2 | 11-03 Ollama adapter | wire records, `tool_calls` accumulation with hard cap, tool role messages, think by capability, `assertToolsReady`, `Msg.toString`; scripted `FakeOllamaServer`; adapter tests | adapters/ollama/*, FakeOllamaServer, OllamaChatAdapterTest | think logic only |
| 2 | 11-04 Prompt and citations | `Prompts.TOOL_RULES` + version, `chatWithTools` (budget order: fixed, tool exchange, passages, history; 413), fences, `CitationBuilder` tool sources, `Citation.kind` | application/Prompts, PromptBuilder, CitationBuilder, domain/Citation, their tests | yes (CHAT_SYSTEM, version) |
| 3 | 11-05 ChatService | Command, `prepare` (intake, chain verify, pairing, capability), `run` seams (skip gate with tools, loop, correction retry, sign, releaseOnce, logging), `ChatServiceTest` | application/ChatService, ChatServiceTest | yes |
| 4 | 11-06 Web, real-server tests, contract | `ChatRequest` DTOs, controller docs, `GlobalExceptionHandler`, `ChatApiIT`, `SseStreamingIT` tool cases, `LogHygieneIT` canaries, `ArchitectureBoundaryTest`, **the only openapi.json regeneration** | adapters/web/*, those ITs, ArchitectureBoundaryTest, openapi.json | no |
| 5 | 11-07 Golden tool tier | `golden/tools-v1.json` (TC01-TC11), `FakeToolApp`, `ToolGoldenRunner`, `GoldenToolsIT` with its own seeding copy (do not edit `GoldenChatIT`), per-model report `target/golden-tools-report-<model>.json`, hermetic run of the deterministic cases | test sources and resources under golden/ (new files) | yes (harness, seeds) |
| 6 | 11-08 Live bake-off (`autonomous: false`) | human runs `-Pgolden -Dtest=GoldenToolsIT` twice: A `-Drag.chat.model=qwen3:4b`, B `-Drag.chat.model=qwen2.5-coder:7b`; merges the D-11-11 table (pass rate, tokens/s from `eval_count/eval_duration`, TTFT, per-round latency, `length` and DEADLINE counts, schema-violation rate); must not pull models, change OLLAMA_* env, restart Ollama or touch docker | `.planning/phases/11-*` notes only | yes (D-11-12) |

Acceptance criteria should assert properties (tamper table all 400 with zero Ollama requests; no-tools request body byte-identical; immediate resume never 429), not grep counts.

## Sources

- Repo (read this session): ChatService, PromptBuilder, Prompts, CitationBuilder, ChatPort, OllamaChatAdapter, ChatController, SseSink, ChatRequest, GlobalExceptionHandler, GenerationGate, FakeOllamaServer, FakeChatPort, ArchitectureBoundaryTest, OpenApiContractIT, GoldenChatIT, application ymls, compose, pom [VERIFIED]
- Live Ollama 0.34.0 probes 2026-10-14 (qwen2.5-coder:7b: think:false accepted; call printed as text with tools; tool-history replay without `tools` works) [VERIFIED]
- `11-AI-SPEC.md` (wire contract, budget table, eval design), Phase 10 `10-RESEARCH.md` C1-C5, `10-09-SUMMARY.md`, `.planning/debug/transform-deadline-qwen3.md`, STATE blocker [CITED: repo]

## Metadata

**Confidence:** code mapping HIGH; wire shapes HIGH (AI-SPEC plus today probes); qwen3 reasoning length under real prompts MEDIUM-LOW (A6)
**Research date:** 2026-10-14; **valid until:** Phase 10 closes or the Ollama version changes
