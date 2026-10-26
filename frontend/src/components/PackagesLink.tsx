import { useRef, useState } from 'react';
import { ApiError, type ProblemDetail } from '../api/client';
import { messageForReason, REASON_MESSAGES } from '../api/reasonMessages';
import { useSetPackagesUrl, type Release } from '../api/releases';
import { ConfirmDialog } from './ConfirmDialog';
import { SMALL_BUTTON } from './EnvironmentLadderSection';
import { Spinner } from './ExportButtons';
import { isAllowedPackagesUrl } from './packagesUrl';

const EDITOR_ID = 'release-packages-editor';
const HELPER_ID = 'release-packages-helper';
const ERROR_ID = 'release-packages-error';
const INVALID_COPY = REASON_MESSAGES.RELEASE_PACKAGES_URL_INVALID;
const PRIMARY = 'rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50';

function problemOf(error: unknown): ProblemDetail | undefined {
  return error instanceof ApiError ? error.problem : undefined;
}

export function PackagesLink({ release }: { release: Release }) {
  const [open, setOpen] = useState(false);
  const [confirmingClear, setConfirmingClear] = useState(false);
  const [announcement, setAnnouncement] = useState('');
  const toggleButton = useRef<HTMLButtonElement>(null);
  const save = useSetPackagesUrl(release.id);
  const clear = useSetPackagesUrl(release.id);
  const url = release.packagesUrl;
  const clearProblem = problemOf(clear.error);

  function close(message: string) {
    setOpen(false);
    setAnnouncement(message);
    toggleButton.current?.focus();
  }

  function toggleEditor() {
    save.reset();
    setAnnouncement('');
    setOpen((o) => !o);
  }

  return (
    <div>
      <div className="mt-2 flex flex-wrap items-center gap-2 text-sm">
        <span className="font-semibold text-gray-700">Packages:</span>
        {url === null ? (
          <span className="text-gray-500">Not set</span>
        ) : isAllowedPackagesUrl(url) ? (
          <>
            <a
              href={url}
              target="_blank"
              rel="noopener noreferrer"
              className="font-semibold text-blue-600 hover:underline"
            >
              Open packages
            </a>
            <span className="sr-only"> (opens in a new tab)</span>
            <small className="text-gray-600" title={url}>
              {new URL(url).hostname}
            </small>
          </>
        ) : (
          <small className="text-amber-700">The saved packages link isn&apos;t a valid OneDrive or SharePoint link.</small>
        )}
        <button
          type="button"
          ref={toggleButton}
          aria-expanded={open}
          aria-controls={EDITOR_ID}
          aria-label={url === null ? undefined : 'Change packages link'}
          onClick={toggleEditor}
          className={SMALL_BUTTON}
        >
          {url === null ? 'Set packages link' : 'Change'}
        </button>
        {url !== null && (
          <button
            type="button"
            aria-label="Clear packages link"
            onClick={() => setConfirmingClear(true)}
            className={SMALL_BUTTON}
          >
            Clear
          </button>
        )}
      </div>
      <p role="status" aria-live="polite" className="sr-only">
        {announcement}
      </p>
      {clearProblem && (
        <p role="alert" data-reason={clearProblem.reason} className="mt-2 text-sm text-red-600">
          Couldn&apos;t clear the packages link — {messageForReason(clearProblem)} Try again.
        </p>
      )}
      {open && (
        <EditorPanel
          current={url}
          pending={save.isPending}
          problem={problemOf(save.error)}
          onSave={(value) => save.mutate(value, { onSuccess: () => close('Packages link saved.') })}
          onCancel={() => close('')}
        />
      )}
      {confirmingClear && (
        <ConfirmDialog
          open
          title="Clear the packages link?"
          body="The link is removed from this release. You can set it again later."
          confirmLabel="Clear link"
          confirmClassName={PRIMARY}
          pending={clear.isPending}
          onConfirm={() =>
            clear.mutate(null, {
              onSuccess: () => setAnnouncement('Packages link cleared.'),
              onSettled: () => setConfirmingClear(false),
            })
          }
          onCancel={() => setConfirmingClear(false)}
        />
      )}
    </div>
  );
}

function EditorPanel(props: {
  current: string | null;
  pending: boolean;
  problem: ProblemDetail | undefined;
  onSave: (value: string) => void;
  onCancel: () => void;
}) {
  const [value, setValue] = useState(props.current ?? '');
  const [invalid, setInvalid] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const trimmed = value.trim();

  function check(): boolean {
    const ok = trimmed === '' || isAllowedPackagesUrl(trimmed);
    setInvalid(!ok);
    return ok;
  }

  function submit() {
    if (!check()) {
      input.current?.focus();
      return;
    }
    props.onSave(trimmed);
  }

  return (
    <div id={EDITOR_ID} className="mt-3 rounded border border-gray-200 bg-white p-4">
      <label htmlFor="release-packages-input" className="block text-sm font-semibold text-gray-700">
        OneDrive or SharePoint link
      </label>
      <input
        id="release-packages-input"
        ref={input}
        type="url"
        inputMode="url"
        autoComplete="off"
        value={value}
        aria-invalid={invalid ? 'true' : undefined}
        aria-describedby={invalid ? `${HELPER_ID} ${ERROR_ID}` : HELPER_ID}
        onChange={(event) => setValue(event.target.value)}
        onBlur={check}
        className="mt-1 w-full rounded border border-gray-300 px-3 py-2 text-sm"
      />
      <small id={HELPER_ID} className="mt-1 block text-gray-600">
        Paste a link to the packages folder. Only https links on sharepoint.com, onedrive.live.com or 1drv.ms are
        accepted.
      </small>
      {invalid && (
        <small id={ERROR_ID} className="mt-1 block text-red-600">
          {INVALID_COPY}
        </small>
      )}
      <div className="mt-3 flex items-center gap-2">
        <button
          type="button"
          disabled={trimmed === '' || trimmed === props.current || props.pending}
          onClick={submit}
          className={PRIMARY}
        >
          Save packages link
        </button>
        <button
          type="button"
          onClick={props.onCancel}
          className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700"
        >
          Cancel
        </button>
        {props.pending && (
          <span className="flex items-center gap-2 text-sm text-gray-600">
            <Spinner />
            Saving…
          </span>
        )}
      </div>
      {props.problem && (
        <p role="alert" data-reason={props.problem.reason} className="mt-2 text-sm text-red-600">
          {messageForReason(props.problem)}
        </p>
      )}
    </div>
  );
}
