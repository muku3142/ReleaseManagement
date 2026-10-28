import { useEffect, useMemo, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useRelease, useReleases } from '../api/releases';
import { ROADMAP_MAX_KEYS, useAddFromRelease, useRoadmap, type RoadmapAddResult } from '../api/roadmap';
import { SMALL_BUTTON } from '../components/EnvironmentLadderSection';
import { Spinner } from '../components/ExportButtons';

const FIELD = 'w-full rounded border border-gray-300 px-3 py-2 text-sm';
const EVERYTHING = { products: [], done: true, archived: true };

function ReleaseTickets(props: {
  releaseId: string;
  selected: string[];
  onChange: (keys: string[]) => void;
  onRoadmap: Set<string>;
}) {
  const release = useRelease(props.releaseId);
  const { selected, onChange, onRoadmap } = props;
  const tickets = release.data?.items ?? [];
  const loadProblem = release.isError && release.error instanceof ApiError ? release.error.problem : undefined;

  const toggle = (key: string, checked: boolean) =>
    onChange(checked ? [...selected, key] : selected.filter((k) => k !== key));

  return (
    <>
      {release.isPending && (
        <div className="flex items-center gap-3">
          <Spinner />
          <p className="text-sm text-gray-600">Loading tickets…</p>
        </div>
      )}
      {loadProblem && (
        <p role="alert" data-reason={loadProblem.reason} className="text-sm text-red-600">
          Couldn't load this release's tickets — {messageForReason(loadProblem)}. Try again.
        </p>
      )}
      {release.isSuccess && tickets.length === 0 && (
        <p className="text-sm text-gray-600">This release has no tickets.</p>
      )}
      {release.isSuccess && tickets.length > 0 && (
        <fieldset>
          <legend className="text-sm font-semibold text-gray-700">Tickets</legend>
          <div className="mt-1 flex items-center gap-2">
            <button type="button" className={SMALL_BUTTON} onClick={() => onChange(tickets.map((t) => t.key))}>
              Select all
            </button>
            <button type="button" className={SMALL_BUTTON} onClick={() => onChange([])}>
              Clear
            </button>
            <span aria-live="polite" className="text-xs text-gray-600">
              {selected.length} selected
            </span>
          </div>
          <div className="mt-2 max-h-64 overflow-y-auto rounded border border-gray-300 p-3">
            {tickets.map((t) => (
              <label key={t.key} className="flex items-center gap-2 py-0.5 text-sm text-gray-700">
                <input
                  type="checkbox"
                  checked={selected.includes(t.key)}
                  onChange={(event) => toggle(t.key, event.target.checked)}
                />
                <span className="font-medium">{t.key}</span>
                <span>{t.summary}</span>
                {onRoadmap.has(t.key) && <span className="text-xs text-gray-600">already on the roadmap</span>}
              </label>
            ))}
          </div>
        </fieldset>
      )}
    </>
  );
}

export function AddFromReleaseDialog(props: {
  open: boolean;
  onClose: () => void;
  onAdded: (result: RoadmapAddResult) => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const add = useAddFromRelease();
  const releases = useReleases('');
  const roadmap = useRoadmap(EVERYTHING);
  const [releaseId, setReleaseId] = useState('');
  const [selected, setSelected] = useState<string[]>([]);
  const [tooMany, setTooMany] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (props.open && !dialog.open) {
      add.reset();
      setReleaseId('');
      setSelected([]);
      setTooMany(false);
      dialog.showModal();
    }
    if (!props.open && dialog.open) dialog.close();
  }, [props.open]);

  const sorted = useMemo(
    () => [...(releases.data?.releases ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    [releases.data],
  );
  const onRoadmap = useMemo(
    () => new Set((roadmap.data?.items ?? []).flatMap((i) => (i.jiraKey ? [i.jiraKey] : []))),
    [roadmap.data],
  );
  const problem = add.isError && add.error instanceof ApiError ? add.error.problem : undefined;

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (selected.length > ROADMAP_MAX_KEYS) {
      setTooMany(true);
      return;
    }
    setTooMany(false);
    add.mutate(
      { releaseId, ticketKeys: selected },
      {
        onSuccess: (result) => {
          props.onAdded(result);
          props.onClose();
        },
      },
    );
  }

  return (
    <dialog
      ref={dialogRef}
      onClose={props.onClose}
      className="w-[40rem] max-w-full rounded border border-gray-200 bg-white p-6"
    >
      <h2 className="text-base font-semibold text-gray-900">Add from a release</h2>
      <form onSubmit={handleSubmit} className="mt-4 space-y-3">
        {releases.isSuccess && sorted.length === 0 ? (
          <p className="text-sm text-gray-600">No releases yet. Create a release first.</p>
        ) : (
          <div>
            <label htmlFor="roadmapRelease" className="block text-sm font-semibold text-gray-700">
              Release
            </label>
            <select
              id="roadmapRelease"
              value={releaseId}
              onChange={(event) => {
                setReleaseId(event.target.value);
                setSelected([]);
                setTooMany(false);
              }}
              className={FIELD}
            >
              <option value="">Select a release</option>
              {sorted.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name} {r.version}
                </option>
              ))}
            </select>
          </div>
        )}

        {releaseId !== '' && (
          <ReleaseTickets releaseId={releaseId} selected={selected} onChange={setSelected} onRoadmap={onRoadmap} />
        )}

        {tooMany && (
          <p role="alert" className="text-sm text-red-600">
            {messageForReason({ status: 400, reason: 'ROADMAP_TOO_MANY_ITEMS' })}
          </p>
        )}
        {problem && (
          <p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
            Couldn't add to the roadmap — {messageForReason(problem)}. Try again.
          </p>
        )}

        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={props.onClose}
            className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={add.isPending || selected.length === 0}
            className="flex items-center gap-2 rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {add.isPending && <Spinner />}
            {add.isPending ? 'Adding…' : 'Add to roadmap'}
          </button>
        </div>
      </form>
    </dialog>
  );
}
