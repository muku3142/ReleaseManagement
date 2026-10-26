import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ClientInstallsSection } from '../../src/components/ClientInstallsSection';
import type { InstallEntry, InstallHistory } from '../../src/api/installs';

vi.mock('@tanstack/react-router', () => ({
  Link: (props: { children?: ReactNode; to?: string }) => <a href={props.to}>{props.children}</a>,
}));

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function entry(id: string, extra: Partial<InstallEntry> = {}): InstallEntry {
  return {
    id,
    productId: 'p1',
    productName: 'Core',
    clientId: 'c1',
    clientName: 'Acme Bank',
    outcome: 'SUCCESS',
    installedAt: '2026-10-10T09:00:00Z',
    recordedAt: '2026-10-10T09:05:00Z',
    recordedBy: 'rm@acme.com',
    note: null,
    supersedesId: null,
    supersededById: null,
    correctionReason: null,
    effective: true,
    ...extra,
  };
}

function history(extra: Partial<InstallHistory> = {}): InstallHistory {
  return {
    releaseId: 'r1',
    frozen: true,
    inProduction: true,
    products: [{ id: 'p1', name: 'Core', archived: false }],
    entries: [],
    ...extra,
  };
}

let container: HTMLDivElement;
let root: Root;
let posts: { url: string; body: Record<string, unknown> }[];

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function mockApi(h: InstallHistory) {
  posts = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    if (init?.method === 'POST') {
      posts.push({ url: String(input), body: JSON.parse(String(init.body)) });
      return json(201, h);
    }
    if (String(input) === '/api/clients') return json(200, { clients: [] });
    return json(200, h);
  });
}

async function renderSection(h: InstallHistory, isFrozen = true) {
  mockApi(h);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <ClientInstallsSection releaseId="r1" isFrozen={isFrozen} />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
  await flush();
}

function byLabel(label: string): HTMLButtonElement {
  const button = container.querySelector(`button[aria-label="${label}"]`);
  expect(button, `no button ${label}`).not.toBeNull();
  return button as HTMLButtonElement;
}

function confirmButton(): HTMLButtonElement {
  return [...container.querySelectorAll('button')].find((b) => b.textContent === 'Void install')!;
}

describe('ClientInstallsSection', () => {
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
      this.open = false;
    };
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

  it('asks to freeze an OPEN release', async () => {
    await renderSection(history({ frozen: false }), false);
    expect(container.textContent).toContain('Freeze this release to record client installs.');
    expect(container.textContent).not.toContain('Record install');
  });

  it('explains an Unassigned frozen release', async () => {
    await renderSection(history({ products: [] }));
    expect(container.textContent).toContain('This release is Unassigned.');
  });

  it('shows the not-in-Production notice and marker', async () => {
    await renderSection(history({ inProduction: false, entries: [entry('i1')] }));
    expect(container.textContent).toContain('Not in Production yet.');
    expect(container.textContent).toContain('not in Production');
  });

  it('strikes through superseded rows and offers no actions on them', async () => {
    await renderSection(
      history({ entries: [entry('i2'), entry('i1', { effective: false, supersededById: 'i2', clientName: 'Old Row' })] }),
    );
    const rows = [...container.querySelectorAll('li')];
    expect(rows[1].textContent).toContain('Superseded');
    expect(rows[1].querySelector('button')).toBeNull();
    expect(rows[1].querySelector('.line-through')?.textContent).toBe('Old Row');
    expect(rows[0].querySelectorAll('button')).toHaveLength(2);
  });

  it('requires a reason to void and posts a VOIDED correction', async () => {
    await renderSection(history({ entries: [entry('i1')] }));
    await act(async () => byLabel('Void install for Acme Bank').click());
    expect(confirmButton().disabled).toBe(true);

    const textarea = container.querySelector('textarea[aria-label="Reason for voiding"]') as HTMLTextAreaElement;
    Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!.call(textarea, 'wrong client');
    await act(async () => {
      textarea.dispatchEvent(new Event('input', { bubbles: true }));
    });
    expect(confirmButton().disabled).toBe(false);
    await act(async () => confirmButton().click());
    await flush();

    expect(posts[0].url).toBe('/api/releases/r1/installs/i1/corrections');
    expect(posts[0].body).toMatchObject({ outcome: 'VOIDED', reason: 'wrong client', installedAt: '2026-10-10T09:00:00Z' });
    expect(container.querySelector('p.sr-only')?.textContent).toBe('Install voided.');
  });

  it('opens the dialog in correct mode', async () => {
    await renderSection(history({ entries: [entry('i1')] }));
    await act(async () => byLabel('Correct install for Acme Bank').click());
    const titles = [...container.querySelectorAll('dialog h2')].map((h) => h.textContent);
    expect(titles).toContain('Correct install');
  });
});
