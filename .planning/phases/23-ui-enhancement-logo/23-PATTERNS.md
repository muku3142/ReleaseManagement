# Phase 23: UI Enhancement & Logo (Foundation) - Pattern Map

**Mapped:** 2026-10-26
**Files analyzed:** 38 (new + modified, excluding the ~21 generated `components/ui/*` files, which are counted as one group)
**Analogs found:** 27 / 38. The rest are greenfield: there is no shadcn, context provider, lazy import, chart or `localStorage` use anywhere in `frontend/src` today.

All paths are relative to `frontend/` unless they start with `src/main`, `src/test` or `docs/`.

---

## Read first: UI-SPEC vs RESEARCH conflicts the planner must settle

The UI-SPEC is the approved contract and wins on visuals, copy and file layout. Several **package-level** points disagree with RESEARCH, which verified them against the registry and a CLI dry-run. Pick one per row before writing the shadcn task.

| Topic | UI-SPEC says | RESEARCH says | Note |
|---|---|---|---|
| `components.json` `style` | `"new-york"`, `baseColor: "zinc"` | `"radix-nova"` (`radix-` prefix is what selects Radix; Base UI is now the default) | RESEARCH Pitfall 1: a non-`radix-*` style may pull `@base-ui/react`. Verify after the first `add` with `grep -L "@base-ui" src/components/ui/*.tsx`. |
| Class merge | `clsx` 2.1.1 + `tailwind-merge` 3.7.0 | `cn@0.4.0` package; `lib/utils.ts` = `export { cn } from "cn"` | Generated files import whichever the registry emits. `cn@0.5.0` needs Node 22, Maven runs Node 20.19.0. |
| Versions | radix-ui 1.7.0, react-table 9.2.8, recharts 3.10.1 | radix-ui 1.8.1, react-table 9.3.1, recharts 3.8.0 (registry pin for `chart.tsx`), plus `react-is@19.2.8` peer | Repo pins exact versions (`"react": "19.2.8"`), so `--save-exact`. |
| Fonts | `@fontsource-variable/inter` + `/nunito` 5.3.0 | not in base list | UI-SPEC wins (typography contract). |
| `shadcn` CLI | `npx`, not installed | devDependency (its `tailwind.css` is `@import`ed by `index.css`) | If `index.css` imports `shadcn/tailwind.css`, it must be installed. |
| Favicon file name | `public/favicon.svg` | `public/icon.svg` | UI-SPEC wins (exact head block given). |
| Vite alias | `path.resolve(__dirname, './src')` | `fileURLToPath(new URL('./src', import.meta.url))` | package is `"type": "module"`; `__dirname` is undefined in ESM config unless Vite shims it. RESEARCH form is safe. |
| Nav | Sidebar (`sidebar` component, `layout/AppSidebar.tsx`...) | Header nav in `AppShell` | UI-SPEC wins. |
| Brand assets | `frontend/public/brand/*` | `docs/brand/*` | UI-SPEC wins; mind jar size. |
| App component folders | `brand/`, `theme/`, `layout/`, `data-table/` subfolders | flat PascalCase `src/components/` | UI-SPEC wins; existing `src/components/` is flat today (see `ls src/components`). |
| `dark` custom variant | `&:where(.dark, .dark *):not(.paper, .paper *)` + `.paper` island | `&:is(.dark *)` | UI-SPEC wins (paper island). |

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `components.json` (new) | config | static | none (RESEARCH Pattern 1 / UI-SPEC "components.json (exact)") | no analog |
| `package.json` (mod) | config | static | itself (exact-pin convention) | exact |
| `tsconfig.json` (mod, `paths`) | config | static | itself | exact |
| `vite.config.ts` (mod, alias + `setupFiles`) | config | static | itself | exact |
| `index.html` (mod: title, favicons, theme-color, boot script) | config | static | itself | exact |
| `public/favicon.svg`, `favicon.ico`, `apple-touch-icon.png`, `public/brand/*` (new) | static asset | file-I/O | none (no `public/` yet) | no analog |
| `src/index.css` (mod: imports, tokens, `.paper`, base, reduced motion) | config (CSS) | static | itself (2 lines) | exact |
| `src/lib/utils.ts` (new) | utility | transform | none | no analog |
| `src/components/ui/*.tsx` (new, generated + 4 owned edits) | component | presentational | shadcn registry output | no analog |
| `src/components/ui/sonner.tsx` (generated, edit import) | component | presentational | RESEARCH Pattern 4 | no analog |
| `src/components/brand/ShipflowMark.tsx` (new) | component | presentational | `src/components/AiResultCard.tsx:190` (`useId`) | partial |
| `src/components/brand/ShipflowLogo.tsx` (new) | component | presentational | `src/components/DashboardTiles.tsx` (`Tile`, small prop-driven presentational) | partial |
| `src/components/theme/ThemeProvider.tsx` + `useTheme` (new) | provider/store | event-driven | `src/api/credentialStatus.ts` (external store + hook) | role-match |
| `src/components/layout/ThemeMenu.tsx` (new) | component | event-driven | `AppShell.tsx` sign-out button + RESEARCH Pattern 2 | partial |
| `src/components/layout/UserMenu.tsx` (new) | component | request-response (mutation) | `src/components/AppShell.tsx:11-16, 23-34` | exact (logic moves) |
| `src/components/layout/AppSidebar.tsx` (new) | component | presentational + query | `src/components/AppShell.tsx:37-103` (nav `Link`s + assistant gate) | exact (logic moves) |
| `src/components/layout/TopBar.tsx` (new) | component | presentational | `src/components/AppShell.tsx:20-36` (header) | role-match |
| `src/components/layout/PageHeader.tsx`, `PageContainer.tsx` (new) | component | presentational | `src/components/DashboardTiles.tsx:3-11` | partial |
| `src/components/AppShell.tsx` (mod: authenticated layout, `.paper` island) | component (layout) | presentational | itself | exact |
| `src/router.tsx` (mod: `RootLayout`, `component: AppShell` on `authenticated`, `staticData.themed`) | route | request-response | itself | exact |
| `src/main.tsx` (mod, if ThemeProvider mounts here) | config | static | itself | exact |
| `src/routes/LoginRoute.tsx` (mod: standalone branded layout, redirect if signed in, no Settings link) | route/page | request-response | itself | exact |
| `src/components/CommandPalette.tsx` + `useGoShortcuts` (new) | component | event-driven + query | `src/components/PlaceholderDrawer.tsx:90-107` (document keydown) + `AppShell.tsx` (hooks) | role-match |
| `src/api/releases.ts`, `clients.ts`, `products.ts`, `roadmap.ts` (mod: optional `enabled`) | service (query hook) | request-response | `src/api/assistant.ts:129-131`, `roadmap.ts:112-118` (hooks that already take `enabled`) | exact |
| `src/components/data-table/DataTable.tsx` + `DataTableToolbar`, `DataTableColumnHeader`, `DataTableFacetedFilter`, `DataTableViewOptions` (new) | component | transform (client sort/filter) | `src/routes/ReleasesRoute.tsx:93-185` (table markup + states) | role-match |
| `src/components/data-table/features.ts` (new, `tableFeatures`) | utility | static | none (RESEARCH Pattern 8) | no analog |
| `src/routes/ReleasesRoute.tsx` (mod: proof page, tokens, DataTable, PageHeader, Skeleton, Empty) | route/page | request-response | itself | exact |
| `src/components/ReadinessPill.tsx` (mod: Badge `asChild`, "Out of date") | component | presentational | itself | exact |
| `src/components/StageChart.tsx` (new, default export, lazy) | component | transform | `src/routes/DashboardRoute.tsx:137-150` (data source) | partial |
| `src/hooks/usePrefersReducedMotion.ts` (new) | hook | event-driven | `src/roadmap/RoadmapTable.tsx:87` (`matchMedia?.(...)` read) | partial |
| `src/routes/DashboardRoute.tsx` (mod: tile only) | route/page | request-response | itself | exact |
| `src/routes/SettingsRoute.tsx` (mod: 3 success messages to toast) | route/page | request-response (mutation) | itself | exact |
| `src/components/CredentialAlert.tsx`, `AdoCredentialBanner.tsx` (mod: `<Alert variant="warning">`) | component | presentational | itself | exact |
| `src/components/RenderedDocument.tsx`, `TemplatePreview.tsx` (mod: `.paper` root) | component | presentational | itself | exact |
| `tests/setup.ts` (new) | test config | static | per-file stubs in `tests/components/ReleaseDetailRoute.test.tsx:596`, `DashboardPortfolioTab.test.tsx:158` | role-match |
| `tests/components/ThemeProvider.test.tsx`, `CommandPalette.test.tsx`, `DataTable.test.tsx`, `tests/routes/LoginRoute.test.tsx` (new) | test | — | `tests/components/AppShell.test.tsx`, `ReleasesRoute.test.tsx`, `tests/routes/RoadmapRoute.test.tsx` | exact |
| `tests/components/AppShell.test.tsx`, `ReleasesRoute.test.tsx`, `ReadinessPill.test.tsx`, `SettingsRoute.test.tsx`, `DashboardRoute.test.tsx` (mod) | test | — | themselves | exact |
| `src/test/java/.../config/SpaForwardingControllerTest.java` (mod, optional) | test (backend) | request-response | itself, lines 45-49 | exact |

---

## Pattern Assignments

### `src/router.tsx` (route, request-response)

**Analog:** itself.

Current root, which mounts the shell for every route including `/login` (line 23):
```tsx
const rootRoute = createRootRoute({ component: AppShell });
```

Pathless authenticated layout to receive `component: AppShell` (lines 34-45). Keep `id: 'authenticated'` so the `useSearch({ from: '/authenticated/...' })` calls stay valid:
```tsx
// Pathless layout route: every route nested under this one requires a signed-in
// session, checked once via the shared query cache before any child route renders.
const authenticatedRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: 'authenticated',
  beforeLoad: async ({ location }) => {
    const session = await fetchSessionOrNull(queryClient);
    if (!session) {
      throw redirect({ to: '/login', search: { redirect: location.href } });
    }
  },
});
```

Per-route options, where `staticData: { themed: true }` goes on `releasesRoute` only (lines 66-70):
```tsx
const releasesRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/releases',
  component: ReleasesRoute,
});
```
`staticData` typing needs a `StaticDataRouteOption` augmentation next to the existing `Register` block (lines 164-168):
```tsx
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
```

Search-param shapes the palette navigates into (do not invent new ones): `portfolioRoute` `{ tab, client }` (lines 85-93), `roadmapRoute` `parseRoadmapSearch` (UUID-validated `item`), `dashboardRoute` `parseDashboardSearch` (`products`).

`RootLayout` is new and is a few lines in `router.tsx` (or its own file): `<><Outlet /><Toaster /></>` plus `TooltipProvider` per UI-SPEC "Router change".

---

### `src/components/AppShell.tsx` (layout component)

**Analog:** itself. Split into `layout/AppSidebar`, `TopBar`, `UserMenu`, `ThemeMenu`.

Imports + hook block to keep (lines 1-16). After the move, `session.data` is guaranteed by `beforeLoad`, so the `session.data &&` guard (line 23) goes per UI-SPEC:
```tsx
import { Link, Outlet } from '@tanstack/react-router';
import { useAssistantStatus } from '../api/assistant';
import { useSession, useLogout } from '../api/auth';
import { AdoCredentialBanner } from './AdoCredentialBanner';
import { CredentialAlert } from './CredentialAlert';

export function AppShell() {
  const session = useSession();
  const logout = useLogout();
  const assistant = useAssistantStatus(!!session.data);

  function handleSignOut() {
    logout.mutate(undefined, {
      onSuccess: () => window.location.assign('/login'),
    });
  }
```
Move `handleSignOut` verbatim into `UserMenu` (and the palette's "Sign out" action). `logout.isPending` drives "Signing out…".

Nav link pattern, which becomes `SidebarMenuButton asChild` + `Link` (lines 38-44). `activeProps` stays the active-state mechanism; Dashboard needs `activeOptions={{ exact: true }}`:
```tsx
<Link
  to="/"
  className="text-gray-600 hover:text-gray-900"
  activeProps={{ className: 'font-semibold text-gray-900' }}
>
  Dashboard
</Link>
```
Assistant gate (lines 80-88): `{assistant.data?.enabled === true && (<Link to="/assistant" ...>Assistant</Link>)}`. Shared by `AppSidebar` and the palette's "Go to" group.

Banners + outlet (lines 105-109), which become `<main id="content" tabIndex={-1} className={cn('p-6 lg:p-8', !themed && 'paper')}>`:
```tsx
<CredentialAlert />
<AdoCredentialBanner />
<main className="p-6">
  <Outlet />
</main>
```
The `themed` flag is read with `useMatches()` / `useRouterState` (last match's `staticData.themed`). That is a new router hook in the shell, so see the AppShell test note under Tests.

---

### `src/routes/LoginRoute.tsx` (page, request-response)

**Analog:** itself. Keep all logic and change only layout, components and copy.

Logic to keep (lines 1-30):
```tsx
import { useState } from 'react';
import { useLogin } from '../api/auth';
import { messageForReason } from '../api/reasonMessages';
import { useJiraSite } from '../api/jiraSite';
import { ApiError } from '../api/client';
import { safeRedirectTarget } from '../safeRedirect';
...
const siteNotConfigured =
  jiraSite.isError && jiraSite.error instanceof ApiError && jiraSite.error.problem.reason === 'SITE_NOT_CONFIGURED';

function handleSubmit(event: React.FormEvent) {
  event.preventDefault();
  login.mutate(
    { email, apiToken },
    {
      onSuccess: () => {
        const search = new URLSearchParams(window.location.search);
        window.location.assign(safeRedirectTarget(search.get('redirect')));
      },
    },
  );
}
const problem = login.error instanceof ApiError ? login.error.problem : undefined;
```

To replace: the Settings dead-end (lines 34-41). It becomes a warning `Alert` with the UI-SPEC copy and no link. `reasonMessages.ts:9` is shared with `SettingsRoute.tsx:25`, so override locally:
```tsx
{siteNotConfigured && jiraSite.error instanceof ApiError && (
  <p role="alert" data-reason={jiraSite.error.problem.reason} className="mt-2 text-sm text-red-600">
    {messageForReason(jiraSite.error.problem)}{' '}
    <a href="/settings" className="underline">Go to Settings</a>
  </p>
)}
```
Field attributes to keep exactly (lines 49-58, 64-73): `id="email" name="email" type="email" required autoComplete="username"` and `id="apiToken" name="apiToken" type="password" required autoComplete="off"`. The error `<p role="alert" data-reason={problem.reason}>` (lines 93-97) becomes `<Alert variant="destructive" role="alert" data-reason=... tabIndex={-1}>`.

New: a signed-in redirect via `useSession()` (`src/api/auth.ts:17-23`) + `safeRedirectTarget`.

---

### `src/components/CommandPalette.tsx` + `useGoShortcuts` (event-driven + query)

**Analog (global key listener):** `src/components/PlaceholderDrawer.tsx:90-107`:
```tsx
useEffect(() => {
  if (!open) return;
  function onKeyDown(e: KeyboardEvent) {
    if (e.key === 'Escape') onClose();
  }
  ...
  document.addEventListener('keydown', onKeyDown);
  document.addEventListener('mousedown', onMouseDown);
  return () => {
    document.removeEventListener('keydown', onKeyDown);
    document.removeEventListener('mousedown', onMouseDown);
  };
}, [open, onClose]);
```
For Ctrl/Cmd+K, use the same shape with no `open` guard (RESEARCH Pattern 7). Dialog Escape/focus-return comes from Radix, so do not hand-roll it.

**Data sources (existing hooks):**
- `useReleases('')`: `src/api/releases.ts:127-132`, key `['releases', '']`, response `{ releases: ReleaseSummary[] }` (`id, name, version, status, jiraProjectKey`).
- `useClients()`: `src/api/clients.ts:54-59`, key `['clients']`, `{ clients: Client[] }` (`id, name, archived`).
- `useProducts()`: `src/api/products.ts:17-22`, key `['products']`, `{ products: Product[] }` (`id, name, archived`).
- `useRoadmap({ products: [], done: false, archived: false })`: `src/api/roadmap.ts:105-110`, `RoadmapItem` (`id, title, jiraKey, archived`).
- `useAssistantStatus(enabled)`: `src/api/assistant.ts:129-131`.
- `useLogout()`: `src/api/auth.ts:41-50`.

Theme actions call `useTheme().setTheme`. Sidebar actions use shadcn `useSidebar().toggleSidebar`.

---

### `src/api/{releases,clients,products,roadmap}.ts` (query hooks; add `enabled`)

**Analog:** hooks that already accept `enabled`. From `src/api/assistant.ts:129-131`:
```ts
export function useAssistantStatus(enabled: boolean) {
  return useQuery({ ...assistantStatusQueryOptions, enabled });
}
```
and `src/api/roadmap.ts:112-118`:
```ts
export function useRoadmapHistory(itemId: string, enabled: boolean) {
  return useQuery({
    queryKey: roadmapHistoryQueryKey(itemId),
    queryFn: () => apiFetch<RoadmapHistory>(`${ITEMS_URL}/${itemId}/history`),
    enabled,
  });
}
```
Apply as a defaulted trailing parameter so existing callers are untouched, e.g. `export function useClients(enabled = true) { return useQuery({ queryKey: clientsQueryKey, queryFn: ..., enabled }); }`.

---

### `src/components/data-table/*` (shared DataTable, client transform)

**Analog:** `src/routes/ReleasesRoute.tsx:93-185` for markup conventions and states. There is no table abstraction in the repo today. The TanStack v9 API comes from RESEARCH Pattern 8 (`useTable`, `tableFeatures`, `table.FlexRender`, `createColumnHelper<Features, Row>()`); do not copy v8 `useReactTable` examples.

Header-cell semantics to keep (lines 96-100): `<th scope="col" ...>`. Row link convention to keep in the primary cell (lines 129-137):
```tsx
<Link
  to="/releases/$releaseId"
  params={{ releaseId: r.id }}
  className="font-semibold text-blue-600 hover:underline"
>
  {r.name}
</Link>
```
Error state stays owned by the page (lines 56-69): `role="alert" data-reason={problem.reason}` + `messageForReason(problem)` + "Try again" calling `releases.refetch()`.

---

### `src/routes/ReleasesRoute.tsx` (proof page)

**Analog:** itself. Keep these pieces verbatim:

Debounced server search (lines 12-22), which moves into the DataTable `toolbar` slot unchanged:
```tsx
const [search, setSearch] = useState('');
const [query, setQuery] = useState('');
// Local-DB search is cheap, unlike Phase 2's Jira search, so it runs as you type rather than
// behind a Load button. 300ms keeps a fast typist to one request per pause.
useEffect(() => {
  const timer = setTimeout(() => setQuery(search.trim()), 300);
  return () => clearTimeout(timer);
}, [search]);
const releases = useReleases(query);
```
The T-05-60 readiness isolation (lines 24-27, 169-177) must survive into the readiness column `cell` renderers:
```tsx
// DOC-09: independent of the releases query on purpose - a readiness failure must not blank the
// whole release table (T-05-60), so its own isError branch only affects the three new cells below.
const readiness = useDocumentReadiness();
const stateOf = useMemo(() => readinessIndex(readiness.data?.entries), [readiness.data]);
...
{readiness.isError ? (
  <span className="text-gray-400">—</span>
) : (
  <ReadinessPill state={stateOf(r.id, type) ?? 'NOT_GENERATED'} releaseId={r.id} documentType={type} />
)}
```
Empty copy kept verbatim (lines 73-89), inside `<Empty>`. Products cell "max 2 + `+{n}`" logic (lines 139-153). `PRODUCT_CHIP` (`ProductsEditor.tsx:9`) becomes `Badge variant="outline"` here only; the constant stays for unmigrated pages. Status cell (lines 155-161): the `bg-gray-800` Frozen pill becomes `Badge neutral` + `Lock`, and Open becomes `Badge outline`.

The old white header strip (lines 31-44, `bg-white p-6` + `<h2>Releases</h2>`) becomes `PageHeader` with an `<h1>`.

---

### `src/components/ReadinessPill.tsx` (component)

**Analog:** itself. The switch on `state` (lines 16-55) stays, each branch becomes `<Badge variant=... asChild><Link .../></Badge>`, and STALE reads "Out of date". The Link targets are unchanged:
```tsx
case 'UP_TO_DATE':
  return (
    <Link
      to="/releases/$releaseId/documents/$documentType"
      params={{ releaseId, documentType }}
      className="rounded border border-green-700 bg-green-50 px-2 py-0.5 text-xs font-semibold text-green-700"
    >
      Up to date
    </Link>
  );
...
case 'NOT_GENERATED':
  return (<Link to="/releases/$releaseId" params={{ releaseId }} hash="documents" ...>Not generated</Link>);
case 'NO_TEMPLATE':
  return (<Link to="/templates" ...>No template</Link>);
```
It is also used by `DashboardRoute.tsx:23` and `DocumentsSection`, so the change is app-wide by design (UI-SPEC "Proof surfaces").

---

### `src/components/StageChart.tsx` + `src/routes/DashboardRoute.tsx` tile

**Analog (data):** `src/routes/DashboardRoute.tsx:102-103, 137-150`:
```tsx
const { summary, stages } = data;
const perStage = new Map(summary.releasesPerStage.map((s) => [s.stageId, s.count]));
...
<div className="rounded border border-gray-200 bg-white p-4">
  <ul className="text-xs text-gray-700">
    {stages
      .filter((s) => !s.retired || (perStage.get(s.id) ?? 0) > 0)
      .map((s) => (
        <li key={s.id} className="tabular-nums">
          {s.name}
          {s.retired ? ' (retired)' : ''} {perStage.get(s.id) ?? 0}
        </li>
      ))}
    <li className="tabular-nums">Not promoted {summary.notPromoted}</li>
  </ul>
  <div className="mt-1 text-sm text-gray-600">Releases per stage</div>
</div>
```
Keep the `<ul>` as `sr-only` (the test asserts `'QA 1'` and `'Not promoted 0'` through `textContent`). Build the chart rows from the same filter. Pass the rows into `StageChart` as props; `StageChart` is the default export used with `lazy(() => import('../components/StageChart'))` + `<Suspense fallback={<Skeleton className="h-24" />}>`. No `lazy`/`Suspense` exists in the repo yet. `ChartConfig` keys must be static constants and never stage names (RESEARCH Security Domain).

---

### `src/routes/SettingsRoute.tsx` (toast proof)

**Analog:** itself. These are the three success paths to convert. Move the success feedback into `onSuccess`, drop the `saved`/`tokenReplaced` state and the green `<p>`, and keep inline errors.

Jira site (lines 27-31, 108):
```tsx
function handleSubmit(event: React.FormEvent) {
  event.preventDefault();
  setSaved(false);
  updateJiraSite.mutate(baseUrl, { onSuccess: () => setSaved(true) });
}
...
{saved && !updateJiraSite.isError && <p className="text-sm text-green-600">Saved.</p>}
```
Token (lines ~42-50, 143-147): `onSuccess: () => { setApiToken(''); setTokenReplaced(true); }` and `<p role="status" className="text-sm text-green-600">Token replaced.</p>`.
ADO (lines ~190-201, 270-274): `onSuccess: () => { setToken(''); setSaved(true); }` and `<p role="status" ...>Azure DevOps settings saved.</p>`.
Target: `onSuccess: () => { setToken(''); toast.success('Azure DevOps settings saved.'); }` with `import { toast } from 'sonner'`. The Jira-site copy becomes "Jira site saved.".

---

### `src/components/theme/ThemeProvider.tsx` (provider, event-driven)

**Analog:** `src/api/credentialStatus.ts:1-36`, the only app-owned client-state store, as a dependency-free external store with a hook. The repo has **no `createContext`**, so the ThemeProvider is the first context. Follow RESEARCH Pattern 2: give it a default context value so `useTheme()` works in un-wrapped tests, wrap `localStorage` in try/catch, accept only `'light' | 'dark'` (else `system`), and add a `matchMedia` change listener while on `system`.
```ts
export function useCredentialInvalid() {
  return useSyncExternalStore(
    (l) => { listeners.add(l); return () => listeners.delete(l); },
    () => invalid,
    () => false,
  );
}
```
There is no `localStorage` use anywhere in `src/` today, so `shipflow-theme` and `shipflow-sidebar` are the first keys.

---

### `src/hooks/usePrefersReducedMotion.ts`

**Analog:** `src/roadmap/RoadmapTable.tsx:87`:
```ts
const smooth = !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
```
Keep the optional-call `matchMedia?.` defensive style, and subscribe to `change` for live updates.

---

### `src/components/brand/ShipflowMark.tsx` / `ShipflowLogo.tsx`

**Analog for unique ids:** `src/components/AiResultCard.tsx:190`: `const ids = { blocked: useId(), fact: useId(), helper: useId() };`. Use `useId()` for the `linearGradient` id. Exact SVG paths, tile radius and transform are in the UI-SPEC "Mark" table and RESEARCH Pattern 5. Presentational prop style follows `DashboardTiles.tsx:3-11` (`Tile({ caption, children, unit })`).

---

### `src/components/CredentialAlert.tsx` (to `<Alert variant="warning">`)

**Analog:** itself (lines 4-15). Keep `role="alert" data-reason="INVALID_CREDENTIALS"`, the copy and the `Link to="/settings"`; only the container classes change:
```tsx
<div role="alert" data-reason="INVALID_CREDENTIALS" className="border-b border-amber-300 bg-amber-50 px-6 py-3 text-sm text-amber-900">
  Your Jira credential is no longer valid.{' '}
  <Link to="/settings" className="font-medium underline">Replace your Jira API token</Link>
</div>
```
Same treatment for `AdoCredentialBanner.tsx`.

---

### `src/components/RenderedDocument.tsx`, `TemplatePreview.tsx` (`.paper`)

**Analog:** themselves. The prose roots to mark `.paper` are `RenderedDocument.tsx:47` and `TemplatePreview.tsx:227`, both `className="prose prose-gray max-w-none p-4" dangerouslySetInnerHTML=...`. The cover card is at `RenderedDocument.tsx:97` (`bg-white p-8`). No `dark:` utilities and no `prose-invert` inside.

---

### `index.html`, `src/index.css`, `vite.config.ts`, `tsconfig.json`, `package.json` (config)

Current `index.html` head: only `<meta charset>`, `<meta viewport>`, `<title>Release Management</title>`. Replace with the UI-SPEC exact head block + boot script (before the module script).

Current `src/index.css`:
```css
@import "tailwindcss";
@plugin "@tailwindcss/typography";
```

Current `vite.config.ts` test block (keep the comment):
```ts
// Tests live under tests/ rather than beside the modules they cover, so src/ stays
// production-only and the build never has to exclude test files.
test: { environment: 'jsdom', include: ['tests/**/*.test.ts?(x)'] },
```
Add `setupFiles: ['./tests/setup.ts']` and `resolve.alias`.

`tsconfig.json` has `"include": ["src", "tests"]` and no `baseUrl`. Add only `"paths": { "@/*": ["./src/*"] }` (TS 7: no `baseUrl`). `noUnusedLocals`/`noUnusedParameters` are on, so generated shadcn files must typecheck under `strict`.

`package.json`: runtime deps are exact-pinned (`"react": "19.2.8"`), devDeps mostly caret. New runtime deps go in `--save-exact`.

---

### Backend: `src/test/java/com/evision/releasemgmt/config/SpaForwardingControllerTest.java` (optional)

**Analog:** itself, lines 45-49:
```java
void assetRequestsAreNotForwardedToIndexHtml() throws Exception {
    mockMvc.perform(get("/assets/index-BJiZJ4nx.js")).andExpect(forwardedUrl(null));
    mockMvc.perform(get("/assets/index-BiO8dE8o.css")).andExpect(forwardedUrl(null));
    mockMvc.perform(get("/assets/nested/deep/chunk-abc123.js")).andExpect(forwardedUrl(null));
}
```
Add `/favicon.svg`, `/favicon.ico`, `/apple-touch-icon.png` and `/brand/shipflow-mark.svg` lines. No production code change is needed.

---

## Shared Patterns

### Test harness (apply to every new/modified test)
**Source:** `tests/components/AppShell.test.tsx:1-55`, `tests/components/ReleasesRoute.test.tsx:1-100`.
- Raw `react-dom/client` + `act`, no Testing Library. `IS_REACT_ACT_ENVIRONMENT = true` in `beforeAll`. A container is appended to `document.body` in `beforeEach`, and `root.unmount()` + `container.remove()` run in `afterEach`.
- Fetch stubbing: either `vi.stubGlobal('fetch', fetchSpy)` + `vi.unstubAllGlobals()` (AppShell) or `vi.spyOn(globalThis, 'fetch')` + `vi.restoreAllMocks()` (Releases, Settings). Because of `unstubAllGlobals`, `tests/setup.ts` must **assign** polyfills, not `vi.stubGlobal` them.
- A fresh `new QueryClient({ defaultOptions: { queries: { retry: false } } })` per render, wrapped in `<StrictMode>` in route tests.
- A `flush()` helper: `await act(async () => { await new Promise((r) => setTimeout(r, 0)); });`.
- Router mock: `vi.mock('@tanstack/react-router', () => ({ Link: ..., useSearch: () => searchParams, useNavigate: () => navigate }))` (`tests/routes/RoadmapRoute.test.tsx:8-15`). The `Link` mock with `params`/`hash`/`search` substitution is in `tests/components/ReadinessPill.test.tsx:16-31`. **Any router hook the shell, palette or sidebar adds (`useMatches`, `useRouterState`, `useNavigate`, `useRouter`) must be added to the AppShell mock**, or those modules must be mocked out the way the file already mocks `CredentialAlert`/`AdoCredentialBanner` (lines 11-12).
- Sibling-module mock: `vi.mock('../../src/components/CredentialAlert', () => ({ CredentialAlert: () => null }));`.
- Toasts render in a Sonner portal outside `container`. Assert on `document.body` (with a `<Toaster/>` mounted in the test) or mock `sonner`'s `toast` and assert the call.

### Tests that will break and must be updated deliberately
- `AppShell.test.tsx:62-111`: asserts the nav label `'Portfolio'` (now "Products & clients"), the ordering Releases→Portfolio→Roadmap→Briefings→Assistant→Templates (the UI-SPEC group order differs: Assistant sits after Dashboard), and the signed-out case at line 110 (the shell is no longer rendered signed out).
- `ReleasesRoute.test.tsx:103-140`: asserts headers `['Name','Version',...,'Project',...]` and a cell index layout. The UI-SPEC merges Name+Version into "Release" and renames to "Jira project".
- `ReadinessPill.test.tsx`: the "Stale" text becomes "Out of date".
- `SettingsRoute.test.tsx:111`: `container.textContent` contains `'Azure DevOps settings saved.'` (now a toast).
- `DashboardRoute.test.tsx:176-184`: `'QA 1'`, `'Not promoted 0'` (kept via the sr-only list), plus `section[aria-label="Summary"] .text-lg` tile figures (untouched in 23). Also `container.querySelector('h2')` is `'Dashboard'`, which is unaffected unless the shell adds an h2.

### Error display (keep inline, never toast)
**Source:** `src/api/reasonMessages.ts:1-9` + every route. The single source of failure text, keyed on `reason`:
```tsx
<p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
  {messageForReason(problem)}
</p>
```
Apply to the login error, the DataTable page error and the palette group errors. `data-reason` hooks are binding test selectors.

### Query hook shape
**Source:** `src/api/clients.ts:54-59`: `useQuery({ queryKey: <exported const key>, queryFn: () => apiFetch<T>(URL) })`. Mutations invalidate by key prefix (`releases.ts:180-181`). The palette must reuse these exact keys so its cache is shared with the pages.

### Pending / spinner
**Source:** `ReleasesRoute.tsx:46-54`: `<span className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600" aria-hidden="true" />` + text. This becomes `<Spinner>` (lucide `Loader2`, `motion-reduce:animate-none`) on migrated surfaces only. The global reduced-motion CSS covers the rest.

### Comment style
Existing code carries short WHY comments with decision IDs (`// DOC-09: ... (T-05-60)`, `// 05-12/gap 1: ...`). New code should cite `D-23-nn` the same way only where the WHY is non-obvious (for example the `.paper` island and the `enabled` parameter).

---

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `components.json` | config | static | shadcn never initialised; use UI-SPEC/RESEARCH (resolve the `style` conflict) |
| `src/components/ui/*` (21 files) | component | presentational | Generated by `npx shadcn@4.21.4 add`; apply the 4 owned edits from UI-SPEC "Owned-component edits" |
| `src/lib/utils.ts` | utility | transform | No class-merge helper exists |
| `src/components/data-table/features.ts` | utility | static | TanStack Table not installed; RESEARCH Pattern 8 |
| `src/components/StageChart.tsx` (chart part) | component | transform | No charts, no `lazy()`/`Suspense` in repo; RESEARCH Pattern 9 |
| `src/components/theme/ThemeProvider.tsx` (context part) | provider | event-driven | No `createContext` and no `localStorage` in repo; RESEARCH Pattern 2 |
| `public/*` favicons and brand assets | static asset | file-I/O | No `public/` folder; rasterise with headless Chrome + Pillow (RESEARCH "Favicon and Asset Pipeline") |
| `index.html` boot script | config | static | No inline scripts today; RESEARCH Pattern 2 |
| `tests/setup.ts` | test config | static | No setup file; per-file `Element.prototype.scrollIntoView = vi.fn()` stubs exist (`ReleaseDetailRoute.test.tsx:596`). Use the RESEARCH "Code Examples" polyfill with assign-if-missing |
| `src/components/layout/PageHeader.tsx`, `PageContainer.tsx` | component | presentational | No shared page-header component; each route hand-rolls an `<h2>` today. Markup is fully specified in UI-SPEC |

---

## Metadata

**Analog search scope:** `frontend/src/{api,components,routes,roadmap}`, `frontend/tests/{components,routes}`, `frontend/{package.json,vite.config.ts,tsconfig.json,index.html}`, `src/test/java/.../config/SpaForwardingControllerTest.java`
**Files scanned:** about 30 read or grepped
**Pattern extraction date:** 2026-10-26
