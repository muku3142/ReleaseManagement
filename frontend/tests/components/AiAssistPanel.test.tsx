import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AiAssistPanel } from '../../src/components/AiAssistPanel';
import type { AiResultCardProps } from '../../src/components/AiResultCard';
import { useAssistantStatus } from '../../src/api/assistant';
import { useTransformLimits, type TransformLimits } from '../../src/api/documentAssist';
import { resetRecentInstructions } from '../../src/documentAssist/recentInstructions';
import type { GeneratedDocument } from '../../src/api/documents';

vi.mock('../../src/api/assistant', () => ({ useAssistantStatus: vi.fn() }));
vi.mock('../../src/api/documentAssist', async (importActual) => ({
  ...(await importActual<typeof import('../../src/api/documentAssist')>()),
  useTransformLimits: vi.fn(),
}));

let cardProps: AiResultCardProps | null = null;
vi.mock('../../src/components/AiResultCard', () => ({
  AiResultCard: (props: AiResultCardProps) => {
    cardProps = props;
    return <div data-testid="card">{`${props.mode}|${props.instruction}|${props.downloadName}`}</div>;
  },
}));

const LIMITS: TransformLimits = {
  numCtx: 8192,
  charsPerToken: 3,
  systemTokens: 500,
  templateSlack: 100,
  instructionMaxChars: 2000,
  presets: {
    CLEAN_UP: { RELEASE_NOTES: 9000, RUNBOOK: 9000, APPROVAL: 9000 },
    SUMMARIZE: { RELEASE_NOTES: 9000, RUNBOOK: 9000, APPROVAL: 9000 },
  },
};

const doc = (overrides: Partial<GeneratedDocument> = {}): GeneratedDocument =>
  ({
    id: 'd1',
    releaseId: 'r1',
    documentType: 'RUNBOOK',
    revision: 3,
    latestRevision: 3,
    markdown: 'x'.repeat(100),
    aiAssisted: false,
    aiMode: null,
    aiInstruction: null,
    ...overrides,
  }) as GeneratedDocument;

describe('AiAssistPanel', () => {
  let container: HTMLDivElement;
  let root: Root;
  const onResultOpenChange = vi.fn();
  const onSaved = vi.fn();

  const render = (document = doc()) =>
    act(() =>
      root.render(
        <AiAssistPanel
          document={document}
          releaseName="Orchestro X"
          releaseVersion="4.2.0"
          onResultOpenChange={onResultOpenChange}
          onSaved={onSaved}
        >
          <p>DOC BODY</p>
        </AiAssistPanel>,
      ),
    );
  const button = (name: string) => [...container.querySelectorAll('button')].find((b) => b.textContent === name) as HTMLButtonElement;
  const click = (name: string) => act(() => button(name).click());
  const textarea = () => container.querySelector('textarea') as HTMLTextAreaElement;
  const type = (value: string) =>
    act(() => {
      Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!.call(textarea(), value);
      textarea().dispatchEvent(new Event('input', { bubbles: true }));
    });
  const setStatus = (data: unknown) => vi.mocked(useAssistantStatus).mockReturnValue({ data } as never);
  const setLimits = (data: TransformLimits | undefined) => vi.mocked(useTransformLimits).mockReturnValue({ data } as never);

  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    cardProps = null;
    resetRecentInstructions();
    onResultOpenChange.mockClear();
    onSaved.mockClear();
    setStatus({ enabled: true, starters: { unscoped: [], release: [] } });
    setLimits(LIMITS);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it.each([[undefined], [{ enabled: false }]])('hidesTheBarWhenStatusIs %j', async (status) => {
    setStatus(status);
    await render();
    expect(container.textContent).not.toContain('AI assist');
    expect(container.textContent).toContain('DOC BODY');
  });

  it('hidesTheBarOnASupersededRevision', async () => {
    await render(doc({ revision: 1 }));
    expect(container.textContent).not.toContain('AI assist');
    expect(container.textContent).toContain('DOC BODY');
  });

  it('showsTheThreeButtonsInOrder', async () => {
    await render();
    const labels = [...container.querySelectorAll('button')].map((b) => b.textContent);
    expect(labels).toEqual(['Clean up', 'Summarize', 'Custom…']);
    expect(container.textContent).toContain("Runs on this server's local model.");
  });

  it('disablesOnlySummarizeWhenTheDocumentIsTooLongForOneRequest', async () => {
    const tight = { ...LIMITS, presets: { CLEAN_UP: { ...LIMITS.presets.CLEAN_UP, RUNBOOK: 1000 }, SUMMARIZE: { ...LIMITS.presets.SUMMARIZE, RUNBOOK: 1000 } } };
    setLimits(tight);
    await render(doc({ markdown: 'x'.repeat(1500) }));
    expect(button('Summarize').disabled).toBe(true);
    expect(button('Clean up').disabled).toBe(false);
    expect(button('Custom…').disabled).toBe(false);
    expect(container.textContent).toContain('too long to summarize on this server (about 1,500 of 1,000 characters)');
    expect(container.textContent).toContain('Clean up and Custom still work');
  });

  it('showsTheSectionMessageWhenTheServerRejectsAPartOfTheDocument', async () => {
    await render();
    await click('Clean up');
    await act(async () => cardProps!.onTooLarge(6000, 5000));
    expect(container.textContent).toContain('A part of this document is too long for the AI model on this server (about 6,000 of 5,000 characters)');
    expect(container.textContent).toContain('Split that section with headings or blank lines');
  });

  it('keepsButtonsEnabledWhenTheLimitsQueryFailed', async () => {
    setLimits(undefined);
    await render(doc({ markdown: 'x'.repeat(100000) }));
    expect(button('Clean up').disabled).toBe(false);
    expect(container.textContent).not.toContain('too long');
  });

  it('customBoxTogglesExpandedAndEnforcesWhitespaceAndCounter', async () => {
    await render();
    expect(button('Custom…').getAttribute('aria-expanded')).toBe('false');
    await click('Custom…');
    expect(button('Custom…').getAttribute('aria-expanded')).toBe('true');
    expect(textarea().maxLength).toBe(2000);
    await type('   ');
    expect(button('Run instruction').disabled).toBe(true);
    await type('a'.repeat(1800));
    expect(container.textContent).not.toContain('1800/2000');
    await type('a'.repeat(1801));
    expect(container.textContent).toContain('1801/2000');
  });

  it('runsCustomWithCtrlEnterAddsToRecentAndClosesTheBox', async () => {
    await render();
    await click('Custom…');
    await type('Rewrite for execs');
    await act(async () => {
      textarea().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true, bubbles: true }));
    });
    expect(container.querySelector('#ai-custom-box')).toBeNull();
    expect(cardProps?.mode).toBe('CUSTOM');
    expect(cardProps?.instruction).toBe('Rewrite for execs');
    expect(onResultOpenChange).toHaveBeenCalledWith(true);
    expect(cardProps?.downloadName).toBe('runbook-orchestro-x-4-2-0-custom.md');
  });

  it('recentSelectListsNewestFirstAndFillsWithoutRunning', async () => {
    await render();
    for (const instruction of ['one', 'two', 'three', 'four', 'five', 'six']) {
      await click('Custom…');
      await type(instruction);
      await click('Run instruction');
      await act(async () => cardProps!.onClose('discarded'));
    }
    await click('Custom…');
    const select = container.querySelector('select[aria-label="Recent instructions"]') as HTMLSelectElement;
    const options = [...select.options].map((o) => o.textContent);
    expect(options).toEqual(['Recent instructions…', 'six', 'five', 'four', 'three', 'two']);
    const before = cardProps;
    await act(async () => {
      select.value = 'four';
      select.dispatchEvent(new Event('change', { bubbles: true }));
    });
    expect(textarea().value).toBe('four');
    expect(container.querySelector('[data-testid="card"]')).toBeNull();
    expect(cardProps).toBe(before);
  });

  it('recentSelectIsAbsentBeforeAnyCustomRun', async () => {
    await render();
    await click('Custom…');
    expect(container.querySelector('select')).toBeNull();
  });

  it('cleanUpOpensTheGridAndDisablesTheButtons', async () => {
    await render();
    await click('Clean up');
    expect(container.textContent).toContain('Original · Revision 3');
    expect(container.querySelector('[data-testid="card"]')?.textContent).toContain('CLEAN_UP|null|runbook-orchestro-x-4-2-0-clean-up.md');
    expect(button('Summarize').disabled).toBe(true);
    expect(button('Summarize').title).toBe('Save or discard the current result first.');
    expect(onResultOpenChange).toHaveBeenCalledWith(true);
    expect(container.textContent).toContain('DOC BODY');
  });

  it('tooLargeFromTheCardClosesItAndShowsTheSizes', async () => {
    await render();
    await click('Summarize');
    await act(async () => cardProps!.onTooLarge(12345, 9000));
    expect(container.querySelector('[data-testid="card"]')).toBeNull();
    expect(container.textContent).toContain('about 12,345 of 9,000 characters');
    expect(onResultOpenChange).toHaveBeenLastCalledWith(false);
  });

  it('closeRestoresTheCardReturnsFocusAndAnnouncesDiscard', async () => {
    await render();
    await click('Clean up');
    await act(async () => cardProps!.onClose('discarded'));
    expect(container.querySelector('[data-testid="card"]')).toBeNull();
    expect(onResultOpenChange).toHaveBeenLastCalledWith(false);
    expect(document.activeElement).toBe(button('Clean up'));
    expect(container.querySelector('[role="status"]')?.textContent).toBe('Result discarded.');
  });

  it('savedPropagatesTheRevisionAndAnnouncesIt', async () => {
    await render();
    await click('Clean up');
    await act(async () => cardProps!.onSaved(5));
    expect(onSaved).toHaveBeenCalledWith(5);
    expect(container.querySelector('[role="status"]')?.textContent).toBe('Saved as Revision 5.');
    expect(container.querySelector('[data-testid="card"]')).toBeNull();
  });

});
