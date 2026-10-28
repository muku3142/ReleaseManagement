---
phase: 05-document-generation
plan: 11
subsystem: ui
tags: [react, tanstack-router, tanstack-query, tailwind, document-generation]

# Dependency graph
requires:
  - phase: 05-document-generation (plan 04)
    provides: "DocumentController's generate/regenerate endpoints and DOCUMENT_NOT_FOUND/NO_ACTIVE_TEMPLATE reason codes"
  - phase: 05-document-generation (plan 08)
    provides: "export.pdf/export.docx download endpoints and EXPORT_FAILED reason code"
  - phase: 05-document-generation (plan 10)
    provides: "frontend/src/api/documents.ts hooks (useDocumentReadiness, useDocumentRevisions, useGenerateDocument, useExportDocument), ReadinessPill, apiFetchBlob/triggerDownload"
provides:
  - "DocumentsSection - one row per document type on release detail, driving generate/regenerate, export PDF/DOCX and revision history for all three document types"
  - "#documents anchor on ReleaseDetailRoute, the landing target for the /releases readiness links plan 05-10 shipped"
affects: [05-12-document-viewer]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Per-document-type sub-component (DocumentRow) each holding its own useGenerateDocument()/useDocumentRevisions() hook instances, so pending/error state cannot bleed across document types without any manual Record<DocumentType, ...> bookkeeping"
    - "One ExportButtons instance per (row, revision) usage site - a shared component reused for both the latest revision's export actions and each revision-history list item's own export actions, each with an isolated useExportDocument() mutation so a superseded revision's export never disables a sibling's"
    - "isPending(format) derived from mutation.isPending && mutation.variables?.format === format, rather than a second piece of local state, to distinguish which of two buttons sharing one mutation instance is the one currently in flight"

key-files:
  created:
    - frontend/src/components/DocumentsSection.tsx
    - frontend/tests/components/DocumentsSection.test.tsx
  modified:
    - frontend/src/routes/ReleaseDetailRoute.tsx
    - frontend/tests/components/ReleaseDetailRoute.test.tsx

key-decisions:
  - "Readiness state (NO_ACTIVE_TEMPLATE/NOT_GENERATED/STALE/UP_TO_DATE) is read from the bulk useDocumentReadiness() grid and treated as authoritative for which Row 2/Row 3 band to render - not re-derived from useDocumentRevisions()'s revision count. This matters when a document type has prior revisions but its template was later deactivated: the readiness state is NO_ACTIVE_TEMPLATE regardless of revision history, so Row 3 shows only the Go to templates link, matching the plan's explicit 'no generate control is rendered at all' instruction."
  - "View links to the document viewer route (/releases/$releaseId/documents/$documentType) are plain <a href> elements, not typed TanStack Router <Link>, mirroring ReadinessPill's established pattern from 05-10 - that route is registered by 05-12, which has not run yet. The href is already correct and needs no change once that route lands."
  - "The Generate/Regenerate button is a single JSX element whose label and className both switch on hasRevisions/readinessState, rather than two separate button blocks - this keeps the literal 'Generating…' pending-label string appearing exactly once in the file (grep-checked by the plan's acceptance criteria) instead of duplicated across a Generate-only and a Regenerate-only render path."
  - "ExportButtons is a single shared component instantiated once per exportable revision (the main row's latest revision, plus one per revision-history list item) - this keeps the 'Exporting PDF…'/'Exporting DOCX…' literal strings appearing exactly once each in the file while still giving every revision its own independent pending/error state."

patterns-established: []

requirements-completed: [DOC-01, DOC-05, DOC-06, DOC-07, DOC-09]

# Metrics
duration: ~18min
completed: 2026-08-10
---

# Phase 5 Plan 11: Documents Section on Release Detail Summary

**DocumentsSection renders one row per document type on `/releases/$releaseId` - readiness pill, state-specific body copy, Generate/Regenerate, View, Export PDF/DOCX and a collapsed-by-default newest-first revision history - all four readiness states covered, error/pending state scoped per document type, and no confirmation dialog on regeneration since it only appends.**

## Performance

- **Duration:** ~18 min
- **Tasks:** 2/2 complete
- **Files modified:** 2 created, 2 modified

## Accomplishments

- `DocumentsSection` renders `DOCUMENT_TYPES` order (Release Notes, Runbook, Approval) inside
  `<section id="documents">`, reusing `ReadinessPill` verbatim for the pill and driving state
  entirely from `useDocumentReadiness()`'s bulk grid plus a per-type `useDocumentRevisions()` call.
- Each `DocumentRow` holds its own `useGenerateDocument()` instance, so a Release Notes generation
  failure or pending state never appears under Runbook or Approval (T-05-65) - verified by
  `generationErrorIsScopedToItsOwnRow` and `generatePendingDisablesThatRowsExportsOnly`.
- `ExportButtons` is a shared component instantiated once per exportable revision (the latest, plus
  one per revision-history row), each with its own `useExportDocument()` mutation - so exporting a
  superseded revision's PDF never disables its DOCX button or any other revision's export controls
  (D-05-06), and only the clicked button shows the `Exporting PDF…`/`Exporting DOCX…` pending label.
- Regenerate is wired directly to the mutation with no `ConfirmDialog` anywhere in the file -
  regeneration is additive (D-05-06), never destructive, so there is nothing to confirm.
- The revision-history disclosure (`▸`/`▾`, `aria-expanded`, `aria-label="Show/Hide revision
  history"`) mirrors release detail's existing ticket-expand pattern exactly, collapsed by default,
  listing revisions newest-first with `(latest)`/`(superseded)` qualifiers and each revision's own
  View/Export actions.
- `DocumentsSection` takes no `status`/`isOpen` prop at all, so it structurally cannot branch on
  release status - it renders identically for OPEN and FROZEN releases, mounted as the last element
  of `ReleaseDetailRoute`'s render tree, after the manual-items table and form.
- Full frontend suite: **170 tests / 19 files**, all green (154 baseline + 15 in
  `DocumentsSection.test.tsx` + 1 added to `ReleaseDetailRoute.test.tsx`).
- No drag interaction anywhere in the new surface - every control is a native `<button>`, `<a>` or
  disclosure `<button aria-expanded>` (D-05-11).

## Task Commits

Each task was committed atomically:

1. **Task 1: DocumentsSection - four states, generate, exports, revision history** - `034cc1a` (feat)
2. **Task 2: Mount the section on the release detail route and test the whole surface** - `ef5418a` (feat)

_No plan-metadata commit here - the orchestrator owns STATE.md/ROADMAP.md writes after all wave agents complete, per this plan's execution mode._

## Files Created/Modified

- `frontend/src/components/DocumentsSection.tsx` - `DocumentsSection`, `DocumentRow`, `DocumentViewLink`, `ExportButtons`, `RevisionRow`
- `frontend/tests/components/DocumentsSection.test.tsx` - 15 tests covering all four states, generate/regenerate, export pending/error scoping, no-dialog regeneration, and revision history
- `frontend/src/routes/ReleaseDetailRoute.tsx` - one import, one `<DocumentsSection>` element as the last item in the render tree
- `frontend/tests/components/ReleaseDetailRoute.test.tsx` - `documentsSectionIsRenderedLastWithItsAnchorId`

## Decisions Made

See `key-decisions` in frontmatter. In short: readiness state (not local revision-count math) drives
which Row 2/Row 3 band renders; View links stay plain anchors until 05-12 registers the document
viewer route; the Generate/Regenerate button and the Export PDF/DOCX buttons are each a single
shared JSX element/component so the plan's literal-string grep checks (`Generating…`,
`Exporting PDF…`, `Exporting DOCX…`) hold regardless of how many states or revisions render them.

## Deviations from Plan

None - plan executed exactly as written. Both tasks' acceptance-criteria greps and the `tsc -b`
(run twice) / `vitest run` verification commands passed without needing any Rule 1-3 auto-fixes.

## Issues Encountered

- Two of the pending-state assertions (`exportPendingDisablesOnlyTheClickedButton`, and
  proactively `generateCallsTheMutationAndShowsGeneratingLabel` /
  `generatePendingDisablesThatRowsExportsOnly`) needed an extra `await flush()` between the click
  and the assertion. TanStack Query's mutation-state notification is scheduled via a real
  `setTimeout(0)` (`systemSetTimeoutZero` in `@tanstack/query-core`), a macrotask that a bare
  `await act(async () => element.click())` does not reliably drain in every run. Added `await
  flush()` (the existing helper that awaits a `setTimeout(resolve, 0)`) after each such click for
  deterministic pending-state assertions. Not a plan deviation - a test-only fix in the same file
  the plan asked me to write, applied before committing.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ReadinessPill`'s Stale/Up-to-date states still render a plain `<a href>` rather than a typed
  `<Link>` - the document viewer route (`/releases/$releaseId/documents/$documentType`) is not
  registered yet. This plan's `DocumentViewLink`/`RevisionRow` View links follow the same
  plain-anchor convention for the same reason. **Whichever plan registers that route (05-12) should
  upgrade both `ReadinessPill` and `DocumentsSection`'s View links to typed `<Link>` in the same
  pass** - the rendered `href` values are already correct in both places, so this is a pure
  client-side-navigation upgrade, not a correctness fix.
- No blockers for 05-12. `DocumentsSection` calls no document-viewer-specific API beyond what
  `frontend/src/api/documents.ts` already exposes.

---
*Phase: 05-document-generation*
*Completed: 2026-08-10*

## Self-Check: PASSED

Both created files (`frontend/src/components/DocumentsSection.tsx`,
`frontend/tests/components/DocumentsSection.test.tsx`) verified present on disk; both task commits
(`034cc1a`, `ef5418a`) verified present in `git log`. Full frontend suite (170 tests / 19 files)
green across two consecutive `vitest run` invocations; `tsc -b` clean across two consecutive runs.
