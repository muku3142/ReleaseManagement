---
phase: 11-tool-call-protocol
plan: 03
subsystem: rag-service/adapters.ollama
tags: [ollama, tool-calls, chat-adapter, test-fake]
requires: [11-02]
provides: [ChatPort.assertToolsReady, tool wire contract in OllamaChatAdapter, scriptable FakeOllamaServer]
affects: [11-04]
key-files:
  modified:
    - rag-service/src/main/java/com/evision/ragservice/ports/ChatPort.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/ollama/OllamaChatAdapter.java
    - rag-service/src/test/java/com/evision/ragservice/application/FakeChatPort.java
    - rag-service/src/test/java/com/evision/ragservice/FakeOllamaServer.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/ollama/OllamaChatAdapterTest.java
requirements: [RAG-08]
metrics:
  completed: 2026-10-15
---

# Phase 11 Plan 03: Ollama tool wire contract Summary

The Ollama adapter now sends native `tools`, replays tool rounds, accumulates streamed `tool_calls`, picks `think` by capability on tool requests, exposes `assertToolsReady()`, and reports `eval_duration`; `FakeOllamaServer` can script one stream per request.

## Commits
- 89517b0 test(11-03): scriptable FakeOllamaServer (`chatScript`, `chatBodies`, `toolCallLine`, `toolCalls`, `eval_duration` in `doneLine`)
- fa945eb feat(11-03): adapter, port method, FakeChatPort, adapter tests

## Accomplishments
- Regression anchor `noToolsRequestBodyIsByteIdenticalToPhase10` was written and pinned on the unchanged adapter first (failed against a placeholder, observed body copied in, passed on HEAD, 13/13), and still passes after the change. The no-tools body keeps `"think":false` and carries no tools keys.
- Tool requests: `tools` array, assistant `tool_calls` replay with object arguments, `role:"tool"` with `tool_name`; `think:true` only for tool requests when `/api/show` lists `thinking`.
- Calls accumulate across chunks (detected from the list, not `done_reason`); missing id becomes `call_<8 hex>`; more than 16 calls raises GenerationFailedException (T-11-04); `thinking` is dropped by ignoreUnknown.
- `assertToolsReady()` raises ModelNotAvailableException without the `tools` capability; 400 "does not support tools" maps to the same exception.
- All wire records are private to adapters.ollama with content-free toString.

## Verification
Full rag-service suite: 294 run, 0 failures (baseline 284 plus 10 new). OllamaChatAdapterTest 22/22.

## Deviations from Plan
- Commit order: the fake server commit precedes the adapter commit because the scripted-fake test lives in OllamaChatAdapterTest (committed with the adapter) and needs the fake to compile. The scripted-fake case stayed in OllamaChatAdapterTest; no FakeOllamaServerTest was needed.
- Phase 10 gate: OllamaChatAdapter was as Phase 10 left it; no rebase needed.

## Known Stubs
None.

## Self-Check: PASSED
