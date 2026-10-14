---
phase: 10-rag-chat-citations
plan: 07
subsystem: rag-service tests and docs
tags: [sse, log-hygiene, archunit, docs]
requires: [10-06]
provides: [SseStreamingIT, LogHygieneIT, outbound-call ArchUnit rules, chat operations docs]
requirements: [SAFE-01, SAFE-02, SAFE-03, RAG-06]
key-files:
  created:
    - rag-service/src/test/java/com/evision/ragservice/adapters/web/SseStreamingIT.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/web/LogHygieneIT.java
  modified:
    - rag-service/src/test/java/com/evision/ragservice/adapters/web/RawHttpSecurityIT.java
    - rag-service/src/test/java/com/evision/ragservice/ArchitectureBoundaryTest.java
    - rag-service/src/test/java/com/evision/ragservice/FakeOllamaServer.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/ChatRequest.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/TransformRequest.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/ollama/OllamaChatAdapter.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/ollama/OllamaEmbeddingAdapter.java
    - rag-service/src/main/java/com/evision/ragservice/domain/ChatMessage.java
    - rag-service/src/main/java/com/evision/ragservice/domain/StreamEvents.java
    - docs/docker.md
metrics:
  completed: 2026-10-14
---

# Phase 10 Plan 07: Streaming and log-hygiene proof Summary

Real-Tomcat proof of first-token latency, client abort, deadline, 429 and indexing resume; a canary log test at default and DEBUG levels; two ArchUnit rules; and the chat/transform operations and accepted-risks docs.

## Commits
- c77fad2 test: SseStreamingIT (6 cases), RawHttpSecurityIT 401 case, FakeOllamaServer.reset()
- b7984c5 test: LogHygieneIT, two ArchUnit rules, size-only toString on request and wire DTOs
- 781f7c7 docs: docs/docker.md "Chat and transform" and "Accepted risks (chat)"

## Verification
Full rag-service suite `clean test`: 251 tests, 0 failures (baseline 240 plus 11 new). Streaming, abort (fake sees abort, gate frees, queued document then indexes), DEADLINE at ~3 s, 429 with Retry-After for plain and event-stream Accept, other client served, mid-stream error and cold-load SEARCHING status all pass on real HTTP. No production defect surfaced there.

## Deviations from Plan

**1. [Rule 3 - Blocking] FakeOllamaServer.reset()** - the abort latch is one-shot, so a shared fake could not prove abort per test. Added `reset()` (re-arms the latch and default script). File is a test helper outside files_modified.

**2. [Rule 2 - Missing critical] DEBUG leak was wider than the plan predicted** - the DEBUG run proved leaks beyond `ChatRequest`/`TransformRequest`: Spring's `DefaultRestClient` logs the outbound Ollama bodies, exposing prompts and document text. Added size-only `toString` to `ChatRequest`, `ChatRequest.Turn`, `TransformRequest`, `ChatMessage`, `StreamEvents.Token` (as planned) plus the private wire records `Msg` (OllamaChatAdapter) and `EmbedRequest` (OllamaEmbeddingAdapter), which are outside files_modified. Serialization unchanged (ChatApiIT, TransformApiIT green).

**3. Seven cases** - SseStreamingIT has six cases; the seventh (401) lives in RawHttpSecurityIT as planned.

## Deferred / accepted
- Phase 9 search and document endpoints are not covered by LogHygieneIT: at `org.springframework.web` DEBUG, Spring logs `SearchResponse`/document bodies (content, titles). Left out of scope; documented in docs/docker.md accepted risks. Follow-up: size-only toString on those DTOs or never run that level outside local debugging.
- LogHygieneIT canary in the request URL path is not asserted: DispatcherServlet logs the request line at DEBUG; paths carry identifiers, not content.

## Known Stubs
None.

## Self-Check: PASSED
