import { act, createElement } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../src/api/client';
import { documentQueryKey, documentRevisionsQueryKey, readinessQueryKey } from '../../src/api/documents';
import {
  streamTransform,
  tooLargeFrom,
  useSaveAiRevision,
  useTransformLimits,
  type TransformEvent,
  type TransformLimits,
  type TransformRequestBody,
} from '../../src/api/documentAssist';

const LIMITS: TransformLimits = {
  numCtx: 4096,
  charsPerToken: 3,
  systemTokens: 152,
  templateSlack: 256,
  instructionMaxChars: 2000,
  presets: {
    CLEAN_UP: { RELEASE_NOTES: 100, RUNBOOK: 100, APPROVAL: 100 },
    SUMMARIZE: { RELEASE_NOTES: 100, RUNBOOK: 100, APPROVAL: 100 },
  },
};

const BODY: TransformRequestBody = {
  releaseId: 'r1',
  documentType: 'RELEASE_NOTES',
  sourceRevision: 2,
  mode: 'CLEAN_UP',
  instruction: null,
};

const enc = new TextEncoder();

function streamResponse(text: string): Response {
  return new Response(
    new ReadableStream({
      start(c) {
        c.enqueue(enc.encode(text));
        c.close();
      },
    }),
    { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
  );
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

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

describe('streamTransform', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=; Max-Age=0';
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('posts the body and dispatches typed events, dropping unknown ones', async () => {
    const text =
      ': hb\n\nevent: nope\ndata: {}\n\nevent: token\ndata: {"text":"hi"}\n\n' +
      'event: done\ndata: {"finishReason":"stop","truncated":false,"complete":true}\n\n';
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(streamResponse(text));
    const events: TransformEvent[] = [];

    await streamTransform(BODY, new AbortController().signal, (e) => events.push(e));

    expect(fetchSpy.mock.calls[0][0]).toBe('/api/assistant/transform');
    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual(BODY);
    expect(events).toEqual([
      { event: 'token', data: { text: 'hi' } },
      { event: 'done', data: { finishReason: 'stop', truncated: false, complete: true } },
    ]);
  });

  it('dispatches an error event', async () => {
    const data = '{"outcome":"BUSY","retryAfterSeconds":5,"characters":null,"maxCharacters":null}';
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(streamResponse(`event: error\ndata: ${data}\n\n`));
    const events: TransformEvent[] = [];

    await streamTransform(BODY, new AbortController().signal, (e) => events.push(e));

    expect(events).toEqual([{ event: 'error', data: JSON.parse(data) }]);
  });

  it('rejects a 413 before the stream with the sizes', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(413, { reason: 'TEXT_TOO_LARGE', characters: 9000, maxCharacters: 5523 }),
    );

    const error = await streamTransform(BODY, new AbortController().signal, () => undefined).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(tooLargeFrom(error)).toEqual({ characters: 9000, maxCharacters: 5523 });
  });

  it('tooLargeFrom ignores other errors', () => {
    expect(tooLargeFrom(new ApiError({ status: 409, reason: 'REVISION_SUPERSEDED' }))).toBeNull();
    expect(tooLargeFrom(new Error('x'))).toBeNull();
  });

  it('rejects with AbortError when aborted', async () => {
    const controller = new AbortController();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(new ReadableStream({ start() {} }), { status: 200 }),
    );
    const pending = streamTransform(BODY, controller.signal, () => undefined);
    await new Promise((resolve) => setTimeout(resolve, 0));

    controller.abort();

    await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
  });
});

describe('document assist hooks', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    root?.unmount();
    container?.remove();
  });

  it('useSaveAiRevision posts the body and invalidates document queries', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(201, { revision: 3 }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(readinessQueryKey(), { entries: [] });
    client.setQueryData(['releases', ''], { releases: [] });
    client.setQueryData(['dashboard'], {});
    client.setQueryData(documentQueryKey('r1', 'RELEASE_NOTES'), {});
    client.setQueryData(documentRevisionsQueryKey('r1', 'RELEASE_NOTES'), { revisions: [] });
    client.setQueryData(['assistant', 'freshness'], { lastIndexedAt: null });
    const result = mount(() => useSaveAiRevision(), client);
    const body = { ...BODY, mode: 'CUSTOM' as const, instruction: 'shorter', markdown: '# Edited' };
    let saved: { revision: number } | undefined;

    await act(async () => {
      saved = await result.current.mutateAsync(body);
    });

    expect(saved).toEqual({ revision: 3 });
    expect(fetchSpy.mock.calls[0][0]).toBe('/api/assistant/documents/ai-revisions');
    expect(JSON.parse((fetchSpy.mock.calls[0][1] as RequestInit).body as string)).toEqual(body);
    for (const key of [
      readinessQueryKey(),
      ['releases', ''],
      ['dashboard'],
      documentQueryKey('r1', 'RELEASE_NOTES'),
      documentRevisionsQueryKey('r1', 'RELEASE_NOTES'),
      ['assistant', 'freshness'],
    ]) {
      expect(client.getQueryState(key)?.isInvalidated).toBe(true);
    }
  });

  it('useTransformLimits does not fetch when disabled', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, LIMITS));
    const client = new QueryClient();
    mount(() => useTransformLimits(false), client);
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('useTransformLimits fetches the limits when enabled', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, LIMITS));
    const client = new QueryClient();
    const result = mount(() => useTransformLimits(true), client);
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 10));
    });

    expect(result.current.data).toEqual(LIMITS);
    expect(client.getQueryCache().find({ queryKey: ['assistant', 'transform-limits'] })).toBeDefined();
  });
});
