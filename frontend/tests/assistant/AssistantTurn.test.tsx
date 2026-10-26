import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AssistantTurn } from '../../src/assistant/AssistantTurn';
import type { Turn } from '../../src/assistant/chatStore';
import type { SourceView } from '../../src/api/assistant';

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, params, hash, search, ...rest }: Record<string, unknown> & { children?: ReactNode }) => (
    <a data-to={String(to)} data-params={JSON.stringify(params)} data-hash={String(hash)} data-search={JSON.stringify(search)} {...rest}>
      {children}
    </a>
  ),
}));

const RID = '3f2b8c1e-5a4d-4e6f-9a7b-1c2d3e4f5a6b';

function turn(overrides: Partial<Turn> = {}): Turn {
  return {
    id: 't1',
    question: 'Is it ready?',
    status: 'done',
    phase: 'answer',
    statusTool: null,
    text: 'Yes [1]',
    held: '',
    revealed: true,
    sources: [],
    tools: [],
    historyTrimmed: false,
    outcome: null,
    retryAfterSeconds: null,
    ...overrides,
  };
}

const TICKET: SourceView = {
  n: 1,
  kind: 'TICKET',
  title: null,
  releaseId: RID,
  releaseVersion: null,
  ticketKey: 'PROJ-123',
  documentType: null,
  revision: null,
};

let container: HTMLDivElement;
let root: Root;
const onRetry = vi.fn();

beforeAll(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
});

beforeEach(() => {
  onRetry.mockReset();
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.useRealTimers();
});

function render(t: Turn) {
  act(() => root.render(<AssistantTurn turn={t} onRetry={onRetry} />));
}

describe('AssistantTurn', () => {
  it('shows only a spinner and the tool label during a tool round', () => {
    render(turn({ status: 'streaming', phase: 'tool', statusTool: 'deployments', text: '' }));
    expect(container.textContent).toContain('Checking deployments…');
    expect(container.querySelector('[aria-busy="true"]')?.textContent).toBe('');
  });

  it('shows Thinking while no event has arrived', () => {
    render(turn({ status: 'streaming', phase: 'thinking', text: '' }));
    expect(container.textContent).toContain('Thinking…');
  });

  it('renders the answer as literal text with chips', () => {
    render(turn({ text: 'Line1\nLine2 [1] <b>x</b>', sources: [TICKET] }));
    const answer = container.querySelector('[aria-live="off"]') as HTMLElement;
    expect(answer.className).toContain('whitespace-pre-wrap');
    expect(answer.textContent).toBe('Line1\nLine2 [1] <b>x</b>');
    expect(answer.querySelector('b')).toBeNull();
    const list = container.querySelector('[aria-label="Sources"]') as HTMLElement;
    expect(list.getAttribute('role')).toBe('list');
    const link = list.querySelector('a') as HTMLElement;
    expect(link.textContent).toBe('[1] Ticket PROJ-123');
    expect(link.getAttribute('data-hash')).toBe('ticket-PROJ-123');
  });

  it('renders an invalid-id chip unlinked', () => {
    render(turn({ sources: [{ ...TICKET, releaseId: 'javascript:alert(1)' }] }));
    const list = container.querySelector('[aria-label="Sources"]') as HTMLElement;
    expect(list.querySelector('a')).toBeNull();
    expect(list.textContent).toBe('[1] Ticket PROJ-123');
  });

  it('keeps the trace panel closed and lists tools and sources when opened', () => {
    render(
      turn({
        sources: [TICKET],
        tools: [{ round: 1, name: 'deployments', arguments: { release: RID }, error: false }],
      }),
    );
    const details = container.querySelector('details') as HTMLDetailsElement;
    expect(details.open).toBe(false);
    expect(details.textContent).toContain(`Deployments · release: ${RID}`);
    expect(details.textContent).toContain('[1] Ticket PROJ-123');
  });

  it('states when no lookups or sources were used', () => {
    render(turn());
    const text = container.querySelector('details')?.textContent;
    expect(text).toContain('No lookups were needed.');
    expect(text).toContain('No sources were used.');
  });

  it('renders a refusal as a muted note without chips', () => {
    render(turn({ status: 'refused', text: '' }));
    expect(container.textContent).toContain("I can't answer that from the data I have access to.");
    expect(container.querySelector('[aria-label="Sources"]')).toBeNull();
    expect(container.querySelector('details')).toBeNull();
  });

  it('shows the trace panel on a refusal only when tools ran', () => {
    render(turn({ status: 'refused', text: '', tools: [{ round: 1, name: 'releases', arguments: {}, error: false }] }));
    expect(container.querySelector('details')).not.toBeNull();
  });

  it('marks a stopped answer as incomplete', () => {
    render(turn({ status: 'stopped', text: 'Partial' }));
    expect(container.textContent).toContain('Partial');
    expect(container.textContent).toContain('Stopped. This answer is incomplete.');
  });

  it('shows the trimmed-history notice', () => {
    render(turn({ historyTrimmed: true }));
    expect(container.textContent).toContain(
      "Older turns weren't used for this answer. Start a new chat to include everything.",
    );
  });

  it.each([
    ['OFFLINE', 'The assistant is offline.', true],
    ['MODEL_MISSING', "The assistant's language model isn't installed.", false],
    ['DEADLINE', 'That took too long and was stopped.', true],
    ['FAILED', 'Something went wrong.', true],
  ] as const)('renders the %s error', (outcome, title, hasRetry) => {
    render(turn({ status: 'error', outcome, text: '' }));
    expect(container.querySelector('[role="alert"]')?.textContent).toContain(title);
    const button = container.querySelector('button');
    expect(button !== null).toBe(hasRetry);
    if (button) {
      act(() => button.click());
      expect(onRetry).toHaveBeenCalledTimes(1);
    }
  });

  it('keeps partial text above a DEADLINE banner with the stopped marker', () => {
    render(turn({ status: 'error', outcome: 'DEADLINE', text: 'Half an answer' }));
    expect(container.textContent).toContain('Half an answer');
    expect(container.textContent).toContain('Stopped. This answer is incomplete.');
  });

  it('counts down BUSY and enables Try again at zero', () => {
    vi.useFakeTimers();
    render(turn({ status: 'error', outcome: 'BUSY', retryAfterSeconds: 2, text: '' }));
    const button = container.querySelector('button') as HTMLButtonElement;
    expect(container.querySelector('[role="status"]')?.textContent).toContain('Try again in 2 seconds.');
    expect(button.disabled).toBe(true);
    act(() => vi.advanceTimersByTime(2000));
    expect(button.disabled).toBe(false);
  });

  it('says to try again in a moment when BUSY has no retry-after', () => {
    render(turn({ status: 'error', outcome: 'BUSY', retryAfterSeconds: null, text: '' }));
    expect(container.textContent).toContain('Try again in a moment.');
    expect((container.querySelector('button') as HTMLButtonElement).disabled).toBe(false);
  });
});
