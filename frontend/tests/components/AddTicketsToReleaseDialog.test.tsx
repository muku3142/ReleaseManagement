import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AddTicketsToReleaseDialog } from '../../src/components/AddTicketsToReleaseDialog';
import type { ReleaseAddResult } from '../../src/api/releases';

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, search, className }: { children?: ReactNode; to: string; search?: Record<string, string>; className?: string }) => (
    <a href={search ? `${to}?${new URLSearchParams(search)}` : to} className={className}>
      {children}
    </a>
  ),
}));

const PROJECTS = [{ id: '10023', key: 'ABC', name: 'Alpha Core' }];

const PRODUCTS = {
  products: [
    { id: 'p1', name: 'Alpha', archived: false, clientCount: 0, releaseCount: 1, jiraProjectKeys: ['ABC'] },
    { id: 'p2', name: 'Beta', archived: false, clientCount: 0, releaseCount: 1, jiraProjectKeys: [] },
  ],
};

const TICKETS = [
  {
    key: 'ABC-1',
    summary: 'Fix login bug',
    issueType: 'Bug',
    status: 'Done',
    resolution: 'Fixed',
    assignee: 'Alice',
    reporter: 'Bob',
    fixVersions: [],
    component: null,
    description: 'Login fails on Safari.',
    comments: [],
    commentTotal: 0,
  },
  {
    key: 'ABC-2',
    summary: 'Add export button',
    issueType: 'Story',
    status: 'Done',
    resolution: 'Fixed',
    assignee: 'Carol',
    reporter: 'Dan',
    fixVersions: [],
    component: null,
    description: 'Export tickets to PDF.',
    comments: [],
    commentTotal: 0,
  },
];

const RELEASE_STUB = {
  id: 'r1',
  name: 'Sprint 42',
  version: '2026.08',
  status: 'OPEN',
  jiraProjectKey: 'ABC',
  createdAt: '2026-08-05T00:00:00Z',
  createdBy: 'rm@acme.com',
  frozenAt: null,
  frozenBy: null,
  lastRefreshedAt: null,
  items: [],
  manualItems: [],
};

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

type MockOverrides = {
  addResponse?: Response;
};

// Every fetch is captured so the request-shape assertions (searches scoped to this release, the
// add body) can read back exactly what was sent, not just that a call happened.
function mockApi(overrides: MockOverrides = {}): { url: string; body: unknown }[] {
  const calls: { url: string; body: unknown }[] = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    calls.push({ url, body });
    if (url === '/api/jira/projects') return json(200, PROJECTS);
    if (url === '/api/products') return json(200, PRODUCTS);
    const sprintsMatch = url.match(/^\/api\/jira\/projects\/([^/]+)\/sprints$/);
    if (sprintsMatch) return json(200, []);
    if (url === '/api/tickets/search' && method === 'POST') return json(200, { tickets: TICKETS });
    if (url === '/api/releases/r1/items' && method === 'POST') {
      if (overrides.addResponse) return overrides.addResponse;
      return json(200, { release: RELEASE_STUB, added: 2, alreadyPresent: 0, notFound: 0 });
    }
    throw new Error(`Unexpected fetch: ${method} ${url}`);
  });
  return calls;
}

let container: HTMLDivElement;
let root: Root;
let client: QueryClient;
const onAdded = vi.fn<(result: ReleaseAddResult) => void>();
const onClose = vi.fn();

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderDialog(open = true, productId: string | null = 'p1') {
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <AddTicketsToReleaseDialog
            open={open}
            releaseId="r1"
            releaseName="Sprint 42"
            version="2026.08"
            productId={productId ?? undefined}
            onAdded={onAdded}
            onClose={onClose}
          />
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

function projectSelect(): HTMLSelectElement {
  return container.querySelector<HTMLSelectElement>('#projectId')!;
}

function rowCheckbox(key: string): HTMLInputElement {
  return container.querySelector<HTMLInputElement>(`input[aria-label="Select ticket ${key}"]`)!;
}

async function selectProject(id: string) {
  await act(async () => {
    const el = projectSelect();
    el.value = id;
    el.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await flush();
}

async function loadTickets() {
  await selectProject('10023');
  await act(async () => byText('Load tickets').click());
  await flush();
}

async function selectBothTickets() {
  await act(async () => {
    rowCheckbox('ABC-1').click();
    rowCheckbox('ABC-2').click();
  });
  await flush();
}

describe('AddTicketsToReleaseDialog', () => {
  beforeAll(() => {
    // jsdom implements <dialog> but not showModal/close, and the component calls both.
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

  it('opensWithTheFullBrowseSurface', async () => {
    mockApi();
    await renderDialog();

    expect(container.querySelector('#projectId')).not.toBeNull();
    expect(container.querySelector('legend')?.textContent).toBe('Status');
    expect(
      [...container.querySelectorAll('button')].some((b) => b.textContent === 'Advanced: edit as JQL'),
    ).toBe(true);
  });

  it('browsesAllOfTheReleasesProductProjectsByDefault', async () => {
    const calls = mockApi();
    await renderDialog();

    expect(projectSelect().options[0]?.textContent).toBe('All Alpha projects');
    await act(async () => byText('Load tickets').click());
    await flush();

    const searchCall = calls.find((c) => c.url === '/api/tickets/search');
    expect(searchCall?.body).toEqual({
      productId: 'p1',
      statusCategories: ['DONE'],
      releaseId: 'r1',
      hideAlreadyInRelease: true,
    });
  });

  it('offersHideTicketsAlreadyInAReleaseCheckedByDefault', async () => {
    mockApi();
    await renderDialog();

    const label = [...container.querySelectorAll('label')].find(
      (l) => l.textContent?.trim() === 'Hide tickets already in a release',
    );
    expect(label?.querySelector<HTMLInputElement>('input[type="checkbox"]')?.checked).toBe(true);
  });

  it('showsTheNoJiraProjectNoticeInsteadOfTheBrowserForAProductWithoutKeys', async () => {
    mockApi();
    await renderDialog(true, 'p2');

    const notice = container.querySelector('[data-reason="PRODUCT_NO_JIRA_PROJECT"]');
    expect(notice?.textContent).toBe('Beta has no Jira project. Add one in Portfolio › Products.');
    expect(notice?.querySelector('a')?.getAttribute('href')).toBe('/portfolio?tab=products');
    expect(container.querySelector('#projectId')).toBeNull();
    expect([...container.querySelectorAll('button')].map((b) => b.textContent)).toEqual(['Cancel']);
  });

  it('asksForAProductWhenTheReleaseHasNone', async () => {
    mockApi();
    await renderDialog(true, null);

    expect(container.textContent).toContain('Choose a product for this release first.');
    expect(container.querySelector('#projectId')).toBeNull();
  });

  it('searchesAreScopedToThisRelease', async () => {
    const calls = mockApi();
    await renderDialog();
    await loadTickets();

    const searchCall = calls.find((c) => c.url === '/api/tickets/search');
    expect(searchCall?.body).toMatchObject({ releaseId: 'r1' });
  });

  it('saysThatAlreadyAddedTicketsAreHidden', async () => {
    mockApi();
    await renderDialog();

    expect(container.textContent).toContain('Tickets already on this release are hidden.');
  });

  it('addsTheSelectedTicketsToThisRelease', async () => {
    const calls = mockApi();
    await renderDialog();
    await loadTickets();
    await selectBothTickets();

    await act(async () => byText('Add to this release').click());
    await flush();

    const addCalls = calls.filter((c) => c.url === '/api/releases/r1/items');
    expect(addCalls).toHaveLength(1);
    expect(addCalls[0]?.body).toEqual({ ticketKeys: ['ABC-1', 'ABC-2'] });
  });

  it('aSuccessfulAddReportsTheServerCountsAndCloses', async () => {
    mockApi();
    await renderDialog();
    await loadTickets();
    await selectBothTickets();

    await act(async () => byText('Add to this release').click());
    await flush();

    expect(onAdded).toHaveBeenCalledWith(
      expect.objectContaining({ added: 2, alreadyPresent: 0, notFound: 0 }),
    );
    expect(onClose).toHaveBeenCalled();
  });

  it('anAddFailureRendersTheAlertAndLeavesTheDialogOpen', async () => {
    mockApi({ addResponse: json(409, { status: 409, reason: 'RELEASE_FROZEN' }) });
    await renderDialog();
    await loadTickets();
    await selectBothTickets();

    await act(async () => byText('Add to this release').click());
    await flush();

    expect(container.querySelectorAll('[data-reason="RELEASE_FROZEN"]')).toHaveLength(1);
    expect(onClose).not.toHaveBeenCalled();
    expect(container.querySelector('#projectId')).not.toBeNull();
  });

  it('reopeningStartsFromAFreshSearchRatherThanThePreviousVisitsResults', async () => {
    mockApi();
    await renderDialog();
    await loadTickets();
    expect(container.querySelectorAll('tbody tr').length).toBeGreaterThan(0);

    await renderDialog(false);
    await renderDialog(true);

    expect(container.querySelectorAll('tbody tr').length).toBe(0);
  });

  it('has no free-text bundle input', async () => {
    mockApi();
    await renderDialog();

    expect(container.querySelector('#addTicketsBundle')).toBeNull();
  });
});
