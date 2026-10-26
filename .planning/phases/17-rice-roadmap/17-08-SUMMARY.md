---
phase: 17-rice-roadmap
plan: 08
subsystem: frontend-roadmap
tags: [roadmap, rice, components, history]
requires: ["17-03"]
provides:
  - "RubricPanel, InputCell, InputHistoryPanel, ItemDetailRow and format helpers under frontend/src/roadmap"
affects: [17-12]
key-files:
  created:
    - frontend/src/roadmap/format.ts
    - frontend/src/roadmap/RubricPanel.tsx
    - frontend/src/roadmap/InputCell.tsx
    - frontend/src/roadmap/InputHistoryPanel.tsx
    - frontend/src/roadmap/ItemDetailRow.tsx
    - frontend/tests/roadmap/format.test.ts
    - frontend/tests/roadmap/InputCell.test.tsx
    - frontend/tests/roadmap/ItemDetailRow.test.tsx
decisions:
  - "InputCell reverts to the saved value on any failed save (server or network); selects are controlled by the saved value so they revert naturally."
metrics:
  tasks: 2
  completed: 2026-10-25
---

# Phase 17 Plan 08: Roadmap leaf components Summary

Rubric block, inline R/I/C/E control, input history panel and expandable details row, ready for 17-12 to wire into the table and route.

## Commits
- 9daa1a6 feat(17-08): add the scoring rubric and inline RICE input controls
- 0552cef feat(17-08): add the roadmap item details row with history

## Verification
`npx tsc -b` clean; `npx vitest run tests/roadmap`: 3 files, 17 tests passed. No `dangerouslySetInnerHTML` in `src/roadmap`.

## Deviations from Plan
None. Notes: the Reach hint is derived from the item's saved product, so it appears after the product change is saved and refetched (it never fills Reach). The history panel sorts newest first client-side as a safeguard. New files are CRLF.

## Known Stubs
None.

## Self-Check: PASSED
