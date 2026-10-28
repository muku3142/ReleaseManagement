---
phase: 14-ai-document-assist
plan: 04
subsystem: assistant
tags: [config-properties, rag, document-assist, size-estimator]
requires:
  - phase: 13-assistant-screen-quality-gate
    provides: AssistantProperties with Prompts, Starters, historyMaxTurns, streamDeadline (13-01)
provides:
  - app.assistant.document-assist config (six instruction texts, num-ctx, chars-per-token, system-tokens, instruction-max-chars)
  - TransformMode enum
  - TransformSizeEstimator mirroring rag-service's transform limit
affects: [14-06, 14-07, document assist UI]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/assistant/TransformMode.java
    - src/main/java/com/evision/releasemgmt/application/assistant/TransformSizeEstimator.java
    - src/test/java/com/evision/releasemgmt/application/assistant/TransformSizeEstimatorTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/config/AssistantProperties.java
    - src/main/resources/application.yml
    - src/test/resources/application.yml
    - compose.app.yaml
    - .env.example
    - docs/docker.md
    - src/test/java/com/evision/releasemgmt/config/AssistantPropertiesTest.java
    - rag-service/src/test/java/com/evision/ragservice/application/PromptBuilderTest.java
key-decisions:
  - "DocumentAssist is a sibling component of Prompts on AssistantProperties; instruction(mode, type) throws for CUSTOM"
  - "TransformSizeEstimator is a plain class constructed from DocumentAssist (no bean); 14-06 constructs it inside the service"
requirements-completed: [ASST-09]
duration: 25min
completed: 2026-10-18
---

# Phase 14 Plan 04: Document-assist config and size estimator Summary

Six per-mode, per-type instruction texts and the rag-service limit mirrors are validated `app.assistant.document-assist` config, and `TransformSizeEstimator` reproduces rag-service's transform limit exactly (shared boundary vectors in both modules).

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 0 Phase 13 gate | n/a | 13-01-SUMMARY present, `record Starters` present |
| 1 Config block | 65bdc79 | Record, both ymls with identical text, compose/.env.example/docs, tests |
| 2 Estimator | dd78373 | Vectors 14->5523, 300->5382, 301->5379, 330->5367, 500->5280, 2000->4530; rag side asserts 1841 and 1510 tokens |

## Verification

Backend: 1097 tests, 0 failures. rag-service: 342 tests, 0 failures. `rag-service/src/main` unchanged.

## Deviations from Plan

- [Rule 3 - Blocking] The new record component made `AssistantServiceTest` and `AssistantStatusControllerTest` constructions fail (enabled=true now validates document-assist); both pass a valid DocumentAssist.
- The worktree started on an older base; reset to 7063e88 per the startup check.
- Working-tree CRLF files were converted to LF (the index is LF) so edits and diffs are clean.
- Estimator is not a bean; it takes `DocumentAssist` in its public constructor.

## Known Stubs

None. Instruction texts are the research drafts; 14-15 tunes them.

## Self-Check: PASSED
