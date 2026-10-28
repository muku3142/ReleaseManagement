import { useLayoutEffect, useRef, useState } from 'react';
import { Link, useRouterState } from '@tanstack/react-router';
import { useAssistantStatus } from '@/api/assistant';
import { PRODUCT_NAME } from '@/components/brand/brand';
import { ProductOpsLogo } from '@/components/brand/ProductOpsLogo';
import { NAV_ITEMS, type NavItem } from '@/components/layout/navItems';
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
  useSidebar,
} from '@/components/ui/sidebar';
import { cn } from '@/lib/utils';

const GROUPS = ['Delivery', 'Planning', 'Configure'] as const;

type IndicatorBox = { top: number; height: number };

function NavMenu({ items }: { items: readonly NavItem[] }) {
  return (
    <SidebarMenu>
      {items.map(({ label, to, icon: Icon }) => (
        <SidebarMenuItem key={to}>
          <SidebarMenuButton
            asChild
            tooltip={label}
            className="h-8 text-sm text-muted-foreground transition-colors duration-(--motion-fast) hover:bg-sidebar-accent hover:text-sidebar-accent-foreground data-[status=active]:bg-primary/10 data-[status=active]:font-semibold data-[status=active]:text-primary"
          >
            <Link to={to} activeOptions={{ exact: to === '/' }}>
              <Icon className="size-4" aria-hidden="true" />
              <span>{label}</span>
            </Link>
          </SidebarMenuButton>
        </SidebarMenuItem>
      ))}
    </SidebarMenu>
  );
}

export function AppSidebar() {
  const assistantEnabled = useAssistantStatus(true).data?.enabled === true;
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const sidebarState = useSidebar().state;
  const navRef = useRef<HTMLElement>(null);
  const measured = useRef(false);
  const [box, setBox] = useState<IndicatorBox | null>(null);
  const [moved, setMoved] = useState(false);

  // Measured against the nav rather than offsetTop, because each menu item is its own positioned ancestor.
  useLayoutEffect(() => {
    const nav = navRef.current;
    const active = nav?.querySelector<HTMLElement>('[aria-current="page"]');
    const next = nav && active
      ? { top: active.getBoundingClientRect().top - nav.getBoundingClientRect().top, height: active.offsetHeight }
      : null;
    if (next && measured.current) setMoved(true);
    measured.current ||= next !== null;
    setBox(next);
  }, [pathname, sidebarState, assistantEnabled]);

  const ungrouped = NAV_ITEMS.filter((item) => item.group === null && (!item.assistantOnly || assistantEnabled));

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader className="h-14 justify-center">
        <SidebarMenuButton asChild size="lg" tooltip={PRODUCT_NAME}>
          <Link to="/" aria-label={`${PRODUCT_NAME}, go to dashboard`}>
            <ProductOpsLogo variant="sidebar" animate />
          </Link>
        </SidebarMenuButton>
      </SidebarHeader>
      <SidebarContent>
        <nav ref={navRef} aria-label="Main" className="relative flex flex-col">
          <span
            data-slot="nav-indicator"
            aria-hidden="true"
            className={cn(
              'pointer-events-none absolute left-0 top-0 w-0.5 rounded-full bg-primary',
              moved && 'transition-transform duration-(--motion-base) ease-(--ease-out)',
              !box && 'opacity-0',
            )}
            style={box ? { transform: `translateY(${box.top}px)`, height: box.height } : undefined}
          />
          <SidebarGroup>
            <NavMenu items={ungrouped} />
          </SidebarGroup>
          {GROUPS.map((group) => (
            <SidebarGroup key={group}>
              <SidebarGroupLabel className="text-xs font-semibold text-muted-foreground">{group}</SidebarGroupLabel>
              <NavMenu items={NAV_ITEMS.filter((item) => item.group === group)} />
            </SidebarGroup>
          ))}
        </nav>
      </SidebarContent>
      <SidebarRail />
    </Sidebar>
  );
}
