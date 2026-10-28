import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useSaveProductBundle, type ProductBundle } from '../api/productBundles';

// Names the product bundle the selected tickets go into. An existing name moves the tickets into
// that bundle (out of any other); a new name creates one.
export function AddToBundleDialog(props: {
  open: boolean;
  productId: string;
  ticketKeys: string[];
  bundles: ProductBundle[];
  onSaved: () => void;
  onClose: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const save = useSaveProductBundle(props.productId);
  const [name, setName] = useState('');

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (props.open && !dialog.open) {
      setName('');
      save.reset();
      dialog.showModal();
    }
    if (!props.open && dialog.open) dialog.close();
  }, [props.open]);

  const problem = save.isError && save.error instanceof ApiError ? save.error.problem : undefined;

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    save.mutate(
      { name: name.trim(), ticketKeys: props.ticketKeys },
      {
        onSuccess: () => {
          props.onSaved();
          props.onClose();
        },
      },
    );
  }

  return (
    <dialog
      ref={dialogRef}
      onClose={props.onClose}
      className="rounded border border-gray-200 bg-white p-6"
    >
      <form onSubmit={handleSubmit}>
        <h2 className="text-base font-semibold text-gray-900">
          Add {props.ticketKeys.length} ticket(s) to a bundle
        </h2>
        <div className="mt-4 space-y-1">
          <label htmlFor="bundleNameInput" className="block text-sm font-semibold text-foreground">
            Bundle name
          </label>
          <input
            id="bundleNameInput"
            type="text"
            list="bundleNameInput-options"
            maxLength={100}
            required
            aria-describedby="bundleNameInput-help"
            value={name}
            onChange={(event) => setName(event.target.value)}
            className="h-9 w-full rounded-md border border-input bg-card px-3 text-sm text-foreground"
          />
          <datalist id="bundleNameInput-options">
            {props.bundles.map((bundle) => (
              <option key={bundle.id} value={bundle.name} />
            ))}
          </datalist>
          <p id="bundleNameInput-help" className="text-sm text-muted-foreground">
            Pick an existing bundle to add to it, or type a new name.
          </p>
        </div>

        {problem && (
          <p role="alert" data-reason={problem.reason} className="mt-2 text-sm text-red-600">
            {messageForReason(problem)}
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
          <button
            type="submit"
            disabled={save.isPending || name.trim() === ''}
            className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
          >
            Save
          </button>
        </div>
      </form>
    </dialog>
  );
}
