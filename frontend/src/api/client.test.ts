import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, ApiError } from './client';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('apiFetch', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=; Max-Age=0';
  });

  afterEach(() => {
    vi.restoreAllMocks();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0';
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
});
