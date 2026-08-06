---
phase: 04-template-authoring
status: context-gathered
gathered: 2026-08-06
requirements: [TMPL-01, TMPL-02, TMPL-03, TMPL-04, TMPL-05, TMPL-06, TMPL-08, TMPL-09]
depends_on: [03-release-assembly]
---

# Phase 04: Template Authoring - Context

**Gathered:** 2026-08-06
**Status:** Ready for planning

<domain>
## Phase Boundary

The roadmap calls this the highest-risk design decision in the project (`.planning/ROADMAP.md:105`),
and it is the only phase whose output every later phase renders through. What ships:

1. **The view-model** — a flattened, fixed, documented set of types the template sees. Never a
   domain record, never a JPA entity (locked in `PROJECT.md` §Key Decisions).
2. **The render pipeline** — jmustache over the view-model, then flexmark over the resulting
   Markdown, bounded by a timeout and a collection cap.
3. **Template persistence** — a `V4` migration, many named templates per document type with one
   active, plus the structured chrome fields and a per-template logo.
4. **The authoring screen** — source editor, placeholder catalog, live preview against a real
   release's data, save-time validation.

**Not delivered here** (later phases): PDF and DOCX export and any real pagination (Phase 5,
DOC-05/DOC-06/DOC-08); document generation and immutable snapshots (Phase 5, DOC-01..04, DOC-07);
grouping by component/epic/label (Phase 5, DOC-02 — only issue-type grouping ships here); Azure
DevOps commit/PR data (Phase 6); promotion and audit (Phase 7).

**Phase 5 consumes this directly.** Every decision below is a constraint on it, not a suggestion.

</domain>

<decisions>
## Implementation Decisions

### View-model and placeholder surface

- **D-04-01 — One unified `{{#items}}` collection, not separate ticket and manual lists.**
  Every release item — ticket-backed and manual — is rendered through one `ItemView` with a
  common shape plus an `isManual` flag. Ticket-only fields (status, assignee, resolution,
  comments) are empty for manual items and the placeholder catalog must say so explicitly.

  *Why:* REL-08 requires manual items to appear "alongside ticket-backed items" in generated
  documents. Two collections would let a template author loop only `{{#tickets}}` and silently
  drop every config change and database script from a runbook — a whole-item version of exactly
  the silent-blanking failure TMPL-04 exists to prevent.

- **D-04-02 — `{{#groups}}` ships now, hard-wired to issue type. No per-template dimension field.**
  The view-model exposes a pre-computed `{{#groups}}{{groupName}}{{#items}}…{{/items}}{{/groups}}`
  alongside the flat `{{#items}}`. Mustache is logic-less and cannot group, so grouping is always
  a view-model concern, never a template one. DOC-02's other dimensions (component, epic, label)
  are Phase 5.

  *Why:* issue type is DOC-02's stated default, and it is the only dimension currently available —
  component, epic and label are not fetched from Jira at all (D-02-02). Shipping the group *shape*
  now means Phase 5 adds dimensions to an existing mechanism instead of reshaping the view-model
  after templates already exist.

  **Planner note:** manual items have a category, not an issue type. See D-04-14.

- **D-04-03 — Root context is the release and nothing else.**
  No `{{generatedAt}}`, no `{{generatedBy}}`, no ticket URLs, no `JiraSiteConfig` dependency. The
  only dates a template can reach are the release's own `createdAt`, `frozenAt` and
  `lastRefreshedAt`.

  *Why:* the release manager's reasoning, confirmed against the specific consequence: a document is
  about the release, so when it happened to be rendered is metadata about the PDF, not a fact about
  the release. Phase 5 records the generation timestamp on the document snapshot (DOC-07) where it
  is queryable and immutable, rather than baking it into rendered prose. This keeps the view-model
  bound to exactly one source object, which is what makes it exhaustively documentable.

  **This applies to chrome fields too** — confirmed explicitly. A footer reading "Generated 6 Aug
  2026" is not expressible; a footer reading "Frozen {{release.frozenAt}}" is.

  **Consequence for Phase 5:** if DOC-04's approval document turns out to need a generation date in
  its rendered text, that is a view-model change and must come back through discussion — not a
  quiet addition at implementation time.

### Template body format and render pipeline

- **D-04-04 — Template body is Markdown + Mustache. Render Mustache first, then parse Markdown.**
  jmustache resolves placeholders against the view-model; flexmark parses the resulting Markdown
  into one AST; Phase 5 walks that single AST to both XHTML→PDF (openhtmltopdf) and OOXML
  (docx4j).

  *Why:* one AST feeding both renderers is the strongest available structural guarantee of DOC-08's
  "visually equivalent, not merely both present". It is also already the project's vocabulary —
  `AdfFlattener` (`adapters/jira/AdfFlattener.java:76`) *already emits Markdown*: `**strong**`,
  `*em*`, backtick code, fenced code blocks, `- ` list items. A ticket description therefore drops
  into a Markdown template as native Markdown with no conversion step. HTML+Mustache was rejected
  because arbitrary HTML is where the PDF and DOCX renderers realistically diverge; a structured
  section model was rejected as far more UI for capped expressiveness.

- **D-04-05 — Interpolate raw; block raw HTML at the flexmark parser instead.**
  jmustache's HTML escaper is **turned off** — it is the wrong escaper for a Markdown pipeline and
  would turn an ampersand in a ticket summary into a visible `&amp;`. The defence moves one layer
  down: flexmark is configured with raw-HTML parsing **disabled**, so any `<` — in interpolated
  ticket text *or* written directly into the template body — renders as literal text and can never
  become a tag.

  *Why:* one defence in one place, and it covers the case HTML-escaping the placeholders would have
  missed entirely (HTML typed into the template itself). Escaping Markdown metacharacters instead
  was rejected because it would render `AdfFlattener`'s `**bold**` as literal asterisks, undoing the
  flattener's work at the exact point it should pay off.

- **D-04-06 — Allowed Mustache subset: variables, sections, inverted sections, comments. Nothing else.**
  `{{x}}`, `{{#x}}…{{/x}}`, `{{^x}}…{{/x}}`, `{{! comment }}`. **No partials** (`{{> name}}`), **no
  lambdas**, **no custom delimiters**, and no dotted traversal beyond the documented view-model
  paths. Anything else is rejected at save time as an unknown construct.

  *Why:* TMPL-02 requires no expression language, no method invocation and no arbitrary property
  traversal. Relying on jmustache's defaults would rest that property on a library's behaviour
  rather than on something this codebase asserts and tests — unacceptable for what the roadmap calls
  the project's highest-risk decision. Partials were rejected specifically because a partial loader
  is a file-access surface that would have to be locked down, for a fragment-sharing need nobody has
  expressed. Inverted sections are kept because "no tickets in this group" is a real authoring need
  and inverted sections are the logic-less way to express it.

### Validation, preview and bounds

- **D-04-07 — Unknown placeholders warn; they do not block the save.**
  Saving runs the TMPL-04 validation render against a sample context and records warnings on the
  template. The save succeeds either way.

  > **Deviation from TMPL-04's wording, chosen deliberately.** The requirement says unknown
  > placeholders are reported "as errors rather than silently blanking them later". The release
  > manager was shown that this option reintroduces deferred failure and chose it anyway, on the
  > basis that authoring should not be blocked. **D-04-08 is what preserves the requirement's
  > actual intent** — the guarantee moves from save-time rejection to render-time visibility.
  > Verification of TMPL-04 must test D-04-08's marker, not a rejected save.

- **D-04-08 — An unknown placeholder renders as a conspicuous marker, e.g. `[[unknown: ticket.compnent]]`.**
  In the live preview and in every Phase 5 generated document. jmustache is configured with a
  missing-key handler that emits the marker, **not** a null/empty default.

  *Why:* this is what makes D-04-07 safe. TMPL-04's stated fear is silent blanking; a loud marker at
  every downstream point is a stronger guarantee than a warning banner nobody re-reads, and unlike
  leaving the raw `{{token}}` in place it reads as a flagged defect rather than as a broken tool.

- **D-04-09 — The live preview renders client-side, in the browser.**
  TMPL-06's preview is a JS Mustache + Markdown render against release data fetched from the API.
  Instant, no round trip per keystroke.

- **D-04-10 — Accepted risk, recorded deliberately: no preview/generation parity mechanism.**
  The browser renderer and the backend jmustache+flexmark pipeline are two independent
  implementations that can diverge in escaping, missing-key behaviour, Markdown extensions and
  section semantics. **No golden-fixture parity test and no server verify action will be built.**
  The mitigation is library choice alone: pick a Mustache-spec-conformant JS implementation and a
  CommonMark-conformant Markdown renderer, and configure them to match D-04-05 (raw HTML disabled)
  and D-04-08 (unknown-placeholder marker) as closely as the libraries allow.

  *Why recorded rather than argued:* the release manager was shown that the first divergence would
  be discovered by someone comparing a PDF against a preview they remember, with no test to point
  at, and accepted it. **Planner note:** the cheap upgrade path if this bites is a shared fixture
  set asserted by both `mvn test` and `vitest` — additive, no redesign. Do not build it now.

  **This does not remove the backend render path.** Save-time validation (TMPL-04) is a *server*
  render against the sample context, so jmustache + flexmark are built and tested in this phase
  regardless. Only the live preview is client-side.

- **D-04-11 — TMPL-05's bounds live in one render service used by every caller.**
  A single `RenderService` owns both a wall-clock execution timeout and a hard cap on how many items
  any section will iterate. Save-time validation goes through it now; Phase 5's document generation
  goes through the same one. Breaching either surfaces as a `ProblemDetail` with a machine-readable
  `reason`, per the Phase 01 error taxonomy — not a generic 500.

  *Why:* mirrors the pattern already proven in this codebase — `AdfFlattener` bounds itself with
  `MAX_NODES` / `MAX_DEPTH` (`adapters/jira/AdfFlattener.java:7`). One owner means Phase 5 cannot
  accidentally ship an unbounded generation path.

### Template identity, lifecycle and seeding

- **D-04-12 — Many named templates per document type; exactly one active per type.**
  Document type is a fixed enum (`RELEASE_NOTES`, `RUNBOOK`, `APPROVAL`). The release manager can
  keep several named templates of each type and mark one active; Phase 5's generation uses the
  active one, so it needs no template picker.

  *Why:* gives a safe way to try a template change without destroying the working one — which is
  the practical need behind TMPL-07 (version history), and TMPL-07 itself is explicitly v2 in
  `REQUIREMENTS.md`. **Planner note:** "exactly one active per type" is an invariant, not a
  convention — enforce it in the database (a partial unique index on `(document_type)` where
  `active`) rather than only in service code.

- **D-04-13 — Templates are freely editable and freely deletable. No delete guard, no soft-delete.**
  Editing is in place with no versioning. Deleting a template that has already generated documents
  is permitted.

  *Why:* Phase 5's DOC-07 stores the *rendered content* on the document snapshot, so deleting a
  template destroys nothing readable — every generated document remains complete and correct.

  > **Known cost, accepted.** "Which template produced this document" becomes unanswerable once
  > that template is deleted, which is some of what pinning a template version in DOC-07 was for.
  > This diverges from Phase 3's D-03-11 shape (hard-delete what has no history, refuse what does).
  > **Planner note for Phase 5:** the document snapshot should therefore store the template's *name
  > and body* at generation time, not merely a foreign key — a dangling FK to a deleted template is
  > the failure this decision makes possible.

- **D-04-14 — Seed three working starter templates, one active per type.**
  Not skeletons — realistic, complete release-notes, runbook and approval templates that already
  exercise `{{#groups}}`, `{{#items}}`, inverted sections and the chrome fields.

  *Why:* the release manager learns the placeholder vocabulary by editing something real rather than
  reading a catalog, and Phase 5's DOC-01..DOC-04 have something to render on day one instead of
  waiting for someone to hand-write a template. The starters double as the end-to-end fixture that
  proves the pipeline. **Planner note:** they are shipped artefacts — a view-model change that
  invalidates a starter is a change that must update the starter too, and a test should assert every
  seeded template validates clean against the current catalog.

### Document chrome and logo

- **D-04-15 — Chrome fields are stored, placeholder-validated, and previewed as an approximation.**
  The TMPL-08 structured fields (cover on/off, cover title, cover subtitle, header text, footer
  text, page-number toggle) persist on the template, and each field's `{{ }}` placeholders go
  through the same validation and the same view-model as the body — no second placeholder set, no
  new expression surface, per the locked decision in `REQUIREMENTS.md` §Document chrome.

  The preview renders the **cover page as a block** at the top of the pane (title, subtitle, logo,
  resolved against real release data) and header/footer text as fixed strips. **Page numbering
  renders as a literal placeholder**, because page count is unknowable without pagination.

  > The preview is deliberately approximate. Nothing paginates until Phase 5, so the preview must
  > not be treated as a specification of PDF or DOCX layout, and DOC-08 parity cannot be verified
  > from it.

- **D-04-16 — One logo per template, stored in Postgres as bytes.**
  The image lives in a column (or a small table keyed by template), not on a filesystem. Raster only,
  SVG rejected, type validated by **content** rather than extension, with byte-size and pixel-dimension
  caps — all four already locked in `REQUIREMENTS.md` §Document chrome security note.

  *Why:* single-jar deployment. No volume to provision, no separate backup, and the logo travels
  with a database dump. The mandated size cap is what keeps rows small.

### Claude's Discretion

The release manager did not select these two areas. Both are recorded with reasoning so they can be
overridden at plan time.

- **D-04-17 — `TicketItemView` exposes only the fields already fetched. Component, epic, label and
  priority are NOT added in this phase.**
  D-02-02 deliberately excluded them; they are not fetched from Jira and not columns on
  `release_ticket_item`. Adding them here would mean extending the Jira field selection, extending
  the materialised snapshot, a `V4` column migration *and* a backfill decision for releases already
  assembled — for placeholders no document can group by until Phase 5 anyway.

  D-04-02 is what makes this safe: grouping ships hard-wired to issue type, so nothing in this
  phase needs the missing fields. **The DOC-02 tension flagged in `02-CONTEXT.md:43` is hereby
  carried to Phase 5 in full** — Phase 5 must extend the Jira fetch, the snapshot and the
  view-model together, and `02-CONTEXT.md`'s "revisit at `/gsd-discuss-phase 5` at the latest" is
  now a hard commitment, not a suggestion.

- **D-04-18 — `CommitView` and `PullRequestView` are defined now, always empty.**
  Both types exist in the view-model and appear in the placeholder catalog from day one, resolving
  to empty collections until Phase 6 populates them.

  *Why:* TMPL-04 mandates that the validation sample context include "a ticket with no linked code".
  If the types do not exist, that requirement cannot be satisfied and templates authored in Phase 4
  cannot reference code detail at all — meaning every template gets rewritten in Phase 6. Defining
  them now costs two records and two catalog entries, and D-04-06's inverted sections make the empty
  case authorable today: `{{^commits}}No linked code{{/commits}}` renders correctly before Azure
  DevOps exists. Phase 6 then only populates data — it does not reshape the view-model.

  **Planner note:** the placeholder catalog must mark these as "always empty until Phase 6" so the
  author is not confused by a placeholder that never resolves.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project scope, requirements and locked decisions
- `.planning/PROJECT.md` — §Constraints (template source is untrusted input; no expression
  language, no method invocation, no arbitrary property traversal; bounded execution and collection
  size). §Key Decisions holds the two non-negotiables this phase implements: *logic-less engine
  (jmustache), never FreeMarker/Pebble/Thymeleaf/Handlebars* and *templates see a dedicated
  flattened view-model, never domain or JPA entities*
- `.planning/REQUIREMENTS.md` — TMPL-01..TMPL-06, TMPL-08, TMPL-09. **§"Document chrome (added
  2026-07-31)":211** is mandatory reading: it locks structured chrome fields over free-form chrome
  templates, records the PDF/DOCX chrome-mechanism split that DOC-08 exists to catch, and states the
  raster-only / no-SVG / validate-by-content rule D-04-16 implements. **TMPL-07 (version history) is
  v2:98 and must NOT be built.** §Out of Scope:148 records why four template engines are banned
- `.planning/ROADMAP.md` §Phase 4:91 — the five success criteria this phase is verified against, and
  **:105**, the instruction that engine and view-model design is settled *before* the authoring UI
  is built on top of it
- `CLAUDE.md` §Technology Stack — `com.samskivert:jmustache:1.16` and
  `com.vladsch.flexmark:flexmark-all:0.64.8` are the nominated libraries; neither is in `pom.xml`
  yet. The §What NOT to Use table explains why every alternative engine is excluded

### Prior phase decisions this phase must respect
- `.planning/phases/02-jira-ticket-browsing/02-CONTEXT.md` — **D-02-02:30** fixes the ticket field
  set the view-model can expose and is the basis of D-04-17; **the DOC-02 tension note at :43** is
  carried forward here; **D-02-04:64** fixes the ADF vocabulary that lands in template output
- `.planning/phases/03-release-assembly/03-CONTEXT.md` — **D-03-06** (materialised ticket copies:
  why preview needs no Jira call), **D-03-03** (the fixed manual-item category enum D-04-01 renders),
  **D-03-11** (the delete rule D-04-13 deliberately departs from), **D-03-04** (why UX-01's
  drag-and-drop is Phase 5, not here)
- `.planning/phases/01-foundation-authentication/01-CONTEXT.md` — **D-03:27** (acting user on every
  state change), **D-04:28** (error surfacing), **D-17:53** (Java 25, never the installed JDK 17)
- `.planning/phases/03-release-assembly/03-UI-SPEC.md` and
  `.planning/phases/02-jira-ticket-browsing/02-UI-SPEC.md` — the design contract the template screen
  must stay consistent with

### Code this phase builds directly on
- `src/main/java/com/evision/releasemgmt/domain/release/Release.java` — plus `ReleaseItem`,
  `ManualItem`, `ManualItemCategory`, `ReleaseStatus`. The aggregate D-04-03 flattens into the
  view-model, and the **only** root object a template sees
- `src/main/java/com/evision/releasemgmt/domain/jira/TicketDetail.java` — the exact record whose
  fields become `ItemView`'s ticket-backed half
- `src/main/java/com/evision/releasemgmt/adapters/jira/AdfFlattener.java` — **:76 `applyMarks`**
  proves the flattener already emits Markdown, which is the evidence behind D-04-04; **:7
  MAX_NODES/MAX_DEPTH** is the bounding pattern D-04-11 mirrors
- `src/main/resources/db/migration/` — `V1`, `V2`, `V3__release_assembly.sql`; this phase adds **`V4`**
- `src/main/java/com/evision/releasemgmt/ports/ReleaseRepository.java` +
  `adapters/persistence/ReleaseRepositoryAdapter.java` — the port/adapter shape a new
  `TemplateRepository` must copy
- `src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java` — the
  `ProblemDetail` + machine-readable `reason` contract D-04-11's bound breaches must use
- `frontend/src/routes/ReleaseDetailRoute.tsx` and `frontend/src/router.tsx` — the route pattern and
  the `authenticatedRoute` layout the template screen nests under
- `frontend/src/api/client.ts` (`apiFetch`) and `frontend/src/api/queryClient.ts` — the established
  data-fetching path

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`AdfFlattener` already emits Markdown** (`adapters/jira/AdfFlattener.java:76`) — `**strong**`,
  `*em*`, backtick code, fenced blocks, `- ` list items. Ticket descriptions and comments require
  **no conversion** to sit inside a Markdown template body. This is the single strongest piece of
  evidence for D-04-04 and it was discovered during the scout, not assumed.
- **Release data is entirely local.** `release_ticket_item` holds the full materialised ticket copy
  (D-03-06), so both the live preview and the validation render read from Postgres with **zero Jira
  calls** — which is what makes a keystroke-frequency preview viable at all.
- **`GlobalExceptionHandler` + `ProblemDetail` with a machine-readable `reason`** — the Phase 01
  error contract. Template validation failures and D-04-11 bound breaches extend this enum; do not
  invent a parallel error scheme.
- **`CurrentUserPort`** — supplies the actor for template create/edit auditing (Phase 01 D-03).
- **`ReleaseRepositoryAdapter` + `ReleaseJpaRepository` + `ReleaseEntity`** — the exact three-layer
  shape (domain record ⇄ port ⇄ adapter ⇄ JPA entity) a template aggregate must follow.

### Established Patterns
- **Hexagonal boundaries, enforced by ArchUnit** — `domain` → `ports` → `application` →
  `adapters/{web,persistence,jira}`. The template aggregate and the view-model belong in a new
  `domain/template` package; **jmustache and flexmark are infrastructure and must not be imported
  from `domain`** — put the render engine behind a port with the adapter in
  `adapters/render` (or equivalent). `ArchitectureBoundaryTest` will fail the build otherwise.
- **Flyway forward-only SQL migrations** — `V4` is the next number. `jpa.hibernate.ddl-auto:
  validate` means a mismatch between entity and migration fails startup rather than silently
  altering the schema.
- **`src/test/resources/application.yml` shadows main's rather than merging** — any new config
  property (render timeout, collection cap, logo size cap) must be added to **both** files or
  full-context integration tests fail on unresolved placeholders. This has bitten the project twice.
- **Jackson 3 (`tools.jackson.*`)** is Boot 4.1's managed databind — `com.fasterxml.jackson.databind`
  does not resolve on this classpath.
- **Frontend has no component or editor library.** React 19 + TanStack Router/Query + Tailwind 4,
  every component hand-rolled (`frontend/src/components/`). D-04-19's textarea choice keeps that
  intact; D-04-09's client-side preview is the one place this phase adds runtime dependencies
  (a Mustache implementation and a Markdown renderer) — the first such additions to the frontend.
- **Testing shape** — Testcontainers PostgreSQL 18 for persistence ITs, WireMock for Jira,
  `@WebMvcTest` via `spring-boot-starter-webmvc-test`, Vitest for the frontend with tests under
  `frontend/tests/`.

### Integration Points
- `ReleaseService` / `ReleaseRepository` is the read path the view-model assembler consumes. It
  reads persisted items — it must **not** reach into `TicketSearchService` or Jira.
- New REST surface sits alongside `/api/releases`; the template route nests under
  `authenticatedRoute` alongside `/releases`.
- **Phase 5 is the direct consumer.** The `RenderService` (D-04-11), the view-model, the
  unknown-placeholder marker (D-04-08) and the seeded starters (D-04-14) are all its inputs. Leave
  the grouping dimension extensible (D-04-02) and the document-snapshot linkage loose (D-04-13).

</code_context>

<specifics>
## Specific Ideas

### Authoring surface (decided)

- **D-04-19 — Plain styled `<textarea>` for the source, no editor library.** The placeholder catalog
  panel inserts at the cursor on click. Zero new dependencies for the editor itself, consistent with
  a frontend that has hand-rolled every component. The live preview is what shows the author what
  they are getting; syntax highlighting is not what makes a template correct.
  CodeMirror 6 and TipTap were both considered and rejected — TipTap specifically because the source
  is Markdown and Mustache sections are *structure*, which has no sensible WYSIWYG representation.
- **D-04-20 — The TMPL-03 catalog is a hand-written constant in code**, with a test asserting it
  matches the view-model's actual fields. Descriptions are properly written prose rather than
  annotation strings; the test is what prevents drift between catalog, validator and render. It must
  cover every legal token, since D-04-06's minimal subset makes an exhaustive catalog possible.
- **D-04-21 — Two-pane layout: editor left, live preview right, catalog as a collapsible drawer.**
  Preview gets permanent screen space; the catalog is consulted occasionally and hidden while
  writing. Three panes were rejected because a document preview at a third of laptop width
  misrepresents the output. `/gsd-ui-phase 4` details this.

### Verification

- Live tenant `https://evisionegypt.atlassian.net`, project key **`CNV`**. Realistic verification
  means previewing a template against a real assembled release built from a `CNV` sprint — including
  one containing both ticket-backed and manual items (D-04-01) and a ticket with no linked code
  (D-04-18), which is TMPL-04's mandated sample case.

</specifics>

<deferred>
## Deferred Ideas

- **Preview/generation parity fixtures → not planned (D-04-10).** Accepted risk. If drift bites, a
  shared fixture set asserted by both `mvn test` and `vitest` is the additive fix. Do not build it
  pre-emptively.
- **Server-side preview endpoint → not built (D-04-09).** The backend render path exists for
  validation only. If the client renderer proves unable to match D-04-05/D-04-08, promoting the
  validation render into a "verify" action is the escape hatch.
- **Component / epic / label placeholders and their grouping dimensions → Phase 5 (D-04-17).**
  Requires extending the Jira field selection, the `release_ticket_item` snapshot, a migration and a
  backfill decision — all three together. Carried from `02-CONTEXT.md:43`.
- **Generation metadata in templates (`{{generatedAt}}` / `{{generatedBy}}`) → revisit only if
  Phase 5 proves DOC-04 needs it (D-04-03).** It is a view-model change, so it comes back through
  discussion rather than being added quietly.
- **TMPL-07 template version history → v2**, explicitly. D-04-12's many-named-templates is the
  lightweight substitute for the need behind it; D-04-13 confirms no versioning is built.
- **Partials / shared template fragments → not planned (D-04-06).** Revisit only if the three
  document types genuinely converge on an identical block, and only with a fixed in-database loader.
- **UX-01 drag-and-drop → Phase 5**, unchanged from D-03-04. Template *section* reordering is not a
  thing in this phase: D-04-04 makes a template one Markdown document, not an ordered section list,
  so there is nothing to reorder. UX-01 remains unmet after this phase.
- **PDF/DOCX chrome rendering and DOC-08 parity → Phase 5 (D-04-15).** The cover-block preview here
  is an approximation and is not a layout specification.

</deferred>

---

*Phase: 4-Template Authoring*
*Context gathered: 2026-08-06*
