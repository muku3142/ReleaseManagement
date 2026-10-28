import { act, createElement, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiFetch } from '../../src/api/client';
import { messageForReason } from '../../src/api/reasonMessages';
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
      products: [],
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
    const release: Release = { ...summary({ id: '1' }), items: [], manualItems: [], unfreezeBlockedReason: null, packagesUrl: null };
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
    const release: Release = { ...summary({ id: '1' }), items: [], manualItems: [], unfreezeBlockedReason: null, packagesUrl: null };
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
      unfreezeBlockedReason: null,

      packagesUrl: null,
    };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, release));

    const result = await apiFetch<Release>('/api/releases/1/refresh', { method: 'POST' });

    expect(result.lastRefreshedAt).not.toBeNull();
  });

  it('resolves a mocked 200 on POST /api/releases/{id}/freeze to a Release with status FROZEN', async () => {
    const release: Release = { ...summary({ id: '1', status: 'FROZEN' }), items: [], manualItems: [], unfreezeBlockedReason: null, packagesUrl: null };
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
    const release: Release = { ...summary({ id: '1' }), items: [], manualItems: [], unfreezeBlockedReason: null, packagesUrl: null };
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
      unfreezeBlockedReason: null,

      packagesUrl: null,
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

  it('useSetPackagesUrl PUTs the url, sets the release cache and invalidates nothing else', async () => {
    const url = 'https://contoso.sharepoint.com/packages';
    const release: Release = {
      ...summary({ id: 'r1' }),
      items: [],
      manualItems: [],
      unfreezeBlockedReason: null,
      packagesUrl: url,
    };
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, release));
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    const queryClient = new QueryClient();
    queryClient.setQueryData(['documents', 'r1'], {});
    queryClient.setQueryData(['dashboard', 'releases'], {});
    const root = createRoot(document.createElement('div'));
    function Probe() {
      const mutation = releasesModule.useSetPackagesUrl('r1');
      useEffect(() => mutation.mutate(url), []); // eslint-disable-line react-hooks/exhaustive-deps
      return null;
    }
    await act(async () => {
      root.render(createElement(QueryClientProvider, { client: queryClient }, createElement(Probe)));
    });
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    await act(async () => root.unmount());
    const [requestUrl, init] = fetchSpy.mock.calls.at(-1)!;
    expect(String(requestUrl)).toBe('/api/releases/r1/packages-url');
    expect(init?.method).toBe('PUT');
    expect(JSON.parse(String(init?.body))).toEqual({ url });
    expect(queryClient.getQueryData(releaseQueryKey('r1'))).toEqual(release);
    expect(queryClient.getQueryState(['documents', 'r1'])?.isInvalidated).toBe(false);
    expect(queryClient.getQueryState(['dashboard', 'releases'])?.isInvalidated).toBe(false);
  });

  it('has a specific message for the Phase 17 reasons', () => {
    expect(messageForReason({ status: 400, reason: 'ROADMAP_ITEM_NOT_FOUND' })).toBe(
      'That roadmap item no longer exists. Reload the page.',
    );
    expect(messageForReason({ status: 400, reason: 'RELEASE_PACKAGES_URL_INVALID' })).toContain('OneDrive or SharePoint');
  });

  it('useCreateRelease posts exactly { name, version, ticketKeys, productId }', async () => {
    const release: Release = { ...summary({ id: 'r1' }), items: [], manualItems: [], unfreezeBlockedReason: null, packagesUrl: null };
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse(201, { release, added: 1, alreadyPresent: 0, notFound: 0 }));
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    const root = createRoot(document.createElement('div'));
    function Probe() {
      const mutation = releasesModule.useCreateRelease();
      useEffect(() => mutation.mutate({ name: 'R1', version: '1.0', groups: [{ ticketKeys: ['ABC-1'] }], productId: 'p1' }), []); // eslint-disable-line react-hooks/exhaustive-deps
      return null;
    }
    await act(async () => {
      root.render(createElement(QueryClientProvider, { client: new QueryClient() }, createElement(Probe)));
    });
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    await act(async () => root.unmount());

    const [url, init] = fetchSpy.mock.calls.at(-1)!;
    expect(String(url)).toBe('/api/releases');
    expect(init?.method).toBe('POST');
    expect(JSON.parse(String(init?.body))).toEqual({ name: 'R1', version: '1.0', ticketKeys: ['ABC-1'], productId: 'p1' });
  });

  describe('useRefreshCodeLinks', () => {
    async function runRefresh(vars: { releaseId: string; ticketKey?: string }): Promise<string> {
      const release: Release = { ...summary({ id: 'r1' }), items: [], manualItems: [], unfreezeBlockedReason: null, packagesUrl: null };
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, release));
      (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
      const container = document.createElement('div');
      const root = createRoot(container);
      function Probe() {
        const mutation = releasesModule.useRefreshCodeLinks();
        useEffect(() => mutation.mutate(vars), []); // eslint-disable-line react-hooks/exhaustive-deps
        return null;
      }
      await act(async () => {
        root.render(
          createElement(QueryClientProvider, { client: new QueryClient() }, createElement(Probe)),
        );
      });
      await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 0));
      });
      await act(async () => root.unmount());
      const [url, init] = fetchSpy.mock.calls.at(-1)!;
      expect(init?.method).toBe('POST');
      return String(url);
    }

    it('posts to code-links/refresh without a ticketKey for the whole release', async () => {
      expect(await runRefresh({ releaseId: 'r1' })).toBe('/api/releases/r1/code-links/refresh');
    });

    it('adds the encoded ticketKey for a per-ticket retry', async () => {
      expect(await runRefresh({ releaseId: 'r1', ticketKey: 'EXAMPLE-1' })).toBe(
        '/api/releases/r1/code-links/refresh?ticketKey=EXAMPLE-1',
      );
      expect(await runRefresh({ releaseId: 'r1', ticketKey: 'A B&C' })).toBe(
        '/api/releases/r1/code-links/refresh?ticketKey=A%20B%26C',
      );
    });
  });

  describe('useUnfreezeRelease', () => {
    async function runUnfreeze(response: Response) {
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response);
      (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
      const client = new QueryClient();
      const invalidate = vi.spyOn(client, 'invalidateQueries');
      const container = document.createElement('div');
      const root = createRoot(container);
      function Probe() {
        const mutation = releasesModule.useUnfreezeRelease();
        useEffect(() => mutation.mutate('r1'), []); // eslint-disable-line react-hooks/exhaustive-deps
        return null;
      }
      await act(async () => {
        root.render(createElement(QueryClientProvider, { client }, createElement(Probe)));
      });
      await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 0));
      });
      await act(async () => root.unmount());
      return { client, fetchSpy, invalidatedKeys: invalidate.mock.calls.map(([filters]) => filters?.queryKey) };
    }

    it('posts to /unfreeze, caches the release and refreshes dependent queries on success', async () => {
      const release: Release = { ...summary({ id: 'r1', status: 'OPEN' }), items: [], manualItems: [], unfreezeBlockedReason: null, packagesUrl: null };
      const { client, fetchSpy, invalidatedKeys } = await runUnfreeze(jsonResponse(200, release));

      const [url, init] = fetchSpy.mock.calls.at(-1)!;
      expect(String(url)).toBe('/api/releases/r1/unfreeze');
      expect(init?.method).toBe('POST');
      expect(client.getQueryData(releaseQueryKey('r1'))).toEqual(release);
      expect(invalidatedKeys).toEqual(
        expect.arrayContaining([
          ['releases'],
          ['dashboard'],
          ['promotions', 'r1'],
          ['installs', 'r1'],
          ['documents', 'readiness'],
        ]),
      );
    });

    it('refetches the release on a 409 rejection', async () => {
      const { invalidatedKeys } = await runUnfreeze(
        jsonResponse(409, { status: 409, reason: 'UNFREEZE_HAS_PROMOTIONS' }),
      );

      expect(invalidatedKeys).toEqual([releaseQueryKey('r1')]);
    });
  });

  describe('bundles (D-17.2-07)', () => {
    async function runMutation<TVars>(
      useHook: () => { mutate: (vars: TVars) => void },
      vars: TVars,
      body: unknown,
    ): Promise<{ url: string; init: RequestInit | undefined }> {
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, body));
      (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
      const root = createRoot(document.createElement('div'));
      function Probe() {
        const mutation = useHook();
        useEffect(() => mutation.mutate(vars), []); // eslint-disable-line react-hooks/exhaustive-deps
        return null;
      }
      await act(async () => {
        root.render(createElement(QueryClientProvider, { client: new QueryClient() }, createElement(Probe)));
      });
      await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 0));
      });
      await act(async () => root.unmount());
      const [url, init] = fetchSpy.mock.calls.at(-1)!;
      return { url: String(url), init };
    }

    const release = (): Release => ({
      ...summary({ id: 'r1' }),
      items: [],
      manualItems: [],
      unfreezeBlockedReason: null,
      packagesUrl: null,
    });
    const addResult = () => ({ release: release(), added: 1, alreadyPresent: 0, notFound: 0 });
    const createVars = { name: 'R1', version: '1.0', productId: 'p1' };

    it('useCreateRelease sends the first group with its trimmed bundle', async () => {
      const { init } = await runMutation(
        releasesModule.useCreateRelease,
        { ...createVars, groups: [{ ticketKeys: ['ABC-1'], bundle: ' Payments fixes ' }] },
        addResult(),
      );
      expect(JSON.parse(String(init?.body))).toEqual({ ...createVars, ticketKeys: ['ABC-1'], bundle: 'Payments fixes' });
    });

    it('useCreateRelease omits a blank or missing bundle', async () => {
      for (const bundle of ['', '   ', undefined]) {
        const { init } = await runMutation(
          releasesModule.useCreateRelease,
          { ...createVars, groups: [{ ticketKeys: ['ABC-1'], bundle }] },
          addResult(),
        );
        expect(JSON.parse(String(init?.body))).toEqual({ ...createVars, ticketKeys: ['ABC-1'] });
      }
    });

    it('useAddTicketsToRelease sends { ticketKeys, bundle } with a bundle and exactly { ticketKeys } without', async () => {
      const withBundle = await runMutation(
        releasesModule.useAddTicketsToRelease,
        { releaseId: 'r1', groups: [{ ticketKeys: ['ABC-1'], bundle: 'Audit' }] },
        addResult(),
      );
      expect(withBundle.url).toBe('/api/releases/r1/items');
      expect(JSON.parse(String(withBundle.init?.body))).toEqual({ ticketKeys: ['ABC-1'], bundle: 'Audit' });

      const without = await runMutation(
        releasesModule.useAddTicketsToRelease,
        { releaseId: 'r1', groups: [{ ticketKeys: ['ABC-1'], bundle: ' ' }] },
        addResult(),
      );
      expect(JSON.parse(String(without.init?.body))).toEqual({ ticketKeys: ['ABC-1'] });
    });

    describe('several groups', () => {
      async function runGroups<TVars>(
        useHook: () => { mutateAsync: (vars: TVars) => Promise<unknown> },
        vars: TVars,
        counts: { added: number; alreadyPresent: number; notFound: number }[],
      ) {
        let call = 0;
        const fetchSpy = vi
          .spyOn(globalThis, 'fetch')
          .mockImplementation(async () => jsonResponse(200, { release: release(), ...counts[call++] }));
        (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
        const root = createRoot(document.createElement('div'));
        let result: unknown;
        function Probe() {
          const mutation = useHook();
          useEffect(() => {
            mutation.mutateAsync(vars).then((r) => (result = r));
          }, []); // eslint-disable-line react-hooks/exhaustive-deps
          return null;
        }
        await act(async () => {
          root.render(createElement(QueryClientProvider, { client: new QueryClient() }, createElement(Probe)));
        });
        await act(async () => {
          await new Promise((resolve) => setTimeout(resolve, 0));
        });
        await act(async () => root.unmount());
        const requests = fetchSpy.mock.calls.map(([url, init]) => ({
          url: String(url),
          body: JSON.parse(String(init?.body)),
        }));
        return { requests, result };
      }

      const groups = [{ ticketKeys: ['ABC-1'] }, { ticketKeys: ['ABC-2', 'ABC-3'], bundle: 'Payments' }];

      it('useAddTicketsToRelease posts one request per group and sums the counts', async () => {
        const { requests, result } = await runGroups(
          releasesModule.useAddTicketsToRelease,
          { releaseId: 'r1', groups },
          [
            { added: 1, alreadyPresent: 0, notFound: 0 },
            { added: 1, alreadyPresent: 1, notFound: 0 },
          ],
        );
        expect(requests).toEqual([
          { url: '/api/releases/r1/items', body: { ticketKeys: ['ABC-1'] } },
          { url: '/api/releases/r1/items', body: { ticketKeys: ['ABC-2', 'ABC-3'], bundle: 'Payments' } },
        ]);
        expect(result).toMatchObject({ added: 2, alreadyPresent: 1, notFound: 0 });
      });

      it('useCreateRelease creates with the first group, then adds the rest to the new release', async () => {
        const { requests, result } = await runGroups(
          releasesModule.useCreateRelease,
          { ...createVars, groups },
          [
            { added: 1, alreadyPresent: 0, notFound: 0 },
            { added: 2, alreadyPresent: 0, notFound: 1 },
          ],
        );
        expect(requests).toEqual([
          { url: '/api/releases', body: { ...createVars, ticketKeys: ['ABC-1'] } },
          { url: '/api/releases/r1/items', body: { ticketKeys: ['ABC-2', 'ABC-3'], bundle: 'Payments' } },
        ]);
        expect(result).toMatchObject({ added: 3, alreadyPresent: 0, notFound: 1 });
      });
    });

    it('useRemoveTicketItem appends ?withBundle=true only when asked', async () => {
      const withBundle = await runMutation(
        releasesModule.useRemoveTicketItem,
        { releaseId: 'r1', ticketKey: 'ABC-1', withBundle: true },
        release(),
      );
      expect(withBundle.url).toBe('/api/releases/r1/items/ABC-1?withBundle=true');
      expect(withBundle.init?.method).toBe('DELETE');

      const plain = await runMutation(
        releasesModule.useRemoveTicketItem,
        { releaseId: 'r1', ticketKey: 'ABC-1' },
        release(),
      );
      expect(plain.url).toBe('/api/releases/r1/items/ABC-1');
    });

    it('releaseBundles returns the unique non-null bundles, sorted case-insensitively', () => {
      expect(
        releasesModule.releaseBundles([{ bundle: 'b' }, { bundle: null }, { bundle: 'A' }, { bundle: 'b' }]),
      ).toEqual(['A', 'b']);
    });
  });
});
