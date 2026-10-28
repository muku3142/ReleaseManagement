---
phase: 13-assistant-screen-quality-gate
plan: 07
subsystem: frontend
tags: [assistant, drawer, release-detail, citations]
requires: [13-04]
provides:
  - assistant/ReleaseAssistantDrawer.tsx
  - ReleaseDetailRoute "Ask about this release" toggle, ticket-row ids, hash expand-and-scroll
affects: [13-06]
key-files:
  created:
    - frontend/src/assistant/ReleaseAssistantDrawer.tsx
    - frontend/tests/assistant/ReleaseAssistantDrawer.test.tsx
  modified:
    - frontend/src/routes/ReleaseDetailRoute.tsx
    - frontend/tests/components/ReleaseDetailRoute.test.tsx
key-decisions:
  - "Escape handling uses onKeyDownCapture on the region so the in-flight state is read before the composer's own Escape handler stops the stream (stop-then-close needs two presses)"
  - "Drawer inner wrapper is sticky/max-h-screen so the composer stays reachable although the region spans the full page height"
  - "Toggle click while open stops the drawer stream itself, since closing via the toggle bypasses the drawer's close()"
requirements-completed: [ASST-05]
completed: 2026-10-18
---

# Phase 13 Plan 07: Release assistant drawer Summary

Release-scoped, non-modal "Ask about this release" drawer (session `drawer:<releaseId>`, fixed scope chip, no click-outside close), plus `id="ticket-KEY"` on ticket rows and a hash effect that expands and scrolls to the matching row so citation chips land correctly.

## Tasks

| Task | Commit |
|------|--------|
| 1 ReleaseAssistantDrawer | feat(13-07): release-scoped assistant drawer |
| 2 Toggle, row ids, hash effect | feat(13-07): ask-about-this-release toggle and ticket-row anchors |

## Verification

- Frontend suite 433 tests green (420 baseline + 13 new); `tsc -b` clean.
- No mousedown/pointerdown handler in the drawer.

## Deviations from Plan

None. Note: the "releaseId change closes the drawer" behaviour is implemented (`useEffect` on `releaseId`) but the route test mock fixes `useParams` to `r1`, so it has no dedicated test.

## Known Stubs

None.

## Self-Check: PASSED
