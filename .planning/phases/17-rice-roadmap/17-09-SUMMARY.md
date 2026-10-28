---
phase: 17-rice-roadmap
plan: 09
subsystem: frontend-roadmap
tags: [roadmap, dialogs, react]
requires: [17-03]
provides:
  - "RoadmapAddResult message component"
  - "AddFromJiraDialog, AddFromReleaseDialog, AddManualItemDialog"
affects: [17-11, 17-12]
key-files:
  created:
    - frontend/src/roadmap/RoadmapAddResult.tsx
    - frontend/src/roadmap/AddFromJiraDialog.tsx
    - frontend/src/roadmap/AddFromReleaseDialog.tsx
    - frontend/src/roadmap/AddManualItemDialog.tsx
    - frontend/tests/roadmap/RoadmapAddResult.test.tsx
    - frontend/tests/roadmap/AddDialogs.test.tsx
metrics:
  tasks: 2
  completed: 2026-10-25
---

# Phase 17 Plan 09: Roadmap add flows Summary

Three add dialogs (Jira, release, manual) plus a shared add-result message with duplicate "View it" links.

## Commits
- 9e5f5eb feat(17-09): add the roadmap add-result message
- see git log: feat(17-09): add the three roadmap add dialogs

## Verification
`npx tsc -b` clean; `npx vitest run tests/roadmap`: 2 files, 20 tests passed.

## Deviations from Plan
- `ReleaseItem` is flat (`items[].key`, `.summary`), not `items[].ticket.*` as the plan interface said; used the real shape.
- `useAddFromJira` takes `string[]` directly (17-03 contract), not `{keys}`; the wire body is still `{keys}`.
- The release ticket checklist is a child component so `useRelease` is only called once a release is chosen (the hook has no `enabled` option).
- `/roadmap` is not registered yet, so links are plain anchors; 17-12 may switch them to the router Link.

## Known Stubs
None.

## Self-Check: PASSED
