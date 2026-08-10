import { Fragment, useState } from 'react';
import { Link, useNavigate, useParams, useSearch } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/auth';
import {
  MANUAL_ITEM_CATEGORIES,
  MANUAL_ITEM_CATEGORY_LABELS,
  useAddManualItem,
  useDeleteRelease,
  useFreezeRelease,
  useRefreshRelease,
  useRelease,
  useRemoveManualItem,
  useRemoveTicketItem,
  useUpdateManualItem,
  type ManualItemCategory,
} from '../api/releases';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { DocumentsSection } from '../components/DocumentsSection';

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
  const [bannerDismissed, setBannerDismissed] = useState(false);
  const showBanner = !bannerDismissed && added !== undefined && alreadyPresent !== undefined;

  const refresh = useRefreshRelease();
  const freeze = useFreezeRelease();
  const remove = useDeleteRelease();
  const [confirming, setConfirming] = useState<'freeze' | 'delete' | null>(null);
  const busy = refresh.isPending || freeze.isPending || remove.isPending;

  const removeTicket = useRemoveTicketItem();
  const addManualItem = useAddManualItem();
  const updateManualItem = useUpdateManualItem();
  const removeManualItem = useRemoveManualItem();
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState<ManualItemCategory>('OTHER');

  const actionProblem =
    (refresh.isError && refresh.error instanceof ApiError && refresh.error.problem) ||
    (freeze.isError && freeze.error instanceof ApiError && freeze.error.problem) ||
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
  const noItems = data.items.length === 0 && data.manualItems.length === 0;

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
    <div className="w-full">
      <div className="bg-white p-6">
        <div>
          <h2 className="text-base font-semibold text-gray-900 inline">{data.name}</h2>
          <span className="ml-2 text-sm text-gray-600">{data.version}</span>
          {isFrozen ? (
            <span className="ml-3 rounded-full bg-gray-800 px-2 py-0.5 text-sm text-white">Frozen</span>
          ) : (
            <span className="ml-3 rounded-full bg-gray-100 px-2 py-0.5 text-sm text-gray-700">Open</span>
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

        {isOpen && (
          <div className="mt-4 flex flex-wrap items-center gap-3">
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

        {actionProblem && (
          <p role="alert" data-reason={actionProblem.reason} className="mt-2 text-sm text-red-600">
            {messageForReason(actionProblem)}
          </p>
        )}

        {isFrozen && (
          <p className="mt-4 rounded bg-gray-100 p-3 text-sm text-gray-700">
            This release is frozen. Its ticket set is locked and cannot be changed.
          </p>
        )}
      </div>

      <ConfirmDialog
        open={confirming === 'freeze'}
        title={`Freeze ${data.name} ${data.version}?`}
        body="Freezing locks the ticket set and manual items exactly as they are now. You cannot add, remove, or refresh items afterward, and there is no way to unfreeze. If scope changes later, create a new release."
        confirmLabel="Freeze release"
        confirmClassName="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        pending={freeze.isPending}
        // onSettled, not onSuccess: showModal() makes the rest of the document inert, so a
        // failure that left the dialog open would render actionProblem behind the backdrop.
        onConfirm={() => freeze.mutate(data.id, { onSettled: () => setConfirming(null) })}
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
          <div className="mt-4 overflow-x-auto rounded border border-gray-200 bg-white">
            <table className="min-w-full text-sm">
              <thead className="border-b border-gray-200">
                <tr>
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
                    <tr className="border-b border-gray-100 hover:bg-gray-50">
                      <td className="whitespace-nowrap px-4 py-2 font-semibold text-gray-900">{item.key}</td>
                      <td className="min-w-[18rem] px-4 py-2 text-gray-700">{item.summary}</td>
                      <td className="whitespace-nowrap px-4 py-2 text-gray-700">{item.issueType}</td>
                      <td className="whitespace-nowrap px-4 py-2 text-gray-700">{item.status}</td>
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
                        <td colSpan={isOpen ? 9 : 8} className="px-4 pb-4">
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

      <DocumentsSection releaseId={data.id} releaseName={data.name} version={data.version} />
    </div>
  );
}
