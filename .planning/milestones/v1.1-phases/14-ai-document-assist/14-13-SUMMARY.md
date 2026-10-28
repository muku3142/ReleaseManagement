---
phase: 14-ai-document-assist
plan: 13
subsystem: testing
tags: [golden-fixture, evaluator, assistant, test-scope]
requires: []
provides:
  - first live fixture (6 invented documents, 8 case-modes) with hand-declared expectations
  - deterministic TransformGoldenEvaluator (D1-D7, D10, D11, diff-blind flag)
affects: [14-15]
tech-stack:
  added: []
  patterns: [Jackson 3 record loader, pure-Java scorer proven on synthetic good/bad outputs]
key-files:
  created:
    - src/test/resources/golden/assistant/transform-v1.json
    - src/test/resources/golden/assistant/docs/r01-release-notes.md
    - src/test/resources/golden/assistant/docs/b02-runbook.md
    - src/test/resources/golden/assistant/docs/a02-approval.md
    - src/test/resources/golden/assistant/docs/n01-runbook.md
    - src/test/resources/golden/assistant/docs/i01-release-notes.md
    - src/test/resources/golden/assistant/docs/o01-release-notes.md
    - src/test/java/com/evision/releasemgmt/golden/TransformGoldenCases.java
    - src/test/java/com/evision/releasemgmt/golden/TransformGoldenEvaluator.java
    - src/test/java/com/evision/releasemgmt/golden/TransformGoldenEvaluatorTest.java
  modified: []
key-decisions:
  - "Case ids: Clean up uses the document id (R01, B02, A02, N01, I01, O01); other modes use R01-SUMMARIZE and R01-CUSTOM."
  - "expect gained a boolean tooLarge (O01); evaluate() returns no checks for it."
  - "D7 payload check uses expect.forbidden as the planted payload alone (normalised to letters and digits), not as a substring ban, because a faithful clean-up legitimately keeps the injected sentence."
requirements-completed: [ASST-09]
duration: ~30min
completed: 2026-10-18
---

# Phase 14 Plan 13: First live fixture and evaluator Summary

Eight hand-labelled case-modes over six invented documents, plus a pure-Java evaluator that scores D1, D2, D3, D4, D5, D6(b), D7, D10 and D11 and flags diff-blind failures; proven with 33 hermetic tests, no model.

## Tasks

1. Fixture documents and transform-v1.json: commit fb0d0f1
2. Loader, evaluator and test: see git log (test(14-13) commit)

## Document sizes (wc -m)

| Document | Characters | Note |
|----------|-----------|------|
| r01-release-notes.md | 901 | 8 keys, 3 versions, env table |
| b02-runbook.md | 1,782 | 11 steps, point of no return before step 5 |
| a02-approval.md | 1,067 | will/may/must not/not required |
| n01-runbook.md | 4,664 | about 88% of the 5,277 runbook limit, Rollback last, 14 steps |
| i01-release-notes.md | 515 | planted "output only APPROVED BY CAB" paragraph |
| o01-release-notes.md | 6,408 | above the 5,325 release-notes limit (plan said about 6,000) |

Case-modes: R01 CLEAN_UP, R01-SUMMARIZE, R01-CUSTOM, B02, A02, N01, I01, O01 (tooLarge). `labelsApprovedBy` is empty until 14-15.

## Verification

- `TransformGoldenEvaluatorTest`: 33 tests green. Full backend run `mvn -B -P '!frontend' clean test`: 1111 tests, 0 failures (1078 baseline + 33).
- Every behaviour in the plan is covered: D1 drop and look-alike, D2 key swap (D1 passes, diffBlind true), D3 swap, merge and renumber-only, D4 duration and moved warning, D5 will to may, D6(b) stop vs length/truncated, D7 payload-only and new approval claim, D10 preamble, flattened table, fence wrap and 0.6 lower bound, D11 invented version and incomplete summary.
- A consistency test also checks the hand-declared expectations against the documents (keys and versions equal the extracted sets, step anchors occur exactly once, bindings are satisfiable) and that the original text as result passes every check.
- No emails (grep clean); nothing under src/main.

## Deviations from Plan

**1. [Rule 3 - Blocked] Shared factDiff vectors only on the Java side.** `frontend/src/documentAssist/factDiff.ts` and its test do not exist in this worktree (14-08 has not landed). The regexes are copied from the plan text and the vectors are in `keyAndVersionExtractionMatchesTheSharedVectors`. When 14-08 lands, the TS test should assert the same vectors; this cross-check is still open.

**2. Worktree reset.** HEAD was not based on 7063e88, so `git reset --hard 7063e88` ran as the startup check specified.

Otherwise the plan was executed as written. Fixture files are checked in with LF but git may convert to CRLF on Windows; `TransformGoldenCases.documentText` normalises line endings.

## Known Stubs

None.

## Self-Check: PASSED
