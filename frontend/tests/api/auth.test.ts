import { describe, expect, it, vi, afterEach } from 'vitest';
import { apiFetch, ApiError } from '../../src/api/client';
import { messageForReason } from '../../src/api/auth';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('messageForReason', () => {
  it('returns pairwise-distinct messages for every known reason', () => {
    const reasons = [
      'INVALID_CREDENTIALS',
      'SESSION_EXPIRED',
      'JIRA_UNREACHABLE',
      'JIRA_RATE_LIMITED',
      'SITE_NOT_CONFIGURED',
      'ACCOUNT_MISMATCH',
      'RESULT_SET_TOO_LARGE',
      'SOMETHING_UNKNOWN',
    ];
    const messages = reasons.map((reason) => messageForReason({ status: 401, reason }));
    expect(new Set(messages).size).toBe(messages.length);
  });
});

describe('apiFetch session semantics', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("useSession's query function returns null (not a throw) on a 401 and rethrows on a 500", async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(401, { status: 401, reason: 'UNAUTHENTICATED' }))
      .mockResolvedValueOnce(jsonResponse(401, { status: 401, reason: 'UNAUTHENTICATED' }));

    async function sessionOrNull() {
      try {
        return await apiFetch('/api/auth/session');
      } catch (error) {
        if (error instanceof ApiError && error.problem.status === 401) return null;
        throw error;
      }
    }

    await expect(sessionOrNull()).resolves.toBeNull();

    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(500, { status: 500, reason: 'INTERNAL_ERROR' }));

    await expect(sessionOrNull()).rejects.toBeInstanceOf(ApiError);
  });
});
