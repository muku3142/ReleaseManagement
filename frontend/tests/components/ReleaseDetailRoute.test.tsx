import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReleaseDetailRoute, addResultMessage } from '../../src/routes/ReleaseDetailRoute';
import type { CodeLinks, Release, ReleaseItem } from '../../src/api/releases';

const navigate = vi.fn();
let locationHash = '';
vi.mock('@tanstack/react-router', () => ({
  useLocation: ({ select }: { select: (l: { hash: string }) => string }) => select({ hash: locationHash }),
  useParams: () => ({ releaseId: 'r1' }),
  useSearch: () => ({}),
  useNavigate: () => navigate,
  Link: (props: { children?: ReactNode }) => <a href="/releases">{props.children}</a>,
}));

const RELEASE: Release = {
  id: 'r1',
  name: 'Sprint 42',
  version: '2026.08',
  status: 'OPEN',
  jiraProjectKey: 'CNV',
  products: [],
  createdAt: '2026-08-05T00:00:00Z',
  createdBy: 'rm@acme.com',
  frozenAt: null,
  frozenBy: null,
  lastRefreshedAt: null,
  items: [],
  manualItems: [
    {
      id: 'm1',
      title: 'Run migration',
      description: 'Run scripts/migrate.sh',
      category: 'DATABASE_SCRIPT',
      addedAt: '2026-08-05T00:00:00Z',
    },
  ],
  unfreezeBlockedReason: null,

  packagesUrl: null,
};

const EMPTY_PROMOTIONS = { stages: [], currentStageId: null, nextStageId: null, entries: [] };
const EMPTY_INSTALLS = { releaseId: 'r1', frozen: false, inProduction: false, products: [], entries: [] };

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
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
          <ReleaseDetailRoute />
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

// Catch-all fetch mock for the add-tickets flow: the dialog's TicketBrowser fires
// jira/projects, jira sprints and tickets/search once opened, and DocumentsSection fires its
// own readiness/revisions calls regardless of whether the dialog is ever opened.
function mockAddTicketsApi(
  release: Release,
  overrides: { tickets?: unknown[]; addResponse?: Response } = {},
) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    if (url === '/api/jira/projects') return json(200, [{ id: '10023', key: 'CNV', name: 'Convergence' }]);
    if (url === '/api/products') return json(200, { products: [{ id: 'p1', name: 'Alpha', archived: false, clientCount: 0, releaseCount: 1, jiraProjectKeys: ['CNV'] }] });
    if (url.match(/^\/api\/jira\/projects\/[^/]+\/sprints$/)) return json(200, []);
    if (url === '/api/tickets/search' && method === 'POST') return json(200, { tickets: overrides.tickets ?? [] });
    if (url === `/api/releases/${release.id}/items` && method === 'POST') {
      return overrides.addResponse ?? json(200, { release, added: 0, alreadyPresent: 0, notFound: 0 });
    }
    if (url.endsWith('/promotions')) return json(200, EMPTY_PROMOTIONS);
    if (String(input).endsWith('/installs')) return json(200, EMPTY_INSTALLS);
    if (url.startsWith('/api/documents/readiness')) return json(200, { entries: [] });
    if (url.includes('/revisions')) return json(200, { revisions: [] });
    return json(200, release);
  });
}

describe('ReleaseDetailRoute', () => {
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
    locationHash = '';
  });

  // showModal() makes the rest of the document inert, so an error rendered in the page content
  // sits behind the backdrop where it cannot be read - on the two irreversible actions the
  // dialog exists to make deliberate.
  it('closes the freeze confirmation on failure so the error is readable', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/freeze')) return json(409, { status: 409, reason: 'RELEASE_FROZEN' });
      if (url.endsWith('/promotions')) return json(200, EMPTY_PROMOTIONS);
      if (String(input).endsWith('/installs')) return json(200, EMPTY_INSTALLS);
      return json(200, RELEASE);
    });

    await renderRoute();
    await act(async () => byText('Freeze release').click());
    expect(container.querySelector('dialog')?.open).toBe(true);

    const confirmButton = [...container.querySelectorAll('dialog button')].find(
      (b) => b.textContent === 'Freeze release',
    ) as HTMLButtonElement;
    await act(async () => confirmButton.click());
    await flush();

    expect([...container.querySelectorAll('dialog')].every((d) => !d.open)).toBe(true);
    expect(container.querySelector('[data-reason="RELEASE_FROZEN"]')?.textContent).toBe(
      'This release is frozen and can no longer be changed.',
    );
  });

  it('renders the Client installs section after the promotion history', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/promotions')) return json(200, EMPTY_PROMOTIONS);
      if (url.endsWith('/installs')) return json(200, EMPTY_INSTALLS);
      return json(200, RELEASE);
    });

    await renderRoute();
    await flush();
    const headings = [...container.querySelectorAll('h3')].map((h) => h.textContent);
    expect(headings.indexOf('Client installs')).toBeGreaterThan(headings.indexOf('Promotion history'));
    expect(container.textContent).toContain('Freeze this release to record client installs.');
  });

  // release_manual_item has no uniqueness constraint, so submitting the prefilled form silently
  // created a second copy of the item that was being edited.
  it('reopens the manual-item form empty after an edit was abandoned', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) =>
      String(input).endsWith('/installs') ? json(200, EMPTY_INSTALLS) : String(input).endsWith('/promotions') ? json(200, EMPTY_PROMOTIONS) : json(200, RELEASE),
    );

    await renderRoute();
    await act(async () => byText('Edit').click());
    expect(container.querySelector<HTMLInputElement>('#manualTitle')!.value).toBe('Run migration');
    expect(byText('Save changes')).toBeDefined();

    await act(async () => byText('+ Add manual item').click());
    expect(container.querySelector('#manualTitle')).toBeNull();

    await act(async () => byText('+ Add manual item').click());

    expect(container.querySelector<HTMLInputElement>('#manualTitle')!.value).toBe('');
    expect(container.querySelector<HTMLTextAreaElement>('#manualDescription')!.value).toBe('');
    expect(container.querySelector<HTMLSelectElement>('#manualCategory')!.value).toBe('OTHER');
    expect(byText('Add item')).toBeDefined();
  });

  it('shows the Products line with Unassigned for a release with no products', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) =>
      String(input).endsWith('/installs') ? json(200, EMPTY_INSTALLS) : String(input).endsWith('/promotions') ? json(200, EMPTY_PROMOTIONS) : json(200, RELEASE),
    );

    await renderRoute();

    expect(container.textContent).toContain('Product:');
    expect(container.textContent).toContain('Unassigned');
  });

  it('does not refetch the release it just deleted', async () => {
    const requested: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      requested.push(`${init?.method ?? 'GET'} ${String(input)}`);
      if (init?.method === 'DELETE') return new Response(null, { status: 204 });
      if (String(input).endsWith('/promotions')) return json(200, EMPTY_PROMOTIONS);
      if (String(input).endsWith('/installs')) return json(200, EMPTY_INSTALLS);
      return json(200, RELEASE);
    });

    await renderRoute();
    expect(requested.filter((r) => r === 'GET /api/releases/r1')).toHaveLength(1);

    await act(async () => byText('Delete release').click());
    const confirmButton = [...container.querySelectorAll('dialog button')].find(
      (b) => b.textContent === 'Delete release',
    ) as HTMLButtonElement;
    await act(async () => confirmButton.click());
    await flush();

    expect(navigate).toHaveBeenCalledWith({ to: '/releases' });
    expect(requested.filter((r) => r === 'GET /api/releases/r1')).toHaveLength(1);
    expect(container.querySelector('[data-reason="RELEASE_NOT_FOUND"]')).toBeNull();
  });

  it('closes the delete confirmation on failure so the error is readable', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      if (init?.method === 'DELETE') return json(409, { status: 409, reason: 'RELEASE_FROZEN' });
      if (String(input).endsWith('/promotions')) return json(200, EMPTY_PROMOTIONS);
      if (String(input).endsWith('/installs')) return json(200, EMPTY_INSTALLS);
      return json(200, RELEASE);
    });

    await renderRoute();
    await act(async () => byText('Delete release').click());

    const confirmButton = [...container.querySelectorAll('dialog button')].find(
      (b) => b.textContent === 'Delete release',
    ) as HTMLButtonElement;
    await act(async () => confirmButton.click());
    await flush();

    expect([...container.querySelectorAll('dialog')].every((d) => !d.open)).toBe(true);
    expect(container.querySelector('[data-reason="RELEASE_FROZEN"]')).not.toBeNull();
    expect(navigate).not.toHaveBeenCalled();
  });

  // The /releases readiness links (05-10) point at `#documents` on this page - if the section
  // moves or drops its id, those links silently land nowhere.
  it('documentsSectionIsRenderedLastWithItsAnchorId', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/promotions')) return json(200, EMPTY_PROMOTIONS);
      if (String(input).endsWith('/installs')) return json(200, EMPTY_INSTALLS);
      if (url.startsWith('/api/documents/readiness')) return json(200, { entries: [] });
      if (url.includes('/revisions')) return json(200, { revisions: [] });
      return json(200, RELEASE);
    });

    await renderRoute();
    await flush();

    const section = container.querySelector('#documents');
    expect(section).not.toBeNull();
    const wrapper = container.firstElementChild;
    expect(wrapper?.lastElementChild).toBe(section);
  });

  it('aFrozenReleaseShowsRecordPromotionInAHistorySectionAboveDocuments', async () => {
    mockAddTicketsApi({ ...RELEASE, status: 'FROZEN', frozenAt: '2026-08-06T00:00:00Z', frozenBy: 'rm@acme.com' });
    await renderRoute();
    await flush();

    expect(byText('Record promotion')).toBeDefined();
    const history = container.querySelector('#history')!;
    const documents = container.querySelector('#documents')!;
    expect(history.compareDocumentPosition(documents) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('anOpenReleaseOffersAnAddTicketsAffordance', async () => {
    mockAddTicketsApi(RELEASE);
    await renderRoute();

    await act(async () => byText('Add tickets').click());
    await flush();

    const dialogs = [...container.querySelectorAll('dialog')];
    expect(dialogs.some((d) => d.open)).toBe(true);
  });

  // ROADMAP criterion 6 / REL-03: the refusal happens before any work, not as a 409 after
  // tickets are chosen. The server-side refusal (ReleaseService.addTickets:67) is already
  // covered by Phase 3's tests and is not re-asserted here.
  it('aFrozenReleaseOffersNoAddTicketsAffordanceAtAll', async () => {
    const frozen: Release = {
      ...RELEASE,
      status: 'FROZEN',
      frozenAt: '2026-08-06T00:00:00Z',
      frozenBy: 'rm@acme.com',
    };
    mockAddTicketsApi(frozen);
    await renderRoute();

    expect(findByText('Add tickets')).toBeUndefined();
    expect([...container.querySelectorAll('dialog')].every((d) => !d.open)).toBe(true);
  });

  it('aSuccessfulAddShowsTheOutcomeBanner', async () => {
    const TICKETS = [
      {
        key: 'CNV-9',
        summary: 'Some fix',
        issueType: 'Bug',
        status: 'Done',
        resolution: 'Fixed',
        assignee: null,
        reporter: 'Bob',
        fixVersions: [],
        component: null,
        description: '',
        comments: [],
        commentTotal: 0,
      },
    ];
    const withProduct = { ...RELEASE, products: [{ id: 'p1', name: 'Alpha', archived: false, hasInstalls: false }] };
    mockAddTicketsApi(withProduct, {
      tickets: TICKETS,
      addResponse: json(200, { release: RELEASE, added: 1, alreadyPresent: 0, notFound: 0 }),
    });

    await renderRoute();
    await act(async () => byText('Add tickets').click());
    await flush();

    const projectSelect = container.querySelector<HTMLSelectElement>('#projectId')!;
    await act(async () => {
      projectSelect.value = '10023';
      projectSelect.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await flush();
    await act(async () => byText('Load tickets').click());
    await flush();

    const ticketCheckbox = container.querySelector<HTMLInputElement>(
      'input[aria-label="Select ticket CNV-9"]',
    )!;
    await act(async () => ticketCheckbox.click());
    await flush();

    await act(async () => byText('Add to this release').click());
    await flush();

    const statusEl = container.querySelector('[role="status"] p');
    expect(statusEl?.textContent).toBe(addResultMessage(1, 0, 0, RELEASE.name, RELEASE.version));
    expect([...container.querySelectorAll('dialog')].every((d) => !d.open)).toBe(true);
  });

  describe('code links', () => {
    const NO_CODE: CodeLinks = {
      status: 'NO_LINK',
      reason: null,
      incomplete: false,
      attemptedAt: null,
      commitsSource: null,
      pullRequests: [],
      commits: [],
      branches: [],
    };

    function item(key: string, codeLinks: CodeLinks): ReleaseItem {
      return {
        key,
        summary: `Summary ${key}`,
        issueType: 'Story',
        status: 'Done',
        resolution: null,
        assignee: null,
        reporter: null,
        fixVersions: [],
        component: null,
        description: '',
        comments: [],
        commentTotal: 0,
        addedAt: '2026-08-05T00:00:00Z',
        codeLinks,
        churn: null,
        bundle: null,
      };
    }

    const LINKED: CodeLinks = {
      ...NO_CODE,
      status: 'LINKED',
      commitsSource: 'ADO_COMPLETE',
      commits: [
        {
          sha: 'abc1234def',
          shortSha: 'abc1234',
          message: 'Fix',
          author: null,
          authoredAt: '2026-10-01T00:00:00Z',
          url: 'https://dev.azure.com/o/p/_git/r/commit/abc1234def',
        },
      ],
      pullRequests: [
        {
          id: '7',
          title: 'PR',
          status: 'completed',
          url: 'https://dev.azure.com/o/p/_git/r/pullrequest/7',
          sourceBranch: 'f',
          targetBranch: 'main',
          countsTowardCommits: true,
        },
      ],
    };
    const FAILED: CodeLinks = { ...NO_CODE, status: 'FAILED', reason: 'ADO_UNREACHABLE' };
    const NOT_ATTEMPTED: CodeLinks = { ...NO_CODE, status: 'NOT_ATTEMPTED' };

    function mockRelease(release: Release) {
      const calls: string[] = [];
      vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
        const url = String(input);
        const method = init?.method ?? 'GET';
        if (method === 'POST') calls.push(`${method} ${url}`);
        if (url.endsWith('/promotions')) return json(200, EMPTY_PROMOTIONS);
        if (String(input).endsWith('/installs')) return json(200, EMPTY_INSTALLS);
        if (url.startsWith('/api/documents/readiness')) return json(200, { entries: [] });
        if (url.includes('/revisions')) return json(200, { revisions: [] });
        return json(200, release);
      });
      return calls;
    }

    function dialogButton(text: string): HTMLButtonElement {
      return [...container.querySelectorAll('dialog button')].find(
        (b) => b.textContent === text,
      ) as HTMLButtonElement;
    }

    it('shows the chip for a linked ticket', async () => {
      mockRelease({ ...RELEASE, items: [item('CNV-1', LINKED)] });
      await renderRoute();
      expect(container.querySelector('[data-status="LINKED"]')?.textContent).toBe('1 commit · 1 PR');
    });

    it('shows per-ticket churn in the row, and No data where there is none', async () => {
      const churned = {
        ...item('CNV-1', LINKED),
        churn: { commitsToDone: 3, postDoneCommits: 1, reopens: 2, keyTaggedOnly: false, outlier: false },
      };
      mockRelease({ ...RELEASE, items: [churned, item('CNV-2', NO_CODE)] });
      await renderRoute();
      expect(container.textContent).toContain('3 to Done · 2 reopens · 1 after Done');
      expect(container.textContent).toContain('No data');
    });

    function arrow(): HTMLButtonElement {
      return container.querySelector<HTMLButtonElement>('button[aria-label$="ticket details"]')!;
    }

    function cellWithText(text: string): HTMLTableCellElement {
      return [...container.querySelectorAll('td')].find((td) => td.textContent === text)!;
    }

    it('clicking anywhere on a ticket row toggles its details', async () => {
      mockRelease({ ...RELEASE, items: [item('CNV-1', LINKED)] });
      await renderRoute();

      await act(async () => cellWithText('Summary CNV-1').click());
      expect(arrow().getAttribute('aria-expanded')).toBe('true');

      await act(async () => cellWithText('Summary CNV-1').click());
      expect(arrow().getAttribute('aria-expanded')).toBe('false');
    });

    it('clicking Retry inside a row does not toggle its details', async () => {
      mockRelease({ ...RELEASE, items: [item('CNV-1', FAILED)] });
      await renderRoute();

      await act(async () => byText('Retry').click());
      expect(arrow().getAttribute('aria-expanded')).toBe('false');
    });

    it('retries a failed ticket with its key', async () => {
      const calls = mockRelease({ ...RELEASE, items: [item('CNV-1', FAILED)] });
      await renderRoute();
      await act(async () => byText('Retry').click());
      expect(calls).toContain('POST /api/releases/r1/code-links/refresh?ticketKey=CNV-1');
    });

    it('refreshes all code links without a ticket key', async () => {
      const calls = mockRelease({ ...RELEASE, items: [item('CNV-1', LINKED)] });
      await renderRoute();
      await act(async () => byText('Refresh code links').click());
      expect(calls).toContain('POST /api/releases/r1/code-links/refresh');
    });

    it('warns at freeze about unchecked code links and still lets the user freeze', async () => {
      const calls = mockRelease({ ...RELEASE, items: [item('CNV-1', LINKED), item('CNV-2', NOT_ATTEMPTED)] });
      await renderRoute();
      await act(async () => byText('Freeze release').click());
      expect(container.querySelector('dialog')?.textContent).toContain(
        '1 ticket(s) have code links that failed or were never checked',
      );
      expect(dialogButton('Refresh code links first')).toBeDefined();
      await act(async () => dialogButton('Freeze release').click());
      await flush();
      expect(calls).toContain('POST /api/releases/r1/freeze');
    });

    it('refreshes code links from the freeze dialog and closes it', async () => {
      const calls = mockRelease({ ...RELEASE, items: [item('CNV-1', FAILED)] });
      await renderRoute();
      await act(async () => byText('Freeze release').click());
      await act(async () => dialogButton('Refresh code links first').click());
      expect(calls).toContain('POST /api/releases/r1/code-links/refresh');
      expect([...container.querySelectorAll('dialog')].every((d) => !d.open)).toBe(true);
    });

    it('shows no freeze warning when every ticket is linked or has no link', async () => {
      mockRelease({ ...RELEASE, items: [item('CNV-1', LINKED), item('CNV-2', NO_CODE)] });
      await renderRoute();
      await act(async () => byText('Freeze release').click());
      expect(container.querySelector('dialog')?.textContent).not.toContain('code links that failed');
      expect(findByText('Refresh code links first')).toBeUndefined();
    });

    it('shows chips but no refresh or retry on a frozen release', async () => {
      mockRelease({ ...RELEASE, status: 'FROZEN', items: [item('CNV-1', FAILED)] });
      await renderRoute();
      expect(container.querySelector('[data-status="FAILED"]')).not.toBeNull();
      expect(findByText('Refresh code links')).toBeUndefined();
      expect(findByText('Retry')).toBeUndefined();
    });
  });

  describe('assistant drawer and ticket anchors', () => {
    function ticket(key: string): ReleaseItem {
      return {
        key,
        summary: `Summary ${key}`,
        issueType: 'Story',
        status: 'Done',
        resolution: null,
        assignee: null,
        reporter: null,
        fixVersions: [],
        component: null,
        description: `Body of ${key}`,
        comments: [],
        commentTotal: 0,
        addedAt: '2026-08-05T00:00:00Z',
        codeLinks: {
          status: 'NO_LINK',
          reason: null,
          incomplete: false,
          attemptedAt: null,
          commitsSource: null,
          pullRequests: [],
          commits: [],
          branches: [],
        },
        churn: null,
        bundle: null,
      };
    }
    const WITH_TICKETS: Release = { ...RELEASE, items: [ticket('CNV-1'), ticket('CNV-2'), ticket('CNV-3')] };

    function mockApi(assistantEnabled: boolean) {
      vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
        const url = String(input);
        if (url === '/api/assistant/status') {
          return json(200, assistantEnabled ? { enabled: true, starters: { unscoped: [], release: ['Summarise it'] } } : { enabled: false });
        }
        if (url === '/api/assistant/freshness') return json(200, { lastIndexedAt: null });
        if (url.endsWith('/promotions')) return json(200, EMPTY_PROMOTIONS);
        if (String(input).endsWith('/installs')) return json(200, EMPTY_INSTALLS);
        if (url.startsWith('/api/documents/readiness')) return json(200, { entries: [] });
        if (url.includes('/revisions')) return json(200, { revisions: [] });
        return json(200, WITH_TICKETS);
      });
    }

    beforeEach(() => {
      Element.prototype.scrollIntoView = vi.fn();
    });

    it('shows no toggle or drawer when the assistant is disabled', async () => {
      mockApi(false);
      await renderRoute();
      expect(findByText('Ask about this release')).toBeUndefined();
      expect(container.querySelector('#assistant-drawer')).toBeNull();
    });

    it('opens and closes the drawer from the toggle and returns focus to it', async () => {
      mockApi(true);
      await renderRoute();
      const toggle = byText('Ask about this release');
      expect(toggle.getAttribute('aria-expanded')).toBe('false');
      expect(toggle.getAttribute('aria-controls')).toBe('assistant-drawer');

      await act(async () => toggle.click());
      expect(container.querySelector('#assistant-drawer')).not.toBeNull();
      expect(toggle.getAttribute('aria-expanded')).toBe('true');
      expect(container.querySelector('#assistant-drawer')!.textContent).toContain('Release: Sprint 42 2026.08');

      await act(async () => toggle.click());
      expect(container.querySelector('#assistant-drawer')).toBeNull();
      expect(document.activeElement).toBe(toggle);
    });

    it('gives every ticket row an id', async () => {
      mockApi(false);
      await renderRoute();
      expect(container.querySelector('tr#ticket-CNV-2')).not.toBeNull();
    });

    it('expands and scrolls to the ticket named by the hash, and follows a hash change', async () => {
      mockApi(false);
      locationHash = 'ticket-CNV-2';
      await renderRoute();
      expect(container.textContent).toContain('Body of CNV-2');
      expect(container.textContent).not.toContain('Body of CNV-3');
      expect(Element.prototype.scrollIntoView).toHaveBeenCalledWith({ block: 'center' });

      locationHash = 'ticket-CNV-3';
      await renderRoute();
      expect(container.textContent).toContain('Body of CNV-3');
    });

    it('ignores a hash that names no ticket on the release', async () => {
      mockApi(false);
      locationHash = 'ticket-NOPE-9';
      await renderRoute();
      expect(container.textContent).not.toContain('Body of');
    });
  });

  describe('unfreeze', () => {
    const FROZEN: Release = {
      ...RELEASE,
      status: 'FROZEN',
      frozenAt: '2026-08-06T00:00:00Z',
      frozenBy: 'rm@acme.com',
    };

    function confirmButton(label: string): HTMLButtonElement {
      return [...container.querySelectorAll('dialog button')].find(
        (b) => b.textContent === label,
      ) as HTMLButtonElement;
    }

    function headerButton(label: string) {
      return [...container.querySelectorAll('button')].find((b) => b.textContent === label && !b.closest('dialog'));
    }

    function mockUnfreeze(
      initial: Release,
      options: { unfreeze?: Response; entries?: unknown[] } = {},
    ) {
      let current = initial;
      const calls: string[] = [];
      vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
        const url = String(input);
        const method = init?.method ?? 'GET';
        calls.push(`${method} ${url}`);
        if (url.endsWith('/unfreeze')) {
          if (options.unfreeze) return options.unfreeze;
          current = { ...RELEASE, unfreezeBlockedReason: null };
          return json(200, current);
        }
        if (url.endsWith('/promotions')) return json(200, EMPTY_PROMOTIONS);
        if (url.endsWith('/installs')) return json(200, EMPTY_INSTALLS);
        if (url.startsWith('/api/documents/readiness')) return json(200, { entries: options.entries ?? [] });
        if (url.includes('/revisions')) return json(200, { revisions: [] });
        return json(200, current);
      });
      return calls;
    }

    it('offers an enabled Unfreeze button and the unlocked banner when nothing blocks it', async () => {
      mockUnfreeze(FROZEN);
      await renderRoute();
      const button = byText('Unfreeze release');
      expect(button.getAttribute('aria-disabled')).toBeNull();
      expect(button.className).toContain('border-gray-300');
      expect(button.className).not.toContain('bg-blue-600');
      expect(container.textContent).toContain(
        'This release is frozen. Its ticket set is locked. Unfreeze it to change tickets, if nothing has been deployed or installed yet.',
      );
    });

    it('unfreezes after confirmation, focuses Add tickets and announces it', async () => {
      const calls = mockUnfreeze(FROZEN);
      await renderRoute();
      await act(async () => byText('Unfreeze release').click());
      expect(container.querySelector('dialog[open]')?.textContent).toContain('Unfreeze Sprint 42 2026.08?');
      await act(async () => confirmButton('Unfreeze release').click());
      await flush();

      expect(calls).toContain('POST /api/releases/r1/unfreeze');
      expect(headerButton('Unfreeze release')).toBeUndefined();
      expect(document.activeElement).toBe(byText('Add tickets'));
      expect(container.querySelector('[role="status"].sr-only')?.textContent).toBe(
        'Sprint 42 2026.08 unfrozen. Tickets can be edited.',
      );
    });

    it.each([
      [
        'UNFREEZE_HAS_PROMOTIONS',
        "Can't unfreeze: this release has promotion history. Create a new release to change scope.",
      ],
      [
        'UNFREEZE_HAS_INSTALLS',
        "Can't unfreeze: client installs are recorded for this release. Create a new release to change scope.",
      ],
    ] as const)('blocks the button with the %s reason and opens no dialog', async (reason, copy) => {
      mockUnfreeze({ ...FROZEN, unfreezeBlockedReason: reason });
      await renderRoute();
      const button = byText('Unfreeze release');
      expect(button.getAttribute('aria-disabled')).toBe('true');
      expect(button.getAttribute('aria-describedby')).toBe('unfreeze-blocked-reason');
      expect(container.querySelector('#unfreeze-blocked-reason')?.textContent).toBe(copy);
      expect(container.textContent).toContain('This release is frozen. Its ticket set is locked and cannot be changed.');

      await act(async () => button.click());
      expect([...container.querySelectorAll('dialog')].every((d) => !d.open)).toBe(true);
    });

    it('shows a server rejection in the alert paragraph and closes the dialog', async () => {
      mockUnfreeze(FROZEN, {
        unfreeze: json(409, { status: 409, reason: 'UNFREEZE_HAS_PROMOTIONS' }),
      });
      await renderRoute();
      await act(async () => byText('Unfreeze release').click());
      await act(async () => confirmButton('Unfreeze release').click());
      await flush();

      expect([...container.querySelectorAll('dialog')].every((d) => !d.open)).toBe(true);
      expect(container.querySelector('[role="alert"][data-reason="UNFREEZE_HAS_PROMOTIONS"]')?.textContent).toContain(
        "Can't unfreeze: this release has promotion history.",
      );
    });

    it('says the freeze can be undone until a promotion or install is recorded', async () => {
      mockUnfreeze(RELEASE);
      await renderRoute();
      expect(headerButton('Unfreeze release')).toBeUndefined();
      await act(async () => byText('Freeze release').click());
      const body = container.querySelector('dialog')?.textContent ?? '';
      expect(body).toContain('You can unfreeze it again until a promotion or client install is recorded');
      expect(body).not.toContain('no way to unfreeze');
    });

    describe('out-of-date documents note', () => {
      const NOTE = 'This release was unfrozen. Its existing documents are out of date';
      const STALE = [{ releaseId: 'r1', documentType: 'RELEASE_NOTES', state: 'STALE' }];

      async function unfreezeNow() {
        await act(async () => byText('Unfreeze release').click());
        await act(async () => confirmButton('Unfreeze release').click());
        await flush();
      }

      it('shows after an unfreeze when the server reports a STALE document', async () => {
        mockUnfreeze(FROZEN, { entries: STALE });
        await renderRoute();
        await unfreezeNow();
        expect(container.textContent).toContain(NOTE);
      });

      it('is absent after an unfreeze when no document is STALE', async () => {
        mockUnfreeze(FROZEN, { entries: [] });
        await renderRoute();
        await unfreezeNow();
        expect(container.textContent).not.toContain(NOTE);
      });

      it('is absent on an open release that was not unfrozen in this visit', async () => {
        mockUnfreeze(RELEASE, { entries: STALE });
        await renderRoute();
        expect(container.textContent).not.toContain(NOTE);
      });
    });
  });

  describe('packages link and Add to roadmap', () => {
    function ticket(key: string): ReleaseItem {
      return {
        key,
        summary: `Summary ${key}`,
        issueType: 'Story',
        status: 'Done',
        resolution: null,
        assignee: null,
        reporter: null,
        fixVersions: [],
        component: null,
        description: '',
        comments: [],
        commentTotal: 0,
        addedAt: '2026-08-05T00:00:00Z',
        codeLinks: {
          status: 'NO_LINK',
          reason: null,
          incomplete: false,
          attemptedAt: null,
          commitsSource: null,
          pullRequests: [],
          commits: [],
          branches: [],
        },
        churn: null,
        bundle: null,
      };
    }

    const PACKAGES = 'https://contoso.sharepoint.com/sites/pkg';

    function mockApi(release: Release) {
      const posts: { url: string; body: unknown }[] = [];
      vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
        const url = String(input);
        const method = init?.method ?? 'GET';
        if (method === 'POST') posts.push({ url, body: JSON.parse(String(init?.body)) });
        if (url === '/api/roadmap/items/from-release') {
          return json(200, {
            added: [{ jiraKey: 'CNV-1', itemId: 'i1' }],
            alreadyPresent: [{ jiraKey: 'CNV-2', itemId: 'i2' }],
            notFound: [],
            rejected: [],
          });
        }
        if (url.endsWith('/promotions')) return json(200, EMPTY_PROMOTIONS);
        if (url.endsWith('/installs')) return json(200, EMPTY_INSTALLS);
        if (url.startsWith('/api/documents/readiness')) return json(200, { entries: [] });
        if (url.includes('/revisions')) return json(200, { revisions: [] });
        return json(200, release);
      });
      return posts;
    }

    it('shows the packages row for a FROZEN release', async () => {
      mockApi({ ...RELEASE, status: 'FROZEN', packagesUrl: PACKAGES });
      await renderRoute();
      const anchor = [...container.querySelectorAll('a')].find((a) => a.textContent === 'Open packages');
      expect(anchor?.getAttribute('href')).toBe(PACKAGES);
    });

    it('sends ticked tickets to the roadmap and reports the duplicates', async () => {
      const posts = mockApi({ ...RELEASE, items: [ticket('CNV-1'), ticket('CNV-2')] });
      await renderRoute();
      for (const key of ['CNV-1', 'CNV-2']) {
        const box = container.querySelector<HTMLInputElement>(`input[aria-label="Select ${key}"]`)!;
        await act(async () => box.click());
      }
      expect(container.textContent).toContain('2 selected');
      await act(async () => byText('Add to roadmap').click());
      await flush();
      expect(posts.find((p) => p.url === '/api/roadmap/items/from-release')?.body).toEqual({
        releaseId: 'r1',
        ticketKeys: ['CNV-1', 'CNV-2'],
      });
      expect(container.textContent).toContain('CNV-2 is already on the roadmap.');
      expect(container.textContent).not.toContain('2 selected');
    });
  });

  describe('bundles', () => {
    function bundled(key: string, bundle: string | null): ReleaseItem {
      return {
        key,
        summary: `Summary ${key}`,
        issueType: 'Story',
        status: 'Done',
        resolution: null,
        assignee: null,
        reporter: null,
        fixVersions: [],
        component: null,
        description: '',
        comments: [],
        commentTotal: 0,
        addedAt: '2026-08-05T00:00:00Z',
        codeLinks: {
          status: 'NO_LINK',
          reason: null,
          incomplete: false,
          attemptedAt: null,
          commitsSource: null,
          pullRequests: [],
          commits: [],
          branches: [],
        },
        churn: null,
        bundle,
      };
    }

    const MIXED = [
      bundled('A-1', 'Payments'),
      bundled('A-2', null),
      bundled('A-3', 'Payments'),
      bundled('A-4', 'Auth'),
    ];
    const GROUPED_ROWS = ['# Auth (1)', 'A-4', '# Payments (2)', 'A-1', 'A-3', '# Ungrouped (1)', 'A-2'];

    function mockBundles(release: Release) {
      const deletes: string[] = [];
      vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
        const url = String(input);
        if (init?.method === 'DELETE') {
          deletes.push(url);
          return json(200, release);
        }
        if (url.endsWith('/promotions')) return json(200, EMPTY_PROMOTIONS);
        if (url.endsWith('/installs')) return json(200, EMPTY_INSTALLS);
        if (url.startsWith('/api/documents/readiness')) return json(200, { entries: [] });
        if (url.includes('/revisions')) return json(200, { revisions: [] });
        return json(200, release);
      });
      return deletes;
    }

    function ticketRows(): string[] {
      const table = [...container.querySelectorAll('table')].find((t) =>
        t.querySelector('input[aria-label="Select all tickets"]'),
      )!;
      return [...table.querySelectorAll('tbody > tr')].map((row) => {
        const header = row.querySelector('th[scope="rowgroup"]');
        if (header) return `# ${header.textContent}`;
        return row.querySelector('td:nth-child(2)')?.textContent ?? '';
      });
    }

    function dialogButton(text: string): HTMLButtonElement {
      const button = [...container.querySelectorAll('dialog[open] button')].find((b) => b.textContent === text);
      expect(button, `no open-dialog button labelled "${text}"`).toBeDefined();
      return button as HTMLButtonElement;
    }

    function removeButton(key: string): HTMLButtonElement {
      return container.querySelector<HTMLButtonElement>(`button[aria-label="Remove ticket ${key}"]`)!;
    }

    it('groups tickets under sorted bundle headers with ungrouped last', async () => {
      mockBundles({ ...RELEASE, items: MIXED });
      await renderRoute();
      expect(ticketRows()).toEqual(GROUPED_ROWS);
      expect(container.querySelector('th[scope="rowgroup"]')?.getAttribute('colspan')).toBe('12');
    });

    it('shows no header rows when no ticket is bundled', async () => {
      mockBundles({ ...RELEASE, items: [bundled('A-2', null), bundled('A-1', null)] });
      await renderRoute();
      expect(ticketRows()).toEqual(['A-2', 'A-1']);
    });

    it('removes an ungrouped ticket straight away', async () => {
      const deletes = mockBundles({ ...RELEASE, items: MIXED });
      await renderRoute();
      await act(async () => removeButton('A-2').click());
      await flush();
      expect(deletes).toEqual(['/api/releases/r1/items/A-2']);
      expect(container.querySelector('dialog[open]')).toBeNull();
    });

    it('asks before removing a bundled ticket and removes the whole bundle on request', async () => {
      const deletes = mockBundles({ ...RELEASE, items: MIXED });
      await renderRoute();
      await act(async () => removeButton('A-1').click());
      expect(deletes).toEqual([]);
      const dialog = container.querySelector('dialog[open]');
      expect(dialog?.querySelector('h2')?.textContent).toBe('Remove A-1?');
      expect(dialog?.textContent).toContain('"Payments"');
      await act(async () => dialogButton('Remove whole bundle').click());
      await flush();
      expect(deletes).toEqual(['/api/releases/r1/items/A-1?withBundle=true']);
      expect(container.querySelector('dialog[open]')).toBeNull();
    });

    it('removes only the ticket when asked', async () => {
      const deletes = mockBundles({ ...RELEASE, items: MIXED });
      await renderRoute();
      await act(async () => removeButton('A-1').click());
      await act(async () => dialogButton('Remove only A-1').click());
      await flush();
      expect(deletes).toEqual(['/api/releases/r1/items/A-1']);
      expect(container.querySelector('dialog[open]')).toBeNull();
    });

    it('sends nothing when the bundle removal is cancelled', async () => {
      const deletes = mockBundles({ ...RELEASE, items: MIXED });
      await renderRoute();
      await act(async () => removeButton('A-1').click());
      await act(async () => dialogButton('Cancel').click());
      await flush();
      expect(deletes).toEqual([]);
      expect(container.querySelector('dialog[open]')).toBeNull();
    });

    it('keeps the header rows but no Remove column on a frozen release', async () => {
      mockBundles({
        ...RELEASE,
        status: 'FROZEN',
        frozenAt: '2026-08-06T00:00:00Z',
        frozenBy: 'rm@acme.com',
        items: MIXED,
      });
      await renderRoute();
      expect(ticketRows()).toEqual(GROUPED_ROWS);
      expect(container.querySelector('button[aria-label^="Remove ticket"]')).toBeNull();
      expect(container.querySelector('th[scope="rowgroup"]')?.getAttribute('colspan')).toBe('11');
    });

    it('selects every ticket across bundles with select all', async () => {
      mockBundles({ ...RELEASE, items: MIXED });
      await renderRoute();
      const selectAll = container.querySelector<HTMLInputElement>('input[aria-label="Select all tickets"]')!;
      await act(async () => selectAll.click());
      expect(container.textContent).toContain('4 selected');
    });
  });
});
