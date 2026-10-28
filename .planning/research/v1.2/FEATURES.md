# Feature Landscape: v1.2 Portfolio & Planning

**Domain:** Internal release-management tool, adding portfolio tracking, RICE roadmap, privacy-friendly usage analytics, AI deployment notes, daily planning assistant
**Researched:** 2026-10-20
**Overall confidence:** MEDIUM. Behaviour descriptions come from the author's knowledge of ProductBoard, Aha!, Jira Product Discovery, Octopus, LaunchDarkly, Plausible and Umami. No live web verification was done in this session. The RICE formula is the standard Intercom definition (HIGH). Tool-specific UI details are MEDIUM and should be spot-checked before they are cited.

Positioning rule applied throughout: the tool sits BESIDE Jira. No velocity, burndown or cycle time. The roadmap is a prioritisation list, not a delivery tracker.

---

## 1. Products and clients (release-to-customer tracking)

### How it works in the wild
- Octopus models tenants (customers) alongside projects and environments. A release is deployed to an environment, optionally per tenant. The dashboard shows a matrix of tenant by environment with the version currently deployed, plus deployment history.
- LaunchDarkly tracks exposure per environment and per segment or context, which is not an install record. The transferable idea is "what does customer X see right now".
- Customer portals and CS tools show "customer X is on version Y since date Z", with a "behind latest" indicator.
- Common core model: Product 1..N Client, Client has an installation record (release, installed-at, optionally recorded-by), and the current version is the latest installation record per client and product.

### Table stakes

| Feature | Why expected | Complexity | Depends on existing |
|---------|--------------|------------|---------------------|
| Product CRUD (name, optional key) | Needed before anything else can be recorded | Low | New table; Flyway V14+ |
| Client CRUD, each belonging to a product | A product has several clients | Low | Product |
| Release to products link (a release covers several products) | Stated requirement | Low-Med | Existing Release entity. Many-to-many join table. |
| Record installation: client, release, installed-at date, optional note | Core of the feature. Back-dating must be allowed because installs are recorded after the fact. | Med | Release, Client. Should respect that the release exists and covers that product. |
| Current-version-per-client view (latest installation wins) | The question being asked is "what does each client run" | Low | Derived by query, not stored. Avoids drift. |
| Installation history per client (audit-style, append-only) | Matches the existing audit-trail ethos | Low | Reuse the audit pattern |
| Dashboard: releases by product | Stated requirement | Med | Existing insights dashboard and its query layer |
| Dashboard: client-by-product matrix with current release and install date | Octopus-style tenant matrix | Med | Same |
| Validation: a client can only install a release covering its product | Prevents bad data | Low | Release-product link |
| Shared ladder unchanged | Stated constraint. Client install is a separate fact from environment promotion. | Low (discipline) | Existing ladder and promotions must not be altered |

### Differentiators (cheap ones worth including)

| Feature | Value | Complexity | Notes |
|---------|-------|------------|-------|
| "Behind latest" badge: how many releases behind or days since latest PROD release | Highest-value signal for a release manager | Low-Med | Derived from release ordering per product |
| Install only allowed once the release has reached the production-flagged stage (warn, not block) | Keeps data honest and uses existing ladder | Low | Existing stage flag |
| Bulk record: install release R for a set of clients on one date | Rollouts hit many clients at once. Without this, data entry is the main friction. | Med | |
| Filter existing release list and documents by product | Natural cross-link | Low | |
| Feed client install facts into the RAG assistant ("which clients are on 2.4?") | Strong v1.1 synergy | Med | rag-service ingestion sources (currently 4). Deferrable. |

### Anti-features

| Anti-feature | Why avoid | Instead |
|--------------|-----------|---------|
| Per-client environment ladders or per-client promotion state | Contradicts "shared ladder unchanged"; Octopus-style tenant-by-environment is a large model | Single install record per client and release |
| Automatic detection of installs (polling client systems, CI/CD hooks) | Out of scope: the tool records, it never integrates with delivery | Manual or bulk entry |
| Client-facing portal or login | Single internal permission level | Internal views only |
| Feature-flag or entitlement management per client (LaunchDarkly) | Different product | Not built |
| Contract, billing or SLA data on clients | CRM scope creep | Name, optional external ref only |
| Hard-deleting clients or products that have history | Breaks audit trail | Archive flag |

---

## 2. RICE roadmap

### How it works in the wild
- RICE (Intercom): Score = (Reach x Impact x Confidence) / Effort.
  - Reach: people or events per time period (number).
  - Impact: discrete scale 3 = massive, 2 = high, 1 = medium, 0.5 = low, 0.25 = minimal.
  - Confidence: percentage, usually 100 / 80 / 50.
  - Effort: person-months (or points), the denominator.
- ProductBoard: a configurable prioritisation score on features, a sortable column, and a drivers-versus-effort view. Aha!: a scorecard with custom metrics and a ranked list, including RICE as a preset. Jira Product Discovery (JPD): "Ideas" with number fields or formula fields. RICE is built by hand, and the view is sorted or grouped by the score. A matrix view plots value against effort.
- Common pattern: the score is computed live and ranks are never stored as a static truth. Items with a missing input show as unscored and sort last. Effort of zero is blocked to avoid divide by zero.

### Table stakes

| Feature | Why expected | Complexity | Depends on existing |
|---------|--------------|------------|---------------------|
| Roadmap item entity: title, description, source (JIRA or MANUAL), optional Jira key, R, I, C, E | Core | Med | New tables. Reuse the existing manual-item pattern from release assembly. |
| Import Jira items by picking tickets or a JQL or project/sprint filter | Stated requirement | Med | Existing ticket provider abstraction, `/search/jql` cursor pagination, existing ticket picker UI. Read-only. |
| Jira-sourced fields refreshed (summary, status), RICE inputs stay local | Jira is read-only, so scores live only in this DB | Med | Existing refresh and stale-detection logic |
| Manual item create, edit, delete | Stated requirement | Low | |
| Score computed server-side, ranked list sorted by score descending | Stated requirement | Low | Pure function; unit-test the formula. BigDecimal with fixed rounding. |
| Inputs validated: Impact limited to the five allowed values, Confidence 0-100, Effort > 0, Reach >= 0 | Garbage scores otherwise | Low | |
| Unscored items shown last with a visible "incomplete" state | Standard tool behaviour | Low | |
| Inline editing of the four inputs in the table | A TPM scoring 30 items will not tolerate a modal per item | Med | Frontend table pattern |
| Tie-break rule that is stable and visible (for example, then by Confidence, then title) | Ranking jitter erodes trust | Low | |
| Done or archived items hidden by default | Roadmap hygiene | Low | |

### Differentiators

| Feature | Value | Complexity | Notes |
|---------|-------|------------|-------|
| Reach suggestion from usage tracking or client counts ("this product has 14 clients") | Unique to this tool; ties features 1, 2 and 3 together | Med | Optional hint only, never auto-written. Depends on feature 1. |
| Link a roadmap item to a product (and optionally a release once shipped) | Closes the loop from plan to shipped | Low-Med | Feature 1 |
| Score history or change log per item | Explains why ranking moved | Med | Defer if time-boxed |
| Value-versus-effort scatter view | Common in ProductBoard and JPD | Med | Defer |
| Per-item rationale text field | Cheap, used in review meetings | Low | |
| Export ranked list to CSV | TPMs paste into slides | Low | |
| Configurable impact scale labels | Minor | Low | Prefer fixed |

### Anti-features

| Anti-feature | Why avoid | Instead |
|--------------|-----------|---------|
| Write scores back to Jira | Jira is read-only by constraint | Keep local; show the Jira key as a link |
| Multiple scoring frameworks (WSJF, ICE, MoSCoW, custom formulas) | Scope creep; a formula engine is an expression-language risk | RICE only |
| Timeline, Gantt or quarter-lane roadmap with dates | Becomes a Jira Advanced Roadmaps clone | Ranked list only |
| Voting, customer feedback portal, idea intake (ProductBoard/JPD insights) | Different product | Not built |
| Capacity planning, sprint planning, velocity | Violates positioning rule | Not built |
| Auto-AI-scoring of RICE inputs | LLM numbers look authoritative and are not; small 4 GB model | Human-entered only |
| Multi-user per-person score averaging | Single permission level | One score per item |

---

## 3. Usage tracking (privacy-friendly)

### How it works in the wild
- Plausible and Umami: a tiny script sends pageview and custom events (`name` plus optional props). No cookies, no persistent identifier. Plausible derives a daily-rotating hash from IP, user agent and domain with a salt that is discarded, to count uniques without storing identity. Dashboards show top pages, event counts and breakdowns by property, over time ranges.
- For an internal tool the simplest honest design is stronger than either: store only counters keyed by (day, event type, page or feature name), with no user id, session id, IP or user agent. Unique-visitor counts are then deliberately not offered.

### Table stakes

| Feature | Why expected | Complexity | Depends on existing |
|---------|--------------|------------|---------------------|
| Page-view event on route change | Basic | Low | React Router/TanStack Router hook in the SPA |
| Feature-action events (for example "export PDF", "promote", "ask assistant", "AI revise") from a fixed allowlist | The user wants a usage matrix of features | Low-Med | Instrument existing actions |
| Ingest endpoint that accepts only an allowlisted event name and increments a counter row per (day, event) | Aggregate-only storage is the privacy guarantee | Low-Med | New endpoint. Upsert counter (`INSERT ... ON CONFLICT DO UPDATE`). The session user must not be read or logged here. |
| No user identity stored or logged for these events, including in access logs and audit | Stated requirement | Med (discipline) | Existing audit trail and request logging must not capture the user for this endpoint. Check actuator and any access-log filter. |
| Dashboard usage matrix: rows = features/pages, columns = time buckets (day, week), cells = counts | Stated requirement | Med | Existing dashboard and charting |
| Date-range selector on the matrix | Standard | Low | |
| Fire-and-forget client send that never blocks UI or surfaces errors | Analytics must not degrade the app | Low | Use `navigator.sendBeacon` or a swallowed fetch |
| Sane retention (aggregate rows are tiny; optionally prune after N months) | Hygiene | Low | |

### Differentiators

| Feature | Value | Complexity | Notes |
|---------|-------|------------|-------|
| Unused-feature highlighting (zero count in the range) | Answers "what should we cut" | Low | Derived |
| Trend sparkline per row | Easy win | Low-Med | |
| Event properties limited to a closed enum (for example document type) | Richer matrix without identity risk | Med | Allowlisted values only |
| Visible privacy note on the page ("counts only, no user data") | Builds trust | Low | |
| Feed into RICE Reach hint | Cross-feature value | Med | Feature 2 |

### Anti-features

| Anti-feature | Why avoid | Instead |
|--------------|-----------|---------|
| Session replay, heatmaps, click tracking | Privacy and bloat | Not built |
| Unique-visitor or per-user funnels | Needs identity or hashing; reintroduces re-identification risk | Counts only |
| Free-form event names or properties from the client | Cardinality blow-up and an injection or PII-leak vector | Server-side allowlist |
| Third-party analytics (Plausible, Umami, Google) | Extra infrastructure and data leaves the machine | Own counters |
| Recording URL parameters or ids (release ids, ticket keys) | Can identify content and people | Route templates only (`/releases/:id`) |
| Cookies or local-storage ids | Not needed for counts | None |
| Usage by role or person | Single permission level, and it would break the no-identity rule | None |

---

## 4. AI deployment notes (runbook "Recommend" mode)

### How it works in the wild
- AI release-note and change-summary tools (GitHub auto-generated release notes, GitLab and Azure DevOps AI summaries, Copilot PR summaries) take commit messages and PR titles, group them, and draft prose. Deployment-focused variants additionally look for signals: migration or schema files, config or env changes, feature flags, dependency bumps, "breaking" keywords.
- The reliable pattern: the model is given structured input and must return structured output in fixed sections, a human reviews and edits, and the result is saved. Good tools show the evidence behind each suggestion. Weak ones present hallucinated steps as fact.
- Existing v1.1 assets this builds on: the AI document assist (refinement saved as an AI-assisted revision) and the rag-service. This feature is a new mode of the same flow, with source data from ADO commits and PRs already enriched on tickets.

### Table stakes

| Feature | Why expected | Complexity | Depends on existing |
|---------|--------------|------------|---------------------|
| "Recommend" mode in the runbook editor, alongside the existing assist/refine mode | Stated requirement | Med | v1.1 AI document-assist UI and rag-service endpoint |
| Input: commit messages plus PR titles and status for the release, grouped per ticket | Stated requirement | Med | Existing ADO enrichment data. Must tolerate tickets with no ADO link (degrade gracefully, state "no code detail for N tickets"). |
| Output in four fixed sections: deploy steps, config/DB changes, risks, validation checks | Stated requirement | Med | Prompt and output schema in rag-service |
| Evidence references per suggestion (ticket key, PR id or commit sha) | Lets the reviewer verify, mitigating hallucination | Med | Citation machinery from v1.1 chat |
| Review before saving: edit, accept or reject per item, then save as a revision flagged AI-assisted | Stated requirement | Med | Existing revision model |
| Prompt-injection hardening: commit messages and PR titles delimited as untrusted data; output is text only and never triggers tools | Project context calls this out | Med | Existing v1.1 injection handling |
| Honest "insufficient data" output when commits are sparse | Prevents invented steps | Low-Med | Prompt design |
| Bounded input size (cap commits and PRs, truncate with a visible note) | Dev model has a small context on a 4 GB GPU | Med | Chunking and token budget logic from the RAG work |
| Clear banner: "AI draft from commit messages, verify before use" | Expectation setting | Low | |
| Failure state when Ollama is down or times out | Standard | Low | Existing handling |

### Differentiators

| Feature | Value | Complexity | Notes |
|---------|-------|------------|-------|
| Deterministic pre-scan for signals (files named migration, `*.sql`, `application*.yml`, "flyway", "env", "feature flag") fed to the model as hints | Much higher accuracy on a small model than free reading | Med | ADO commit file paths may not be fetched currently. Check the enrichment data; fetching changed files is a new ADO read call. Flag for phase research. |
| Merge suggestions into the existing runbook template placeholders or an appended section | Keeps the document consistent | Med | Template engine is the high-risk surface; insert as plain text, never as template source |
| Per-section regenerate | Cheap iteration | Low-Med | |
| Diff view against the previous revision | Already partly present from v1.1 revisions | Low | |
| Rollback hints section | Often wanted in runbooks | Low | Prompt addition only; stays within the four requested sections plus risks |

### Anti-features

| Anti-feature | Why avoid | Instead |
|--------------|-----------|---------|
| Auto-saving AI output without review | Stated requirement is reviewed then saved; unreviewed deploy steps are dangerous | Explicit save |
| Executing or generating runnable scripts or commands | Tool never deploys or triggers | Prose steps only |
| Fetching source diffs or file contents into prompts | Large, sensitive and out of stated input scope | Commit messages and PR titles/status only (changed-file names are a maybe, see above) |
| Sending this data to an external provider | Feature 4 follows the Ollama-only rule; the external option is scoped to the daily planner | Local model only |
| Treating output as an approval artifact or auto-filling the approval document | Liability | Runbook only |
| Writing back to ADO or Jira | Read-only | None |

---

## 5. Daily planning assistant

### How it works in the wild
- Tools such as Motion, Sunsama, Reclaim, Linear/Jira "my work" digests, and Copilot-style "what should I do today" prompts propose a short ordered list from tasks, deadlines and calendar. Good ones keep the list short (3-7 items), give a one-line reason per item, link each item to the underlying object, and allow dismissal.
- For an internal ops tool the strongest approach is deterministic candidate generation (rules over real data), with the LLM only ranking and phrasing the explanation. This avoids fabricated tasks.

### Table stakes

| Feature | Why expected | Complexity | Depends on existing |
|---------|--------------|------------|---------------------|
| Page showing a short proposed list with a one-line reason per item and a deep link to the release, document or roadmap item | Stated requirement | Med | Routing |
| Inputs gathered server-side from real data: releases in flight, pending promotions, docs not ready, RICE top items | Stated requirement | Med | Existing release, promotion and document-status queries; RICE ranking (feature 2). Handle the case where roadmap data is absent. |
| Deterministic candidate list built first; the model orders and explains it and may not add items | Prevents hallucinated work | Med | Prompt and post-validation: every item must map to a known candidate id, otherwise drop it |
| Works with no model (fallback: the deterministic list in a rule-based order) | Ollama down must not blank the page | Low-Med | |
| Provider setting: Ollama default; admin may enable an external OpenAI-compatible endpoint (base URL, key, model) | Stated requirement | Med-High | Settings page; credential storage; rag-service client abstraction. The key must be encrypted and write-only, reusing the existing credential pattern (write must not decrypt). |
| Clear off-machine disclosure: a banner on the page whenever external is active, plus a confirm step when an admin enables it, listing what data categories are sent | Stated requirement | Low-Med | |
| Off by default; Ollama remains the default after reset | | Low | |
| Minimise what is sent externally (titles, statuses, counts only; no document bodies, no commit messages) | Data-protection sanity | Med | Payload builder with an explicit allowlist, and a test that asserts its shape |
| Regenerate button and a generated-at timestamp | Staleness | Low | |
| Only on demand or cached per day, not on every page load | A 4 GB GPU is slow | Low-Med | Simple cache keyed by date and input hash |

### Differentiators

| Feature | Value | Complexity | Notes |
|---------|-------|------------|-------|
| Reason chips such as "PROD promotion pending 3 days" backed by data | Builds trust and is mostly deterministic | Low-Med | |
| Dismiss or snooze an item for today | Mild but useful | Low-Med | Needs persisted state; without user identity, store per app and not per person |
| Show "inputs used" and "sent to: local/external" footer | Transparency | Low | |
| Plan items link to the Recommend mode when a runbook is missing | Ties features 4 and 5 together | Low | |

### Anti-features

| Anti-feature | Why avoid | Instead |
|--------------|-----------|---------|
| Calendar, meeting or time-block scheduling | Different product | A list only |
| Per-person plans or workload balancing | Single permission level; no velocity or capacity | One shared plan |
| Items the model invented, not traceable to a candidate | Hallucination risk | Post-validate against candidate ids |
| Sprint or Jira-board planning, velocity, burndown | Violates positioning | Not built |
| Silent external fallback when Ollama fails | Would send data off machine without consent | Show an error or the deterministic list |
| Pushing notifications, email or Slack digests | Scope creep | In-app page only |
| Any write action from the plan (promote, install) | Assistant is read-only | Links only |

---

## Feature Dependencies

```
Products + Clients (1)  ->  Client matrix on dashboard
Products (1)            ->  RICE item to product link (2, optional)
Client counts (1)       ->  RICE Reach hint (2, differentiator)
Usage counters (3)      ->  RICE Reach hint (2, differentiator)
Existing ADO enrichment ->  AI deployment notes (4)
Existing v1.1 AI assist + revisions -> AI deployment notes (4)
RICE ranking (2)        ->  Daily plan inputs (5)
Existing releases/promotions/doc status -> Daily plan (5)
Provider/credential settings (5) -> reuses existing encrypted credential pattern
```

Independent and parallelisable: 1, 3 and 4. Feature 2 is independent of 1 except for optional links. Feature 5 is last, because it consumes 2 and benefits from everything else.

## Existing-code touch points

| New feature | Touches |
|-------------|---------|
| 1 | Release entity (product link), insights dashboard queries and charts, Flyway migrations, possibly rag-service ingestion |
| 2 | Ticket provider interface (search by JQL or picker), manual-item pattern, new route and nav entry |
| 3 | SPA router hook, new unauthenticated-identity-free endpoint, request logging and audit filters, dashboard |
| 4 | rag-service (new mode and prompt), runbook editor UI, revision model, ADO enrichment data |
| 5 | rag-service provider abstraction, Settings (admin), credential encryption, new page |

## MVP Recommendation

Prioritise (in this order):
1. Products, clients and release-product link, install recording, current-version matrix (table stakes of 1). Everything else wants this data.
2. RICE items, Jira import, scoring and ranking (table stakes of 2).
3. Usage counters, allowlisted events, matrix (table stakes of 3).
4. Recommend mode with fixed four sections, evidence references and review-then-save (table stakes of 4).
5. Daily plan with a deterministic candidate list, an Ollama-ranked order, and the external provider gated and disclosed (table stakes of 5).

Cheap differentiators worth taking: "behind latest" badge (1), bulk install (1), CSV export (2), unused-feature highlighting (3), deterministic signal pre-scan (4), reason chips (5), and the cross-links between features.

Defer: scatter view and score history (2), RAG ingestion of client installs (1), per-section regenerate (4), snooze (5).

## Risks to flag for phase research

- Feature 3: confirm no existing request logging or audit mechanism attaches the user to the ingest call. This is the most likely way to break the "no identity" promise.
- Feature 4: confirm what the ADO enrichment currently stores (commit message only, or also changed paths) before promising config/DB detection. A small 4 GB-GPU model has weak long-context quality, so input caps and structured output need measuring.
- Feature 5: the external provider is the only path that sends data off the machine. Needs an explicit data allowlist, an SSRF check on the admin-supplied base URL, and encrypted key storage.
- Feature 2: Jira import must go through the existing provider interface and the `/search/jql` cursor pagination. Do not read the story-point or sprint fields beyond what exists.

## Sources

- RICE definition: Intercom, "RICE: Simple prioritization for product managers" (HIGH, standard formula).
- Product behaviour (ProductBoard, Aha!, Jira Product Discovery, Octopus tenants, LaunchDarkly, Plausible, Umami): author's knowledge of public docs, not re-fetched this session (MEDIUM).
- Project context: `D:\IdeaProjects\ReleaseManagement\.planning\PROJECT.md`.
