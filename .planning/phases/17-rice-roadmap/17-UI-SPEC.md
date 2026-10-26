---
phase: 17
slug: rice-roadmap
status: approved
reviewed_at: 2026-10-25
shadcn_initialized: false
preset: none
created: 2026-10-25
---

# Phase 17 — UI Design Contract: RICE Roadmap and Release Packages Link

> Visual and interaction contract for ROAD-01..07 (new page `/roadmap`) and PKG-01/02 (one "Open packages" link on release detail). Extends `16-UI-SPEC.md` (tokens, tables, tiles, filter popover, help toggles, states) and `15.1-UI-SPEC.md` (release detail header block, blocked-action pattern, `ConfirmDialog` usage). No new dependency, component library, chart library, icon library, type size or colour family.

Decisions D-17-01..D-17-24 in `17-CONTEXT.md` are locked and not reopened. Items marked **(discretion)** are choices made here so the planner does not have to.

New elements: (1) "Roadmap" nav link and page, (2) add flows (Jira browser, release tickets, manual), (3) ranked table with inline R/I/C/E editing, (4) scoring rubric, (5) expandable item detail with history, Jira refresh, product link and archive, (6) CSV export, (7) release detail "Packages" row, (8) "Add to roadmap" on the release ticket list.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none. No `components.json`. Not reopened (13-UI-SPEC). |
| Preset | not applicable |
| Component library | none. Native `<table>`, `<details>`, `<dialog>`, `<button>`, `<input>`, `<select>`, `<textarea>`, `<a>`; TanStack `Link`. |
| Reuse (confirmed present in `frontend/src/components`) | `ProductFilter` (multi-select popover, props `products`, `selected`, `onChange`), `TicketBrowser` (props `actionLabel`, `onAction(keys, projectKey)`), `ConfirmDialog`, `Spinner` (from `ExportButtons`), `SMALL_BUTTON` (from `EnvironmentLadderSection`), `PRODUCT_CHIP` (from `ProductsEditor`), the `<dialog>` + `showModal()` pattern of `AddTicketsToReleaseDialog` / `RecordInstallDialog`, the `FIELD` input class from `RecordInstallDialog`, the help-toggle and tab-bar markup of Phase 16. `FIELD` and `PRODUCT_CHIP` are module-local or exported ad hoc today; the planner may export them from one shared place, not required. |
| Icon library | none. Glyphs only: `?`, `×`, `—`, `·`, `…`, `→`. Spinner is the existing `h-4 w-4 animate-spin motion-reduce:animate-none rounded-full border-2 border-gray-300 border-t-blue-600`. |
| Chart library | none. |
| Font | system default (Tailwind `sans`). `tabular-nums` on every number (rank, score, R, E, counts). |

Stack facts: React, Vite, TypeScript, TanStack Router (`validateSearch`) and Query (hand-written fetch layer in `frontend/src/api/`), Tailwind v4 utilities. Errors render through `ApiError.problem` and `messageForReason`, `role="alert"`, `data-reason={problem.reason}`. The only new nav entry is `/roadmap`.

---

## Spacing Scale

Reuses the Phase 2-16 scale. No new tokens.

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4px | Chip and label gaps (`gap-1`), `mt-1` under inputs |
| sm | 8px | Control gaps (`gap-2`), `mt-2` helper lines |
| md | 16px | Table cells (`px-4 py-2`), panel padding (`p-4`), grid gaps (`gap-4`), `mt-4` between blocks |
| lg | 24px | Dialog padding (`p-6`), empty-state panel padding (`p-6`), `mt-6` before sub-sections |
| xl | 32px | `space-y-8` between the page's major blocks |
| 2xl | 48px | Not used |
| 3xl | 64px | Not used |

Exceptions (inherited classes, matched exactly, not new tokens): `px-3 py-1.5` bordered buttons; `px-2 py-0.5` chips and markers; `px-2 py-1` small row actions (`SMALL_BUTTON`); 12px (`gap-3`, `p-3`) in the filter popover and amber notices; `px-3 py-2` form fields (`FIELD`); `px-2 py-1` for in-table inputs (tighter than `FIELD` so a row stays one line).

Fixed dimensions (all on the 4px grid): R and E inputs `w-20` (80px); Impact select `w-32` (128px); Confidence select `w-28` (112px); quarter input `w-24` (96px); item title column `min-w-[16rem]` (256px); Jira column `min-w-[12rem]` (192px); sticky-left Rank + Item columns `bg-white`; manual-item dialog `w-[32rem] max-w-full` (512px); Jira dialog `w-[min(90vw,72rem)] max-w-none`; history list `max-h-64 overflow-y-auto` (256px).

---

## Typography

Reuses the Phase 5 / 8 / 15 / 16 scale. 3 sizes in use (the 18px Display of Phase 16 is not used here), 2 weights (400, 600).

| Role | Size | Weight | Line Height |
|------|------|--------|-------------|
| Body | 14px (`text-sm`) | 400 | 1.5 |
| Label | 14px (`text-sm`) | 600 | 1.5 |
| Heading | 16px (`text-base`) | 600 | 1.2 |
| Small (units, fetched time, helper lines, chips, markers, footnotes) | 12px (`text-xs`) | 400; 600 inside chips and markers | 1.5 |

Usage:
- "Roadmap" page heading and the "Details" panel headings ("Inputs", "Input history", "Details"): Heading 16/600.
- Table column headings, item titles, score, active toggle labels: Label 14/600. Other cell text and in-table inputs: Body 14/400.
- Units in column headings ("clients", "person-weeks"), fetched time, Jira key, history timestamps, markers, rubric footnotes: Small 12.

---

## Color

No new colour. Existing palette and meanings.

| Role | Value | Usage |
|------|-------|-------|
| Dominant (60%) | `gray-50` canvas, empty-state panels | Page, empty states |
| Secondary (30%) | `white` with `border-gray-200` | Table, rubric panel, detail panel, dialogs, filter popover; `gray-100` for status chips, product chips (`PRODUCT_CHIP`) and the expanded-row background |
| Accent (10%) | `blue-600` | See reserved-for list |
| Destructive | `red-600` | Error text (`role="alert"`) and invalid-field text only. Nothing in this phase deletes data (items are archived, D-17-08); no red buttons. |
| Warning | `amber-700` text, `amber-50` bg, `amber-600` border | "unavailable in Jira" marker, "Missing: …" hint on unscored rows, partial-result notices |
| Muted | `gray-500` / `gray-400` text | Archived rows, "Not scored", dashes, units, "Manual" tag |

Accent (`blue-600`) reserved for:
1. **Primary buttons**: "Add to roadmap" (dialog action, already styled by `TicketBrowser`), "Add item" (manual dialog submit), "Save packages link". Style `rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50`. One per dialog or panel.
2. **Links**: item links, release links, "Open packages", "Clear filter" text links. (The "Roadmap" nav link is not blue: it uses the same `font-semibold text-gray-900` active style as the other nav links.)
3. The pending-spinner leading edge.
4. `focus-visible:ring-2 focus-visible:ring-blue-600` on every new control (in-table inputs and selects, "Details" toggle, row buttons, checkboxes, nav link).

Colour is never the only signal: "unavailable in Jira", "Not scored", "archived", "Manual", "Done" are all words. Score is never colour-coded.

---

## Screen: Roadmap (`/roadmap`)

Nav: link **"Roadmap"** in `AppShell` directly after "Portfolio" and before "Assistant", same classes as the other nav links (D-17-06). Route is in the authenticated shell. The page is readable by every signed-in user (D-17-09: Jira summary and status are shown to every app user).

Search params (all optional, absent = default, malformed values dropped to default, changes use `navigate({ replace: true })`, ids and flags only, nothing in `localStorage`):

| Param | Values | Default | Notes |
|-------|--------|---------|-------|
| `products` | array of product ids | empty = all | D-17-16 / ROAD-05 product filter, same semantics as Phase 16. Items with no product are shown only when the filter is empty. |
| `done` | `true` | off | "Show done" (D-17-14) |
| `archived` | `true` | off | "Show archived" (D-17-08) |
| `item` | item id | none | Expands that item's detail row and scrolls it into view. Opening it from the "already on the roadmap" link also turns `done`/`archived` on and drops `products` as needed so the row is visible. |

Layout, top to bottom, inside `space-y-8`:

```
<h2> Roadmap
Toolbar:  [Add from Jira] [Add from a release] [Add manual item]   ...   [Refresh from Jira] [Export CSV]
Filter row: [Products: All products v]  [ ] Show done  [ ] Show archived        "{n} items shown"
Result region (role="status"): outcome of the last add / refresh, dismissible
How scoring works  (<details open>)
Ranked table
```

(Toolbar and filter row share one block with `space-y-4`; `flex flex-wrap items-center gap-3` per row; the right-hand group uses `ml-auto`.)

Buttons use the bordered style `rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 disabled:opacity-50`. None is blue: the page has no single dominant action (blue is reserved for dialog submits).

### Product filter, Show done, Show archived

- Product filter: the existing `ProductFilter` component unchanged (label "Products", values "All products" / name / "{n} products", Escape and outside-click close, "Clear" link). It is hidden when no products exist, as in Phase 16.
- **"Show done"** and **"Show archived"**: native checkboxes with Body labels. Off by default. Archived items keep a Small gray **"archived"** tag after the title and `text-gray-500` row text. Done items show status "Done".
- Counter, Small `text-gray-600`, right side: **"{n} items shown"** / **"1 item shown"**, announced politely when it changes.
- Items linked to an archived product (discretion, follows D-15-07): they stay visible. The product name shows with Small "archived". The archived product appears in the filter popover only when it is already selected in the URL (the existing `ProductFilter` rule). New links to archived products are not offered in the product select (see Details panel).

### How scoring works (ROAD-02, D-17-05)

A `<details open>` block, `rounded border border-gray-200 bg-white p-4`, summary Label **"How scoring works"** (focus ring). Open by default; closing it is not remembered (no persistence). Contents:

- Formula line, Body: **"Score = Reach × Impact × Confidence ÷ Effort. Items missing any input are not scored."**
- Three-column list (`grid gap-4 sm:grid-cols-3`), each with a Label heading and Small `text-gray-600` lines:

| Input | Copy |
|-------|------|
| **Reach (clients)** | **"How many clients this affects. A whole number, 0 or more. Choosing a product shows how many clients are linked to it, as a hint only."** |
| **Impact** | **"3 Massive · 2 High · 1 Medium · 0.5 Low · 0.25 Minimal. How much it moves things for each client reached."** |
| **Confidence** | **"High 100%: backed by data. Medium 80%: some evidence. Low 50%: a guess."** |
| **Effort (person-weeks)** | **"Total work in person-weeks. Decimals are fine (0.5). Must be above 0."** (fourth entry; grid is `sm:grid-cols-2 lg:grid-cols-4`) |

The same units appear permanently in the table column headings (Small unit line), so the rubric is visible even when this block is collapsed. Each in-table control has `aria-describedby` pointing at a visually hidden id of its rubric sentence.

### Ranked table (ROAD-03, D-17-14..17)

Focal point: the ranked table is the page's visual anchor, read from its Rank and Score columns; the page has no primary button.

Table `min-w-full text-sm` in `overflow-x-auto rounded border border-gray-200 bg-white`, header `px-4 py-2 text-left font-semibold text-gray-700`, rows `border-b border-gray-100 hover:bg-gray-50`, all headings `scope="col"`. Rank and Item columns are `sticky left-0 bg-white` (Item offset by the rank column width, `left-16`; rank column `w-16` = 64px). Columns in this order (D-17-16):

| Column | Cell |
|--------|------|
| **Rank** | Position `1..n` among scored rows (`tabular-nums`, Label). Unscored rows show `—` (`text-gray-400`, `aria-label="Not ranked"`). No shared ranks: ties still get successive positions in the D-17-15 order. |
| **Item** | Title (Label). Jira items: Small `text-gray-600` Jira key under the title, plain text (this app has no Jira base URL contract; not a link). Manual items: Small gray tag **"Manual"** in the same place. Archived tag when shown. A **"Details"** `SMALL_BUTTON` after the title toggles the detail row (`aria-expanded`, `aria-controls="item-{id}-detail"`, `aria-label="Details for {title}"`). |
| **Score** | `{score to 1 decimal}` Label `tabular-nums` (D-17-17), e.g. `12.5`. Unscored: **"Not scored"** `text-gray-500`, with `title` and a Small `text-amber-700` line **"Missing: Reach, Effort"** (names the missing inputs, in R, I, C, E order). |
| **Reach** (Small unit line "clients") | Number input. |
| **Impact** | Select with options `0.25 Minimal`, `0.5 Low`, `1 Medium`, `2 High`, `3 Massive`; unset shows an empty first option **"Select…"** (not selectable once set). |
| **Confidence** | Select with options `High 100%`, `Medium 80%`, `Low 50%`; unset shows **"Select…"**. Stored value is the percent (100 / 80 / 50). |
| **Effort** (Small unit line "person-weeks") | Number input, decimals allowed, above 0. |
| **Product** | Product chip (`PRODUCT_CHIP`) or `—` `text-gray-400` with `title="No product"`. Read-only here; edited in the Details panel. |
| **Status** | Plain text: Idea / Planned / In progress / Done. Read-only here. |
| **Target quarter** | Text (e.g. `2027-Q1`) or `—`. Read-only here. |
| **Owner** | Text or `—`. Read-only here. |
| **Jira** | Jira items: Small `text-gray-600` Jira status (plain text, stored summary and status per D-17-09), second line Small **"Fetched {relative}"** (`title` = full `toLocaleString()`), then a `SMALL_BUTTON` **"Refresh"** (`aria-label="Refresh {key} from Jira"`). If unavailable: amber marker `rounded border border-amber-600 bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-700` **"unavailable in Jira"** with `title` **"Jira no longer returns this issue. The last fetched summary and status are kept."**; the fetched time then reads **"Last fetched {relative}"**. Manual items: `—`. |

Relative time format (not live-updating, recomputed on render): **"just now"** (under 1 minute), **"{n}m ago"**, **"{n}h ago"**, **"{n}d ago"**; the label is **"Fetched {relative}"**.

Ordering (D-17-15) is done by the server in `ranked()`; the UI never re-sorts, recomputes or rounds before sorting. Scored rows come first. When unscored rows exist, a full-width separator row sits before them: `<tr><th colSpan scope="rowgroup" className="bg-gray-50 px-4 py-2 text-left text-xs font-semibold text-gray-600">Not scored yet ({n}). Oldest first.</th></tr>`.

Row text for archived rows is `text-gray-500`; archived rows are not editable inline (inputs `disabled`, with the Details panel offering "Restore").

### Inline R/I/C/E editing (ROAD-02)

- One input = one save. Number inputs commit on **blur** and on **Enter**; selects commit on **change**. **Escape** in a number input reverts to the saved value. An unchanged value sends nothing.
- Client-side check before sending (the server is the authority and returns 400 for the same rules): Reach whole number ≥ 0; Effort number > 0; empty clears nothing and is treated as "no change" for a set value (clearing an input is not offered). Invalid input shows Small `text-red-600` text under the control, linked by `aria-describedby`, and `aria-invalid="true"`; nothing is sent:
  - Reach: **"Enter a whole number, 0 or more."**
  - Effort: **"Enter a number above 0, for example 0.5."**
- While saving, the control is `aria-busy` with a trailing Spinner. On success the table refetches and the row may move; focus stays on the control that was edited (the row element is keyed by item id so React keeps it mounted) and the polite live region announces **"{title}: score {score}, rank {n}."** or **"{title}: not scored yet."**
- Server rejection (400 or 409, any `reason`): the control reverts to the saved value and the message renders under it, `role="alert"` `data-reason`, Small `text-red-600`, via `messageForReason`. Network failure: **"Couldn't save {input} — {message}. Try again."**
- Edits are row-locked server side; the UI does not queue edits. Controls in the row are `disabled` while one of its saves is pending.

### Details panel (expandable row, ROAD-06, D-17-07/08/12) **(discretion: expandable row, not a drawer)**

Pressing "Details" inserts a full-width row directly below the item: `<tr id="item-{id}-detail"><td colSpan>` with `bg-gray-100 p-4` content, so the table stays in view and the user keeps their place. One panel can be open at a time per item; several items can be open together. `item=<id>` opens one from a link. Closing returns focus to that row's "Details" button. Sections, top to bottom, `space-y-4`:

**Details** (Heading), a `grid gap-4 sm:grid-cols-2` form using `FIELD` styling, each saved on blur/change like the inline inputs:

| Field | Control |
|-------|---------|
| Title | text input. Manual items editable; Jira items show the stored summary read-only with Small **"Comes from Jira. Refresh to update it."** |
| Status | select: Idea / Planned / In progress / Done. Setting Done removes the row from the ranked view unless "Show done" is on; the live region announces **"{title} moved to Done."** and focus moves to the table heading. |
| Product | select: **"No product"**, then active products by name; the item's current product is listed even if archived (marked "archived"); archived products are otherwise not offered (D-15-07). Shows Small `text-gray-600` Reach hint when a product is chosen: **"{product} has {n} linked clients"** (D-17-04; hint only, never fills Reach; singular **"{product} has 1 linked client"**). |
| Target quarter | text input, placeholder `2027-Q1` Free text; helper Small **"For example 2027-Q1."** |
| Owner | text input, free text. |
| Notes | `<textarea rows=4>`, plain text, `sm:col-span-2`, rendered everywhere as React text children (no markdown, no HTML). |

**Jira** (only for Jira items): Small line **"{KEY} · {Jira status} · Fetched {relative}"**, a `SMALL_BUTTON` **"Refresh from Jira"** (per item, D-17-12), unavailable marker as in the table.

**Input history** (Heading, ROAD-06): table (same table classes, Small text) with columns **When · Who · Input · Change**; newest first; `max-h-64 overflow-y-auto`.
- When: `toLocaleString()`. Who: user name/email from the server. Input: **Reach / Impact / Confidence / Effort**. Change: `{old} → {new}` with units, e.g. `10 → 25`, `Medium (80%) → High (100%)`, `1 → 2`, `2 → 3.5 person-weeks`; first-ever value shows **"not set → {new}"**.
- Whether status, owner, quarter and notes edits are also recorded is the planner's call (D-17 discretion); if recorded they appear in the same table with the field name in the Input column. RICE rows are the requirement.
- Empty: Small `text-gray-600` **"No input changes yet."**
- Loading: Spinner + **"Loading history…"**. Error: `role="alert"` **"Couldn't load the history — {message}. Try again."** + **Try again**.

**Archive / Restore**: a `SMALL_BUTTON` **"Archive item"** (or **"Restore item"** on archived items). No confirmation: it is reversible and nothing is deleted. Helper Small `text-gray-600` under it: **"Archived items are hidden from the ranking. Their history is kept."** Archiving removes the row from the ranked view unless "Show archived" is on; announce **"{title} archived."** / **"{title} restored."**

---

## Add flows (ROAD-01, D-17-10, D-17-11)

All three are `<dialog>` + `showModal()`; Escape and backdrop dismissal equal Cancel. Result messages appear in the page's result region (below), not in the dialog, because the dialog closes on success.

### Add from Jira (D-17-10a)

Wide dialog (`w-[min(90vw,72rem)] max-w-none rounded border border-gray-200 bg-white p-6`), title **"Add from Jira"**. Body is the existing `TicketBrowser` mounted only while open (as `AddTicketsToReleaseDialog` does), `actionLabel="Add to roadmap"`, in `mt-4 max-h-[70vh] overflow-y-auto`. Key search and JQL come from the browser unchanged. There is no "add all" button; the browser's select-all checkbox applies only to the loaded, ticked rows and the action label names a count via its existing "{n} selected" text. Footer: **Cancel** only. Pending: Spinner + **"Adding to roadmap…"**. On success the dialog closes and the result region reports the outcome.

### Add from a release (D-17-10b)

Dialog `w-[40rem] max-w-full`, title **"Add from a release"**. Fields:
1. **Release** select (all releases, newest first, option text `{name} {version}`; label Label style).
2. After a release is chosen, its tickets load as a checklist (`max-h-64 overflow-y-auto rounded border border-gray-300 p-3`, one row per ticket: checkbox, Jira key Label, summary Body). Row text for tickets already on the roadmap is `text-gray-500` with Small **"already on the roadmap"**; the checkbox stays enabled (adding is skipped server-side and reported, D-17-11). A **"Select all"** / **"Clear"** `SMALL_BUTTON` pair with a polite **"{n} selected"** counter, as in `RecordInstallDialog`.
3. Footer: **Cancel** and primary **"Add to roadmap"** (disabled while nothing is ticked or pending).

Release has no tickets: Small `text-gray-600` **"This release has no tickets."** No releases exist: **"No releases yet. Create a release first."**. Tickets loading: Spinner + **"Loading tickets…"**. Error: `role="alert"` **"Couldn't load this release's tickets — {message}. Try again."**

### Add manual item

Dialog `w-[32rem] max-w-full`, title **"Add manual item"**, `<form>` with `space-y-3`:
- **Title** (required, `FIELD`, `maxLength` per server, label Label style). Empty after trim blocks submit, field error Small `text-red-600` **"Enter a title."**; server `VALIDATION_FAILED` `fieldErrors.title` shows in the same place (as the release manual-item form does).
- **Product** (optional select, "No product" default, active products only).
- Footer: **Cancel** and primary **"Add item"**. Pending: button disabled, Spinner + **"Adding…"**.

New items start with status **Idea** and no R/I/C/E, so they land in the "Not scored yet" group; the result region says so (below). The new item's detail row opens (`item=<id>`) after the manual dialog closes so the user can set inputs straight away.

### Add result region (D-17-11)

A single `role="status" aria-live="polite"` block under the filter row, `rounded border border-gray-200 bg-white p-3 text-sm`, with a **"Dismiss"** `SMALL_BUTTON` (`aria-label="Dismiss message"`). It stays until dismissed or replaced. Lines:

| Outcome | Copy |
|---------|------|
| Added | **"Added {n} items to the roadmap."** / **"Added 1 item to the roadmap."** Small `text-gray-600` second line **"They have no scores yet. Set Reach, Impact, Confidence and Effort to rank them."** |
| Duplicate (one per skipped issue) | **"{KEY} is already on the roadmap."** with **"View it"** blue link (`/roadmap?item={id}`), exact wording as D-17 specifics. More than 5 duplicates: first 5 then **"and {n} more already on the roadmap."** |
| All skipped | **"Nothing added. {KEY} is already on the roadmap."** (or **"Nothing added. All {n} are already on the roadmap."**) |
| Cap exceeded (planner's cap, discretion) | `role="alert"` **"Too many items at once. Add up to {cap} at a time."** |
| Failure | `role="alert"` **"Couldn't add to the roadmap — {message}. Try again."** |

---

## Jira refresh (ROAD-04, D-17-12/13)

- **"Refresh from Jira"** (toolbar): refreshes every Jira item, not only the visible ones. `title` **"Updates the Jira summary and status of every Jira item. Your Reach, Impact, Confidence and Effort are never changed."** No background job, no refresh on page open (so no loading state on arrival).
- Per item: the "Refresh" button in the Jira cell and in the Details panel.
- Pending: the pressed button is `disabled` with Spinner and label **"Refreshing…"**; the toolbar button disables other refresh buttons while an all-items refresh runs; the per-item buttons of other rows stay usable only when no all-items refresh is running.
- Result region copy:

| Outcome | Copy |
|---------|------|
| All refreshed | **"Refreshed {n} items from Jira."** + when some are gone **"{k} no longer in Jira and are marked unavailable."** |
| One refreshed | **"Refreshed {KEY} from Jira."** |
| Issue vanished (confirmed by a successful Jira call, D-17-13) | **"{KEY} is no longer in Jira. It stays on the roadmap and is marked unavailable."** |
| Jira unreachable / unauthorised | `role="alert"` **"Couldn't reach Jira — {message}. Nothing was changed. Try again."**. The unavailable flag is never set by this outcome. |
| No Jira items yet | toolbar button disabled with `aria-disabled` and Small `text-gray-600` **"No Jira items to refresh."** (visible text, `aria-describedby`). |

Local inputs are never touched; after a refresh, scores and ranks do not change, only summary, Jira status, fetched time and the unavailable flag.

---

## CSV export (ROAD-07, D-17-18)

Button **"Export CSV"** (bordered style) at the right of the toolbar. It exports exactly what is on screen: the current product filter, the Done and archived toggles, the server's current order and every table column. A Small `text-gray-600` line beside it reads **"Exports the {n} items shown."** Pending: Spinner + **"Exporting CSV…"**, button disabled. Download uses the existing blob download (as `ExportButtons`). The file is UTF-8 and opens correctly in Excel; filename **(discretion)** `roadmap-{YYYY-MM-DD}.csv`; text cells guarded against formula injection server-side; the UI adds nothing else. Disabled with `aria-disabled` and helper **"Nothing to export."** when zero items are shown. Error: `role="alert"` Small `text-red-600` **"Couldn't export the CSV — {message}. Try again."**

---

## Page states

| Surface | Loading | Empty | Error |
|---------|---------|-------|-------|
| Roadmap table | Spinner + **"Loading roadmap…"** | No items at all: panel `rounded bg-gray-50 p-6 text-center`, heading **"No roadmap items yet"**, body **"Add items from Jira or by hand, then set Reach, Impact, Confidence and Effort to rank them."**, with the same three add buttons. Filters hide everything: heading **"No items match these filters"**, body **"Choose different products, or turn on Show done or Show archived."**, with **"Clear filters"** blue text link (clears `products`, `done`, `archived`). Only done/archived items exist and both toggles are off: same "No items match" panel. | `role="alert"` `data-reason` **"Couldn't load the roadmap — {message}. Try again."** + **Try again** |
| Product filter (products fetch) | none (control hidden until loaded) | hidden | the filter is hidden; the table still loads and the product column shows names from the item payload |
| Add / refresh / export | per section above | per section above | per section above |

---

## Screen: Release detail, Packages link (PKG-01/02, D-17-19..22, D-17-24)

### Placement

In the release header block of `ReleaseDetailRoute`, a new row **directly below `ProductsEditor`**, above the OPEN/FROZEN action row, for every release status (OPEN or FROZEN). Component `PackagesLink`, mirroring `ProductsEditor` (row + inline editor panel + polite announcement). The header order becomes:

```
meta line
ProductsEditor
PackagesLink                    <- new, always rendered
[OPEN] action row  /  [FROZEN] Unfreeze row + helper
actionProblem
frozen banner (FROZEN)
```

The packages link is not a template token and generated documents do not include it (D-17-22). Setting it never marks documents out of date, so there is **no** "marks documents out of date" warning (unlike `ProductsEditor`).

### Row (read mode)

`mt-2 flex flex-wrap items-center gap-2 text-sm`:

| State | Content |
|-------|---------|
| Link set (and passes the client-side check) | Label **"Packages:"** (`font-semibold text-gray-700`), then `<a href={url} target="_blank" rel="noopener noreferrer" className="font-semibold text-blue-600 hover:underline">Open packages</a>` followed by visually hidden **" (opens in a new tab)"**, then Small `text-gray-600` hostname (e.g. `contoso.sharepoint.com`; full URL in `title`), then `SMALL_BUTTON` **"Change"** (`aria-label="Change packages link"`) and `SMALL_BUTTON` **"Clear"** (`aria-label="Clear packages link"`). |
| Link set but fails the client-side check (defence in depth, should not happen) | Same row, but the URL is **not** a link: Small `text-amber-700` **"The saved packages link isn't a valid OneDrive or SharePoint link."** + **"Change"** + **"Clear"** |
| Not set | **"Packages:"** + `text-gray-500` **"Not set"** + `SMALL_BUTTON` **"Set packages link"** |

Buttons carry `aria-expanded` / `aria-controls="release-packages-editor"` (Set / Change) and return focus to themselves when the editor closes. The URL is rendered only through the `href` attribute and the hostname text child, never `dangerouslySetInnerHTML`.

### Editor (set / change)

Inline panel `id="release-packages-editor"`, `mt-3 rounded border border-gray-200 bg-white p-4` (same as `ProductsEditor`):
- Label (Label style) **"OneDrive or SharePoint link"**, `<input type="url" inputMode="url" autoComplete="off">` with `FIELD` class, prefilled when changing, `aria-describedby` to the helper and the error.
- Helper Small `text-gray-600`: **"Paste a link to the packages folder. Only https links on sharepoint.com, onedrive.live.com or 1drv.ms are accepted."**
- Buttons `mt-3 flex gap-2`: primary **"Save packages link"** (disabled when empty after trim, unchanged, or pending) and **"Cancel"** (bordered).
- Pending: Spinner + **"Saving…"** beside the buttons.
- Success: panel closes, focus to the row's Change / Set button, polite announcement **"Packages link saved."**

**Client-side pre-check** (advisory; the server is the authority and may reject what the client accepts or vice versa; the client never blocks a value the server would accept): on blur and on submit, parse with `new URL()`; valid only when `protocol === 'https:'` and the hostname equals or ends with `.sharepoint.com`, equals `onedrive.live.com` or equals `1drv.ms` (case-insensitive). Invalid values are not sent. The field gets `aria-invalid="true"` and Small `text-red-600` error text (not `role="alert"` for the blur check, the field's `aria-describedby` carries it; the submit-time check also moves focus to the field):

- **"Use an https link to OneDrive or SharePoint (sharepoint.com, onedrive.live.com or 1drv.ms)."** (used for non-URL text, non-https, and other hosts: one message so the fix is the same.)

**Server rejection** (400): the panel stays open, the typed value is kept, and `role="alert"` `data-reason={problem.reason}` Small `text-red-600` shows `messageForReason(problem)`. Required `messageForReason` entry (reason code is a placeholder, the planner aligns it to the server's enum): `RELEASE_PACKAGES_URL_INVALID` -> **"Use an https link to OneDrive or SharePoint (sharepoint.com, onedrive.live.com or 1drv.ms)."** Other failures fall through to the generic mapping, e.g. **"Couldn't save the packages link — {message}. Try again."**

### Clear

**"Clear"** opens the existing `ConfirmDialog` (no reason field; no audit, D-17-24): title **"Clear the packages link?"**, body **"The link is removed from this release. You can set it again later."**, confirm label **"Clear link"**, confirm class `rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50`, cancel **"Cancel"** (existing). `pending` follows the mutation; closes `onSettled` (the 15.1 rationale: an inert backdrop would hide an error paragraph). Success announces **"Packages link cleared."** Failure renders in the row's `role="alert"` paragraph (`text-sm text-red-600`, same pattern as `actionProblem`).

### Frozen state

Not blocked. No `aria-disabled`, no helper line, no "frozen" caveat: the control behaves identically for OPEN and FROZEN releases. The Unfreeze row and the frozen banner are unchanged.

---

## Screen: Release detail, "Add to roadmap" on tickets (D-17-10b)

The Tickets list (`Tickets ({n})`) gets a leading checkbox column. A selection row above the table appears once anything is ticked, `mt-2 flex flex-wrap items-center gap-3 text-sm`: Small `text-gray-600` **"{n} selected"** (polite), `SMALL_BUTTON` **"Select all"** / **"Clear"** and the bordered button **"Add to roadmap"**. Available in OPEN and FROZEN releases (it reads the release, it never changes it). Header checkbox `aria-label="Select all tickets"`, row checkboxes `aria-label="Select {KEY}"`, indeterminate state handled as in `TicketBrowser`. On press the tickets are posted to the roadmap and a result message shows below the selection row in the same copy as the Add result region, with the duplicate **"View it"** link to `/roadmap?item={id}` and, on success, a **"Go to Roadmap"** blue link (`/roadmap`). Failure: `role="alert"` **"Couldn't add to the roadmap — {message}. Try again."** Pending: button disabled, Spinner + **"Adding…"**.

---

## Copywriting Contract

| Element | Copy |
|---------|------|
| Primary CTA (page) | No single primary. Dialog submits: **"Add to roadmap"**, **"Add item"** |
| Nav link | **"Roadmap"** |
| Page heading | **"Roadmap"** |
| Toolbar | **"Add from Jira"**, **"Add from a release"**, **"Add manual item"**, **"Refresh from Jira"**, **"Export CSV"** |
| Filters | **"Products"**, **"All products"**, **"Show done"**, **"Show archived"**, **"{n} items shown"**, **"Clear filters"** |
| Rubric | **"How scoring works"**; formula **"Score = Reach × Impact × Confidence ÷ Effort. Items missing any input are not scored."**; units **"clients"**, **"person-weeks"**; Impact **"3 Massive · 2 High · 1 Medium · 0.5 Low · 0.25 Minimal"**; Confidence **"High 100% · Medium 80% · Low 50%"** |
| Table columns | **Rank · Item · Score · Reach · Impact · Confidence · Effort · Product · Status · Target quarter · Owner · Jira** |
| Unscored | **"Not scored"**; **"Missing: {inputs}"**; separator **"Not scored yet ({n}). Oldest first."** |
| Jira cell | **"Fetched {relative}"**, **"Last fetched {relative}"**, marker **"unavailable in Jira"**, **"Refresh"** |
| Reach hint | **"{product} has {n} linked clients"** / **"{product} has 1 linked client"** |
| Statuses | **Idea · Planned · In progress · Done** |
| Details panel | **"Details"**, **"Input history"**, **"Archive item"**, **"Restore item"**, history empty **"No input changes yet."** |
| Duplicate on add | **"{KEY} is already on the roadmap."** + **"View it"** |
| Empty (no items) | Heading **"No roadmap items yet"**; body **"Add items from Jira or by hand, then set Reach, Impact, Confidence and Effort to rank them."** |
| Empty (filters) | Heading **"No items match these filters"**; body **"Choose different products, or turn on Show done or Show archived."**; action **"Clear filters"** |
| Validation (Reach / Effort / Title) | **"Enter a whole number, 0 or more."**; **"Enter a number above 0, for example 0.5."**; **"Enter a title."** |
| Error (load) | **"Couldn't load the roadmap — {message}. Try again."** + **Try again** |
| Error (save input) | **"Couldn't save {input} — {message}. Try again."** |
| Error (Jira) | **"Couldn't reach Jira — {message}. Nothing was changed. Try again."** |
| Error (CSV) | **"Couldn't export the CSV — {message}. Try again."** |
| Packages row | **"Packages:"**, **"Open packages"**, **"Not set"**, **"Set packages link"**, **"Change"**, **"Clear"** |
| Packages editor | Label **"OneDrive or SharePoint link"**; helper **"Paste a link to the packages folder. Only https links on sharepoint.com, onedrive.live.com or 1drv.ms are accepted."**; button **"Save packages link"** |
| Packages error (client and server) | **"Use an https link to OneDrive or SharePoint (sharepoint.com, onedrive.live.com or 1drv.ms)."** |
| Destructive confirmation | Nothing in this phase deletes data. Archive is reversible and unconfirmed. **Clear the packages link?**: **"The link is removed from this release. You can set it again later."** with confirm **"Clear link"** (blue, not red: the value is a recoverable pointer, not data; same treatment as Unfreeze in 15.1). |
| Announcements (polite, one `role="status"` region per surface) | **"{title}: score {score}, rank {n}."**, **"{title}: not scored yet."**, **"{title} moved to Done."**, **"{title} archived."**, **"{title} restored."**, **"{n} items shown."**, **"Packages link saved."**, **"Packages link cleared."** |

---

## Interaction and Accessibility

- **Native semantics.** Real `<table>` with `scope`, real `<button>`, `<select>`, `<input>`, `<dialog>`. The detail row is a real `<tr>` controlled by the "Details" button's `aria-expanded`. The product filter is the existing `<details>` with `<fieldset>`.
- **Keyboard order.** Heading, toolbar buttons, product filter, Show done, Show archived, result region "Dismiss", rubric summary, then the table row by row: Details button, Reach, Impact, Confidence, Effort, Refresh. Escape inside a number input reverts it; Escape closes dialogs (returning focus to the button that opened them) and the product popover.
- **Focus.** After an inline save the focus stays on the edited control even if its row moves. Closing a detail row returns focus to its "Details" button. Opening via `item=<id>` scrolls the row into view (no smooth scroll under `prefers-reduced-motion`) and moves focus to its detail heading (`tabIndex={-1}`).
- **Live regions.** One visually hidden `role="status" aria-live="polite" aria-atomic="true"` region on the roadmap page for the announcement strings; the add/refresh result block is itself `role="status"`; error paragraphs use `role="alert"`.
- **Rubric and units.** Units are in the column headings at all times; the rubric block is a convenience, not the only place units appear (D-17-05, ROAD-02).
- **Blocked actions** (Refresh from Jira with no Jira items, Export CSV with nothing shown) use `aria-disabled="true"` + `aria-describedby` to a visible helper line, not `disabled`. Pending uses `disabled` plus Spinner.
- **Contrast.** `amber-700` on `amber-50`/white and `red-600` on white meet 4.5:1; essential text is `gray-600` or darker; `gray-400` is for dashes only, always with an `aria-label` or `title`.
- **Reduced motion.** The spinner is the only animation.
- **Plain-text safety.** Titles, Jira summaries, Jira statuses, owners, notes, quarters, product names and history values render as React text children only. Notes keep line breaks via `whitespace-pre-wrap`. No `dangerouslySetInnerHTML`. The packages `href` is only set after the client check; the link opens with `rel="noopener noreferrer"`.
- **Cache.** Add, edit, archive, refresh and product-link mutations invalidate the roadmap query key; release-products and archive/rename of products invalidate the roadmap too (the Reach hint reads linked-client counts). Packages-link mutations invalidate the release detail query only. No manual refresh button for the table.
- **Responsive.** Desktop-first internal tool. The table scrolls in `overflow-x-auto` with the Rank and Item columns sticky-left; toolbar and filter row wrap; dialogs are `max-w-full`.
- **No browser persistence.** State is URL search params (ids and flags only); the rubric open/closed state and detail-row expansion are not remembered.

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none | not applicable, no shadcn tool in use |
| third-party | none | not applicable |

---

## Requirement-to-UI Traceability

| Requirement / Decision | UI Contract Element |
|---|---|
| ROAD-01 (add from Jira by key/JQL, manual) | Add from Jira dialog (`TicketBrowser`), Add from a release dialog, Add manual item dialog, add result region |
| ROAD-02 (R/I/C/E with units and rubric, invalid rejected) | Inline R/I/C/E controls, units in headings, "How scoring works", inline validation, server rejection |
| ROAD-03 (ranked, missing inputs last, deterministic) | Ranked table, "Not scored yet" separator, server-side order |
| ROAD-04 (refresh, fetched time, never overwrites, flag vanished) | "Refresh from Jira" (all and per item), "Fetched {relative}", "unavailable in Jira" marker |
| ROAD-05 (product link, product filter) | Product select in Details, product column, `ProductFilter`, Reach hint |
| ROAD-06 (who/when/old-to-new history) | Details panel "Input history" |
| ROAD-07 (CSV export) | "Export CSV", what-is-on-screen helper line |
| PKG-01 (set / change / clear, OPEN or FROZEN, open from release detail) | `PackagesLink` row, editor, clear confirm, no frozen blocking |
| PKG-02 (assistant answers where packages are) | No UI. Answer wording is the assistant's; the link is the same value shown in the release header |
| D-17-04 | Reach hint text only, never auto-fills |
| D-17-05 | Rubric block plus permanent unit headings |
| D-17-06 | "Roadmap" nav link and `/roadmap` page |
| D-17-07 | Status, Notes, Target quarter, Owner in Details panel, read-only copies in the table |
| D-17-08 | Archive / Restore, "Show archived" |
| D-17-09 | Jira summary and status shown to every user; description and comments never shown |
| D-17-10 | Add from Jira / from a release dialogs, release detail "Add to roadmap" |
| D-17-11 | Duplicate message with "View it" link |
| D-17-12 / D-17-13 | Refresh buttons, fetched time, unavailable flag only after a successful Jira call |
| D-17-14 / D-17-15 / D-17-17 | Open items only, "Show done", server order, 1-decimal score |
| D-17-16 | Column list |
| D-17-18 | CSV exports what is on screen |
| D-17-19..22 | One link, https and host pre-check, editable in every status, "Open packages" in a new tab with `rel="noopener noreferrer"`, not a template token |
| D-17-24 | Clear allowed, no audit UI |

---

## Assumptions recorded (discretion, not settled by CONTEXT)

1. Item detail is an expandable table row, not a drawer.
2. Status, product, quarter, owner and notes are edited in the Details panel; only R/I/C/E are inline in the table.
3. Clearing a RICE input is not offered; inputs can be changed but not unset from the UI.
4. The rubric block is open by default and not remembered.
5. The "already on the roadmap" link is `/roadmap?item={id}` and widens the filters as needed so the item is visible.
6. Jira keys on roadmap items are plain text; no Jira base URL is assumed.
7. The packages Clear action uses `ConfirmDialog` with a blue confirm button.
8. The CSV filename `roadmap-{YYYY-MM-DD}.csv`, the add/refresh cap and the history scope for non-RICE fields are the planner's calls.
9. Relative fetched time is computed on render, not live-updated.
10. The release ticket list gains a checkbox column for "Add to roadmap"; the existing list has none today.
11. The server's reason code names (`RELEASE_PACKAGES_URL_INVALID`) are placeholders the planner aligns to the real enum.

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS
- [ ] Dimension 2 Visuals: PASS
- [ ] Dimension 3 Color: PASS
- [ ] Dimension 4 Typography: PASS
- [ ] Dimension 5 Spacing: PASS
- [ ] Dimension 6 Registry Safety: PASS

**Approval:** pending
