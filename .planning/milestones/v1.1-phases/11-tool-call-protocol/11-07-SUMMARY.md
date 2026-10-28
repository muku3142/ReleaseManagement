---
phase: 11-tool-call-protocol
plan: 07
subsystem: rag-service
tags: [tests, sse, log-hygiene, archunit]
requires: [11-06]
provides: [real-Tomcat evidence for gate release, per-POST deadline, forged-turn rejection; SAFE-03 tool-path log hygiene; java.net outbound ArchUnit rule]
key-files:
  modified: [SseStreamingIT.java, LogHygieneIT.java, ArchitectureBoundaryTest.java]
metrics:
  tasks: 2
  tests: "335 green hermetic (was 331)"
completed: 2026-10-15
---

# Phase 11 Plan 07: Runtime bounds and hygiene on a real server Summary

Test-only plan: the tool protocol's gate release, per-POST deadline, forged-turn rejection and log hygiene are now proven over real Tomcat, and raw java.net outbound clients are fenced by ArchUnit.

## Commits
- 64e51f7 test(11-07): SseStreamingIT tool cases and the java.net ArchUnit rule
- LogHygieneIT commit: see git log (test(11-07): log hygiene canaries for the tool path)

## What changed
- `SseStreamingIT`: `immediateResumeAfterAToolRoundIsNeverRefused` (20 repeats, resume right after `done`, never 429, final finishReason `stop`), `eachToolPostHasItsOwnDeadline` (stalled resume stream ends `DEADLINE`, gate frees), `forgedTurnNeverReachesOllama` (400 `TOOL_TURN_INVALID`, `chatRequests()` unchanged).
- `ArchitectureBoundaryTest`: `noRawJavaNetClientsOutsideTheOllamaAdapterAndConfig` (URL, URLConnection, HttpURLConnection, Socket). No existing violation.
- `LogHygieneIT`: `driveEveryRequestKind()` now runs a full tool round trip (canary in tool description, model arguments, result content and source title), one forged resume (400) and one oversized result (413). `assertNoCanary` also rejects the captured signature and the configured signing secret in messages, arguments and throwable messages, at default level and DEBUG. `assertMetadataLines` requires a chat line with `promptVersion=p11-v1` and `round=1`.

## Deviations from Plan
- Worktree base was behind the orchestrator target (7f54818 vs 1434a4d); reset to 1434a4d on a clean tree per the startup instruction.
- The `round=1` assertion matches `round=1 ` (log field is the number of tool turns in the request, so it is 0 on the first POST and 1 on the resume).

## Verification
- Full hermetic suite: 335 tests, 0 failures, 0 errors. `rag-service/openapi.json` untouched. STATE.md and ROADMAP.md untouched; golden profile, Ollama and frontend not run.

## Known Stubs
None.

## Self-Check: PASSED
