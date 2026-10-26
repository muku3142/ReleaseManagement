---
phase: 11-tool-call-protocol
plan: 08
subsystem: rag-service golden tier
tags: [golden, tools, bake-off, test-harness]
requires: [11-06]
provides: [tools-v1.json fixture, FakeToolApp, ToolsFixtureTest, GoldenToolsIT]
affects: [11-09]
key-files:
  created:
    - rag-service/src/test/resources/golden/tools-v1.json
    - rag-service/src/test/java/com/evision/ragservice/golden/FakeToolApp.java
    - rag-service/src/test/java/com/evision/ragservice/golden/ToolsFixtureTest.java
    - rag-service/src/test/java/com/evision/ragservice/golden/GoldenToolsIT.java
key-decisions:
  - "expect.maxRounds counts tool-call rounds (not POSTs); TC09 uses the service cap of 4"
  - "Default model is qwen2.5-coder:7b (matches GoldenChatIT), not the qwen3:4b in the plan text"
requirements-completed: [RAG-08]
duration: ~35 min
completed: 2026-10-15
---

# Phase 11 Plan 08: Golden tool tier Summary

Golden tool bake-off instrument: 12-case fixture, a synthetic app that executes the fixture tools, a hermetic consistency test, and an opt-in live harness that writes one report per model.

## Tasks

| Task | Commit | Result |
|------|--------|--------|
| 1. tools-v1.json, FakeToolApp, ToolsFixtureTest | e53bdad | 6 hermetic tests, in the default suite |
| 2. GoldenToolsIT | b5fc577 | compiles, skips cleanly with a dead Ollama |

## Verification

- `mvn clean test`: 337 tests, 0 failures, 0 errors (331 before plus 6 new). GoldenToolsIT is excluded.
- `-Pgolden -Dtest=GoldenToolsIT` with `RAG_GOLDEN_OLLAMA_URL=http://localhost:1`: 0 run, BUILD SUCCESS (assumption skips).
- GoldenChatIT, chat-v1.json and clients.json are unchanged.
- The live harness was NOT run (11-09). The TC08 and TC10 paths, the SSE tool loop and the report are therefore compile-checked only.

## Harness details

- Registers `rag.chat.model` (from `-Drag.golden.model`, default qwen2.5-coder:7b) and the 4096 profile (num-predict 512, k 4, history budget 600, tools num-predict 768) through @DynamicPropertySource, as GoldenChatIT does.
- Deterministic cases fail the test: TC08 (9 mutations: argument, call id, name, round, dropped first turn, reordered, other client, other question, random signature; each must be 400 TOOL_TURN_INVALID with no `generation endpoint=chat` log line) and TC10 (one 8001-char result, two 7000-char results; 413 TOOL_RESULT_TOO_LARGE, no generation line).
- Model cases are recorded only. Report `target/golden-tools-report-<model>.json` holds config (Ollama version, capabilities, thinkCapable, num-ctx, promptVersion), per-run failed check names, posts, tool names per round, TTFT, latency, tokens/s from evalCount/evalMs, done reasons, errors, correction retries, and a summary (pass rates, median latency, DEADLINE/length/correction counts). No answer text, tool content or signatures.
- A run that needs 6 POSTs fails the test as a harness bug.

## Deviations from Plan

- **[Rule 1 - Bug] Default model.** Plan text says `qwen3:4b`; that model is uninstalled and the Phase 10 profile is qwen2.5-coder:7b, so the default follows GoldenChatIT.
- **maxRounds semantics.** The plan left `maxRounds` undefined; I fixed it as tool-call rounds and set per-case values (1 for single-call cases, 2 for TC03, 4 for TC09).
- **Seeding.** Only client A is seeded (client B is not needed for tool cases); the client UUID is read via `SELECT id FROM client_app WHERE name = 'golden-A'`.

## Known risks for 11-09

- qwen2.5-coder may print calls as text rather than native tool_calls; those runs will show failed `calls` checks, which is the measurement.
- TC10 two-result case relies on the context-budget check (PromptBuilder) rather than the per-result limit; if the 4096 budget fits 2 x 7000 chars it would be a service finding, not a harness bug (7000 chars is about 2300 tokens each, so it should not fit).
- The fixture's `labeling` field still awaits the reviews listed there.

## Self-Check: PASSED

Files exist, commits e53bdad and b5fc577 are in the log, STATE.md and ROADMAP.md untouched.
