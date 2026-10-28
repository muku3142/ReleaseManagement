import { StrictMode, act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginRoute } from '../../src/routes/LoginRoute';
import { PRODUCT_NAME, PRODUCT_TAGLINE } from '../../src/components/brand/brand';

const SITE = { baseUrl: 'https://acme.atlassian.net', updatedAt: '2026-01-01T00:00:00Z', updatedBy: 'admin' };
const SESSION = { accountId: 'a-1', email: 'rm@acme.com', absoluteExpiry: '2026-12-31T00:00:00Z' };

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

type Stub = { session?: Response; site?: Response; login?: Response };

let container: HTMLDivElement;
let root: Root;
let assign: ReturnType<typeof vi.fn>;
let replace: ReturnType<typeof vi.fn>;
const originalLocation = window.location;

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function stubFetch(stub: Stub) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    if (url === '/api/auth/session') return stub.session ?? json(401, { status: 401, reason: 'UNAUTHENTICATED' });
    if (url === '/api/auth/refresh') return json(401, { status: 401, reason: 'UNAUTHENTICATED' });
    if (url === '/api/settings/jira-site') return stub.site ?? json(200, SITE);
    if (url === '/api/auth/login') return stub.login ?? json(200, SESSION);
    return json(404, { status: 404, reason: 'UNKNOWN' });
  });
}

function stubLocation(search: string) {
  assign = vi.fn();
  replace = vi.fn();
  Object.defineProperty(window, 'location', {
    configurable: true,
    value: { ...originalLocation, search, assign, replace },
  });
}

async function renderRoute() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <LoginRoute />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
}

function setValue(id: string, value: string) {
  const input = container.querySelector<HTMLInputElement>(`#${id}`)!;
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
  return act(async () => {
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

async function submit() {
  await act(async () => {
    container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
  await flush();
}

describe('LoginRoute', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    stubLocation('');
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    Object.defineProperty(window, 'location', { configurable: true, value: originalLocation });
    vi.restoreAllMocks();
  });

  it('renders the brand lockup, a single "Sign in" heading and no navigation', async () => {
    stubFetch({});
    await renderRoute();

    expect(container.textContent).toContain(PRODUCT_NAME);
    expect(container.textContent).toContain(PRODUCT_TAGLINE);
    const headings = container.querySelectorAll('h1');
    expect(headings).toHaveLength(1);
    expect(headings[0].textContent).toBe('Sign in');
    expect(container.querySelector('nav')).toBeNull();
  });

  it('connects the lockup mark in once', async () => {
    stubFetch({});
    await renderRoute();

    expect(container.querySelectorAll('svg .motion-safe\\:animate-mark-link').length).toBeGreaterThan(0);
  });

  it('names the configured Jira site with the URL emphasised', async () => {
    stubFetch({});
    await renderRoute();

    expect(container.textContent).toContain('Signing in to https://acme.atlassian.net.');
    const url = [...container.querySelectorAll('span')].find((s) => s.textContent === SITE.baseUrl);
    expect(url?.className).toContain('font-semibold');
  });

  it('hides the site line while the site query is pending', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) =>
      String(input) === '/api/settings/jira-site'
        ? new Promise<Response>(() => {})
        : Promise.resolve(json(401, { status: 401, reason: 'UNAUTHENTICATED' })),
    );
    await renderRoute();

    expect(container.textContent).not.toContain('Signing in to');
  });

  it('keeps the field attributes, labels and the token help link', async () => {
    stubFetch({});
    await renderRoute();

    const email = container.querySelector<HTMLInputElement>('#email')!;
    expect(email.name).toBe('email');
    expect(email.type).toBe('email');
    expect(email.required).toBe(true);
    expect(email.getAttribute('autocomplete')).toBe('username');
    const token = container.querySelector<HTMLInputElement>('#apiToken')!;
    expect(token.name).toBe('apiToken');
    expect(token.type).toBe('password');
    expect(token.required).toBe(true);
    expect(token.getAttribute('autocomplete')).toBe('off');
    expect(container.querySelector('label[for="email"]')?.textContent).toBe('Jira email');
    expect(container.querySelector('label[for="apiToken"]')?.textContent).toBe('Jira API token');

    expect(container.textContent).toContain('Need a token?');
    const link = [...container.querySelectorAll('a')].find((a) => a.textContent === "Create one on Atlassian's site")!;
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toBe('noreferrer');
  });

  it('has a single default-variant submit button that shows progress while pending', async () => {
    stubFetch({});
    vi.mocked(globalThis.fetch).mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/auth/login') return new Promise<Response>(() => {});
      if (url === '/api/settings/jira-site') return json(200, SITE);
      return json(401, { status: 401, reason: 'UNAUTHENTICATED' });
    });
    await renderRoute();

    const buttons = container.querySelectorAll('button');
    expect(buttons).toHaveLength(1);
    expect(buttons[0].getAttribute('data-variant')).toBe('default');
    expect(buttons[0].textContent).toBe('Sign in');

    await setValue('email', 'rm@acme.com');
    await setValue('apiToken', 'token');
    await submit();

    expect(buttons[0].disabled).toBe(true);
    expect(buttons[0].textContent).toBe('Signing in…');
  });

  it('focuses a destructive alert on a rejected login and keeps the field values', async () => {
    stubFetch({ login: json(401, { status: 401, reason: 'INVALID_CREDENTIALS' }) });
    await renderRoute();

    await setValue('email', 'rm@acme.com');
    await setValue('apiToken', 'bad');
    await submit();

    const alert = container.querySelector<HTMLElement>('[role="alert"][data-reason="INVALID_CREDENTIALS"]')!;
    expect(alert.textContent).toContain('Your Jira credential is no longer valid.');
    expect(document.activeElement).toBe(alert);
    expect(container.querySelector<HTMLInputElement>('#email')!.value).toBe('rm@acme.com');
    expect(container.querySelector<HTMLInputElement>('#apiToken')!.value).toBe('bad');
    expect(assign).not.toHaveBeenCalled();
  });

  it('goes to the safe redirect target after signing in', async () => {
    stubLocation('?redirect=%2Freleases%2F42');
    stubFetch({});
    await renderRoute();

    await setValue('email', 'rm@acme.com');
    await setValue('apiToken', 'token');
    await submit();

    expect(assign).toHaveBeenCalledWith('/releases/42');
  });

  it('explains RM_JIRA_DEFAULT_BASE_URL instead of the form when no site is configured', async () => {
    stubFetch({ site: json(404, { status: 404, reason: 'SITE_NOT_CONFIGURED' }) });
    await renderRoute();

    expect(container.querySelector('form')).toBeNull();
    const alert = container.querySelector('[role="alert"][data-reason="SITE_NOT_CONFIGURED"]')!;
    expect(alert.textContent).toContain('No Jira site is set up yet');
    expect(alert.textContent).toContain(
      `${PRODUCT_NAME} takes its Jira Cloud site from the RM_JIRA_DEFAULT_BASE_URL setting the first time it starts.`,
    );
    expect(container.querySelector('a[href="/settings"]')).toBeNull();
  });

  it('sends a signed-in visitor on to the safe redirect target', async () => {
    stubLocation('?redirect=%2Freleases');
    stubFetch({ session: json(200, SESSION) });
    await renderRoute();

    expect(replace).toHaveBeenCalledWith('/releases');
  });

  it('falls back to the safe default for an external redirect', async () => {
    stubLocation('?redirect=https%3A%2F%2Fevil.example');
    stubFetch({ session: json(200, SESSION) });
    await renderRoute();

    expect(replace).toHaveBeenCalledWith('/');
  });
});
