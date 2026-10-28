import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { TicketBrowser } from '../../src/components/TicketBrowser';

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children?: ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

const PRODUCTS = {
  products: [{ id: 'p1', name: 'Alpha', archived: false, clientCount: 0, releaseCount: 0, jiraProjectKeys: ['ABC'] }],
};

function ticket(key: string, summary: string) {
  return {
    key,
    summary,
    description: `Description of ${key}`,
    issueType: 'Story',
    status: 'Done',
    resolution: 'Done',
    assignee: null,
    reporter: 'rm',
    comments: [],
    commentTotal: 0,
  };
}

const TICKETS = [ticket('ABC-1', 'First ticket'), ticket('ABC-2', 'Second ticket')];

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function mockApi() {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    if (url === '/api/jira/projects') return json(200, [{ id: '1', key: 'ABC', name: 'Alpha Core' }]);
    if (url === '/api/products') return json(200, PRODUCTS);
    if (url === '/api/tickets/search') return json(200, { tickets: TICKETS });
    throw new Error(`Unexpected fetch: ${url}`);
  });
}

let container: HTMLDivElement;
let root: Root;
const onAction = vi.fn();

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderAndSearch() {
  await act(async () => {
    root.render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <TicketBrowser productId="p1" actionLabel="Use" onAction={onAction} />
      </QueryClientProvider>,
    );
  });
  await flush();
  await act(async () => [...container.querySelectorAll('button')].find((b) => b.textContent === 'Load tickets')!.click());
  await flush();
}

const summaryCell = (text: string) =>
  [...container.querySelectorAll('td')].find((td) => td.textContent === text)!;
const checkbox = (key: string) => container.querySelector<HTMLInputElement>(`input[aria-label="Select ticket ${key}"]`)!;
const detailsShown = (key: string) => container.textContent!.includes(`Description of ${key}`);

async function fire(target: Element, type: 'click' | 'dblclick', detail: number) {
  await act(async () => {
    target.dispatchEvent(new MouseEvent(type, { bubbles: true, detail }));
  });
}

// What a browser sends for a double click: click(1), click(2), dblclick(2).
async function doubleClick(target: Element) {
  await fire(target, 'click', 1);
  await fire(target, 'click', 2);
  await fire(target, 'dblclick', 2);
}

describe('TicketBrowser row clicks', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    mockApi();
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
    onAction.mockReset();
  });

  it('a single click selects the row without expanding it, and a second one deselects', async () => {
    await renderAndSearch();

    await fire(summaryCell('First ticket'), 'click', 1);
    expect(checkbox('ABC-1').checked).toBe(true);
    expect(detailsShown('ABC-1')).toBe(false);

    await fire(summaryCell('First ticket'), 'click', 1);
    expect(checkbox('ABC-1').checked).toBe(false);
  });

  it('a double click on an unselected row expands it and leaves it unselected', async () => {
    await renderAndSearch();

    await doubleClick(summaryCell('First ticket'));

    expect(checkbox('ABC-1').checked).toBe(false);
    expect(detailsShown('ABC-1')).toBe(true);
  });

  it('a double click on a selected row expands it and leaves it selected', async () => {
    await renderAndSearch();
    await fire(summaryCell('First ticket'), 'click', 1);

    await doubleClick(summaryCell('First ticket'));

    expect(checkbox('ABC-1').checked).toBe(true);
    expect(detailsShown('ABC-1')).toBe(true);
  });

  it('a double click on an expanded row collapses it', async () => {
    await renderAndSearch();
    await doubleClick(summaryCell('First ticket'));

    await doubleClick(summaryCell('First ticket'));

    expect(detailsShown('ABC-1')).toBe(false);
  });

  it('a double click still toggles when the browser highlighted the word under the cursor', async () => {
    await renderAndSearch();
    vi.spyOn(window, 'getSelection').mockReturnValue({
      toString: () => 'First',
      removeAllRanges: () => {},
    } as unknown as Selection);

    await fire(summaryCell('First ticket'), 'click', 1);
    expect(checkbox('ABC-1').checked).toBe(false);
    await fire(summaryCell('First ticket'), 'click', 2);
    expect(checkbox('ABC-1').checked).toBe(true);
    await fire(summaryCell('First ticket'), 'dblclick', 2);
    expect(detailsShown('ABC-1')).toBe(true);
  });

  it('the arrow button expands on one click and does not select', async () => {
    await renderAndSearch();

    await fire(container.querySelector('button[aria-label="Expand ticket details"]')!, 'click', 1);

    expect(detailsShown('ABC-1')).toBe(true);
    expect(checkbox('ABC-1').checked).toBe(false);
  });

  it('clicking the checkbox cell selects once', async () => {
    await renderAndSearch();

    await act(async () => checkbox('ABC-1').closest('label')!.click());

    expect(checkbox('ABC-1').checked).toBe(true);
    expect(detailsShown('ABC-1')).toBe(false);
  });
});
