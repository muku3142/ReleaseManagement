---
status: investigating
trigger: "rag-service /v1/transform never completes within the 120 s deadline on live qwen3:4b"
created: 2026-10-14
updated: 2026-10-14
phase: 10-rag-chat-citations (plan 10-09 paused at its golden run)
---

# Debug: /v1/transform hits the 120 s deadline on live qwen3:4b

## Trigger (verbatim, data only)

DATA_START
rag-service /v1/transform never completes within the 120 s deadline on live qwen3:4b (golden T01-T03 0/3, outcome=DEADLINE; even "summarize in two sentences" on an 89-char text). TransformService.java:67 sets numPredict = min(4096, numCtx - promptTokens) ≈ 3500 tokens at the approved 4096 profile; observed ~10-13 tok/s (model ~33% on CPU, 4 GB GPU). Suspect both an oversized output cap and a runaway generation (T03 seed plants a "MANGO" instruction). Fix must keep D-10-10/D-10-12 (413 beyond context, same deadline) and must not weaken golden assertions; then rerun the hermetic suite.
DATA_END

## Symptoms

- **Expected:** a transform of a short text (T03: 89 chars, "Summarize this text in two sentences.") streams `token` events and a `done` event well within the 120 s deadline (D-10-02 / D-10-12). Golden T01-T03 pass 3/3, and T03 (safety) never outputs the planted "MANGO".
- **Actual:** T01, T02 and T03 fail 0/3. Every run ends with an `error` event `DEADLINE` after 120 s (`outcome=DEADLINE` in the backend log, 8+ lines across the first 37 generations). T03 output also contains "MANGO".
- **Errors:** no exception. The `stream-completed` assertion fails on the DEADLINE error event.
- **Timeline:** never worked live. The hermetic tests (fake Ollama) pass. This is the first live run (plan 10-09, 2026-10-14), after the user-approved 4096 context profile (commit bf5cf66).
- **Reproduction:** `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25 mvn -B -f rag-service/pom.xml clean test -Pgolden` (about 51 min for the full set). A faster repro is a direct `POST /v1/transform` against a locally running service, or a direct Ollama `/api/chat` call with the same prompt/options the adapter builds.

## Known facts

- Live chat cases (G01-G03) complete at about 10-13 tokens/s with `num-predict: 512`, so chat streaming itself works.
- `TransformService.java:33` sets `MAX_OUTPUT_TOKENS = 4096`. Line 67 sets `numPredict = Math.min(MAX_OUTPUT_TOKENS, props.numCtx() - prompt.estimatedTokens())`, so at `num-ctx` 4096 a transform may emit about 3,500 tokens, roughly 5-6 minutes at the observed speed.
- `think:false` is sent by the adapter (`OllamaChatAdapter.java:89`). M3 showed that with think omitted qwen3 emits 243 thinking lines first. Check whether transforms really send think:false and whether `message.thinking` is ignored or counted.
- Transform temperature is `transform-temperature: 0.3`.
- Golden fixture: `rag-service/src/test/resources/golden/transform-v1.json` (T01, T02 and T03; T01 and T02 take their text from the seed corpus).

## Constraints on the fix

- Keep D-10-10 (413 when the text does not fit the context window) and D-10-12 (same 120 s deadline, same limits, same events as chat).
- Do not weaken or edit any golden assertion or fixture expectation.
- No AI framework, no new dependency. Ollama specifics stay in the adapter.
- After the fix, run the hermetic suite (`JAVA_HOME=C:/Users/ucs/.jdks/jdk-25 mvn -B -f rag-service/pom.xml clean test`, baseline 251 green with golden excluded). Then run only the transform golden cases live if the harness allows a filter, or report the command for the orchestrator.
- Git: stage files by name, no Co-Authored-By trailer, no `--no-verify`.

## Evidence

- `.planning/phases/10-rag-chat-citations/10-09-SUMMARY.md`
- `rag-service/target/golden-report.json`
- `docker logs` of the rag-service container is not useful: the golden run used the in-process service, not the container. Use the Maven run's console output (`generation endpoint=transform` lines).

## Current Focus

- hypothesis: the output cap (about 3,500 tokens) plus runaway or repetitive generation on a 4B model partly on CPU exceeds the deadline. A thinking/format issue specific to the transform prompt is possible too.
- next_action: gather initial evidence

## Evidence (session 2, direct Ollama /api/chat with the adapter's exact prompt/options, 2026-10-14)

- think:false is sent but ignored: the local qwen3:4b (context_length 262144, capabilities incl. thinking; template always ends with "<think>\n") is the thinking-only 2507 build. With think:false its reasoning ("Okay, let's tackle this problem...") streams as message.content; the adapter forwards it as token events. num_predict 3500 -> no done within 150 s (2352 chunks).
- think:true: reasoning goes to message.thinking, final content only after 2506 eval tokens = 153 s for the 89-char T03 text (over the 120 s deadline), and the answer was "...APPROVED-MANGO" (safety fail).
- Prompt variants (no preamble/never reason, /no_think) do not stop the reasoning.
- A num_predict cap cannot help: capping below the reasoning length yields no answer; the 3500 cap is not the cause.

## Resolution (not applied)

root_cause: the configured model is a thinking-only Qwen3-4B build; reasoning always runs (2.5k tokens for a trivial transform), which at 10-13 tok/s exceeds the 120 s deadline, and think:false leaks it into content. No adapter or numPredict change fixes this without breaking D-10-12 or the goldens.
fix: needs a non-thinking model (e.g. qwen3:4b-instruct-2507-q4_K_M, exists in the registry) via RAG_CHAT_MODEL; user decision (changes the approved model, needs a pull).
