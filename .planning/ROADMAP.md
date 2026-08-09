# Roadmap: ReleaseManagement

## Overview

The journey starts with a horizontal foundation (Spring Boot/React skeleton, hexagonal package boundaries, single-jar deployment) plus the one authentication capability the project needs — signing in with a Jira credential, since there is no separate user store. From there, every phase is a vertical slice: Jira ticket browsing, release assembly, template authoring, document generation, Azure DevOps enrichment, and promotion/audit tracking each deliver a complete, working end-to-end capability (domain + persistence + REST + React screen), not a technical layer. The build order follows the real dependency chain research uncovered — ticket browsing before release assembly, release assembly and a settled template engine before document generation, document generation working Jira-only before Azure DevOps enrichment is layered on — while the Jira→Azure DevOps link-discovery spike is pulled forward into the Jira phase so its result is known before the enrichment phase is scoped in detail.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Foundation & Authentication** - Spring Boot/React skeleton with hexagonal boundaries, single-jar deployment, Jira site configuration, and sign-in via the release manager's own Jira credentials
- [ ] **Phase 2: Jira Ticket Browsing** - Browse/filter the complete candidate ticket set from the configured Jira Cloud site; run the Jira→ADO link-discovery spike
- [x] **Phase 3: Release Assembly** - Create, freeze, and manage named/versioned releases built from browsed tickets (completed 2026-08-06)
- [ ] **Phase 4: Template Authoring** - Author, validate, and live-preview per-document-type templates against a safe, documented view-model
- [ ] **Phase 5: Document Generation** - Generate, preview, and export release notes, runbooks, and approval documents as immutable snapshots
- [ ] **Phase 6: Azure DevOps Enrichment** - Surface linked commits/PRs per ticket, degrading gracefully when absent, with tracked-expiry credentials
- [ ] **Phase 7: Promotion Tracking & Audit Trail** - Promote releases through the fixed environment ladder with a complete, append-only history

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
- [ ] 04-01-PLAN.md — View-model records, placeholder catalog and the template scanner (wave 1)
- [ ] 04-02-PLAN.md — V4 migration, Template aggregate, repository port and persistence adapter (wave 1)
- [ ] 04-03-PLAN.md — Render port, jmustache/flexmark adapter, bounded RenderService and view assembler (wave 2)
- [ ] 04-04-PLAN.md — TemplateService, REST surface, DTOs and the new ProblemDetail reason codes (wave 3)
- [ ] 04-05-PLAN.md — Content-sniffed logo upload with byte and pixel-dimension caps (wave 4)
- [ ] 04-06-PLAN.md — Three seeded starter templates and the backend lifecycle integration tests (wave 4)
- [ ] 04-07-PLAN.md — Templates API client module, /templates list route, router and nav registration (wave 5)
- [ ] 04-08-PLAN.md — Two-pane authoring route: editor, save, validation states, unsaved-changes guard (wave 6)
- [ ] 04-09-PLAN.md — Client-side Mustache + Markdown preview with the unknown-placeholder marker (wave 7)
- [ ] 04-10-PLAN.md — Chrome fields form, logo upload control and chrome rendering in the preview (wave 8)
- [ ] 04-11-PLAN.md — Placeholder catalog drawer, insert-at-cursor and the live-tenant verification (wave 9)

**UI hint**: yes

Note: engine and view-model design (jmustache integration, `ReleaseView`/`TicketView`/`CommitView`/`PullRequestView` shape, placeholder catalog metadata, validation-render mechanics) is the highest-risk decision in the project per research convergence and must be settled — as the first work inside this phase — before the authoring UI is built on top of it.

### Phase 5: Document Generation
**Goal**: Release manager can generate, preview, and export release notes, deployment runbooks, and approval documents from templates, with every generated document retained as an immutable audit record — working correctly for Jira-only releases with no Azure DevOps data
**Mode:** mvp
**Depends on**: Phase 3, Phase 4
**Requirements**: DOC-01, DOC-02, DOC-03, DOC-04, DOC-05, DOC-06, DOC-07
**Success Criteria** (what must be TRUE):
  1. Release manager can generate release notes for a release, grouped by issue type by default and by component, epic, or label when the template specifies it
  2. Release manager can generate a deployment runbook with distinct places for deploy steps, post-deploy validation, and a rollback section with explicit trigger conditions
  3. Release manager can generate an approval/change document carrying risk, impact, and backout-plan fields
  4. Release manager can preview any generated document and export it as PDF or DOCX
  5. Every generated document is stored as an immutable snapshot recording template version, content, and generation timestamp — regenerating creates a new revision instead of altering the previous one
**Plans**: TBD
**UI hint**: yes

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
**Plans**: TBD
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
**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation & Authentication | 7/8 | In Progress|  |
| 2. Jira Ticket Browsing | 6/7 | In Progress|  |
| 3. Release Assembly | 8/8 | Complete   | 2026-08-06 |
| 4. Template Authoring | 0/TBD | Not started | - |
| 5. Document Generation | 0/TBD | Not started | - |
| 6. Azure DevOps Enrichment | 0/TBD | Not started | - |
| 7. Promotion Tracking & Audit Trail | 0/TBD | Not started | - |
