import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useProducts } from '../api/products';
import {
  openReleases,
  useAddTicketsToRelease,
  useCreateRelease,
  useReleases,
  type ReleaseAddResult,
} from '../api/releases';

// Rendered only inside the "New release…" form so /api/products is requested only once it is open.
function ProductPicker(props: { selected: string[]; onChange: (ids: string[]) => void }) {
  const products = useProducts();
  const active = (products.data?.products ?? [])
    .filter((p) => !p.archived)
    .sort((a, b) => a.name.localeCompare(b.name));

  return (
    <fieldset>
      <legend className="block text-sm font-semibold text-gray-700">Products</legend>
      <small className="block text-xs text-gray-600">
        Choose at least one product this release covers. You can change them later.
      </small>
      {products.isSuccess && active.length === 0 ? (
        <small className="mt-1 block text-xs text-gray-600">
          Add a product on the{' '}
          <Link to="/portfolio" className="text-blue-600 underline">
            Portfolio page
          </Link>{' '}
          first.
        </small>
      ) : (
        <div className="mt-1 max-h-40 space-y-1 overflow-y-auto rounded border border-gray-300 p-3">
          {active.map((p) => (
            <label key={p.id} className="flex items-center gap-2 text-sm font-normal text-gray-900">
              <input
                type="checkbox"
                checked={props.selected.includes(p.id)}
                onChange={(event) =>
                  props.onChange(
                    event.target.checked ? [...props.selected, p.id] : props.selected.filter((id) => id !== p.id),
                  )
                }
              />
              {p.name}
            </label>
          ))}
        </div>
      )}
    </fieldset>
  );
}

// D-03-01: this component owns exactly one job - choose a destination release for an
// already-chosen ticketKeys array. It contains no ticket search, project picker, sprint picker
// or ticket table of its own.
export function AddToReleaseDialog(props: {
  open: boolean;
  ticketKeys: string[];
  jiraProjectKey: string | null;
  onClose: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const navigate = useNavigate();

  const releases = useReleases('');
  const createRelease = useCreateRelease();
  const addTickets = useAddTicketsToRelease();

  const [filter, setFilter] = useState('');
  const [formOpenState, setFormOpenState] = useState(false);
  const [name, setName] = useState('');
  const [version, setVersion] = useState('');
  const [productIds, setProductIds] = useState<string[]>([]);

  // The dialog is permanently mounted by TicketsRoute, so without this reset it reopens against a
  // new ticket selection still holding the previous attempt's form values and error - and
  // resubmitting would re-send the same rejected name and version.
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (props.open && !dialog.open) {
      setName('');
      setVersion('');
      setProductIds([]);
      setFilter('');
      setFormOpenState(false);
      createRelease.reset();
      addTickets.reset();
      dialog.showModal();
    }
    if (!props.open && dialog.open) dialog.close();
  }, [props.open]);

  const targets = openReleases(releases.data?.releases ?? []);
  const zeroOpenReleases = releases.isSuccess && targets.length === 0;
  // D-03-02: collapsed by default when at least one open release exists; auto-expanded, with the
  // list section skipped entirely, when none do.
  const formOpen = formOpenState || zeroOpenReleases;

  const trimmedFilter = filter.trim().toLowerCase();
  const filteredTargets =
    targets.length > 8 && trimmedFilter !== ''
      ? targets.filter(
          (r) =>
            r.name.toLowerCase().includes(trimmedFilter) ||
            r.version.toLowerCase().includes(trimmedFilter),
        )
      : targets;

  const listProblem =
    releases.isError && releases.error instanceof ApiError ? releases.error.problem : undefined;
  const showList = !listProblem && !releases.isPending && targets.length > 0;

  function goToRelease(result: ReleaseAddResult) {
    props.onClose();
    navigate({
      to: '/releases/$releaseId',
      params: { releaseId: result.release.id },
      search: {
        added: result.added,
        alreadyPresent: result.alreadyPresent,
        notFound: result.notFound,
      },
    });
  }

  function handleCreateSubmit(event: React.FormEvent) {
    event.preventDefault();
    createRelease.mutate(
      {
        name: name.trim(),
        version: version.trim(),
        jiraProjectKey: props.jiraProjectKey,
        ticketKeys: props.ticketKeys,
        productIds,
      },
      { onSuccess: goToRelease },
    );
  }

  const addProblem =
    addTickets.isError && addTickets.error instanceof ApiError ? addTickets.error.problem : undefined;

  const createProblem =
    createRelease.isError && createRelease.error instanceof ApiError
      ? createRelease.error.problem
      : undefined;
  const nameError =
    createProblem?.reason === 'VALIDATION_FAILED' ? createProblem.fieldErrors?.name : undefined;
  const versionError =
    createProblem?.reason === 'VALIDATION_FAILED' ? createProblem.fieldErrors?.version : undefined;
  // RELEASE_DUPLICATE has its own copy below, and a name/version field error is rendered against
  // its own input. Everything else - a ticketKeys[n] violation, JIRA_UNREACHABLE, a frozen target
  // - would otherwise be swallowed.
  const showCreateProblem =
    createProblem !== undefined &&
    createProblem.reason !== 'RELEASE_DUPLICATE' &&
    nameError === undefined &&
    versionError === undefined;

  return (
    <dialog
      ref={dialogRef}
      onClose={props.onClose}
      className="rounded border border-gray-200 bg-white p-6"
    >
      <h2 className="text-base font-semibold text-gray-900">
        Add {props.ticketKeys.length} ticket(s) to a release
      </h2>

      {releases.isPending && (
        <div className="mt-4 flex items-center gap-3">
          <span
            className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
            aria-hidden="true"
          />
          <p className="text-sm text-gray-600">Loading releases…</p>
        </div>
      )}

      {listProblem && (
        <div className="mt-4">
          <p role="alert" data-reason={listProblem.reason} className="text-sm text-red-600">
            {messageForReason(listProblem)}
          </p>
          <button
            type="button"
            onClick={() => releases.refetch()}
            className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700"
          >
            Try again
          </button>
        </div>
      )}

      {showList && (
        <div className="mt-4">
          {targets.length > 8 && (
            <input
              type="search"
              placeholder="Filter by name or version…"
              aria-label="Filter releases"
              value={filter}
              onChange={(event) => setFilter(event.target.value)}
              className="mb-3 w-full rounded border border-gray-300 px-3 py-2 text-sm"
            />
          )}
          <ul className="space-y-2">
            {filteredTargets.map((r) => (
              <li key={r.id} className="flex items-center justify-between gap-3">
                <span>
                  <span className="text-sm font-semibold text-gray-900">{r.name}</span>
                  <span className="ml-2 text-sm text-gray-600">{r.version}</span>
                </span>
                <button
                  type="button"
                  onClick={() =>
                    addTickets.mutate(
                      { releaseId: r.id, ticketKeys: props.ticketKeys },
                      { onSuccess: goToRelease },
                    )
                  }
                  disabled={addTickets.isPending}
                  className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 disabled:opacity-50"
                >
                  Add
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

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

      <button
        type="button"
        onClick={() => setFormOpenState((open) => !open)}
        aria-expanded={formOpen}
        className="mt-4 text-sm text-blue-600 underline"
      >
        New release…
      </button>

      {formOpen && (
        <form onSubmit={handleCreateSubmit} className="mt-3 space-y-2">
          <div>
            <label htmlFor="releaseName" className="block text-sm font-semibold text-gray-700">
              Release name
            </label>
            <input
              id="releaseName"
              type="text"
              required
              value={name}
              onChange={(event) => setName(event.target.value)}
              className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label htmlFor="releaseVersion" className="block text-sm font-semibold text-gray-700">
              Version
            </label>
            <input
              id="releaseVersion"
              type="text"
              required
              value={version}
              onChange={(event) => setVersion(event.target.value)}
              className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
            />
          </div>
          <ProductPicker selected={productIds} onChange={setProductIds} />

          {nameError && <p className="text-sm text-red-600">{nameError}</p>}
          {versionError && <p className="text-sm text-red-600">{versionError}</p>}
          {createProblem?.reason === 'RELEASE_DUPLICATE' && (
            <p role="alert" data-reason="RELEASE_DUPLICATE" className="mt-2 text-sm text-red-600">
              {`A release named '${name}' version '${version}' already exists. Choose a different name or version.`}
            </p>
          )}
          {showCreateProblem && (
            <p role="alert" data-reason={createProblem.reason} className="mt-2 text-sm text-red-600">
              {messageForReason(createProblem)}
            </p>
          )}

          <button
            type="submit"
            disabled={
              createRelease.isPending || name.trim() === '' || version.trim() === '' || productIds.length === 0
            }
            className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
          >
            Create &amp; add
          </button>
        </form>
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
