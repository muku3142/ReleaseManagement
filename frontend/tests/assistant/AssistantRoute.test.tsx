import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AssistantRoute } from '../../src/routes/AssistantRoute';
import type { Session } from '../../src/assistant/chatStore';

const STARTERS = { unscoped: ['Unscoped starter'], release: ['Release starter {release}'] };

const mocks = vi.hoisted(() => ({
  session: undefined as unknown as { current: Session },
  actions: { newChat: vi.fn(), setScope: vi.fn() },
  status: undefined as unknown as Record<string, unknown>,
  releases: [] as unknown[],
  chatProps: undefined as unknown as { starters: string[]; sessionKey: string },
  refetch: vi.fn(),
}));

vi.mock('../../src/assistant/chatStore', () => ({
  useChatSession: () => mocks.session.current,
  chatActions: mocks.actions,
}));
vi.mock('../../src/api/assistant', () => ({ useAssistantStatus: () => mocks.status }));
vi.mock('../../src/api/releases', () => ({ useReleases: () => ({ data: { releases: mocks.releases } }) }));
vi.mock('../../src/assistant/FreshnessLine', () => ({ FreshnessLine: () => <p>freshness</p> }));
vi.mock('../../src/assistant/AssistantChat', () => ({
  AssistantChat: (props: { starters: string[]; sessionKey: string; scopeRow: ReactNode }) => {
    mocks.chatProps = props;
    return (
      <div>
        <textarea aria-label="Ask a question" />
        {props.scopeRow}
      </div>
    );
  },
}));

function session(overrides: Partial<Session> = {}): Session {
  return { scope: null, turns: [], banner: null, inFlight: null, ...overrides };
}

const TURN = { id: 't1' } as unknown as Session['turns'][number];

let container: HTMLDivElement;
let root: Root;

beforeAll(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
});

beforeEach(() => {
  Object.values(mocks.actions).forEach((fn) => fn.mockReset());
  mocks.refetch.mockReset();
  mocks.session = { current: session() };
  mocks.status = { isPending: false, isError: false, data: { enabled: true, starters: STARTERS }, refetch: mocks.refetch };
  mocks.releases = [
    { id: 'r1', name: 'Sprint 41', version: '1.0', createdAt: '2026-07-01T00:00:00Z' },
    { id: 'r2', name: 'Sprint 42', version: '1.1', createdAt: '2026-08-01T00:00:00Z' },
  ];
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

function render() {
  act(() => root.render(<AssistantRoute />));
}

function select(): HTMLSelectElement {
  return container.querySelector('select[aria-label="Scope to a release"]') as HTMLSelectElement;
}

function button(name: string): HTMLButtonElement {
  return Array.from(container.querySelectorAll('button')).find((b) => b.textContent === name) as HTMLButtonElement;
}

describe('AssistantRoute', () => {
  it('renders the header, freshness line and an All releases first picker, newest release first', () => {
    render();
    expect(container.querySelector('h2')?.textContent).toBe('Assistant');
    expect(container.textContent).toContain('freshness');
    expect(button('New chat').disabled).toBe(true);
    expect(Array.from(select().options).map((o) => o.textContent)).toEqual(['All releases', 'Sprint 42 1.1', 'Sprint 41 1.0']);
    expect(mocks.chatProps.sessionKey).toBe('page');
    expect(mocks.chatProps.starters).toEqual(STARTERS.unscoped);
  });

  it('picking a release scopes the page chat', () => {
    render();
    act(() => {
      select().value = 'r2';
      select().dispatchEvent(new Event('change', { bubbles: true }));
    });
    expect(mocks.actions.setScope).toHaveBeenCalledWith('page', { releaseId: 'r2', label: 'Sprint 42 1.1' });
  });

  it('shows a removable scope chip and the release starters when scoped', () => {
    mocks.session = { current: session({ scope: { releaseId: 'r2', label: 'Sprint 42 1.1' } }) };
    render();
    expect(select()).toBeNull();
    expect(container.textContent).toContain('Release: Sprint 42 1.1');
    expect(mocks.chatProps.starters).toEqual(STARTERS.release);
    act(() => container.querySelector<HTMLButtonElement>('button[aria-label^="Remove release scope"]')!.click());
    expect(mocks.actions.setScope).toHaveBeenCalledWith('page', null);
  });

  it('shows the scope-change announcement inline and in the live region', () => {
    mocks.actions.setScope.mockReturnValue('Started a new chat for all releases.');
    mocks.session = { current: session({ scope: { releaseId: 'r2', label: 'Sprint 42 1.1' } }) };
    render();
    act(() => container.querySelector<HTMLButtonElement>('button[aria-label^="Remove release scope"]')!.click());
    expect(container.querySelector('[aria-live="polite"]')?.textContent).toBe('Started a new chat for all releases.');
    expect(container.textContent?.match(/Started a new chat for all releases\./g)).toHaveLength(2);
  });

  it('New chat clears the transcript, announces it and focuses the composer', () => {
    mocks.session = { current: session({ turns: [TURN] }) };
    render();
    act(() => button('New chat').click());
    expect(mocks.actions.newChat).toHaveBeenCalledWith('page');
    expect(container.querySelector('[aria-live="polite"]')?.textContent).toBe('Chat cleared.');
    expect(document.activeElement).toBe(container.querySelector('textarea'));
  });

  it('shows a loading indicator while the status loads', () => {
    mocks.status = { isPending: true, isError: false, data: undefined, refetch: mocks.refetch };
    render();
    expect(container.textContent).toContain('Loading assistant…');
  });

  it('shows a retryable alert when the status fails to load', () => {
    mocks.status = { isPending: false, isError: true, error: new Error('x'), data: undefined, refetch: mocks.refetch };
    render();
    expect(container.querySelector('[role="alert"]')?.textContent).toContain("Couldn't load the assistant");
    act(() => button('Try again').click());
    expect(mocks.refetch).toHaveBeenCalled();
  });

  it('renders nothing when the assistant is disabled', () => {
    mocks.status = { isPending: false, isError: false, data: { enabled: false }, refetch: mocks.refetch };
    render();
    expect(container.innerHTML).toBe('');
  });

  it('keeps the same session turns across unmount and remount', () => {
    mocks.session = { current: session({ turns: [TURN] }) };
    render();
    act(() => root.unmount());
    root = createRoot(container);
    render();
    expect(button('New chat').disabled).toBe(false);
  });
});
