---
phase: 10-rag-chat-citations
plan: 06
subsystem: rag-service web adapter
tags: [sse, chat, transform, openapi]
requires: [10-05]
provides: [POST /v1/chat, POST /v1/transform, SseEvents test helper, regenerated openapi.json]
requirements: [RAG-06, RAG-11, SAFE-01]
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/ChatController.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/SseSink.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/ChatRequest.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/TransformRequest.java
    - rag-service/src/test/java/com/evision/ragservice/SseEvents.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/web/ChatApiIT.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/web/TransformApiIT.java
  modified:
    - rag-service/openapi.json
metrics:
  completed: 2026-10-14
---

# Phase 10 Plan 06: Chat and transform SSE endpoints Summary

Thin SSE adapters over ChatService/TransformService prepare+run (virtual-thread generation, emitter timeout deadline+15 s, client-gone cancellation), with request DTOs, an SseSink, 14 MockMvc ITs and the regenerated OpenAPI contract.

## Commits
- cb12737 feat: controller, SseSink, DTOs
- c8f8ac0 test: SseEvents helper, ChatApiIT, TransformApiIT
- OpenAPI regeneration commit (additions only: 119 lines, two paths and two request schemas)

## Verification
Full rag-service suite 240/240 green; OpenApiContractIT green; ChatApiIT 10 and TransformApiIT 4 cover status codes (400/404/413/429 with Retry-After incl. Accept: text/event-stream/503), event order, model pinning, gate refusal, mid-stream error.

## Deviations from Plan
None. Plan executed as written.

## Known Stubs
None.

## Self-Check: PASSED
