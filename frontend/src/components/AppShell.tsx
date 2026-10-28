import { Outlet, useMatches } from '@tanstack/react-router';
import { AppSidebar } from '@/components/layout/AppSidebar';
import { TopBar } from '@/components/layout/TopBar';
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar';
import { cn } from '@/lib/utils';
import { AdoCredentialBanner } from './AdoCredentialBanner';
import { CredentialAlert } from './CredentialAlert';

export function AppShell() {
  const themed = useMatches({ select: (matches) => matches[matches.length - 1]?.staticData?.themed === true });
  const leafRouteId = useMatches({ select: (matches) => matches[matches.length - 1]?.routeId });

  return (
    <SidebarProvider>
      <a
        href="#content"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-popover focus:px-3 focus:py-2 focus:text-sm focus:shadow-md"
      >
        Skip to content
      </a>
      <AppSidebar />
      <SidebarInset className="bg-background">
        <TopBar />
        <CredentialAlert />
        <AdoCredentialBanner />
        <main
          id="content"
          tabIndex={-1}
          className={cn('flex-1 p-6 lg:p-8 outline-none', !themed && 'paper bg-background text-foreground')}
        >
          <div key={leafRouteId} className="motion-safe:animate-page-in">
            <Outlet />
          </div>
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}
