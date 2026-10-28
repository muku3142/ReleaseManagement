import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { TicketsRoute } from '../../src/routes/TicketsRoute';
import { TooltipProvider } from '../../src/components/ui/tooltip';

const navigate = vi.fn();
let searchParams: { product?: string; new?: true } = {};
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigate,
  useSearch: () => searchParams,
  Link: (props: { children?: ReactNode; to: string; search?: Record<string, string>; className?: string }) => (
    <a href={props.search ? `${props.to}?${new URLSearchParams(props.search)}` : props.to} className={props.className}>
      {props.children}
    </a>
  ),
}));

const PROJECTS = [
  { id: '10023', key: 'ABC', name: 'Alpha Core' },
  { id: '10024', key: 'XYZ', name: 'Alpha Mobile' },
  { id: '10025', key: 'OTH', name: 'Other' },
];

const PRODUCTS = {
  products: [
    { id: 'p2', name: 'Gamma', archived: false, clientCount: 0, releaseCount: 0, jiraProjectKeys: ['OTH'] },
    { id: 'p1', name: 'Alpha', archived: false, clientCount: 0, releaseCount: 0, jiraProjectKeys: ['ABC', 'XYZ'] },
    { id: 'p3', name: 'Beta', archived: false, clientCount: 0, releaseCount: 0, jiraProjectKeys: [] },
    { id: 'p4', name: 'Delta', archived: true, clientCount: 0, releaseCount: 0, jiraProjectKeys: ['ABC'] },
  ],
};

const DEFAULT_SPRINTS: Record<string, { id: string; name: string; state: string }[]> = {
  '10023': [{ id: '10891', name: 'Sprint 42', state: 'active' }],
  '10024': [],
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
    comments: ['Looks good now.'],
    commentTotal: 1,
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
  {
    key: 'ABC-3',
    summary: 'Improve search performance',
    issueType: 'Task',
    status: 'Done',
    resolution: null,
    assignee: null,
    reporter: 'Eve',
    fixVersions: [],
    component: null,
    description: 'Speed up ticket search.',
    comments: [],
    commentTotal: 0,
  },
];

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

type MockOverrides = {
  tickets?: { tickets: typeof TICKETS };
  searchStatus?: number;
  searchReason?: string;
  sprintsByProject?: Record<string, { id: string; name: string; state: string }[]>;
  products?: () => Promise<Response>;
  releases?: unknown[];
};

// Every /api/tickets/search request body is captured so the request-shape assertions can read
// back exactly what was sent - not just that a search happened.
function mockApi(overrides: MockOverrides = {}): Record<string, unknown>[] {
  const searchBodies: Record<string, unknown>[] = [];
  const sprintsByProject = overrides.sprintsByProject ?? DEFAULT_SPRINTS;
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    if (url === '/api/products') return overrides.products ? overrides.products() : json(200, PRODUCTS);
    if (url === '/api/jira/projects') return json(200, PROJECTS);
    const sprintsMatch = url.match(/^\/api\/jira\/projects\/([^/]+)\/sprints$/);
    if (sprintsMatch) return json(200, sprintsByProject[sprintsMatch[1] ?? ''] ?? []);
    if (url === '/api/tickets/search' && method === 'POST') {
      searchBodies.push(JSON.parse(String(init?.body ?? '{}')));
      if (overrides.searchStatus !== undefined) {
        return json(overrides.searchStatus, {
          status: overrides.searchStatus,
          reason: overrides.searchReason ?? 'JIRA_UNREACHABLE',
        });
      }
      return json(200, overrides.tickets ?? { tickets: TICKETS });
    }
    if (url === '/api/releases') return json(200, { releases: overrides.releases ?? [] });
    if (url === '/api/products/p1/bundles') return json(200, { bundles: [] });
    throw new Error(`Unexpected fetch: ${method} ${url}`);
  });
  return searchBodies;
}

let container: HTMLDivElement;
let root: Root;
let client: QueryClient;

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderRoute(search: { product?: string; new?: true } = { product: 'p1' }) {
  searchParams = search;
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <TooltipProvider>
            <TicketsRoute />
          </TooltipProvider>
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  // Products load first, then the browser mounts and loads its Jira projects.
  await flush();
  await flush();
}

function byText(text: string): HTMLButtonElement {
  const button = findByText(text);
  expect(button, `no button labelled "${text}"`).toBeDefined();
  return button!;
}

function findByText(text: string): HTMLButtonElement | undefined {
  return [...container.querySelectorAll('button')].find((b) => b.textContent === text);
}

function productSelect(): HTMLSelectElement | null {
  return container.querySelector<HTMLSelectElement>('#productId');
}

function projectSelect(): HTMLSelectElement {
  return container.querySelector<HTMLSelectElement>('#projectId')!;
}

function sprintSelect(): HTMLSelectElement {
  return container.querySelector<HTMLSelectElement>('#sprintId')!;
}

function statusCheckbox(label: string): HTMLInputElement {
  const labelEl = [...container.querySelectorAll('fieldset label')].find(
    (l) => l.textContent?.trim() === label,
  );
  expect(labelEl, `no status checkbox labelled "${label}"`).toBeDefined();
  return labelEl!.querySelector('input[type="checkbox"]')!;
}

function hideReleasedCheckbox(): HTMLInputElement | null {
  const labelEl = [...container.querySelectorAll('label')].find(
    (l) => l.textContent?.trim() === 'Hide tickets already in a release',
  );
  return labelEl?.querySelector<HTMLInputElement>('input[type="checkbox"]') ?? null;
}

function selectAllCheckbox(): HTMLInputElement {
  return container.querySelector<HTMLInputElement>('input[aria-label="Select all loaded tickets"]')!;
}

function rowCheckbox(key: string): HTMLInputElement {
  return container.querySelector<HTMLInputElement>(`input[aria-label="Select ticket ${key}"]`)!;
}

function expandButtons(): HTMLButtonElement[] {
  return [...container.querySelectorAll('button')].filter((b) => {
    const label = b.getAttribute('aria-label');
    return label === 'Expand ticket details' || label === 'Collapse ticket details';
  });
}

function selectionStatus(): string {
  return container.querySelector('[role="status"]')?.textContent ?? '';
}

// React tracks the native value setter on controlled form elements to distinguish its own writes
// from external ones; assigning `.value` directly leaves that tracker believing nothing changed.
function setNativeValue(element: HTMLTextAreaElement, value: string) {
  const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!;
  setter.call(element, value);
  element.dispatchEvent(new Event('input', { bubbles: true }));
}

async function choose(select: HTMLSelectElement, value: string) {
  await act(async () => {
    select.value = value;
    select.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await flush();
}

async function loadTickets() {
  await act(async () => byText('Load tickets').click());
  await flush();
}

describe('TicketsRoute', () => {
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
    navigate.mockReset();
  });

  describe('choosing a product', () => {
    it('asks for a product first and offers no project choice before one is chosen', async () => {
      mockApi();
      await renderRoute({});

      expect(container.querySelector('h2')?.textContent).toBe('Browse tickets');
      expect(container.querySelector('label[for="productId"]')?.textContent).toBe('Product');
      const options = [...productSelect()!.querySelectorAll('option')].map((o) => o.textContent);
      expect(options).toEqual(['Select a product…', 'Alpha', 'Beta', 'Gamma']);
      expect(productSelect()!.value).toBe('');
      expect(container.textContent).toContain('Choose a product to browse its tickets.');
      expect(container.querySelector('#projectId')).toBeNull();
      expect(findByText('Load tickets')).toBeUndefined();
    });

    it('writes the chosen product to the URL and keeps new', async () => {
      mockApi();
      await renderRoute({ new: true });

      await choose(productSelect()!, 'p1');

      expect(navigate).toHaveBeenCalledTimes(1);
      const call = navigate.mock.calls[0][0] as {
        to: string;
        replace: boolean;
        search: (prev: Record<string, unknown>) => Record<string, unknown>;
      };
      expect(call.to).toBe('/tickets');
      expect(call.replace).toBe(true);
      expect(call.search({ new: true })).toEqual({ new: true, product: 'p1' });
    });

    it('preselects the product from the URL and shows which projects it searches', async () => {
      mockApi();
      await renderRoute({ product: 'p1' });

      expect(productSelect()!.value).toBe('p1');
      const helper = container.querySelector('#productScope');
      expect(helper?.textContent).toBe('Searches ABC, XYZ');
      expect([...helper!.querySelectorAll('.font-mono')].map((k) => k.textContent)).toEqual(['ABC', 'XYZ']);
      expect(productSelect()!.getAttribute('aria-describedby')).toBe('productScope');
      expect(projectSelect().querySelector('option')?.textContent).toBe('All Alpha projects');
      expect(container.textContent).not.toContain('Choose a product to browse its tickets.');
    });

    it.each([
      ['an unknown', 'nope'],
      ['an archived', 'p4'],
    ])('treats %s product id in the URL as nothing chosen', async (_label, id) => {
      mockApi();
      await renderRoute({ product: id });

      expect(productSelect()!.value).toBe('');
      expect(container.textContent).toContain('Choose a product to browse its tickets.');
      expect(container.querySelector('#projectId')).toBeNull();
    });

    it('shows the no-Jira-project notice and nothing to browse, add or create for a product without keys', async () => {
      mockApi();
      await renderRoute({ product: 'p3', new: true });

      const notice = container.querySelector('[data-reason="PRODUCT_NO_JIRA_PROJECT"]');
      expect(notice?.textContent).toBe('Beta has no Jira project. Add one in Portfolio › Products.');
      expect(notice?.querySelector('a')?.getAttribute('href')).toBe('/portfolio?tab=products');
      expect(container.querySelector('#projectId')).toBeNull();
      expect(findByText('Load tickets')).toBeUndefined();
      expect(findByText('Create release')).toBeUndefined();
      expect(container.querySelector('#releaseName')).toBeNull();
      expect(container.querySelector('#productScope')).toBeNull();
    });

    it('shows a loading line while products load', async () => {
      mockApi({ products: () => new Promise<Response>(() => {}) });
      await renderRoute({});

      const status = container.querySelector('[role="status"]');
      expect(status?.textContent).toBe('Loading products…');
      expect(productSelect()).toBeNull();
    });

    it('shows the failure and refetches on Try again', async () => {
      let calls = 0;
      mockApi({
        products: async () => {
          calls += 1;
          return calls === 1 ? json(503, { status: 503, reason: 'JIRA_UNREACHABLE' }) : json(200, PRODUCTS);
        },
      });
      await renderRoute({});

      expect(container.querySelector('[data-reason="JIRA_UNREACHABLE"]')?.getAttribute('role')).toBe('alert');

      await act(async () => byText('Try again').click());
      await flush();

      expect(productSelect()).not.toBeNull();
    });

    it('points to Portfolio › Products when there is no active product', async () => {
      mockApi({ products: async () => json(200, { products: [PRODUCTS.products[3]] }) });
      await renderRoute({});

      expect(container.textContent).toContain('No products yet. Add one in Portfolio › Products.');
      expect(container.querySelector('a[href="/portfolio?tab=products"]')).not.toBeNull();
      expect(productSelect()).toBeNull();
    });

    it('clears loaded tickets and the selection when the product changes', async () => {
      mockApi();
      await renderRoute({ product: 'p1' });
      await loadTickets();
      await act(async () => rowCheckbox('ABC-1').click());
      expect(selectionStatus()).toBe('1 selected');

      await renderRoute({ product: 'p2' });

      expect(productSelect()!.value).toBe('p2');
      expect(container.querySelector('tbody tr')).toBeNull();
      expect(rowCheckbox('ABC-1')).toBeNull();
    });
  });

  describe('new release mode', () => {
    it('titles the page New release and opens the dialog on the create form', async () => {
      mockApi();
      await renderRoute({ product: 'p1', new: true });

      expect(container.querySelector('h2')?.textContent).toBe('New release');
      expect(container.textContent).toContain('Pick a product, select its tickets, then name the release.');

      await loadTickets();
      await act(async () => rowCheckbox('ABC-1').click());
      await act(async () => byText('Create release').click());
      await flush();

      const dialog = [...container.querySelectorAll('dialog')].find((d) => d.textContent?.includes('Product: Alpha'));
      expect(dialog?.open).toBe(true);
      expect(dialog?.querySelector('h2')?.textContent).toBe('New release with 1 ticket(s)');
      expect(dialog?.textContent).toContain('Product: Alpha');
      expect(dialog?.querySelector('#releaseName')).not.toBeNull();
      expect([...dialog!.querySelectorAll('button')].some((b) => b.textContent === 'New release…')).toBe(false);
    });
  });

  describe('ticket browsing within the product', () => {
    it('searches every project of the product by default', async () => {
      const searchBodies = mockApi();
      await renderRoute();

      await loadTickets();

      expect(searchBodies).toEqual([
        { productId: 'p1', statusCategories: ['DONE'], hideAlreadyInRelease: true },
      ]);
    });

    it('offers Hide tickets already in a release, checked by default', async () => {
      mockApi();
      await renderRoute();

      expect(hideReleasedCheckbox()?.checked).toBe(true);
    });

    it('stops sending hideAlreadyInRelease once the box is unchecked', async () => {
      const searchBodies = mockApi();
      await renderRoute();

      await act(async () => hideReleasedCheckbox()!.click());
      await loadTickets();

      expect(searchBodies).toEqual([{ productId: 'p1', statusCategories: ['DONE'] }]);
    });

    it('sends hideAlreadyInRelease with advanced JQL too', async () => {
      const searchBodies = mockApi();
      await renderRoute();

      await act(async () => byText('Advanced: edit as JQL').click());
      const textarea = container.querySelector<HTMLTextAreaElement>('#jql')!;
      await act(async () => setNativeValue(textarea, 'project = ABC'));
      await loadTickets();

      expect(searchBodies[0]).toEqual({ jql: 'project = ABC', hideAlreadyInRelease: true });
    });

    describe('Hide tickets in release', () => {
      const release = (id: string, name: string, productId: string) => ({
        id,
        name,
        version: '1.0',
        status: 'OPEN',
        jiraProjectKey: null,
        createdAt: '2026-10-01T00:00:00Z',
        createdBy: 'a',
        frozenAt: null,
        frozenBy: null,
        lastRefreshedAt: null,
        itemCount: 1,
        products: [{ id: productId, name: productId, archived: false, hasInstalls: false }],
      });
      const RELEASES = [release('r1', 'Alpha one', 'p1'), release('r2', 'Beta one', 'p2')];

      function hideReleaseSelect(): HTMLSelectElement {
        return container.querySelector<HTMLSelectElement>('#hideReleaseId')!;
      }

      it("lists only the product's releases, with None first", async () => {
        mockApi({ releases: RELEASES });
        await renderRoute();

        const options = [...hideReleaseSelect().options].map((o) => o.textContent);
        expect(options).toEqual(['None', 'Alpha one 1.0']);
      });

      it('is disabled while every release is already hidden', async () => {
        mockApi({ releases: RELEASES });
        await renderRoute();

        expect(hideReleaseSelect().disabled).toBe(true);
        await act(async () => hideReleasedCheckbox()!.click());
        expect(hideReleaseSelect().disabled).toBe(false);
      });

      it('sends the chosen release so its tickets are left out', async () => {
        const searchBodies = mockApi({ releases: RELEASES });
        await renderRoute();

        await act(async () => hideReleasedCheckbox()!.click());
        await choose(hideReleaseSelect(), 'r1');
        await loadTickets();

        expect(searchBodies).toEqual([{ productId: 'p1', statusCategories: ['DONE'], releaseId: 'r1' }]);
      });

      it('names the hidden release when nothing matched', async () => {
        mockApi({ releases: RELEASES, tickets: { tickets: [] } });
        await renderRoute();

        await act(async () => hideReleasedCheckbox()!.click());
        await choose(hideReleaseSelect(), 'r1');
        await loadTickets();

        const hint = [...container.querySelectorAll('p')].find((p) => p.textContent?.includes('No tickets matched'));
        expect(hint?.textContent).toContain('or every match is in Alpha one 1.0');
      });
    });

    it('says the matches may already be in a release when hiding them left nothing', async () => {
      mockApi({ tickets: { tickets: [] } });
      await renderRoute();

      await loadTickets();

      const hint = [...container.querySelectorAll('p')].find((p) => p.textContent?.includes('No tickets matched'));
      expect(hint?.textContent).toContain('or every match is already in a release');
    });

    it('lists only the product projects in the project picker', async () => {
      mockApi();
      await renderRoute();

      const options = [...projectSelect().querySelectorAll('option')].map((o) => o.textContent);
      expect(options).toEqual(['All Alpha projects', 'ABC — Alpha Core', 'XYZ — Alpha Mobile']);
    });

    it('enables the sprint picker only once one project is chosen', async () => {
      mockApi();
      await renderRoute();

      expect(sprintSelect().disabled).toBe(true);

      await choose(projectSelect(), '10023');

      expect(sprintSelect().disabled).toBe(false);
    });

    it('says so when a project has no sprints instead of sitting empty', async () => {
      mockApi();
      await renderRoute();

      await choose(projectSelect(), '10024');

      expect(sprintSelect().disabled).toBe(true);
      expect(sprintSelect().querySelector('option')?.textContent).toBe('No sprints in this project');
    });

    it('posts the picker body for one project and renders a row per ticket', async () => {
      const searchBodies = mockApi();
      await renderRoute();

      await choose(projectSelect(), '10023');
      await choose(sprintSelect(), '10891');
      await loadTickets();

      expect(searchBodies).toEqual([
        { projectId: '10023', sprintId: '10891', statusCategories: ['DONE'], hideAlreadyInRelease: true },
      ]);
      const keyCells = [...container.querySelectorAll('tbody tr td:nth-child(2)')].map((c) => c.textContent);
      expect(keyCells).toEqual(['ABC-1', 'ABC-2', 'ABC-3']);
    });

    it('defaults the status filter to Done and sends it as chosen', async () => {
      const searchBodies = mockApi();
      await renderRoute();

      await choose(projectSelect(), '10023');
      await act(async () => statusCheckbox('Done').click());
      await act(async () => statusCheckbox('To Do').click());
      await loadTickets();

      expect(searchBodies[0]).toEqual({
        projectId: '10023',
        statusCategories: ['TO_DO'],
        hideAlreadyInRelease: true,
      });
    });

    it('sends only the query on the advanced JQL path', async () => {
      const searchBodies = mockApi();
      await renderRoute();

      await act(async () => byText('Advanced: edit as JQL').click());
      const textarea = container.querySelector<HTMLTextAreaElement>('#jql')!;
      await act(async () => setNativeValue(textarea, 'project = ABC AND sprint = 42'));
      await loadTickets();

      expect(searchBodies[0]).toEqual({ jql: 'project = ABC AND sprint = 42', hideAlreadyInRelease: true });
    });

    it('clears the previous selection on a fresh search', async () => {
      mockApi();
      await renderRoute();

      await loadTickets();
      await act(async () => rowCheckbox('ABC-1').click());
      expect(selectionStatus()).toBe('1 selected');

      await loadTickets();

      expect(selectionStatus()).toBe('0 selected');
    });

    it('selects a ticket from anywhere in its checkbox cell without expanding the row', async () => {
      mockApi();
      await renderRoute();

      await loadTickets();

      await act(async () => rowCheckbox(TICKETS[0].key).closest('label')!.click());
      expect(rowCheckbox(TICKETS[0].key).checked).toBe(true);
      expect(selectionStatus()).toBe('1 selected');
      expect(container.textContent).not.toContain(TICKETS[0].description);
    });

    it('select all checks every loaded row and the count agrees', async () => {
      mockApi();
      await renderRoute();

      await loadTickets();

      await act(async () => selectAllCheckbox().click());
      expect(selectionStatus()).toBe('3 selected');
      expect(TICKETS.every((t) => rowCheckbox(t.key).checked)).toBe(true);

      await act(async () => selectAllCheckbox().click());
      expect(selectionStatus()).toBe('0 selected');
    });

    it('expands one row at a time with its description', async () => {
      mockApi();
      await renderRoute();

      await loadTickets();

      await act(async () => expandButtons()[0].click());
      expect(container.textContent).toContain(TICKETS[0].description);

      await act(async () => expandButtons()[1].click());
      expect(container.textContent).not.toContain(TICKETS[0].description);
      expect(container.textContent).toContain(TICKETS[1].description);
    });

    it('names the scope when nothing matched', async () => {
      mockApi({ tickets: { tickets: [] } });
      await renderRoute();

      await choose(projectSelect(), '10023');
      await loadTickets();

      expect(container.textContent).toContain('No tickets found');
      const hint = [...container.querySelectorAll('p')].find((p) => p.textContent?.includes('No tickets matched'));
      expect(hint?.textContent).toContain('ABC');
      expect(hint?.textContent).toContain('with status Done');
    });

    it('renders a search failure with a Try again button', async () => {
      mockApi({ searchStatus: 503, searchReason: 'JIRA_UNREACHABLE' });
      await renderRoute();

      await loadTickets();

      expect(container.querySelector('[data-reason="JIRA_UNREACHABLE"]')).not.toBeNull();
      expect(findByText('Try again')).toBeDefined();
    });

    it('shows Add to release only with a selection and opens the product-fixed dialog with those keys', async () => {
      mockApi();
      await renderRoute();

      await loadTickets();

      expect(findByText('Add to release')).toBeUndefined();

      await act(async () => rowCheckbox('ABC-1').click());
      await act(async () => rowCheckbox('ABC-2').click());

      await act(async () => byText('Add to release').click());
      await flush();

      const dialog = [...container.querySelectorAll('dialog')].find((d) => d.textContent?.includes('Product: Alpha'));
      expect(dialog?.open).toBe(true);
      expect(dialog?.querySelector('h2')?.textContent).toBe('Add 2 ticket(s) to a release');
      expect(dialog?.textContent).toContain('Product: Alpha');
    });
  });
});
