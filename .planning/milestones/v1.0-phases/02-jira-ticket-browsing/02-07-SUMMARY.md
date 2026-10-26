---
phase: 02-jira-ticket-browsing
plan: 07
subsystem: ticket-browsing-ui
tags: [react, adf, accessibility, aria-expanded, jql, jira-05, jira-02]
dependency-graph:
  requires:
    - "02-01: AdfFlattener output format — the markers this panel renders verbatim"
    - "02-05: the backend's mutual-exclusivity rule and 2000-character JQL limit"
    - "02-06: TicketsRoute table, runSearch, loaded/selected state, useTicketSearch"
  provides:
    - "Row-expand detail panel showing flattened description and comments, bounded and scrolling"
    - "Advanced: edit as JQL disclosure closing JIRA-02's third filter dimension"
    - "loadedMode tracking so empty-state copy matches the search that actually ran"
  affects:
    - "Phase 3 release assembly: consumes `selected` (ReadonlySet<string> of ticket keys) from this component"
    - "Phase 5 document generation: reads the same flattened description/comment strings this panel displays, so what the release manager reads here is what a document will contain"
tech-stack:
  added: []
  patterns:
    - "Server-flattened rich text is rendered in whitespace-pre-wrap, never re-parsed on the client — one interpretation of the string across browse and document generation"
    - "An icon-only control needs both aria-expanded (state) and a toggling aria-label (name); a glyph is not an accessible name"
    - "Bounded height with internal scroll instead of ellipsis truncation wherever content is read for a decision"
    - "Which mode produced the current results is recorded at success time, not read from live UI flags the user may have since changed"
key-files:
  created: []
  modified:
    - frontend/src/routes/TicketsRoute.tsx
decisions:
  - "D-PLAN-07-A applied: the detail panel renders flattened text in whitespace-pre-wrap rather than parsed Markdown — no new dependency, no HTML injection surface, and browse and Phase 5 cannot diverge in how the same string is interpreted"
  - "D-PLAN-07-B applied: the JQL path sends mode 'jql' only, so the picker selection is ignored for that search; the discriminated union makes sending both structurally impossible"
  - "D-PLAN-07-C applied: one expandedKey rather than a set — one open panel keeps the table scannable, which is why rows were chosen over cards"
  - "D-PLAN-07-D applied: comment truncation is shown as 'showing N of M', never hidden"
  - "Rows are wrapped in a keyed Fragment so the detail panel can be a sibling <tr>: a nested table or a div inside <tbody> would be invalid markup, and colSpan on a sibling row is the standard expandable-row structure"
  - "No client-side JQL validation — Jira's parser is the authority and a second grammar would be a sync burden with no benefit"
metrics:
  duration_minutes: 0
  tasks_completed: 2
  files_created: 0
  completed_date: "2026-08-04"
---

# Phase 02 Plan 07: Row Expansion & JQL Escape Hatch Summary

The last two capabilities the phase owed: reading a ticket's full rich text without leaving the screen, and dropping into raw JQL when the fix-version picker is not enough.

## What Was Built

**Task 1 — Row-expand ADF detail panel (JIRA-05).** A ninth, rightmost column carries a real `<button>` toggle with both `aria-expanded` and an `aria-label` that flips between "Expand ticket details" and "Collapse ticket details" — the `▸`/`▾` glyph is not an accessible name, so `aria-expanded` alone would leave the control announced as an unnamed button (UI-SPEC binding follow-up 2, an accessibility defect rather than polish). Expanding renders a sibling `<tr>` with `colSpan={9}`: Description first, then Comments, inside a `max-h-80 overflow-y-auto` panel. Bounded-with-internal-scroll rather than ellipsis-truncated, because a release manager reading a description to decide what goes into generated documentation must never have content silently hidden. Content is rendered as React text children in `whitespace-pre-wrap`, preserving the flattener's newlines and list indentation with zero dependencies and no HTML injection surface. The comment heading reports "showing N of M" whenever `commentTotal` exceeds the returned body count.

**Task 2 — "Advanced: edit as JQL" (JIRA-02).** A collapsed-by-default disclosure button with `aria-expanded` reveals a `maxLength={2000}` textarea mirroring the backend's `@Size(max = 2000)`, so the limit is felt in the browser rather than arriving as a server-side `VALIDATION_FAILED`. `runSearch` is generalised to pick the mode, so both paths share one trigger and **Try again** retries whichever mode actually ran. `loadedMode` records which mode produced the current results at success time rather than reading the live `jqlOpen` flag, which the user may have toggled since — reading the flag would show JQL empty-state copy for picker results. The JQL path gets its own empty-state copy ("No tickets matched your JQL. Try a broader query.").

## Final Component Contract (for Phase 3)

**`frontend/src/routes/TicketsRoute.tsx`** — 383 lines. Table is **9 columns**: `[select checkbox] Key · Summary · Type · Status · Resolution · Assignee · Reporter · [expand toggle]`.

State variables:

| Name | Type | Purpose |
|---|---|---|
| `projectId` | `string` | picker selection (`''` = none) |
| `fixVersionId` | `string` | picker selection (`''` = none) |
| `statusCategories` | `StatusCategory[]` | JIRA-07 filter, seeded to `['DONE']`; `[]` means every status |
| `loaded` | `Ticket[] \| null` | `null` = no search run yet, distinct from an empty result |
| `selected` | `ReadonlySet<string>` | **ticket keys — this is what Phase 3 consumes** |
| `expandedKey` | `string \| null` | at most one open detail panel |
| `jqlOpen` | `boolean` | disclosure state, collapsed by default |
| `jql` | `string` | raw query text, capped at 2000 chars |
| `loadedMode` | `'fixVersion' \| 'jql' \| null` | which mode produced `loaded` |

Also: `selectAllRef` (for the `indeterminate` DOM property), and the `projects` / `versions` / `search` TanStack hooks. There is deliberately **no** action button consuming `selected` — Phase 3 owns that.

## Verification

- `npx tsc -b`, `npm run build`, `npx vitest run` — all exit 0; **22 frontend tests green**.
- Backend untouched by this plan and unchanged at **207 green**.
- All task 1 criteria pass, including `dangerouslySetInnerHTML` = 0 and `text-ellipsis` = 1 (the Summary cell only — the detail panel must not truncate).
- All task 2 criteria pass after two fixes described below.
- Commits: `7d5c662` (Task 1), `d81ac97` (Task 2).

## Deviations from Plan

1. **Rows wrapped in `<Fragment key={...}>`.** The plan showed the detail panel as a second `<tr>` after each row but left the row still keyed on `<tr>`. Emitting two sibling rows per ticket needs one keyed parent, and a `<div>` inside `<tbody>` would be invalid markup — so each ticket renders as a keyed `Fragment` containing its row and, conditionally, its panel.
2. **Empty-state picker copy moved into a template literal.** JSX line-wrapping split the phrase "Advanced: edit as JQL" across two source lines, so the acceptance grep found 1 occurrence instead of 2. The copy is unchanged as rendered; it is now a single template literal so the phrase is contiguous in source as well as on screen.

## Issues Encountered

One acceptance criterion could not be met as literally written and was verified by substance instead. It required `grep -c 'loadedMode'` to return `>= 3` (declaration, set, read), but `setLoadedMode` carries a capital **L** and so does not match a lowercase `loadedMode` pattern — the exact-case count is 2. The variable is genuinely declared (line 26), set (line 59) and read (line 244); `grep -ci` returns 3. The criterion assumed case-insensitive matching; no code change was warranted.

## Known Stubs

None. This plan closes the JQL gap that plan 06's summary flagged, so JIRA-02's three filter dimensions — project, fix version and JQL — are all present in the UI. That summary's "Known Stubs" note was corrected accordingly.

Note for Phase 5: the description and comment strings displayed here are the same ones document generation will consume, so this panel doubles as a preview of what a generated document will contain. Comment bodies are only Jira's first embedded page; anything needing the complete comment history will require the dedicated paginated comment endpoint.

## Threat Flags

`T-02-07-01` (XSS via Jira-authored rich text) is the material one and is mitigated structurally: text children only, `dangerouslySetInnerHTML` grep-asserted absent across the route. `T-02-07-02` (raw JQL) is accepted by design — the query runs with the signed-in user's own credential via `currentContext()`, so it cannot surface anything they could not already see in Jira's issue navigator, and it is bounded at 2000 characters on both sides. `T-02-07-05` (truncation presented as complete) is closed by the "showing N of M" heading.

## Self-Check: PASSED

The one modified file verified on disk. Both task commits verified in `git log`. Typecheck, build and tests re-run green after the final fix.
