import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { TicketsRoute } from '../../src/routes/TicketsRoute';

const navigate = vi.fn();
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigate,
  Link: (props: { children?: ReactNode }) => <a href="/releases">{props.children}</a>,
}));

const PROJECTS = [
  { id: '10023', key: 'CNV', name: 'Convergence' },
  { id: '10024', key: 'PAY', name: 'Payments' },
];

const DEFAULT_SPRINTS: Record<string, { id: string; name: string; state: string }[]> = {
  '10023': [{ id: '10891', name: 'Sprint 42', state: 'active' }],
  '10024': [],
};

const TICKETS = [
  {
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
    comments: ['Looks good now.'],
    commentTotal: 1,
  },
  {
    key: 'CNV-2',
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
    key: 'CNV-3',
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
};

// Every /api/tickets/search request body is captured so the request-shape assertions (tests 4,
// 5, 6) can read back exactly what was sent - not just that a search happened.
function mockApi(overrides: MockOverrides = {}): Record<string, unknown>[] {
  const searchBodies: Record<string, unknown>[] = [];
  const sprintsByProject = overrides.sprintsByProject ?? DEFAULT_SPRINTS;
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
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
    if (url === '/api/releases') return json(200, { releases: [] });
    throw new Error(`Unexpected fetch: ${method} ${url}`);
  });
  return searchBodies;
}

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderRoute() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <TicketsRoute />
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

function findByText(text: string): HTMLButtonElement | undefined {
  return [...container.querySelectorAll('button')].find((b) => b.textContent === text);
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
// Using the underlying prototype setter avoids that - copied verbatim from
// TemplateDetailRoute.test.tsx, which established the pattern.
function setNativeValue(element: HTMLTextAreaElement, value: string) {
  const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!;
  setter.call(element, value);
  element.dispatchEvent(new Event('input', { bubbles: true }));
}

async function selectProject(id: string) {
  await act(async () => {
    const el = projectSelect();
    el.value = id;
    el.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await flush();
}

async function selectSprint(id: string) {
  await act(async () => {
    const el = sprintSelect();
    el.value = id;
    el.dispatchEvent(new Event('change', { bubbles: true }));
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
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
    navigate.mockReset();
  });

  it('listsEveryJiraProjectInThePicker', async () => {
    mockApi();
    await renderRoute();

    const options = [...projectSelect().querySelectorAll('option')].map((o) => o.textContent);
    expect(options).toContain('CNV — Convergence');
    expect(options).toContain('PAY — Payments');
  });

  it('theSprintPickerIsDisabledUntilAProjectIsChosen', async () => {
    mockApi();
    await renderRoute();

    expect(sprintSelect().disabled).toBe(true);

    await selectProject('10023');

    expect(sprintSelect().disabled).toBe(false);
  });

  it('aProjectWithNoSprintsSaysSoInsteadOfSittingEmpty', async () => {
    mockApi();
    await renderRoute();

    await selectProject('10024');

    expect(sprintSelect().disabled).toBe(true);
    expect(sprintSelect().querySelector('option')?.textContent).toBe('No sprints in this project');
  });

  it('loadTicketsPostsThePickerBodyAndRendersARowPerTicket', async () => {
    const searchBodies = mockApi();
    await renderRoute();

    await selectProject('10023');
    await selectSprint('10891');
    await loadTickets();

    expect(searchBodies).toHaveLength(1);
    expect(searchBodies[0]).toEqual({ projectId: '10023', sprintId: '10891', statusCategories: ['DONE'] });

    const keyCells = [...container.querySelectorAll('tbody tr td:nth-child(2)')].map((c) => c.textContent);
    expect(keyCells).toEqual(['CNV-1', 'CNV-2', 'CNV-3']);
  });

  it('theStatusFilterDefaultsToDoneAndIsSentAsChosen', async () => {
    const searchBodies = mockApi();
    await renderRoute();

    await selectProject('10023');
    await act(async () => statusCheckbox('Done').click());
    await act(async () => statusCheckbox('To Do').click());
    await loadTickets();

    expect(searchBodies[0]).toEqual({ projectId: '10023', statusCategories: ['TO_DO'] });
  });

  it('theAdvancedJqlPathSendsOnlyTheQueryAndIgnoresThePickers', async () => {
    const searchBodies = mockApi();
    await renderRoute();

    await act(async () => byText('Advanced: edit as JQL').click());
    const textarea = container.querySelector<HTMLTextAreaElement>('#jql')!;
    await act(async () => setNativeValue(textarea, 'project = CNV AND sprint = 42'));
    await loadTickets();

    expect(searchBodies[0]).toEqual({ jql: 'project = CNV AND sprint = 42' });
    expect(searchBodies[0]).not.toHaveProperty('projectId');
  });

  it('aFreshSearchClearsThePreviousSelection', async () => {
    mockApi();
    await renderRoute();

    await selectProject('10023');
    await loadTickets();

    await act(async () => rowCheckbox('CNV-1').click());
    expect(selectionStatus()).toBe('1 selected');

    await loadTickets();

    expect(selectionStatus()).toBe('0 selected');
  });

  it('selectAllChecksEveryLoadedRowAndTheCountAgrees', async () => {
    mockApi();
    await renderRoute();

    await selectProject('10023');
    await loadTickets();

    await act(async () => selectAllCheckbox().click());
    expect(selectionStatus()).toBe('3 selected');
    expect(TICKETS.every((t) => rowCheckbox(t.key).checked)).toBe(true);

    await act(async () => selectAllCheckbox().click());
    expect(selectionStatus()).toBe('0 selected');
  });

  it('expandingARowShowsItsDescriptionAndCommentsAndOnlyOneRowAtATime', async () => {
    mockApi();
    await renderRoute();

    await selectProject('10023');
    await loadTickets();

    await act(async () => expandButtons()[0].click());
    expect(container.textContent).toContain(TICKETS[0].description);

    await act(async () => expandButtons()[1].click());
    expect(container.textContent).not.toContain(TICKETS[0].description);
    expect(container.textContent).toContain(TICKETS[1].description);
  });

  it('anEmptyResultRendersTheNamedScopeHint', async () => {
    mockApi({ tickets: { tickets: [] } });
    await renderRoute();

    await selectProject('10023');
    await loadTickets();

    expect(container.textContent).toContain('No tickets found');
    const hint = [...container.querySelectorAll('p')].find((p) => p.textContent?.includes('No tickets matched'));
    expect(hint?.textContent).toContain('CNV');
    expect(hint?.textContent).toContain('with status Done');
  });

  it('aSearchFailureRendersTheAlertAndATryAgainButton', async () => {
    mockApi({ searchStatus: 503, searchReason: 'JIRA_UNREACHABLE' });
    await renderRoute();

    await selectProject('10023');
    await loadTickets();

    expect(container.querySelector('[data-reason="JIRA_UNREACHABLE"]')).not.toBeNull();
    expect(findByText('Try again')).toBeDefined();
  });

  it('theAddToReleaseTriggerAppearsOnlyWithASelectionAndOpensTheDialogWithThoseKeys', async () => {
    mockApi();
    await renderRoute();

    await selectProject('10023');
    await loadTickets();

    expect(findByText('Add to release')).toBeUndefined();

    await act(async () => rowCheckbox('CNV-1').click());
    await act(async () => rowCheckbox('CNV-2').click());

    await act(async () => byText('Add to release').click());
    await flush();

    const dialog = container.querySelector('dialog');
    expect(dialog?.open).toBe(true);
    expect(dialog?.querySelector('h2')?.textContent).toBe('Add 2 ticket(s) to a release');
  });
});
