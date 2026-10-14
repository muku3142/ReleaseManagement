---
phase: 10-rag-chat-citations
plan: 01
subsystem: rag-service
tags: [chat, generation, ports, config]
requires: []
provides:
  - ChatPort, EventSink, Cancellation, Completion, GenerationOptions, StreamEvents, Citation contracts
  - GenerationGate (per-client and global in-flight cap) with IndexWorker pause
  - GenerationTimers (deadline and heartbeat scheduler)
  - rag.chat.* ChatProperties and compose/env wiring
  - 429/503/413/400 error mappings with new reasons
affects: [10-02, 10-03, 10-04, 10-05, 10-06]
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/ports/ChatPort.java
    - rag-service/src/main/java/com/evision/ragservice/ports/EventSink.java
    - rag-service/src/main/java/com/evision/ragservice/domain/Cancellation.java
    - rag-service/src/main/java/com/evision/ragservice/application/GenerationGate.java
    - rag-service/src/main/java/com/evision/ragservice/application/GenerationTimers.java
    - rag-service/src/main/java/com/evision/ragservice/config/ChatProperties.java
  modified:
    - rag-service/src/main/java/com/evision/ragservice/application/IndexWorker.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/GlobalExceptionHandler.java
    - compose.app.yaml
decisions:
  - Single validated ChatProperties record instead of scattered @Value params
  - No Executor/TaskScheduler beans, so Boot's scheduler for IndexWorker is unchanged
metrics:
  tasks: 3
  tests: 158 passing (baseline 149)
---

# Phase 10 Plan 01: Generation contracts and plumbing Summary

Framework-free generation contracts (ChatPort, EventSink, first-reason-wins Cancellation), an in-memory GenerationGate that pauses IndexWorker.tick() while any slot is held, rag.chat.* configuration in both yml files plus compose/.env, and five new error mappings.

## Commits

- 30e61af: domain types, ports, Cancellation and its test
- a546b68: ChatProperties, GenerationConfig, GenerationGate, GenerationTimers, IndexWorker pause, handlers, yml, tests
- compose/.env commit: RAG_CHAT_* entries

## Deviations from Plan

- TextTooLargeException is mapped via HttpStatus.PAYLOAD_TOO_LARGE (413) with reason TEXT_TOO_LARGE, as specified.
- GenerationGateTest passes null for the Duration properties it does not use.
- `docker compose config` needed dummy required env vars (RAG_ADMIN_KEY etc.) set inline to validate.
- The first Task 1 commit briefly carried a Co-Authored-By trailer; it was amended away before anything else was built on it.

## Known Stubs

None.

## Threat Flags

None.

## Self-Check: PASSED

Full rag-service suite: 158 tests, 0 failures, BUILD SUCCESS.
