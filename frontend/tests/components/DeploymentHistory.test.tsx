import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { DeploymentHistory } from '../../src/components/DeploymentHistory';
import type { DashboardSearch, DeploymentEntry } from '../../src/api/dashboard';

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

const STAGES = [
  { id: 'prod', name: 'Production', position: 2, production: true, retired: false, inUse: true },
  { id: 'old', name: 'Legacy', position: 0, production: false, retired: true, inUse: true },
  { id: 'qa', name: 'QA', position: 1, production: false, retired: false, inUse: true },
];

function entry(n: number, overrides: Partial<DeploymentEntry> = {}): DeploymentEntry {
  return {
    id: `p${n}`,
    releaseId: `r${n}`,
    releaseName: `Release ${n}`,
    releaseVersion: '1.0',
    stageId: 'prod',
    stageName: 'Production',
    stageRetired: false,
    outcome: 'SUCCESS',
    deployedAt: '2026-10-10T10:00:00Z',
    recordedBy: 'rm@acme.com',
    note: null,
    corrected: false,
    ...overrides,
  };
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

let container: HTMLDivElement;
let root: Root;
let urls: string[];
let deployments: (url: URL) => Response;

function deploymentUrls(): URL[] {
  return urls.filter((u) => u.startsWith('/api/dashboard/deployments')).map((u) => new URL(u, 'http://x'));
}

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderSection(search: DashboardSearch = {}, onSearchChange: (next: DashboardSearch) => void = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <DeploymentHistory search={search} onSearchChange={onSearchChange} />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
  await flush();
}

function button(label: string): HTMLButtonElement {
  const found = [...container.querySelectorAll('button')].find((b) => b.textContent!.trim() === label);
  if (!found) throw new Error(`no button ${label}`);
  return found;
}

async function setValue(el: HTMLInputElement | HTMLSelectElement, value: string) {
  const proto = el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  await act(async () => {
    Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(el, value);
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
  });
}

describe('DeploymentHistory', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 12));
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    urls = [];
    deployments = () => json(200, { entries: [entry(1)], hasMore: false });
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = typeof input === 'string' ? input : input.toString();
      urls.push(url);
      if (url.startsWith('/api/environment-ladder')) return json(200, { stages: STAGES });
      return deployments(new URL(url, 'http://x'));
    });
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('requests the last 30 days of all environments, 50 at a time, by default', async () => {
    await renderSection();

    const [first] = deploymentUrls();
    expect(first.searchParams.get('stageId')).toBeNull();
    expect(first.searchParams.get('limit')).toBe('50');
    expect(first.searchParams.get('offset')).toBe('0');
    expect(first.searchParams.get('from')).toBe(new Date(2026, 8, 13).toISOString());
    expect(first.searchParams.get('to')).toBe(new Date(2026, 9, 13).toISOString());
  });

  it('lists active stages in position order, then retired ones, and reports a choice', async () => {
    const onChange = vi.fn();
    await renderSection({ all: true }, onChange);

    const select = container.querySelector<HTMLSelectElement>('#history-environment')!;
    expect([...select.options].map((o) => o.textContent)).toEqual([
      'All environments',
      'QA',
      'Production',
      'Legacy (retired)',
    ]);
    await setValue(select, 'qa');
    expect(onChange).toHaveBeenCalledWith({ all: true, stage: 'qa' });
  });

  it('renders release, environment, outcome, deployed time, actor, note and the corrected marker', async () => {
    deployments = () =>
      json(200, {
        entries: [entry(1, { outcome: 'FAILED', note: 'Smoke test failed', corrected: true, stageRetired: true })],
        hasMore: false,
      });
    await renderSection();

    const cells = [...container.querySelectorAll('tbody tr td')];
    expect(cells[0].querySelector('a')!.getAttribute('href')).toBe('/releases/r1');
    expect(cells[1].textContent).toBe('Production (retired)');
    expect(cells[2].textContent).toContain('Failed');
    expect(cells[2].querySelector('span')!.className).toContain('text-red-700');
    expect(cells[2].querySelector('a')!.getAttribute('href')).toBe('/releases/r1#history');
    expect(cells[3].textContent).toBe(new Date('2026-10-10T10:00:00Z').toLocaleString());
    expect(cells[4].textContent).toBe('rm@acme.com');
    expect(cells[5].getAttribute('title')).toBe('Smoke test failed');
  });

  it('pages with Load more by offset and does not duplicate rows', async () => {
    const firstPage = Array.from({ length: 50 }, (_, i) => entry(i));
    deployments = (url) =>
      url.searchParams.get('offset') === '0'
        ? json(200, { entries: firstPage, hasMore: true })
        : json(200, { entries: [entry(49), entry(50)], hasMore: false });
    await renderSection();
    expect(container.textContent).toContain('50 promotions shown · more available');

    await act(async () => button('Load more').click());
    await flush();

    expect(deploymentUrls()[1].searchParams.get('offset')).toBe('50');
    expect(container.querySelectorAll('tbody tr')).toHaveLength(51);
    expect(container.textContent).toContain('51 promotions shown');
    expect(container.textContent).not.toContain('more available');
    expect([...container.querySelectorAll('button')].some((b) => b.textContent === 'Load more')).toBe(false);
  });

  it('sends no request when the start date is after the end date', async () => {
    await renderSection({ from: '2026-10-10', to: '2026-10-01' });

    expect(container.textContent).toContain('Start date must be on or before end date.');
    expect(deploymentUrls()).toHaveLength(0);
  });

  it('sends no request for a range over 366 days', async () => {
    await renderSection({ from: '2025-01-01', to: '2026-10-01' });

    expect(container.textContent).toContain('Choose a range of 366 days or less.');
    expect(deploymentUrls()).toHaveLength(0);
  });

  it('shows the empty state and clears stage and dates, keeping the other URL state', async () => {
    deployments = () => json(200, { entries: [], hasMore: false });
    const onChange = vi.fn();
    await renderSection({ all: true, stage: 'qa', from: '2026-10-01', products: ['a'], archived: true }, onChange);

    expect(container.textContent).toContain('No promotions match these filters');
    const clear = [...container.querySelectorAll('button')].filter((b) => b.textContent === 'Clear filters');
    await act(async () => clear[clear.length - 1].click());
    expect(onChange.mock.calls[0][0]).toEqual({
      all: true,
      stage: undefined,
      from: undefined,
      to: undefined,
      products: ['a'],
      archived: true,
    });
  });

  it('sends the selected products with the request', async () => {
    deployments = () => json(200, { entries: [], hasMore: false });
    await renderSection({ products: ['a'] });

    expect(deploymentUrls()[0].searchParams.getAll('productIds')).toEqual(['a']);
  });

  it('scopes a failed request to the section with a retry', async () => {
    deployments = () => json(500, { reason: 'INTERNAL_ERROR', title: 'x', status: 500 });
    await renderSection();

    const alert = container.querySelector('[role="alert"]')!;
    expect(alert.textContent).toContain("Couldn't load deployment history");
    expect(alert.getAttribute('data-reason')).toBe('INTERNAL_ERROR');
    expect(button('Try again')).toBeTruthy();
  });
});
