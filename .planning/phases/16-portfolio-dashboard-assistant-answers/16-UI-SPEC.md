---
phase: 16
slug: portfolio-dashboard-assistant-answers
status: approved
reviewed_at: 2026-10-22
shadcn_initialized: false
preset: none
created: 2026-10-22
---

# Phase 16 — UI Design Contract: Portfolio Dashboard & Assistant Answers

> Visual and interaction contract for PDASH-01..03 and ASST-11 on the home route `/` (`DashboardRoute`). Extends `15-UI-SPEC.md` (Portfolio visual language, tabs, "Show archived", amber markers, copy style) and amends `08-UI-SPEC.md` (assumption 1 and the summary-strip rule "tiles are plain numbers, no filtering"; reversed by D-16-13/14 for exactly two tiles). No new dependency, component library, chart library, icon library, type size or colour family.

Decisions D-16-01..D-16-15 in `16-CONTEXT.md` are locked and not reopened. Items marked **(discretion)** are Claude's-discretion choices made here so the planner does not have to.

New elements: (1) Releases / Portfolio tab bar, (2) multi-select product filter, (3) clickable and explained "Releases missing documents" / "Documents out of date" tiles with a filter chip, (4) Portfolio tab: four tiles, per-product summary table, "Clients behind" list with a "Not installed" group, client x product matrix, (5) assistant answer wording (ASST-11).

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none. No `components.json`. Not reopened (13-UI-SPEC). |
| Preset | not applicable |
| Component library | none. Native `<table>`, `<details>`, `<button>`, `<input type="checkbox">`, `<a>` via TanStack `Link`. Reuse `Tile`, `ReleaseMatrix`, `DeploymentHistory`, `Spinner` from `DashboardRoute.tsx`; the Portfolio tab-bar markup from `PortfolioRoute.tsx` (extract a shared `TabBar` if the planner wants; not required). |
| Icon library | none. Glyphs only: `?`, `×`, `—`, `·`, `…`. Spinner is the existing `h-4 w-4 animate-spin motion-reduce:animate-none rounded-full border-2 border-gray-300 border-t-blue-600`. |
| Chart library | none (D-08-09). No charts in this phase. |
| Font | system default (Tailwind `sans`). `tabular-nums` on every count. |

Stack facts: React, Vite, TypeScript, TanStack Router (`validateSearch` on the `/authenticated/` index route) and Query (hand-written fetch layer in `frontend/src/api/`), Tailwind v4 utilities. Errors render through `ApiError.problem` and `messageForReason`, `role="alert"`, `data-reason={problem.reason}`. Whether the Portfolio tab reads one endpoint or several is the planner's call **(discretion, D-16 list)**; this contract assumes each Portfolio section loads and fails independently (see States).

---

## Spacing Scale

Reuses the Phase 2-15 scale. No new tokens.

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4px | Chip/label gaps (`gap-1`), caption-to-figure gap (`mt-1`) |
| sm | 8px | Control gaps (`gap-2`), chip padding, `mt-2` helper lines |
| md | 16px | Tile padding (`p-4`), table cells (`px-4 py-2`), grid gaps (`gap-4`), `mt-4` between blocks |
| lg | 24px | Empty-state panel padding (`p-6`), `mt-6` before sub-sections |
| xl | 32px | `space-y-8` between the major sections of a tab (existing) |
| 2xl | 48px | Not used |
| 3xl | 64px | Not used |

Exceptions (inherited classes, matched exactly, not new tokens): `px-3 py-1.5` bordered buttons and controls; `px-2 py-0.5` chips and markers; `px-2 py-1` small row actions; 12px (`gap-3`, `p-3`) in the filter popover and amber notices, as in 15-UI-SPEC.

Fixed dimensions: filter popover `w-64` (256px) with checklist `max-h-64 overflow-y-auto` (256px); help toggle `h-6 w-6` (24px) glyph button; matrix client column `sticky left-0 bg-white min-w-[12rem]` (192px); matrix product columns `min-w-[14rem]` (224px). All on the 4px grid.

---

## Typography

Reuses the Phase 5 / 8 / 15 scale verbatim. 4 sizes, 2 weights (400, 600).

| Role | Size | Weight | Line Height |
|------|------|--------|-------------|
| Body | 14px (`text-sm`) | 400 | 1.5 |
| Label | 14px (`text-sm`) | 600 | 1.5 |
| Heading | 16px (`text-base`) | 600 | 1.2 |
| Display (tile figures only, existing `text-lg`) | 18px | 600 | 1.2 |
| Small (dates, markers, helper lines, chips, footnotes) | 12px (`text-xs`) | 400; 600 inside chips and markers | 1.5 |

Usage:
- "Dashboard" page heading and section headings ("Releases", "Products", "Clients behind", "Client x product matrix", "Deployment history"): Heading 16/600.
- Tile figures: Display 18/600 `tabular-nums`; tile captions Body `text-gray-600`; tile unit/help lines Small.
- Table column headings, product/client names in rows, tab labels (active): Label 14/600; inactive tab and cell text: Body 14/400.
- Release name inside a cell: `font-semibold text-blue-600 hover:underline` (Label); installed date, version, markers: Small.

---

## Color

No new colour. Existing palette and existing meanings.

| Role | Value | Usage |
|------|-------|-------|
| Dominant (60%) | `gray-50` canvas, empty-state panels | Page, empty states |
| Secondary (30%) | `white` with `border-gray-200` | Tiles, tables, matrix, filter popover; `gray-100` for selected rows and the filter chip |
| Accent (10%) | `blue-600` | See reserved-for list |
| Destructive | `red-600` / `red-700` | Load-failure text only. Nothing in this phase is destructive; no red confirm buttons. |
| Warning | `amber-700` text, `amber-50` bg, `amber-600` border | "not in Production" marker, "behind" marker, readiness-unavailable note |
| Success | `green-700` text | "Up to date" text in the matrix only |
| Muted | `gray-500` / `gray-400` text | Archived rows, "Not installed", dashes, units |

Accent (`blue-600`) reserved for:
1. **Selected tab underline**: `border-b-2 border-blue-600` on the active tab (text `text-gray-900 font-semibold`), copied from the Portfolio page.
2. **Links**: release links (`font-semibold text-blue-600 hover:underline`), client-name links, "Clear filter" / "Clear" text links, "Portfolio page" links.
3. The pending-spinner leading edge.
4. `focus-visible:ring-2 focus-visible:ring-blue-600` on every new control (tabs, tiles that are buttons, "?" toggle, filter summary, product-name buttons, checkboxes).

There is no filled primary button on this screen (read-only dashboard). Tile active state is not blue: an active clickable tile uses `border-gray-900 bg-gray-50` (border-2 is avoided; use `ring-1 ring-gray-900`) and `aria-pressed="true"`.

Colour is never the only signal: "not in Production", "behind", "Up to date", "archived", "Not installed" are all words.

---

## Screen: Dashboard (`/`), shell

Search params (all optional, absent = default, malformed values dropped to default, changes use `navigate({ replace: true })`, like the existing `all` flag; ids only, nothing in `localStorage`):

| Param | Values | Default | Notes |
|-------|--------|---------|-------|
| `tab` | `releases` \| `portfolio` | `releases` | D-16-01 |
| `products` | array of product ids | empty = all products | D-16-02/03; preserved across tabs |
| `docs` | `missing` \| `stale` | none | Releases tab only; D-16-13/14 |
| `archived` | `true` | off | Portfolio tab only; D-16-07 |
| `all`, `stage`, `from`, `to` | existing | existing | unchanged |

Layout, top to bottom, inside the existing `space-y-8` wrapper:

```
<h2> Dashboard
Filter row:   [Products: All products v]  (selected chips are not repeated; summary text only)
Tab bar:      Releases | Portfolio
Tab panel
```

(Filter row and tab bar sit in one block with `space-y-4`, so the 32px rhythm starts after the tab bar.)

### Product filter (D-16-02, D-16-03)

- Control: native `<details>` disclosure. Summary is a bordered button `rounded border border-gray-300 px-3 py-1.5 text-sm`, visible label **"Products"** (Small `text-gray-600`, above, same as the History filter labels) and button text: **"All products"** when none selected; the one name when one is selected; **"{name}, {name}"** for two; **"{n} products"** for three or more (full list in `title`).
- Popover: `absolute z-10 mt-1 w-64 rounded border border-gray-200 bg-white p-3`. A `<fieldset>` with `<legend className="sr-only">Filter by product</legend>`, `max-h-64 overflow-y-auto`, one native checkbox row per active product (Body label), sorted by name case-insensitive. A product already in the URL that is archived is listed with " (archived)" so it can be unticked. Footer row `mt-3`: **"Clear"** text link (blue, disabled/hidden when nothing selected) and nothing else; changes apply immediately (no Apply button).
- Selecting nothing means all products. Escape closes the popover and returns focus to the summary; clicking outside closes it.
- No products exist: the control is hidden entirely (the page shows its own empty state).
- The filter narrows the Releases tab (tiles, matrix, deployment history) and the whole Portfolio tab. A release covering two selected products is counted once in every total (PDASH-01).
- Filter-active signal: the summary button gets `bg-gray-100` when anything is selected, and the strip footnote names the scope (see Releases tab).

### Tab bar (D-16-01)

Identical markup to the Portfolio page: `role="tablist" aria-label="Dashboard views"`, `flex gap-4 border-b border-gray-200`, two `role="tab"` buttons **"Releases"** and **"Portfolio"** with `aria-selected`, `aria-controls`, `px-1 pb-2 text-sm`, active `-mb-px border-b-2 border-blue-600 font-semibold text-gray-900`, inactive `text-gray-600 hover:text-gray-900`. Left/Right arrows move between tabs (roving `tabIndex`); the panel is `role="tabpanel"`. No counts on tabs. Switching tabs keeps `products`; it clears `docs` (Releases only) and keeps `archived`.

---

## Releases tab (today's view, amended)

Order unchanged: summary strip, release matrix, deployment history. All three respect the product filter.

### Summary strip

Same grid (`grid grid-cols-2 gap-4 lg:grid-cols-5`). Only the first two tiles change.

**Tile "Releases missing documents"** (was "Need paperwork") and **"Documents out of date"** (was "Stale"), D-16-13/14:

- The tile becomes a `<button type="button" aria-pressed={active}>` filling the tile (`w-full text-left`), same surface classes as `Tile`, plus `hover:bg-gray-50`. Figure in Display, caption Body, then a Small unit line `text-xs text-gray-500` **"releases"** (the count's unit, so the number cannot be misread as documents; the planner confirms `paperworkCounts` counts releases for both and adjusts the unit word if it does not).
- A `?` help toggle sits top-right of the tile, **outside** the pressing button (sibling, absolutely positioned `right-2 top-2`), `h-6 w-6 rounded border border-gray-300 text-xs font-semibold text-gray-700`, `aria-label="What does this count?"`, `aria-expanded`, `aria-controls="{tile}-help"`. Pressing it toggles an inline paragraph inside the tile (not a hover-only tooltip, so keyboard and touch users get it): `id="{tile}-help"` `mt-2 text-xs text-gray-600`. The same text is also the toggle's `title`. Clicking the toggle never applies the filter.
- Help copy, missing: **"Releases with at least one document that has not been generated yet or is out of date. Document types that have no template are ignored."**
- Help copy, out of date: **"Releases with a document that is out of date because tickets changed after it was generated. Frozen releases are never counted, their documents do not go out of date."**
- Click behaviour: pressing a tile sets `docs=missing` / `docs=stale`; pressing the active tile clears it; pressing the other tile switches. Active tile: `ring-1 ring-gray-900 bg-gray-50`.
- Readiness unavailable (readiness request failed, T-05-60): both tiles show **"—"**, the button is `aria-disabled="true"` (no filtering, click does nothing, stays in Tab order) with `aria-describedby` to a Small `text-amber-700` line in the tile **"Document status unavailable"**. The rest of the dashboard is unaffected, as today.

The other three tiles (two medians, Releases per stage) are unchanged and are not clickable. Tiles are computed over the product-filtered visible set, and are **not** narrowed by `docs` (so the strip does not change under the user when a tile is pressed).

Footnote under the strip (replaces "Over {n} visible releases."): **"Over {n} visible releases."** when no product filter; **"Over {n} visible releases in {product names, or '{k} products'}."** when filtered.

### Filter chip and matrix

Directly under the matrix heading row, only while `docs` is set: a row `mt-2 flex flex-wrap items-center gap-2 text-sm` containing chip `rounded border border-gray-300 bg-gray-100 px-2 py-0.5 text-xs font-semibold text-gray-700` with text **"Showing: Releases missing documents · {k} of {n} releases"** (or **"Showing: Documents out of date · {k} of {n} releases"**) and a **"Clear filter"** blue text link (`text-xs`). The chip container is `role="status"` so the change is announced politely. The matrix rows are the visible releases whose readiness (via the existing `readinessIndex` lookup, never recomputed per cell) matches the same rule as the tile, so the table row count equals the tile figure. `<tfoot>` "Median (visible)" stays over the product-filtered visible set, unchanged by `docs`.

Matrix heading caption, loading, error and the two existing empty states are unchanged. New empty state when `docs` is set and no rows match: Body `text-gray-600` in the usual `bg-gray-50 p-6 text-center` panel, heading **"No releases match this filter"**, body **"Every visible release has up-to-date documents."** (for `missing`: **"Every visible release has all of its documents generated and current."**), with **"Clear filter"**. New empty state when the product filter leaves no releases (and `docs` is not set): heading **"No releases for these products"**, body **"Nothing in the selected products is open, frozen or in a stage short of Production, and nothing shipped in the last 90 days."** with **"Clear"** products link and the existing **"Show all releases"** link.

The existing "No releases yet" state (zero releases anywhere) is unchanged.

Deployment history: gets the product filter only (server-side, ids in the request). Its own stage/date filters, copy and states are unchanged.

---

## Portfolio tab (new)

Order, `space-y-8`:

```
Header line: "Show archived" checkbox (right)  [+ helper sentence]
[1] Four tiles            (grid-cols-2 lg:grid-cols-4 gap-4)
[2] Products table        (per-product summary, D-16-04)
[3] Clients behind        (id="behind"): list + "Not installed" group (id="not-installed")
[4] Client x product matrix
```

Helper sentence (Body `text-gray-600`, left of the checkbox): **"What each client runs, and who is behind the newest frozen release of each product. Installs are recorded from a release's page."**

"Show archived" (D-16-07): checkbox, default off, sets `archived=true`. Archived clients and archived products are hidden everywhere on this tab (tiles, tables, matrix, behind list) unless it is on; when on they appear with Small gray **"archived"** tag after the name (names `text-gray-500`). Tile counts follow the checkbox (archived excluded by default).

### [1] Tiles (D-16-15)

Same `Tile` look. Figure Display, caption Body, Small `text-xs text-gray-500` second line where noted. All respect the product filter and the archived checkbox.

| Caption | Figure | Second line | Click |
|---------|--------|-------------|-------|
| **Clients behind** | count of client/product pairs behind | **"client/product pairs"** | button: moves focus to the "Clients behind" section heading and scrolls it into view (`#behind`); no URL state |
| **Not installed** | count of linked pairs with no successful install | **"linked, nothing installed"** | button: same, targeting the "Not installed" group heading (`#not-installed`) |
| **Active products / clients** | **"{p} / {c}"** | **"products / clients"** | not clickable (plain `Tile`) |
| **Installs last 30 days** | count of effective SUCCESS installs | **"successful installs"** | not clickable |

Clickable tiles use the same button treatment and `hover:bg-gray-50`; they carry `aria-describedby` to the second line. Each tile has the `?` help toggle pattern from the Releases tiles with copy:
- Clients behind: **"A client is behind when a newer frozen release of that product exists than the one it runs. Newest is by frozen date, not by version."**
- Not installed: **"Clients linked to a product with no successful install recorded. They are not counted as behind."**
- Installs last 30 days: **"Successful installs recorded in the last 30 days. Voided and superseded installs are not counted."**
- Active products / clients: no help toggle.

### [2] Products table (PDASH-01, D-16-04)

Heading **"Products"**, caption (Small `text-gray-500`): **"Releases per product. A release that covers several products appears in each product's row; totals elsewhere count it once."**

Table `min-w-full text-sm` in `overflow-x-auto rounded border border-gray-200 bg-white`, header `px-4 py-2 text-left font-semibold text-gray-700`, rows `border-b border-gray-100 hover:bg-gray-50`. Columns (`scope="col"`):

| Column | Cell |
|--------|------|
| Product | `<button>` with the product name (Label, `text-gray-900`, `hover:underline`, focus ring); archived: `text-gray-500` + "archived" tag. Pressing sets `products=[thisId]` (replacing any selection). If this is already the only selected product, pressing clears the product filter. `aria-pressed` reflects "is the sole selected product"; the row gets `bg-gray-100`. `aria-label="Filter dashboard to {name}"`. |
| Open | count, `tabular-nums` |
| Frozen | count of frozen releases not yet in Production |
| In Production | count that reached Production |
| Newest frozen | release link `{name} {version}` (blue, semibold) + Small frozen date (`toLocaleDateString()`), or `—` `text-gray-400` with `title="No frozen release yet"` |
| Clients on newest | **"{n} of {m} clients"** (`tabular-nums`) where m = linked clients; Small **"all up to date"** `text-green-700` when n = m and m > 0; `—` when no frozen release or m = 0 |

Rows sorted by name, case-insensitive, archived last. Respects the product filter (only selected products show; with none selected, all show) and the archived checkbox. A trailing `<tfoot>` row **"All selected products"** (or **"All products"**) shows the de-duplicated totals for Open / Frozen / In Production (each release counted once), leaving the other columns blank; this is the visible proof of PDASH-01. Releases with no product are not expected (D-15-18); if the API returns any, they appear in a final row named **"Unassigned"** (`text-gray-500`, defensive only, same wording as Phase 15).

### [3] Clients behind (PDASH-03, D-16-09..12)

Section `id="behind"`, heading **"Clients behind"** (Heading, `tabIndex={-1}` so the tile can focus it), caption Small `text-gray-500`: **"Compared with the newest frozen release of each product, whether or not it has reached Production."**

List table (same table classes), one row per client/product pair that is behind, sorted by releases-behind descending, then client name, then product name. Columns:

| Column | Cell |
|--------|------|
| Client | link to `/portfolio?tab=clients&client={id}` (blue, semibold), archived tag if shown (D-16-08) |
| Product | Body |
| Runs | release link `{name} {version}` + Small installed date; amber Small **"not in Production"** after it when applicable (same marker text as 15) |
| Behind | amber marker `rounded border border-amber-600 bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-700` with text **"{n} releases behind"** (**"1 release behind"** when n = 1) |
| Newest | release link `{name} {version}` + Small frozen date |

Row-level sentence for assistive tech and narrow wrapping (visually hidden `sr-only` inside the Behind cell, after the marker): **"{n} releases behind: newest is {name}"** and, in the cell's visible second line, Small `text-gray-600` **"newest is {name}"** is not repeated because the Newest column already shows it. Where a single-cell rendering is needed (matrix cell, below) the phrase reads **"{n} releases behind: newest is {name}"**, per the user's example.

Empty (no pairs behind, but linked pairs exist): panel `rounded bg-gray-50 p-6 text-center`, heading **"Everyone is up to date"**, body **"Every client that has an install runs the newest frozen release of its product."** No linked pairs at all: see Portfolio-level empty state.

**"Not installed" group** (D-16-12), `mt-6`, `id="not-installed"`, sub-heading Label with `tabIndex={-1}`: **"Not installed ({n})"**, caption Small `text-gray-500` **"Linked to the product, nothing installed yet. Not counted as behind."** A simple list `mt-2 divide-y divide-gray-100`, `<li className="flex flex-wrap items-center gap-2 py-2 text-sm">`: client link (blue), `·`, product name, `grow` spacer, Small `text-gray-600` **"Newest available: {name} {version}"** as a release link, or **"No frozen release yet"** `text-gray-400`. Sorted by product then client. Group is hidden when n = 0 (no empty copy; absence is the signal).

### [4] Client x product matrix (PDASH-02, D-16-05..08)

Heading **"Client x product"**, caption Small `text-gray-500`: **"The release each client currently runs, per product."** Table in `overflow-x-auto rounded border border-gray-200 bg-white`, `min-w-full text-sm`. Header: first column **"Client"** (`scope="col"`, sticky-left, `bg-white`), then one `scope="col"` column per product (Label, archived tag when shown). Row headers are the client names (`<th scope="row">`, link to the client detail, blue semibold, archived tag). Rows sorted by client name case-insensitive, archived last; columns sorted by product name, archived last. Respects the product filter (columns) and the archived checkbox (rows and columns). Cell contents (`align-top`, `px-4 py-2`), first match wins:

1. **Not linked** (D-16-06): `—` in `text-gray-400`, `aria-label="Not linked to {product}"`.
2. **Linked, no effective successful install** (D-16-06, D-15-12): **"Not installed"** `text-gray-500`.
3. **Runs a release** (D-16-05): stacked
   - release link **"{release name} {version}"** (`font-semibold text-blue-600 hover:underline`, to `/releases/$releaseId`);
   - Small `text-gray-600` **"Installed {date}"** (`toLocaleDateString()`; full `toLocaleString()` in `title`);
   - when the release has not reached Production: Small amber text **"not in Production"** (`text-xs text-amber-700`);
   - behind status, one of: amber marker **"{n} releases behind"** (same classes as the behind list) followed by Small `text-gray-600` **"newest is {name}"**; or Small `text-green-700` **"Up to date"** when running the newest frozen release; or nothing when the product has no frozen release (nothing to be behind).

"Currently runs" comes from the `current_client_release` view (D-15-05) and is displayed as received; the UI never derives it. Behind status is received from the API (the UI does not count releases or compare dates). No popover, no inline expansion (D-16-08); the client name is the only path to history.

### Portfolio-level states

| Surface | Loading | Empty | Error |
|---------|---------|-------|-------|
| Tiles + products table | spinner + **"Loading portfolio…"** | no products exist: heading **"No products yet"**, body **"Add products and clients on the Portfolio page, then record installs from a frozen release."** with **"Portfolio page"** link (blue underline, `/portfolio`); other portfolio sections are not rendered | `role="alert"` red: **"Couldn't load the portfolio — {message}. Try again."** + gray-bordered **Try again** |
| Behind list / Not installed | spinner + **"Loading clients behind…"** | per [3] | **"Couldn't load clients behind — {message}. Try again."** + **Try again** |
| Matrix | spinner + **"Loading client matrix…"** | products exist but no clients (after archived filter): heading **"No clients yet"**, body **"Add a client on the Portfolio page, then link it to the products it runs."** with the same link. Product filter leaves no columns: heading **"No products match this filter"**, body **"Choose different products, or clear the filter to see all of them."** with **"Clear"** | **"Couldn't load the client matrix — {message}. Try again."** + **Try again** |

Each section loads and fails independently so one failing endpoint blanks only itself.

---

## Copywriting Contract

| Element | Copy |
|---------|------|
| Primary CTA | None. Read-only screen; controls are the product filter, tabs, tile filters and the "Show archived" checkbox. |
| Tab labels | **"Releases"**, **"Portfolio"** |
| Product filter label / values | **"Products"**; **"All products"**; **"{name}"**; **"{n} products"**; legend (sr-only) **"Filter by product"**; link **"Clear"** |
| Tile (renamed) | **"Releases missing documents"** (was "Need paperwork"), unit **"releases"** |
| Tile (renamed) | **"Documents out of date"** (was "Stale"), unit **"releases"** |
| Tile help toggle | `aria-label` **"What does this count?"** |
| Help: missing | **"Releases with at least one document that has not been generated yet or is out of date. Document types that have no template are ignored."** |
| Help: out of date | **"Releases with a document that is out of date because tickets changed after it was generated. Frozen releases are never counted, their documents do not go out of date."** |
| Tile filter chip | **"Showing: Releases missing documents · {k} of {n} releases"** / **"Showing: Documents out of date · {k} of {n} releases"** + **"Clear filter"** |
| Readiness unavailable (tiles) | **"Document status unavailable"** |
| Empty: docs filter, none match | Heading **"No releases match this filter"**; body **"Every visible release has all of its documents generated and current."** / **"Every visible release has up-to-date documents."**; action **"Clear filter"** |
| Empty: product filter, no releases | Heading **"No releases for these products"**; body **"Nothing in the selected products is open, frozen or in a stage short of Production, and nothing shipped in the last 90 days."**; actions **"Clear"**, **"Show all releases"** |
| Footnote (filtered scope) | **"Over {n} visible releases in {names}."** |
| Portfolio tiles | **"Clients behind"**, **"Not installed"**, **"Active products / clients"**, **"Installs last 30 days"** |
| Portfolio helper | **"What each client runs, and who is behind the newest frozen release of each product. Installs are recorded from a release's page."** |
| Show archived | **"Show archived"** (checkbox); row tag **"archived"** |
| Products table heading / caption | **"Products"**; **"Releases per product. A release that covers several products appears in each product's row; totals elsewhere count it once."** |
| Products table columns | **Product · Open · Frozen · In Production · Newest frozen · Clients on newest** |
| Clients on newest | **"{n} of {m} clients"**; **"all up to date"** |
| Product row action | `aria-label` **"Filter dashboard to {name}"** |
| Behind section | Heading **"Clients behind"**; caption **"Compared with the newest frozen release of each product, whether or not it has reached Production."** |
| Behind marker | **"{n} releases behind"** / **"1 release behind"**; combined phrase **"{n} releases behind: newest is {name}"** |
| Behind columns | **Client · Product · Runs · Behind · Newest** |
| Behind empty | Heading **"Everyone is up to date"**; body **"Every client that has an install runs the newest frozen release of its product."** |
| Not installed group | **"Not installed ({n})"**; caption **"Linked to the product, nothing installed yet. Not counted as behind."**; row suffix **"Newest available: {name} {version}"** / **"No frozen release yet"** |
| Matrix | Heading **"Client x product"**; caption **"The release each client currently runs, per product."** |
| Matrix cells | not linked **"—"** (`aria-label` "Not linked to {product}"); **"Not installed"**; **"Installed {date}"**; **"not in Production"**; **"{n} releases behind"** + **"newest is {name}"**; **"Up to date"** |
| Portfolio empty: no products | Heading **"No products yet"**; body **"Add products and clients on the Portfolio page, then record installs from a frozen release."**; link **"Portfolio page"** |
| Portfolio empty: no clients | Heading **"No clients yet"**; body **"Add a client on the Portfolio page, then link it to the products it runs."** |
| Matrix empty: filter | Heading **"No products match this filter"**; body **"Choose different products, or clear the filter to see all of them."** |
| Loading | **"Loading portfolio…"**, **"Loading clients behind…"**, **"Loading client matrix…"** (spinner + text) |
| Error | **"Couldn't load the {portfolio / clients behind / client matrix} — {message}. Try again."** + **Try again** (`role="alert"`, `data-reason`) |
| Destructive confirmation | None. This phase has no destructive action; nothing is created, changed or deleted from the dashboard. |
| Announcements (polite, one `role="status"` region) | **"Filtered to {names}."**, **"Showing all products."**, **"Showing {k} of {n} releases."**, **"Filter cleared."** |

Existing copy kept verbatim from `08-UI-SPEC.md` (matrix headings, durations, churn/code-link cells, history filters, "No releases yet", "Show all releases") is not restated.

---

## Interaction and Accessibility

- **Native semantics.** Tabs use the ARIA tabs pattern with roving tabindex (as `PortfolioRoute`). Tile buttons are real `<button>`s with `aria-pressed`. The "?" toggle is a real `<button>` with `aria-expanded`; help text is in the DOM and visible when expanded, never hover-only. Filter popover is `<details>` with a `<fieldset>`. Tables use `<th scope="col">` / `<th scope="row">`.
- **Blocked actions** (tiles while readiness is unavailable) use `aria-disabled="true"` + `aria-describedby` to the visible note, not `disabled`.
- **Keyboard order.** Heading, product filter, tab bar, then the panel. Releases: tiles (each tile button, then its "?" toggle), matrix header checkbox, filter chip "Clear filter", table links, history filters. Portfolio: "Show archived", tiles, product-name buttons, behind list links, not-installed links, matrix links in row-major order.
- **Focus.** Pressing a Portfolio tile moves focus to the target heading (`tabIndex={-1}`) and scrolls it into view (respecting `prefers-reduced-motion`: no smooth scroll). Clearing a tile filter returns focus to the tile that set it. Closing the filter popover with Escape returns focus to its summary.
- **Live regions.** One visually hidden `role="status" aria-live="polite" aria-atomic="true"` region announces the events listed in the copy table; error paragraphs use `role="alert"`.
- **Contrast.** `amber-700` on `amber-50`/white, `green-700` on white, `red-600` on white meet 4.5:1; essential text is `gray-600` or darker; `gray-400` is for dashes and "No frozen release yet" only, always with a text equivalent in `aria-label` or `title`.
- **Reduced motion.** The spinner is the only animation (`motion-reduce:animate-none`).
- **Plain-text safety.** Product names, client names, release names and versions render as React text children only, never `dangerouslySetInnerHTML`.
- **Cache.** Install, void, correct, promotion, freeze and product-assignment mutations invalidate the dashboard and portfolio query keys so counts and "behind" stay consistent with the release and client pages. No manual refresh button (as Phase 8).
- **Degradation.** A readiness failure blanks only the document cells and disables the two document tiles (T-05-60); portfolio sections never depend on readiness.
- **Responsive.** Desktop-first internal tool. Tiles `grid-cols-2` below `lg`; every table scrolls in `overflow-x-auto`; matrix client column sticky-left; popover stays within the viewport (`max-w-[calc(100vw-2rem)]`).
- **No persistence in the browser.** All state is in URL search params (ids and flags only).

---

## Assistant answers (ASST-11)

No new UI. The existing Assistant page renders the answer as it does for other tools; the contract is the answer wording the tool's structured fields must support. The tool reads `current_client_release` and the ledger live (CONTEXT, discretion), so figures match this dashboard. Answers are produced by the assistant from structured fields; the system prompt/tool description must steer toward these shapes:

| Question | Answer shape |
|----------|--------------|
| Which release does {client} run for {product}? | **"{Client} runs {release name} {version} for {Product}, installed {date}."** Add **"This release has not reached Production."** when `inProduction` is false. |
| Client linked, nothing installed | **"{Client} is linked to {Product} but has no successful install recorded."** |
| Client not linked to product | **"{Client} isn't linked to {Product}."** |
| Which clients run {release}? | **"{n} clients currently run {release name} {version}: {Client A} (installed {date}), {Client B} (installed {date})."** More than 10: first 10 then **"and {n} more"**. |
| No client currently runs it | **"No client currently runs {release name} {version}."** (Distinct from "never installed": a client whose current release is a newer one is not listed.) |
| Unknown or ambiguous client/product/release | Existing `ReleaseRef` / name-resolution behaviour: ask which one is meant, listing the candidates; never guess. |

"Runs" always means the effective current release (D-15-05, D-15-16), the same as the dashboard matrix. Archived clients are included when asked about by name and tagged **"(archived)"**. No tables, charts or links beyond the existing assistant citation behaviour are added.

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
| PDASH-01 (grouped by product, product filter, no double-count) | Product filter; Products table with de-duplicated `<tfoot>` totals; "Over {n} visible releases in {names}" footnote |
| PDASH-02 (client x product matrix) | Portfolio tab [4] matrix |
| PDASH-03 (clients behind) | Portfolio tab [3] list, "Not installed" group, "Clients behind" tile |
| ASST-11 | "Assistant answers" section |
| D-16-01 | Tab bar, `tab` URL param |
| D-16-02 / D-16-03 | Multi-select filter in the URL, narrows tiles, matrix, history and Portfolio tab |
| D-16-04 | Products table; product-name button sets the filter |
| D-16-05 / D-16-06 | Matrix cell contents and the two empty cells |
| D-16-07 | "Show archived" checkbox and "archived" tags |
| D-16-08 | Client name links to Portfolio > Clients > client; release links to release detail; no popover |
| D-16-09..12 | Behind list: newest frozen target, ordered by frozen date, "N releases behind: newest is X", separate "Not installed" group |
| D-16-13 / D-16-14 | Renamed clickable tiles, "?" help text, filter chip; amends 08-UI-SPEC assumption 1 |
| D-16-15 | Four Portfolio tiles |
| Established pattern (T-05-60) | Readiness failure disables only the two document tiles |

---

## Assumptions recorded (discretion, not settled by CONTEXT)

1. Tooltip mechanism: an inline-expanding "?" toggle plus `title`, not a hover popover.
2. Click-to-filter state is shown as a chip above the matrix and a `docs` URL param; tiles stay computed over the product-filtered set, not the `docs`-filtered set.
3. Portfolio tab tiles for Clients behind / Not installed scroll to and focus their section rather than filtering, so there is no second filter model on that tab.
4. The Products table is on the Portfolio tab (CONTEXT: the Portfolio tab "holds the new views") and shows only the selected products; pressing the sole-selected product clears the filter.
5. Behind counts and "newest" are computed by the API; a frozen release later rolled back in promotion still counts as a target while it stays frozen (CONTEXT's expected default; the planner states it). The UI renders what it receives.
6. The tile unit word "releases" is confirmed against `paperworkCounts` at implementation time.
7. Archived products appear in the filter popover only when already selected in the URL.
8. Matrix cell date format is `toLocaleDateString()`, with full timestamp in `title`.
9. The count of "10 clients" cap in assistant answers is a presentation default; the tool may return more structured rows.

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS
- [ ] Dimension 2 Visuals: PASS
- [ ] Dimension 3 Color: PASS
- [ ] Dimension 4 Typography: PASS
- [ ] Dimension 5 Spacing: PASS
- [ ] Dimension 6 Registry Safety: PASS

**Approval:** pending
