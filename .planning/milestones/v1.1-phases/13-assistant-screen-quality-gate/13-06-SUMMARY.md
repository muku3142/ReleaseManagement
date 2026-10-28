---
phase: 13-assistant-screen-quality-gate
plan: 06
subsystem: frontend
tags: [assistant, react, router, nav]
requires: [13-01, 13-02, 13-04]
provides:
  - routes/AssistantRoute.tsx (Assistant page on session 'page')
  - /assistant route with disabled-redirect guard
  - Assistant nav item shown only when status.enabled
affects: [13-07, 13-08]
key-files:
  created:
    - frontend/src/routes/AssistantRoute.tsx
    - frontend/tests/assistant/AssistantRoute.test.tsx
    - frontend/tests/components/AppShell.test.tsx
  modified:
    - frontend/src/router.tsx
    - frontend/src/components/AppShell.tsx
key-decisions:
  - "A disabled status inside AssistantRoute renders nothing; the beforeLoad redirect is the real guard"
  - "Non-ApiError status failures read 'The server could not be reached' in the load-failure alert"
requirements-completed: [ASST-02, ASST-08]
completed: 2026-10-18
---

# Phase 13 Plan 06: Assistant page Summary

The `/assistant` route (redirects to `/` when the status query says disabled), an Assistant nav link between Releases and Templates that renders only on `enabled: true`, and the page itself: header, New chat, freshness line, release picker that becomes a removable scope chip, and the shared chat body on session `page`.

## Tasks

| Task | Notes |
|------|-------|
| 2 AssistantRoute + tests | 9 tests; scope change goes through `chatActions.setScope` and shows its announcement inline and in a polite live region |
| 1 Route, guard, nav item + AppShell tests | 5 tests (enabled, disabled, loading, error, signed out with no status fetch) |

Committed in the order 2 then 1 because the router imports AssistantRoute.

## Verification

- Frontend suite: 434 tests green (420 baseline + 14 new); `tsc -b` clean.
- No `localStorage` or `sessionStorage` in AssistantRoute.

## Deviations from Plan

None in behaviour. The "survives navigation" behaviour relies on the module chat store (covered by chatStore tests); the route test only checks the remount keeps reading the session.

## Known Stubs

None.

## Self-Check: PASSED
