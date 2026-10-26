import { createRootRoute, createRoute, createRouter, redirect } from '@tanstack/react-router';
import { AppShell } from './components/AppShell';
import { DashboardRoute } from './routes/DashboardRoute';
import { SettingsRoute } from './routes/SettingsRoute';
import { TicketsRoute } from './routes/TicketsRoute';
import { ReleasesRoute } from './routes/ReleasesRoute';
import { ReleaseDetailRoute } from './routes/ReleaseDetailRoute';
import { TemplatesRoute } from './routes/TemplatesRoute';
import { TemplateDetailRoute } from './routes/TemplateDetailRoute';
import { DocumentViewerRoute } from './routes/DocumentViewerRoute';
import { PortfolioRoute } from './routes/PortfolioRoute';
import { RoadmapRoute } from './routes/RoadmapRoute';
import { BriefingsRoute } from './routes/BriefingsRoute';
import { LoginRoute } from './routes/LoginRoute';
import { AssistantRoute } from './routes/AssistantRoute';
import { assistantStatusQueryOptions } from './api/assistant';
import { fetchSessionOrNull } from './api/auth';
import { parseDashboardSearch, parseProducts } from './api/dashboard';
import { parseRoadmapSearch } from './api/roadmap';
import { queryClient } from './api/queryClient';
import { safeRedirectTarget } from './safeRedirect';

const rootRoute = createRootRoute({ component: AppShell });

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  validateSearch: (search: Record<string, unknown>): { redirect?: string } => ({
    redirect: typeof search.redirect === 'string' ? safeRedirectTarget(search.redirect) : undefined,
  }),
  component: LoginRoute,
});

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

const dashboardRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/',
  validateSearch: parseDashboardSearch,
  component: DashboardRoute,
});

const settingsRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/settings',
  component: SettingsRoute,
});

const ticketsRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/tickets',
  component: TicketsRoute,
});

const releasesRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/releases',
  component: ReleasesRoute,
});

const releaseDetailRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/releases/$releaseId',
  validateSearch: (
    search: Record<string, unknown>,
  ): { added?: number; alreadyPresent?: number; notFound?: number } => ({
    added: typeof search.added === 'number' ? search.added : undefined,
    alreadyPresent: typeof search.alreadyPresent === 'number' ? search.alreadyPresent : undefined,
    notFound: typeof search.notFound === 'number' ? search.notFound : undefined,
  }),
  component: ReleaseDetailRoute,
});

const portfolioRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/portfolio',
  validateSearch: (search: Record<string, unknown>): { tab?: 'products' | 'clients'; client?: string } => ({
    tab: search.tab === 'clients' ? 'clients' : search.tab === 'products' ? 'products' : undefined,
    client: typeof search.client === 'string' ? search.client : undefined,
  }),
  component: PortfolioRoute,
});

const roadmapRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/roadmap',
  validateSearch: parseRoadmapSearch,
  component: RoadmapRoute,
});

const briefingsRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/briefings',
  validateSearch: (search: Record<string, unknown>): { products?: string[] } => ({
    products: parseProducts(search.products),
  }),
  component: BriefingsRoute,
});

const assistantRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/assistant',
  beforeLoad: async () => {
    const status = await queryClient.ensureQueryData(assistantStatusQueryOptions);
    if (!status.enabled) throw redirect({ to: '/' });
  },
  component: AssistantRoute,
});

const templatesRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/templates',
  component: TemplatesRoute,
});

const templateDetailRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/templates/$templateId',
  component: TemplateDetailRoute,
});

// 05-12/gap 1: DocumentViewerRoute is a flat sibling of releaseDetailRoute, not nested under it -
// every View link the phase built (ReadinessPill, DocumentsSection) already points at this path.
const documentViewerRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: '/releases/$releaseId/documents/$documentType',
  validateSearch: (search: Record<string, unknown>): { revision?: number } => ({
    revision: typeof search.revision === 'number' ? search.revision : undefined,
  }),
  component: DocumentViewerRoute,
});

const routeTree = rootRoute.addChildren([
  authenticatedRoute.addChildren([
    dashboardRoute,
    ticketsRoute,
    releasesRoute,
    releaseDetailRoute,
    portfolioRoute,
    roadmapRoute,
    briefingsRoute,
    assistantRoute,
    templatesRoute,
    templateDetailRoute,
    documentViewerRoute,
    settingsRoute,
  ]),
  loginRoute,
]);

export const router = createRouter({ routeTree });

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
