import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { PortfolioTab } from '../../src/components/PortfolioTab';
import type { PortfolioBehind, PortfolioMatrix, PortfolioProductRow, PortfolioSummary } from '../../src/api/dashboard';

type LinkProps = {
  children?: ReactNode;
  to?: string;
  params?: Record<string, string>;
  search?: Record<string, string>;
  onClick?: (event: React.MouseEvent) => void;
};

vi.mock('@tanstack/react-router', () => ({
  Link: (props: LinkProps) => {
    let href = props.to ?? '';
    for (const [key, value] of Object.entries(props.params ?? {})) {
      href = href.replace(`$${key}`, value);
    }
    if (props.search) href += `?${new URLSearchParams(props.search).toString()}`;
    return (
      <a href={href} onClick={props.onClick}>
        {props.children}
      </a>
    );
  },
}));

const NEWEST = { releaseId: 'r2', name: 'Sprint 43', version: '2026.09', frozenAt: '2026-09-05T00:00:00Z' };

function productRow(overrides: Partial<PortfolioProductRow> = {}): PortfolioProductRow {
  return {
    id: 'a',
    name: 'Core',
    archived: false,
    releases: { open: 1, frozen: 2, inProduction: 3 },
    newestFrozen: NEWEST,
    linkedClients: 4,
    clientsOnNewest: 1,
    ...overrides,
  };
}

function summary(overrides: Partial<PortfolioSummary> = {}): PortfolioSummary {
  return {
    tiles: { clientsBehind: 3, notInstalled: 2, activeProducts: 2, activeClients: 5, installsLast30Days: 7 },
    products: [productRow(), productRow({ id: 'b', name: 'Billing', newestFrozen: null, clientsOnNewest: 0 })],
    totals: { open: 4, frozen: 5, inProduction: 6 },
    unassigned: null,
    ...overrides,
  };
}

const EMPTY_BEHIND: PortfolioBehind = { behind: [], notInstalled: [] };
const EMPTY_MATRIX: PortfolioMatrix = { products: [], clients: [], cells: [] };

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

let container: HTMLDivElement;
let root: Root;
let summaryResponse: () => Response;
let behindResponse: () => Response;
let matrixResponse: () => Response;
let productsResponse: () => Response;
const requested: string[] = [];
const onProductsChange = vi.fn();
const onArchivedChange = vi.fn();

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderTab(props: { productIds?: string[]; archived?: boolean } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <PortfolioTab
            productIds={props.productIds ?? []}
            archived={props.archived ?? false}
            onProductsChange={onProductsChange}
            onArchivedChange={onArchivedChange}
          />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
  await flush();
}

function button(name: string): HTMLButtonElement {
  const found = [...container.querySelectorAll('button')].find((b) => b.textContent === name || b.getAttribute('aria-label') === name);
  if (!found) throw new Error(`no button ${name}`);
  return found;
}

describe('PortfolioTab', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    requested.length = 0;
    onProductsChange.mockReset();
    onArchivedChange.mockReset();
    summaryResponse = () => json(200, summary());
    behindResponse = () => json(200, EMPTY_BEHIND);
    matrixResponse = () => json(200, EMPTY_MATRIX);
    productsResponse = () => json(200, { products: [{ id: 'a', name: 'Core', archived: false, clientCount: 1, releaseCount: 1 }] });
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = typeof input === 'string' ? input : input.toString();
      requested.push(url);
      if (url.startsWith('/api/dashboard/portfolio/summary')) return summaryResponse();
      if (url.startsWith('/api/dashboard/portfolio/behind')) return behindResponse();
      if (url.startsWith('/api/dashboard/portfolio/matrix')) return matrixResponse();
      if (url.startsWith('/api/products')) return productsResponse();
      return json(404, {});
    });
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
  });

  it('shows the four tiles with the API figures; only the first two are buttons', async () => {
    await renderTab();

    const text = container.textContent!;
    expect(text).toContain('Clients behind');
    expect(text).toContain('client/product pairs');
    expect(text).toContain('linked, nothing installed');
    expect(text).toContain('2 / 5');
    expect(text).toContain('products / clients');
    expect(text).toContain('successful installs');
    expect(container.querySelector('#tile-behind')!.textContent).toContain('3');
    expect(container.querySelector('#tile-not-installed')!.textContent).toContain('2');
    const plain = [...container.querySelectorAll('button')].filter((b) => b.textContent!.includes('Installs last 30 days'));
    expect(plain).toHaveLength(0);
    expect(container.querySelectorAll('button[aria-label="What does this count?"]')).toHaveLength(3);
  });

  it('moves focus to the section headings from the two action tiles', async () => {
    await renderTab({});
    const scroll = vi.fn();
    Element.prototype.scrollIntoView = scroll;

    await act(async () => container.querySelector<HTMLButtonElement>('#tile-behind')!.click());
    expect(document.activeElement!.id).toBe('behind-heading');
    expect(scroll).toHaveBeenCalledWith({ block: 'start' });
  });

  it('toggles a help paragraph without applying anything', async () => {
    await renderTab();

    await act(async () => container.querySelectorAll<HTMLButtonElement>('button[aria-label="What does this count?"]')[0].click());
    expect(container.textContent).toContain('Newest is by frozen date, not by version.');
  });

  it('sets includeArchived from the checkbox and requests with it', async () => {
    await renderTab({ archived: true });
    expect(requested.some((u) => u.includes('includeArchived=true'))).toBe(true);
    const box = container.querySelector<HTMLInputElement>('input[type="checkbox"]')!;
    expect(box.checked).toBe(true);
    await act(async () => box.click());
    expect(onArchivedChange).toHaveBeenCalledWith(false);
  });

  it('renders product rows with newest frozen, clients on newest and totals', async () => {
    await renderTab();

    const rows = [...container.querySelectorAll('tbody tr')];
    const cells = [...rows[0].querySelectorAll('td')].map((c) => c.textContent);
    expect(cells[0]).toBe('Core');
    expect(cells.slice(1, 4)).toEqual(['1', '2', '3']);
    expect(cells[4]).toContain('Sprint 43 2026.09');
    expect(rows[0].querySelector('a')!.getAttribute('href')).toBe('/releases/r2');
    expect(cells[5]).toBe('1 of 4 clients');
    const second = [...rows[1].querySelectorAll('td')];
    expect(second[4].textContent).toBe('—');
    expect(second[4].querySelector('[title="No frozen release yet"]')).toBeTruthy();
    expect(second[5].textContent).toBe('—');
    const foot = container.querySelector('tfoot')!;
    expect(foot.textContent).toContain('All products');
    expect(foot.textContent).toContain('456');
  });

  it('says all up to date, tags archived products and adds an Unassigned row', async () => {
    summaryResponse = () =>
      json(
        200,
        summary({
          products: [productRow({ clientsOnNewest: 4, archived: true })],
          unassigned: { open: 1, frozen: 0, inProduction: 0 },
        }),
      );
    await renderTab({ productIds: ['a', 'b'] });

    const text = container.querySelector('tbody')!.textContent!;
    expect(text).toContain('4 of 4 clients');
    expect(text).toContain('all up to date');
    expect(text).toContain('archived');
    expect(text).toContain('Unassigned');
    expect(container.querySelector('tfoot')!.textContent).toContain('All selected products');
  });

  it('filters to a product on row click and on the name button, once', async () => {
    await renderTab();

    await act(async () => container.querySelector<HTMLTableRowElement>('tbody tr')!.click());
    expect(onProductsChange).toHaveBeenLastCalledWith(['a']);

    onProductsChange.mockReset();
    await act(async () => button('Filter dashboard to Core').click());
    expect(onProductsChange).toHaveBeenCalledTimes(1);
    expect(onProductsChange).toHaveBeenCalledWith(['a']);
  });

  it('clears the filter when the sole selected product is pressed and marks it pressed', async () => {
    await renderTab({ productIds: ['a'] });

    const name = button('Filter dashboard to Core');
    expect(name.getAttribute('aria-pressed')).toBe('true');
    expect(name.closest('tr')!.className).toContain('bg-gray-100');
    expect(button('Filter dashboard to Billing').getAttribute('aria-pressed')).toBe('false');
    await act(async () => name.click());
    expect(onProductsChange).toHaveBeenCalledWith([]);
  });

  it('does not change the filter when the newest frozen link is followed', async () => {
    await renderTab();

    const link = container.querySelector<HTMLAnchorElement>('tbody a')!;
    link.addEventListener('click', (e) => e.preventDefault());
    await act(async () => link.click());
    expect(onProductsChange).not.toHaveBeenCalled();
  });

  it('shows a loading line, then an error with a retry, for the summary', async () => {
    summaryResponse = () => json(500, { reason: 'server_error' });
    await renderTab();

    const alert = container.querySelector('[role="alert"]')!;
    expect(alert.textContent).toContain("Couldn't load the portfolio — ");
    expect(alert.textContent).toContain('Try again.');
    expect(container.querySelector('tbody')).toBeNull();
    expect(button('Try again')).toBeTruthy();
  });

  it('shows No products yet and renders no other section when there are no products', async () => {
    productsResponse = () => json(200, { products: [] });
    summaryResponse = () => json(200, summary({ products: [] }));
    await renderTab();

    expect(container.textContent).toContain('No products yet');
    expect(container.querySelector('a[href="/portfolio"]')!.textContent).toBe('Portfolio page');
    expect(container.querySelector('#behind-heading')).toBeNull();
    expect(container.querySelector('table')).toBeNull();
  });

  describe('client x product matrix', () => {
    const RUN = { releaseId: 'r1', name: 'Sprint 42', version: '2026.08', installedAt: '2026-08-10T00:00:00Z', inProduction: true };
    const cell = (clientId: string, productId: string, overrides: Record<string, unknown> = {}) => ({
      clientId,
      productId,
      run: RUN,
      releasesBehind: 0,
      newest: NEWEST,
      upToDate: true,
      ...overrides,
    });
    const matrix = (cells: unknown[], overrides: Record<string, unknown> = {}) => ({
      products: [
        { id: 'a', name: 'Core', archived: false },
        { id: 'b', name: 'Billing', archived: true },
      ],
      clients: [
        { id: 'c1', name: 'Acme', archived: false },
        { id: 'c2', name: 'Zed', archived: true },
      ],
      cells,
      ...overrides,
    });
    const mx = () => container.querySelector('section[aria-labelledby="matrix-heading"]')!;
    const cellAt = (row: number, col: number) => mx().querySelectorAll('tbody tr')[row].querySelectorAll('td')[col];

    it('renders the three cell kinds, row headers and archived tags', async () => {
      matrixResponse = () =>
        json(
          200,
          matrix([
            cell('c1', 'a'),
            cell('c1', 'b', { run: null, releasesBehind: null, newest: null, upToDate: false }),
            cell('c2', 'a', { releasesBehind: 2, upToDate: false, run: { ...RUN, inProduction: false } }),
          ]),
        );
      await renderTab();

      expect(mx().querySelector('h3')!.textContent).toBe('Client x product');
      const heads = [...mx().querySelectorAll('thead th')].map((h) => h.textContent);
      expect(heads).toEqual(['Client', 'Core', 'Billingarchived']);
      const rowHead = mx().querySelectorAll('tbody th[scope="row"]');
      expect(rowHead[0].querySelector('a')!.getAttribute('href')).toBe('/portfolio?tab=clients&client=c1');
      expect(rowHead[1].textContent).toContain('archived');

      expect(cellAt(0, 0).querySelector('a')!.getAttribute('href')).toBe('/releases/r1');
      expect(cellAt(0, 0).textContent).toContain('Sprint 42 2026.08');
      expect(cellAt(0, 0).textContent).toContain('Installed ');
      expect(cellAt(0, 0).textContent).toContain('Up to date');
      expect(cellAt(0, 0).textContent).not.toContain('not in Production');
      expect(cellAt(0, 1).textContent).toBe('Not installed');
      expect(cellAt(1, 0).textContent).toContain('not in Production');
      expect(cellAt(1, 0).textContent).toContain('2 releases behind');
      expect(cellAt(1, 0).textContent).toContain('newest is Sprint 43');
      const unlinked = cellAt(1, 1).querySelector('[aria-label="Not linked to Billing"]')!;
      expect(unlinked.textContent).toBe('—');
    });

    it('shows nothing about being behind when the product has no frozen release', async () => {
      matrixResponse = () =>
        json(200, matrix([cell('c1', 'a', { releasesBehind: null, newest: null, upToDate: false })]));
      await renderTab();

      expect(cellAt(0, 0).textContent).not.toContain('behind');
      expect(cellAt(0, 0).textContent).not.toContain('Up to date');
    });

    it('uses the singular marker for one release behind', async () => {
      matrixResponse = () => json(200, matrix([cell('c1', 'a', { releasesBehind: 1, upToDate: false })]));
      await renderTab();

      expect(cellAt(0, 0).textContent).toContain('1 release behind');
    });

    it('shows No clients yet with a link to the Portfolio page', async () => {
      matrixResponse = () => json(200, matrix([], { clients: [] }));
      await renderTab();

      expect(mx().textContent).toContain('No clients yet');
      expect(mx().textContent).toContain('Add a client on the Portfolio page, then link it to the products it runs.');
      expect(mx().querySelector('a[href="/portfolio"]')!.textContent).toBe('Portfolio page');
    });

    it('offers Clear products when the filter leaves no columns', async () => {
      matrixResponse = () => json(200, matrix([], { products: [] }));
      await renderTab({ productIds: ['zzz'] });

      expect(mx().textContent).toContain('No products match this filter');
      await act(async () => [...mx().querySelectorAll('button')].find((b) => b.textContent === 'Clear products')!.click());
      expect(onProductsChange).toHaveBeenCalledWith([]);
    });

    it('fails on its own with a retry', async () => {
      matrixResponse = () => json(500, { status: 500, reason: 'INTERNAL_ERROR' });
      await renderTab();

      expect(mx().querySelector('[role="alert"]')!.textContent).toContain("Couldn't load the client matrix — ");
      expect([...mx().querySelectorAll('button')].some((b) => b.textContent === 'Try again')).toBe(true);
      expect(container.querySelector('#behind-heading')).toBeTruthy();
    });
  });

  describe('clients behind', () => {
    const RUN = { releaseId: 'r1', name: 'Sprint 42', version: '2026.08', installedAt: '2026-08-10T00:00:00Z', inProduction: true };
    const behindRow = (overrides: Record<string, unknown> = {}) => ({
      clientId: 'c1',
      clientName: 'Acme',
      clientArchived: false,
      productId: 'a',
      productName: 'Core',
      runs: RUN,
      releasesBehind: 2,
      newest: NEWEST,
      ...overrides,
    });
    const section = () => container.querySelector('#behind')!;

    it('lists behind rows in API order with links, markers and sr-only sentence', async () => {
      behindResponse = () =>
        json(200, {
          behind: [
            behindRow(),
            behindRow({ clientId: 'c2', clientName: 'Zed', releasesBehind: 1, runs: { ...RUN, inProduction: false } }),
          ],
          notInstalled: [],
        });
      await renderTab();

      expect(section().querySelector('h3')!.id).toBe('behind-heading');
      expect(section().textContent).toContain('whether or not it has reached Production.');
      const rows = [...section().querySelectorAll('tbody tr')];
      expect(rows.map((r) => r.querySelector('td')!.textContent)).toEqual(['Acme', 'Zed']);
      const first = rows[0].querySelectorAll('td');
      expect(first[0].querySelector('a')!.getAttribute('href')).toBe('/portfolio?tab=clients&client=c1');
      expect(first[2].querySelector('a')!.getAttribute('href')).toBe('/releases/r1');
      expect(first[2].textContent).not.toContain('not in Production');
      expect(first[3].textContent).toContain('2 releases behind');
      expect(first[3].querySelector('.sr-only')!.textContent).toBe('2 releases behind: newest is Sprint 43');
      expect(first[4].querySelector('a')!.getAttribute('href')).toBe('/releases/r2');
      const second = rows[1].querySelectorAll('td');
      expect(second[2].textContent).toContain('not in Production');
      expect(second[3].textContent).toContain('1 release behind');
      expect(second[3].textContent).not.toContain('1 releases behind');
    });

    it('tags archived clients', async () => {
      behindResponse = () => json(200, { behind: [behindRow({ clientArchived: true })], notInstalled: [] });
      await renderTab();

      expect(section().querySelector('tbody td')!.textContent).toContain('archived');
    });

    it('says everyone is up to date when nothing is behind', async () => {
      await renderTab();

      expect(section().textContent).toContain('Everyone is up to date');
      expect(section().textContent).toContain('Every client that has an install runs the newest frozen release of its product.');
      expect(section().querySelector('#not-installed')).toBeNull();
    });

    it('lists the Not installed group with its count and newest available release', async () => {
      const notInstalled = (clientId: string, newest: unknown) => ({
        clientId,
        clientName: `Client ${clientId}`,
        clientArchived: false,
        productId: 'a',
        productName: 'Core',
        newest,
      });
      behindResponse = () => json(200, { behind: [], notInstalled: [notInstalled('x', NEWEST), notInstalled('y', null)] });
      await renderTab();

      const group = container.querySelector('#not-installed')!;
      expect(group.querySelector('h4')!.id).toBe('not-installed-heading');
      expect(group.querySelector('h4')!.textContent).toBe('Not installed (2)');
      expect(group.textContent).toContain('Linked to the product, nothing installed yet. Not counted as behind.');
      const items = [...group.querySelectorAll('li')];
      expect(items[0].textContent).toContain('Newest available: Sprint 43 2026.09');
      expect(items[0].querySelector('a[href="/releases/r2"]')).toBeTruthy();
      expect(items[1].textContent).toContain('No frozen release yet');
    });

    it('focuses the Not installed heading from its tile', async () => {
      behindResponse = () =>
        json(200, { behind: [], notInstalled: [{ clientId: 'x', clientName: 'X', clientArchived: false, productId: 'a', productName: 'Core', newest: null }] });
      await renderTab();
      Element.prototype.scrollIntoView = vi.fn();

      await act(async () => container.querySelector<HTMLButtonElement>('#tile-not-installed')!.click());
      expect(document.activeElement!.id).toBe('not-installed-heading');
    });

    it('fails on its own: the products table still renders', async () => {
      behindResponse = () => json(500, { status: 500, reason: 'INTERNAL_ERROR' });
      await renderTab();

      const alert = section().querySelector('[role="alert"]')!;
      expect(alert.textContent).toContain("Couldn't load clients behind — ");
      expect(alert.getAttribute('data-reason')).toBe('INTERNAL_ERROR');
      expect(container.querySelector('#products-heading')).toBeTruthy();
      expect(container.querySelectorAll('tbody tr').length).toBeGreaterThan(0);
    });

    it('shows a loading line while the request is pending', async () => {
      behindResponse = () => json(200, EMPTY_BEHIND);
      vi.mocked(globalThis.fetch).mockImplementation(async (input) => {
        const url = String(input);
        if (url.includes('/portfolio/behind')) return new Promise<Response>(() => {});
        if (url.includes('/portfolio/summary')) return summaryResponse();
        if (url.startsWith('/api/products')) return productsResponse();
        return matrixResponse();
      });
      await renderTab();

      expect(section().textContent).toContain('Loading clients behind…');
    });
  });
});
