import { StrictMode, act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { EnvironmentLadderSection } from '../../src/components/EnvironmentLadderSection';
import type { LadderStage } from '../../src/api/ladder';

const LADDER_URL = '/api/environment-ladder';

function stage(id: string, name: string, position: number, extra: Partial<LadderStage> = {}): LadderStage {
  return { id, name, position, production: false, retired: false, inUse: false, ...extra };
}

const DEV = stage('dev', 'DEV', 1, { inUse: true });
const TEST = stage('test', 'TEST', 2);
const UAT = stage('uat', 'UAT', 3, { retired: true });
const PROD = stage('prod', 'PROD', 4, { production: true, inUse: true });

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

type Call = { url: string; method: string; body: unknown };

let container: HTMLDivElement;
let root: Root;
let client: QueryClient;
let calls: Call[];

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function stubFetch(stages: LadderStage[], mutationResponse?: Response) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const method = init?.method ?? 'GET';
    calls.push({ url: String(input), method, body: init?.body ? JSON.parse(String(init.body)) : undefined });
    if (method !== 'GET' && mutationResponse) return mutationResponse.clone();
    return json(200, { stages });
  });
}

async function renderSection() {
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <EnvironmentLadderSection />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
}

function rowOf(name: string): HTMLElement {
  return Array.from(container.querySelectorAll('li')).find((li) => li.textContent?.includes(name))!;
}

function buttonIn(parent: ParentNode, label: string): HTMLButtonElement | undefined {
  return Array.from(parent.querySelectorAll('button')).find((b) => b.textContent === label);
}

async function click(button: HTMLButtonElement | undefined) {
  await act(async () => button!.click());
  await flush();
}

function setValue(input: HTMLInputElement, value: string) {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
  return act(async () => {
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

function mutations() {
  return calls.filter((c) => c.method !== 'GET');
}

describe('EnvironmentLadderSection', () => {
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
    calls = [];
    client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
  });

  it('lists stages in order with Production and retired tags and only the valid actions', async () => {
    stubFetch([DEV, TEST, UAT, PROD]);
    await renderSection();

    const names = Array.from(container.querySelectorAll('li')).map((li) => li.textContent);
    expect(names[0]).toContain('DEV');
    expect(names[3]).toContain('PROD');
    expect(rowOf('PROD').textContent).toContain('Production');
    expect(rowOf('UAT').textContent).toContain('retired');

    expect(buttonIn(rowOf('DEV'), 'Up')!.disabled).toBe(true);
    expect(buttonIn(rowOf('PROD'), 'Down')!.disabled).toBe(true);
    expect(buttonIn(rowOf('TEST'), 'Make Production')).toBeDefined();
    expect(buttonIn(rowOf('TEST'), 'Retire')).toBeDefined();
    expect(buttonIn(rowOf('TEST'), 'Delete')).toBeDefined();
    expect(buttonIn(rowOf('DEV'), 'Delete')).toBeUndefined();
    expect(buttonIn(rowOf('PROD'), 'Retire')).toBeUndefined();
    expect(buttonIn(rowOf('PROD'), 'Delete')).toBeUndefined();
    expect(buttonIn(rowOf('PROD'), 'Make Production')).toBeUndefined();
    expect(buttonIn(rowOf('UAT'), 'Restore')).toBeDefined();
    expect(buttonIn(rowOf('UAT'), 'Make Production')).toBeUndefined();
    expect(buttonIn(rowOf('UAT'), 'Retire')).toBeUndefined();
  });

  it('moves a stage up by PUTting the swapped order', async () => {
    stubFetch([DEV, TEST, UAT, PROD]);
    await renderSection();

    await click(buttonIn(rowOf('TEST'), 'Up'));

    expect(mutations()).toEqual([
      { url: `${LADDER_URL}/order`, method: 'PUT', body: { stageIds: ['test', 'dev', 'uat', 'prod'] } },
    ]);
  });

  it('renames through an inline input and can cancel', async () => {
    stubFetch([DEV, TEST, UAT, PROD]);
    await renderSection();

    await click(buttonIn(rowOf('TEST'), 'Rename'));
    const input = container.querySelector<HTMLInputElement>('input[aria-label="Name for TEST"]')!;
    expect(input.value).toBe('TEST');
    await click(buttonIn(container, 'Cancel'));
    expect(container.querySelector('input[aria-label="Name for TEST"]')).toBeNull();
    expect(mutations()).toEqual([]);

    await click(buttonIn(rowOf('TEST'), 'Rename'));
    await setValue(container.querySelector<HTMLInputElement>('input[aria-label="Name for TEST"]')!, ' Staging ');
    await click(buttonIn(container, 'Save'));

    expect(mutations()).toEqual([{ url: `${LADDER_URL}/stages/test`, method: 'PUT', body: { name: 'Staging' } }]);
  });

  it('adds a trimmed stage and clears the input', async () => {
    stubFetch([DEV, TEST, UAT, PROD]);
    await renderSection();

    const input = container.querySelector<HTMLInputElement>('#newStageName')!;
    await setValue(input, '  QA ');
    await click(buttonIn(container, 'Add stage'));

    expect(mutations()).toEqual([{ url: `${LADDER_URL}/stages`, method: 'POST', body: { name: 'QA' } }]);
    expect(container.querySelector<HTMLInputElement>('#newStageName')!.value).toBe('');
  });

  it('issues retire, restore and make-production requests', async () => {
    stubFetch([DEV, TEST, UAT, PROD]);
    await renderSection();

    await click(buttonIn(rowOf('TEST'), 'Retire'));
    await click(buttonIn(rowOf('UAT'), 'Restore'));
    await click(buttonIn(rowOf('TEST'), 'Make Production'));

    expect(mutations()).toEqual([
      { url: `${LADDER_URL}/stages/test/retire`, method: 'POST', body: undefined },
      { url: `${LADDER_URL}/stages/uat/restore`, method: 'POST', body: undefined },
      { url: `${LADDER_URL}/production-stage`, method: 'PUT', body: { stageId: 'test' } },
    ]);
  });

  it('deletes only after the confirmation dialog is confirmed', async () => {
    stubFetch([DEV, TEST, UAT, PROD]);
    await renderSection();

    await click(buttonIn(rowOf('TEST'), 'Delete'));
    const dialog = container.querySelector('dialog')!;
    expect(dialog.textContent).toContain('Delete stage');
    expect(dialog.textContent).toContain('TEST');
    expect(mutations()).toEqual([]);

    await click(buttonIn(dialog, 'Delete'));

    expect(mutations()).toEqual([{ url: `${LADDER_URL}/stages/test`, method: 'DELETE', body: undefined }]);
  });

  it('shows the stable reason for a stage that is in use', async () => {
    stubFetch(
      [DEV, TEST, UAT, PROD],
      json(409, { status: 409, reason: 'STAGE_IN_USE', title: 'Stage in use' }),
    );
    await renderSection();

    await click(buttonIn(rowOf('TEST'), 'Retire'));

    const alert = container.querySelector('[role="alert"]');
    expect(alert?.getAttribute('data-reason')).toBe('STAGE_IN_USE');
  });

  it('replaces the ladder data and invalidates promotions queries after a mutation', async () => {
    stubFetch([DEV, TEST, UAT, PROD]);
    client.setQueryData(['promotions', 'release-1'], { stages: [] });
    await renderSection();

    await click(buttonIn(rowOf('TEST'), 'Retire'));

    expect(client.getQueryData<{ stages: LadderStage[] }>(['environment-ladder'])?.stages).toHaveLength(4);
    expect(client.getQueryState(['promotions', 'release-1'])?.isInvalidated).toBe(true);
  });

  it('renders a stage name literally', async () => {
    stubFetch([stage('x', '<b>x</b>', 1)]);
    await renderSection();

    expect(container.querySelector('li b')).toBeNull();
    expect(container.querySelector('li')!.textContent).toContain('<b>x</b>');
  });
});
