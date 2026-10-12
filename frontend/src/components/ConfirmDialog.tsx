import { useEffect, useRef, type ReactNode } from 'react';

// Shared by the freeze and delete confirmations - the phase's two irreversible actions. No form
// element exists here: confirming requires an explicit click on the named button, never Enter on
// a bare focus state. Escape and backdrop dismissal both route to onCancel, equivalent to Cancel.
export function ConfirmDialog(props: {
  open: boolean;
  title: string;
  body: string;
  confirmLabel: string;
  confirmClassName: string;
  pending: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  children?: ReactNode;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (props.open && !dialog.open) dialog.showModal();
    if (!props.open && dialog.open) dialog.close();
  }, [props.open]);

  return (
    <dialog
      ref={dialogRef}
      onClose={props.onCancel}
      className="rounded border border-gray-200 bg-white p-6"
    >
      <h2 className="text-base font-semibold text-gray-900">{props.title}</h2>
      <p className="mt-2 text-sm text-gray-700">{props.body}</p>
      {props.children}
      <div className="mt-4 flex gap-3">
        <button
          type="button"
          onClick={props.onCancel}
          className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={props.onConfirm}
          disabled={props.pending}
          className={props.confirmClassName}
        >
          {props.confirmLabel}
        </button>
      </div>
    </dialog>
  );
}
