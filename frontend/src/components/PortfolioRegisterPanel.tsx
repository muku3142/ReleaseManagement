import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { ConfirmDialog } from './ConfirmDialog';
import { SMALL_BUTTON } from './EnvironmentLadderSection';

type Mutation<V> = {
  mutate: (variables: V, options?: { onSuccess?: () => void }) => void;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

export type RegisterItem = { id: string; name: string; archived: boolean; meta: string };

const CONFIRM_BLUE = 'rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white';
const BORDERED_BUTTON = 'rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700';

function archiveBody(noun: 'product' | 'client', name: string): string {
  return noun === 'product'
    ? `Archive ${name}? It keeps its clients and install history, but can't be added to new releases or receive new installs. You can unarchive it at any time.`
    : `Archive ${name}? It keeps its products and install history, but can't receive new installs. You can unarchive it at any time.`;
}

export function PortfolioRegisterPanel(props: {
  noun: 'product' | 'client';
  heading: string;
  items: RegisterItem[];
  loading: boolean;
  loadError?: Error | null;
  onRetry: () => void;
  add: Mutation<string>;
  rename: Mutation<{ id: string; name: string }>;
  archive: Mutation<string>;
  unarchive: Mutation<string>;
  selectedId?: string;
  onSelect?: (id: string) => void;
  emptyHeading: string;
  emptyBody: string;
  renderDetails?: (item: RegisterItem) => React.ReactNode;
}) {
  const { noun, add, rename, archive, unarchive } = props;
  const [showArchived, setShowArchived] = useState(false);
  const [newName, setNewName] = useState('');
  const [submittedName, setSubmittedName] = useState('');
  const [renamingId, setRenamingId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [archiving, setArchiving] = useState<RegisterItem | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const renameInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (renamingId) renameInput.current?.focus();
  }, [renamingId]);

  const mutations = [add, rename, archive, unarchive];
  const busy = mutations.some((m) => m.isPending);
  const failed = mutations.find((m) => m.isError);
  const problem = failed?.error instanceof ApiError ? failed.error.problem : undefined;
  const loadProblem = props.loadError instanceof ApiError ? props.loadError.problem : undefined;

  const visible = props.items
    .filter((item) => showArchived || !item.archived)
    .sort(
      (a, b) =>
        Number(a.archived) - Number(b.archived) || a.name.toLowerCase().localeCompare(b.name.toLowerCase()),
    );

  function errorText(): string | null {
    if (!failed) return null;
    if (problem?.reason === `${noun.toUpperCase()}_NAME_DUPLICATE`) {
      return `A ${noun} named '${submittedName}' already exists. Names are not case-sensitive. Choose a different name.`;
    }
    return problem ? messageForReason(problem) : 'Something went wrong. Try again, and check the server logs if it persists.';
  }

  function handleAdd(event: React.FormEvent) {
    event.preventDefault();
    const name = newName.trim();
    setSubmittedName(name);
    add.mutate(name, { onSuccess: () => setNewName('') });
  }

  function saveRename(event: React.FormEvent, id: string) {
    event.preventDefault();
    const name = renameValue.trim();
    setSubmittedName(name);
    rename.mutate({ id, name }, { onSuccess: () => setRenamingId(null) });
  }

  function confirmArchive() {
    if (!archiving) return;
    const item = archiving;
    archive.mutate(item.id, {
      onSuccess: () => {
        setArchiving(null);
        setAnnouncement(`${item.name} archived.`);
      },
    });
  }

  function handleUnarchive(item: RegisterItem) {
    unarchive.mutate(item.id, { onSuccess: () => setAnnouncement(`${item.name} unarchived.`) });
  }

  const message = errorText();
  const Noun = noun === 'product' ? 'Product' : 'Client';

  return (
    <section className="rounded border border-gray-200 bg-white p-4">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="text-base font-semibold text-gray-900">{props.heading}</h2>
        <span className="grow" />
        <label className="flex items-center gap-2 text-xs text-gray-600">
          <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
          Show archived
        </label>
      </div>

      <form onSubmit={handleAdd} className="mt-3 flex flex-wrap items-end gap-2">
        <div className="flex flex-col gap-1">
          <label htmlFor={`new-${noun}-name`} className="text-sm font-medium text-gray-700">
            New {noun} name
          </label>
          <input
            id={`new-${noun}-name`}
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            maxLength={100}
            required
            className="w-72 rounded border border-gray-300 px-2 py-1 text-sm"
          />
        </div>
        <button
          type="submit"
          disabled={busy}
          className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        >
          Add {noun}
        </button>
      </form>

      {message && (
        <p role="alert" data-reason={problem?.reason} className="mt-2 text-sm text-red-600">
          {message}
        </p>
      )}

      {props.loading && (
        <div className="mt-4 flex items-center gap-3">
          <span
            className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
            aria-hidden="true"
          />
          <p className="text-sm text-gray-600">Loading {noun}s…</p>
        </div>
      )}

      {props.loadError && (
        <div className="mt-4">
          <p role="alert" data-reason={loadProblem?.reason} className="text-sm text-red-600">
            Couldn&apos;t load {noun}s —{' '}
            {(loadProblem ? messageForReason(loadProblem) : props.loadError.message).replace(/\.$/, '')}. Try again.
          </p>
          <button type="button" onClick={props.onRetry} className={`mt-2 ${BORDERED_BUTTON}`}>
            Try again
          </button>
        </div>
      )}

      {!props.loading && !props.loadError && props.items.length === 0 && (
        <div className="mt-4">
          <p className="text-sm font-semibold text-gray-900">{props.emptyHeading}</p>
          <p className="mt-1 text-sm text-gray-600">{props.emptyBody}</p>
        </div>
      )}

      <ul className="mt-4 divide-y divide-gray-100">
        {visible.map((item) => (
          <li
            key={item.id}
            className={`flex flex-wrap items-center gap-2 py-2 text-sm ${
              props.onSelect && props.selectedId === item.id ? 'bg-gray-100' : ''
            }`}
          >
            {renamingId === item.id ? (
              <form onSubmit={(e) => saveRename(e, item.id)} className="flex items-center gap-2">
                <input
                  ref={renameInput}
                  aria-label={`Name for ${item.name}`}
                  value={renameValue}
                  onChange={(e) => setRenameValue(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Escape') setRenamingId(null);
                  }}
                  maxLength={100}
                  required
                  className="rounded border border-gray-300 px-2 py-1 text-sm"
                />
                <button type="submit" disabled={busy} className={SMALL_BUTTON}>
                  Save
                </button>
                <button type="button" onClick={() => setRenamingId(null)} className={SMALL_BUTTON}>
                  Cancel
                </button>
              </form>
            ) : (
              <>
                {props.onSelect ? (
                  <button
                    type="button"
                    onClick={() => props.onSelect?.(item.id)}
                    aria-current={props.selectedId === item.id ? 'true' : undefined}
                    className={`text-left font-semibold ${item.archived ? 'text-gray-500 line-through' : 'text-gray-900'}`}
                  >
                    {item.name}
                  </button>
                ) : (
                  <span className={`font-semibold ${item.archived ? 'text-gray-500 line-through' : 'text-gray-900'}`}>
                    {item.name}
                  </span>
                )}
                {item.archived && <span className="text-xs text-gray-500">archived</span>}
                <span className="text-xs text-gray-600">{item.meta}</span>
                <span className="grow" />
                <button
                  type="button"
                  disabled={busy}
                  aria-label={`Rename ${item.name}`}
                  onClick={() => {
                    setRenamingId(item.id);
                    setRenameValue(item.name);
                  }}
                  className={SMALL_BUTTON}
                >
                  Rename
                </button>
                {item.archived ? (
                  <button
                    type="button"
                    disabled={busy}
                    aria-label={`Unarchive ${item.name}`}
                    onClick={() => handleUnarchive(item)}
                    className={SMALL_BUTTON}
                  >
                    Unarchive
                  </button>
                ) : (
                  <button
                    type="button"
                    disabled={busy}
                    aria-label={`Archive ${item.name}`}
                    onClick={() => setArchiving(item)}
                    className={SMALL_BUTTON}
                  >
                    Archive
                  </button>
                )}
                {props.renderDetails?.(item)}
              </>
            )}
          </li>
        ))}
      </ul>

      <ConfirmDialog
        open={archiving !== null}
        title={`Archive ${noun}`}
        body={archiving ? archiveBody(noun, archiving.name) : ''}
        confirmLabel="Archive"
        confirmClassName={CONFIRM_BLUE}
        pending={archive.isPending}
        onConfirm={confirmArchive}
        onCancel={() => setArchiving(null)}
      />

      <div role="status" aria-live="polite" className="sr-only" data-noun={Noun}>
        {announcement}
      </div>
    </section>
  );
}
