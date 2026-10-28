---
phase: 13-assistant-screen-quality-gate
plan: 08
subsystem: api
tags: [assistant, sse, spring-mvc, security]
requires:
  - phase: 13-assistant-screen-quality-gate
    provides: ChatListener.heartbeat/toolStarted, ClientGoneException, AssistantAnswer fields, streamDeadline (13-01, 13-03, 13-05)
provides:
  - POST /api/assistant/chat streaming browser event protocol v1 (status, discard, token, done, error, hb)
  - BrowserStreamListener, generic over RagChatPort.ChatListener (reusable by 14-07)
  - AssistantSourceViews server-side citation view with validated ids
affects: [13-02 client, 14-07]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/web/AssistantChatController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/BrowserStreamListener.java
    - src/main/java/com/evision/releasemgmt/adapters/web/AssistantSourceViews.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/AssistantChatRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/AssistantChatEvents.java
    - src/test/java/com/evision/releasemgmt/adapters/web/AssistantChatStreamIT.java
    - src/test/java/com/evision/releasemgmt/adapters/web/AssistantSourceViewsTest.java
  modified:
    - src/test/java/com/evision/releasemgmt/AssistantSwitchOffIT.java
    - src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java
key-decisions:
  - "SecurityConfig unchanged: the ASYNC dispatcher permit was not needed"
requirements-completed: [ASST-02, ASST-06]
completed: 2026-10-18
---

# Phase 13 Plan 08: Browser chat SSE endpoint Summary

`POST /api/assistant/chat` streams the orchestrator to the browser over the cookie login on a virtual thread, with heartbeats, a listener deadline and disconnect-to-ClientGoneException handling.

## Tasks

| Task | Commit |
|------|--------|
| 1 Request DTO, event records, AssistantSourceViews | fd17bda |
| 2 Controller, BrowserStreamListener, real-HTTP IT | e176e39 |
| 3 Switch-off and architecture guards | f5d41e9 |

## ASYNC permit

Not needed. AssistantChatStreamIT runs over real HTTP (RANDOM_PORT, java.net.http.HttpClient) with root logger at DEBUG, and every test asserts no log line contains AccessDeniedException or "already committed". SecurityConfig was not modified.

## Verification

Full backend suite: 1106 tests, 0 failures (`mvn -B -P '!frontend' clean test`), baseline 1084. IT cases cover event order and headers, BUSY and REJECTED errors, scoped release, 401/400/404 before any stream, disconnect reaching the port as ClientGoneException within 3 s, DEADLINE error (stream-deadline PT2S in the IT), and a log-sentinel check (question, token, tool argument, api key).

## Deviations from Plan

- [Rule 1] A request with no cookie at all returns 403, not 401: the CSRF filter answers before authentication, as for every other POST under /api. An invalid/expired access cookie returns 401 problem+json. The IT asserts both; the plan's "no cookie -> 401" wording was inaccurate.
- IT stream deadline is PT2S (not PT1S) to leave margin; the fake heartbeats for 6 s.
- AssistantSwitchOffIT looks up the `requestMappingHandlerMapping` bean by name because actuator adds a second RequestMappingHandlerMapping.
- ArchitectureBoundaryTest: REAL_CHAT_PATH now uses a name set (AssistantService, AssistantChatController, BrowserStreamListener, AssistantStatusController).
- Edited files keep CRLF; the worktree was reset to base 15e1e86 at start.

## Notes for others

- BrowserStreamListener.finish/sendTerminal are package-private; 14-07 can reuse the class within adapters.web.
- Tool arguments in `done.tools` are the effective arguments (forced release scope included).

## Known Stubs

None.

## Self-Check: PASSED
