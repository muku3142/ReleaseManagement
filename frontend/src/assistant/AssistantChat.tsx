import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react';
import { QUESTION_MAX_CHARS, statusLabel } from '../api/assistant';
import { AssistantTurn } from './AssistantTurn';
import { chatActions, useChatSession, type TurnStatus } from './chatStore';

const COUNTER_FROM_CHARS = 1300;
const FOLLOW_SCROLL_PX = 80;
const ANNOUNCE_INTERVAL_MS = 1000;

const BANNERS = {
  OFFLINE: {
    style: 'border-amber-600 bg-amber-50 text-amber-700',
    title: 'The assistant is offline.',
    body: "The search service isn't reachable right now. Your question wasn't answered. Try again in a few minutes.",
  },
  MODEL_MISSING: {
    style: 'border-red-600 bg-red-50 text-red-700',
    title: "The assistant's language model isn't installed.",
    body: 'Ask an administrator to pull the configured chat model on the assistant host.',
  },
};

function announcementFor(status: TurnStatus, tool: string | null): string | null {
  if (status === 'done') return 'Answer ready.';
  if (status === 'stopped') return 'Stopped.';
  if (status === 'streaming' && tool !== null) return statusLabel(tool);
  return null;
}

type ChatProps = { sessionKey: string; starters: string[]; scopeRow: ReactNode };

export function AssistantChat({ sessionKey, starters, scopeRow }: ChatProps) {
  const session = useChatSession(sessionKey);
  const [draft, setDraft] = useState('');
  const [announcement, setAnnouncement] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const following = useRef(true);
  const lastAnnounced = useRef(0);
  const wasInFlight = useRef(false);

  const streaming = session.inFlight !== null;
  const lastTurn = session.turns[session.turns.length - 1];
  const canSend = draft.trim() !== '' && !streaming;
  const scopeLabel = session.scope?.label ?? null;

  useEffect(() => {
    const onScroll = () => {
      const bottom = bottomRef.current?.getBoundingClientRect().bottom ?? 0;
      following.current = bottom - window.innerHeight < FOLLOW_SCROLL_PX;
    };
    window.addEventListener('scroll', onScroll, { capture: true, passive: true });
    return () => window.removeEventListener('scroll', onScroll, { capture: true });
  }, []);

  useEffect(() => {
    if (following.current) bottomRef.current?.scrollIntoView?.({ block: 'end' });
  }, [lastTurn?.text, lastTurn?.status, session.turns.length]);

  useEffect(() => {
    if (wasInFlight.current && !streaming) textareaRef.current?.focus();
    wasInFlight.current = streaming;
  }, [streaming]);

  const lastStatus = lastTurn?.status;
  const lastTool = lastTurn?.statusTool;
  useEffect(() => {
    if (!lastStatus) return;
    const message = announcementFor(lastStatus, lastTool ?? null);
    const now = Date.now();
    if (message === null || (lastStatus === 'streaming' && now - lastAnnounced.current < ANNOUNCE_INTERVAL_MS)) return;
    lastAnnounced.current = now;
    setAnnouncement(message);
  }, [lastStatus, lastTool]);

  useEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${el.scrollHeight}px`;
  }, [draft]);

  function submit(question: string) {
    setAnnouncement('Assistant is working.');
    lastAnnounced.current = Date.now();
    chatActions.ask(sessionKey, question);
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!canSend) return;
    submit(draft.trim());
    setDraft('');
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      onSubmit(event);
    } else if (event.key === 'Escape' && streaming) {
      chatActions.stop(sessionKey);
    }
  }

  const banner = session.banner ? BANNERS[session.banner] : null;
  const visibleStarters = starters.map((s) => (scopeLabel ? s.replaceAll('{release}', scopeLabel) : s));

  return (
    <div className="flex flex-col">
      <div className="flex-1 space-y-4">
        {session.turns.length === 0 ? (
          <div className="py-12 text-center">
            <h3 className="text-base font-semibold">
              {scopeLabel ? `Ask about ${scopeLabel}` : 'Ask about your releases'}
            </h3>
            <p className="mt-2 text-xs text-gray-600">
              Answers come from your releases, tickets, deployments, documents and linked code. Every answer shows where
              it came from.
            </p>
            {visibleStarters.length > 0 && (
              <div className="mx-auto mt-4 flex max-w-md flex-col gap-2">
                {visibleStarters.map((starter) => (
                  <button
                    key={starter}
                    type="button"
                    disabled={streaming}
                    onClick={() => submit(starter)}
                    className="w-full rounded border border-gray-300 px-3 py-1.5 text-left text-sm text-gray-700 focus-visible:ring-2 focus-visible:ring-blue-600"
                  >
                    {starter}
                  </button>
                ))}
              </div>
            )}
          </div>
        ) : (
          session.turns.map((turn) => (
            <AssistantTurn key={turn.id} turn={turn} onRetry={() => chatActions.retry(sessionKey, turn.id)} />
          ))
        )}
        <div ref={bottomRef} />
      </div>

      <div className="sticky bottom-0 bg-gray-50 pt-2 pb-4">
        {banner && (
          <div role="alert" className={`mb-2 rounded border p-4 text-sm ${banner.style}`}>
            <p className="font-semibold">{banner.title}</p>
            <p>{banner.body}</p>
          </div>
        )}
        <div className="mb-2">{scopeRow}</div>
        <form onSubmit={onSubmit} className="flex items-end gap-2">
          <div className="grow">
            <textarea
              ref={textareaRef}
              rows={3}
              maxLength={QUESTION_MAX_CHARS}
              aria-label="Ask a question"
              placeholder="Ask a question…"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={onKeyDown}
              className="max-h-48 w-full resize-none overflow-y-auto rounded border border-gray-300 p-2 text-sm focus-visible:ring-2 focus-visible:ring-blue-600"
            />
            {draft.length > COUNTER_FROM_CHARS && (
              <p className="text-xs text-amber-700">{`${draft.length}/${QUESTION_MAX_CHARS}`}</p>
            )}
          </div>
          {streaming ? (
            <button
              type="button"
              aria-label="Stop generating"
              onClick={() => chatActions.stop(sessionKey)}
              onKeyDown={(e) => e.key === 'Escape' && chatActions.stop(sessionKey)}
              className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700 focus-visible:ring-2 focus-visible:ring-blue-600"
            >
              Stop
            </button>
          ) : (
            <button
              type="submit"
              disabled={!canSend}
              className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-blue-600"
            >
              Send
            </button>
          )}
        </form>
      </div>
      <div role="status" aria-live="polite" aria-atomic="true" className="sr-only">
        {announcement}
      </div>
    </div>
  );
}
