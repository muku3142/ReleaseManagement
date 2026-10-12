# Roadmap: ReleaseManagement

## Overview

The journey starts with a horizontal foundation (Spring Boot/React skeleton, hexagonal package boundaries, single-jar deployment) plus the one authentication capability the project needs — signing in with a Jira credential, since there is no separate user store. From there, every phase is a vertical slice: Jira ticket browsing, release assembly, template authoring, document generation, Azure DevOps enrichment, and promotion/audit tracking each deliver a complete, working end-to-end capability (domain + persistence + REST + React screen), not a technical layer. The build order follows the real dependency chain research uncovered — ticket browsing before release assembly, release assembly and a settled template engine before document generation, document generation working Jira-only before Azure DevOps enrichment is layered on — while the Jira→Azure DevOps link-discovery spike is pulled forward into the Jira phase so its result is known before the enrichment phase is scoped in detail.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Foundation & Authentication** - Spring Boot/React skeleton with hexagonal boundaries, single-jar deployment, Jira site configuration, and sign-in via the release manager's own Jira credentials
- [x] **Phase 2: Jira Ticket Browsing** - Browse/filter the complete candidate ticket set from the configured Jira Cloud site; run the Jira→ADO link-discovery spike (completed 2026-10-07)
- [x] **Phase 3: Release Assembly** - Create, freeze, and manage named/versioned releases built from browsed tickets (completed 2026-08-06)
- [ ] **Phase 4: Template Authoring** - Author, validate, and live-preview per-document-type templates against a safe, documented view-model (all code merged 2026-08-10; awaiting the 04-11 live-tenant verification)
- [x] **Phase 5: Document Generation** - Generate, preview, and export release notes, runbooks, and approval documents as immutable snapshots (completed 2026-08-11)
- [x] **Phase 5.1: Per-Template Logo Sizing** - Make the cover logo's rendered size a per-template setting and close the DOCX cover-logo parity defect (INSERTED, completed 2026-08-12)
- [x] **Phase 5.2: Release Ticket Selection** - Hide already-added tickets while assembling, and add tickets from the release page itself (INSERTED, completed 2026-08-12)
- [x] **Phase 5.3: Actor Identity Display** - Show a person's email instead of an opaque Jira account id, resolved at read time so existing history displays correctly (INSERTED, completed 2026-08-13)
- [ ] **Phase 5.4: Template Selection At Generation Time** - Choose which template produces a document when generating, instead of relying on whichever template is active (INSERTED)
- [ ] **Phase 6: Azure DevOps Enrichment** - Surface linked commits/PRs per ticket, degrading gracefully when absent, with tracked-expiry credentials
- [ ] **Phase 7: Promotion Tracking & Audit Trail** - Promote releases through the fixed environment ladder with a complete, append-only history
- [ ] **Phase 8: Release Insights Dashboard** - One screen answering what Jira structurally cannot: outstanding paperwork, ladder position, what went where and when, and how long releases actually take

## Phase Details

### Phase 1: Foundation & Authentication
**Goal**: Release manager can configure the Jira Cloud site, sign in with their own Jira credentials, and land on a running application that is architecturally ready for every later phase
**Mode:** mvp
**Depends on**: Nothing (first phase)
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, JIRA-01
**Success Criteria** (what must be TRUE):
  1. Release manager can configure which Jira Cloud site the system reads from, and see it in the UI
  2. Release manager can sign in by supplying their Jira email and API token, which the system validates live against that site before granting access
  3. Signing in with an invalid, expired, or revoked token is rejected with a clear error, not a silent failure or generic crash
  4. Stored Jira credentials are encrypted at rest and never appear in any API response after being saved
  5. Release manager can replace an expired or revoked Jira token from a settings screen without losing any existing releases, templates, or history

Note: JIRA-01 was moved here from Phase 2 during phase-1 discussion — AUTH-01 requires validating a token against Jira, which is impossible without knowing the site, so the two cannot be separated.
**Plans**: 8 across 6 waves

**Wave 1**
  - 01: Java 25 toolchain, Spring Boot scaffold, hexagonal boundaries + ArchUnit, Flyway V1 baseline, ProblemDetail error shape, Jira site config API — JIRA-01

**Wave 2** *(blocked on Wave 1 completion)*
  - 02: React SPA built into the jar, app shell, settings screen, SPA deep-link forwarding — JIRA-01
  - 03: Jira credential-validation port, failure taxonomy, live + stub adapters — AUTH-01
  - 04: AES-256-GCM encrypted credential vault with key versioning — AUTH-03, AUTH-04

**Wave 3** *(blocked on Wave 2 completion)*
  - 05: HS256 JWT pair in httpOnly cookies, refresh with immutable absolute cap, CSRF, stateless filter chain — AUTH-01

**Wave 4** *(blocked on Wave 3 completion)*
  - 06: login/refresh/logout/session endpoints, per-principal credential resolution — AUTH-01, AUTH-02, AUTH-03

**Wave 5** *(blocked on Wave 4 completion)*
  - 07: sign-in screen, route guard, silent refresh, token-replacement flow — AUTH-01, AUTH-04

**Wave 6** *(blocked on Wave 5 completion — NOT autonomous, requires a human and a real Jira site)*
  - 08: live-Jira UAT of all five success criteria, plus capture of the real `/myself` fixture — AUTH-01, AUTH-02, AUTH-03, AUTH-04, JIRA-01

Cross-cutting constraints (appear in 2+ plans): stateless sessions with no server-side token store or denylist (D-05, D-09); the acting user recorded on every state change (D-03); the stored token never returned to the client in any form (D-13); every failure surfaced as a ProblemDetail with a machine-readable `reason` (D-04); Java 25, never the installed JDK 17 (D-17).
**UI hint**: yes

### Phase 2: Jira Ticket Browsing
**Goal**: Release manager can browse and filter the complete candidate ticket set for a release from the configured Jira Cloud site, with every Jira read scoped to their own credential
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: JIRA-02, JIRA-03, JIRA-04, JIRA-05, JIRA-06
**Success Criteria** (what must be TRUE):
  1. All ticket reads use the signed-in user's own Jira credential, so results never exceed what that user can see in Jira
  2. Release manager can browse tickets filtered by project, fix version, and JQL, and sees the complete matching set even when it spans many pages (built against `/search/jql` with cursor pagination, never the deprecated `/search` endpoint)
  3. Ticket fields display correctly regardless of an instance's custom-field configuration (resolved by name via field metadata), and rich-text fields render as readable text
  4. Release manager gets a clear, actionable error when Jira is unreachable, rate-limited, or rejects the credential — distinguishable from "no results"
**Plans**: TBD
**UI hint**: yes

Note: this phase also runs the Jira→Azure DevOps link-discovery verification spike (go/no-go on whether `dev-status` carries ADO data for a real ticket on the real instance, with a commit/PR-message key-parsing fallback against ADO's own API as contingency). The spike is technical groundwork for Phase 6 and does not itself map to a v1 requirement.

### Phase 3: Release Assembly
**Goal**: Release manager can assemble a named, versioned release from browsed Jira tickets, freeze its scope, and manage the resulting set of releases
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: REL-01, REL-02, REL-03, REL-04, REL-05
**Success Criteria** (what must be TRUE):
  1. Release manager can create a named, versioned release and add/remove Jira tickets on it while it is still open
  2. Release manager can freeze a release, which snapshots its exact ticket set at that moment
  3. Release manager can open any release and see its ticket set with per-ticket detail
  4. Release manager can list and search all releases
**Plans**: 8 across 6 waves
**UI hint**: yes

### Phase 4: Template Authoring
**Goal**: Release manager can author, validate, and live-preview a separate template per document type against a safe, documented, fixed view-model
**Mode:** mvp
**Depends on**: Phase 3
**Requirements**: TMPL-01, TMPL-02, TMPL-03, TMPL-04, TMPL-05, TMPL-06, TMPL-08, TMPL-09
**Success Criteria** (what must be TRUE):
  1. Release manager can create and edit a separate template for each document type — release notes, deployment runbook, approval document
  2. Templates use `{{ }}`-style placeholders resolved against a documented, fixed view-model of release/ticket/code-reference data, with no expression language, method invocation, or arbitrary property traversal reachable from the template
  3. Release manager can browse a catalog of every available placeholder while authoring, with a description of what each resolves to
  4. Saving a template runs a validation render against a sample context (including a ticket with no linked code) and reports unknown placeholders as errors rather than silently blanking them later
  5. Release manager can preview a template against a real selected release's actual data while editing, and rendering is bounded by an execution timeout and a collection-size limit
  6. Release manager can define a template's document chrome through structured fields — cover page on/off, cover title and subtitle, running header, footer, page numbering — each accepting the same `{{ }}` placeholders as the body, introducing no new expression surface
  7. Release manager can upload and manage a per-template logo image, restricted to raster formats with type validated by content rather than extension, and bounded in byte size and pixel dimensions
**Plans**: 11 across 9 waves

Plans:
**Wave 1**
- [x] 04-01-PLAN.md — View-model records, placeholder catalog and the template scanner (wave 1)
- [x] 04-02-PLAN.md — V4 migration, Template aggregate, repository port and persistence adapter (wave 1)

**Wave 2** *(blocked on Wave 1 completion)*
- [x] 04-03-PLAN.md — Render port, jmustache/flexmark adapter, bounded RenderService and view assembler (wave 2)

**Wave 3** *(blocked on Wave 2 completion)*
- [x] 04-04-PLAN.md — TemplateService, REST surface, DTOs and the new ProblemDetail reason codes (wave 3)

**Wave 4** *(blocked on Wave 3 completion)*
- [x] 04-05-PLAN.md — Content-sniffed logo upload with byte and pixel-dimension caps (wave 4)
- [x] 04-06-PLAN.md — Three seeded starter templates and the backend lifecycle integration tests (wave 4)

**Wave 5** *(blocked on Wave 4 completion)*
- [x] 04-07-PLAN.md — Templates API client module, /templates list route, router and nav registration (wave 5)

**Wave 6** *(blocked on Wave 5 completion)*
- [x] 04-08-PLAN.md — Two-pane authoring route: editor, save, validation states, unsaved-changes guard (wave 6)

**Wave 7** *(blocked on Wave 6 completion)*
- [x] 04-09-PLAN.md — Client-side Mustache + Markdown preview with the unknown-placeholder marker (wave 7)

**Wave 8** *(blocked on Wave 7 completion)*
- [x] 04-10-PLAN.md — Chrome fields form, logo upload control and chrome rendering in the preview (wave 8)

**Wave 9** *(blocked on Wave 8 completion)*
- [x] 04-11-PLAN.md — Placeholder catalog drawer, insert-at-cursor and the live-tenant verification (wave 9)

**Cross-cutting constraints:**
- Release manager can upload, replace and remove one logo per template (TMPL-09, D-04-16)

**UI hint**: yes

Note: engine and view-model design (jmustache integration, `ReleaseView`/`TicketView`/`CommitView`/`PullRequestView` shape, placeholder catalog metadata, validation-render mechanics) is the highest-risk decision in the project per research convergence and must be settled — as the first work inside this phase — before the authoring UI is built on top of it.

### Phase 5: Document Generation
**Goal**: Release manager can generate, preview, and export release notes, deployment runbooks, and approval documents from templates, with every generated document retained as an immutable audit record — working correctly for Jira-only releases with no Azure DevOps data
**Mode:** mvp
**Depends on**: Phase 3, Phase 4
**Requirements**: DOC-01, DOC-02, DOC-03, DOC-04, DOC-05, DOC-06, DOC-07, DOC-08, DOC-09, UX-01
**Success Criteria** (what must be TRUE):
  1. Release manager can generate release notes for a release, grouped by issue type by default and by component when the template specifies it (narrowed by D-05-02 — epic and label are out of scope)
  2. Release manager can generate a deployment runbook with distinct places for deploy steps, post-deploy validation, and a rollback section with explicit trigger conditions
  3. Release manager can generate an approval/change document carrying risk, impact, and backout-plan fields
  4. Release manager can preview any generated document and export it as PDF or DOCX
  5. Every generated document is stored as an immutable snapshot recording template version, content, and generation timestamp — regenerating creates a new revision instead of altering the previous one
  6. Both the PDF and DOCX export of a document render that template's cover page, running header, footer and page numbering, and the two formats are visually equivalent rather than merely both present
  7. Release manager can see across all releases which of the three documents have been generated, which are stale relative to the release's current ticket set, and which were never generated
  8. UX-01 is explicitly resolved rather than deferred a fourth time: it is closed to v2 with recorded reasoning (D-05-11), and every interactive element this phase adds is a native, keyboard-operable HTML element with no drag interaction anywhere in its shipped surfaces
**Plans**: 11 across 5 waves
**UI hint**: yes

### Phase 05.1: Per-Template Logo Sizing (INSERTED)

**Goal**: Template author controls how large the cover logo renders, as a per-template chrome setting stored on the snapshot, and all four renderers agree on that size
**Requirements**: DOC-05, DOC-06, DOC-08, TMPL-08
**Depends on**: Phase 5
**Plans**: 4 across 3 waves
**UI hint**: yes

**Why this was inserted (found 2026-08-11, after Phase 5 was verified):** the cover logo is sized in
three independent places and only two of them cap it.

| Surface | Current sizing | A 512×512 logo renders at |
|---------|----------------|---------------------------|
| PDF | `PdfExportAdapter.java:97` — `.cover img { max-height: 3cm }` | ~1.2 in |
| Browser preview + viewer | `RenderedDocument.tsx:62` — `max-h-20` | 80 px |
| DOCX | `DocxExportAdapter.java:204-205` — native px × `EMU_PER_PIXEL`, **no clamp** | 5.3 in |

`LogoValidator` accepts uploads up to 2000×2000 px, so the DOCX worst case is ~20.8 in tall on an
11.7-in A4 page. This is a DOC-08 parity defect: `D-05-07` deliberately left cross-format parity to a
human eyeball check because PDF and DOCX apply chrome through completely separate mechanisms with no
shared code path, and that check only catches this when the logo is large enough to notice.

**Success criteria:**
1. A template author can set the cover logo's rendered height and see the change in the authoring preview
2. The chosen size is stored on the snapshot, so an existing document keeps the size it was generated with when re-exported
3. A generated document's logo is the same size in the PDF, the DOCX and the document viewer
4. A logo larger than the configured height is scaled down with its aspect ratio preserved, in every renderer
5. No logo can exceed the page's printable area in either export format

**Scope notes:**
- Adds a field to `ChromeFields` (`TMPL-08`/`D-04-15` — structured chrome fields, not a second free-form template), so it needs a V6 migration on both `templates` and `document_snapshots`
- Append-only snapshots (`D-05-06`/`DOC-07`) are unchanged: existing rows get the migration's default, and are not rewritten
- The DOCX clamp is a defect fix and is required regardless of the per-template setting

Plans:
- [x] 05.1-01-PLAN.md — ChromeFields gains logoRenderHeightMm: V6 migration on both tables, threaded through persistence, DTOs, controllers, generation and the seeder (wave 1)
- [x] 05.1-02-PLAN.md — The "Cover logo height (mm)" control, the TypeScript mirror, and mm-based cover-logo sizing in the shared preview/viewer component (wave 1)
- [x] 05.1-03-PLAN.md — Both exporters honour the stored height, proportionally and downward only, plus the cross-format seam assertion (wave 2)
- [x] 05.1-04-PLAN.md — Full regression, V6 against pre-phase rows, and the human parity check across PDF/DOCX/viewer (wave 3)

### Phase 05.2: Release Ticket Selection (INSERTED)

**Goal**: Release manager assembles a release without re-encountering tickets it already contains, and can add tickets to a release from the release page itself rather than only from the browse screen
**Requirements**: REL-01, REL-03, JIRA-02
**Depends on**: Phase 3 (release assembly), Phase 2 (ticket browsing)
**Plans**: 3 across 2 waves
**UI hint**: yes

**Why this was inserted (requested 2026-08-11):** two assembly frictions the release manager hit in
use. Neither is a correctness bug — the backend already tolerates both situations — they are about not
making a person do avoidable work.

1. **Already-added tickets keep appearing in browse.** When assembling a release, tickets already on
   it still show in the ticket table, so the release manager scrolls past and re-selects tickets they
   have already taken.
2. **Tickets can only be added from the browse screen.** The flow is browse → select → choose a
   destination release. There is no way to start from a release and pull tickets into it, which is the
   natural direction once a release exists.

**Success criteria:**
1. Browsing tickets in the context of a release does not list tickets that release already contains
2. Browsing tickets outside any release context still lists everything, unchanged
3. Release manager can open a release and add tickets to it without leaving that page
4. The add-from-release flow offers the same project, sprint, status and JQL filtering and the same
   multi-select as the browse screen — not a reduced second search
5. The ticket browse surface exists once in the codebase, used by both entry points
6. A frozen release offers no add affordance at all, rather than failing after tickets are chosen

**Scope notes:**
- Filtering is scoped to the *target* release only. The schema's
  `release_ticket_item_key_unique UNIQUE (release_id, ticket_key)` deliberately allows one ticket in
  several releases (a hotfix legitimately ships in two), and that capability is preserved
- Filtering happens server-side, after `JiraTicketPort.searchAll` returns, so the count the UI reports
  is honest. It must NOT be expressed in JQL — Jira knows nothing about our releases
- `searchAll` already exhausts Jira's cursor pagination server-side (`JIRA-03`) and returns a complete
  `List<TicketDetail>`, so no cursor or page-top-up logic is involved
- `ReleaseRepository.findTicketKeys(releaseId)` already exists (`ReleaseService.addTickets:70` uses it
  for its own de-duplication) — the filter reuses it rather than adding a query
- The add path already guards frozen releases (`ReleaseService.addTickets:67` calls
  `release.requireOpen()`) and already treats an all-already-present selection as success with honest
  counts (`D-03-13`), which stays as the race safety net
- `items_changed_at` document-staleness stamping happens in `ReleaseRepositoryAdapter`, below the
  service layer, so a new entry point cannot bypass it

Plans:

**Wave 1**
- [x] 05.2-01 — Server-side release-membership filter on `POST /api/tickets/search`, threaded as a separate argument so `toJql()` structurally cannot see it — REL-01, JIRA-02
- [x] 05.2-02 — Extract the browse surface out of `TicketsRoute.tsx` (416 lines) into one reusable `TicketBrowser`; behaviour-preserving, with the existing suite passing untouched as the proof — JIRA-02

**Wave 2** *(blocked on Wave 1 completion)*
- [x] 05.2-03 — The release page's own add-tickets dialog, composing the extracted browser — REL-01, REL-03, JIRA-02

### Phase 05.3: Actor Identity Display (INSERTED)

**Goal**: Every actor shown in the UI is a person's email rather than an opaque Jira account id, resolved at read time so already-recorded history displays correctly too
**Requirements**: AUTH-02, DOC-07
**Depends on**: Phase 1 (credential storage), Phase 5 (document snapshots)
**Plans**: 3 across 3 waves
**UI hint**: yes

**Why this was inserted (found 2026-08-11):** every actor field in the system stores the signed-in
user's **Jira account id**, and the API returns it verbatim, so the UI shows values like
`5f5f5f58aba9660071082b49` where a person belongs. `AuthenticationService.java:58` issues the JWT as
`jwtTokenService.issue(identity.accountId(), email)` — the subject is the accountId and the email is a
separate `CLAIM_EMAIL` claim — so `principal.getName()` yields the accountId at all **9 capture sites
across 5 controllers**: `DocumentController:56` (`generatedBy`), `ReleaseController:54,86` (`createdBy`,
`frozenBy`), `TemplateController:80,114,131,136,142` (`createdBy`, `updatedBy`),
`JiraSiteConfigController:35` (`updatedBy`). Eight response DTOs carry these fields.

**Direction chosen: resolve at read time, keep storing the accountId.** The accountId is the *right*
thing to persist — it is stable, whereas an email can change, and `document_snapshot` is append-only
(`D-05-06`/`DOC-07`) so a stored label could never be corrected afterwards. Resolving for display
instead fixes every already-generated document with no migration and without touching immutability.
Rejected: storing the email going forward (leaves every existing row opaque, loses the stable
identifier) and storing both (schema change across four tables, still does not fix history).

**The trap this phase must not fall into.** `JiraCredentialRepository.findByAccountId` returns a
`JiraCredential` carrying `apiToken`, and loading that entity **decrypts** `encrypted_token`. The
adapter deliberately uses JPQL for `upsert`, `findSessionsInvalidatedAt` and `markSessionsInvalidated`
specifically to avoid that (see the comment at `JiraCredentialRepositoryAdapter.java:33`) because a
rotated `RM_CREDENTIAL_KEY` throws on stale ciphertext — a bug that once locked every user out of the
very sign-in that would have replaced the unreadable token. Using `findByAccountId` on a display path
would let a decryption failure break an unrelated screen such as a document list. This phase needs a
**projection-only** lookup returning `(account_id, email)` without loading the entity, in the same
style as `findSessionsInvalidatedAt`.

**Success criteria:**
1. Every actor field in every API response resolves to an email when that account's credential row exists
2. Documents, releases and templates recorded *before* this phase display correctly, with no migration and no stored value rewritten
3. An account whose credential row no longer exists degrades to showing the account id — never a blank, an error, or a crash
4. No display path can trigger token decryption: a rotated or unreadable `RM_CREDENTIAL_KEY` cannot break a document, release or template screen
5. Resolving many actors on a list screen does not issue one query per row

**Scope notes:**
- Nothing stored changes. No migration. No write path is touched.
- The stored API token must never be read, decrypted or returned on this path (`AUTH-03`/`D-13`: the
  token is never returned to the client in any form)
- An email is PII. It is already exposed for the signed-in user via the session endpoint and this is a
  single-release-manager tool, but the threat model should state the disclosure surface explicitly
  rather than assume it is fine

Plans:

**Wave 1**
- [x] 05.3-01 — The read-only actor lookup: a narrow port plus a JPQL scalar projection that never hydrates `JiraCredentialEntity`, so decryption cannot fire on a display path — AUTH-02

**Wave 2** *(blocked on Wave 1 completion)*
- [x] 05.3-02 — Wire resolution into the four controllers that map actor-bearing responses, batched so a list does not issue a query per row — DOC-07

**Wave 3** *(blocked on Wave 2 completion — NOT autonomous, ends at a human checkpoint)*
- [x] 05.3-03 — Prove the central claim across the seam: with an undecryptable credential row present, the screens still render — AUTH-02, DOC-07

### Phase 05.4: Template Selection At Generation Time (INSERTED)

**Goal**: The release manager chooses which template produces a document at the moment of generation, instead of the choice being implied by whichever template is currently active
**Requirements**: DOC-01, DOC-07, TMPL-01
**Depends on**: Phase 5 (document generation), Phase 05.3 (shares DocumentController)
**Plans**: 7 plans
**UI hint**: yes

**Why this was inserted (requested 2026-08-12):** generation picks its template implicitly.
`DocumentGenerationService.java:51` resolves it as
`templateRepository.findActive(documentType).orElseThrow(NoActiveTemplateException::new)`, and
`TemplateRepositoryAdapter.setActive()` keeps exactly one template active per document type by
deactivating the previous one. To generate with a different template the release manager must leave the
release, go to Templates, activate the other one, come back and generate — and that switch is global, so
it silently changes what every other release will generate next.

**Direction chosen (D-05.4-01, decided 2026-08-12): `active` stays as the default, the choice becomes
an optional override.** `generate()` accepts an optional templateId; when it is absent the current
`findActive` behaviour is unchanged, and the picker preselects the active template. Rejected: removing
the `active` concept outright — a larger diff (drop `setActive`, `findActive`, the activate UI and the
`template.active` column) for a workflow the release manager has not asked to lose.

**Direction chosen (D-05.4-02, decided 2026-08-12): the picker is filtered to the slot's document type
and the server rejects a mismatch.** Per-type readiness, revision numbering and export filenames all key
off document type today, so letting a runbook template fill the release-notes slot would make the
snapshot's own `documentType` dishonest.

**What this phase does NOT need.** `DocumentSnapshot.create(...)` already stores `template.id()`,
`template.name()` and `template.body()` on every snapshot (DOC-07), so which template produced a
document is already recorded — there is no schema change and no migration in this phase.
*(Superseded 2026-10-07 by D-05.4-04 below: the per-user default needs one new table, `V7`.)*

**Per-user default template (D-05.4-03 to D-05.4-06, decided 2026-10-07).** The user added that each user
must be able to set their own default template. This default is layered over the global one:
generation resolves the explicit pick first, then the user's own default for that type, then the
global active template, and otherwise rejects. The default is set from the Templates screen. It is stored in one
`(account_id, document_type) → template_id` table with `ON DELETE CASCADE`. Readiness's blocking state
becomes "no template of this type exists" (`NO_TEMPLATE`), so it reads the same for every viewer.
See `05.4-CONTEXT.md`.

**Success criteria:**
1. Release manager can choose, at the point of generating, which template of that document type is used
2. Generating without making a choice uses the active template exactly as it does today
3. The picker offers only templates matching the slot's document type, and the server rejects a mismatched templateId rather than trusting the client
4. The resulting snapshot records the template actually chosen, so the revision history shows which template produced each revision
5. Choosing a template for one generation does not change what any other release generates next
6. Each user can set their own default template per document type from the Templates screen; it
   preselects the picker and is used when no explicit choice is made, and never affects another user

**Scope notes:**
- `DocumentController.java:48` is also edited by plan 05.3-02 — this phase must run after Phase 05.3, not beside it
- Readiness (`DocumentReadinessService.java:38-40,62-64`, `NO_ACTIVE_TEMPLATE`) ~~keeps its current meaning, since `active` survives~~ becomes `NO_TEMPLATE` (no template of the type exists), per D-05.4-06
- Frontend surface: `DocumentsSection.tsx:87,107,116` (the Generate button and its three `NO_ACTIVE_TEMPLATE` branches), `api/documents.ts`, and a template list query filtered by document type

Plans:

**Wave 1**
- [x] 05.4-01 — V7 `template_user_default` table (composite PK, FK ON DELETE CASCADE) and five port methods incl. per-type existence check — TMPL-01

**Wave 2** *(blocked on Wave 1 completion)*
- [x] 05.4-02 — Rename NO_ACTIVE_TEMPLATE to NO_TEMPLATE end to end on the backend, readiness via existence check, two new D-05.4-02 rejection codes — DOC-01
- [x] 05.4-04 — `PUT/DELETE /api/templates/{id}/my-default` and per-requester `myDefault` on every template response — TMPL-01

**Wave 3** *(blocked on Wave 2 completion)*
- [x] 05.4-03 — Generation resolves explicit pick, then my default, then active; optional request body; templateName on revisions — DOC-01, DOC-07
- [x] 05.4-05 — Templates screen: "Set as my default" / "Clear my default" and a distinct "My default" badge — TMPL-01

**Wave 4** *(blocked on Wave 3 completion)*
- [x] 05.4-06 — Type-filtered picker with default-then-active preselection, NO_TEMPLATE on the frontend, reason messages, template name in history — DOC-01, DOC-07

**Wave 5** *(blocked on Wave 4 completion — NOT autonomous, ends at a human checkpoint)*
- [ ] 05.4-07 — Two-principal / cascade / provenance IT, full regression, human verify — DOC-01, DOC-07, TMPL-01

### Phase 6: Azure DevOps Enrichment
**Goal**: Release manager sees each ticket's linked Azure DevOps commits and pull requests where available, with the tool degrading gracefully when a link is absent or enrichment fails, and credentials safely managed against the 2026 PAT retirement
**Mode:** mvp
**Depends on**: Phase 5
**Requirements**: ADO-01, ADO-02, ADO-03, ADO-04, ADO-05
**Success Criteria** (what must be TRUE):
  1. Where a Jira ticket links to Azure DevOps, release manager sees that ticket's linked commits and pull requests, discovered via whichever mechanism the Phase 2 spike confirmed
  2. Tickets with no Azure DevOps link display and generate documents normally — no error, no empty sections, no failed generation
  3. Release manager can tell the difference between a ticket that has no Azure DevOps link and one whose enrichment was attempted and failed
  4. Release manager configures an org-scoped Azure DevOps credential with a tracked expiry date and is warned before it expires
  5. Enriching a release containing many tickets completes in bounded time through parallel, cached lookups rather than one sequential call per ticket
  6. *(added 2026-10-07, data for INSIGHT-03)* Each stored commit keeps its author date, and each Jira-backed item stores its status-category history (scoped, cached `/changelog`, status changes only), so Phase 8 can separate development churn from testing rework

**Discussed 2026-10-07** alongside Phase 8. See `06-CONTEXT.md` (D-06-01..08). **02-04 ran 2026-10-07 — GO** (`02-DEVSTATUS-SPIKE.md`): `applicationType=AzureRepos`; dev-status lists only
key-tagged commits, so the ADO PAT is **optional** and adds complete commit lists (D-06-09). Ready to plan.
**Plans**: 10 across 6 waves
**UI hint**: yes

### Phase 7: Promotion Tracking & Audit Trail
**Goal**: Release manager can promote a release through the fixed environment ladder and review the complete, append-only history of releases, promotions, and generated documents
**Mode:** mvp
**Depends on**: Phase 3, Phase 5
**Requirements**: DEPL-01, DEPL-02, DEPL-03, DEPL-04, AUDIT-01, AUDIT-02, AUDIT-03
**Success Criteria** (what must be TRUE):
  1. Release manager can see the fixed environment ladder and which stage each release currently sits at
  2. Release manager can record the promotion of a release to the next environment, optionally attaching a free-text note, captured as an immutable entry (target environment, timestamp, actor, outcome)
  3. Release manager can view the complete promotion history of a release, not just its current stage
  4. Release manager can view every document generated for a release, including superseded revisions
  5. Release and promotion history is append-only — a correction adds a new entry rather than editing or deleting an existing one

**Constraints placed by Phase 8 (decided 2026-10-07, D-08-14):** only frozen releases can be promoted.
Promotions are append-only rows with environment, `promoted_at`, `promoted_by`, outcome (SUCCESS/FAILED)
and an optional note, indexed for `(environment, promoted_at)` filtering. The ladder order and the
Production environment are defined. A `current_stage` column alone is insufficient.

**Discussed 2026-10-07** (`07-CONTEXT.md`, D-07-01..12):
- Stages: DEV → TEST → UAT → PROD are seeded. The ladder is editable in Settings, but there is one
  ladder only. Stage ids are permanent, and a used stage is retired, never deleted. Exactly one stage
  is flagged Production.
- Promotion targets the next stage or redeploys the current one; skipping is rejected.
- Outcomes are SUCCESS / FAILED / ROLLED_BACK. A rollback lowers the current stage.
- Each promotion is one entry, recorded after the fact, carrying `deployed_at` and `recorded_at`.
- Corrections are append-only entries that supersede the original, with a required reason.
- Any signed-in user may record or correct a promotion.
**Plans**: 7 across 5 waves
**UI hint**: yes

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 5.1 → 5.2 → 5.3 → 6 → 7 → 8

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation & Authentication | 7/8 | In Progress|  |
| 2. Jira Ticket Browsing | 7/7 | Complete | 2026-10-07 |
| 3. Release Assembly | 8/8 | Complete   | 2026-08-06 |
| 4. Template Authoring | 11/11 | Code merged, awaiting live-tenant verification (04-11 task 3) |  |
| 5. Document Generation | 13/13 | Complete   | 2026-08-11 |
| 5.1 Per-Template Logo Sizing | 4/4 | Complete   | 2026-08-12 |
| 5.2 Release Ticket Selection | 3/3 | Complete   | 2026-08-12 |
| 5.3 Actor Identity Display | 3/3 | Complete   | 2026-08-13 |
| 5.4 Template Selection At Generation Time | 6/7 | In Progress | - |
| 6. Azure DevOps Enrichment | 9/10 | In Progress|  |
| 7. Promotion Tracking & Audit Trail | 6/7 | In Progress|  |
| 8. Release Insights Dashboard | 7/8 | In Progress|  |

### Phase 8: Release Insights Dashboard
**Goal**: Release manager opens one screen and sees what Jira structurally cannot answer — which releases have outstanding paperwork, where each sits on the environment ladder, what went to each environment and when, and how long releases actually take
**Depends on**: Phase 7 (promotion and ladder data), Phase 6 (enrichment coverage), Phase 5 (documentation readiness)
**Requirements**: AUDIT-04, INSIGHT-01, INSIGHT-02, INSIGHT-03
**Plans**: 8 across 5 waves
**UI hint**: yes

**Why this is Phase 8 and not a decimal after Phase 5 (decided 2026-08-11).** The user asked for
dashboard insights "not available in Jira". That request does **not** reverse the recorded decision to
drop analytics — `PROJECT.md:41` drops Jira's *agile reports* (velocity, cycle time, burndown, sprint
scope change) and in the same breath states that "dashboards here must answer what Jira structurally
cannot — release documentation status, environment-ladder position, promotion history". This phase is
exactly that carve-out.

It is sequenced after Phase 7 because of data availability, not preference. Of the three areas that
entry names, only documentation readiness exists today (`DOC-09`, shipped in Phase 5 as the readiness
pills). Ladder position (`DEPL-01`) and promotion history (`AUDIT-01`, `DEPL-03`) are Phase 7. Building
a dashboard earlier would have shown one data source and largely duplicated the releases list, then
been reworked once Phase 7 landed.

**Requirement bookkeeping this phase performs:**
- **`AUDIT-04` finds its home here.** It was mapped to Phase 7 in `REQUIREMENTS.md` but **no ROADMAP
  phase's `Requirements` line claimed it**, so the automated coverage gate could never check it — the
  same defect class as the known `REL-08` omission in Phase 3. It is a cross-release dashboard
  requirement ("view deployment history across all releases on one screen, filterable by environment and
  date range"), so it belongs to this phase and is removed from Phase 7's implicit scope.
- **`INSIGHT-01` and `INSIGHT-02` are new**, added 2026-08-11 for the two insight areas the user selected
  that no existing requirement covers. This grows v1 from 47 requirements to 49.

**Success criteria:**
1. Release manager sees, across all releases on one screen, which of the three documents are generated, stale, or never generated — without opening each release
2. Release manager sees where every release currently sits on the environment ladder
3. Release manager can answer "what went to Production, and when" from one screen, filtered by environment and date range, without opening each release (`AUDIT-04`)
4. Release manager sees how long releases take through their own lifecycle — created to frozen, frozen to production — computed from timestamps this system owns (`INSIGHT-01`)
5. Release manager sees each release's composition: ticket count, manual versus ticket-backed split, and what share of tickets carry linked Azure DevOps commits or pull requests (`INSIGHT-02`)
6. Every figure on the dashboard is derived from this system's own database — no new Jira integration surface, and specifically no Agile board/burndown/story-point reads beyond the sprint listing Phase 2 already uses *(amended 2026-10-07: Phase 6 adds a scoped, cached `/changelog` read for status history, captured at refresh time, never at render time)*
7. *(added 2026-10-07, INSIGHT-03)* Release manager can distinguish development problems (commits needed to reach first Done) from testing problems (reopens after Done, commits after first Done), per ticket and summarised per release

**Discussed 2026-10-07.** See `08-CONTEXT.md` (D-08-01..14).

**Scope notes:**
- **This phase must not rebuild Jira's reports.** `PROJECT.md:41` is still binding: no velocity, no Jira
  cycle time, no burndown, no sprint scope change. Criterion 4 measures *this tool's* release process
  (created → frozen → promoted), which Jira cannot compute because it does not know when a release was
  frozen or deployed here. That distinction is the whole justification — state it in the plans so a
  future reader does not mistake criterion 4 for the thing that was dropped.
- `frontend/src/routes/DashboardRoute.tsx` is currently a 15-line placeholder whose copy still reads
  "No releases yet. Release assembly arrives in a later phase" — stale since Phase 3 shipped. This phase
  replaces it.
- `DOC-09` is still `Pending` in `REQUIREMENTS.md` although Phase 5 shipped the readiness data this
  phase consumes. Confirm it before building criterion 1 on it.
- Read-only over existing tables. This phase should need no new write path; whether it needs new
  read-side projections or aggregate queries is a planning question.

Plans:
- [ ] TBD (run /gsd-plan-phase 8 to break down)
