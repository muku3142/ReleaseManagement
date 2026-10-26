import { Fragment, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams, useSearch } from '@tanstack/react-router';
import { useAssistantStatus } from '../api/assistant';
import { chatActions } from '../assistant/chatStore';
import { ReleaseAssistantDrawer } from '../assistant/ReleaseAssistantDrawer';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useDocumentReadiness } from '../api/documents';
import { ProductsEditor } from '../components/ProductsEditor';
import { PackagesLink } from '../components/PackagesLink';
import { SMALL_BUTTON } from '../components/EnvironmentLadderSection';
import { Spinner } from '../components/ExportButtons';
import { useAddFromRelease, type RoadmapAddResult as RoadmapAddResultData } from '../api/roadmap';
import { RoadmapAddResult } from '../roadmap/RoadmapAddResult';
import {
  MANUAL_ITEM_CATEGORIES,
  MANUAL_ITEM_CATEGORY_LABELS,
  useAddManualItem,
  useDeleteRelease,
  useFreezeRelease,
  useRefreshCodeLinks,
  useRefreshRelease,
  useRelease,
  useRemoveManualItem,
  useRemoveTicketItem,
  useUnfreezeRelease,
  useUpdateManualItem,
  type ManualItemCategory,
  type ReleaseAddResult,
} from '../api/releases';
import { AddTicketsToReleaseDialog } from '../components/AddTicketsToReleaseDialog';
import { CodeLinksChip, CodeLinksDetail } from '../components/CodeLinks';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { DocumentsSection } from '../components/DocumentsSection';
import { ItemChurn } from '../components/ItemChurn';
import { ClientInstallsSection } from '../components/ClientInstallsSection';
import { PromotionSection } from '../components/PromotionSection';

// D-03-13: idempotent add, honest feedback - never phrased as a warning, because a duplicate
// add is a success, not a partial failure. A key Jira did not return is neither added nor
// already present, so it gets its own clause rather than being silently absorbed into "added".
export function addResultMessage(
  added: number,
  alreadyPresent: number,
  notFound: number,
  name: string,
  version: string,
) {
  if (notFound > 0) {
    return `${added} added, ${alreadyPresent} already on this release, ${notFound} not found in Jira.`;
  }
  if (alreadyPresent === 0) return `${added} ticket(s) added to ${name} ${version}.`;
  if (added === 0) return `All ${alreadyPresent} selected tickets were already on this release.`;
  return `${added} added, ${alreadyPresent} already on this release.`;
}

export function ReleaseDetailRoute() {
  // The pathless authenticated layout route is registered with id: 'authenticated' (no `path`),
  // so TanStack Router's generated route-id space prefixes every child with /authenticated even
  // though the actual URL (and `to=` navigation) has no such segment.
  const { releaseId } = useParams({ from: '/authenticated/releases/$releaseId' });
  const { added, alreadyPresent, notFound } = useSearch({
    from: '/authenticated/releases/$releaseId',
  });
  const navigate = useNavigate();
  const release = useRelease(releaseId);
  const [expandedKey, setExpandedKey] = useState<string | null>(null);
  const assistant = useAssistantStatus(true);
  const [assistantOpen, setAssistantOpen] = useState(false);
  const assistantToggle = useRef<HTMLButtonElement>(null);
  const hash = useLocation({ select: (l) => l.hash });

  useEffect(() => {
    setAssistantOpen(false);
    setUnfrozenThisVisit(false);
  }, [releaseId]);

  const addTicketsButton = useRef<HTMLButtonElement>(null);
  const [focusAddTickets, setFocusAddTickets] = useState(false);
  const [announcement, setAnnouncement] = useState('');
  const [unfrozenThisVisit, setUnfrozenThisVisit] = useState(false);
  const releaseStatus = release.data?.status;
  const readiness = useDocumentReadiness();

  useEffect(() => {
    if (focusAddTickets && releaseStatus === 'OPEN') {
      addTicketsButton.current?.focus();
      setFocusAddTickets(false);
    }
  }, [focusAddTickets, releaseStatus]);

  // The hash is user-controlled: it is only ever compared against loaded ticket keys, never used as a selector.
  const loadedItems = release.data?.items;
  useEffect(() => {
    const fragment = hash.replace(/^#/, '');
    const key = fragment.startsWith('ticket-') ? fragment.slice('ticket-'.length) : null;
    if (key === null || !loadedItems?.some((item) => item.key === key)) return;
    setExpandedKey(key);
    document.getElementById(`ticket-${key}`)?.scrollIntoView({ block: 'center' });
  }, [hash, loadedItems]);
  const [bannerDismissed, setBannerDismissed] = useState(false);
  const showBanner = !bannerDismissed && added !== undefined && alreadyPresent !== undefined;
  const [addOpen, setAddOpen] = useState(false);
  const [addOutcome, setAddOutcome] = useState<ReleaseAddResult | null>(null);

  const refresh = useRefreshRelease();
  const refreshCodeLinks = useRefreshCodeLinks();
  const freeze = useFreezeRelease();
  const unfreeze = useUnfreezeRelease();
  const remove = useDeleteRelease();
  const [confirming, setConfirming] = useState<'freeze' | 'unfreeze' | 'delete' | null>(null);
  const busy =
    refresh.isPending ||
    refreshCodeLinks.isPending ||
    freeze.isPending ||
    unfreeze.isPending ||
    remove.isPending;

  const removeTicket = useRemoveTicketItem();
  const addManualItem = useAddManualItem();
  const updateManualItem = useUpdateManualItem();
  const removeManualItem = useRemoveManualItem();
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState<ManualItemCategory>('OTHER');

  const addToRoadmap = useAddFromRelease();
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [roadmapResult, setRoadmapResult] = useState<RoadmapAddResultData | null>(null);
  const selectAllRef = useRef<HTMLInputElement>(null);
  const itemCount = release.data?.items.length ?? 0;
  const allSelected = itemCount > 0 && selected.size === itemCount;
  const someSelected = selected.size > 0 && !allSelected;
  const roadmapProblem =
    addToRoadmap.isError && addToRoadmap.error instanceof ApiError ? addToRoadmap.error.problem : undefined;

  // indeterminate is a DOM property, not an HTML attribute, so it cannot be set as a JSX prop.
  useEffect(() => {
    if (selectAllRef.current) selectAllRef.current.indeterminate = someSelected;
  }, [someSelected]);

  function toggleSelected(key: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (!next.delete(key)) next.add(key);
      return next;
    });
  }

  function sendToRoadmap(releaseId: string) {
    setRoadmapResult(null);
    addToRoadmap.mutate(
      { releaseId, ticketKeys: [...selected] },
      {
        onSuccess: (result) => {
          setRoadmapResult(result);
          setSelected(new Set());
        },
      },
    );
  }

  const actionProblem =
    (refresh.isError && refresh.error instanceof ApiError && refresh.error.problem) ||
    (refreshCodeLinks.isError && refreshCodeLinks.error instanceof ApiError && refreshCodeLinks.error.problem) ||
    (freeze.isError && freeze.error instanceof ApiError && freeze.error.problem) ||
    (unfreeze.isError && unfreeze.error instanceof ApiError && unfreeze.error.problem) ||
    (remove.isError && remove.error instanceof ApiError && remove.error.problem) ||
    (removeTicket.isError && removeTicket.error instanceof ApiError && removeTicket.error.problem) ||
    (addManualItem.isError && addManualItem.error instanceof ApiError && addManualItem.error.problem) ||
    (updateManualItem.isError && updateManualItem.error instanceof ApiError && updateManualItem.error.problem) ||
    (removeManualItem.isError && removeManualItem.error instanceof ApiError && removeManualItem.error.problem) ||
    undefined;

  const problem = release.isError && release.error instanceof ApiError ? release.error.problem : undefined;

  if (release.isPending) {
    return (
      <div className="flex items-center gap-3">
        <span
          className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
          aria-hidden="true"
        />
        <p className="text-sm text-gray-600">Loading release…</p>
      </div>
    );
  }

  if (problem && problem.reason === 'RELEASE_NOT_FOUND') {
    return (
      <div>
        <p role="alert" data-reason="RELEASE_NOT_FOUND" className="text-sm text-red-600">
          {messageForReason(problem)}
        </p>
        <Link to="/releases" className="mt-2 inline-block text-sm text-blue-600 underline">
          Back to releases
        </Link>
      </div>
    );
  }

  if (problem) {
    return (
      <div>
        <p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
          {messageForReason(problem)}
        </p>
        <button
          type="button"
          onClick={() => release.refetch()}
          className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700"
        >
          Try again
        </button>
      </div>
    );
  }

  const data = release.data;
  if (!data) return null;

  const isFrozen = data.status === 'FROZEN';
  const isOpen = data.status === 'OPEN';
  const blocked = data.unfreezeBlockedReason !== null;
  const hasStaleDocument =
    readiness.data?.entries?.some((e) => e.releaseId === data.id && e.state === 'STALE') ?? false;
  const noItems = data.items.length === 0 && data.manualItems.length === 0;
  const uncheckedCount = data.items.filter(
    (i) => i.codeLinks.status === 'FAILED' || i.codeLinks.status === 'NOT_ATTEMPTED',
  ).length;

  const saving = addManualItem.isPending || updateManualItem.isPending;
  const manualItemProblem =
    (addManualItem.isError && addManualItem.error instanceof ApiError && addManualItem.error.problem) ||
    (updateManualItem.isError && updateManualItem.error instanceof ApiError && updateManualItem.error.problem) ||
    undefined;
  const titleError =
    manualItemProblem?.reason === 'VALIDATION_FAILED' ? manualItemProblem.fieldErrors?.title : undefined;

  function resetManualItemForm() {
    setTitle('');
    setDescription('');
    setCategory('OTHER');
    setEditingId(null);
    setFormOpen(false);
  }

  function startEditingManualItem(item: { id: string; title: string; description: string; category: ManualItemCategory }) {
    setEditingId(item.id);
    setTitle(item.title);
    setDescription(item.description);
    setCategory(item.category);
    setFormOpen(true);
  }

  const currentReleaseId = data.id;

  function handleManualItemSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (editingId) {
      updateManualItem.mutate(
        { releaseId: currentReleaseId, manualItemId: editingId, title: title.trim(), description, category },
        { onSuccess: resetManualItemForm },
      );
    } else {
      addManualItem.mutate(
        { releaseId: currentReleaseId, title: title.trim(), description, category },
        { onSuccess: resetManualItemForm },
      );
    }
  }

  return (
    <div className="relative w-full">
      {assistantOpen && assistant.data?.enabled && (
        <ReleaseAssistantDrawer
          releaseId={data.id}
          label={`${data.name} ${data.version}`}
          starters={assistant.data.starters.release}
          onClose={() => {
            setAssistantOpen(false);
            assistantToggle.current?.focus();
          }}
        />
      )}
      <div className="bg-white p-6">
        <div className="flex items-center">
          <h2 className="text-base font-semibold text-gray-900 inline">{data.name}</h2>
          <span className="ml-2 text-sm text-gray-600">{data.version}</span>
          {isFrozen ? (
            <span className="ml-3 rounded-full bg-gray-800 px-2 py-0.5 text-sm text-white">Frozen</span>
          ) : (
            <span className="ml-3 rounded-full bg-gray-100 px-2 py-0.5 text-sm text-gray-700">Open</span>
          )}
          <span className="grow" />
          <p role="status" aria-live="polite" aria-atomic="true" className="sr-only">
            {announcement}
          </p>
          {assistant.data?.enabled && (
            <button
              type="button"
              ref={assistantToggle}
              aria-expanded={assistantOpen}
              aria-controls="assistant-drawer"
              onClick={() => {
                if (assistantOpen) {
                  chatActions.stop(`drawer:${data.id}`);
                  assistantToggle.current?.focus();
                }
                setAssistantOpen(!assistantOpen);
              }}
              className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 focus-visible:ring-2 focus-visible:ring-blue-600"
            >
              Ask about this release
            </button>
          )}
        </div>

        <div className="mt-2 space-x-4 text-sm text-gray-600">
          {data.jiraProjectKey !== null && <span>Project: {data.jiraProjectKey}</span>}
          <span>
            Created by {data.createdBy} on {new Date(data.createdAt).toLocaleString()}
          </span>
          {isFrozen && data.frozenAt !== null && (
            <span>
              Frozen by {data.frozenBy} on {new Date(data.frozenAt).toLocaleString()}
            </span>
          )}
          {data.lastRefreshedAt !== null && (
            <span>Last refreshed: {new Date(data.lastRefreshedAt).toLocaleString()}</span>
          )}
        </div>

        <ProductsEditor release={data} />

        <PackagesLink release={data} />

        {isOpen && (
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <button
              type="button"
              ref={addTicketsButton}
              onClick={() => setAddOpen(true)}
              disabled={busy}
              className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
            >
              Add tickets
            </button>
            <button
              type="button"
              onClick={() => refresh.mutate(data.id)}
              disabled={busy}
              className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700 disabled:opacity-50"
            >
              Refresh from Jira
            </button>
            {refresh.isPending && (
              <span className="flex items-center gap-2 text-sm text-gray-600">
                <span
                  className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
                  aria-hidden="true"
                />
                Refreshing…
              </span>
            )}
            <button
              type="button"
              onClick={() => refreshCodeLinks.mutate({ releaseId: data.id })}
              disabled={busy}
              className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700 disabled:opacity-50"
            >
              Refresh code links
            </button>
            {refreshCodeLinks.isPending && (
              <span className="flex items-center gap-2 text-sm text-gray-600">
                <span
                  className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
                  aria-hidden="true"
                />
                Refreshing code links…
              </span>
            )}
            <button
              type="button"
              onClick={() => setConfirming('freeze')}
              disabled={busy}
              className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
            >
              Freeze release
            </button>
            <button
              type="button"
              onClick={() => setConfirming('delete')}
              disabled={busy}
              className="rounded border border-red-600 px-4 py-2 text-sm font-semibold text-red-600 disabled:opacity-50"
            >
              Delete release
            </button>
          </div>
        )}

        {isFrozen && (
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={() => {
                if (!blocked) setConfirming('unfreeze');
              }}
              disabled={busy}
              aria-disabled={blocked ? 'true' : undefined}
              aria-describedby={blocked ? 'unfreeze-blocked-reason' : undefined}
              className={`rounded border border-gray-300 px-4 py-2 text-sm text-gray-700 focus-visible:ring-2 focus-visible:ring-blue-600 disabled:opacity-50${blocked ? ' cursor-not-allowed opacity-50' : ''}`}
            >
              Unfreeze release
            </button>
            {unfreeze.isPending && (
              <span className="flex items-center gap-2 text-sm text-gray-600">
                <span
                  className="h-4 w-4 animate-spin motion-reduce:animate-none rounded-full border-2 border-gray-300 border-t-blue-600"
                  aria-hidden="true"
                />
                Unfreezing…
              </span>
            )}
          </div>
        )}
        {data.unfreezeBlockedReason !== null && (
          <p id="unfreeze-blocked-reason" className="mt-2 text-xs text-amber-700">
            {messageForReason({ status: 409, reason: data.unfreezeBlockedReason })}
          </p>
        )}

        {isOpen && unfrozenThisVisit && hasStaleDocument && (
          <p className="mt-2 text-xs text-amber-700">
            This release was unfrozen. Its existing documents are out of date and will need regenerating after you
            freeze it again.
          </p>
        )}

        {actionProblem && (
          <p role="alert" data-reason={actionProblem.reason} className="mt-2 text-sm text-red-600">
            {messageForReason(actionProblem)}
          </p>
        )}

        {isFrozen && (
          <p className="mt-4 rounded bg-gray-100 p-3 text-sm text-gray-700">
            {blocked
              ? 'This release is frozen. Its ticket set is locked and cannot be changed.'
              : 'This release is frozen. Its ticket set is locked. Unfreeze it to change tickets, if nothing has been deployed or installed yet.'}
          </p>
        )}
      </div>

      <ConfirmDialog
        open={confirming === 'freeze'}
        title={`Freeze ${data.name} ${data.version}?`}
        body="Freezing locks the ticket set and manual items exactly as they are now. You cannot add, remove, or refresh items while it is frozen. You can unfreeze it again until a promotion or client install is recorded; after that, create a new release if scope changes."
        confirmLabel="Freeze release"
        confirmClassName="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        pending={freeze.isPending}
        // onSettled, not onSuccess: showModal() makes the rest of the document inert, so a
        // failure that left the dialog open would render actionProblem behind the backdrop.
        onConfirm={() => freeze.mutate(data.id, { onSettled: () => setConfirming(null) })}
        onCancel={() => setConfirming(null)}
      >
        {uncheckedCount > 0 && (
          <>
            <p className="mt-3 text-sm text-amber-800">
              {uncheckedCount} ticket(s) have code links that failed or were never checked. Frozen releases keep
              whatever code links they have.
            </p>
            <button
              type="button"
              onClick={() => {
                setConfirming(null);
                refreshCodeLinks.mutate({ releaseId: data.id });
              }}
              className="mt-2 rounded border border-gray-300 px-4 py-2 text-sm text-gray-700"
            >
              Refresh code links first
            </button>
          </>
        )}
      </ConfirmDialog>

      <ConfirmDialog
        open={confirming === 'unfreeze'}
        title={`Unfreeze ${data.name} ${data.version}?`}
        body="Unfreezing reopens this release so you can add, remove, or refresh tickets. Nothing has been deployed or installed, so no history is affected. Documents generated so far will be marked out of date once you change the tickets, until you regenerate them. Freeze the release again when the ticket set is final."
        confirmLabel="Unfreeze release"
        confirmClassName="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        pending={unfreeze.isPending}
        // onSettled closes the dialog for the same reason as freeze: a rejection must be readable.
        onConfirm={() =>
          unfreeze.mutate(data.id, {
            onSuccess: () => {
              setFocusAddTickets(true);
              setUnfrozenThisVisit(true);
              setAnnouncement(`${data.name} ${data.version} unfrozen. Tickets can be edited.`);
            },
            onSettled: () => setConfirming(null),
          })
        }
        onCancel={() => setConfirming(null)}
      />

      <ConfirmDialog
        open={confirming === 'delete'}
        title={`Delete ${data.name} ${data.version}?`}
        body="This permanently deletes the release and its items. This cannot be undone."
        confirmLabel="Delete release"
        confirmClassName="rounded bg-red-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        pending={remove.isPending}
        onConfirm={() =>
          remove.mutate(data.id, {
            onSuccess: () => navigate({ to: '/releases' }),
            onSettled: () => setConfirming(null),
          })
        }
        onCancel={() => setConfirming(null)}
      />

      <AddTicketsToReleaseDialog
        open={addOpen}
        releaseId={data.id}
        releaseName={data.name}
        version={data.version}
        onAdded={setAddOutcome}
        onClose={() => setAddOpen(false)}
      />

      {showBanner && (
        <div
          role="status"
          className="mt-4 flex items-start justify-between rounded border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900"
        >
          <p>{addResultMessage(added, alreadyPresent, notFound ?? 0, data.name, data.version)}</p>
          <button
            type="button"
            aria-label="Dismiss"
            onClick={() => setBannerDismissed(true)}
            className="ml-3 rounded px-2 text-blue-900 focus-visible:ring-2 focus-visible:ring-blue-600"
          >
            ×
          </button>
        </div>
      )}

      {addOutcome !== null && (
        <div
          role="status"
          className="mt-4 flex items-start justify-between rounded border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900"
        >
          <p>
            {addResultMessage(
              addOutcome.added,
              addOutcome.alreadyPresent,
              addOutcome.notFound,
              data.name,
              data.version,
            )}
          </p>
          <button
            type="button"
            aria-label="Dismiss added tickets"
            onClick={() => setAddOutcome(null)}
            className="ml-3 rounded px-2 text-blue-900 focus-visible:ring-2 focus-visible:ring-blue-600"
          >
            ×
          </button>
        </div>
      )}

      {noItems ? (
        <div className="mt-6 rounded bg-gray-50 p-6 text-center">
          {isFrozen ? (
            <p className="text-sm text-gray-600">This release has no items.</p>
          ) : (
            <>
              <p className="text-base font-semibold text-gray-900">No items yet</p>
              <p className="mt-2 text-sm text-gray-600">
                Add tickets from the ticket browser, or add a manual item below.
              </p>
            </>
          )}
        </div>
      ) : (
        <>
          <h3 className="mt-6 text-base font-semibold text-gray-900">Tickets ({data.items.length})</h3>
          {selected.size > 0 && (
            <div className="mt-2 flex flex-wrap items-center gap-3 text-sm">
              <span role="status" aria-live="polite" className="text-gray-700">
                {selected.size} selected
              </span>
              <button
                type="button"
                className={SMALL_BUTTON}
                onClick={() => setSelected(new Set(data.items.map((item) => item.key)))}
              >
                Select all
              </button>
              <button type="button" className={SMALL_BUTTON} onClick={() => setSelected(new Set())}>
                Clear
              </button>
              <button
                type="button"
                disabled={addToRoadmap.isPending}
                onClick={() => sendToRoadmap(data.id)}
                className="flex items-center gap-2 rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700 disabled:opacity-50"
              >
                {addToRoadmap.isPending && <Spinner />}
                {addToRoadmap.isPending ? 'Adding…' : 'Add to roadmap'}
              </button>
            </div>
          )}
          {roadmapResult && (
            <div className="mt-2 text-sm text-gray-700">
              <RoadmapAddResult result={roadmapResult} showRoadmapLink />
            </div>
          )}
          {roadmapProblem && (
            <p role="alert" data-reason={roadmapProblem.reason} className="mt-2 text-sm text-red-600">
              Couldn&apos;t add to the roadmap — {messageForReason(roadmapProblem)} Try again.
            </p>
          )}
          <div className="mt-4 overflow-x-auto rounded border border-gray-200 bg-white">
            <table className="min-w-full text-sm">
              <thead className="border-b border-gray-200">
                <tr>
                  <th scope="col" className="px-4 py-2">
                    <input
                      type="checkbox"
                      ref={selectAllRef}
                      aria-label="Select all tickets"
                      checked={allSelected}
                      onChange={() =>
                        setSelected(allSelected ? new Set() : new Set(data.items.map((item) => item.key)))
                      }
                    />
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Key
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Summary
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Type
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Status
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Code
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Churn
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Resolution
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Assignee
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Reporter
                  </th>
                  <th scope="col" className="px-4 py-2" />
                  {isOpen && <th scope="col" className="px-4 py-2" />}
                </tr>
              </thead>
              <tbody>
                {data.items.map((item) => (
                  <Fragment key={item.key}>
                    {/* Same row-click behaviour as TicketBrowser: the arrow stays for keyboard users, and
                        controls inside the row (chip, Retry, Remove) and text selection don't toggle. */}
                    <tr
                      id={`ticket-${item.key}`}
                      className="cursor-pointer border-b border-gray-100 hover:bg-gray-50"
                      onClick={(event) => {
                        if ((event.target as HTMLElement).closest('button, a, input, label')) return;
                        if (window.getSelection()?.toString()) return;
                        setExpandedKey(expandedKey === item.key ? null : item.key);
                      }}
                    >
                      <td className="px-4 py-2">
                        <input
                          type="checkbox"
                          aria-label={`Select ${item.key}`}
                          checked={selected.has(item.key)}
                          onChange={() => toggleSelected(item.key)}
                        />
                      </td>
                      <td className="whitespace-nowrap px-4 py-2 font-semibold text-gray-900">{item.key}</td>
                      <td className="min-w-[18rem] px-4 py-2 text-gray-700">{item.summary}</td>
                      <td className="whitespace-nowrap px-4 py-2 text-gray-700">{item.issueType}</td>
                      <td className="whitespace-nowrap px-4 py-2 text-gray-700">{item.status}</td>
                      <td className="px-4 py-2">
                        <CodeLinksChip
                          links={item.codeLinks}
                          onRetry={
                            isOpen
                              ? () => refreshCodeLinks.mutate({ releaseId: data.id, ticketKey: item.key })
                              : undefined
                          }
                          retryDisabled={busy}
                        />
                      </td>
                      <td className="px-4 py-2">
                        <ItemChurn churn={item.churn} />
                      </td>
                      <td className="whitespace-nowrap px-4 py-2 text-gray-700">{item.resolution ?? '—'}</td>
                      <td className="whitespace-nowrap px-4 py-2 text-gray-700">{item.assignee ?? '—'}</td>
                      <td className="whitespace-nowrap px-4 py-2 text-gray-700">{item.reporter ?? '—'}</td>
                      <td className="px-4 py-2">
                        <button
                          type="button"
                          onClick={() => setExpandedKey(expandedKey === item.key ? null : item.key)}
                          aria-expanded={expandedKey === item.key}
                          aria-label={
                            expandedKey === item.key ? 'Collapse ticket details' : 'Expand ticket details'
                          }
                          className="rounded px-2 text-sm text-gray-600 hover:text-gray-900 focus-visible:ring-2 focus-visible:ring-blue-600"
                        >
                          {expandedKey === item.key ? '▾' : '▸'}
                        </button>
                      </td>
                      {isOpen && (
                        <td className="px-4 py-2">
                          <button
                            type="button"
                            onClick={() => removeTicket.mutate({ releaseId: data.id, ticketKey: item.key })}
                            disabled={removeTicket.isPending}
                            aria-label={`Remove ticket ${item.key}`}
                            className="text-sm text-gray-600 hover:text-gray-900 disabled:opacity-50"
                          >
                            Remove
                          </button>
                        </td>
                      )}
                    </tr>
                    {expandedKey === item.key && (
                      <tr>
                        <td colSpan={isOpen ? 12 : 11}className="px-4 pb-4">
                          <div className="max-h-80 overflow-y-auto rounded border border-gray-200 bg-gray-50 p-4">
                            <h3 className="text-sm font-semibold text-gray-900">Description</h3>
                            <p className="mt-2 whitespace-pre-wrap text-sm text-gray-700">
                              {item.description || 'No description.'}
                            </p>

                            <h3 className="mt-4 text-sm font-semibold text-gray-900">
                              Comments
                              {item.commentTotal > item.comments.length
                                ? ` (showing ${item.comments.length} of ${item.commentTotal})`
                                : ''}
                            </h3>
                            {item.comments.length === 0 ? (
                              <p className="mt-2 text-sm text-gray-600">No comments.</p>
                            ) : (
                              <ul className="mt-2 space-y-3">
                                {item.comments.map((comment, index) => (
                                  <li
                                    key={index}
                                    className="whitespace-pre-wrap border-l-2 border-gray-200 pl-3 text-sm text-gray-700"
                                  >
                                    {comment}
                                  </li>
                                ))}
                              </ul>
                            )}

                            <CodeLinksDetail links={item.codeLinks} />
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>

          <h3 className="mt-6 text-base font-semibold text-gray-900">
            Manual items ({data.manualItems.length})
          </h3>
          <div className="mt-4 overflow-x-auto rounded border border-gray-200 bg-white">
            <table className="min-w-full text-sm">
              <thead className="border-b border-gray-200">
                <tr>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Title
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Category
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Description
                  </th>
                  {isOpen && <th scope="col" className="px-4 py-2" />}
                </tr>
              </thead>
              <tbody>
                {data.manualItems.map((item) => (
                  <tr key={item.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="whitespace-nowrap px-4 py-2 font-semibold text-gray-900">{item.title}</td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">
                      {MANUAL_ITEM_CATEGORY_LABELS[item.category]}
                    </td>
                    <td
                      className="max-w-xs truncate px-4 py-2 text-gray-700"
                      title={item.description}
                    >
                      {item.description}
                    </td>
                    {isOpen && (
                      <td className="whitespace-nowrap px-4 py-2">
                        <button
                          type="button"
                          onClick={() => startEditingManualItem(item)}
                          className="mr-3 text-sm text-gray-600 hover:text-gray-900"
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          onClick={() => removeManualItem.mutate({ releaseId: data.id, manualItemId: item.id })}
                          disabled={removeManualItem.isPending}
                          aria-label={`Remove manual item ${item.title}`}
                          className="text-sm text-gray-600 hover:text-gray-900 disabled:opacity-50"
                        >
                          Remove
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {isOpen && (
        <button
          type="button"
          // Clearing editingId alone left title/description/category holding the row that was
          // being edited, so reopening read as a create form prefilled with it - and submitting
          // created a duplicate manual item, which release_manual_item has no constraint against.
          onClick={() => (formOpen ? resetManualItemForm() : setFormOpen(true))}
          aria-expanded={formOpen}
          className="mt-3 text-sm text-blue-600 underline"
        >
          + Add manual item
        </button>
      )}

      {formOpen && (
        <form onSubmit={handleManualItemSubmit} className="mt-2 space-y-3">
          <div>
            <label htmlFor="manualTitle" className="block text-sm font-semibold text-gray-700">
              Title
            </label>
            <input
              id="manualTitle"
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={200}
              className="mt-1 rounded border border-gray-300 px-3 py-2 text-sm"
            />
            {titleError && (
              <p role="alert" className="mt-1 text-sm text-red-600">
                {titleError}
              </p>
            )}
          </div>
          <div>
            <label htmlFor="manualDescription" className="block text-sm font-semibold text-gray-700">
              Description
            </label>
            <textarea
              id="manualDescription"
              rows={3}
              maxLength={5000}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label htmlFor="manualCategory" className="block text-sm font-semibold text-gray-700">
              Category
            </label>
            <select
              id="manualCategory"
              value={category}
              onChange={(e) => setCategory(e.target.value as ManualItemCategory)}
              className="mt-1 rounded border border-gray-300 px-3 py-2 text-sm"
            >
              {MANUAL_ITEM_CATEGORIES.map((value) => (
                <option key={value} value={value}>
                  {MANUAL_ITEM_CATEGORY_LABELS[value]}
                </option>
              ))}
            </select>
          </div>
          <button
            type="submit"
            disabled={saving || title.trim() === ''}
            className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
          >
            {editingId ? 'Save changes' : 'Add item'}
          </button>
        </form>
      )}

      <PromotionSection releaseId={data.id} isFrozen={isFrozen} />
      <ClientInstallsSection releaseId={data.id} isFrozen={isFrozen} />
      <DocumentsSection releaseId={data.id} releaseName={data.name} version={data.version} />
    </div>
  );
}
