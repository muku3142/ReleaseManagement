# Phase 23: UI Baseline Audit (pre-foundation)

**Audited:** 2026-10-26
**Scope:** the whole frontend (`frontend/src`, about 17,000 lines across 107 files), not a single phase
**Baseline:** the abstract 6-pillar standards, plus the existing contracts in 15-, 16-, 17- and 17.1-UI-SPEC (shared tokens: 4 sizes, weights 400/600, `blue-600` accent, amber warning, `gray-50` canvas)
**Screenshots:** none. This is a code-only audit; the live app at :8080 was not signed into.
**Purpose:** the before-measure for Phase 23, and the page checklist for Phase 23.1. The name `23-UI-REVIEW.md` is kept for the post-implementation review.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | Copy is specific and contract-driven. Issues: the old product name, and "Stale" vs "Documents out of date" for the same state. |
| 2. Visuals | 2/4 | Page title, section heading and dialog title are all 16/600. There are no icons, no logo and 3 different page-header patterns. The dashboard matrix has 13 columns with no focal point. |
| 3. Color | 2/4 | 1,507 raw colour classes, zero tokens, zero `dark:` variants. Two status colours fail AA contrast, and there are 3 amber and 2 green variants for the same meaning. |
| 4. Typography | 2/4 | The size scale is mostly held. But 42 `font-medium` break the 400/600 contract, and there is no page-title size, so the hierarchy is flat. |
| 5. Spacing | 3/4 | Spacing is on the 4px grid. Page containers, header strips and edge insets differ between pages, and buttons come in 9 padding variants. |
| 6. Experience Design | 3/4 | Loading, error and empty states and destructive confirmations are covered well. But 24 of 30 spinners ignore reduced motion, focus rings are missing in 37 of 40 files with controls, signed-out users see the nav, and there are no toasts or skeletons. |

**Overall: 15/24**

---

## Top 5 Priority Fixes

1. **No design tokens, no dark mode (Color / D-23-05, D-23-06):** 1,507 raw colour classes and 0 `dark:` variants (`index.css` is 2 lines). Dark mode is impossible without touching every file.
   - Fix: define the semantic tokens in "Colour Inventory → proposed tokens" (below) in `@theme`.
   - Migrate the shell first, then pages in D-23-12 order.
   - Map 1:1: `text-gray-900`→`text-foreground`, `text-gray-600/500`→`text-muted-foreground`, `bg-white`→`bg-card`, `bg-gray-50`→`bg-background`/`bg-muted`, `border-gray-200/300`→`border-border`/`border-input`, `text-red-600`→`text-destructive`, `blue-600`→`primary`.
2. **No visual hierarchy, which is why it "looks plain/dated" (Visuals / Typography / D-23-13):**
   - Every page title is `text-base font-semibold`, the same as section headings and dialog titles (`DashboardRoute.tsx:542`, `ReleasesRoute.tsx:32`, `ReleaseDetailRoute.tsx:290`, `ConfirmDialog.tsx:33`).
   - The shell `<h1>` is plain text "Release Management" (`AppShell.tsx:22`), with no mark, no icons anywhere and no elevation (4 `shadow` uses in the whole app).
   - Fix: add a page-title step (`text-xl`/`text-2xl` semibold) in a shared `PageHeader` (title, description, actions slot), and keep 16/600 for sections. Add lucide icons to nav, buttons and status badges. Card surfaces get a `shadow-xs` + `rounded-lg` token.
3. **Spinners ignore reduced motion (Experience, breaks the existing contract):**
   - Every UI-SPEC since 15 requires `motion-reduce:animate-none`. 24 of 30 spinners lack it: `DashboardRoute.tsx:65`, `ExportButtons.tsx:6` (the shared `Spinner`), `PortfolioStates.tsx:8` (the shared `SectionLoading`), `ReleaseDetailRoute.tsx:183,362,379`, `ReleasesRoute.tsx:49`, `TemplatesRoute.tsx:58`, `DeploymentHistory.tsx:21`, `TicketBrowser.tsx:260`, and 14 more.
   - There are also 5 separate Spinner definitions.
   - Fix: one `Spinner` (shadcn `spinner` / lucide `Loader2`) with `motion-reduce:animate-none`. Use Skeleton for first loads (D-23-07).
4. **Inconsistent and failing status colours (Color / dark mode):**
   - `ReadinessPill` "Stale" uses `text-amber-600` on `amber-50` (`ReadinessPill.tsx:33`), about 3:1, which fails AA at 12px. Every other amber marker uses `amber-700`.
   - Success text `text-green-600` (`SettingsRoute.tsx` "Token replaced." / "Azure DevOps settings saved.", ADO badge `OK`) is about 3.3:1 and also fails.
   - "Up to date" has a `border-green-700` border while briefings "All clear" has `border-green-600`.
   - Fix: one `Badge` with `success|warning|destructive|neutral|info` variants backed by tokens. ReadinessPill, StatusPill, CodeLinksChip, the Frozen/Open/Active pills and the amber markers all use it.
5. **The shell shows the full nav to signed-out users, and "Go to Settings" on login is a dead end (Experience):**
   - `AppShell` renders the nav outside the `session.data &&` guard (`AppShell.tsx:37-103`). The login page (rendered inside the shell, `router.tsx:23-31`) shows Dashboard…Settings links that bounce straight back to /login.
   - When the site is unconfigured, login shows "Go to Settings" (`LoginRoute.tsx:45`). But `/settings` is under `authenticatedRoute`, and `PUT /api/settings/jira-site` requires auth (`SecurityConfig.java:43-46`), so the link loops back to login.
   - Fix: render login outside the shell (a dedicated branded layout, D-23-01). Hide the nav without a session. Replace the link with operator guidance (where the site is set: env/compose) — verify how a fresh install gets its site.

Further WARNINGs, ranked, are in the per-page checklist below. More than 6 issues exist on every high-priority page.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)

Strengths:
- Copy follows the UI-SPEC copy tables almost verbatim.
- Errors go through `messageForReason` with `data-reason`.
- Empty states say what to do next, e.g. `ReleasesRoute.tsx:185-192`, `RoadmapRoute.tsx:185-188`.
- No "Submit/OK/Click here" CTAs; labels are verb + object ("Freeze release", "Record install", "Save Azure DevOps token").

Issues:
- **WARNING: the product name is still "Release Management".**
  - `frontend/index.html` `<title>Release Management</title>` and `AppShell.tsx:22`.
  - D-23-01 requires "Shipflow · your product roadmap helper".
- **WARNING: the same state has two names.**
  - The readiness pill says **"Stale"** (`ReadinessPill.tsx:39`).
  - The dashboard tile, its help text and the filter chip say **"Documents out of date"** (`DashboardRoute.tsx:43`), and the Documents section body says "The release has changed since this was generated" (`DocumentsSection.tsx:139`).
  - 16-UI-SPEC renamed the tile, but the pill was never renamed. Pick "Out of date" everywhere.
- **WARNING: "Portfolio" names two different things.** It is the `/portfolio` register page (products/clients CRUD, `PortfolioRoute.tsx:119`) and a Dashboard tab (read-only analytics, `DashboardRoute.tsx:31`). The palette (D-23-09) needs distinct names, e.g. "Products & clients" for the register.
- Minor: "Clear products" (`DashboardRoute.tsx:341`), where 16-UI-SPEC says **"Clear"**.
- Minor: the ADO credential badge says **"OK"** (`SettingsRoute.tsx:166`). Use "Valid".
- Minor: the run-history column header **"Run (Cairo)"** hard-codes a timezone (`BriefingsRoute.tsx:217`, `briefingFormat.ts` `Africa/Cairo`).
- Minor: Settings loading is a bare **"Loading…"** with no subject or spinner (`SettingsRoute.tsx:58`). The Jira-site load error has no `role="alert"` (`SettingsRoute.tsx:61`).
- Minor: "Something went wrong" is the non-ApiError fallback in 9 places (`AssistantTurn.tsx:35`, `AiResultCard.tsx:67,100`, `PortfolioRegisterPanel.tsx:74`, `ProductJiraProjectsEditor.tsx:94`, `PortfolioStates.tsx:21`, `DashboardRoute.tsx:601`, `BriefingSettingsSection.tsx:334`). This is acceptable as a fallback, but should be one shared string.
- Minor: dense always-visible footnotes. The dashboard matrix carries two paragraphs of methodology under the table (`DashboardRoute.tsx:475-484`), and 5 multi-sentence `title=` tooltips on cells (`DashboardRoute.tsx:35-56`). Move them behind an info Tooltip/Popover.

### Pillar 2: Visuals (2/4)

- **BLOCKER (for D-23-13): no focal point or hierarchy.**
  - Page title, section, empty-state and dialog headings all render at 16/600 (60 headings use `text-base font-semibold`, see the h-tag grep).
  - The dashboard opens with a 16px "Dashboard" heading, then a filter button, tabs, five identical tiles and a 13-column table (`DashboardRoute.tsx:396`). Nothing leads the eye.
  - The "Releases per stage" tile is a `text-xs` list, not a figure (`DashboardRoute.tsx:137-150`). It breaks the tile pattern, and it is the obvious chart candidate (D-23-08).
- **WARNING: three page-header patterns.**
  1. Heading on the canvas inside `space-y-8`: Dashboard, Roadmap, Briefings, Tickets.
  2. Borderless white strip `bg-white p-6` that bleeds against the canvas: Releases `:31`, Release detail `:288`, Templates `:51`, Template detail `:261`, Portfolio `:118`, Document viewer `:121`.
  3. A narrow form column: Settings `max-w-xl` then `max-w-3xl` (`SettingsRoute.tsx:55,158`), Assistant `max-w-3xl` centred with a `text-xl` title (`AssistantRoute.tsx:79-81`).
- **WARNING: heading semantics clash.** The Portfolio page uses `<h1>` (`PortfolioRoute.tsx:119`) while the shell already has an `<h1>` (`AppShell.tsx:22`). Every other page uses `<h2>`.
- **WARNING: no icons.** Controls use glyphs only (`?`, `×`, `▸/▾`, `←`, `+`): the expand toggle `ReleaseDetailRoute.tsx:728`, dismiss `:547,572`, drawer close `ReleaseAssistantDrawer.tsx:71`, `PlaceholderDrawer.tsx:132`, the help toggle `DashboardTiles.tsx:13`. Each has an aria-label, which is good, but they read as dated. Use lucide.
- **WARNING: release detail action row.** Up to 6 controls of equal weight sit in one row, and two are filled primary buttons ("Add tickets" `:347` and "Freeze release" `:389`). 15/17-UI-SPEC say one primary per panel.
- **WARNING: status pills differ in shape and size.**
  - Frozen/Open/Active use `rounded-full text-sm` (`ReleaseDetailRoute.tsx:293`, `ReleasesRoute.tsx:157`, `TemplatesRoute.tsx:116`, `TemplateDetailRoute.tsx:310`).
  - Every other chip uses `rounded text-xs font-semibold`.
- **WARNING: depth.** Everything is a 1px `gray-200` border with a 4px radius. Dialogs have no styled `::backdrop` (the UA default is near-transparent, `ConfirmDialog.tsx:28-32`).
- Good: the matrix code-link bar has `role="img"` + aria-label (`DashboardRoute.tsx:186`). LadderDots carry `aria-current="step"` and sr-only labels.

### Pillar 3: Color (2/4)

1,507 colour-class uses, 0 CSS variables, 0 `dark:`. There are no hex/rgb literals and only 4 inline styles (the code-links bar widths, `DashboardRoute.tsx:187-190`; logo height, `RenderedDocument.tsx:109`).

The 60/30/10 split roughly holds:
- **60%:** `gray-50` canvas (44) and `bg-white` surfaces (71).
- **30%:** gray borders and text (about 900).
- **10%:** `blue-600`: `bg-blue-600` ×37 (filled buttons), `text-blue-600` ×56 (links), `ring-blue-600` ×50 (focus).

The accent is mostly disciplined. It goes outside the reserved list in two places:
- The release-detail double primary (`ReleaseDetailRoute.tsx:347,389`).
- "Rename" as a blue text link (`TemplateDetailRoute.tsx:285`) where Portfolio uses a gray `SMALL_BUTTON` for the same verb.

Defects:
- **WARNING (contrast):** `text-amber-600` on `bg-amber-50` for "Stale" (`ReadinessPill.tsx:39`). `text-green-600` for success text (`SettingsRoute.tsx` token/ADO saved, `ADO_STATE_BADGE.OK`). The briefing spec's own rule is 700-on-50.
- **WARNING (drift): three warning palettes.**
  - Spec: `amber-700`/`amber-50`/`amber-600`.
  - The shell banners use `amber-900`/`amber-300` (`CredentialAlert.tsx:9`, `AdoCredentialBanner.tsx:16`).
  - Template detail uses `amber-900`/`amber-200` (`TemplateDetailRoute.tsx:360`).
  - CodeLinks uses `amber-800` (`CodeLinks.tsx:72,112`), and the freeze dialog also uses `amber-800` (`ReleaseDetailRoute.tsx:471`).
- **WARNING (drift): an off-spec info palette.** `blue-50`/`blue-200`/`blue-900` banners (`ReleaseDetailRoute.tsx:538,555`, `TemplateDetailRoute.tsx:351`, `TemplatePreview.tsx:222`) are not in any spec. They need an `info` token, or should become toasts.
- **WARNING:** the frozen pill `bg-gray-800 text-white` is the only dark fill in the app, and it inverts badly in dark mode.
- Hover on sticky columns: rows use `hover:bg-gray-50` but sticky cells are `bg-white` (`DashboardRoute.tsx:414-415`, `RoadmapTable.tsx:97-164`, `PortfolioMatrix.tsx:110-123`), so the hover band breaks at the sticky column.

### Pillar 4: Typography (2/4)

**Sizes:** `text-sm` 533 · `text-xs` 210 · `text-base` 64 · `text-lg` 5 · `text-xl` 1 · `text-2xl` 1. That is within the 4-size contract, except:
- `text-xl` on the Assistant title (`AssistantRoute.tsx:81`), the only page with a larger title.
- `text-2xl` on the document cover (`RenderedDocument.tsx:112`). This is acceptable because it is document content, not UI.

**Weights:** `font-semibold` 284 · **`font-medium` 42** · `font-normal` 5 · `font-mono` 10. The contract is 400/600 only. `font-medium` is concentrated in:
- `SettingsRoute.tsx` (labels and all 3 primaries, `:104,139,266`)
- `LoginRoute.tsx:54,69,97`
- `BriefingSettingsSection.tsx:26,29,594`
- `EnvironmentLadderSection.tsx:137,153,170`
- the dialog primaries (`RecordInstallDialog.tsx:313`, `RecordPromotionDialog.tsx:283`, `AddFromReleaseDialog.tsx:191`, `AddManualItemDialog.tsx:119`, `PromotionSection.tsx:48`, `ClientInstallsSection.tsx:177`)

So a primary button is 500 weight on Settings and in dialogs but 600 on release detail and documents. Form labels are 500 on Settings and Login and 600 on release detail (`ReleaseDetailRoute.tsx:863`).

Other findings:
- **WARNING:** there is no page-title role. Pages, sections, card titles, dialog titles and empty-state titles all use 16/600. The empty-state title in Briefings is `text-sm` (`BriefingsRoute.tsx:194,201`) but `text-base` everywhere else.
- **WARNING:** the release status pill is `text-sm` while all other chips are `text-xs` (see Visuals).
- Good: `tabular-nums` is applied consistently to counts and durations.

Proposed scale for Phase 23:
- 12 for meta
- 14 for body
- 16/600 for section headings
- 20/600 for the page title
- 24–30/600 for the tile figure (dashboard KPI)
- 400/500/600 weights, with 500 reserved for buttons and labels through the shadcn defaults (this amends the 400/600 rule deliberately)

### Pillar 5: Spacing (3/4)

The distribution is healthy and on the 4px grid: `py-2` 223, `px-4` 163, `mt-2` 137, `px-3` 89, `gap-2` 81, `mt-4` 77, `p-4` 50, `p-6` 39. There are no 2.5/3.5/5/7 half-steps. Arbitrary values are spec-declared fixed widths (`w-[32rem]`, `min-w-[12rem]`, `w-[min(90vw,72rem)]`, `max-h-[70vh]`), except:
- `w-[380px]` on both drawers (`ReleaseAssistantDrawer.tsx:54`, `PlaceholderDrawer.tsx:125`)
- `h-[calc(100vh-260px)]` on the template editor (`TemplateDetailRoute.tsx:423`), a magic number tied to header height that breaks when banners show

Issues:
- **WARNING: no shared page container.** `main` is `p-6` full-bleed (`AppShell.tsx:107`). Pages choose their own width: full, `max-w-xl`, `max-w-3xl` or `max-w-sm`.
  - Settings stacks two widths on one page (`SettingsRoute.tsx:55` vs `:158`), so the Briefings section is wider than the sections above it.
  - Page-level edge insets of `mx-6` are hand-applied in Document viewer (`:171,190,204,228`), Template detail (`:343-393`) and AiAssistPanel (`:71-214`), because those pages sit flush in the white strip.
- **WARNING: button padding has 9 variants.**
  - `px-4 py-2` (42)
  - `px-3 py-1.5` (21)
  - `px-2 py-1` (10)
  - `px-3 py-1` (2)
  - `px-2 py-2` (1, `DocumentsSection.tsx` select)
  - plus bordered buttons at `px-4 py-2` (`ReleaseDetailRoute.tsx:355,372`) that the spec says are `px-3 py-1.5`
  - The same bordered button is 36px tall in one place and 32px in another.
- Minor: the dashboard puts `space-y-8` (32px) between heading, filter and tab bar (`DashboardRoute.tsx:541`). 16-UI-SPEC says filter + tabs share `space-y-4`.
- Minor: release-detail metadata uses `space-x-4` inline spans (`ReleaseDetailRoute.tsx:321`), which do not wrap cleanly. Use `flex flex-wrap gap-x-4`.

### Pillar 6: Experience Design (3/4)

Strengths:
- Every query surface has loading, error (`role="alert"`, 75 instances, with Try again) and empty states.
- Destructive actions use `ConfirmDialog` with named confirm labels: delete release, delete template, delete stage, void install, archive.
- Blocked actions use `aria-disabled` + `aria-describedby`.
- State lives in the URL; ARIA tabs with roving tabindex are used on Dashboard and Portfolio.
- 21 optimistic or cache writes (`onMutate`/`setQueryData`).
- Polite live regions announce state changes.

Defects:
- **WARNING:** 24 of 30 spinners lack `motion-reduce:animate-none` (see fix 3).
- **WARNING: focus rings.** 37 of 40 files with interactive controls have no `focus-visible` ring on most controls. The UA outline still shows, so this is not inaccessible, but it is inconsistent with the specs.
  - The shared `SMALL_BUTTON` (`EnvironmentLadderSection.tsx:17`), `ConfirmDialog` buttons, `ReadinessPill` links, all nav links and all dialog fields lack it.
  - **The template body textarea removes the outline with no replacement** (`TemplateDetailRoute.tsx:438`, `focus:outline-none`). Keyboard users lose focus on the main editing surface.
- **WARNING:** signed-out nav and the login dead end (see fix 5).
- **WARNING:** removing a manual item deletes typed text with no confirm and no undo (`ReleaseDetailRoute.tsx:827-835`). Removing a ticket is recoverable but also has no undo (`:733-741`). This is the first candidate for the "toast with Undo" pattern (D-23-07).
- **WARNING: success feedback differs in each place.**
  - Inline green text: `SettingsRoute.tsx` and `BriefingSettingsSection.tsx:530,598`.
  - Dismissible blue banners: `ReleaseDetailRoute.tsx:536-575`.
  - A notice box with Dismiss: `RoadmapRoute.tsx` notice region.
  - sr-only only: `PackagesLink`, `RecordInstallDialog`, `AiAssistPanel:233`.
  - D-23-07 replaces all of these with toasts. Errors stay inline (`role="alert"`) because several flows depend on the error showing beside the control.
- **WARNING:** the ARIA misuse hides data from screen-reader users. `aria-label={CHURN_TITLE}` / `{REWORK_TITLE}` on `<td>` (`DashboardRoute.tsx:452,455`), and `aria-label={TOOLTIP}` on a `<span>` in `ItemChurn.tsx:9,31`, replace the visible figures with the explanation, or are ignored on a generic element. Use `aria-describedby`.
- **WARNING:** the Template detail Body / "Chrome & Logo" switcher looks like tabs but has no `role="tab"` (`TemplateDetailRoute.tsx:399-420`). Dashboard and Portfolio duplicate the tablist markup and keyboard handler (`DashboardRoute.tsx:532-573`, `PortfolioRoute.tsx:103-147`).
- **WARNING:** the nav is 9 links in a `flex gap-4` with no wrap or overflow (`AppShell.tsx:37`), so it overflows below about 900px. There is no current-section indicator beyond bold text.
- Minor: the Document viewer shows a ReadinessPill that links to the page you are already on (`DocumentViewerRoute.tsx:134`).
- Minor: there is no "New release" entry point on `/releases`. Creation only exists in the Tickets flow (explained in the empty state, `ReleasesRoute.tsx:187`). Expose it as a palette action (D-23-09).
- Minor: the manual-item form has no Cancel button; it closes only via the "+ Add manual item" toggle (`ReleaseDetailRoute.tsx:852-918`).

Registry audit: not applicable. There is no `components.json` yet, so shadcn is not initialised.

---

## Per-Page Checklist for Phase 23.1 (D-23-12 order)

Legend: [B] blocker for the page's migration, [W] warning, [K] keep (an interaction rule from an existing UI-SPEC that must survive the migration).

### Priority 1a: Dashboard `/` (`routes/DashboardRoute.tsx`, `DashboardTiles.tsx`, `DeploymentHistory.tsx`, `ReadinessPill.tsx`, `LadderDots.tsx`, `ProductFilter.tsx`)
- [ ] [B] Page title via `PageHeader`. Replace the 16px `<h2>` (`:542`) and give KPI tiles a real figure size (Card + 24–30px figure).
- [ ] [B] "Releases per stage" tile (`:137-150`) becomes a bar chart (D-23-08). The two median tiles get a sparkline/trend only if data exists. No new data (scope).
- [ ] [W] The 13-column release matrix (`:386-472`) becomes a DataTable with column visibility. Default visible: Release, the 3 doc pills, Stage, the two durations. Composition, Story points, Code links, Churn and Rework go behind "Columns".
- [ ] [W] Move the 5 long `title=` explanations and the 2 footnote paragraphs (`:35-56`, `:475-484`) into header info Tooltips/Popovers.
- [ ] [W] Fix `aria-label` on the churn/rework `<td>` (`:452,455`) by switching to `aria-describedby`.
- [ ] [W] Spinner reduced motion (`:62-69`). Use a Skeleton for the tiles and table on first load.
- [ ] [W] Sticky first column: its hover background must match the row (`:415`), and it must become token-backed for dark mode.
- [ ] [W] Code-links mini-bar colours (`:187-190`: `blue-600`/`gray-300`/`red-600`/`gray-100`) go to chart tokens.
- [ ] [W] Spacing: filter + tab bar share `space-y-4` (spec), not `space-y-8` (`:541`).
- [ ] [W] "Last promotion failed" pill (`:254`) becomes Badge destructive.
- [ ] [K] Tabs keep the `tab` URL param, `replace: true`, Left/Right roving focus, and switching clears `docs` but keeps `products`.
- [ ] [K] FilterTile `aria-pressed`. The "?" help toggle is a sibling of the tile button, inline text not hover-only (16-SPEC assumption 1). `aria-disabled` + note when readiness fails. Clearing the filter returns focus to the tile.
- [ ] [K] ProductFilter: Escape and outside-click close and return focus to the trigger; changes apply immediately; hidden when there are no products; `max-w-[calc(100vw-2rem)]`. The shadcn Popover + Command keeps this behaviour if configured.
- [ ] [K] A readiness failure blanks only the doc cells and the two doc tiles (T-05-60).

### Priority 1b: Portfolio tab on the dashboard (`PortfolioTab.tsx`, `PortfolioBehind.tsx`, `PortfolioMatrix.tsx`, `PortfolioStates.tsx`) and the `/portfolio` register (`PortfolioRoute.tsx`, `PortfolioRegisterPanel.tsx`, `ClientDetailPanel.tsx`, `ProductJiraProjectsEditor.tsx`)
- [ ] [B] Rename one of the two "Portfolio" surfaces (Copywriting) before the palette indexes them.
- [ ] [W] Use `<h1>` in `PortfolioRoute.tsx:119` only after the shell drops its own `<h1>`, or change it to `<h2>` (pick one convention app-wide).
- [ ] [W] Portfolio tiles (`PortfolioTab.tsx:12` `TILE_BUTTON`) become the same Card as the dashboard tiles. "Installs last 30 days" is the chart candidate.
- [ ] [W] Products / behind / matrix tables become DataTable (sortable). The matrix sticky column (`PortfolioMatrix.tsx:110,123`) needs a token background.
- [ ] [W] `ARCHIVED_TAG` ×3 and `AMBER_MARKER` ×2 duplicates (`PortfolioBehind.tsx:7-8`, `PortfolioMatrix.tsx:6-7`, `PortfolioTab.tsx:11`) become a Badge variant.
- [ ] [W] SectionLoading spinner reduced motion (`PortfolioStates.tsx:8`); use Skeleton rows.
- [ ] [W] Register panels: `BORDERED_BUTTON`/`CONFIRM_BLUE` (`PortfolioRegisterPanel.tsx:16-17`, `ClientDetailPanel.tsx:12-13`) become Button. Add focus rings.
- [ ] [K] Behind/Not-installed tiles focus and scroll to `#behind`/`#not-installed` headings (`tabIndex={-1}`), with no smooth scroll under reduced motion.
- [ ] [K] Archive uses a blue (non-destructive) confirm; Void is the only red confirm, with a required reason; Unlink is `aria-disabled` when installs exist (15-SPEC).
- [ ] [K] Colour is never the only signal ("archived", "Not installed", "N releases behind" are words).

### Priority 2: Release detail `/releases/$id` (`ReleaseDetailRoute.tsx`, `ProductsEditor`, `PackagesLink`, `PromotionSection`, `RecordPromotionDialog`, `ClientInstallsSection`, `RecordInstallDialog`, `DocumentsSection`, `ExportButtons`, `CodeLinks`, `ItemChurn`, `ReleaseAssistantDrawer`)
- [ ] [B] Information architecture. The page is one long scroll: header → tickets → manual items → promotion → installs → documents (`:921-923`). Documents (the core value) come last. Add section tabs or an in-page section nav (Overview / Tickets / Promotion / Installs / Documents) with hash deep links (`#documents`, `#history`, `#ticket-KEY` are already linked from other pages).
- [ ] [B] Action hierarchy (`:341-401`). One primary (Add tickets, or Freeze when ready). Refresh from Jira and Refresh code links go to a split-button or DropdownMenu. Delete moves to an overflow "…" menu, destructive styled.
- [ ] [W] Status pill Frozen/Open (`:293,295`) becomes Badge (`text-xs`); `bg-gray-800` breaks in dark mode.
- [ ] [W] Remove manual item (`:827`) and Remove ticket (`:733`) get a toast with Undo, or a confirm for the manual item.
- [ ] [W] Result banners (`:536-575`, blue palette) become toasts. Keep the `added/alreadyPresent/notFound` query-param copy (D-03-13: a duplicate is success, not a warning).
- [ ] [W] Tickets table (`:629-784`) becomes DataTable with row selection, keeping the expandable detail row. Replace the ▸/▾ glyph with a chevron icon, and replace the 3 inline spinners (`:183,362,379`) with reduced-motion Spinners.
- [ ] [W] Manual-item form (`:861-918`): Input/Textarea/Select/Label, a Cancel button, focus rings; consider a Dialog instead of an inline form.
- [ ] [W] Metadata line `space-x-4` (`:321`) becomes a wrapped definition list.
- [ ] [W] DocumentsSection: the generate button switches between blue and bordered by state (`DocumentsSection.tsx:178-184`). Keep the rule but use Button variants. Rename "Stale" to "Out of date".
- [ ] [W] The assistant drawer (`ReleaseAssistantDrawer.tsx:54`) becomes Sheet (non-modal, or modal with focus return) and drops `w-[380px]` for a token width.
- [ ] [K] Freeze/Unfreeze/Delete confirms close `onSettled`, not `onSuccess`, because `showModal()` makes the page inert and an error must stay readable. Radix AlertDialog must keep this.
- [ ] [K] Unfreeze blocked: `aria-disabled` + `aria-describedby="unfreeze-blocked-reason"`, stays in tab order (15.1-SPEC).
- [ ] [K] After unfreeze, focus moves to "Add tickets" and is announced; the post-unfreeze stale-docs notice stays.
- [ ] [K] Row click toggles detail except on controls and text selection (`:683-687`); the hash `#ticket-KEY` is compared to loaded keys only, never used as a selector.
- [ ] [K] Clearing the packages link is a blue confirm "Clear link" (17-SPEC); the Void install confirm is red with a reason.

### Priority 2b: Document viewer `/releases/$id/documents/$type` (`DocumentViewerRoute.tsx`, `RenderedDocument.tsx`, `AiAssistPanel.tsx`, `AiResultCard.tsx`)
- [ ] [W] Header strip with "← Back to release" text link (`:123-130`) becomes PageHeader + Breadcrumb.
- [ ] [W] Notices at `mx-6` (`:171-228`) become Alert components inside the page container.
- [ ] [W] Revision `<select>` (`:139`) becomes Select. The self-linking ReadinessPill (`:134`) becomes a non-link Badge.
- [ ] [K] Revision select is disabled while an AI result is open, with an explanation (`:142-143`).

### Priority 3a: Roadmap `/roadmap` (`RoadmapRoute.tsx`, `roadmap/*`)
- [ ] [B] `RoadmapTable` becomes the first full DataTable (D-23-10): sticky Rank + Item columns, sort, column choice. Its in-cell editing must survive.
- [ ] [W] Toolbar: 3 add buttons + Refresh + Export CSV + inline help `<small>`s (`:210-252`) become one "Add" DropdownMenu (Jira / release / manual), Refresh, and Export as a secondary/ghost Button.
- [ ] [W] The notice region with a Dismiss button (`:279-302`) becomes a toast. Keep it persistent for partial-failure results (RoadmapAddResult) so it is not lost.
- [ ] [W] RubricPanel `<details open>` (`RubricPanel.tsx:12`) becomes Collapsible.
- [ ] [W] Three roadmap dialogs (`AddFromJiraDialog.tsx:45`, `AddFromReleaseDialog.tsx:131`, `AddManualItemDialog.tsx:59`) become Dialog. Their primaries use `font-medium`.
- [ ] [K] InputCell: one input = one save; commit on blur/Enter; Escape reverts; an unchanged value sends nothing (17-SPEC). Archive is reversible and unconfirmed. Score is never colour-coded.

### Priority 3b: Briefings `/briefings` (`BriefingsRoute.tsx`, `briefings/*`)
- [ ] [W] Cards (`:166`) become Card; StatusPill (`StatusPill.tsx`, `briefingFormat.ts:12-23`) becomes Badge variants. This is the canonical status-to-colour map to seed tokens.
- [ ] [W] Empty-state title is `text-sm` (`:194,201`) while it is `text-base` elsewhere. Use the shared Empty component.
- [ ] [W] Run-history table: header has no `border-b` (`:214`, unlike every other table). Make it a DataTable. Make the "Run (Cairo)" header timezone label come from config.
- [ ] [K] `data-status` carries the enum; the label word is always present; "Show more" pages by `PAGE_SIZE`.

### Priority 3c: Settings `/settings` (`SettingsRoute.tsx`, `EnvironmentLadderSection.tsx`, `BriefingSettingsSection.tsx`)
- [ ] [B] Layout: 5 stacked sections at two widths (`:55`, `:158`) with 4 filled Save buttons. Use a settings sub-nav (vertical Tabs: Jira, Azure DevOps, Environment ladder, Briefings) inside one container width. Theme selection could also live here, in addition to the header toggle (D-23-06).
- [ ] [W] Success text `text-green-600` (fails AA) becomes a toast. The ADO "OK" badge becomes Badge success "Valid".
- [ ] [W] `font-medium` labels and primaries (`:73-266`, `BriefingSettingsSection.tsx:26,29,594`) become Label/Button.
- [ ] [W] The hand-rolled switch (`BriefingSettingsSection.tsx:~281-296`, `role="switch"`) becomes Switch.
- [ ] [W] Jira-site load error lacks `role="alert"` (`:61`); "Loading…" (`:58`) has no spinner or subject.
- [ ] [W] `SMALL_BUTTON` (exported from `EnvironmentLadderSection.tsx:17` and imported app-wide) becomes Button `size="sm" variant="outline"`, with a focus ring.
- [ ] [K] Ladder rename: Enter saves, Escape cancels; delete stage confirm is red; the ADO token field is "Leave blank to change only the expiry date".

### Priority 4a: Templates `/templates` and Template detail `/templates/$id` (`TemplatesRoute.tsx`, `TemplateDetailRoute.tsx`, `TemplatePreview.tsx`, `PlaceholderDrawer.tsx`, `ChromeFieldsForm.tsx`, `LogoUpload.tsx`, `NewTemplateDialog.tsx`)
- [ ] [B] The template body textarea has `focus:outline-none` with no replacement (`TemplateDetailRoute.tsx:438`). Restore a visible focus style.
- [ ] [W] The Body / "Chrome & Logo" switcher (`:399-420`) becomes Tabs (ARIA).
- [ ] [W] Editor height `h-[calc(100vh-260px)]` (`:423`) becomes a flex layout filling the remaining viewport.
- [ ] [W] Active/Default pills (`TemplatesRoute.tsx:116-126`, `TemplateDetailRoute.tsx:310,326`) become Badge; the amber dot `bg-amber-600` (`TemplatesRoute.tsx:126`) becomes a token.
- [ ] [W] "Rename" as a blue text link (`TemplateDetailRoute.tsx:285`) becomes Button ghost/sm, consistent with Portfolio.
- [ ] [W] PlaceholderDrawer (`:125`) becomes Sheet; its close "×" button has no focus ring (`:132`).
- [ ] [K] `beforeunload` guard on unsaved edits (`:117`); "Preview approximation" disclaimer; unknown-placeholder highlighting `[[unknown: x]]`.
- [ ] [K] Do not conflate `LogoUpload` (the document-template logo) with the app logo (23-CONTEXT).

### Priority 4b: The rest
- **Shell `AppShell.tsx`**
  - [ ] [B] Brand mark + wordmark (D-23-02) replaces the text h1 (`:22`).
  - [ ] [B] Hide the nav without a session; move login out of the shell.
  - [ ] [W] Nav overflow (`:37`): a sidebar, or a wrapped/compact nav with icons.
  - [ ] [W] The Sign out button (`:26-33`) moves into a user DropdownMenu with the theme toggle.
  - [ ] [W] The credential banners (`CredentialAlert.tsx:9`, `AdoCredentialBanner.tsx:16`, amber-900/300) become Alert warning tokens.
- **Login `LoginRoute.tsx`**
  - [ ] [B] The "Go to Settings" dead end (`:45`).
  - [ ] [W] Branded layout: logo, name, tagline (D-23-01); Card; Input/Label/Button; focus rings (`:57-98`). The "Create one on Atlassian's site" link should be styled as a link (`:89`, currently `underline` only, no colour).
- **Releases list `ReleasesRoute.tsx`**
  - [ ] [W] DataTable with status/product filters and sort.
  - [ ] [W] Frozen/Open pill becomes Badge (`:157-159`).
  - [ ] [W] Spinner (`:49`).
  - [ ] [W] Expose "New release" via the palette.
- **Tickets `TicketsRoute.tsx` + `TicketBrowser.tsx` + `AddToReleaseDialog.tsx`**
  - [ ] [W] The ticket table becomes DataTable (row selection).
  - [ ] [W] The two spinners in AddToReleaseDialog (`:178,241`) and TicketBrowser (`:260`).
  - [ ] [W] The dialog's "New release…" toggle becomes Collapsible or a second Dialog step.
- **Assistant `AssistantRoute.tsx` + `assistant/*`**
  - [ ] [W] The `text-xl` title (`:81`) is the only one in the app; align it with PageHeader.
  - [ ] [W] The spinner uses `border-t-gray-600` (`:23`), unlike all others.
  - [ ] [W] Chat bubbles `ml-12`/`mr-12` (`AssistantTurn.tsx:180,184`) become tokenised message bubbles.
  - [ ] [K] Citations, the freshness line, and Stop/New chat semantics stay.

---

## Hand-rolled UI Primitive Inventory → shadcn/ui

| Primitive | Where (file:line) | Count | shadcn replacement | Care points |
|---|---|---|---|---|
| Confirm dialog | `components/ConfirmDialog.tsx:28` (used by ReleaseDetail ×3, Templates, TemplateDetail:514, EnvironmentLadder:165, PortfolioRegisterPanel:266, ClientDetailPanel:243, ClientInstallsSection:212, PackagesLink) | 1 shared | **AlertDialog** | Keep: Escape/backdrop = Cancel; close `onSettled`; `confirmDisabled` (Void reason); children slot; blue vs red confirm by reversibility |
| Modal dialogs (native `<dialog>` + `showModal()`) | `AddTicketsToReleaseDialog.tsx:35`, `AddToReleaseDialog.tsx:166`, `NewTemplateDialog.tsx:44`, `RecordInstallDialog.tsx:127`, `RecordPromotionDialog.tsx:159`, `roadmap/AddFromJiraDialog.tsx:45`, `roadmap/AddFromReleaseDialog.tsx:131`, `roadmap/AddManualItemDialog.tsx:59` | 8 | **Dialog** | Keep the mount-only-while-open pattern (TicketBrowser inside), focus return to the opener, wide-dialog sizes (`w-[min(90vw,72rem)]`) |
| Side drawers | `assistant/ReleaseAssistantDrawer.tsx:54`, `components/PlaceholderDrawer.tsx:125` | 2 | **Sheet** (+ ScrollArea) | Both are non-modal `role="region"` today, so the page stays usable. Choose `modal={false}` or accept modal. Mobile bottom-sheet behaviour (`max-lg:` classes) |
| Popover multi-select | `components/ProductFilter.tsx:44` (`<details>`) | 1 (used on Dashboard, Roadmap, Briefings) | **Popover + Command** (checkbox list) | Escape/outside close with focus return; immediate apply; "Clear"; archived-selected items listed |
| Disclosure / collapsible | `DashboardTiles.tsx:13` HelpToggle; `roadmap/RubricPanel.tsx:12`; `briefings/AppFallbackSection.tsx:76`; `assistant/AssistantTurn.tsx:92`; `DocumentViewerRoute.tsx:197`; expandable rows `ReleaseDetailRoute.tsx:745`, `roadmap/ItemDetailRow.tsx` | 7 | **Collapsible** (HelpToggle may become **Popover**, but 16-SPEC requires inline/click, not hover) | `aria-expanded`/`aria-controls` already correct |
| Tabs | `DashboardRoute.tsx:549-573`, `PortfolioRoute.tsx:124-147` (ARIA, duplicated), `TemplateDetailRoute.tsx:399-420` (no ARIA) | 3 | **Tabs** | URL-driven value (`tab` param, `replace: true`) |
| Tooltips | 55 `title=` attributes; long ones in `DashboardRoute.tsx:35-56`, `ItemChurn.tsx:3`, `RoadmapRoute.tsx:29`, `LadderDots.tsx:145` (compact dots), `DocumentViewerRoute.tsx:17` | ~55 | **Tooltip** (short), **HoverCard/Popover** (long explanations) | Keep a text equivalent for touch and keyboard; do not move essential info into hover-only |
| Badges / pills / chips | `ReadinessPill.tsx:23-56`; `briefings/StatusPill.tsx` + `briefingFormat.ts:12-23`; `ProductsEditor.tsx:9` PRODUCT_CHIP; `ARCHIVED_TAG` (`PortfolioBehind.tsx:7`, `PortfolioMatrix.tsx:6`, `PortfolioTab.tsx:11`); `AMBER_MARKER` (`PortfolioBehind.tsx:8`, `PortfolioMatrix.tsx:7`); `CodeLinks.tsx:35` CHIP_COLOURS; Frozen/Open/Active (`ReleaseDetailRoute.tsx:293,295`, `ReleasesRoute.tsx:157,159`, `TemplatesRoute.tsx:116,119`, `TemplateDetailRoute.tsx:310`); `DashboardRoute.tsx:254,364`; `DocumentsSection.tsx` AiBadge; `assistant/AssistantTurn.tsx:7`, `ScopeChip.tsx`; outcome text `api/promotions.ts:50-53` | ~20 definitions | **Badge** with variants `success / warning / destructive / info / neutral / outline` | ReadinessPill is a **link** badge (`asChild`); `data-status`/`data-state` test hooks must stay |
| Buttons | 37 inline `bg-blue-600` primaries; `SMALL_BUTTON` `EnvironmentLadderSection.tsx:17` (imported app-wide); `BORDERED_BUTTON` ×4 (`BriefingSettingsSection.tsx:29`, `ClientDetailPanel.tsx:13`, `DeploymentHistory.tsx:16`, `PortfolioRegisterPanel.tsx:17`); `BUTTON` ×3 (`AiAssistPanel.tsx:24`, `AiResultCard.tsx:42`, `AssistantTurn.tsx:10`); `PRIMARY` `PackagesLink.tsx:14`; `CONFIRM_BLUE` ×2; red outline `ReleaseDetailRoute.tsx:397`; glyph icon buttons (×, ▸, ?) | ~150 | **Button** (`default`, `outline`, `destructive`, `ghost`, `link`; sizes `sm`, `default`, `icon`) | Keep the `aria-disabled` (in tab order) vs `disabled` distinction; the pending label swap ("Generating…") |
| Form fields | `FIELD` ×3 (`ProductJiraProjectsEditor.tsx:8`, `RecordInstallDialog.tsx:21`, `roadmap/AddFromReleaseDialog.tsx:9`); `NUMBER_INPUT` `BriefingSettingsSection.tsx:25`; `CONTROL` `DeploymentHistory.tsx:14`; inline inputs in Login, Settings, ReleaseDetail, TemplateDetail; `roadmap/InputCell.tsx` | ~40 | **Input, Textarea, Label, Select / NativeSelect, Checkbox, Form field** | Native `<select>` is used for many short lists. A NativeSelect keeps the native keyboard and mobile behaviour; use the Radix Select only where styling matters |
| Switch | `BriefingSettingsSection.tsx:~281-296` (`role="switch"`, hand-rolled knob) | 1 | **Switch** | Already reduced-motion aware |
| Tables | `DashboardRoute.tsx:386`, `DeploymentHistory.tsx`, `ReleasesRoute.tsx:204`, `ReleaseDetailRoute.tsx:629,789`, `PortfolioTab.tsx`, `PortfolioBehind.tsx`, `PortfolioMatrix.tsx`, `ClientDetailPanel.tsx`, `ClientInstallsSection.tsx`, `PromotionSection.tsx`, `roadmap/RoadmapTable.tsx`, `BriefingsRoute.tsx:213`, `TicketBrowser.tsx`, `TemplatesRoute.tsx`; duplicated `TH`/`TD` (`DashboardRoute.tsx:26-27`, `PortfolioBehind.tsx:5-6`, `PortfolioMatrix.tsx:5`, `PortfolioTab.tsx:9-10`) | 15 | **Table** (simple) / shared **DataTable** on TanStack Table (Roadmap, Releases, Dashboard matrix, Tickets, Release tickets, Run history) | Sticky columns, `scope` attributes, `<tfoot>` medians/totals, expandable detail rows, row selection with indeterminate |
| Cards / tiles / panels | `DashboardTiles.tsx:3,48` Tile/FilterTile; `PortfolioTab.tsx:12` TILE_BUTTON; `AiAssistPanel.tsx:23` CARD; `ClientDetailPanel.tsx:11` PANEL; `DocumentsSection.tsx:38`; `TemplatesRoute.tsx:96`; Briefing cards `BriefingsRoute.tsx:166` | ~10 | **Card** | FilterTile is a pressable card (`aria-pressed`) with a sibling help button |
| Inline success/info messages | `SettingsRoute.tsx` (Token replaced / ADO saved); `BriefingSettingsSection.tsx:530,598`; `ReleaseDetailRoute.tsx:536-575`; `RoadmapRoute.tsx:279-302`; `TemplateDetailRoute.tsx:351`; sr-only-only success in `PackagesLink.tsx:101`, `RecordInstallDialog.tsx:95`, `AiAssistPanel.tsx:233` | ~10 | **Sonner toast** (success / info), with Undo where reversible | Keep the polite live-region text (Sonner announces); errors stay inline `role="alert"` |
| Persistent alerts / notices | `CredentialAlert.tsx:9`, `AdoCredentialBanner.tsx:16`, amber notices (`BriefingsRoute.tsx:310`, `AiAssistPanel.tsx:197`, `DocumentViewerRoute.tsx:204`, `TemplateDetailRoute.tsx:360`, `RecordInstallDialog` not-in-Production), error blocks + Try again (`PortfolioStates.tsx:16` SectionError and ~10 copies) | ~20 | **Alert** (`default`, `warning`, `destructive`) | `role="alert"` vs `role="status"` as today; `data-reason` hooks |
| Loading | 30 spinners, 5 definitions (`DashboardRoute.tsx:62`, `DeploymentHistory.tsx:18`, `ExportButtons.tsx:8`, `BriefingSettingsSection.tsx:166`, `PortfolioStates.tsx:4`) | 30 | **Spinner** (one, reduced-motion) + **Skeleton** for first page loads | Spinner + text pattern ("Loading roadmap…") stays for in-place actions |
| Empty states | ~20 `rounded bg-gray-50 p-6 text-center` panels (`DashboardRoute.tsx:322,335,351,374`, `ReleasesRoute.tsx:182`, `RoadmapRoute.tsx:176,184`, `BriefingsRoute.tsx:193,200`, `TemplatesRoute.tsx:99`, `TemplateDetailRoute.tsx:481`, `ReleaseDetailRoute.tsx:578`, …) | ~20 | **Empty** (shadcn empty block) or a shared EmptyState | Heading + body + action copy is contract text; keep it verbatim |
| Navigation | `AppShell.tsx:37-103` (9 inline `Link`s), Sign out button `:26` | 1 | **Sidebar** or NavigationMenu + **DropdownMenu** (user menu: email, theme, Sign out) + **Command** palette (D-23-09) | Conditional "Assistant" link (`assistant.data?.enabled`) |
| Breadcrumb / back links | `DocumentViewerRoute.tsx:123` "← Back to release", `ReleaseDetailRoute.tsx:197` "Back to releases" | 2 | **Breadcrumb** | — |
| Separator / dividers | `divide-gray-100` ×7, `border-b border-gray-100` row rules | — | **Separator** / table tokens | — |

---

## Colour Inventory (raw Tailwind classes, by frequency and meaning)

Total: 1,507 colour-class uses in `.tsx`, plus 9 class strings in `.ts` (`api/promotions.ts:50-53`, `briefings/briefingFormat.ts:12-23`).

| Class | Uses | Meaning today | Proposed semantic token |
|---|---|---|---|
| `text-gray-700` | 224 | body text in cells, bordered-button text, labels | `text-foreground` (or `--foreground-secondary`) |
| `text-gray-600` | 217 | secondary text, captions, helper lines, inactive tabs/nav | `text-muted-foreground` |
| `border-gray-300` | 151 | control borders (buttons, inputs, chips) | `border-input` |
| `text-gray-900` | 146 | headings, emphasised names, active tab/nav | `text-foreground` |
| `text-red-600` | 83 | error text (`role="alert"`), invalid fields | `text-destructive` |
| `border-gray-200` | 76 | surface borders (cards, tables, dialogs) | `border-border` |
| `bg-white` | 71 | surfaces: cards, tables, dialogs, popovers, sticky cells | `bg-card` / `bg-popover` |
| `text-gray-500` | 66 | muted: archived, units, versions, "Not generated" | `text-muted-foreground` (+ an `--muted-foreground-subtle` if needed) |
| `text-blue-600` | 56 | links, text actions | `text-primary` (links: `--link`) |
| `ring-blue-600` | 50 | focus ring | `ring-ring` |
| `text-amber-700` | 44 | warning text and markers | `text-warning` |
| `bg-gray-50` | 44 | canvas, empty-state panels, hover rows, active tile | `bg-background` (canvas), `bg-muted` (panels), `hover:bg-accent` |
| `text-white` | 42 | text on filled primary/destructive buttons, frozen pill | `text-primary-foreground` / `text-destructive-foreground` |
| `bg-blue-600` | 37 | filled primary buttons, ladder reached dot, code-link bar, switch on | `bg-primary` (dots/bars: `--chart-1` or `--ladder-reached`) |
| `bg-gray-100` | 28 | chips, selected rows, filter-active, frozen notice | `bg-secondary` / `bg-muted` |
| `text-gray-400` | 21 | dashes, "No data", "No template" | `text-muted-foreground/70` (check contrast; 16-SPEC limits it to dashes with text equivalents) |
| `bg-amber-50` | 19 | warning surfaces and markers | `bg-warning-muted` |
| `border-gray-100` | 16 | table row rules | `border-border/60` |
| `border-amber-600` | 16 | warning borders | `border-warning` |
| `text-red-700` | 11 | FAILED outcome, red pills, red notices | `text-destructive` |
| `border-red-600` | 9 | failed pills, delete outline, failed ladder dot | `border-destructive` |
| `divide-gray-100` | 7 | list dividers | `divide-border` |
| `text-blue-900` | 6 | info banners (off-spec) | `text-info` |
| `bg-red-50` | 6 | failed pills, assistant error box | `bg-destructive-muted` |
| `bg-blue-50` | 6 | info banners, LINKED code-link chip, default-template button | `bg-info-muted` |
| `text-green-700` | 5 | success: Up to date, all clear, SUCCESS outcome | `text-success` |
| `text-amber-900` | 5 | shell credential banners, unknown-placeholder code (off-spec) | `text-warning` (normalise) |
| `border-blue-600` | 5 | active tab underline, "Default" pill outline | `border-primary` |
| `bg-red-600` | 5 | destructive confirm buttons, failed code-link segment | `bg-destructive` |
| `text-green-600` | 4 | Settings success text, ADO "OK" (**fails AA**) | `text-success` (700-equivalent) |
| `border-blue-200` | 4 | info banner borders | `border-info` |
| `bg-gray-800` | 4 | Frozen / Active pills (dark fill) | `bg-foreground text-background`, or Badge `secondary` |
| `text-blue-700` | 3 | "Production" marker, default-template button | `text-primary` |
| `text-amber-800` | 3 | CodeLinks incomplete, freeze-dialog warning (off-spec) | `text-warning` |
| `bg-gray-300` | 3 | ladder ahead dot, no-link bar segment, switch off | `bg-muted-foreground/30` / `--ladder-ahead` |
| `border-amber-300` / `bg-amber-100` | 2 / 2 | shell banners border / unknown-placeholder highlight | `border-warning` / `bg-warning-muted` |
| `text-amber-600` | 1 | "Stale" pill (**fails AA**) | `text-warning` |
| `ring-blue-300` | 1 | current ladder dot halo | `ring-primary/40` |
| `ring-gray-900` | 1 | pressed filter tile | `ring-foreground` |
| `border-green-700` / `border-green-600` / `bg-green-50` | 1 / 1 (in .ts) / 2 | success pill borders (inconsistent) / success pill bg | `border-success` / `bg-success-muted` |
| `bg-amber-600` | 1 | "has default" dot (`TemplatesRoute.tsx:126`) | `bg-warning` |
| `border-white` / `white/40` | 1 / 1 | spinner on blue button (`RecordInstallDialog.tsx:318`) | `primary-foreground` |

**Proposed semantic token set** (light + dark values in `@theme` / `:root` / `.dark`):
- shadcn base: `background, foreground, card, card-foreground, popover, popover-foreground, primary (brand, from the sketch), primary-foreground, secondary, secondary-foreground, muted, muted-foreground, accent, accent-foreground, destructive, destructive-foreground, border, input, ring, chart-1..5, sidebar-*`
- additions this app needs: `success, success-muted, warning, warning-muted, info, info-muted` and their `-foreground` (or `-border`) pairs
- domain aliases that point at the above: `--ladder-reached, --ladder-ahead, --ladder-failed, --ladder-current-ring`; readiness `up-to-date → success`, `out-of-date → warning`; briefing statuses per `briefingFormat.ts`; outcome text per `api/promotions.ts`

**Status-to-meaning map to preserve** (the source of truth for the Badge variants):

| State | Variant |
|---|---|
| SUCCESS / Up to date / All clear / "Up to date" (matrix) | success |
| STALE / ROLLED_BACK / Needs attention / Needs credential / Unreadable / behind / not in Production / outlier / incomplete | warning |
| FAILED / Run failed / Last promotion failed / errors | destructive |
| Quiet / Skipped / archived / VOIDED / Not run yet / NO_LINK / NOT_ATTEMPTED | neutral or muted |
| LINKED code links | info |

Colour is never the only signal; keep every word label.

---

## Dark-mode Hazards (D-23-06)

| Hazard | Where | Why it breaks | Treatment |
|---|---|---|---|
| Hard-coded white surfaces | `bg-white` ×71 across 48 files (top: `DashboardRoute.tsx` 5, `RoadmapTable.tsx` 5, `TemplateDetailRoute.tsx` 4) | They stay white on a dark canvas | → `bg-card` / `bg-popover` |
| Sticky table cells | `DashboardRoute.tsx:390,415,463`, `RoadmapTable.tsx:97,100,155,164`, `PortfolioMatrix.tsx:110,123` | `bg-white` sticky cells show as white stripes; the hover mismatch already exists in light mode | `bg-card` + `group-hover:bg-muted` on the row |
| Gray canvas and panels | `AppShell.tsx:19` `bg-gray-50`; ~20 empty-state panels `bg-gray-50`; hover `bg-gray-50` | Light canvas | `bg-background`, `bg-muted`, `hover:bg-accent` |
| Native `<dialog>` | the 9 dialogs above | UA `::backdrop` and `color: CanvasText` defaults; `bg-white` forced | Replaced by Radix Dialog/AlertDialog with token overlay |
| Ladder dots | `LadderDots.tsx:139,149`: reached `bg-blue-600`, ahead `bg-gray-300`, failed `border-red-600 bg-white`, current `ring-blue-300` | A white-filled failed dot glows on dark; `gray-300` ahead dots have no contrast against a dark card; the blue halo disappears | Ladder tokens; failed = `bg-card border-destructive`; check 3:1 non-text contrast in both themes |
| Readiness pills | `ReadinessPill.tsx:23-56` (green/amber 50 backgrounds, `gray-400` italic "No template") | Pastel 50 backgrounds glare on dark; `amber-600` text already fails in light | Badge `success`/`warning` with dark values (for example a `success/15` background + `success` text) |
| Briefing status pills | `briefingFormat.ts:12-23` (and `not_run` is `bg-white`) | Same as readiness; `bg-white` pill | Badge variants |
| Code-links chip and mini-bar | `CodeLinks.tsx:35-39`, `DashboardRoute.tsx:186-191` | Pastel chips; the `gray-100` + `gray-300` bar segments vanish | Chart and status tokens |
| Frozen / Active pills | `bg-gray-800 text-white` (`ReleaseDetailRoute.tsx:293`, `ReleasesRoute.tsx:157`, `TemplatesRoute.tsx:116`, `TemplateDetailRoute.tsx:310`) | A dark pill on a dark card is invisible | Badge `secondary`, or `bg-foreground text-background` |
| Rendered document preview | `RenderedDocument.tsx:47` (`prose prose-gray` + `dangerouslySetInnerHTML`), cover `:97` `bg-white`, header/footer `:118,127` `bg-gray-100`, unknown placeholder `amber-100/900` `:37,69`; `TemplatePreview.tsx` | Documents are **not themed** (D-23-06). The preview must stay "paper" | Wrap in a forced-light container (`.light` scope or `data-theme="light"` + `bg-white text-black`, `prose` not `prose-invert`) with a visible page edge on the dark canvas |
| Template textarea | `TemplateDetailRoute.tsx:438` (no bg/colour set, `focus:outline-none`) | Inherits; caret and selection contrast need checking; no focus style | `bg-background text-foreground` + a focus ring |
| Uploaded logo images | `LogoUpload.tsx:77`, `RenderedDocument.tsx:105` | Transparent PNG/SVG logos designed for white disappear on dark | Show logo previews on a forced-light tile |
| Inline style colours | `DashboardRoute.tsx:187-190` (width only; colours are classes) | — | Tokens on the classes |
| Spinners | `border-gray-300 border-t-blue-600` ×28; `border-white/40 border-t-white` `RecordInstallDialog.tsx:318`; `border-t-gray-600` `AssistantRoute.tsx:23` | A light track on dark | One Spinner using `currentColor` |
| Switch knob | `BriefingSettingsSection.tsx:~291` `bg-white shadow` on `bg-gray-300` | Off state is invisible on dark | shadcn Switch |
| Shell banners | `CredentialAlert.tsx:9`, `AdoCredentialBanner.tsx:16` (`amber-50`/`amber-900`) | Glare | Alert `warning` token |
| `text-gray-400` dashes / `italic` placeholders | 21 uses | Contrast in dark must be re-checked | `muted-foreground` |
| Charts (new, D-23-08) | — | — | Use `--chart-*` tokens from the start; never hard-code series colours |

---

## Files Audited

`frontend/index.html`, `frontend/package.json`, `frontend/src/index.css`, `main.tsx`, `router.tsx`, `components/AppShell.tsx`.

Routes: `DashboardRoute`, `ReleasesRoute`, `ReleaseDetailRoute`, `PortfolioRoute`, `RoadmapRoute`, `BriefingsRoute`, `SettingsRoute`, `TemplatesRoute`, `TemplateDetailRoute`, `DocumentViewerRoute`, `LoginRoute`, `TicketsRoute`, `AssistantRoute`.

Components: `ConfirmDialog`, `ReadinessPill`, `LadderDots`, `DashboardTiles`, `CredentialAlert`, `AdoCredentialBanner`, `DocumentsSection`, `ItemChurn`, `CodeLinks`, `RenderedDocument`, `PlaceholderDrawer`, `BriefingSettingsSection`.

`briefings/StatusPill`, `briefings/briefingFormat.ts`, `assistant/ReleaseAssistantDrawer`, `api/credentialStatus.ts`, `api/adoCredential.ts`, `api/promotions.ts`.

Grep-level class and pattern audits covered all 107 non-test `.tsx`/`.ts` files.

Contracts: 23-CONTEXT, 15-, 16-, 17- and 17.1-UI-SPEC.

Backend cross-check: `config/SecurityConfig.java:43-56`.
