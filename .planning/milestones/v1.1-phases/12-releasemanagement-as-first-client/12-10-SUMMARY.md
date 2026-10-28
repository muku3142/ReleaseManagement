---
phase: 12-releasemanagement-as-first-client
plan: 10
subsystem: assistant
tags: [rag, sse, spring-restclient, jackson3, wiremock]
requires: [12-04, 12-05, 12-07, 12-09, 11-06]
provides: [RagChatPort implementation over /v1/chat, ragChatRestClient bean]
affects: [AssistantService]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/rag/RagChatAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/rag/RagChatAdapterTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/config/RagRestClientConfig.java
decisions:
  - "Signed turn fields (round, questionId, calls, signature) are copied as JsonNodes from the received tool_call data, never re-serialised from domain objects."
  - "503 reason MODEL_NOT_AVAILABLE (the real rag-service reason) maps to MODEL_MISSING; any other 503 maps to OFFLINE."
metrics:
  tests: "1046 green (1036 + 10 new)"
  completed: 2026-10-15
---

# Phase 12 Plan 10: RagChatAdapter Summary

RagChatPort over rag-service `/v1/chat`: Jackson-tree request building, a ~40-line SSE line parser, byte-faithful signed-turn resend, and status/in-stream error mapping to FailureReason without ever throwing.

## Tasks

- Task 0 (Phase 11 gate): open. `toolTurns` present in rag-service/openapi.json, 11-06-SUMMARY.md present; 11-06 merged at 1434a4d. Worktree was reset to that base.
- Task 1: `ragChatRestClient` bean (reuses `ragClient` factory, `app.assistant.chat.*` timeouts already present in both application.yml files), `RagChatAdapter`, `RagChatAdapterTest` (10 tests).

## Behaviour notes

- Wire shapes taken from ChatController.EVENTS and ChatRequest DTO: citations `items[].{n,kind,documentId,title,url,collection,metadata}`, `done.{refusal,finishReason}`, error reasons DEADLINE, OLLAMA_UNAVAILABLE, EMBEDDING_UNAVAILABLE, MODEL_NOT_AVAILABLE, GENERATION_FAILED, TOOL_CALL_INVALID, INTERNAL.
- 429 BUSY; 400/413 REJECTED; other statuses FAILED; connect failure OFFLINE; request timeout DEADLINE; malformed data or stream ending without `done` FAILED.
- Tool results send `source {id, title}` only (no url) when a sourceId is present.
- Logging: the adapter logs nothing; ChatRequest/SignedTurn/ToolCall toString already redact content.
- Phase 13 hooks: `readStream` is the single loop; comment lines are skipped there (heartbeat forwarding, listener exception propagation and closing the stream on abort are one-place additions).

## Deviations from Plan

- [Rule 1 - Contract] The plan text says 503 reason `MODEL_MISSING`; rag-service actually emits `MODEL_NOT_AVAILABLE`, which the adapter follows.
- Limitation: the JDK HttpClient read timeout applies until response headers, not between SSE chunks; mid-stream stalls rely on rag-service heartbeats and its 120 s deadline (T-12-07).

## Known Stubs

None.

## Self-Check: PASSED

Backend suite: 1046 tests, 0 failures; ArchitectureBoundaryTest green.
