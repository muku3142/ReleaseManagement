import { createRootRoute, createRoute, createRouter, redirect } from '@tanstack/react-router';
import { AppShell } from './components/AppShell';
import { DashboardRoute } from './routes/DashboardRoute';
import { SettingsRoute } from './routes/SettingsRoute';
import { TicketsRoute } from './routes/TicketsRoute';
import { LoginRoute } from './routes/LoginRoute';
import { fetchSessionOrNull } from './api/auth';
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

const routeTree = rootRoute.addChildren([
  authenticatedRoute.addChildren([dashboardRoute, ticketsRoute, settingsRoute]),
  loginRoute,
]);

export const router = createRouter({ routeTree });

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
