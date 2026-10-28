# Phase 23: UI Enhancement & Logo (Foundation) - Research

**Researched:** 2026-10-26
**Domain:** React 19 + Vite 8 + Tailwind v4 design-system foundation (shadcn/ui on Radix, theming, toasts, command palette, TanStack Table v9, Recharts), branding assets, router layout split
**Confidence:** HIGH for stack and integration (verified against the npm registry, the shadcn registry source and a CLI dry-run against this repo's config). MEDIUM for a few jsdom/cascade behaviours, which are flagged inline.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Name and logo**
- **D-23-01:** The product name is **Shipflow**, with the tagline **"your product roadmap helper"**. They appear on the login page and in the README, and the page `<title>` reads "Shipflow · your product roadmap helper". "Release Management" is no longer the product name. (Revised 2026-10-26: the user first typed "Amek", then chose Shipflow. Several unrelated apps already use the name "Shipflow", so the user should check before publishing.)
- **D-23-02:** The logo is **an icon plus a wordmark**. The icon alone is the favicon and the collapsed-nav mark.
- **D-23-03 (resolved 2026-10-26, sketch 001):** Winner **C: Sail**, palette **Indigo → Sky** (#4f46e5 → #38bdf8): a white sail-boat glyph on a rounded gradient tile, with a rounded lowercase "shipflow" wordmark. Originally: Claude drafts **three hand-written SVG concepts** in a sketch (`/gsd-sketch`) and the user picks one.
  - Each concept is shown in 2–3 palettes, and the **brand colour is chosen in that sketch**.
  - The final assets are an SVG (icon, and icon + wordmark, for light and dark), a favicon (SVG plus a PNG/ICO fallback) and a PNG export for README use.

**Component system**
- **D-23-04:** Adopt **shadcn/ui**: Radix primitives, component code copied into the repo and owned, on the existing Tailwind v4, with `lucide-react` icons. This matches CLAUDE.md.
  - Hand-rolled dialogs, menus, tabs, tooltips, badges and buttons are replaced as pages migrate (23.1).
- **D-23-05:** Brand and semantic **design tokens** go in `@theme` / CSS variables in `frontend/src/index.css`.
  - Today the code has ~1,000 hard-coded `gray-*` / `blue-600` classes.
  - New and migrated code uses only token classes. Pages not yet migrated keep their raw classes until 23.1.

**Dark mode**
- **D-23-06:** **Light and dark.** It follows the OS setting by default, with a toggle in the header. The choice is remembered per browser.
  - Charts, the ladder dots, the readiness pills and the rendered-document preview must stay legible in both themes. Generated documents themselves are not themed.

**Dynamic components (all four chosen)**
- **D-23-07:** **Motion and feedback.** Toasts replace inline success/failure messages, with skeleton loaders and smooth transitions. Reduced motion is respected. Optimistic updates are used where TanStack Query already makes them safe.
- **D-23-08:** **Charts** come from one shared chart component set; the library is for research to pick, e.g. Recharts or the shadcn charts. They get their first real use in 23.1 (dashboard and portfolio). Phase 18's usage trends will reuse them.
- **D-23-09:** **Command palette (Ctrl/Cmd+K)** to jump to releases, clients, products, roadmap items, settings and existing actions, plus a few keyboard shortcuts, shown in the palette.
- **D-23-10:** **Rich tables** use one shared DataTable, likely TanStack Table, matching the TanStack stack. It supports sorting, filtering, sticky headers and column choice.

**Sequencing and priorities**
- **D-23-11:** Run **`/gsd-ui-review` first** as the baseline audit. Its findings become the page checklist for 23.1 and the before/after measure.
- **D-23-12:** Page priority for 23.1: (1) Dashboard and portfolio, the main candidates for charts; (2) Release detail; (3) Roadmap, briefings and settings (the roadmap table is a DataTable candidate); (4) templates/documents and the rest, last.
- **D-23-13:** The main pain point to fix is **"looks plain/dated"**: overall visual quality first, then consistency.

### Claude's Discretion
- The exact shadcn component list in the base set.
- The chart and table libraries, within the TanStack-friendly constraint.
- Which page proves each shared component in Phase 23 itself.
- Animation durations and easing.
- The shortcut keys.

### Deferred Ideas (OUT OF SCOPE)
- Templates/documents page restyle, the lowest priority in 23.1.
- README and screenshots (Phase 24).

Also out of scope (CONTEXT `<domain>`): new features or new data (the palette only navigates and triggers existing actions); page-by-page migration (Phase 23.1, UI-07/UI-08).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| UI-01 | Branded "Shipflow" with tagline; SVG logo (icon + wordmark) and favicon in header, login page and browser tab | §Favicon and asset pipeline, §Pattern 5 (inline `ShipflowMark` component), §Pattern 3 (login outside the shell). Spring already serves `/favicon.ico`, `/icon.svg` etc. with no backend change. |
| UI-02 | Light/dark switch, OS default, remembered per browser, themed surfaces legible | §Pattern 2 (no-flash script + ThemeProvider with try/catch + `matchMedia` listener), §Pitfall 3 (unmigrated pages in dark), §Pitfall 4 (document preview stays light) |
| UI-03 | Colours from brand + semantic tokens; UI built from owned shadcn/ui components | §Pattern 1 (shadcn init with `-b radix`, `components.json`, `index.css` token layout), §Standard Stack, base component list |
| UI-04 | Toasts, skeletons, reduced motion | Sonner via shadcn `sonner` (edited to drop `next-themes`), `skeleton` + `spinner`, a global `prefers-reduced-motion` rule (§Pattern 6) |
| UI-05 | Ctrl/Cmd+K palette over releases, clients, products, roadmap items, settings, actions | §Pattern 7: shadcn `command` (cmdk 1.1.1) fed by the existing `useReleases('')`, `useClients()`, `useProducts()`, `useRoadmap(...)` hooks. No new endpoints. |
| UI-06 | Shared DataTable (sort, filter, sticky header, column choice) + chart component, each used on ≥1 page | §Pattern 8 (TanStack Table **v9** `useTable` + `tableFeatures`, proven on `/releases`), §Pattern 9 (shadcn `chart` + Recharts, lazy-loaded, proven on the dashboard "Releases per stage" tile) |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **Stack:** React SPA on a Java Spring Boot REST backend; Vite + TypeScript; Tailwind + shadcn/ui is the preferred UI lane (CLAUDE.md "Alternatives Considered"); TanStack Query; the router in use is TanStack Router (code-based).
- **No over-engineering or over-abstraction; minimal code; follow official docs.** Use shadcn components as generated. The shadcn data-table guide says to build your own DataTable from its pattern; do that, without wrapping it further.
- **Prefer editing existing files over creating new ones.** No feature flags or backward-compatibility shims.
- **Validate at system boundaries:** `localStorage` and URL params are boundaries (theme value, palette navigation).
- **Never commit secrets.** Stage files by name. No `Co-Authored-By` trailers (user memory overrides the attribution reminder).
- **GSD workflow enforcement:** edits go through `/gsd-execute-phase`.
- **Environment (orchestrator + user memory):** never run `npm run dev`; use `npm ci` only if `node_modules` is missing; Maven needs `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25`. A Vite or Vitest process holding `frontend/node_modules` makes a Maven `npm ci` fail with Windows EPERM and leaves a half-deleted tree.
- **Security constraint (project):** template source is untrusted. That is unrelated to this phase, but the chart `ChartStyle` injection point (see Security Domain) belongs to the same class of issue.

## Summary

The repo is a single Vite 8.1.5 + React 19.2.8 + TypeScript 7.0.2 + Tailwind 4.3.3 SPA with code-based TanStack Router 1.170.18 and TanStack Query 5.101.4. Tests use Vitest 4.1.10 on jsdom 26.1.0 with raw `react-dom/client` + `act`, not Testing Library. The baseline is 847 tests passing in about 13 s; one run showed a single flaky failure that passed on rerun. `index.css` is two lines. There is no `@/` alias, no `public/` folder and no `components.json`. The frontend is built by `frontend-maven-plugin` (Node **v20.19.0**) into `target/classes/static`. Spring serves it, `SecurityConfig` permits all GET/HEAD outside `/api` and `/actuator`, and `SpaForwardingController` only forwards dot-free paths. So `/icon.svg`, `/favicon.ico` and lazy chunks under `/assets/` need **no backend change**. There is no CSP header today, so an inline no-flash theme script in `index.html` works.

The shadcn ecosystem moved a lot in 2026, and training-era knowledge is wrong in three ways:
1. **Base UI is now the shadcn default** (July 2026). Because D-23-04 locks Radix, init **must** pass `-b radix`, and the style is `radix-nova` (or another `radix-*` preset).
2. Components import **`cn` from the `cn` package**, not a `clsx`/`tailwind-merge` helper (September 2026). The latest `cn@0.5.0` declares `node >=22`, but the Maven build runs Node 20.19, so pin `cn@0.4.0`.
3. The shadcn data-table guide now targets **TanStack Table v9** (`useTable` + `tableFeatures`, GA since 2026-08-04, latest 9.3.1), not v8's `useReactTable`.

A CLI dry-run (`shadcn@4.21.4 add … --dry-run`) against a copy of this repo's config with `"style": "radix-nova"` resolved the full base set: 27 files, with dependencies `cn`, `sonner`, `next-themes`, `recharts@3.8.0` and `cmdk`. Recharts is about 137 KB gzipped on top of a main chunk that is already 740 KB raw / 219 KB gzipped, so the chart must be lazy-loaded.

The structural changes are small, and each one fixes a baseline finding:
- **Router:** move `AppShell` from the root route onto the existing pathless `authenticated` route, and give the root a bare `<Outlet/>` + `<Toaster/>`. Login then renders outside the shell. Route IDs (`/authenticated/...`, used by `useSearch({from})` in 9 places) stay unchanged.
- **Login dead end:** `SITE_NOT_CONFIGURED` is effectively unreachable, because `JiraSiteConfigSeeder` seeds `RM_JIRA_DEFAULT_BASE_URL` (default `https://example.atlassian.net`) whenever the table is empty. The "Go to Settings" link (`LoginRoute.tsx:45`) should become operator guidance naming that variable.
- **Global CSS:** a single `prefers-reduced-motion` rule fixes the 24 non-compliant spinners app-wide without touching them.

**Primary recommendation:** Hand-write `components.json` (`radix-nova`, aliases `@/…`, css `src/index.css`), add the `@/*` path (no `baseUrl`; TypeScript 7 removed it) and the Vite alias. Install pinned deps, then `npx shadcn@4.21.4 add` the base set. Replace the generated `next-themes` usage in `sonner.tsx` with a ~40-line owned ThemeProvider. Put tokens in `index.css` (shadcn `:root`/`.dark` + `@theme inline` + app extras `success/warning/info`). Add a `tests/setup.ts` with jsdom polyfills. Prove DataTable on `/releases` and the chart (lazy) on the dashboard "Releases per stage" tile.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Logo, wordmark, favicon | Browser / Client (React component + static files) | CDN / Static (Spring `classpath:static` via the jar) | Favicons are static files Vite copies from `public/`; the in-app mark is an inline SVG component so it themes and needs no request |
| Theme (light/dark/system, persistence) | Browser / Client | — | `localStorage` + `matchMedia`; a no-flash inline script in `index.html` runs before first paint |
| Design tokens | Browser / Client (CSS) | — | CSS variables in `index.css`, consumed by Tailwind v4 `@theme inline` |
| Toasts, skeletons, motion | Browser / Client | — | Pure presentation; mutation callbacks call `toast.*` |
| Command palette data | Browser / Client | API / Backend (existing GET endpoints only) | Reuses cached TanStack Query data from `/api/releases`, `/api/clients`, `/api/products`, `/api/roadmap/items`; **no new endpoints** |
| DataTable sorting/filtering/visibility | Browser / Client | — | Client-side TanStack Table row models over data already fetched; server search on `/releases` (`?query=`) stays as it is |
| Charts | Browser / Client | — | Recharts renders data the dashboard already loads; lazy chunk under `/assets/` |
| Login layout / auth gating | Browser / Client (router) | API / Backend (`/api/auth/session`, unchanged) | `authenticatedRoute.beforeLoad` already gates; only the layout component moves |
| Static asset serving and security | API / Backend (Spring) | — | Already correct: `GET /**` permitAll, SPA forwarder ignores dotted paths. A test addition is optional, with no code change |

## Standard Stack

All versions verified against the npm registry on 2026-10-26 (`npm view <pkg> version` / `time`). The rule "at least two weeks old" means published on or before 2026-10-12.

### Core (new runtime dependencies)
| Library | Version (pin) | Published | Purpose | Why Standard |
|---------|---------------|-----------|---------|--------------|
| `radix-ui` | 1.8.1 | 2026-10-12 | Radix primitives (single package; shadcn radix components import `{ Dialog as DialogPrimitive } from "radix-ui"`) | Locked by D-23-04; the shadcn `radix` base declares it [VERIFIED: shadcn registry `bases.ts`, CLI dry-run output] |
| `cn` | **0.4.0** (not 0.5.0) | 2026-09-22 | `cn()` class merge, a drop-in for `twMerge(clsx())` | Every shadcn component now imports `cn` from `"cn"` [CITED: ui.shadcn.com changelog 2026-09-cn]. 0.5.0 (2026-10-16) declares `engines.node >=22`, but the Maven build uses Node 20.19.0 [VERIFIED: npm view cn@0.5.0 engines; pom.xml `<nodeVersion>v20.19.0`] |
| `class-variance-authority` | 0.7.1 | 2024 | Variant API in Button/Badge/etc. | shadcn manual-install dependency [CITED: shadcn docs installation/manual] |
| `lucide-react` | 1.55.0 | 2026-10-10 | Icons | `iconLibrary: "lucide"`; D-23-04. Latest 1.61.0 is 5 days old, so pin 1.55.0 [VERIFIED: npm] |
| `sonner` | 2.0.8 | 2026-08-09 | Toasts | shadcn `sonner` component dependency; React 19 peer OK [VERIFIED: npm peerDependencies] |
| `cmdk` | 1.1.1 | 2025-03-14 | Command palette engine | shadcn `command` dependency; its `@radix-ui/react-dialog ^1.1.6` dedupes against radix-ui's 1.3.1 [VERIFIED: npm] |
| `@tanstack/react-table` | 9.3.1 | 2026-10-12 | Headless table (sort/filter/visibility) | The shadcn data-table guide now targets v9 [CITED: ui.shadcn.com/docs/components/data-table]. v9 GA 2026-08-04; brings `@tanstack/react-store ^0.11.2` |
| `recharts` | 3.8.0 (registry pin) | 2026-03-06 | Charts under shadcn `chart` | The shadcn radix registry pins `recharts@3.8.0` for `chart.tsx` [VERIFIED: registry `_registry.ts` line 229 + dry-run]. Latest is 3.10.1; stay on the registry pin unless the planner chooses to verify 3.10 against `chart.tsx` |
| `react-is` | 19.2.8 | — | Recharts peer dependency | Recharts 3 lists `react-is` as a peer; match the React version (19.2.8) [VERIFIED: npm view recharts@3.8.0 peerDependencies] |

### Dev dependencies
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `shadcn` | 4.21.4 (CLI; 2026-10-07) | CLI + `shadcn/tailwind.css` (keyframes + `data-open`/`data-closed`/`data-checked`… custom variants the components use) | Imported by `index.css` at build time only, so **devDependency**. Its own deps (ts-morph, babel, MCP SDK) are heavy and must not be runtime deps [VERIFIED: unpkg shadcn@4.21.4/dist/tailwind.css, npm view dependencies] |
| `tw-animate-css` | 1.4.0 | `animate-in/out`, `fade-in`, `zoom-in` utilities used by the dialog/popover/sheet classes | Imported in `index.css`; devDependency (CSS only) |

**Not added:**
- `next-themes`: the shadcn `sonner` item pulls it in, but it targets Next.js and was last published 2025-03-11. Use an owned ThemeProvider (official shadcn Vite dark-mode pattern) and edit `sonner.tsx` to read it.
- `clsx` / `tailwind-merge`: replaced by `cn`.
- `@testing-library/*`: not needed; the repo's raw `act` pattern works. Adding it is optional and would be a convention change across 74 files.
- `vaul`, `react-day-picker`: not in the base set.

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| shadcn `-b radix` | Base UI (shadcn's new default) | Contradicts locked D-23-04. Radix is "not being deprecated; every update ships for both" [CITED: shadcn changelog 2026-07-base-ui-default] |
| Sonner | shadcn "Toast" (new, July 2026) | Base-UI-only component, so not available on the Radix base [CITED: shadcn changelog 2026-07-toast] |
| TanStack Table v9 `useTable` | v8 8.21.3 / v9 `useLegacyTable` | v8's last release was 2025-04 and the shadcn docs moved off it. `useLegacyTable` is deprecated and includes every feature, giving a bigger bundle [CITED: tanstack.com/table/v9 migrating guide] |
| Recharts via shadcn `chart` | visx / ECharts / Chart.js | shadcn `chart` gives token-driven theming (`--chart-1..5`, `.dark` overrides) for free; D-23-08 suggests Recharts or shadcn charts |
| Vite `resolve.alias` | `resolve.tsconfigPaths: true` (Vite 8 native) | Both work. The Vite docs note a performance cost for `tsconfigPaths`, and the shadcn Vite guide uses an explicit alias [CITED: ui.shadcn.com/docs/installation/vite; vite.dev/guide/features] |

**Installation (run from `frontend/`, with no Vite/Vitest process running):**
```bash
npm install radix-ui@1.8.1 cn@0.4.0 class-variance-authority@0.7.1 lucide-react@1.55.0 \
  sonner@2.0.8 cmdk@1.1.1 @tanstack/react-table@9.3.1 recharts@3.8.0 react-is@19.2.8 --save-exact
npm install -D shadcn@4.21.4 tw-animate-css@1.4.0 --save-exact
npx shadcn@4.21.4 add button badge card input label textarea native-select checkbox switch \
  separator skeleton spinner kbd tooltip popover dropdown-menu dialog alert-dialog alert sheet \
  tabs table command sonner chart empty
# then: npm uninstall next-themes   (and edit src/components/ui/sonner.tsx, see Pattern 4)
```
The repo pins exact versions (`"react": "19.2.8"`), so use `--save-exact`. `shadcn add` installs `latest` of `cn`/`sonner`/`cmdk` unless they are already present, so **install the pinned versions first**. Then check `package.json` after `add` and re-pin anything it bumped (especially `cn` → 0.5.0).

## Architecture Patterns

### System Architecture Diagram

```
                      browser request
                            │
            ┌───────────────┴────────────────┐
            │ Spring Boot (jar, static/)     │  GET /**  permitAll; dotted paths served as files
            │  /icon.svg /favicon.ico        │──────────────► static file (from frontend/public via Vite)
            │  /assets/*.js (incl. lazy      │──────────────► static file
            │   chart chunk)                 │
            │  /releases/..  (no dot)        │──► SpaForwardingController → index.html
            └───────────────┬────────────────┘
                            ▼
 index.html <head>: inline theme script ── reads localStorage('shipflow-theme') ──► toggles <html class="dark">
                            │                 (try/catch; falls back to matchMedia)
                            ▼
 main.tsx: ThemeProvider ─► QueryClientProvider ─► RouterProvider
                            │
                 rootRoute (RootLayout: <Outlet/> + <Toaster/>)
                 ├── /login ──► LoginRoute (branded standalone layout: mark + wordmark + tagline + Card)
                 └── authenticated (beforeLoad: session or redirect /login)
                       component: AppShell
                         ├── header: ShipflowMark + wordmark, nav (lucide icons), ModeToggle, user menu
                         ├── CommandPalette (Ctrl/Cmd+K) ◄── useReleases('') / useClients / useProducts / useRoadmap
                         │        └─ select ─► router.navigate(...) or action (theme, sign out)
                         ├── CredentialAlert, AdoCredentialBanner (unchanged)
                         └── <Outlet/> pages
                               ├── /releases ──► DataTable (TanStack Table v9 features: sort/filter/visibility)
                               └── /        ──► "Releases per stage" tile ─► React.lazy(StageChart) ─► Recharts (separate chunk)
 mutations ─► onSuccess/onError ─► toast.success / toast.error (Sonner, rendered by root <Toaster/>)
```

### Recommended Project Structure
```
frontend/
├── components.json                 # NEW, hand-written (radix-nova, aliases @/…)
├── index.html                      # title, favicon links, inline no-flash theme script
├── public/                         # NEW, Vite copies to dist root, so Spring serves at /
│   ├── favicon.ico                 # 32×32 (+16) fallback
│   ├── icon.svg                    # gradient tile + sail glyph
│   └── apple-touch-icon.png        # 180×180, opaque background
├── src/
│   ├── index.css                   # imports + @custom-variant dark + tokens + base + reduced-motion
│   ├── lib/utils.ts                # export { cn } from "cn"
│   ├── components/
│   │   ├── ui/                     # shadcn-generated, owned (kebab-case files, @/ imports)
│   │   ├── ShipflowLogo.tsx        # ShipflowMark (icon) + ShipflowLogo (mark + wordmark)
│   │   ├── ThemeProvider.tsx       # theme context + useTheme
│   │   ├── ModeToggle.tsx          # header toggle (DropdownMenu: Light/Dark/System)
│   │   ├── CommandPalette.tsx      # CommandDialog + global Ctrl/Cmd+K
│   │   ├── DataTable.tsx           # shared DataTable + column header + view options
│   │   ├── dataTableFeatures.ts    # tableFeatures({...}) shared instance + type
│   │   └── StageChart.tsx          # lazy-loaded chart proof (default export for React.lazy)
│   ├── components/AppShell.tsx     # existing; now the authenticated layout
│   ├── routes/LoginRoute.tsx       # existing; now standalone branded layout
│   └── router.tsx                  # root = RootLayout; authenticatedRoute.component = AppShell
├── tests/setup.ts                  # NEW, jsdom polyfills (registered via test.setupFiles)
docs/brand/                         # README/Phase 24 assets (not served by the app)
├── shipflow-logo-light.svg, shipflow-logo-dark.svg (wordmark outlined to paths)
└── shipflow-logo.png
```
Keep app-level files in the existing flat PascalCase `src/components/` convention. Only `ui/` uses the shadcn kebab-case + `@/` style. App code keeps relative imports (the existing convention), so the alias exists for generated files and new code may use either. Do not mass-rewrite existing imports.

### Pattern 1: shadcn init on this repo (manual config, CLI for components)

**What:** Write `components.json` by hand and use the CLI only for `add`. Running `shadcn init` is not needed. It would install Base UI unless `-b radix` is passed, may write `src/styles/globals.css`, and would overwrite `index.css` decisions the UI-SPEC owns.

```json
// frontend/components.json  (dry-run verified with shadcn@4.21.4)
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "radix-nova",
  "rsc": false,
  "tsx": true,
  "tailwind": { "config": "", "css": "src/index.css", "baseColor": "neutral", "cssVariables": true, "prefix": "" },
  "aliases": { "components": "@/components", "utils": "@/lib/utils", "ui": "@/components/ui", "lib": "@/lib", "hooks": "@/hooks" },
  "iconLibrary": "lucide"
}
```
`style` "cannot be changed after initialization" [CITED: shadcn components-json docs]. The `radix-` prefix selects Radix; `nova` / `vega` / `maia` / `lyra` / `mira` / … are density/shape presets. The UI-SPEC may choose a different `radix-*` preset. If it does not, use `radix-nova`.

```jsonc
// frontend/tsconfig.json: add paths. NO baseUrl (TypeScript 7 removed it; paths resolve relative to tsconfig)
"paths": { "@/*": ["./src/*"] }
```
```ts
// frontend/vite.config.ts
import { fileURLToPath, URL } from 'node:url';
// ...
resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
test: { environment: 'jsdom', include: ['tests/**/*.test.ts?(x)'], setupFiles: ['./tests/setup.ts'] },
```
Use `fileURLToPath(new URL(...))` rather than the doc's `path.resolve(__dirname)`, because the package is `"type": "module"`.

`index.css` layout. The UI-SPEC owns the values; this is the shape from the official manual install [CITED: shadcn installation/manual]:
```css
@import "tailwindcss";
@import "tw-animate-css";
@import "shadcn/tailwind.css";
@plugin "@tailwindcss/typography";

@custom-variant dark (&:is(.dark *));

@theme inline {
  --color-background: var(--background);
  /* … the full shadcn list (card, popover, primary, secondary, muted, accent, destructive, border, input, ring, chart-1..5, sidebar-*) … */
  /* app extras from 23-UI-BASELINE "proposed tokens": */
  --color-success: var(--success);   --color-success-muted: var(--success-muted);
  --color-warning: var(--warning);   --color-warning-muted: var(--warning-muted);
  --color-info: var(--info);         --color-info-muted: var(--info-muted);
  --color-brand-from: var(--brand-from); --color-brand-to: var(--brand-to);
  --radius-sm: calc(var(--radius) * 0.6); /* … radius scale … */
}
:root { color-scheme: light; --radius: 0.625rem; --background: …; /* light values */ }
.dark { color-scheme: dark; --background: …; /* dark values */ }
@layer base { * { @apply border-border outline-ring/50; } body { @apply bg-background text-foreground; } }
```
Note `color-scheme` on `:root` / `.dark`. It makes native scrollbars, `<select>`, date inputs and the native `<dialog>` UA colours follow the theme, which matters for the unmigrated pages.

### Pattern 2: Theme with no flash, OS default and safe persistence

The official shadcn Vite provider reads `localStorage` unguarded and ignores OS changes while on "system" [CITED: shadcn docs dark-mode/vite]. Adapt it:

```html
<!-- index.html <head>, before any stylesheet; runs synchronously before first paint -->
<script>
  try {
    var t = localStorage.getItem('shipflow-theme');
    var dark = t === 'dark' || (t !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.classList.toggle('dark', dark);
  } catch (e) {}
</script>
```
```tsx
// ThemeProvider.tsx (sketch): theme ∈ 'light' | 'dark' | 'system'; key 'shipflow-theme'
function readStored(): Theme {
  try { const v = localStorage.getItem(KEY); return v === 'light' || v === 'dark' ? v : 'system'; }
  catch { return 'system'; }               // Safari private mode / storage disabled
}
useEffect(() => {
  const mq = window.matchMedia('(prefers-color-scheme: dark)');
  const apply = () => document.documentElement.classList.toggle('dark', theme === 'dark' || (theme === 'system' && mq.matches));
  apply();
  if (theme !== 'system') return;
  mq.addEventListener('change', apply);     // follow the OS live while on "system"
  return () => mq.removeEventListener('change', apply);
}, [theme]);
// setTheme: try { theme === 'system' ? localStorage.removeItem(KEY) : localStorage.setItem(KEY, theme) } catch {}
```
Use a default context value (not `undefined` + throw), so components that call `useTheme()` (ModeToggle, `sonner.tsx`) still render in existing unit tests that do not wrap a provider.

### Pattern 3: Login outside the shell (router layout split)

```tsx
// router.tsx: minimal diff
const rootRoute = createRootRoute({ component: RootLayout });   // RootLayout = <><Outlet /><Toaster /></>
const authenticatedRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: 'authenticated',
  beforeLoad: /* unchanged */,
  component: AppShell,                                             // NEW: shell only for signed-in routes
});
```
- Route IDs stay `/authenticated/...`, so the 9 `useSearch({ from: '/authenticated/…' })` calls are untouched.
- `AppShell` keeps using `useSession()` for the email. It no longer needs the signed-out branch, but keep the existing `AppShell.test.tsx` "signed out" case meaningful (the assistant status must still not be fetched without a session), or update that one test deliberately.
- Not-found URLs now render without the shell (the root has no shell). Add `notFoundComponent` only if the UI-SPEC wants one; this is optional.
- **Login dead end:** `SITE_NOT_CONFIGURED` is returned only when `jira_site_config` is empty, and `JiraSiteConfigSeeder` fills it from `app.jira.default-base-url` (`RM_JIRA_DEFAULT_BASE_URL`, default `https://example.atlassian.net`) whenever it is empty [VERIFIED: docs/database.md §jira_site_config, application.yml:33]. So a fresh install always has a site. Replace the link with operator guidance (e.g. "Ask an administrator to set RM_JIRA_DEFAULT_BASE_URL and restart."). The UI-SPEC owns the copy. `reasonMessages.ts:9` ("Set one on the Settings screen first.") is shared with `SettingsRoute.tsx:25`; change only the login rendering, or give login its own sentence.

### Pattern 4: Sonner without next-themes

The generated `src/components/ui/sonner.tsx` starts with `import { useTheme } from "next-themes"` [VERIFIED: CLI dry-run --view]. Change that one import to the owned provider and pass the resolved theme:
```tsx
import { useTheme } from "@/components/ThemeProvider";
const { theme } = useTheme();          // 'light' | 'dark' | 'system'; Sonner accepts all three
<Sonner theme={theme} … />
```
Mount `<Toaster />` once in `RootLayout`, so toasts survive route changes and also work on `/login`. `toast()` calls with no mounted Toaster are no-ops, so existing component tests are unaffected. Keep errors inline (`role="alert"`, the baseline says flows depend on it). Toasts are for success/info, plus Undo where reversible.

### Pattern 5: The logo as an inline component

Use one source of truth for the glyph (sketch 001, variant C, verbatim paths):
```tsx
// ShipflowLogo.tsx
export function ShipflowMark({ className }: { className?: string }) {
  const id = useId();                                   // unique gradient id per instance
  return (
    <svg viewBox="0 0 32 32" className={className} aria-hidden="true">
      <defs><linearGradient id={id} x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#4f46e5"/><stop offset="1" stopColor="#38bdf8"/></linearGradient></defs>
      <rect width="32" height="32" rx="9" fill={`url(#${id})`} />   {/* tile radius ≈ 28% per sketch */}
      <g fill="#fff" transform="translate(6.08 6.08) scale(0.62)">  {/* glyph at 62% of tile, per sketch .tile svg */}
        <path d="M15 3v16H6.5z"/><path d="M17.5 7.5L26 19h-8.5z" opacity=".7"/><path d="M4 22h24l-3.2 5H7.2z"/>
      </g>
    </svg>
  );
}
```
The wordmark is live text ("shipflow", lowercase, rounded stack `ui-rounded, "SF Pro Rounded", "Nunito", "Segoe UI", system-ui`, weight 800 per the sketch theme). The accessible name comes from the visible text, or from `aria-label="Shipflow"` on the home link. The shell's `<h1>` becomes the logo link. The UI-SPEC decides heading semantics, given the baseline's `<h1>` clash with `PortfolioRoute.tsx:119`.

The same SVG (with literal `id`s) is saved as `public/icon.svg`. Note that `ui-rounded` only exists on Apple platforms, so on Windows the wordmark renders in Nunito (if installed) or Segoe UI. If the UI-SPEC wants an identical wordmark everywhere, either self-host a font (`@fontsource-variable/nunito` 5.3.0, OFL), or render the in-app wordmark as outlined SVG paths.

### Pattern 6: Reduced motion, globally

```css
@media (prefers-reduced-motion: reduce) {
  *, ::before, ::after {
    animation-duration: 0.01ms !important; animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important; scroll-behavior: auto !important;
  }
}
```
This covers the shadcn `animate-in/out` classes, Sonner, skeleton `animate-pulse`, and the **24 existing spinners that lack `motion-reduce:animate-none`** (baseline fix 3) without touching them. Radix Presence still receives `animationend` (duration is not zero), so exit-unmount keeps working. The new shared `Spinner` should still carry `motion-reduce:animate-none` per the existing UI-SPEC rule. [ASSUMED: Radix Presence behaviour under 0.01ms; widely used pattern, low risk]

### Pattern 7: Command palette from existing hooks

```tsx
// CommandPalette.tsx (sketch)
const [open, setOpen] = useState(false);
useEffect(() => {
  const onKey = (e: KeyboardEvent) => {
    if (e.key.toLowerCase() === 'k' && (e.metaKey || e.ctrlKey)) { e.preventDefault(); setOpen((o) => !o); }
  };
  document.addEventListener('keydown', onKey);
  return () => document.removeEventListener('keydown', onKey);
}, []);
```

| Group | Source (existing) | Query key reused | Navigate to |
|---|---|---|---|
| Pages | static list (same as nav; Assistant only if `useAssistantStatus(...).data?.enabled`) | — | `/`, `/tickets`, `/releases`, `/portfolio`, `/roadmap`, `/briefings`, `/assistant`, `/templates`, `/settings` |
| Releases | `useReleases('')` → `GET /api/releases` (unbounded list, `ReleaseSummary{id,name,version,status}`) | `['releases','']` (shared with `/releases` page) | `/releases/$releaseId` |
| Clients | `useClients()` → `{id,name,archived}` | `['clients']` | `/portfolio?tab=clients&client=<id>` (existing search params) |
| Products | `useProducts()` → `{id,name,archived}` | `['products']` | No product detail route exists; use `/?products=<id>` (dashboard filter, `parseDashboardSearch`) or `/portfolio?tab=products`. The planner/UI-SPEC picks one; no new route |
| Roadmap items | `useRoadmap({products:[],done:false,archived:false})` | `['roadmap','list',…]` | `/roadmap?item=<uuid>` (`parseRoadmapSearch` already validates a UUID `item` and focuses that row) |
| Actions | theme light/dark/system, Sign out (`useLogout`), "Create a release" → `/tickets` (the only creation path, baseline 6) | — | — |

- **Fetch only when open.** These hooks call `useQuery` with no `enabled` option. Add an optional `enabled = true` parameter to the four hooks (a one-line change each). Otherwise, opening the shell would fire 4 list requests on every page load. Cached data shows instantly when present.
- Use cmdk's built-in filtering (`shouldFilter` default). Give each item a `value` of `${name} ${version}` plus a unique suffix (e.g. the id in `keywords`), because duplicate `value`s collapse in cmdk.
- Archived clients and products: show them, with an "archived" badge, or hide them. That is a UI-SPEC call.
- Shortcuts (discretion): keep to **Ctrl/Cmd+K** for the palette, and show any extra shortcuts as `Kbd` hints. Do not add single-letter global shortcuts: the app has many text inputs and in-cell editors (`roadmap/InputCell.tsx`).

### Pattern 8: Shared DataTable on TanStack Table v9

The structure follows the official guide [CITED: ui.shadcn.com/docs/components/data-table, "This guide uses TanStack Table v9"]:
```ts
// dataTableFeatures.ts
import { columnFilteringFeature, columnVisibilityFeature, rowSortingFeature,
  createFilteredRowModel, createSortedRowModel, filterFn_includesString,
  sortFn_alphanumeric, sortFn_text, tableFeatures } from '@tanstack/react-table';
export const features = tableFeatures({
  columnFilteringFeature, columnVisibilityFeature, rowSortingFeature,
  filteredRowModel: createFilteredRowModel(), sortedRowModel: createSortedRowModel(),
  filterFns: { includesString: filterFn_includesString },
  sortFns: { alphanumeric: sortFn_alphanumeric, text: sortFn_text },
});
export type DataTableFeatures = typeof features;
```
```tsx
// DataTable.tsx core
const table = useTable({ features, data, columns,
  onSortingChange: setSorting, onColumnFiltersChange: setColumnFilters, onColumnVisibilityChange: setColumnVisibility,
  state: { sorting, columnFilters, columnVisibility } });
// render: <table.FlexRender header={header} /> / <table.FlexRender cell={cell} />
// columns: createColumnHelper<DataTableFeatures, Row>().columns([...])
// column choice: table.getAllColumns().filter(c => c.getCanHide()) → DropdownMenuCheckboxItem
```
- **Sticky header:** shadcn `Table` wraps `<table>` in a `relative w-full overflow-x-auto` div, so `sticky top-0` on `<thead>`/`<th>` sticks relative to *that* scroll container. Give the DataTable container a `max-h-*` + `overflow-auto` (and an opaque `bg-card` on the header cells), or sticky does nothing.
- **Proof page: `/releases`** (`ReleasesRoute.tsx:194-284`). It has 7 + 3 columns, no in-cell editing and no expandable rows, and it already has a server search box (keep that; add client-side sort and column choice). The doc-readiness columns must keep the T-05-60 rule (a readiness failure blanks only those cells). The Roadmap table (in-cell editing, expandable rows, sticky Rank+Item) is a 23.1 job.
- Extra v9 features needed in 23.1 (row selection, expanding, column pinning for sticky first columns) are added to the same `features` object later. That is the extension point, so do not pre-register them now.

### Pattern 9: Chart, lazy-loaded and token-driven

- `npx shadcn add chart` → `ui/chart.tsx` (`ChartContainer`, `ChartTooltip(Content)`, `ChartLegend(Content)`, `ChartConfig`). Colours come from `--chart-1..5` with `.dark` overrides.
- **Proof:** the dashboard "Releases per stage" tile (`DashboardRoute.tsx:137-150`, data already in `stages` + `perStage` + `summary.notPromoted`). Render a small `BarChart accessibilityLayer` **in addition to** the existing text list (visually de-emphasised or `sr-only`). `DashboardRoute.test.tsx:176-184` asserts `'QA 1'` and `'Not promoted 0'`, and the text list is also the accessible equivalent.
- **Lazy:** `const StageChart = lazy(() => import('../components/StageChart'))` inside `<Suspense fallback={<Skeleton className="h-24" />}>`. Recharts (about 137 KB gzipped full package [VERIFIED: bundlephobia API, recharts@3.8.0]) then lands in its own `/assets/*.js` chunk, already excluded from the SPA forwarder. Route-level `.lazy()` (TanStack Router `createLazyRoute`) is unnecessary for this phase.
- **Bar colours per stage:** put `fill: 'var(--chart-1)'` (etc.) on the data rows, or use one series colour. **Never** derive `ChartConfig` keys from stage names (see Security Domain).

### Anti-Patterns to Avoid
- **`npx shadcn init` with defaults:** silently installs Base UI (`@base-ui/react`) instead of Radix [CITED: changelog 2026-07-base-ui-default].
- **Adding `baseUrl` for the alias:** TypeScript 7 removed it, causing a hard error (TS5102) [CITED: community migration notes; MEDIUM].
- **Keeping `next-themes`:** a second theme source of truth and a Next-oriented dependency.
- **Migrating pages in this phase:** only the shell, login and the two proof surfaces change. Leave raw `gray-*`/`blue-600` classes elsewhere (D-23-05).
- **Wrapping shadcn components in another abstraction layer:** use them as generated (CLAUDE.md: no over-abstraction).
- **Eager-importing `recharts` from a module in the main graph:** that includes any re-export through a barrel file.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Accessible dialog/menu/popover/tooltip | more native `<dialog>` + hand-rolled focus code | shadcn `dialog`, `alert-dialog`, `dropdown-menu`, `popover`, `tooltip` (Radix) | Focus trap/return, portals, collision handling, typeahead, `aria-*` |
| Fuzzy palette list with keyboard nav | custom filter + roving focus | `cmdk` via shadcn `command` | Scoring, grouping, `aria-activedescendant`, scroll-into-view |
| Toast queue, stacking, live region | inline banners / custom portal | `sonner` | Announces politely, stacks, swipe/timeout, promise toasts |
| Sorting/filtering/visibility state | ad-hoc `useState` sorts per table | TanStack Table v9 | Multi-sort, typed column defs, consistent API across 15 tables in 23.1 |
| SVG charts + theming | hand-drawn SVG bars | Recharts via shadcn `chart` | Axes, tooltips, `accessibilityLayer`, `--chart-*` token theming |
| Class merging | string concatenation / custom dedupe | `cn` (shadcn's package) | Tailwind conflict resolution (`px-2` vs `px-4`) |
| Favicon raster export | a script or a build-time plugin | One-off headless Chrome screenshot + Pillow (both on this machine), committed outputs | Assets change rarely; no build dependency needed |

**Key insight:** every interactive primitive in the baseline inventory (9 dialogs, 2 drawers, 3 tab sets, 55 tooltips, ~20 badges) already has a shadcn/Radix equivalent. The foundation's job is to make those available and themed, not to build them.

## Favicon and Asset Pipeline

- **Files in `frontend/public/`** (Vite copies `public/` verbatim to `dist/` root; `maven-resources-plugin` copies `frontend/dist` → `target/classes/static`) [VERIFIED: pom.xml:234-247]:
  - `icon.svg`: the gradient tile + glyph (Pattern 5 SVG with static ids)
  - `favicon.ico`: 32×32 + 16×16
  - `apple-touch-icon.png`: 180×180, opaque (iOS ignores transparency, so fill the tile edge-to-edge or use a background)
- **`index.html`:**
  ```html
  <title>Shipflow · your product roadmap helper</title>
  <link rel="icon" href="/favicon.ico" sizes="32x32">
  <link rel="icon" href="/icon.svg" type="image/svg+xml">
  <link rel="apple-touch-icon" href="/apple-touch-icon.png">
  ```
  [CITED: evilmartians.com "How to Favicon" pattern; ASSUMED it is still current best practice]
- **Serving/security:** `SecurityConfig` permits `GET /**` and `HEAD /**` after the `/api` and `/actuator` rules. `SpaForwardingController` forwards only `^(?!api|actuator|assets)[^.]*` paths, so `/icon.svg` etc. (they contain a dot) go to the static handler [VERIFIED: SecurityConfig.java, SpaForwardingController.java]. No backend change is needed. Optionally add `/icon.svg`, `/favicon.ico` and `/apple-touch-icon.png` cases to `SpaForwardingControllerTest.assetRequestsAreNotForwardedToIndexHtml`.
- **Rasterising (one-off, committed):**
  - Chrome is installed at `C:/Program Files/Google/Chrome/Application/chrome.exe`, Python 3.11.5 with Pillow 11.1.0, and fontTools 4.60.1 [VERIFIED: local probe].
  - `chrome --headless --screenshot=out.png --window-size=180,180 --default-background-color=00000000 icon.svg`, then `Image.save('favicon.ico', sizes=[(16,16),(32,32)])`.
  - ImageMagick/Inkscape are **not** available.
- **README assets (D-23-03; consumed by Phase 24):** put them in `docs/brand/` (not served, keeps the jar lean). GitHub renders SVG in an `<img>` without web fonts, so lockup SVGs with a `<text>` wordmark render in whatever font the viewer has. Outline the wordmark to paths (fontTools with a chosen OFL font), or rely on the PNG for the README.

## Common Pitfalls

### Pitfall 1: shadcn CLI defaults are no longer Radix + clsx
**What goes wrong:** `shadcn init` installs Base UI. Components then import `@base-ui/react` and use `render` instead of `asChild`.
**How to avoid:** Hand-write `components.json` with `"style": "radix-nova"`, or pass `init -b radix`. Check after each `add` that generated files import from `"radix-ui"`.
**Warning signs:** `@base-ui/react` in `package.json`.

### Pitfall 2: `cn@latest` engine mismatch on the Maven Node
**What goes wrong:** `shadcn add` installs `cn@0.5.0` (`engines.node >=22`). The Maven build's Node is v20.19.0, so `npm ci` prints `EBADENGINE`. It is a warning, not a failure, but it is noise, and a future `engine-strict` would break the build.
**How to avoid:** Install `cn@0.4.0 --save-exact` before running `add`, then re-check `package.json`.

### Pitfall 3: Dark mode on not-yet-migrated pages (the 23 → 23.1 window)
**What goes wrong:** `body { bg-background text-foreground }` makes inherited text near-white in dark mode. Unmigrated pages keep `bg-white` cards (71 uses), so any text without an explicit `text-gray-*` class becomes invisible. Pastel `*-50` pills glare.
**Why:** D-23-05 keeps raw classes until 23.1. Success criterion 2 only requires the shell, login and new components to be legible.
**How to avoid:** Accept it within the phase pair: 23.1 directly follows 23, and nothing is published in between (Phase 24 is later). Note it in the verification checklist so a reviewer does not file it as a 23 defect. Do **not** add a feature flag (CLAUDE.md). `color-scheme` on `:root`/`.dark` keeps native controls coherent in the meantime.
**Warning signs:** a UAT step "toggle dark, open Settings" failing in 23. That step belongs to 23.1.

### Pitfall 4: The rendered-document preview must stay "paper"
**What goes wrong:** `RenderedDocument.tsx` / `TemplatePreview.tsx` use `prose prose-gray` + `bg-white`. In dark mode, inherited `text-foreground` and any future `dark:` utilities would theme a document that D-23-06 says is never themed.
**How to avoid:** Keep the preview on raw light classes (no token classes, no `dark:`). Give its root `bg-white text-gray-900 [color-scheme:light]` and never `prose-invert`. With `@custom-variant dark (&:is(.dark *))`, any `dark:` utility inside the preview *will* match, so the rule is "no `dark:` inside the paper".
**Warning signs:** grey-on-dark text in the document viewer after toggling.

### Pitfall 5: jsdom lacks APIs that Radix, cmdk, Recharts and Sonner touch
**What goes wrong:** jsdom 26 has no `ResizeObserver` (Radix `useSize` in Switch/Checkbox, floating-ui autoUpdate in Popover/Dropdown/Tooltip, Recharts `ResponsiveContainer`), no `matchMedia` (ThemeProvider, Sonner `theme="system"`), no `PointerEvent` and no `Element.prototype.hasPointerCapture` / `releasePointerCapture` / `scrollIntoView` [VERIFIED: no PointerEvent impl in `node_modules/jsdom/lib/jsdom/living/events`; existing tests stub `scrollIntoView` per-file].
**How to avoid:** Add `tests/setup.ts` (via `test.setupFiles`) that defines these **only if missing**, by direct assignment, not `vi.stubGlobal`, because 9+ tests call `vi.unstubAllGlobals()`. Defaults: `matchMedia` → `matches: false` (light, motion allowed); `ResizeObserver` → no-op class; pointer-capture stubs → no-ops; `scrollIntoView` → no-op (tests that assign their own spy still win).
- Open Radix menus in tests with keyboard (`keydown` Enter/ArrowDown on the trigger), or dispatch `new MouseEvent('pointerdown', { bubbles: true, button: 0 })`, because Radix DropdownMenu opens on pointerdown, not click [ASSUMED: Radix trigger handler; MEDIUM].
- Note: `RoadmapTable.tsx:87` reads `matchMedia?.(…)`. With the polyfill it takes the "smooth" branch in tests; no current test asserts that branch's argument [VERIFIED: grep].
**Warning signs:** `ResizeObserver is not defined`, or `window.matchMedia is not a function`, in previously green suites.

### Pitfall 6: Stale `vite.config.js` shadows `vite.config.ts` for Vite (not Vitest)
**What goes wrong:** `tsc -b` emits `frontend/vite.config.js` (gitignored; `tsconfig.node.json` is `composite` without `noEmit`). Vite's config lookup order is `vite.config.js` **before** `.ts` [VERIFIED: `node_modules/vite/dist/node/chunks/node.js` DEFAULT_CONFIG_FILES]. Vitest prefers `.ts` [VERIFIED: vitest constants]. So a bare `npx vite build` after editing `vite.config.ts` uses the old alias-less config.
**How to avoid:** Always build with `npm run build` (`tsc -b` regenerates the `.js` first), which is what Maven runs. Optionally delete the stale `vite.config.js` after editing.

### Pitfall 7: `@layer base` universal rules change unmigrated pages slightly
**What goes wrong:** `* { @apply border-border outline-ring/50 }` changes (a) the colour of any bare `border` without a colour class (Tailwind v4 default is `currentColor`) and (b) the colour of the UA focus outline that 37 unmigrated files rely on (baseline Pillar 6). `ring/50` is half-transparent, which could weaken focus visibility on unmigrated controls.
**How to avoid:** Keep the official rule (consistency), but set the `--ring` value to meet 3:1 non-text contrast at /50 in both themes (UI-SPEC). Alternatively, scope the outline part to `:focus-visible` only. Verify by tabbing through the Releases and Settings pages. [ASSUMED: browser treatment of `outline-color` with UA `outline-style: auto` varies; MEDIUM]

### Pitfall 8: Main bundle growth
**What goes wrong:** The main chunk is already 740 KB raw / 219 KB gzipped (above Vite's 500 KB warning) [VERIFIED: `dist/assets/index-*.js`]. Adding Recharts eagerly adds about 137 KB gzipped. TanStack Table v9 is about 32 KB, cmdk about 17 KB, Sonner about 9 KB gzipped [VERIFIED: bundlephobia API].
**How to avoid:** Lazy-load only the chart (Pattern 9). Table, cmdk, Sonner and Radix stay in the main chunk (used by the shell and lists). Record before/after chunk sizes in the plan's verification step. Do not raise `chunkSizeWarningLimit` to hide it.

### Pitfall 9: AppShell tests and the layout move
**What goes wrong:** `tests/components/AppShell.test.tsx` mocks `@tanstack/react-router` with only `Link` and `Outlet`. New shell code that calls `useNavigate`, `useRouter` or `useRouterState` (e.g. the palette) crashes those tests.
**How to avoid:** Extend the mock in that file with the hooks the shell uses (e.g. `useNavigate: () => vi.fn()`), or keep router calls inside `CommandPalette` and mock that module in the AppShell test (the file already mocks `CredentialAlert`/`AdoCredentialBanner` the same way). The nav-order assertions (`nav a` labels) must keep passing, so keep link text plain (icons `aria-hidden`).

## Code Examples

### tests/setup.ts (jsdom polyfills, additive only)
```ts
// Source: synthesized from jsdom gaps verified above; assignments only when absent so per-test spies still win
if (!window.matchMedia) {
  window.matchMedia = (query: string) => ({
    matches: false, media: query, onchange: null,
    addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {}, dispatchEvent: () => false,
  }) as MediaQueryList;
}
if (!('ResizeObserver' in globalThis)) {
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} } as unknown as typeof ResizeObserver;
}
Element.prototype.scrollIntoView ??= function scrollIntoView() {};
Element.prototype.hasPointerCapture ??= () => false;
Element.prototype.releasePointerCapture ??= () => {};
```

### Toast from an existing mutation (pattern for 23.1; one proof in 23)
```tsx
// Source: sonner README API (toast.success / toast.error)
import { toast } from 'sonner';
mutation.mutate(vars, { onSuccess: () => toast.success('Azure DevOps settings saved.') });
```
If the planner uses Settings as the toast proof, update `SettingsRoute.test.tsx:111`. It asserts the inline text `'Azure DevOps settings saved.'` in `container`; a Toaster renders in a portal outside `container`, or not at all if unmounted.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| shadcn on Radix by default | Base UI default; Radix via `-b radix` / `radix-*` styles | 2026-07-02 | Must opt into Radix explicitly |
| `lib/utils.ts` with `clsx` + `tailwind-merge` | `cn` package; `lib/utils.ts` = `export { cn } from "cn"` | 2026-09-03 | One dependency; mind its Node engine |
| TanStack Table v8 `useReactTable` + `get*RowModel()` | v9 `useTable` + `tableFeatures({...})`, `create*RowModel()`, `table.FlexRender` | v9.0.0 2026-08-04 | Use v9 APIs from the start; v8 tutorials are stale |
| `tailwind.config.js` + `darkMode: 'class'` | Tailwind v4 CSS-first: `@custom-variant dark (&:is(.dark *))`, `@theme inline` | Tailwind v4 | No JS config file |
| TS `baseUrl` + `paths` | `paths` only (relative to tsconfig) | TS 6.0 deprecated, 7.0 removed | Repo is on TS 7.0.2 |
| shadcn `toast` (Radix) | Sonner (Radix) / new Base-UI Toast | — | Sonner for this repo |

**Deprecated/outdated:** `next-themes` for non-Next SPAs (unneeded); the shadcn `default` style (use `new-york`/`radix-*`); Create React App.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Radix Presence unmounts correctly with the 0.01 ms reduced-motion rule | Pattern 6 | Dialog/popover exit might hang under reduced motion. Detect with a manual check using OS reduced-motion on; fall back to `motion-reduce:animate-none` on content classes |
| A2 | Radix DropdownMenu opens on `pointerdown` (not `click`) in tests | Pitfall 5 | Test helpers need adjusting; low impact |
| A3 | The `outline-ring/50` universal rule weakens UA focus outlines on unmigrated controls | Pitfall 7 | Either no issue, or a visible focus regression; verify by keyboard walk-through |
| A4 | The favicon trio (ico 32 + svg + apple-touch 180) is still current best practice | Asset pipeline | Missing an icon on some platform; trivial to add |
| A5 | `shadcn add` re-pins `cn` to latest even when a version is installed | Install | Only a `package.json` diff to fix; re-check after `add` |
| A6 | Recharts 3.8.0 (registry pin) is the right choice over 3.10.1 | Standard Stack | Small; the planner may upgrade after checking `chart.tsx` types |

## Open Questions

1. **Which `radix-*` preset (nova/vega/maia/…) and brand token values?**
   - What we know: the UI-SPEC (in progress) owns visual values. `style` cannot be changed after init.
   - Recommendation: the planner reads `23-UI-SPEC.md` before the shadcn task. If it is silent, use `radix-nova`.
2. **Product palette target:** the dashboard filter (`/?products=id`) or `/portfolio?tab=products`?
   - Recommendation: the UI-SPEC decides. Both exist today, and no new route is needed.
3. **Which page proves toasts in 23?**
   - Options: Settings success messages (also fixes the AA-failing `text-green-600`; one test assertion changes), or the shell's sign-out/theme actions only.
   - Recommendation: Settings' two success messages, since that is small and visible.
4. **Wordmark font fidelity** (system `ui-rounded` stack vs self-hosted Nunito vs outlined paths)
   - Recommendation: the UI-SPEC decides. Outlined paths in the `docs/brand` SVGs either way.
5. **Flaky test:** one of 847 failed once and passed on rerun (not identified).
   - Recommendation: the executor should note the failing test name if it recurs, and not attribute it to this phase blindly.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node (local) | npm install, vitest, vite | ✓ | v20.19.0 | — |
| Node (Maven build) | frontend-maven-plugin | ✓ (downloaded by the plugin) | v20.19.0 | — (pin `cn@0.4.0`) |
| npm | installs | ✓ | 10.8.2 | — |
| `frontend/node_modules` | tests/build | ✓ (present) | vite 8.1.5, vitest 4.1.10, jsdom 26.1.0, tailwind 4.3.3 | — |
| npm registry / ui.shadcn.com registry | `npm install`, `shadcn add` | ✓ (dry-run succeeded) | — | — |
| Chrome (headless) | PNG/ICO rasterisation | ✓ | `C:/Program Files/Google/Chrome/Application/chrome.exe` | Edge (`msedge.exe`) also present |
| Python + Pillow | ICO packing | ✓ | 3.11.5 / Pillow 11.1.0 (`py`) | — |
| fontTools | outlining the wordmark for brand SVGs | ✓ | 4.60.1 | PNG only for the README |
| ImageMagick / Inkscape / rsvg | — | ✗ | — | Chrome + Pillow (above) |
| JDK 25 | `mvn` verification of jar packaging | ✓ per memory (`C:/Users/ucs/.jdks/jdk-25`) | — | — |

**Missing dependencies with no fallback:** none.
**Missing with fallback:** ImageMagick/Inkscape (use headless Chrome + Pillow).

## Validation Architecture

(`workflow.nyquist_validation` is `false` in `.planning/config.json`. This section is included because the orchestrator asked for it.)

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 4.1.10 + jsdom 26.1.0; raw `react-dom/client` + `act` (no Testing Library) |
| Config file | `frontend/vite.config.ts` (`test` block); add `setupFiles: ['./tests/setup.ts']` |
| Quick run command | `cd frontend && npx vitest run tests/components/AppShell.test.tsx` (any single file, about 2–4 s) |
| Full suite command | `cd frontend && npm test` (847 tests, about 13 s) + `npm run typecheck` + `npm run build` |
| Packaging check | `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25 mvn -q -DskipTests package`, then confirm `target/classes/static/icon.svg` exists (stop any Vite/Vitest first) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UI-01 | Header shows mark + "shipflow" wordmark; login renders logo + tagline without nav; `index.html` title + icon links | unit + static | `npx vitest run tests/components/AppShell.test.tsx tests/routes/LoginRoute.test.tsx`; `grep -q 'Shipflow · your product roadmap helper' frontend/index.html` | AppShell ✅ / LoginRoute ❌ Wave 0 |
| UI-01 | Favicons served, not SPA-forwarded | backend unit | `mvn -Dtest=SpaForwardingControllerTest test` (add `/icon.svg`, `/favicon.ico` cases) | ✅ (extend) |
| UI-02 | ThemeProvider: stored value respected, invalid/blocked storage → system, OS change followed while on system, toggle persists | unit | `npx vitest run tests/components/ThemeProvider.test.tsx` | ❌ Wave 0 |
| UI-03 | Token CSS present; shadcn components import `radix-ui` (not `@base-ui`) | static/build | `npm run build` + `grep -L "@base-ui" src/components/ui/*.tsx` | n/a |
| UI-04 | Toast shown on a success path; Skeleton while pending; reduced-motion CSS rule present | unit + static | `npx vitest run tests/components/SettingsRoute.test.tsx` (if chosen as proof) | ✅ (update one assertion) |
| UI-05 | Ctrl+K and Meta+K open the palette; groups list releases/clients/products/roadmap items from mocked fetch; selecting navigates | unit | `npx vitest run tests/components/CommandPalette.test.tsx` | ❌ Wave 0 |
| UI-06 | DataTable sorts on header activation, hides a column via column choice; `/releases` still lists rows + readiness blanking (T-05-60) | unit | `npx vitest run tests/components/DataTable.test.tsx tests/components/ReleasesRoute.test.tsx` | DataTable ❌ / ReleasesRoute ✅ |
| UI-06 | Chart renders lazily on the dashboard; text list still present | unit | `npx vitest run tests/components/DashboardRoute.test.tsx` | ✅ (needs ResizeObserver polyfill) |
| all | Visual legibility in light/dark, keyboard focus, reduced motion | manual | UAT walk-through (shell, login, palette, `/releases`, dashboard tile) in both themes and with OS reduced motion | manual-only (visual) |

### Sampling Rate
- **Per task commit:** the touched test file(s) via `npx vitest run <file>`
- **Per wave merge:** `npm test && npm run typecheck`
- **Phase gate:** `npm test && npm run build` (record the main + chart chunk sizes) + Maven package check, before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `frontend/tests/setup.ts`: jsdom polyfills (`matchMedia`, `ResizeObserver`, pointer capture, `scrollIntoView`) + `setupFiles` in `vite.config.ts`
- [ ] `frontend/tests/components/ThemeProvider.test.tsx`: UI-02
- [ ] `frontend/tests/routes/LoginRoute.test.tsx`: UI-01 branded layout + no "Go to Settings" link
- [ ] `frontend/tests/components/CommandPalette.test.tsx`: UI-05
- [ ] `frontend/tests/components/DataTable.test.tsx`: UI-06
- [ ] Extend the `tests/components/AppShell.test.tsx` router mock for any new router hooks

## Security Domain

### Applicable ASVS Categories (Level 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (unchanged; login only restyled) | existing `/api/auth/login`; keep `autoComplete` attributes and `type="password"` |
| V3 Session Management | no (unchanged) | cookie session; sign-out still calls `useLogout` then `window.location.assign('/login')` |
| V4 Access Control | yes (route gating moves) | `authenticatedRoute.beforeLoad` unchanged. Moving `AppShell` onto it means no nav leaks to signed-out users (fixes baseline fix 5). Server-side `/api/**` auth is the real control |
| V5 Input Validation | yes | Theme value from `localStorage` accepted only as `'light'`/`'dark'` (anything else → system). Palette navigates with typed router params from API data, never builds URLs from free text. `?item=` is already UUID-validated (`parseRoadmapSearch`) |
| V6 Cryptography | no | — |
| V14 Configuration | yes | Static assets served via the existing `GET /**` permit. No new public endpoints. No CSP today; see the threat table |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| CSS injection via shadcn `ChartStyle` (`dangerouslySetInnerHTML` builds `--color-${key}: ${color}` from `ChartConfig`) [VERIFIED: registry chart.tsx] | Tampering | `ChartConfig` keys and colours are **static developer constants** (e.g. `{ releases: { label: 'Releases', color: 'var(--chart-1)' } }`). Never use stage/product/client names (user-entered) as keys or colours; pass per-row colours via data `fill` |
| XSS through palette/table cells rendering release, client and roadmap titles | Tampering | React text rendering only; no `dangerouslySetInnerHTML` in new components; cmdk `value`/`keywords` are plain strings |
| Inline script in `index.html` vs a future CSP (Phase 24 hosted) | Elevation | Keep the no-flash script tiny and static. If a CSP is added later, allow it with a `sha256-` hash. Record this as a note for Phase 24 |
| Open redirect after login | Spoofing | Unchanged: `safeRedirectTarget` stays on the login success path |
| Clickjacking / spoofed branding | Spoofing | Out of scope (no header changes in this phase) |

## Sources

### Primary (HIGH confidence)
- npm registry (`npm view … version / time / peerDependencies / engines / dependencies`), 2026-10-26: radix-ui, cn, sonner, cmdk, @tanstack/react-table, recharts, lucide-react, tailwind-merge, tw-animate-css, next-themes, shadcn, react-is, @testing-library/*
- shadcn/ui source (github.com/shadcn-ui/ui `apps/v4`): `content/docs/installation/manual.mdx`, `dark-mode/vite.mdx`, `(root)/cli.mdx`, `(root)/components-json.mdx`, changelog `2026-07-base-ui-default.mdx`, `2026-07-toast.mdx`, `2026-09-cn.mdx`; `registry/bases.ts`; `registry/bases/radix/ui/{sonner,command,chart}.tsx`, `_registry.ts`
- https://ui.shadcn.com/docs/components/data-table (TanStack Table v9 guide)
- https://ui.shadcn.com/docs/installation/vite
- `npx shadcn@4.21.4 add … --dry-run [--view]` against a scratch copy of this repo's config with `radix-nova`
- Repo files: `frontend/package.json`, `vite.config.ts`, `tsconfig*.json`, `index.html`, `src/router.tsx`, `src/components/AppShell.tsx`, `src/routes/{LoginRoute,ReleasesRoute,DashboardRoute}.tsx`, `src/api/{releases,clients,products,roadmap,auth}.ts`, `pom.xml`, `SecurityConfig.java`, `SpaForwardingController.java`, `SpaForwardingControllerTest.java`, `docs/database.md`, `docs/docker.md`; `node_modules/vite` and `node_modules/vitest` config-file constants; baseline `npm test` run (847 passed)

### Secondary (MEDIUM confidence)
- https://tanstack.com/table/v9/docs/framework/react/guide/migrating (via search summary): `useTable`, `tableFeatures`, `useLegacyTable`
- https://tanstack.com/router/latest/docs/framework/react/guide/code-splitting (`createLazyRoute` / `.lazy()`)
- https://vite.dev/guide/features.md (`resolve.tsconfigPaths` performance note)
- TypeScript 7 `baseUrl` removal: https://github.com/microsoft/TypeScript/pull/62389, https://blog.openreplay.com/upgrade-typescript-7/, https://github.com/shadcn-ui/ui/discussions/11017
- bundlephobia API sizes (recharts 3.8.0, @tanstack/react-table 9.3.1, cmdk 1.1.1, sonner 2.0.8)

### Tertiary (LOW confidence)
- Favicon trio convention (Evil Martians "How to Favicon"), from training knowledge and not re-fetched

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. Registry-verified versions, dates, peers and engines, plus a CLI dry-run on this repo's config.
- Architecture: HIGH. The router/layout change and asset serving were verified against the repo code and backend config.
- Pitfalls: MEDIUM-HIGH. The jsdom gaps and config shadowing were verified locally. The focus-outline cascade and the Presence reduced-motion behaviour are assumptions (A1, A3).

**Research date:** 2026-10-26
**Valid until:** about 2026-11-09 (14 days). shadcn and lucide ship weekly; re-check the `cn` engines and the shadcn CLI version at execution time.
