import { StrictMode, act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { PackagesLink } from '../../src/components/PackagesLink';
import type { Release } from '../../src/api/releases';

const URL_OK = 'https://contoso.sharepoint.com/sites/pkg';

function release(packagesUrl: string | null, status: 'OPEN' | 'FROZEN' = 'OPEN'): Release {
  return {
    id: 'r1',
    name: 'Sprint 42',
    version: '2026.08',
    status,
    jiraProjectKey: null,
    products: [],
    createdAt: '2026-08-05T00:00:00Z',
    createdBy: 'rm@acme.com',
    frozenAt: null,
    frozenBy: null,
    lastRefreshedAt: null,
    items: [],
    manualItems: [],
    unfreezeBlockedReason: null,
    packagesUrl,
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

async function renderLink(r: Release) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <PackagesLink release={r} />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
}

function button(label: string): HTMLButtonElement {
  const found = [...container.querySelectorAll('button')].find(
    (b) => b.textContent === label || b.getAttribute('aria-label') === label,
  );
  expect(found, `no button "${label}"`).toBeDefined();
  return found!;
}

async function click(el: HTMLElement) {
  await act(async () => el.click());
  await flush();
}

async function type(value: string) {
  const input = container.querySelector('input')!;
  await act(async () => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

function mockPut(response?: Response) {
  const puts: unknown[] = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    if (String(input) === '/api/releases/r1/packages-url' && init?.method === 'PUT') {
      puts.push(JSON.parse(String(init.body)));
      return response ?? json(200, release(URL_OK));
    }
    return json(200, {});
  });
  return puts;
}

describe('PackagesLink', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    HTMLDialogElement.prototype.showModal ??= function showModal(this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close ??= function close(this: HTMLDialogElement) {
      this.removeAttribute('open');
    };
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
  });

  it('shows Not set with a Set action', async () => {
    mockPut();
    await renderLink(release(null));
    expect(container.textContent).toContain('Packages:');
    expect(container.textContent).toContain('Not set');
    expect(button('Set packages link')).toBeDefined();
  });

  it('opens a valid link safely in a new tab, with hostname, Change and Clear', async () => {
    mockPut();
    await renderLink(release(URL_OK, 'FROZEN'));
    const anchor = container.querySelector('a')!;
    expect(anchor.textContent).toBe('Open packages');
    expect(anchor.getAttribute('href')).toBe(URL_OK);
    expect(anchor.getAttribute('target')).toBe('_blank');
    expect(anchor.getAttribute('rel')).toBe('noopener noreferrer');
    expect(container.textContent).toContain('(opens in a new tab)');
    expect(container.textContent).toContain('contoso.sharepoint.com');
    expect(button('Change packages link').disabled).toBe(false);
    expect(button('Clear packages link').disabled).toBe(false);
  });

  it('renders no anchor for a saved link that fails the pre-check', async () => {
    mockPut();
    await renderLink(release('javascript:alert(1)'));
    expect(container.querySelector('a')).toBeNull();
    expect(container.textContent).toContain("The saved packages link isn't a valid OneDrive or SharePoint link.");
    expect(button('Change packages link')).toBeDefined();
    expect(button('Clear packages link')).toBeDefined();
  });

  it('refuses an invalid URL on blur without sending anything', async () => {
    const puts = mockPut();
    await renderLink(release(null));
    await click(button('Set packages link'));
    await type('http://x');
    await act(async () => {
      container.querySelector('input')!.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
    });
    const input = container.querySelector('input')!;
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(container.textContent).toContain('Use an https link to OneDrive or SharePoint');
    expect(puts).toEqual([]);
  });

  it('saves a valid URL, closes and announces', async () => {
    const puts = mockPut();
    await renderLink(release(null));
    await click(button('Set packages link'));
    await type(URL_OK);
    await click(button('Save packages link'));
    expect(puts).toEqual([{ url: URL_OK }]);
    expect(container.querySelector('#release-packages-editor')).toBeNull();
    expect(container.querySelector('[role="status"]')!.textContent).toBe('Packages link saved.');
  });

  it('keeps the panel open and shows a server 400', async () => {
    mockPut(json(400, { status: 400, title: 'Bad', reason: 'RELEASE_PACKAGES_URL_INVALID' }));
    await renderLink(release(null));
    await click(button('Set packages link'));
    await type(URL_OK);
    await click(button('Save packages link'));
    const alert = container.querySelector('[role="alert"]')!;
    expect(alert.getAttribute('data-reason')).toBe('RELEASE_PACKAGES_URL_INVALID');
    expect(container.querySelector('#release-packages-editor')).not.toBeNull();
  });

  it('clears the link after confirmation', async () => {
    const puts = mockPut(json(200, release(null)));
    await renderLink(release(URL_OK));
    await click(button('Clear packages link'));
    expect(container.textContent).toContain('Clear the packages link?');
    await click(button('Clear link'));
    expect(puts).toEqual([{ url: null }]);
    expect(container.querySelector('[role="status"]')!.textContent).toBe('Packages link cleared.');
  });
});
