import { useSyncExternalStore } from 'react';
import { streamChat, type ErrorOutcome, type HistoryMessage, type SourceView, type StreamEvent, type ToolView } from '../api/assistant';
import { ApiError } from '../api/client';

// The first characters of each round are held back so a round that turns out to be a tool call
// (D-13-21) never flashes text the user then sees disappear.
export const HOLD_BACK_CHARS = 150;

export type Scope = { releaseId: string; label: string } | null;
export type TurnStatus = 'streaming' | 'done' | 'refused' | 'error' | 'stopped';

export type Turn = {
  id: string;
  question: string;
  status: TurnStatus;
  phase: 'thinking' | 'tool' | 'answer';
  statusTool: string | null;
  text: string;
  held: string;
  revealed: boolean;
  sources: SourceView[];
  tools: ToolView[];
  historyTrimmed: boolean;
  outcome: ErrorOutcome | null;
  retryAfterSeconds: number | null;
};

export type Session = {
  scope: Scope;
  turns: Turn[];
  banner: 'OFFLINE' | 'MODEL_MISSING' | null;
  inFlight: AbortController | null;
};

const EMPTY_SESSION: Session = { scope: null, turns: [], banner: null, inFlight: null };

// Chat text lives in memory only (D-13-12): nothing here touches web storage.
const sessions = new Map<string, Session>();
const listeners = new Set<() => void>();

function getSession(key: string): Session {
  return sessions.get(key) ?? EMPTY_SESSION;
}

function setSession(key: string, update: (session: Session) => Session) {
  sessions.set(key, update(getSession(key)));
  listeners.forEach((l) => l());
}

function updateTurn(key: string, turnId: string, update: (turn: Turn) => Turn) {
  setSession(key, (s) => ({ ...s, turns: s.turns.map((t) => (t.id === turnId ? update(t) : t)) }));
}

function hasTurn(key: string, turnId: string): boolean {
  return getSession(key).turns.some((t) => t.id === turnId);
}

function reveal(turn: Turn): Turn {
  return turn.revealed ? turn : { ...turn, text: turn.held, held: '', revealed: true };
}

function applyEvent(turn: Turn, event: StreamEvent): Turn {
  switch (event.event) {
    case 'status':
      return { ...turn, statusTool: event.data.tool, phase: turn.revealed ? turn.phase : 'tool' };
    case 'discard':
      return { ...turn, text: '', held: '', revealed: false, phase: 'tool' };
    case 'token': {
      if (turn.revealed) return { ...turn, text: turn.text + event.data.text };
      const held = turn.held + event.data.text;
      return held.length >= HOLD_BACK_CHARS
        ? { ...turn, text: held, held: '', revealed: true, phase: 'answer' }
        : { ...turn, held };
    }
    case 'done': {
      const { refused, sources, tools, historyTrimmed } = event.data;
      const base = { ...turn, tools, historyTrimmed, statusTool: null };
      return refused
        ? { ...base, status: 'refused', text: '', held: '', sources: [] }
        : { ...reveal(base), status: 'done', phase: 'answer', sources };
    }
    case 'error':
      return {
        ...reveal(turn),
        status: 'error',
        outcome: event.data.outcome,
        retryAfterSeconds: event.data.retryAfterSeconds,
      };
  }
}

function applyBanner(key: string, event: StreamEvent) {
  if (event.event === 'error' && (event.data.outcome === 'OFFLINE' || event.data.outcome === 'MODEL_MISSING')) {
    setSession(key, (s) => ({ ...s, banner: event.data.outcome as 'OFFLINE' | 'MODEL_MISSING' }));
  } else if (event.event === 'done') {
    setSession(key, (s) => ({ ...s, banner: null }));
  }
}

function failTurn(key: string, turnId: string) {
  updateTurn(key, turnId, (t) => (t.status === 'streaming' ? { ...reveal(t), status: 'error', outcome: 'FAILED' } : t));
}

function historyOf(turns: Turn[]): HistoryMessage[] {
  return turns
    .filter((t) => t.status === 'done')
    .flatMap((t) => [
      { role: 'user' as const, content: t.question },
      { role: 'assistant' as const, content: t.text },
    ]);
}

function abortInFlight(key: string) {
  getSession(key).inFlight?.abort();
}

// Not crypto.randomUUID(): browsers expose it only on HTTPS or localhost, and the app is also served over plain HTTP.
let nextTurnId = 0;

function ask(key: string, question: string) {
  const session = getSession(key);
  if (session.inFlight) return;

  const controller = new AbortController();
  const turn: Turn = {
    id: `turn-${++nextTurnId}`,
    question,
    status: 'streaming',
    phase: 'thinking',
    statusTool: null,
    text: '',
    held: '',
    revealed: false,
    sources: [],
    tools: [],
    historyTrimmed: false,
    outcome: null,
    retryAfterSeconds: null,
  };
  const body = { question, history: historyOf(session.turns), releaseId: session.scope?.releaseId ?? null };
  setSession(key, (s) => ({ ...s, turns: [...s.turns, turn], inFlight: controller }));

  streamChat(body, controller.signal, (event) => {
    if (!hasTurn(key, turn.id)) return;
    updateTurn(key, turn.id, (t) => applyEvent(t, event));
    applyBanner(key, event);
  })
    .then(() => failTurn(key, turn.id))
    .catch((error: unknown) => {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      if (error instanceof ApiError && error.problem.status === 401) window.location.assign('/login');
      failTurn(key, turn.id);
    })
    .finally(() => {
      if (getSession(key).inFlight === controller) setSession(key, (s) => ({ ...s, inFlight: null }));
    });
}

function stop(key: string) {
  const { inFlight, turns } = getSession(key);
  if (!inFlight) return;
  inFlight.abort();
  const streaming = turns.find((t) => t.status === 'streaming');
  setSession(key, (s) => ({
    ...s,
    inFlight: null,
    turns: s.turns.map((t) => (t.id === streaming?.id ? { ...reveal(t), status: 'stopped' } : t)),
  }));
}

function newChat(key: string) {
  abortInFlight(key);
  setSession(key, (s) => ({ ...s, turns: [], inFlight: null }));
}

// Returns the announcement for the live region, or null when nothing visible changed.
function setScope(key: string, scope: Scope): string | null {
  const current = getSession(key);
  if ((current.scope?.releaseId ?? null) === (scope?.releaseId ?? null)) return null;
  abortInFlight(key);
  setSession(key, (s) => ({ ...s, scope, turns: [], inFlight: null }));
  if (current.turns.length === 0) return null;
  return scope ? `Started a new chat for Release: ${scope.label}.` : 'Started a new chat for all releases.';
}

function retry(key: string, turnId: string) {
  const { inFlight, turns } = getSession(key);
  const failed = turns.find((t) => t.id === turnId);
  if (inFlight || !failed) return;
  setSession(key, (s) => ({ ...s, turns: s.turns.filter((t) => t.id !== turnId) }));
  ask(key, failed.question);
}

export const chatActions = { ask, stop, newChat, setScope, retry, getSession };

export function useChatSession(key: string): Session {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => getSession(key),
    () => EMPTY_SESSION,
  );
}
