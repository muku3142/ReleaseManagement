# Phase 5: Document Generation - Research

**Researched:** 2026-08-10
**Domain:** Markdown-AST-to-OOXML/PDF document generation (docx4j, openhtmltopdf), Jira component field, immutable-snapshot document storage
**Confidence:** HIGH on the two highest-risk unknowns (flexmark-docx-converter compatibility, table-parsing gap); MEDIUM on exact docx4j/openhtmltopdf chrome code shapes (community forum sources, no official cookbook); HIGH on Jira component field shape and existing-codebase patterns

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Runbook and approval content**

- **D-05-01 — Runbook and approval content is static text authored in the template, not modelled data.**
  Deploy steps, post-deploy validation, rollback trigger + procedure, risk, impact and backout plan
  are written into the runbook and approval templates as ordinary Markdown. No new entity, no form,
  no per-release values.

  *Why:* cheapest path to a complete document set, and it needs nothing beyond what Phase 4 already
  built. Three alternatives were declined: structured fields on the release (a V5 migration plus a
  form screen), a narrower version adding only the three approval scalars, and a
  prompt-at-generation-time form.

  > **Cost, accepted deliberately.** This content is **identical for every release** until someone
  > edits the template. A generated approval document's "risk" and "backout plan" are boilerplate,
  > not an assessment of the release being approved. **DOC-03 and DOC-04 were reworded** (commit
  > `616f414`) from "whose data model provides…" to "with distinct, clearly-labelled sections…
  > authored as template content", because static text cannot satisfy a requirement that says *data
  > model*. Rewording was chosen over leaving them permanently unmet. Per-release approval scalars
  > are recorded as the **v2 candidate** — that is the case where boilerplate is least defensible,
  > because a human signs the result.

  **Planner note:** the three seeded starter templates (D-04-14) are where this content lives. The
  runbook and approval starters must ship with real, labelled sections — they are now the *only*
  thing satisfying DOC-03 and DOC-04, not merely a convenience.

**Ticket fields and grouping**

- **D-05-02 — Fetch and store `component` only. Not epic, not label.**
  D-04-17 deferred component/epic/label to this phase as a hard commitment. Only **component** is
  taken: it is a plain field on the Jira issue, it needs no parent-link resolution, and it is the
  dimension that matches real documents.

  *Evidence:* the release manager's actual `Orchstro 4.2.0 Release Notes.docx` groups by
  component-shaped sections — "Swift Day Screen", "Matching Module", "Security" — not by issue type.
  See `docs/templates/orchstro-release-notes.md` for the full mapping of that document onto the
  view-model.

  **DOC-02's wording names epic and label explicitly and must be trimmed to match**, in the same way
  DOC-03/DOC-04 were. Do this before the requirements-coverage gate runs.

  Work implied: extend the Jira field selection (through JIRA-04's existing metadata-resolution
  mechanism, never a hardcoded `customfield_NNNNN`), add a column to `release_ticket_item`, a **`V5`**
  migration, and extend `ItemView` + `PlaceholderCatalog` + the `{{#groups}}` dimension.

- **D-05-03 — New column is nullable. No backfill. Items with no component group as "Uncategorised".**
  Existing `release_ticket_item` rows keep `NULL`. Open releases pick the value up naturally on their
  next refresh (D-03-07). **Frozen releases are never touched.**

  *Why:* backfilling a frozen release would mutate an audit record, contradicting D-03-10 ("freeze is
  the point after which the stored copy stops changing") and undermining DOC-07's immutability
  guarantee, which rests on it. A migration-time Jira call was also declined because it would breach
  D-14 — nothing reaches an external system while no user is signed in.

- **D-05-04 — Grouping dimension is per-template, defaulting to issue type.**
  Phase 4 shipped `{{#groups}}` hard-wired to issue type (D-04-02). This phase adds the dimension as
  a template setting: issue type (default) or component. `{{groupName}}` and the `{{#groups}}`
  section shape do not change, so **templates authored in Phase 4 keep working unchanged** — that was
  the point of shipping the group shape early.

**Document snapshots**

- **D-05-05 — A snapshot stores rendered Markdown plus a copy of the template.**
  Persist the post-Mustache, pre-flexmark Markdown, together with the template's **name, body and
  chrome fields** at generation time. PDF and DOCX are rendered **on demand** from the stored
  Markdown; neither is stored.

  *Why the template copy is mandatory, not optional:* D-04-13 made templates freely deletable, so a
  foreign key to `template` can dangle. Storing name + body + chrome is what keeps "which template
  produced this document" answerable after the template is gone.

  > **Cost, accepted deliberately.** The stored document is **not byte-immutable**. Upgrading
  > flexmark, openhtmltopdf or docx4j changes what a re-export produces, so a PDF downloaded next
  > year may not be byte-identical to one downloaded today. Storing generated PDF/DOCX bytes was the
  > alternative and was declined for storage cost and slower generation. **If byte-identical
  > re-download ever becomes a real requirement, that is the change** — it is additive (two blob
  > columns), not a redesign.

- **D-05-06 — Regeneration appends a revision. Everything is kept; the latest is shown.**
  Revisions are numbered per (release, document type). The release screen shows the latest;
  superseded revisions stay readable and exportable, which is what AUDIT-02 will surface in Phase 7.
  Nothing is ever overwritten or pruned.

  *Why all revisions stay exportable:* an auditor asking "send me exactly what was approved in March"
  is much of the reason the audit trail exists. Restricting export to the latest revision was
  considered and declined.

**PDF/DOCX parity**

- **D-05-07 — Accepted risk, recorded deliberately: no automated parity assertion for DOC-08.**
  Equivalence rests on **D-04-04's structural guarantee** — one flexmark AST feeds both renderers, so
  body content cannot diverge by construction — plus a **manual visual check once per phase**.
  Neither structural assertions on both outputs nor golden-image comparison will be built.

  > **The gap this leaves is precise.** The shared AST covers the *body*. It does **not** cover
  > *chrome*, which each renderer applies separately and by a completely different mechanism:
  > openhtmltopdf uses CSS `@page` with `counter(page)`/`counter(pages)`; docx4j uses per-section
  > header/footer parts with `PAGE`/`NUMPAGES` field codes. Chrome is therefore the one genuinely
  > divergent area and the one left unverified by automation.
  >
  > DOC-08's wording is **not** violated — a human eyeball check does verify visual equivalence. But
  > it means **this phase needs a human-verification plan of its own**, in the shape of Phase 4's
  > 04-11. Do not mark DOC-08 complete on a passing test suite.
  >
  > **Planner note:** the cheap upgrade if this bites is structural assertions over both outputs —
  > heading count and text, every item key present in both, chrome strings present in both. Additive,
  > no redesign. Do not build it now.

### Claude's Discretion

The release manager elected not to discuss the areas below. Each is recorded with reasoning so it can
be overridden at plan time.

- **D-05-08 — DOC-09 readiness lives as columns on the existing release library, not a new screen.**
  `/releases` already lists every release; three columns (Release notes / Runbook / Approval) with a
  per-cell state of generated, stale, or never-generated is the smallest thing that answers "what
  paperwork is outstanding" without a new route. `03-CONTEXT.md` explicitly anticipated this: *"The
  release library screen is the first surface Phase 5's DOC-09 will extend — leave room for it."*

- **D-05-09 — "Stale" means the release changed after the document was generated.**
  Compare the document revision's `generated_at` against the release's `last_refreshed_at` and the
  most recent item add/remove. D-03-07 recorded `last_refreshed_at` for exactly this purpose. A
  **frozen** release can never go stale — its ticket set cannot change — which makes "frozen +
  generated" the terminal, fully-satisfied state.

- **D-05-10 — Generation is an explicit per-document-type action, never automatic.**
  A "Generate" action per document type on the release screen, using that type's **active** template
  (D-04-12). Automatic generation on freeze was considered and rejected: it would produce documents
  nobody asked for, and D-04-13 permits a document type to have **zero active templates**, which
  auto-generation would have to fail on silently. The UI must handle the zero-active-template case as
  a clear "no active template for this type" state rather than an error.

- **D-05-11 — UX-01's drag-and-drop is reconsidered, and may have no consumer left in this phase.**
  UX-01 was deferred from Phase 3 (D-03-04) and again from Phase 4 (D-04-04, "a Markdown body has no
  sections to reorder"). **D-05-01 has now removed its last expected consumer** — runbook deploy
  steps are static template text, so there is nothing structured to reorder.

  What remains reorderable is **items within a release**, which D-03-04 declined on the grounds that
  "the ordering that actually matters is the ordering inside a generated document, which is
  template-driven at generation time" — and that reasoning still holds, because grouping is
  template-driven (D-05-04) and within-group order is deterministic.

  **Recommendation: raise this explicitly at plan time rather than silently deferring a third time.**
  Either build item reordering on the release detail screen with its keyboard equivalent, or move
  UX-01 to v2 with the reasoning recorded. A requirement deferred three times without decision is a
  requirement nobody has actually chosen to drop.

### Deferred Ideas (OUT OF SCOPE)

- **Per-release runbook and approval values → v2 (D-05-01).** Especially the three approval scalars,
  where boilerplate is least defensible.
- **Epic and label grouping dimensions → not planned (D-05-02).** DOC-02's wording must be trimmed to
  match. Additive if wanted later.
- **Backfilling component onto existing releases → not planned (D-05-03).** Frozen releases must never
  be backfilled at all.
- **Storing generated PDF/DOCX bytes → not planned (D-05-05).** The additive fix if byte-identical
  re-download becomes a requirement.
- **Automated PDF/DOCX parity assertions → not planned (D-05-07).** Structural assertions over both
  outputs are the cheap upgrade if drift bites.
- **Table of contents in generated documents → not scoped.** Would need a renderer feature in both
  paths.
- **UX-01 drag-and-drop → undecided, must be resolved at plan time (D-05-11).** Deferred twice
  already; D-05-01 removed its last expected consumer. Decide to build it or move it to v2 — do not
  defer silently a third time.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DOC-01 | Release manager can generate release notes for a release from its template | Architecture Patterns' System Architecture Diagram (`DocumentGenerationService` orchestration path); existing `RenderService`/`ReleaseViewAssembler` reused unchanged |
| DOC-02 | Release notes group tickets by issue type by default, and a template can group by component (D-05-02 narrows this from the original component/epic/label wording — epic/label are out of scope) | Jira `components` field shape (array of `{id, name}` objects, confirmed against the existing `fixVersions` handling pattern in `JiraRestTicketAdapter`); D-05-04's per-template dimension setting extends `ReleaseViewAssembler`'s existing grouping method |
| DOC-03 | Deployment runbook with distinct, clearly-labelled deploy/validation/rollback sections, authored as template content (reworded 2026-08-10 — no data-model implication) | No rendering-technology research needed — D-05-01 makes this a content-authoring task (starter template body), not a code task; the AST-walker vocabulary (Code Examples) must correctly render whatever heading/list structure the starter authors use |
| DOC-04 | Approval/change document with clearly-labelled risk/impact/backout-plan sections, authored as template content (reworded 2026-08-10) | Same as DOC-03 |
| DOC-05 | Export any generated document as PDF | openhtmltopdf chrome mechanism (Pattern 3), fixed AST vocabulary → XHTML via the *existing* `HtmlRenderer` (Code Examples), streaming/buffering guidance (Pitfall 6) |
| DOC-06 | Export any generated document as DOCX | The core research finding of this phase: hand-rolled AST walker into docx4j 17.0.2 (Standard Stack, Alternatives Considered), chrome mechanism (Pattern 3), fixed vocabulary table (Code Examples) |
| DOC-07 | Each generated document stored as an immutable snapshot recording template version, content and generation timestamp; regeneration creates a new revision | Recommended Project Structure (`DocumentSnapshot` aggregate, append-only repository adapter); D-05-05/D-05-06 constraints copied above govern the shape directly |
| DOC-08 | PDF and DOCX both render cover/header/footer/page-numbering, visually equivalent (not merely both present) | The two independent chrome mechanisms researched in depth (Pattern 3, Sources) — openhtmltopdf CSS `@page`/`counter()`, docx4j `titlePg`/`HdrFtrRef`/`CTSimpleField`; D-05-07's accepted-risk framing (no automated parity check) carried into User Constraints above |
| DOC-09 | Visibility across all releases of generated/stale/never-generated document state | Architectural Responsibility Map (readiness computed backend-side, rendered frontend-side only); D-05-08/D-05-09 constraints above define the exact comparison and surface |
| UX-01 | Drag-and-drop with keyboard-accessible equivalent, wherever reordering is the natural gesture | Not a rendering-technology research question — D-05-11 (above) flags this as an explicitly unresolved product decision the planner must raise, not something this research can settle by investigating a library |

</phase_requirements>

## Summary

The single highest-risk unknown going into this phase — whether flexmark ships a ready-made
Markdown-AST-to-OOXML converter — resolves to **no, don't use it**. `flexmark-docx-converter`
exists and its `DocxRenderer` genuinely accepts a pre-parsed `Node`/`Document` (matching this
project's package-private `parseMarkdown` exactly), but it is pinned to **docx4j 11.4.9**, and
docx4j 17.0.0 made breaking API changes (`getContent()` replacing `getEGBlockLevelElts()` /
`getParagraphContent()` / `getRunContent()`; `ArrayListDocx4j` replacing four separate
`ArrayList*` classes) that CLAUDE.md's locked docx4j 17.0.2 sits on the far side of. Forcing the
two together is a real compatibility risk, not a hypothetical one, and the module also drags in
Log4j 2 and eleven additional flexmark extension artifacts this project doesn't use. Recommendation:
**hand-walk the AST**, directly into docx4j 17.0.2 objects, confined to the project's own fixed
and small vocabulary (paragraph, heading, bold, italic, inline code, fenced code block,
bullet/ordered list, plus **table** — see below). This is not the "don't hand-roll a complex
problem" trap CLAUDE.md warns about: the node-type set is closed, small, and already enumerated by
the AdfFlattener/template-authoring vocabulary, not an open-ended Markdown superset.

A second, unrelated but equally concrete finding: **the backend's flexmark `Parser` cannot parse
pipe tables today**, because `com.vladsch.flexmark:flexmark` (core only, no
`flexmark-ext-tables`) is on the classpath and GFM tables are an opt-in extension, not core
CommonMark. The client-side `markdown-it` preview, by contrast, parses tables **by default** (its
"default" preset applies no rule restrictions, so the always-registered `table` block rule is
active) — confirmed by reading both libraries' actual rule tables, not by inference. This is a
real client/server divergence that predates this phase (Phase 4 shipped it) but Phase 5 is where
it becomes visible and where D-04-04's "one AST feeds both renderers" guarantee would silently
exclude tables from both PDF and DOCX output. The reference document
(`docs/templates/orchstro-release-notes.md`) contains an "Updated Services" table. Fix: add
`flexmark-ext-tables:0.64.8` and register `TablesExtension.create()` on both the `Parser.builder()`
and `HtmlRenderer.builder()` in `MustacheFlexmarkRenderAdapter`.

For chrome, openhtmltopdf and docx4j remain the two independent, non-shared mechanisms
`REQUIREMENTS.md` already documents — this research confirms *how*, not *whether*, each does it:
openhtmltopdf via CSS `@page`/`@page :first` margin boxes with `counter(page)`/`counter(pages)`
(a documented, supported feature, with known quirks around top-center/top-right margin-box
positioning); docx4j via a `titlePg` first-page section flag plus `HdrFtrRef.FIRST`/`.DEFAULT`
header parts and `CTSimpleField` `PAGE`/`NUMPAGES` field codes. Both are well short of complex:
each is roughly a dozen lines of setup per renderer, not a subsystem.

**Primary recommendation:** hand-roll a small AST-walking visitor per renderer (openhtmltopdf gets
XHTML from the *existing* `HtmlRenderer` almost for free; docx4j needs a genuinely new,
narrow node-to-OOXML walker), add `flexmark-ext-tables` to close the table gap in both the shared
AST and the client preview parity story, and treat chrome (cover/header/footer/page-numbers) as
two short, renderer-specific blocks of setup code that consume the same `ChromeFields` the
render-context mapper already resolves.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Document generation (Markdown → snapshot) | API / Backend | — | `RenderService` already owns bounded rendering; generation is a new `application` service calling it, writing a JPA entity |
| PDF export (on-demand render) | API / Backend | — | openhtmltopdf runs server-side against PDFBox; no browser PDF generation exists or is wanted |
| DOCX export (on-demand render) | API / Backend | — | docx4j runs server-side; OOXML has no browser-native generation path |
| Grouping dimension (issue type / component) | API / Backend | — | `ReleaseViewAssembler` already owns grouping (D-04-02); D-05-04 extends the same class |
| Jira component fetch | API / Backend | — | `JiraRestTicketAdapter` + `FieldMetadataService` (JIRA-04's existing name-resolution mechanism); no client-side Jira access exists anywhere in this project |
| Document viewer (rendered Markdown display) | Browser / Client | — | Reuses `TemplatePreview.tsx`'s `markdown-it` instance against **already-resolved** Markdown (D-05-05); no server round-trip needed to display a stored snapshot's body |
| Readiness state (stale/up-to-date/never-generated) | API / Backend | Browser / Client | Staleness is a comparison of two backend timestamps (D-05-09); backend computes and returns the state, frontend only renders the pill — do not duplicate the comparison client-side |
| Revision history | Database / Storage | API / Backend | Append-only rows keyed by (release, document type, revision number); backend exposes a list endpoint, no client-side computation |

## Standard Stack

### Core (already locked by CLAUDE.md — versions reverified this session)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.openhtmltopdf:openhtmltopdf-pdfbox` | 1.0.10 `[VERIFIED: Maven Central, checked this session]` | HTML/XHTML → PDF | Locked by CLAUDE.md; current release confirmed unchanged since the stack doc was written |
| `org.docx4j:docx4j-JAXB-ReferenceImpl` | 17.0.2 `[VERIFIED: Maven Central, checked this session]` | DOCX generation | Locked by CLAUDE.md; current release confirmed unchanged |
| `com.vladsch.flexmark:flexmark` | 0.64.8 (already a dependency) | Markdown parse → shared `Document` AST | Already in use since Phase 4 (`MustacheFlexmarkRenderAdapter`) |

### Supporting — new for this phase

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `com.vladsch.flexmark:flexmark-ext-tables` | 0.64.8 `[VERIFIED: Maven Central, checked this session — matches the already-pinned flexmark core version exactly, same release train]` | GFM pipe-table parsing | **Required**, not optional — see Summary. Register `TablesExtension.create()` on both `Parser.builder().extensions(...)` and `HtmlRenderer.builder().extensions(...)` in `MustacheFlexmarkRenderAdapter`. Table AST nodes (`TableBlock`, `TableHead`, `TableBody`, `TableRow`, `TableCell`) are then what the new DOCX walker must also handle — see Code Examples |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hand-rolled flexmark→docx4j AST walker | `flexmark-docx-converter` | **Declined.** Pinned to docx4j 11.4.9; docx4j 17.0.0 (this project's locked version) made breaking API changes the converter's compiled bytecode depends on (`getEGBlockLevelElts`/`getParagraphContent`/`getRunContent` removed in favour of `getContent`; `ArrayListWml`/`ArrayListDml`/`ArrayListVml`/`ArrayListMce` collapsed into `ArrayListDocx4j`). Also pulls in Log4j 2 (core+api+1.2-api) and ten flexmark extension artifacts this project doesn't use, and its own default styling/template (`getDefaultTemplate()`) would need to be fought to match this project's chrome design rather than saving work. If a future phase revisits this, first confirm whether a docx4j-17-compatible release of `flexmark-docx-converter` has shipped — it had not as of this research. |

**Installation (add to `pom.xml`):**
```xml
<dependency>
  <groupId>com.openhtmltopdf</groupId>
  <artifactId>openhtmltopdf-pdfbox</artifactId>
  <version>1.0.10</version>
</dependency>
<dependency>
  <groupId>org.docx4j</groupId>
  <artifactId>docx4j-JAXB-ReferenceImpl</artifactId>
  <version>17.0.2</version>
</dependency>
<dependency>
  <groupId>com.vladsch.flexmark</groupId>
  <artifactId>flexmark-ext-tables</artifactId>
  <version>0.64.8</version>
</dependency>
```
None of these three are Boot-managed (no entry in `spring-boot-dependencies`' BOM), matching the
project's existing pattern for `jmustache` and `flexmark` — pin the version explicitly, as those
two already do.

## Architecture Patterns

### System Architecture Diagram

```
Release detail screen                 ReleasesRoute (DOC-09 columns)
        │  "Generate {DocType}"              │  readiness query per (release, docType)
        ▼                                     ▼
  POST /releases/{id}/documents/{type}   GET /releases/{id}/documents/readiness
        │
        ▼
  DocumentGenerationService
        │  1. load Release (local, D-03-06 — no Jira call)
        │  2. ReleaseViewAssembler.assemble(release)   ── existing, extended with component/group-dimension
        │  3. load the ACTIVE Template for docType     ── existing TemplateService
        │  4. RenderService.render(template.body(), view, "Body")   ── existing, BOUNDED (D-04-11)
        │     → returns resolved-Markdown string (post-Mustache, pre-flexmark)
        │  5. persist DocumentSnapshot: markdown + copied template name/body/chrome + revision N+1
        ▼
  document_snapshot table (immutable, append-only — D-05-05, D-05-06)

On-demand export (separate call, NOT part of generation):
  GET /releases/{id}/documents/{type}/{revision}/export.pdf
        │
        ▼
  ExportService.toPdf(snapshot)
        │  1. flexmark Parser.parse(snapshot.markdown())   ── same AST vocabulary as generation time
        │  2. HtmlRenderer.render(document) → XHTML body fragment   ── ALREADY EXISTS (MustacheFlexmarkRenderAdapter)
        │  3. wrap XHTML body in a chrome-aware document shell built from snapshot.chrome()
        │     (cover block / @page CSS with counter(page)/counter(pages) / logo as data: URI)
        │  4. openhtmltopdf PdfRendererBuilder(fresh instance).withHtmlContent(...).toStream(os).run()
        ▼
  PDF bytes → ResponseEntity<byte[]> (Content-Disposition: attachment)

  GET /releases/{id}/documents/{type}/{revision}/export.docx
        │
        ▼
  ExportService.toDocx(snapshot)
        │  1. flexmark Parser.parse(snapshot.markdown())   ── SAME AST, walked by a NEW hand-rolled visitor
        │  2. DocxAstWalker.walk(document, mainDocumentPart)  ── new: Paragraph/Run/Table/List OOXML objects
        │  3. build cover section (titlePg) + body section, each with own header/footer parts,
        │     PAGE/NUMPAGES CTSimpleField codes, logo as BinaryPartAbstractImage + relationship
        │  4. WordprocessingMLPackage(fresh instance).save(os)
        ▼
  DOCX bytes → ResponseEntity<byte[]> (Content-Disposition: attachment)
```

### Recommended Project Structure

```
src/main/java/com/evision/releasemgmt/
├── application/
│   ├── DocumentGenerationService.java      # new — orchestrates generate/regenerate, writes snapshot
│   ├── DocumentExportService.java          # new — PDF/DOCX on-demand render from a stored snapshot
│   └── DocumentReadinessService.java       # new — D-05-09 staleness comparison, feeds DOC-09
├── domain/document/                        # new package, mirrors domain/template's shape
│   ├── DocumentSnapshot.java                # aggregate: markdown, template copy, chrome copy, revision, generatedAt, generatedBy
│   ├── DocumentType.java                    # or reuse TemplateDocumentType directly — see Open Questions
│   └── DocumentNotFoundException.java
├── adapters/render/
│   ├── MustacheFlexmarkRenderAdapter.java  # existing — extend Parser/HtmlRenderer builders with TablesExtension
│   ├── PdfExportAdapter.java               # new — confines openhtmltopdf imports (ArchUnit boundary, matches jmustache/flexmark confinement)
│   └── DocxExportAdapter.java              # new — confines docx4j imports; owns the AST walker
├── adapters/persistence/
│   └── DocumentSnapshotRepositoryAdapter.java  # new — append-only, no update/delete methods exposed
└── adapters/web/
    └── DocumentController.java             # new — generate, export.pdf, export.docx, readiness endpoints
```

### Pattern 1: Confine openhtmltopdf and docx4j behind the render port, exactly like jmustache/flexmark

**What:** The codebase's `com_context` note is explicit: "`com.samskivert` (jmustache) and
`com.vladsch` (flexmark) are confined to `adapters.render` — openhtmltopdf and docx4j must be
confined the same way, behind a port, never imported from `domain` or `application`." This phase
does not need a *new* port necessarily — `RenderEnginePort` already returns an HTML string from
`render()`; PDF/DOCX export is a distinct operation (it consumes a **stored snapshot's Markdown**,
not a live template+view), so it is idiomatic to add sibling methods or a sibling port
(`DocumentExportPort`) rather than overload `RenderEnginePort`.

**When to use:** Any place PDF or DOCX bytes are produced.

**Example (illustrative shape, not the exact class layout):**
```java
// Source: project pattern (RenderEnginePort), applied to a new sibling port
public interface DocumentExportPort {
    byte[] toPdf(String resolvedMarkdown, ChromeFields chrome, byte[] logoBytesOrNull);
    byte[] toDocx(String resolvedMarkdown, ChromeFields chrome, byte[] logoBytesOrNull);
}
```

### Pattern 2: Build a fresh renderer instance per export call — never a shared, reused stateful builder

**What:** Both `PdfRendererBuilder` (openhtmltopdf) and `WordprocessingMLPackage` (docx4j) are
short-lived, per-document objects in every example the libraries themselves publish — there is no
"compile once, execute many times" API analogous to jmustache's `Mustache.compiler()` or
flexmark's `Parser`/`HtmlRenderer` (which genuinely are documented as reusable and are already
built once in `MustacheFlexmarkRenderAdapter`'s constructor). openhtmltopdf's own maintainers
discuss (GitHub issue #355) how to make `PdfBoxRenderer` reusable across calls specifically
*because* it is not reusable by default; the standard, safe pattern remains "new builder, new
`WordprocessingMLPackage.createPackage()`, per call."

**When to use:** Every PDF/DOCX export call.

**Why it matters for this phase specifically:** unlike `MustacheFlexmarkRenderAdapter`'s
constructor-built `Mustache.Compiler`/`Parser`/`HtmlRenderer` (safe to share, immutable
configuration objects), a `PdfRendererBuilder` accumulates per-document state (`withHtmlContent`,
fonts, output stream) and a `WordprocessingMLPackage` **is** the document being built — sharing
either across concurrent requests would corrupt output, not just be inefficient.

### Pattern 3: Two independent chrome-application code paths sharing one input shape

**What:** `ChromeFields` (already exists, TMPL-08) and the copied logo bytes are the single input
both exporters consume. Each exporter is responsible for its own idiomatic mechanism — do not try
to build a shared "chrome renderer" abstraction over CSS-margin-boxes and OOXML-section-parts;
they have nothing in common below the `ChromeFields` record itself, and forcing a shared
abstraction over two unrelated mechanisms is exactly the "unnecessary layers, wrappers, indirection"
CLAUDE.md's engineering principles warn against.

**openhtmltopdf side** (CSS, generated once per export as a `<style>` block or external stylesheet
string, using the already-Mustache-resolved chrome text — chrome fields are resolved through
`RenderService`/`RenderContextMapper` exactly like the body, before export ever sees them):
```css
/* Source: openhtmltopdf Page-features wiki (github.com/danfickle/openhtmltopdf/wiki/Page-features) */
@page {
    size: A4;
    margin: 2.5cm 2cm;
    @top-center { content: element(runningHeader); }
    @bottom-center { content: "Page " counter(page) " of " counter(pages); }
}
@page :first {
    /* Suppress the running header/footer on the cover page — same technique the reference
       template's chrome mapping implies (cover has no header text). */
    @top-center { content: none; }
    @bottom-center { content: none; }
}
#running-header { position: running(runningHeader); }
```
```html
<!-- the header element must exist in the flow once, marked running() -->
<div id="running-header">Orchstro 4.2.0 Release Notes</div>
```

**docx4j side** (per-section header/footer parts, `titlePg` for the cover page):
```java
// Source: docx4j forum pattern (docx4java.org/forums — "Example header/footer, cover page with
// page numbers"), method names verified against docx4j 17.0.2's retained API surface
// (getContent()-based navigation; HeaderPart/FooterPart/HdrFtrRef unchanged across the 17.0 break).
SectPr sectPr = wordMLPackage.getDocumentModel().getSections().get(0).getSectPr();
sectPr.setTitlePg(new BooleanDefaultTrue()); // first page gets its own header/footer

HeaderPart coverHeader = HeaderPart.newHeaderPart(); // empty — cover page shows no running header
mainDocumentPart.addTargetPart(coverHeader);
Relationship coverRel = mainDocumentPart.addTargetPart(coverHeader);
sectPr.getEGHdrFtrReferences().add(headerRef(coverRel.getId(), HdrFtrRef.FIRST));

HeaderPart bodyHeader = HeaderPart.newHeaderPart();
bodyHeader.getContent().add(paragraphWithText(resolvedHeaderText));
Relationship bodyRel = mainDocumentPart.addTargetPart(bodyHeader);
sectPr.getEGHdrFtrReferences().add(headerRef(bodyRel.getId(), HdrFtrRef.DEFAULT));
```

**Page X of Y field code (docx4j):**
```java
// Source: docx4j forum "Example header/footer, cover page with page numbers" thread;
// CTSimpleField is docx4j's high-level wrapper over the raw fldSimple/fldChar XML — prefer it
// over hand-building fldChar begin/separate/end runs unless a specific limitation forces the drop.
P footerPara = factory.createP();
R run1 = factory.createR();
CTSimpleField pageField = FieldsPPr.createFldSimple(" PAGE \\* MERGEFORMAT ");
run1.getContent().add(pageField);
// literal " of " run, then a second CTSimpleField with " NUMPAGES \\* MERGEFORMAT "
```

### Pattern 4: Content-Disposition and filenames on export

**What:** The project already has exactly this pattern for logo download
(`TemplateController.downloadLogo`, `adapters/web/TemplateController.java:155-168`) —
`ResponseEntity<byte[]>` with `Content-Disposition`, `Content-Type`, and `Cache-Control` headers
set explicitly. Export should reuse this idiom with `attachment` instead of `inline` (a real
file-save action, not an in-page view) and `Content-Type` of `application/pdf` or
`application/vnd.openxmlformats-officedocument.wordprocessingml.document`.

**Anti-Patterns to Avoid**
- **Do not derive the Content-Disposition filename from unsanitized release name/version.** A
  release name containing `"` or a CR/LF could corrupt the header or (in older, unpatched HTTP
  stacks) enable header injection. Strip or replace non-filename-safe characters before
  interpolating into the header value — the existing `downloadLogo` doesn't need this because the
  filename there comes from the *validated upload's own filename metadata*, not free-text user
  input; export filenames are the first place in this codebase building a filename from a release
  name, so this is new exposure, not a reused-safely pattern.
- **Do not try to share one "chrome renderer" abstraction between PDF and DOCX** (see Pattern 3).
- **Do not reuse a `PdfRendererBuilder` or `WordprocessingMLPackage` instance across requests**
  (see Pattern 2).
- **Do not register `TablesExtension` only on the parser and forget the renderer builder** (or
  vice versa) — both `Parser.builder().extensions(List.of(TablesExtension.create()))` and
  `HtmlRenderer.builder().extensions(List.of(TablesExtension.create()))` need the extension for
  the existing HTML-preview path to keep working; the new DOCX walker needs to handle
  `TableBlock`/`TableRow`/`TableCell` nodes regardless, since it walks the same `Document`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|--------------|-----|
| Markdown parsing / CommonMark+GFM table syntax | A custom pipe-table parser | `flexmark-ext-tables` (see Standard Stack) | Table syntax has real edge cases (alignment markers, escaped pipes, cell colspan-via-empty-cells conventions) already solved and tested upstream |
| PDF page-numbering / running headers | Manual per-page text placement via low-level PDFBox `PDPageContentStream` calls | openhtmltopdf's CSS `@page` margin-box + `counter()` mechanism | This is precisely the "declarative, standards-based" path openhtmltopdf exists to provide over raw PDFBox; CLAUDE.md already locked openhtmltopdf specifically so nobody drops to raw PDFBox for chrome |
| DOCX field codes (PAGE/NUMPAGES) | Hand-built raw `fldChar`/`instrText` run sequences | docx4j's `CTSimpleField` wrapper (`FieldsPPr.createFldSimple`) | The raw begin/separate/end `fldChar` triplet is fiddly and error-prone to get byte-correct; `CTSimpleField` is docx4j's own supported shortcut for exactly this case and is what the community/forum examples converge on |

**Key insight:** the two things that look like "big scary rendering pipelines" — PDF pagination
and DOCX field codes — are both already solved by the locked libraries via a declarative API. The
one genuinely new piece of code this phase must hand-write is the **AST-to-OOXML body walker**,
and that is scoped tightly by the project's own fixed vocabulary (see Code Examples), not an
open-ended Markdown-to-Word problem.

## Common Pitfalls

### Pitfall 1: Assuming the shared-AST guarantee already covers tables
**What goes wrong:** A template author (or the seeded Orchstro-style starter) puts a Markdown
pipe table in the body. The preview (`markdown-it`, client-side) renders it as an HTML table. The
backend (`flexmark` core only) silently renders the same source as a single paragraph of literal
`| Service | Service |` text — no error, no warning, just wrong output that only shows up in the
generated document, never in the authoring preview.
**Why it happens:** GFM tables are a flexmark *extension* (`flexmark-ext-tables`), not part of
flexmark's core CommonMark parser, while markdown-it's default preset enables its `table` block
rule unconditionally.
**How to avoid:** Add `flexmark-ext-tables` and register `TablesExtension.create()` on both the
`Parser.builder()` and `HtmlRenderer.builder()` in `MustacheFlexmarkRenderAdapter` (see Standard
Stack, Code Examples). Confirm with a save-time-validation-style render of a template body
containing a table, checked against both the HTML preview path and the new DOCX walker.
**Warning signs:** A generated document's table renders as a paragraph of pipe-delimited text.

### Pitfall 2: docx4j 17's breaking API changes silently reappearing via a transitive dependency
**What goes wrong:** Any future addition of a docx4j-adjacent community library (not just
`flexmark-docx-converter`) may transitively pull an older `docx4j-core`/`docx4j-JAXB-ReferenceImpl`
version. Maven's nearest-declaration-wins resolution means the project's own explicit `17.0.2`
dependency usually wins, but a `mvn dependency:tree` check is cheap insurance, not paranoia — the
specific removed methods (`getEGBlockLevelElts`, `getParagraphContent`, `getRunContent`) fail at
**runtime** (a `NoSuchMethodError`), not compile time, if a stale transitive jar shadows the
declared one in an unusual classloading order.
**Why it happens:** docx4j 17.0.0 removed methods rather than deprecating them.
**How to avoid:** Run `mvn dependency:tree -Dincludes=org.docx4j` after adding any new
docx4j-touching dependency this phase or later, and confirm exactly one docx4j version resolves.
**Warning signs:** `NoSuchMethodError` referencing `getEGBlockLevelElts`/`getParagraphContent`/
`getRunContent`/`ArrayListWml` at test time — a strong, specific signal of exactly this problem,
not a generic classpath issue.

### Pitfall 3: Font embedding — openhtmltopdf/PDFBox rejects OpenType (.otf), needs TrueType (.ttf)
**What goes wrong:** A logo or chrome design assumption about "any font file" breaks silently or
with a cryptic PDFBox exception if an `.otf` font is registered via `useFont()`.
**Why it happens:** `openhtmltopdf-pdfbox`'s embedded-font support rides on PDFBox's font
subsystem, which does not support OpenType (CFF-flavoured `.otf`) fonts for embedding — only
TrueType (`.ttf`).
**How to avoid:** This project doesn't currently plan to register any custom font (system default
sans-serif is the whole typography contract per the UI-SPEC, and no font upload feature exists),
so this is a **latent** pitfall, not an active one this phase. Record it so nobody adds a
"nicer PDF font" nice-to-have later without checking the file extension first.
**Warning signs:** N/A this phase — no font registration is in scope.

### Pitfall 4: `PdfBoxRenderer.cleanup()` / resource leaks on error paths
**What goes wrong:** openhtmltopdf's lower-level `PdfBoxRenderer` (reached if the adapter drops
below the `PdfRendererBuilder().toStream().run()` convenience API for any reason — e.g., to
reuse a layout pass) must have `cleanup()` called explicitly, including on exception paths, or
PDFBox font-cache/temp resources leak.
**How to avoid:** Prefer the `PdfRendererBuilder().withHtmlContent(...).toStream(os).run()`
one-shot convenience API (which manages its own lifecycle internally) rather than manually
obtaining a `PdfBoxRenderer` — nothing in this phase's requirements needs the lower-level API.
**Warning signs:** Rising memory/file-descriptor usage under repeated export load in a long-running
process — not something a short-lived test run will surface, worth a mental note for later
observability, not a blocking concern for this phase's scope.

### Pitfall 5: Bounded rendering only covers the Markdown-resolution step, not export
**What goes wrong:** `RenderService`'s timeout/collection-cap bounds (TMPL-05, D-04-11) apply to
*generation* (resolving Mustache + parsing to the AST). PDF/DOCX **export** re-parses the
already-bounded, already-stored Markdown on demand (D-05-05) — there is no *new* unbounded
collection at export time (the item/group counts are frozen into the stored Markdown text), but
the walk-and-render step itself (openhtmltopdf's layout pass, the new DOCX AST walker) has no
timeout of its own.
**Why it happens:** the existing bound was designed around Mustache/flexmark parse cost, not
PDF layout or OOXML tree construction cost.
**How to avoid:** For v1, this is an accepted-risk gap consistent with D-05-07's own "accepted
risk, recorded deliberately" pattern for parity — the collection cap already bounds how much
content export ever sees (it was bounded when the snapshot was generated), so pathological input
is not actually reachable through the normal generate→export path. Flag as an explicit **non-goal**
for this phase rather than silently building a second timeout wrapper around export — that would
be exactly the kind of speculative hardening CLAUDE.md's "no over-engineering" principle rules out
absent a demonstrated problem.
**Warning signs:** N/A — documented as an accepted, bounded-by-construction gap.

### Pitfall 6: Streaming vs. buffering PDF/DOCX out of Spring MVC
**What goes wrong:** Reaching for `StreamingResponseBody` (Spring's true streaming mechanism) adds
real complexity (a `Runnable`-shaped output-stream callback, exception handling that can no longer
set the HTTP status after the first byte is flushed) for no benefit at this document scale.
**Why it happens:** Confusing "PDF/DOCX generation" with "large file serving" — the two are not
the same problem here. A release-notes document for a realistically-sized release (bounded by the
same collection cap as generation) is at most a few hundred KB to a few MB — this project's own
existing `downloadLogo` pattern already returns `ResponseEntity<byte[]>` for exactly this class of
problem (a bounded, server-generated binary blob), and PDF/DOCX export is the same shape, just a
different generator.
**How to avoid:** Render into an in-memory `ByteArrayOutputStream` (`PdfRendererBuilder.toStream()`
and `WordprocessingMLPackage.save()` both accept an `OutputStream` directly — an in-memory buffer
works with neither library requiring code changes) and return `ResponseEntity<byte[]>`, matching
`downloadLogo`'s existing idiom exactly. Reconsider only if a future phase's real, measured
document sizes make this impractical — per CLAUDE.md's optimization guidance, that is a "measure
first" problem, not a v1 design constraint.
**Warning signs:** N/A — this is the recommended default, not a detected problem.

## Code Examples

### Registering the tables extension (both parse and render sides)
```java
// Source: flexmark-java Tables-Extension wiki (github.com/vsch/flexmark-java/wiki/Tables-Extension)
// Applied to the existing adapter's constructor — MustacheFlexmarkRenderAdapter.java:44-45
this.markdownParser = Parser.builder()
        .extensions(List.of(TablesExtension.create()))
        .build();
this.htmlRenderer = HtmlRenderer.builder()
        .extensions(List.of(TablesExtension.create()))
        .build();
```
The existing `neutralizeRawHtml` walk (lines 80-92) is unaffected — `TableBlock` and its children
are not `HtmlBlockBase`/`HtmlInlineBase` subtypes, so no change is needed there.

### The fixed AST vocabulary the new DOCX walker must handle

Derived from what `AdfFlattener` emits (comments/descriptions) plus what template authors can add
in body Markdown (per this phase's explicit out-of-scope note — do not build support for anything
beyond this list):

| flexmark node type | Source | OOXML target |
|---|---|---|
| `Paragraph` | body text, ADF-flattened description paragraphs | `P` (run of `R`/`Text`) |
| `Heading` (levels 1-6, template authors use `##`/`###`) | body headings (`## {{groupName}}`, `### {{key}} \| {{title}}`) | `P` with a `pStyle` referencing Word's built-in `Heading1`..`Heading6` style |
| `StrongEmphasis` (bold) | body/description | `RPr` with `b` |
| `Emphasis` (italic) | body/description | `RPr` with `i` |
| `Code` (inline code) | body/description | `RPr` with a monospace `rFonts` override |
| `FencedCodeBlock` | body only (ADF flattening doesn't emit code fences) | `P` per line, monospace `rFonts`, optionally shaded via `PPr`/`shd` |
| `BulletList` / `OrderedList` / `ListItem` | body only | `P` with `numPr` referencing a `numbering.xml` abstract-num definition (bullet vs. decimal) |
| `TableBlock` / `TableHead` / `TableBody` / `TableRow` / `TableCell` (from `flexmark-ext-tables`) | body only | `Tbl` / `Tr` / `Tc` |
| `Text` (including neutralized raw-HTML text — see `neutralizeRawHtml`) | everywhere | plain `R`/`Text` run content |

**Explicitly not needed** (confirmed against `AdfFlattener` and the project's template-authoring
constraints): images embedded *inline in body Markdown* (the only image this project handles is
the chrome logo, a structured field — TMPL-08/09 — not a body-Markdown `![]()`), blockquotes,
horizontal rules, footnotes, definition lists, strikethrough, or any other flexmark extension.
Confirm this list against the actual starter templates and `PlaceholderCatalog` before
implementation — this table is this research's best evidence, not a substitute for re-checking
the seeded starters' bodies once D-05-01's real runbook/approval content is authored.

## Runtime State Inventory

Not applicable — this is new capability (document generation, snapshots), not a rename, refactor,
or migration of existing state. Skipped per the "greenfield phases" guidance.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `flexmark-docx-converter` has not shipped a docx4j-17-compatible release as of this research session | Alternatives Considered | Low — even if one now exists, the "Don't Hand-Roll" analysis above (small fixed vocabulary, chrome dominates the DOCX work regardless) means hand-rolling remains reasonable either way; re-verify only if the planner wants to revisit this specific tradeoff |
| A2 | The seeded starter templates (post-D-05-01 rewrite, with real runbook/approval content) will not introduce Markdown constructs outside the fixed vocabulary table above (e.g., no blockquotes, no nested lists beyond one level) | Code Examples | Medium — if the real starter content (written as part of this phase, per D-05-01's "planner note") uses a construct outside this list, the DOCX walker needs one more node-type branch; cheap to add, but the plan should account for verifying the actual starter bodies before finalizing the walker's node-type list, not just this research's inference from `AdfFlattener` |
| A3 | Release-notes-scale documents (bounded by the existing collection cap) stay well within openhtmltopdf/docx4j's practical memory/time budgets without a dedicated export-side timeout | Pitfall 5 | Low-Medium — untested against this project's actual `app.template.render.max-collection-size` value at real openhtmltopdf/docx4j scale; if generation later proves slow at the cap's upper bound, an export-side timeout (mirroring `RenderService.renderBounded`) is the additive fix, not a redesign |

## Open Questions

1. **Does `DocumentType` need its own enum, or should this phase reuse `TemplateDocumentType`?**
   - What we know: `TemplateDocumentType` already exists (RELEASE_NOTES/RUNBOOK/APPROVAL, inferred
     from `TemplateDetailRoute`'s "document-type subtitle" and `04-UI-SPEC.md`'s three starter
     templates). A document snapshot is generated *from* a template of a given type.
   - What's unclear: whether `DocumentSnapshot` should hold a direct `TemplateDocumentType`
     reference (simplest — no new enum) or whether the domain wants a conceptually separate
     `DocumentType` that happens to have the same three values (more "correct" DDD-wise, more
     ceremony).
   - Recommendation: reuse `TemplateDocumentType` directly. Introducing a parallel enum with
     identical values for a conceptual distinction nothing in this phase's requirements asks for
     is the kind of unnecessary indirection CLAUDE.md's engineering principles rule out. Confirm
     at plan time, not research time — this is a two-line decision, not a research gap.

2. **Exact page-size and margin defaults for the PDF/@page rule.**
   - What we know: openhtmltopdf supports `size: A4` / `size: letter` etc. The reference document
     (`Orchstro 4.2.0 Release Notes.docx`) is presumably Word's own default (Letter or A4
     depending on the authoring locale) — not confirmed from the docx4j/openhtmltopdf research
     itself.
   - What's unclear: which page size the release manager's organisation expects. Not a technical
     unknown — a product/content decision.
   - Recommendation: default to A4 (matches the evisionegypt.atlassian.net org's apparent
     Egypt/EU locale) unless the release manager states otherwise at plan or execution time; this
     is a one-line CSS constant, trivially changed later.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Maven Central reachability | Adding openhtmltopdf/docx4j/flexmark-ext-tables deps | ✓ | — | — verified all three resolve at the pinned versions this session |
| Java 25 toolchain | Build | ✓ (installed at `C:\Users\ucs\.jdks\jdk-25`) | 25.0.4 | — |
| Local `java`/`mvn` on PATH | Build | ⚠ default `java -version` on this machine resolves to JDK 17, not 25 | 17.0.16 | **Known existing landmine, not new this phase** — every Maven invocation needs `JAVA_HOME` pointed at the JDK 25 install explicitly (per prior-session memory); does not block this phase, just needs the same care every other phase's build commands already require |

No new external services (no new Jira/ADO calls, no new database, no new runtime dependency
beyond the three JVM libraries above) — this phase is otherwise self-contained relative to the
existing environment.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-------------------|
| V5 Input Validation | Yes | The Markdown *content* reaching both new exporters is already-validated, already-Mustache-resolved text that passed TMPL-04's save-time render (D-05-05's snapshot only exists post-validation) — no new untrusted-template-injection surface is introduced. The one genuinely new input this phase adds is the Jira `component` field (D-05-02), resolved through the existing JIRA-04 name-based field-metadata mechanism, same trust boundary as every other ticket field already handled by `JiraRestTicketAdapter` |
| V6 Cryptography | No | No new cryptographic material this phase (no new secrets, no new credential handling) |
| V12/V13-class (File/Resource handling — not a numbered ASVS 4.x category by that exact name, called out because this phase newly generates binary file responses) | Yes | Content-Disposition filename sanitization (see Pattern 4's Anti-Pattern) — strip/replace characters outside a safe filename charset before interpolating release name/version into the header; set `Content-Type` explicitly (`application/pdf` / the OOXML wordprocessing MIME type) and `X-Content-Type-Options: nosniff`, mirroring the existing `downloadLogo` pattern exactly |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|-----------------------|
| HTTP response header injection via a release name/version containing CR/LF or quote characters, reflected into `Content-Disposition` | Tampering | Sanitize/allowlist the filename component before setting the header (new exposure this phase — see Pattern 4) |
| Resource exhaustion via a pathologically large release generating an oversized PDF/DOCX | Denial of Service | Already bounded transitively: the stored Markdown was itself produced under `RenderService`'s existing collection cap (TMPL-05) at generation time; export re-parses only that already-bounded text — see Pitfall 5 for why no *new* bound is needed |
| XXE / malicious OOXML on the DOCX **write** path | Tampering / Information Disclosure | Not applicable — docx4j is used here purely to *generate* a new `.docx` from trusted, already-validated content; no untrusted `.docx`/`.xml` is ever parsed by this phase. (Contrast with a hypothetical future "import a DOCX template" feature, which is explicitly out of scope — TMPL-08 uses structured chrome fields specifically to avoid that class of feature.) |
| SVG/XML-based image injection via the chrome logo | Spoofing / Tampering | Already mitigated by Phase 4's `LogoValidator` (content-sniffed raster-only validation, SVG rejected — TMPL-09) at upload time; this phase only re-embeds already-validated bytes, introducing no new image-handling surface |

## Sources

### Primary (HIGH confidence)
- `src/main/java/com/evision/releasemgmt/adapters/render/MustacheFlexmarkRenderAdapter.java` — existing render pipeline, `parseMarkdown`'s package-private contract
- `src/main/java/com/evision/releasemgmt/application/RenderService.java`, `ReleaseViewAssembler.java`, `RenderContextMapper.java` — existing bounded-render and view-assembly pipeline
- `src/main/java/com/evision/releasemgmt/adapters/web/TemplateController.java:155-168` — existing `ResponseEntity<byte[]>` binary-download pattern
- `src/main/java/com/evision/releasemgmt/adapters/jira/JiraRestTicketAdapter.java`, `domain/jira/TicketDetail.java` — existing Jira field-fetch pattern (`fixVersions` as the direct analog for `component`)
- `D:/IdeaProjects/ReleaseManagement/frontend/node_modules/markdown-it/dist/markdown-it.mjs` (installed package source, read directly, lines ~2377-2382 and ~3410-3509) — confirms `table` is unconditionally registered on `ParserBlock` and that the `default` preset applies no rule restriction, while `commonmark` explicitly excludes `table` from its block-rule allowlist
- Maven Central `maven-metadata.xml` for `openhtmltopdf-pdfbox`, `docx4j-JAXB-ReferenceImpl`, `flexmark-docx-converter`, `flexmark-ext-tables` (fetched this session) — current release versions
- `pom.xml` (current state — confirms openhtmltopdf/docx4j/flexmark-ext-tables are not yet dependencies)

### Secondary (MEDIUM confidence)
- [flexmark-docx-converter DocxRenderer.java](https://github.com/vsch/flexmark-java/blob/master/flexmark-docx-converter/src/main/java/com/vladsch/flexmark/docx/converter/DocxRenderer.java) — public API shape (accepts pre-parsed `Node`), fetched via WebFetch
- [flexmark-docx-converter pom.xml](https://raw.githubusercontent.com/vsch/flexmark-java/master/flexmark-docx-converter/pom.xml) — confirms `docx4j-JAXB-ReferenceImpl:11.4.9` and Log4j 2 transitive dependencies
- [docx4j 17.0.0 release announcement](https://www.docx4java.org/forums/announces/docx4j-17-0-0-and-importxhtml-17-0-0-released-t3171.html) — confirms the specific breaking API changes (`getContent()`, `ArrayListDocx4j`, JAXB `Child` package move)
- [openhtmltopdf Page-features wiki](https://github.com/danfickle/openhtmltopdf/wiki/Page-features) — `@page`/`@page :first`, margin boxes, `counter(page)`/`counter(pages)`, documented limitations (`@page:last` unsupported, named-page blank-page quirk, top-center/top-right margin-box positioning quirk in issue #134)
- [openhtmltopdf Fonts wiki](https://github.com/danfickle/openhtmltopdf/wiki/Fonts) and issue #683 (font reuse) — `useFont()` shape, TrueType-only embedding constraint
- [openhtmltopdf issue #355](https://github.com/danfickle/openhtmltopdf/issues/355) — confirms `PdfBoxRenderer`/`PdfRendererBuilder` are not designed for cross-call reuse by default
- [docx4j forum: "Example header/footer, cover page with page numbers"](https://www.docx4java.org/forums/docx-java-f6/example-header-footer-cover-page-with-page-numbers-t1621.html) — `titlePg`, `HdrFtrRef.FIRST`/`.DEFAULT`, `CTSimpleField` PAGE/NUMPAGES pattern
- [docx4j BinaryPartAbstractImage javadoc/source](https://github.com/plutext/docx4j/blob/master/docx4j-core/src/main/java/org/docx4j/openpackaging/parts/WordprocessingML/BinaryPartAbstractImage.java) — image embedding mechanism (`createImagePart`, relationship via `addTargetPart`)
- Atlassian Jira Cloud REST API v3 community sources — `fields.components` as an array of `{id, name}` objects (component field shape); official schema page (`developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-issue-fields/`) referenced but not independently re-fetched this session — MEDIUM rather than HIGH because the exact official page content wasn't re-verified byte-for-byte, only corroborated by multiple community sources plus the existing `fixVersions` handling in this codebase (which is the same shape: an array of name-bearing objects)

### Tertiary (LOW confidence)
- None — every finding above either came from a primary source (codebase, Maven Central, an official repo/forum) or was cross-checked against a second source before inclusion.

## Metadata

**Confidence breakdown:**
- Standard stack (library choice, versions): HIGH — versions independently reverified against Maven Central this session, not assumed from CLAUDE.md
- flexmark-docx-converter incompatibility finding: HIGH — based on reading the actual `pom.xml` (docx4j 11.4.9) and the actual docx4j 17.0.0 release notes' breaking-change list, not inference
- Table-parsing gap finding: HIGH — based on reading the actual installed `markdown-it` bundle source and flexmark's documented extension model, not inference
- Chrome mechanism code shapes (openhtmltopdf CSS, docx4j titlePg/CTSimpleField): MEDIUM — corroborated by an official wiki (openhtmltopdf) and multiple community forum threads (docx4j), but no single official docx4j "cookbook" page was found; the exact method signatures should be spot-checked against docx4j 17.0.2's actual Javadoc during implementation, not copied verbatim from a forum thread written against an older version
- Jira component field shape: MEDIUM-HIGH — matches the existing `fixVersions` array-of-objects pattern already implemented in this codebase, corroborated by multiple community sources
- Pitfalls (thread-safety, font embedding, streaming): MEDIUM-HIGH — openhtmltopdf's own GitHub issues are primary-ish evidence of maintainer intent, not third-party speculation

**Research date:** 2026-08-10
**Valid until:** 30 days for the library-version findings (stable, slow-moving libraries); the
flexmark-docx-converter/docx4j-17 incompatibility finding should be re-checked only if a new
flexmark-docx-converter release appears — otherwise it does not decay
