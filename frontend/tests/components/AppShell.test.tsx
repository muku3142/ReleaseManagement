import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppShell } from '../../src/components/AppShell';

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children?: ReactNode; to: string }) => <a href={to}>{children}</a>,
  Outlet: () => null,
}));
vi.mock('../../src/components/AdoCredentialBanner', () => ({ AdoCredentialBanner: () => null }));
vi.mock('../../src/components/CredentialAlert', () => ({ CredentialAlert: () => null }));

type Reply = { status: number; body?: unknown } | 'pending';

const SESSION = { accountId: 'a1', email: 'rm@example.com', absoluteExpiry: '2099-01-01T00:00:00Z' };
const STARTERS = { unscoped: [], release: [] };

let container: HTMLDivElement;
let root: Root;
let fetchSpy: ReturnType<typeof vi.fn>;

beforeAll(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
});

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

async function renderShell(replies: { session: Reply; status: Reply }) {
  fetchSpy = vi.fn((path: string) => {
    const reply = path === '/api/auth/session' ? replies.session : replies.status;
    if (reply === 'pending') return new Promise<Response>(() => {});
    return Promise.resolve(new Response(JSON.stringify(reply.body ?? {}), { status: reply.status }));
  });
  vi.stubGlobal('fetch', fetchSpy);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <AppShell />
      </QueryClientProvider>,
    );
  });
  if (replies.session !== 'pending' && replies.session.status === 200) {
    await vi.waitFor(() => expect(fetchSpy.mock.calls.map(([path]) => path)).toContain('/api/assistant/status'));
    await act(async () => new Promise((resolve) => setTimeout(resolve, 20)));
  }
}

function navLabels(): string[] {
  return Array.from(container.querySelectorAll('nav a')).map((a) => a.textContent ?? '');
}

describe('AppShell Assistant nav item', () => {
  it('shows Assistant between Releases and Templates when enabled', async () => {
    await renderShell({
      session: { status: 200, body: SESSION },
      status: { status: 200, body: { enabled: true, starters: STARTERS } },
    });
    await vi.waitFor(() => expect(navLabels()).toContain('Assistant'));
    const labels = navLabels();
    expect(labels.indexOf('Portfolio')).toBe(labels.indexOf('Releases') + 1);
    expect(labels.indexOf('Roadmap')).toBe(labels.indexOf('Portfolio') + 1);
    expect(labels.indexOf('Briefings')).toBe(labels.indexOf('Roadmap') + 1);
    expect(labels.indexOf('Assistant')).toBe(labels.indexOf('Briefings') + 1);
    expect(labels.indexOf('Templates')).toBe(labels.indexOf('Assistant') + 1);
  });

  it('links Roadmap to /roadmap straight after Portfolio, with the assistant off', async () => {
    await renderShell({ session: { status: 200, body: SESSION }, status: { status: 200, body: { enabled: false } } });
    const labels = navLabels();
    expect(labels.indexOf('Roadmap')).toBe(labels.indexOf('Portfolio') + 1);
    const link = Array.from(container.querySelectorAll('nav a')).find((a) => a.textContent === 'Roadmap');
    expect(link?.getAttribute('href')).toBe('/roadmap');
  });

  it('F5: links Briefings to /briefings straight after Roadmap', async () => {
    await renderShell({ session: { status: 200, body: SESSION }, status: { status: 200, body: { enabled: false } } });
    const labels = navLabels();
    expect(labels.indexOf('Briefings')).toBe(labels.indexOf('Roadmap') + 1);
    const link = Array.from(container.querySelectorAll('nav a')).find((a) => a.textContent === 'Briefings');
    expect(link?.getAttribute('href')).toBe('/briefings');
  });

  it('hides Assistant when disabled', async () => {
    await renderShell({ session: { status: 200, body: SESSION }, status: { status: 200, body: { enabled: false } } });
    expect(navLabels()).not.toContain('Assistant');
  });

  it('hides Assistant while the status is loading', async () => {
    await renderShell({ session: { status: 200, body: SESSION }, status: 'pending' });
    expect(navLabels()).not.toContain('Assistant');
  });

  it('hides Assistant when the status request fails', async () => {
    await renderShell({ session: { status: 200, body: SESSION }, status: { status: 500, body: { reason: 'UNKNOWN' } } });
    expect(navLabels()).not.toContain('Assistant');
  });

  it('hides Assistant and does not fetch the status when signed out', async () => {
    await renderShell({ session: { status: 401, body: { reason: 'SESSION_EXPIRED' } }, status: { status: 200, body: { enabled: true, starters: STARTERS } } });
    expect(navLabels()).not.toContain('Assistant');
    expect(fetchSpy.mock.calls.map(([path]) => path)).not.toContain('/api/assistant/status');
  });
});
