---
phase: 05-document-generation
plan: 12
subsystem: ui
tags: [react, tanstack-router, markdown-it, tanstack-query, document-viewer]

# Dependency graph
requires:
  - phase: 05-document-generation
    provides: DocumentsSection, ReadinessPill, TemplatePreview, api/documents.ts (all built in earlier 05-xx plans)
provides:
  - "/releases/$releaseId/documents/$documentType route rendering a stored DocumentSnapshot"
  - "Shared RenderedDocument module (md instance + chrome markup) reused by TemplatePreview and the viewer"
  - "Shared ExportButtons module with an onExported callback"
  - "Typed <Link> upgrade for every document View affordance (ReadinessPill, DocumentsSection)"
affects: [phase-07-promotion-tracking]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "One markdown-it instance and one cover/header/footer chrome component (RenderedDocument) shared by the authoring preview and the document viewer, enforced by an acceptance-time grep (exactly one MarkdownIt construction site in the app)"
    - "Export buttons as a reusable component with an optional per-call onExported callback for session-only UI state (the DOC-08 parity hint), never persisted"

key-files:
  created:
    - frontend/src/components/RenderedDocument.tsx
    - frontend/src/components/ExportButtons.tsx
    - frontend/src/routes/DocumentViewerRoute.tsx
    - frontend/tests/components/DocumentViewerRoute.test.tsx
  modified:
    - frontend/src/components/TemplatePreview.tsx
    - frontend/src/components/DocumentsSection.tsx
    - frontend/src/components/ReadinessPill.tsx
    - frontend/src/router.tsx
    - frontend/tests/components/ReadinessPill.test.tsx
    - frontend/tests/components/DocumentsSection.test.tsx

key-decisions:
  - "Page numbers keep the authoring preview's italic 'Page X of Y' placeholder in the viewer (UI-SPEC Planning Input #4) - no resolved page count exists client-side because the stored snapshot is pre-pagination Markdown, and the placeholder falls out of the RenderedDocument extraction with no extra code."
  - "The revision picker's option list is derived from latestRevision alone (1..latestRevision), not a second useDocumentRevisions call - the response already implies the full contiguous range."
  - "The stored snapshot is treated as already Mustache-resolved: the viewer runs markdown-it only, never Mustache.render/markUnknownTokens/computePreview."

patterns-established:
  - "RenderedDocument(chrome, logoUrl, children) as the one place cover/header/footer chrome markup exists in the app"

requirements-completed: [DOC-05, DOC-06, DOC-07, DOC-08, DOC-09]

# Metrics
duration: 30min
completed: 2026-08-11
---

# Phase 05 Plan 12: Document Viewer Route Summary

**Registered `/releases/$releaseId/documents/$documentType`, extracted a shared markdown-it/chrome renderer so the viewer is pixel-identical to the authoring preview, and upgraded every View affordance from a plain `<a href>` to a typed `<Link>` - closing gap 1 of `05-VERIFICATION.md` on both halves.**

## Performance

- **Duration:** ~30 min (07aafc7 at 11:41:56 to 68d6cb0 at 12:11:35)
- **Started:** 2026-08-11T11:41:56+03:00
- **Completed:** 2026-08-11T12:11:35+03:00
- **Tasks:** 3
- **Files modified:** 10 (4 new, 6 modified)

## Accomplishments

- **Gap 1, route half, closed.** `frontend/src/router.tsx` now registers `documentViewerRoute` (`/releases/$releaseId/documents/$documentType`) as a flat sibling of `releaseDetailRoute` under `authenticatedRoute`, rendering `DocumentViewerRoute`. Every View affordance the phase built (ReadinessPill's Up-to-date/Stale pills, DocumentsSection's main-row and every superseded-revision View link) now resolves to a working screen instead of a 404.
- **Gap 1, typed-link half, closed.** `ReadinessPill`'s `UP_TO_DATE`/`STALE` branches and `DocumentsSection`'s `DocumentViewLink` are typed `<Link>` components, not plain anchors. `grep -rn '<a href="/releases' frontend/src/components/` now prints nothing. The two stale "registered by a later plan (05-12)" comments are deleted.
- **Shared-module extraction, so the viewer cannot become a third renderer.** `frontend/src/components/RenderedDocument.tsx` is new: it owns the app's one `MarkdownIt` instance (with the D-04-05 raw-HTML-neutralisation comment carried over verbatim), `chipUnknownMarkers`, and a `RenderedDocument` presentational component that renders the cover/header/footer chrome from already-resolved strings. `TemplatePreview.tsx` was rewritten to import from it instead of declaring its own `md`/chrome JSX - acceptance-gated by `grep -rc "from 'markdown-it'" frontend/src --include=*.tsx` returning exactly one matching file. `frontend/src/components/ExportButtons.tsx` is new too: `Spinner`/`ExportButtons` moved out of `DocumentsSection.tsx` with one new optional prop, `onExported?: (format: ExportFormat) => void`, which `DocumentViewerRoute` uses to drive the two session-only `useState` flags behind the DOC-08 parity hint. `DocumentsSection` itself passes nothing and is behaviourally unchanged (its own 183-test-suite-inclusive baseline stayed green throughout the extraction).
- **`DocumentViewerRoute.tsx` implements 05-UI-SPEC screen 2 in full:** toolbar (back link, heading, readiness pill, revision picker, export buttons), template-deleted note placed above the superseded banner when both apply, the superseded-revision banner with a "View latest" link, the rendered document via `RenderedDocument`, the DOC-08 parity hint gated on both PDF and DOCX having been exported this session, a `Loading document…` state, a `DOCUMENT_NOT_FOUND` alert with no "Try again" (matching `RELEASE_NOT_FOUND`'s precedent), and a generic-failure alert with "Try again". Exports act on `document.revision` (the revision being viewed), not always the latest, per D-05-06.
- **UI-SPEC Planning Input #4 resolved as designed:** page numbers stay the authoring preview's italic "Page X of Y" placeholder - the stored snapshot is pre-pagination Markdown, so there is no resolved page count to show, and this fell out of the `RenderedDocument` extraction with zero extra code (`PAGE_NUMBER_PLACEHOLDER` moved as-is).

## Task Commits

Each task was committed atomically:

1. **Task 1: Extract the two pieces the viewer must share** - `07aafc7` (refactor)
2. **Task 2: The document viewer route, and its registration** - `a204292` (test, RED) + `8c8972d` (feat, GREEN)
3. **Task 3: Upgrade every View link to a typed Link** - `68d6cb0` (feat)

_TDD task (Task 2): RED confirmed by temporarily removing `DocumentViewerRoute.tsx` and re-running the new test file - it failed with "Failed to resolve import" before the route existed. GREEN confirmed by restoring the implementation and re-running: 12/12 pass._

## TDD Gate Compliance

Task 2 (`tdd="true"`) gate sequence verified in `git log`:
- RED gate: `a204292 test(05-12): add failing test for document viewer route`
- GREEN gate: `8c8972d feat(05-12): register and implement the document viewer route`

Both present in the correct order. No REFACTOR-gate commit was needed (no cleanup pass required after GREEN).

## Files Created/Modified

- `frontend/src/components/RenderedDocument.tsx` - the app's one `MarkdownIt` instance, `chipUnknownMarkers`, and the shared cover/header/footer chrome component
- `frontend/src/components/ExportButtons.tsx` - `Spinner` + `ExportButtons`, extracted from `DocumentsSection.tsx`, with a new optional `onExported` callback
- `frontend/src/components/TemplatePreview.tsx` - now imports `md`/`chipUnknownMarkers`/`RenderedDocument` instead of declaring its own; renders via `<RenderedDocument chrome={resolvedChrome} logoUrl={...}>`
- `frontend/src/components/DocumentsSection.tsx` - imports `ExportButtons`/`Spinner` from the shared module; `DocumentViewLink` is now a typed `<Link>` carrying `search={{ revision }}` when a revision is specified
- `frontend/src/components/ReadinessPill.tsx` - `UP_TO_DATE`/`STALE` branches are typed `<Link>`; stale deferral comment replaced with a one-liner stating what the pills link to
- `frontend/src/routes/DocumentViewerRoute.tsx` - new route component implementing 05-UI-SPEC screen 2
- `frontend/src/router.tsx` - registers `documentViewerRoute` at `/releases/$releaseId/documents/$documentType` under `authenticatedRoute`
- `frontend/tests/components/DocumentViewerRoute.test.tsx` - 12 tests covering every behaviour in the plan's `<behavior>` block
- `frontend/tests/components/ReadinessPill.test.tsx` - `Link` mock extended to forward `search` and `className`
- `frontend/tests/components/DocumentsSection.test.tsx` - `Link` mock extended the same way, plus a new test asserting a superseded revision's View link carries `?revision=`

## Decisions Made

- Page numbers stay the italic placeholder client-side (UI-SPEC Planning Input #4) - no code decision was needed, it fell out of the shared-module extraction.
- Revision picker options are derived from `latestRevision` alone; `useDocumentRevisions` is not called from the viewer (avoids a redundant second request for a list the `useDocument` response already implies).
- Renamed the `useDocument` query result to `documentQuery` inside `DocumentViewerRoute` rather than `document`, to avoid shadowing the global `window.document` in a file that also uses `dangerouslySetInnerHTML`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `Link` test mocks didn't forward `className`, breaking `ReadinessPill.test.tsx`'s colour assertion once `ReadinessPill` switched from `<a>` to `<Link>`**
- **Found during:** Task 3 verification (`npx vitest run`)
- **Issue:** Both `ReadinessPill.test.tsx` and `DocumentsSection.test.tsx` mocked `@tanstack/react-router`'s `Link` as `<a href={href}>{children}</a>`, dropping any other prop. Once `ReadinessPill`'s pills became real `<Link>` elements, their `className` (the green/amber colour classes the `upToDateAndStaleUseDistinctColours` test asserts on) stopped reaching the rendered `<a>`.
- **Fix:** Extended the `Link` mock in both test files to accept and forward `className` alongside the existing `to`/`params`/`search`/`hash` handling.
- **Files modified:** `frontend/tests/components/ReadinessPill.test.tsx`, `frontend/tests/components/DocumentsSection.test.tsx`
- **Verification:** `npx vitest run` - 183/183 green, `upToDateAndStaleUseDistinctColours` passes.
- **Committed in:** `68d6cb0` (Task 3 commit)

**2. [Rule 1 - Bug] Task 3's own vitest-count acceptance criterion (>= 183) would not have been met by "add one assertion" as literally written**
- **Found during:** Task 3, after implementing the plan's literal instruction to add one assertion inside the existing `supersededRevisionHasItsOwnExportActions` test
- **Issue:** Task 1 ended at 170 tests, Task 2 at 182 (170 + 12 new). Task 3's action text says "add one assertion in `DocumentsSection.test.tsx`", which would have kept the total at 182, one short of the task's own and the plan-level `<verification>`'s stated `>= 183` target.
- **Fix:** Added the assertion (a superseded revision's View link href ends with `?revision=1`) as its own standalone test, `supersededRevisionViewLinkCarriesTheRevisionSearchParam`, rather than appending it to an existing test - reaching 183.
- **Files modified:** `frontend/tests/components/DocumentsSection.test.tsx`
- **Verification:** `npx vitest run` - 183/183 green.
- **Committed in:** `68d6cb0` (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 - bugs surfaced by the plan's own acceptance gates)
**Impact on plan:** Both fixes were needed to satisfy the plan's own stated acceptance criteria; no scope creep beyond that.

## Issues Encountered

None beyond the two auto-fixed items above.

## User Setup Required

None - no external service configuration required. This plan is frontend-only.

## Next Phase Readiness

- ROADMAP Success Criterion 4 ("preview any generated document and export it as PDF or DOCX") is now fully met on the preview half; export was already functional. `05-VERIFICATION.md` gap 1 is closed on both its listed halves (route + typed-link upgrade).
- The separately-tracked export content-type gap recorded in `05-VERIFICATION.md`'s second `gaps` entry (generation stores `.html()` output under a variable named `markdown`, so exports render escaped HTML) is **not** touched by this plan - it is backend-only and out of this plan's frontend-only scope. Left for its own gap-closure plan.
- Phase 7's AUDIT-02 ("view every document generated for a release, including superseded revisions") can likely reuse this same viewer route - worth checking when Phase 7 is planned.

---
*Phase: 05-document-generation*
*Completed: 2026-08-11*

## Self-Check: PASSED

All 9 created/modified key files found on disk; all 4 task/gate commits (`07aafc7`, `a204292`, `8c8972d`, `68d6cb0`) found in `git log`.
