import { act, createElement, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../src/api/client';
import { messageForReason } from '../../src/api/reasonMessages';
import { useAddProduct, useArchiveProduct, productsQueryKey } from '../../src/api/products';
import { useLinkProduct, useUnlinkProduct, clientQueryKey } from '../../src/api/clients';
import {
  installsQueryKey,
  rejectedClients,
  useCorrectInstall,
  useRecordInstall,
} from '../../src/api/installs';
import { releaseQueryKey, useSetReleaseProduct } from '../../src/api/releases';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

// Mounts a probe that fires the mutation once, then returns the single fetch call and the cache.
async function runMutation<TVars>(
  useHook: () => { mutate: (vars: TVars) => void },
  vars: TVars,
  response: unknown,
  seed?: () => QueryClient,
) {
  const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, response));
  (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const queryClient = seed?.() ?? new QueryClient();
  const root = createRoot(document.createElement('div'));
  function Probe() {
    const mutation = useHook();
    useEffect(() => mutation.mutate(vars), []); // eslint-disable-line react-hooks/exhaustive-deps
    return null;
  }
  await act(async () => {
    root.render(createElement(QueryClientProvider, { client: queryClient }, createElement(Probe)));
  });
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
  await act(async () => root.unmount());
  const [url, init] = fetchSpy.mock.calls.at(-1)!;
  return { url: String(url), init: init as RequestInit, queryClient };
}

const MATRIX_KEY = ['dashboard', 'portfolio', 'matrix', '', 'active'];

function clientWithMatrix(): QueryClient {
  const queryClient = new QueryClient();
  queryClient.setQueryData(MATRIX_KEY, { products: [], clients: [], cells: [] });
  return queryClient;
}

describe('portfolio API client', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it.each([
    ['recording an install', () => useRecordInstall('r1'), { productId: 'p1', clientIds: ['c1'], outcome: 'SUCCESS' as const, installedAt: null, note: null }],
    ['linking a client to a product', () => useLinkProduct('c1'), 'p1'],
    ['archiving a product', () => useArchiveProduct(), 'p1'],
    ['setting the release product', () => useSetReleaseProduct('r1'), 'p1'],
  ] as const)('%s refreshes the dashboard', async (_name, useHook, vars) => {
    const { queryClient } = await runMutation(
      useHook as () => { mutate: (v: unknown) => void },
      vars,
      {},
      clientWithMatrix,
    );
    expect(queryClient.getQueryState(MATRIX_KEY)?.isInvalidated).toBe(true);
  });

  it('useRecordInstall posts the input and stores the history', async () => {
    const input = { productId: 'p1', clientIds: ['c1'], outcome: 'SUCCESS' as const, installedAt: null, note: null };
    const history = { releaseId: 'r1', frozen: true, inProduction: true, products: [], entries: [] };
    const { url, init, queryClient } = await runMutation(() => useRecordInstall('r1'), input, history);
    expect(url).toBe('/api/releases/r1/installs');
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual(input);
    expect(queryClient.getQueryData(installsQueryKey('r1'))).toEqual(history);
  });

  it('useCorrectInstall posts to the correction path without the installId in the body', async () => {
    const vars = { installId: 'i1', outcome: 'VOIDED' as const, installedAt: '2026-08-05T00:00:00Z', note: null, reason: 'typo' };
    const { url, init } = await runMutation(() => useCorrectInstall('r1'), vars, {});
    expect(url).toBe('/api/releases/r1/installs/i1/corrections');
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual({
      outcome: 'VOIDED',
      installedAt: '2026-08-05T00:00:00Z',
      note: null,
      reason: 'typo',
    });
  });

  it('useSetReleaseProduct puts the single productId and stores the release', async () => {
    const release = { id: 'r1', products: [] };
    const { url, init, queryClient } = await runMutation(() => useSetReleaseProduct('r1'), 'p2', release);
    expect(url).toBe('/api/releases/r1/product');
    expect(init.method).toBe('PUT');
    expect(JSON.parse(String(init.body))).toEqual({ productId: 'p2' });
    expect(queryClient.getQueryData(releaseQueryKey('r1'))).toEqual(release);
  });

  it('useLinkProduct puts and useUnlinkProduct deletes the client-product link', async () => {
    const detail = { id: 'c1', name: 'Acme', archived: false, runs: [], history: [] };
    const link = await runMutation(() => useLinkProduct('c1'), 'p1', detail);
    expect(link.url).toBe('/api/clients/c1/products/p1');
    expect(link.init.method).toBe('PUT');
    expect(link.queryClient.getQueryData(clientQueryKey('c1'))).toEqual(detail);
    const unlink = await runMutation(() => useUnlinkProduct('c1'), 'p1', detail);
    expect(unlink.url).toBe('/api/clients/c1/products/p1');
    expect(unlink.init.method).toBe('DELETE');
  });

  it('useAddProduct posts the name and replaces the products cache', async () => {
    const list = { products: [{ id: 'p1', name: 'Core', archived: false, clientCount: 0, releaseCount: 0 }] };
    const { url, init, queryClient } = await runMutation(() => useAddProduct(), 'Core', list);
    expect(url).toBe('/api/products');
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual({ name: 'Core' });
    expect(queryClient.getQueryData(productsQueryKey)).toEqual(list);
  });

  it('rejectedClients returns the offending clients of an INSTALL_CLIENTS_REJECTED error only', () => {
    const clients = [{ clientId: 'c1', name: 'Acme', reason: 'NOT_LINKED' as const }];
    const rejected = new ApiError({ status: 409, reason: 'INSTALL_CLIENTS_REJECTED', clients });
    expect(rejectedClients(rejected)).toEqual(clients);
    expect(rejectedClients(new ApiError({ status: 409, reason: 'RELEASE_FROZEN' }))).toEqual([]);
    expect(rejectedClients(new Error('boom'))).toEqual([]);
  });

  it('renders the D-15-15 and D-17.2 product messages', () => {
    expect(messageForReason({ status: 409, reason: 'PRODUCT_HAS_INSTALLS_ON_RELEASE' })).toBe(
      "Can't change: installs are recorded for this product on this release.",
    );
    expect(messageForReason({ status: 422, reason: 'RELEASE_PRODUCT_REQUIRED' })).toBe(
      'Choose a product for this release.',
    );
  });
});
