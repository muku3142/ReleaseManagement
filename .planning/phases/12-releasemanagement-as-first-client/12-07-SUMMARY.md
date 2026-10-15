---
phase: 12-releasemanagement-as-first-client
plan: 07
subsystem: assistant
tags: [tool-loop, rag, spring]
requires: [12-01, 12-02, 12-04]
provides: [AssistantService]
affects: [12-10, 13]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/assistant/AssistantService.java
    - src/test/java/com/evision/releasemgmt/application/assistant/AssistantServiceTest.java
decisions:
  - "Round number on ToolRun is the POST attempt number (1-based)"
metrics:
  completed: 2026-10-15
---

# Phase 12 Plan 07: AssistantService Summary

Caller-side Phase 11 tool loop: asks via RagChatPort with all tool definitions and the four rm-* collections, runs requested tools sequentially in-process, resends each SignedTurn unchanged with results, caps at maxRounds+1 POSTs, and returns tools run and sources for Phase 13.

## Tasks

| Task | Commit |
|------|--------|
| 1. AssistantService + scripted-fake test (9 tests green) | 8bdc158 |

## Behaviour notes

- Unknown tool, ToolArgumentException, other RuntimeException, and results over tool-result-max-chars all become isError results; unexpected exceptions give a generic "tool failed".
- Logs carry tool name, round, error flag and duration only. Nothing is stored.
- No dependency on RagIndexPort, IndexSyncer or IndexSweep (grep clean).

## Deviations from Plan

None. Verification ran only the plan's own test class plus test-compile (per orchestrator memory constraint); the full suite is left to the orchestrator after merge.

## Self-Check: PASSED
