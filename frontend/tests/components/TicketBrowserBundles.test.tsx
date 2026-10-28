import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { TicketBrowser } from '../../src/components/TicketBrowser';

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children?: ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

const PRODUCTS = {
  products: [{ id: 'p1', name: 'Alpha', archived: false, clientCount: 0, releaseCount: 0, jiraProjectKeys: ['ABC'] }],
};

function ticket(key: string, summary: string) {
  return {
    key,
    summary,
    description: '',
    issueType: 'Story',
    status: 'Done',
    resolution: 'Done',
    assignee: null,
    reporter: 'rm',
    comments: [],
    commentTotal: 0,
  };
}

const TICKETS = [ticket('ABC-1', 'First ticket'), ticket('ABC-2', 'Second ticket'), ticket('ABC-3', 'Third ticket')];
const PAYMENTS = { id: 'b1', name: 'Payments', ticketKeys: ['ABC-1', 'ABC-2', 'ABC-9'] };

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

type Request = { url: string; method: string; body?: unknown };

// The bundle list is mutable so a POST/DELETE answer reflects what the server would now hold.
function mockApi(initialBundles: { id: string; name: string; ticketKeys: string[] }[]) {
  let bundles = initialBundles;
  const requests: Request[] = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    requests.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : undefined });
    if (url === '/api/jira/projects') return json(200, [{ id: '1', key: 'ABC', name: 'Alpha Core' }]);
    if (url === '/api/products') return json(200, PRODUCTS);
    if (url === '/api/tickets/search') return json(200, { tickets: TICKETS });
    if (url === '/api/products/p1/bundles' && method === 'GET') return json(200, { bundles });
    if (url === '/api/products/p1/bundles' && method === 'POST') {
      const { name, ticketKeys } = JSON.parse(String(init?.body)) as { name: string; ticketKeys: string[] };
      const stripped = bundles
        .map((b) => ({ ...b, ticketKeys: b.ticketKeys.filter((k) => !ticketKeys.includes(k)) }))
        .filter((b) => b.ticketKeys.length > 0);
      const existing = stripped.find((b) => b.name.toLowerCase() === name.toLowerCase());
      bundles = existing
        ? stripped.map((b) => (b === existing ? { ...b, ticketKeys: [...b.ticketKeys, ...ticketKeys] } : b))
        : [...stripped, { id: 'new', name, ticketKeys }];
      return json(200, { bundles });
    }
    if (url.startsWith('/api/products/p1/bundles/') && method === 'DELETE') {
      bundles = bundles.filter((b) => !url.endsWith(`/${b.id}`));
      return json(200, { bundles });
    }
    throw new Error(`Unexpected fetch: ${method} ${url}`);
  });
  return requests;
}

let container: HTMLDivElement;
let root: Root;
const onAction = vi.fn();

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderAndSearch(productId: string | undefined = 'p1') {
  await act(async () => {
    root.render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <TicketBrowser productId={productId} actionLabel="Add to release" onAction={onAction} />
      </QueryClientProvider>,
    );
  });
  await flush();
  await act(async () => button('Load tickets').click());
  await flush();
}

const button = (text: string) => [...container.querySelectorAll('button')].find((b) => b.textContent === text)!;
const byLabel = (label: string) => container.querySelector<HTMLElement>(`[aria-label="${label}"]`)!;
const rowText = () => [...container.querySelectorAll('tbody > tr')].map((tr) => tr.textContent);

async function fire(target: Element, type: 'click' | 'dblclick', detail: number) {
  await act(async () => {
    target.dispatchEvent(new MouseEvent(type, { bubbles: true, detail }));
  });
}

const cell = (text: string) => [...container.querySelectorAll('td')].find((td) => td.textContent === text)!;

describe('TicketBrowser product bundles', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
      this.open = false;
    };
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
    onAction.mockReset();
  });

  it('shows a bundle row with its loaded count and keeps its tickets out of the loose rows', async () => {
    mockApi([PAYMENTS]);
    await renderAndSearch();

    const rows = rowText();
    expect(rows).toHaveLength(2);
    expect(rows[0]).toContain('Payments');
    expect(rows[0]).toContain('2 of 3 tickets');
    expect(rows[1]).toContain('ABC-3');
    expect(container.textContent).not.toContain('First ticket');
  });

  it('expands to list loaded members and the missing keys', async () => {
    mockApi([PAYMENTS]);
    await renderAndSearch();

    await fire(byLabel('Expand bundle'), 'click', 1);

    expect(container.textContent).toContain('First ticket');
    expect(container.textContent).toContain('Second ticket');
    expect(container.textContent).toContain('ABC-9 not in these results');

    await fire(byLabel('Collapse bundle'), 'click', 1);
    expect(container.textContent).not.toContain('First ticket');
  });

  it('a double click expands the bundle row and leaves its selection alone', async () => {
    mockApi([PAYMENTS]);
    await renderAndSearch();

    await fire(cell('2 of 3 tickets'), 'click', 1);
    await fire(cell('2 of 3 tickets'), 'click', 1);
    await fire(cell('2 of 3 tickets'), 'click', 2);
    await fire(cell('2 of 3 tickets'), 'dblclick', 2);

    expect((byLabel('Select bundle Payments') as HTMLInputElement).checked).toBe(true);
    expect(container.textContent).toContain('First ticket');
  });

  it('selecting a bundle and a loose ticket adds one group each, the bundle under its own name', async () => {
    mockApi([PAYMENTS]);
    await renderAndSearch();

    await fire(cell('2 of 3 tickets'), 'click', 1);
    await fire(cell('Third ticket'), 'click', 1);
    expect(container.textContent).toContain('3 selected');
    await act(async () => button('Add to release').click());

    expect(onAction).toHaveBeenCalledWith(
      ['ABC-3', 'ABC-1', 'ABC-2'],
      [{ ticketKeys: ['ABC-3'] }, { ticketKeys: ['ABC-1', 'ABC-2'], bundle: 'Payments' }],
    );
  });

  it('a lone selected bundle sends only its own group', async () => {
    mockApi([PAYMENTS]);
    await renderAndSearch();

    await fire(cell('2 of 3 tickets'), 'click', 1);
    await act(async () => button('Add to release').click());

    expect(onAction).toHaveBeenCalledWith(['ABC-1', 'ABC-2'], [{ ticketKeys: ['ABC-1', 'ABC-2'], bundle: 'Payments' }]);
  });

  it('select all covers the loose rows and the bundle rows', async () => {
    mockApi([PAYMENTS]);
    await renderAndSearch();

    await act(async () => byLabel('Select all loaded tickets').click());
    expect(container.textContent).toContain('3 selected');
    expect((byLabel('Select bundle Payments') as HTMLInputElement).checked).toBe(true);
    expect((byLabel('Select ticket ABC-3') as HTMLInputElement).checked).toBe(true);

    await act(async () => byLabel('Select all loaded tickets').click());
    expect(container.textContent).toContain('0 selected');
  });

  it('Add to bundle offers the existing names, posts the selection, clears it and moves the ticket under the bundle', async () => {
    const requests = mockApi([PAYMENTS]);
    await renderAndSearch();

    await fire(cell('Third ticket'), 'click', 1);
    await act(async () => button('Add to bundle').click());

    const dialog = container.querySelector('dialog')!;
    expect(dialog.open).toBe(true);
    expect([...dialog.querySelectorAll('option')].map((o) => o.getAttribute('value'))).toEqual(['Payments']);

    const input = dialog.querySelector<HTMLInputElement>('#bundleNameInput')!;
    const setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
    await act(async () => {
      setValue.call(input, 'Payments');
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await act(async () => {
      dialog.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(requests.find((r) => r.method === 'POST' && r.url.endsWith('/bundles'))?.body).toEqual({ name: 'Payments', ticketKeys: ['ABC-3'] });
    expect(dialog.open).toBe(false);
    expect(container.textContent).toContain('0 selected');
    expect(rowText()).toHaveLength(1);
    expect(rowText()[0]).toContain('3 of 4 tickets');
  });

  it('Delete bundle sends DELETE and its tickets become loose rows', async () => {
    const requests = mockApi([PAYMENTS]);
    await renderAndSearch();

    await act(async () => button('Delete bundle').click());
    await flush();

    expect(requests.find((r) => r.method === 'DELETE')?.url).toBe('/api/products/p1/bundles/b1');
    expect(rowText()).toHaveLength(3);
    expect(container.textContent).toContain('First ticket');
  });

  it('without a product there is no bundles request and no Add to bundle button', async () => {
    const requests = mockApi([PAYMENTS]);
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      requests.push({ url, method: 'GET' });
      if (url === '/api/jira/projects') return json(200, [{ id: '1', key: 'ABC', name: 'Alpha Core' }]);
      if (url === '/api/tickets/search') return json(200, { tickets: TICKETS });
      throw new Error(`Unexpected fetch: ${url}`);
    });
    requests.length = 0;
    await act(async () => {
      root.render(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <TicketBrowser actionLabel="Add" onAction={onAction} />
        </QueryClientProvider>,
      );
    });
    await flush();
    await act(async () => {
      const select = container.querySelector<HTMLSelectElement>('#projectId')!;
      select.value = '1';
      select.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await act(async () => button('Load tickets').click());
    await flush();
    await fire(cell('First ticket'), 'click', 1);

    expect(requests.some((r) => r.url.includes('/bundles'))).toBe(false);
    expect(container.textContent).toContain('1 selected');
    expect([...container.querySelectorAll('button')].some((b) => b.textContent === 'Add to bundle')).toBe(false);
  });
});
