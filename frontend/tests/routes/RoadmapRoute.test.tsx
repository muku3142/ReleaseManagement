import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { RoadmapRoute } from '../../src/routes/RoadmapRoute';
import type { RoadmapItem } from '../../src/api/roadmap';

const navigate = vi.fn();
let searchParams: Record<string, unknown> = {};

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children?: ReactNode; to?: string }) => <a href={to}>{children}</a>,
  useSearch: () => searchParams,
  useNavigate: () => navigate,
}));

function item(overrides: Partial<RoadmapItem>): RoadmapItem {
  return {
    id: 'i1',
    source: 'MANUAL',
    jiraKey: null,
    title: 'Item',
    jiraStatus: null,
    jiraSyncedAt: null,
    jiraUnavailable: false,
    product: null,
    status: 'IDEA',
    targetQuarter: null,
    owner: null,
    notes: null,
    reach: 10,
    impact: 2,
    confidence: 80,
    effort: 1,
    scoreDisplay: '16.0',
    rank: 1,
    missing: [],
    createdAt: '2026-01-01T00:00:00Z',
    archived: false,
    ...overrides,
  };
}

const FIRST = item({ id: 'i1', title: 'Alpha', source: 'JIRA', jiraKey: 'CNV-1', jiraStatus: 'Open', jiraSyncedAt: new Date().toISOString() });
const SECOND = item({ id: 'i2', title: 'Beta', scoreDisplay: '8.0', rank: 2 });

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

let container: HTMLDivElement;
let root: Root;
let calls: { url: string; method: string }[];
let list: RoadmapItem[];
let refreshResponse: Response | null;

beforeAll(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
});

beforeEach(() => {
  searchParams = {};
  navigate.mockReset();
  calls = [];
  list = [FIRST, SECOND];
  refreshResponse = null;
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    calls.push({ url, method });
    if (url === '/api/roadmap/refresh') return refreshResponse ?? json(200, { refreshed: 3, unavailable: [{ itemId: 'i1', jiraKey: 'CNV-1' }] });
    if (url === '/api/roadmap/items/i1/refresh') return json(200, { refreshed: 0, unavailable: [{ itemId: 'i1', jiraKey: 'CNV-1' }] });
    if (url.startsWith('/api/roadmap/export.csv')) return new Response('csv', { status: 200 });
    if (url.startsWith('/api/roadmap/items')) return json(200, { items: list });
    if (url === '/api/products') return json(200, { products: [] });
    return json(200, { releases: [] });
  });
  URL.createObjectURL = vi.fn(() => 'blob:x');
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.restoreAllMocks();
});

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderRoute() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <RoadmapRoute />
      </QueryClientProvider>,
    );
  });
  await flush();
}

const button = (label: string) =>
  Array.from(container.querySelectorAll('button')).find((b) => b.textContent === label) as HTMLButtonElement | undefined;

async function click(el: Element | undefined | null) {
  await act(async () => {
    el!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });
  await flush();
}

const listCalls = () => calls.filter((c) => c.url.startsWith('/api/roadmap/items?') || c.url === '/api/roadmap/items');

describe('RoadmapRoute', () => {
  it('renders the rows in server order with the count', async () => {
    await renderRoute();
    const titles = Array.from(container.querySelectorAll('tbody tr td:nth-child(2) span.font-semibold')).map((s) => s.textContent);
    expect(titles).toEqual(['Alpha', 'Beta']);
    expect(container.textContent).toContain('2 items shown');
    expect(container.querySelector('h2')?.textContent).toBe('Roadmap');
  });

  it('puts Show done in the URL and requests done=true', async () => {
    searchParams = { done: true };
    await renderRoute();
    expect(listCalls().some((c) => c.url === '/api/roadmap/items?done=true')).toBe(true);
    const checkbox = Array.from(container.querySelectorAll('label'))
      .find((l) => l.textContent === 'Show done')!
      .querySelector('input')!;
    expect(checkbox.checked).toBe(true);
    await click(checkbox);
    expect(navigate).toHaveBeenCalledTimes(1);
    const call = navigate.mock.calls[0][0];
    expect(call.replace).toBe(true);
    expect(call.search({ done: true })).toEqual({ done: undefined });
  });

  it('exports with the same query string as the list', async () => {
    searchParams = { done: true, archived: true };
    await renderRoute();
    expect(container.textContent).toContain('Exports the 2 items shown.');
    await click(button('Export CSV'));
    const exported = calls.find((c) => c.url.startsWith('/api/roadmap/export.csv'))!;
    expect(exported.url).toBe('/api/roadmap/export.csv?done=true&archived=true');
    expect(listCalls().some((c) => c.url === '/api/roadmap/items?done=true&archived=true')).toBe(true);
  });

  it('blocks export when nothing is shown', async () => {
    list = [];
    await renderRoute();
    const exportButton = button('Export CSV')!;
    expect(exportButton.getAttribute('aria-disabled')).toBe('true');
    expect(container.textContent).toContain('Nothing to export.');
    await click(exportButton);
    expect(calls.some((c) => c.url.startsWith('/api/roadmap/export.csv'))).toBe(false);
  });

  it('reports a refresh from Jira', async () => {
    await renderRoute();
    await click(button('Refresh from Jira'));
    expect(calls.some((c) => c.url === '/api/roadmap/refresh' && c.method === 'POST')).toBe(true);
    expect(container.textContent).toContain('Refreshed 3 items from Jira.');
    expect(container.textContent).toContain('1 no longer in Jira and are marked unavailable.');
  });

  it('says the issue is gone after a single refresh', async () => {
    await renderRoute();
    const refresh = container.querySelector('[aria-label="Refresh CNV-1 from Jira"]');
    list = [FIRST];
    await click(refresh);
    expect(calls.some((c) => c.url === '/api/roadmap/items/i1/refresh')).toBe(true);
    expect(container.textContent).toContain('CNV-1 is no longer in Jira. It stays on the roadmap and is marked unavailable.');
  });

  it('shows a Jira failure and keeps the notice free of results', async () => {
    refreshResponse = json(502, { status: 502, reason: 'JIRA_UNAVAILABLE', detail: 'timed out.' });
    await renderRoute();
    await click(button('Refresh from Jira'));
    const alert = container.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain("Couldn't reach Jira");
    expect(alert?.textContent).toContain('Nothing was changed. Try again.');
  });

  it('blocks refresh when there are no Jira items', async () => {
    list = [SECOND];
    await renderRoute();
    expect(button('Refresh from Jira')!.getAttribute('aria-disabled')).toBe('true');
    expect(container.textContent).toContain('No Jira items to refresh.');
  });

  it('explains an empty roadmap and filtered-empty state', async () => {
    list = [];
    await renderRoute();
    expect(container.textContent).toContain('No roadmap items yet');
    act(() => root.unmount());
    root = createRoot(container);
    searchParams = { archived: true };
    await renderRoute();
    expect(container.textContent).toContain('No items match these filters');
    await click(button('Clear filters'));
    expect(navigate.mock.calls.at(-1)![0].search({ archived: true })).toEqual({
      products: undefined,
      done: undefined,
      archived: undefined,
    });
  });

  it('opens the detail row named by the item param', async () => {
    searchParams = { item: 'i2' };
    await renderRoute();
    expect(container.querySelector('#item-i2-detail')).not.toBeNull();
    expect(container.querySelector('#item-i1-detail')).toBeNull();
  });
});
