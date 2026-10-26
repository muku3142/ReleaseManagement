import { useEffect, useRef } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useAddTicketsToRelease, type ReleaseAddResult } from '../api/releases';
import { TicketBrowser } from './TicketBrowser';

// D-05.2-07: the mirror image of AddToReleaseDialog - here the release is fixed and the tickets
// are chosen, rather than the tickets being fixed and the release chosen. A sibling component,
// not a second mode on that one.
export function AddTicketsToReleaseDialog(props: {
  open: boolean;
  releaseId: string;
  releaseName: string;
  version: string;
  onAdded: (result: ReleaseAddResult) => void;
  onClose: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const addTickets = useAddTicketsToRelease();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (props.open && !dialog.open) {
      addTickets.reset();
      dialog.showModal();
    }
    if (!props.open && dialog.open) dialog.close();
  }, [props.open]);

  const addProblem =
    addTickets.isError && addTickets.error instanceof ApiError ? addTickets.error.problem : undefined;

  return (
    <dialog
      ref={dialogRef}
      onClose={props.onClose}
      className="w-[min(90vw,72rem)] max-w-none rounded border border-gray-200 bg-white p-6"
    >
      <h2 className="text-base font-semibold text-gray-900">
        Add tickets to {props.releaseName} {props.version}
      </h2>

      {/* Mounted only while open. The dialog element itself never unmounts - the caller keeps it
          mounted so showModal()/close() have something to act on - so a permanently mounted browser
          would reopen still listing the tickets the previous visit just added. */}
      <div className="mt-4 max-h-[70vh] overflow-y-auto">
        {props.open && (
          <TicketBrowser
            releaseId={props.releaseId}
            actionLabel="Add to this release"
            onAction={(ticketKeys) =>
              addTickets.mutate(
                { releaseId: props.releaseId, ticketKeys },
                {
                  onSuccess: (result) => {
                    props.onAdded(result);
                    props.onClose();
                  },
                },
              )
            }
          />
        )}
      </div>

      {addTickets.isPending && (
        <div className="mt-4 flex items-center gap-3">
          <span
            className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
            aria-hidden="true"
          />
          <p className="text-sm text-gray-600">Adding tickets…</p>
        </div>
      )}

      {addProblem && (
        <p role="alert" data-reason={addProblem.reason} className="mt-2 text-sm text-red-600">
          {messageForReason(addProblem)}
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
