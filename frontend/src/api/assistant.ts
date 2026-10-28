import { useQuery } from '@tanstack/react-query';
import { apiFetch, apiFetchRaw } from './client';

// Mirrors the server @Size on the question; leaves room for the scoped-question prefix under rag-service's 2000-char limit.
export const QUESTION_MAX_CHARS = 1500;

export type HistoryMessage = { role: 'user' | 'assistant'; content: string };

export type ChatRequestBody = { question: string; history: HistoryMessage[]; releaseId: string | null };

export type SourceView = {
  n: number;
  kind: 'TICKET' | 'DOCUMENT' | 'MANUAL_ITEM' | 'CODE_LINKS' | 'TOOL';
  title: string | null;
  releaseId: string | null;
  releaseVersion: string | null;
  ticketKey: string | null;
  documentType: 'RELEASE_NOTES' | 'RUNBOOK' | 'APPROVAL' | null;
  revision: number | null;
};

export type ToolView = { round: number; name: string; arguments: Record<string, unknown>; error: boolean };

export type DoneEvent = {
  refused: boolean;
  sources: SourceView[];
  tools: ToolView[];
  historyTrimmed: boolean;
  meta: { model: string | null; promptHash: string | null };
};

export type ErrorOutcome = 'OFFLINE' | 'BUSY' | 'MODEL_MISSING' | 'DEADLINE' | 'FAILED';

export type ErrorEvent = { outcome: ErrorOutcome; retryAfterSeconds: number | null };

export type StreamEvent =
  | { event: 'status'; data: { tool: string } }
  | { event: 'discard'; data: Record<string, never> }
  | { event: 'token'; data: { text: string } }
  | { event: 'done'; data: DoneEvent }
  | { event: 'error'; data: ErrorEvent };

export type ParsedSseEvent = { event: string; data: unknown };

// Consumes every complete (blank-line terminated) block of the buffer and returns the unconsumed tail.
export function parseSse(buffer: string): { events: ParsedSseEvent[]; rest: string } {
  const normalized = buffer.replace(/\r\n/g, '\n');
  const blocks = normalized.split('\n\n');
  const rest = blocks.pop() ?? '';
  const events: ParsedSseEvent[] = [];
  for (const block of blocks) {
    let event = 'message';
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith(':')) continue;
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
    }
    if (data.length > 0) events.push({ event, data: JSON.parse(data.join('\n')) });
  }
  return { events, rest };
}

const KNOWN_EVENTS = new Set(['status', 'discard', 'token', 'done', 'error']);

function abortError(): DOMException {
  return new DOMException('The operation was aborted.', 'AbortError');
}

export async function streamChat(
  body: ChatRequestBody,
  signal: AbortSignal,
  onEvent: (event: StreamEvent) => void,
): Promise<void> {
  await streamSse('/api/assistant/chat', body, signal, (parsed) => {
    if (KNOWN_EVENTS.has(parsed.event)) onEvent(parsed as StreamEvent);
  });
}

// POSTs `body` and dispatches every parsed SSE event, in order, to `onEvent`; filtering by event name is the caller's job.
export async function streamSse(
  path: string,
  body: unknown,
  signal: AbortSignal,
  onEvent: (event: ParsedSseEvent) => void,
): Promise<void> {
  const response = await apiFetchRaw(path, {
    method: 'POST',
    body: JSON.stringify(body),
    signal,
    headers: { Accept: 'text/event-stream, application/problem+json' },
  });
  if (!response.body) throw new Error('Stream response has no body');
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  signal.addEventListener('abort', () => void reader.cancel().catch(() => undefined), { once: true });
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (signal.aborted) throw abortError();
    if (done) return;
    buffer += decoder.decode(value, { stream: true });
    const parsed = parseSse(buffer);
    buffer = parsed.rest;
    for (const parsedEvent of parsed.events) onEvent(parsedEvent);
  }
}

const TOOL_LABELS: Record<string, string> = {
  deployments: 'Checking deployments…',
  releases: 'Looking up release details…',
  tickets: 'Looking up release details…',
  documents: 'Checking documents…',
};

export function statusLabel(tool: string | null): string {
  if (tool === null) return 'Thinking…';
  return TOOL_LABELS[tool] ?? 'Looking things up…';
}

export type AssistantStatus = { enabled: false } | { enabled: true; starters: { unscoped: string[]; release: string[] } };

export const assistantStatusQueryOptions = {
  queryKey: ['assistant', 'status'] as const,
  queryFn: () => apiFetch<AssistantStatus>('/api/assistant/status'),
  staleTime: 5 * 60_000,
};

export function useAssistantStatus(enabled: boolean) {
  return useQuery({ ...assistantStatusQueryOptions, enabled });
}

export function useAssistantFreshness(enabled: boolean) {
  return useQuery({
    queryKey: ['assistant', 'freshness'],
    queryFn: () => apiFetch<{ lastIndexedAt: string | null }>('/api/assistant/freshness'),
    staleTime: 60_000,
    refetchOnWindowFocus: true,
    enabled,
  });
}
