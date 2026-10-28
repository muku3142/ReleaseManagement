import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useProducts } from '../api/products';
import { useAddManualItem, type RoadmapItem } from '../api/roadmap';
import { Spinner } from '../components/ExportButtons';

const FIELD = 'w-full rounded border border-gray-300 px-3 py-2 text-sm';

export function AddManualItemDialog(props: {
  open: boolean;
  onClose: () => void;
  onAdded: (item: RoadmapItem) => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const add = useAddManualItem();
  const products = useProducts();
  const [title, setTitle] = useState('');
  const [productId, setProductId] = useState('');
  const [titleMissing, setTitleMissing] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (props.open && !dialog.open) {
      add.reset();
      setTitle('');
      setProductId('');
      setTitleMissing(false);
      dialog.showModal();
    }
    if (!props.open && dialog.open) dialog.close();
  }, [props.open]);

  const problem = add.isError && add.error instanceof ApiError ? add.error.problem : undefined;
  const titleProblem = problem?.reason === 'VALIDATION_FAILED' ? problem.fieldErrors?.title : undefined;
  const titleError = titleMissing ? 'Enter a title.' : titleProblem;

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = title.trim();
    if (trimmed === '') {
      setTitleMissing(true);
      return;
    }
    setTitleMissing(false);
    add.mutate(
      { title: trimmed, productId },
      {
        onSuccess: (item) => {
          props.onAdded(item);
          props.onClose();
        },
      },
    );
  }

  return (
    <dialog
      ref={dialogRef}
      onClose={props.onClose}
      className="w-[32rem] max-w-full rounded border border-gray-200 bg-white p-6"
    >
      <h2 className="text-base font-semibold text-gray-900">Add manual item</h2>
      <form onSubmit={handleSubmit} className="mt-4 space-y-3" noValidate>
        <div>
          <label htmlFor="manualItemTitle" className="block text-sm font-semibold text-gray-700">
            Title
          </label>
          <input
            id="manualItemTitle"
            type="text"
            maxLength={500}
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            aria-invalid={titleError ? true : undefined}
            className={FIELD}
          />
          {titleError && <p className="mt-1 text-xs text-red-600">{titleError}</p>}
        </div>
        <div>
          <label htmlFor="manualItemProduct" className="block text-sm font-semibold text-gray-700">
            Product
          </label>
          <select
            id="manualItemProduct"
            value={productId}
            onChange={(event) => setProductId(event.target.value)}
            className={FIELD}
          >
            <option value="" disabled>Choose a product</option>
            {(products.data?.products ?? [])
              .filter((p) => !p.archived)
              .map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
          </select>
        </div>

        {problem && !titleProblem && (
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
            disabled={add.isPending || productId === ''}
            className="flex items-center gap-2 rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {add.isPending && <Spinner />}
            {add.isPending ? 'Adding…' : 'Add item'}
          </button>
        </div>
      </form>
    </dialog>
  );
}
