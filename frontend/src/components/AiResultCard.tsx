import { useEffect, useId, useLayoutEffect, useReducer, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { messageForReason } from '../api/reasonMessages';
import { ApiError, triggerDownload } from '../api/client';
import {
  streamTransform,
  tooLargeFrom,
  useSaveAiRevision,
  type TransformErrorOutcome,
  type TransformEvent,
  type TransformMode,
} from '../api/documentAssist';
import type { DocumentType } from '../api/templates';
import { factDiff, type FactDiff } from '../documentAssist/factDiff';
import {
  canSave,
  initialResultState,
  resultReducer,
  type IncompleteCause,
  type ResultState,
} from '../documentAssist/resultState';
import { MarkdownBody } from './RenderedDocument';

export type AiResultCardProps = {
  releaseId: string;
  documentType: DocumentType;
  sourceRevision: number;
  originalMarkdown: string;
  mode: TransformMode;
  instruction: string | null;
  downloadName: string;
  onClose: (reason: 'discarded' | 'closed') => void;
  onSaved: (revision: number) => void;
  onTooLarge: (characters: number, maxCharacters: number) => void;
};

const TITLES: Record<TransformMode, string> = {
  CLEAN_UP: 'Cleaned-up version',
  SUMMARIZE: 'Summary',
  CUSTOM: 'Custom result',
};

const BUTTON = 'rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-blue-600';
const AMBER = 'rounded border border-amber-600 bg-amber-50 p-4 text-sm text-amber-700';
const RED = 'rounded border border-red-600 bg-red-50 p-4 text-sm text-red-700';
const MONO = 'whitespace-pre-wrap break-words p-4 font-mono text-sm';
const SPINNER = 'h-4 w-4 animate-spin motion-reduce:animate-none rounded-full border-2 border-gray-300 border-t-blue-600';
const CHIP_CAP = 10;
const DEBOUNCE_MS = 500;
const COPIED_MS = 2000;

type Blocked = { title: string; body: string; retry: boolean; style: 'amber' | 'red' };

const BLOCKED: Record<IncompleteCause, Blocked | null> = {
  length: {
    title: 'This result is incomplete.',
    body: "The model ran out of room before the end of the document, so the last sections may be missing. It can't be saved. Copy what you need, or discard it and try again.",
    retry: false,
    style: 'amber',
  },
  deadline: {
    title: 'That took too long and was stopped.',
    body: "This result is incomplete and can't be saved. Try again, or use a shorter document.",
    retry: true,
    style: 'amber',
  },
  failed: {
    title: 'Something went wrong.',
    body: "The result is incomplete and can't be saved. Your document is unchanged. Try again.",
    retry: true,
    style: 'amber',
  },
  stopped: null,
};

type ErrorCopy = { title: string; body: string; style: 'amber' | 'red'; role: 'alert' | 'status'; retry: boolean };

const ERROR_COPY: Record<Exclude<TransformErrorOutcome, 'BUSY' | 'TOO_LARGE'>, ErrorCopy> = {
  OFFLINE: {
    title: 'The assistant is offline.',
    body: "The AI service isn't reachable right now. Your document is unchanged and document generation still works. Try again in a few minutes.",
    style: 'amber',
    role: 'alert',
    retry: true,
  },
  MODEL_MISSING: {
    title: "The assistant's language model isn't installed.",
    body: 'Ask an administrator to pull the configured chat model on the assistant host.',
    style: 'red',
    role: 'alert',
    retry: false,
  },
  DEADLINE: {
    title: 'That took too long and was stopped.',
    body: "This result is incomplete and can't be saved. Try again, or use a shorter document.",
    style: 'amber',
    role: 'alert',
    retry: true,
  },
  FAILED: {
    title: 'Something went wrong.',
    body: "The assistant couldn't finish this result. Your document is unchanged. Try again.",
    style: 'red',
    role: 'alert',
    retry: true,
  },
};

function Banner({ title, body, style, role, children }: { title: string; body: string; style: 'amber' | 'red'; role: 'alert' | 'status'; children?: ReactNode }) {
  return (
    <div role={role} className={style === 'amber' ? AMBER : RED}>
      <p className="font-semibold">{title}</p>
      <p>{body}</p>
      {children}
    </div>
  );
}

function ChipGroup({ label, items }: { label: string; items: string[] }) {
  if (items.length === 0) return null;
  return (
    <div className="mt-2">
      <p className="text-xs font-semibold">{label}</p>
      <ul className="flex flex-wrap gap-1">
        {items.slice(0, CHIP_CAP).map((item) => (
          <li key={item} className="rounded border border-amber-600 bg-white px-2 py-0.5 font-mono text-xs text-amber-700">
            {item}
          </li>
        ))}
      </ul>
      {items.length > CHIP_CAP && <p className="text-xs">{`and ${items.length - CHIP_CAP} more`}</p>}
    </div>
  );
}

function FactCheck({ diff, id }: { diff: FactDiff; id: string }) {
  const missing = [...diff.missingKeys, ...diff.missingVersions];
  const added = [...diff.addedKeys, ...diff.addedVersions];
  if (missing.length === 0 && added.length === 0) {
    return (
      <p id={id} aria-live="off" className="text-xs text-gray-600">
        All ticket keys and versions in the original are present, and none were added. Other wording changes still need your review.
      </p>
    );
  }
  return (
    <div id={id} aria-live="off" className={AMBER}>
      <p className="font-semibold">Check before saving: ticket keys or versions differ from the original.</p>
      <ChipGroup label="Missing from the result" items={missing} />
      <ChipGroup label="Not in the original" items={added} />
      <p className="mt-2 text-xs">You can still save. Look at each one beside the original first.</p>
    </div>
  );
}

function bodyText(state: ResultState): string {
  switch (state.phase) {
    case 'streaming':
    case 'incomplete':
      return state.text;
    case 'complete':
    case 'saving':
      return state.buffer;
    default:
      return '';
  }
}

export function AiResultCard({
  releaseId,
  documentType,
  sourceRevision,
  originalMarkdown,
  mode,
  instruction,
  downloadName,
  onClose,
  onSaved,
  onTooLarge,
}: AiResultCardProps) {
  const [state, dispatch] = useReducer(resultReducer, initialResultState);
  const [preview, setPreview] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [tooLarge, setTooLarge] = useState(false);
  const [copy, setCopy] = useState<'idle' | 'copied' | 'failed'>('idle');
  const [announcement, setAnnouncement] = useState('');
  const [diff, setDiff] = useState<FactDiff | null>(null);
  const [shownChars, setShownChars] = useState(0);
  const [secondsLeft, setSecondsLeft] = useState(0);
  const save = useSaveAiRevision();
  const ids = { blocked: useId(), fact: useId(), helper: useId() };

  const controllerRef = useRef<AbortController | null>(null);
  const charsRef = useRef(0);
  const rootRef = useRef<HTMLDivElement>(null);
  const stopRef = useRef<HTMLButtonElement>(null);
  const bodyRef = useRef<HTMLTextAreaElement & HTMLDivElement>(null);
  const keepRef = useRef<HTMLButtonElement>(null);
  const discardRef = useRef<HTMLButtonElement>(null);
  const previousPhase = useRef(state.phase);
  const [progress, setProgress] = useState<{ section: number; sections: number } | null>(null);

  const text = bodyText(state);
  charsRef.current = text.length;
  const editable = state.phase === 'complete' || state.phase === 'saving';
  const running = state.phase === 'waiting' || state.phase === 'streaming';
  const edited = (state.phase === 'complete' || state.phase === 'saving') && state.edited;
  const verifyBanner = documentType !== 'RELEASE_NOTES';
  const hasText = text.length > 0;

  function start() {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    const live = () => controllerRef.current === controller && !controller.signal.aborted;
    setAnnouncement('Assistant is working.');
    setProgress(null);
    const onEvent = (event: TransformEvent) => {
      if (!live()) return;
      if (event.event === 'token') dispatch({ type: 'token', text: event.data.text });
      else if (event.event === 'progress') setProgress(event.data);
      else if (event.event === 'remainder') dispatch({ type: 'remainder', keep: event.data.keep, text: event.data.text });
      else if (event.event === 'done') dispatch({ type: 'done', complete: event.data.complete, truncated: event.data.truncated });
      else if (event.data.outcome === 'TOO_LARGE' && event.data.characters !== null && event.data.maxCharacters !== null) {
        setTooLarge(true);
        onTooLarge(event.data.characters, event.data.maxCharacters);
      } else {
        dispatch({ type: 'error', outcome: event.data.outcome === 'TOO_LARGE' ? 'FAILED' : event.data.outcome, retryAfterSeconds: event.data.retryAfterSeconds });
      }
    };
    streamTransform({ releaseId, documentType, sourceRevision, mode, instruction }, controller.signal, onEvent).then(
      () => {
        if (live()) dispatch({ type: 'ended' });
      },
      (error: unknown) => {
        if (!live()) return;
        const large = tooLargeFrom(error);
        if (large) {
          setTooLarge(true);
          onTooLarge(large.characters, large.maxCharacters);
        } else if (error instanceof ApiError && error.problem.status === 401) {
          window.location.assign('/login');
        } else {
          dispatch({ type: 'ended' });
        }
      },
    );
  }

  useEffect(() => {
    rootRef.current?.scrollIntoView?.({ block: 'start' });
    start();
    return () => controllerRef.current?.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- one run per mount; Try again calls start() itself
  }, []);

  useEffect(() => {
    if (state.phase === 'waiting') stopRef.current?.focus();
  }, [state.phase]);

  useEffect(() => {
    const from = previousPhase.current;
    previousPhase.current = state.phase;
    if (from !== 'waiting' && from !== 'streaming') return;
    if (state.phase === 'complete') {
      setAnnouncement(state.partial ? 'Result incomplete. The rest of the document was kept as it was.' : 'Result ready.');
    }
    if (state.phase === 'incomplete') setAnnouncement(state.cause === 'stopped' ? 'Stopped.' : 'Result incomplete.');
    if (state.phase === 'complete' || state.phase === 'incomplete') bodyRef.current?.focus();
  }, [state]);

  useEffect(() => {
    if (state.phase !== 'streaming') return;
    const timer = setInterval(() => setShownChars(charsRef.current), 1000);
    return () => clearInterval(timer);
  }, [state.phase]);

  useLayoutEffect(() => {
    if (state.phase === 'error') setSecondsLeft(state.retryAfterSeconds ?? 0);
  }, [state]);

  useEffect(() => {
    if (secondsLeft <= 0) return;
    const timer = setTimeout(() => setSecondsLeft((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [secondsLeft]);

  const factBuffer = editable ? text : null;
  useEffect(() => {
    if (factBuffer === null) return;
    if (diff === null) {
      setDiff(factDiff(originalMarkdown, factBuffer));
      return;
    }
    const timer = setTimeout(() => setDiff(factDiff(originalMarkdown, factBuffer)), DEBOUNCE_MS);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- diff only seeds the first computation
  }, [factBuffer, originalMarkdown]);

  useEffect(() => {
    if (confirming) keepRef.current?.focus();
  }, [confirming]);

  useEffect(() => {
    if (copy !== 'copied') return;
    const timer = setTimeout(() => setCopy('idle'), COPIED_MS);
    return () => clearTimeout(timer);
  }, [copy]);

  function stop() {
    controllerRef.current?.abort();
    dispatch({ type: 'stop' });
  }

  function retry() {
    setPreview(false);
    setDiff(null);
    setShownChars(0);
    dispatch({ type: 'retry' });
    start();
  }

  function onKeyDown(event: KeyboardEvent) {
    if (event.key !== 'Escape') return;
    if (confirming) {
      setConfirming(false);
      discardRef.current?.focus();
    } else if (running) {
      stop();
    }
  }

  function discard() {
    if (edited) setConfirming(true);
    else onClose('discarded');
  }

  function onSave() {
    if (state.phase !== 'complete' || !canSave(state)) return;
    dispatch({ type: 'save-start' });
    save.mutate(
      { releaseId, documentType, sourceRevision, mode, instruction, markdown: state.buffer },
      {
        onSuccess: (saved) => onSaved(saved.revision),
        onError: (error) =>
          dispatch({
            type: 'save-failed',
            message: error instanceof ApiError ? messageForReason(error.problem) : 'The server could not be reached.',
          }),
      },
    );
  }

  function onCopy() {
    navigator.clipboard.writeText(text).then(
      () => {
        setCopy('copied');
        setAnnouncement('Copied to clipboard.');
      },
      () => setCopy('failed'),
    );
  }

  if (tooLarge) return null;

  const saveActive = canSave(state);
  const blocked = state.phase === 'incomplete' ? BLOCKED[state.cause] : null;
  const emptyBuffer = editable && text.trim() === '';
  const partial = state.phase === 'complete' || state.phase === 'saving' ? state.partial : null;
  const blockedId = blocked || partial || emptyBuffer || (state.phase === 'incomplete' && state.cause === 'stopped') ? ids.blocked : undefined;
  const saving = state.phase === 'saving';
  const showSave = state.phase === 'complete' || state.phase === 'incomplete' || saving;
  const describedBy = [blockedId, editable && !emptyBuffer ? ids.fact : undefined, ids.helper].filter(Boolean).join(' ');
  const toggleDisabled = running || !hasText || state.phase === 'error';

  const body = (() => {
    if (state.phase === 'error') return null;
    if (running && state.phase === 'waiting') {
      return (
        <p className="flex items-center gap-2 p-4 text-xs text-gray-600">
          <span className={SPINNER} aria-hidden="true" />
          Waiting for the model…
        </p>
      );
    }
    if (state.phase === 'streaming') {
      return (
        <div>
          <p className="flex items-center gap-2 px-4 pt-4 text-xs text-gray-600">
            <span className={SPINNER} aria-hidden="true" />
            {`${progress && progress.sections > 1 ? `Section ${progress.section} of ${progress.sections} · ` : ''}Writing… ${(shownChars || state.text.length).toLocaleString()} characters so far`}
          </p>
          <p className="px-4 text-xs text-gray-600">Long documents can take a few minutes on this server. You can stop at any time.</p>
          <div aria-live="off" aria-busy="true" className={MONO}>
            {state.text}
          </div>
        </div>
      );
    }
    if (preview) return <MarkdownBody markdown={text} blockImages />;
    if (editable) {
      return (
        <textarea
          ref={bodyRef}
          aria-label="Edited result (Markdown)"
          spellCheck={false}
          readOnly={saving}
          value={text}
          onChange={(e) => dispatch({ type: 'edit', buffer: e.target.value })}
          className="block min-h-96 w-full resize-y p-4 font-mono text-sm focus-visible:ring-2 focus-visible:ring-blue-600"
        />
      );
    }
    return (
      <div ref={bodyRef} tabIndex={-1} aria-label="Result source" className={MONO}>
        {text}
      </div>
    );
  })();

  const error = state.phase === 'error' ? state : null;
  const errorCopy = error && error.outcome !== 'BUSY' && error.outcome !== 'TOO_LARGE' ? ERROR_COPY[error.outcome] : null;

  return (
    <div ref={rootRef} onKeyDown={onKeyDown} className="rounded border border-gray-200 bg-white">
      <div className="flex items-center gap-2 border-b border-gray-200 p-4">
        <h2 className="text-base font-semibold">{TITLES[mode]}</h2>
        {edited && <span className="text-xs text-gray-500">Edited</span>}
        <span className="grow" />
        <div role="group" aria-label="Result view" className="flex gap-1">
          <button
            type="button"
            aria-pressed={!preview}
            disabled={toggleDisabled}
            onClick={() => setPreview(false)}
            className={`${BUTTON} ${!preview ? 'bg-gray-100 font-semibold text-gray-900' : ''}`}
          >
            {editable ? 'Edit' : 'Source'}
          </button>
          <button
            type="button"
            aria-pressed={preview}
            disabled={toggleDisabled}
            onClick={() => setPreview(true)}
            className={`${BUTTON} ${preview ? 'bg-gray-100 font-semibold text-gray-900' : ''}`}
          >
            Preview
          </button>
        </div>
      </div>

      {verifyBanner && (
        <div className="border-b border-amber-600 bg-amber-50 p-4 text-sm text-amber-700">
          <p className="font-semibold">Verify steps before use.</p>
          <p>AI-assisted text can change commands, order, values or risk wording. Compare every step with the original before you rely on it.</p>
        </div>
      )}

      <div className="max-h-[70vh] min-h-96 overflow-y-auto">
        {error && errorCopy && <Banner {...errorCopy} />}
        {error && error.outcome === 'BUSY' && (
          <Banner
            title="The assistant is busy with another question."
            body={error.retryAfterSeconds === null ? 'Try again in a moment.' : `Try again in ${secondsLeft} seconds.`}
            style="amber"
            role="status"
          />
        )}
        {body}
      </div>

      <div className="flex flex-col gap-2 px-4 pt-4">
        {state.phase === 'incomplete' && state.cause === 'stopped' && (
          <p className="text-xs text-gray-500">Stopped. This result is incomplete.</p>
        )}
        {partial && (
          <div id={ids.blocked}>
            <Banner
              title={partial === 'deadline' ? 'That took too long and was stopped.' : 'Something went wrong partway through.'}
              body={`${progress ? `Stopped at section ${progress.section} of ${progress.sections}. That section and the ones after it were not processed` : 'The sections after the stop point were not processed'} and are kept as they were. You can save this result, or try again.`}
              style="amber"
              role="alert"
            >
              <button type="button" onClick={retry} disabled={saving} className={`${BUTTON} mt-2`}>
                Try again
              </button>
            </Banner>
          </div>
        )}
        {blocked && (
          <div id={ids.blocked}>
            <Banner title={blocked.title} body={blocked.body} style={blocked.style} role="alert">
              {blocked.retry && (
                <button type="button" onClick={retry} className={`${BUTTON} mt-2`}>
                  Try again
                </button>
              )}
            </Banner>
          </div>
        )}
        {state.phase === 'incomplete' && state.cause === 'stopped' && (
          <p id={ids.blocked} role="alert" className="text-sm text-amber-700">
            A stopped result can&apos;t be saved.
          </p>
        )}
        {emptyBuffer && <Banner title="Nothing to save." body="The result is empty." style="amber" role="alert" />}
        {editable && !emptyBuffer && diff && <FactCheck diff={diff} id={ids.fact} />}
        {state.phase === 'complete' && state.saveError && (
          <p role="alert" className={RED}>
            {`Couldn't save the revision — ${state.saveError} Your result is still here. Try again.`}
          </p>
        )}
        {copy === 'failed' && <p className="text-xs text-amber-700">Couldn&apos;t copy. Select the text and copy it manually.</p>}
      </div>

      {confirming ? (
        <div aria-label="Confirm discard" role="region" className="flex flex-col gap-2 p-4">
          <p className="text-sm font-semibold">Discard your edits?</p>
          <p className="text-sm">Your changes to this result will be lost. The original revision is not affected.</p>
          <div className="flex gap-2">
            <button
              ref={keepRef}
              type="button"
              onClick={() => {
                setConfirming(false);
                discardRef.current?.focus();
              }}
              className={BUTTON}
            >
              Keep editing
            </button>
            <button
              type="button"
              onClick={() => onClose('discarded')}
              className="rounded border border-red-600 px-3 py-1.5 text-sm text-red-700 focus-visible:ring-2 focus-visible:ring-blue-600"
            >
              Discard edits
            </button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap items-center gap-2 border-t border-gray-200 p-4">
          {running && (
            <button ref={stopRef} type="button" aria-label="Stop generating" onClick={stop} className={BUTTON}>
              Stop
            </button>
          )}
          {showSave && (
            <button
              type="button"
              aria-disabled={!saveActive || undefined}
              disabled={saving}
              aria-describedby={describedBy}
              onClick={saveActive ? onSave : undefined}
              className={`flex items-center gap-2 rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white focus-visible:ring-2 focus-visible:ring-blue-600 ${
                saveActive && !saving ? '' : 'cursor-not-allowed opacity-50'
              }`}
            >
              {saving && <span className={SPINNER} aria-hidden="true" />}
              {saving ? 'Saving…' : `Accept and save as revision ${sourceRevision + 1}`}
            </button>
          )}
          {(state.phase === 'complete' || state.phase === 'incomplete' || saving) && (
            <>
              <button type="button" disabled={saving} onClick={onCopy} className={BUTTON}>
                {copy === 'copied' ? 'Copied' : 'Copy'}
              </button>
              <button
                type="button"
                disabled={saving || state.phase === 'incomplete'}
                onClick={() => triggerDownload(new Blob([text], { type: 'text/markdown' }), downloadName)}
                className={BUTTON}
              >
                Download
              </button>
              <button ref={discardRef} type="button" disabled={saving} onClick={discard} className={BUTTON}>
                Discard
              </button>
            </>
          )}
          {error && (
            <>
              {(error.outcome === 'BUSY' || errorCopy?.retry) && (
                <button type="button" disabled={secondsLeft > 0} onClick={retry} className={BUTTON}>
                  Try again
                </button>
              )}
              <button type="button" onClick={() => onClose('closed')} className={BUTTON}>
                Close
              </button>
            </>
          )}
        </div>
      )}

      {showSave && (
        <p id={ids.helper} className="px-4 pb-4 text-xs text-gray-600">
          {`Accepting adds Revision ${sourceRevision + 1} as the current revision, so exports and the assistant will use it. Revision ${sourceRevision} stays in the history unchanged.`}
        </p>
      )}

      <div role="status" aria-live="polite" aria-atomic="true" className="sr-only">
        {announcement}
      </div>
    </div>
  );
}
