import { act, useState } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { RoadmapTable } from '../../src/roadmap/RoadmapTable';
import type { RoadmapItem } from '../../src/api/roadmap';

function item(overrides: Partial<RoadmapItem>): RoadmapItem {
  return {
    id: 'i1',
    source: 'MANUAL',
    jiraKey: null,
    title: 'Item',
    jiraStatus: null,
    jiraSyncedAt: null,
    jiraUnavailable: false,
    product: null,
    status: 'IDEA',
    targetQuarter: null,
    owner: null,
    notes: null,
    reach: 10,
    impact: 2,
    confidence: 80,
    effort: 1,
    scoreDisplay: '16.0',
    rank: 1,
    missing: [],
    createdAt: '2026-01-01T00:00:00Z',
    archived: false,
    ...overrides,
  };
}

const SCORED = item({
  id: 'i1',
  title: 'Scored one',
  source: 'JIRA',
  jiraKey: 'CNV-1',
  jiraStatus: 'In Progress',
  jiraSyncedAt: new Date(Date.now() - 2 * 3600 * 1000).toISOString(),
  product: { id: 'p1', name: 'Core', archived: false },
  targetQuarter: '2027-Q1',
  owner: 'Sam',
});
const UNSCORED = item({
  id: 'i2',
  title: 'Unscored one',
  reach: null,
  effort: null,
  scoreDisplay: null,
  rank: null,
  missing: ['REACH', 'EFFORT'],
});
const UNSCORED_2 = item({ id: 'i3', title: 'Unscored two', scoreDisplay: null, rank: null, reach: null, missing: ['REACH'] });
const GONE = item({
  id: 'i4',
  title: 'Gone',
  source: 'JIRA',
  jiraKey: 'CNV-9',
  jiraUnavailable: true,
  jiraStatus: 'Done',
  jiraSyncedAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
  rank: 2,
  scoreDisplay: '1.0',
  archived: true,
});

let container: HTMLDivElement;
let root: Root;

beforeAll(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
});

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    const body = url.endsWith('/history') ? { entries: [] } : { products: [] };
    return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
  });
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.restoreAllMocks();
});

function Harness({ items, onRefreshItem = vi.fn() }: { items: RoadmapItem[]; onRefreshItem?: (i: RoadmapItem) => void }) {
  const [open, setOpen] = useState<Set<string>>(new Set());
  return (
    <RoadmapTable
      items={items}
      openIds={open}
      onToggle={(id) =>
        setOpen((prev) => {
          const next = new Set(prev);
          if (!next.delete(id)) next.add(id);
          return next;
        })
      }
      refreshDisabled={false}
      onRefreshItem={onRefreshItem}
    />
  );
}

async function render(items: RoadmapItem[], onRefreshItem?: (i: RoadmapItem) => void) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <Harness items={items} onRefreshItem={onRefreshItem} />
      </QueryClientProvider>,
    );
  });
}

const rows = () => Array.from(container.querySelectorAll('tbody tr'));

describe('RoadmapTable', () => {
  it('renders the columns in order with units', async () => {
    await render([SCORED]);
    const headings = Array.from(container.querySelectorAll('thead th')).map((th) => th.textContent);
    expect(headings).toEqual([
      'Rank',
      'Item',
      'Score',
      'Reachclients',
      'Impact',
      'Confidence',
      'Effortperson-weeks',
      'Product',
      'Status',
      'Target quarter',
      'Owner',
      'Jira',
    ]);
  });

  it('keeps the given order and shows rank and score for scored rows', async () => {
    await render([GONE, SCORED]);
    const titles = Array.from(container.querySelectorAll('tbody tr td:nth-child(2) span.font-semibold')).map((s) => s.textContent);
    expect(titles).toEqual(['Gone', 'Scored one']);
    expect(rows()[1].textContent).toContain('16.0');
  });

  it('marks unscored rows and adds one separator before the first', async () => {
    await render([SCORED, UNSCORED, UNSCORED_2]);
    const text = container.textContent!;
    expect(text).toContain('Not scored yet (2). Oldest first.');
    expect(text.match(/Oldest first\./g)).toHaveLength(1);
    expect(text).toContain('Not scored');
    expect(text).toContain('Missing: Reach, Effort');
    expect(container.querySelectorAll('[aria-label="Not ranked"]')).toHaveLength(2);
  });

  it('has no separator when every row is scored', async () => {
    await render([SCORED]);
    expect(container.textContent).not.toContain('Oldest first.');
  });

  it('shows Jira freshness, a refresh button and manual markers', async () => {
    const onRefresh = vi.fn();
    await render([SCORED, UNSCORED], onRefresh);
    expect(container.textContent).toContain('Fetched 2h ago');
    expect(container.textContent).toContain('Manual');
    const refresh = container.querySelector<HTMLButtonElement>('[aria-label="Refresh CNV-1 from Jira"]')!;
    await act(async () => refresh.click());
    expect(onRefresh).toHaveBeenCalledWith(expect.objectContaining({ id: 'i1' }));
  });

  it('shows the unavailable marker, archived tag and disabled inputs', async () => {
    await render([GONE]);
    expect(container.textContent).toContain('unavailable in Jira');
    expect(container.textContent).toContain('Last fetched 5m ago');
    expect(container.textContent).toContain('archived');
    expect(container.querySelector<HTMLInputElement>('input[type="number"]')!.disabled).toBe(true);
  });

  it('toggles the details row', async () => {
    await render([SCORED]);
    const button = container.querySelector<HTMLButtonElement>('[aria-label="Details for Scored one"]')!;
    expect(button.getAttribute('aria-expanded')).toBe('false');
    await act(async () => button.click());
    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(container.querySelector('#item-i1-detail')).not.toBeNull();
    await act(async () => button.click());
    expect(container.querySelector('#item-i1-detail')).toBeNull();
  });
});
