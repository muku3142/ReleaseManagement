import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { TicketBrowser } from '../../src/components/TicketBrowser';

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, search, className }: { children?: ReactNode; to: string; search?: Record<string, string>; className?: string }) => (
    <a href={search ? `${to}?${new URLSearchParams(search)}` : to} className={className}>
      {children}
    </a>
  ),
}));

const PROJECTS = [
  { id: '1', key: 'ABC', name: 'Alpha Core' },
  { id: '2', key: 'XYZ', name: 'Alpha Mobile' },
  { id: '3', key: 'OTH', name: 'Other' },
];

const PRODUCTS = {
  products: [
    { id: 'p1', name: 'Alpha', archived: false, clientCount: 0, releaseCount: 0, jiraProjectKeys: ['ABC', 'XYZ', 'ZZZ'] },
  ],
};

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function mockApi(): unknown[] {
  const searches: unknown[] = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    if (url === '/api/jira/projects') return json(200, PROJECTS);
    if (url === '/api/products') return json(200, PRODUCTS);
    if (/^\/api\/jira\/projects\/[^/]+\/sprints$/.test(url)) return json(200, [{ id: 's1', name: 'Sprint 1', state: 'active' }]);
    if (url === '/api/tickets/search') {
      searches.push(JSON.parse(String(init?.body)));
      return json(200, { tickets: [] });
    }
    throw new Error(`Unexpected fetch: ${url}`);
  });
  return searches;
}

let container: HTMLDivElement;
let root: Root;
let client: QueryClient;

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function render() {
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <TicketBrowser productId="p1" actionLabel="Use" onAction={() => {}} />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
}

const select = (id: string) => container.querySelector<HTMLSelectElement>(`#${id}`)!;
const button = (text: string) => [...container.querySelectorAll('button')].find((b) => b.textContent === text)!;

async function click(text: string) {
  await act(async () => button(text).click());
  await flush();
}

describe('TicketBrowser in product mode', () => {
  beforeAll(() => {
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
  });

  it('defaults to every mapped project and searches by product', async () => {
    const searches = mockApi();
    await render();

    const options = [...select('projectId').options].map((o) => o.textContent);
    expect(options).toEqual(['All Alpha projects', 'ABC — Alpha Core', 'XYZ — Alpha Mobile']);
    expect(select('projectId').value).toBe('');
    expect(select('sprintId').disabled).toBe(true);
    expect([...select('sprintId').options].map((o) => o.textContent)).toEqual(['Pick one project to filter by sprint']);
    expect(button('Load tickets').disabled).toBe(false);

    await click('Load tickets');

    expect(searches).toEqual([{ productId: 'p1', statusCategories: ['DONE'] }]);
    expect(container.textContent).toContain('No tickets matched Alpha (ABC, XYZ, ZZZ) with status Done');
  });

  it('narrows to one project and enables the sprint picker', async () => {
    const searches = mockApi();
    await render();

    await act(async () => {
      select('projectId').value = '1';
      select('projectId').dispatchEvent(new Event('change', { bubbles: true }));
    });
    await flush();

    expect(select('sprintId').disabled).toBe(false);
    await click('Load tickets');

    expect(searches).toEqual([{ projectId: '1', statusCategories: ['DONE'] }]);
  });

  it('warns about a mapped key Jira does not list, without blocking', async () => {
    mockApi();
    await render();

    const warning = [...container.querySelectorAll('p.text-warning')].map((p) => p.textContent);
    expect(warning).toEqual(["ZZZ isn't a Jira project you can see. Check the mapping in Portfolio › Products."]);
    expect(container.querySelector('p.text-warning a')?.getAttribute('href')).toBe('/portfolio?tab=products');
    expect(button('Load tickets').disabled).toBe(false);
  });

  it('says advanced JQL is not scoped to the product', async () => {
    mockApi();
    await render();

    await click('Advanced: edit as JQL');

    const helper = container.querySelector('#jql-scope-helper');
    expect(helper?.textContent).toBe("Advanced JQL searches every project you can see, not only Alpha's.");
    expect(container.querySelector('#jql')?.getAttribute('aria-describedby')).toBe('jql-scope-helper');
  });
});
