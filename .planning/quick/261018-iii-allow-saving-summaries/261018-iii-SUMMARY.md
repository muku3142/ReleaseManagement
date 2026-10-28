---
phase: quick-261018-iii
status: complete
completed: 2026-10-18
---

# Quick 261018-iii: Allow saving Summarize results Summary

A complete Summarize result can now be edited and saved as AI-assisted revision N+1, like Clean up. This is D-14-13, which reverses D-14-03.

## Commits
- 47929f1: backend. V13 widens the ai_mode CHECK, adds AiMode.SUMMARIZE, and removes the rejection in saveAiRevision (exhaustive switch). Unit test and IT updated.
- 2b21198: frontend. canSave drops its mode parameter, editable/showSave and the summary-only note are removed, history shows "AI instruction: Summarize", the viewer banner shows "Summarized".
- e617d59: docs. D-14-13 added to 14-CONTEXT, with D-14-03 marked superseded. UI-SPEC, AI-SPEC and document-assist-flow aligned.

## Verification
- Backend: the full `mvn -B -P '!frontend' clean test` ran 1260 tests with 1 error. That error was my IT assertion reading aiMode from the save response, which does not carry it. I fixed it to read the latest document via GET. The re-run of AssistantTransformStreamIT and AssistantTransformServiceTest passed (31 tests). I did not re-run the full suite after the fix. Nothing else in main changed in between.
- Frontend: vitest 537 passed (baseline 535) and `tsc -b` is clean.

## Deviations
- [Rule 3] Removed the now-unused private `length()` helper in AssistantTransformService, and the unused `TransformMode` import in resultState.ts (tsc TS6196).
- Removed canSave's `mode` parameter (the plan's preferred option) and updated its callers and tests.
- The IT checks the staleness basis with `stalenessBasis()` across the three revisions. The save response has no aiMode, so aiMode, aiInstruction and markdown are read via GET.
- Commits carry no Co-Authored-By trailer, per the task constraints and the user's CLAUDE.md.
- The branch base was wrong at start (it was 18ea6f7). I reset it to 981946c per the startup check.

## Known Stubs
None.

## Self-Check: PASSED
Commits 47929f1, 2b21198 and e617d59 exist. V13 exists. `grep SUMMARIZE_REJECTED src` and `grep "reading only" frontend/src` return nothing.
