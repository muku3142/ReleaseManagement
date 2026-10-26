import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ChatRequestBody, StreamEvent } from '../../src/api/assistant';
import { ApiError } from '../../src/api/client';
import { streamChat } from '../../src/api/assistant';
import { chatActions, HOLD_BACK_CHARS } from '../../src/assistant/chatStore';

vi.mock('../../src/api/assistant', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../src/api/assistant')>()),
  streamChat: vi.fn(),
}));

type Script = {
  body: ChatRequestBody;
  signal: AbortSignal;
  emit: (event: StreamEvent) => void;
  end: () => void;
  fail: (error: unknown) => void;
};

let scripts: Script[] = [];
let keyCounter = 0;
const newKey = () => `test-${keyCounter++}`;

const token = (text: string): StreamEvent => ({ event: 'token', data: { text } });
const done = (over: Partial<Extract<StreamEvent, { event: 'done' }>['data']> = {}): StreamEvent => ({
  event: 'done',
  data: { refused: false, sources: [], tools: [], historyTrimmed: false, meta: { model: null, promptHash: null }, ...over },
});

const last = () => scripts[scripts.length - 1];
const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

function turnOf(key: string, index = 0) {
  return chatActions.getSession(key).turns[index];
}

beforeEach(() => {
  scripts = [];
  vi.mocked(streamChat).mockImplementation(
    (body, signal, onEvent) =>
      new Promise<void>((resolve, reject) => {
        scripts.push({ body, signal, emit: onEvent, end: resolve, fail: reject });
      }),
  );
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('hold-back', () => {
  it('holds fewer than 150 characters until done', async () => {
    const key = newKey();
    chatActions.ask(key, 'q');
    last().emit(token('a'.repeat(149)));
    expect(turnOf(key).text).toBe('');
    last().emit(done());
    expect(turnOf(key).text).toBe('a'.repeat(149));
    expect(turnOf(key).status).toBe('done');
  });

  it('reveals at the threshold and appends afterwards', () => {
    const key = newKey();
    chatActions.ask(key, 'q');
    last().emit(token('a'.repeat(HOLD_BACK_CHARS - 1)));
    last().emit(token('b'));
    expect(turnOf(key).text).toBe('a'.repeat(HOLD_BACK_CHARS - 1) + 'b');
    last().emit(token('c'));
    expect(turnOf(key).text.endsWith('bc')).toBe(true);
  });

  it('drops held text on discard and shows the tool status', () => {
    const key = newKey();
    chatActions.ask(key, 'q');
    last().emit(token('a'.repeat(40)));
    last().emit({ event: 'discard', data: {} });
    last().emit({ event: 'status', data: { tool: 'deployments' } });
    expect(turnOf(key)).toMatchObject({ text: '', held: '', phase: 'tool', statusTool: 'deployments' });
  });

  it('clears already shown text on discard and keeps only the final round', () => {
    const key = newKey();
    chatActions.ask(key, 'q');
    last().emit(token('a'.repeat(200)));
    last().emit({ event: 'discard', data: {} });
    expect(turnOf(key).text).toBe('');
    last().emit(token('final'));
    last().emit(done());
    expect(turnOf(key).text).toBe('final');
  });
});

describe('terminal events', () => {
  it('clears text and sources on a refusal but keeps tools', () => {
    const key = newKey();
    chatActions.ask(key, 'q');
    last().emit(token('nope'));
    const tools = [{ round: 1, name: 'releases', arguments: {}, error: false }];
    last().emit(done({ refused: true, tools, sources: [{ n: 1 } as never] }));
    expect(turnOf(key)).toMatchObject({ status: 'refused', text: '', sources: [], tools });
  });

  it('records an error outcome, reveals held text and sets no banner for BUSY', () => {
    const key = newKey();
    chatActions.ask(key, 'q');
    last().emit(token('partial'));
    last().emit({ event: 'error', data: { outcome: 'BUSY', retryAfterSeconds: 5 } });
    expect(turnOf(key)).toMatchObject({ status: 'error', outcome: 'BUSY', retryAfterSeconds: 5, text: 'partial' });
    expect(chatActions.getSession(key).banner).toBeNull();
  });

  it('sets the banner on OFFLINE and clears it on a later done', async () => {
    const key = newKey();
    chatActions.ask(key, 'q');
    last().emit({ event: 'error', data: { outcome: 'OFFLINE', retryAfterSeconds: null } });
    last().end();
    await flush();
    expect(chatActions.getSession(key).banner).toBe('OFFLINE');
    chatActions.ask(key, 'again');
    last().emit(done());
    expect(chatActions.getSession(key).banner).toBeNull();
  });

  it('sets the banner on MODEL_MISSING', () => {
    const key = newKey();
    chatActions.ask(key, 'q');
    last().emit({ event: 'error', data: { outcome: 'MODEL_MISSING', retryAfterSeconds: null } });
    expect(chatActions.getSession(key).banner).toBe('MODEL_MISSING');
  });

  it('fails the turn when the stream ends without done or error', async () => {
    const key = newKey();
    chatActions.ask(key, 'q');
    last().end();
    await flush();
    expect(turnOf(key)).toMatchObject({ status: 'error', outcome: 'FAILED' });
    expect(chatActions.getSession(key).inFlight).toBeNull();
  });

  it('fails the turn on a non-abort rejection', async () => {
    const key = newKey();
    chatActions.ask(key, 'q');
    last().fail(new Error('boom'));
    await flush();
    expect(turnOf(key)).toMatchObject({ status: 'error', outcome: 'FAILED' });
  });

  it('redirects to /login on a 401 that survived the refresh', async () => {
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign });
    const key = newKey();
    chatActions.ask(key, 'q');
    last().fail(new ApiError({ status: 401, reason: 'SESSION_EXPIRED' }));
    await flush();
    expect(assign).toHaveBeenCalledWith('/login');
    vi.unstubAllGlobals();
  });
});

describe('stop', () => {
  it('aborts, keeps partial text as stopped and does not turn the AbortError into an error', async () => {
    const key = newKey();
    chatActions.ask(key, 'q');
    last().emit(token('a'.repeat(200)));
    last().emit(token('tail'));
    chatActions.stop(key);
    expect(last().signal.aborted).toBe(true);
    last().fail(new DOMException('aborted', 'AbortError'));
    await flush();
    expect(turnOf(key)).toMatchObject({ status: 'stopped', text: 'a'.repeat(200) + 'tail' });
    expect(chatActions.getSession(key).inFlight).toBeNull();
  });

  it('keeps held text when stopped before the threshold', () => {
    const key = newKey();
    chatActions.ask(key, 'q');
    last().emit(token('short'));
    chatActions.stop(key);
    expect(turnOf(key)).toMatchObject({ status: 'stopped', text: 'short' });
  });
});

describe('history', () => {
  it('sends only completed turns', async () => {
    const key = newKey();
    chatActions.ask(key, 'one');
    last().emit(token('answer one'));
    last().emit(done());
    last().end();
    await flush();
    chatActions.ask(key, 'two');
    last().emit({ event: 'error', data: { outcome: 'FAILED', retryAfterSeconds: null } });
    last().end();
    await flush();
    chatActions.ask(key, 'three');
    last().emit(done({ refused: true }));
    last().end();
    await flush();
    chatActions.ask(key, 'four');
    chatActions.stop(key);
    chatActions.ask(key, 'five');

    expect(last().body.history).toEqual([
      { role: 'user', content: 'one' },
      { role: 'assistant', content: 'answer one' },
    ]);
    expect(last().body.question).toBe('five');
  });

  it('ignores ask while a turn is in flight', () => {
    const key = newKey();
    chatActions.ask(key, 'one');
    chatActions.ask(key, 'two');
    expect(scripts).toHaveLength(1);
    expect(chatActions.getSession(key).turns).toHaveLength(1);
  });
});

describe('scope and sessions', () => {
  it('aborts, clears turns and announces on a scope change with turns', () => {
    const key = newKey();
    chatActions.setScope(key, { releaseId: 'r1', label: 'Atlas 1.0' });
    chatActions.ask(key, 'q');
    expect(last().body.releaseId).toBe('r1');
    const announcement = chatActions.setScope(key, null);
    expect(last().signal.aborted).toBe(true);
    expect(chatActions.getSession(key).turns).toEqual([]);
    expect(announcement).toBe('Started a new chat for all releases.');
    expect(chatActions.setScope(key, { releaseId: 'r2', label: 'Atlas 2.0' })).toBeNull();
  });

  it('announces the release label when a scope is chosen over existing turns', () => {
    const key = newKey();
    chatActions.ask(key, 'q');
    expect(chatActions.setScope(key, { releaseId: 'r1', label: 'Atlas 1.0' })).toBe('Started a new chat for Release: Atlas 1.0.');
  });

  it('only changes the scope when there are no turns, and ignores an identical scope', () => {
    const key = newKey();
    expect(chatActions.setScope(key, { releaseId: 'r1', label: 'x' })).toBeNull();
    expect(chatActions.getSession(key).scope?.releaseId).toBe('r1');
    chatActions.ask(key, 'q');
    expect(chatActions.setScope(key, { releaseId: 'r1', label: 'x' })).toBeNull();
    expect(chatActions.getSession(key).turns).toHaveLength(1);
  });

  it('newChat keeps the scope, clears turns and aborts', () => {
    const key = newKey();
    chatActions.setScope(key, { releaseId: 'r1', label: 'x' });
    chatActions.ask(key, 'q');
    chatActions.newChat(key);
    expect(last().signal.aborted).toBe(true);
    expect(chatActions.getSession(key)).toMatchObject({ turns: [], scope: { releaseId: 'r1' }, inFlight: null });
  });

  it('retry replaces the failed turn with a fresh one for the same question', async () => {
    const key = newKey();
    chatActions.ask(key, 'q');
    last().emit({ event: 'error', data: { outcome: 'BUSY', retryAfterSeconds: 1 } });
    last().end();
    await flush();
    chatActions.retry(key, turnOf(key).id);
    const turns = chatActions.getSession(key).turns;
    expect(turns).toHaveLength(1);
    expect(turns[0]).toMatchObject({ question: 'q', status: 'streaming' });
    expect(scripts).toHaveLength(2);
  });

  it('keeps page and drawer sessions independent and never touches web storage', () => {
    const storage = vi.spyOn(Storage.prototype, 'setItem');
    const read = vi.spyOn(Storage.prototype, 'getItem');
    chatActions.ask('page-x', 'p');
    chatActions.ask('drawer:r1', 'd1');
    chatActions.ask('drawer:r2', 'd2');
    last().emit(token('x'));
    expect(chatActions.getSession('page-x').turns[0].question).toBe('p');
    expect(chatActions.getSession('drawer:r1').turns[0].question).toBe('d1');
    expect(chatActions.getSession('drawer:r2').turns[0].held).toBe('x');
    expect(chatActions.getSession('drawer:r1').turns[0].held).toBe('');
    expect(storage).not.toHaveBeenCalled();
    expect(read).not.toHaveBeenCalled();
  });
});
