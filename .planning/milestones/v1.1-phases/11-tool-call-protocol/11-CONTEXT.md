# Phase 11: Tool-Call Protocol - Context

**Gathered:** 2026-10-14
**Status:** Ready for planning (run `/gsd-ai-integration-phase 11` first, per ROADMAP). Execution must wait until Phase 10 closes.

<domain>
## Phase Boundary

Extends `/v1/chat` in `rag-service` so that an app can send tool definitions with a question. When the model needs exact data:
- the service streams `tool_call` events;
- the app runs those tools itself and posts the results back;
- the service answers using them, with tool sources in the citations.

The service never calls into an app and holds no app credentials (RAG-08). The tool loop is capped, a malformed call is retried once and then fails honestly, and the chat model is chosen by a measured comparison.

Not in this phase:
- ReleaseManagement's real tools and the calls into rag-service (Phase 12);
- any UI (Phase 13);
- the full golden quality gate (SAFE-04, Phase 13).

Phase 10 behaviour for requests without tools must not change.
</domain>

<decisions>
## Implementation Decisions

### Already locked (Phase 9/10 CONTEXT, PROJECT.md, research/v1.1, ROADMAP scope notes)
- Standalone, domain-free `rag-service`; per-client API keys; another client's collection is 404. Ollama is the only outbound call. There is no AI framework: a hand-rolled `RestClient` adapter behind `ChatPort`.
- Stateless chat: history is a request parameter and nothing is persisted (D-10-06). Caller-owned instructions (D-10-09). Per-client in-flight limit with 429 (D-10-01). Fail-fast 503 reasons (D-10-04). Indexing pauses during generation (D-10-03).
- Plain-text answers; untrusted text fenced as data; metadata-only logs (SAFE-02/03). Citations built by the service, never from model text (RAG-07).
- Tool arguments are validated against the caller's supplied schema. Tool results are authoritative for numbers, dates and status. Tool-result size is capped (ROADMAP scope notes).

### Resuming a paused tool round
- **D-11-01: Fully stateless resume.** After a `tool_call` event the stream ends. The app continues by POSTing `/v1/chat` again with the original question, its history, the model's tool-call turn and the tool results. The service keeps no session state and no continuation token.
- **D-11-02: The tool-call turn is signed.** The `tool_call` event carries an HMAC over the calls (id, name, arguments), keyed by a service secret. The app must return the turn unchanged. The service rejects a missing, tampered or invented tool-call turn with 400. This keeps the service stateless while stopping a caller from forging what the model asked for. The secret is new configuration; rotating it invalidates only in-flight rounds.
- **D-11-03: Several calls per round.** One `tool_call` event may list several calls. The app runs them all and returns every result together in the next request.

### Tools and retrieval
- **D-11-04: Pre-retrieval stays, tools are added on top.** Requests with tools keep Phase 10's pre-retrieval exactly (passages in the prompt). The caller's tools are added. There is no built-in search tool in this phase.
- **D-11-05: Tool results win conflicts.** When a tool result and a passage disagree on a number, date or status, the answer follows the tool result. The prompt states this, and citations show both sources.
- **D-11-06: No score-gate refusal when tools are present.** The Phase 10 retrieval-score gate skips the model only for requests without tools, because a tool may answer what no passage covers. The model-level `NO_ANSWER` rule still applies after tools run.

### Loop limits and failures
- **D-11-07: At most 4 tool rounds per question, configurable.** The round count travels in the signed tool-call turn. At the cap the service tells the model to answer with what it has or say it cannot; it never emits a fifth round. A cap on calls per round also applies (Claude's discretion; around 4).
- **D-11-08: The deadline applies per request.** Each POST, and so each round, gets its own deadline (D-10-02, 120 s). The app's own tool execution time between requests is not counted by the service.
- **D-11-09: Tool failures are results.** An app reports a failed tool as an error result (`{"error": "..."}`) for that call. The model is told the tool failed and must not invent the value; it answers with what it has or says the data was unavailable. A malformed tool call from the model, such as an unknown tool or arguments that fail the caller's schema, is retried once with a correction message, then ends in an honest failure (ROADMAP success criterion 3).
- **D-11-10: Tool results must fit the context budget, with 413 beyond.** Tool results share `num_ctx` with passages and history. There is a per-result cap (around 8,000 characters, configurable) and an overall budget check. Oversized results get 413 with a clear reason, never silent truncation, as with D-10-10. Note: on the approved 4096 dev profile the budget is tight, so research must size passages, history and tool results together.

### Model comparison
- **D-11-11: Compare only the installed models.** `qwen3:4b` (the installed 2507 thinking build) and `qwen2.5-coder:7b`, each on the golden tool questions × 3 runs, recording pass rate, tokens/s and time to first token (ROADMAP success criterion 5). The user chose not to pull more models for this comparison. Caveat: the thinking `qwen3:4b` already exceeded the 120 s deadline in Phase 10 (see STATE blocker), and `qwen2.5-coder:7b` runs about 42% on GPU on the 4 GB dev card. Report results honestly even if neither candidate passes; a failing result is a finding, not something to hide.
- **D-11-12: The Phase 10 model decision comes first.** The comparison does not replace the deferred Phase 10 chat-model choice. The user decides Phase 10's model before Phase 11 executes; Phase 11 then confirms or replaces it with measured data.
- **D-11-13: Tool routing is split from answering (amends D-11-02).** Live measurement (debug session zero-native-tool-calls) showed the retrieval-QA frame ("answer only from SOURCES", NO_ANSWER, a SOURCES block in the user turn) makes a 4B model refuse instead of calling a tool, while the same model calls tools correctly without that frame. When tools are offered and no tool results exist yet, round 1 is a short tool-routing request: the routing system prompt (tool-call rules only), history and the question, with the tools and with no SOURCES and no NO_ANSWER rule. If the model calls tools, the service emits tool_call as before (the signed turn of D-11-02 is unchanged). If it returns text, that text is discarded and the normal retrieval answer request runs (sources, tools, and the existing NO_ANSWER and citation rules). Rounds after tool results use the existing QA frame unchanged. The routing request is not an extra tool round for the round cap.

### Claude's Discretion
- The tool definition format (an OpenAI/Ollama-style function schema; which JSON Schema subset is supported for argument validation), the maximum number of tools per request, and the maximum calls per round.
- The `tool_call` event payload shape (call ids, names, arguments as an object, the signature) and the request shape for returning tool results (`role:"tool"` messages keyed by call id).
- HMAC algorithm and encoding, the secret's config key, and how the round count and the original question are bound into the signature.
- How tool-result sources are supplied (e.g. an optional `source` with id, title and url per result) and rendered as citations next to passage citations.
- Prompt changes for tool use (bump `PROMPT_VERSION`), and the golden tool-question fixture (new questions, reusing Phase 10's format and the `golden` profile).
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone and requirements
- `.planning/ROADMAP.md`: "Phase 11: Tool-Call Protocol" (goal, success criteria, research flags, scope notes, AI-integration flag)
- `.planning/REQUIREMENTS.md`: RAG-08, plus RAG-07 and SAFE-01..03 as they apply to tool rounds
- `.planning/PROJECT.md`: Key Decisions row "Exception (2026-10-13): a standalone, domain-free RAG service" (the service returns tool calls and never calls into apps)

### Phase 10 (the chat path this phase extends)
- `.planning/phases/10-rag-chat-citations/10-CONTEXT.md`: D-10-01..12
- `.planning/phases/10-rag-chat-citations/10-AI-SPEC.md` and `10-RESEARCH.md`: Ollama `/api/chat` facts, SSE mechanics, fencing, citation and refusal design. RESEARCH's corrections override the AI-SPEC.
- `.planning/phases/10-rag-chat-citations/10-09-SUMMARY.md`: live measurements on the dev GPU, including the thinking-model deadline failure
- `.planning/debug/transform-deadline-qwen3.md`: root cause of the thinking-model behaviour
- `.planning/STATE.md`: the open Phase 10 chat-model blocker

### Research (v1.1)
- `.planning/research/v1.1/SUMMARY.md`: tool-calling guidance (few flat schemas, validation with one retry, caps in code) and the Ollama tool-calling verification items
- `.planning/research/v1.1/PITFALLS.md`: small-model tool failures and runaway loops
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `rag-service/.../application/ChatService.java`: prepare/run order (404, 400, 503, 429), the deadline, the metadata log line. Tool rounds extend this flow.
- `rag-service/.../ports/ChatPort.java` and `adapters/ollama/OllamaChatAdapter.java`: the NDJSON stream. Ollama tool calls arrive as `message.tool_calls`; the adapter must surface them through the port without leaking Ollama shapes.
- `application/PromptBuilder.java` and `Prompts.java`: nonce fencing and token budgeting. Tool results are untrusted text and must be fenced the same way.
- `application/CitationBuilder.java`: MARKED/RETRIEVED citations, to be extended with tool sources.
- `domain/MetadataRules.java`: the bounded-validation style to copy for tool-argument and schema validation.
- `adapters/web/ChatController.java`, `SseSink`, and the `SseEvents` test helper: a new `tool_call` event.
- `rag-service/src/test/resources/golden/` and `GoldenChatIT`: the fixture format and `golden` profile to extend with tool questions.

### Established Patterns
- Hexagonal packages enforced by `ArchitectureBoundaryTest`, including the Phase 10 rule that `java.net.http` lives only in `adapters.ollama`/`config`. That rule backs success criterion 2 (no outbound route except Ollama).
- ProblemDetail errors with a `reason` via `GlobalExceptionHandler`.
- Hermetic tests use the JDK `HttpServer` fake Ollama (`FakeOllamaServer`); live tests only via `mvn test -Pgolden`.
- `openapi.json` plus `OpenApiContractIT`: exactly one plan regenerates the contract.

### Integration Points
- New config: the tool-signing secret, max rounds, max calls per round, the per-result cap. Add them to both `application.yml` files, `compose.app.yaml`, `.env.example` and `docs/docker.md`.
- The `/v1/chat` request and response contract changes: `tools`, tool-call turns, tool results, and the `tool_call` event.
</code_context>

<specifics>
## Specific Ideas
- A caller must never be able to forge the model's tool calls (D-11-02), but must stay in full control of running tools and of what data it returns (D-11-09).
- On the 4 GB dev GPU every extra round costs seconds to tens of seconds. Prefer fewer, parallel calls (D-11-03) and keep the per-round prompt small.
</specifics>

<deferred>
## Deferred Ideas
- A built-in `search_knowledge` tool instead of pre-retrieval (D-11-04), if measurements later show it helps.
- A continuation-token resume (D-11-01).
- A whole-question deadline carried across rounds (D-11-08).
- Comparing larger or newly pulled models (8B, `qwen3:4b-instruct-2507`), which the user excluded for now (D-11-11). Revisit for production hardware.
</deferred>

---

*Phase: 11-tool-call-protocol*
*Context gathered: 2026-10-14*
