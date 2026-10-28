# Roadmap: ReleaseManagement

## Milestones

- ✅ **v1.0 MVP** — Phases 1-8, incl. inserted 5.1-5.4 (shipped 2026-10-20)
- ✅ **v1.1 Release Assistant** — Phases 9-14 (shipped 2026-10-20)
- 🚧 **v1.2 Portfolio & Planning** — Phases 15-20 (in progress, started 2026-10-20)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-8) — SHIPPED 2026-10-20</summary>

- [x] Phase 1: Foundation & Authentication (8/8 plans)
- [x] Phase 2: Jira Ticket Browsing (7/7 plans)
- [x] Phase 3: Release Assembly (8/8 plans)
- [x] Phase 4: Template Authoring (11/11 plans)
- [x] Phase 5: Document Generation (13/13 plans)
- [x] Phase 5.1: Per-Template Logo Sizing (4/4 plans, INSERTED)
- [x] Phase 5.2: Release Ticket Selection (3/3 plans, INSERTED)
- [x] Phase 5.3: Actor Identity Display (3/3 plans, INSERTED)
- [x] Phase 5.4: Template Selection At Generation Time (7/7 plans, INSERTED)
- [x] Phase 6: Azure DevOps Enrichment (10/10 plans)
- [x] Phase 7: Promotion Tracking & Audit Trail (7/7 plans)
- [x] Phase 8: Release Insights Dashboard (8/8 plans)

Full details: [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)

</details>

<details>
<summary>✅ v1.1 Release Assistant (Phases 9-14) — SHIPPED 2026-10-20</summary>

- [x] Phase 9: RAG Service Foundation & Index (11/11 plans)
- [x] Phase 10: RAG Chat & Citations (9/9 plans)
- [x] Phase 11: Tool-Call Protocol (9/9 plans)
- [x] Phase 12: ReleaseManagement as First Client (11/11 plans)
- [x] Phase 13: Assistant Screen & Quality Gate (14/14 plans)
- [x] Phase 14: AI Document Assist (16/16 plans)

Full details: [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)

</details>

### 🚧 v1.2 Portfolio & Planning (In Progress)

**Milestone Goal:** Track which products and clients each release reaches, give technical product managers a RICE-ranked roadmap, show aggregate app usage, and add AI deployment notes and a daily plan.

- [x] **Phase 15: Products, Clients & Installations** - Product/client ledger, release-product assignment and append-only client install records
- [x] **Phase 15.1: Unfreeze and Flexible Promotion** (INSERTED) - Reopen an undeployed frozen release to change tickets; promote straight to any later stage (completed 2026-10-25)
- [x] **Phase 16: Portfolio Dashboard & Assistant Answers** - Releases by product, client x product matrix, behind-latest view, and assistant questions about installs (completed 2026-10-25)
- [x] **Phase 17: RICE Roadmap** - Jira and manual roadmap items ranked by an app-computed RICE score (completed 2026-10-26)
- [ ] **Phase 17.1: Product Briefings Scheduler** (INSERTED) - Daily digest, sprint check and weekly status per active product, scheduled in the app
- [ ] **Phase 17.2: Product-First Releases** (INSERTED) - One product per release; pick the product first and its Jira projects follow
- [ ] **Phase 18: Usage Tracking** - Client product usage read from GA4 or Umami, shown per client and around each release install
- [ ] **Phase 19: AI Deployment Notes (Runbook Recommend)** - Cited, human-reviewed deployment suggestions from stored ADO evidence on the self-hosted model
- [ ] **Phase 20: Daily Planner & External Provider** - Closed-set daily plan with rule-based fallback and a two-key-gated external provider
- [ ] **Phase 21: Integration Settings, Slack & GitHub Adapters** - Choose Jira, Slack, Azure DevOps and GitHub in Settings; Slack as a ticket source, GitHub as a code host; multiple ADO/GitHub connections per product
- [ ] **Phase 22: License Keys** - Signed, offline-checked license keys that set what an installation may do
- [ ] **Phase 23: UI Enhancement & Logo (Foundation)** - ProductOps name, logo and favicon, tokens with dark mode, shadcn/ui, toasts, command palette, shared table and chart components
- [ ] **Phase 23.1: UI Page Migration** (INSERTED) - Every page moved onto the foundation, with charts and rich tables
- [ ] **Phase 24: GitHub Publishing & Hosted Deployment** - Public source repo, versioned GHCR releases, and a hosted instance on Supabase Postgres

## Phase Details

Execution order: 15 first (everything else reads the ledger or products). 17, 18 and 19 are independent of each other and may run in parallel after 15; 19 carries unmeasured model risk, so start it early. 20 is last (needs 15, 17 and the `synthesize` profile from 19). 17.1 (inserted 2026-10-25) needs only 15 and runs after 17.

Flyway versions are pre-assigned per slice and keep their number regardless of execution order: V14 portfolio (Phase 15), V15 ai_mode (Phase 19), V16 roadmap (Phase 17), V17 usage (Phase 18), V18 planning provider (Phase 20), V19 briefings (Phase 17.1), V23 release single product (Phase 17.2). Flyway runs out of order (FlywayConfig, user decision 2026-10-25, plan 17-01), so each version applies in execution order; a migration must not depend on a higher-numbered one.

### Phase 15: Products, Clients & Installations
**Goal**: A release manager can say which products a release covers and record which clients installed it, with a correctable, append-only history that never touches promotion tracking
**Depends on**: Nothing in v1.2 (builds on shipped v1.0/v1.1)
**Requirements**: PORT-01, PORT-02, PORT-03, PORT-04, PORT-05, PORT-06, PORT-07, PORT-08
**Flyway**: V14 portfolio (products, clients, client_product, release_product, client_deployment with append-only trigger and `supersedes_id`). Test V14 against a realistic v1.1 snapshot, not only an empty database. Separate ledger: `environment_stage`, `promotion` and `PromotionService` stay untouched. A product change on a release must bump `items_changed_at` so documents go stale.
**Success Criteria** (what must be TRUE):
  1. User can create, rename and archive products and clients, link a client to several products, and assign products to a release (at least one required); existing releases are given one product per Jira project by the migration
  2. User can record that a client installed a FROZEN release for one of its products, with a back-dated time and a note; a release that does not cover the product or is not FROZEN is rejected, and one that has not reached Production shows a warning but is allowed
  3. User can correct a wrong installation: the original stays visible in history as superseded, and the release's promotion history is unchanged
  4. User can see per client which release it currently runs for each product, and can record one release as installed for several clients in a single bulk action
  5. A generated document template can print the release's product names, and changing a release's products marks its existing documents stale
**Plans**: 13 plans in 5 waves
- [x] 15-01-PLAN.md — V14 portfolio schema, ledger invariants IT, migration-over-v1.1-data IT
- [x] 15-02-PLAN.md — Portfolio domain, install rules (bulk/correct), four ports, ProblemDetail mapping
- [x] 15-03-PLAN.md — Frontend API layer (products, clients, installs, release products) and reason messages
- [x] 15-04-PLAN.md — Product/client/link persistence and release product-set replace under the release lock
- [x] 15-05-PLAN.md — Portfolio page: Products/Clients tabs, links, per-client runs view
- [x] 15-06-PLAN.md — Release products UI: create picker, detail editor, Releases column, Unassigned
- [x] 15-07-PLAN.md — Append-only client_deployment persistence, ArchUnit guard, ledger IT
- [x] 15-08-PLAN.md — Release API: products on create, PUT products, products in responses
- [x] 15-09-PLAN.md — Templates: {{#products}}{{name}}{{/products}} (PORT-08) incl. preview and drawer
- [x] 15-10-PLAN.md — Client installs section: record/bulk/correct/void dialog and history
- [x] 15-11-PLAN.md — Product/client services and REST incl. client runs read model
- [x] 15-12-PLAN.md — Install service and REST (record, bulk, corrections)
- [x] 15-13-PLAN.md — End-to-end HTTP IT, full suites, live walk-through (checkpoint)
**UI hint**: yes

### Phase 15.1: Unfreeze and Flexible Promotion (INSERTED)
**Goal**: A release manager can reopen a frozen release to change its tickets while it has not been deployed anywhere, and can promote a release straight to any later stage
**Depends on**: Phase 15
**Requirements**: REL-UNFREEZE-01, REL-UNFREEZE-02, REL-UNFREEZE-03, PROMO-SKIP-01, PROMO-SKIP-02, PROMO-SKIP-03 (decisions D-15.1-01..06 in 15.1-CONTEXT.md)
**Reverses**: D-07-07 (target must be the next stage); keeps D-07-06 (only frozen releases are promoted)
**Success Criteria** (what must be TRUE):
  1. User can unfreeze a FROZEN release that has no promotion entries and no client install records; it returns to OPEN, tickets can be added or removed, and freezing again stamps a new frozen time
  2. Unfreeze is rejected, with a reason the UI shows, once any promotion or install exists for the release
  3. User can record a promotion to any later active stage or a redeploy to the current stage; promoting to an earlier stage is still rejected (rollback is the way back)
  4. Correction replay and every dashboard metric accept stage skips; documents of a refrozen release reflect the ticket changes (stale until regenerated)
**Plans**: 8 plans

Plans:
- [x] 15.1-01-PLAN.md — Any-later-stage promotion rule, PROMOTION_STAGE_EARLIER rename, requirement IDs
- [x] 15.1-02-PLAN.md — Stale-aware readiness for frozen releases (D-15.1-05)
- [x] 15.1-03-PLAN.md — Unfreeze under the release row lock with ledger guard + persistence IT
- [x] 15.1-04-PLAN.md — Unfreeze service/REST, unfreezeBlockedReason, end-to-end HTTP IT
- [x] 15.1-05-PLAN.md — SPA unfreeze mutation, Release type field, reason copy
- [x] 15.1-06-PLAN.md — Release detail Unfreeze button, blocked state, dialogs, stale note
- [x] 15.1-07-PLAN.md — Record promotion stage picker: redeploy, skip ahead, no earlier stages
- [x] 15.1-08-PLAN.md — Full suites, deploy-branch decision, DB backup, live walk-through (checkpoint)
**UI hint**: yes

### Phase 16: Portfolio Dashboard & Assistant Answers
**Goal**: A release manager can see the portfolio at a glance and ask the assistant what a client runs
**Depends on**: Phase 15, Phase 15.1 (behind ordering uses the current frozen_at; promotions may skip stages)
**Requirements**: PDASH-01, PDASH-02, PDASH-03, ASST-11
**Success Criteria** (what must be TRUE):
  1. User can see releases grouped by product on the dashboard and filter it by product; a release in two products is counted once in totals (fixture-backed)
  2. User can see a client x product matrix showing which release each client runs
  3. User can see which clients are behind the newest installable release of their product
  4. User can ask the assistant which release a client runs for a product, and which clients run a given release, and get an answer matching the installation ledger
**Plans**: 8 plans
Plans:
- [x] 16-01-PLAN.md — Product filter on dashboard releases, summary and deployment history (no double count)
- [x] 16-02-PLAN.md — Frozen-date ordering for "behind" and read-only portfolio port over V14 views
- [x] 16-03-PLAN.md — Portfolio summary / behind / matrix service and endpoints
- [x] 16-04-PLAN.md — Frontend URL state, product-aware requests, portfolio hooks, dashboard cache invalidation
- [x] 16-05-PLAN.md — installs assistant tool and rag-service tool caps 6 / 1900
- [x] 16-06-PLAN.md — Product filter UI and explained, clickable document tiles
- [x] 16-07-PLAN.md — Releases / Portfolio tabs and the Portfolio tab views
- [x] 16-08-PLAN.md — HTTP flow IT, full suites, deploy and live walk-through (checkpoint)
**UI hint**: yes

### Phase 17: RICE Roadmap
**Goal**: A technical product manager can maintain a roadmap ranked by an honest, reproducible RICE score
**Depends on**: Phase 15 (optional product link)
**Requirements**: ROAD-01, ROAD-02, ROAD-03, ROAD-04, ROAD-05, ROAD-06, ROAD-07, PKG-01, PKG-02 (PKG added 2026-10-25, decisions D-17-19..24)
**Flyway**: V16 roadmap (roadmap_item, roadmap_item_input_history) plus the nullable `release.packages_url` column. Score = reach x impact x (confidence/100) / effort, computed in Java, never stored.
**Success Criteria** (what must be TRUE):
  1. User can add roadmap items from Jira (by key or JQL search) and as manual items, and set Reach, Impact (0.25 / 0.5 / 1 / 2 / 3), Confidence (0-100%) and Effort (> 0) with the units and rubric visible in the UI; invalid values are rejected
  2. User sees items ranked by the app-computed RICE score; items with missing inputs sort last in a stable, deterministic order
  3. Refreshing a Jira item updates its summary and status, shows when they were fetched, and never overwrites local RICE inputs; an item whose Jira issue vanished stays and is flagged
  4. User can link an item to a product, filter the roadmap by product, and see the who/when/old-to-new history of its RICE inputs
  5. User can export the ranked roadmap as CSV
  6. User can set, change or clear one OneDrive/SharePoint packages link on a release (OPEN or FROZEN) and open it from release detail; any other URL is rejected; the assistant answers where a release's packages are
**Plans**: 13 plans in 4 waves

**Wave 1**
- [x] 17-01-PLAN.md — V16 (roadmap tables, append-only history, release.packages_url), Release.packagesUrl sweep, Flyway out-of-order
- [x] 17-02-PLAN.md — Roadmap domain: validation, BigDecimal RICE scorer, total-order ranking, ports, history ArchUnit rule
- [x] 17-03-PLAN.md — Frontend API layer: roadmap hooks, URL filters, CSV download, packages-link hook, reason copy, rubric

**Wave 2** *(blocked on Wave 1 completion)*
- [x] 17-04-PLAN.md — JPA entities and adapters; Jira-only refresh writes; locked input edits with history; Postgres IT
- [x] 17-05-PLAN.md — RoadmapService: ranked(filter), manual items, input edits, details, archive, history
- [x] 17-06-PLAN.md — RoadmapImportService: add from Jira / from a release; refresh telling gone from failed
- [x] 17-07-PLAN.md — Packages URL validator, PUT packages-url, 400 reason, releases tool field, release index document
- [x] 17-08-PLAN.md — Rubric panel, inline R/I/C/E inputs, details row with history
- [x] 17-09-PLAN.md — Add dialogs (Jira, release tickets, manual) and add-result message

**Wave 3** *(blocked on Wave 2 completion)*
- [x] 17-10-PLAN.md — RoadmapController, DTOs, CsvWriter (quoting, formula-injection guard), exception mappings
- [x] 17-11-PLAN.md — Release detail: packages link row and Add to roadmap ticket checkboxes
- [x] 17-12-PLAN.md — Roadmap table, /roadmap page, route and nav link

**Wave 4** *(blocked on Wave 3 completion)*
- [x] 17-13-PLAN.md — End-to-end HTTP IT, full suites, backup, rebuild and live walk-through (checkpoint)
**UI hint**: yes

### Phase 17.1: Product Briefings Scheduler (INSERTED)
**Goal**: When Claude's scheduled tasks are unavailable, the app still produces the daily Jira digest, the weekly sprint backlog check and the weekly status update for every active product, on the same schedule and with no Anthropic dependency
**Depends on**: Phase 15 (products); executes after Phase 17 by numbering, no code dependency on it
**Requirements**: BRIEF-01, BRIEF-02, BRIEF-03, BRIEF-04, BRIEF-05, BRIEF-06, BRIEF-07, BRIEF-08 (decisions D-17.1-01..17 in 17.1-CONTEXT.md)
**Source**: the three Jira tasks exported to `scheduledTasks/` (01 daily digest, 02 sprint backlog check, 03 weekly status). Tasks 04 (regulation watch) and 05 (Gulf investment brief) stay Claude-only. Claude remains the primary producer; the app is the fallback. The Briefings page and Claude feed follow `scheduledTasks/briefings-build-prompt.md` (tests B1-B8, F1-F5).
**Flyway**: V19 briefings (pre-assigned): product Jira project keys (backfilled from each product's releases), schedule/settings, briefing runs and their results.
**Success Criteria** (what must be TRUE):
  1. After a grace period past each slot, the app looks for Claude's result for that task and slot in the configured `briefings/data` directory; it runs its own briefing (marked "app fallback") for each product not covered by a working Claude result (missing, or status `failed`), and skips products Claude covered. An unreadable or unconfigured directory counts as Claude unavailable
  2. Each active (non-archived) product with a Jira project key can get its own daily digest (Sun-Thu 08:44), sprint backlog check (Wed 09:41) and weekly status (Thu 13:41), Africa/Cairo, work week Sun-Thu; products without a key are skipped and shown as such
  3. Briefing content is computed by rules from Jira fields (blockers, stale 3+ working days, not-ready stories, changes since the last run, due within 5 days or overdue; readiness field checks; this week's completed / in progress / blocked / added), and the briefing is complete with the AI assistant switched off
  4. When the assistant is enabled, the self-hosted model adds only a short headline/executive summary over the computed facts; a failed or slow model call never fails the run
  5. A Briefings page at `/briefings` shows all five briefings from Claude's `briefings/data/<id>.json` (only the five fixed ids are read; files over 1 MB, malformed JSON and IO errors show "Couldn't read this briefing's file", never a 500), and for the three Jira tasks, per product, Claude's result when present and the app's fallback otherwise, with status (ok / attention / quiet / failed / needs credential / skipped), headline, attention count and the full briefing with linked issue keys; old runs are pruned to the configured count
  6. An admin sets the schedules and grace period, enables/disables each task, picks whose stored Jira credential the scheduler uses, and can run a task now; a missing or expired credential records a "needs credential" run instead of failing silently; Jira stays read-only
**Plans**: 16 plans in 6 waves

**Wave 1**
- [x] 17.1-01-PLAN.md — Claude feed backend: domain, hardened file reader, GET /api/briefings, briefings/ files, read-only mount (B1-B8)
- [x] 17.1-02-PLAN.md — Read-only /briefings page and nav link (F1-F5)
- [x] 17.1-03-PLAN.md — V19, Jira key backfill, product Jira keys API
- [x] 17.1-04-PLAN.md — Portfolio Jira keys editor and all phase reason messages

**Wave 2** *(blocked on Wave 1 completion)*
- [x] 17.1-05-PLAN.md — Domain types, work-week calendar, JQL builder, digest rules, Jira briefing port
- [x] 17.1-09-PLAN.md — Briefings page: per-product app fallback, issue links, credential notice, filter
- [x] 17.1-10-PLAN.md — Settings > Briefings UI

**Wave 3** *(blocked on Wave 2 completion)*
- [x] 17.1-06-PLAN.md — Shared Jira search client, briefing adapter, request-free field lookup
- [x] 17.1-07-PLAN.md — Sprint check and weekly status rules
- [x] 17.1-08-PLAN.md — Run storage (one per slot, pruning) and settings storage

**Wave 4** *(blocked on Wave 3 completion)*
- [x] 17.1-11-PLAN.md — Scheduler credential, optional headline with deadline, runner
- [x] 17.1-12-PLAN.md — GET /api/briefings adds app results
- [x] 17.1-13-PLAN.md — config.json reader, settings service, self-designation

**Wave 5** *(blocked on Wave 4 completion)*
- [x] 17.1-14-PLAN.md — Scheduler tick in Africa/Cairo, unconditional scheduling
- [x] 17.1-15-PLAN.md — Settings API and Run briefing now (no 401 on credential failure)

**Wave 6** *(blocked on Wave 5 completion)*
- [ ] 17.1-16-PLAN.md — End-to-end IT, full suites, backup, rebuild and live walk-through (checkpoint)
**UI hint**: yes

### Phase 17.2: Product-First Releases (INSERTED)
**Goal**: A release belongs to exactly one product, and creating one starts from the product: its Jira projects come from the Portfolio mapping, and the tickets are picked from those projects
**Depends on**: Phase 15 (products, release_product), Phase 17.1 (product ↔ Jira project mapping). Touches the release pages Phase 23 restyles (23-09 changes ReleasesRoute), so run it after Phase 23 or between Phase 23 waves, never in parallel with 23-09.
**Requirements**: REL-PF-01, REL-PF-02, REL-PF-03, REL-PF-04
**Scope (user, 2026-10-26)**: 17.2-CONTEXT.md (D-17.2-01..04). Live data: 1 release, 1 product (CNV), so the migration has nothing to merge.
**Success Criteria** (what must be TRUE):
  1. "New release" on the Releases page asks for the product first, then opens the ticket browser scoped to every Jira project mapped to that product, and the release is created with that one product
  2. The Tickets page also starts from a product, not a free Jira project choice
  3. A release has exactly one product, enforced in the database; portfolio, installs, "clients behind" and the assistant tools keep working
  4. A product with no Jira project blocks release creation with "{Product} has no Jira project. Add one in Portfolio › Products", linking there
**Plans**: 13 plans in 4 waves
Plans:
- [ ] 17.2-01-PLAN.md — V23 UNIQUE(release_id), single-product setProduct/create API, PRODUCT_NO_JIRA_PROJECT, fixture splits
- [ ] 17.2-02-PLAN.md — productId ticket-search mode with quoted `project in (...)`
- [ ] 17.2-03-PLAN.md — TicketBrowser product mode, no-Jira-project notice, scoped AddTicketsToReleaseDialog, /tickets search params
- [ ] 17.2-04-PLAN.md — single-product ProductsEditor and PUT /product hook
- [ ] 17.2-05-PLAN.md — product-first Tickets page and product-fixed AddToReleaseDialog
- [ ] 17.2-06-PLAN.md — Releases "New release" and palette action (gated on 23-12)
- [ ] 17.2-07-PLAN.md — full suites, lic stack (:8090) from a copy of live, user walk-through (checkpoint, wave 4)
- [ ] 17.2-08-PLAN.md — V24 bundle column, bundle-aware create/add/remove API (D-17.2-07)
- [ ] 17.2-09-PLAN.md — "Hide tickets already in a release" checkbox, default on (D-17.2-06)
- [ ] 17.2-10-PLAN.md — hideAlreadyInRelease search flag, one bounded lookup (D-17.2-06)
- [ ] 17.2-11-PLAN.md — BUNDLE grouping dimension for documents (D-17.2-07)
- [ ] 17.2-12-PLAN.md — bundle name in AddToReleaseDialog and AddTicketsToReleaseDialog (D-17.2-07)
- [ ] 17.2-13-PLAN.md — release view grouped by bundle, remove whole bundle or one ticket (D-17.2-07)
**UI hint**: yes

### Phase 18: Usage Tracking
**Goal**: A release manager can see how the products installed at each client are actually used, read from the client's analytics provider (Google Analytics 4 or Umami), next to the installs the app already tracks
**Depends on**: Phase 15 (clients, products, install ledger), Phase 16 (portfolio tab), Phase 23 (UI components and charts). Scope redefined 2026-10-26 (18-CONTEXT.md): read-only analytics integration, not in-app counters.
**Requirements**: USAGE-01, USAGE-02, USAGE-03, USAGE-04, USAGE-05
**Flyway**: next free version (V17 was reserved for the old counter design; outOfOrder is on, planner decides).
**Success Criteria** (what must be TRUE):
  1. An admin can add a GA4 or Umami analytics connection in Settings, test it, and never see its secret again after saving
  2. A client + product can be mapped to a connection and a property/website id; the first sync backfills 90 days and a daily sync keeps it current, storing only aggregate daily figures
  3. Client detail shows a usage panel (period totals, trend, top pages/events) and the portfolio dashboard shows a usage column, both labelled approximate
  4. Usage around a release compares average daily active users and sessions 14 days before vs after the install at that client
  5. An unreachable or unauthorised provider never breaks a page: the last synced data and a problem note are shown, and unmapped client-products show "No analytics source"
**Plans**: TBD
**UI hint**: yes

### Phase 19: AI Deployment Notes (Runbook Recommend)
**Goal**: A release manager gets cited, reviewable deployment suggestions for a release from its stored Azure DevOps evidence, and nothing is saved without approval
**Depends on**: Nothing in v1.2 (builds on v1.1 doc-assist and shipped ADO enrichment; may run in parallel, start early)
**Requirements**: RECO-01, RECO-02, RECO-03, RECO-04, RECO-05, RECO-06
**Flyway**: V15 ai_mode (adds the Recommend mode to AI-assisted revisions). rag-service gets a backward-compatible optional `profile` (`rewrite` default, `synthesize`) on `/v1/transform` with its own prompt; Recommend always uses it on the self-hosted model.
**AI phase**: needs `/gsd-ai-integration-phase` before planning (prompt quality at 4096 tokens, truncation and `num_ctx`, injection set).
**Quality gate**: a measured golden-set run (including prompt-injection cases in commit and PR text) must pass BEFORE human approval or sign-off; report numbers, not assertions (v1.1 retrospective lesson).
**Success Criteria** (what must be TRUE):
  1. User can run Recommend on a release's runbook and get suggested deploy steps, configuration and database changes, risks and validation checks, with a count of the tickets, commits and PRs read; with no ADO data the action is disabled and says why
  2. Every suggestion cites a ticket key, PR id or commit id present in the evidence; citations not in the evidence are dropped or flagged by the server
  3. Likely migration and configuration changes found by the deterministic pre-scan appear as hints to the model and in the coverage report
  4. User reviews the suggestions and explicitly saves them as an AI-assisted runbook revision; closing the page saves nothing
  5. The golden-set report shows citation validity and injection resistance meeting the agreed thresholds before sign-off, and Recommend never calls an external provider
**Plans**: TBD
**UI hint**: yes

### Phase 20: Daily Planner & External Provider
**Goal**: A release manager opens one page and sees what to work on today, with an optional, tightly gated external model
**Depends on**: Phase 15, Phase 17, Phase 19 (`synthesize` profile)
**Requirements**: PLAN-01, PLAN-02, PLAN-03, PLAN-04, PLAN-05, PLAN-06
**Flyway**: V18 planning provider (provider settings, encrypted write-only key via `EncryptedTokenConverter`, allow-list enable/disable audit). `PlanningModelPort` with `RagPlanningModel` (default, ships first as the first plan) and `OpenAiCompatibleAdapter` (second plan) in the app, not in rag-service; ArchUnit pins the adapter to `PlanningService`.
**AI phase**: needs `/gsd-ai-integration-phase` before planning (OpenAI-compatible streaming dialects, SSRF and DNS rebinding on the base URL, closed-candidate-set prompt).
**Quality gate**: a measured golden-set run (closed-set adherence, dropped unknown items, no payload fields beyond the allowlist) must pass BEFORE human approval; includes a payload-shape test on the external request.
**Success Criteria** (what must be TRUE):
  1. User can open a daily planning page that proposes today's work from releases in flight, pending promotions, documents not ready, clients behind and top RICE items, each item with a one-line reason
  2. The plan contains only items from the app's own candidate set; unknown items returned by the model are dropped
  3. With the model unavailable, the page still shows a rule-based plan
  4. The external provider can be switched on only when the ops allow-list permits its host and a signed-in user enables it in Settings; the API key is write-only and encrypted, and there is no silent fallback between providers
  5. While the external provider is on, a persistent banner names the host receiving data, each enable/disable appears in the audit trail, and only allowlisted minimal fields (no commit text, ticket descriptions or document text) are sent
**Plans**: TBD
**UI hint**: yes

### Phase 21: Integration Settings, Slack & GitHub Adapters
**Goal**: An admin chooses in Settings which integrations the installation uses (Jira, Slack, Azure DevOps, GitHub) in any combination, and the new Slack ticket-source and GitHub code-host adapters work beside the existing Jira and Azure DevOps ones
**Depends on**: Phase 15 (connections belong to products); builds on the v1.0 ticket-provider interface and ADO enrichment. UI uses the Phase 23 components
**Requirements**: TBD (to be defined in /gsd-discuss-phase 21)
**Scope (user, 2026-10-22)**: (1) a Settings "Integrations" section: Jira, Slack, Azure DevOps and GitHub each switched on or off with their own credentials, in any combination (a ticket may link to ADO and GitHub at once); (2) a Slack adapter that acts as a ticket source like Jira, behind the existing ticket-provider port; (3) a GitHub adapter in the same role as Azure DevOps: follow a ticket's links to GitHub commits and PRs, read-only, optional, degrading gracefully like ADO. (4) code-host connections are configured per product: each product can have several Azure DevOps connections (organisation/project URL + token) and several GitHub connections (org/repo + token), replacing today's single installation-wide ADO credential. (5) **several Jira sites** (user, 2026-10-27): more than one Jira instance can be connected (e.g. the company site plus a client's own Jira), replacing today's single RM_JIRA_DEFAULT_BASE_URL site; each product Jira project key (Phase 17.1 mapping) records which site it lives on, and each user stores a credential per site they use.
**Open questions**: For several Jira sites: how existing users' single credential and the env-seeded site migrate; whether a Jira project key is unique per site or globally (the 17.1 mapping assumes global uniqueness); how briefings (scheduler user), roadmap import and product-first releases (17.2) pick the site from the product's mapping; whether the Advanced JQL box gets a site picker. Slack has no native ticket model; decide what a Slack "ticket" is (Slack Lists items, or messages in designated channels) and which ticket fields map (key, status, assignee). Decide whether "manage tickets" means read-only like Jira (project constraint: Jira is read-only) or also writes. Decide whether Jira can be switched off when Slack is the ticket source, and how GitHub links are discovered (Jira dev-status shows GitHub when the GitHub for Jira app is installed). GitHub credentials need tracked expiry, like ADO's org-scoped tokens. Decide how a ticket's code link picks its connection (match the link's org/repo against the connections of the release's products; what happens when a release covers several products, or no connection matches), and how the existing single ADO credential migrates to per-product connections.
**Plans**: TBD
**UI hint**: yes

### Phase 22: License Keys
**Goal**: The app runs under a signed license key that sets what an installation may do, checked offline, without breaking an install whose license lapses mid-release
**Depends on**: Phase 15 (limits may count products and clients), Phase 21 (keys may gate integrations). UI uses the Phase 23 components
**Requirements**: TBD (to be defined in /gsd-discuss-phase 22)
**Scope (user, 2026-10-22)**: license keys checked by the app itself — no license server or online activation in this phase. Open for discussion: tiers and which features they gate, limits (seats, clients, products), expiry and grace behaviour, how keys are signed and installed, and what a lapsed key blocks.
**Open question**: Phase 24 publishes the source publicly, and an in-app check in public source can be removed by anyone who builds it; decide in discussion whether keys apply to the downloadable/hosted builds only, and how the source license fits.
**Plans**: TBD

### Phase 23: UI Enhancement & Logo (Foundation)
**Goal**: The app is branded "ProductOps — The product delivery platform" and has the shared design foundation every page will use: logo, theme tokens with light and dark mode, shadcn/ui components, toasts and motion, a command palette, and shared table and chart components
**Depends on**: Phases 15–17.1 (current pages). Moved ahead of 18, 21 and 22 on 2026-10-26; pages those phases add must use the Phase 23 components.
**Requirements**: UI-01, UI-02, UI-03, UI-04, UI-05, UI-06 (UI-04 also covers the D-23-14/D-23-15 hover highlights and animations)
**Scope**: 23-CONTEXT.md (D-23-01..15). Starts with a /gsd-ui-review baseline audit, then a /gsd-sketch of three SVG logo concepts (brand colour chosen there), then /gsd-ui-phase.
**Success Criteria** (what must be TRUE):
  1. The header, login page and browser tab show the chosen ProductOps logo and favicon, and the title reads "ProductOps · The product delivery platform"
  2. The theme follows the OS and can be toggled; the shell, login and every new shared component are legible in light and dark
  3. Brand and semantic colours come from tokens, and shadcn/ui base components are in the repo
  4. Success and failure feedback shows as toasts, loading as skeletons, hover highlights and animations (page fade, row stagger, KPI count-up, status pulse, logo connect-in) are in place, and all motion respects reduced-motion
  5. Ctrl/Cmd+K opens a command palette that jumps to releases, clients, products, roadmap items and settings
  6. A shared DataTable (sort, filter, sticky header, column choice) and a shared chart component exist and are each used on at least one page
**Plans**: 12 plans in 6 waves
- [ ] 23-01-PLAN.md — Pinned toolchain, components.json (radix-nova), @/ alias, jsdom setup, token sheet, ThemeProvider + boot script, ProductOps head
- [ ] 23-02-PLAN.md — Favicons, README lockups, ProductOpsMark/ProductOpsLogo, static-asset forwarding tests
- [ ] 23-03-PLAN.md — shadcn/ui base set on Radix with owned edits, status Badge/Alert variants, Sonner on the owned theme
- [ ] 23-04-PLAN.md — Ctrl/Cmd+K command palette, shared nav list, G-sequence shortcuts, lazy list hooks
- [ ] 23-05-PLAN.md — Shared DataTable on TanStack Table v9 (sort, facets, sticky header, column choice)
- [ ] 23-06-PLAN.md — Lazy token-driven StageChart on the dashboard "Releases per stage" tile
- [ ] 23-07-PLAN.md — Standalone branded login, Settings toasts, paper surfaces and token banners
- [ ] 23-08-PLAN.md — Router layout split, sidebar shell, top bar with palette/theme/account menus, paper island, nav hover + sliding indicator, page fade, sidebar logo connect-in
- [ ] 23-09-PLAN.md — /releases reference page: PageHeader, DataTable, Badge readiness ("Out of date"), Skeleton, Empty
- [ ] 23-10-PLAN.md — Full suites, app container rebuild, live walk-through in light and dark incl. motion/hover and reduced motion (checkpoint)
- [ ] 23-11-PLAN.md — Motion and hover layer: motion tokens and keyframes, useCountUp/usePulseOnChange/useEntranceStagger, DataTable row wash/accent bar/stagger, Card lift, Button press, mark connect-in
- [ ] 23-12-PLAN.md — Motion applied: dashboard KPI count-up, tile lift and list stagger, readiness/ladder status pulses, login logo connect-in, clickable /releases rows
**UI hint**: yes

### Phase 23.1: UI Page Migration (INSERTED)
**Goal**: Every existing page uses the Phase 23 foundation and looks modern and consistent, with charts on the dashboard and portfolio and rich tables where they help
**Depends on**: Phase 23
**Requirements**: UI-07, UI-08
**Scope**: 23-CONTEXT.md D-23-12/13. Pages in priority order: dashboard and portfolio (charts), release detail, roadmap/briefings/settings (DataTable), then templates/documents and the rest. The baseline audit findings are the checklist.
**Success Criteria** (what must be TRUE):
  1. Every page uses token colours and the shared components; no raw gray-*/blue-* classes remain in migrated code
  2. The dashboard and portfolio show real charts, and the roadmap and other dense lists use the shared DataTable
  3. A repeat /gsd-ui-review scores higher than the Phase 23 baseline on every pillar
**Plans**: TBD
**UI hint**: yes

### Phase 24: GitHub Publishing & Hosted Deployment
**Goal**: The application is published on GitHub as a public source repository, as versioned downloadable releases, and as a hosted instance backed by Supabase Postgres
**Depends on**: Phase 23.1
**Requirements**: TBD (to be defined in /gsd-discuss-phase 24)
**Scope (user, 2026-10-22)**: (1) public source repo — secrets removed from history first (the API key committed as a compose default in 7f63e06, and the `.env` committed on master), README and contribution docs; (2) downloadable release — GitHub Actions builds versioned images to GHCR with a compose file; (3) hosted demo/SaaS on Supabase as hosted Postgres (pgvector for rag-service). Open for discussion: where the app and rag-service containers and Ollama run when hosted, Flyway against Supabase, which remote becomes canonical (origin is Azure DevOps today), and the source license.
**Plans**: TBD
**UI hint**: no

## Progress

| Milestone | Phases | Plans | Status | Shipped |
|-----------|--------|-------|--------|---------|
| v1.0 MVP | 1-8 | 89/89 | Complete | 2026-10-20 |
| v1.1 Release Assistant | 9-14 | 70/70 | Complete | 2026-10-20 |
| v1.2 Portfolio & Planning | 15-24 | 0/? | In progress | - |

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 15. Products, Clients & Installations | 13/13 | Complete | 2026-10-22 |
| 15.1. Unfreeze and Flexible Promotion | 8/8 | Complete   | 2026-10-25 |
| 16. Portfolio Dashboard & Assistant Answers | 8/8 | Complete   | 2026-10-25 |
| 17. RICE Roadmap | 13/13 | Complete   | 2026-10-26 |
| 17.1. Product Briefings Scheduler | 15/16 | In Progress|  |
| 17.2. Product-First Releases | 0/7 | Planned | - |
| 18. Usage Tracking | 0/? | Not started | - |
| 19. AI Deployment Notes (Runbook Recommend) | 0/? | Not started | - |
| 20. Daily Planner & External Provider | 0/? | Not started | - |
| 21. Integration Settings, Slack & GitHub Adapters | 0/? | Not started | - |
| 22. License Keys | 0/? | Not started | - |
| 23. UI Enhancement & Logo (Foundation) | 0/? | Not started | - |
| 23.1. UI Page Migration | 0/? | Not started | - |
| 24. GitHub Publishing & Hosted Deployment | 0/? | Not started | - |
