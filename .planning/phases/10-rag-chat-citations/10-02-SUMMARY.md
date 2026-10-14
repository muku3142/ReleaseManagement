---
phase: 10-rag-chat-citations
plan: 02
subsystem: rag-service
tags: [ollama, chat, streaming, cancellation]
requires: [10-01]
provides:
  - OllamaChatAdapter implementing ChatPort over /api/chat (NDJSON) and /api/show
  - ollamaStreamClient bean (read timeout = deadline + 5 s)
  - FakeOllamaServer reusable JDK HttpServer test fake that observes client aborts
affects: [10-05, 10-06, 10-07]
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/adapters/ollama/OllamaChatAdapter.java
    - rag-service/src/test/java/com/evision/ragservice/FakeOllamaServer.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/ollama/OllamaChatAdapterTest.java
  modified:
    - rag-service/src/main/java/com/evision/ragservice/config/OllamaRestClientConfig.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/ollama/OllamaEmbeddingAdapter.java
decisions:
  - Malformed-line failure carries no cause so the offending line text cannot leak
  - Pre-header cancellation relies on the read-timeout backstop plus immediate onCancel once headers arrive (no thread interrupt)
metrics:
  tasks: 3
  tests: OllamaChatAdapterTest 12 new
---

# Phase 10 Plan 02: OllamaChatAdapter Summary

ChatPort over Ollama: readiness probe via /api/show capability check, NDJSON token streaming with explicit num_ctx/num_predict/temperature/top_p/keep_alive/think:false, cancellation that closes the response stream, and fixed-message error translation.

## Commits

- ba4b19c: stream client bean, `@Qualifier` on the embedding adapter, FakeOllamaServer
- OllamaChatAdapter and OllamaChatAdapterTest commit (see git log, `feat(10-02): add OllamaChatAdapter`)

## Verification

- RESEARCH A1 proven: cancelling from another thread unblocks the reader, returns `Completion.cancelled(DEADLINE)` within 2 s, and the fake observes the abort.
- Cancel while headers are delayed returns `Completion.cancelled(CLIENT_GONE)` once headers arrive.
- No exception message contains stubbed body text (SECRET_TEXT cases).
- Full rag-service suite green.

## Deviations from Plan

None - plan executed as written.

## Known Stubs

None.

## Threat Flags

None.

## Self-Check: PASSED
