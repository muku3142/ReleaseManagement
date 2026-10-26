import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { InputCell } from '../../src/roadmap/InputCell';
import type { RiceField, RoadmapItem } from '../../src/api/roadmap';

const ITEM: RoadmapItem = {
  id: 'i1',
  source: 'MANUAL',
  jiraKey: null,
  title: 'Faster export',
  jiraStatus: null,
  jiraSyncedAt: null,
  jiraUnavailable: false,
  product: null,
  status: 'IDEA',
  targetQuarter: null,
  owner: null,
  notes: null,
  reach: 10,
  impact: 1,
  confidence: 80,
  effort: 2,
  scoreDisplay: '4.0',
  rank: 1,
  missing: [],
  createdAt: '2026-10-01T00:00:00Z',
  archived: false,
};

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

let container: HTMLDivElement;
let root: Root;
let puts: unknown[];

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderCell(field: RiceField, props: { disabled?: boolean; item?: RoadmapItem } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <InputCell item={props.item ?? ITEM} field={field} disabled={props.disabled} />
      </QueryClientProvider>,
    );
  });
}

function control(name: string): HTMLInputElement | HTMLSelectElement {
  return container.querySelector(`[aria-label="${name} for Faster export"]`)!;
}

async function type(el: HTMLInputElement, value: string) {
  await act(async () => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

async function blur(el: HTMLElement) {
  await act(async () => {
    el.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
  });
  await flush();
}

async function key(el: HTMLElement, k: string) {
  await act(async () => {
    el.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true }));
  });
  await flush();
}

function mockPut(response: Response | (() => Response) = () => json(200, ITEM)) {
  puts = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
    puts.push(JSON.parse(String(init?.body)));
    return typeof response === 'function' ? response() : response;
  });
}

describe('InputCell', () => {
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

  it('rejects a fractional Reach on blur and sends nothing', async () => {
    mockPut();
    await renderCell('REACH');
    const input = control('Reach') as HTMLInputElement;
    await type(input, '2.5');
    await blur(input);
    expect(container.textContent).toContain('Enter a whole number, 0 or more.');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(puts).toHaveLength(0);
  });

  it('sends the full input set with the one changed field on Enter', async () => {
    mockPut();
    await renderCell('REACH');
    const input = control('Reach') as HTMLInputElement;
    await type(input, '25');
    await key(input, 'Enter');
    expect(puts).toEqual([{ reach: 25, impact: 1, confidence: 80, effort: 2 }]);
  });

  it('reverts on Escape and sends nothing for an unchanged value', async () => {
    mockPut();
    await renderCell('REACH');
    const input = control('Reach') as HTMLInputElement;
    await type(input, '99');
    await key(input, 'Escape');
    expect(input.value).toBe('10');
    await blur(input);
    expect(puts).toHaveLength(0);
  });

  it('rejects zero Effort', async () => {
    mockPut();
    await renderCell('EFFORT');
    const input = control('Effort') as HTMLInputElement;
    await type(input, '0');
    await blur(input);
    expect(container.textContent).toContain('Enter a number above 0, for example 0.5.');
    expect(puts).toHaveLength(0);
  });

  it('saves a select on change and shows the server reason on rejection, reverting the value', async () => {
    mockPut(() => json(400, { reason: 'ROADMAP_INVALID_INPUT', status: 400, title: 'Bad' }));
    await renderCell('IMPACT');
    const select = control('Impact') as HTMLSelectElement;
    await act(async () => {
      select.value = '2';
      select.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await flush();
    expect(puts).toEqual([{ reach: 10, impact: 2, confidence: 80, effort: 2 }]);
    const alert = container.querySelector('[role="alert"]')!;
    expect(alert).not.toBeNull();
    expect(alert.getAttribute('data-reason')).toBe('ROADMAP_INVALID_INPUT');
    expect(select.value).toBe('1');
  });

  it('disables the control when disabled', async () => {
    mockPut();
    await renderCell('CONFIDENCE', { disabled: true });
    expect((control('Confidence') as HTMLSelectElement).disabled).toBe(true);
  });
});
