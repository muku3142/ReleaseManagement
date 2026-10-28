---
phase: 14-ai-document-assist
plan: 10
subsystem: frontend
tags: [result-card, reducer, streaming, fact-check]
requires: [14-08, 14-09]
provides:
  - resultReducer, initialResultState, canSave (frontend/src/documentAssist/resultState.ts)
  - AiResultCard, AiResultCardProps (frontend/src/components/AiResultCard.tsx)
affects: [14-11]
key-files:
  created:
    - frontend/src/documentAssist/resultState.ts
    - frontend/tests/documentAssist/resultState.test.ts
    - frontend/src/components/AiResultCard.tsx
    - frontend/tests/components/AiResultCard.test.tsx
decisions:
  - "Reducer events apply only in waiting/streaming, so a late AbortError or ended after Stop cannot change the state."
  - "A stream that ends with no done/error and a pre-stream ApiError (other than 413/401) both dispatch 'ended', which becomes FAILED."
metrics:
  tests: "477 -> 508 (full suite green), tsc -b clean"
  completed: 2026-10-18
requirements: [ASST-09, ASST-10]
---

# Phase 14 Plan 10: Result-state reducer and AiResultCard Summary

Pure reducer holding every Save-block rule (`canSave` true only for a complete, non-blank Clean up/Custom result) and the AI result card that streams, stops, edits, previews with images blocked, fact-checks, saves or discards per 14-UI-SPEC.

## Commits
- 68ffd47: resultReducer, initialResultState, canSave (11 tests)
- cc6e2d3: AiResultCard and 20 tests

## Deviations from Plan
None. Tests use `createRoot` and `act` (no testing-library in the repo), as in 14-08.

## Notes for 14-11
- Card props exactly as in the plan. `onClose('closed')` comes from the error-state Close button, `onClose('discarded')` from Discard.
- The card has its own polite live region for working/ready/incomplete/stopped/copied. "Result discarded." and "Saved as Revision {n}." are NOT announced by the card because it unmounts on those; the panel must announce them.
- The card defines its own spinner class (with `motion-reduce:animate-none`) rather than editing the shared one in ExportButtons.
- The card does not scroll the page on later phases, only once on mount (`scrollIntoView` guarded for jsdom).
- A 413 (thrown, or an SSE TOO_LARGE event with sizes) calls `onTooLarge` and the card renders null; the panel must unmount it.

## Known Stubs
None.

## Threat Flags
None. No storage, console or dangerouslySetInnerHTML in the card (grep clean; Preview goes through MarkdownBody with blockImages).

## Self-Check: PASSED
Full suite 508/508, `tsc -b` clean.
