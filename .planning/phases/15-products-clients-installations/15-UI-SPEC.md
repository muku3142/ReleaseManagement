---
phase: 15
slug: products-clients-installations
status: approved
reviewed_at: 2026-10-20
shadcn_initialized: false
preset: none
created: 2026-10-20
---

# Phase 15 — UI Design Contract: Products, Clients & Installations

> Visual and interaction contract for PORT-01..PORT-08. Extends the existing app shell, the promotion UI (`PromotionSection`, `RecordPromotionDialog`) and the admin-list pattern (`EnvironmentLadderSection`). Baseline: `14-UI-SPEC.md`. No new dependency, no component library, no chart library, no icon library, no new type size, no new colour family. New elements: (1) "Portfolio" nav item and route with Products / Clients tabs, (2) client detail panel ("runs" view), (3) products editor on release detail, (4) "Client installs" section with record dialog and history, (5) required product picker in the create-release form (D-15-18), (6) products column in the Releases table ("Unassigned" kept only as a defensive label). Revised 2026-10-21 for D-15-17 (existing releases get a backfilled product per Jira project) and D-15-18 (products are required).

Decisions D-15-01..D-15-13 in `15-CONTEXT.md` are locked and not reopened here.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none. No `components.json`. Not reopened (decided in 13-UI-SPEC). |
| Preset | not applicable |
| Component library | none. Native `<button>`, `<select>`, `<input>`, `<textarea>`, `<dialog>` (via `showModal()`), `<details>`, `<table>`. Existing `ConfirmDialog` is reused for destructive confirmations. |
| Icon library | none. Plain text and glyphs only (`·`, `—`, `…`). Pending state is the existing spinner `h-4 w-4 animate-spin motion-reduce:animate-none rounded-full border-2 border-gray-300 border-t-blue-600`. |
| Font | system default (Tailwind `sans`). `font-mono` is not used in this phase. |

Stack facts: React, Vite, TypeScript, TanStack Router (new `/portfolio` route in `router.tsx` under `authenticatedRoute`) and Query (hand-written fetch layer in `frontend/src/api/`, new `products.ts`, `clients.ts`, `installs.ts`; mutations invalidate their lists and the affected release), Tailwind v4 utilities only. Errors come from `ApiError.problem` rendered through `messageForReason`, `role="alert"`, `data-reason={problem.reason}`, exactly as the promotion UI does.

Where the release is created: the create-release form lives in `AddToReleaseDialog.tsx` ("New release…" toggle), not in `ReleasesRoute`. The product picker goes there.

---

## Spacing Scale

Reuses the Phase 2-14 scale. No new tokens.

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4px | Chip gaps (`gap-1`), tab/label gaps |
| sm | 8px | Button gaps (`gap-2`), list row padding (`py-2`), label-to-control gaps |
| md | 16px | Section padding (`p-4`), table cell `px-4`, gap between panels (`gap-4`), `mt-4` between blocks |
| lg | 24px | Page padding (`p-6`, existing), dialog padding (`p-6`), `mt-6` between sections |
| xl | 32px | Not used |
| 2xl | 48px | Not used |
| 3xl | 64px | Not used |

Exceptions (inherited classes, matched exactly, not new tokens): `px-3 py-1.5` bordered buttons and controls; `px-4 py-2` filled primary buttons and dialog Cancel; `px-2 py-1` small row-action buttons (`SMALL_BUTTON` from `EnvironmentLadderSection`); `px-2 py-0.5` badges and chips; `py-0.5` tab-free pills; `px-3 py-2` form inputs (as in `RecordPromotionDialog`).

Fixed dimensions: Portfolio body is a two-column grid from `lg` up (`grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]`): list left, detail right; single column below `lg` (list, then detail). Client checklist in the install dialog: `max-h-64 overflow-y-auto` (64 = 256px, on the 4px grid). Dialog width: natural (`<dialog>` default sizing), with `w-[32rem] max-w-full` on the install dialog (512px, on the grid).

---

## Typography

Reuses the Phase 5 / 13 / 14 scale verbatim. 4 sizes max, 2 weights (400 and 600).

| Role | Size | Weight | Line Height |
|------|------|--------|-------------|
| Body | 14px (`text-sm`) | 400 | 1.5 |
| Label | 14px (`text-sm`) | 600 (`font-semibold`) | 1.5 |
| Heading | 16px (`text-base`) | 600 | 1.2 |
| Small (metadata, helper lines, chips, badges, row actions) | 12px (`text-xs`) | 400; 600 inside chips and badges | 1.5 |

Note: the app shell title uses `text-lg font-semibold` (existing, untouched). The existing `font-medium` on a few legacy buttons is not copied; new buttons use `font-semibold` for filled primary buttons and 400 for bordered ones, matching Phase 14.

Usage:
- Page heading "Portfolio", panel titles ("Products", "Clients", client name in detail, "Client installs", "Products" on release detail): Heading 16/600.
- Form labels, table column headings, product name inside rows, tab labels: Label 14/600 (inactive tab 14/400).
- Row text, helper paragraphs, empty-state body, dialog body: Body 14/400.
- Dates, "recorded by", notes in history, "archived" tag, "Superseded" tag, counters: Small 12.

---

## Color

No new colour. Existing palette only, mapped to the existing outcome colours.

| Role | Value | Usage |
|------|-------|-------|
| Dominant (60%) | `gray-50` page canvas, `white` panels | Page, panels, table bodies |
| Secondary (30%) | `white` cards with `border-gray-200`; `gray-100` tags, tab bar underline rail, selected list row | Panels, list rows, tags |
| Accent (10%) | `blue-600` | See reserved-for list |
| Destructive | `red-600` / `red-700` text | Error text, FAILED outcome text, and confirm buttons of Archive only where noted below |
| Warning | `amber-700` text, `amber-50` bg, `amber-600` border | Not-in-Production warning, "not in Production" marker, ROLLED_BACK outcome text, removal-blocked notices |
| Muted | `gray-500` / `gray-400` text | Archived rows, superseded rows (`text-gray-400`, as promotion history), VOIDED |
| Success | `green-700` text | SUCCESS outcome text only (existing `OUTCOME_COLOURS`) |

Accent (`blue-600`) reserved for:
1. **Filled primary buttons**: "Add product", "Add client", "Record install", "Save correction" (one filled button per form or dialog).
2. **Selected tab underline**: `border-b-2 border-blue-600` on the active Portfolio tab (text stays `text-gray-900 font-semibold`).
3. Links to releases (`text-blue-600 hover:underline`, existing convention) and the existing "Production" marker text style `text-blue-700`.
4. `focus-visible:ring-2 focus-visible:ring-blue-600` on every new control, and the spinner's leading edge.

Everything else is gray-bordered (`border border-gray-300 text-gray-700`): Rename, Archive, Unarchive, Link, Unlink, Correct, Void, Cancel, Edit products. Archive uses a gray-bordered button and a plain (non-red) `ConfirmDialog` confirm button `bg-blue-600`: archiving is reversible, so it is not destructive-styled. Void is the only red confirm (`rounded bg-red-600 px-4 py-2 text-sm font-semibold text-white`), because it writes a permanent ledger row. Colour is never the only signal: outcomes carry text labels, "archived" / "Superseded" / "not in Production" are text, superseded rows use `text-gray-400` plus the word "Superseded".

---

## Navigation

`AppShell.tsx`: add a `Link to="/portfolio"` labelled **"Portfolio"**, same classes and `activeProps` as siblings, placed between "Releases" and "Assistant" (order: Dashboard, Tickets, Releases, Portfolio, Assistant, Templates, Settings). Always visible (no feature gate). Phases 16 and 17 add content to this route, so the route owns the page, not Settings.

---

## Screen: Portfolio (`PortfolioRoute`, `/portfolio`)

Search params: `tab` (`products` | `clients`, default `products`), `client` (selected client id, Clients tab only). Using search params keeps tab and selection linkable and survives reload.

Layout, top to bottom, inside `<div className="w-full">`:

```
white header block (bg-white p-6): heading "Portfolio", one helper line, tab bar
body (mt-6): Products tab  = single panel (product list)
             Clients tab   = grid: client list | client detail
```

Header block: Heading **"Portfolio"**; helper (Body `text-gray-600`, `mt-2`): **"The products you ship and the clients that run them. Record which clients installed a release from that release's page."**

Tab bar: `role="tablist"` `aria-label="Portfolio"`, `mt-4 flex gap-4 border-b border-gray-200`. Two `role="tab"` buttons **"Products"** and **"Clients"** with `aria-selected`, `aria-controls`, `px-1 pb-2 text-sm`; active: `border-b-2 border-blue-600 font-semibold text-gray-900` (offset `-mb-px`), inactive `text-gray-600 hover:text-gray-900`. Left/Right arrow keys move between tabs (roving `tabIndex`); the panel is `role="tabpanel"`. Tab counts are not shown.

### Products tab

One panel `rounded border border-gray-200 bg-white p-4`.

1. Panel top row (`flex flex-wrap items-center gap-3`): Heading **"Products"**, `grow` spacer, checkbox **"Show archived"** (default off; Small label, native checkbox) .
2. **Add form** (`mt-3`, same shape as the ladder's "New stage name"): label **"New product name"** (Label), input `maxLength={100}` `required`, filled button **"Add product"**. Placed above the list in a `flex flex-wrap items-end gap-2` row (input `w-72`, 288px).
3. **List** `<ul className="mt-4 divide-y divide-gray-100">`, sorted by name case-insensitive, archived last when shown. Each `<li className="flex flex-wrap items-center gap-2 py-2 text-sm">`:
   - Name (Label, `text-gray-900`; archived: `text-gray-500 line-through`) and, when archived, Small tag **"archived"** (`text-xs text-gray-500`).
   - Small `text-gray-600`: **"{n} clients · {m} releases"** (client count active+archived linked; release count covered). Counts are optional if the API does not supply them; the planner may drop the release count but not the client count.
   - `grow` spacer, then row actions (`SMALL_BUTTON`): **Rename**, **Archive** (or **Unarchive** when archived).
   - Rename swaps the row content for an inline form identical to `EnvironmentLadderSection` rename (input `aria-label="Name for {name}"`, **Save**, **Cancel**), focus on the input; Enter saves, Escape cancels.
4. Archive opens `ConfirmDialog` (title **"Archive product"**, confirm label **"Archive"**, blue confirm). Body: **"Archive {name}? It keeps its clients and install history, but can't be added to new releases or receive new installs. You can unarchive it at any time."** Unarchive acts immediately (no confirm).
5. Errors (duplicate name, too long, server) in one `role="alert"` paragraph under the form: `mt-2 text-sm text-red-600`, via `messageForReason`. Duplicate name copy: **"A product named '{name}' already exists. Names are not case-sensitive. Choose a different name."**
6. Loading: spinner + **"Loading products…"**. Load failure: **"Couldn't load products — {message}. Try again."** + gray-bordered **Try again** (same pattern as `DeploymentHistory`).

### Clients tab

Left panel (client list), right panel (client detail). Both `rounded border border-gray-200 bg-white p-4`.

**Client list panel** — same structure as the Products panel: Heading **"Clients"**, "Show archived" checkbox, add form (label **"New client name"**, button **"Add client"**), list. Each row is a selectable item: the name is a `<button>` that selects the client (`aria-current="true"` when selected; selected row `bg-gray-100`), Small `text-gray-600` **"{n} products"**, archived tag, and the row actions **Rename**, **Archive** / **Unarchive** (same behaviour and confirm as products; archive body: **"Archive {name}? It keeps its products and install history, but can't receive new installs. You can unarchive it at any time."**). Duplicate client name copy mirrors products.

**Client detail panel** (PORT-06, D-15-11/12/13). When no client is selected: Body `text-gray-600`: **"Select a client to see what it runs."** Otherwise, top to bottom:

1. Header row: Heading = client name (+ "archived" tag), nothing else.
2. **"Runs"** block (Label heading, `mt-4`): table `min-w-full text-sm`, columns **Product**, **Current release**, **Installed**, and an actions column with no heading text (visually hidden `<span className="sr-only">Actions</span>`). One row per linked product (archived products shown with "archived" tag, sorted by name):
   - Product (Label).
   - Current release when an effective SUCCESS install exists: release link `text-blue-600 font-semibold hover:underline` to `/releases/$releaseId` with text **"{release name} {version}"**; if the release has not reached Production, a Small amber marker next to it **"not in Production"** (`text-xs text-amber-700`, per D-15-11).
   - Installed: `new Date(installedAt).toLocaleString()`, Body `text-gray-700`.
   - When there is no effective SUCCESS install (D-15-12): Current release cell shows **"Not installed"** in `text-gray-500` (Body) and the Installed cell **"—"**. Never hidden.
   - Actions: **Unlink** (`SMALL_BUTTON`). If the client has any install record for that product, the button is `aria-disabled="true"` (`opacity-50 cursor-not-allowed`, stays in Tab order) with `aria-describedby` pointing at the row's explanatory Small line (below); clicking does nothing. Without installs, Unlink acts after `ConfirmDialog` (title **"Unlink product"**, body **"Unlink {product} from {client}? You can link it again later."**, confirm **"Unlink"**, blue).
   - Explanation line under a blocked row (Small `text-amber-700`, shown always for blocked rows, not only on hover): **"Can't unlink: {client} has install records for {product}. Archive the client instead to stop new installs."** with the word "Archive" not a link (the Archive button is in the list panel). This is the D-15-13 copy.
3. **Link a product** control (`mt-3`, only if the client is not archived): row `flex flex-wrap items-end gap-2`: label **"Link a product"** (Label) over a `<select>` listing active products not yet linked (first option `Choose a product…`, `disabled`, selected) and a gray-bordered **Link** button, disabled until a product is chosen. Archived products are not offered (D-15-07; decided here for the Claude's-discretion question: no new links to archived products). If every active product is linked: Small `text-gray-600` **"Every active product is already linked."** If no products exist at all: **"No products yet. Add one on the Products tab."** with "Products tab" as a button that switches tab.
4. **"Install history"** block (Label heading, `mt-6`): the client's full install history across products, newest `installedAt` first, including superseded and voided rows. Row format identical to the release-side history (see "Install history row") except that it is read-only (no Correct/Void here, since installs are recorded and corrected only from the release page, D-15-09) and the release is a link: **"{Product} · {release name} {version}"**. Empty: Body `text-gray-600` **"No installs recorded for this client yet. Installs are recorded from a frozen release's page."**
5. Load/failure states as in the Products tab.

Archived client: Link control hidden, existing links and history remain visible, Unlink rules unchanged.

---

## Screen: Release detail changes (`ReleaseDetailRoute`)

Existing order: header block (`bg-white p-6`), items, ..., `PromotionSection`, `DocumentsSection`. Phase 15 inserts:

```
header block: name, version, status badge, meta line
[A] Products line + editor        (inside header block, below the meta line, mt-2)
... items (existing) ...
PromotionSection (existing)
[B] ClientInstallsSection         (new, directly after PromotionSection, same section styling)
DocumentsSection (existing)
```

### [A] Products line and editor (D-15-01, D-15-03, D-15-18)

In the header block, below the meta `div`, a row `mt-2 flex flex-wrap items-center gap-2 text-sm`:
- Label **"Products:"** (Label 14/600, `text-gray-700`), then one chip per product: `rounded border border-gray-300 bg-gray-100 px-2 py-0.5 text-xs font-semibold text-gray-700` (archived products append ` (archived)` inside the chip, text only).
- Zero products: the single muted text **"Unassigned"** (Body `text-gray-500`) in place of the chips. Defensive only: V14 backfills a product for every existing release (D-15-17) and the server never lets a release have none (D-15-18), so this shows only if the data is somehow inconsistent.
- Gray-bordered **"Edit products"** button (`SMALL_BUTTON`), available on OPEN and FROZEN releases alike (no `requireOpen` gating, D-15-01). `aria-expanded`, `aria-controls="release-products-editor"`.

Editor (`id="release-products-editor"`, opens inline directly below the row, `mt-3 rounded border border-gray-200 bg-white p-4`, no modal):
- Label **"Products covered by this release"** (Label). A checkbox list `max-h-64 overflow-y-auto` of all active products (name, Label-less 14/400), plus any currently assigned archived product shown checked with ` (archived)`; archived products can be unchecked but cannot be newly checked (their checkbox is `disabled` when unchecked and the row carries Small `text-gray-500` "archived").
- A product that has install records for this release is shown checked and **locked**: checkbox `aria-disabled`/`disabled`, with Small `text-amber-700` line under it: **"Can't remove: installs are recorded for this product on this release."** (D-15-01, D-15-15: any ledger row, including voided or superseded ones, blocks removal permanently.)
- **Last product locked (D-15-18):** whenever exactly one product is checked, that checkbox is `aria-disabled="true"` (unchecking does nothing) with Small `text-amber-700` line under it: **"A release needs at least one product."** (linked by `aria-describedby`). Checking another product releases the lock, so replacing A with B is: tick B, untick A. If the locked product also has installs, both explanation lines show.
- Empty (no products exist at all): Body `text-gray-600` **"No products yet. Add one on the Portfolio page."** with "Portfolio page" a `Link to="/portfolio"` (`text-blue-600 underline`).
- Buttons row (`mt-3 flex gap-2`): filled **"Save products"** (disabled while unchanged, pending, or with no product checked) and gray-bordered **"Cancel"**. Save closes the editor, focus returns to "Edit products", and a polite status announcement says **"Products updated."**
- Staleness note (D-15-02 revised, Option A), Small `text-xs text-amber-700` under the buttons, shown only when the release is OPEN (frozen documents never show stale, D-05-09): **"Changing products marks this release's existing documents as out of date. Regenerate them to include the change."**
- Server error (including the blocked-removal case naming the product) in `role="alert"` `text-sm text-red-600`.

### [B] Client installs section (`ClientInstallsSection`, D-15-09)

`<section id="installs" className="mt-6 rounded border border-gray-200 bg-white p-4">`, styled identically to `PromotionSection`. Always rendered, content depends on state:

Header row (`flex items-center justify-between`): Heading **"Client installs"**; right side a link `text-sm text-blue-600 hover:underline` **"Promotion history"** (`href="#history"`).

States (first match wins):
1. Loading: **"Loading…"** (Body `text-gray-600`). Failure: red `role="alert"` via `messageForReason`.
2. **OPEN release**: Body `text-gray-600` **"Freeze this release to record client installs."** No history to show (open releases cannot have installs).
3. **FROZEN, zero products** (defensive only, see D-15-17/D-15-18): Body `text-gray-600` **"This release is Unassigned. Add the products it covers (Edit products above) before recording installs."** Existing history (if any) cannot exist.
4. **FROZEN with products**: intro line then button then history.
   - Not-in-Production notice, shown above the button only when the release has not reached the Production stage: `mt-3 rounded border border-amber-600 bg-amber-50 p-3 text-sm text-amber-700`, title (Label) **"Not in Production yet."**, body **"This release hasn't been promoted to Production. You can still record installs; they will be marked 'not in Production'."** (This inline copy is informational; the dialog repeats the warning, below.)
   - Filled **"Record install"** button (`mt-3 rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white`).
   - History list (below).

### Record install dialog (`RecordInstallDialog`, native `<dialog>` + `showModal()`, same mount/reset pattern as `RecordPromotionDialog`)

`rounded border border-gray-200 bg-white p-6`, width `w-[32rem] max-w-full`, title Heading **"Record install"** (or **"Correct install"**). Form `mt-4 space-y-3`; fields in this order:

1. **Product** — label **"Product"**, `<select id="installProduct">` of the release's products (archived products `disabled`, suffixed ` (archived)`). If the release has exactly one product it is preselected and the select is still shown. Changing product clears the client selection.
2. **Clients** — `<fieldset>` with `<legend>` **"Clients that installed it"** (Label). Body: if the product has no active linked clients: Body `text-gray-600` **"No active clients are linked to {product}. Link clients on the Portfolio page."** with a `Link to="/portfolio"` ("Portfolio page") and the Record button disabled. Otherwise a checkbox list `max-h-64 overflow-y-auto rounded border border-gray-300 p-3`, one row per active linked client sorted by name (checkbox + Body label); above it, a row with Small gray-bordered **"Select all"** and **"Clear"** buttons and a live count Small `text-gray-600` **"{n} selected"** (`aria-live="polite"`). Archived clients are not listed (D-15-07). Ticking several is the bulk install (PORT-07). Not shown in Correct mode (a correction keeps the single client; the client name is shown as read-only Body text **"Client: {name}"**).
3. **Outcome** — `<select id="installOutcome">`: Success, Failed, Rolled back (Correct mode adds Voided via the Void action, not this select; the select offers the three recordable outcomes only). Default **Success**. When **Rolled back** is selected, Small `text-gray-600` helper under the select (D-15-16): **"A rollback doesn't change what the client runs. To show the client went back to an older release, record a Success install of that release."**
4. **Installed at** — label **"Installed at (leave empty for now)"**, `datetime-local` (`step` 1 in Correct mode), `max` = now. Empty = now. Back-dating is allowed and no warning is shown for it. Helper (Small `text-gray-600`, `mt-1`): **"Back-dating an install never replaces a newer one as the client's current release."** (D-15-05). Future values are rejected client-side with Small red text **"Installed time can't be in the future."**
5. **Note (optional)** — `<textarea maxLength={2000}>`.
6. Correct mode only: **Reason for correction** — `required` textarea, `maxLength={2000}` (as `RecordPromotionDialog`). Prefilled from the original: release (fixed), product (fixed), client (fixed), installed time, outcome, note (D-15-06).
7. **Not-in-Production warning** — in the dialog, directly above the buttons, only when the release has not reached Production: `rounded border border-amber-600 bg-amber-50 p-3 text-sm text-amber-700`, `role="status"`, title (Label) **"This release isn't in Production yet."**, body **"You can still record this install. It will appear as 'not in Production' on the client's page."** Never blocks submit.
8. **Error region** (above buttons, `role="alert"`, `data-reason`): `text-sm text-red-600`. Bulk failure (D-15-10) shows the all-or-nothing copy and names the offending clients as a list:
   - Lead (Label, `text-red-700`): **"Nothing was recorded."**
   - Body: **"These clients can't receive this install: {name}, {name}, …. Untick them or fix their links on the Portfolio page, then record again."** If the server provides a reason per client, append it in parentheses after each name (e.g. `Acme Bank (archived)`). More than 5 names: first 5 then **"and {n} more"**. Selections stay ticked; offending clients get a Small red `text-xs text-red-700` suffix **"can't receive this install"** in the checklist so they are findable without relying on colour alone (suffix is text).
9. Buttons (`flex justify-end gap-3`): gray-bordered **Cancel** (`px-4 py-2`), filled **"Record install"** / **"Save correction"**. Record label is count-aware when more than one client is ticked: **"Record install for {n} clients"**. Disabled while pending, with no client selected, or (Correct) with empty reason. Pending shows spinner + **"Recording…"** / **"Saving…"** on the button.

Keyboard/focus: `showModal()` focuses the first control (Product select); Escape closes (existing dialog behaviour, discards input); on success the dialog closes and focus returns to the **Record install** button (or the **Correct** button that opened it); polite announcement **"Install recorded for {n} client(s)."** / **"Correction saved."** / **"Install voided."**

### Install history row (release page, D-15-06)

`<ul className="mt-4 divide-y divide-gray-100">`, ordered newest `installedAt` first, grouped visually by product with a Small `text-gray-600 font-semibold` product sub-heading only when the release has more than one product with installs. Each `<li className={`py-2 text-sm ${effective ? '' : 'text-gray-400'}`}>`, copying the promotion row:

- Line 1: client name (`font-semibold`, `text-gray-900` when effective) + product name (Small, only when not grouped) + outcome label `OUTCOME_LABELS` in `OUTCOME_COLOURS` (effective only) + when superseded a Small tag **"Superseded"** (`ml-2 text-xs`). Superseded rows are struck through: client name gets `line-through` (D-15-06 / specifics: "superseded rows struck through"). VOIDED effective rows show the outcome **"Voided"** in `text-gray-500`.
- Line 2 (Small): **"Installed {datetime} · Recorded {datetime} by {user}"**; a Small amber **"not in Production"** marker (`text-amber-700`) appended after a `·` when the install was flagged.
- Note: `mt-1` Body (effective `text-gray-700`).
- Correction reason: Small **"Correction: {reason}"**.
- Row actions (effective rows only, FROZEN release): gray-bordered `SMALL_BUTTON` **Correct** and **Void** (`mt-1 flex gap-2`). Correct opens the dialog in Correct mode. **Void** opens `ConfirmDialog` with a required reason: title **"Void install"**, body **"Void the install of {product} for {client}? The original stays visible as superseded. This can't be undone, but you can record a new install afterwards."**, a required `<textarea aria-label="Reason for voiding">` as `children`, confirm **"Void install"** (red), disabled until the reason is non-empty. A voided row has no actions.
- Empty history (FROZEN, products exist): Body `text-gray-600` **"No client installs recorded yet."**

---

## Create-release form changes (D-15-03, D-15-18)

In `AddToReleaseDialog.tsx`, inside the "New release…" form, after Version and before the errors: a required field

- `<fieldset>` with `<legend>` **"Products"** (Label; no "(optional)"), Small helper `text-gray-600`: **"Choose at least one product this release covers. You can change them later."**
- A checkbox list (`max-h-40 overflow-y-auto rounded border border-gray-300 p-3`, 160px) of active products, sorted by name; 14/400 labels. Native checkboxes, not a custom multi-select. Archived products are not listed (D-15-07).
- No active products (D-15-18): Small `text-gray-600` **"Add a product on the Portfolio page first."** (plain text with a `Link to="/portfolio"` on "Portfolio page"; opening it leaves the dialog, which is acceptable since nothing is created yet). The form cannot be submitted.
- At least one product must be ticked: **"Create & add"** (label unchanged) is disabled while none is ticked, in addition to its existing pending rule. The selection is always sent with the create request. A server rejection (`RELEASE_PRODUCT_REQUIRED`, or `PRODUCT_ARCHIVED` if a product was archived meanwhile) shows in the form's existing error paragraph: **"Choose at least one product for this release."**

---

## Releases table (`ReleasesRoute`) and "Unassigned"

Add one column **"Products"** after "Version" (heading Label 14/600, `px-4 py-2`). Cell: up to 2 chips (same chip style as release detail), then Small `text-gray-600` **"+{n}"** if more (full list in the cell's `title`); zero products (defensive only, D-15-17/D-15-18): **"Unassigned"** in `text-gray-500` Body. No filter or grouping by product in this phase (Phase 16).

---

## Copywriting Contract

| Element | Copy |
|---------|------|
| Primary CTA (Portfolio, products) | **"Add product"** |
| Primary CTA (Portfolio, clients) | **"Add client"** |
| Primary CTA (release page) | **"Record install"** (**"Record install for {n} clients"** when more than one is ticked) |
| Save products | **"Save products"** |
| Nav label | **"Portfolio"** |
| Empty state: products tab | Heading **"No products yet"**; body **"Add your first product above. Releases can then be tagged with the products they cover."** |
| Empty state: clients tab | Heading **"No clients yet"**; body **"Add a client above, then link it to the products it runs."** |
| Empty state: client detail (nothing selected) | **"Select a client to see what it runs."** |
| Empty state: client with no links | **"This client isn't linked to any product yet. Use 'Link a product' below."** |
| Empty state: client install history | **"No installs recorded for this client yet. Installs are recorded from a frozen release's page."** |
| Empty state: release installs, none yet | **"No client installs recorded yet."** |
| Empty state: release not frozen | **"Freeze this release to record client installs."** |
| Empty state: release unassigned (defensive) | **"This release is Unassigned. Add the products it covers (Edit products above) before recording installs."** |
| Create form: products helper | **"Choose at least one product this release covers. You can change them later."** |
| Create form: no active products | **"Add a product on the Portfolio page first."** (link on "Portfolio page") |
| Products editor: last product lock | **"A release needs at least one product."** |
| Error: create without a product | **"Choose at least one product for this release."** (`RELEASE_PRODUCT_REQUIRED`) |
| Error: product set would become empty | **"A release needs at least one product."** (`RELEASE_LAST_PRODUCT`) |
| Empty state: product has no linked clients (dialog) | **"No active clients are linked to {product}. Link clients on the Portfolio page."** |
| Release with no products (defensive only) | **"Unassigned"** |
| Never installed | **"Not installed"** |
| Not-in-Production warning (dialog) | **"This release isn't in Production yet."** + **"You can still record this install. It will appear as 'not in Production' on the client's page."** |
| Not-in-Production marker | **"not in Production"** |
| Archived marker | **"archived"** |
| Superseded marker | **"Superseded"** |
| Name too long / duplicate | **"A product named '{name}' already exists. Names are not case-sensitive. Choose a different name."** (clients: "A client named '{name}' …") |
| Error: product removal blocked | **"Can't remove: installs are recorded for this product on this release."** (D-15-15; server error shown verbatim via `messageForReason` if it differs) |
| Error: unlink blocked | **"Can't unlink: {client} has install records for {product}. Archive the client instead to stop new installs."** |
| Error: bulk install rejected (all-or-nothing) | **"Nothing was recorded."** + **"These clients can't receive this install: {names}. Untick them or fix their links on the Portfolio page, then record again."** |
| Error: record/correct failure (generic) | **"Couldn't record the install — {messageForReason}. Try again."** |
| Error: load failure | **"Couldn't load {products / clients / installs} — {message}. Try again."** + **Try again** button |
| Destructive: Void | **Void install**: **"Void the install of {product} for {client}? The original stays visible as superseded. This can't be undone, but you can record a new install afterwards."** Required reason; buttons **Cancel** / **Void install** (red) |
| Reversible: Archive product | **Archive product**: **"Archive {name}? It keeps its clients and install history, but can't be added to new releases or receive new installs. You can unarchive it at any time."** Buttons **Cancel** / **Archive** |
| Reversible: Archive client | **Archive client**: **"Archive {name}? It keeps its products and install history, but can't receive new installs. You can unarchive it at any time."** Buttons **Cancel** / **Archive** |
| Reversible: Unlink | **Unlink product**: **"Unlink {product} from {client}? You can link it again later."** Buttons **Cancel** / **Unlink** |
| Staleness note | **"Changing products marks this release's existing documents as out of date. Regenerate them to include the change."** |
| Back-date helper | **"Back-dating an install never replaces a newer one as the client's current release."** |
| Announcements (polite) | **"Products updated."**, **"Install recorded for {n} client(s)."**, **"Correction saved."**, **"Install voided."**, **"{name} archived."**, **"{name} unarchived."** |

Destructive actions in this phase: only **Void** (permanent ledger row) is destructive-styled. Archive, Unarchive and Unlink are reversible and use the normal blue confirm. Installs and products are never deleted (ledger is append-only, entities are archived).

---

## Interaction and Accessibility

- **Native semantics.** Tabs use the ARIA tabs pattern with roving tabindex. Dialogs are native `<dialog>` via `showModal()` (focus trap and Escape provided by the platform), as in `RecordPromotionDialog`. Checklists are `<fieldset>`/`<legend>` with native checkboxes. Rename inputs have `aria-label`. Tables use `<th scope="col">`.
- **Blocked actions** (Unlink with installs, locked product removal, the last checked product in the editor) use `aria-disabled="true"` plus `aria-describedby` to the visible explanation, not the `disabled` attribute, so screen-reader users hear why. Click handlers do nothing.
- **Keyboard order.** Portfolio: tabs → show-archived checkbox → add form → list rows (select, Rename, Archive) → (Clients) detail: Link control → table row actions → history. Release page: Edit products (and editor controls) → items → Record install → history rows (Correct, Void). Dialog order is the field order above, Cancel before the primary button.
- **Focus return.** Rename Save/Cancel → the row's Rename button. Archive confirm → list row (or the "Show archived" checkbox if the row disappears). Dialog close → the opener. Editor Save/Cancel → "Edit products".
- **Live regions.** One visually hidden `role="status" aria-live="polite" aria-atomic="true"` region per page announces the events listed in the copy table. Error paragraphs use `role="alert"` (announced once). The bulk-selected count uses `aria-live="polite"`.
- **Contrast.** `amber-700` on `amber-50`/white and `red-700`/`red-600` on white meet 4.5:1; `gray-400` is used only for superseded rows (decorative-weight history, the word "Superseded" is also present) as in the existing promotion history; essential text uses `gray-600` or darker.
- **Reduced motion.** The spinner is the only animation (`motion-reduce:animate-none`), label text always shown.
- **Plain-text safety.** Product names, client names, notes and correction reasons render as React text children only, never `dangerouslySetInnerHTML`; they are user-entered and must not be interpreted.
- **Pending and double-submit.** All mutating buttons disable while any related mutation is pending (as `EnvironmentLadderSection`'s `busy`). The record dialog cannot be submitted twice.
- **Time handling.** Datetime-local to ISO conversion and the "untouched keeps original instant verbatim" rule are copied from `RecordPromotionDialog` (`toLocalInput`, `deployedAtEdited`).
- **Responsive.** Desktop-first internal tool. Portfolio grid stacks below `lg`; button rows wrap; tables scroll in `overflow-x-auto`.
- **No persistence in the browser.** Tab and selected client live in the URL search params (ids only). Nothing is written to `localStorage` or `sessionStorage`.

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
| PORT-01 / PORT-02 (register products and clients: create, rename, archive) | Portfolio route, Products and Clients tabs, add form, inline rename, archive/unarchive with confirm |
| PORT-03 (client <-> product links) | "Link a product" control and "Runs" table with Unlink on client detail |
| PORT-04 (release covers products; PORT-03 wording: at least one required) | Required picker in the create-release form, products editor with last-product lock on release detail, products column (defensive "Unassigned") |
| PORT-05 / PORT-07 (record install, bulk) | "Client installs" section, Record install dialog with product select and client checklist (Select all / Clear / count) |
| PORT-06 (what each client runs) | Client detail "Runs" table (current release, installed date, not-in-Production marker, "Not installed") plus full install history |
| PORT-08 (templates print products) | No UI beyond the products being editable; the existing template placeholder drawer lists the new placeholder (planner adds the entry to `PlaceholderDrawer` data if it is catalogue-driven) |
| D-15-01 | "Edit products" available on OPEN and FROZEN; locked checkbox with explanation when installs exist |
| D-15-02 | Staleness note in the editor, OPEN releases only (Option A) |
| D-15-03 (revised) | Product picker in create form; products editable on release detail; "Unassigned" only as a defensive label |
| D-15-04 | Outcome select: Success / Failed / Rolled back; Voided only via Void |
| D-15-05 | Back-date helper line; client detail shows only the effective SUCCESS release |
| D-15-06 | Correct (prefilled, required reason) and Void; superseded rows struck through and tagged |
| D-15-07 | "archived" tags; archived items excluded from pickers and checklists; links and history stay visible; Unarchive |
| D-15-08 | Portfolio nav item and route, not Settings |
| D-15-09 | Installs recorded only in the release-page section; client page install history is read-only |
| D-15-10 | All-or-nothing error: "Nothing was recorded." plus offending client names |
| D-15-11 / D-15-12 | Runs table with "Not installed" row, never hidden |
| D-15-13 | Unlink blocked with the "Archive the client instead" explanation |
| D-15-15 | Locked product in the editor with "Can't remove: installs are recorded for this product on this release." |
| D-15-16 | Rolled back hint under the outcome select |
| D-15-17 | No UI of its own: existing releases arrive with a backfilled product chip named after their Jira project; the Products tab lists those products, renamable like any other |
| D-15-18 | Required create picker ("Create & add" disabled until one is ticked), "Add a product on the Portfolio page first." empty state, last checked product locked with "A release needs at least one product." |

---

## Assumptions recorded (not settled by CONTEXT)

1. The Portfolio page uses tab + selected-client search params (`?tab=clients&client={id}`) so views are linkable.
2. The Clients tab is a list + detail split. A separate `/portfolio/clients/$id` route was rejected to avoid a navigation hop; the planner may split it if the detail view grows in Phase 16.
3. No new links from a client to an archived product, and archived products/clients are never offered in pickers (extends D-15-07 to the open "link to archived product" discretion item).
4. Install correction keeps release, product and client fixed (as stated in D-15-06: prefilled from the original: release, installed time, outcome, note). Changing the release of an install is out of scope: void and record again.
5. "Select all" in the client checklist selects only active linked clients of the chosen product.
6. Void requires a reason, like Correct, since it writes a superseding ledger row. If the API makes the reason optional for void, keep the field optional and drop the disabled rule.
7. "Production reached" for the not-in-Production warning and marker is derived by the API (an `inProduction` boolean per release and per install row); the UI does not compute stage logic.
8. Counts on Portfolio list rows ("{n} clients", "{n} products") are shown if cheaply available from the list endpoints; release counts on product rows are optional.
9. Product chips on the Releases table cap at 2 with "+{n}"; no filtering by product (Phase 16).
10. Sorting: names case-insensitive ascending, archived last. Install history newest `installedAt` first, ties by `recordedAt`.
11. The product/client name length cap is 100 (matches ladder stage names); notes and reasons 2000 (matches promotion notes).

---

## Checker Sign-Off

- [x] Dimension 1 Copywriting: FLAG (non-blocking)
- [x] Dimension 2 Visuals: FLAG (non-blocking)
- [x] Dimension 3 Color: PASS
- [x] Dimension 4 Typography: PASS
- [x] Dimension 5 Spacing: FLAG (non-blocking)
- [x] Dimension 6 Registry Safety: PASS

**Approval:** approved 2026-10-20 (gsd-ui-checker, 6 non-blocking FLAGs: generic Cancel/Save labels, single-word row actions need aria-labels with the item name, no declared focal point per screen, accent list omits Save products and blue confirm buttons, 12px spacing not in the token table, inherited py-1.5/py-0.5 exceptions)
