import { act, createElement, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as client from '../../src/api/client';
import {
  parseRoadmapSearch,
  roadmapQueryKey,
  roadmapQueryString,
  useExportRoadmapCsv,
  useSetInputs,
} from '../../src/api/roadmap';
import { MAX_PRODUCT_FILTER } from '../../src/api/dashboard';

const A = '11111111-1111-4111-8111-111111111111';
const B = '22222222-2222-4222-8222-222222222222';

afterEach(() => vi.restoreAllMocks());

async function mount<TVars>(
  useHook: () => { mutate: (vars: TVars) => void },
  vars: TVars,
  queryClient: QueryClient,
) {
  (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
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
}

describe('parseRoadmapSearch', () => {
  it('keeps only UUID products and caps them', () => {
    expect(parseRoadmapSearch({ products: `${A},nope,${B}` }).products).toEqual([A, B]);
    const many = Array.from({ length: MAX_PRODUCT_FILTER + 5 }, (_, i) => `00000000-0000-4000-8000-${String(i).padStart(12, '0')}`);
    expect(parseRoadmapSearch({ products: many }).products).toHaveLength(MAX_PRODUCT_FILTER);
  });

  it('accepts only a literal true for the flags', () => {
    expect(parseRoadmapSearch({ done: 'true' }).done).toBe(true);
    expect(parseRoadmapSearch({ archived: true }).archived).toBe(true);
    expect(parseRoadmapSearch({ done: 'yes' }).done).toBeUndefined();
  });

  it('drops a malformed item id', () => {
    expect(parseRoadmapSearch({ item: 'not-uuid' }).item).toBeUndefined();
    expect(parseRoadmapSearch({ item: A }).item).toBe(A);
  });
});

describe('roadmap query string and key', () => {
  it('sorts products and omits empty flags', () => {
    expect(roadmapQueryString({ products: [B, A], done: true, archived: false })).toBe(`productIds=${A},${B}&done=true`);
    expect(roadmapQueryString({ products: [], done: false, archived: false })).toBe('');
  });

  it('ignores product selection order in the key', () => {
    const base = { done: false, archived: false };
    expect(roadmapQueryKey({ ...base, products: [B, A] })).toEqual(roadmapQueryKey({ ...base, products: [A, B] }));
  });
});

describe('roadmap hooks', () => {
  it('invalidates every roadmap query after saving inputs', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({}), { status: 200, headers: { 'Content-Type': 'application/json' } }),
    );
    const queryClient = new QueryClient();
    queryClient.setQueryData(['roadmap', 'list', '', false, false], { items: [] });
    queryClient.setQueryData(['roadmap', 'history', 'x'], { entries: [] });
    queryClient.setQueryData(['products'], {});
    await mount(useSetInputs, { itemId: 'x', inputs: { reach: 1, impact: 1, confidence: 100, effort: 1 } }, queryClient);
    expect(queryClient.getQueryState(['roadmap', 'list', '', false, false])?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(['roadmap', 'history', 'x'])?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(['products'])?.isInvalidated).toBe(false);
  });

  it('downloads the CSV for the on-screen filter under a dated name', async () => {
    const blob = new Blob(['a']);
    const fetchBlob = vi.spyOn(client, 'apiFetchBlob').mockResolvedValue(blob);
    const download = vi.spyOn(client, 'triggerDownload').mockImplementation(() => undefined);
    await mount(useExportRoadmapCsv, { products: [B, A], done: true, archived: false }, new QueryClient());
    expect(fetchBlob).toHaveBeenCalledWith(`/api/roadmap/export.csv?productIds=${A},${B}&done=true`);
    expect(download).toHaveBeenCalledWith(blob, expect.stringMatching(/^roadmap-\d{4}-\d{2}-\d{2}\.csv$/));
  });
});
