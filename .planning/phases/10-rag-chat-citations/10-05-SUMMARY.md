---
phase: 10-rag-chat-citations
plan: 05
subsystem: rag-service application layer
tags: [chat, transform, sse-orchestration, generation-gate, refusal]
requires: [10-01, 10-02, 10-03, 10-04]
provides: [ChatService.prepare/run, TransformService.prepare/run, FakeChatPort, RecordingEventSink]
affects: [10-06]
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/application/ChatService.java
    - rag-service/src/main/java/com/evision/ragservice/application/TransformService.java
    - rag-service/src/test/java/com/evision/ragservice/application/FakeChatPort.java
    - rag-service/src/test/java/com/evision/ragservice/application/RecordingEventSink.java
    - rag-service/src/test/java/com/evision/ragservice/application/ChatServiceTest.java
    - rag-service/src/test/java/com/evision/ragservice/application/TransformServiceTest.java
decisions:
  - "Truncation heuristic lives in ChatService.probablyTruncated (package-private static) and is reused by TransformService"
  - "totalMs in the log line is wall-clock run time, not Ollama's total_duration"
metrics:
  tests: "226/226 green (baseline 200, +26)"
  completed: 2026-10-14
---

# Phase 10 Plan 05: Chat and Transform services Summary

ChatService and TransformService each split into prepare (request thread, HTTP-mapped exceptions, in the 404, 400, MIXED_EMBEDDING_MODELS, 503, 429 order) and run (generation thread, reports only through EventSink); the gate is always released before sink.complete().

## Commits
- 3df6145 feat(10-05): ChatService prepare/run
- see git log for the TransformService commit (feat(10-05): TransformService ...)

## Deviations from Plan
None of substance. Small additions: `ChatService.probablyTruncated` extracted so transform reuses the heuristic; a cancelled completion with CLIENT_GONE emits nothing further (covered by a test).

## Notes
- Cancel before Ollama headers only takes effect once headers arrive (per 10-02); run logic does not assume instant cancellation, the deadline test uses a fake that blocks on the cancel latch.
- Log lines are metadata only; no getMessage(), no request or answer text.

## Known Stubs
None.

## Self-Check: PASSED
