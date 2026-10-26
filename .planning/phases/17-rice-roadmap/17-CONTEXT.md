# Phase 17: RICE Roadmap - Context

**Gathered:** 2026-10-22
**Status:** Ready for planning

<domain>
## Phase Boundary

A technical product manager keeps a roadmap of items. Items come from Jira or are entered by hand. Each item is ranked by an app-computed RICE score: reach x impact x (confidence/100) / effort, computed in Java and never stored. The phase covers:
- adding items;
- setting RICE inputs with a visible rubric;
- the ranked table;
- Jira refresh that never overwrites local inputs;
- an optional product link with a product filter;
- per-input change history;
- CSV export (ROAD-01..07);
- a release packages link, a OneDrive/SharePoint URL shown on release detail and answerable by the assistant (PKG-01/02, D-17-19..24, added 2026-10-25).

Flyway V16 is pre-assigned. Jira stays read-only.

Not in scope: planning views by quarter, the Phase 20 daily planner (which later consumes the top-ranked items), and writing anything back to Jira.

</domain>

<decisions>
## Implementation Decisions

### RICE units and rubric
- **D-17-01:** **Reach = number of clients affected.** It is entered by hand as a whole number of 0 or more.
- **D-17-02:** **Effort = person-weeks.** Decimals are allowed (e.g. 0.5) and the value must be above 0.
- **D-17-03:** **Confidence uses fixed steps**: High 100%, Medium 80%, Low 50% (Intercom's rubric). This keeps scores comparable between scorers and satisfies ROAD-02's 0-100% range. Impact stays {0.25, 0.5, 1, 2, 3} as in ROAD-02.
- **D-17-04:** When an item is linked to a product, the Reach field shows a **hint only**, e.g. "Core Banking has 12 linked clients". It never auto-fills, so every input is an explicit user value and the history stays honest.
- **D-17-05:** The rubric (units and the meaning of each Impact and Confidence step) is visible in the UI next to the inputs.

### Item lifecycle and page
- **D-17-06:** A new top-level **"Roadmap"** nav page (route `/roadmap`), separate from Portfolio.
- **D-17-07:** Besides the RICE inputs and the optional product link, an item has these fields:
  - **Status**: Idea / Planned / In progress / Done;
  - **Notes**: free text, rendered as plain text;
  - **Target quarter**: e.g. 2027-Q1;
  - **Owner**: free-text name.
- **D-17-08:** Items are **archived, never deleted**. Archived items sit behind "Show archived" and keep their input history (consistent with D-15-07).
- **D-17-09:** Jira summary and status, fetched at import or refresh, are **stored and shown to every app user**. Description and comments are never stored.

### Jira import and refresh
- **D-17-10:** Two ways to add items from Jira:
  - **(a)** Pick from the Tickets browser's search results (key or JQL): tick issues, then "Add to roadmap". There is no "add all" button.
  - **(b)** Pick from a **release's tickets**: on the release detail page, or in the roadmap's add dialog, choose tickets already in a release and add them.

  Manual items are created with a title.
- **D-17-11:** One item per Jira issue. Adding an issue that is already on the roadmap is **skipped**, and the result names it, e.g. "CNV-12 is already on the roadmap", with a link to it.
- **D-17-12:** Refresh happens through a **"Refresh from Jira" button for all items and one per item**. Each item shows when it was fetched, e.g. "Fetched 2h ago". There is no background job and no refresh on page open.
- **D-17-13:** An item whose Jira issue has vanished is kept and flagged "unavailable". It is flagged only after a successful Jira call confirms the issue is gone (research pitfall: one deleted key can blank a whole batch query).

### Ranking table and CSV
- **D-17-14:** The ranking shows **open items only** (Idea, Planned, In progress). Done items sit behind "Show done" and archived items behind "Show archived".
- **D-17-15:** Ordering:
  1. score, highest first;
  2. ties broken by higher Impact, then the item added earlier;
  3. unscored items (any input missing) come last, oldest first.

  Sorting uses the full-precision score.
- **D-17-16:** Columns:
  - rank;
  - title / Jira key;
  - score;
  - R, I, C and E inputs, editable inline, with a rubric help;
  - product, status, target quarter, owner;
  - Jira status, the fetched time and the "unavailable" flag.
- **D-17-17:** The score is displayed to **1 decimal**. Sorting uses full precision.
- **D-17-18:** The CSV exports **what is on screen**: the current product filter, the Done/archived toggles, the current order and all columns. It must open correctly in Excel (UTF-8).

### Release packages link (PKG-01/02, added 2026-10-25)
Unrelated to RICE; the user placed it in this phase. It is release-scoped and ships in the same V16 migration.
- **D-17-19:** **One** packages link per release: a nullable `release.packages_url` column. No label, no list of links.
- **D-17-20:** Only OneDrive/SharePoint links are accepted: `https` scheme, and the host is `*.sharepoint.com`, `onedrive.live.com` or `1drv.ms`. Anything else is rejected with 400. Validate at the API boundary; the frontend may pre-check but the server is the authority.
- **D-17-21:** Editable in **every** status, OPEN or FROZEN, like products after freeze (D-15-01). Setting it never stamps `items_changed_at` and never makes documents stale.
- **D-17-22:** Shown on **release detail** as an "Open packages" link (new tab, `rel="noopener noreferrer"`) with set / change / clear controls. **Not** a template token: generated documents do not include it.
- **D-17-23:** The **assistant** can answer where a release's packages are. Expose it as a field on an existing release tool result and in the indexed release text, **not** as a new tool: Phase 16 (D-16-16) already raised rag-service to its 6-tool / ~1900-char cap.
- **D-17-24:** Clearing is allowed (set back to null). No audit or history for link changes (no audit table exists, same as D-15 product edits).

### Claude's Discretion
- How input history (ROAD-06) is shown: per-item drawer or expandable row. It covers R, I, C and E edits, each with who, when and old -> new. Whether status, owner, quarter and notes edits are also recorded is the planner's call; RICE inputs are the requirement.
- Whether archived products can be linked to new items (follow D-15-07: no new links to archived products) and how the product filter treats items linked to archived products.
- CSV details: formula-injection guarding on text cells, BOM for Excel, filename.
- The cap on how many issues one refresh or add touches.
- The `ranked()` service method should be reusable later by the Phase 20 planner's top-N.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope and requirements
- `.planning/ROADMAP.md` §"Phase 17: RICE Roadmap": goal, success criteria, V16
- `.planning/REQUIREMENTS.md`: ROAD-01..07
- `.planning/research/v1.2/SUMMARY.md`, `ARCHITECTURE.md`, `PITFALLS.md`: milestone research (no new dependencies; RICE pitfalls)
- `.planning/phases/17-rice-roadmap/17-RESEARCH.md`: phase research. Jira refresh traps, targeted UPDATE so refresh never overwrites inputs, BigDecimal scoring, CsvWriter, V16 sketch.

### Related phases
- `.planning/phases/15-products-clients-installations/15-CONTEXT.md`: products (optional link), archive semantics (D-15-07)
- `CLAUDE.md`: Jira read-only, `/search/jql` cursor pagination, provider interface

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Phase 15 product slice (domain / ports / JPA adapter / controller / React Query hooks): the template for the roadmap slice.
- `TicketBrowser` (`actionLabel` / `onAction` props) and `TicketSearchService`: add-from-Jira flow (D-17-10a).
- The release detail page's ticket list: add-from-release flow (D-17-10b).
- `ActorResolutionService` / `Principal`: "who" in the history.
- `PromotionService.appendUnderLock` pattern: row-locked input edits with history in the same transaction.
- `ExportButtons` blob download: CSV download in the frontend.

### Established Patterns
- Hexagonal slice, Flyway SQL migrations, React Query hooks per feature, URL search params for filters.

### Integration Points
- Nav: add "Roadmap" next to Portfolio.
- Release detail: an "Add to roadmap" action on selected tickets.

</code_context>

<specifics>
## Specific Ideas

- Reach hint text: "{product} has {n} linked clients".
- Duplicate message: "{KEY} is already on the roadmap".

</specifics>

<deferred>
## Deferred Ideas

None from this discussion.

</deferred>

---

*Phase: 17-rice-roadmap*
*Context gathered: 2026-10-22*
