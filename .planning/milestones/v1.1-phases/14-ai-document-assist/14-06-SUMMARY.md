---
phase: 14-ai-document-assist
plan: 06
subsystem: assistant
tags: [transform, ai-revision, size-preflight, safe-03]
requires: [14-03, 14-04, 14-05]
provides: [AssistantTransformService, TransformTooLargeException, InvalidAssistRequestException]
affects: [14-07]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/assistant/TransformTooLargeException.java
    - src/main/java/com/evision/releasemgmt/domain/assistant/InvalidAssistRequestException.java
    - src/main/java/com/evision/releasemgmt/application/assistant/AssistantTransformService.java
    - src/test/java/com/evision/releasemgmt/application/assistant/AssistantTransformServiceTest.java
requirements-completed: [ASST-09, ASST-10]
metrics:
  tests: "1222 green (baseline 1202)"
completed: 2026-10-18
---
# Phase 14 Plan 06: AssistantTransformService Summary

`AssistantTransformService` prepares a transform from the stored latest revision plus a configured or custom instruction, refuses oversize documents before any model call, runs the transform, and saves only Clean up and Custom results as AI revisions with a truthful recorded instruction. Logs are metadata only.

## What was built
- `prepare`: finds the latest snapshot (DocumentNotFoundException), checks the source revision (RevisionSupersededException), resolves the instruction (config text for CLEAN_UP and SUMMARIZE, client text ignored; CUSTOM required, at most instruction-max-chars, no NUL), then checks size last and throws `TransformTooLargeException(characters, maxCharacters)`.
- `run`: passes Done and Failed through, fills `TooLarge(null)` with the pre-flight limit, logs one INFO line (OK, TRUNCATED, TOO_LARGE_SERVER, the failure reason, CLIENT_GONE with rethrow). Pre-flight refusal logs TOO_LARGE_PREFLIGHT.
- `saveAiRevision`: rejects SUMMARIZE (SUMMARIZE_REJECTED log) and NUL markdown, records the configured text for CLEAN_UP and the user's text for CUSTOM, propagates RevisionSupersededException. The UI-only truncation Save block is noted in a code comment.
- `limits()`: config-only numbers plus per-preset max chars.
- `Prepared.toString` prints sizes only. The estimator is constructed inside the service (not a bean). No FactDiff class, per orchestrator decision 3.

## Commits
- Tasks 1 and 2 landed in one commit because both live in the same new service and test file.

## Deviations from Plan
None. The 4-arg constructor omits the plan's Clock and estimator parameters: no clock is needed here (AiRevisionService owns timestamps) and the estimator is built from DocumentAssist as 14-04 specified.

## Verification
Full `mvn -B -P '!frontend' clean test`: 1222 tests, 0 failures. ArchitectureBoundaryTest passes.

## Self-Check: PASSED
