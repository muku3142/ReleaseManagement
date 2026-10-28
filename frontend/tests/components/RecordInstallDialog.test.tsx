import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { RecordInstallDialog } from '../../src/components/RecordInstallDialog';
import type { InstallEntry, InstallHistory } from '../../src/api/installs';

vi.mock('@tanstack/react-router', () => ({
  Link: (props: { children?: ReactNode; to?: string }) => <a href={props.to}>{props.children}</a>,
}));

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const CLIENTS = {
  clients: [
    { id: 'c2', name: 'Beta Bank', archived: false, productIds: ['p1'] },
    { id: 'c1', name: 'Acme Bank', archived: false, productIds: ['p1'] },
    { id: 'c3', name: 'Old Bank', archived: true, productIds: ['p1'] },
    { id: 'c4', name: 'Other Bank', archived: false, productIds: ['p2'] },
  ],
};

function history(inProduction = true): InstallHistory {
  return {
    releaseId: 'r1',
    frozen: true,
    inProduction,
    products: [{ id: 'p1', name: 'Core', archived: false }],
    entries: [],
  };
}

const ENTRY: InstallEntry = {
  id: 'i1',
  productId: 'p1',
  productName: 'Core',
  clientId: 'c1',
  clientName: 'Acme Bank',
  outcome: 'SUCCESS',
  installedAt: '2026-10-10T09:00:00.123Z',
  recordedAt: '2026-10-10T09:05:00Z',
  recordedBy: 'rm@acme.com',
  note: null,
  supersedesId: null,
  supersededById: null,
  correctionReason: null,
  effective: true,
};

let container: HTMLDivElement;
let root: Root;
let posts: { url: string; body: Record<string, unknown> }[];
let onDone: Mock<(message: string) => void>;

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function mockApi(post?: () => Response) {
  posts = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    if (init?.method === 'POST') {
      posts.push({ url: String(input), body: JSON.parse(String(init.body)) });
      return post ? post() : json(201, history());
    }
    return json(200, CLIENTS);
  });
}

async function renderDialog(h: InstallHistory, correcting?: InstallEntry) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <RecordInstallDialog open releaseId="r1" history={h} correcting={correcting} onClose={vi.fn()} onDone={onDone} />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
  await flush();
}

function byText(text: string): HTMLButtonElement {
  const button = [...container.querySelectorAll('button')].find((b) => b.textContent === text);
  expect(button, `no button labelled "${text}"`).toBeDefined();
  return button!;
}

async function change(el: HTMLSelectElement | HTMLTextAreaElement, value: string) {
  Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value')!.set!.call(el, value);
  await act(async () => {
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
  });
}

async function submit() {
  await act(async () => {
    container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
  await flush();
}

describe('RecordInstallDialog', () => {
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
    onDone = vi.fn();
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
  });

  it('lists only active clients linked to the product and bulk-records the selection', async () => {
    mockApi();
    await renderDialog(history());
    const names = [...container.querySelectorAll('fieldset label')].map((l) => l.textContent);
    expect(names).toEqual(['Acme Bank', 'Beta Bank']);

    await act(async () => byText('Select all').click());
    expect(container.textContent).toContain('2 selected');
    expect(byText('Record install for 2 clients')).toBeDefined();

    await submit();
    expect(posts).toHaveLength(1);
    expect(posts[0].url).toBe('/api/releases/r1/installs');
    expect(posts[0].body).toMatchObject({ productId: 'p1', clientIds: ['c1', 'c2'], outcome: 'SUCCESS', installedAt: null });
    expect(onDone).toHaveBeenCalledWith('Install recorded for 2 clients.');
  });

  it('shows the rollback hint only for Rolled back', async () => {
    mockApi();
    await renderDialog(history());
    expect(container.textContent).not.toContain("A rollback doesn't change what the client runs.");
    await change(container.querySelector('#installOutcome') as HTMLSelectElement, 'ROLLED_BACK');
    expect(container.textContent).toContain("A rollback doesn't change what the client runs.");
  });

  it('warns when not in Production but still submits', async () => {
    mockApi();
    await renderDialog(history(false));
    expect(container.querySelector('[role="status"]')?.textContent).toContain("This release isn't in Production yet.");
    await act(async () => byText('Select all').click());
    await submit();
    expect(posts).toHaveLength(1);
  });

  it('names the first five rejected clients and marks them', async () => {
    const clients = Array.from({ length: 6 }, (_, i) => ({
      clientId: i === 0 ? 'c1' : `x${i}`,
      name: `Client ${i + 1}`,
      reason: 'ARCHIVED' as const,
    }));
    mockApi(() => json(409, { status: 409, reason: 'INSTALL_CLIENTS_REJECTED', clients }));
    await renderDialog(history());
    await act(async () => byText('Select all').click());
    await submit();
    const alert = container.querySelector('[role="alert"]')!;
    expect(alert.textContent).toContain('Nothing was recorded.');
    expect(alert.textContent).toContain('Client 5 (archived)');
    expect(alert.textContent).not.toContain('Client 6');
    expect(alert.textContent).toContain('and 1 more');
    expect(container.querySelector('fieldset')!.textContent).toContain("can't receive this install");
    expect(container.textContent).toContain('2 selected');
  });

  it('corrects an install, requiring a reason and keeping the original instant', async () => {
    mockApi();
    await renderDialog(history(), ENTRY);
    expect(container.textContent).toContain('Client: Acme Bank');
    expect(container.querySelector('h2')!.textContent).toBe('Correct install');
    expect(byText('Save correction').disabled).toBe(true);

    await change(container.querySelector('#installReason') as HTMLTextAreaElement, 'wrong client');
    expect(byText('Save correction').disabled).toBe(false);
    await submit();

    expect(posts[0].url).toBe('/api/releases/r1/installs/i1/corrections');
    expect(posts[0].body).toMatchObject({
      outcome: 'SUCCESS',
      installedAt: '2026-10-10T09:00:00.123Z',
      reason: 'wrong client',
    });
    expect(onDone).toHaveBeenCalledWith('Correction saved.');
  });
});
