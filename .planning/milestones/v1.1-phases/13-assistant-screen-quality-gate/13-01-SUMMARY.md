---
phase: 13-assistant-screen-quality-gate
plan: 01
subsystem: api
tags: [spring-boot, config-properties, rag, assistant]
requires:
  - phase: 12-releasemanagement-as-first-client
    provides: AssistantProperties, RagIndexAdapter, conditional assistant wiring
provides:
  - app.assistant prompts, tool descriptions, starters, history-max-turns, stream-deadline (both ymls, validated at startup)
  - RagStatusPort and RagIndexAdapter.lastIndexedAt
  - GET /api/assistant/status (always) and GET /api/assistant/freshness (assistant on)
  - problem+json 503 ASSISTANT_UNAVAILABLE for RagServiceException
affects: [13-02, 13-03, assistant UI, scoped chat]
tech-stack:
  added: []
  patterns: [ObjectProvider for beans that exist only when the switch is on]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/ports/RagStatusPort.java
    - src/main/java/com/evision/releasemgmt/adapters/web/AssistantStatusController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/AssistantStatusResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/AssistantFreshnessResponse.java
    - src/test/java/com/evision/releasemgmt/adapters/web/AssistantStatusControllerTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/config/AssistantProperties.java
    - src/main/java/com/evision/releasemgmt/adapters/rag/RagIndexAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
    - src/main/resources/application.yml
    - src/test/resources/application.yml
    - compose.app.yaml
    - docs/docker.md
key-decisions:
  - "scoped-question has no env placeholder: a ${...:default} containing {release}/{question} braces risks misbinding, so it is a plain quoted yml value overridable via relaxed binding (APP_ASSISTANT_PROMPTS_SCOPEDQUESTION) or SPRING_APPLICATION_JSON"
  - "Only RM_ASSISTANT_STREAM_DEADLINE is added to compose; lists and multi-line text are not"
requirements-completed: [ASST-02, ASST-07, ASST-08]
duration: 25min
completed: 2026-10-18
---

# Phase 13 Plan 01: Assistant config and status endpoints Summary

Prompts, starters, history cap and stream deadline are now fail-fast-validated `app.assistant.*` config, and the frontend can read `/api/assistant/status` and `/api/assistant/freshness`.

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 1 Config surface | d68ae43 | AssistantProperties extended; both ymls, compose, docs; binding tests for both ymls |
| 2 Status and freshness | 192d926 | RagStatusPort, adapter, controller, 503 handler, switch-off IT |

## Verification

Full backend suite: 1071 tests, 0 failures (`mvn -B -P '!frontend' test`). ArchitectureBoundaryTest and AssistantSwitchOffIT green.

## Deviations from Plan

- The worktree started on an older base; reset to the required base commit aa1c4e1 per the startup check.
- Freshness off-switch (404) and status off-switch are covered by direct controller construction in AssistantStatusControllerTest plus AssistantSwitchOffIT, rather than a second WebMvcTest context.
- The generic rag call helper is named `fetch` (a `call` overload with Runnable/Supplier lambdas is ambiguous).
- Edited files keep their CRLF line endings.

## Known Stubs

None. The tool descriptions in yml are not yet consumed by the tool classes; wiring is outside this plan.

## Self-Check: PASSED
