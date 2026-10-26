---
phase: 07-promotion-tracking-audit-trail
plan: 04
subsystem: frontend-promotion
tags: [react, promotion, dialog, tanstack-query]
requires: ["07-03"]
provides:
  - "promotions.ts: types, promotionsQueryKey, usePromotions, useRecordPromotion, OUTCOME_LABELS"
  - "PromotionSection (stepped-dot ladder, history, #documents link) and RecordPromotionDialog"
  - "REASON_MESSAGES entries for every Phase 7 reason code (07-05 and 07-06 need not touch auth.ts)"
affects: [07-05, 07-06]
key-files:
  created:
    - frontend/src/api/promotions.ts
    - frontend/src/components/PromotionSection.tsx
    - frontend/src/components/RecordPromotionDialog.tsx
    - frontend/tests/components/PromotionSection.test.tsx
  modified:
    - frontend/src/api/auth.ts
    - frontend/src/routes/ReleaseDetailRoute.tsx
    - frontend/tests/components/ReleaseDetailRoute.test.tsx
requirements-completed: [DEPL-01, DEPL-02, DEPL-04, AUDIT-01, AUDIT-02]
metrics:
  tasks: 2
  files: 7
  completed: 2026-10-12
---

# Phase 7 Plan 04: Promotion UI Summary

The release page now shows the environment ladder as stepped dots with the current stage, a "Record promotion" dialog for frozen releases, the full promotion history, and an in-page link to the existing document revisions.

## Commits
- e4fea31: promotions.ts, auth reason messages, PromotionSection, RecordPromotionDialog, 13 component tests
- e14c36e: mount above `<DocumentsSection`, route test mocks answer `/promotions`, one new page test

## Verification
Frontend baseline measured at plan start: 253 tests. After: 267 (13 + 1 new), 0 failures; `npx tsc -b` clean. `ReleaseDetailRoute.tsx` diff is 2 added lines, 0 removed.

## Decisions
- The dialog is mounted only while the release is frozen (alongside the button), so unfrozen pages gain no extra `<dialog>` that could disturb existing `querySelector('dialog')` tests.
- Dialog closes via `onSuccess: props.onClose`; the history updates through `setQueryData` from the 201 body.

## Deviations from Plan
**1. [Rule 3 - Blocking] Route test `mockResolvedValue` replaced** in 'reopens the manual-item form empty...' by a `mockImplementation` that answers `/promotions`; a single shared Response could not serve a second body read. Assertions unchanged.

**2. Worktree base reset** from 4d1d621 to the required 35af1bd before work.

## Known Stubs
None.

## Threat Flags
None. Notes, stage names and actors render as React text only (test renders `<b>x</b>` literally); no `dangerouslySetInnerHTML`.

## Self-Check: PASSED
