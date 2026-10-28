import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AiResultCard, type AiResultCardProps } from '../../src/components/AiResultCard';
import { ApiError, triggerDownload } from '../../src/api/client';
import { streamTransform, useSaveAiRevision, type TransformEvent } from '../../src/api/documentAssist';

vi.mock('../../src/api/documentAssist', async (importActual) => ({
  ...(await importActual<typeof import('../../src/api/documentAssist')>()),
  streamTransform: vi.fn(),
  useSaveAiRevision: vi.fn(),
}));
vi.mock('../../src/api/client', async (importActual) => ({
  ...(await importActual<typeof import('../../src/api/client')>()),
  triggerDownload: vi.fn(),
}));

type Mutate = (body: unknown, options: { onSuccess: (r: { revision: number }) => void; onError: (e: unknown) => void }) => void;

let emit: (event: TransformEvent) => void;
let finish: () => void;
let fail: (error: unknown) => void;
let signal: AbortSignal;
const mutate = vi.fn<Mutate>();

const token = (text: string): TransformEvent => ({ event: 'token', data: { text } });
const done = (finishReason: string | null = 'stop', truncated = false): TransformEvent => ({
  event: 'done',
  data: { finishReason, truncated, complete: finishReason === 'stop' && !truncated },
});
const errorEvent = (outcome: 'OFFLINE' | 'BUSY' | 'MODEL_MISSING' | 'DEADLINE' | 'FAILED', retryAfterSeconds: number | null = null): TransformEvent => ({
  event: 'error',
  data: { outcome, retryAfterSeconds, characters: null, maxCharacters: null },
});

describe('AiResultCard', () => {
  let container: HTMLDivElement;
  let root: Root;
  const onClose = vi.fn();
  const onSaved = vi.fn();
  const onTooLarge = vi.fn();

  const render = (overrides: Partial<AiResultCardProps> = {}) =>
    act(() =>
      root.render(
        <AiResultCard
          releaseId="r1"
          documentType="RELEASE_NOTES"
          sourceRevision={3}
          originalMarkdown="PROJ-1 PROJ-2"
          mode="CLEAN_UP"
          instruction={null}
          downloadName="release-notes-x-1-0-clean-up.md"
          onClose={onClose}
          onSaved={onSaved}
          onTooLarge={onTooLarge}
          {...overrides}
        />,
      ),
    );
  const button = (name: string) =>
    [...container.querySelectorAll('button')].find((b) => (b.getAttribute('aria-label') ?? b.textContent) === name) as HTMLButtonElement;
  const click = (name: string) => act(() => button(name).click());
  const events = (...list: TransformEvent[]) => act(() => list.forEach(emit));
  const textarea = () => container.querySelector('textarea') as HTMLTextAreaElement;
  const type = (value: string) =>
    act(() => {
      Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!.call(textarea(), value);
      textarea().dispatchEvent(new Event('input', { bubbles: true }));
    });
  const completeWith = (output: string) => events(token(output), done());

  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    vi.mocked(streamTransform).mockImplementation((_body, s, onEvent) => {
      signal = s;
      emit = onEvent;
      return new Promise<void>((resolve, reject) => {
        finish = resolve;
        fail = reject;
      });
    });
    vi.mocked(useSaveAiRevision).mockReturnValue({ mutate } as unknown as ReturnType<typeof useSaveAiRevision>);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText: vi.fn().mockResolvedValue(undefined) }, configurable: true });
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
    vi.useRealTimers();
    vi.clearAllMocks();
    vi.restoreAllMocks();
  });

  it('streams on mount, focuses Stop and shows the character count', () => {
    render({ mode: 'CUSTOM', instruction: 'shorter' });
    expect(streamTransform).toHaveBeenCalledTimes(1);
    expect(vi.mocked(streamTransform).mock.calls[0][0]).toEqual({
      releaseId: 'r1',
      documentType: 'RELEASE_NOTES',
      sourceRevision: 3,
      mode: 'CUSTOM',
      instruction: 'shorter',
    });
    expect(document.activeElement).toBe(button('Stop generating'));
    events(token('hello'));
    expect(container.textContent).toContain('Writing… 5 characters so far');
  });

  it('shows which section is being written for a sectioned run', () => {
    render();
    events(token('# Title\n'), { event: 'progress', data: { section: 2, sections: 5 } });
    expect(container.textContent).toContain('Section 2 of 5 · Writing… 8 characters so far');
  });

  it('Stop aborts and leaves an unsaveable result', () => {
    render();
    events(token('part'));
    click('Stop generating');
    expect(signal.aborted).toBe(true);
    expect(container.textContent).toContain('Stopped. This result is incomplete.');
    expect(container.textContent).toContain("A stopped result can't be saved.");
    const save = button('Accept and save as revision 4');
    expect(save.getAttribute('aria-disabled')).toBe('true');
    act(() => save.click());
    expect(mutate).not.toHaveBeenCalled();
  });

  it('Escape inside the card stops the stream', () => {
    render();
    events(token('part'));
    act(() => {
      container.querySelector('h2')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    });
    expect(signal.aborted).toBe(true);
  });

  it('edits a complete Clean up result and previews it without images', () => {
    render();
    completeWith('![alt](http://x/y.png) done');
    expect(textarea().value).toBe('![alt](http://x/y.png) done');
    type('changed ![alt](http://x/y.png)');
    expect(container.textContent).toContain('Edited');
    click('Preview');
    expect(container.querySelector('img')).toBeNull();
    expect(container.textContent).toContain('[image: alt]');
  });

  it('warns about differing keys without disabling Save', () => {
    vi.useFakeTimers();
    render();
    completeWith('PROJ-1 PROJ-3');
    expect(container.textContent).toContain('Check before saving: ticket keys or versions differ from the original.');
    const missing = [...container.querySelectorAll('p')].find((p) => p.textContent === 'Missing from the result')!;
    expect(missing.nextElementSibling!.textContent).toBe('PROJ-2');
    const added = [...container.querySelectorAll('p')].find((p) => p.textContent === 'Not in the original')!;
    expect(added.nextElementSibling!.textContent).toBe('PROJ-3');
    expect(button('Accept and save as revision 4').getAttribute('aria-disabled')).toBeNull();
    type('PROJ-1 PROJ-2');
    act(() => {
      vi.advanceTimersByTime(500);
    });
    expect(container.textContent).toContain('All ticket keys and versions in the original are present');
    expect(container.textContent).not.toContain('Check before saving');
  });

  it('saves the edited buffer and reports the new revision', () => {
    render({ mode: 'CUSTOM', instruction: 'shorter' });
    completeWith('PROJ-1 PROJ-2');
    type('PROJ-1 PROJ-2 edited');
    click('Accept and save as revision 4');
    expect(mutate.mock.calls[0][0]).toEqual({
      releaseId: 'r1',
      documentType: 'RELEASE_NOTES',
      sourceRevision: 3,
      mode: 'CUSTOM',
      instruction: 'shorter',
      markdown: 'PROJ-1 PROJ-2 edited',
    });
    expect(container.textContent).toContain('Accepting adds Revision 4');
    act(() => mutate.mock.calls[0][1].onSuccess({ revision: 4 }));
    expect(onSaved).toHaveBeenCalledWith(4);
  });

  it('keeps the buffer and explains a failed save', () => {
    render();
    completeWith('PROJ-1 PROJ-2');
    type('PROJ-1 PROJ-2 mine');
    click('Accept and save as revision 4');
    act(() =>
      mutate.mock.calls[0][1].onError(new ApiError({ status: 409, reason: 'VALIDATION_FAILED', fieldErrors: { a: 'Out of date.' } })),
    );
    expect(container.textContent).toContain("Couldn't save the revision — Out of date. Your result is still here. Try again.");
    expect(textarea().value).toBe('PROJ-1 PROJ-2 mine');
  });

  it('lets a complete summary be edited and saved like Clean up', () => {
    render({ mode: 'SUMMARIZE' });
    completeWith('short');
    expect(container.textContent).not.toContain('Summaries are for reading only');
    expect(button('Edit')).toBeDefined();
    expect(button('Source')).toBeUndefined();
    type('short edited');
    expect(button('Accept and save as revision 4').getAttribute('aria-disabled')).toBeNull();
    click('Accept and save as revision 4');
    expect(mutate.mock.calls[0][0]).toMatchObject({ mode: 'SUMMARIZE', markdown: 'short edited' });
    expect(button('Download')).toBeDefined();
  });

  it('blocks Save on a stopped summary like Clean up', () => {
    render({ mode: 'SUMMARIZE' });
    events(token('cut'), done('length'));
    expect(container.textContent).toContain('This result is incomplete.');
    expect(button('Accept and save as revision 4').getAttribute('aria-disabled')).toBe('true');
  });

  const kept = '# A\nONE\n# B\n';
  const whole = `${kept}Two\n# C\nThree\n`;
  const filledDeadline = (): TransformEvent[] => [
    token('# A\nONE\n'),
    { event: 'progress', data: { section: 1, sections: 3 } },
    token('# B\n'),
    { event: 'progress', data: { section: 2, sections: 3 } },
    token('TW'),
    { event: 'remainder', data: { keep: kept.length, text: 'Two\n# C\nThree\n' } },
    errorEvent('DEADLINE'),
  ];

  it('lets a deadline stop after finished sections be saved with the rest kept as it was', () => {
    render({ mode: 'CLEAN_UP' });
    events(...filledDeadline());
    expect(textarea().value).toBe(whole);
    expect(container.textContent).toContain('That took too long and was stopped.');
    expect(container.textContent).toContain('Stopped at section 2 of 3');
    expect(container.textContent).toContain('kept as they were');
    expect(button('Accept and save as revision 4').getAttribute('aria-disabled')).toBeNull();
    expect(button('Download').disabled).toBe(false);
    click('Accept and save as revision 4');
    expect(mutate.mock.calls[0][0]).toMatchObject({ markdown: whole });
  });

  it('Try again after a filled stop restarts the run', () => {
    render({ mode: 'CLEAN_UP' });
    events(...filledDeadline());
    click('Try again');
    expect(streamTransform).toHaveBeenCalledTimes(2);
  });

  it('keeps a deadline stop without a remainder blocked', () => {
    render({ mode: 'CLEAN_UP' });
    events(token('# A\nONE\n'), errorEvent('DEADLINE'));
    expect(container.textContent).toContain("can't be saved");
    expect(button('Accept and save as revision 4').getAttribute('aria-disabled')).toBe('true');
  });

  it('keeps a Summarize deadline stop blocked', () => {
    render({ mode: 'SUMMARIZE' });
    events(token('partial summary'), errorEvent('DEADLINE'));
    expect(button('Accept and save as revision 4').getAttribute('aria-disabled')).toBe('true');
  });

  it('shows the verify banner for runbook and approval only', () => {
    render({ documentType: 'RUNBOOK', mode: 'SUMMARIZE' });
    expect(container.textContent).toContain('Verify steps before use.');
    render({ documentType: 'APPROVAL' });
    expect(container.textContent).toContain('Verify steps before use.');
    render({ documentType: 'RELEASE_NOTES' });
    expect(container.textContent).not.toContain('Verify steps before use.');
  });

  it('blocks Save and Download on a length end but keeps Copy', () => {
    render();
    events(token('cut'), done('length'));
    expect(container.textContent).toContain('This result is incomplete.');
    expect(button('Accept and save as revision 4').getAttribute('aria-disabled')).toBe('true');
    expect(button('Download').disabled).toBe(true);
    expect(button('Copy').disabled).toBe(false);
  });

  it('blocks Save on a stream that ends without done', async () => {
    render();
    events(token('cut'));
    await act(async () => finish());
    expect(container.textContent).toContain('Something went wrong.');
    expect(button('Accept and save as revision 4').getAttribute('aria-disabled')).toBe('true');
  });

  it('shows OFFLINE with Try again and Close, and Try again restarts the run', () => {
    render();
    events(errorEvent('OFFLINE'));
    expect(container.textContent).toContain('The assistant is offline.');
    click('Try again');
    expect(streamTransform).toHaveBeenCalledTimes(2);
    events(errorEvent('OFFLINE'));
    click('Close');
    expect(onClose).toHaveBeenCalledWith('closed');
  });

  it('disables Try again for BUSY until the countdown ends', () => {
    vi.useFakeTimers();
    render();
    events(errorEvent('BUSY', 2));
    expect(button('Try again').disabled).toBe(true);
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(button('Try again').disabled).toBe(true);
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(button('Try again').disabled).toBe(false);
  });

  it('offers only Close for MODEL_MISSING', () => {
    render();
    events(errorEvent('MODEL_MISSING'));
    expect(button('Try again')).toBeUndefined();
    expect(button('Close')).toBeDefined();
  });

  it('hands a 413 to onTooLarge and renders nothing', async () => {
    render();
    await act(async () =>
      fail(new ApiError({ status: 413, reason: 'TOO_LARGE', characters: 9000, maxCharacters: 5000 } as never)),
    );
    expect(onTooLarge).toHaveBeenCalledWith(9000, 5000);
    expect(container.innerHTML).toBe('');
  });

  it('discards an unedited result at once', () => {
    render();
    completeWith('PROJ-1 PROJ-2');
    click('Discard');
    expect(onClose).toHaveBeenCalledWith('discarded');
  });

  it('confirms before discarding edits and Escape cancels', () => {
    render();
    completeWith('PROJ-1 PROJ-2');
    type('PROJ-1 PROJ-2 edited');
    click('Discard');
    expect(onClose).not.toHaveBeenCalled();
    expect(container.textContent).toContain('Discard your edits?');
    expect(document.activeElement).toBe(button('Keep editing'));
    act(() => {
      button('Keep editing').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    });
    expect(container.textContent).not.toContain('Discard your edits?');
    click('Discard');
    click('Discard edits');
    expect(onClose).toHaveBeenCalledWith('discarded');
  });

  it('aborts the stream on unmount', () => {
    render();
    act(() => root.unmount());
    expect(signal.aborted).toBe(true);
    root = createRoot(container);
  });

  it('copies the buffer and downloads it as Markdown', async () => {
    render();
    completeWith('PROJ-1 PROJ-2');
    type('PROJ-1 PROJ-2 edited');
    await act(async () => button('Copy').click());
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('PROJ-1 PROJ-2 edited');
    expect(button('Copied')).toBeDefined();
    click('Download');
    const [blob, name] = vi.mocked(triggerDownload).mock.calls[0];
    expect(blob.type).toBe('text/markdown');
    expect(name).toBe('release-notes-x-1-0-clean-up.md');
  });

  it('never touches browser storage or the console', () => {
    const storage = vi.spyOn(Storage.prototype, 'setItem');
    const consoles = (['log', 'warn', 'error', 'info', 'debug'] as const).map((m) => vi.spyOn(console, m));
    render();
    completeWith('PROJ-1 PROJ-2');
    type('edited');
    click('Preview');
    expect(storage).not.toHaveBeenCalled();
    consoles.forEach((spy) => expect(spy).not.toHaveBeenCalled());
  });
});
