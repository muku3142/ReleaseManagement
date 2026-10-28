import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { OverrideCell } from '../../src/roadmap/OverrideCell';
import type { CalculatedField, RoadmapItem } from '../../src/api/roadmap';

const ITEM: RoadmapItem = {
  id: 'i1',
  source: 'JIRA',
  jiraKey: 'CNV-1',
  title: 'Faster export',
  jiraStatus: 'To Do',
  jiraSyncedAt: null,
  jiraUnavailable: false,
  product: null,
  status: 'IDEA',
  targetQuarter: null,
  owner: null,
  notes: null,
  reach: 7,
  impact: 2,
  confidence: 80,
  effort: 2,
  scoreDisplay: '5.6',
  rank: 1,
  missing: [],
  calculated: { reach: 7, impact: 2, confidence: 80 },
  overrides: { reach: null, impact: null, confidence: null },
  calculatedFields: ['REACH', 'IMPACT', 'CONFIDENCE'],
  jiraPriority: 'High',
  readinessSignals: 2,
  createdAt: '2026-10-01T00:00:00Z',
  archived: false,
};

const OVERRIDDEN: RoadmapItem = { ...ITEM, reach: 20, overrides: { ...ITEM.overrides, reach: 'Big client onboarding' } };

type Call = { url: string; method: string | undefined; body: unknown };

let container: HTMLDivElement;
let root: Root;
let calls: Call[];

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function mockFetch(response: () => Response = () => json(200, ITEM)) {
  calls = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    calls.push({ url: String(input), method: init?.method, body: init?.body ? JSON.parse(String(init.body)) : undefined });
    return response();
  });
}

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderCell(field: CalculatedField, item: RoadmapItem = ITEM) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <OverrideCell item={item} field={field} />
      </QueryClientProvider>,
    );
  });
}

function button(text: string): HTMLButtonElement {
  return Array.from(container.querySelectorAll('button')).find((b) => b.textContent === text)!;
}

async function click(el: HTMLElement) {
  await act(async () => {
    el.click();
  });
  await flush();
}

async function type(el: HTMLInputElement, value: string) {
  await act(async () => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

function reasonInput(label: string): HTMLInputElement {
  return container.querySelector(`[aria-label="Reason for overriding ${label} for Faster export"]`)!;
}

describe('OverrideCell', () => {
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

  it('shows the calculated value as text with a Calculated label and no control', async () => {
    mockFetch();
    await renderCell('IMPACT');
    expect(container.textContent).toContain('2 High');
    expect(container.textContent).toContain('Calculated');
    expect(container.querySelector('select, input')).toBeNull();
    expect(button('Override')).toBeDefined();
    expect(button('Reset to calculated')).toBeUndefined();
  });

  it('shows Not set when there is no calculated value', async () => {
    mockFetch();
    await renderCell('CONFIDENCE', { ...ITEM, confidence: null, calculated: { ...ITEM.calculated, confidence: null } });
    expect(container.textContent).toContain('Not set');
  });

  it('keeps Save disabled until a value and a non-blank reason are given, then sends the override', async () => {
    mockFetch();
    await renderCell('REACH');
    await click(button('Override'));
    const value = container.querySelector('[aria-label="Reach override for Faster export"]') as HTMLInputElement;
    const reason = reasonInput('Reach');
    expect(reason.maxLength).toBe(200);
    await type(value, '20');
    await type(reason, '   ');
    expect(button('Save').disabled).toBe(true);
    await type(reason, ' Big client onboarding ');
    expect(button('Save').disabled).toBe(false);
    await click(button('Save'));
    expect(calls).toEqual([
      { url: '/api/roadmap/items/i1/overrides/REACH', method: 'PUT', body: { value: 20, reason: 'Big client onboarding' } },
    ]);
  });

  it('rejects a fractional Reach client-side', async () => {
    mockFetch();
    await renderCell('REACH');
    await click(button('Override'));
    await type(container.querySelector('[aria-label="Reach override for Faster export"]') as HTMLInputElement, '2.5');
    await type(reasonInput('Reach'), 'why');
    expect(button('Save').disabled).toBe(true);
  });

  it('uses a select for Impact and closes on Cancel without sending', async () => {
    mockFetch();
    await renderCell('IMPACT');
    await click(button('Override'));
    expect(container.querySelector('select')).not.toBeNull();
    await click(button('Cancel'));
    expect(container.querySelector('select')).toBeNull();
    expect(calls).toHaveLength(0);
  });

  it('shows the Overridden marker, the reason, the calculated value and resets to calculated', async () => {
    mockFetch();
    await renderCell('REACH', OVERRIDDEN);
    const marker = Array.from(container.querySelectorAll('small')).find((s) => s.textContent?.startsWith('Overridden'))!;
    expect(marker.textContent).toBe('Overridden: Big client onboarding');
    expect(marker.getAttribute('title')).toBe('Big client onboarding');
    expect(container.textContent).toContain('Calculated: 7');
    await click(button('Reset to calculated'));
    expect(calls).toEqual([{ url: '/api/roadmap/items/i1/overrides/REACH', method: 'DELETE', body: undefined }]);
  });

  it('shows the server reason as an alert when the save fails', async () => {
    mockFetch(() => json(400, { reason: 'ROADMAP_FIELD_NOT_CALCULATED', status: 400, title: 'Bad' }));
    await renderCell('REACH', OVERRIDDEN);
    await click(button('Reset to calculated'));
    const alert = container.querySelector('[role="alert"]')!;
    expect(alert.getAttribute('data-reason')).toBe('ROADMAP_FIELD_NOT_CALCULATED');
    expect(alert.textContent).toBe('Only calculated values can be overridden.');
  });
});
