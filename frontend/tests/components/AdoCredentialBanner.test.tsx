import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdoCredentialBanner } from '../../src/components/AdoCredentialBanner';

vi.mock('@tanstack/react-router', () => ({
  Link: (props: { children?: ReactNode; to?: string }) => <a href={props.to}>{props.children}</a>,
}));

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

let container: HTMLDivElement;
let root: Root;

async function renderBanner(body: unknown) {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(body));
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <AdoCredentialBanner />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

const configured = (state: string) => ({
  configured: true,
  organization: 'acme',
  expiresOn: '2026-12-01',
  state,
  updatedAt: '2026-10-01T00:00:00Z',
  updatedBy: 'rm@acme.com',
});

describe('AdoCredentialBanner', () => {
  beforeAll(() => {
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

  it('renders nothing when the token is OK', async () => {
    await renderBanner(configured('OK'));
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });

  it('renders nothing when unconfigured', async () => {
    await renderBanner({ configured: false, organization: null, expiresOn: null, state: null });
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });

  it('warns when the token is expiring and links to settings', async () => {
    await renderBanner(configured('EXPIRING'));
    const alert = container.querySelector('[role="alert"]');
    expect(alert?.getAttribute('data-reason')).toBe('ADO_CREDENTIAL_EXPIRING');
    expect(alert?.textContent).toContain('expires on');
    expect(alert?.querySelector('a')?.getAttribute('href')).toBe('/settings');
  });

  it('states the fallback when the token has expired', async () => {
    await renderBanner(configured('EXPIRED'));
    const alert = container.querySelector('[role="alert"]');
    expect(alert?.getAttribute('data-reason')).toBe('ADO_CREDENTIAL_EXPIRED');
    expect(alert?.textContent).toContain('expired on');
    expect(alert?.querySelector('a')?.getAttribute('href')).toBe('/settings');
  });
});
