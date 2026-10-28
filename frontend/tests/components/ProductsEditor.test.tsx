import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProductsEditor } from '../../src/components/ProductsEditor';
import type { Release, ReleaseProduct } from '../../src/api/releases';

vi.mock('@tanstack/react-router', () => ({
  Link: (props: { children?: ReactNode; to?: string }) => <a href={props.to}>{props.children}</a>,
}));

const ALPHA: ReleaseProduct = { id: 'p1', name: 'Alpha', archived: false, hasInstalls: false };
const DELTA: ReleaseProduct = { id: 'p3', name: 'Delta', archived: true, hasInstalls: false };

const CATALOG = {
  products: [
    { id: 'p2', name: 'Bravo', archived: false, clientCount: 0, releaseCount: 0, jiraProjectKeys: [] },
    { id: 'p1', name: 'Alpha', archived: false, clientCount: 0, releaseCount: 0, jiraProjectKeys: ['ALP'] },
    { id: 'p3', name: 'Delta', archived: true, clientCount: 0, releaseCount: 0, jiraProjectKeys: ['DEL'] },
    { id: 'p4', name: 'Echo', archived: true, clientCount: 0, releaseCount: 0, jiraProjectKeys: ['ECH'] },
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
    createdBy: 'rm@example.com',
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

async function click(el: HTMLElement) {
  await act(async () => el.click());
  await flush();
}

function radio(name: string): HTMLInputElement {
  const label = [...container.querySelectorAll('label')].find((l) => l.textContent?.startsWith(name));
  expect(label, `no radio for ${name}`).toBeDefined();
  return label!.querySelector('input')!;
}

function optionLabels(): string[] {
  return [...container.querySelectorAll('label')].map((l) => l.textContent ?? '');
}

function mockApi(putResponse?: Response, catalog: unknown = CATALOG) {
  const puts: unknown[] = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    if (url === '/api/releases/r1/product' && init?.method === 'PUT') {
      puts.push(JSON.parse(String(init.body)));
      return putResponse ?? json(200, release([ALPHA]));
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

  it('shows the single product chip with Change product, or Unassigned with Choose product', async () => {
    mockApi();
    await renderEditor(release([DELTA]));
    expect(container.textContent).toContain('Product:');
    expect(container.textContent).toContain('Delta (archived)');
    expect(container.textContent).not.toContain('Unassigned');
    byText('Change product');

    await act(async () => root.unmount());
    root = createRoot(container);
    await renderEditor(release([]));
    expect(container.textContent).toContain('Unassigned');
    byText('Choose product');
  });

  it('offers one radio per active product plus the archived current one, sorted A to Z', async () => {
    mockApi();
    await renderEditor(release([DELTA]));
    await click(byText('Change product'));
    expect(container.querySelector('fieldset legend')?.textContent).toBe('Product of this release');
    expect(container.querySelectorAll('input[type="radio"]')).toHaveLength(3);
    expect(optionLabels().map((l) => l.split(/archived| · /)[0])).toEqual(['Alpha', 'Bravo', 'Delta']);
    expect(radio('Delta').checked).toBe(true);
  });

  it('marks a product without a Jira project and keeps it selectable', async () => {
    const puts = mockApi();
    await renderEditor(release([ALPHA]));
    await click(byText('Change product'));
    expect(radio('Bravo').closest('label')?.textContent).toContain(' · no Jira project');
    expect(radio('Alpha').closest('label')?.textContent).not.toContain('no Jira project');
    await click(radio('Bravo'));
    expect(radio('Bravo').checked).toBe(true);
    expect(byText('Save product').disabled).toBe(false);
    await click(byText('Save product'));
    expect(puts).toEqual([{ productId: 'p2' }]);
  });

  it('keeps Save disabled until the choice changes', async () => {
    mockApi();
    await renderEditor(release([ALPHA]));
    await click(byText('Change product'));
    expect(byText('Save product').disabled).toBe(true);
    await click(radio('Bravo'));
    expect(byText('Save product').disabled).toBe(false);
    await click(radio('Alpha'));
    expect(byText('Save product').disabled).toBe(true);
  });

  it('saves the chosen product, closes and announces the update', async () => {
    const puts = mockApi();
    await renderEditor(release([ALPHA]));
    await click(byText('Change product'));
    await click(radio('Bravo'));
    await click(byText('Save product'));
    expect(puts).toEqual([{ productId: 'p2' }]);
    expect(container.querySelector('#release-product-editor')).toBeNull();
    expect(container.querySelector('[role="status"]')?.textContent).toBe('Product updated.');
    expect(document.activeElement).toBe(byText('Change product'));
  });

  it('blocks Change product with a visible reason when installs are recorded', async () => {
    mockApi();
    await renderEditor(release([{ ...ALPHA, hasInstalls: true }]));
    const button = byText('Change product');
    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(button.disabled).toBe(false);
    const note = container.querySelector(`#${button.getAttribute('aria-describedby')}`);
    expect(note?.textContent).toBe("Can't change: installs are recorded for this product on this release.");
    await click(button);
    expect(container.querySelector('#release-product-editor')).toBeNull();
  });

  it('is editable on a FROZEN release', async () => {
    mockApi();
    await renderEditor(release([ALPHA], 'FROZEN'));
    await click(byText('Change product'));
    expect(container.querySelector('#release-product-editor')).not.toBeNull();
  });

  it('keeps the panel open with the choice kept on error', async () => {
    mockApi(json(409, { status: 409, reason: 'PRODUCT_HAS_INSTALLS_ON_RELEASE' }));
    await renderEditor(release([ALPHA]));
    await click(byText('Change product'));
    await click(radio('Bravo'));
    await click(byText('Save product'));
    const alert = container.querySelector('[role="alert"][data-reason="PRODUCT_HAS_INSTALLS_ON_RELEASE"]');
    expect(alert?.textContent).toBe("Can't change: installs are recorded for this product on this release.");
    expect(container.querySelector('#release-product-editor')).not.toBeNull();
    expect(radio('Bravo').checked).toBe(true);
  });

  it('closes on Escape and Cancel, returning focus to Change product', async () => {
    mockApi();
    await renderEditor(release([ALPHA]));
    await click(byText('Change product'));
    await act(async () => {
      radio('Bravo').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    });
    expect(container.querySelector('#release-product-editor')).toBeNull();
    expect(document.activeElement).toBe(byText('Change product'));

    await click(byText('Change product'));
    await click(byText('Cancel'));
    expect(container.querySelector('#release-product-editor')).toBeNull();
    expect(document.activeElement).toBe(byText('Change product'));
  });

  it('points to the Portfolio page when no products exist', async () => {
    mockApi(undefined, { products: [] });
    await renderEditor(release([]));
    await click(byText('Choose product'));
    expect(container.textContent).toContain('No products yet. Add one on the');
    expect(container.querySelector('a[href="/portfolio"]')).not.toBeNull();
  });
});
