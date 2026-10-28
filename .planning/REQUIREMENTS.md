| ASST-11 |  | Complete || PDASH-03 |  | Complete || PDASH-02 |  | Complete || PDASH-01 |  | Complete || PROMO-SKIP-03 |  | Complete || PROMO-SKIP-02 |  | Complete || PROMO-SKIP-01 |  | Complete || REL-UNFREEZE-03 |  | Complete || REL-UNFREEZE-02 |  | Complete || REL-UNFREEZE-01 |  | Complete |# Requirements: ReleaseManagement

## Milestone v1.2 Requirements — Portfolio & Planning

Defined 2026-10-20. Track which products and clients each release reaches, give technical product managers a
RICE-ranked roadmap, show aggregate app usage, and add AI deployment notes and a daily plan. Research:
`.planning/research/v1.2/SUMMARY.md`. Phase numbering continues at 15.

Decisions taken while scoping (2026-10-20):
- A client can use several products (many-to-many).
- An installation can be recorded only for a FROZEN release; if the release has not reached the Production stage, a warning is shown but the install is allowed.
- The external AI provider is enabled by two keys: an ops `allowed-hosts` property (empty = off) and a Settings toggle any signed-in user can flip.
- Usage tracking is aggregate only; no user identity is stored.

### Portfolio — Products and Clients

- [x] **PORT-01**: User can create, rename and archive products
- [x] **PORT-02**: User can create, rename and archive clients and link each client to one or more products
- [x] **PORT-03**: User can assign one or more products to a release (at least one is required); existing releases get a product per Jira project during migration
- [x] **PORT-04**: User can record that a client installed a release for one of its products, with a back-datable install time and a note; only releases covering that product and in FROZEN state are accepted, with a warning if the release has not reached the Production stage
- [x] **PORT-05**: User can correct a wrong installation record; the original stays in history (append-only, superseded), and promotion history is never touched
- [x] **PORT-06**: User can see, per client, which release it currently runs for each product, derived from installation history
- [x] **PORT-07**: User can record one release as installed for several clients at once (bulk install)
- [x] **PORT-08**: Generated document templates can reach the release's product names

### Portfolio — Dashboard

- [x] **PDASH-01**: User can see releases grouped by product on the dashboard, and filter the dashboard by product; a release in two products is never double-counted in totals
- [x] **PDASH-02**: User can see a client x product matrix showing the release each client runs
- [x] **PDASH-03**: User can see which clients are behind the newest installable release of their product

### Release lifecycle — Unfreeze and flexible promotion (Phase 15.1, inserted 2026-10-22)

- [x] **REL-UNFREEZE-01**: User can unfreeze a FROZEN release that has no promotion entries and no client install records; it returns to OPEN with its frozen time and frozen-by cleared, its tickets can be changed, and freezing again stamps a new frozen time
- [x] **REL-UNFREEZE-02**: Unfreeze is rejected with a reason the UI shows (not frozen / has promotions / has installs) once any promotion or install row exists, including failed, voided and superseded ones; the check is race-free against a concurrent promotion or install
- [x] **REL-UNFREEZE-03**: A frozen release's documents read as out of date when they were generated before its last refresh, or before a ticket-set change made before the freeze; product edits after freezing never make documents out of date
- [x] **PROMO-SKIP-01**: User can record a promotion to the current stage (redeploy) or any later active stage, skipping stages, including as the first entry, with no reason required
- [x] **PROMO-SKIP-02**: A promotion to a stage earlier than the current stage is rejected; rollback on the current stage remains the way back
- [x] **PROMO-SKIP-03**: Correction replay and every dashboard metric accept releases that skipped stages

### Releases — Product first (Phase 17.2, inserted 2026-10-26)

- [ ] **REL-PF-01**: User creates a release by choosing its product first; the ticket browser then searches every Jira project mapped to that product (Portfolio › Products)
- [ ] **REL-PF-02**: Both entry points (Releases page "New release" and the Tickets page) start from a product instead of a free Jira project choice
- [ ] **REL-PF-03**: A release has exactly one product, enforced in storage, and existing portfolio, install, "clients behind" and assistant behaviour keeps working
- [ ] **REL-PF-04**: A product with no mapped Jira project blocks release creation with a message linking to Portfolio › Products

### Roadmap — RICE

- [ ] **ROAD-01**: User can add roadmap items from Jira tickets (by key or JQL search via the existing provider) and add manual items
- [ ] **ROAD-02**: User can set Reach, Impact (0.25 / 0.5 / 1 / 2 / 3), Confidence (0-100%) and Effort (> 0) on each item, with documented units and rubric shown in the UI
- [ ] **ROAD-03**: User can see items ranked by RICE score computed by the app; items with missing inputs sort last with a deterministic tie-break
- [ ] **ROAD-04**: Refreshing a Jira item updates its Jira fields (summary, status) and shows when they were fetched, without overwriting local RICE inputs; an item whose Jira issue becomes unavailable is kept and flagged
- [ ] **ROAD-05**: User can optionally link a roadmap item to a product and filter the roadmap by product
- [ ] **ROAD-06**: User can see the change history of an item's RICE inputs (who, when, old to new)
- [ ] **ROAD-07**: User can export the ranked roadmap as CSV

### Release packages link (added to Phase 17, 2026-10-25)

- [ ] **PKG-01**: User can set, change or clear one packages link (a OneDrive/SharePoint URL) on a release from its detail page, whether the release is OPEN or FROZEN, and open it from there
- [ ] **PKG-02**: The assistant can answer where a release's packages are, returning the stored link

### Product briefings — Claude fallback (Phase 17.1, inserted 2026-10-25)

- [ ] **BRIEF-01**: When Claude's result for a scheduled briefing slot is missing after a grace period, the app produces that briefing itself for each active product not covered by Claude, and skips covered products
- [ ] **BRIEF-02**: Daily Jira digest per product (Sun-Thu): blockers, stale in-progress issues (3+ working days), not-ready stories, changes since the last run, due within 5 days or overdue
- [ ] **BRIEF-03**: Weekly sprint backlog check per product: next sprint or top-ranked backlog items scored by readiness field checks, with what is missing per item
- [ ] **BRIEF-04**: Weekly status update per product: this week's completed, in progress, blocked and newly added work, sprint committed vs done, overdue items, as an executive and an engineering draft
- [ ] **BRIEF-05**: Briefings are computed from Jira fields with no Anthropic dependency; the self-hosted model, when enabled, adds only a headline/summary and its failure never fails a run
- [ ] **BRIEF-06**: A Briefings page shows each task's runs per product, Claude's or the app's, with status, headline, attention count and the full briefing
- [ ] **BRIEF-07**: An admin configures schedules, grace period, enabled tasks and the user whose stored Jira credential the scheduler uses, and can run a task now; a missing credential is recorded as a run status
- [ ] **BRIEF-08**: A read-only Briefings page shows the latest result and run history of all five Claude briefings (including regulation watch and Gulf investment brief) from `briefings/data/<id>.json`; only the five fixed ids are read, oversized or malformed files show a read error and never a server error

### Usage — Analytics

- [ ] **USAGE-01**: An admin can add analytics connections (GA4 service account, or Umami URL + API token) in Settings; secrets are encrypted at rest, never returned after save, and each connection can be tested
- [ ] **USAGE-02**: Each client + product can be mapped to a connection and a GA4 property id or Umami website id; the app only reads from providers and degrades gracefully when one is unreachable or unauthorised
- [ ] **USAGE-03**: A daily sync stores only aggregate daily figures per mapping (active users, sessions, page views, top pages/events), backfilling 90 days on first mapping; no user-, session- or IP-level data is stored
- [ ] **USAGE-04**: User sees a usage panel on client detail (period totals, trend, top pages/events) and a usage column on the portfolio dashboard, labelled approximate, with "last synced" and "No analytics source" states
- [ ] **USAGE-05**: User sees usage around a release: average daily active users and sessions in the 14 days before vs after its install at that client

### AI Deployment Notes — Runbook Recommend

- [ ] **RECO-01**: User can run a Recommend mode on a release's deployment runbook that proposes deploy steps, configuration and database changes, risks and validation checks
- [ ] **RECO-02**: Recommend reads the release's stored Azure DevOps commit messages and PR titles/status (no live ADO call) and shows how many tickets, commits and PRs were included; with no ADO data it is disabled with an explanation
- [ ] **RECO-03**: Every suggestion cites a ticket key, PR id or commit id; the server drops or flags citations not present in the evidence
- [ ] **RECO-04**: A deterministic pre-scan of commit and PR text flags likely migration and configuration changes and passes them to the model as hints
- [ ] **RECO-05**: User reviews the suggestions and saves them as an AI-assisted runbook revision; nothing is saved automatically
- [ ] **RECO-06**: Recommend always runs on the self-hosted model, never an external provider, and resists instructions embedded in commit or PR text (measured on a golden set including injection cases before sign-off)

### Planning — Daily Planner

- [ ] **PLAN-01**: User can open a daily planning page that proposes what to work on today, drawn from releases in flight, pending promotions, documents not ready, clients behind and top RICE items
- [ ] **PLAN-02**: The plan only contains items from the app's own candidate set, each with a one-line reason; unknown items from the model are dropped
- [ ] **PLAN-03**: The page still shows a rule-based plan when the model is unavailable
- [ ] **PLAN-04**: An external OpenAI-compatible provider can be enabled only when the ops allow-list permits its host and a user turns it on in Settings; the API key is write-only and encrypted
- [ ] **PLAN-05**: While the external provider is on, the planner shows a persistent banner naming the host data is sent to, each enable/disable is recorded in the audit trail, and there is no silent fallback between providers
- [ ] **PLAN-06**: Only a minimal, allowlisted set of fields (no commit text, ticket descriptions or document text) is sent to an external provider

### Assistant

- [x] **ASST-11**: User can ask the assistant which release a client runs for a product, and which clients run a given release

### UI — Enhancement & Logo (Phases 23 and 23.1, defined 2026-10-26)

- [ ] **UI-01**: The app is branded "ProductOps" with the tagline "The product delivery platform": a chosen SVG logo (icon + wordmark) and favicon appear in the header, login page and browser tab
- [ ] **UI-02**: User can switch between light and dark themes (defaulting to the OS setting); the choice is remembered per browser and every themed surface stays legible
- [ ] **UI-03**: Colours come from brand and semantic design tokens, and the UI is built from owned shadcn/ui components
- [ ] **UI-04**: Feedback is shown as toasts, loading as skeletons, and motion respects the reduced-motion setting
- [ ] **UI-05**: User can open a command palette (Ctrl/Cmd+K) to jump to releases, clients, products, roadmap items, settings and existing actions
- [ ] **UI-06**: Shared DataTable (sort, filter, sticky header, column choice) and chart components are available to every page
- [ ] **UI-07**: Every existing page is migrated to the tokens and shared components, in the priority order dashboard/portfolio, release detail, roadmap/briefings/settings, then the rest
- [ ] **UI-08**: The dashboard and portfolio show real charts, and dense lists such as the roadmap use the shared DataTable

## Future Requirements

- RICE scatter view and score history charts
- Snoozing or completing daily plan items; persisted plans
- Per-section regenerate in Recommend mode
- Indexing client installations into the RAG collection
- Per-client environment ladders (v2 PROV-02)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Per-client ladders | Breaks the unchanged shared ladder; deferred to v2 |
| Auto-detecting client installs | The app records deployments; it does not observe them |
| Timeline / Gantt / capacity / velocity planning | Beside-Jira positioning; Jira and PM tools own this |
| Writing to Jira or Azure DevOps | Both remain read-only |
| AI-generated RICE inputs | Scores are a TPM judgement; AI would make ranking unaccountable |
| Alternative scoring frameworks or formula editor | A formula engine is an expression-language surface; RICE only |
| Unique visitors, sessions or per-user usage | Decided aggregate-only |
| Third-party analytics (SaaS or self-hosted) | No egress, no new stack for a counter table |
| Auto-saved AI output | Every AI result is reviewed before saving |
| External provider for Recommend, chat or doc assist | Those send commit/ticket/document text; external is planner-only |
| Planner write actions | The planner suggests; it never promotes, installs or edits |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PORT-01 | Phase 15 | Complete |
| PORT-02 | Phase 15 | Complete |
| PORT-03 | Phase 15 | Complete |
| PORT-04 | Phase 15 | Complete |
| PORT-05 | Phase 15 | Complete |
| PORT-06 | Phase 15 | Complete |
| PORT-07 | Phase 15 | Complete |
| PORT-08 | Phase 15 | Complete |
| REL-UNFREEZE-01 | Phase 15.1 | Pending |
| REL-UNFREEZE-02 | Phase 15.1 | Pending |
| REL-UNFREEZE-03 | Phase 15.1 | Pending |
| PROMO-SKIP-01 | Phase 15.1 | Pending |
| PROMO-SKIP-02 | Phase 15.1 | Pending |
| PROMO-SKIP-03 | Phase 15.1 | Pending |
| PDASH-01 | Phase 16 | Pending |
| PDASH-02 | Phase 16 | Pending |
| PDASH-03 | Phase 16 | Pending |
| ASST-11 | Phase 16 | Pending |
| REL-PF-01 | Phase 17.2 | Pending |
| REL-PF-02 | Phase 17.2 | Pending |
| REL-PF-03 | Phase 17.2 | Pending |
| REL-PF-04 | Phase 17.2 | Pending |
| ROAD-01 | Phase 17 | Pending |
| ROAD-02 | Phase 17 | Pending |
| ROAD-03 | Phase 17 | Pending |
| ROAD-04 | Phase 17 | Pending |
| ROAD-05 | Phase 17 | Pending |
| ROAD-06 | Phase 17 | Pending |
| ROAD-07 | Phase 17 | Pending |
| PKG-01 | Phase 17 | Pending |
| PKG-02 | Phase 17 | Pending |
| BRIEF-01 | Phase 17.1 | Pending |
| BRIEF-02 | Phase 17.1 | Pending |
| BRIEF-03 | Phase 17.1 | Pending |
| BRIEF-04 | Phase 17.1 | Pending |
| BRIEF-05 | Phase 17.1 | Pending |
| BRIEF-06 | Phase 17.1 | Pending |
| BRIEF-07 | Phase 17.1 | Pending |
| BRIEF-08 | Phase 17.1 | Pending |
| USAGE-01 | Phase 18 | Pending |
| USAGE-02 | Phase 18 | Pending |
| USAGE-03 | Phase 18 | Pending |
| USAGE-04 | Phase 18 | Pending |
| USAGE-05 | Phase 18 | Pending |
| RECO-01 | Phase 19 | Pending |
| RECO-02 | Phase 19 | Pending |
| RECO-03 | Phase 19 | Pending |
| RECO-04 | Phase 19 | Pending |
| RECO-05 | Phase 19 | Pending |
| RECO-06 | Phase 19 | Pending |
| PLAN-01 | Phase 20 | Pending |
| PLAN-02 | Phase 20 | Pending |
| PLAN-03 | Phase 20 | Pending |
| PLAN-04 | Phase 20 | Pending |
| PLAN-05 | Phase 20 | Pending |
| PLAN-06 | Phase 20 | Pending |
| UI-01 | Phase 23 | Pending |
| UI-02 | Phase 23 | Pending |
| UI-03 | Phase 23 | Pending |
| UI-04 | Phase 23 | Pending |
| UI-05 | Phase 23 | Pending |
| UI-06 | Phase 23 | Pending |
| UI-07 | Phase 23.1 | Pending |
| UI-08 | Phase 23.1 | Pending |

---
*Requirements defined: 2026-10-20*
