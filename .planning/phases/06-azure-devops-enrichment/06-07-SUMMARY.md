---
phase: 06-azure-devops-enrichment
plan: 07
subsystem: frontend
tags: [react, code-links, release-detail]
requires: [06-05]
provides:
  - CodeLinks types, useRefreshCodeLinks mutation
  - CodeLinksChip / CodeLinksDetail components
  - Release page code column, Refresh code links action, freeze-time warning
affects: [06-09]
key-files:
  created:
    - frontend/src/components/CodeLinks.tsx
    - frontend/tests/components/CodeLinks.test.tsx
  modified:
    - frontend/src/api/releases.ts
    - frontend/src/components/ConfirmDialog.tsx
    - frontend/src/routes/ReleaseDetailRoute.tsx
    - frontend/tests/api/releases.test.ts
    - frontend/tests/components/ReleaseDetailRoute.test.tsx
key-decisions:
  - "Freeze warning count is computed client-side from items' codeLinks.status; no new endpoint"
requirements-completed: [ADO-01, ADO-03]
duration: ~15 min
completed: 2026-10-12
---

# Phase 6 Plan 07: Code links UI Summary

Each ticket row on the release page now shows a code-link chip ("3 commits · 1 PR", "No link", "Couldn't fetch (reason)" with Retry, "Not checked") that expands to commits, PRs and branches. Open releases get a "Refresh code links" button, and the freeze dialog warns about failed or unchecked tickets while still allowing the freeze.

## Tasks

| Task | Commit | Result |
|------|--------|--------|
| 1. Types, mutation, components | 7460a03 | CodeLinks types, useRefreshCodeLinks, CodeLinksChip/Detail, safeAdoHref, REASON_LABELS |
| 2. Page wiring and freeze warning | see git log (second feat(06-07) commit) | Code column, header button, per-ticket Retry, ConfirmDialog children, freeze warning with "Refresh code links first" |

## Verification

Frontend baseline 232 tests; now 253 pass (25 files). `npx tsc -b` clean.

## Deviations from Plan

None in behaviour. The plan said to add `codeLinks` to existing `ReleaseItem` fixtures; none existed (all fixtures had empty `items`), so only new fixtures were added. The environment instruction to run `npm ci` in the worktree overrode the plan's "no npm ci" note.

Worktree base reset to d9b6bae after the HEAD assertions passed.

## Known Stubs

None.

## Threat Flags

None. T-06-29 (anchors only for https://dev.azure.com/, text rendering, no dangerouslySetInnerHTML, tested with `javascript:`), T-06-30 (`target="_blank" rel="noopener noreferrer"`), T-06-31 (existing apiFetch) are mitigated as planned.

## Self-Check: PASSED
