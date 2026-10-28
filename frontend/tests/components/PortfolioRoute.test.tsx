import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { PortfolioRoute } from '../../src/routes/PortfolioRoute';

const navigate = vi.fn();
let searchParams: Record<string, unknown> = {};

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children?: ReactNode; to?: string }) => <a href={to}>{children}</a>,
  useSearch: () => searchParams,
  useNavigate: () => navigate,
}));

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const ACTIVE = { id: 'p1', name: 'Core', archived: false, clientCount: 2, releaseCount: 3 };
const ARCHIVED = { id: 'p2', name: 'Legacy', archived: true, clientCount: 0, releaseCount: 1 };

let container: HTMLDivElement;
let root: Root;
let calls: { url: string; method: string; body?: unknown }[];
let postResponse: Response | null;

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function stubFetch(products = [ACTIVE, ARCHIVED]) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    calls.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : undefined });
    if (method === 'POST' && postResponse) return postResponse;
    if (url === '/api/products') return json(200, { products });
    if (url.startsWith('/api/products/')) return json(200, { products });
    if (url === '/api/clients') return json(200, { clients: [] });
    return json(404, { status: 404, reason: 'NOT_FOUND' });
  });
}

async function renderRoute() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <PortfolioRoute />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
}

function button(label: string) {
  return Array.from(container.querySelectorAll('button')).find((b) => b.textContent === label || b.getAttribute('aria-label') === label);
}

async function click(el: Element | undefined | null) {
  await act(async () => {
    el!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });
  await flush();
}

async function typeAndSubmit(id: string, value: string) {
  const input = container.querySelector<HTMLInputElement>(`#${id}`)!;
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
  await act(async () => {
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await act(async () => {
    input.closest('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
  await flush();
}

describe('PortfolioRoute', () => {
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
    postResponse = null;
    searchParams = {};
    navigate.mockReset();
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
  });

  it('shows the tablist and moves to the Clients tab on ArrowRight', async () => {
    stubFetch();
    await renderRoute();
    const tabs = container.querySelectorAll('[role="tab"]');
    expect(container.querySelector('[role="tablist"]')?.getAttribute('aria-label')).toBe('Portfolio');
    expect(Array.from(tabs).map((t) => t.textContent)).toEqual(['Products', 'Clients']);
    await act(async () => {
      tabs[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    });
    expect(navigate).toHaveBeenCalledWith({ to: '/portfolio', search: { tab: 'clients', client: undefined } });
  });

  it('adds a product by posting the trimmed name', async () => {
    stubFetch();
    await renderRoute();
    await typeAndSubmit('new-product-name', '  Billing ');
    const post = calls.find((c) => c.method === 'POST');
    expect(post).toMatchObject({ url: '/api/products', body: { name: 'Billing' } });
  });

  it('shows the named duplicate copy on a 409', async () => {
    stubFetch();
    postResponse = json(409, { status: 409, reason: 'PRODUCT_NAME_DUPLICATE' });
    await renderRoute();
    await typeAndSubmit('new-product-name', 'Core');
    expect(container.querySelector('[role="alert"]')?.textContent).toBe(
      "A product named 'Core' already exists. Names are not case-sensitive. Choose a different name.",
    );
  });

  it('hides archived products until Show archived is ticked', async () => {
    stubFetch();
    await renderRoute();
    expect(container.textContent).not.toContain('Legacy');
    await click(container.querySelector('input[type="checkbox"]'));
    expect(container.textContent).toContain('Legacy');
    expect(container.textContent).toContain('archived');
  });

  it('archives only after the confirm dialog is accepted', async () => {
    stubFetch();
    await renderRoute();
    await click(button('Archive Core'));
    expect(calls.some((c) => c.url.endsWith('/archive'))).toBe(false);
    const confirm = Array.from(container.querySelectorAll('dialog button')).find((b) => b.textContent === 'Archive');
    await click(confirm);
    expect(calls.find((c) => c.url === '/api/products/p1/archive')?.method).toBe('POST');
  });
});
