# Phase 8: Release Insights Dashboard - Context

**Gathered:** 2026-10-07
**Status:** Ready for planning **after Phases 6 and 7 exist**. The decisions are locked now, but the data
they read is built in Phases 6 and 7.
**Source:** `/gsd-discuss-phase 8`, run alongside Phase 6 at the user's request, plus a codebase
assumptions scan.

<domain>
## Phase Boundary

**In scope:** replace the placeholder `frontend/src/routes/DashboardRoute.tsx` (already the `/` route,
the post-login landing page, and the first nav link) with one screen answering:
- documentation readiness (DOC-09);
- ladder position;
- what went where and when (AUDIT-04);
- lifecycle durations (INSIGHT-01);
- composition and ADO coverage (INSIGHT-02);
- **development vs testing churn per ticket (INSIGHT-03, added 2026-10-07)**.

All of it reads this system's database only.

**Out of scope:** Jira agile reports (velocity, Jira cycle time, burndown); any live Jira or ADO call
while rendering; charts beyond inline bars; paging of the release matrix.

**Folded todo:** `.planning/todos/pending/items-changed-at-noop-stamping-on-remove-paths.md`.
`ReleaseRepositoryAdapter.java` stamps `items_changed_at` even when nothing was deleted:
- `removeItem` at `:99-104`;
- `removeManualItem` at `:173-179`.

That falsely shows paperwork as STALE on the dashboard. Fix it here, the same way WR-01 fixed `addItems`.
</domain>

<decisions>
## Implementation Decisions

### Metric definitions
- **D-08-01: "In Production" = the first successful promotion to Production.** Failed attempts don't
  count, and later re-promotions don't move the date.
- **D-08-02: Failed promotions are visible but never advance the stage.** They are listed in deployment
  history with a failed marker. A release whose latest promotion failed is flagged "last promotion
  failed".
- **D-08-03: Durations are shown per release, plus the median across the visible set.**
  - The durations: created → frozen, and frozen → production.
  - Medians, not averages, so one stuck release does not skew them.
  - Created → frozen is computable today: `created_at`, `frozen_at` (`V3__release_assembly.sql:12,14`),
    and freeze is one-way (`Release.java:25-31`).
  - Frozen → production needs Phase 7.
- **D-08-04: ADO coverage denominator = Jira-backed items only.** Manual items are excluded. Show three
  counts: linked / no link / enrichment failed (ADO-03). `LINKED` items flagged incomplete count as
  linked (D-06-03). Items `NOT_ATTEMPTED` (enriched before Phase 6, or frozen before it shipped) are shown
  as a separate "no data" count, never as "no link".

### INSIGHT-03: development vs testing churn (added 2026-10-07)
- **D-08-05: Two signals are kept distinct, because they diagnose different problems.**
  - **Development churn:** the number of commits to reach Done, i.e. commits with an author date before
    the ticket's **first** transition into status category Done.
  - **Testing rework:** the **reopen count** (transitions from a Done-category status back to a
    non-Done one) and the **commits made after first Done**.
  - ~~Caused-by bugs~~: **dropped 2026-10-07.** Bugs are not linked consistently in Jira, so the count
    would mostly read zero and mislead. Testing rework is reopens plus post-Done commits.
- **D-08-06: "Done" = Jira status category Done**, not status names. Status history comes from
  Phase 6's scoped, cached `/changelog` capture (D-06-08), not from the resolution date, which is
  overwritten on re-resolution and would misfile reopen-window commits as development.
- **D-08-07: Display.**
  - Per ticket, on the release page: commits-to-done, reopen count, and post-Done commits.
  - Per release, in the dashboard matrix: median commits-to-done, total reopens, post-Done commit share,
    with the outlier tickets flagged.
  - Tickets with no history or no linked commits are excluded from medians and counted as "no data",
    never as zero.
- These measure *this release's code and test churn*, which Jira cannot compute because it does not
  join commits to status history. They are not Jira cycle time. State that in the plans so a reader does
  not mistake it for the dropped analytics.

### Screen shape
- **D-08-08: A release matrix plus sections, on one screen.** Top to bottom:
  1. A summary strip: outstanding-paperwork counts, releases per stage, median durations.
  2. A release matrix, one row per release: doc pills, ladder stage (stepped dots), durations,
     composition with ADO coverage, and churn figures. Each row links to its release.
  3. A deployment history panel.
- **D-08-09: Numbers and inline bars only.** No chart library.
- **D-08-10: The dashboard is the home route.** Replace `DashboardRoute` in place at `/`. No router or
  nav change is needed (`router.tsx:55-59`, `AppShell.tsx:35-41`).

### Which releases appear
- **D-08-11: By default, in-flight releases plus those shipped in the last 90 days.** In-flight means
  open, frozen, or not yet in Production. Shipped means first successful Production promotion within 90
  days. A toggle shows all releases. Medians and summary counts are computed over the visible set.

### Deployment history (AUDIT-04)
- **D-08-12: A section on the dashboard, not its own route.** It lists promotions (release, environment,
  outcome, when, who) with environment and date-range filters applied **server-side**.
- **D-08-13: Default filter: all environments, last 30 days, failed promotions included and marked.**
  Filters are reflected in URL search params so a view can be shared.

### Requirements this phase places on Phase 7 (record them in Phase 7's context when it is discussed)
- **D-08-14: Only frozen releases can be promoted.** Frozen → production is therefore always defined, and
  the paperwork matches what shipped.
- The promotion data must include:
  - an append-only promotion record with release id, target environment from the fixed ladder,
    `promoted_at`, `promoted_by` (accountId; 05.3 actor resolution applies), outcome (at least
    SUCCESS / FAILED), and an optional note (DEPL-04);
  - no UPDATE or DELETE path (AUDIT-03);
  - an index supporting a filter by `(environment, promoted_at)`;
  - a defined ladder order and a defined Production environment.
- A `current_stage` column alone is insufficient: it makes AUDIT-04, the failure markers and D-08-01
  impossible.
- **Resolved by the Phase 7 discussion (2026-10-07, `07-CONTEXT.md`).** Phase 8 uses:
  - the **Production flag** (D-07-04) to identify Production, not a stage name;
  - **`deployed_at`** for durations and history, not `recorded_at` (D-07-10);
  - **effective (non-superseded)** entries only (D-07-11);
  - current stage computed from SUCCESS minus later ROLLED_BACK, by current ladder order (D-07-08).
    A rolled-back Production release still keeps its first-success shipped date;
  - **retired stages** still appear in deployment history, but not as filter defaults or matrix
    columns.
- **Amended 2026-10-12 (D-07-13..15):** "later" rollback means later by `deployed_at` (tie-break
  `recorded_at`), and an effective **`VOIDED`** entry (correction-only) counts toward no metric, no
  current stage and no history count. Every Phase 8 query must exclude it.

### Decided after research (2026-10-12)
- **D-08-15: A release rolled back out of Production stays visible by default.** It counts as in flight
  whenever its current stage is below Production, however old its first Production success is.
- **D-08-16: "Shipped" follows the current Production flag.** First Production success is the earliest
  effective SUCCESS on whichever stage is flagged Production now. Moving the flag changes historical ship
  dates; recording flag history was rejected (it needs a schema change).
- **D-08-17: Items with incomplete enrichment count as "no data" for commit-based churn.** This covers
  `COMMIT_LIMIT` and partial ADO data. They are excluded from commits-to-done and post-Done commits, but
  still count toward reopens.
- **D-08-18: Outlier ticket = commits-to-done above the release's upper Tukey fence (Q3 + 1.5 x IQR, only
  when at least 4 tickets have data), or 2 or more reopens.**
- Research defaults accepted without a separate question: "last promotion failed" is the latest effective,
  non-VOIDED entry across all stages, cleared by any later SUCCESS; total reopens include tickets that
  have history but no commits. The dashboard max date span is 366 days and is a backend constant that
  the UI must match (08-UI-SPEC).

### Claude's Discretion
- **Read model:** a dedicated dashboard query/endpoint with set-based aggregate subqueries, not more
  columns on `ReleaseSummary`. Those columns would slow `/releases` and `DocumentReadinessService.all()`,
  which reuses `search("")`. No per-release queries (N+1).
- **Readiness:** reuse `GET /api/documents/readiness` and `ReadinessPill` as they are (T-05-55: never
  re-derive readiness on the client). Code against the `NO_TEMPLATE` state from Phase 05.4.
  - Replace `readinessFor`'s linear `Array.find` (`frontend/src/api/documents.ts:48-54`, commented as
    O(1)) with a Map index, since the matrix multiplies it across many rows.
- **Styling:** plain Tailwind tables, pills and badges as in `ReleasesRoute.tsx:93-170`. No component
  library.
- **Cache invalidation:** promotion and enrichment mutations must invalidate the dashboard query key.
- **DOC-09 bookkeeping:** DOC-09 is satisfied per `05-VERIFICATION.md:193` but still marked Pending in
  `REQUIREMENTS.md:200`. Correct the marker when this phase closes; do not rebuild the feature.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

- `.planning/ROADMAP.md`: the Phase 6, 7 and 8 sections. Phase 8's scope notes and success criteria are
  binding; criterion 4's "this tool's own process, not Jira cycle time" distinction now also covers
  INSIGHT-03.
- `.planning/REQUIREMENTS.md`: DOC-09, AUDIT-04, INSIGHT-01..03, DEPL-01..04, AUDIT-01..03.
- `.planning/PROJECT.md`: Out of Scope, line ~41, and the 2026-10-07 scoped `/changelog` reversal.
- `.planning/phases/06-azure-devops-enrichment/06-CONTEXT.md`: D-06-02/03 (status values, incomplete
  flag) and D-06-08 (commit dates, status history, issue links). The data source for D-08-04..07.
- `.planning/phases/05-document-generation/05-VERIFICATION.md:193`: DOC-09 evidence.
- `.planning/phases/05.4-template-selection-at-generation-time/05.4-CONTEXT.md`: D-05.4-06, the
  `NO_TEMPLATE` rename.
- `.planning/todos/pending/items-changed-at-noop-stamping-on-remove-paths.md`: the folded todo.
- Code:
  - `frontend/src/routes/DashboardRoute.tsx`
  - `frontend/src/routes/ReleasesRoute.tsx`
  - `frontend/src/api/documents.ts`
  - `frontend/src/components/ReadinessPill.tsx`
  - `src/main/java/com/evision/releasemgmt/application/DocumentReadinessService.java`
  - `adapters/persistence/ReleaseJpaRepository.java:20-41` (set-based count subquery pattern)
  - `adapters/persistence/ReleaseRepositoryAdapter.java`
</canonical_refs>

<specifics>
## Specific Ideas

- The ladder shown as stepped dots per row; a failed latest promotion gets a marker on the stage it
  failed to reach.
- Every count that excludes items (manual, no data, not attempted) says so in a tooltip or footnote, so
  no number silently hides its denominator.
</specifics>

<deferred>
## Deferred Ideas

- Duration trend charts: a chart library was declined (D-08-09).
- Per-reopen-cycle commit breakdown: offered, not chosen. The plain development vs post-Done split was
  selected.
- PR review findings as a quality signal: offered 2026-10-07, not selected.
</deferred>

---

*Phase: 08-release-insights-dashboard*
*Context gathered: 2026-10-07*
