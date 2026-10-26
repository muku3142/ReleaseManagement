import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { ROADMAP_MAX_KEYS, useAddFromJira, type RoadmapAddResult } from '../api/roadmap';
import { Spinner } from '../components/ExportButtons';
import { TicketBrowser } from '../components/TicketBrowser';

export function AddFromJiraDialog(props: {
  open: boolean;
  onClose: () => void;
  onAdded: (result: RoadmapAddResult) => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const add = useAddFromJira();
  const [tooMany, setTooMany] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (props.open && !dialog.open) {
      add.reset();
      setTooMany(false);
      dialog.showModal();
    }
    if (!props.open && dialog.open) dialog.close();
  }, [props.open]);

  const problem = add.isError && add.error instanceof ApiError ? add.error.problem : undefined;

  function handleAction(keys: string[]) {
    if (keys.length > ROADMAP_MAX_KEYS) {
      setTooMany(true);
      return;
    }
    setTooMany(false);
    add.mutate(keys, {
      onSuccess: (result) => {
        props.onAdded(result);
        props.onClose();
      },
    });
  }

  return (
    <dialog
      ref={dialogRef}
      onClose={props.onClose}
      className="w-[min(90vw,72rem)] max-w-none rounded border border-gray-200 bg-white p-6"
    >
      <h2 className="text-base font-semibold text-gray-900">Add from Jira</h2>

      {/* Mounted only while open so a reopened dialog starts from a fresh search, not the last visit's results. */}
      <div className="mt-4 max-h-[70vh] overflow-y-auto">
        {props.open && <TicketBrowser actionLabel="Add to roadmap" onAction={handleAction} />}
      </div>

      {add.isPending && (
        <div className="mt-4 flex items-center gap-3">
          <Spinner />
          <p className="text-sm text-gray-600">Adding to roadmap…</p>
        </div>
      )}

      {tooMany && (
        <p role="alert" className="mt-2 text-sm text-red-600">
          {messageForReason({ status: 400, reason: 'ROADMAP_TOO_MANY_ITEMS' })}
        </p>
      )}
      {problem && (
        <p role="alert" data-reason={problem.reason} className="mt-2 text-sm text-red-600">
          Couldn't add to the roadmap — {messageForReason(problem)}. Try again.
        </p>
      )}

      <div className="mt-6 flex justify-end gap-3">
        <button
          type="button"
          onClick={props.onClose}
          className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700"
        >
          Cancel
        </button>
      </div>
    </dialog>
  );
}
