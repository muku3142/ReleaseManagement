import { createRootRoute, createRoute, createRouter } from '@tanstack/react-router';
import { AppShell } from './components/AppShell';
import { DashboardRoute } from './routes/DashboardRoute';
import { SettingsRoute } from './routes/SettingsRoute';

const rootRoute = createRootRoute({ component: AppShell });

const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: DashboardRoute,
});

const settingsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/settings',
  component: SettingsRoute,
});

const routeTree = rootRoute.addChildren([dashboardRoute, settingsRoute]);

export const router = createRouter({ routeTree });

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
