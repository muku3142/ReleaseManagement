import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReleaseAssistantDrawer } from '../../src/assistant/ReleaseAssistantDrawer';
import type { Session, Turn } from '../../src/assistant/chatStore';

const mocks = vi.hoisted(() => ({
  session: undefined as unknown as { current: Session },
  keys: [] as string[],
  actions: { ask: vi.fn(), stop: vi.fn(), retry: vi.fn(), newChat: vi.fn(), setScope: vi.fn() },
}));

vi.mock('../../src/assistant/chatStore', () => ({
  useChatSession: (key: string) => {
    mocks.keys.push(key);
    return mocks.session.current;
  },
  chatActions: mocks.actions,
}));
vi.mock('../../src/api/assistant', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../src/api/assistant')>()),
  useAssistantFreshness: () => ({ data: undefined }),
}));
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children?: React.ReactNode }) => <a>{children}</a>,
}));

function session(overrides: Partial<Session> = {}): Session {
  return { scope: null, turns: [], banner: null, inFlight: null, ...overrides };
}

const STREAMING_TURN: Turn = {
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
};

let container: HTMLDivElement;
let root: Root;
const onClose = vi.fn();
const STARTERS = ['What is in {release}?', 'Anything risky?'];

async function render(releaseId = 'r1', label = 'Atlas 4.2.0') {
  await act(async () => {
    root.render(
      <QueryClientProvider client={new QueryClient()}>
        <ReleaseAssistantDrawer releaseId={releaseId} label={label} starters={STARTERS} onClose={onClose} />
      </QueryClientProvider>,
    );
  });
}

function pressEscape(target: Element) {
  return act(async () => {
    target.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
  });
}

beforeAll(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  Element.prototype.scrollIntoView = vi.fn();
});

beforeEach(() => {
  Object.values(mocks.actions).forEach((fn) => fn.mockReset());
  onClose.mockReset();
  mocks.keys = [];
  mocks.session = { current: session() };
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
});

describe('ReleaseAssistantDrawer', () => {
  it('renders a fixed release scope with no picker or remove button', async () => {
    await render();

    const region = container.querySelector('[role="region"]')!;
    expect(region.getAttribute('aria-label')).toBe('Ask about this release');
    expect(region.id).toBe('assistant-drawer');
    expect(container.textContent).toContain('Release: Atlas 4.2.0');
    expect(container.querySelector('button[aria-label="Close assistant"]')).not.toBeNull();
    expect([...container.querySelectorAll('button')].some((b) => b.textContent === 'New chat')).toBe(true);
    expect(container.querySelector('button[aria-label^="Remove release scope"]')).toBeNull();
    expect(container.querySelector('select')).toBeNull();
  });

  it('scopes the drawer session and focuses the first starter when empty', async () => {
    await render();

    expect(mocks.actions.setScope).toHaveBeenCalledWith('drawer:r1', { releaseId: 'r1', label: 'Atlas 4.2.0' });
    expect(document.activeElement?.textContent).toBe(STARTERS[0]);
  });

  it('focuses the composer when the chat already has turns', async () => {
    mocks.session = { current: session({ turns: [{ ...STREAMING_TURN, status: 'done' }] }) };
    await render();

    expect(document.activeElement?.tagName).toBe('TEXTAREA');
  });

  it('stops the stream then closes from the close button', async () => {
    await render();
    await act(async () => container.querySelector<HTMLButtonElement>('button[aria-label="Close assistant"]')!.click());

    expect(mocks.actions.stop).toHaveBeenCalledWith('drawer:r1');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('closes on Escape when idle', async () => {
    await render();
    await pressEscape(container.querySelector('[role="region"] h3')!);

    expect(mocks.actions.stop).toHaveBeenCalledWith('drawer:r1');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('first Escape in the composer while streaming stops only, the second closes', async () => {
    mocks.session = { current: session({ turns: [STREAMING_TURN], inFlight: { turnId: 't1' } as never }) };
    await render();
    const textarea = container.querySelector('textarea')!;

    await pressEscape(textarea);
    expect(mocks.actions.stop).toHaveBeenCalledWith('drawer:r1');
    expect(onClose).not.toHaveBeenCalled();

    mocks.session = { current: session({ turns: [{ ...STREAMING_TURN, status: 'stopped' }] }) };
    await render();
    await pressEscape(container.querySelector('textarea')!);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('does not close on a click outside', async () => {
    await render();
    await act(async () => {
      document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
      document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(onClose).not.toHaveBeenCalled();
  });

  it('uses a separate session per release', async () => {
    await render('r1');
    await render('r2', 'Boreas 1.0');

    expect(mocks.keys).toContain('drawer:r1');
    expect(mocks.keys).toContain('drawer:r2');
    expect(mocks.actions.setScope).toHaveBeenLastCalledWith('drawer:r2', { releaseId: 'r2', label: 'Boreas 1.0' });
  });
});
