import { describe, expect, it, vi, afterEach } from 'vitest';
import { apiFetch, ApiError } from '../../src/api/client';
import { messageForReason } from '../../src/api/reasonMessages';

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
      'NO_TEMPLATE',
      'TEMPLATE_TYPE_MISMATCH',
      'CHOSEN_TEMPLATE_NOT_FOUND',
      'SOMETHING_UNKNOWN',
    ];
    const messages = reasons.map((reason) => messageForReason({ status: 401, reason }));
    expect(new Set(messages).size).toBe(messages.length);
  });
});

describe('messageForReason unfreeze and promotion copy', () => {
  it.each([
    ['UNFREEZE_HAS_PROMOTIONS', "Can't unfreeze: this release has promotion history. Create a new release to change scope."],
    ['UNFREEZE_HAS_INSTALLS', "Can't unfreeze: client installs are recorded for this release. Create a new release to change scope."],
    ['UNFREEZE_RELEASE_NOT_FROZEN', 'This release is already open.'],
    [
      'PROMOTION_STAGE_EARLIER',
      "Can't promote to an earlier stage. Choose the current stage or a later one; to go back, record a rollback on the current stage.",
    ],
  ])('maps %s to its UI copy', (reason, copy) => {
    expect(messageForReason({ status: 409, reason })).toBe(copy);
  });

  it('no longer knows PROMOTION_STAGE_SKIPPED and falls back to the generic message', () => {
    expect(messageForReason({ status: 409, reason: 'PROMOTION_STAGE_SKIPPED' })).toBe(
      messageForReason({ status: 409, reason: 'SOMETHING_UNKNOWN' }),
    );
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
