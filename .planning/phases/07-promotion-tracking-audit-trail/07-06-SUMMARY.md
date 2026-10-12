---
phase: 07-promotion-tracking-audit-trail
plan: 06
subsystem: promotion-history
tags: [corrections, audit, append-only, react]
requires: [07-03, 07-04]
provides:
  - "PromotionHistory.correct(...) field-check-only correction/void factory"
  - "POST /api/releases/{releaseId}/promotions/{promotionId}/corrections"
  - "Correct action, Superseded/Voided/Correction markers in PromotionSection"
affects: [07-07]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/CorrectPromotionRequest.java
  modified:
    - src/main/java/com/evision/releasemgmt/domain/promotion/PromotionHistory.java
    - src/main/java/com/evision/releasemgmt/application/PromotionService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/PromotionController.java
    - frontend/src/api/promotions.ts
    - frontend/src/components/PromotionSection.tsx
    - frontend/src/components/RecordPromotionDialog.tsx
key-decisions:
  - "Correction never replays ladder-sequence rules (D-07-14); retired check applies to a changed stage only"
  - "One dialog component with an optional correcting prop"
requirements-completed: [AUDIT-03, AUDIT-01]
duration: ~25 min
completed: 2026-10-12
---

# Phase 7 Plan 06: Corrections Summary

Corrections and voids are recorded as superseding entries through the existing locked append path, with a correction mode in the record dialog and Superseded/Voided/Correction markers in the history.

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 1 Correction rule, service, endpoint | 560084c | domain `correct`, `PromotionService.correct`, `CorrectPromotionRequest`, controller endpoint, tests |
| 2 Correct action and markers | 20eee9e | `useCorrectPromotion`, dialog correction mode, section markers, 8 new tests |

## Verification

- Backend `PromotionHistoryTest` (35), `PromotionServiceTest` (5), `PromotionControllerTest` (13), `PromotionPersistenceIT` (7), `PromotionSchemaIT` (16): all green.
- Frontend: `npx vitest run` 285 passed (baseline 277, +8), `npx tsc -b` clean.

## Deviations from Plan

- **[Rule 3 - Blocking]** The plan said to reuse private bounds/stage-lookup helpers in `PromotionHistory`, but they were inline in `record`. Extracted `requireDeployedAtInBounds` and `findStage` and used them from both `record` and `correct` (no behaviour change; existing tests still pass).
- Added a guard disabling "Save correction" when the deployed-at field is edited to empty, since `new Date('')` would throw on submit. The datetime input uses `step=1` in correction mode only so seconds survive the prefill.
- The worktree base was corrected with `git reset --hard 5bce35f` at startup as the spawn instructions required.

## Known Stubs

None.

## Threat Flags

None. T-07-24..28 mitigations are in place: lookup scoped to the path release's entries (IT covers cross-release), reason `@NotBlank`, actor from principal only (no actor field), reason rendered as React text only (test asserts markup stays literal).

## Self-Check: PASSED
