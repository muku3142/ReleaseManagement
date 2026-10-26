---
phase: 13-assistant-screen-quality-gate
plan: 03
subsystem: api
tags: [assistant, rag, tool-loop, scope]
requires:
  - phase: 13-assistant-screen-quality-gate
    provides: AssistantProperties prompts, historyMaxTurns (13-01)
provides:
  - ChatListener.heartbeat() and toolStarted(String) defaults; listener exceptions propagate out of ask()
  - ClientGoneException
  - AssistantAnswer.historyTrimmed
  - release-scoped question, forced tool scope, description overrides, history cap in AssistantService
affects: [13-05, 13-04 stream controller]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/assistant/ClientGoneException.java
  modified:
    - src/main/java/com/evision/releasemgmt/ports/RagChatPort.java
    - src/main/java/com/evision/releasemgmt/domain/assistant/AssistantAnswer.java
    - src/main/java/com/evision/releasemgmt/application/assistant/AssistantService.java
    - src/test/java/com/evision/releasemgmt/application/assistant/AssistantServiceTest.java
key-decisions:
  - "Scoped question is filled in a single pass over the template (split at {question}); chained String.replace would substitute a release name containing {question} or a question containing {release} twice"
requirements-completed: [ASST-05, ASST-07]
completed: 2026-10-18
---

# Phase 13 Plan 03: Scoped orchestrator Summary

AssistantService now scopes a chat to one release on retrieval and on every tool call, applies configured tool descriptions, caps history at 20 with a trimmed flag, and exposes toolStarted/abort hooks.

## Tasks

| Task | Commit |
|------|--------|
| 1 Listener callbacks, ClientGoneException, historyTrimmed | 77ea805 |
| 2 Scope, forced tool arguments, overrides, history cap, toolStarted | 9dc8efe |

## Verification

Full backend suite: 1078 tests, 0 failures (`mvn -B -P '!frontend' clean test`), ArchitectureBoundaryTest green.

## Deviations from Plan

- [Rule 1] The plan said fill {release} first then {question} with String.replace. That still double-substitutes a release named `x{question}y`, so the template is filled in a single pass instead. Same observable output otherwise.
- The worktree started on an older base and was reset to the required base 2637129 per the startup check.
- Edited files keep CRLF endings.

## Notes for others

- Tool description overrides are now wired: AssistantService sends `app.assistant.prompts.tool-descriptions` text (else the tool's description()) in the ToolDefinition. The tool classes' own description() text is unchanged.
- AssistantService constructor gained `ReleaseService` and `AssistantProperties`; `ask(...)` signature is unchanged (AssistantLiveCheck compiles).
- A scoped ask for an unknown release throws ReleaseNotFoundException before any chat call; the stream controller must map it.
- RagChatAdapter was not touched; it must call listener.heartbeat() (13-05).

## Known Stubs

None.

## Self-Check: PASSED
