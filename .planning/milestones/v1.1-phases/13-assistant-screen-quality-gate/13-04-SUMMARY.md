---
phase: 13-assistant-screen-quality-gate
plan: 04
subsystem: frontend
tags: [assistant, react, citations, plain-text]
requires: [13-02]
provides:
  - assistant/citations.ts (chipFor)
  - assistant/AssistantTurn.tsx
  - assistant/AssistantChat.tsx (shared page and drawer body)
  - assistant/ScopeChip.tsx, assistant/FreshnessLine.tsx
affects: [13-06, 13-07]
key-files:
  created:
    - frontend/src/assistant/citations.ts
    - frontend/src/assistant/AssistantTurn.tsx
    - frontend/src/assistant/AssistantChat.tsx
    - frontend/src/assistant/ScopeChip.tsx
    - frontend/src/assistant/FreshnessLine.tsx
    - frontend/tests/assistant/citations.test.ts
    - frontend/tests/assistant/AssistantTurn.test.tsx
    - frontend/tests/assistant/AssistantChat.test.tsx
  modified:
    - .planning/phases/13-assistant-screen-quality-gate/13-UI-SPEC.md
key-decisions:
  - "Chip labels use DOCUMENT_TYPE_LABELS as-is, so a document chip reads 'Release Notes 4.2.0 rev 3'"
  - "BUSY countdown starts from retryAfterSeconds and ticks locally; the sr-only sentence carries the initial value"
  - "Live-region announcements are driven by status and tool transitions only, so token updates never re-announce"
requirements-completed: [ASST-06, ASST-07, ASST-08]
completed: 2026-10-18
---

# Phase 13 Plan 04: Chat UI body Summary

Shared chat body for the Assistant page and release drawer: plain-text turns with validated internal citation chips, a collapsed "How I answered" trace, all ASST-08 states, starters, composer with Stop, scope chip and index-freshness line.

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 1 citations, AssistantTurn, UI-SPEC amendment | see git log (feat(13-04): citation chips...) | 29 tests |
| 2 AssistantChat, ScopeChip, FreshnessLine | see git log (feat(13-04): chat body...) | 33 tests |

## Verification

- Frontend suite: 420 tests green (379 baseline + 41 new); `tsc -b` clean.
- No `dangerouslySetInnerHTML`, Markdown library, `localStorage` or `sessionStorage` in `frontend/src/assistant`.
- UI-SPEC no longer contains the "Commit a1b2c3d" chip row; it has an Amendments section (D-13-21, D-13-22, assumptions 4/5/7).

## Deviations from Plan

None in behaviour. TDD was done as tests plus implementation per task in one commit each. The `Release` chip row (no SourceView kind maps to it) was removed from the UI-SPEC chip table rather than kept.

## Known Stubs

None. `AssistantChat` takes `starters` and `scopeRow` from its caller; 13-06 and 13-07 wire them.

## Self-Check: PASSED
