import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiFetch } from '../../src/api/client';
import { messageForReason } from '../../src/api/auth';
import {
  MANUAL_ITEM_CATEGORIES,
  MANUAL_ITEM_CATEGORY_LABELS,
  releaseQueryKey,
  releaseSearchPath,
  releasesQueryKey,
  type Release,
  type ReleaseSearchResponse,
} from '../../src/api/releases';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('releases API client', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('releaseSearchPath returns the base path for an empty query', () => {
    expect(releaseSearchPath('')).toBe('/api/releases');
  });

  it('releaseSearchPath encodes a space in the query', () => {
    expect(releaseSearchPath('sprint 42')).toBe('/api/releases?query=sprint%2042');
  });

  it('releaseSearchPath percent-encodes & and #', () => {
    expect(releaseSearchPath('a&b#c')).toBe('/api/releases?query=a%26b%23c');
  });

  it('releasesQueryKey and releaseQueryKey are stable tuples', () => {
    expect(releasesQueryKey('x')).toEqual(['releases', 'x']);
    expect(releaseQueryKey('id')).toEqual(['release', 'id']);
  });

  it('MANUAL_ITEM_CATEGORIES is the fixed four-value enum with a label for each', () => {
    expect(MANUAL_ITEM_CATEGORIES).toEqual([
      'CONFIG_CHANGE',
      'DATABASE_SCRIPT',
      'INFRASTRUCTURE',
      'OTHER',
    ]);
    for (const category of MANUAL_ITEM_CATEGORIES) {
      expect(MANUAL_ITEM_CATEGORY_LABELS[category]).toBeTruthy();
    }
  });

  it('resolves a mocked 200 on /api/releases to { releases: [...] }', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(200, { releases: [{ id: '1', name: 'R1', version: '1.0' }] }),
    );

    const result = await apiFetch<ReleaseSearchResponse>(releaseSearchPath(''));

    expect(result.releases).toHaveLength(1);
  });

  it('rejects with an ApiError whose problem.reason is RELEASE_NOT_FOUND on a mocked 404', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(404, { status: 404, reason: 'RELEASE_NOT_FOUND' }),
    );

    await expect(apiFetch<Release>('/api/releases/does-not-exist')).rejects.toSatisfy(
      (error) => error instanceof ApiError && error.problem.reason === 'RELEASE_NOT_FOUND',
    );
  });

  it('messageForReason resolves RELEASE_NOT_FOUND to its contracted copy', () => {
    expect(messageForReason({ status: 404, reason: 'RELEASE_NOT_FOUND' })).toBe(
      "This release doesn't exist or has been deleted.",
    );
  });

  it('messageForReason resolves RELEASE_FROZEN to its contracted copy', () => {
    expect(messageForReason({ status: 409, reason: 'RELEASE_FROZEN' })).toBe(
      'This release is frozen and can no longer be changed.',
    );
  });

  it('messageForReason resolves RELEASE_DUPLICATE to its contracted copy', () => {
    expect(messageForReason({ status: 409, reason: 'RELEASE_DUPLICATE' })).toBe(
      'A release with this name and version already exists. Choose a different name or version.',
    );
  });

  it('treats an empty releases list as a success, not an error', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, { releases: [] }));

    const result = await apiFetch<ReleaseSearchResponse>(releaseSearchPath(''));

    expect(result.releases).toEqual([]);
  });
});
