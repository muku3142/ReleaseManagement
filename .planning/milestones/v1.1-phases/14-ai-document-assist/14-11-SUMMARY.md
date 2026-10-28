---
phase: 14-ai-document-assist
plan: 11
subsystem: frontend
tags: [viewer, assist-bar, banners, two-column]
requires: [14-08, 14-09, 14-10]
provides:
  - AiAssistPanel (frontend/src/components/AiAssistPanel.tsx)
  - AI-assisted and verify banners in DocumentViewerRoute
affects: [14-12]
key-files:
  created:
    - frontend/src/components/AiAssistPanel.tsx
    - frontend/tests/components/AiAssistPanel.test.tsx
  modified:
    - frontend/src/routes/DocumentViewerRoute.tsx
    - frontend/tests/components/DocumentViewerRoute.test.tsx
decisions:
  - "The panel owns the document card wrapper (m-6 idle, grid column when active) so the route passes only RenderedDocument as children."
  - "Too-long limits are per button (Clean up and Summarize use their own preset, Custom uses maxCharsFor(1)); the message shows the smallest limit among the disabled ones, or the recomputed limit while a typed instruction is over."
  - "The panel announces 'Result discarded.' and 'Saved as Revision {n}.' in its own polite region; the route only clears ?revision and moves focus."
  - "Focus after save waits for data.revision === the saved revision, so a refetch that still returns the old revision cannot focus a stale banner."
metrics:
  tests: "514 baseline -> 535 (full suite green), tsc -b clean"
  completed: 2026-10-18
requirements: [ASST-09, ASST-10]
---

# Phase 14 Plan 11: AiAssistPanel and viewer integration Summary

Assist bar (Clean up, Summarize, Custom...), Custom box with in-memory recent-5, size-limit guard, two-column original/result layout hosting AiResultCard, and AI-assisted plus verify banners for saved AI revisions, all outside RenderedDocument.

## Commits
- 5470334: AiAssistPanel and 15 tests
- Task 2 commit: DocumentViewerRoute integration, banners, 7 new route tests (committed with this SUMMARY)

## Deviations from Plan
- Route tests stub AiAssistPanel (the panel has its own suite), so the Revision-select and onSaved cases drive the route through the stub's callbacks.
- Escape inside the Custom box does not stop a stream: Custom closes before the card opens, and the card handles Escape itself.

## Logout reset of recent instructions
Not wired. Phase 13's chat store (`assistant/chatStore.ts`) has no logout reset; sign-out does `window.location.assign('/login')`, a full reload that clears every module-level store including `recentInstructions`. `resetRecentInstructions` remains exported for tests.

## Known Stubs
None.

## Threat Flags
None. T-14-39 blockImages when `aiAssisted`; T-14-40 banners with instruction as a text node; T-14-41 Revision select disabled while a result is open. No storage use in the panel (grep clean).

## Self-Check: PASSED
Full suite 535/535, `tsc -b` clean, banner strings absent from RenderedDocument.tsx.
