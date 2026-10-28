---
phase: 13-assistant-screen-quality-gate
plan: 05
subsystem: api
tags: [assistant, rag, sse, abort-chain]
requires:
  - phase: 13-assistant-screen-quality-gate
    provides: ChatListener.heartbeat, ClientGoneException, AssistantAnswer.historyTrimmed (13-03)
provides:
  - RagChatAdapter forwards SSE comment lines to listener.heartbeat()
  - Listener exceptions propagate unchanged out of RagChatAdapter.chat and close the upstream exchange
  - ChatOutcome.Answered(historyTrimmed, model, promptHash), ChatOutcome.Failed(retryAfterSeconds)
  - AssistantAnswer(historyTrimmed OR-ed with app cap, model, promptHash, retryAfterSeconds)
affects: [13-08, 13-12, 13-14]
key-files:
  modified:
    - src/main/java/com/evision/releasemgmt/domain/assistant/ChatOutcome.java
    - src/main/java/com/evision/releasemgmt/domain/assistant/AssistantAnswer.java
    - src/main/java/com/evision/releasemgmt/application/assistant/AssistantService.java
    - src/main/java/com/evision/releasemgmt/adapters/rag/RagChatAdapter.java
    - src/test/java/com/evision/releasemgmt/application/assistant/AssistantServiceTest.java
    - src/test/java/com/evision/releasemgmt/adapters/rag/RagChatAdapterTest.java
key-decisions:
  - "Listener exceptions are wrapped in a private ListenerFailure inside the exchange lambda and unwrapped outside it. Without this, an UncheckedIOException or JacksonException thrown by the listener would be remapped by RestClient or by the adapter's own catches to OFFLINE or FAILED."
requirements-completed: [ASST-02, ASST-08]
completed: 2026-10-18
---

# Phase 13 Plan 05: RagChatAdapter abort chain and outcome fields Summary

The adapter now forwards rag-service heartbeats, lets listener exceptions cancel the upstream stream, parses Retry-After on 429, and carries `historyTrimmed`, `model` and `promptHash` from the rag `done` event through to `AssistantAnswer`.

## Tasks

| Task | Commit |
|------|--------|
| 0 Phase 12 gate (GATE-OPEN) | none |
| 1 Outcome and answer fields, AssistantService pass-through | 655f189 |
| 2 Heartbeat, listener exception propagation, Retry-After, done meta | 827228a |

## Verification

Full backend suite: 1084 tests, 0 failures (`mvn -B -P '!frontend' clean test`), up from 1078. ArchitectureBoundaryTest green. New WireMock tests cover:
- Heartbeats: two comment lines give two `heartbeat()` calls.
- Done metadata: present and absent.
- Retry-After: numeric, missing and HTTP-date.
- Listener exceptions: `ClientGoneException` on the 2nd token propagates as the same instance and the 3rd token is never delivered. An `IllegalStateException` from `heartbeat()` also propagates as-is.

## Deviations from Plan

- The plan's heartbeat test uses an anonymous `RecordingListener`. I made it a non-final class so a `HeartbeatCounter` subclass could reuse it. Test-only change.
- Task 1 asserted Retry-After 5 only after Task 2 wired it, so the Task 1 test temporarily expected `null` and Task 2 switched it to 5.
- Edited files keep CRLF endings. The worktree started on an older base and was reset to 5e3407d per the startup check.

## Notes for others

- Closing the upstream stream relies on `RestClient.exchange` closing the response when the lambda exits by exception. The live CLIENT_GONE check is 13-14.
- The stream deadline for a stall mid-stream is still the listener's job (13-08), as noted in 12-10.
- No log lines were added.

## Known Stubs

None.

## Self-Check: PASSED
