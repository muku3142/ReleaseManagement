---
phase: 13-assistant-screen-quality-gate
plan: 09
subsystem: rag-service
tags: [prompts, configuration, SAFE-04]
requires: [13-05]
provides: ["rag.prompts.* rule-text configuration", "PromptRules.hash() in generation logs"]
affects: [13-10]
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/application/PromptRules.java
    - rag-service/src/test/java/com/evision/ragservice/application/PromptRulesTest.java
  modified:
    - rag-service/src/main/java/com/evision/ragservice/config/GenerationConfig.java
    - rag-service/src/main/java/com/evision/ragservice/application/PromptBuilder.java
    - rag-service/src/main/java/com/evision/ragservice/application/ChatService.java
    - rag-service/src/main/java/com/evision/ragservice/application/TransformService.java
    - rag-service/src/main/resources/application.yml
    - rag-service/src/test/java/com/evision/ragservice/application/PromptBuilderTest.java
    - rag-service/src/test/java/com/evision/ragservice/application/ChatServiceTest.java
    - rag-service/src/test/java/com/evision/ragservice/application/TransformServiceTest.java
decisions:
  - "Java defaults in Prompts.java are the single source; main application.yml copy is held byte-identical by a drift test; test yml has no prompts block (D-13-18)"
  - "Data fences remain static final in PromptBuilder (SAFE-02)"
metrics:
  tasks: 3
  tests: "rag-service 351 green (baseline 341)"
completed: 2026-10-18
---

# Phase 13 Plan 09: PromptRules Summary

Prompt rule text (chat, transform, tool rules, final-round rule, correction) is now `rag.prompts.*` configuration with the
`Prompts` constants as defaults, startup validation, and a 12-hex SHA-256 `promptHash` appended to every chat and transform
generation log line.

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 0 Phase 11 gate | - | 11-07/08/09 SUMMARYs present per orchestrator |
| 1 PromptRules + yml block | 3777e83 | record, validation (blank key, NO_ANSWER, exactly one %s), hash, main yml block, drift tests |
| 2 Wiring + logging | 88e939f | PromptBuilder takes PromptRules (old constructors delegate to defaults); ChatService/TransformService take PromptRules and log `promptHash=` as last field |

## Verification

- Full rag-service hermetic suite: 351 tests, 0 failures. No golden, Ollama or containers run.
- `Prompts.<TEXT>` reads in main code appear only in PromptRules.java.
- New PromptBuilderTest cases were appended; existing tests untouched (kept clear of 14-04's additions).

## Deviations from Plan

None. `ChatService.logRun` and the transform log also gained a stored `promptHash` field computed once at construction.

## Notes

- Did not touch the `${RAG_CHAT_MODEL:...}` line in application.yml; the prompts block was appended at the end of `rag:`.
- Python was unavailable, so the yml block was written by hand; the drift test proves it equals the Java defaults.
- 13-10 can read `rules.hash()` for the `done` event; ChatService/TransformService already hold it as `promptHash`.

## Self-Check: PASSED
