import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiFetch } from '../../src/api/client';
import { messageForReason } from '../../src/api/auth';
import * as releasesModule from '../../src/api/releases';
import {
  MANUAL_ITEM_CATEGORIES,
  MANUAL_ITEM_CATEGORY_LABELS,
  openReleases,
  releaseQueryKey,
  releaseSearchPath,
  releasesQueryKey,
  type Release,
  type ReleaseAddResult,
  type ReleaseSearchResponse,
  type ReleaseSummary,
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

  function summary(overrides: Partial<ReleaseSummary>): ReleaseSummary {
    return {
      id: '1',
      name: 'R1',
      version: '1.0',
      status: 'OPEN',
      jiraProjectKey: null,
      createdAt: '2026-08-05T00:00:00Z',
      createdBy: 'user@example.com',
      frozenAt: null,
      frozenBy: null,
      lastRefreshedAt: null,
      itemCount: 0,
      ...overrides,
    };
  }

  it('openReleases keeps only OPEN entries and preserves their incoming order', () => {
    const open1 = summary({ id: '1', status: 'OPEN' });
    const frozen = summary({ id: '2', status: 'FROZEN' });
    const open2 = summary({ id: '3', status: 'OPEN' });

    expect(openReleases([open1, frozen, open2])).toEqual([open1, open2]);
  });

  it('resolves a mocked 201 on POST /api/releases to { release, added, alreadyPresent }', async () => {
    const release: Release = { ...summary({ id: '1' }), items: [], manualItems: [] };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(201, { release, added: 2, alreadyPresent: 0, notFound: 0 }),
    );

    const result = await apiFetch<ReleaseAddResult>('/api/releases', {
      method: 'POST',
      body: JSON.stringify({ name: 'R1', version: '1.0', ticketKeys: ['CNV-1', 'CNV-2'] }),
    });

    expect(result).toEqual({ release, added: 2, alreadyPresent: 0, notFound: 0 });
  });

  it('rejects with an ApiError whose problem.reason is RELEASE_DUPLICATE on a mocked 409', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(409, { status: 409, reason: 'RELEASE_DUPLICATE' }),
    );

    await expect(
      apiFetch<ReleaseAddResult>('/api/releases', {
        method: 'POST',
        body: JSON.stringify({ name: 'R1', version: '1.0', ticketKeys: [] }),
      }),
    ).rejects.toSatisfy(
      (error) => error instanceof ApiError && error.problem.reason === 'RELEASE_DUPLICATE',
    );
  });

  it('rejects with an ApiError carrying problem.fieldErrors.name on a mocked 400', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(400, {
        status: 400,
        reason: 'VALIDATION_FAILED',
        fieldErrors: { name: 'must not be blank' },
      }),
    );

    await expect(
      apiFetch<ReleaseAddResult>('/api/releases', {
        method: 'POST',
        body: JSON.stringify({ name: '', version: '1.0', ticketKeys: [] }),
      }),
    ).rejects.toSatisfy(
      (error) =>
        error instanceof ApiError && error.problem.fieldErrors?.name === 'must not be blank',
    );
  });

  it('resolves a mocked 200 on POST /api/releases/{id}/items with every count, including the all-duplicate case as a success', async () => {
    const release: Release = { ...summary({ id: '1' }), items: [], manualItems: [] };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(200, { release, added: 0, alreadyPresent: 3, notFound: 0 }),
    );

    const result = await apiFetch<ReleaseAddResult>('/api/releases/1/items', {
      method: 'POST',
      body: JSON.stringify({ ticketKeys: ['CNV-1', 'CNV-2', 'CNV-3'] }),
    });

    expect(result).toEqual({ release, added: 0, alreadyPresent: 3, notFound: 0 });
  });

  it('rejects with an ApiError whose problem.reason is RELEASE_FROZEN on the items endpoint', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(409, { status: 409, reason: 'RELEASE_FROZEN' }),
    );

    await expect(
      apiFetch<ReleaseAddResult>('/api/releases/1/items', {
        method: 'POST',
        body: JSON.stringify({ ticketKeys: ['CNV-1'] }),
      }),
    ).rejects.toSatisfy(
      (error) => error instanceof ApiError && error.problem.reason === 'RELEASE_FROZEN',
    );
  });

  it('resolves a mocked 200 on POST /api/releases/{id}/refresh to a Release with a non-null lastRefreshedAt', async () => {
    const release: Release = {
      ...summary({ id: '1', lastRefreshedAt: '2026-08-05T12:00:00Z' }),
      items: [],
      manualItems: [],
    };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, release));

    const result = await apiFetch<Release>('/api/releases/1/refresh', { method: 'POST' });

    expect(result.lastRefreshedAt).not.toBeNull();
  });

  it('resolves a mocked 200 on POST /api/releases/{id}/freeze to a Release with status FROZEN', async () => {
    const release: Release = { ...summary({ id: '1', status: 'FROZEN' }), items: [], manualItems: [] };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, release));

    const result = await apiFetch<Release>('/api/releases/1/freeze', { method: 'POST' });

    expect(result.status).toBe('FROZEN');
  });

  it('rejects with an ApiError whose problem.reason is RELEASE_FROZEN on a mocked 409 freeze', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(409, { status: 409, reason: 'RELEASE_FROZEN' }),
    );

    await expect(apiFetch<Release>('/api/releases/1/freeze', { method: 'POST' })).rejects.toSatisfy(
      (error) => error instanceof ApiError && error.problem.reason === 'RELEASE_FROZEN',
    );
  });

  it('resolves a mocked 204 on DELETE /api/releases/{id} to undefined rather than throwing', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }));

    const result = await apiFetch<void>('/api/releases/1', { method: 'DELETE' });

    expect(result).toBeUndefined();
  });

  it('rejects with an ApiError whose problem.reason is RELEASE_FROZEN on a mocked 409 delete', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(409, { status: 409, reason: 'RELEASE_FROZEN' }),
    );

    await expect(apiFetch<void>('/api/releases/1', { method: 'DELETE' })).rejects.toSatisfy(
      (error) => error instanceof ApiError && error.problem.reason === 'RELEASE_FROZEN',
    );
  });

  it('resolves a mocked 200 on DELETE /api/releases/{id}/items/CNV-1 to a Release', async () => {
    const release: Release = { ...summary({ id: '1' }), items: [], manualItems: [] };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, release));

    const result = await apiFetch<Release>('/api/releases/1/items/CNV-1', { method: 'DELETE' });

    expect(result).toEqual(release);
  });

  it('resolves a mocked 201 on POST /api/releases/{id}/manual-items to a Release whose manualItems[0].category is DATABASE_SCRIPT', async () => {
    const release: Release = {
      ...summary({ id: '1' }),
      items: [],
      manualItems: [
        {
          id: 'm1',
          title: 'Run migration',
          description: 'Run the migration script',
          category: 'DATABASE_SCRIPT',
          addedAt: '2026-08-05T00:00:00Z',
        },
      ],
    };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(201, release));

    const result = await apiFetch<Release>('/api/releases/1/manual-items', {
      method: 'POST',
      body: JSON.stringify({ title: 'Run migration', description: 'Run the migration script', category: 'DATABASE_SCRIPT' }),
    });

    expect(result.manualItems[0].category).toBe('DATABASE_SCRIPT');
  });

  it('rejects with an ApiError carrying problem.fieldErrors.title on a mocked 400 on the manual-items endpoint', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(400, {
        status: 400,
        reason: 'VALIDATION_FAILED',
        fieldErrors: { title: 'must not be blank' },
      }),
    );

    await expect(
      apiFetch<Release>('/api/releases/1/manual-items', {
        method: 'POST',
        body: JSON.stringify({ title: '', description: '', category: 'OTHER' }),
      }),
    ).rejects.toSatisfy(
      (error) => error instanceof ApiError && error.problem.fieldErrors?.title === 'must not be blank',
    );
  });

  it('exports no hook whose name contains unfreeze (D-03-10)', () => {
    expect(Object.keys(releasesModule).some((k) => /unfreeze/i.test(k))).toBe(false);
  });
});
