import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { CommandPalette } from '../../src/components/CommandPalette';
import { ThemeProvider } from '../../src/components/theme/ThemeProvider';
import { SidebarProvider } from '../../src/components/ui/sidebar';

const navigate = vi.fn();

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigate,
}));

const RELEASE_ID = '11111111-1111-4111-8111-111111111111';
const CLIENT_ID = '22222222-2222-4222-8222-222222222222';
const PRODUCT_ID = '33333333-3333-4333-8333-333333333333';
const ITEM_ID = '44444444-4444-4444-8444-444444444444';

const release = (id: string, name: string, version: string, status: 'OPEN' | 'FROZEN') => ({
  id,
  name,
  version,
  status,
  jiraProjectKey: 'CNV',
});

const DEFAULT_RESPONSES: Record<string, () => Response | Promise<Response>> = {
  '/api/releases': () =>
    json(200, {
      releases: [
        release(RELEASE_ID, 'Alpha', '1.2', 'FROZEN'),
        release('55555555-5555-4555-8555-555555555555', '<img src=x onerror=alert(1)>', '9.9', 'OPEN'),
      ],
    }),
  '/api/clients': () =>
    json(200, {
      clients: [
        { id: CLIENT_ID, name: 'Alpha Client', archived: false, productIds: [] },
        { id: '66666666-6666-4666-8666-666666666666', name: 'Alpha Gone Co', archived: true, productIds: [] },
      ],
    }),
  '/api/products': () =>
    json(200, {
      products: [
        { id: PRODUCT_ID, name: 'Alpha Product', archived: false, clientCount: 0, releaseCount: 0, jiraProjectKeys: [] },
        { id: '77777777-7777-4777-8777-777777777777', name: 'Alpha Retired', archived: true, clientCount: 0, releaseCount: 0, jiraProjectKeys: [] },
      ],
    }),
  '/api/roadmap/items': () =>
    json(200, { items: [{ id: ITEM_ID, title: 'Alpha roadmap item', jiraKey: 'CNV-7', archived: false }] }),
  '/api/assistant/status': () => json(200, { enabled: false }),
};

let responses: Record<string, () => Response | Promise<Response>>;
let fetchSpy: ReturnType<typeof vi.spyOn>;

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function requestedUrls(): string[] {
  return fetchSpy.mock.calls.map(([input]) => String(input));
}

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderPalette() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <ThemeProvider>
          <SidebarProvider>
            <CommandPalette />
          </SidebarProvider>
        </ThemeProvider>
      </QueryClientProvider>,
    );
  });
  await flush();
}

async function pressCtrlK(target: EventTarget = document.body, init: KeyboardEventInit = { ctrlKey: true }) {
  await act(async () => {
    target.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', bubbles: true, cancelable: true, ...init }));
  });
  await flush();
}

const dialog = () => document.querySelector<HTMLElement>('[role="dialog"]');

async function type(value: string) {
  const input = document.querySelector<HTMLInputElement>('[cmdk-input]')!;
  await act(async () => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await flush();
  await flush();
}

function groupHeadings(): string[] {
  return [...document.querySelectorAll('[cmdk-group-heading]')].map((h) => h.textContent ?? '');
}

function item(text: string): HTMLElement | undefined {
  return [...document.querySelectorAll<HTMLElement>('[cmdk-item]')].find((el) => el.textContent?.includes(text));
}

async function choose(text: string) {
  const target = item(text);
  expect(target, `item "${text}"`).toBeDefined();
  await act(async () => target!.click());
  await flush();
}

describe('CommandPalette', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    localStorage.clear();
    navigate.mockReset();
    responses = { ...DEFAULT_RESPONSES };
    fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const path = String(input).split('?')[0];
      const respond = responses[path];
      return respond ? respond() : json(404, { reason: 'NOT_FOUND' });
    });
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    document.body.innerHTML = '';
    document.documentElement.classList.remove('dark');
    delete (navigator as unknown as Record<string, unknown>).platform;
    vi.restoreAllMocks();
  });

  it('fetches no list until it is first opened', async () => {
    await renderPalette();
    const lists = ['/api/releases', '/api/clients', '/api/products', '/api/roadmap/items'];
    expect(requestedUrls().filter((url) => lists.some((list) => url.startsWith(list)))).toEqual([]);

    await pressCtrlK();
    expect(requestedUrls()).toEqual(expect.arrayContaining(lists));
  });

  it('requests active roadmap items only (not done, not archived)', async () => {
    await renderPalette();
    await pressCtrlK();
    expect(requestedUrls()).toContain('/api/roadmap/items');
    expect(requestedUrls().filter((url) => url.startsWith('/api/roadmap/items'))).toEqual(['/api/roadmap/items']);
  });

  it('opens with Ctrl+K, titled "Command palette", and closes with Ctrl+K', async () => {
    await renderPalette();
    expect(dialog()).toBeNull();
    await pressCtrlK();
    const opened = dialog()!;
    expect(opened).not.toBeNull();
    expect(document.getElementById(opened.getAttribute('aria-labelledby')!)!.textContent).toBe('Command palette');
    await pressCtrlK();
    expect(dialog()).toBeNull();
  });

  it('opens with Meta+K and from inside an input', async () => {
    await renderPalette();
    await pressCtrlK(document.body, { metaKey: true });
    expect(dialog()).not.toBeNull();
    await pressCtrlK(document.body, { metaKey: true });
    expect(dialog()).toBeNull();

    const field = document.createElement('input');
    document.body.appendChild(field);
    await pressCtrlK(field);
    expect(dialog()).not.toBeNull();
  });

  it('shows the trigger with a Ctrl K hint on Windows', async () => {
    Object.defineProperty(navigator, 'platform', { value: 'Win32', configurable: true });
    await renderPalette();
    expect(container.textContent).toContain('Search or jump to…');
    expect([...container.querySelectorAll('kbd')].map((k) => k.textContent)).toContain('Ctrl K');
    expect(container.querySelector('[aria-label="Open command palette"]')).not.toBeNull();
  });

  it('shows a ⌘ K hint on macOS', async () => {
    Object.defineProperty(navigator, 'platform', { value: 'MacIntel', configurable: true });
    await renderPalette();
    expect([...container.querySelectorAll('kbd')].map((k) => k.textContent)).toContain('⌘ K');
  });

  it('opens from the trigger button', async () => {
    await renderPalette();
    const trigger = [...container.querySelectorAll('button')].find((b) => b.textContent?.includes('Search or jump to…'))!;
    await act(async () => trigger.click());
    await flush();
    expect(dialog()).not.toBeNull();
  });

  it('shows Go to and Actions with an empty query, and data groups only once text is typed', async () => {
    await renderPalette();
    await pressCtrlK();
    await flush();
    expect(groupHeadings()).toEqual(['Go to', 'Actions']);
    expect(item('Releases')?.textContent).toContain('G R');
    expect(item('Products & clients')?.textContent).toContain('G P');
    expect(item('Assistant')).toBeUndefined();

    await type('alpha');
    expect(groupHeadings()).toEqual(expect.arrayContaining(['Releases', 'Clients', 'Products', 'Roadmap items']));
  });

  it('lists the Assistant last when it is enabled', async () => {
    responses['/api/assistant/status'] = () => json(200, { enabled: true, starters: { unscoped: [], release: [] } });
    await renderPalette();
    await pressCtrlK();
    const goTo = document.querySelector('[cmdk-group]')!;
    const labels = [...goTo.querySelectorAll('[cmdk-item]')].map((el) => el.textContent ?? '');
    expect(labels.at(-1)).toContain('Assistant');
  });

  it('New release opens the product-first tickets page, also found by its old name', async () => {
    await renderPalette();
    await pressCtrlK();
    expect(item('Start a release from tickets')).toBeUndefined();
    await choose('New release');
    expect(navigate).toHaveBeenLastCalledWith({ to: '/tickets', search: { new: true } });
    expect(dialog()).toBeNull();

    await pressCtrlK();
    await type('start a release');
    expect(item('New release')).toBeDefined();
  });

  it('finds a release by name and opens its page, then closes', async () => {
    await renderPalette();
    await pressCtrlK();
    await type('alpha');
    const found = item('Alpha 1.2')!;
    expect(found.textContent).toContain('Frozen');

    await choose('Alpha 1.2');
    expect(navigate).toHaveBeenCalledWith({ to: '/releases/$releaseId', params: { releaseId: RELEASE_ID } });
    expect(dialog()).toBeNull();
  });

  it('navigates a client, a product and a roadmap item to their existing pages', async () => {
    await renderPalette();
    await pressCtrlK();
    await type('alpha');
    await choose('Alpha Client');
    expect(navigate).toHaveBeenLastCalledWith({ to: '/portfolio', search: { tab: 'clients', client: CLIENT_ID } });

    await pressCtrlK();
    await type('alpha');
    await choose('Alpha Product');
    expect(navigate).toHaveBeenLastCalledWith({ to: '/', search: expect.objectContaining({ products: [PRODUCT_ID] }) });

    await pressCtrlK();
    await type('alpha');
    expect(item('Alpha roadmap item')?.textContent).toContain('CNV-7');
    await choose('Alpha roadmap item');
    expect(navigate).toHaveBeenLastCalledWith({ to: '/roadmap', search: expect.objectContaining({ item: ITEM_ID }) });
  });

  it('hides archived clients and products', async () => {
    await renderPalette();
    await pressCtrlK();
    await type('alpha');
    expect(item('Alpha Gone Co')).toBeUndefined();
    expect(item('Alpha Retired')).toBeUndefined();
  });

  it('shows a per-group error while the other groups still work', async () => {
    responses['/api/clients'] = () => json(500, { reason: 'INTERNAL' });
    await renderPalette();
    await pressCtrlK();
    await type('alpha');
    expect(document.body.textContent).toContain("Couldn't load clients. Close the palette and open it again to retry.");
    expect(item('Alpha 1.2')).toBeDefined();
  });

  it('shows a loading row while a list is pending', async () => {
    responses['/api/releases'] = () => new Promise<Response>(() => {});
    await renderPalette();
    await pressCtrlK();
    await type('a');
    expect(document.body.textContent).toContain('Loading releases…');
  });

  it('shows the empty message when nothing matches', async () => {
    await renderPalette();
    await pressCtrlK();
    await type('zzz');
    expect(document.body.textContent).toContain('No results for “zzz”.');
  });

  it('switches the theme and omits the current theme', async () => {
    await renderPalette();
    await pressCtrlK();
    expect(item('Use system theme')).toBeUndefined();
    await choose('Use dark theme');
    expect(document.documentElement.classList.contains('dark')).toBe(true);

    await pressCtrlK();
    expect(item('Use dark theme')).toBeUndefined();
    expect(item('Use system theme')).toBeDefined();
  });

  it('collapses and expands the sidebar', async () => {
    await renderPalette();
    await pressCtrlK();
    expect(item('Collapse sidebar')?.textContent).toMatch(/(Ctrl|⌘) B/);
    await choose('Collapse sidebar');
    expect(document.querySelector('[data-slot="sidebar-wrapper"]')).not.toBeNull();

    await pressCtrlK();
    expect(item('Expand sidebar')).toBeDefined();
    expect(item('Collapse sidebar')).toBeUndefined();
  });

  it('renders a hostile release name as text', async () => {
    await renderPalette();
    await pressCtrlK();
    await type('img');
    expect(item('<img src=x onerror=alert(1)>')).toBeDefined();
    expect(document.querySelector('img')).toBeNull();
  });
});
