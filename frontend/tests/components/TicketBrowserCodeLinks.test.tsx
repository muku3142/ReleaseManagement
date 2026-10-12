import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { TicketBrowser } from '../../src/components/TicketBrowser';
import type { CodeLinks } from '../../src/api/releases';

const PROJECTS = [{ id: '10023', key: 'CNV', name: 'Convergence' }];

const TICKET = {
  key: 'CNV-1',
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
};

const LINKED: CodeLinks = {
  status: 'LINKED',
  reason: null,
  incomplete: false,
  attemptedAt: '2026-10-12T00:00:00Z',
  commitsSource: 'KEY_TAGGED',
  pullRequests: [],
  commits: [
    {
      sha: 'abcdef1234567',
      shortSha: 'abcdef1',
      message: 'CNV-1 fix login',
      author: 'Alice',
      authoredAt: '2026-10-01T00:00:00Z',
      url: 'https://dev.azure.com/org/proj/_git/repo/commit/abc',
    },
  ],
  branches: [],
};

const FAILED: CodeLinks = {
  status: 'FAILED',
  reason: 'JIRA_UNREACHABLE',
  incomplete: false,
  attemptedAt: '2026-10-12T00:00:00Z',
  commitsSource: null,
  pullRequests: [],
  commits: [],
  branches: [],
};

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function mockApi(codeLinks: CodeLinks) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    if (url === '/api/jira/projects') return json(PROJECTS);
    if (/^\/api\/jira\/projects\/[^/]+\/sprints$/.test(url)) return json([]);
    if (url === '/api/tickets/search' && init?.method === 'POST') return json({ tickets: [TICKET] });
    if (url === '/api/tickets/CNV-1/code-links') return json(codeLinks);
    throw new Error(`Unexpected fetch: ${init?.method ?? 'GET'} ${url}`);
  });
}

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function buttonByText(text: string): HTMLButtonElement | undefined {
  return [...container.querySelectorAll('button')].find((b) => b.textContent === text);
}

async function renderAndExpand() {
  await act(async () => {
    root.render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <TicketBrowser actionLabel="Add" onAction={vi.fn()} />
      </QueryClientProvider>,
    );
  });
  await flush();
  await act(async () => {
    const select = container.querySelector<HTMLSelectElement>('#projectId')!;
    select.value = '10023';
    select.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await flush();
  await act(async () => buttonByText('Load tickets')!.click());
  await flush();
  await act(async () => container.querySelector<HTMLButtonElement>('button[aria-label$="ticket details"]')!.click());
}

function codeLinkCalls(fetchSpy: ReturnType<typeof mockApi>) {
  return fetchSpy.mock.calls.filter(([url]) => String(url).includes('/code-links'));
}

describe('TicketBrowser linked commits', () => {
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

  it('offersTheButtonOnAnExpandedRowAndFetchesNothingUntilClicked', async () => {
    const fetchSpy = mockApi(LINKED);
    await renderAndExpand();

    expect(buttonByText('Show linked commits')).toBeDefined();
    expect(codeLinkCalls(fetchSpy)).toHaveLength(0);
  });

  it('doesNotShowTheButtonOnACollapsedRow', async () => {
    mockApi(LINKED);
    await renderAndExpand();
    await act(async () => container.querySelector<HTMLButtonElement>('button[aria-label$="ticket details"]')!.click());

    expect(buttonByText('Show linked commits')).toBeUndefined();
  });

  it('clickingFetchesTheLiveLinksAndRendersTheCommit', async () => {
    const fetchSpy = mockApi(LINKED);
    await renderAndExpand();

    await act(async () => buttonByText('Show linked commits')!.click());
    await flush();

    const calls = codeLinkCalls(fetchSpy);
    expect(calls).toHaveLength(1);
    expect(String(calls[0][0])).toBe('/api/tickets/CNV-1/code-links');
    expect(container.textContent).toContain('Code links');
    expect(container.textContent).toContain('abcdef1');
  });

  it('aFailedLookupShowsTheCouldntFetchState', async () => {
    mockApi(FAILED);
    await renderAndExpand();

    await act(async () => buttonByText('Show linked commits')!.click());
    await flush();

    expect(container.textContent).toContain("Couldn't fetch (");
  });
});
