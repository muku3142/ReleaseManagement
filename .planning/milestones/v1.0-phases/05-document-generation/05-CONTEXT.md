---
phase: 05-document-generation
status: context-gathered
gathered: 2026-08-10
requirements: [DOC-01, DOC-02, DOC-03, DOC-04, DOC-05, DOC-06, DOC-07, DOC-08, DOC-09, UX-01]
depends_on: [03-release-assembly, 04-template-authoring]
---

# Phase 05: Document Generation - Context

**Gathered:** 2026-08-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Turn Phase 4's templates plus Phase 3's release data into actual documents: generate release notes,
deployment runbooks and approval documents; retain each as an immutable snapshot; export PDF and
DOCX; and surface which paperwork is missing or stale across all releases.

**Scope was corrected during this discussion.** `.planning/ROADMAP.md` listed only DOC-01..07;
`REQUIREMENTS.md` maps **DOC-08** and **DOC-09** here too, and **UX-01** has been deferred into this
phase twice (D-03-04, then D-04-04). The roadmap now carries all ten plus three added success
criteria (commit `0bbccbe`).

**Not delivered here:** Azure DevOps commit/PR data (Phase 6 — `CommitView`/`PullRequestView` exist
and resolve empty, per D-04-18); the environment ladder, promotions and promotion history (Phase 7);
per-release runbook/approval values (v2 — see D-05-01).

</domain>

<decisions>
## Implementation Decisions

### Runbook and approval content

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

### Ticket fields and grouping

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

### Document snapshots

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

### PDF/DOCX parity

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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project scope, requirements and locked decisions
- `.planning/PROJECT.md` — §Key Decisions: *generated documents stored as immutable snapshots with
  template version pinned*, and *templates see a dedicated flattened view-model, never domain or JPA
  entities*. §Constraints for the bounded-rendering rule
- `.planning/REQUIREMENTS.md` — DOC-01..DOC-09 and UX-01. **§"Runbook and approval content (revised
  2026-08-10)"** records why DOC-03/DOC-04 were reworded and what it costs. **§"Document chrome
  (added 2026-07-31)":211** is the DOC-08 background: it states outright that PDF and DOCX *share no
  mechanism for chrome*, which is why D-05-07's gap is where it is
- `.planning/ROADMAP.md` §Phase 5 — goal plus **eight** success criteria (three added 2026-08-10)
- `CLAUDE.md` §Technology Stack — `openhtmltopdf-pdfbox` for PDF, `docx4j` for DOCX, `flexmark` for
  Markdown; §Alternatives Considered explains docx4j over Apache POI

### Prior phase decisions this phase must respect
- `.planning/phases/04-template-authoring/04-CONTEXT.md` — **D-04-04** (one AST feeds both renderers
  — the whole basis of D-05-07), **D-04-13** (free deletion → why D-05-05 copies the template),
  **D-04-02/D-04-17** (grouping and the deferred fields D-05-02 picks up), **D-04-03** (release-only
  root context), **D-04-11** (`RenderService` owns the bounds generation inherits), **D-04-18**
  (CommitView/PullRequestView exist and resolve empty until Phase 6)
- `.planning/phases/03-release-assembly/03-CONTEXT.md` — **D-03-06** (materialised ticket snapshots),
  **D-03-07** (`last_refreshed_at`, the basis of D-05-09), **D-03-10** (one-way freeze, why D-05-03
  refuses to backfill), **D-03-04** (why UX-01 keeps moving)
- `.planning/phases/02-jira-ticket-browsing/02-CONTEXT.md` — **D-02-02:30** fixes the ticket field set
  D-05-02 extends; **the DOC-02 tension note at :43** is the debt this phase settles
- `.planning/phases/01-foundation-authentication/01-CONTEXT.md` — **D-03** (acting user on every state
  change), **D-14** (nothing reaches an external system while no user is signed in — why D-05-03
  declines a migration-time Jira call), **D-17** (Java 25)

### Code this phase builds directly on
- `src/main/java/com/evision/releasemgmt/application/RenderService.java` — owns the execution timeout
  and collection cap; generation MUST go through it, not around it
- `src/main/java/com/evision/releasemgmt/adapters/render/MustacheFlexmarkRenderAdapter.java` —
  **`parseMarkdown` is package-private specifically so this phase can walk the single `Document` to
  both renderers** (D-04-04). Read its `neutralizeRawHtml` comment before touching the AST
- `src/main/java/com/evision/releasemgmt/application/ReleaseViewAssembler.java` — builds `ReleaseView`;
  D-05-02's component field and D-05-04's dimension land here
- `src/main/java/com/evision/releasemgmt/domain/template/PlaceholderCatalog.java` — hand-written
  catalog with a reflection drift test; a new view-model field must be added here too or that test fails
- `src/main/resources/db/migration/` — `V1`..`V4`; this phase adds **`V5`**
- `src/main/java/com/evision/releasemgmt/config/TemplateSeeder.java` +
  `src/main/resources/templates/starter-*.md` — where D-05-01's runbook and approval content lives
- `src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java` — extend for
  generation/export failures; do not invent a parallel error scheme
- `frontend/src/routes/ReleasesRoute.tsx` — D-05-08 extends this with readiness columns
- `frontend/src/components/TemplatePreview.tsx` — the client renderer; document preview should reuse
  or mirror it rather than becoming a third implementation
- `docs/status-responses.md` — the full status/reason contract and its known gaps

### Source material
- `docs/templates/orchstro-release-notes.md` — a real customer release-notes document mapped onto the
  view-model, with exactly what does and does not reproduce. **The strongest available evidence for
  what DOC-01/DOC-02 must actually produce**

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **The whole render pipeline already exists and is tested** — `RenderEnginePort`,
  `MustacheFlexmarkRenderAdapter`, `RenderService` (bounded), `ReleaseViewAssembler`,
  `TemplateScanner`, `PlaceholderCatalog`. This phase consumes them; it should not rebuild any of it.
- **`parseMarkdown` returns the flexmark `Document`** and is deliberately package-private for this
  phase's use — that single AST is the entire mechanism behind D-05-07.
- **Three seeded starter templates** already exist and are validated at startup through the normal
  create/save/activate path, so a broken starter fails startup loudly.
- **`GlobalExceptionHandler` + `reason` contract** — 25 reason codes today; extend it.
- **Client-side preview** (`TemplatePreview.tsx`, mustache + markdown-it) already renders body and
  chrome with the unknown-placeholder marker.

### Established Patterns
- **Hexagonal boundaries, ArchUnit-enforced.** `com.samskivert` (jmustache) and `com.vladsch`
  (flexmark) are confined to `adapters.render` — openhtmltopdf and docx4j must be confined the same
  way, behind a port, never imported from `domain` or `application`.
- **Flyway forward-only**, `ddl-auto: validate` — entity and migration must agree or startup fails.
- **`src/test/resources/application.yml` shadows main's** — every new property goes in BOTH files.
  This has broken the build twice.
- **Jackson 3 (`tools.jackson.*`)**; `com.fasterxml.jackson.databind` does not resolve.
- **`tsc -b` is incremental** and can report a stale failure on the first run after a branch change —
  run it twice before believing it.
- Testing: Testcontainers PostgreSQL 18, WireMock for Jira, `@WebMvcTest`, Vitest under
  `frontend/tests/` (never co-located).

### Integration Points
- Generation reads a `Release` (local, no Jira call) and the **active** `Template` for the document
  type, renders via `RenderService`, and writes a snapshot row.
- `/releases` gains DOC-09 columns (D-05-08). `03-CONTEXT.md` anticipated this.
- **Phase 7 consumes this phase's snapshots** for AUDIT-02 ("every document generated for a release,
  including superseded revisions") — D-05-06's keep-everything model is what makes that possible.

</code_context>

<specifics>
## Specific Ideas

- **The reference document is real and available:** `Orchstro 4.2.0 Release Notes.docx`. Its
  structure — cover, running header, `Orchstro | Page X of Y` footer, TOC, Release Summary,
  component-grouped ticket sections, Updated Services table — is the concrete target for what
  "correct output" looks like. `docs/templates/orchstro-release-notes.md` holds the mapping.
- Two features of that document are **known not reproducible** and should not be silently attempted:
  a Word-generated **table of contents** (no Markdown construct, neither renderer emits one), and the
  `Component | Summary` entry line, which D-05-02 partly resolves.
- Live tenant for verification: `https://evisionegypt.atlassian.net`, project key **`CNV`**.

</specifics>

<deferred>
## Deferred Ideas

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

</deferred>

---

*Phase: 5-Document Generation*
*Context gathered: 2026-08-10*
