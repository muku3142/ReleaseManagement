import { useState } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/auth';
import {
  useAddStage,
  useDeleteStage,
  useEnvironmentLadder,
  useMoveProductionStage,
  useRenameStage,
  useReorderStages,
  useRestoreStage,
  useRetireStage,
  type LadderStage,
} from '../api/ladder';
import { ConfirmDialog } from './ConfirmDialog';

const SMALL_BUTTON = 'rounded border border-gray-300 px-2 py-1 text-xs text-gray-700 disabled:opacity-50';

export function EnvironmentLadderSection() {
  const ladder = useEnvironmentLadder();
  const add = useAddStage();
  const rename = useRenameStage();
  const reorder = useReorderStages();
  const retire = useRetireStage();
  const restore = useRestoreStage();
  const moveProduction = useMoveProductionStage();
  const remove = useDeleteStage();
  const [newName, setNewName] = useState('');
  const [renamingId, setRenamingId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [deleting, setDeleting] = useState<LadderStage | null>(null);

  const stages = ladder.data?.stages ?? [];
  const failed = [add, rename, reorder, retire, restore, moveProduction, remove].find((m) => m.isError);
  const problem = failed?.error instanceof ApiError ? failed.error.problem : undefined;
  const busy = [add, rename, reorder, retire, restore, moveProduction, remove].some((m) => m.isPending);

  function move(index: number, offset: -1 | 1) {
    const ids = stages.map((s) => s.id);
    [ids[index], ids[index + offset]] = [ids[index + offset], ids[index]];
    reorder.mutate(ids);
  }

  function handleAdd(event: React.FormEvent) {
    event.preventDefault();
    add.mutate(newName.trim(), { onSuccess: () => setNewName('') });
  }

  function startRename(stage: LadderStage) {
    setRenamingId(stage.id);
    setRenameValue(stage.name);
  }

  function saveRename(event: React.FormEvent, stage: LadderStage) {
    event.preventDefault();
    rename.mutate({ id: stage.id, name: renameValue.trim() }, { onSuccess: () => setRenamingId(null) });
  }

  return (
    <>
      <h2 className="mt-8 text-base font-semibold text-gray-900">Environment ladder</h2>
      <p className="mt-2 text-sm text-gray-600">
        One ladder for every release. Renaming a stage relabels its history; a stage with promotions can only be
        retired.
      </p>

      {ladder.isPending && <p className="mt-2 text-sm text-gray-600">Loading…</p>}
      {ladder.isError && <p className="mt-2 text-sm text-red-600">Failed to load the environment ladder.</p>}

      <ol className="mt-4 space-y-2">
        {stages.map((stage, index) => (
          <li key={stage.id} className="flex flex-wrap items-center gap-2 text-sm">
            {renamingId === stage.id ? (
              <form onSubmit={(event) => saveRename(event, stage)} className="flex items-center gap-2">
                <input
                  aria-label={`Name for ${stage.name}`}
                  value={renameValue}
                  onChange={(event) => setRenameValue(event.target.value)}
                  maxLength={100}
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
                <span className={`font-medium ${stage.retired ? 'text-gray-500 line-through' : 'text-gray-900'}`}>
                  {stage.name}
                </span>
                {stage.production && <span className="text-xs font-medium text-blue-700">Production</span>}
                {stage.retired && <span className="text-xs text-gray-500">retired</span>}
                <button type="button" disabled={busy || index === 0} onClick={() => move(index, -1)} className={SMALL_BUTTON}>
                  Up
                </button>
                <button
                  type="button"
                  disabled={busy || index === stages.length - 1}
                  onClick={() => move(index, 1)}
                  className={SMALL_BUTTON}
                >
                  Down
                </button>
                <button type="button" disabled={busy} onClick={() => startRename(stage)} className={SMALL_BUTTON}>
                  Rename
                </button>
                {!stage.retired && !stage.production && (
                  <button type="button" disabled={busy} onClick={() => moveProduction.mutate(stage.id)} className={SMALL_BUTTON}>
                    Make Production
                  </button>
                )}
                {!stage.retired && !stage.production && (
                  <button type="button" disabled={busy} onClick={() => retire.mutate(stage.id)} className={SMALL_BUTTON}>
                    Retire
                  </button>
                )}
                {stage.retired && (
                  <button type="button" disabled={busy} onClick={() => restore.mutate(stage.id)} className={SMALL_BUTTON}>
                    Restore
                  </button>
                )}
                {!stage.inUse && !stage.production && (
                  <button type="button" disabled={busy} onClick={() => setDeleting(stage)} className={SMALL_BUTTON}>
                    Delete
                  </button>
                )}
              </>
            )}
          </li>
        ))}
      </ol>

      <form onSubmit={handleAdd} className="mt-4 space-y-2">
        <label htmlFor="newStageName" className="block text-sm font-medium text-gray-700">
          New stage name
        </label>
        <input
          id="newStageName"
          name="newStageName"
          type="text"
          required
          maxLength={100}
          value={newName}
          onChange={(event) => setNewName(event.target.value)}
          className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
        />
        <button
          type="submit"
          disabled={busy}
          className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          Add stage
        </button>
      </form>

      {problem && (
        <p role="alert" data-reason={problem.reason} className="mt-2 text-sm text-red-600">
          {messageForReason(problem)}
        </p>
      )}

      <ConfirmDialog
        open={deleting !== null}
        title="Delete stage"
        body={deleting ? `Delete the stage ${deleting.name}? This cannot be undone.` : ''}
        confirmLabel="Delete"
        confirmClassName="rounded bg-red-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        pending={remove.isPending}
        onConfirm={() => {
          if (deleting) remove.mutate(deleting.id, { onSettled: () => setDeleting(null) });
        }}
        onCancel={() => setDeleting(null)}
      />
    </>
  );
}
