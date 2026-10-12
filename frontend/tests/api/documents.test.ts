import { act, createElement } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiFetchBlob, triggerDownload } from '../../src/api/client';
import {
  documentRevisionsQueryKey,
  readinessIndex,
  readinessQueryKey,
  useDocument,
  useExportDocument,
  useGenerateDocument,
  type GeneratedDocument,
  type ReadinessEntry,
} from '../../src/api/documents';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function generatedDocument(overrides: Partial<GeneratedDocument> = {}): GeneratedDocument {
  return {
    id: 'doc-1',
    releaseId: 'r1',
    documentType: 'RELEASE_NOTES',
    revision: 1,
    latestRevision: 1,
    markdown: '# Notes',
    templateName: 'Standard notes',
    templateDeleted: false,
    hasLogo: false,
    chrome: {
      coverEnabled: false,
      coverTitle: '',
      coverSubtitle: '',
      headerText: '',
      footerText: '',
      pageNumbersEnabled: false,
      logoRenderHeightMm: 30,
    },
    generatedAt: '2026-08-10T09:00:00Z',
    generatedBy: 'rm@acme.com',
    ...overrides,
  };
}

// Mirrors LogoUpload.test.tsx's harness: hooks built on useMutation/useQuery need a real
// QueryClientProvider, not a bare Probe render.
let container: HTMLDivElement;
let root: Root;

function mount<T>(useHook: () => T, client: QueryClient): { current: T } {
  const result: { current: T } = { current: undefined as unknown as T };
  function Probe() {
    result.current = useHook();
    return null;
  }
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  act(() => {
    root.render(createElement(QueryClientProvider, { client }, createElement(Probe)));
  });
  return result;
}

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

describe('documents API client', () => {
  beforeEach(() => {
    // triggerDownload clicks a real <a href="blob:..."> - jsdom attempts an actual navigation on
    // that click, which it does not implement. Stubbing click() keeps every test's assertions
    // about downloads (not jsdom's navigation support) rather than adding this per test.
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    root?.unmount();
    container?.remove();
  });

  it('readinessIndex returns the matching state and undefined otherwise', () => {
    const entries: ReadinessEntry[] = [
      { releaseId: 'r1', documentType: 'RELEASE_NOTES', state: 'UP_TO_DATE' },
      { releaseId: 'r1', documentType: 'RUNBOOK', state: 'STALE' },
      { releaseId: 'r1', documentType: 'APPROVAL', state: 'NO_TEMPLATE' },
    ];
    const stateOf = readinessIndex(entries);

    expect(stateOf('r1', 'RELEASE_NOTES')).toBe('UP_TO_DATE');
    expect(stateOf('r1', 'APPROVAL')).toBe('NO_TEMPLATE');
    expect(stateOf('r2', 'RELEASE_NOTES')).toBeUndefined();
    expect(readinessIndex(undefined)('r1', 'RELEASE_NOTES')).toBeUndefined();
  });

  it('generate invalidates readiness and the revisions/releases queries', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(201, generatedDocument()));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(readinessQueryKey(), { entries: [] });
    client.setQueryData(['releases', ''], { releases: [] });
    client.setQueryData(['dashboard'], {});
    client.setQueryData(documentRevisionsQueryKey('r1', 'RELEASE_NOTES'), { revisions: [] });
    const result = mount(() => useGenerateDocument(), client);

    await act(async () => {
      await result.current.mutateAsync({ releaseId: 'r1', documentType: 'RELEASE_NOTES' });
    });

    expect(client.getQueryState(readinessQueryKey())?.isInvalidated).toBe(true);
    expect(client.getQueryState(['releases', ''])?.isInvalidated).toBe(true);
    expect(client.getQueryState(['dashboard'])?.isInvalidated).toBe(true);
    expect(client.getQueryState(documentRevisionsQueryKey('r1', 'RELEASE_NOTES'))?.isInvalidated).toBe(true);
  });

  it('generate without a templateId sends no body', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(201, generatedDocument()));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const result = mount(() => useGenerateDocument(), client);

    await act(async () => {
      await result.current.mutateAsync({ releaseId: 'r1', documentType: 'RELEASE_NOTES' });
    });

    const init = fetchMock.mock.calls[0][1];
    expect(init?.method).toBe('POST');
    expect(init?.body).toBeUndefined();
  });

  it('generate with a templateId sends it as the JSON body', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(201, generatedDocument()));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const result = mount(() => useGenerateDocument(), client);

    await act(async () => {
      await result.current.mutateAsync({ releaseId: 'r1', documentType: 'RELEASE_NOTES', templateId: 't9' });
    });

    expect(fetchMock.mock.calls[0][1]?.body).toBe('{"templateId":"t9"}');
  });

  it('export requests the correct URL per format and revision', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(new Blob(['bytes']), { status: 200 }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const result = mount(() => useExportDocument(), client);

    await act(async () => {
      await result.current.mutateAsync({
        releaseId: 'r1',
        documentType: 'RUNBOOK',
        revision: 3,
        format: 'docx',
        releaseName: 'Orchstro',
        version: '4.2.0',
      });
    });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/releases/r1/documents/RUNBOOK/export.docx?revision=3',
      expect.anything(),
    );
  });

  it('export failure throws an ApiError carrying the reason', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(502, { status: 502, reason: 'EXPORT_FAILED', detail: 'render failed' }),
    );
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const result = mount(() => useExportDocument(), client);

    await expect(
      act(async () => {
        await result.current.mutateAsync({
          releaseId: 'r1',
          documentType: 'RELEASE_NOTES',
          format: 'pdf',
          releaseName: 'Orchstro',
          version: '4.2.0',
        });
      }),
    ).rejects.toSatisfy((error: unknown) => error instanceof ApiError && error.problem.reason === 'EXPORT_FAILED');
  });

  it('apiFetchBlob output can be handed to triggerDownload, which revokes the object URL', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(new Blob(['bytes']), { status: 200 }));
    const createSpy = vi.fn(() => 'blob:mock-url');
    const revokeSpy = vi.fn();
    vi.stubGlobal('URL', { ...URL, createObjectURL: createSpy, revokeObjectURL: revokeSpy });

    const blob = await apiFetchBlob('/api/releases/r1/documents/RELEASE_NOTES/export.pdf');
    triggerDownload(blob, 'Orchstro 4.2.0 Release Notes.pdf');

    expect(createSpy).toHaveBeenCalledWith(blob);
    expect(revokeSpy).toHaveBeenCalledWith('blob:mock-url');
  });

  it('document query does not retry on 404', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse(404, { status: 404, reason: 'DOCUMENT_NOT_FOUND' }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    mount(() => useDocument('r1', 'RELEASE_NOTES'), client);

    await flush();
    await flush();

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
