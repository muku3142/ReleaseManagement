---
phase: 11-tool-call-protocol
plan: 02
subsystem: rag-service
tags: [tool-calls, hmac, schema-validation]
requires: []
provides: [ToolTurnSigner, ToolPolicy, ToolSchema, tool domain records, ToolCallEvent, TOOL_CALL_INVALID]
affects: [11-03, 11-04, 11-05, 11-06]
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/domain/ToolSchema.java
    - rag-service/src/main/java/com/evision/ragservice/application/ToolTurnSigner.java
    - rag-service/src/main/java/com/evision/ragservice/application/ToolPolicy.java
  modified:
    - rag-service/src/main/java/com/evision/ragservice/domain/ChatMessage.java
    - rag-service/src/main/java/com/evision/ragservice/domain/Completion.java
    - rag-service/src/main/java/com/evision/ragservice/domain/GenerationOptions.java
    - rag-service/src/main/java/com/evision/ragservice/domain/StreamEvents.java
    - rag-service/src/main/java/com/evision/ragservice/domain/ErrorReason.java
decisions:
  - Canonical numbers are normalised BigDecimal kept as JSON numbers (RESEARCH Pitfall 2), not "n:" strings
  - Unknown model tool names and non-NAME argument keys are never echoed into correction texts
metrics:
  completed: 2026-10-15
---

# Phase 11 Plan 02: Tool protocol trust core Summary

HMAC-SHA256 signed tool-turn chain, flat JSON-schema-subset validator and call/resume policy, as framework-free code, plus additive record extensions that keep every Phase 10 constructor.

## Commits
- Task 1: tool domain types, ToolSchema, additive edits (ChatMessage TOOL role/toolCalls, Completion evalDurationMs/toolCalls, GenerationOptions tools, StreamEvents.ToolCallEvent and Done.forToolCalls, ErrorReason.TOOL_CALL_INVALID)
- Task 2: ToolTurnSigner with tamper table (16 mutations rejected, number/key-order variants accepted)
- Task 3: ToolPolicy (check / requireValidResume / isFinalRound)

## Verification
Full rag-service suite: 278 tests, 0 failures (251 baseline + 27 new). openapi.json untouched.

## Deviations from Plan
None. The tamper table is a loop over a map in one test (no new dependency).

## Known Stubs
None.

## Self-Check: PASSED
