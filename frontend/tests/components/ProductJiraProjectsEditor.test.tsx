import { StrictMode, act, useState } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProductJiraProjectsEditor } from '../../src/components/ProductJiraProjectsEditor';
import type { Product } from '../../src/api/products';

function product(over: Partial<Product> = {}): Product {
  return { id: 'p1', name: 'Core', archived: false, clientCount: 0, releaseCount: 0, jiraProjectKeys: ['CNV', 'ABC'], ...over };
}

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

function Harness({ p }: { p: Product }) {
  const [open, setOpen] = useState(false);
  return <ProductJiraProjectsEditor product={p} open={open} onToggle={() => setOpen((o) => !o)} />;
}

async function renderEditor(p: Product, response?: Response) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    calls.push({ url: String(input), method: init?.method ?? 'GET', body: init?.body ? JSON.parse(String(init.body)) : undefined });
    return response ?? json(200, { products: [p] });
  });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <Harness p={p} />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
}

function button(label: string) {
  return Array.from(container.querySelectorAll('button')).find(
    (b) => b.textContent === label || b.getAttribute('aria-label') === label,
  );
}

async function click(el: Element | undefined | null) {
  await act(async () => {
    el!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });
  await flush();
}

async function type(value: string) {
  const input = container.querySelector<HTMLInputElement>('input')!;
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
  await act(async () => {
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

async function save() {
  await act(async () => {
    container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
  await flush();
}

beforeAll(() => {
  (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
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

describe('ProductJiraProjectsEditor', () => {
  it('shows chips and an Edit button when keys are set', async () => {
    await renderEditor(product());
    expect(container.textContent).toContain('Jira projects:');
    expect(container.textContent).toContain('CNV');
    expect(container.textContent).toContain('ABC');
    expect(button('Edit Jira projects for Core')?.textContent).toBe('Edit Jira projects');
  });

  it('uses the singular label for one key', async () => {
    await renderEditor(product({ jiraProjectKeys: ['CNV'] }));
    expect(container.textContent).toContain('Jira project:');
  });

  it('says briefings skip a product with no keys', async () => {
    await renderEditor(product({ jiraProjectKeys: [] }));
    expect(container.textContent).toContain('No Jira project');
    expect(container.textContent).toContain('Briefings skip this product.');
    expect(button('Set Jira project for Core')?.textContent).toBe('Set Jira project');
  });

  it('shows archived keys read-only without an edit button', async () => {
    await renderEditor(product({ archived: true }));
    expect(container.textContent).toContain('CNV');
    expect(container.querySelector('button')).toBeNull();
    expect(container.textContent).toContain("Archived products don't run briefings.");
  });

  it('opens a prefilled editor wired to the button, with Save disabled until changed', async () => {
    await renderEditor(product());
    const edit = button('Edit Jira projects for Core')!;
    await click(edit);
    expect(edit.getAttribute('aria-expanded')).toBe('true');
    expect(edit.getAttribute('aria-controls')).toBe('product-p1-jira-editor');
    expect(container.querySelector('#product-p1-jira-editor')).not.toBeNull();
    expect(container.querySelector('input')!.value).toBe('CNV, ABC');
    expect((button('Save Jira projects') as HTMLButtonElement).disabled).toBe(true);
  });

  it('upper-cases on blur', async () => {
    await renderEditor(product());
    await click(button('Edit Jira projects for Core'));
    await type('xyz');
    const input = container.querySelector('input')!;
    await act(async () => {
      input.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
    });
    expect(input.value).toBe('XYZ');
  });

  it('rejects an invalid key client-side and sends nothing', async () => {
    await renderEditor(product());
    await click(button('Edit Jira projects for Core'));
    await type('c-1');
    await save();
    expect(container.querySelector('[role="alert"]')?.textContent).toBe(
      '“C-1” isn\'t a valid Jira project key. Keys are letters and digits, starting with a letter, for example CNV.',
    );
    expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(0);
  });

  it('saves valid keys, closes, returns focus and announces', async () => {
    await renderEditor(product());
    const edit = button('Edit Jira projects for Core')!;
    await click(edit);
    await type('cnv, new');
    await save();
    const put = calls.find((c) => c.method === 'PUT')!;
    expect(put.url).toBe('/api/products/p1/jira-projects');
    expect(put.body).toEqual({ keys: ['CNV', 'NEW'] });
    expect(container.querySelector('#product-p1-jira-editor')).toBeNull();
    expect(document.activeElement).toBe(edit);
    expect(container.querySelector('[role="status"]')?.textContent).toBe('Jira projects saved for Core.');
  });

  it('shows the taken-key copy with data-reason and keeps the typed value', async () => {
    await renderEditor(
      product(),
      json(409, { status: 409, reason: 'PRODUCT_JIRA_KEY_TAKEN', key: 'CNV', productName: 'Core' }),
    );
    await click(button('Edit Jira projects for Core'));
    await type('cnv, zzz');
    await save();
    const alert = container.querySelector('[role="alert"]')!;
    expect(alert.textContent).toBe('“CNV” already belongs to Core.');
    expect(alert.getAttribute('data-reason')).toBe('PRODUCT_JIRA_KEY_TAKEN');
    expect(container.querySelector('input')!.value).toBe('cnv, zzz');
  });

  it('falls back to messageForReason when the taken response lacks the key', async () => {
    await renderEditor(product(), json(409, { status: 409, reason: 'PRODUCT_JIRA_KEY_TAKEN' }));
    await click(button('Edit Jira projects for Core'));
    await type('zzz');
    await save();
    expect(container.querySelector('[role="alert"]')?.textContent).toBe(
      'That Jira project key already belongs to another product. A key can belong to one product only.',
    );
  });

  it('Cancel closes without saving', async () => {
    await renderEditor(product());
    await click(button('Edit Jira projects for Core'));
    await click(button('Cancel'));
    expect(container.querySelector('#product-p1-jira-editor')).toBeNull();
    expect(calls).toHaveLength(0);
  });

  it('clearing the field saves an empty key list', async () => {
    await renderEditor(product());
    await click(button('Edit Jira projects for Core'));
    await type('');
    await save();
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ keys: [] });
  });
});
