import { act, useEffect, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppShell } from '../../src/components/AppShell';
import { PRODUCT_NAME } from '../../src/components/brand/brand';
import { ThemeProvider } from '../../src/components/theme/ThemeProvider';
import { TooltipProvider } from '../../src/components/ui/tooltip';

type Match = { routeId: string; staticData?: { themed?: boolean } };

const router = vi.hoisted(() => ({
  pathname: '/',
  matches: [] as { routeId: string; staticData?: { themed?: boolean } }[],
  outletMounts: 0,
}));

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    children,
    to,
    activeOptions: _activeOptions,
    activeProps: _activeProps,
    ...rest
  }: {
    children?: ReactNode;
    to: string;
    activeOptions?: unknown;
    activeProps?: unknown;
  }) => {
    const active = router.pathname === to;
    return (
      <a href={to} aria-current={active ? 'page' : undefined} data-status={active ? 'active' : undefined} {...rest}>
        {children}
      </a>
    );
  },
  Outlet: function Outlet() {
    useEffect(() => {
      router.outletMounts += 1;
    }, []);
    return null;
  },
  useMatches: ({ select }: { select?: (matches: Match[]) => unknown } = {}) =>
    select ? select(router.matches) : router.matches,
  useNavigate: () => vi.fn(),
  useRouterState: ({ select }: { select: (state: { location: { pathname: string } }) => unknown }) =>
    select({ location: { pathname: router.pathname } }),
}));
vi.mock('../../src/components/AdoCredentialBanner', () => ({ AdoCredentialBanner: () => <div data-testid="ado-banner" /> }));
vi.mock('../../src/components/CredentialAlert', () => ({ CredentialAlert: () => <div data-testid="credential-alert" /> }));
vi.mock('../../src/components/CommandPalette', () => ({ CommandPalette: () => <div data-testid="command-palette" /> }));

type Reply = { status: number; body?: unknown } | 'pending';

const SESSION = { accountId: 'a1', email: 'rm@example.com', absoluteExpiry: '2099-01-01T00:00:00Z' };
const STARTERS = { unscoped: [], release: [] };

let container: HTMLDivElement;
let root: Root;
let client: QueryClient;
let fetchSpy: ReturnType<typeof vi.fn>;

beforeAll(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
});

beforeEach(() => {
  router.pathname = '/';
  router.matches = [{ routeId: '__root__' }, { routeId: '/authenticated' }, { routeId: '/authenticated/' }];
  router.outletMounts = 0;
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
  localStorage.clear();
  document.documentElement.classList.remove('dark');
});

function shell() {
  return (
    <ThemeProvider>
      <QueryClientProvider client={client}>
        <TooltipProvider>
          <AppShell />
        </TooltipProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}

function respond(reply: Reply): Promise<Response> {
  if (reply === 'pending') return new Promise<Response>(() => {});
  return Promise.resolve(new Response(reply.body === undefined ? null : JSON.stringify(reply.body), { status: reply.status }));
}

async function renderShell(status: Reply = { status: 200, body: { enabled: false } }, logout: Reply = { status: 204 }) {
  fetchSpy = vi.fn((path: string) => {
    if (path === '/api/auth/session') return respond({ status: 200, body: SESSION });
    if (path === '/api/auth/logout') return respond(logout);
    return respond(status);
  });
  vi.stubGlobal('fetch', fetchSpy);
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => root.render(shell()));
  await vi.waitFor(() => expect(fetchSpy.mock.calls.map(([path]) => path)).toContain('/api/assistant/status'));
  await act(async () => new Promise((resolve) => setTimeout(resolve, 20)));
}

async function rerender() {
  await act(async () => root.render(shell()));
}

function nav(): HTMLElement {
  return container.querySelector('nav[aria-label="Main"]')!;
}

function navLabels(): string[] {
  return Array.from(nav().querySelectorAll('a')).map((a) => a.textContent ?? '');
}

function indicator(): HTMLElement {
  return nav().querySelector('[data-slot="nav-indicator"]')!;
}

describe('AppShell sidebar nav', () => {
  it('lists the pages in sidebar order with the assistant off', async () => {
    await renderShell();
    expect(navLabels()).toEqual([
      'Dashboard',
      'Tickets',
      'Releases',
      'Roadmap',
      'Briefings',
      'Products & clients',
      'Templates',
      'Settings',
    ]);
    const roadmap = Array.from(nav().querySelectorAll('a')).find((a) => a.textContent === 'Roadmap');
    expect(roadmap?.getAttribute('href')).toBe('/roadmap');
  });

  it('shows Assistant straight after Dashboard when enabled', async () => {
    await renderShell({ status: 200, body: { enabled: true, starters: STARTERS } });
    await vi.waitFor(() => expect(navLabels()).toContain('Assistant'));
    expect(navLabels().slice(0, 3)).toEqual(['Dashboard', 'Assistant', 'Tickets']);
  });

  it('hides Assistant while the status is loading', async () => {
    await renderShell('pending');
    expect(navLabels()).not.toContain('Assistant');
  });

  it('hides Assistant when the status request fails', async () => {
    await renderShell({ status: 500, body: { reason: 'UNKNOWN' } });
    expect(navLabels()).not.toContain('Assistant');
  });

  it('renders the Delivery, Planning and Configure group labels', async () => {
    await renderShell();
    const text = nav().textContent ?? '';
    for (const group of ['Delivery', 'Planning', 'Configure']) expect(text).toContain(group);
  });

  it('gives nav links the sidebar-accent hover background', async () => {
    await renderShell();
    for (const link of nav().querySelectorAll('a')) expect(link.className).toContain('hover:bg-sidebar-accent');
  });
});

describe('AppShell brand header', () => {
  it('links the product lockup to the dashboard and renders no h1', async () => {
    await renderShell();
    const link = container.querySelector(`a[aria-label="${PRODUCT_NAME}, go to dashboard"]`);
    expect(link?.getAttribute('href')).toBe('/');
    expect(link?.textContent).toContain(PRODUCT_NAME);
    const paths = link?.querySelectorAll('path') ?? [];
    expect(paths.length).toBeGreaterThan(0);
    for (const path of paths) expect(path.getAttribute('class')).toContain('motion-safe:animate-mark-link');
    expect(container.querySelector('h1')).toBeNull();
  });
});

describe('AppShell active nav indicator', () => {
  it('is a single hidden indicator, transparent when no link is active', async () => {
    router.pathname = '/nowhere';
    await renderShell();
    expect(nav().querySelectorAll('[data-slot="nav-indicator"]')).toHaveLength(1);
    expect(indicator().getAttribute('aria-hidden')).toBe('true');
    expect(indicator().className).toContain('opacity-0');
  });

  it('appears without a slide on first position and slides with transform once moved', async () => {
    router.pathname = '/tickets';
    await renderShell();
    expect(indicator().className).not.toContain('opacity-0');
    expect(indicator().className).not.toContain('transition-transform');
    expect(indicator().style.transform).toMatch(/^translateY\(/);

    router.pathname = '/releases';
    await rerender();
    expect(indicator().className).toContain('transition-transform');
    expect(indicator().className).toContain('duration-(--motion-base)');
  });
});

describe('AppShell layout', () => {
  it('makes a Skip to content link the first focusable element', async () => {
    await renderShell();
    const first = container.querySelector('a, button, [tabindex]:not([tabindex="-1"])');
    expect(first?.textContent).toBe('Skip to content');
    expect(first?.getAttribute('href')).toBe('#content');
    const main = container.querySelector('main#content');
    expect(main?.getAttribute('tabindex')).toBe('-1');
    expect(container.querySelectorAll('main')).toHaveLength(1);
  });

  it('wraps unmigrated routes in the paper island', async () => {
    await renderShell();
    expect(container.querySelector('main')?.classList.contains('paper')).toBe(true);
  });

  it('renders themed routes on the themed canvas', async () => {
    router.matches = [{ routeId: '__root__' }, { routeId: '/authenticated' }, { routeId: '/authenticated/releases', staticData: { themed: true } }];
    await renderShell();
    expect(container.querySelector('main')?.classList.contains('paper')).toBe(false);
  });

  it('fades the page in, remounting only when the leaf route changes', async () => {
    await renderShell();
    expect(container.querySelector('main > div')?.className).toContain('motion-safe:animate-page-in');
    expect(router.outletMounts).toBe(1);

    await rerender();
    expect(router.outletMounts).toBe(1);

    router.matches = [{ routeId: '__root__' }, { routeId: '/authenticated' }, { routeId: '/authenticated/tickets' }];
    await rerender();
    expect(router.outletMounts).toBe(2);
  });

  it('still renders the credential banners inside the shell', async () => {
    await renderShell();
    expect(container.querySelector('[data-testid="credential-alert"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="ado-banner"]')).not.toBeNull();
  });
});

function pressEnter(element: Element) {
  act(() => {
    element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
  });
}

function menuItem(role: string, label: string): HTMLElement | undefined {
  return Array.from(document.body.querySelectorAll<HTMLElement>(`[role="${role}"]`)).find((item) =>
    item.textContent?.includes(label),
  );
}

async function openAccountMenu() {
  const trigger = container.querySelector('button[aria-label="Account menu"]')!;
  await vi.waitFor(() => expect(trigger.textContent).toContain('R'));
  pressEnter(trigger);
  await vi.waitFor(() => expect(menuItem('menuitem', 'Sign out')).toBeDefined());
}

describe('AppShell top bar', () => {
  it('holds the sidebar toggle, palette, theme menu and account menu in order', async () => {
    await renderShell();
    const header = container.querySelector('header')!;
    const controls = Array.from(
      header.querySelectorAll(
        'button[aria-label="Toggle sidebar"], [data-testid="command-palette"], button[aria-label="Change theme"], button[aria-label="Account menu"]',
      ),
    ).map((element) => element.getAttribute('aria-label') ?? element.getAttribute('data-testid'));
    expect(controls).toEqual(['Toggle sidebar', 'command-palette', 'Change theme', 'Account menu']);
  });

  it('offers Light, Dark and System with the current theme checked, and applies Dark', async () => {
    await renderShell();
    pressEnter(container.querySelector('button[aria-label="Change theme"]')!);
    await vi.waitFor(() => expect(menuItem('menuitemradio', 'System')).toBeDefined());
    const labels = Array.from(document.body.querySelectorAll('[role="menuitemradio"]')).map((item) => item.textContent);
    expect(labels).toEqual(['Light', 'Dark', 'System']);
    expect(menuItem('menuitemradio', 'System')?.getAttribute('aria-checked')).toBe('true');

    act(() => menuItem('menuitemradio', 'Dark')!.click());
    await vi.waitFor(() => expect(document.documentElement.classList.contains('dark')).toBe(true));
    expect(localStorage.getItem('productops-theme')).toBe('dark');
  });

  it('shows the account initial and email, and signs out to /login', async () => {
    await renderShell();
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign });
    await openAccountMenu();
    expect(document.body.querySelector('[role="menu"]')?.textContent).toContain(SESSION.email);

    act(() => menuItem('menuitem', 'Sign out')!.click());
    await vi.waitFor(() => expect(assign).toHaveBeenCalledWith('/login'));
    expect(fetchSpy.mock.calls.some(([path, init]) => path === '/api/auth/logout' && init?.method === 'POST')).toBe(true);
  });

  it('disables the sign-out item while logout is pending', async () => {
    await renderShell({ status: 200, body: { enabled: false } }, 'pending');
    await openAccountMenu();
    act(() => menuItem('menuitem', 'Sign out')!.click());
    await vi.waitFor(() => expect(menuItem('menuitem', 'Signing out…')?.getAttribute('aria-disabled')).toBe('true'));
  });
});
