---
phase: 11-tool-call-protocol
plan: 04
subsystem: rag-service
tags: [prompt, citations, tool-calls]
requires: [11-02]
provides: [Prompts.TOOL_RULES, PromptBuilder.chatWithTools, PromptBuilder.requireToolBudget, PromptBuilder.withCorrection, CitationKind, tool-source citations]
affects: [11-05]
key-files:
  created: [rag-service/src/main/java/com/evision/ragservice/domain/CitationKind.java]
  modified: [Prompts.java, PromptBuilder.java, TokenEstimator.java, CitationBuilder.java, Citation.java, PromptBuilderTest.java, CitationBuilderTest.java]
decisions:
  - "RETRIEVED fallback cites the passage selection followed by every tool source (error results never get an id)"
  - "chat() passage/history fitting extracted to a private fit() shared with chatWithTools; chat() behaviour unchanged"
metrics:
  tasks: 2
  tests: "306 green hermetic (was 294)"
completed: 2026-10-15
---

# Phase 11 Plan 04: Tool prompt and tool citations Summary

p11-v1 TOOL rules, a nonce-fenced and budgeted `chatWithTools`, a one-shot correction turn, and tool-source citations with `kind` PASSAGE or TOOL on every item.

## Commits
- 6373931 feat(11-04): p11-v1 tool rules and budgeted, fenced chatWithTools
- 588dfd4 feat(11-04): cite tool sources with an explicit citation kind

## What changed
- `Prompts`: added TOOLS_PROMPT_VERSION p11-v1, TOOL_RULES, FINAL_ROUND_RULE, CORRECTION. PROMPT_VERSION (p10-v2), CHAT_SYSTEM (incl. the Phase 10 rule 3) and TRANSFORM_SYSTEM untouched (diff has only added lines).
- `PromptBuilder`: `requireToolBudget` (413 `ToolResultTooLargeException` for an exchange that does not fit, `InvalidContentException` for a question that does not fit), `chatWithTools`, `withCorrection`, `neutraliseTool`, `NumberedToolSource`, `BuiltPrompt.toolSources` (4-arg constructor kept). Source titles never enter fences.
- `TokenEstimator.estimateChars`.
- `Citation.kind` (6-arg constructor defaults to PASSAGE), `CitationKind`, `CitationBuilder.build(answer, sent, tools)`.

## Decisions
- RETRIEVED fallback (Claude's discretion per CONTEXT): passages at or above minScore (max 3), then all tool sources.
- Refactor: the passage/history fitting loop of `chat()` moved into a private `fit()` used by both `chat()` and `chatWithTools()` to avoid duplicating it; no behaviour change, all pre-existing tests pass untouched.

## Deviations from Plan
- Worktree base was behind the orchestrator target; reset to ffa3191 per the startup instruction (clean tree, no work lost).
- Behaviour test for "room for one passage only" implemented as a monotonic sweep over growing results (passages used never increases, estimate + numPredict <= numCtx, fewer than offered at the end) rather than hardcoding token counts, per Pitfall 5.

## Verification
- Full hermetic suite: 306 tests, 0 failures. openapi.json unchanged. Golden profile not run.

## Known Stubs
None.

## Self-Check: PASSED
