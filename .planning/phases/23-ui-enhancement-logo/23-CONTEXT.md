# Phase 23: UI Enhancement & Logo (Foundation) - Context

**Gathered:** 2026-10-26
**Status:** Ready for UI audit, then UI spec

<domain>
## Phase Boundary

The user split the original Phase 23 in two (2026-10-26):

- **Phase 23 (this phase): the foundation.** It covers:
  - the "Shipflow" name, logo and favicon
  - brand and theme tokens, with light and dark mode
  - the shadcn/ui base component set
  - the app shell (header, nav, login page)
  - toasts and motion
  - the command palette
  - shared, ready-to-use DataTable and Chart components

  The shell and login use all of this, and at least one page proves each shared component works.
- **Phase 23.1 (inserted): the page migration.** Existing pages move onto the foundation in priority order. Charts go into the dashboard and portfolio, and rich tables go where they help.

The phase now runs before Phases 18, 21 and 22, at the user's choice. Pages those phases add must be built on the Phase 23 components, so they need no later polish pass.

Not in scope:
- new features or new data. The command palette only navigates to and triggers existing actions.
- the README and published images. Phase 24 owns those; Phase 23 delivers the logo assets for it.
</domain>

<decisions>
## Implementation Decisions

### Name and logo
- **D-23-01 (decided 2026-10-26, final):** The product name is **ProductOps** (renamed from Plurio the same day; same violet hub mark and palette; the wordmark keeps the name's casing). Previously: The product name was **Plurio**, with the tagline **"The product delivery platform"** and the description "One connected workspace for roadmaps, backlogs, engineering execution, release planning, and delivery visibility." (for the login page, README and Phase 24 listing). Shipflow was taken. The name lives in frontend/src/components/brand/brand.ts plus the index.html title; component and asset file names still say Shipflow and are renamed after Phase 23 so in-flight plans stay valid. Superseded wording: The product name is **Shipflow**, with the tagline **"your product roadmap helper"**. They appear on the login page and in the README, and the page `<title>` reads "Shipflow · your product roadmap helper". "Release Management" is no longer the product name. (Revised 2026-10-26: the user first typed "Amek", then chose Shipflow. Several unrelated apps already use the name "Shipflow", so the user should check before publishing.)
- **D-23-02:** The logo is **an icon plus a wordmark**. The icon alone is the favicon and the collapsed-nav mark.
- **D-23-03 (re-resolved 2026-10-26, sketch 002): Winner C "Connected hub" in Violet (kept for ProductOps) (#6d5bf5 → #a78bfa); app primary/chart-1 = violet-600 light, violet-400 + violet-950 text dark. Superseded sketch-001 result:** Winner **C: Sail**, palette **Indigo → Sky** (#4f46e5 → #38bdf8): a white sail-boat glyph on a rounded gradient tile, with a rounded lowercase "shipflow" wordmark. Originally: Claude drafts **three hand-written SVG concepts** in a sketch (`/gsd-sketch`) and the user picks one.
  - Each concept is shown in 2–3 palettes, and the **brand colour is chosen in that sketch**.
  - The final assets are an SVG (icon, and icon + wordmark, for light and dark), a favicon (SVG plus a PNG/ICO fallback) and a PNG export for README use.

### Component system
- **D-23-04:** Adopt **shadcn/ui**: Radix primitives, component code copied into the repo and owned, on the existing Tailwind v4, with `lucide-react` icons. This matches CLAUDE.md.
  - Hand-rolled dialogs, menus, tabs, tooltips, badges and buttons are replaced as pages migrate (23.1).
- **D-23-05:** Brand and semantic **design tokens** go in `@theme` / CSS variables in `frontend/src/index.css`.
  - Today the code has ~1,000 hard-coded `gray-*` / `blue-600` classes.
  - New and migrated code uses only token classes. Pages not yet migrated keep their raw classes until 23.1.

### Dark mode
- **D-23-06:** **Light and dark.** It follows the OS setting by default, with a toggle in the header. The choice is remembered per browser.
  - Charts, the ladder dots, the readiness pills and the rendered-document preview must stay legible in both themes. Generated documents themselves are not themed.

### Dynamic components (all four chosen)
- **D-23-07:** **Motion and feedback.** Toasts replace inline success/failure messages, with skeleton loaders and smooth transitions. Reduced motion is respected. Optimistic updates are used where TanStack Query already makes them safe.
- **D-23-08:** **Charts** come from one shared chart component set; the library is for research to pick, e.g. Recharts or the shadcn charts. They get their first real use in 23.1 (dashboard and portfolio). Phase 18's usage trends will reuse them.
- **D-23-09:** **Command palette (Ctrl/Cmd+K)** to jump to releases, clients, products, roadmap items, settings and existing actions, plus a few keyboard shortcuts, shown in the palette.
- **D-23-10:** **Rich tables** use one shared DataTable, likely TanStack Table, matching the TanStack stack. It supports sorting, filtering, sticky headers and column choice.

### Sequencing and priorities
- **D-23-11:** Run **`/gsd-ui-review` first** as the baseline audit. Its findings become the page checklist for 23.1 and the before/after measure.
- **D-23-12:** Page priority for 23.1:
  1. **Dashboard and portfolio**, the main candidates for charts
  2. **Release detail**
  3. **Roadmap, briefings and settings** (the roadmap table is a DataTable candidate)
  4. templates/documents and the rest, last
- **D-23-13:** The main pain point to fix is **"looks plain/dated"**: overall visual quality first, then consistency.

### Motion and hover (user, 2026-10-26)
- **D-23-14: Hover highlights everywhere it helps.**
  - **Table rows:** a soft violet wash; clickable rows also get a left accent bar.
  - **Cards and tiles:** lift slightly (shadow plus a 1-2px rise) with a violet border.
  - **Sidebar and nav items:** background highlight, plus an active indicator that slides between items.
  - **Buttons and links:** buttons brighten with a subtle press scale; links get an animated underline.
- **D-23-15: Animations.**
  - **Pages and lists:** pages fade in; list and table rows stagger in (150-250ms).
  - **Dashboard:** KPI numbers count up and chart bars grow in on first load.
  - **Status changes:** a badge or ladder dot pulses briefly when its status changes (promotion recorded, doc regenerated).
  - **Logo:** the hub mark's nodes connect in once on the login page and on app load (~600ms).

  Every animation is off under prefers-reduced-motion. Nothing loops or delays interaction.

### Claude's Discretion
- The exact shadcn component list in the base set.
- The chart and table libraries, within the TanStack-friendly constraint.
- Which page proves each shared component in Phase 23 itself.
- Animation durations and easing.
- The shortcut keys.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

- `.planning/ROADMAP.md`: Phases 23 and 23.1 (split 2026-10-26)
- `./CLAUDE.md`: the stack guidance (Tailwind + shadcn/ui preferred, TanStack Router/Query, Vite, TypeScript)
- `frontend/src/index.css`: today only `@import "tailwindcss"` and the typography plugin; tokens go here
- `frontend/src/components/AppShell.tsx`: header and nav; the plain-text "Release Management" title gets replaced
- `frontend/index.html`: `<title>`; no favicon today, and there is no `frontend/public/` yet
- Existing UI-SPECs, for the current interaction rules to keep: `.planning/phases/15-*/15-UI-SPEC.md`, `16-*/16-UI-SPEC.md`, `17-rice-roadmap/17-UI-SPEC.md`, `17.1-product-briefings-scheduler/17.1-UI-SPEC.md`
- `frontend/src/components/LogoUpload.tsx`: the **document-template** logo, unrelated to the app logo. Don't conflate them.
</canonical_refs>

<specifics>
## Specific Ideas
- One idea for the logo motif is an environment ladder or rising steps, echoing the promotion ladder. The sketch should explore it but is not limited to it.
- Login, header, browser tab and README all show the same mark.
</specifics>

<deferred>
## Deferred Ideas
- Templates/documents page restyle, the lowest priority in 23.1.
- README and screenshots (Phase 24).
</deferred>
</content>
