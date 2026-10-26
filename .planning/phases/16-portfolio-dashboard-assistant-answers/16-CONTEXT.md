# Phase 16: Portfolio Dashboard & Assistant Answers - Context

**Gathered:** 2026-10-22
**Status:** Ready for planning

<domain>
## Phase Boundary

The dashboard shows the portfolio built in Phase 15. That means releases summarised by product, a product filter, a client x product matrix of what each client runs, and which clients are behind the newest installable release of their product. The assistant can answer which release a client runs for a product, and which clients run a given release (PDASH-01..03, ASST-11). The phase also fixes the existing "Need paperwork" and "Stale" tiles, which the user found unclear.

Not in scope: recording or correcting installs (Phase 15 owns that, from release detail only), usage tracking (Phase 18), and the daily planner (Phase 20, which consumes "clients behind").

</domain>

<decisions>
## Implementation Decisions

### Dashboard layout and product filter
- **D-16-01:** The dashboard gets two tabs. **Releases** is today's view: summary strip, release matrix and deployment history. **Portfolio** holds the new views. The selected tab is a URL search param, like the existing `all` flag.
- **D-16-02:** The product filter narrows **everything**: the Releases tab tiles, the release matrix, the deployment history and the Portfolio tab views. It lives in the URL, so it survives a reload and a shared link. A release in two selected products is counted once in every total (PDASH-01; needs a fixture-backed test).
- **D-16-03:** The filter is **multi-select**. Selecting nothing means all products.
- **D-16-04:** "Releases grouped by product" is a **per-product summary table**. Each product has one row with these columns:
  - releases open / frozen / in Production;
  - the newest frozen release;
  - clients on the newest frozen release vs linked clients.

  Clicking a product row sets the filter to that product.

### Client x product matrix
- **D-16-05:** A cell shows:
  - the release name/version, linking to release detail;
  - the installed date;
  - an amber "not in Production" marker when that release has not reached Production;
  - a "behind" marker (see D-16-09..12).

  These are the same facts as Phase 15's client detail runs table. "Currently runs" comes from the `current_client_release` view (D-15-05) and is not re-derived.
- **D-16-06:** There are two empty states:
  - client not linked to the product: blank or a dash;
  - linked but no effective successful install: "Not installed" (consistent with D-15-12).
- **D-16-07:** Archived clients and products are **hidden by default**. A "Show archived" checkbox brings them back, labelled "archived", as on the Portfolio page (D-15-07).
- **D-16-08:** The client name opens Portfolio > Clients > that client, i.e. the existing client detail with runs and install history. The release in a cell links to the release detail page. No inline popover.

### What "behind" means
- **D-16-09:** The comparison target is the **newest FROZEN release that contains the product**, whether or not it has reached Production. Installs are allowed from FROZEN (Phase 15), so this is the newest release a client could install.
- **D-16-10:** "Newest" is ordered **by frozen date**, not by version. Versions are free text and don't sort reliably.
- **D-16-11:** The view shows **how far behind**: "N releases behind". N is the number of frozen releases of that product that are newer than the one the client runs. The newest release's name is shown too.
- **D-16-12:** Linked clients with nothing installed form a **separate "Not installed" group** in the behind view. They are not counted as behind.

### Summary tiles
- **D-16-13:** The "Need paperwork" tile is fixed in three ways:
  - renamed to **"Releases missing documents"**;
  - given a help tooltip that states the rule (a document not generated yet or stale; document types with no template are ignored);
  - made **clickable**: clicking it filters the release matrix to those releases. The current rule is `paperworkCounts` in `frontend/src/api/dashboard.ts:149`. This reverses the Phase 8 UI-SPEC rule "tiles are plain numbers, no filtering".
- **D-16-14:** "Stale" gets the same treatment:
  - renamed to **"Documents out of date"**;
  - given a tooltip: tickets changed after the document was generated, and frozen releases never go stale (D-05-09);
  - made click-to-filter.
- **D-16-15:** The Portfolio tab gets four tiles, all respecting the product filter:
  - **Clients behind**: client/product pairs behind (D-16-09..11); clicking it shows the behind list.
  - **Not installed**: linked pairs with no successful install.
  - **Active products / clients**: counts.
  - **Installs last 30 days**: effective SUCCESS installs.

### Assistant tool (decided after research)
- **D-16-16:** ASST-11 is a **new `installs` tool**. rag-service's tool-intake cap rises from 5 tools / 1500 definition characters to 6 tools and about 1900 characters. That change touches four places: rag-service `application.yml`, its test `application.yml`, `ToolPropertiesTest`, and the host `ToolDefinitionsTest`. rag-service is redeployed with or before the app. rag-service prompt text is NOT edited, so `promptHash` stays the same and no golden rerun is needed. The answer shape is steered by result fields (`state`, `inProduction`). The fallback of extending the `releases` tool was rejected by the user.

### Claude's Discretion
- **ASST-11 (not discussed; user accepted the default):** add an assistant tool that reads `current_client_release` and the ledger **live**, not the search index, so its answers always match the dashboard. Phase 12 kept the tool set consolidated, so either extend an existing tool or add one, whichever fits that design. It returns structured fields only, like the other tools. "Which clients run release X" uses `ReleaseRef` resolution.
- How a release that is frozen but later rolled back in promotion is treated as a "behind" target. The planner decides and states it; the expected default is that it still counts while it stays frozen.
- Tooltip mechanism, tile styling, and how the click-to-filter state is shown and cleared (e.g. a chip above the matrix).
- Whether the per-product summary and the matrix come from one dashboard endpoint or separate ones.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope and requirements
- `.planning/ROADMAP.md` §"Phase 16: Portfolio Dashboard & Assistant Answers": goal and success criteria
- `.planning/REQUIREMENTS.md`: PDASH-01, PDASH-02, PDASH-03, ASST-11
- `.planning/PROJECT.md`: beside-Jira positioning ("dashboards here must answer what Jira structurally cannot")

### Phase 15 (data this phase reads)
- `.planning/phases/15-products-clients-installations/15-CONTEXT.md`: D-15-05 (currently-runs rule), D-15-07 (archived), D-15-12 ("Not installed"), D-15-16 (ROLLED_BACK does not change current)
- `.planning/phases/15-products-clients-installations/15-UI-SPEC.md`: Portfolio page and client detail visual language to reuse
- `src/main/resources/db/migration/V14__*.sql`: `effective_client_deployment` and `current_client_release` views, which are the single home of the D-15-05 rule

### Existing dashboard (Phase 8)
- `.planning/milestones/v1.0-phases/08-release-insights-dashboard-the-cross-release-views-jira-stru/08-CONTEXT.md`: D-08-xx dashboard decisions (visible-release window, rolled-back visibility, ship date)
- `.planning/milestones/v1.0-phases/08-release-insights-dashboard-the-cross-release-views-jira-stru/08-UI-SPEC.md`: tile and matrix contract that D-16-13/14 amend

### Assistant tools (Phase 12)
- `.planning/milestones/v1.1-phases/12-releasemanagement-as-first-client/12-CONTEXT.md`: tool design (consolidated tools, structured fields only, ReleaseRef)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `frontend/src/routes/DashboardRoute.tsx`: `SummaryStrip`, `Tile`, `ReleaseMatrix`, `DeploymentHistory`. The tabs wrap these.
- `frontend/src/api/dashboard.ts`: `paperworkCounts` (rule for D-16-13/14), `useDashboardReleases`, `readinessIndex`
- `frontend/src/routes/PortfolioRoute.tsx`, `frontend/src/components/ClientDetailPanel.tsx`, `PortfolioRegisterPanel.tsx`: client detail runs table (same cell facts as D-16-05), "Show archived" pattern, and the link target for D-16-08
- Phase 15 React Query hooks (`useProducts`, `useClients`, `useClient`, `useInstalls`)
- `src/main/java/com/evision/releasemgmt/application/DashboardService.java`, `adapters/web/DashboardController.java`: dashboard read model to extend with product filtering
- `src/main/java/com/evision/releasemgmt/application/assistant/tools/` (`ToolRegistry`, `DashboardTool`, `DeploymentsTool`, `ReleaseRef`): assistant tool pattern

### Established Patterns
- Dashboard search params live in the URL (`useSearch({ from: '/authenticated/' })`, `replace: true` navigation).
- Readiness is fetched independently, so a readiness failure only blanks the document cells (T-05-60). Click-to-filter on the paperwork tiles must keep that degradation.

### Integration Points
- The `current_client_release` view (V14) feeds the matrix, behind and the ASST-11 tool.
- The release list/summary needs product ids so the product filter can apply to the Releases tab and deployment history.

</code_context>

<specifics>
## Specific Ideas

- The user did not understand what "Need paperwork" reflects. The new caption plus tooltip must explain it without a manual.
- "Behind" should read like "2 releases behind: newest is Release 4.5".

</specifics>

<deferred>
## Deferred Ideas

- Added to the roadmap during this session (not Phase 16 scope): Phase 21 License Keys and Phase 22 GitHub Publishing & Hosted Deployment (commit 8989a71).
- AI deployment notes from commits is Phase 19, unchanged.

</deferred>

---

*Phase: 16-portfolio-dashboard-assistant-answers*
*Context gathered: 2026-10-22*
