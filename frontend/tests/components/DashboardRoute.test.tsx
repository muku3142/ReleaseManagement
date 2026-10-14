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
    dashboardResponse = () => json(200, data([row()]));
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
      if (url.startsWith('/api/documents/readiness')) return readinessResponse();
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
      'Need paperwork',
      'Stale',
      'Median created → frozen',
      'Median frozen → production',
      'Releases per stage',
    ]) {
      expect(text).toContain(caption);
    }
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

    const box = container.querySelector<HTMLInputElement>('input[type="checkbox"]')!;
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
});
