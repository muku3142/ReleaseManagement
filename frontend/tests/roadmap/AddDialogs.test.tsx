import { StrictMode, act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AddFromJiraDialog } from '../../src/roadmap/AddFromJiraDialog';
import { AddFromReleaseDialog } from '../../src/roadmap/AddFromReleaseDialog';
import { AddManualItemDialog } from '../../src/roadmap/AddManualItemDialog';
import type { RoadmapAddResult } from '../../src/api/roadmap';

const TICKETS = ['CNV-1', 'CNV-2'].map((key) => ({
  key,
  summary: `Summary ${key}`,
  issueType: 'Bug',
  status: 'Done',
  resolution: 'Fixed',
  assignee: null,
  reporter: null,
  fixVersions: [],
  component: null,
  description: '',
  comments: [],
  commentTotal: 0,
}));

const RELEASES = [
  { id: 'r1', name: 'Old', version: '1.0', createdAt: '2026-01-01T00:00:00Z' },
  { id: 'r2', name: 'New', version: '2.0', createdAt: '2026-06-01T00:00:00Z' },
];

const RESULT: RoadmapAddResult = { added: [{ itemId: 'i1', jiraKey: 'CNV-1' }], alreadyPresent: [], notFound: [] };

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

type Call = { url: string; method: string; body: unknown };
type Overrides = { post?: (url: string) => Response | undefined; releaseTickets?: string[] };

function mockApi(overrides: Overrides = {}): Call[] {
  const calls: Call[] = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    calls.push({ url, method, body });
    if (url === '/api/jira/projects') return json(200, [{ id: '10023', key: 'CNV', name: 'Convergence' }]);
    if (/^\/api\/jira\/projects\/[^/]+\/sprints$/.test(url)) return json(200, []);
    if (url === '/api/tickets/search') return json(200, { tickets: TICKETS });
    if (url === '/api/releases') return json(200, { releases: RELEASES });
    if (url === '/api/releases/r2')
      return json(200, { id: 'r2', items: (overrides.releaseTickets ?? ['CNV-1', 'CNV-2']).map((key) => ({ key, summary: `Summary ${key}` })) });
    if (url.startsWith('/api/roadmap/items?'))
      return json(200, { items: [{ id: 'i9', jiraKey: 'CNV-2' }] });
    if (url === '/api/products')
      return json(200, {
        products: [
          { id: 'p1', name: 'Active product', archived: false },
          { id: 'p2', name: 'Old product', archived: true },
        ],
      });
    if (method === 'POST') {
      const response = overrides.post?.(url);
      if (response) return response;
      if (url === '/api/roadmap/items') return json(200, { id: 'm1', title: 'x' });
      return json(200, RESULT);
    }
    throw new Error(`Unexpected fetch: ${method} ${url}`);
  });
  return calls;
}

let container: HTMLDivElement;
let root: Root;
let client: QueryClient;
const onAdded = vi.fn();
const onClose = vi.fn();

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function render(element: React.ReactElement) {
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>{element}</QueryClientProvider>
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

async function choose(select: HTMLSelectElement, value: string) {
  await act(async () => {
    select.value = value;
    select.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await flush();
}

async function type(input: HTMLInputElement, value: string) {
  await act(async () => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

describe('roadmap add dialogs', () => {
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
    client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
    onAdded.mockReset();
    onClose.mockReset();
  });

  describe('AddFromJiraDialog', () => {
    async function pickBoth() {
      await render(<AddFromJiraDialog open onClose={onClose} onAdded={onAdded} />);
      await choose(container.querySelector<HTMLSelectElement>('#projectId')!, '10023');
      await act(async () => byText('Load tickets').click());
      await flush();
      await act(async () => {
        container.querySelector<HTMLInputElement>('input[aria-label="Select ticket CNV-1"]')!.click();
        container.querySelector<HTMLInputElement>('input[aria-label="Select ticket CNV-2"]')!.click();
      });
    }

    it('postsTheTickedKeysAndClosesWithTheResult', async () => {
      const calls = mockApi();
      await pickBoth();
      await act(async () => byText('Add to roadmap').click());
      await flush();

      expect(calls.find((c) => c.url === '/api/roadmap/items/jira')?.body).toEqual({ keys: ['CNV-1', 'CNV-2'] });
      expect(onAdded).toHaveBeenCalledWith(RESULT);
      expect(onClose).toHaveBeenCalled();
    });

    it('hasNoAddAllButton', async () => {
      mockApi();
      await render(<AddFromJiraDialog open onClose={onClose} onAdded={onAdded} />);
      expect(container.textContent?.toLowerCase()).not.toContain('add all');
    });

    it('showsAFailureAndStaysOpen', async () => {
      mockApi({ post: () => json(502, { status: 502, reason: 'JIRA_UNREACHABLE' }) });
      await pickBoth();
      await act(async () => byText('Add to roadmap').click());
      await flush();

      const alert = container.querySelector('[role="alert"]');
      expect(alert?.textContent).toMatch(/^Couldn't add to the roadmap — .*Try again\.$/);
      expect(onClose).not.toHaveBeenCalled();
    });
  });

  describe('AddFromReleaseDialog', () => {
    it('listsReleasesNewestFirst', async () => {
      mockApi();
      await render(<AddFromReleaseDialog open onClose={onClose} onAdded={onAdded} />);
      const labels = [...container.querySelectorAll('#roadmapRelease option')].map((o) => o.textContent);
      expect(labels).toEqual(['Select a release', 'New 2.0', 'Old 1.0']);
    });

    it('marksTicketsAlreadyOnTheRoadmapAndKeepsThemSelectable', async () => {
      mockApi();
      await render(<AddFromReleaseDialog open onClose={onClose} onAdded={onAdded} />);
      await choose(container.querySelector<HTMLSelectElement>('#roadmapRelease')!, 'r2');

      const labels = [...container.querySelectorAll('label')].filter((l) => l.querySelector('input[type="checkbox"]'));
      expect(labels).toHaveLength(2);
      expect(labels[0].textContent).not.toContain('already on the roadmap');
      expect(labels[1].textContent).toContain('already on the roadmap');
      expect(labels[1].querySelector('input')!.disabled).toBe(false);
    });

    it('disablesSubmitUntilSomethingIsTickedThenPostsTheKeys', async () => {
      const calls = mockApi();
      await render(<AddFromReleaseDialog open onClose={onClose} onAdded={onAdded} />);
      await choose(container.querySelector<HTMLSelectElement>('#roadmapRelease')!, 'r2');
      expect(byText('Add to roadmap').disabled).toBe(true);

      await act(async () => container.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')[0].click());
      expect(byText('Add to roadmap').disabled).toBe(false);
      expect(container.textContent).toContain('1 selected');

      await act(async () => byText('Add to roadmap').click());
      await flush();
      expect(calls.find((c) => c.url === '/api/roadmap/items/from-release')?.body).toEqual({
        releaseId: 'r2',
        ticketKeys: ['CNV-1'],
      });
      expect(onAdded).toHaveBeenCalledWith(RESULT);
    });

    it('saysWhenTheReleaseHasNoTickets', async () => {
      mockApi({ releaseTickets: [] });
      await render(<AddFromReleaseDialog open onClose={onClose} onAdded={onAdded} />);
      await choose(container.querySelector<HTMLSelectElement>('#roadmapRelease')!, 'r2');
      expect(container.textContent).toContain('This release has no tickets.');
    });

    it('refusesMoreThanTheCapWithoutARequest', async () => {
      const many = Array.from({ length: 101 }, (_, i) => `CNV-${i + 1}`);
      const calls = mockApi({ releaseTickets: many });
      await render(<AddFromReleaseDialog open onClose={onClose} onAdded={onAdded} />);
      await choose(container.querySelector<HTMLSelectElement>('#roadmapRelease')!, 'r2');
      await act(async () => byText('Select all').click());
      await act(async () => byText('Add to roadmap').click());
      await flush();

      expect(container.querySelector('[role="alert"]')?.textContent).toBe(
        'Too many items at once. Add up to 100 at a time.',
      );
      expect(calls.some((c) => c.method === 'POST')).toBe(false);
    });
  });

  describe('AddManualItemDialog', () => {
    it('requiresATitleAndSendsNoRequest', async () => {
      const calls = mockApi();
      await render(<AddManualItemDialog open onClose={onClose} onAdded={onAdded} />);
      await act(async () => byText('Add item').click());
      await flush();

      expect(container.textContent).toContain('Enter a title.');
      expect(calls.some((c) => c.method === 'POST')).toBe(false);
    });

    it('offersOnlyActiveProducts', async () => {
      mockApi();
      await render(<AddManualItemDialog open onClose={onClose} onAdded={onAdded} />);
      const labels = [...container.querySelectorAll('#manualItemProduct option')].map((o) => o.textContent);
      expect(labels).toEqual(['No product', 'Active product']);
    });

    it('postsTheTitleAndProductAndReturnsTheItem', async () => {
      const calls = mockApi();
      await render(<AddManualItemDialog open onClose={onClose} onAdded={onAdded} />);
      await type(container.querySelector<HTMLInputElement>('#manualItemTitle')!, 'Dark mode');
      await choose(container.querySelector<HTMLSelectElement>('#manualItemProduct')!, 'p1');
      await act(async () => byText('Add item').click());
      await flush();

      expect(calls.find((c) => c.url === '/api/roadmap/items' && c.method === 'POST')?.body).toEqual({
        title: 'Dark mode',
        productId: 'p1',
      });
      expect(onAdded).toHaveBeenCalledWith({ id: 'm1', title: 'x' });
    });

    it('showsAServerTitleErrorUnderTheField', async () => {
      mockApi({
        post: () => json(400, { status: 400, reason: 'VALIDATION_FAILED', fieldErrors: { title: 'Title is too long.' } }),
      });
      await render(<AddManualItemDialog open onClose={onClose} onAdded={onAdded} />);
      await type(container.querySelector<HTMLInputElement>('#manualItemTitle')!, 'Dark mode');
      await act(async () => byText('Add item').click());
      await flush();

      expect(container.textContent).toContain('Title is too long.');
      expect(onClose).not.toHaveBeenCalled();
    });
  });
});
