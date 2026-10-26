---
phase: 14-ai-document-assist
plan: 14
subsystem: testing
tags: [golden, live-tier, maven-profile, docs, assistant]
requires: [14-13, 14-05, 13-12]
provides: [AssistantTransformGoldenIT, assistant-golden profile, Document assist runbook]
affects: [14-15]
key-files:
  created:
    - src/test/java/com/evision/releasemgmt/golden/AssistantTransformGoldenIT.java
  modified:
    - pom.xml
    - docs/quality-gate.md
requirements-completed: [ASST-09]
completed: 2026-10-18
---

# Phase 14 Plan 14: Live transform harness Summary

An opt-in `AssistantTransformGoldenIT` runs the transform fixture through the real `RagTransformAdapter` and writes a metadata-only `target/assistant-golden-report.json`. The `assistant-golden` profile selects it, and docs/quality-gate.md has the runbook.

## Commits

| Task | Commit |
|------|--------|
| 1 IT and pom profile | d6edfc7 |
| 2 docs section | 6474817 |

## What was built

- pom: shared `excludedGroups` is now `quality-gate,assistant-golden`; profile `assistant-golden` (`groups=assistant-golden`, `excludedGroups=none`) sits beside `quality-gate`.
- IT (`@Tag("assistant-golden")`, no Spring context): skipped unless `RAG_GOLDEN_BASE_URL` and `RAG_GOLDEN_API_KEY` are set. It binds `src/main/resources/application.yml` for the instructions and estimator, builds the client with `RagRestClientConfig.ragClient(..., PT2S, PT130S)`, runs cases sequentially and prints `case=X run=n/N outcome=...` with no text. O01 is refused by the estimator and recorded as `TOO_LARGE_PREFLIGHT` with no adapter call. Options: `-Dgolden.cases`, `-Dgolden.runs` (default 3), `-Dgolden.keepOutputs`.
- Report: per run checks, diff-blind flag, sizes, tokens, finishReason, truncated, TTFT, tokens/s, total, outcome. Overall: 12-hex SHA-256 of the six instructions, incomplete rate, Clean up pass rate, diff-blind failure rate, and the latency inputs (r10, ttft95, sizeLimit, deadline, latencyLimitChars, neededDeadline) with the selected branch, or `NOT_EVALUATED` when N01 did not complete.
- Docs: "Document assist (Phase 14)" with the dev run, the production-host run, the gate table, the ship-blocker statement, the latency rule, no `max-chars` cap until branch 3, and the 10% diff-blind rule.

## Verification

- `mvn -P '!frontend,assistant-golden' test -Dtest=AssistantTransformGoldenIT` with no env: BUILD SUCCESS, class not executed (0 tests run), not failed.
- Full `mvn -B -P '!frontend' clean test`: 1247 run, 0 failures; the IT did not run.
- The live golden was not run, by instruction.

## Deviations from Plan

- Worktree base was reset to f0d42eb by the startup check.
- Profile activation is documented as `-P '!frontend,assistant-golden'` (one list), as instructed, so the plan's `Passistant-golden` grep string is not present. The other three docs greps pass.
- The plan's "Skipped: 1" grep does not match: a `@BeforeAll` assumption reports "Tests run: 0" (same as 13-12), so I asserted the property instead.
- Model tag is recorded as `null`: the adapter does not surface `done.meta`.

## Known Stubs

None. The live path itself is unexercised until 14-15.

## Self-Check: PASSED
