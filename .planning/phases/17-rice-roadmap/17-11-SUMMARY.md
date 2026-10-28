---
phase: 17-rice-roadmap
plan: 11
subsystem: frontend
tags: [react, packages-link, roadmap]
requires: [17-03, 17-09]
provides: [PackagesLink, isAllowedPackagesUrl, release-detail Add to roadmap]
affects: [frontend/src/routes/ReleaseDetailRoute.tsx]
key-files:
  created:
    - frontend/src/components/packagesUrl.ts
    - frontend/src/components/PackagesLink.tsx
    - frontend/tests/components/packagesUrl.test.ts
    - frontend/tests/components/PackagesLink.test.tsx
  modified:
    - frontend/src/routes/ReleaseDetailRoute.tsx
    - frontend/src/api/reasonMessages.ts
    - frontend/tests/components/ReleaseDetailRoute.test.tsx
requirements-completed: [PKG-01, ROAD-01]
completed: 2026-10-25
---

# Phase 17 Plan 11: Packages link and Add to roadmap Summary

Release detail gets a set/change/clear/open packages row (OPEN and FROZEN, client host pre-check) and ticket checkboxes with an "Add to roadmap" action reporting added and already-present tickets.

## Commits
- d1a41a5 feat(17-11): add the release packages link row
- second commit: feat(17-11): add packages link and Add to roadmap to release detail

## Tests
`npx tsc -b` clean; packagesUrl (15), PackagesLink (7 in file), ReleaseDetailRoute (+2) all pass: 55 tests across 3 files.

## Deviations
- [Rule 3] Exported `REASON_MESSAGES` from `reasonMessages.ts` so the client pre-check reuses the server's message instead of duplicating the string.
- The Clear `ConfirmDialog` is mounted only while confirming; an always-mounted dialog broke existing tests that take the first `<dialog>` in the page (freeze dialogs).
- Ticket table gained a column, so the expanded-row `colSpan` moved from 10/11 to 11/12.

## Self-Check: PASSED
