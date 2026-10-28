import { createElement, act, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import {
  productBundlesQueryKey,
  useDeleteProductBundle,
  useProductBundles,
  useSaveProductBundle,
} from '../../src/api/productBundles';

const LIST = { bundles: [{ id: 'b1', name: 'Payments', ticketKeys: ['ABC-1'] }] };

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

async function renderProbe(client: QueryClient, probe: () => null) {
  const root = createRoot(document.createElement('div'));
  await act(async () => {
    root.render(createElement(QueryClientProvider, { client }, createElement(probe)));
  });
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
  await act(async () => root.unmount());
}

describe('product bundles API client', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  afterEach(() => vi.restoreAllMocks());

  it('useProductBundles reads the product bundle list and stays idle without a product', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => jsonResponse(LIST));
    const client = new QueryClient();
    await renderProbe(client, () => {
      useProductBundles('p1');
      useProductBundles(undefined);
      return null;
    });

    expect(fetchSpy.mock.calls.map(([url]) => String(url))).toEqual(['/api/products/p1/bundles']);
    expect(client.getQueryData(productBundlesQueryKey('p1'))).toEqual(LIST);
  });

  it('useSaveProductBundle posts { name, ticketKeys } and caches the returned list', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => jsonResponse(LIST));
    const client = new QueryClient();
    await renderProbe(client, () => {
      const mutation = useSaveProductBundle('p1');
      useEffect(() => mutation.mutate({ name: 'Payments', ticketKeys: ['ABC-1'] }), []); // eslint-disable-line react-hooks/exhaustive-deps
      return null;
    });

    const [url, init] = fetchSpy.mock.calls[0];
    expect(String(url)).toBe('/api/products/p1/bundles');
    expect(init?.method).toBe('POST');
    expect(JSON.parse(String(init?.body))).toEqual({ name: 'Payments', ticketKeys: ['ABC-1'] });
    expect(client.getQueryData(productBundlesQueryKey('p1'))).toEqual(LIST);
  });

  it('useDeleteProductBundle sends DELETE for the bundle and caches the returned list', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => jsonResponse({ bundles: [] }));
    const client = new QueryClient();
    await renderProbe(client, () => {
      const mutation = useDeleteProductBundle('p1');
      useEffect(() => mutation.mutate('b1'), []); // eslint-disable-line react-hooks/exhaustive-deps
      return null;
    });

    const [url, init] = fetchSpy.mock.calls[0];
    expect(String(url)).toBe('/api/products/p1/bundles/b1');
    expect(init?.method).toBe('DELETE');
    expect(client.getQueryData(productBundlesQueryKey('p1'))).toEqual({ bundles: [] });
  });
});
