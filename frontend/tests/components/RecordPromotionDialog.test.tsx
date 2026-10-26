import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { RecordPromotionDialog, recordStageOptions, skipNote, skippedStages } from '../../src/components/RecordPromotionDialog';
import type { PromotionHistory, PromotionStage } from '../../src/api/promotions';

function stage(id: string, name: string, position: number, extra: Partial<PromotionStage> = {}): PromotionStage {
  return { id, name, position, production: false, retired: false, ...extra };
}

const DEV = stage('s1', 'DEV', 1);
const TEST = stage('s2', 'TEST', 2);
const UAT = stage('s3', 'UAT', 3);
const PROD = stage('s4', 'PROD', 4, { production: true });

function history(stages: PromotionStage[], currentStageId: string | null, nextStageId: string | null): PromotionHistory {
  return { stages, currentStageId, nextStageId, entries: [] };
}

describe('recordStageOptions', () => {
  it('offers every active stage when nothing is deployed yet', () => {
    const options = recordStageOptions(history([DEV, TEST, UAT, PROD], null, 's1'));
    expect(options.map((o) => o.label)).toEqual(['DEV', 'TEST', 'UAT', 'PROD']);
  });

  it('offers the current stage as a redeploy and every later stage', () => {
    const options = recordStageOptions(history([DEV, TEST, UAT, PROD], 's2', 's3'));
    expect(options.map((o) => o.label)).toEqual(['TEST (redeploy)', 'UAT', 'PROD']);
    expect(options.every((o) => !o.disabled)).toBe(true);
  });

  it('skips retired later stages', () => {
    const options = recordStageOptions(history([DEV, TEST, { ...UAT, retired: true }, PROD], 's2', 's4'));
    expect(options.map((o) => o.label)).toEqual(['TEST (redeploy)', 'PROD']);
  });

  it('offers only the redeploy at the last stage', () => {
    const options = recordStageOptions(history([DEV, TEST, UAT, PROD], 's4', null));
    expect(options.map((o) => o.label)).toEqual(['PROD (redeploy)']);
  });

  it('keeps a retired current stage visible but disabled', () => {
    const options = recordStageOptions(history([DEV, { ...TEST, retired: true }, UAT, PROD], 's2', 's3'));
    expect(options).toEqual([
      { id: 's2', label: 'TEST (retired)', disabled: true },
      { id: 's3', label: 'UAT', disabled: false },
      { id: 's4', label: 'PROD', disabled: false },
    ]);
  });

  it('leaves nothing selectable when the retired current stage is the last', () => {
    const options = recordStageOptions(history([DEV, TEST, UAT, { ...PROD, retired: true }], 's4', null));
    expect(options).toEqual([{ id: 's4', label: 'PROD (retired)', disabled: true }]);
  });

  it('orders by position rather than array order', () => {
    const options = recordStageOptions(history([PROD, UAT, TEST, DEV], null, 's1'));
    expect(options.map((o) => o.label)).toEqual(['DEV', 'TEST', 'UAT', 'PROD']);
  });
});

describe('skippedStages', () => {
  const h = history([DEV, TEST, UAT, PROD], 's1', 's2');

  it('lists the active stages between the current and the selected one', () => {
    expect(skippedStages(h, 's4').map((s) => s.name)).toEqual(['TEST', 'UAT']);
  });

  it('is empty for the next stage and for the current stage', () => {
    expect(skippedStages(h, 's2')).toEqual([]);
    expect(skippedStages(h, 's1')).toEqual([]);
  });

  it('does not count retired stages as skipped', () => {
    expect(skippedStages(history([DEV, { ...TEST, retired: true }, UAT, PROD], 's1', 's3'), 's4').map((s) => s.name)).toEqual(['UAT']);
  });

  it('counts from the start of the ladder when nothing is deployed', () => {
    expect(skippedStages(history([DEV, TEST, UAT, PROD], null, 's1'), 's3').map((s) => s.name)).toEqual(['DEV', 'TEST']);
  });
});

describe('skipNote', () => {
  it('names the skipped stages', () => {
    expect(skipNote(['TEST', 'UAT'])).toBe('This skips 2 stage(s): TEST, UAT.');
  });

  it('truncates after three names', () => {
    expect(skipNote(['A', 'B', 'C', 'D', 'E'])).toBe('This skips 5 stage(s): A, B, C and 2 more.');
  });
});

let container: HTMLDivElement;
let root: Root;
let posts: unknown[];

function mockPost(response: () => Response) {
  posts = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
    posts.push(JSON.parse(String(init?.body)));
    return response();
  });
}

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderDialog(h: PromotionHistory) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <RecordPromotionDialog open releaseId="r1" history={h} onClose={() => {}} />
      </QueryClientProvider>,
    );
  });
  return container.querySelector('dialog') as HTMLDialogElement;
}

async function pick(select: HTMLSelectElement, value: string) {
  Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')!.set!.call(select, value);
  await act(async () => {
    select.dispatchEvent(new Event('change', { bubbles: true }));
  });
}

function labels(dialog: HTMLDialogElement) {
  return [...dialog.querySelectorAll('#promotionStage option')].map((o) => o.textContent);
}

function recordButton(dialog: HTMLDialogElement) {
  return [...dialog.querySelectorAll('button')].find((b) => b.textContent === 'Record')!;
}

describe('RecordPromotionDialog record mode', () => {
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

  const atDev = history([DEV, TEST, UAT, PROD], 's1', 's2');
  const help =
    "Choose the next stage or skip ahead. Pick DEV to record a redeploy. Earlier stages aren't offered; record a rollback instead.";

  it('offers the redeploy and later stages, preselects the next one and explains the choice', async () => {
    const dialog = await renderDialog(atDev);
    const select = dialog.querySelector<HTMLSelectElement>('#promotionStage')!;

    expect(labels(dialog)).toEqual(['DEV (redeploy)', 'TEST', 'UAT', 'PROD']);
    expect(select.value).toBe('s2');
    expect(select.getAttribute('aria-describedby')).toBe('promotionStageHelp');
    expect(dialog.querySelector('#promotionStageHelp')?.textContent).toBe(help);
    expect(dialog.querySelector('[role="status"]')).toBeNull();
  });

  it('notes the skipped stages and posts the chosen stage without a reason', async () => {
    mockPost(() => new Response(JSON.stringify(atDev), { status: 201, headers: { 'Content-Type': 'application/json' } }));
    const dialog = await renderDialog(atDev);

    await pick(dialog.querySelector<HTMLSelectElement>('#promotionStage')!, 's4');
    expect(dialog.querySelector('[role="status"]')?.textContent).toBe('This skips 2 stage(s): TEST, UAT.');
    expect(dialog.querySelector('#promotionReason')).toBeNull();

    await act(async () => recordButton(dialog).click());
    await flush();
    expect(posts).toEqual([{ stageId: 's4', outcome: 'SUCCESS' }]);
  });

  it('shows neither a help nor a skip line when nothing is deployed', async () => {
    const dialog = await renderDialog(history([DEV, TEST, UAT, PROD], null, 's1'));

    expect(labels(dialog)).toEqual(['DEV', 'TEST', 'UAT', 'PROD']);
    expect(dialog.querySelector('#promotionStageHelp')).toBeNull();
    expect(dialog.querySelector('#promotionStage')?.hasAttribute('aria-describedby')).toBe(false);
  });

  it('disables Record and explains when no stage can be promoted to', async () => {
    const dialog = await renderDialog(history([DEV, TEST, UAT, { ...PROD, retired: true }], 's4', null));

    expect(recordButton(dialog).disabled).toBe(true);
    expect(dialog.textContent).toContain('No active stage is available to promote to. Add or restore a stage in Settings.');
  });

  it('keeps the full list with retired stages disabled when correcting', async () => {
    const client = new QueryClient();
    await act(async () => {
      root.render(
        <QueryClientProvider client={client}>
          <RecordPromotionDialog
            open
            releaseId="r1"
            history={history([DEV, { ...TEST, retired: true }, UAT, PROD], 's3', 's4')}
            correcting={{
              id: 'e1',
              stageId: 's3',
              stageName: 'UAT',
              outcome: 'SUCCESS',
              deployedAt: '2026-10-10T09:00:00Z',
              recordedAt: '2026-10-10T09:05:00Z',
              recordedBy: 'rm@acme.com',
              note: null,
              supersedesId: null,
              supersededById: null,
              correctionReason: null,
              effective: true,
            }}
            onClose={() => {}}
          />
        </QueryClientProvider>,
      );
    });
    const dialog = container.querySelector('dialog') as HTMLDialogElement;

    expect(labels(dialog)).toEqual(['DEV', 'TEST (retired)', 'UAT', 'PROD']);
    expect(dialog.querySelector('#promotionStageHelp')).toBeNull();
  });
});
