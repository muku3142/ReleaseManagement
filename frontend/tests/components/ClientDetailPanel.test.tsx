import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ClientDetailPanel } from '../../src/components/ClientDetailPanel';
import type { ClientDetail } from '../../src/api/clients';

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, params }: { children?: ReactNode; to?: string; params?: Record<string, string> }) => (
    <a href={(to ?? '').replace('$releaseId', params?.releaseId ?? '')}>{children}</a>
  ),
}));

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const entry = (overrides: Record<string, unknown> = {}) => ({
  id: 'i1',
  productId: 'p1',
  productName: 'Core',
  releaseId: 'r1',
  releaseName: 'Sprint 1',
  releaseVersion: '1.0',
  inProduction: true,
  outcome: 'SUCCESS',
  installedAt: '2026-08-01T10:00:00Z',
  recordedAt: '2026-08-01T11:00:00Z',
  recordedBy: 'rm@example.com',
  note: null,
  supersedesId: null,
  supersededById: null,
  correctionReason: null,
  effective: true,
  ...overrides,
});

const DETAIL = {
  id: 'c1',
  name: 'Acme',
  archived: false,
  runs: [
    {
      productId: 'p1',
      productName: 'Core',
      productArchived: false,
      hasInstalls: true,
      current: { releaseId: 'r1', releaseName: 'Sprint 1', releaseVersion: '1.0', installedAt: '2026-08-01T10:00:00Z', inProduction: false },
    },
    { productId: 'p2', productName: 'Reports', productArchived: false, hasInstalls: false, current: null },
  ],
  history: [entry(), entry({ id: 'i0', effective: false, supersededById: 'i1' })],
} as unknown as ClientDetail;

const PRODUCTS = [
  { id: 'p1', name: 'Core', archived: false, clientCount: 1, releaseCount: 1 },
  { id: 'p2', name: 'Reports', archived: false, clientCount: 1, releaseCount: 0 },
  { id: 'p3', name: 'Mobile', archived: false, clientCount: 0, releaseCount: 0 },
  { id: 'p4', name: 'Old', archived: true, clientCount: 0, releaseCount: 0 },
];

let container: HTMLDivElement;
let root: Root;
let calls: { url: string; method: string }[];

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderPanel() {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    calls.push({ url, method });
    if (url.startsWith('/api/clients/c1')) return json(200, DETAIL);
    if (url === '/api/products') return json(200, { products: PRODUCTS });
    return json(404, { status: 404, reason: 'NOT_FOUND' });
  });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <ClientDetailPanel clientId="c1" onShowProducts={() => {}} />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
}

function button(label: string) {
  return Array.from(container.querySelectorAll('button')).find((b) => b.getAttribute('aria-label') === label || b.textContent === label)!;
}

async function click(el: Element) {
  await act(async () => {
    el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });
  await flush();
}

describe('ClientDetailPanel', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    HTMLDialogElement.prototype.showModal = function () {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function () {
      this.removeAttribute('open');
    };
  });

  beforeEach(() => {
    calls = [];
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
  });

  it('shows the current release link and the not-in-Production marker', async () => {
    await renderPanel();
    const link = container.querySelector('table a')!;
    expect(link.textContent).toBe('Sprint 1 1.0');
    expect(link.getAttribute('href')).toBe('/releases/r1');
    expect(container.querySelector('table')!.textContent).toContain('not in Production');
  });

  it('shows Not installed for a run with no current release', async () => {
    await renderPanel();
    expect(container.querySelector('table')!.textContent).toContain('Not installed');
  });

  it('blocks unlink with the explanation when installs exist and sends nothing', async () => {
    await renderPanel();
    const unlink = button('Unlink Core');
    expect(unlink.getAttribute('aria-disabled')).toBe('true');
    expect(container.textContent).toContain(
      "Can't unlink: Acme has install records for Core. Archive the client instead to stop new installs.",
    );
    const before = calls.length;
    await click(unlink);
    expect(calls.length).toBe(before);
  });

  it('unlinks a run without installs after confirmation', async () => {
    await renderPanel();
    await click(button('Unlink Reports'));
    await click(Array.from(container.querySelectorAll('dialog button')).find((b) => b.textContent === 'Unlink')!);
    expect(calls).toContainEqual({ url: '/api/clients/c1/products/p2', method: 'DELETE' });
  });

  it('marks superseded history rows', async () => {
    await renderPanel();
    expect(container.querySelector('ul')!.textContent).toContain('Superseded');
  });

  it('offers only active unlinked products and links on Link', async () => {
    await renderPanel();
    const options = Array.from(container.querySelectorAll('#link-product option')).map((o) => o.textContent);
    expect(options).toEqual(['Choose a product…', 'Mobile']);
    const select = container.querySelector<HTMLSelectElement>('#link-product')!;
    const setter = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')!.set!;
    await act(async () => {
      setter.call(select, 'p3');
      select.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await click(button('Link'));
    expect(calls).toContainEqual({ url: '/api/clients/c1/products/p3', method: 'PUT' });
  });
});
