---
phase: 05-document-generation
verified: 2026-08-11T00:00:00Z
status: gaps_found
score: 6/8 roadmap success criteria verified (1 failed, 1 needs human verification)
overrides_applied: 0
gaps:
  - truth: "Release manager can preview any generated document and export it as PDF or DOCX (ROADMAP Success Criterion 4)"
    status: failed
    reason: >
      The document-viewer route that renders a generated document's content
      (`/releases/$releaseId/documents/$documentType`) was designed in 05-UI-SPEC.md as "screen 2" of
      this phase and referenced throughout 05-PATTERNS.md/05-RESEARCH.md as reusing TemplatePreview's
      markdown-it renderer, but no plan in this phase (05-01 through 05-11, the full set that exists)
      ever created it. `frontend/src/router.tsx` registers no route matching that path. Every "View"
      link the phase built (ReadinessPill's Up-to-date/Stale pills, DocumentsSection's
      DocumentViewLink for both the latest and every superseded revision) points at this
      unregistered URL and, by the phase's own code comments, was left as a plain `<a href>` "because
      the document viewer route ... is registered by a later plan (05-12)" — a plan that does not
      exist in this phase's plan set. Export (PDF/DOCX download) is fully functional and independent
      of this route, so only the "preview" half of the criterion fails, but the criterion is
      conjunctive ("preview ... and export") and is not met as stated.
    artifacts:
      - path: "frontend/src/router.tsx"
        issue: "No route registered for /releases/$releaseId/documents/$documentType"
      - path: "frontend/src/components/ReadinessPill.tsx"
        issue: "Up-to-date/Stale pills link to the unregistered viewer route"
      - path: "frontend/src/components/DocumentsSection.tsx"
        issue: "DocumentViewLink (main row and every RevisionRow) links to the unregistered viewer route"
    missing:
      - "A DocumentViewerRoute (or equivalent) component registered at /releases/$releaseId/documents/$documentType that renders a stored DocumentSnapshot's markdown/chrome (reusing TemplatePreview's markdown-it renderer per 05-UI-SPEC.md screen 2), handles the superseded-revision banner, template-deleted note, DOCUMENT_NOT_FOUND state, and the DOC-08 human-verification hint UI-SPEC specified for this screen."
      - "Upgrade of ReadinessPill and DocumentsSection's View links from plain <a href> to typed <Link> once the route exists (already flagged as a pure follow-up by 05-10-SUMMARY.md and 05-11-SUMMARY.md)."
human_verification:
  - test: "DOC-08 visual parity: generate a document, export both PDF and DOCX, open them side by side"
    expected: "Cover page, running header, footer text and 'Page X of Y' numbering are visually equivalent between the two formats (same content, same general placement) - not merely both present"
    why_human: "D-05-07 deliberately declined automated parity assertions or golden-image comparison. Chrome is applied by two independent mechanisms (openhtmltopdf CSS @page/counter() vs docx4j titlePg/HdrFtrRef/CTSimpleField) with no shared code path, so a human eyeball check is the only verification this phase built. Automated tests (PdfExportAdapterTest, DocxExportAdapterTest) prove structural presence of cover/header/footer/page-numbering in each format independently, not that the two look alike."
---

# Phase 5: Document Generation Verification Report

**Phase Goal:** Release manager can generate, preview, and export release notes, deployment runbooks,
and approval documents from templates, with every generated document retained as an immutable audit
record — working correctly for Jira-only releases with no Azure DevOps data.

**Verified:** 2026-08-11
**Status:** gaps_found
**Re-verification:** No — initial verification

**Note on mode:** ROADMAP.md marks this phase `Mode: mvp`, but the phase goal text is not formatted
as a User Story ("As a X, I want Y, so that Z."). The `gsd-sdk`/`gsd-tools` CLI used to validate that
format and to query roadmap/plan data programmatically is not available in this environment. Rather
than refuse verification, this report falls back to standard goal-backward verification against
ROADMAP.md's eight explicit, testable Success Criteria (Step 2a / Option C fallback), which is a
superset of what MVP narrowing would check.

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Generate release notes, grouped by issue type by default and by component when the template specifies it (epic/label out of scope per D-05-02) | ✓ VERIFIED | `DocumentGenerationService.generate` (05-04) renders through `RenderService`; `ReleaseViewAssembler.assemble(release, dimension)` (05-09) groups by `template.groupingDimension()`; `GroupingDimension` enum + toolbar `<select>` in `TemplateDetailRoute.tsx`; `ReleaseViewAssemblerTest` covers both dimensions plus the "Uncategorised" default. ROADMAP wording confirmed narrowed (`grep -c 'epic, or label' ROADMAP.md` → 0). |
| 2 | Generate a deployment runbook with distinct, labelled deploy-steps / post-deploy-validation / rollback-with-trigger-conditions sections | ✓ VERIFIED | `src/main/resources/templates/starter-runbook.md` has real content under `## Deploy steps`, `## Post-deploy validation`, `## Rollback` (confirmed via direct read — no `_Replace with` stubs remain; "Trigger conditions" section present). |
| 3 | Generate an approval/change document with risk, impact, and backout-plan sections | ✓ VERIFIED | `src/main/resources/templates/starter-approval.md` has real content under `## Risk`, `## Impact`, `## Backout plan` (confirmed via direct read — no `_Describe` stubs remain). |
| 4 | Preview any generated document and export it as PDF or DOCX | ✗ FAILED | Export is fully functional (see Artifacts below). **Preview is not implemented** — see Gaps below. |
| 5 | Every generated document stored as an immutable snapshot (template version, content, generation timestamp); regeneration appends a new revision | ✓ VERIFIED | `DocumentSnapshotRepository` (05-03) exposes only `create` + reads, no update/delete/setActive (grep-confirmed, code-reviewed). `DocumentSnapshotPersistenceIT` (8 tests) proves revision 1 stays intact after revision 2 is written and survives source-template deletion. `DocumentsSection.tsx`'s Regenerate button opens no confirmation dialog and only appends. |
| 6 | Both PDF and DOCX render cover page, running header, footer and page numbering; the two formats are visually equivalent, not merely both present | ⚠️ STRUCTURALLY VERIFIED / VISUAL EQUIVALENCE NEEDS HUMAN | `PdfExportAdapterTest` (11 tests) and `DocxExportAdapterTest` (15 tests, re-run and confirmed green) independently assert cover/header/footer/page-numbering presence in each format via PDFBox text extraction and reloaded OOXML respectively. D-05-07 explicitly declined any automated cross-format parity check (structural or golden-image) — visual equivalence is a human judgment call this phase left undecided. See Human Verification below. |
| 7 | Cross-release visibility of which of the three documents are generated / stale / never generated | ✓ VERIFIED | `DocumentReadinessService.state()`/`all()` (05-10) implements the exact FROZEN/NO_ACTIVE_TEMPLATE/NOT_GENERATED/STALE/UP_TO_DATE logic read directly from source; `stampItemsChanged` wired into all 6 item-mutating `ReleaseRepositoryAdapter` methods plus `create` (confirmed by direct read) — so ticket removal is visible to staleness, closing the gap the plan called out. `/releases` gained three `ReadinessPill` columns (`ReleasesRoute.test.tsx`, `ReadinessPill.test.tsx` — 21 tests re-run, green). |
| 8 | UX-01 explicitly resolved to v2 with recorded reasoning; every interactive element this phase adds is native/keyboard-operable with no drag interaction | ✓ VERIFIED | REQUIREMENTS.md UX-01 marked `[x] RESOLVED 2026-08-10 — moved to v2` with a full dated decision note. `grep` for `onDrag`/`draggable` across every Phase-5-authored frontend file (`DocumentsSection.tsx`, `ReadinessPill.tsx`, `ReleasesRoute.tsx`, `ReleaseDetailRoute.tsx`, `TemplateDetailRoute.tsx`, `documents.ts`) returns nothing. `LogoUpload.tsx`'s pre-existing `onDragOver` is confirmed Phase-4-only via `git log` (last touched in commit `24fe6fe`, a Phase 4 commit) — correctly not a violation per the verification brief. |

**Score:** 6/8 truths fully verified; 1 failed (preview); 1 needs human sign-off (visual equivalence) with automated structural coverage in place.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `DocumentGenerationService.java` | Generate orchestration through `RenderService`, no lifecycle guard | ✓ VERIFIED | Confirmed via 05-04-PLAN/SUMMARY and grep evidence in prior review; no `RenderEnginePort` import, no `requireOpen`. |
| `DocumentSnapshotRepository` + adapter | Append-only (create + reads only) | ✓ VERIFIED | No update/delete/setActive method exists anywhere on the port or adapter (plan-time grep criteria + code review confirmed no regression). |
| `PdfExportAdapter.java` | Cover/header/footer/page-numbering via shared flexmark AST | ✓ VERIFIED | `PdfExportAdapterTest` 11/11 green on re-run; uses `renderAdapter.parseMarkdown`/`renderHtml`, no second `Parser`/`HtmlRenderer`. |
| `DocxExportAdapter.java` + `DocxAstWalker.java` | Same chrome via docx4j, closed AST vocabulary, unknown-node fallback to literal text | ✓ VERIFIED | `DocxExportAdapterTest` 15/15 green on re-run (one more than the plan's stated 14 — a review-cycle addition, not a regression). WR-03 numbering-level fix (`ilvl 0-4` + `Math.min(depth, MAX_LIST_DEPTH)`) confirmed present in both `DocxExportAdapter.java` and `DocxAstWalker.java`. |
| `ExportFilename.java` | Allowlist Content-Disposition sanitiser | ✓ VERIFIED | `ExportFilenameTest` 11/11 green; `LogoValidator.safeFilename` (CR-01 fix) confirmed strips quotes/control chars, matching the same posture. |
| `DocumentReadinessService.java` | Single staleness comparison, FROZEN terminal state | ✓ VERIFIED | Direct read matches plan exactly; `DocumentReadinessServiceTest` 9/9 green on re-run. |
| `frontend/src/components/DocumentsSection.tsx` | Generate/Regenerate, export, revision history, per-row error scoping | ✓ VERIFIED (as a generate/export/history surface) | Full file read; per-`DocumentRow` hook instances confirmed (no cross-type state bleed); `ConfirmDialog` absent (no dialog on regenerate); 15/15 `DocumentsSection.test.tsx` + `ReadinessPill.test.tsx` tests re-run green. |
| `frontend/src/router.tsx` — document viewer route | `/releases/$releaseId/documents/$documentType` registered and rendering a stored snapshot | ✗ **MISSING** | No such route exists anywhere in `router.tsx`. This is the artifact behind the Truth-4 failure above. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `ReleaseRepositoryAdapter` | `release.items_changed_at` | `stampItemsChanged` on every item mutation | ✓ WIRED | Confirmed present at all 6 mutation call sites plus `create` (direct file read). |
| `DocumentGenerationService` | `RenderService` | `renderService.render`/`resolveText` | ✓ WIRED | Confirmed by 05-04 grep evidence; no direct `RenderEnginePort` usage. |
| `PdfExportAdapter`/`DocxExportAdapter` | `MustacheFlexmarkRenderAdapter.parseMarkdown`/`renderHtml` | constructor injection, same package | ✓ WIRED | Confirmed no second `Parser`/`HtmlRenderer` instantiation in either adapter. |
| `DocumentsSection.tsx` | `useGenerateDocument`/`useExportDocument`/`useDocumentRevisions` | `frontend/src/api/documents.ts` | ✓ WIRED | Confirmed by direct file read — every hook imported and used correctly. |
| `ReadinessPill`/`DocumentsSection` View links | document viewer route | plain `<a href>` (typed `<Link>` deferred pending route registration) | ✗ **NOT WIRED** | The target route does not exist. This is not merely a "typed Link vs plain anchor" cosmetic gap (which the brief said not to flag) — the underlying route itself was never built, so the link goes nowhere regardless of anchor type. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `DocumentsSection` readiness pills | `readiness.data?.entries` | `GET /api/documents/readiness` → `DocumentReadinessService.all()` → real DB queries (`ReleaseRepository.search`, `TemplateRepository.findActive`, `DocumentSnapshotRepository.findLatestForAllReleases`) | Yes | ✓ FLOWING |
| `DocumentsSection` revision history | `revisions.data?.revisions` | `GET .../revisions` → `DocumentSnapshotRepository.findAllRevisions` | Yes | ✓ FLOWING |
| Export download bytes | `apiFetchBlob` response | `GET .../export.pdf` / `.../export.docx` → `DocumentExportService` → stored `snapshot.markdown()`/`chrome()` (no re-render) | Yes | ✓ FLOWING |
| Document viewer content | N/A | N/A — no such screen exists to trace | N/A | ✗ DISCONNECTED (no source exists because no consumer exists) |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| DOCX/PDF export adapters still produce valid, chrome-bearing output after review fixes | `mvn test -Dtest='DocxExportAdapterTest,PdfExportAdapterTest,DocumentReadinessServiceTest,ExportFilenameTest'` | 46 tests, 0 failures | ✓ PASS |
| Frontend Documents/Readiness surfaces render and pass their own test suite | `npx vitest run tests/components/DocumentsSection.test.tsx tests/components/ReadinessPill.test.tsx` | 21 tests, 0 failures | ✓ PASS |
| CR-01 fix (logo filename sanitisation) present | direct file read of `LogoValidator.safeFilename` | strips `"`, `\`, CR/LF, control chars | ✓ PASS |
| WR-03 fix (DOCX nested-list numbering) present | direct file read of `DocxExportAdapter`/`DocxAstWalker` | `ilvl` 0-4 defined, walker depth capped via `Math.min` | ✓ PASS |
| Document viewer route reachable | `grep -rn "documents/\$documentType" frontend/src/router.tsx` | no match — route not registered | ✗ FAIL (see Gaps) |

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|-----------------|--------------|--------|----------|
| DOC-01 | 05-04, 05-11 | Generate release notes from template | ✓ SATISFIED | Generate button + `DocumentGenerationService`, confirmed working end-to-end for generation and regeneration. |
| DOC-02 | 05-01, 05-02, 05-07, 05-09 | Group by issue type / component (epic/label out of scope) | ✓ SATISFIED | `GroupingDimension`, `{{component}}` catalog token, toolbar control all confirmed. |
| DOC-03 | 05-02 | Runbook labelled sections (superseded to template-content wording) | ✓ SATISFIED | Real content confirmed in `starter-runbook.md`. |
| DOC-04 | 05-02 | Approval labelled sections (superseded to template-content wording) | ✓ SATISFIED | Real content confirmed in `starter-approval.md`. |
| DOC-05 | 05-01, 05-05, 05-08, 05-11 | Export as PDF | ✓ SATISFIED | `export.pdf` endpoint + `ExportButtons` UI confirmed wired and tested. |
| DOC-06 | 05-01, 05-06, 05-08, 05-11 | Export as DOCX | ✓ SATISFIED | `export.docx` endpoint + `ExportButtons` UI confirmed wired and tested. |
| DOC-07 | 05-03, 05-04, 05-11 | Immutable snapshot, append-only revisioning | ✓ SATISFIED | No mutation surface on the repository/port; IT proves revision isolation. |
| DOC-08 | 05-05, 05-06 | PDF/DOCX chrome parity, visually equivalent | ? NEEDS HUMAN | Structural presence proven per-format by automated tests; cross-format visual equivalence has no automated check by deliberate design (D-05-07) and needs a human side-by-side check. |
| DOC-09 | 05-10, 05-11 | Cross-release readiness (generated/stale/never) | ✓ SATISFIED | `DocumentReadinessService` + `stampItemsChanged` + `/releases` columns confirmed wired and removal-aware. |
| UX-01 | 05-02 | Resolved to v2, no drag interaction added | ✓ SATISFIED | Confirmed resolved in REQUIREMENTS.md; no drag handlers in any Phase-5-authored file. |

**Orphaned requirements:** None — every ID mapped to Phase 5 in REQUIREMENTS.md's phase-mapping table (DOC-01..09, UX-01) appears in at least one plan's `requirements:` frontmatter.

**Note:** No REQUIREMENTS.md line item literally says "preview a generated document" — that phrase only appears in ROADMAP.md's phase goal and Success Criterion 4. The gap below is therefore reported against the ROADMAP contract (Step 2a), not against a specific DOC-xx ID, and none of the ten requirement IDs are individually blocked by it. It nonetheless blocks the phase goal as ROADMAP.md states it.

### Anti-Patterns Found

No blocker or warning-level anti-patterns (TODO/FIXME/placeholder/stub markers, empty implementations, hardcoded-empty props) found in the Phase-5-authored backend or frontend files inspected. The two already-tracked, intentionally-not-reported items named in the verification brief were confirmed present and unchanged:
- `AdfFlattener` blank-line-between-paragraphs gap (tracked todo, out of this phase's scope — actually already fixed in a `quick-260810-wc3` commit visible in the git log, unrelated to this phase's plans).
- `removeItem`/`removeManualItem` unconditionally stamping `items_changed_at` even on a no-op delete (confirmed still present at lines 99-105 and 173-179 of `ReleaseRepositoryAdapter.java` — matches the tracked-todo description exactly, not a new finding).

### Human Verification Required

#### 1. DOC-08 Visual Parity Check

**Test:** Generate a document (any of the three types) with a template that has chrome enabled (cover, header, footer, page numbers). Export both PDF and DOCX. Open both files side by side.
**Expected:** Cover page layout, running header text, footer text and "Page X of Y" numbering are visually equivalent between the two formats — not necessarily pixel-identical, but recognizably the same document with the same chrome content in the same general positions.
**Why human:** D-05-07 is an explicit, recorded, deliberate decision to accept this gap rather than build automated parity assertions or golden-image comparison, because the two renderers use completely independent mechanisms (CSS `@page`/`counter()` vs. OOXML `titlePg`/`HdrFtrRef`/`CTSimpleField`) with no shared code below the flexmark AST (which only covers body content, not chrome). This was supposed to be surfaced to the user via an in-app hint on the document viewer screen per 05-UI-SPEC.md ("Visual parity check — open the PDF and DOCX you just exported side by side...") — that hint does not exist because the document viewer itself was never built (see Gaps below), so this check must be performed manually without in-app prompting.

## Gaps Summary

**One blocking gap: the document viewer (preview) screen was designed but never built.**

`05-UI-SPEC.md` explicitly designs two screens for this phase: screen 1 (the `/releases` readiness
columns and the release-detail Documents section — both built, across plans 05-10 and 05-11) and
screen 2, "`/releases/$releaseId/documents/$documentType` — document viewer (new route)" (UI-SPEC
line 296), which is meant to render a stored `DocumentSnapshot`'s resolved Markdown/chrome by reusing
`TemplatePreview.tsx`'s renderer, plus the superseded-revision banner, template-deleted note,
document-not-found state, and the DOC-08 visual-parity hint.

This phase's plan set stops at `05-11-PLAN.md`. Both `05-10-SUMMARY.md` and `05-11-SUMMARY.md`
explicitly and repeatedly reference "plan 05-12" as the plan that will register this route and
explain that they deliberately left `ReadinessPill` and `DocumentsSection`'s View links as plain
`<a href>` rather than typed `<Link>` "because the document viewer route ... is registered by a
later plan (05-12)". No `05-12-PLAN.md` or `05-12-SUMMARY.md` exists anywhere in
`.planning/phases/05-document-generation/`, and no `05-CONTEXT.md` decision declines or defers this
work — on the contrary, `05-CONTEXT.md`'s own canonical-references section lists
`frontend/src/components/TemplatePreview.tsx` as code this phase builds on specifically because
"document preview should reuse or mirror it." This was expected, in-scope work that was simply never
turned into an executed plan.

**User-facing impact:** every "View" link this phase shipped — the green "Up to date" pill, the amber
"Stale" pill, the "View" action next to a document's latest revision, and the "View" action next to
every superseded revision in the revision-history disclosure — navigates to a URL with no matching
route in `frontend/src/router.tsx`. A release manager clicking any of these gets no rendered document.
Export (PDF/DOCX download) is unaffected and fully functional through a separate code path
(`useExportDocument`/`apiFetchBlob`), so document *generation* and *export* work end-to-end; only
*preview*, which ROADMAP.md's phase goal and Success Criterion 4 name explicitly, is missing.

This does not block Phase 6 (Azure DevOps Enrichment) or Phase 7 (Promotion Tracking), which do not
declare a dependency on the document viewer specifically, though Phase 7's AUDIT-02 ("view every
document generated for a release, including superseded revisions") is related enough that the same
screen would likely serve both — worth keeping in mind when this gap is closed, but not close enough
evidence to defer this gap onto Phase 7 per the conservative-matching rule.

---

_Verified: 2026-08-11_
_Verifier: Claude (gsd-verifier)_
