import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReleasesRoute } from '../../src/routes/ReleasesRoute';

type LinkProps = {
  children?: ReactNode;
  to?: string;
  params?: Record<string, string>;
  search?: Record<string, unknown>;
  hash?: string;
  className?: string;
};

const navigate = vi.fn();

vi.mock('@tanstack/react-router', () => ({
  Link: (props: LinkProps) => {
    let href = props.to ?? '';
    for (const [key, value] of Object.entries(props.params ?? {})) {
      href = href.replace(`$${key}`, value);
    }
    if (props.search) {
      href += `?${new URLSearchParams(Object.entries(props.search).map(([key, value]) => [key, String(value)]))}`;
    }
    if (props.hash) href += `#${props.hash}`;
    return (
      <a href={href} className={props.className}>
        {props.children}
      </a>
    );
  },
  useNavigate: () => navigate,
}));

const RELEASE = {
  id: 'r1',
  name: 'Sprint 42',
  version: '2026.08',
  status: 'OPEN',
  jiraProjectKey: 'CNV',
  createdAt: '2026-08-05T00:00:00Z',
  createdBy: 'rm@acme.com',
  frozenAt: null,
  frozenBy: null,
  lastRefreshedAt: null,
  itemCount: 3,
  products: [] as { id: string; name: string; archived: boolean; hasInstalls: boolean }[],
};

type ReleaseFixture = typeof RELEASE;

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function mockFetch(readiness: Response | (() => Response), releases: ReleaseFixture | ReleaseFixture[] = RELEASE) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (url.startsWith('/api/documents/readiness')) {
      return typeof readiness === 'function' ? readiness() : readiness;
    }
    return json(200, { releases: Array.isArray(releases) ? releases : [releases] });
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
          <ReleasesRoute />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
  await flush();
}

function headerTexts(): string[] {
  return [...container.querySelectorAll('th')].map((th) => th.textContent!.trim());
}

function firstDataRowCells(): HTMLTableCellElement[] {
  return [...container.querySelectorAll('tbody tr')[0].querySelectorAll('td')];
}

function firstColumnTexts(): string[] {
  return [...container.querySelectorAll('tbody tr')].map((tr) => tr.querySelector('td')?.textContent ?? '');
}

function click(element: Element) {
  act(() => {
    (element as HTMLElement).click();
  });
}

function sortButton(title: string): HTMLButtonElement {
  const button = container.querySelector<HTMLButtonElement>(`button[aria-label="Sort by ${title}"]`);
  expect(button).not.toBeNull();
  return button!;
}

describe('ReleasesRoute', () => {
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
    document.body.innerHTML = '';
    vi.restoreAllMocks();
  });

  it('clickingARowOpensTheReleaseOnce', async () => {
    navigate.mockReset();
    mockFetch(json(200, { entries: [] }));
    await renderRoute();
    container.addEventListener('click', (event) => event.preventDefault());

    const cells = firstDataRowCells();
    const items = cells[headerTexts().indexOf('Items')];
    await click(items);
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith({ to: '/releases/$releaseId', params: { releaseId: 'r1' } });

    navigate.mockReset();
    await click(cells[0].querySelector('a')!);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('releaseLinkHasTheAnimatedUnderline', async () => {
    mockFetch(json(200, { entries: [] }));
    await renderRoute();
    const link = firstDataRowCells()[0].querySelector('a')!;
    expect(link.classList).toContain('link-underline');
    expect(link.classList).not.toContain('hover:underline');
  });

  it('rendersThePageHeader', async () => {
    mockFetch(json(200, { entries: [] }));

    await renderRoute();

    const headings = container.querySelectorAll('h1');
    expect(headings).toHaveLength(1);
    expect(headings[0].textContent).toBe('Releases');
    expect(container.textContent).toContain('Every release, its status and its documents.');
  });

  it('tableGainsThreeReadinessColumnsInDocumentTypeOrder', async () => {
    mockFetch(
      json(200, {
        entries: [{ releaseId: 'r1', documentType: 'RELEASE_NOTES', state: 'UP_TO_DATE' }],
      }),
    );

    await renderRoute();

    const headers = headerTexts();
    expect(headers.slice(-3)).toEqual(['Release Notes', 'Runbook', 'Approval']);

    const cells = firstDataRowCells();
    // Release, Products, Status, Jira project, Items, Last refreshed, then the three readiness cells.
    expect(cells[6].textContent).toBe('Up to date');
    expect(cells[7].textContent).toBe('Not generated');
    expect(cells[8].textContent).toBe('Not generated');
  });

  it('releaseColumnsAreInOrder', async () => {
    mockFetch(json(200, { entries: [] }));

    await renderRoute();

    const headers = headerTexts();
    expect(headers.slice(0, 6)).toEqual(['Release', 'Products', 'Status', 'Jira project', 'Items', 'Last refreshed']);

    const cells = firstDataRowCells();
    const link = cells[0].querySelector('a')!;
    expect(link.textContent).toBe('Sprint 42 2026.08');
    expect(link.getAttribute('href')).toBe('/releases/r1');
    expect(link.className).toContain('text-primary');
    expect(link.className).toContain('font-semibold');
    expect(cells[2].textContent).toBe('Open');
    expect(cells[3].textContent).toBe('CNV');
    expect(cells[4].textContent).toBe('3');
    expect(cells[5].textContent).toBe('—');
  });

  it('showsFrozenAsANeutralBadgeWithALockIcon', async () => {
    mockFetch(json(200, { entries: [] }), { ...RELEASE, status: 'FROZEN' });

    await renderRoute();

    const badge = firstDataRowCells()[2].querySelector('[data-slot="badge"]')!;
    expect(badge.textContent).toBe('Frozen');
    expect(badge.getAttribute('data-variant')).toBe('neutral');
    expect(badge.querySelector('svg')).not.toBeNull();
  });

  it('readinessFailureStillRendersTheReleaseTable', async () => {
    mockFetch(() => json(500, { status: 500, reason: 'INTERNAL_ERROR' }));

    await renderRoute();

    const cells = firstDataRowCells();
    expect(cells[0].textContent).toBe('Sprint 42 2026.08');
    expect(cells[4].textContent).toBe('3');
    expect(cells[6].textContent).toBe('—');
    expect(cells[7].textContent).toBe('—');
    expect(cells[8].textContent).toBe('—');
    // No second error banner - the table itself is still fully usable.
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });

  it('showsTwoProductChipsThenPlusN', async () => {
    const products = ['Core', 'Mobile', 'Legacy'].map((name, i) => ({
      id: `p${i}`,
      name,
      archived: false,
      hasInstalls: false,
    }));
    mockFetch(json(200, { entries: [] }), { ...RELEASE, products });

    await renderRoute();

    const cell = firstDataRowCells()[1];
    expect(cell.textContent).toBe('CoreMobile+1');
    expect(cell.querySelector('[title]')!.getAttribute('title')).toBe('Core, Mobile, Legacy');
  });

  it('showsUnassignedForAReleaseWithNoProducts', async () => {
    mockFetch(json(200, { entries: [] }));

    await renderRoute();

    expect(firstDataRowCells()[1].textContent).toBe('Unassigned');
  });

  it('sortsItemsNumerically', async () => {
    mockFetch(json(200, { entries: [] }), [
      { ...RELEASE, id: 'a', name: 'A', itemCount: 10 },
      { ...RELEASE, id: 'b', name: 'B', itemCount: 9 },
      { ...RELEASE, id: 'c', name: 'C', itemCount: 100 },
    ]);

    await renderRoute();
    click(sortButton('Items'));

    expect(firstColumnTexts()).toEqual(['B 2026.08', 'A 2026.08', 'C 2026.08']);
  });

  it('statusFacetFilteredToFrozenLeavesOnlyFrozenReleases', async () => {
    mockFetch(json(200, { entries: [] }), [
      { ...RELEASE, id: 'a', name: 'A' },
      { ...RELEASE, id: 'b', name: 'B', status: 'FROZEN' },
    ]);

    await renderRoute();
    click(container.querySelector('[data-slot="faceted-filter-trigger"]')!);
    const frozen = [...document.querySelectorAll<HTMLElement>('[role="option"]')].find((el) =>
      el.textContent?.startsWith('Frozen'),
    )!;
    click(frozen);

    expect(firstColumnTexts()).toEqual(['B 2026.08']);
  });

  it('debouncesTheSearchIntoAQueriedFetch', async () => {
    const fetchMock = mockFetch(json(200, { entries: [] }));

    await renderRoute();
    const input = container.querySelector<HTMLInputElement>('input[aria-label="Search releases"]')!;
    expect(input.placeholder).toBe('Search by name or version…');

    await act(async () => {
      const setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
      setValue.call(input, 'sprint');
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    const queried = () => fetchMock.mock.calls.some(([url]) => String(url) === '/api/releases?query=sprint');
    expect(queried()).toBe(false);

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 350));
    });
    await flush();
    expect(queried()).toBe(true);
  });

  it('showsSkeletonRowsWhileTheFirstRequestIsPending', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise<Response>(() => {}));

    await renderRoute();

    expect(container.querySelector('[aria-busy="true"]')).not.toBeNull();
    expect(container.textContent).toContain('Loading releases…');
    expect(container.querySelectorAll('[data-slot="skeleton"]').length).toBeGreaterThan(0);
  });

  it('showsTheEmptyStateWhenThereAreNoReleases', async () => {
    mockFetch(json(200, { entries: [] }), []);

    await renderRoute();

    expect(container.textContent).toContain('No releases yet');
    expect(container.textContent).toContain('Choose New release, pick a product and select its tickets.');
    expect([...container.querySelectorAll('a')].some((a) => a.textContent === 'Browse tickets')).toBe(false);
  });

  it('headerNewReleaseLinkOpensTheProductFirstTicketsPage', async () => {
    mockFetch(json(200, { entries: [] }));

    await renderRoute();

    const link = [...container.querySelectorAll('header a')].find((a) => a.textContent?.trim() === 'New release');
    expect(link).toBeDefined();
    expect(link!.getAttribute('href')).toContain('/tickets?new=true');
    expect(link!.querySelector('svg')!.getAttribute('aria-hidden')).toBe('true');
  });

  it('showsTheNoMatchStateForAQuery', async () => {
    mockFetch(json(200, { entries: [] }), []);

    await renderRoute();
    const input = container.querySelector<HTMLInputElement>('input[aria-label="Search releases"]')!;
    await act(async () => {
      const setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
      setValue.call(input, 'zzz');
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 350));
    });
    await flush();

    expect(container.textContent).toContain("No releases match 'zzz'");
    expect(container.textContent).toContain('Try a different name or version.');
  });

  it('showsTheErrorWithTryAgainThatRefetches', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/documents/readiness')) return json(200, { entries: [] });
      return json(500, { status: 500, reason: 'INTERNAL_ERROR' });
    });

    await renderRoute();

    const alert = container.querySelector('[role="alert"]')!;
    expect(alert.getAttribute('data-reason')).toBe('INTERNAL_ERROR');
    const releaseCalls = () => fetchMock.mock.calls.filter(([url]) => String(url) === '/api/releases').length;
    const before = releaseCalls();
    const retry = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Try again')!;
    await act(async () => retry.click());
    await flush();
    expect(releaseCalls()).toBeGreaterThan(before);
  });
});
