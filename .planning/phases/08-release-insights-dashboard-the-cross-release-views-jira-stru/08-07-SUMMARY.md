---
phase: 08-release-insights-dashboard
plan: 07
subsystem: frontend
tags: [dashboard, react, tailwind, audit, insights]
requires: ["08-04", "08-06"]
provides:
  - Home route `/` with summary strip, release matrix and deployment history
  - Shared LadderDots (compact + failed marker) and OUTCOME_COLOURS in api/promotions
  - DashboardSpanContractTest pinning the 366-day limit across client and server
affects: [PromotionSection]
tech-stack:
  added: []
  patterns: [independent query branches per section, URL-held filters, inline bars via width style only]
key-files:
  created:
    - frontend/src/components/LadderDots.tsx
    - frontend/src/components/DeploymentHistory.tsx
    - frontend/tests/components/DeploymentHistory.test.tsx
    - frontend/tests/components/DashboardRoute.test.tsx
    - src/test/java/com/evision/releasemgmt/adapters/web/DashboardSpanContractTest.java
  modified:
    - frontend/src/routes/DashboardRoute.tsx
    - frontend/src/components/PromotionSection.tsx
    - frontend/src/api/promotions.ts
key-decisions:
  - "Error copy strips the trailing period of messageForReason so the contract string 'Couldn't load ... — {msg}. Try again.' does not read '..'"
  - "History shows a section spinner only while loading; with an invalid range and no prior data it shows just the inline message"
requirements-completed: [AUDIT-04, INSIGHT-01, INSIGHT-02, INSIGHT-03]
duration: ~25 min
completed: 2026-10-12
---

# Phase 8 Plan 07: Dashboard screen Summary

The home route now renders the approved 08-UI-SPEC screen: summary strip, release matrix and a server-filtered deployment history with Load more paging.

## Commits
- c20115c refactor(08-07): LadderDots extraction, OUTCOME_COLOURS move, DashboardSpanContractTest
- 255db8d feat(08-07): DeploymentHistory section (8 tests)
- 714f052 feat(08-07): DashboardRoute summary strip and matrix (15 tests)

## Verification
- Full frontend suite: 31 files, 328 tests passed; `npx tsc -b` clean.
- `PromotionSection.test.tsx` passes unedited (21 tests) after the LadderDots extraction.
- `DashboardSpanContractTest` passes under Maven (`-P '!frontend'`).
- No `dangerouslySetInnerHTML` or chart library in the new files; router.tsx and AppShell.tsx untouched.

## Deviations from Plan
- Worktree base was behind the required base commit; reset to 0d34138 as the startup check instructs.
- No other deviations. Rule 2 note: the dashboard error branch also handles a non-ApiError failure with a generic message rather than rendering nothing.

## Known Stubs
None.

## Threat Flags
None. T-08-21..24 mitigations applied (React text only, typed Link, messageForReason only, no request on invalid range).

## Self-Check: PASSED
