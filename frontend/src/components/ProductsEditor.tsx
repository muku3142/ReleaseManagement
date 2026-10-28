import { useRef, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useProducts } from '../api/products';
import { useSetReleaseProduct, type Release } from '../api/releases';
import { SMALL_BUTTON } from './EnvironmentLadderSection';
import { Spinner } from './ExportButtons';

export const PRODUCT_CHIP = 'rounded border border-gray-300 bg-gray-100 px-2 py-0.5 text-xs font-semibold text-gray-700';
const EDITOR_ID = 'release-product-editor';
const INSTALLS_NOTE_ID = 'release-product-installs';
const HAS_INSTALLS_COPY = "Can't change: installs are recorded for this product on this release.";

// D-15-15: the lock is advisory; the server rejects the change with PRODUCT_HAS_INSTALLS_ON_RELEASE.
export function ProductsEditor({ release }: { release: Release }) {
  const [open, setOpen] = useState(false);
  const [announcement, setAnnouncement] = useState('');
  const editButton = useRef<HTMLButtonElement>(null);
  const current = release.products[0];
  const blocked = current?.hasInstalls === true;

  function close(saved: boolean) {
    setOpen(false);
    setAnnouncement(saved ? 'Product updated.' : '');
    editButton.current?.focus();
  }

  return (
    <div className="mt-2">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="font-semibold text-foreground">Product:</span>
        {current ? (
          <span className={PRODUCT_CHIP}>
            {current.name}
            {current.archived ? ' (archived)' : ''}
          </span>
        ) : (
          <span className="text-muted-foreground">Unassigned</span>
        )}
        <button
          type="button"
          ref={editButton}
          aria-expanded={open}
          aria-controls={EDITOR_ID}
          aria-disabled={blocked ? 'true' : undefined}
          aria-describedby={blocked ? INSTALLS_NOTE_ID : undefined}
          onClick={() => {
            if (!blocked) setOpen((o) => !o);
          }}
          className={SMALL_BUTTON}
        >
          {current ? 'Change product' : 'Choose product'}
        </button>
      </div>
      {blocked && (
        <small id={INSTALLS_NOTE_ID} className="mt-1 block text-xs text-warning">
          {HAS_INSTALLS_COPY}
        </small>
      )}
      <p role="status" aria-live="polite" className="sr-only">
        {announcement}
      </p>
      {open && <EditorPanel release={release} onClose={close} />}
    </div>
  );
}

function EditorPanel({ release, onClose }: { release: Release; onClose: (saved: boolean) => void }) {
  const products = useProducts();
  const save = useSetReleaseProduct(release.id);
  const currentId = release.products[0]?.id;
  const [selectedId, setSelectedId] = useState<string | undefined>(currentId);

  const options = (products.data?.products ?? [])
    .filter((p) => !p.archived || p.id === currentId)
    .map((p) => ({ id: p.id, name: p.name, archived: p.archived, noJiraProject: p.jiraProjectKeys.length === 0 }));
  const current = release.products[0];
  if (current && !options.some((o) => o.id === current.id)) {
    options.push({ id: current.id, name: current.name, archived: current.archived, noJiraProject: false });
  }
  options.sort((a, b) => a.name.localeCompare(b.name));

  const problem = save.isError && save.error instanceof ApiError ? save.error.problem : undefined;

  return (
    <div
      id={EDITOR_ID}
      className="mt-3 rounded border border-border bg-card p-4"
      onKeyDown={(event) => {
        if (event.key === 'Escape') onClose(false);
      }}
    >
      <fieldset>
        <legend className="text-sm font-semibold text-foreground">Product of this release</legend>
        {products.isSuccess && options.length === 0 ? (
          <p className="mt-2 text-sm text-muted-foreground">
            No products yet. Add one on the{' '}
            <Link to="/portfolio" className="text-primary underline">
              Portfolio page
            </Link>
            .
          </p>
        ) : (
          <ul className="mt-2 max-h-64 space-y-2 overflow-y-auto">
            {options.map((o) => (
              <li key={o.id}>
                <label className="flex items-center gap-2 text-sm text-foreground">
                  <input
                    type="radio"
                    name="release-product"
                    className="size-4 accent-primary"
                    checked={selectedId === o.id}
                    onChange={() => setSelectedId(o.id)}
                  />
                  {o.name}
                  {o.archived && <small className="text-xs text-muted-foreground">archived</small>}
                  {o.noJiraProject && <small className="text-xs text-muted-foreground"> · no Jira project</small>}
                </label>
              </li>
            ))}
          </ul>
        )}
      </fieldset>

      <div className="mt-3 flex gap-2">
        <button
          type="button"
          disabled={selectedId === undefined || selectedId === currentId || save.isPending}
          onClick={() => {
            if (selectedId) save.mutate(selectedId, { onSuccess: () => onClose(true) });
          }}
          className="inline-flex items-center gap-2 rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
        >
          {save.isPending ? (
            <>
              <Spinner />
              Saving…
            </>
          ) : (
            'Save product'
          )}
        </button>
        <button
          type="button"
          onClick={() => onClose(false)}
          className="rounded border border-border px-4 py-2 text-sm text-foreground"
        >
          Cancel
        </button>
      </div>

      {problem && (
        <p role="alert" data-reason={problem.reason} className="mt-2 text-sm text-destructive">
          {messageForReason(problem)}
        </p>
      )}
    </div>
  );
}
