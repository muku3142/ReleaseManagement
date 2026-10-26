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
- [ ] **Phase 17: RICE Roadmap** - Jira and manual roadmap items ranked by an app-computed RICE score
- [ ] **Phase 17.1: Product Briefings Scheduler** (INSERTED) - Daily digest, sprint check and weekly status per active product, scheduled in the app
- [ ] **Phase 18: Usage Tracking** - Aggregate, identity-free page and feature counts shown as a dashboard matrix
- [ ] **Phase 19: AI Deployment Notes (Runbook Recommend)** - Cited, human-reviewed deployment suggestions from stored ADO evidence on the self-hosted model
- [ ] **Phase 20: Daily Planner & External Provider** - Closed-set daily plan with rule-based fallback and a two-key-gated external provider
- [ ] **Phase 21: Integration Settings, Slack & GitHub Adapters** - Choose Jira, Slack, Azure DevOps and GitHub in Settings; Slack as a ticket source, GitHub as a code host; multiple ADO/GitHub connections per product
- [ ] **Phase 22: License Keys** - Signed, offline-checked license keys that set what an installation may do
- [ ] **Phase 23: UI Enhancement & Logo** - Application logo and favicon, and a polish pass across the interface before publishing
- [ ] **Phase 24: GitHub Publishing & Hosted Deployment** - Public source repo, versioned GHCR releases, and a hosted instance on Supabase Postgres

## Phase Details

Execution order: 15 first (everything else reads the ledger or products). 17, 18 and 19 are independent of each other and may run in parallel after 15; 19 carries unmeasured model risk, so start it early. 20 is last (needs 15, 17 and the `synthesize` profile from 19). 17.1 (inserted 2026-10-25) needs only 15 and runs after 17.

Flyway versions are pre-assigned per slice and keep their number regardless of execution order: V14 portfolio (Phase 15), V15 ai_mode (Phase 19), V16 roadmap (Phase 17), V17 usage (Phase 18), V18 planning provider (Phase 20), V19 briefings (Phase 17.1). Flyway runs out of order (FlywayConfig, user decision 2026-10-25, plan 17-01), so each version applies in execution order; a migration must not depend on a higher-numbered one.

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
- [ ] 17-13-PLAN.md — End-to-end HTTP IT, full suites, backup, rebuild and live walk-through (checkpoint)
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

### Phase 18: Usage Tracking
**Goal**: The owner can see which pages and features are used, without any user being identifiable
**Depends on**: Nothing in v1.2 (independent; may run in parallel)
**Requirements**: USAGE-01, USAGE-02, USAGE-03, USAGE-04
**Flyway**: V17 usage (`usage_counter(day, event_key, count)`, no identity column). Preliminary check: `SecurityConfig` CSRF behaviour with fetch keepalive, no request-log or audit filter attaches the principal to the ingest route, neutral endpoint name, manual adblock check.
**Success Criteria** (what must be TRUE):
  1. Navigating the app and using named features produces page-view and feature counts keyed by route template and allowlisted event name, never by concrete id or URL; unknown event names are rejected
  2. The database holds only daily counts per event key; no user, session, IP or URL appears in storage or in logs for usage events (proved by a schema test and a log-capture test)
  3. User can open a usage matrix (pages/features x days or weeks) on the dashboard, labelled approximate
  4. Capturing usage never blocks or slows the UI, and a development StrictMode render does not double-count
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
**Depends on**: Phase 15 (connections belong to products); builds on the v1.0 ticket-provider interface and ADO enrichment
**Requirements**: TBD (to be defined in /gsd-discuss-phase 21)
**Scope (user, 2026-10-22)**: (1) a Settings "Integrations" section: Jira, Slack, Azure DevOps and GitHub each switched on or off with their own credentials, in any combination (a ticket may link to ADO and GitHub at once); (2) a Slack adapter that acts as a ticket source like Jira, behind the existing ticket-provider port; (3) a GitHub adapter in the same role as Azure DevOps: follow a ticket's links to GitHub commits and PRs, read-only, optional, degrading gracefully like ADO. (4) code-host connections are configured per product: each product can have several Azure DevOps connections (organisation/project URL + token) and several GitHub connections (org/repo + token), replacing today's single installation-wide ADO credential.
**Open questions**: Slack has no native ticket model; decide what a Slack "ticket" is (Slack Lists items, or messages in designated channels) and which ticket fields map (key, status, assignee). Decide whether "manage tickets" means read-only like Jira (project constraint: Jira is read-only) or also writes. Decide whether Jira can be switched off when Slack is the ticket source, and how GitHub links are discovered (Jira dev-status shows GitHub when the GitHub for Jira app is installed). GitHub credentials need tracked expiry, like ADO's org-scoped tokens. Decide how a ticket's code link picks its connection (match the link's org/repo against the connections of the release's products; what happens when a release covers several products, or no connection matches), and how the existing single ADO credential migrates to per-product connections.
**Plans**: TBD
**UI hint**: yes

### Phase 22: License Keys
**Goal**: The app runs under a signed license key that sets what an installation may do, checked offline, without breaking an install whose license lapses mid-release
**Depends on**: Phase 15 (limits may count products and clients), Phase 21 (keys may gate integrations)
**Requirements**: TBD (to be defined in /gsd-discuss-phase 22)
**Scope (user, 2026-10-22)**: license keys checked by the app itself — no license server or online activation in this phase. Open for discussion: tiers and which features they gate, limits (seats, clients, products), expiry and grace behaviour, how keys are signed and installed, and what a lapsed key blocks.
**Open question**: Phase 24 publishes the source publicly, and an in-app check in public source can be removed by anyone who builds it; decide in discussion whether keys apply to the downloadable/hosted builds only, and how the source license fits.
**Plans**: TBD

### Phase 23: UI Enhancement & Logo
**Goal**: The app has its own logo and a polished, consistent interface before it is published
**Depends on**: Phase 21 (Settings integrations UI), Phase 22 (licence screens), so the polish pass covers every page that ships
**Requirements**: TBD (to be defined in /gsd-discuss-phase 23)
**Scope (user, 2026-10-22)**: (1) create an application logo and favicon, used in the app header, login page, browser tab and published README/images; (2) a UI enhancement pass across the app. Open for discussion: which pages and pain points to prioritise, whether to adopt a component library or keep plain Tailwind (CLAUDE.md suggests shadcn/ui), dark mode, a visual audit baseline (/gsd-ui-review), and logo direction (style, colours, wordmark vs icon) and the product name it carries.
**Plans**: TBD
**UI hint**: yes

### Phase 24: GitHub Publishing & Hosted Deployment
**Goal**: The application is published on GitHub as a public source repository, as versioned downloadable releases, and as a hosted instance backed by Supabase Postgres
**Depends on**: Phase 23
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
| 17. RICE Roadmap | 12/13 | In Progress|  |
| 17.1. Product Briefings Scheduler | 15/16 | In Progress|  |
| 18. Usage Tracking | 0/? | Not started | - |
| 19. AI Deployment Notes (Runbook Recommend) | 0/? | Not started | - |
| 20. Daily Planner & External Provider | 0/? | Not started | - |
| 21. Integration Settings, Slack & GitHub Adapters | 0/? | Not started | - |
| 22. License Keys | 0/? | Not started | - |
| 23. UI Enhancement & Logo | 0/? | Not started | - |
| 24. GitHub Publishing & Hosted Deployment | 0/? | Not started | - |
