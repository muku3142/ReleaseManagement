import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReleasesRoute } from '../../src/routes/ReleasesRoute';

type LinkProps = { children?: ReactNode; to?: string; params?: Record<string, string>; hash?: string };

vi.mock('@tanstack/react-router', () => ({
  Link: (props: LinkProps) => {
    let href = props.to ?? '';
    for (const [key, value] of Object.entries(props.params ?? {})) {
      href = href.replace(`$${key}`, value);
    }
    if (props.hash) href += `#${props.hash}`;
    return <a href={href}>{props.children}</a>;
  },
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
};

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function mockFetch(readiness: Response | (() => Response)) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (url.startsWith('/api/documents/readiness')) {
      return typeof readiness === 'function' ? readiness() : readiness;
    }
    return json(200, { releases: [RELEASE] });
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
    vi.restoreAllMocks();
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
    // Name, Version, Status, Project, Items, Last refreshed, then the three readiness cells.
    expect(cells[6].textContent).toBe('Up to date');
    expect(cells[7].textContent).toBe('Not generated');
    expect(cells[8].textContent).toBe('Not generated');
  });

  it('existingSixColumnsAreUnchanged', async () => {
    mockFetch(json(200, { entries: [] }));

    await renderRoute();

    const headers = headerTexts();
    expect(headers.slice(0, 6)).toEqual(['Name', 'Version', 'Status', 'Project', 'Items', 'Last refreshed']);

    const cells = firstDataRowCells();
    expect(cells[0].textContent).toBe('Sprint 42');
    expect(cells[1].textContent).toBe('2026.08');
    expect(cells[2].textContent).toBe('Open');
    expect(cells[3].textContent).toBe('CNV');
    expect(cells[4].textContent).toBe('3');
    expect(cells[5].textContent).toBe('—');
  });

  it('readinessFailureStillRendersTheReleaseTable', async () => {
    mockFetch(() => json(500, { status: 500, reason: 'INTERNAL_ERROR' }));

    await renderRoute();

    const cells = firstDataRowCells();
    expect(cells[0].textContent).toBe('Sprint 42');
    expect(cells[6].textContent).toBe('—');
    expect(cells[7].textContent).toBe('—');
    expect(cells[8].textContent).toBe('—');
    // No second error banner - the table itself is still fully usable.
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });
});
