import { useEffect, useRef, useState } from 'react';
import { useNavigate } from '@tanstack/react-router';
import { Spinner } from '@/components/ui/spinner';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import {
  openReleases,
  useAddTicketsToRelease,
  useCreateRelease,
  useReleases,
  groupKeys,
  type ReleaseAddResult,
  type TicketGroup,
} from '../api/releases';

// D-03-01: this component owns exactly one job - choose a destination release for an
// already-chosen selection (ticket groups). D-17.2-01: the product is fixed by the Tickets page, so the
// dialog only offers that product's open releases and creates releases for it.
export function AddToReleaseDialog(props: {
  open: boolean;
  groups: TicketGroup[];
  productId: string;
  productName: string;
  startWithForm?: boolean;
  onClose: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const nameInputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  const releases = useReleases('');
  const createRelease = useCreateRelease();
  const addTickets = useAddTicketsToRelease();

  const [filter, setFilter] = useState('');
  const [formOpenState, setFormOpenState] = useState(false);
  const [name, setName] = useState('');
  const [version, setVersion] = useState('');

  // The dialog is permanently mounted by TicketsRoute, so without this reset it reopens against a
  // new ticket selection still holding the previous attempt's form values and error - and
  // resubmitting would re-send the same rejected name and version.
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (props.open && !dialog.open) {
      setName('');
      setVersion('');
      setFilter('');
      setFormOpenState(false);
      createRelease.reset();
      addTickets.reset();
      dialog.showModal();
      if (props.startWithForm) nameInputRef.current?.focus();
    }
    if (!props.open && dialog.open) dialog.close();
  }, [props.open, props.productId]);

  const targets = openReleases(releases.data?.releases ?? []).filter((r) =>
    r.products.some((p) => p.id === props.productId),
  );
  const zeroOpenReleases = !props.startWithForm && releases.isSuccess && targets.length === 0;
  // D-03-02: collapsed by default when the product has at least one open release; auto-expanded
  // when it has none, and always open when the user came to create a new release.
  const formOpen = formOpenState || zeroOpenReleases || props.startWithForm === true;

  const ticketCount = groupKeys(props.groups).length;
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
    !props.startWithForm && releases.isError && releases.error instanceof ApiError
      ? releases.error.problem
      : undefined;
  const showList = !props.startWithForm && !listProblem && !releases.isPending && targets.length > 0;

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
        productId: props.productId,
        groups: props.groups,
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
  // its own input. Everything else - a ticketKeys[n] violation, JIRA_UNREACHABLE,
  // PRODUCT_NO_JIRA_PROJECT, PRODUCT_ARCHIVED - would otherwise be swallowed.
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
        {props.startWithForm
          ? `New release with ${ticketCount} ticket(s)`
          : `Add ${ticketCount} ticket(s) to a release`}
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">Product: {props.productName}</p>

      {!props.startWithForm && releases.isPending && (
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

      {zeroOpenReleases && (
        <p className="mt-4 text-sm text-muted-foreground">
          {props.productName} has no open releases. Create one below.
        </p>
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
                  aria-label={`Add to ${r.name} ${r.version}`}
                  onClick={() =>
                    addTickets.mutate(
                      { releaseId: r.id, groups: props.groups },
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

      {!props.startWithForm && (
        <button
          type="button"
          onClick={() => setFormOpenState((open) => !open)}
          aria-expanded={formOpen}
          className="mt-4 text-sm text-blue-600 underline"
        >
          New release…
        </button>
      )}

      {formOpen && (
        <form onSubmit={handleCreateSubmit} className="mt-3 space-y-2">
          <div>
            <label htmlFor="releaseName" className="block text-sm font-semibold text-gray-700">
              Release name
            </label>
            <input
              ref={nameInputRef}
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
            disabled={createRelease.isPending || name.trim() === '' || version.trim() === ''}
            className="inline-flex items-center gap-2 rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
          >
            {createRelease.isPending ? (
              <>
                <Spinner />
                Creating release…
              </>
            ) : props.startWithForm ? (
              'Create release'
            ) : (
              'Create & add'
            )}
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
