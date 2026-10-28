import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ItemDetailRow } from '../../src/roadmap/ItemDetailRow';
import type { InputHistoryEntry, RoadmapItem } from '../../src/api/roadmap';

const MANUAL: RoadmapItem = {
  id: 'i1',
  source: 'MANUAL',
  jiraKey: null,
  title: 'Faster export',
  jiraStatus: null,
  jiraSyncedAt: null,
  jiraUnavailable: false,
  product: { id: 'p1', name: 'Core Banking', archived: false },
  status: 'IDEA',
  targetQuarter: null,
  owner: null,
  notes: null,
  reach: null,
  impact: null,
  confidence: null,
  effort: null,
  scoreDisplay: null,
  rank: null,
  missing: ['REACH', 'IMPACT', 'CONFIDENCE', 'EFFORT'],
  calculated: { reach: 0, impact: null, confidence: null },
  overrides: { reach: null, impact: null, confidence: null },
  calculatedFields: ['REACH'],
  jiraPriority: null,
  readinessSignals: null,
  createdAt: '2026-10-01T00:00:00Z',
  archived: false,
};
const JIRA: RoadmapItem = {
  ...MANUAL,
  id: 'i2',
  source: 'JIRA',
  jiraKey: 'REL-7',
  jiraStatus: 'In Review',
  jiraSyncedAt: new Date(Date.now() - 5 * 60_000).toISOString(),
};

const CATALOG = {
  products: [
    { id: 'p1', name: 'Core Banking', archived: false, clientCount: 12, releaseCount: 0 },
    { id: 'p2', name: 'Mobile', archived: false, clientCount: 1, releaseCount: 0 },
    { id: 'p3', name: 'Legacy', archived: true, clientCount: 3, releaseCount: 0 },
  ],
};

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

let container: HTMLDivElement;
let root: Root;
let calls: { url: string; method: string; body?: unknown }[];

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function mockApi(history: Response | InputHistoryEntry[] = []) {
  calls = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    calls.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : undefined });
    if (url === '/api/products') return json(200, CATALOG);
    if (url.endsWith('/history')) return history instanceof Response ? history : json(200, { entries: history });
    return json(200, MANUAL);
  });
}

async function renderRow(item: RoadmapItem) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <table>
          <tbody>
            <ItemDetailRow item={item} colSpan={12} />
          </tbody>
        </table>
      </QueryClientProvider>,
    );
  });
  await flush();
}

function byId(id: string) {
  return container.querySelector<HTMLInputElement & HTMLSelectElement & HTMLTextAreaElement>(`#${id}`)!;
}

function button(text: string): HTMLButtonElement | undefined {
  return [...container.querySelectorAll('button')].find((b) => b.textContent === text);
}

async function change(el: HTMLElement, value: string) {
  await act(async () => {
    const proto = el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(el, value);
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
  });
  await flush();
}

async function blur(el: HTMLElement) {
  await act(async () => {
    el.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
  });
  await flush();
}

const puts = () => calls.filter((c) => c.method === 'PUT');

describe('ItemDetailRow', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });
  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });
  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
  });

  it('lets a manual item edit its title and a Jira item only refresh it', async () => {
    mockApi();
    await renderRow(MANUAL);
    expect(byId('i1-title').readOnly).toBe(false);
    expect(button('Refresh from Jira')).toBeUndefined();

    await act(async () => root.unmount());
    root = createRoot(container);
    await renderRow(JIRA);
    expect(byId('i2-title').readOnly).toBe(true);
    expect(container.textContent).toContain('Comes from Jira. Refresh to update it.');
    expect(container.textContent).toContain('REL-7 · In Review · Fetched 5m ago');

    await act(async () => button('Refresh from Jira')!.click());
    await flush();
    expect(calls.some((c) => c.url === '/api/roadmap/items/i2/refresh' && c.method === 'POST')).toBe(true);
  });

  it('offers active products plus the current one, and shows the Reach hint', async () => {
    mockApi();
    await renderRow(MANUAL);
    const names = [...byId('i1-product').options].map((o) => o.textContent);
    expect(names).toEqual(['Core Banking', 'Mobile']);
    expect(container.textContent).toContain('Core Banking has 12 linked clients');

    await act(async () => root.unmount());
    root = createRoot(container);
    await renderRow({ ...MANUAL, product: { id: 'p3', name: 'Legacy', archived: true } });
    expect([...byId('i1-product').options].map((o) => o.textContent)).toContain('Legacy (archived)');
    expect(container.textContent).toContain('Legacy has 3 linked clients');
  });

  it('uses the singular for one linked client and never changes Reach', async () => {
    mockApi();
    await renderRow({ ...MANUAL, product: { id: 'p2', name: 'Mobile', archived: false } });
    expect(container.textContent).toContain('Mobile has 1 linked client');
    expect(container.textContent).not.toContain('1 linked clients');
  });

  it('sends the full details on a status change, and null for a blank owner', async () => {
    mockApi();
    await renderRow({ ...MANUAL, owner: 'Dana' });
    await change(byId('i1-status'), 'PLANNED');
    expect(puts()[0].body).toEqual({
      title: 'Faster export',
      status: 'PLANNED',
      productId: 'p1',
      targetQuarter: null,
      owner: 'Dana',
      notes: null,
    });
    await change(byId('i1-owner'), '  ');
    await blur(byId('i1-owner'));
    expect((puts()[1].body as { owner: string | null }).owner).toBeNull();
  });

  it('renders history newest first with formatted changes', async () => {
    mockApi([
      { field: 'REACH', oldValue: null, newValue: 10, changedAt: '2026-10-01T10:00:00Z', changedBy: 'ann@acme.com', kind: 'ENTERED', reason: null },
      { field: 'CONFIDENCE', oldValue: 80, newValue: 100, changedAt: '2026-10-02T10:00:00Z', changedBy: 'bob@acme.com', kind: 'ENTERED', reason: null },
    ]);
    await renderRow(MANUAL);
    const rows = [...container.querySelectorAll('table table tbody tr')].map((r) => r.textContent ?? '');
    expect(rows[0]).toContain('Medium (80%) → High (100%)');
    expect(rows[1]).toContain('not set → 10');
  });

  it('labels override and reset rows in the history', async () => {
    mockApi([
      { field: 'REACH', oldValue: 7, newValue: 20, changedAt: '2026-10-01T10:00:00Z', changedBy: 'ann@acme.com', kind: 'OVERRIDDEN', reason: 'Big client onboarding' },
      { field: 'REACH', oldValue: 20, newValue: 7, changedAt: '2026-10-02T10:00:00Z', changedBy: 'ann@acme.com', kind: 'RESET', reason: null },
    ]);
    await renderRow(MANUAL);
    const text = [...container.querySelectorAll('table table tbody tr')].map((r) => r.textContent ?? '').join('|');
    expect(text).toContain('Overridden: Big client onboarding');
    expect(text).toContain('Reset to calculated');
  });

  it('shows the empty and error states of the history', async () => {
    mockApi([]);
    await renderRow(MANUAL);
    expect(container.textContent).toContain('No input changes yet.');

    await act(async () => root.unmount());
    root = createRoot(container);
    mockApi(json(500, { reason: 'INTERNAL', status: 500, title: 'x', detail: 'boom' }));
    await renderRow(MANUAL);
    expect(container.querySelector('[role="alert"]')?.textContent).toContain("Couldn't load the history");
    expect(button('Try again')).toBeDefined();
  });

  it('archives and restores', async () => {
    mockApi();
    await renderRow(MANUAL);
    await act(async () => button('Archive item')!.click());
    await flush();
    expect(calls.some((c) => c.url === '/api/roadmap/items/i1/archive' && c.method === 'POST')).toBe(true);

    await act(async () => root.unmount());
    root = createRoot(container);
    await renderRow({ ...MANUAL, archived: true });
    await act(async () => button('Restore item')!.click());
    await flush();
    expect(calls.some((c) => c.url === '/api/roadmap/items/i1/restore' && c.method === 'POST')).toBe(true);
    expect(container.textContent).toContain('Archived items are hidden from the ranking. Their history is kept.');
  });

  it('marks a Jira item that is gone as unavailable', async () => {
    mockApi();
    await renderRow({ ...JIRA, jiraUnavailable: true });
    const marker = [...container.querySelectorAll('span')].find((s) => s.textContent === 'unavailable in Jira');
    expect(marker?.getAttribute('title')).toBe(
      'Jira no longer returns this issue. The last fetched summary and status are kept.',
    );
    expect(container.textContent).toContain('Last fetched 5m ago');
  });
});
