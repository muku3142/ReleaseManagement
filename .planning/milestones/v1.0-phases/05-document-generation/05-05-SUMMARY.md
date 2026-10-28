---
phase: 05-document-generation
plan: 05
subsystem: api
tags: [openhtmltopdf, pdfbox, flexmark, pdf-export, chrome, docx4j]

# Dependency graph
requires:
  - phase: 05-document-generation (plan 01, wave 1)
    provides: MustacheFlexmarkRenderAdapter.parseMarkdown/renderHtml (the shared flexmark AST
      hand-off, D-04-04), flexmark-ext-tables registration, openhtmltopdf/docx4j already on the
      classpath and ArchUnit-confined to adapters.render
provides:
  - PdfExportPort - single-method port taking already-resolved Markdown, ChromeFields and an
    optional LogoImage, returning PDF bytes
  - PdfExportAdapter - full XHTML chrome shell (cover page, running header via CSS
    position:running(), @page/@page:first margin boxes, HTML/CSS-escaped chrome text, data-URI
    logo) rendered through openhtmltopdf's PdfRendererBuilder against the shared flexmark AST's
    HTML output
  - A pom.xml fix pinning org.apache.pdfbox:fontbox to 2.0.24, without which every PDF render in
    this codebase fails at runtime (see Deviations) - this is load-bearing for any future
    openhtmltopdf consumer, not specific to this plan's files
affects: [05-06-plan (DOCX export - shares the same shared-AST/ChromeFields input shape, and may
  need to re-verify fontbox resolution once its own tests exercise docx4j at runtime),
  05-16-plan (manual PDF/DOCX visual parity check - needs to know the PDF chrome mechanism
  exactly as implemented here)]

# Tech tracking
tech-stack:
  added: []
  patterns: ["PdfExportAdapter constructor-injects MustacheFlexmarkRenderAdapter (same package)
    to reach its package-private parseMarkdown/renderHtml pair - the only way to consume the
    shared AST without a new abstraction layer", "Chrome text resolved via two different escaping
    functions depending on destination: escapeHtml for text placed in the XHTML body, escapeCss
    for text placed inside a CSS string literal (the footer) - never the same function for both"]

key-files:
  created:
    - src/main/java/com/evision/releasemgmt/ports/PdfExportPort.java
    - src/main/java/com/evision/releasemgmt/adapters/render/PdfExportAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/render/PdfExportAdapterTest.java
  modified:
    - pom.xml

key-decisions:
  - "Pinned org.apache.pdfbox:fontbox to 2.0.24 as a direct top-level dependency - docx4j 17.0.2's
    own fontbox:3.0.8 declaration was winning Maven's nearest-wins mediation over
    openhtmltopdf-pdfbox's transitive pdfbox:2.0.24 -> fontbox:2.0.24, and pdfbox 2.0.24's font
    classes call a fontbox 2.x-only method fontbox 3.0.8 no longer has. Every PDF render failed
    before this fix, regardless of custom fonts (there are none - RESEARCH.md Pitfall 3 remains
    correctly out of scope)."
  - "No CSS construct from the plan's snippet needed changing against the actual openhtmltopdf
    1.0.10 jar - @page/@page:first margin boxes, position: running(), and the counter(page)/
    counter(pages) footer all worked exactly as specified, verified by extracted-PDF-text
    assertions, not just compiling CSS."

patterns-established:
  - "Any future openhtmltopdf consumer inherits the fontbox 2.0.24 pin already in pom.xml - do not
    remove it while docx4j-JAXB-ReferenceImpl 17.0.2 (or any release still declaring fontbox 3.x)
    remains a dependency."

requirements-completed: [DOC-05]

# Metrics
duration: ~20min (across two sessions - an API session-limit interruption occurred after Task 1's
  files were written but before they were committed; work resumed from verified on-disk state)
completed: 2026-08-10
---

# Phase 5 Plan 05: PDF Export Adapter Summary

**openhtmltopdf-based PDF export with a cover page, CSS position:running() header, @page/counter() footer numbering, and HTML/CSS-escaped chrome text, rendered from the same flexmark AST the authoring preview uses - plus a load-bearing fontbox version pin that was silently broken before this plan exercised the renderer.**

## Performance

- **Duration:** ~20 min of active work, spread across two sessions due to an API session-limit
  interruption between writing Task 1's files and committing them
- **Started:** 2026-08-10 (session 1)
- **Completed:** 2026-08-10T20:15:52+03:00 (Task 2 commit, session 2)
- **Tasks:** 2/2 complete
- **Files modified:** 4 (3 created, 1 modified)

## Accomplishments
- `PdfExportPort`/`PdfExportAdapter` render an already-resolved document snapshot's Markdown and
  chrome to PDF bytes through the exact same flexmark `Document` AST and `HtmlRenderer` the
  authoring preview and save-time validation use (D-04-04) - no second parser/renderer
  configuration exists anywhere in the adapter.
- Full chrome support: a cover page that suppresses its own header/footer via `@page :first`,
  a running header using CSS `position: running()`, and a footer that renders real
  `counter(page)`/`counter(pages)` values in all four footer-text/page-numbers combinations.
- Two independent, destination-specific escaping paths: chrome text placed in the XHTML body is
  HTML-escaped; chrome text placed inside a CSS string literal (the footer) is CSS-escaped
  (backslash/quote escaped, CR/LF/`}`/`<` stripped) - both proven against extracted PDF text, not
  the generated HTML/CSS source.
- Logo embedding as a `data:` URI typed from stored `LogoMetadata.contentType()` (never the
  filename), verified by counting image XObjects in the produced PDF.
- Discovered and fixed a real, previously-latent dependency conflict (fontbox 2.0.24 vs 3.0.8)
  that would have broken every PDF export in the application, not just this plan's tests.
- 11/11 new tests green; full backend suite 452/452 green (441 baseline + 11 new), no regressions.

## Task Commits

Each task was committed atomically:

1. **Task 1: PdfExportPort and the XHTML chrome shell** - `0821890` (feat)
2. **Task 2: Assert on the produced PDF, not just that bytes came back** - `d0cf780` (test) -
   includes the pom.xml fontbox pin required for these tests (and any PDF render) to run at all

**Plan metadata:** (this commit, docs: complete plan)

## Files Created/Modified
- `src/main/java/com/evision/releasemgmt/ports/PdfExportPort.java` - single-method port,
  `toPdf(resolvedMarkdown, resolvedChrome, logo)`, `@throws UncheckedIOException` until plan 05-08
  introduces `ExportFailedException`
- `src/main/java/com/evision/releasemgmt/adapters/render/PdfExportAdapter.java` - builds the XHTML
  chrome shell (cover/running-header/body divs, `@page` CSS with escaped chrome text) and renders
  it with a fresh `PdfRendererBuilder` per call, `withHtmlContent(xhtml, null)` (no base URI)
- `src/test/java/com/evision/releasemgmt/adapters/render/PdfExportAdapterTest.java` - 11 tests,
  each opening the produced PDF with PDFBox (`PDDocument.load(byte[])`, not `Loader.loadPDF` - see
  Deviations) and asserting on extracted text (`PDFTextStripper`), page count, or image XObjects
- `pom.xml` - added a direct `org.apache.pdfbox:fontbox:2.0.24` dependency (see Deviations)

## Decisions Made
- Reused the plan's exact CSS snippet verbatim (page size, margin boxes, cover/heading/table/code
  styling) - none of it needed adjustment against the real openhtmltopdf 1.0.10 jar. The
  MEDIUM-confidence risk RESEARCH.md flagged (issue #134, top-center/top-right margin-box
  positioning quirks) did not manifest for the `@top-center`/`@bottom-center` combination actually
  used here.
- Footer CSS content is built as a single concatenated CSS string (`"<escaped footer> \2022 Page "
  counter(page) " of " counter(pages)`) rather than as separate adjacent string literals, so the
  escaped footer text and the bullet separator share one CSS string token - confirmed correct by
  the `quoteInFooterTextDoesNotBreakTheStylesheet` test.
- Test PDF inspection uses PDFBox 2.x's `PDDocument.load(byte[])`/`PDFTextStripper`, not PDFBox
  3.x's `Loader.loadPDF` - see Deviations for why the plan's own read_first note pointed at the
  wrong API family.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Pinned `org.apache.pdfbox:fontbox` to 2.0.24 in `pom.xml`**
- **Found during:** Task 2, first test run (`mvn test -Dtest=PdfExportAdapterTest`)
- **Issue:** All 11 tests failed with `NoClassDefFoundError`/`NoSuchMethodError` on
  `org.apache.pdfbox.pdmodel.font.PDType1Font`'s static initializer, ultimately
  `NoSuchMethodError: TTFParser.parse(InputStream)`. `mvn dependency:tree -Dverbose` showed
  `org.apache.pdfbox:fontbox:jar:2.0.24` (declared transitively by `pdfbox:2.0.24`, which
  `openhtmltopdf-pdfbox:1.0.10` depends on) being **omitted for conflict with 3.0.8** - docx4j
  17.0.2 declares `fontbox:3.0.8` as its own direct (shallower) dependency, and Maven's
  nearest-wins mediation picked that version project-wide. PDFBox 2.0.24's font-mapper
  initialization calls a fontbox 2.x-only `TTFParser.parse(InputStream)` overload that fontbox
  3.0.8 no longer exposes with that signature, so **every single PDF render fails**, independent
  of any custom font usage.
- **Fix:** Added a direct top-level `<dependency>` on `org.apache.pdfbox:fontbox:2.0.24` in
  `pom.xml`. A directly-declared dependency always outranks a transitive one in Maven's mediation
  regardless of depth, so this restores 2.0.24 everywhere without touching docx4j's own declared
  version or excluding anything from docx4j's dependency graph.
- **Files modified:** `pom.xml`
- **Verification:** `mvn dependency:tree -Dverbose -Dincludes=org.apache.pdfbox:fontbox` now shows
  a single resolved version (2.0.24, with docx4j's 3.0.8 explicitly omitted for conflict). All 11
  `PdfExportAdapterTest` tests pass; full suite `mvn test` - 452 tests, 0 failures, 0 errors (441
  baseline + 11 new).
- **Committed in:** `d0cf780` (Task 2 commit)
- **Why this belongs to this plan despite touching a file outside the plan's declared
  `files_modified` list:** the conflict was latent since plan 05-01 added both libraries to
  `pom.xml` without ever invoking the PDF renderer; it only surfaces the moment any code actually
  calls `PdfRendererBuilder.run()`. This plan is the first to do that, so it is the first place the
  fix could be discovered and verified. Left unfixed, DOC-05 could not be delivered at all. Plan
  05-06 (DOCX export, running in parallel) is unaffected - docx4j's own save path does not appear
  to touch the fontbox classes that conflict, and the full suite stayed green with the pin in
  place.

### Process Note (not a Rule 1-4 deviation, documented for traceability)

**The plan's Task 2 `read_first` pointed at the wrong PDFBox API family.** The plan told the
executor to "confirm the exact `Loader` entry point for the PDFBox 3.x line rather than using the
removed `PDDocument.load`" - but `mvn dependency:tree -Dincludes=org.apache.pdfbox` shows the
version that actually resolves for `openhtmltopdf-pdfbox:1.0.10` is `pdfbox:2.0.24`, not a 3.x
release (docx4j pulls `fontbox`/`pdfbox-io:3.0.8` as separate, narrower artifacts, not the full
`pdfbox` 3.x core module). `org.apache.pdfbox.Loader` does not exist in the 2.0.24 jar at all
(confirmed via `jar tf`); `PDDocument.load(byte[])` is the correct, present API and is what the
test file uses throughout. No functional impact - just a corrected API choice, verified against
the actual resolved jar before writing any test code.

---

**Total deviations:** 1 auto-fixed (1 blocking), plus 1 process note (API-family correction, no
functional impact).
**Impact on plan:** The fontbox pin was strictly necessary - without it, DOC-05 cannot be
delivered at all, in this plan or by any future PDF-rendering code in this codebase. No scope
creep beyond the one dependency line; docx4j's own declared version is untouched.

## Issues Encountered
None beyond the deviation above.

## Test Results

- `mvn test -Dtest=PdfExportAdapterTest`: **11 tests, 0 failures, 0 errors.**
- `mvn test -Dtest=ArchitectureBoundaryTest`: green - `openhtmltopdf` confined to `adapters.render`
  (verified via `grep -rn 'com.openhtmltopdf' src/main/java | grep -v 'adapters/render/'` → 0
  matches, in addition to the ArchUnit test itself passing).
- `mvn test` (full suite): **452 tests, 0 failures, 0 errors** (441 baseline + 11 new - matches the
  plan's target exactly).
- All Task 1 and Task 2 grep-based acceptance criteria re-verified independently (counter(pages),
  position: running(runningHeader), size: A4, renderAdapter.parseMarkdown, absence of a second
  Parser/HtmlRenderer, absence of `metadata().filename()`, exactly one non-field
  `new PdfRendererBuilder()`, zero `com.openhtmltopdf` imports outside `adapters/render/`).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `PdfExportPort`/`PdfExportAdapter` are ready for a future `DocumentExportService`/
  `DocumentController` (not yet built - no plan in this wave adds them) to call with a stored
  `DocumentSnapshot`'s markdown/chrome/logo.
- **DOC-05 is fully satisfied by this plan.** DOC-08 (PDF and DOCX chrome parity) is only
  half-satisfied here - the PDF side is complete and tested, but DOC-08 as a whole needs plan
  05-06's DOCX chrome to land before it can be marked complete. This plan intentionally did **not**
  call `requirements mark-complete` for DOC-08 to avoid a parallel-worktree race with 05-06 marking
  the same requirement from a different, possibly-conflicting state; the orchestrator should mark
  DOC-08 complete only after confirming 05-06's DOCX chrome is also merged and green.
- **ROADMAP.md/STATE.md are intentionally untouched per this plan's execution constraints** - no
  edit is needed beyond the standard plan-progress bump the orchestrator applies after the wave.
- The fontbox 2.0.24 pin in `pom.xml` is now a durable constraint: if a future dependency bump
  changes docx4j's own fontbox version, re-run
  `mvn dependency:tree -Dverbose -Dincludes=org.apache.pdfbox:fontbox` before assuming PDF export
  still works.
- No blockers for plan 05-16's manual PDF/DOCX visual parity check - this summary documents the
  exact PDF chrome mechanism (CSS `@page`/`position: running()`/`counter()`) it needs to compare
  against docx4j's `titlePg`/`HdrFtrRef`/`CTSimpleField` mechanism.

---
*Phase: 05-document-generation*
*Completed: 2026-08-10*

## Self-Check: PASSED

All claimed files exist on disk (PdfExportPort.java, PdfExportAdapter.java,
PdfExportAdapterTest.java, this SUMMARY.md). Both claimed commit hashes (0821890, d0cf780) are
present in `git log`.
