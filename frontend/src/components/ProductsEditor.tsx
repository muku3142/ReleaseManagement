import { useRef, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useProducts } from '../api/products';
import { useSetReleaseProducts, type Release, type ReleaseProduct } from '../api/releases';
import { SMALL_BUTTON } from './EnvironmentLadderSection';

export const PRODUCT_CHIP = 'rounded border border-gray-300 bg-gray-100 px-2 py-0.5 text-xs font-semibold text-gray-700';
const EDITOR_ID = 'release-products-editor';
const LAST_PRODUCT_COPY = 'A release needs at least one product.';
const HAS_INSTALLS_COPY = "Can't remove: installs are recorded for this product on this release.";

export function ProductsEditor({ release }: { release: Release }) {
  const [open, setOpen] = useState(false);
  const [announcement, setAnnouncement] = useState('');
  const editButton = useRef<HTMLButtonElement>(null);

  function close(saved: boolean) {
    setOpen(false);
    setAnnouncement(saved ? 'Products updated.' : '');
    editButton.current?.focus();
  }

  return (
    <div className="mt-2">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="font-semibold text-gray-700">Products:</span>
        {release.products.length === 0 ? (
          <span className="text-gray-500">Unassigned</span>
        ) : (
          release.products.map((p) => (
            <span key={p.id} className={PRODUCT_CHIP}>
              {p.name}
              {p.archived ? ' (archived)' : ''}
            </span>
          ))
        )}
        <button
          type="button"
          ref={editButton}
          aria-expanded={open}
          aria-controls={EDITOR_ID}
          onClick={() => setOpen((o) => !o)}
          className={SMALL_BUTTON}
        >
          Edit products
        </button>
      </div>
      <p role="status" aria-live="polite" className="sr-only">
        {announcement}
      </p>
      {open && <EditorPanel release={release} onClose={close} />}
    </div>
  );
}

function EditorPanel({ release, onClose }: { release: Release; onClose: (saved: boolean) => void }) {
  const products = useProducts();
  const save = useSetReleaseProducts(release.id);
  const [selected, setSelected] = useState<string[]>(() => release.products.map((p) => p.id));

  const assigned = new Map<string, ReleaseProduct>(release.products.map((p) => [p.id, p]));
  const options = [
    ...(products.data?.products ?? [])
      .filter((p) => !p.archived || assigned.has(p.id))
      .map((p) => ({ id: p.id, name: p.name, archived: p.archived })),
  ];
  for (const p of release.products) {
    if (!options.some((o) => o.id === p.id)) options.push({ id: p.id, name: p.name, archived: p.archived });
  }
  options.sort((a, b) => a.name.localeCompare(b.name));

  const initial = release.products.map((p) => p.id);
  const unchanged =
    selected.length === initial.length && selected.every((id) => initial.includes(id));
  const problem = save.isError && save.error instanceof ApiError ? save.error.problem : undefined;

  function toggle(id: string, checked: boolean) {
    setSelected((current) => (checked ? [...current, id] : current.filter((s) => s !== id)));
  }

  return (
    <div id={EDITOR_ID} className="mt-3 rounded border border-gray-200 bg-white p-4">
      <p className="text-sm font-semibold text-gray-700">Products covered by this release</p>

      {products.isSuccess && options.length === 0 ? (
        <p className="mt-2 text-sm text-gray-600">
          No products yet. Add one on the{' '}
          <Link to="/portfolio" className="text-blue-600 underline">
            Portfolio page
          </Link>
          .
        </p>
      ) : (
        <ul className="mt-2 max-h-64 space-y-2 overflow-y-auto">
          {options.map((o) => {
            const checked = selected.includes(o.id);
            const hasInstalls = assigned.get(o.id)?.hasInstalls === true;
            const isLast = checked && selected.length === 1;
            const lockedByInstalls = checked && hasInstalls;
            const archivedUnchecked = o.archived && !checked;
            const notes = [
              lockedByInstalls ? `${o.id}-installs` : null,
              isLast ? `${o.id}-last` : null,
            ].filter((n): n is string => n !== null);
            return (
              <li key={o.id}>
                <label className="flex items-center gap-2 text-sm text-gray-900">
                  <input
                    type="checkbox"
                    checked={checked}
                    disabled={lockedByInstalls || archivedUnchecked}
                    aria-disabled={lockedByInstalls || isLast ? 'true' : undefined}
                    aria-describedby={notes.length > 0 ? notes.join(' ') : undefined}
                    onChange={(event) => {
                      if (checked && (lockedByInstalls || isLast)) return;
                      toggle(o.id, event.target.checked);
                    }}
                  />
                  {o.name}
                  {o.archived && <small className="text-xs text-gray-600">archived</small>}
                </label>
                {lockedByInstalls && (
                  <small id={`${o.id}-installs`} className="block text-xs text-amber-700">
                    {HAS_INSTALLS_COPY}
                  </small>
                )}
                {isLast && (
                  <small id={`${o.id}-last`} className="block text-xs text-amber-700">
                    {LAST_PRODUCT_COPY}
                  </small>
                )}
              </li>
            );
          })}
        </ul>
      )}

      <div className="mt-3 flex gap-2">
        <button
          type="button"
          disabled={unchanged || selected.length === 0 || save.isPending}
          onClick={() => save.mutate(selected, { onSuccess: () => onClose(true) })}
          className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        >
          Save products
        </button>
        <button
          type="button"
          onClick={() => onClose(false)}
          className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700"
        >
          Cancel
        </button>
      </div>

      {release.status === 'OPEN' && (
        <small className="mt-2 block text-xs text-amber-700">
          Changing products marks this release&apos;s existing documents as out of date. Regenerate them to include the
          change.
        </small>
      )}

      {problem && (
        <p role="alert" data-reason={problem.reason} className="mt-2 text-sm text-red-600">
          {messageForReason(problem)}
        </p>
      )}
    </div>
  );
}
