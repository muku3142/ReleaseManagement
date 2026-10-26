import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { DashboardRoute } from '../../src/routes/DashboardRoute';
import type { DashboardData, DashboardReleaseRow } from '../../src/api/dashboard';

type LinkProps = { children?: ReactNode; to?: string; params?: Record<string, string>; hash?: string };

const navigate = vi.fn();
let searchParams: Record<string, unknown> = {};

vi.mock('@tanstack/react-router', () => ({
  Link: (props: LinkProps) => {
    let href = props.to ?? '';
    for (const [key, value] of Object.entries(props.params ?? {})) {
      href = href.replace(`$${key}`, value);
    }
    if (props.hash) href += `#${props.hash}`;
    return <a href={href}>{props.children}</a>;
  },
  useSearch: () => searchParams,
  useNavigate: () => navigate,
}));

const STAGES = [
  { id: 'qa', name: 'QA', position: 1, production: false, retired: false },
  { id: 'prod', name: 'Production', position: 2, production: true, retired: false },
];

function row(overrides: Partial<DashboardReleaseRow> = {}): DashboardReleaseRow {
  return {
    id: 'r1',
    name: 'Sprint 42',
    version: '2026.08',
    status: 'FROZEN',
    createdAt: '2026-08-01T00:00:00Z',
    frozenAt: '2026-08-05T00:00:00Z',
    currentStageId: 'qa',
    shippedAt: null,
    lastFailedStageId: null,
    createdToFrozenSeconds: 4 * 86400,
    frozenToProductionSeconds: null,
    composition: { tickets: 10, manual: 2, linked: 6, noLink: 2, failed: 1, noData: 1 },
    storyPoints: { total: 21, estimated: 8, unestimated: 2, unread: 0 },
    churn: {
      medianCommitsToDone: 3.25,
      commitNoData: 2,
      reopens: 4,
      reopenNoData: 1,
      postDoneShare: 0.126,
      outliers: 3,
      keyTagged: 5,
    },
    ...overrides,
  };
}

function data(releases: DashboardReleaseRow[], totalReleases = releases.length): DashboardData {
  return {
    summary: {
      visibleReleases: releases.length,
      totalReleases,
      medianCreatedToFrozenSeconds: 4 * 86400,
      createdToFrozenCount: 1,
      medianFrozenToProductionSeconds: null,
      frozenToProductionCount: 0,
      releasesPerStage: [{ stageId: 'qa', count: 1 }],
      notPromoted: 0,
    },
    stages: STAGES,
    releases,
  };
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

let container: HTMLDivElement;
let root: Root;
let dashboardResponse: () => Response;
let readinessResponse: () => Response;
let productsResponse: () => Response;
const portfolioRequests: string[] = [];
const PORTFOLIO_SUMMARY = {
  tiles: { clientsBehind: 0, notInstalled: 0, activeProducts: 0, activeClients: 0, installsLast30Days: 0 },
  products: [],
  totals: { open: 0, frozen: 0, inProduction: 0 },
  unassigned: null,
};

const PRODUCT_LIST = [
  { id: 'a', name: 'Core', archived: false, clientCount: 1, releaseCount: 1 },
  { id: 'b', name: 'Billing', archived: false, clientCount: 1, releaseCount: 1 },
];

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
          <DashboardRoute />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
  await flush();
}

function firstRowCells(): HTMLTableCellElement[] {
  return [...container.querySelectorAll('tbody tr')[0].querySelectorAll('td')];
}

describe('DashboardRoute', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    searchParams = {};
    navigate.mockReset();
    portfolioRequests.length = 0;
    dashboardResponse = () => json(200, data([row()]));
    productsResponse = () => json(200, { products: PRODUCT_LIST });
    readinessResponse = () =>
      json(200, {
        entries: [
          { releaseId: 'r1', documentType: 'RELEASE_NOTES', state: 'UP_TO_DATE' },
          { releaseId: 'r1', documentType: 'RUNBOOK', state: 'STALE' },
          { releaseId: 'r1', documentType: 'APPROVAL', state: 'NO_TEMPLATE' },
        ],
      });
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/dashboard/releases')) return dashboardResponse();
      if (url.startsWith('/api/dashboard/portfolio/')) {
        portfolioRequests.push(url);
        return json(200, url.includes('/summary') ? PORTFOLIO_SUMMARY : url.includes('/behind') ? { behind: [], notInstalled: [] } : { products: [], clients: [], cells: [] });
      }
      if (url.startsWith('/api/documents/readiness')) return readinessResponse();
      if (url.startsWith('/api/products')) return productsResponse();
      if (url.startsWith('/api/dashboard/deployments')) return json(200, { entries: [], hasMore: false });
      if (url.startsWith('/api/environment-ladder')) return json(200, { stages: [] });
      return json(404, {});
    });
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
  });

  it('renders the summary strip with the visible-set footnote and paperwork counts', async () => {
    await renderRoute();

    const text = container.textContent!;
    expect(container.querySelector('h2')!.textContent).toBe('Dashboard');
    for (const caption of [
      'Releases missing documents',
      'Documents out of date',
      'Median created → frozen',
      'Median frozen → production',
      'Releases per stage',
    ]) {
      expect(text).toContain(caption);
    }
    expect(text).not.toContain('Need paperwork');
    expect(text).toContain('Over 1 visible releases.');
    expect(text).toContain('4 d');
    expect(text).toContain('QA 1');
    expect(text).toContain('Not promoted 0');
    const tiles = [...container.querySelectorAll('section[aria-label="Summary"] .text-lg')];
    expect(tiles[0].textContent).toBe('1');
    expect(tiles[1].textContent).toBe('1');
    expect(container.querySelector('[title="No release has reached this point yet"]')).toBeTruthy();
  });

  it('renders each readiness state, keeping NO_TEMPLATE distinct from NOT_GENERATED', async () => {
    await renderRoute();

    const cells = firstRowCells();
    expect(cells[0].querySelector('a')!.getAttribute('href')).toBe('/releases/r1');
    expect(cells[1].textContent).toBe('Up to date');
    expect(cells[2].textContent).toBe('Stale');
    expect(cells[3].textContent).toBe('No template');
    expect(cells[3].textContent).not.toBe('Not generated');
  });

  it('shows stage, durations and composition for a frozen, unpromoted-to-production release', async () => {
    await renderRoute();

    const cells = firstRowCells();
    expect(cells[4].textContent).toContain('QA');
    expect(cells[4].querySelector('a')!.getAttribute('href')).toBe('/releases/r1#history');
    expect(cells[5].textContent).toBe('4 d');
    expect(cells[6].textContent).toBe('—');
    expect(cells[6].getAttribute('title')).toBe('Not in production yet');
    expect(cells[7].textContent).toBe('10 tickets · 2 manual');
    expect(container.querySelector('tfoot')!.textContent).toContain('Median (visible)');
  });

  it('shows Not promoted for a release with no stage, and the failed marker with the stage not advanced', async () => {
    dashboardResponse = () =>
      json(
        200,
        data([
          row({ id: 'r1', currentStageId: null, lastFailedStageId: 'qa' }),
          row({ id: 'r2', name: 'Other', currentStageId: null }),
        ]),
      );
    await renderRoute();

    const rows = [...container.querySelectorAll('tbody tr')];
    expect(rows[0].textContent).toContain('Last promotion failed');
    expect(rows[0].textContent).toContain('Not promoted');
    expect(rows[0].querySelector('li[data-state="failed"]')).toBeTruthy();
    expect(rows[0].querySelector('li[data-state="reached"]')).toBeNull();
    expect(rows[1].textContent).not.toContain('Last promotion failed');
  });

  it('describes the code-links bar with all four counts and keeps no-data visible', async () => {
    await renderRoute();

    const cell = firstRowCells()[9];
    expect(cell.querySelector('[role="img"]')!.getAttribute('aria-label')).toBe(
      '6 linked, 2 no link, 1 failed, 1 no data',
    );
    expect(cell.textContent).toContain('6 linked · 2 no link · 1 failed · 1 no data');
    expect(cell.getAttribute('title')).toContain('2 manual items are excluded');
  });

  it('says No Jira tickets for a release with none', async () => {
    dashboardResponse = () =>
      json(
        200,
        data([row({ composition: { tickets: 0, manual: 3, linked: 0, noLink: 0, failed: 0, noData: 0 } })]),
      );
    await renderRoute();

    expect(firstRowCells()[9].textContent).toBe('No Jira tickets');
  });

  describe('story points column', () => {
    async function storyPointsCell(overrides: Partial<DashboardReleaseRow>): Promise<HTMLTableCellElement> {
      dashboardResponse = () => json(200, data([row(overrides)]));
      await renderRoute();
      return firstRowCells()[8];
    }

    it('has a Story points column header', async () => {
      await renderRoute();

      const headings = [...container.querySelectorAll('thead th')].map((th) => th.textContent);
      expect(headings).toContain('Story points');
    });

    it('shows the total and the unestimated count', async () => {
      const cell = await storyPointsCell({ storyPoints: { total: 21, estimated: 8, unestimated: 2, unread: 0 } });

      expect(cell.textContent).toContain('21 pts');
      expect(cell.textContent).toContain('2 unestimated');
    });

    it('omits the unestimated note when every ticket has an estimate', async () => {
      const cell = await storyPointsCell({ storyPoints: { total: 13.5, estimated: 4, unestimated: 0, unread: 0 } });

      expect(cell.textContent).toContain('13.5 pts');
      expect(cell.textContent).not.toContain('unestimated');
    });

    it('shows Unknown when no ticket was read', async () => {
      const cell = await storyPointsCell({ storyPoints: { total: 0, estimated: 0, unestimated: 0, unread: 10 } });

      expect(cell.textContent).toBe('Unknown');
    });

    it('shows the not-read count next to a partial total', async () => {
      const cell = await storyPointsCell({ storyPoints: { total: 5, estimated: 2, unestimated: 0, unread: 3 } });

      expect(cell.textContent).toContain('5 pts');
      expect(cell.textContent).toContain('3 not read');
    });

    it('shows a dash for a release with no Jira tickets', async () => {
      const cell = await storyPointsCell({
        composition: { tickets: 0, manual: 3, linked: 0, noLink: 0, failed: 0, noData: 0 },
        storyPoints: { total: 0, estimated: 0, unestimated: 0, unread: 0 },
      });

      expect(cell.textContent).toBe('—');
    });
  });

  it('renders churn and rework with denominators', async () => {
    await renderRoute();

    const cells = firstRowCells();
    expect(cells[10].textContent).toContain('3.3 commits to Done');
    expect(cells[10].textContent).toContain('5 key-tagged only');
    expect(cells[10].textContent).toContain('3 outliers');
    expect(cells[10].textContent).toContain('2 no data');
    expect(cells[11].textContent).toContain('4 reopens · 13% after Done');
    expect(cells[11].textContent).toContain('1 no data');
  });

  it('says No data when every ticket is excluded from the churn median', async () => {
    dashboardResponse = () =>
      json(
        200,
        data([
          row({
            churn: {
              medianCommitsToDone: null,
              commitNoData: 4,
              reopens: 0,
              reopenNoData: 0,
              postDoneShare: null,
              outliers: 0,
              keyTagged: 0,
            },
          }),
        ]),
      );
    await renderRoute();

    const cells = firstRowCells();
    expect(cells[10].textContent).toBe('No data');
    expect(cells[11].textContent).toBe('0 reopens');
  });

  it('always shows the churn disclaimer and the denominator footnote', async () => {
    await renderRoute();

    expect(container.textContent).toContain('They are not Jira cycle time.');
    expect(container.textContent).toContain('manual items are excluded. Medians and counts are computed over the releases shown.');
  });

  it('toggles show-all through the URL', async () => {
    await renderRoute();
    expect(container.textContent).toContain('In-flight releases and those shipped in the last 90 days.');

    const box = [...container.querySelectorAll('label')]
      .find((l) => l.textContent === 'Show all releases')!
      .querySelector('input')!;
    await act(async () => box.click());

    const call = navigate.mock.calls[0][0];
    expect(call.replace).toBe(true);
    expect(call.search({ stage: 'qa' })).toEqual({ stage: 'qa', all: true });
  });

  it('captions the all-releases view', async () => {
    searchParams = { all: true };
    await renderRoute();

    expect(container.textContent).toContain('All releases.');
    expect((fetch as unknown as { mock: { calls: string[][] } }).mock.calls.map((c) => c[0])).toContain(
      '/api/dashboard/releases?scope=all',
    );
  });

  it('points to the ticket browser when no releases exist at all', async () => {
    dashboardResponse = () => json(200, data([], 0));
    await renderRoute();

    expect(container.textContent).toContain('No releases yet');
    expect([...container.querySelectorAll('a')].find((a) => a.textContent === 'Browse tickets')!.getAttribute('href')).toBe(
      '/tickets',
    );
  });

  it('offers Show all releases when releases exist but none are visible', async () => {
    dashboardResponse = () => json(200, data([], 7));
    await renderRoute();

    expect(container.textContent).toContain('No in-flight or recently shipped releases');
    const link = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Show all releases')!;
    await act(async () => link.click());
    expect(navigate.mock.calls[0][0].search({})).toEqual({ all: true });
  });

  describe('document tiles', () => {
    const TYPES = ['RELEASE_NOTES', 'RUNBOOK', 'APPROVAL'];
    const entries = (releaseId: string, states: string[]) =>
      TYPES.map((documentType, i) => ({ releaseId, documentType, state: states[i] }));

    beforeEach(() => {
      dashboardResponse = () =>
        json(200, data([row({ id: 'r1', name: 'One' }), row({ id: 'r2', name: 'Two' }), row({ id: 'r3', name: 'Three' })]));
      readinessResponse = () =>
        json(200, {
          entries: [
            ...entries('r1', ['UP_TO_DATE', 'STALE', 'NO_TEMPLATE']),
            ...entries('r2', ['UP_TO_DATE', 'UP_TO_DATE', 'UP_TO_DATE']),
            ...entries('r3', ['NOT_GENERATED', 'UP_TO_DATE', 'UP_TO_DATE']),
          ],
        });
    });

    const tile = (id: string) => container.querySelector<HTMLButtonElement>(`button#${id}`)!;
    const bodyRows = () => [...container.querySelectorAll('tbody tr')].map((tr) => tr.querySelector('a')!.textContent);
    const searchOf = (call: number) => navigate.mock.calls[call][0].search;

    it('shows unit lines and the help text behind the question mark', async () => {
      await renderRoute();

      expect(tile('tile-missing').textContent).toBe('2Releases missing documentsreleases');
      expect(tile('tile-stale').textContent).toBe('1Documents out of datereleases');
      const help = container.querySelectorAll<HTMLButtonElement>('button[aria-label="What does this count?"]');
      await act(async () => help[0].click());
      await act(async () => help[1].click());
      expect(container.querySelector('#tile-missing-help')!.textContent).toContain(
        'Document types that have no template are ignored.',
      );
      expect(container.querySelector('#tile-stale-help')!.textContent).toContain(
        'A frozen release is only counted if it was unfrozen and its tickets changed.',
      );
    });

    it('toggles docs through the URL', async () => {
      await renderRoute();
      await act(async () => tile('tile-missing').click());
      expect(searchOf(0)({ stage: 'qa' })).toEqual({ stage: 'qa', docs: 'missing' });
      expect(navigate.mock.calls[0][0].replace).toBe(true);

      searchParams = { docs: 'missing' };
      await renderRoute();
      expect(tile('tile-missing').getAttribute('aria-pressed')).toBe('true');
      await act(async () => tile('tile-missing').click());
      expect(searchOf(1)({ docs: 'missing' })).toEqual({ docs: undefined });
      await act(async () => tile('tile-stale').click());
      expect(searchOf(2)({ docs: 'missing' })).toEqual({ docs: 'stale' });
    });

    it('filters the matrix to exactly the counted releases and keeps tile figures', async () => {
      searchParams = { docs: 'missing' };
      await renderRoute();

      expect(bodyRows()).toEqual(['One', 'Three']);
      expect(container.textContent).toContain('Showing: Releases missing documents · 2 of 3 releases');
      expect(tile('tile-missing').textContent).toContain('2');
      expect(tile('tile-stale').textContent).toContain('1');
    });

    it('filters to out-of-date releases', async () => {
      searchParams = { docs: 'stale' };
      await renderRoute();

      expect(bodyRows()).toEqual(['One']);
      expect(container.textContent).toContain('Showing: Documents out of date · 1 of 3 releases');
    });

    it('Clear filter clears docs and focuses the tile', async () => {
      searchParams = { docs: 'missing' };
      await renderRoute();
      const clear = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Clear filter')!;
      await act(async () => clear.click());

      expect(searchOf(0)({ docs: 'missing' })).toEqual({ docs: undefined });
      expect(document.activeElement).toBe(tile('tile-missing'));
    });

    it.each([
      ['missing', 'Every visible release has all of its documents generated and current.'],
      ['stale', 'Every visible release has up-to-date documents.'],
    ])('says nothing matches for docs=%s when every release is fine', async (docs, body) => {
      searchParams = { docs };
      readinessResponse = () => json(200, { entries: entries('r1', ['UP_TO_DATE', 'UP_TO_DATE', 'UP_TO_DATE']) });
      dashboardResponse = () => json(200, data([row({ id: 'r1' })]));
      await renderRoute();

      expect(container.textContent).toContain('No releases match this filter');
      expect(container.textContent).toContain(body);
      expect(container.querySelector('tbody')).toBeNull();
    });

    it('disables both tiles and ignores docs=missing when readiness fails', async () => {
      searchParams = { docs: 'missing' };
      readinessResponse = () => json(500, { status: 500, reason: 'INTERNAL_ERROR' });
      await renderRoute();

      for (const id of ['tile-missing', 'tile-stale']) {
        expect(tile(id).textContent).toContain('—');
        expect(tile(id).getAttribute('aria-disabled')).toBe('true');
        expect(tile(id).getAttribute('aria-pressed')).toBe('false');
        await act(async () => tile(id).click());
      }
      expect(container.textContent).toContain('Document status unavailable');
      expect(navigate).not.toHaveBeenCalled();
      expect(bodyRows()).toEqual(['One', 'Two', 'Three']);
      expect(container.textContent).not.toContain('Showing:');
    });
  });

  describe('product filter', () => {
    const fetchedUrls = () => (fetch as unknown as { mock: { calls: string[][] } }).mock.calls.map((c) => c[0]);

    it('sends the selected products and narrows the footnote to them', async () => {
      searchParams = { products: ['a'] };
      dashboardResponse = () => json(200, data([row(), row({ id: 'r2', name: 'Other' })]));
      await renderRoute();

      expect(fetchedUrls().some((u) => u.startsWith('/api/dashboard/releases') && u.includes('productIds=a'))).toBe(true);
      expect(container.textContent).toContain('Over 2 visible releases in Core.');
    });

    it('counts products in the footnote beyond two', async () => {
      searchParams = { products: ['a', 'b', 'c'] };
      await renderRoute();

      expect(container.textContent).toContain('Over 1 visible releases in 3 products.');
    });

    it('has no scope in the footnote without a filter', async () => {
      await renderRoute();

      expect(container.textContent).toContain('Over 1 visible releases.');
    });

    it('adds and clears products through the URL and announces it', async () => {
      searchParams = { products: ['a'] };
      await renderRoute();

      const box = [...container.querySelectorAll('label')]
        .find((l) => l.textContent === 'Billing')!
        .querySelector('input')!;
      await act(async () => box.click());
      let call = navigate.mock.calls[0][0];
      expect(call.replace).toBe(true);
      expect(call.search({ stage: 'qa' })).toEqual({ stage: 'qa', products: ['a', 'b'] });
      expect(container.querySelector('[role="status"]')!.textContent).toBe('Filtered to Core, Billing.');

      const clear = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Clear products')!;
      await act(async () => clear.click());
      call = navigate.mock.calls[1][0];
      expect(call.search({ products: ['a'] })).toEqual({ products: undefined });
      expect(container.querySelector('[role="status"]')!.textContent).toBe('Showing all products.');
    });

    it('shows the product empty state with both ways out', async () => {
      searchParams = { products: ['a'] };
      dashboardResponse = () => json(200, data([], 7));
      await renderRoute();

      expect(container.textContent).toContain('No releases for these products');
      expect(container.textContent).toContain(
        'Nothing in the selected products is open, frozen or in a stage short of Production, and nothing shipped in the last 90 days.',
      );
      const buttons = [...container.querySelectorAll<HTMLButtonElement>('section button')];
      const clear = buttons.find((b) => b.textContent === 'Clear products')!;
      const showAll = buttons.find((b) => b.textContent === 'Show all releases')!;
      await act(async () => clear.click());
      expect(navigate.mock.calls[0][0].search({ products: ['a'] })).toEqual({ products: undefined });
      await act(async () => showAll.click());
      expect(navigate.mock.calls[1][0].search({})).toEqual({ all: true });
    });

    it('keeps the plain empty state when there are no releases at all', async () => {
      searchParams = { products: ['a'] };
      dashboardResponse = () => json(200, data([], 0));
      await renderRoute();

      expect(container.textContent).toContain('No releases yet');
      expect(container.textContent).not.toContain('No releases for these products');
    });

    it('hides the filter when there are no products', async () => {
      productsResponse = () => json(200, { products: [] });
      await renderRoute();

      expect(container.querySelector('details')).toBeNull();
    });
  });

  it('shows a scoped alert with Try again when the dashboard fails, leaving history mounted', async () => {
    dashboardResponse = () => json(500, { status: 500, reason: 'INTERNAL_ERROR' });
    await renderRoute();

    const alert = container.querySelector('[role="alert"]')!;
    expect(alert.getAttribute('data-reason')).toBe('INTERNAL_ERROR');
    expect(alert.textContent).toContain("Couldn't load the dashboard — ");
    expect([...container.querySelectorAll('button')].some((b) => b.textContent === 'Try again')).toBe(true);
    expect(container.textContent).toContain('Deployment history');
  });

  it('renders dashes in the document cells and still the matrix when readiness fails', async () => {
    readinessResponse = () => json(500, { status: 500, reason: 'INTERNAL_ERROR' });
    await renderRoute();

    const cells = firstRowCells();
    expect(cells[1].textContent).toBe('—');
    expect(cells[2].textContent).toBe('—');
    expect(cells[3].textContent).toBe('—');
    expect(cells[0].textContent).toContain('Sprint 42');
  });
  describe('tabs', () => {
    const tab = (name: string) =>
      [...container.querySelectorAll<HTMLButtonElement>('[role="tab"]')].find((t) => t.textContent === name)!;

    it('selects Releases by default and does not fetch portfolio data', async () => {
      await renderRoute();

      expect(container.querySelector('[role="tablist"]')!.getAttribute('aria-label')).toBe('Dashboard views');
      expect(tab('Releases').getAttribute('aria-selected')).toBe('true');
      expect(tab('Portfolio').getAttribute('aria-selected')).toBe('false');
      expect(container.querySelector('section[aria-label="Summary"]')).toBeTruthy();
      expect(portfolioRequests).toEqual([]);
    });

    it('shows the Portfolio tab instead of the release content when tab=portfolio', async () => {
      searchParams = { tab: 'portfolio' };
      await renderRoute();

      expect(tab('Portfolio').getAttribute('aria-selected')).toBe('true');
      expect(container.querySelector('section[aria-label="Summary"]')).toBeNull();
      expect(container.textContent).toContain('Show archived');
      expect(portfolioRequests.length).toBeGreaterThan(0);
    });

    it('switches with replace, keeping products and archived and clearing docs', async () => {
      searchParams = { products: ['a'], archived: true, docs: 'missing' };
      await renderRoute();

      await act(async () => tab('Portfolio').click());
      const call = navigate.mock.calls[0][0];
      expect(call.replace).toBe(true);
      expect(call.search({ products: ['a'], archived: true, docs: 'missing' })).toEqual({
        products: ['a'],
        archived: true,
        tab: 'portfolio',
        docs: undefined,
      });
    });

    it('drops the tab param when going back to Releases', async () => {
      searchParams = { tab: 'portfolio' };
      await renderRoute();

      await act(async () => tab('Releases').click());
      expect(navigate.mock.calls[0][0].search({ tab: 'portfolio' }).tab).toBeUndefined();
    });

    it('moves selection and focus with the arrow keys', async () => {
      await renderRoute();

      await act(async () => {
        tab('Releases').dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
      });
      expect(navigate.mock.calls[0][0].search({}).tab).toBe('portfolio');
      expect(document.activeElement).toBe(tab('Portfolio'));
    });

    it('sets archived from the Portfolio checkbox', async () => {
      searchParams = { tab: 'portfolio' };
      await renderRoute();

      const box = [...container.querySelectorAll('label')].find((l) => l.textContent === 'Show archived')!.querySelector('input')!;
      await act(async () => box.click());
      expect(navigate.mock.calls[0][0].search({ tab: 'portfolio' })).toEqual({ tab: 'portfolio', archived: true });
    });
  });
});
