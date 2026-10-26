import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProductsEditor } from '../../src/components/ProductsEditor';
import type { Release, ReleaseProduct } from '../../src/api/releases';

vi.mock('@tanstack/react-router', () => ({
  Link: (props: { children?: ReactNode; to?: string }) => <a href={props.to}>{props.children}</a>,
}));

const CORE: ReleaseProduct = { id: 'p1', name: 'Core', archived: false, hasInstalls: false };
const LEGACY: ReleaseProduct = { id: 'p3', name: 'Legacy', archived: true, hasInstalls: false };

const CATALOG = {
  products: [
    { id: 'p1', name: 'Core', archived: false, clientCount: 0, releaseCount: 0 },
    { id: 'p2', name: 'Mobile', archived: false, clientCount: 0, releaseCount: 0 },
    { id: 'p3', name: 'Legacy', archived: true, clientCount: 0, releaseCount: 0 },
    { id: 'p4', name: 'Old', archived: true, clientCount: 0, releaseCount: 0 },
  ],
};

function release(products: ReleaseProduct[], status: 'OPEN' | 'FROZEN' = 'OPEN'): Release {
  return {
    id: 'r1',
    name: 'Sprint 42',
    version: '2026.08',
    status,
    jiraProjectKey: null,
    products,
    createdAt: '2026-08-05T00:00:00Z',
    createdBy: 'rm@acme.com',
    frozenAt: null,
    frozenBy: null,
    lastRefreshedAt: null,
    items: [],
    manualItems: [],
    unfreezeBlockedReason: null,

    packagesUrl: null,
  };
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderEditor(r: Release) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <ProductsEditor release={r} />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
}

function byText(text: string): HTMLButtonElement {
  const button = [...container.querySelectorAll('button')].find((b) => b.textContent === text);
  expect(button, `no button labelled "${text}"`).toBeDefined();
  return button!;
}

async function openEditor() {
  await act(async () => byText('Edit products').click());
  await flush();
}

function checkbox(name: string): HTMLInputElement {
  const label = [...container.querySelectorAll('label')].find((l) => l.textContent?.startsWith(name));
  expect(label, `no checkbox for ${name}`).toBeDefined();
  return label!.querySelector('input')!;
}

async function click(el: HTMLElement) {
  await act(async () => el.click());
}

function mockApi(putResponse?: Response, catalog: unknown = CATALOG) {
  const puts: unknown[] = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    if (url === '/api/releases/r1/products' && init?.method === 'PUT') {
      puts.push(JSON.parse(String(init.body)));
      return putResponse ?? json(200, release([CORE]));
    }
    if (url === '/api/products') return json(200, catalog);
    return json(200, {});
  });
  return puts;
}

describe('ProductsEditor', () => {
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

  it('shows product chips, and Unassigned only for a release with no products', async () => {
    mockApi();
    await renderEditor(release([CORE, LEGACY]));
    expect(container.textContent).toContain('Products:');
    expect(container.textContent).toContain('Core');
    expect(container.textContent).toContain('Legacy (archived)');
    expect(container.textContent).not.toContain('Unassigned');

    await act(async () => root.unmount());
    root = createRoot(container);
    await renderEditor(release([]));
    expect(container.textContent).toContain('Unassigned');
  });

  it('offers Edit products on a FROZEN release', async () => {
    mockApi();
    await renderEditor(release([CORE], 'FROZEN'));
    expect(byText('Edit products')).toBeDefined();
  });

  it('locks a product with installs and explains why', async () => {
    mockApi();
    await renderEditor(release([{ ...CORE, hasInstalls: true }, { id: 'p2', name: 'Mobile', archived: false, hasInstalls: false }]));
    await openEditor();
    const core = checkbox('Core');
    expect(core.checked).toBe(true);
    expect(core.disabled).toBe(true);
    expect(core.getAttribute('aria-disabled')).toBe('true');
    expect(container.textContent).toContain("Can't remove: installs are recorded for this product on this release.");
    await click(core);
    expect(core.checked).toBe(true);
  });

  it('does not let an archived product be newly checked', async () => {
    mockApi();
    await renderEditor(release([CORE, LEGACY]));
    await openEditor();
    expect(checkbox('Legacy').checked).toBe(true);
    expect(checkbox('Legacy').disabled).toBe(false);
    expect(container.textContent).not.toContain('Old');
  });

  it('disables an unchecked archived product that is still assigned after unticking', async () => {
    mockApi();
    await renderEditor(release([CORE, LEGACY]));
    await openEditor();
    await click(checkbox('Legacy'));
    expect(checkbox('Legacy').checked).toBe(false);
    expect(checkbox('Legacy').disabled).toBe(true);
  });

  it('locks the last checked product and keeps it checked on click', async () => {
    mockApi();
    await renderEditor(release([CORE]));
    await openEditor();
    const core = checkbox('Core');
    expect(core.getAttribute('aria-disabled')).toBe('true');
    expect(core.disabled).toBe(false);
    expect(container.textContent).toContain('A release needs at least one product.');
    await click(core);
    expect(core.checked).toBe(true);
  });

  it('locks the remaining product once one of two is unticked, and releases it when another is ticked', async () => {
    mockApi();
    await renderEditor(release([CORE, { id: 'p2', name: 'Mobile', archived: false, hasInstalls: false }]));
    await openEditor();
    expect(checkbox('Core').getAttribute('aria-disabled')).toBeNull();
    await click(checkbox('Mobile'));
    expect(checkbox('Mobile').checked).toBe(false);
    expect(checkbox('Core').getAttribute('aria-disabled')).toBe('true');
    await click(checkbox('Mobile'));
    expect(checkbox('Core').getAttribute('aria-disabled')).toBeNull();
  });

  it('keeps Save disabled for a zero-product release until one is ticked', async () => {
    mockApi();
    await renderEditor(release([]));
    await openEditor();
    expect(byText('Save products').disabled).toBe(true);
    await click(checkbox('Core'));
    expect(byText('Save products').disabled).toBe(false);
  });

  it('PUTs the selected ids and closes with an announcement', async () => {
    const puts = mockApi();
    await renderEditor(release([CORE]));
    await openEditor();
    await click(checkbox('Mobile'));
    await click(checkbox('Core'));
    await click(byText('Save products'));
    await flush();
    expect(puts).toEqual([{ productIds: ['p2'] }]);
    expect(container.querySelector('#release-products-editor')).toBeNull();
    expect(container.querySelector('[role="status"]')?.textContent).toBe('Products updated.');
  });

  it('shows the staleness note for OPEN releases only', async () => {
    mockApi();
    await renderEditor(release([CORE], 'OPEN'));
    await openEditor();
    expect(container.textContent).toContain('marks this release');

    await act(async () => root.unmount());
    root = createRoot(container);
    await renderEditor(release([CORE], 'FROZEN'));
    await openEditor();
    expect(container.querySelector('#release-products-editor')).not.toBeNull();
    expect(container.textContent).not.toContain('marks this release');
  });

  it('renders the install-lock copy for a 409 PRODUCT_HAS_INSTALLS_ON_RELEASE', async () => {
    mockApi(json(409, { status: 409, reason: 'PRODUCT_HAS_INSTALLS_ON_RELEASE' }));
    await renderEditor(release([CORE]));
    await openEditor();
    await click(checkbox('Mobile'));
    await click(byText('Save products'));
    await flush();
    expect(container.querySelector('[data-reason="PRODUCT_HAS_INSTALLS_ON_RELEASE"]')?.textContent).toContain(
      "Can't remove: installs are recorded for this product on this release.",
    );
  });

  it('renders the last-product copy for a 409 RELEASE_LAST_PRODUCT', async () => {
    mockApi(json(409, { status: 409, reason: 'RELEASE_LAST_PRODUCT' }));
    await renderEditor(release([CORE]));
    await openEditor();
    await click(checkbox('Mobile'));
    await click(byText('Save products'));
    await flush();
    expect(container.querySelector('[data-reason="RELEASE_LAST_PRODUCT"]')?.textContent).toBe(
      'A release needs at least one product.',
    );
  });

  it('points to the Portfolio page when no products exist', async () => {
    mockApi(undefined, { products: [] });
    await renderEditor(release([]));
    await openEditor();
    expect(container.textContent).toContain('No products yet. Add one on the');
    expect(container.querySelector('a[href="/portfolio"]')).not.toBeNull();
  });
});
