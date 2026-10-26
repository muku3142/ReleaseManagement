---
phase: 13-assistant-screen-quality-gate
plan: 10
subsystem: rag-service
tags: [sse, done-event, openapi, heartbeat]
requires: [13-09]
provides: [done.historyTrimmed, done.model, done.promptHash, heartbeat-5s]
affects: [rag-service, docs]
key-files:
  modified:
    - rag-service/src/main/java/com/evision/ragservice/domain/StreamEvents.java
    - rag-service/src/main/java/com/evision/ragservice/application/ChatService.java
    - rag-service/src/main/java/com/evision/ragservice/application/TransformService.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/ChatController.java
    - rag-service/src/main/resources/application.yml
    - rag-service/openapi.json
    - docs/docker.md
    - rag-service/src/test/java/com/evision/ragservice/application/ChatServiceTest.java
    - rag-service/src/test/java/com/evision/ragservice/application/TransformServiceTest.java
requirements: [ASST-07, SAFE-04]
metrics:
  tasks: 3
  tests: 355 green (baseline 352)
---

# Phase 13 Plan 10: done fields, 5 s heartbeat, contract and docs Summary

Every rag-service `done` event (answer, GATE/MODEL refusal, tool_calls, transform) now carries `historyTrimmed`, `model` and `promptHash`, and the heartbeat is 5 s.

## What changed

- `StreamEvents.Done` gained `boolean historyTrimmed, String model, String promptHash` appended after the six existing fields (order unchanged); `forToolCalls` takes them too.
- `ChatService`: `historyTrimmed = prompt.historyTurnsUsed() < plan.history().size()` via a private helper; GATE refusal reports false (no prompt built); `refuse` now takes the flag. `model` is `props.model()`, `promptHash` is the field already holding `rules.hash()`. `TransformService` reports false, model, hash.
- `rag.chat.heartbeat-interval` is `PT5S` in main application.yml (test yml still PT1S).
- `ChatController.EVENTS` documents the new fields; `rag-service/openapi.json` regenerated once.
- `docs/docker.md`: new done fields, 5 s heartbeat, "Prompt rule text" paragraph (rag.prompts.* keys, RAG_PROMPTS_* env names, fences fixed, startup validation, hash-change rerun rule).

## Verification

- Full rag-service hermetic suite: 355 tests, 0 failures (OpenApiContractIT passes without the update flag).
- openapi.json diff: 2 lines changed (the `EVENTS` description on /v1/chat and /v1/transform), nothing else.
- Tests added: done carries model/hash and historyTrimmed false when all history is kept; true when the budget keeps fewer turns; GATE and MODEL refusals and the tool_calls done carry model and hash; transform done.

## Commits

- ac6b7e3: feat(13-10): add historyTrimmed, model and promptHash to the done event
- Task 2 commit: feat(13-10): heartbeat PT5S, document done fields and prompt rules

## Deviations from Plan

None. Note: `docs/quality-gate.md`, referenced by the docs text as the plan mandates, does not exist yet; later Phase 13 plans (12-14) own it.

## Self-Check: PASSED
