# Phase 18: Usage Tracking - Context

**Gathered:** 2026-10-26
**Status:** Ready for research (scope redefined; the 2026-10-22 RESEARCH.md is superseded)

<domain>
## Phase Boundary

Release Management shows how the **products installed in client environments** are actually used, by reading usage data
**read-only** from a third-party analytics provider (Google Analytics 4 or Umami) that those products already report to.
Usage is shown per client and product, next to the installs Phase 15 records.

**Scope change (user, 2026-10-26).** The roadmap's earlier wording was in-app, identity-free page/feature counters for the
Release Management app itself (`usage_counter`, a `POST` ingest endpoint and a frontend `track()`). The user replaced that:
the aim is client-side product usage arriving from an analytics provider. Nothing is captured from Release Management's own
UI, and there is no ingest endpoint.

In scope:
- the provider connection and the mapping
- a daily sync into Postgres
- a client detail usage panel
- a portfolio dashboard usage column
- usage before and after a release install
- top pages/events per client

Out of scope:
- instrumenting the client products themselves. That is each product's own work; this app only consumes the data.
- analytics of the Release Management app itself
- writing anything to GA4 or Umami
</domain>

<decisions>
## Implementation Decisions

### Providers
- **D-18-01:** One analytics-provider port, modelled on the ticket-provider and ADO pattern, with two adapters:
  - **GA4:** Google Analytics Data API, authenticated by a service account.
  - **Umami:** Umami REST API, base URL plus API token.
- **D-18-02:** Read-only against both providers, like Jira (constraint).
- **D-18-03:** If a provider is unreachable, unauthorised or misconfigured, it must degrade gracefully, like ADO enrichment. Pages show the last synced data with a "last synced"/problem note, and nothing else in the app fails.

### Connections and mapping
- **D-18-04:** Settings has a list of **analytics connections**. Examples: "eVision GA4" holding the service-account JSON, or "Client X Umami" holding a URL and API token. The rules:
  - Each has a name and a provider type.
  - Secrets are encrypted at rest with the existing credential encryption (`EncryptedTokenConverter` / `RM_CREDENTIAL_KEY`).
  - Secrets are never returned after save, following the locked decision that credential writes must not decrypt.
  - Each connection has a **Test** button.
- **D-18-05:** A mapping is made **per client + product**, building on Phase 15's `product_client` link. It picks a connection plus the provider's id, either a GA4 property id or an Umami website id.
  - It can be removed.
  - A client-product with no mapping shows "No analytics source", not zeros.
- **D-18-06:** Phase 21 (Integration Settings) will later fold analytics connections into its integrations screen. Phase 18 builds the connections list so that move is possible, without waiting for Phase 21.

### Sync
- **D-18-07:** A **daily scheduled sync** pulls daily totals into Postgres. Pages read only from the DB and never call the provider while rendering.
- **D-18-08:** When a mapping is first created, the sync **backfills 90 days**. After that it fetches incrementally and re-fetches the last few days, because providers revise recent days.
- **D-18-09:** Store aggregate daily figures only, per mapping per day: active users, sessions, page views, and the top pages/events with their counts. Never store user-level, IP or session-level data from the provider.

### Display
- **D-18-10:** A **client detail usage panel** for each mapped product shows active users, sessions and page views for a chosen period, with a trend. It sits beside the client's currently installed release.
- **D-18-11:** A **portfolio dashboard usage column** shows a usage figure per client/product in the Phase 16 Portfolio tab, so low-usage or dormant clients stand out.
- **D-18-12:** **Usage around a release:** average daily active users and sessions in the **14 days before vs the 14 days after** the release's install date at that client (Phase 15 install ledger, effective SUCCESS installs). It shows "not enough data yet" until 14 days after the install have passed.
- **D-18-13:** **Top pages / events:** the client's most-used screens and features as reported by the provider, for the chosen period.

### Claude's Discretion
- The sync schedule time and zone. Africa/Cairo is suggested, to match the briefings scheduler.
- The period options on the panel. 7/30/90 days is suggested.
- How many top pages/events to store and show.
- The table design. A new Flyway migration is needed. V17 was pre-assigned but is now out of order (the DB is at V19 and `FlywayConfig` enables outOfOrder), so the planner chooses V17 or the next free number.
- Which portfolio figure the column shows, for example 30-day active users with a trend arrow.
- How the GA4 service-account JSON is entered: upload or paste.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

- `.planning/ROADMAP.md`: Phase 18 (goal and success criteria rewritten 2026-10-26 to this scope)
- `.planning/REQUIREMENTS.md`: USAGE-01..05 (rewritten 2026-10-26)
- `.planning/phases/18-usage-tracking/18-RESEARCH.md`: **superseded**. It covers the old in-app counter design. Only its notes on the Phase 16 dashboard tab param are still relevant.
- `.planning/phases/15-products-clients-installations/15-CONTEXT.md`: clients, products, `product_client` and the install ledger ("current release" = latest effective SUCCESS by installed_at)
- `.planning/phases/16-portfolio-dashboard-assistant-answers/16-CONTEXT.md`: the Portfolio tab and the URL-param tabs (D-16-01)
- `src/main/resources/db/migration/V14__portfolio.sql`: product, client, product_client, client_deployment
- `src/main/java/com/evision/releasemgmt/ports/AdoRepositoryPort.java`, `AdoCredentialRepository.java`: the optional, read-only, degrade-gracefully integration pattern
- `src/main/java/com/evision/releasemgmt/adapters/persistence/crypto/EncryptedTokenConverter.java`: credential encryption
- `src/main/java/com/evision/releasemgmt/config/FlywayConfig.java`: outOfOrder(true)
- Phase 17.1 briefings scheduler (`BriefingScheduleService`, `BriefingSlots`): the in-app scheduling pattern with zone and an overlap guard
- External: GA4 Data API `runReport` (properties/{id}:runReport, service-account auth, quotas); Umami API `/api/websites/{id}/stats`, `/pageviews`, `/metrics` (token auth). The researcher should verify current versions and quotas.
</canonical_refs>

<specifics>
## Specific Ideas
- The user expects this to be "a little tricky". The tricky parts to research:
  - GA4 service-account access, which the client or eVision must grant on each property
  - GA4 quota limits per property
  - Umami Cloud vs self-hosted API differences
  - Mapping a client inside a shared GA4 property, if clients share one. This is not chosen; per client+product mapping assumes separate properties/websites.
- Usage numbers are approximate (ad blockers, consent, provider sampling). Label them so in the UI.
</specifics>

<deferred>
## Deferred Ideas
- Analytics of the Release Management app itself (the original Phase 18 idea), possibly self-hosted Umami. Not planned.
- Clients sharing one GA4 property and being told apart by a custom dimension.
- Moving connections into Phase 21's integration settings (D-18-06).
</deferred>
</content>
