---
phase: 11-tool-call-protocol
plan: 05
subsystem: rag-service
tags: [chat-service, tool-calls, gate, logging]
requires: [11-01, 11-02, 11-03, 11-04]
provides: [ChatService.Command.tools/toolTurns, ChatService.Plan.tools/toolTurns/questionId, tool-aware prepare, run tools branch]
affects: [11-06]
key-files:
  modified: [ChatService.java, FakeChatPort.java, ChatServiceTest.java]
decisions:
  - "Command keeps its 5-arg constructor; null tool lists normalise to empty in the compact constructor"
  - "Generation is a bounded 2-attempt loop; the single correction retry shares the per-request watchdog"
  - "guard.finish() is not called on the tool-call path (tokens before a tool call are provisional)"
metrics:
  tasks: 2
  tests: "326 green hermetic (was 306)"
completed: 2026-10-15
---

# Phase 11 Plan 05: ChatService tool protocol Summary

Stateless tool loop in `ChatService`: prepare rejects forged or oversized tool input before Ollama and the gate; run emits a signed `tool_call` after freeing the slot, with one correction retry, a hard round cap, tool citations and metadata-only logging.

## Commits
- 33345d6 feat(11-05): wire the stateless tool protocol into ChatService (Tasks 1 and 2 together)

## What changed
- `prepare`: definitions (400), `verifyChain` then `requireValidResume` (400 / 413), `requireToolBudget` (413), and only then `assertReady`, `assertToolsReady` and the gate. `questionId` is the first turn's id on a resume, else the random requestId.
- `run`: score-gate refusal skipped when tools are present (D-11-06); `chatWithTools` prompt; tools omitted and `FINAL_ROUND_RULE` added at the cap; `releaseOnce` (AtomicBoolean) is the only `gate.release` call, invoked before `tool_call` is sent and again (no-op) in `finally`.
- Correction: invalid calls (unknown tool, schema, too many, duplicate id, repeat, any call at the cap) get one retry via `withCorrection`; a second failure sends `error TOOL_CALL_INVALID`. The retry is not a round.
- Log line: existing fields and order kept, appended `tools round questionId callsEmitted callNames unknownCallNames correctionRetry toolResultChars evalMs`; `promptVersion` is p11-v1 for tool requests. Only validated names and counts are logged.
- `FakeChatPort`: `completions` and `tokenScript` per-call scripting.
- `ChatServiceTest`: 20 new cases (prepare rejections, signed event, early release with immediate resume, round trip with TOOL citation and second retrieval, gate skip, NO_ANSWER, cap, correction paths, deadline, log canary).

## Deviations from Plan
- Worktree base was behind the orchestrator target; reset to bab6077 per the startup instruction (clean tree).
- Tasks 1 and 2 landed in one commit because both edit the same `ChatService` regions (record, constructor, prepare, run, logRun) and the tests share a helper block.
- The "round=1" log assertion is exercised on a resume with one answered turn that emits two new calls, since `round` is defined as turns answered.

## Verification
- Full hermetic suite: 326 tests, 0 failures. openapi.json untouched. `verifyChain(` precedes `chat.assertReady();`; `gate.release(` appears once. Golden profile not run.

## Known Stubs
None.

## Self-Check: PASSED
