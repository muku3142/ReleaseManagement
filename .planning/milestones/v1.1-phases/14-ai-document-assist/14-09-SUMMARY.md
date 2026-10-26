---
phase: 14-ai-document-assist
plan: 09
subsystem: frontend-data-layer
tags: [sse, react-query, document-assist]
requires: [13-02]
provides: [documentAssist client, streamSse, AI fields on document types]
affects: [14-10, 14-11, 14-12]
key-files:
  created:
    - frontend/src/api/documentAssist.ts
    - frontend/tests/api/documentAssist.test.ts
  modified:
    - frontend/src/api/documents.ts
    - frontend/src/api/assistant.ts
    - frontend/tests/api/assistant.test.ts
    - frontend/tests/api/documents.test.ts
    - frontend/tests/components/DocumentViewerRoute.test.tsx
    - frontend/tests/components/DocumentsSection.test.tsx
decisions:
  - "streamSse yields all parsed events; streamChat and streamTransform each filter their own event names."
  - "tooLargeFrom reads characters/maxCharacters off ApiError.problem for status 413 only."
metrics:
  tests: "447 -> 461 (full suite green), tsc -b clean"
  completed: 2026-10-18
---

# Phase 14 Plan 09: Document assist frontend data layer Summary

Typed client for document assist: AI fields on document types, a generic `streamSse` shared with chat, `api/documentAssist.ts` (limits query, server-exact `maxCharsFor` estimator, `streamTransform`, `tooLargeFrom`, `useSaveAiRevision`).

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 0 Phase 13 gate | none | 13-02 summary, `apiFetchRaw` and `parseSse` present |
| 1 AI fields + streamSse | 215ebaa | `aiAssisted/aiMode/aiInstruction` on `GeneratedDocument` and `DocumentRevision`, `AiMode` exported, 3 fixture files updated, `streamChat` now a thin wrapper, one `streamSse` test |
| 2 documentAssist.ts | see git log (feat(14-09): document assist client) | 13 tests: estimator vectors 5523/5382/5379/4530, zero-room case, event typing, 413 sizes, abort, save invalidation, limits enabled/disabled |

## Deviations from Plan

None. Notes: the worktree was reset to 7063e88 per the startup check (its base differed). `useSaveAiRevision` invalidates `['assistant','freshness']` because that key exists in assistant.ts. The save response is typed `{ revision: number }` per 14-07's `SavedResponse`.

## Known Stubs

None.

## Threat Flags

None. No storage or console use in documentAssist.ts (grep clean).

## Self-Check: PASSED
