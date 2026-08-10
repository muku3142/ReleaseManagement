---
phase: 05-document-generation
plan: 08
subsystem: api
tags: [spring-boot, content-disposition, header-injection, pdf-export, docx-export, problem-detail]

# Dependency graph
requires:
  - phase: 05-document-generation (plan 04)
    provides: DocumentController (adapters/web), GlobalExceptionHandler's problem(...) helper,
      DOCUMENT_NOT_FOUND/NO_ACTIVE_TEMPLATE reason codes, DocumentGenerationService's
      latest/revision passthroughs
  - phase: 05-document-generation (plan 05)
    provides: PdfExportPort/PdfExportAdapter - renders resolvedMarkdown/ChromeFields/LogoImage to
      PDF bytes
  - phase: 05-document-generation (plan 06)
    provides: DocxExportPort/DocxExportAdapter - renders the same shape to DOCX bytes
provides:
  - "GET /api/releases/{releaseId}/documents/{documentType}/export.pdf?revision=N and
    .../export.docx?revision=N - DOC-05/DOC-06 made reachable over HTTP for any stored revision"
  - "ExportFilename - the codebase's first Content-Disposition filename builder, an allowlist
    sanitiser plus a single attachment(filename) header-value constructor"
  - "DocumentExportService - snapshot -> bytes via PdfExportPort/DocxExportPort with provably no
    RenderService dependency"
  - "ExportFailedException -> 502 EXPORT_FAILED ProblemDetail reason code"
affects: [05-10-frontend-export-download (binds to these two URLs and the exact filename
  format verbatim), 05-09 (parallel plan also appending to docs/status-responses.md)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Allowlist (not denylist) character filtering for any free text that reaches an HTTP header
      value - replace everything outside [A-Za-z0-9 ._-] with '_', not just the characters known
      to be dangerous today"
    - "A dedicated attachment(filename) helper that builds the whole Content-Disposition value, so
      no controller ever concatenates 'attachment; filename=\"...\"' by hand a second time"
    - "Export services take (releaseId, documentType, revisionOrNull) and resolve the snapshot
      internally, matching DocumentGenerationService's existing latest/revision split rather than
      accepting a pre-resolved snapshot"

key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/web/ExportFilename.java
    - src/main/java/com/evision/releasemgmt/domain/document/ExportFailedException.java
    - src/main/java/com/evision/releasemgmt/application/DocumentExportService.java
    - src/test/java/com/evision/releasemgmt/adapters/web/ExportFilenameTest.java
    - src/test/java/com/evision/releasemgmt/application/DocumentExportServiceTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/DocumentExportControllerTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/web/DocumentController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
    - src/test/java/com/evision/releasemgmt/adapters/web/DocumentControllerTest.java
    - docs/status-responses.md

key-decisions:
  - "Controller resolves the effective revision via the existing DocumentGenerationService.latest/
    revision (same pattern the GET endpoint already uses) purely to learn the concrete revision
    number for the filename, then calls DocumentExportService separately for the bytes - two
    lookups of the same snapshot row, not one, accepted as the same class of cheap extra indexed
    lookup DocumentResponse's nextRevision/hasLogo/templateStillExists passthroughs already are"
  - "EXPORT_FAILED appended to docs/status-responses.md as the LAST row of the reason-code table
    (not grouped with the other 502s) specifically to keep the merge with plan 05-09's parallel
    edit to the same file clean, per the plan's own instruction"
  - "No frontend REASON_MESSAGES entry added for EXPORT_FAILED in this plan - out of this plan's
    declared file scope (backend-only); the reason degrades safely to the generic fallback per
    docs/status-responses.md section 9's documented behaviour until 05-10's frontend work adds it"

patterns-established:
  - "ExportFilename is now the one place in the codebase that builds a Content-Disposition
    filename from free text - any future export/download feature with a user-influenced filename
    should extend or reuse it, not hand-roll a second sanitiser"

requirements-completed: [DOC-05, DOC-06]

# Metrics
duration: ~75min
completed: 2026-08-10
---

# Phase 5 Plan 08: PDF/DOCX Download Endpoints Summary

**Two new DocumentController endpoints (export.pdf/export.docx) call the existing PdfExportPort/DocxExportPort against stored, unmodified snapshot content, and every filename they produce passes through a new allowlist-based Content-Disposition sanitiser proven safe against a full 0x00-0x7F character sweep and a hostile end-to-end request.**

## Performance

- **Duration:** ~75 min
- **Tasks:** 3/3 complete
- **Files modified:** 6 created, 4 modified

## Accomplishments
- `ExportFilename.sanitize`/`attachment` closes the codebase's first-ever free-text-into-HTTP-header
  exposure: every character outside `[A-Za-z0-9 ._-]` is replaced, runs are collapsed, edges are
  stripped, an emptied candidate falls back to `document`, and the whole thing is capped at 120
  characters including the extension - proven by a full `0x00`-`0x7F` allowlist sweep
  (`everyCharacterOutsideTheAllowlistIsReplaced`) and a hostile-name corpus asserted directly
  against the produced header value (`noOutputContainsAHeaderTerminator`).
- `DocumentExportService.toPdf`/`toDocx` resolve a stored `DocumentSnapshot` (latest or a specific
  revision) and pass its markdown/chrome/logo straight to `PdfExportPort`/`DocxExportPort` with no
  re-resolution step and no `RenderService` dependency at all - enforced by a grep criterion and
  proven behaviourally by `pdfUsesTheStoredMarkdownAndStoredChrome`/`docxUsesTheStoredMarkdownAndStoredChrome`,
  which capture the exact port arguments.
- `supersededRevisionIsExportable` makes D-05-06's "every revision, not just the latest, stays
  exportable" guarantee executable: requesting revision 1 when revision 3 exists exports revision
  1's stored content untouched.
- `DocumentController` gained `GET .../export.pdf` and `GET .../export.docx`, both returning
  `ResponseEntity<byte[]>` with `attachment` disposition, explicit `Content-Type`,
  `X-Content-Type-Options: nosniff` and `Cache-Control: no-store` - and the filename always carries
  the revision actually exported, even when the request omitted `?revision=`.
- A renderer failure (either port throwing) becomes a single `ExportFailedException` mapped to
  `502 EXPORT_FAILED`, following the existing `JIRA_ERROR` precedent for "a downstream component we
  called failed" - one reason code for both formats, per the plan's explicit instruction not to
  split it into `PDF_EXPORT_FAILED`/`DOCX_EXPORT_FAILED`.
- `hostileReleaseNameCannotEscapeTheContentDispositionHeader` is the end-to-end proof: a release
  named `A"B\r\nX-Injected: 1/../..` still produces a `Content-Disposition` matching
  `^attachment; filename="[A-Za-z0-9 ._-]+\.pdf"$` with no injected header present in the response.
- Full backend suite: **519 tests**, all green (493 baseline + 26 new: 11 `ExportFilenameTest` +
  8 `DocumentExportServiceTest` + 7 `DocumentExportControllerTest`).

## Task Commits

Each task was committed atomically, following this repo's established test-then-feat convention:

1. **Task 1: ExportFilename - the allowlist sanitiser and its regression tests**
   - `02fa999` (test) - failing tests for every `<behavior>` bullet plus the full-sweep and hostile-corpus tests
   - `b86bc6d` (feat) - `ExportFilename` implementation; also corrects one of the plan's own illustrative test cases (see Deviations)
2. **Task 2: DocumentExportService and the two export endpoints** - `f099834` (feat)
3. **Task 3: Export service and endpoint tests, including the security cases** - `0c6fe8c` (test)

_No plan-metadata commit - orchestrator owns STATE.md/ROADMAP.md writes after all wave agents complete._

## Files Created/Modified
- `src/main/java/com/evision/releasemgmt/adapters/web/ExportFilename.java` - `sanitize`, `attachment`
- `src/main/java/com/evision/releasemgmt/domain/document/ExportFailedException.java` - marker exception carrying the renderer's cause
- `src/main/java/com/evision/releasemgmt/application/DocumentExportService.java` - `toPdf`, `toDocx`
- `src/main/java/com/evision/releasemgmt/adapters/web/DocumentController.java` - `exportPdf`, `exportDocx`, `effectiveRevision`, `exportFilename` helpers
- `src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java` - `EXPORT_FAILED` (502)
- `docs/status-responses.md` - `EXPORT_FAILED` appended as the last reason-code row, count bumped 27 -> 28
- `src/test/java/com/evision/releasemgmt/adapters/web/ExportFilenameTest.java` - 11 tests
- `src/test/java/com/evision/releasemgmt/application/DocumentExportServiceTest.java` - 8 tests
- `src/test/java/com/evision/releasemgmt/adapters/web/DocumentExportControllerTest.java` - 7 tests
- `src/test/java/com/evision/releasemgmt/adapters/web/DocumentControllerTest.java` - added `@MockitoBean` for the two new `DocumentController` constructor dependencies so its existing `@WebMvcTest` context still loads

## Decisions Made
- The controller resolves the snapshot twice per export request - once via the existing
  `DocumentGenerationService.latest`/`revision` (to learn the effective revision number for the
  filename) and once inside `DocumentExportService` (to get the bytes). This mirrors the existing
  `nextRevision`/`hasLogo`/`templateStillExists` pattern of accepting a cheap extra indexed lookup
  rather than reshaping `DocumentExportService`'s plan-specified signature to also return the
  resolved snapshot.
- `EXPORT_FAILED` was appended as the literal last row of `docs/status-responses.md`'s reason table
  (after the `handleExceptionInternal` catch-all row), not grouped with the other `502`s, because
  plan 05-09 edits the same file in a parallel worktree and the plan explicitly asked for a
  minimal, merge-safe append.
- No frontend `REASON_MESSAGES` entry was added for `EXPORT_FAILED`. This plan's declared file list
  is backend-only; `docs/status-responses.md` itself documents that an unmapped reason degrades
  safely to a generic fallback message rather than a crash, so this is a deliberate, safe deferral
  rather than a silent gap - the entry belongs with 05-10's frontend export/download UI.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected the plan's own "sanitises to nothing" example, which cannot occur as literally described**
- **Found during:** Task 1, first green-phase test run
- **Issue:** The plan's `<behavior>` bullet illustrates the empty-candidate fallback with
  `sanitize("\"\"\"", "\"\"\"", RELEASE_NOTES, 1, "pdf")` expecting `"document.pdf"`. But the
  algorithm's own step 1 always appends the document-type label and revision suffix (e.g.
  `"Release Notes r1"`) to the raw candidate *before* allowlist filtering runs on the whole string.
  Since that suffix is always non-empty and already allowlist-safe, the composed candidate can
  never be fully emptied by a hostile name/version alone - the actual result for that input is
  `"Release Notes r1.pdf"`, not `"document.pdf"`. This is a genuine inconsistency in the plan's
  illustrative example, not a flaw in the implemented algorithm (which matches the plan's action
  steps exactly).
- **Fix:** Renamed the test to assert the real, correct behaviour (`aNameAndVersionThatSanitiseToNothingStillProduceANonEmptySafeFilename`,
  expecting `"Release Notes r1.pdf"`), and added a second test
  (`aCandidateEmptiedByTruncationFallsBackToDocument`) that genuinely reaches the `document`
  fallback branch via the one path that can actually empty the candidate: truncation (step 5) to
  zero characters when the extension itself is long enough to consume the entire 120-character
  budget. The fallback implementation itself (step 4's literal empty-check, plus a defensive
  re-check after truncation) was kept exactly as specified - it is correct, just unreachable via a
  hostile name/version alone given the fixed, always-present label+revision suffix.
- **Files modified:** `src/test/java/com/evision/releasemgmt/adapters/web/ExportFilenameTest.java`
- **Verification:** All 11 `ExportFilenameTest` tests green.
- **Committed in:** `b86bc6d` (Task 1 GREEN commit)

**2. [Rule 1 - Bug] Fixed two self-inflicted grep failures from my own comment wording**
- **Found during:** Task 1 (ExportFilename.java) and Task 2 (DocumentExportService.java), immediately after writing each file
- **Issue:** A javadoc comment in `ExportFilename.java` literally contained the substring
  `filename*=` (explaining why no RFC 5987 extended-notation parameter is emitted), which is
  exactly what the acceptance criterion's negative grep (`filename\*=` must return `0`) is designed
  to catch. Separately, the mandated design comment in `DocumentExportService.java` literally said
  `RenderService` twice (explaining the *absence* of that dependency), tripping the acceptance
  criterion that greps for `RenderService` expecting `0`. Both are the same class of issue plan
  05-04 documented: prose explaining what code deliberately does *not* do can accidentally contain
  the exact string a grep-based negative check is watching for.
- **Fix:** Reworded both comments to convey the identical meaning without the flagged substring
  (`ExportFilename`: "RFC 5987/6266 extended-notation (\"ext-value\") header parameter";
  `DocumentExportService`: "the bounded render/resolution pipeline"). Re-ran both greps to confirm
  `0` before committing either file.
- **Files modified:** `src/main/java/com/evision/releasemgmt/adapters/web/ExportFilename.java`,
  `src/main/java/com/evision/releasemgmt/application/DocumentExportService.java`
- **Verification:** `grep -c 'filename\*=' ExportFilename.java` -> `0`;
  `grep -c 'RenderService' DocumentExportService.java` -> `0`; both files' full test runs stayed green.
- **Committed in:** `b86bc6d` (Task 1) and `f099834` (Task 2) - fixed before either commit, not a follow-up

**3. [Rule 1 - Bug/Blocking] Extended the existing `DocumentControllerTest` with mocks for the controller's two new constructor dependencies**
- **Found during:** Task 2, first test run of the pre-existing `DocumentControllerTest`
- **Issue:** Adding `DocumentExportService` and `ReleaseRepository` to `DocumentController`'s
  constructor (required by the plan's own Task 2 action) broke the pre-existing
  `DocumentControllerTest`'s `@WebMvcTest` context, which only mocked `DocumentGenerationService` -
  the context failed to load with an unsatisfied-dependency error, and every test in that class
  failed as a result.
- **Fix:** Added `@MockitoBean` fields for `DocumentExportService` and `ReleaseRepository` to
  `DocumentControllerTest`, matching the existing pattern for `DocumentGenerationService`. No test
  logic in that file needed to change - only its bean wiring.
- **Files modified:** `src/test/java/com/evision/releasemgmt/adapters/web/DocumentControllerTest.java`
- **Verification:** `mvn test -Dtest=DocumentControllerTest` - all 8 pre-existing tests green again.
- **Committed in:** `f099834` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (all Rule 1 - bugs found and fixed before their respective
commits, no architectural changes, no scope creep beyond files the plan already listed or a
directly-caused test-wiring break).

## Issues Encountered
None beyond the three deviations above, all resolved within the same execution before their
respective task commits.

## Test Results

- `mvn test -Dtest=ExportFilenameTest`: **11 tests, 0 failures, 0 errors.**
- `mvn test -Dtest=DocumentExportServiceTest`: **8 tests, 0 failures, 0 errors.**
- `mvn test -Dtest=DocumentExportControllerTest`: **7 tests, 0 failures, 0 errors.**
- `mvn test -Dtest=DocumentControllerTest`: **8 tests, 0 failures, 0 errors** (pre-existing, re-verified after the constructor change).
- `mvn test -Dtest=ArchitectureBoundaryTest`: green.
- `mvn test` (full suite): **519 tests, 0 failures, 0 errors** (493 baseline + 26 new).
- All grep-based acceptance criteria from the plan re-verified independently after the comment
  fixes: `sanitize`/`attachment` each defined exactly once, `filename\*=` absent, `RenderService`
  absent from `DocumentExportService.java`, `snapshot.markdown()` appears exactly twice,
  `EXPORT_FAILED` appears exactly once in `GlobalExceptionHandler.java`,
  `PDF_EXPORT_FAILED`/`DOCX_EXPORT_FAILED` absent, `ExportFilename.attachment` appears exactly
  twice in `DocumentController.java`, `filename=\"` appears exactly once (the pre-existing logo
  endpoint), `StreamingResponseBody` absent, `wordprocessingml.document` appears exactly once.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The exact export URLs and filename format plan 05-10's frontend download helper needs to bind to:
  - `GET /api/releases/{releaseId}/documents/{documentType}/export.pdf?revision=N`
  - `GET /api/releases/{releaseId}/documents/{documentType}/export.docx?revision=N`
  - `revision` omitted means the latest revision is exported.
  - Filename format: `"{releaseName} {version} {DocumentTypeLabel} r{revision}.{ext}"` with every
    character outside `[A-Za-z0-9 ._-]` replaced by `_`, e.g. `Orchstro 4.2.0 Release Notes r2.pdf`.
    `DocumentTypeLabel` is exactly `Release Notes` / `Runbook` / `Approval`.
  - Both responses set `Content-Disposition: attachment; filename="..."`, so a plain `<a href>` or
    `window.location` navigation triggers a browser file-save dialog without any client-side blob
    handling needed.
- `docs/status-responses.md` now documents `EXPORT_FAILED` (502) as the 28th explicit reason code.
  05-10's frontend work should add the corresponding `REASON_MESSAGES` entry in
  `frontend/src/api/auth.ts` when it builds the export/download UI - not done here, as it is
  outside this plan's backend-only file scope.
- **DOC-05 and DOC-06 are now fully satisfied end-to-end** - both formats are reachable over HTTP
  for any stored revision, latest or superseded, with no re-resolution and a closed header-injection
  surface.
- No blockers for 05-10 or for plan 05-16's manual PDF/DOCX visual parity check - this plan changed
  no rendering logic in `PdfExportAdapter`/`DocxExportAdapter`, only how their output reaches the
  browser.

---
*Phase: 05-document-generation*
*Completed: 2026-08-10*

## Self-Check: PASSED

All 11 created/modified files verified present on disk (ExportFilename.java,
ExportFailedException.java, DocumentExportService.java, ExportFilenameTest.java,
DocumentExportServiceTest.java, DocumentExportControllerTest.java, DocumentController.java,
GlobalExceptionHandler.java, DocumentControllerTest.java, docs/status-responses.md, this
SUMMARY.md). All 4 task commits (`02fa999`, `b86bc6d`, `f099834`, `0c6fe8c`) verified present in
`git log`. Full backend suite (519 tests) green.
