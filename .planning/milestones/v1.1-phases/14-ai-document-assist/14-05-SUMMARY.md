---
phase: 14-ai-document-assist
plan: 05
subsystem: rag-adapters
tags: [sse, rag-service, transform, wiremock]
requires: [13-03, 13-05]
provides: [SseStreamReader, ListenerFailure, RagTransformPort, RagTransformAdapter, TransformOutcome]
affects: [14-07]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/rag/SseStreamReader.java
    - src/main/java/com/evision/releasemgmt/adapters/rag/ListenerFailure.java
    - src/main/java/com/evision/releasemgmt/adapters/rag/RagTransformAdapter.java
    - src/main/java/com/evision/releasemgmt/ports/RagTransformPort.java
    - src/main/java/com/evision/releasemgmt/domain/assistant/TransformOutcome.java
    - src/test/java/com/evision/releasemgmt/adapters/rag/SseStreamReaderTest.java
    - src/test/java/com/evision/releasemgmt/adapters/rag/RagTransformAdapterTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/rag/RagChatAdapter.java
requirements: [ASST-09]
metrics:
  tasks: 3
  tests: "1100 green (baseline 1084)"
---

# Phase 14 Plan 05: SseStreamReader, RagTransformPort and RagTransformAdapter Summary

Chat and transform now share one package-private SSE line loop, and `RagTransformAdapter` streams `/v1/transform` with chat's outcome vocabulary and client-gone abort chain.

## Commits
- 9657fb0 refactor: extract SseStreamReader and ListenerFailure (RagChatAdapterTest unchanged and green)
- 58a5d36 feat: RagTransformPort, RagTransformAdapter, TransformOutcome, tests

## What was built
- `SseStreamReader.read(stream, Handler)` returns true if the handler stopped the read. Handler exceptions propagate unchanged.
- `ListenerFailure` was private in RagChatAdapter. It is now a package-private top-level class with a static `notifyListener(Runnable)` and `listenerException()`. `RagChatAdapter.isTimeout` is now package-private and shared.
- `TransformOutcome` is sealed: `Done`, `TooLarge(maxChars)` and `Failed(reason, retryAfterSeconds)`. `RagTransformPort.transform(text, instruction, ChatListener)` reuses the chat listener.
- `RagTransformAdapter` uses the `ragChatRestClient` bean and the same `@ConditionalOnProperty` as chat. 413 `maxChars` is parsed from the problem `detail` by regex only. The wire record has a redacting `toString`, and nothing is logged.

## Deviations
- **Gate command (not a plan Rule deviation):** the literal grep `listener.heartbeat()` fails because 13-05 wrote `notifyListener(listener::heartbeat)`. The behaviour is present, 13-05-SUMMARY exists, ClientGoneException exists, and RagChatAdapterTest has heartbeat tests. I treated the gate as open.
- **Worktree base:** reset to 15e1e86 as instructed, since the initial base was older.
- **ListenerFailure extraction:** the plan permitted this, so it is not a deviation.

## Verification
Full `mvn -B -P '!frontend' clean test`: 1100 tests, 0 failures. RagChatAdapterTest is unmodified. `grep readLine` finds it only in SseStreamReader.

## Threat flags
None. T-14-13 to T-14-15 are mitigated by the listener-exception propagation test, the digits-only Retry-After and regex-only 413 parse, and the log-sentinel test.

## Self-Check: PASSED
