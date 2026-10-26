---
phase: 14-ai-document-assist
plan: 12
subsystem: frontend-documents
tags: [react, document-assist, revision-history]
requires: [14-09]
provides: [AI badge and instruction line in revision history, regenerate notice]
key-files:
  modified:
    - frontend/src/components/DocumentsSection.tsx
    - frontend/tests/components/DocumentsSection.test.tsx
decisions:
  - "Notice id is regenerate-notice-{documentType}; shown only when latest.aiAssisted and readinessState is not NO_TEMPLATE."
  - "Instruction is rendered as a JSX text node (T-14-42) and repeated in the title attribute."
metrics:
  tests: "477 -> 483 (full suite green), tsc -b clean"
  completed: 2026-10-18
---

# Phase 14 Plan 12: History badge, instruction line, regenerate notice Summary

AI-assisted revisions now carry an "AI-assisted" badge, say "saved" instead of "generated", and show `AI instruction: Clean up` or `AI instruction: "{text}"` in the revision history; the DocumentRow summary line gets the same badge and wording. When the latest revision is AI-assisted and Regenerate is offered, a persistent amber notice sits under the button row and is referenced by the button's `aria-describedby`. Non-AI revisions render as before.

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 1 AI badge, instruction line, notice | see git log (feat(14-12)) | 6 new tests: CLEAN_UP row, CUSTOM row with HTML-looking text, summary line, notice plus aria-describedby, no notice for non-AI latest, no notice for NO_TEMPLATE |

## Deviations from Plan

None. The worktree was reset to b6893d1 per the startup check (its base differed). The plan's verify uses `npm run typecheck`; `npx tsc -b` was used as instructed by the orchestrator.

## Known Stubs

None.

## Threat Flags

None.

## Self-Check: PASSED
