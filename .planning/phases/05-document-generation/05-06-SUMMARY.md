---
phase: 05-document-generation
plan: 06
subsystem: api
tags: [docx4j, flexmark, docx-export, ooxml, render-pipeline]

# Dependency graph
requires:
  - phase: 05-document-generation (plan 01)
    provides: MustacheFlexmarkRenderAdapter.parseMarkdown (shared flexmark AST hand-off, D-04-04),
      flexmark-ext-tables registered on the parser, openhtmltopdf/docx4j on the classpath under
      ArchUnit confinement, RenderService.resolveText for bounded chrome-field resolution
provides:
  - DocxExportPort - a one-method port (toDocx) mirroring RenderEnginePort's javadoc convention
  - DocxAstWalker - a hand-rolled flexmark Node -> docx4j P/R/Tbl/Tr/Tc walker over the project's
    fixed, closed node vocabulary; unknown node types degrade to literal text, never markup
  - DocxExportAdapter - package assembly: fresh WordprocessingMLPackage per call, a fixed-constant
    numbering part (numId 1=bullet, 2=decimal), optional cover page with content-sniffed logo, and
    per-section HeaderPart/FooterPart chrome with CTSimpleField PAGE/NUMPAGES field codes
affects: [05-16 (manual PDF/DOCX parity check reads this alongside 05-05-SUMMARY.md)]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Hand-walk a closed, fixed AST vocabulary directly into a target object model when a
    ready-made converter is version-incompatible (flexmark-docx-converter is pinned to
    docx4j-11.4.9; this project is on docx4j 17.0.2) - confine the walker to exactly what the
    upstream vocabulary can produce, not an open-ended superset", "Every docx4j Part added via
    addTargetPart needs its OWN explicit PartName when more than one instance of the same part type
    is added in one export call - the no-arg constructor's implicit default name collides silently
    and only fails at save() time", "An empty JaxbXmlPart (e.g. a FIRST-page header/footer with no
    real content) still needs its JAXB element forced into existence via getContent() before save,
    or docx4j treats it as an unchanged source part with nothing to copy from"]

key-files:
  created:
    - src/main/java/com/evision/releasemgmt/ports/DocxExportPort.java
    - src/main/java/com/evision/releasemgmt/adapters/render/DocxAstWalker.java
    - src/main/java/com/evision/releasemgmt/adapters/render/DocxExportAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/render/DocxExportAdapterTest.java
  modified: []

key-decisions:
  - "CTSimpleField worked exactly as RESEARCH.md's Don't-Hand-Roll section hoped - no fldChar
    begin/separate/end triplet fallback was needed. factory.createCTSimpleField() +
    field.setInstr(...) + factory.createPFldSimple(field) added directly to P.getContent()
    (not wrapped in a run) produced valid, round-trippable PAGE/NUMPAGES fields on the first attempt."
  - "Every docx4j API name flagged MEDIUM-confidence in the plan was corrected BEFORE coding, via
    javap against the real docx4j 17.0.2 jars, not discovered by compile failure: HeaderPart/
    FooterPart have no newHeaderPart() factory (use the plain no-arg constructor);
    SectPr.getEGHdrFtrReferences() returns List<CTRel>, and HeaderReference/FooterReference are
    added to it directly (both extend CTRel, both have a no-arg constructor plus inherited
    setId(String)); FieldsPPr.createFldSimple(...) does not exist (use
    factory.createCTSimpleField()/createPFldSimple(...) instead)."
  - "A2 vocabulary check (mandatory, RESEARCH.md assumption A2): read the actual merged bodies of
    starter-release-notes.md, starter-runbook.md, starter-approval.md and
    docs/templates/orchstro-release-notes.md before finalising DocxAstWalker's branch list. Result:
    they use only headings (levels 1-3), paragraphs, bullet lists, one ordered list
    (starter-runbook.md's numbered deploy/validation steps), bold, italic, one inline-code span
    (starter-runbook.md's `{{id}}` commit hash), and one pipe table (the approval doc's Field/Value
    table and orchstro's Updated Services table). No blockquotes, no fenced code blocks, no nested
    lists, no images, no thematic breaks, no strikethrough. RESEARCH.md's vocabulary table is
    sufficient as written; ThematicBreak/BlockQuote branches remain defensive-only, exercised only
    by direct DocxAstWalker construction in tests, not by any real template body."
  - "A part-name collision (not flagged by the plan or RESEARCH.md) surfaced only at mvn test time,
    not at compile time: a second HeaderPart/FooterPart (the FIRST-page ones, added only when
    coverEnabled) silently reused the same docx4j-assigned default part name as the DEFAULT one,
    overwriting it in the package's part map and failing at pkg.save() with 'part store has
    changed, and sourcePartStore not set'. Fixed by giving every HeaderPart/FooterPart an explicit
    PartName (/word/header1.xml, header2.xml, footer1.xml, footer2.xml). A related second failure
    (same error, same tests) came from the empty FIRST-page parts never having their JAXB element
    initialised - fixed by adding an explicit empty paragraph to each, which is also schema-correct
    for CT_HdrFtr (at least one block-level child)."

patterns-established:
  - "docx4j API surface should be verified against the real jars with javap before writing code
    against a MEDIUM-confidence research finding, not after a compile or runtime failure - this
    plan's every docx4j-specific method name matched the pre-verified research exactly, and zero
    NoSuchMethodError-class failures occurred; the two failures that did occur (part-name collision,
    uninitialised empty part) were both save()-time framework behaviours no amount of javap alone
    would have surfaced, and both are now documented above for the next docx4j consumer."

requirements-completed: [DOC-06, DOC-08]

# Metrics
duration: ~20min (task commits only; javap-based API verification preceded implementation and is
  not included in this figure)
completed: 2026-08-10
---

# Phase 5 Plan 06: DOCX Export Summary

**Hand-rolled flexmark-AST-to-OOXML walker feeding docx4j 17.0.2, producing a `.docx` with cover
page, running header, footer and PAGE/NUMPAGES field codes from the same shared AST the PDF path
walks - closing DOC-06 and the DOCX half of DOC-08.**

## Performance

- **Duration:** ~20 min (3 task commits, 20:22-20:41 local time)
- **Tasks:** 3/3 complete
- **Files modified:** 4 (3 created, 1 of the 3 additionally patched by a same-session bugfix)

## Accomplishments
- `DocxAstWalker` maps the project's entire closed, fixed Markdown vocabulary (headings, paragraphs,
  bold/italic/inline-code, bullet/ordered lists with correct `numId`/`ilvl`, fenced/indented code
  blocks, tables, links-as-text-only, soft/hard line breaks) onto real docx4j OOXML objects, with a
  mandatory literal-text fallback for anything outside that vocabulary - proven by
  `unknownNodeDegradesToLiteralText` and `rawHtmlIsLiteralTextNotMarkup`.
- `DocxExportAdapter` assembles a fresh `WordprocessingMLPackage` per call: a fixed-constant
  numbering part matching the walker's `numId` 1/2, an optional cover page (centred title/subtitle,
  content-sniffed logo sized from stored metadata, page break), and per-section chrome (`titlePg`,
  FIRST/DEFAULT header and footer parts, `CTSimpleField` PAGE/NUMPAGES fields) - all reachable only
  when `ChromeFields` enables them.
- Every docx4j 17.0.2 API name RESEARCH.md flagged MEDIUM-confidence was verified against the real
  jars via `javap` before coding (see key-decisions) - zero `NoSuchMethodError`-class failures.
- 14 tests reopen the produced bytes with `WordprocessingMLPackage.load(...)` and assert on the
  object model, never on the writer's own intent.
- Full backend suite: 455 tests green (441 baseline + 14 new), no regressions.
  `ArchitectureBoundaryTest` confirms docx4j still appears in no package outside `adapters.render`.
  `mvn dependency:tree -Dincludes=org.docx4j` still resolves exactly one version.

## Task Commits

Each task was committed atomically:

1. **Task 1: DocxExportPort and the AST-to-OOXML body walker** - `f6ec1ef` (feat)
2. **Task 2: Package assembly - sections, header/footer parts, field codes, cover, logo** - `ed53e56` (feat)
3. **Task 3: Reopen the produced .docx and assert on its OOXML** - `44a3a40` (test)
   - Includes the part-name-collision and empty-part bugfix (see Deviations), found and fixed while
     getting Task 3's tests green.

**Plan metadata:** (this commit, docs: complete plan)

## Files Created/Modified
- `src/main/java/com/evision/releasemgmt/ports/DocxExportPort.java` - one-method port, mirrors
  `RenderEnginePort`'s javadoc convention
- `src/main/java/com/evision/releasemgmt/adapters/render/DocxAstWalker.java` - flexmark `Document`
  -> docx4j `P`/`R`/`Tbl` walker; plain class, not a `@Component`, constructed per export call
- `src/main/java/com/evision/releasemgmt/adapters/render/DocxExportAdapter.java` - `@Component`
  implementing `DocxExportPort`; package assembly, numbering, cover, chrome
- `src/test/java/com/evision/releasemgmt/adapters/render/DocxExportAdapterTest.java` - 14 tests,
  every one reopening the produced bytes with docx4j before asserting

## Decisions Made
See `key-decisions` in frontmatter for the full detail. Summary:
- `CTSimpleField` (not the raw `fldChar` triplet) worked for PAGE/NUMPAGES on the first attempt.
- Every MEDIUM-confidence docx4j API name in the plan was corrected before coding via `javap`
  against the real 17.0.2 jars.
- The A2 vocabulary check against the real starter/orchstro template bodies confirms RESEARCH.md's
  vocabulary table is sufficient; no additional node-type branch was needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed a header/footer part-name collision discovered by Task 3's tests**
- **Found during:** Task 3 (writing `headerAndFooterPartsExistWithFirstAndDefaultRefs`,
  `coverPageIsPresentAndTitlePgIsSet`, `logoBecomesAnImagePartWhenPresent` - all three exercise
  `coverEnabled=true`, which is the only path that adds a second `HeaderPart`/`FooterPart`)
- **Issue:** `new HeaderPart()` / `new FooterPart()` (no-arg constructor) leaves docx4j to assign
  one fixed default part name per content type. Adding a second instance of the same part type
  (the FIRST-page header/footer, added only when `coverEnabled`) silently overwrote the first
  (DEFAULT) part's entry in the package's part map. The failure surfaced only at `pkg.save()`, as
  `Docx4JException: part store has changed, and sourcePartStore not set` - not at compile time, and
  not on the simpler `coverEnabled=false` tests.
- **Fix:** Gave every `HeaderPart`/`FooterPart` an explicit, unique `PartName`
  (`/word/header1.xml`, `/word/header2.xml`, `/word/footer1.xml`, `/word/footer2.xml`). Separately,
  the empty FIRST-page parts (no real content) never lazily initialised their JAXB element the way
  the DEFAULT parts did via `getContent().add(...)` with real content, so they hit the same save()
  error for a second reason; fixed by adding an explicit empty paragraph to each, which is also the
  schema-correct minimum for `CT_HdrFtr`.
- **Files modified:** `src/main/java/com/evision/releasemgmt/adapters/render/DocxExportAdapter.java`
- **Verification:** All 14 `DocxExportAdapterTest` tests green; full suite (455 tests) green.
- **Committed in:** `44a3a40` (Task 3 commit - fix and tests landed together since the tests are
  what surfaced the bug)

**2. [Rule 1 - Bug] Removed a comment phrase that tripped its own no-hand-built-XML grep criterion**
- **Found during:** Task 1, immediately after writing `DocxAstWalker.java`
- **Issue:** A code comment explaining the `Link` node's text-only handling used the literal word
  "Hyperlink", which the acceptance criterion's own grep (`Hyperlink`) is designed to catch as a
  signal that a real `Hyperlink` OOXML element was created. The comment was prose, not code, but
  the grep is blunt by design.
- **Fix:** Reworded the comment to describe the same guarantee without the flagged word.
- **Files modified:** `src/main/java/com/evision/releasemgmt/adapters/render/DocxAstWalker.java`
- **Verification:** `grep -cE '"<w:|createHyperlink|Hyperlink' DocxAstWalker.java` returns `0`.
- **Committed in:** `f6ec1ef` (Task 1 commit - fixed before the commit, not a follow-up)

---

**Total deviations:** 2 auto-fixed (2 bugs, both Rule 1). No architectural changes, no scope
creep - both fixes are corrections to this plan's own new code, required for the plan's own
acceptance criteria and verification steps to pass.

## Issues Encountered
None beyond the two deviations above, both resolved within the same execution.

## Test Results

- `mvn test -Dtest=DocxExportAdapterTest`: **14 tests, 0 failures, 0 errors.**
- `mvn test -Dtest=ArchitectureBoundaryTest`: green - docx4j confined to `adapters.render`
  (`grep -rn 'org.docx4j' src/main/java --include=*.java | grep -vc 'adapters/render/'` returns `0`).
- `mvn dependency:tree -Dincludes=org.docx4j`: exactly one `docx4j-JAXB-ReferenceImpl:17.0.2`
  resolves (with its `docx4j-core`/`docx4j-generated-objects`/`docx4j-copy` siblings, all `17.0.2`).
- `mvn test` (full suite): **455 tests, 0 failures, 0 errors** (441 baseline + 14 new).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `DocxExportPort`/`DocxExportAdapter` are ready for plan 05-08 to wire behind the export
  controller/service and convert `UncheckedIOException` to the `EXPORT_FAILED` reason code.
- Plan 05-16's manual PDF/DOCX parity check should read this summary alongside `05-05-SUMMARY.md`
  (the PDF export plan) - both consume the same shared flexmark AST (D-04-04) but apply chrome via
  two genuinely independent mechanisms (CSS `@page`/`counter()` vs. `titlePg`/`HdrFtrRef`/
  `CTSimpleField`), which is D-05-07's accepted, documented parity gap.
- No blockers. docx4j 17.0.2's full API surface needed by this plan (walker vocabulary, numbering,
  header/footer parts, field codes, image embedding) is now verified and exercised by a green test
  suite; a future plan touching docx4j further can build on this rather than re-verifying from
  scratch.

---
*Phase: 5-Document Generation*
*Completed: 2026-08-10*

## Self-Check: PASSED

All claimed files exist on disk (DocxExportPort.java, DocxAstWalker.java, DocxExportAdapter.java,
DocxExportAdapterTest.java, this SUMMARY.md). All claimed commit hashes (f6ec1ef, ed53e56, 44a3a40)
are present in `git log`.
