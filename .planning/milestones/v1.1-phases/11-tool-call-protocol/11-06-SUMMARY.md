---
phase: 11-tool-call-protocol
plan: 06
subsystem: rag-service
tags: [web, dto, openapi, problem-json, docs]
requires: [11-05]
provides: [ChatRequest.tools/toolTurns, TOOL_TURN_INVALID 400, TOOL_RESULT_TOO_LARGE 413, regenerated openapi.json, docs/docker.md tool protocol]
affects: [12-10]
key-files:
  modified: [ChatRequest.java, ChatController.java, GlobalExceptionHandler.java, ChatApiIT.java, rag-service/openapi.json, docs/docker.md]
decisions:
  - "Wire backstops 20 tools / 20 turns / 16 calls; configured caps stay in the service"
  - "Result content length is not Bean-Validated; the service returns 413"
metrics:
  tasks: 2
  tests: "331 green hermetic (was 326)"
completed: 2026-10-15
---

# Phase 11 Plan 06: Tool protocol over HTTP Summary

`POST /v1/chat` now accepts `tools` and `toolTurns`, maps rejected turns and oversized results to 400 `TOOL_TURN_INVALID` and 413 `TOOL_RESULT_TOO_LARGE`, and the contract and caller docs describe the protocol.

## Commits
- 02113b9 feat(11-06): expose the tool protocol on POST /v1/chat
- 9f5086e docs(11-06): regenerate openapi.json and document the tool protocol

## What changed
- `ChatRequest`: optional `tools` and `toolTurns` with nested `Tool`, `ToolTurnDto`, `Call`, `Result` (`@JsonProperty("isError")`), `Source` (url `https?://\S{1,2040}`); every nested toString is content-free; `toCommand()` maps to the 7-arg `Command`.
- `GlobalExceptionHandler`: two new handlers. `ChatController.EVENTS`: `tool_call`, citation `kind`, `TOOL_CALL_INVALID`, new problem reasons.
- `ChatApiIT`: 5 new cases (round trip with a TOOL citation, tamper/invented/5-turn rejection with zero Ollama requests, 413 with zero requests, malformed tool input, toString canary); happy path now asserts `kind` PASSAGE.
- `openapi.json` regenerated once. Diff: chat and transform event descriptions, `tools`/`toolTurns` on `ChatRequest`, new schemas `Tool`, `ToolTurnDto`, `Call`, `Result`, `Source`. Nothing else changed.
- `docs/docker.md`: `### Tool calls: protocol` and two Accepted-risks bullets (tool-result injection, replay).

## Deviations from Plan
- Worktree base was behind the orchestrator target (7f54818 vs 3089dd3); reset to 3089dd3 on a clean tree per the startup instruction.
- The shared event description also appears on `/v1/transform`, so its openapi description mentions `tool_call` too (single shared constant).

## Findings
- The new component schemas have generic names (`Call`, `Result`, `Source`, `Tool`); a future DTO with the same simple name would collide in springdoc.

## Verification
- Full hermetic suite: 331 tests, 0 failures. `toolTurns` present in openapi.json. Golden profile, Ollama and frontend not touched. STATE.md and ROADMAP.md untouched.

## Known Stubs
None.

## Self-Check: PASSED
