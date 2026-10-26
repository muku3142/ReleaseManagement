# Phase 4: Template Authoring - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-06
**Phase:** 4-template-authoring
**Areas discussed:** Placeholder surface & grouping, Template body format, Editor surface, Validation & preview behaviour, Template identity & seeding, Chrome & logo scope

**Areas offered but not selected:** Ticket field coverage, Code-reference placeholders — both delegated to Claude's discretion (see end of log).

---

## Placeholder surface & grouping

### Q1 — How should a template see ticket-backed vs manual items?

| Option | Description | Selected |
|--------|-------------|----------|
| One unified `{{#items}}` list | Common ItemView shape plus an `isManual` flag; REL-08's "alongside" comes free, one loop for the author; cost is empty fields for manual items | ✓ |
| Separate `{{#tickets}}` / `{{#manualItems}}` | Honest shapes, no empty fields; cost is that a template can silently omit manual items entirely | |
| Both — separate plus a merged view | Maximum flexibility; cost is three collections and the same data reachable two ways | |

**User's choice:** One unified `{{#items}}` list
**Notes:** The silent-omission risk on the two-collection option was the deciding factor — it is a whole-item version of the failure TMPL-04 exists to prevent.

### Q2 — How much of DOC-02's grouping lands in this phase?

| Option | Description | Selected |
|--------|-------------|----------|
| Build the group mechanism now | Full `{{#groups}}` with a per-template group-by dimension field | |
| Flat `{{#items}}` only — grouping is Phase 5 | Smaller phase; cost is a view-model reshape later, after templates exist | |
| Group by issue type only, now | `{{#groups}}` hard-wired to DOC-02's stated default, no dimension picker; sidesteps the fact that component/epic/label are not fetched at all | ✓ |

**User's choice:** Group by issue type only, now
**Notes:** Ships the group *shape* so Phase 5 adds dimensions rather than reshaping. Left open: which bucket manual items fall into — taken as discretion (D-04-14 planner note).

### Q3 — What else does the root context expose?

| Option | Description | Selected |
|--------|-------------|----------|
| Release + generation metadata + ticket URLs | `{{generatedAt}}`, `{{generatedBy}}`, per-item Jira `{{url}}`; cost is a JiraSiteConfig dependency in the view-model | |
| Release + generation metadata only | Generation stamps but no URLs | |
| Release data only | Strictly the Release aggregate — smallest surface, exhaustively documentable | ✓ |

**User's choice:** Release data only

### Q4 — Does "release data only" also apply to chrome fields?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — release dates are enough | createdAt / frozenAt / lastRefreshedAt are the meaningful dates; render time is metadata about the PDF, not the release | ✓ |
| Allow generation metadata after all | Cheap to add, cover pages and footers are the obvious consumers | |
| Only inside chrome fields | Two placeholder sets to document and validate | |

**User's choice:** Yes — release dates are enough
**Notes:** Asked specifically because TMPL-08 says chrome fields accept the same placeholders, and a footer date is the obvious want. Confirmed deliberately. Phase 5's DOC-07 snapshot is where the generation timestamp lives.

---

## Template body format

### Q1 — What is a template's body source, literally?

| Option | Description | Selected |
|--------|-------------|----------|
| Markdown + Mustache | One flexmark AST feeds both the PDF and DOCX paths — strongest structural guarantee of DOC-08 parity; matches what AdfFlattener already emits | ✓ |
| HTML / XHTML + Mustache | Full styling control and a direct openhtmltopdf path; cost is sanitising untrusted HTML and mapping arbitrary HTML to OOXML | |
| Structured section model | Near-guaranteed parity and a natural home for UX-01; cost is substantially more UI and capped expressiveness | |

**User's choice:** Markdown + Mustache
**Notes:** Decided after the scout found `AdfFlattener.applyMarks` (`adapters/jira/AdfFlattener.java:76`) already emitting `**bold**`, `*em*`, backtick code, fenced blocks and `- ` list items — ticket text needs no conversion to sit in a Markdown template.

### Q2 — How are interpolated values escaped in a Markdown pipeline?

| Option | Description | Selected |
|--------|-------------|----------|
| Interpolate raw; block HTML at the parser | jmustache escaping off, flexmark raw-HTML disabled; one defence in one place, also covers HTML typed into the template body | ✓ |
| Escape Markdown metacharacters | Ticket text renders exactly as typed; cost is that AdfFlattener's `**bold**` renders as literal asterisks | |
| Keep HTML escaping | Cost is visible `&amp;` / `&lt;` entities in output, and it still misses HTML in the template body | |

**User's choice:** Interpolate raw; block HTML at the parser

### Q3 — Which Mustache feature subset is allowed?

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal subset only | Variables, sections, inverted sections, comments. No partials, no lambdas, no custom delimiters; anything else rejected at save | ✓ |
| Minimal subset plus partials | Shared fragments across the three document types; cost is a partial loader as a file-access surface | |
| jmustache defaults | Least code; cost is that the security property rests on library defaults rather than an asserted allowlist | |

**User's choice:** Minimal subset only
**Notes:** Inverted sections kept deliberately — they are the logic-less way to express "no tickets in this group", and they make D-04-18's always-empty commit/PR collections authorable today.

---

## Editor surface

### Q1 — What does the release manager type into?

| Option | Description | Selected |
|--------|-------------|----------|
| Plain textarea + catalog panel | Zero new dependencies, consistent with a hand-rolled frontend; cost is no syntax highlighting | ✓ |
| CodeMirror 6 | Markdown highlighting plus a Mustache overlay that could colour unknown placeholders live; cost is a substantial new dependency | |
| TipTap WYSIWYG | Best for a non-technical author; cost is Markdown↔ProseMirror round-tripping and no sensible representation of Mustache sections | |

**User's choice:** Plain textarea + catalog panel

### Q2 — Where does the TMPL-03 catalog come from?

| Option | Description | Selected |
|--------|-------------|----------|
| Derived from the view-model, annotated | Catalog, validator and render cannot disagree by construction | |
| Hand-written catalog constant | Prose descriptions, dead simple; a test asserts it matches the view-model's actual fields | ✓ |
| Catalog stored in the database | Editable without deploy; cost is a migration, seed data and an admin surface for a problem the project does not have | |

**User's choice:** Hand-written catalog constant

### Q3 — Screen layout

| Option | Description | Selected |
|--------|-------------|----------|
| Two panes + catalog drawer | Editor left, preview right, catalog collapsible; preview gets permanent space | ✓ |
| Three panes side by side | Nothing hidden; cost is a document preview at a third of laptop width | |
| Editor + catalog, preview on demand | Realistic preview width; cost is a click in the middle of the edit→see loop | |

**User's choice:** Two panes + catalog drawer
**Notes:** Coarse shape only — `/gsd-ui-phase 4` details it.

---

## Validation & preview behaviour

### Q1 — Does an unknown placeholder stop the save?

| Option | Description | Selected |
|--------|-------------|----------|
| Block the save | Strongest reading of TMPL-04; guarantees Phase 5 never receives a failing template; cost is no work-in-progress saves | |
| Block on unknown, allow draft saves | Keeps WIP safe without weakening the guarantee; cost is a status field and a second save path | |
| Warn and save | Most forgiving; cost is that it reintroduces the deferred-failure mode TMPL-04 was written to eliminate | ✓ |

**User's choice:** Warn and save
**Notes:** Recorded in CONTEXT.md as a deliberate deviation from TMPL-04's wording. The cost was stated in the option text and the choice was made with it visible. Q2 is what preserves the requirement's intent.

### Q2 — What does an unknown placeholder render as?

| Option | Description | Selected |
|--------|-------------|----------|
| Render a visible marker | `[[unknown: ticket.compnent]]` — loud at every downstream point, missing-key handler rather than a null default | ✓ |
| Render empty, rely on the warning | Clean output; cost is literally the "silently blanking" behaviour TMPL-04 names | |
| Leave the raw token in place | Self-documenting; cost is that it reads as a bug in a signed approval document | |

**User's choice:** Render a visible marker
**Notes:** This is what makes "warn and save" acceptable — the guarantee moves from save-time rejection to render-time visibility.

### Q3 — Where does the live preview render?

| Option | Description | Selected |
|--------|-------------|----------|
| Server-rendered, debounced live | One engine, byte-identical to Phase 5 generation; data is local so no Jira call | |
| Server-rendered, explicit button | Same fidelity, bounded load; cost is that TMPL-06 says "while editing" | |
| Client-side render | Instant, no network; cost is two independent implementations that will drift and eventually misrepresent generation | ✓ |

**User's choice:** Client-side render

### Q4 — What prevents client/server render divergence?

| Option | Description | Selected |
|--------|-------------|----------|
| Golden-fixture parity test in CI | Shared fixtures asserted by both suites; divergence fails the build | |
| Server "verify" action alongside live preview | Authoritative render on demand against real data | |
| Both | Strongest coverage; most work | |
| Accept the drift | Rely on Mustache-spec and CommonMark conformance; cost is that the first divergence is found by someone comparing a PDF to a remembered preview | ✓ |

**User's choice:** Accept the drift
**Notes:** Recorded in CONTEXT.md as D-04-10, an accepted risk in the same style as Phase 01's D-02, with the additive upgrade path noted for the planner. The backend render path still exists — TMPL-04 validation is a server render.

### Q5 — Where do TMPL-05's bounds live?

| Option | Description | Selected |
|--------|-------------|----------|
| In the render service, both paths | One RenderService owns timeout and collection cap; validation now, generation in Phase 5; breach is a ProblemDetail | ✓ |
| Bounds plus a truncation marker | Mirrors AdfFlattener's `[content truncated]`; cost is that a truncated document is a wrong document | |
| Timeout only | Cost is that TMPL-05 names both bounds, leaving the requirement partly unmet | |

**User's choice:** In the render service, both paths

---

## Template identity & seeding

### Q1 — Three templates, or three types with many each?

| Option | Description | Selected |
|--------|-------------|----------|
| Exactly three — one per type | Smallest model, matches TMPL-01's wording, no picker needed in Phase 5; cost is no variants and no undo | |
| Many named templates, one active per type | Safe experimentation without destroying the working template; cost is an active-flag invariant and a management screen | ✓ |
| Three, plus a reset-to-default action | Minimal model without the "I broke it" fear; cost is permanent starter artefacts | |

**User's choice:** Many named templates, one active per type
**Notes:** Adjacent to TMPL-07 (version history), which stays v2 — this is the lightweight substitute for the need behind it.

### Q2 — Seed starter templates?

| Option | Description | Selected |
|--------|-------------|----------|
| Seed three working starters | Realistic templates using groups, items and chrome; doubles as the end-to-end fixture and gives Phase 5 something to render immediately | ✓ |
| Seed a minimal skeleton only | Less to maintain, no half-appropriate document shipped | |
| Start empty | Nothing to maintain; cost is a blank textarea as the first experience and nothing for DOC-01..04 to render | |

**User's choice:** Seed three working starters

### Q3 — Editing and deletion against DOC-07

| Option | Description | Selected |
|--------|-------------|----------|
| Freely editable; deletion blocked once used | Mirrors D-03-11 exactly — hard-delete what has no history, refuse what does | |
| Freely editable and deletable | Simplest rule; the document snapshot holds its own content so nothing readable is lost; cost is that "which template produced this" becomes unanswerable | ✓ |
| Soft-delete — archive, never remove | Nothing ever lost; cost is soft-delete machinery Phase 3 deliberately declined to build | |

**User's choice:** Freely editable and deletable
**Notes:** CONTEXT.md records the traceability cost and adds a planner note for Phase 5 — the document snapshot must store the template's name and body, not merely a foreign key, since a dangling FK is the failure this makes possible.

---

## Chrome & logo scope

### Q1 — How far does chrome go in this phase?

| Option | Description | Selected |
|--------|-------------|----------|
| Stored and validated, not rendered | Page numbering is meaningless without pagination; cost is that the author cannot see their cover until Phase 5 | |
| Stored, validated, and previewed as a cover block | Cover renders as a block, header/footer as strips, page numbers as a literal placeholder; cost is a deliberately approximate preview | ✓ |
| Defer chrome entirely to Phase 5 | Keeps this phase tightly about the engine; cost is moving two v1 requirements into the largest phase | |

**User's choice:** Stored, validated, and previewed as a cover block
**Notes:** CONTEXT.md flags explicitly that this preview is not a layout specification and DOC-08 parity cannot be verified from it.

### Q2 — Logo storage and multiplicity

| Option | Description | Selected |
|--------|-------------|----------|
| One logo per template, stored in Postgres | No volume to provision, travels with a database dump, bounded by the mandated size cap | ✓ |
| One shared org logo, application-wide | Matches reality (one company, one logo); cost is no per-type branding and a template concern on Settings | |
| A small logo library, referenced by templates | Most flexible; cost is asset management, orphan handling and a picker for an unstated need | |

**User's choice:** One logo per template, stored in Postgres
**Notes:** Raster-only, SVG rejected, validate-by-content and size/dimension caps were already locked in REQUIREMENTS.md §Document chrome and were not re-asked.

---

## Claude's Discretion

Two gray areas were offered and not selected; both are recorded in CONTEXT.md with full reasoning so they can be overridden at plan time.

- **Ticket field coverage → D-04-17.** Component, epic, label and priority are NOT added in this phase. They are not fetched from Jira and not columns on `release_ticket_item`; adding them means extending the field selection, the snapshot, a migration and a backfill decision — for placeholders nothing can group by until Phase 5. Q2 of the first area (issue-type-only grouping) is what makes this safe. The DOC-02 tension flagged at `02-CONTEXT.md:43` is carried to Phase 5 as a hard commitment.
- **Code-reference placeholders → D-04-18.** `CommitView` and `PullRequestView` ARE defined now, resolving to empty collections until Phase 6. Without them TMPL-04's mandated "ticket with no linked code" sample cannot exist and every Phase 4 template would need rewriting in Phase 6. Inverted sections make the empty case authorable today.

Also taken as discretion within a discussed area:

- **Manual-item bucket when grouping by issue type** (offered as a follow-up, user chose to move on) — planner note on D-04-14.
- **Authoring-surface specifics** — insert gesture and catalog filtering were offered as follow-ups and declined; `/gsd-ui-phase 4` settles them.

## Deferred Ideas

- Preview/generation parity fixtures — not planned (D-04-10), additive if drift bites
- Server-side preview endpoint — not built (D-04-09); the validation render is the escape hatch
- Component/epic/label placeholders and grouping dimensions — Phase 5 (D-04-17)
- Generation metadata in templates — revisit only if Phase 5 proves DOC-04 needs it (D-04-03)
- TMPL-07 template version history — v2, explicitly
- Partials / shared template fragments — not planned (D-04-06)
- UX-01 drag-and-drop — Phase 5; a Markdown body has no sections to reorder
- PDF/DOCX chrome rendering and DOC-08 parity — Phase 5 (D-04-15)
