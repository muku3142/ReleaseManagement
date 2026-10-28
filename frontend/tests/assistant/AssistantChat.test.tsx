import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AssistantChat } from '../../src/assistant/AssistantChat';
import { ScopeChip } from '../../src/assistant/ScopeChip';
import { FreshnessLine } from '../../src/assistant/FreshnessLine';
import type { Session, Turn } from '../../src/assistant/chatStore';

const mocks = vi.hoisted(() => ({
  session: undefined as unknown as { current: Session },
  actions: { ask: vi.fn(), stop: vi.fn(), retry: vi.fn(), newChat: vi.fn(), setScope: vi.fn() },
  freshness: { data: undefined as { lastIndexedAt: string | null } | undefined },
}));

vi.mock('../../src/assistant/chatStore', () => ({
  useChatSession: () => mocks.session.current,
  chatActions: mocks.actions,
}));
vi.mock('../../src/api/assistant', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../src/api/assistant')>()),
  useAssistantFreshness: () => mocks.freshness,
}));
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children?: ReactNode }) => <a>{children}</a>,
}));

function session(overrides: Partial<Session> = {}): Session {
  return { scope: null, turns: [], banner: null, inFlight: null, ...overrides };
}

function streamingTurn(overrides: Partial<Turn> = {}): Turn {
  return {
    id: 't1',
    question: 'q',
    status: 'streaming',
    phase: 'thinking',
    statusTool: null,
    text: '',
    held: '',
    revealed: false,
    sources: [],
    tools: [],
    historyTrimmed: false,
    outcome: null,
    retryAfterSeconds: null,
    ...overrides,
  };
}

let container: HTMLDivElement;
let root: Root;
const client = new QueryClient();

beforeAll(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
});

beforeEach(() => {
  Object.values(mocks.actions).forEach((fn) => fn.mockReset());
  mocks.session = { current: session() };
  mocks.freshness.data = undefined;
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

function render(node: ReactNode) {
  act(() => root.render(<QueryClientProvider client={client}>{node}</QueryClientProvider>));
}

function chat(starters: string[] = []) {
  render(<AssistantChat sessionKey="page" starters={starters} scopeRow={null} />);
}

function textarea() {
  return container.querySelector('textarea') as HTMLTextAreaElement;
}

function type(value: string) {
  act(() => {
    const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')?.set;
    setter?.call(textarea(), value);
    textarea().dispatchEvent(new Event('input', { bubbles: true }));
  });
}

function press(key: string, shiftKey = false) {
  const event = new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true });
  act(() => {
    textarea().dispatchEvent(event);
  });
  return event;
}

function button(name: string) {
  return [...container.querySelectorAll('button')].find(
    (b) => b.textContent === name || b.getAttribute('aria-label') === name,
  ) as HTMLButtonElement | undefined;
}

describe('AssistantChat', () => {
  it('shows a scoped empty state with starters that send on click', () => {
    mocks.session.current = session({ scope: { releaseId: 'r1', label: 'Atlas 4.2.0' } });
    chat(['Is {release} ready for Production?']);
    expect(container.querySelector('h3')?.textContent).toBe('Ask about Atlas 4.2.0');
    expect(container.textContent).toContain('Every answer shows where it came from.');
    act(() => button('Is Atlas 4.2.0 ready for Production?')?.click());
    expect(mocks.actions.ask).toHaveBeenCalledWith('page', 'Is Atlas 4.2.0 ready for Production?');
  });

  it('shows the unscoped heading and omits an empty starter block', () => {
    chat([]);
    expect(container.querySelector('h3')?.textContent).toBe('Ask about your releases');
    expect(container.querySelectorAll('.max-w-md button')).toHaveLength(0);
  });

  it('sends on Enter, clears the textarea and keeps Shift+Enter for a line break', () => {
    chat();
    type('How are we?');
    expect(press('Enter', true).defaultPrevented).toBe(false);
    expect(mocks.actions.ask).not.toHaveBeenCalled();
    press('Enter');
    expect(mocks.actions.ask).toHaveBeenCalledWith('page', 'How are we?');
    expect(textarea().value).toBe('');
  });

  it('disables Send for whitespace and caps input at 1500 with a counter above 1300', () => {
    chat();
    type('   ');
    expect(button('Send')?.disabled).toBe(true);
    expect(textarea().maxLength).toBe(1500);
    type('a'.repeat(1300));
    expect(container.textContent).not.toContain('/1500');
    type('a'.repeat(1301));
    expect(container.textContent).toContain('1301/1500');
  });

  it('swaps Send for Stop while streaming and stops on click or Escape', () => {
    mocks.session.current = session({ turns: [streamingTurn()], inFlight: new AbortController() });
    chat();
    expect(button('Send')).toBeUndefined();
    type('next');
    press('Enter');
    expect(mocks.actions.ask).not.toHaveBeenCalled();
    act(() => button('Stop generating')?.click());
    press('Escape');
    expect(mocks.actions.stop).toHaveBeenCalledTimes(2);
    expect(mocks.actions.stop).toHaveBeenCalledWith('page');
  });

  it('shows the session banner for OFFLINE and MODEL_MISSING only', () => {
    mocks.session.current = session({ banner: 'OFFLINE' });
    chat();
    expect(container.querySelector('[role="alert"]')?.className).toContain('amber');
    expect(container.textContent).toContain('The assistant is offline.');
    mocks.session.current = session({ banner: 'MODEL_MISSING' });
    chat();
    expect(container.querySelector('[role="alert"]')?.className).toContain('red');
    mocks.session.current = session();
    chat();
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });

  it('announces submit and completion in the polite live region', () => {
    chat();
    const live = container.querySelector('[role="status"][aria-live="polite"]') as HTMLElement;
    type('hi');
    press('Enter');
    expect(live.textContent).toBe('Assistant is working.');
    mocks.session.current = session({ turns: [streamingTurn({ status: 'done', text: 'ok' })] });
    chat();
    expect(live.textContent).toBe('Answer ready.');
  });
});

describe('ScopeChip', () => {
  it('shows the label with a remove button only when removable', () => {
    const onRemove = vi.fn();
    render(<ScopeChip label="Atlas 4.2.0" onRemove={onRemove} />);
    expect(container.textContent).toContain('Release: Atlas 4.2.0');
    act(() => button('Remove release scope Atlas 4.2.0')?.click());
    expect(onRemove).toHaveBeenCalled();
    render(<ScopeChip label="Atlas 4.2.0" />);
    expect(container.querySelector('button')).toBeNull();
  });
});

describe('FreshnessLine', () => {
  it('shows relative time with the absolute time in the title', () => {
    mocks.freshness.data = { lastIndexedAt: new Date(Date.now() - 4 * 60_000 - 5000).toISOString() };
    render(<FreshnessLine enabled />);
    const line = container.querySelector('p') as HTMLElement;
    expect(line.textContent).toBe('Search index last updated 4 minutes ago');
    expect(line.title).not.toBe('');
    expect(line.className).toContain('text-gray-500');
  });

  it('warns when older than 24 hours', () => {
    mocks.freshness.data = { lastIndexedAt: new Date(Date.now() - 30 * 3_600_000).toISOString() };
    render(<FreshnessLine enabled />);
    const line = container.querySelector('p') as HTMLElement;
    expect(line.textContent).toContain(' · Recent changes may be missing.');
    expect(line.className).toContain('amber');
  });

  it('says the index has not been built when null', () => {
    mocks.freshness.data = { lastIndexedAt: null };
    render(<FreshnessLine enabled />);
    expect(container.textContent).toBe("Search index hasn't been built yet.");
  });

  it('renders nothing without data', () => {
    render(<FreshnessLine enabled />);
    expect(container.textContent).toBe('');
  });
});
