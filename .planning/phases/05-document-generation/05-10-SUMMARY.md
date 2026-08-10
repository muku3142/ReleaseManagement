---
phase: 05-document-generation
plan: 10
subsystem: api
tags: [spring-boot, react-query, tanstack-router, document-readiness, tailwind]

# Dependency graph
requires:
  - phase: 05-document-generation (plan 03)
    provides: "release.items_changed_at column (V5, unmapped by any entity until this plan), DocumentSnapshotRepository.findLatestForAllReleases()"
  - phase: 05-document-generation (plan 04)
    provides: "DocumentController's endpoint shape/DTOs this plan's frontend hooks bind to"
  - phase: 05-document-generation (plan 08)
    provides: "export.pdf/export.docx endpoints and filename format useExportDocument binds to"
provides:
  - "Release/ReleaseSummary.itemsChangedAt, stamped on every item-set mutation via a shared stampItemsChanged helper"
  - "DocumentReadinessService.state()/all() - the single staleness comparison and the fixed-query-count cross-release grid"
  - "GET /api/documents/readiness"
  - "frontend/src/api/documents.ts - every document hook (readiness, generate, export, revisions) plans 05-11/05-12 consume"
  - "frontend/src/api/client.ts's apiFetchBlob/triggerDownload - the app's first binary-response path"
  - "ReadinessPill - the shared four-state pill component"
  - "Three readiness columns on /releases"
affects: [05-11-releases-detail-documents-section, 05-12-document-viewer]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Plain <a href> instead of a typed TanStack Router <Link> for a route another plan in the same phase has not registered yet - upgrade to a typed Link is a trivial follow-up once that route lands"
    - "A mutation (not a query) for binary export downloads, wrapping apiFetchBlob + triggerDownload, so pending/error state is real and the result is never cached"

key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/document/DocumentReadinessState.java
    - src/main/java/com/evision/releasemgmt/application/DocumentReadinessService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/DocumentReadinessController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReadinessEntryResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReadinessResponse.java
    - src/test/java/com/evision/releasemgmt/application/DocumentReadinessServiceTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/DocumentReadinessControllerTest.java
    - frontend/src/api/documents.ts
    - frontend/src/components/ReadinessPill.tsx
    - frontend/tests/api/documents.test.ts
    - frontend/tests/components/ReadinessPill.test.tsx
    - frontend/tests/components/ReleasesRoute.test.tsx
  modified:
    - src/main/java/com/evision/releasemgmt/domain/release/Release.java
    - src/main/java/com/evision/releasemgmt/domain/release/ReleaseSummary.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseSummaryRow.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseJpaRepository.java
    - frontend/src/api/client.ts
    - frontend/src/api/auth.ts
    - frontend/src/routes/ReleasesRoute.tsx
    - several test files updating Release/ReleaseSummary record construction sites

key-decisions:
  - "The initial item set at Release creation is stamped with itemsChangedAt = createdAt inside toEntity(Release), a distinct call site from the six-method stampItemsChanged helper the plan asked to extract for the mutation methods"
  - "Stale/Up-to-date pill states render a plain <a href> rather than a typed <Link> because the document viewer route (/releases/$releaseId/documents/$documentType) is registered by a later plan in this same phase (05-11/05-12) and does not exist in router.tsx yet - a typed Link to an unregistered route fails tsc. The href is already correct and needs no change once that route lands."
  - "useGenerateDocument invalidates ['releases'] (item counts/status), the readiness key, and this document's document/revision keys rather than trying to patch cache state manually - a generate that leaves a stale pill on screen was called out explicitly as the whole feature failing"

patterns-established:
  - "stampItemsChanged(ReleaseEntity) is now the one place that marks a release's item set as changed - any future release mutation must call it, not set the column directly"

requirements-completed: [DOC-09]

# Metrics
duration: ~50min
completed: 2026-08-10
---

# Phase 5 Plan 10: Cross-Release Document Readiness Summary

**DocumentReadinessService computes the four-state readiness grid (NO_ACTIVE_TEMPLATE/NOT_GENERATED/STALE/UP_TO_DATE) from `release.items_changed_at` (newly mapped and stamped) plus `last_refreshed_at` in a fixed number of queries, exposed as one bulk endpoint and rendered as three new `/releases` columns via a shared ReadinessPill and a new frontend document API module.**

## Performance

- **Duration:** ~50 min
- **Tasks:** 3/3 complete
- **Files modified:** 12 created, 16 modified

## Accomplishments

- `Release`/`ReleaseSummary` gained `itemsChangedAt`; `ReleaseRepositoryAdapter` stamps it via a single
  extracted `stampItemsChanged` helper called from all six item-set-mutating methods
  (`addItems`, `removeItem`, `replaceItemSnapshots`, `addManualItem`, `updateManualItem`,
  `removeManualItem`), so a ticket **removal** — which leaves no timestamp anywhere else — is now
  visible to the staleness comparison, satisfying D-05-09's explicit removal-awareness requirement.
- `DocumentReadinessService.state(...)` is the single pure staleness comparison; `all()` answers the
  whole (release × document type) grid in a fixed number of queries: one `search("")`, three
  `findActive(type)` calls (one per document type, not per release), and one
  `findLatestForAllReleases()` — proven by `allDoesNotQueryPerRelease`'s Mockito invocation-count
  assertions (T-05-59).
- `GET /api/documents/readiness` exposes the grid as `{ entries: [...] }`, returning an empty array
  rather than 404 on an empty database.
- `frontend/src/api/client.ts` gained the app's first binary-response path — `apiFetchBlob` (reuses
  the existing `ApiError`/`ProblemDetail` parsing) and `triggerDownload` (revokes the object URL
  immediately after the synthetic click, closing T-05-57's blob-URL-leak risk).
- `frontend/src/api/documents.ts` exposes every hook plans 05-11/05-12 need: `useDocumentReadiness`
  + `readinessFor` (O(1) per table cell), `useDocument`, `useDocumentRevisions`,
  `useGenerateDocument` (invalidates `['releases']`, the readiness key, and this document's
  document/revision keys), `useExportDocument` (a mutation over `apiFetchBlob`/`triggerDownload`,
  filename composed client-side through the same `[A-Za-z0-9 ._-]` allowlist the backend export
  endpoint uses), and `documentLogoUrl`.
- `ReadinessPill` renders the four states with the UI-SPEC's exact copy/classes
  (`text-green-700`/`bg-green-50` for Up to date, `text-amber-600`/`bg-amber-50` for Stale,
  plain gray text for the other two) and no `rounded-full`/`green-600` anywhere.
- `ReleasesRoute` appends three columns (Release Notes · Runbook · Approval) fed by
  `useDocumentReadiness`; the readiness query is independent of the releases query, so a readiness
  failure renders the six original columns normally with an em dash in the three new cells
  (T-05-60) instead of blanking the table.
- Full backend suite: **541 tests**, all green (519 baseline + 22 new/updated across
  `DocumentReadinessServiceTest` (9), `DocumentReadinessControllerTest` (2), and record-construction
  fixes across 8 pre-existing test files).
- Full frontend suite: **154 tests / 18 files**, all green (139 baseline + 6 in `documents.test.ts` +
  6 in `ReadinessPill.test.tsx` + 3 in `ReleasesRoute.test.tsx`).

## Task Commits

Each task was committed atomically:

1. **Task 1: items_changed_at, DocumentReadinessService and the bulk readiness endpoint** - `32a481d` (feat)
2. **Task 2: Frontend document API module, binary download and new reason messages** - `095d81a` (feat)
3. **Task 3: ReadinessPill and the three /releases columns** - `af96bad` (feat)

_No plan-metadata commit here - the orchestrator owns STATE.md/ROADMAP.md writes after all wave agents complete, per this plan's execution mode._

## Files Created/Modified

- `src/main/java/com/evision/releasemgmt/domain/document/DocumentReadinessState.java` - the four-value enum
- `src/main/java/com/evision/releasemgmt/application/DocumentReadinessService.java` - `state(...)`, `all()`
- `src/main/java/com/evision/releasemgmt/adapters/web/DocumentReadinessController.java` - `GET /api/documents/readiness`
- `src/main/java/com/evision/releasemgmt/adapters/web/dto/ReadinessEntryResponse.java`, `ReadinessResponse.java`
- `src/main/java/com/evision/releasemgmt/domain/release/Release.java`, `ReleaseSummary.java` - `itemsChangedAt`
- `src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseEntity.java` - `items_changed_at` column mapping
- `src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java` - `stampItemsChanged` helper + 6 call sites + `toEntity`/`toDomain`/`toSummary` mapping
- `src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseSummaryRow.java`, `ReleaseJpaRepository.java` - projection field
- `src/test/java/com/evision/releasemgmt/application/DocumentReadinessServiceTest.java` - 9 tests (one per `<behavior>` bullet plus `allDoesNotQueryPerRelease`)
- `src/test/java/com/evision/releasemgmt/adapters/web/DocumentReadinessControllerTest.java` - 2 tests
- `frontend/src/api/client.ts` - `apiFetchBlob`, `triggerDownload`
- `frontend/src/api/documents.ts` - the document API module
- `frontend/src/api/auth.ts` - `DOCUMENT_NOT_FOUND`/`NO_ACTIVE_TEMPLATE`/`EXPORT_FAILED` copy
- `frontend/src/components/ReadinessPill.tsx` - the shared four-state pill
- `frontend/src/routes/ReleasesRoute.tsx` - three appended columns
- `frontend/tests/api/documents.test.ts` - 6 tests
- `frontend/tests/components/ReadinessPill.test.tsx` - 6 tests
- `frontend/tests/components/ReleasesRoute.test.tsx` - 3 tests (new file)
- Eight pre-existing test files (`DocumentGenerationServiceTest`, `ReleaseServiceManualItemsTest`,
  `ReleaseServiceTicketsTest`, `ReleaseViewAssemblerTest`, `DocumentExportControllerTest`,
  `ReleaseControllerTest`) updated only at their `Release`/`ReleaseSummary` record construction
  sites to add the new `itemsChangedAt` positional argument - no assertion logic changed.

## Decisions Made

- `toEntity(Release release)` (used only by `create`) stamps `itemsChangedAt` to `release.createdAt()`
  directly rather than routing through `stampItemsChanged` - the plan's action text distinguished
  "set it at create" from the six-method helper explicitly, and `create` builds a brand-new entity
  rather than mutating a loaded one, so sharing the helper there would not fit its `(ReleaseEntity
  entity)` shape without an awkward extra `Instant.now()` parameter.
- The frontend's `ReadinessPill` uses a plain `<a href>` (not a typed TanStack Router `<Link>`) for
  the Stale/Up-to-date states, because their target route is registered by plan 05-11/05-12, which
  runs after this one in wave 5. A typed `Link to="/releases/$releaseId/documents/$documentType"`
  fails `tsc` today since that route string isn't in the router's registered tree yet. The rendered
  `href` is already the correct final URL - no change is needed here once that route exists, and
  `everyStateIsAKeyboardReachableLink` still passes since a plain anchor is exactly as keyboard-reachable
  as a typed `Link` (both render `<a>`).
- `documentLogoUrl`/`useDocument`/`useDocumentRevisions` were built exactly per the plan's method
  list even though this plan's own screens don't call them yet - they exist specifically so 05-11/05-12
  have a stable module to import from on day one, per the plan's explicit purpose statement.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Reworded a comment that tripped its own negative grep**
- **Found during:** Task 2, immediately after writing `client.ts`
- **Issue:** The initial comment on `triggerDownload` read "revokeObjectURL is not optional: ...",
  which combined with the actual `URL.revokeObjectURL(url)` call to make `grep -c 'revokeObjectURL'
  frontend/src/api/client.ts` return `2` instead of the plan's literal `1`.
- **Fix:** Reworded the comment to convey the same reasoning ("cleaning up the object URL
  immediately after the click is not optional...") without repeating the flagged identifier.
  Re-ran the grep to confirm `1` before committing.
- **Files modified:** `frontend/src/api/client.ts`
- **Verification:** `grep -c 'revokeObjectURL' frontend/src/api/client.ts` → `1`; full frontend
  suite re-run green.
- **Committed in:** `095d81a` (Task 2 commit) - fixed before the commit, not a follow-up.

---

**Total deviations:** 1 auto-fixed (Rule 1, self-inflicted grep/comment issue caught before
committing). No architectural changes, no scope creep beyond files the plan already listed.

## Issues Encountered

- Discovered while implementing Task 3 that the document viewer route this plan's UI-SPEC/action
  text specifies as the Stale/Up-to-date link target does not exist in `router.tsx` - it is built by
  plan 05-11/05-12, which run after this plan in wave 5. Resolved via the plain-`<a>` deviation
  documented above rather than registering a placeholder route (out of this plan's declared file
  scope) or blocking on a checkpoint.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `frontend/src/api/documents.ts`'s exact exported names are stable for 05-11/05-12 to import
  directly: `DocumentReadinessState`, `ReadinessEntry`, `ReadinessResponse`, `DocumentRevision`,
  `DocumentRevisionListResponse`, `GeneratedDocument`, `ExportFormat`, `readinessQueryKey`,
  `documentQueryKey`, `documentRevisionsQueryKey`, `useDocumentReadiness`, `readinessFor`,
  `useDocument`, `useDocumentRevisions`, `useGenerateDocument`, `useExportDocument`,
  `documentLogoUrl`.
- `ReadinessPill` is ready to be reused verbatim on release detail's Documents section (05-11) - no
  changes needed to the component itself.
- **Follow-up for whichever plan registers `/releases/$releaseId/documents/$documentType`:**
  `ReadinessPill`'s Stale/Up-to-date branches currently render a plain `<a href>` instead of a typed
  `<Link>`. Once that route is registered, converting those two branches to `<Link to="/releases/
  $releaseId/documents/$documentType" params={{ releaseId, documentType }}>` is a pure upgrade (same
  rendered `href`, same tests) - not required for correctness, but worth doing for client-side
  transitions instead of a full page reload.
- No blockers for 05-11 or 05-12.

---
*Phase: 05-document-generation*
*Completed: 2026-08-10*

## Self-Check: PASSED

All 12 created files verified present on disk; all 3 task commits (`32a481d`, `095d81a`, `af96bad`)
verified present in `git log`. Full backend suite (541 tests) and full frontend suite (154 tests /
18 files) both green.
