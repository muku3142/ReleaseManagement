# Phase 11: Tool-Call Protocol - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-14
**Phase:** 11-tool-call-protocol
**Areas discussed:** Resuming a paused tool round, Tools vs retrieval, Loop limits & failures, Model bake-off candidates

---

## Resuming a paused tool round

| Question | Options | Selected |
|----------|---------|----------|
| How the app continues | Resend full transcript / Continuation token / You decide | Resend full transcript |
| Trust the echoed tool-call turn | Sign it (HMAC) / Trust the transcript / You decide | Sign the tool-call turn |
| Several calls per round | Yes, app runs all / One call per round / You decide | Yes, the app runs them all |

## Tools vs retrieval

| Question | Options | Selected |
|----------|---------|----------|
| Retrieval when tools exist | Pre-retrieve + tools / Retrieval as built-in tool / You decide | Pre-retrieve + tools |
| Tool result vs passage conflict | Tool result wins / Say they disagree | Tool result wins |
| Score-gate refusal with tools | Skip the gate when tools exist / Keep the gate / You decide | Skip the score gate when tools exist |

## Loop limits & failures

| Question | Options | Selected |
|----------|---------|----------|
| Max tool rounds | 4 configurable / 2 / You decide | 4 rounds, configurable |
| Deadline scope | Per request / Whole question | Per request |
| App-side tool failure | Error result to the model / Abort / You decide | Error result to the model |
| Tool result size | Fit budget, 413 beyond / Truncate with marker / You decide | Fit the context budget, 413 beyond |

## Model bake-off candidates

| Question | Options | Selected |
|----------|---------|----------|
| Candidates | qwen3:4b-instruct-2507 / qwen3:4b thinking (installed) / qwen2.5-coder:7b (installed) / an 8B | qwen3:4b (installed, thinking), qwen2.5-coder:7b (installed) — no new pulls |
| Settle Phase 10's model too | No, decide Phase 10 first / Yes, one decision in Phase 11 | No — decide Phase 10 first |

**Note recorded:** the thinking qwen3:4b already exceeded the 120 s deadline in Phase 10; the bake-off reports results honestly even if neither candidate passes.

## Claude's Discretion

Tool definition format and JSON Schema subset, tools per request, calls per round, tool_call event shape, tool-result request shape, HMAC details, tool-source citation format, prompt changes and golden tool fixture.

## Deferred Ideas

Built-in search tool; continuation token; whole-question deadline; larger or newly pulled models.
