---
phase: 03-release-assembly
status: context-gathered
gathered: 2026-08-04
requirements: [REL-01, REL-02, REL-03, REL-04, REL-05, REL-08]
depends_on: [02-jira-ticket-browsing]
---

# Phase 03: Release Assembly - Context

**Gathered:** 2026-08-04
**Status:** Ready for planning

<domain>
## Phase Boundary

This is the first phase in which the system **owns** an entity. Phases 1-2 only read Jira;
here a `Release` becomes the system of record: named and versioned, holding ticket-backed and
manual items, freezable, listable and searchable.

Delivered: the `Release` aggregate + its items, a Flyway `V3` migration, the REST surface for
create / add-remove items / freeze / read / list-search, and the React screens for a release
library and a release detail view, plus an "Add to release" action on the existing `/tickets`
screen.

**Not delivered here** (belongs to later phases): templates and placeholders (Phase 4),
document generation and staleness reporting (Phase 5, DOC-09), Azure DevOps enrichment
(Phase 6), the environment ladder, promotion and promotion history (Phase 7).

</domain>

<decisions>
## Implementation Decisions

### Assembly flow (discussed with the release manager)

- **D-03-01 — Tickets are pushed from `/tickets`, not pulled from the release.**
  The release manager browses on the existing `/tickets` screen, ticks tickets using the
  multi-select already implemented there, and hits "Add to release". There is **no embedded
  ticket picker inside the release detail screen** — the release screen manages what is already
  on the release.

  *Why:* reuses everything Phase 2 built (project/sprint/JQL filters, status filter, table,
  selection state) instead of duplicating that UI or extracting it into a shared component
  before there is a second consumer. Matches the real gesture: you finish reviewing a sprint's
  tickets and push them into a release.

- **D-03-02 — The "Add to release" picker lists open releases *and* can create one inline.**
  The picker shows open releases; frozen releases are not valid targets. It also offers
  "New release…", which takes name and version inline and creates-then-adds in one step.

  *Why:* the common path is "I have just finished browsing a sprint and this **is** the
  release" — forcing a detour to a separate create screen breaks that. Planner note: the
  create-and-add path must be **one transactional operation** so a failed add cannot leave an
  orphan empty release behind.

- **D-03-03 — Manual item category is a small fixed enum in code.**
  `CONFIG_CHANGE`, `DATABASE_SCRIPT`, `INFRASTRUCTURE`, `OTHER` — the three examples named in
  REL-08 plus one escape hatch. **Not** free text, and **not** a lookup table.

  *Why:* Phase 5's DOC-02 grouping needs a value it can group and filter on deterministically;
  free text fragments grouping on typos ("DB script" vs "Database script"). A lookup table adds
  a join and a table nobody edits in v1. Adding a category later is a migration plus an enum
  value — cheap, and only if actually asked for.

- **D-03-04 — Release items carry no user-controlled order in this phase.**
  No `position` column, no drag-and-drop, no reorder endpoints. Items sort deterministically:
  ticket-backed items first ordered by ticket key, then manual items ordered by title.
  Grouping stays entirely template-driven at generation time (DOC-02: issue type by default,
  or component / epic / label).

  *Why:* the ordering that actually matters is the ordering **inside a generated document**,
  which is Phase 5's concern and is template-driven there. Building a position column plus the
  keyboard-accessible equivalent UX-01 mandates, in Phase 3, would be building it for a consumer
  that does not exist yet. **UX-01's "moving items into or between groupings on a release"
  therefore lands in Phase 5, not here** — carried forward as a deferred item below.

### Claude's Discretion

The release manager elected not to discuss the three areas below and delegated them. Each
decision is recorded with its reasoning so it can be overridden at plan time.

#### Application / product dimension

- **D-03-05 — No `application` entity in v1. A release carries a nullable `jira_project_key`.**
  `.planning/PROJECT.md:99` parked this exact question for this phase ("Assumed yes; this is a
  data-model dimension, not an architecture change. Confirm before release data modelling.").

  The call: the application dimension **already exists** — it is the Jira project the tickets
  came from (`CNV` on the live tenant). Record `jira_project_key` on the release, defaulted from
  the project being browsed when the release is created inline (D-03-02), and use it to group and
  filter the release library (REL-05). It is **advisory, not enforced** — nothing rejects a
  release whose items span projects.

  *Why:* this satisfies "assumed yes" — several products can be managed in one instance and are
  distinguishable in the library — without an entity nobody has asked to administer, and with no
  admin screen, no FK and no seed data. If a real product concept emerges later (several Jira
  projects rolling into one product), an `application` table plus an FK is a purely additive
  migration. Phase 7's ladder is per-release and is unaffected either way.

#### Freeze and snapshot depth

- **D-03-06 — Release items store a materialised copy of the ticket's fields, not just its key.**
  Each ticket-backed item persists the full `TicketDetail` payload (key, summary, issue type,
  status, resolution, assignee, reporter, fixVersions, description, comments, commentTotal) at
  the moment it is added. Reading a release **never** calls Jira.

  Suggested shape, not binding: scalar identity fields (key, summary, issue type, status,
  resolution, assignee) as columns because the release detail table and REL-05 search need to
  sort and filter on them; the bulky flattened rich text (description, comments) as `JSONB`,
  which the stack doc already nominates for exactly this ("JSONB columns are a good fit for
  storing raw Jira payload fragments"). Final shape is the planner's call.

  *Why:* the alternative — store keys, re-fetch live — fails REL-04 for any release older than
  the tickets themselves (deleted, moved or permission-changed tickets), shows *current* status
  rather than the status at freeze, costs N Jira calls per screen view, and makes Phase 4's live
  template preview and Phase 5's generation non-deterministic and slow. Storing the copy is also
  what "this app is the system of record" means in practice.

- **D-03-07 — Refresh while open; immutable once frozen.**
  While a release is `OPEN`, an explicit "refresh from Jira" action re-pulls the current field
  values for its ticket-backed items — staleness is the accepted cost of D-03-06, and this is
  its mitigation. Freezing takes no extra snapshot: **freeze is simply the point after which the
  stored copy stops changing**, because add, remove and refresh are all rejected on a frozen
  release. That is exactly REL-03's "snapshots its exact ticket set at that moment".

  Record `last_refreshed_at` per release — DOC-09's notion of a document being "stale relative
  to the release's current ticket set" needs a timestamp to compare against in Phase 5.

#### Release identity and lifecycle

- **D-03-08 — Name and version are both required free text; unique together.**
  Version is **not** validated as semver. Releases here are sprint-scoped, and real version
  labels in this organisation may look like `2026.08`, `Sprint 42` or `R14.2`; enforcing semver
  would be a fight with reality for no benefit. A `UNIQUE (name, version)` constraint surfaces a
  duplicate as a `ProblemDetail` with a machine-readable `reason`, per the Phase 01 taxonomy.

- **D-03-09 — A release is not modelled as 1:1 with a sprint.**
  No sprint foreign key, no sprint column. Sprint is a *filter* on the ticket browse screen
  (Phase 2), not release identity — a release may legitimately draw from more than one sprint,
  and coupling the two would make that unrepresentable.

- **D-03-10 — Exactly two states: `OPEN` → `FROZEN`. Freeze is a one-way door.**
  There is no unfreeze. Reversible freeze would silently destroy the one property this phase
  exists to provide, and DOC-07 (immutable documents) and AUDIT-03 (append-only) both assume it
  holds. If scope changes after a freeze, the answer is a new release — which is precisely the
  diff v2's REL-06 describes.

  Phase 7's ladder position is a **separate dimension** on the release, not additional release
  statuses. Do not model promotion as a status enum value here.

- **D-03-11 — An `OPEN` release can be hard-deleted; a `FROZEN` one never can.**
  No soft-delete machinery. An open release has no history anyone relies on, so deleting one
  destroys nothing; a frozen release is an audit record and deletion must be rejected. This also
  covers the "froze it by mistake" case without reintroducing unfreeze — with the caveat that a
  mistakenly frozen release stays in the library. Accepted.

- **D-03-12 — Every state change records the acting user.**
  `created_at` / `created_by`, `frozen_at` / `frozen_by`, `last_refreshed_at`. This continues
  Phase 1's D-03 ("the acting user recorded on every state change"), resolved through
  `CurrentUserPort`.

- **D-03-13 — Adding a ticket already on the release is idempotent, not an error.**
  The push flow (D-03-01) is bulk and overlapping selections are normal — a partial 409 in the
  middle of a 40-ticket add is a worse outcome than a silent no-op. Removal (REL-02) happens on
  the release detail screen and is allowed only while `OPEN`. Manual items are editable while
  `OPEN`, frozen thereafter, same rule as ticket-backed items.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project-level decisions and requirements
- `.planning/PROJECT.md` — §Constraints, §Key Decisions; **§Open Questions:99** is the
  multi-application question this phase's D-03-05 answers; §Out of Scope:43 records the
  2026-08-04 Agile-API reversal that made releases sprint-scoped
- `.planning/REQUIREMENTS.md` — REL-01..REL-05, **REL-08** (manual items), and the v2 entries
  REL-06/REL-07 that must NOT be built here; UX-01 (cross-cutting drag-and-drop)
- `.planning/ROADMAP.md` §Phase 3 — the four success criteria this phase is verified against
- `CLAUDE.md` §Technology Stack — Spring Boot 4.1 / Java 25 / PostgreSQL 18 / Flyway; the JSONB
  guidance relied on by D-03-06

### Prior phase decisions this phase must respect
- `.planning/phases/02-jira-ticket-browsing/02-CONTEXT.md` — D-02-02 fixes the ticket field set
  that D-03-06 materialises; D-02-04 fixes how ADF is flattened; the "Carried over from Phase 01"
  section holds the error taxonomy and `RestClient` setup rules
- `.planning/phases/02-jira-ticket-browsing/02-PATTERNS.md` — file-to-analog mapping used when
  placing new backend and frontend files
- `.planning/phases/02-jira-ticket-browsing/02-UI-SPEC.md` — the design contract the release
  screens must stay consistent with

### Code the phase builds directly on
- `src/main/java/com/evision/releasemgmt/domain/jira/TicketDetail.java` — the exact record whose
  fields D-03-06 persists
- `src/main/resources/db/migration/` — `V1__init_schema.sql`, `V2__session_invalidation.sql`;
  this phase adds **`V3`**
- `frontend/src/routes/TicketsRoute.tsx` — holds the existing multi-select (`selected` at :20)
  that D-03-01's "Add to release" action hangs off
- `frontend/src/router.tsx` — release routes nest under `authenticatedRoute`, same as `/tickets`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`/tickets` multi-select** — `frontend/src/routes/TicketsRoute.tsx:20` already tracks a
  `ReadonlySet<string>` of selected keys and renders header/row checkboxes with an
  indeterminate state. D-03-01 adds an action bar to this, nothing more.
- **`TicketDetail` / `TicketResponse`** — the domain record and its web DTO are already the exact
  shape D-03-06 needs to persist and return. No new Jira field work in this phase.
- **`GlobalExceptionHandler` + `ProblemDetail` with a machine-readable `reason`** — Phase 01's
  error contract. Release-specific failures (frozen release mutated, duplicate name+version,
  release not found) extend this; do not invent a parallel error scheme.
- **`CurrentUserPort`** — supplies the actor for D-03-12 without new plumbing.
- **`authenticatedRoute`** (`frontend/src/router.tsx`) — pathless layout route that already
  enforces a session before any child renders; release routes nest under it.
- **TanStack Query client** (`frontend/src/api/queryClient.ts`) and the `apiFetch` wrapper in
  `frontend/src/api/client.ts` — the established data-fetching path.

### Established Patterns
- **Hexagonal boundaries, enforced by ArchUnit** — `domain` → `ports` → `application` →
  `adapters/{web,persistence,jira}`. The `Release` aggregate belongs in a new `domain/release`
  package with its repository expressed as a port; JPA entities stay in
  `adapters/persistence/entity`. `ArchitectureBoundaryTest` will fail the build otherwise.
- **Flyway forward-only SQL migrations** — `V3` is the next number.
- **`src/test/resources/application.yml` shadows main's rather than merging** — any new config
  property must be added to **both** or full-context integration tests fail on unresolved
  placeholders. This has bitten the project before.
- **Jackson 3 (`tools.jackson.*`)** is Boot 4.1's managed databind —
  `com.fasterxml.jackson.databind.ObjectMapper` does not resolve. Relevant if JSONB mapping
  needs a custom converter.
- **Testing shape** — Testcontainers PostgreSQL 18 for persistence integration tests, WireMock
  for Jira, `@WebMvcTest` via `spring-boot-starter-webmvc-test`.

### Integration Points
- `TicketController` / `TicketSearchService` are the read path a new `ReleaseService` consumes
  when materialising item snapshots (D-03-06) and on refresh (D-03-07) — via
  `JiraAccessContextService.currentContext()`, never a shared credential (AUTH-02).
- New REST surface sits alongside `/api/tickets`; new React routes sit alongside `/tickets`.
- The release library screen is the first surface Phase 5's DOC-09 will extend with
  documentation-readiness columns — leave room for it, do not build it.

</code_context>

<specifics>
## Specific Ideas

- Live tenant for verification: `https://evisionegypt.atlassian.net`, project key **`CNV`**.
  Realistic assembly verification means pushing a real sprint's tickets from `/tickets` into a
  new release created inline.
- The intended shape of the primary flow, end to end: *browse `/tickets` → filter to a sprint →
  tick the tickets → "Add to release" → "New release…" → name + version → land on the new
  release.*

</specifics>

<deferred>
## Deferred Ideas

- **UX-01's release-item drag-and-drop → Phase 5.** D-03-04 declines to build ordering here
  because the ordering that matters is the ordering inside a generated document. UX-01 remains a
  v1 requirement and remains unmet after this phase; Phase 5 must deliver it, including the
  keyboard-accessible equivalent UX-01 explicitly requires.
- **`application` entity → later, only if needed.** D-03-05 ships an advisory `jira_project_key`
  instead. Revisit if a product legitimately spans several Jira projects.
- **Admin-managed manual-item categories → not planned.** D-03-03 fixes the enum in code.
- **REL-06 (scope-change diff since freeze) and REL-07 (advisory "not ready" ticket flags)** are
  v2 in `.planning/REQUIREMENTS.md` and must not be built in this phase. D-03-10's one-way freeze
  is what makes REL-06 computable later.
- **DOC-09 documentation-readiness columns on the release library** — Phase 5. D-03-07's
  `last_refreshed_at` is the timestamp it will compare against.

</deferred>

---

*Phase: 3-Release Assembly*
*Context gathered: 2026-08-04*
</content>
