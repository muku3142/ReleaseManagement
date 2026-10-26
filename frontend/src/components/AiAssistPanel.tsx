import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { useAssistantStatus } from '../api/assistant';
import { useTransformLimits, type TransformMode } from '../api/documentAssist';
import type { GeneratedDocument } from '../api/documents';
import { addRecentInstruction, useRecentInstructions } from '../documentAssist/recentInstructions';
import { AiResultCard } from './AiResultCard';

type AiAssistPanelProps = {
  document: GeneratedDocument;
  releaseName: string;
  releaseVersion: string;
  children: ReactNode;
  onResultOpenChange: (open: boolean) => void;
  onSaved: (revision: number) => void;
};

type Run = { id: number; mode: TransformMode; instruction: string | null };
type TooLong = { characters: number; maxCharacters: number; summarizeOnly: boolean };

const INSTRUCTION_MAX = 2000;
const COUNTER_FROM = 1800;
const RECENT_LABEL_MAX = 80;
const CARD = 'rounded border border-gray-200 bg-white';
const BUTTON = 'rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-blue-600';
const OPEN_HINT = 'Save or discard the current result first.';
const MODE_SLUG: Record<TransformMode, string> = { CLEAN_UP: 'clean-up', SUMMARIZE: 'summary', CUSTOM: 'custom' };

const kebab = (text: string) =>
  text
    .toLowerCase()
    .replace(/[_\s.]+/g, '-')
    .replace(/[^a-z0-9-]/g, '')
    .replace(/-+/g, '-');

const truncate = (text: string) => (text.length > RECENT_LABEL_MAX ? `${text.slice(0, RECENT_LABEL_MAX)}…` : text);

export function AiAssistPanel({ document, releaseName, releaseVersion, children, onResultOpenChange, onSaved }: AiAssistPanelProps) {
  const status = useAssistantStatus(true);
  const visible = status.data?.enabled === true && document.revision === document.latestRevision;
  const limits = useTransformLimits(visible).data;
  const recent = useRecentInstructions();

  const [run, setRun] = useState<Run | null>(null);
  const [tooLarge, setTooLarge] = useState<TooLong | null>(null);
  const [customOpen, setCustomOpen] = useState(false);
  const [text, setText] = useState('');
  const [announcement, setAnnouncement] = useState('');
  const runCounter = useRef(0);
  const trigger = useRef<HTMLButtonElement | null>(null);
  const refocus = useRef(false);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const customButton = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (run === null && refocus.current) {
      refocus.current = false;
      trigger.current?.focus();
    }
  }, [run]);

  const resultOpen = run !== null;
  const documentChars = document.markdown.length;
  // Clean up and Custom are sent section by section, so only Summarize is limited by the whole document's size.
  const summarizeMax = limits?.presets.SUMMARIZE[document.documentType];
  const summarizeOver = summarizeMax !== undefined && documentChars > summarizeMax;
  const tooLong: TooLong | null =
    tooLarge ??
    (summarizeOver ? { characters: documentChars, maxCharacters: summarizeMax, summarizeOnly: true } : null);

  if (!visible) {
    return <div className={`m-6 ${CARD}`}>{children}</div>;
  }

  function start(mode: TransformMode, instruction: string | null, button: HTMLButtonElement | null) {
    trigger.current = button;
    setTooLarge(null);
    runCounter.current += 1;
    setRun({ id: runCounter.current, mode, instruction });
    onResultOpenChange(true);
  }

  function runCustom() {
    const instruction = text.trim();
    if (instruction === '' || resultOpen) return;
    addRecentInstruction(instruction);
    setCustomOpen(false);
    start('CUSTOM', instruction, customButton.current);
  }

  function closeCard() {
    refocus.current = true;
    setRun(null);
    onResultOpenChange(false);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      runCustom();
    }
  }

  const hint = resultOpen ? OPEN_HINT : undefined;
  const modeButton = (label: string, mode: 'CLEAN_UP' | 'SUMMARIZE', over: boolean) => (
    <button
      type="button"
      className={BUTTON}
      disabled={resultOpen || over}
      title={hint}
      onClick={(e) => start(mode, null, e.currentTarget)}
    >
      {label}
    </button>
  );

  return (
    <>
      <div role="status" aria-live="polite" aria-atomic="true" className="sr-only">
        {announcement}
      </div>

      <div className={`mx-6 mt-2 ${CARD} p-4`}>
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-semibold">AI assist</span>
          {modeButton('Clean up', 'CLEAN_UP', false)}
          {modeButton('Summarize', 'SUMMARIZE', summarizeOver)}
          <button
            type="button"
            ref={customButton}
            className={BUTTON}
            disabled={resultOpen}
            title={hint}
            aria-expanded={customOpen}
            aria-controls="ai-custom-box"
            onClick={() => setCustomOpen((open) => !open)}
          >
            Custom…
          </button>
        </div>
        <p className="mt-2 text-xs text-gray-600">
          Runs on this server&apos;s local model. Only this document&apos;s text is sent. Results can be saved as a new revision; the original is never changed.
        </p>
      </div>

      {customOpen && (
        <div id="ai-custom-box" className={`mx-6 mt-2 ${CARD} p-4`}>
          {recent.length > 0 && (
            <select
              aria-label="Recent instructions"
              value=""
              className="mb-2 block rounded border border-gray-300 px-2 py-1 text-sm focus-visible:ring-2 focus-visible:ring-blue-600"
              onChange={(e) => {
                setText(e.target.value);
                textareaRef.current?.focus();
              }}
            >
              <option value="" disabled>
                Recent instructions…
              </option>
              {recent.map((entry) => (
                <option key={entry} value={entry} title={entry}>
                  {truncate(entry)}
                </option>
              ))}
            </select>
          )}
          <label htmlFor="ai-custom-instruction" className="block text-sm font-semibold">
            Custom instruction
          </label>
          <textarea
            id="ai-custom-instruction"
            ref={textareaRef}
            rows={3}
            maxLength={INSTRUCTION_MAX}
            value={text}
            placeholder="e.g. Rewrite for executives, keep every ticket key and version"
            onChange={(e) => setText(e.target.value)}
            onKeyDown={handleKeyDown}
            className="mt-1 block w-full rounded border border-gray-300 p-2 text-sm focus-visible:ring-2 focus-visible:ring-blue-600"
          />
          {text.length > COUNTER_FROM && <p className="mt-1 text-xs text-amber-700">{`${text.length}/${INSTRUCTION_MAX}`}</p>}
          <p className="mt-1 text-xs text-gray-600">
            The model sees only this document and your instruction. The instruction is saved with the revision if you save the result.
          </p>
          <button
            type="button"
            className="mt-2 rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-blue-600"
            disabled={text.trim() === '' || resultOpen}
            onClick={runCustom}
          >
            Run instruction
          </button>
        </div>
      )}

      {tooLong && (
        <div role="status" className="mx-6 mt-2 rounded border border-amber-600 bg-amber-50 p-4 text-sm text-amber-700">
          <p className="font-semibold">
            {tooLong.summarizeOnly
              ? `This document is too long to summarize on this server (about ${tooLong.characters.toLocaleString()} of ${tooLong.maxCharacters.toLocaleString()} characters).`
              : `A part of this document is too long for the AI model on this server (about ${tooLong.characters.toLocaleString()} of ${tooLong.maxCharacters.toLocaleString()} characters).`}
          </p>
          <p className="text-xs">
            {tooLong.summarizeOnly
              ? 'Clean up and Custom still work: they process the document one section at a time.'
              : 'Nothing was sent. Split that section with headings or blank lines, or use a larger model on the server.'}
          </p>
        </div>
      )}

      {run === null ? (
        <div className={`m-6 ${CARD}`}>{children}</div>
      ) : (
        <div className="mx-6 mb-6 mt-2 grid grid-cols-1 gap-4 lg:grid-cols-2">
          <div>
            <p className="mb-1 text-xs font-semibold text-gray-500">{`Original · Revision ${document.revision}`}</p>
            <div className={CARD}>{children}</div>
          </div>
          <AiResultCard
            key={run.id}
            releaseId={document.releaseId}
            documentType={document.documentType}
            sourceRevision={document.revision}
            originalMarkdown={document.markdown}
            mode={run.mode}
            instruction={run.instruction}
            downloadName={`${kebab(document.documentType)}-${kebab(releaseName)}-${kebab(releaseVersion)}-${MODE_SLUG[run.mode]}.md`}
            onClose={(reason) => {
              if (reason === 'discarded') setAnnouncement('Result discarded.');
              closeCard();
            }}
            onSaved={(revision) => {
              setAnnouncement(`Saved as Revision ${revision}.`);
              setRun(null);
              onResultOpenChange(false);
              onSaved(revision);
            }}
            onTooLarge={(characters, maxCharacters) => {
              setTooLarge({ characters, maxCharacters, summarizeOnly: run.mode === 'SUMMARIZE' });
              closeCard();
            }}
          />
        </div>
      )}
    </>
  );
}
