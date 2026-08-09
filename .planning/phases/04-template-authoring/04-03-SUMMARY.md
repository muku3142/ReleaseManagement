---
phase: 04-template-authoring
plan: 03
subsystem: templates
tags: [java, jmustache, flexmark, render-pipeline, security-control, view-model]

# Dependency graph
requires:
  - phase: 04-template-authoring
    plan: 01
    provides: "ReleaseView/ItemView/GroupView view-model records, TemplateScanner, PlaceholderCatalog, TemplateMalformedException, RenderBoundExceededException"
  - phase: 03-release-assembly
    provides: "Release/ReleaseItem/ManualItem/ManualItemCategory aggregate and TicketDetail's exact field set"
provides:
  - "RenderEnginePort + MustacheFlexmarkRenderAdapter: the only place jmustache/flexmark are imported"
  - "RenderService: the single bounded render entry point (execution timeout + collection-size cap)"
  - "RenderContextMapper: ReleaseView -> Map/List/String/Boolean, no reflectable types"
  - "ReleaseViewAssembler: Release -> ReleaseView with deterministic issue-type/category grouping"
affects: [04-04, 04-05, phase-05-document-generation]

# Tech tracking
tech-stack:
  added:
    - "com.samskivert:jmustache:1.16 (adapters/render only)"
    - "com.vladsch.flexmark:flexmark:0.64.8 - core module only, not flexmark-all (adapters/render only)"
  patterns:
    - "Post-parse AST neutralisation: HtmlBlock/HtmlCommentBlock/HtmlInline/HtmlInlineComment nodes are replaced with Text nodes carrying the same raw characters, because flexmark 0.64.8's inline HTML parsing has no Parser DataKey to disable it at parse time"
    - "Virtual-thread-per-task executor + Future.get(timeout) as the wall-clock bound; a pre-flight collection-size walk as the throughput bound - two independent TMPL-05 controls in one RenderService"
    - "Unknown-placeholder rewrite as a single left-to-right character pass with a Deque<Boolean> tracking which open sections are unknown, mirroring TemplateScanner's own stack shape"

key-files:
  created:
    - src/main/java/com/evision/releasemgmt/ports/RenderEnginePort.java
    - src/main/java/com/evision/releasemgmt/adapters/render/MustacheFlexmarkRenderAdapter.java
    - src/main/java/com/evision/releasemgmt/application/RenderResult.java
    - src/main/java/com/evision/releasemgmt/application/RenderContextMapper.java
    - src/main/java/com/evision/releasemgmt/application/RenderService.java
    - src/main/java/com/evision/releasemgmt/application/ReleaseViewAssembler.java
    - src/test/java/com/evision/releasemgmt/adapters/render/MustacheFlexmarkRenderAdapterTest.java
    - src/test/java/com/evision/releasemgmt/application/RenderServiceTest.java
    - src/test/java/com/evision/releasemgmt/application/ReleaseViewAssemblerTest.java
  modified:
    - pom.xml
    - src/main/resources/application.yml
    - src/test/resources/application.yml
    - src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java

key-decisions:
  - "flexmark:0.64.8 core module only (not flexmark-all) - only CommonMark parse + HTML render are needed this phase; Phase 5 adds whatever extension modules PDF/DOCX actually require"
  - "Raw HTML is defended against by a post-parse AST walk, not a Parser configuration option - flexmark 0.64.8's InlineParserImpl.parseHtmlInline() is called unconditionally with no DataKey guard (verified against the library's own sources, not assumed), so config alone cannot make the AST-node assertion true. The walk converts HtmlBlockBase/HtmlInlineBase nodes to Text nodes, which are always rendered HTML-escaped, satisfying both the render-output test and the zero-HtmlBlock/HtmlInline AST test"
  - "jmustache's own HTML escaper stays off (escapeHTML(false)); emptyStringIsFalse(true) is the only other compiler flag set, verified against 1.16's own Mustache.java source rather than guessed - standardsMode/strictSections/defaultValue are left at their defaults on purpose"
  - "RenderService.markUnknownTokens tracks open-section state with a Deque<Boolean> rather than name-only matching, so a closing tag is blanked only when its own opening tag was unknown - avoids mis-blanking an unrelated valid section that happens to share a name with an unknown token elsewhere in the same template"

patterns-established:
  - "Pattern: when a library's documented Parser/Renderer options cannot prove a security property (verified by reading the library's own source, not just its javadoc), neutralise the AST directly after parsing rather than trusting a configuration flag - the neutralisation itself is the testable control"
  - "Pattern: RenderService is the only caller of RenderEnginePort and the only place that owns TMPL-05's two bounds; ArchitectureBoundaryTest enforces both that adapters.render is the only importer of the render libraries and that application cannot reach adapters.render directly"

requirements-completed: [TMPL-02, TMPL-04, TMPL-05]

# Metrics
duration: 55min
completed: 2026-08-09
---

# Phase 04 Plan 03: Render Pipeline Summary

**jmustache + flexmark render pipeline behind one port/adapter, a `RenderService` that owns both TMPL-05 bounds, and a `ReleaseViewAssembler` that turns a `Release` into the deterministic `ReleaseView` the whole pipeline renders — with raw HTML neutralised by walking the parsed Markdown AST rather than trusting a library flag, because flexmark 0.64.8 has no parser option that actually stops inline HTML.**

## Performance

- **Duration:** ~55 min
- **Started:** 2026-08-09 (session start)
- **Completed:** 2026-08-09T14:20:32+03:00
- **Tasks:** 3
- **Files modified:** 13 (9 created, 4 modified)

## Accomplishments

- `RenderEnginePort` + `MustacheFlexmarkRenderAdapter`: jmustache resolves placeholders against a
  plain `Map` context (HTML escaping off), then flexmark parses the result as Markdown; every
  `HtmlBlock`/`HtmlCommentBlock`/`HtmlInline`/`HtmlInlineComment` node the parser produces is
  replaced by a `Text` node holding the identical raw characters before rendering, because
  flexmark 0.64.8's inline HTML recognition (`InlineParserImpl.parseHtmlInline()`) has no
  `Parser` option that can be turned off — confirmed by reading the library's own source, not
  assumed from its javadoc. `<script>` is inert whether typed into the body or arriving through
  an interpolated ticket field, and the AST Phase 5 will walk contains zero raw-HTML nodes.
- `RenderService` is the single bounded render entry point (D-04-11): a virtual-thread executor
  plus `Future.get(timeout)` bounds wall-clock execution, and a pre-flight walk of every
  collection in the `ReleaseView` (items, groups, group items, comments, commits, pull requests)
  bounds throughput — both proven by tests that actually trip them, not just assert the config
  value. Unknown placeholders are rewritten to `[[unknown: token]]` before the source reaches the
  engine, using a `Deque<Boolean>` to track which open sections are unknown so a same-named valid
  section elsewhere is never mis-blanked.
- `RenderContextMapper` flattens `ReleaseView` into nested `Map`/`List` of `String`/`Boolean`
  only — no record, no entity, nothing the engine could reflect into a method call (TMPL-02).
- `ReleaseViewAssembler` turns a `Release` into a `ReleaseView`: one ordered item list
  (ticket-backed items sorted by key, manual items sorted by `addedAt`, tickets first), and
  deterministic groups — issue-type groups alphabetically first, then manual-category groups in
  `ManualItemCategory` declaration order, resolving UI-SPEC planning input #4. Category labels
  match `frontend/src/api/releases.ts`'s `MANUAL_ITEM_CATEGORY_LABELS` exactly.
- `ArchitectureBoundaryTest` gained a new rule confining `com.samskivert`/`com.vladsch` imports to
  `adapters.render`, and `adapters.render` joined the existing application-vs-adapters ban so
  `RenderService` can only reach the engine through `RenderEnginePort`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add jmustache and flexmark, write the render port and adapter** - `42497a2` (feat)
2. **Task 2: Write RenderService with the timeout, collection cap and unknown-placeholder marker** - `58ff51f` (feat)
3. **Task 3: Write the release-to-view-model assembler and extend the ArchUnit boundary rules** - `0a1116b` (feat)

**Plan metadata:** committed separately by orchestrator after wave merge (per parallel-execution contract, this executor does not touch STATE.md/ROADMAP.md).

## Files Created/Modified

- `pom.xml` - adds `com.samskivert:jmustache:1.16` and `com.vladsch.flexmark:flexmark:0.64.8` (core module, not `flexmark-all`)
- `src/main/resources/application.yml`, `src/test/resources/application.yml` - `app.template.render.timeout: PT2S` and `app.template.render.max-collection-size: 500`, added identically to both (the test file shadows main's rather than merging)
- `src/main/java/com/evision/releasemgmt/ports/RenderEnginePort.java` - the port keeping jmustache/flexmark out of domain/application
- `src/main/java/com/evision/releasemgmt/adapters/render/MustacheFlexmarkRenderAdapter.java` - jmustache + flexmark implementation, with the AST-neutralisation walk
- `src/main/java/com/evision/releasemgmt/application/RenderResult.java` - `{html, unknownTokens}` carrier
- `src/main/java/com/evision/releasemgmt/application/RenderContextMapper.java` - `ReleaseView` -> plain-data `Map`
- `src/main/java/com/evision/releasemgmt/application/RenderService.java` - the bounded entry point: scan, collection cap, unknown-token marker, timeout
- `src/main/java/com/evision/releasemgmt/application/ReleaseViewAssembler.java` - `Release` -> `ReleaseView` with grouping
- `src/test/java/com/evision/releasemgmt/adapters/render/MustacheFlexmarkRenderAdapterTest.java` - 9 tests, one per behavior bullet, including the AST-node assertion
- `src/test/java/com/evision/releasemgmt/application/RenderServiceTest.java` - 7 tests: unknown marker, unknown section, partial rejection, both collection-cap trips, timeout trip, context-mapper leaf-type walk
- `src/test/java/com/evision/releasemgmt/application/ReleaseViewAssemblerTest.java` - 6 tests: item ordering, manual-item shape, ticket-item shape, group ordering, frozen/open status+frozenAt, null-to-empty
- `src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java` - new `renderLibrariesLiveOnlyInTheRenderAdapter` rule; `adapters.render` added to the application-vs-adapters ban

## Decisions Made

- **flexmark core module only, not `flexmark-all`.** CLAUDE.md's stack table nominates
  `flexmark-all:0.64.8`, but that bundle pulls in every extension plus PDF/DOCX converter modules.
  This phase needs only CommonMark parse and HTML render; CLAUDE.md's own no-over-engineering rule
  applies to dependency weight. Recorded as required by the plan's Task 1 action block.
- **Raw HTML neutralised by AST walk, not by a `Parser`/`HtmlRenderer` configuration flag.** The plan's
  Task 1 anticipated this might be necessary ("if the AST-node assertion cannot be satisfied by
  parser options alone, add an explicit post-parse check"). Investigation of flexmark 0.64.8's own
  sources (`InlineParserImpl.java:417`) confirmed `parseHtmlInline()` is called unconditionally for
  every `<` character with no `Parser` `DataKey` guarding it — there is no config-only way to stop
  inline HTML from becoming an `HtmlInline` node. Rather than throwing (which would also reject
  interpolated ticket content containing `<`, breaking the "render as escaped text" requirement),
  `MustacheFlexmarkRenderAdapter.neutralizeRawHtml` walks the parsed `Document` and replaces every
  `HtmlBlockBase`/`HtmlInlineBase` node with a `Text` node carrying the same raw characters. `Text`
  nodes are always rendered HTML-escaped by flexmark's core renderer, so this satisfies both the
  render-output behavior test and the separate zero-`HtmlBlock`/`HtmlInline` AST assertion.
- **jmustache configuration verified against 1.16's actual source**, not guessed: `emptyStringIsFalse`
  is a real compiler flag (`Mustache.Compiler.emptyStringIsFalse(boolean)`); missing-key resolution
  throws `MustacheException` by default with no `defaultValue()` set, confirmed in `Template.java`'s
  `checkForMissing`; empty-collection inverted sections (`{{^commits}}`) are falsy through jmustache's
  built-in iterator-emptiness check in `InvertedSegment.execute`, independent of `emptyStringIsFalse` -
  so only the empty-string case needed that flag turned on.
- **`markUnknownTokens` tracks section state with a `Deque<Boolean>`**, not name-only lookup against the
  unknown-token set, so a `{{/name}}` is blanked only when its own matching `{{#name}}`/`{{^name}}` was
  itself unknown - not whenever `name` happens to also appear as an unrelated unknown token elsewhere in
  the same template.

## Deviations from Plan

None functionally beyond what is already documented above as "Decisions Made" (the flexmark raw-HTML
neutralisation approach, explicitly anticipated as a fallback by the plan's own Task 1 action block).
One cosmetic note: the plan's Task 3 action block sketch for `MustacheFlexmarkRenderAdapter`'s
constructor used a shared `MutableDataSet` passed to both the `Parser` and `HtmlRenderer` builders;
since no `Parser`/`HtmlRenderer` options are actually set (the neutralisation walk is the real
defence, not a config flag), the implementation uses the no-argument `Parser.builder()` /
`HtmlRenderer.builder()` instead, avoiding an unused, empty `MutableDataSet`.

## Issues Encountered

None. JDK 25 was available at `$HOME/.jdks/jdk-25`, network access allowed resolving
`com.samskivert:jmustache:1.16` and `com.vladsch.flexmark:flexmark:0.64.8` (both absent from the
local Maven cache, which only held flexmark 0.42.14 from an earlier, unrelated dependency). The full
`mvn clean test` suite (382 tests, up from the 359-test baseline: +9 adapter, +7 service, +6
assembler, +1 ArchUnit) passed with zero failures/errors.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `RenderService`, `RenderEnginePort`, `RenderContextMapper` and `ReleaseViewAssembler` are ready for
  plan 04-04 (the template CRUD/preview REST layer) to call directly.
- Phase 5's document generation is the eventual second caller of this same `RenderService` (D-04-11);
  no bound-enforcement logic needs to be duplicated there.
- Phase 5's PDF/DOCX AST walk can rely on `MustacheFlexmarkRenderAdapter.parseMarkdown` producing a
  `Document` with zero raw-HTML nodes, already proven by `MustacheFlexmarkRenderAdapterTest`.
- No blockers identified.

## Threat Flags

None beyond the plan's own `<threat_model>`, which this plan's implementation satisfies directly
(T-04-12 through T-04-17). No new network endpoint, auth path, file access pattern or schema change
was introduced outside what the plan specified.

---
*Phase: 04-template-authoring*
*Completed: 2026-08-09*

## Self-Check: PASSED

All 9 created files (6 main source + 3 test files) plus this SUMMARY.md verified present on disk.
All 4 commit hashes (`42497a2`, `58ff51f`, `0a1116b`, `95d7da7`) verified present in `git log`. Full
`mvn clean test` (JAVA_HOME=jdk-25) exits 0 with 382 tests passing (359 baseline + 23 new), including
`ArchitectureBoundaryTest`.
