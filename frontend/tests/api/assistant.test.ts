import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../src/api/client';
import { parseSse, statusLabel, streamChat, streamSse, type StreamEvent } from '../../src/api/assistant';

const BODY = { question: 'q', history: [], releaseId: null };
const enc = new TextEncoder();

function streamResponse(...chunks: Uint8Array[]): Response {
  return new Response(
    new ReadableStream({
      start(c) {
        chunks.forEach((chunk) => c.enqueue(chunk));
        c.close();
      },
    }),
    { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
  );
}

function problem(status: number, reason: string): Response {
  return new Response(JSON.stringify({ status, reason }), { status, headers: { 'Content-Type': 'application/problem+json' } });
}

describe('parseSse', () => {
  it('returns complete events and keeps the partial tail', () => {
    const { events, rest } = parseSse('event: token\ndata: {"text":"a"}\n\nevent: tok');
    expect(events).toEqual([{ event: 'token', data: { text: 'a' } }]);
    expect(rest).toBe('event: tok');
  });

  it('parses CRLF framing identically', () => {
    const { events, rest } = parseSse('event: token\r\ndata: {"text":"a"}\r\n\r\n');
    expect(events).toEqual([{ event: 'token', data: { text: 'a' } }]);
    expect(rest).toBe('');
  });

  it('ignores comment blocks and blocks without data', () => {
    expect(parseSse(': hb\n\nevent: token\n\n').events).toEqual([]);
  });

  it('joins multiple data lines with a newline', () => {
    expect(parseSse('event: token\ndata: {"text":\ndata: "a"}\n\n').events).toEqual([{ event: 'token', data: { text: 'a' } }]);
  });

  it('returns unknown event names for the consumer to ignore', () => {
    expect(parseSse('event: nope\ndata: {}\n\n').events).toEqual([{ event: 'nope', data: {} }]);
  });
});

describe('streamChat', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=; Max-Age=0';
  });

  afterEach(() => {
    vi.restoreAllMocks();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0';
  });

  it('reassembles a multi-byte character split across chunks', async () => {
    const bytes = enc.encode('event: token\ndata: {"text":"héllo"}\n\n');
    const split = bytes.indexOf(0xc3) + 1;
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(streamResponse(bytes.slice(0, split), bytes.slice(split)));
    const events: StreamEvent[] = [];

    await streamChat(BODY, new AbortController().signal, (e) => events.push(e));

    expect(events).toEqual([{ event: 'token', data: { text: 'héllo' } }]);
  });

  it('drops unknown events and keeps order', async () => {
    const text = 'event: nope\ndata: {}\n\nevent: status\ndata: {"tool":"deployments"}\n\nevent: token\ndata: {"text":"x"}\n\n';
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(streamResponse(enc.encode(text)));
    const names: string[] = [];

    await streamChat(BODY, new AbortController().signal, (e) => names.push(e.event));

    expect(names).toEqual(['status', 'token']);
  });

  it('posts JSON with the stream Accept header, XSRF echo and same-origin credentials', async () => {
    document.cookie = 'XSRF-TOKEN=abc123';
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(streamResponse());

    await streamChat(BODY, new AbortController().signal, () => undefined);

    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = new Headers(init.headers);
    expect(path).toBe('/api/assistant/chat');
    expect(init.method).toBe('POST');
    expect(init.credentials).toBe('same-origin');
    expect(init.body).toBe(JSON.stringify(BODY));
    expect(headers.get('Accept')).toBe('text/event-stream, application/problem+json');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('X-XSRF-TOKEN')).toBe('abc123');
  });

  it('refreshes once on a session 401 and retries', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(problem(401, 'SESSION_EXPIRED'))
      .mockResolvedValueOnce(new Response('{}', { status: 200 }))
      .mockResolvedValueOnce(streamResponse(enc.encode('event: discard\ndata: {}\n\n')));
    const events: StreamEvent[] = [];

    await streamChat(BODY, new AbortController().signal, (e) => events.push(e));

    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual(['/api/assistant/chat', '/api/auth/refresh', '/api/assistant/chat']);
    expect(events).toHaveLength(1);
  });

  it('rejects with ApiError on a pre-stream 404 and emits no events', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(problem(404, 'RELEASE_NOT_FOUND'));
    const onEvent = vi.fn();

    await expect(streamChat(BODY, new AbortController().signal, onEvent)).rejects.toSatisfy(
      (e: unknown) => e instanceof ApiError && e.problem.status === 404,
    );
    expect(onEvent).not.toHaveBeenCalled();
  });

  it('rejects with an AbortError when aborted mid-stream', async () => {
    const response = new Response(new ReadableStream({ start() {} }), { status: 200 });
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(response);
    const controller = new AbortController();

    const pending = streamChat(BODY, controller.signal, () => undefined);
    await new Promise((resolve) => setTimeout(resolve, 0));
    controller.abort();

    await expect(pending).rejects.toSatisfy((e: unknown) => e instanceof DOMException && e.name === 'AbortError');
  });
});

describe('statusLabel', () => {
  it('maps tools to user-facing labels', () => {
    expect(statusLabel('deployments')).toBe('Checking deployments…');
    expect(statusLabel('releases')).toBe('Looking up release details…');
    expect(statusLabel('tickets')).toBe('Looking up release details…');
    expect(statusLabel('documents')).toBe('Checking documents…');
    expect(statusLabel('dashboard')).toBe('Looking things up…');
    expect(statusLabel(null)).toBe('Thinking…');
  });
});

describe('streamSse', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('posts to the given path and dispatches every event, comments dropped', async () => {
    const text = ': hb\n\nevent: custom\r\ndata: {"a":1}\r\n\r\nevent: token\ndata: {"text":"x"}\n\n';
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(streamResponse(enc.encode(text)));
    const events: { event: string; data: unknown }[] = [];

    await streamSse('/api/other', { k: 1 }, new AbortController().signal, (e) => events.push(e));

    expect(fetchSpy.mock.calls[0][0]).toBe('/api/other');
    expect(events).toEqual([
      { event: 'custom', data: { a: 1 } },
      { event: 'token', data: { text: 'x' } },
    ]);
  });
});
