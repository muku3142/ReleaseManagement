---
phase: 14-ai-document-assist
plan: 08
subsystem: frontend
tags: [factDiff, recent-instructions, markdown-it, image-blocking]
requires: []
provides:
  - factDiff, TICKET_KEY, VERSION (frontend/src/documentAssist/factDiff.ts)
  - useRecentInstructions, addRecentInstruction, resetRecentInstructions, RECENT_MAX
  - MarkdownBody({markdown, blockImages}) in RenderedDocument.tsx
affects: [14-11]
key-files:
  created:
    - frontend/src/documentAssist/factDiff.ts
    - frontend/src/documentAssist/recentInstructions.ts
    - frontend/tests/documentAssist/factDiff.test.ts
    - frontend/tests/documentAssist/recentInstructions.test.tsx
    - frontend/tests/components/RenderedDocument.test.tsx
  modified:
    - frontend/src/components/RenderedDocument.tsx
    - frontend/src/routes/DocumentViewerRoute.tsx
decisions:
  - Image and link_open rules on the shared md instance branch on env.blockImages, so other md.render callers are unchanged.
metrics:
  tests: "463 passing (baseline 447)"
requirements: [ASST-09, ASST-10]
---

# Phase 14 Plan 08: factDiff, recent instructions, MarkdownBody Summary

Pure ticket-key/version diff, in-memory recent-5 instruction store, and a shared MarkdownBody that renders model-supplied images as `[image: alt]` text with hardened links.

## Commits
- 985840f: factDiff and recent instructions store
- Task 2 commit: MarkdownBody with remote-image blocking; viewer switched to it (this SUMMARY is committed with it)

## Deviations from Plan
- recentInstructions test is `.test.tsx` (not `.test.ts`) and uses a probe component with `createRoot`, because `@testing-library/react` is not installed and no new dependency is allowed. Rule 3.
- Comment in recentInstructions.ts avoids the words localStorage/sessionStorage so the plan's grep acceptance check finds nothing.

## Notes
- Logout reset: 13-02/13-06 SUMMARYs were not consulted (not in this wave); `resetRecentInstructions` is exported and 14-11 should call it wherever the chat store resets on logout. Until then it clears on reload only.
- DocumentViewerRoute.test.tsx was not edited (owned by 14-09); it stays green.
- Viewer passes `blockImages={false}`; 14-11 turns it on for AI revisions.

## Known Stubs
None.

## Self-Check: PASSED
Full suite 463/463, `tsc -b` clean.
