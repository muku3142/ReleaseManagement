import { StrictMode, act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { PromotionSection } from '../../src/components/PromotionSection';
import type { PromotionEntry, PromotionHistory, PromotionStage } from '../../src/api/promotions';

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function stage(id: string, name: string, position: number, extra: Partial<PromotionStage> = {}): PromotionStage {
  return { id, name, position, production: false, retired: false, ...extra };
}

const DEV = stage('s1', 'DEV', 1);
const TEST = stage('s2', 'TEST', 2);
const UAT = stage('s3', 'UAT', 3);
const PROD = stage('s4', 'PROD', 4, { production: true });

function entry(id: string, extra: Partial<PromotionEntry> = {}): PromotionEntry {
  return {
    id,
    stageId: 's2',
    stageName: 'TEST',
    outcome: 'SUCCESS',
    deployedAt: '2026-10-10T09:00:00Z',
    recordedAt: '2026-10-10T09:05:00Z',
    recordedBy: 'rm@acme.com',
    note: null,
    supersedesId: null,
    supersededById: null,
    correctionReason: null,
    effective: true,
    ...extra,
  };
}

const AT_TEST: PromotionHistory = {
  stages: [DEV, TEST, UAT, PROD],
  currentStageId: 's2',
  nextStageId: 's3',
  entries: [entry('e2'), entry('e1', { stageId: 's1', stageName: 'DEV', outcome: 'FAILED' })],
};
const NOT_DEPLOYED: PromotionHistory = {
  stages: [DEV, TEST, UAT, PROD],
  currentStageId: null,
  nextStageId: 's1',
  entries: [],
};

let container: HTMLDivElement;
let root: Root;
let posts: unknown[];

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function mockApi(history: PromotionHistory, post?: () => Response) {
  posts = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
    if (init?.method === 'POST') {
      posts.push(JSON.parse(String(init.body)));
      return post ? post() : json(201, history);
    }
    return json(200, history);
  });
}

async function renderSection(isFrozen: boolean) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <PromotionSection releaseId="r1" isFrozen={isFrozen} />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
  await flush();
}

function byText(scope: ParentNode, text: string): HTMLButtonElement {
  const button = [...scope.querySelectorAll('button')].find((b) => b.textContent === text);
  expect(button, `no button labelled "${text}"`).toBeDefined();
  return button!;
}

async function change(el: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement, value: string) {
  const proto = Object.getPrototypeOf(el);
  Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(el, value);
  await act(async () => {
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
  });
}

async function openDialog() {
  await act(async () => byText(container, 'Record promotion').click());
  return container.querySelector('dialog') as HTMLDialogElement;
}

describe('PromotionSection', () => {
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
  });

  it('draws stepped dots with the current stage marked', async () => {
    mockApi(AT_TEST);
    await renderSection(true);

    const dots = [...container.querySelectorAll('ol[aria-label="Environment ladder"] li')];
    expect(dots.map((d) => d.getAttribute('data-state'))).toEqual(['reached', 'reached', 'ahead', 'ahead']);
    expect(dots.map((d) => d.getAttribute('aria-current'))).toEqual([null, 'step', null, null]);
    expect(dots[3].textContent).toContain('Production');
    expect(container.textContent).toContain('Current stage: TEST');
  });

  it('says Not deployed yet when there is no current stage', async () => {
    mockApi(NOT_DEPLOYED);
    await renderSection(true);
    expect(container.textContent).toContain('Not deployed yet');
    expect(container.textContent).toContain('No promotions recorded yet.');
  });

  it('hides a retired stage unless it is the current one', async () => {
    mockApi({ ...NOT_DEPLOYED, stages: [DEV, stage('s2', 'TEST', 2, { retired: true }), UAT, PROD] });
    await renderSection(true);
    expect(container.querySelectorAll('ol li')).toHaveLength(3);
  });

  it('draws a retired stage that is the current one', async () => {
    mockApi({
      ...AT_TEST,
      stages: [DEV, stage('s2', 'TEST', 2, { retired: true }), UAT, PROD],
    });
    await renderSection(true);
    expect(container.querySelectorAll('ol li')).toHaveLength(4);
    expect(container.querySelector('ol li[aria-current="step"]')?.textContent).toContain('TEST (retired)');
  });

  it('lists entries with outcome, both times, actor and note', async () => {
    mockApi({ ...AT_TEST, entries: [entry('e2', { note: 'smoke ok' }), AT_TEST.entries[1]] });
    await renderSection(true);

    const items = [...container.querySelectorAll('ul > li')];
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('TEST');
    expect(items[0].textContent).toContain('Success');
    expect(items[0].textContent).toContain(new Date('2026-10-10T09:00:00Z').toLocaleString());
    expect(items[0].textContent).toContain(`Recorded ${new Date('2026-10-10T09:05:00Z').toLocaleString()} by rm@acme.com`);
    expect(items[0].textContent).toContain('smoke ok');
    expect(items[1].textContent).toContain('Failed');
  });

  it('renders note markup as literal text', async () => {
    mockApi({ ...AT_TEST, entries: [entry('e2', { note: '<b>x</b>' })] });
    await renderSection(true);
    expect(container.textContent).toContain('<b>x</b>');
    expect(container.querySelector('b')).toBeNull();
  });

  it('offers no record action on an open release', async () => {
    mockApi(AT_TEST);
    await renderSection(false);
    expect([...container.querySelectorAll('button')].some((b) => b.textContent === 'Record promotion')).toBe(false);
    expect(container.textContent).toContain('Freeze this release to record promotions.');
  });

  it('preselects the next stage and switches to the current stage for a rollback', async () => {
    mockApi(AT_TEST);
    await renderSection(true);
    const dialog = await openDialog();

    expect(dialog.open).toBe(true);
    const target = dialog.querySelector<HTMLSelectElement>('#promotionStage')!;
    expect(target.value).toBe('s3');

    await change(dialog.querySelector<HTMLSelectElement>('#promotionOutcome')!, 'ROLLED_BACK');
    expect(dialog.querySelector<HTMLSelectElement>('#promotionStage')!.value).toBe('s2');
  });

  it('shows retired stages as disabled options', async () => {
    mockApi({ ...AT_TEST, stages: [DEV, TEST, UAT, stage('s5', 'OLD', 5, { retired: true })] });
    await renderSection(true);
    const dialog = await openDialog();

    const retired = [...dialog.querySelectorAll('option')].find((o) => o.textContent === 'OLD (retired)');
    expect(retired?.disabled).toBe(true);
  });

  it('posts only stage and outcome when the optional fields are untouched, then shows the new entry', async () => {
    const after: PromotionHistory = { ...AT_TEST, currentStageId: 's3', nextStageId: 's4', entries: [entry('e3', { stageId: 's3', stageName: 'UAT' }), ...AT_TEST.entries] };
    mockApi(AT_TEST, () => json(201, after));
    await renderSection(true);
    const dialog = await openDialog();

    await act(async () => byText(dialog, 'Record').click());
    await flush();

    expect(posts).toEqual([{ stageId: 's3', outcome: 'SUCCESS' }]);
    expect(dialog.open).toBe(false);
    expect(container.textContent).toContain('Current stage: UAT');
    expect(container.querySelectorAll('ul > li')).toHaveLength(3);
  });

  it('sends a local deployed time as an instant and a trimmed note', async () => {
    mockApi(AT_TEST);
    await renderSection(true);
    const dialog = await openDialog();

    await change(dialog.querySelector<HTMLInputElement>('#promotionDeployedAt')!, '2026-10-12T09:30');
    await change(dialog.querySelector<HTMLTextAreaElement>('#promotionNote')!, '  rolled out  ');
    await act(async () => byText(dialog, 'Record').click());
    await flush();

    expect(posts).toEqual([
      {
        stageId: 's3',
        outcome: 'SUCCESS',
        deployedAt: new Date('2026-10-12T09:30').toISOString(),
        note: 'rolled out',
      },
    ]);
  });

  it('keeps the dialog open and shows the mapped message on a rejected promotion', async () => {
    mockApi(AT_TEST, () => json(409, { status: 409, reason: 'PROMOTION_STAGE_SKIPPED' }));
    await renderSection(true);
    const dialog = await openDialog();

    await act(async () => byText(dialog, 'Record').click());
    await flush();

    expect(dialog.open).toBe(true);
    const alert = dialog.querySelector('[role="alert"]');
    expect(alert?.getAttribute('data-reason')).toBe('PROMOTION_STAGE_SKIPPED');
    expect(alert?.textContent).toContain('next stage of the ladder');
  });

  it('links to the document revision history', async () => {
    mockApi(AT_TEST);
    await renderSection(true);
    const link = container.querySelector('section#history a[href="#documents"]');
    expect(link?.textContent).toBe('Document revisions');
  });
});
