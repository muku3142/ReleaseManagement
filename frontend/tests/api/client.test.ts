import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, createElement } from 'react';
import { createRoot } from 'react-dom/client';
import { apiFetch, ApiError } from '../../src/api/client';
import { clearCredentialInvalid, useCredentialInvalid } from '../../src/api/credentialStatus';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function readCredentialInvalid(): boolean {
  const container = document.createElement('div');
  let value = false;
  function Probe() {
    value = useCredentialInvalid();
    return null;
  }
  const root = createRoot(container);
  act(() => {
    root.render(createElement(Probe));
  });
  root.unmount();
  return value;
}

describe('apiFetch', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=; Max-Age=0';
  });

  afterEach(() => {
    vi.restoreAllMocks();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0';
    clearCredentialInvalid();
  });

  it('sends X-XSRF-TOKEN on a non-GET request when the cookie is set', async () => {
    document.cookie = 'XSRF-TOKEN=abc123';
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, { ok: true }));

    await apiFetch('/api/settings/jira-site', { method: 'PUT', body: JSON.stringify({}) });

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get('X-XSRF-TOKEN')).toBe('abc123');
  });

  it('does not send X-XSRF-TOKEN on a GET request', async () => {
    document.cookie = 'XSRF-TOKEN=abc123';
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, { ok: true }));

    await apiFetch('/api/settings/jira-site');

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get('X-XSRF-TOKEN')).toBeNull();
  });

  it('rejects with an ApiError carrying problem.reason on a 400 problem+json response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(400, { status: 400, reason: 'VALIDATION_FAILED', detail: 'bad baseUrl' }),
    );

    await expect(apiFetch('/api/settings/jira-site', { method: 'PUT', body: '{}' })).rejects.toSatisfy(
      (error: unknown) => error instanceof ApiError && error.problem.reason === 'VALIDATION_FAILED',
    );
  });

  it('resolves to undefined on a 204 response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }));

    const result = await apiFetch('/api/settings/jira-site', { method: 'DELETE' });

    expect(result).toBeUndefined();
  });

  it('retries exactly once after a single-flight silent refresh on a session-reason 401', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(401, { status: 401, reason: 'SESSION_EXPIRED' }))
      .mockResolvedValueOnce(jsonResponse(200, {}))
      .mockResolvedValueOnce(jsonResponse(200, { accountId: 'a' }));

    const result = await apiFetch('/api/auth/session');

    expect(result).toEqual({ accountId: 'a' });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/session');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/auth/refresh');
    expect(fetchMock.mock.calls[2][0]).toBe('/api/auth/session');
  });

  it('rethrows without retrying when the refresh itself 401s', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(401, { status: 401, reason: 'SESSION_EXPIRED' }))
      .mockResolvedValueOnce(jsonResponse(401, { status: 401, reason: 'INVALID_SESSION' }));

    await expect(apiFetch('/api/auth/session')).rejects.toSatisfy(
      (error: unknown) => error instanceof ApiError && error.problem.reason === 'SESSION_EXPIRED',
    );
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('single-flights concurrent session-reason 401s into exactly one refresh call', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(401, { status: 401, reason: 'SESSION_EXPIRED' }))
      .mockResolvedValueOnce(jsonResponse(401, { status: 401, reason: 'SESSION_EXPIRED' }))
      .mockResolvedValueOnce(jsonResponse(200, {}))
      .mockResolvedValueOnce(jsonResponse(200, { a: 1 }))
      .mockResolvedValueOnce(jsonResponse(200, { a: 2 }));

    await Promise.all([apiFetch('/api/one'), apiFetch('/api/two')]);

    const refreshCalls = fetchMock.mock.calls.filter((call) => call[0] === '/api/auth/refresh');
    expect(refreshCalls).toHaveLength(1);
  });

  it('marks the credential invalid and does not refresh on INVALID_CREDENTIALS', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse(401, { status: 401, reason: 'INVALID_CREDENTIALS' }));

    await expect(apiFetch('/api/auth/login', { method: 'POST' })).rejects.toBeInstanceOf(ApiError);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(readCredentialInvalid()).toBe(true);
  });

  it('does not recurse when the refresh request itself fails', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse(401, { status: 401, reason: 'INVALID_SESSION' }));

    await expect(apiFetch('/api/auth/refresh', { method: 'POST' })).rejects.toBeInstanceOf(ApiError);

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
