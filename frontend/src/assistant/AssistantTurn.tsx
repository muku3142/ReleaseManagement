import { useEffect, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { statusLabel, type ErrorOutcome, type SourceView, type ToolView } from '../api/assistant';
import { chipFor } from './citations';
import type { Turn } from './chatStore';

const CHIP_CLASS = 'max-w-full truncate rounded border border-gray-200 bg-gray-50 px-2 py-0.5 text-xs';
const SPINNER_CLASS =
  'h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600 motion-reduce:animate-none';
const BUTTON_CLASS =
  'mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-blue-600';

type ErrorCopy = { title: string; body: string; style: 'amber' | 'red'; retry: boolean };

const ERROR_COPY: Record<Exclude<ErrorOutcome, 'BUSY'>, ErrorCopy> = {
  OFFLINE: {
    title: 'The assistant is offline.',
    body: "The search service isn't reachable right now. Your question wasn't answered. Try again in a few minutes.",
    style: 'amber',
    retry: true,
  },
  MODEL_MISSING: {
    title: "The assistant's language model isn't installed.",
    body: 'Ask an administrator to pull the configured chat model on the assistant host.',
    style: 'red',
    retry: false,
  },
  DEADLINE: {
    title: 'That took too long and was stopped.',
    body: 'Try a narrower question, or name a specific release or ticket.',
    style: 'amber',
    retry: true,
  },
  FAILED: {
    title: 'Something went wrong.',
    body: "The assistant couldn't finish this answer. Try again, or rephrase the question.",
    style: 'red',
    retry: true,
  },
};

const BANNER_STYLE = {
  amber: 'border-amber-600 bg-amber-50 text-amber-700',
  red: 'border-red-600 bg-red-50 text-red-700',
};

function SourceChip({ source }: { source: SourceView }) {
  const { label, target } = chipFor(source);
  const className = `${CHIP_CLASS} ${target ? 'text-blue-600 hover:underline' : 'text-gray-700'}`;
  return (
    <li className="max-w-full" role="listitem">
      {target?.to === '/releases/$releaseId' ? (
        <Link to={target.to} params={target.params} hash={target.hash} title={label} className={`block ${className}`}>
          {label}
        </Link>
      ) : target ? (
        <Link
          to={target.to}
          params={target.params}
          search={target.search}
          title={label}
          className={`block ${className}`}
        >
          {label}
        </Link>
      ) : (
        <span title={label} className={`block ${CHIP_CLASS} text-gray-700`}>
          {label}
        </span>
      )}
    </li>
  );
}

function formatValue(value: unknown): string {
  return typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value);
}

function ToolRow({ tool }: { tool: ToolView }) {
  const name = tool.name.charAt(0).toUpperCase() + tool.name.slice(1);
  const args = Object.entries(tool.arguments).map(([key, value]) => `${key}: ${formatValue(value)}`);
  return (
    <li>
      {name}
      {args.length > 0 && <span className="font-mono text-xs">{` · ${args.join(' · ')}`}</span>}
    </li>
  );
}

function TracePanel({ turn }: { turn: Turn }) {
  return (
    <details className="mt-2 text-xs">
      <summary className="cursor-pointer font-semibold text-gray-600">How I answered</summary>
      <div className="mt-2 space-y-2 border-t border-gray-100 pt-2 text-gray-600">
        <div>
          <p className="font-semibold">Tools used</p>
          {turn.tools.length === 0 ? (
            <p>No lookups were needed.</p>
          ) : (
            <ul className="list-disc pl-4">
              {turn.tools.map((tool, i) => (
                <ToolRow key={i} tool={tool} />
              ))}
            </ul>
          )}
        </div>
        <div>
          <p className="font-semibold">Sources used</p>
          {turn.sources.length === 0 ? (
            <p>No sources were used.</p>
          ) : (
            <ul className="list-none">
              {turn.sources.map((source) => (
                <li key={source.n}>{chipFor(source).label}</li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </details>
  );
}

function BusyBanner({ seconds, onRetry }: { seconds: number | null; onRetry: () => void }) {
  const [remaining, setRemaining] = useState(seconds ?? 0);

  useEffect(() => {
    const timer = setInterval(() => setRemaining((r) => Math.max(0, r - 1)), 1000);
    return () => clearInterval(timer);
  }, []);

  const sentence = seconds === null ? 'Try again in a moment.' : `Try again in ${seconds} seconds.`;
  return (
    <div role="status" className={`rounded border p-4 text-sm ${BANNER_STYLE.amber}`}>
      <p className="font-semibold">The assistant is busy with another question.</p>
      <p className="sr-only">{sentence}</p>
      <p aria-hidden="true">{seconds === null ? sentence : `Try again in ${remaining} seconds.`}</p>
      <button type="button" className={BUTTON_CLASS} disabled={remaining > 0} onClick={onRetry}>
        Try again
      </button>
    </div>
  );
}

function ErrorBanner({ turn, onRetry }: { turn: Turn; onRetry: () => void }) {
  const outcome = turn.outcome ?? 'FAILED';
  if (outcome === 'BUSY') return <BusyBanner seconds={turn.retryAfterSeconds} onRetry={onRetry} />;
  const copy = ERROR_COPY[outcome];
  return (
    <div role="alert" className={`rounded border p-4 text-sm ${BANNER_STYLE[copy.style]}`}>
      <p className="font-semibold">{copy.title}</p>
      <p>{copy.body}</p>
      {copy.retry && (
        <button type="button" className={BUTTON_CLASS} onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

function StatusLine({ turn }: { turn: Turn }) {
  const label = statusLabel(turn.phase === 'thinking' ? null : turn.statusTool);
  return (
    <div className="flex items-center gap-2 text-xs text-gray-600">
      <span className={SPINNER_CLASS} aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function AssistantTurn({ turn, onRetry }: { turn: Turn; onRetry: () => void }) {
  const streaming = turn.status === 'streaming';
  const showText = turn.status !== 'refused' && turn.text !== '';
  const showStatus = streaming && (turn.phase !== 'answer' || turn.text === '');
  const incomplete = turn.status === 'stopped' || (turn.status === 'error' && turn.outcome === 'DEADLINE' && showText);

  return (
    <div className="space-y-4">
      <div className="ml-12 rounded bg-gray-100 p-4">
        <p className="text-xs font-semibold text-gray-500">You</p>
        <p className="whitespace-pre-wrap break-words text-sm">{turn.question}</p>
      </div>
      <div className="mr-12 rounded border border-gray-200 bg-white p-4">
        <p className="text-xs font-semibold text-gray-500">Assistant</p>
        {turn.historyTrimmed && (
          <p className="text-xs text-amber-700">
            Older turns weren&apos;t used for this answer. Start a new chat to include everything.
          </p>
        )}
        {showStatus && <StatusLine turn={turn} />}
        {turn.status === 'refused' ? (
          <div className="mt-2 rounded border border-gray-200 bg-gray-100 p-4 text-sm text-gray-600">
            <p>I can&apos;t answer that from the data I have access to.</p>
            <p className="text-xs">
              Try naming a release, ticket or document, or ask about something recorded in this tool.
            </p>
          </div>
        ) : (
          <div
            tabIndex={-1}
            aria-live="off"
            aria-busy={streaming}
            className="whitespace-pre-wrap break-words text-sm"
          >
            {showText ? turn.text : null}
          </div>
        )}
        {incomplete && <p className="text-xs text-gray-500">Stopped. This answer is incomplete.</p>}
        {turn.status === 'error' && (
          <div className="mt-2">
            <ErrorBanner turn={turn} onRetry={onRetry} />
          </div>
        )}
        {turn.status === 'done' && turn.sources.length > 0 && (
          <ul role="list" aria-label="Sources" className="mt-2 flex flex-wrap gap-1">
            {turn.sources.map((source) => (
              <SourceChip key={source.n} source={source} />
            ))}
          </ul>
        )}
        {(turn.status === 'done' || (turn.status === 'refused' && turn.tools.length > 0)) && <TracePanel turn={turn} />}
      </div>
    </div>
  );
}
