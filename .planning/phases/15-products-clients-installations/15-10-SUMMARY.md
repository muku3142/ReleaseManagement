---
phase: 15-products-clients-installations
plan: 10
subsystem: frontend-releases
tags: [react, installs, release-detail]
requires: [15-03, 15-06]
requirements: [PORT-04, PORT-05, PORT-07]
key-files:
  created:
    - frontend/src/components/ClientInstallsSection.tsx
    - frontend/src/components/RecordInstallDialog.tsx
    - frontend/tests/components/ClientInstallsSection.test.tsx
    - frontend/tests/components/RecordInstallDialog.test.tsx
  modified:
    - frontend/src/components/ConfirmDialog.tsx
    - frontend/src/components/RecordPromotionDialog.tsx
    - frontend/src/routes/ReleaseDetailRoute.tsx
    - frontend/tests/components/ReleaseDetailRoute.test.tsx
metrics:
  tasks: 2
  completed: 2026-10-22
---

# Phase 15 Plan 10: Client installs section Summary

"Client installs" section on the release page after the promotion history: single and bulk install recording, correction with required reason, and void via ConfirmDialog, with UI-SPEC copy verbatim (D-15-05, -09, -10, -14, -16).

## Commits
- 5b4600f feat(15-10): record and correct client installs dialog
- 554f48c test(15-10): type the onDone mock
- ffcf516 feat(15-10): client installs section with history, correct and void

## Deviations from Plan

**1. [Rule 3 - Blocking] Existing ReleaseDetailRoute test mocks lacked `/installs`**
- Fallback mock returned the release JSON, breaking the new section; added an `EMPTY_INSTALLS` response to every fetch mock in ReleaseDetailRoute.test.tsx.

No other deviations. `/portfolio` linked with a plain `to`. `ConfirmDialog` gained `confirmDisabled`.

## Verification
`npx tsc -b` clean; `npx vitest run` 53 files, 588 tests pass.

## Known Stubs
None.

## Self-Check: PASSED
