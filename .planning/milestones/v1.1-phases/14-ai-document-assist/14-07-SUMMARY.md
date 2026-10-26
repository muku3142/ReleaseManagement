---
phase: 14-ai-document-assist
plan: 07
subsystem: api
tags: [assistant, transform, sse, ai-revision, switch-off]
requires: [14-06, 13-08]
provides: [AssistantTransformController, transform SSE protocol, limits endpoint, ai-revisions save endpoint]
affects: [14-09 client]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/web/AssistantTransformController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/AssistantTransformRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/SaveAiRevisionRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/AssistantTransformEvents.java
    - src/test/java/com/evision/releasemgmt/adapters/web/AssistantTransformStreamIT.java
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
    - src/test/java/com/evision/releasemgmt/AssistantSwitchOffIT.java
    - src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java
requirements-completed: [ASST-09, ASST-10]
metrics:
  tests: "1253 green (baseline 1240)"
completed: 2026-10-18
---

# Phase 14 Plan 07: Transform and ai-revisions endpoints Summary

`GET /api/assistant/transform/limits`, `POST /api/assistant/transform` (SSE relay on a virtual thread through the reused `BrowserStreamListener`) and `POST /api/assistant/documents/ai-revisions` (201 `{revision}`) are served behind the cookie login, proven over real HTTP.

## Commits
- f949316 feat: DTOs, controller, 400/409/413 problem mappings
- test commit (next in log): `AssistantTransformStreamIT`, switch-off and architecture guards

## Decisions and notes
- Token events reuse `AssistantChatEvents.TokenEvent` (identical shape); new `AssistantTransformEvents` holds done, error (with characters/maxCharacters), saved and limits views.
- Done `complete` = finishReason "stop" and not truncated; REJECTED is reported as FAILED; a rag TooLarge becomes error TOO_LARGE with the pre-flight characters and the service-filled max.
- `GlobalExceptionHandler` gained handlers for RevisionSuperseded (409), InvalidAssistRequest (400) and TransformTooLarge (413 with `characters`, `maxCharacters`). DocumentNotFound already mapped to 404.
- Guards were added additively: a new test method in `AssistantSwitchOffIT` (no transform controller/service/port bean, no mapping for the three paths) and a new `transformPathCannotWriteTheIndexOnTheRealClasses` rule in `ArchitectureBoundaryTest`; the existing chat-path set was not edited, to keep merges with 13-12 clean.
- SecurityConfig unchanged; no AccessDeniedException on async completion (asserted in the IT).

## Deviations from Plan
- [Rule 1] The document generate endpoint returns 201, not 200; the SC3 case asserts 201.
- The log-hygiene check ignores `org.hibernate.*` loggers: at root DEBUG Hibernate dumps whole entities (document markdown) on flush. That is the test's log level, not application logging; application, Spring and Tomcat lines are still checked for all sentinels and the api key.
- Missing cookie yields 403 (CSRF first), invalid cookie 401, as noted by 13-08.
- Edited CRLF files kept CRLF; the worktree was reset to a183eae at start.

## Known Stubs
None.

## Self-Check: PASSED
